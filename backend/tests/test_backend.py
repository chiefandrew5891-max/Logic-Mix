import gzip
import hashlib
import json
import tempfile
import unittest
import urllib.error
from pathlib import Path

from logicmix_backend import languages
from logicmix_backend.adapters.sources import (ADAPTERS, KaikkiAdapter, HunspellAdapter, FrequencyTsvAdapter,
                                               WikidataAdapter, open_text)
from logicmix_backend.client_reference import ClientCache
from logicmix_backend.fetch import fetch_bytes, FetchError, RateLimiter
from logicmix_backend.models import Status, advance
from logicmix_backend.normalize import Policy
from logicmix_backend.packs import build_pack, validate_pack_bytes, PackError
from logicmix_backend.pipeline import import_source, merge_language
from logicmix_backend.publisher import Publisher, LocalObjectStore, PublishError
from logicmix_backend.registry import SourceRegistry, SourceInfo
from logicmix_backend.store import Store

FX = Path(__file__).parent / "fixtures"
POLICY = Policy(blocklist=frozenset({"badword"}))


def imp(store, reg, lang, src="fixture", policy=POLICY):
    return import_source(store, reg, ADAPTERS[src], src, lang, lambda: open_text(str(FX / f"{lang}_fixture.jsonl")), policy)


class Imports(unittest.TestCase):
    def setUp(self):
        self.reg, self.store = SourceRegistry(), Store()

    def test_valid_import_filters_bad_records(self):
        r = imp(self.store, self.reg, "en")
        self.assertEqual(r["status"], "ok")
        rej = r["rejects"]
        self.assertEqual(rej["blocklist"], 1)
        self.assertEqual(rej["alphabet"], 2)          # cat's, zzzz9
        self.assertGreaterEqual(rej["empty"], 2)      # пустое слово и битый JSON
        self.assertEqual(rej["duplicate_merged"], 1)  # Apple/apple

    def test_russian_rejects_latin_and_keeps_yo_original(self):
        imp(self.store, self.reg, "ru")
        es = {e.lemma: e for e in merge_language(self.store, self.reg, "ru")}
        self.assertNotIn("hello", es)
        self.assertEqual(es["зелёный"].game, "ЗЕЛЕНЫЙ")
        self.assertEqual(es["зелёный"].original, "зелёный")

    def test_wrong_language_alphabet_not_substituted(self):
        # английский файл, импортируемый как ru: ни одной записи → сбой, данных нет
        r = import_source(self.store, self.reg, ADAPTERS["fixture"], "fixture", "ru",
                          lambda: open_text(str(FX / "en_fixture.jsonl")), POLICY)
        self.assertEqual(r["status"], "failed")
        self.assertEqual(merge_language(self.store, self.reg, "ru"), [])

    def test_empty_and_unavailable_source_keep_last_good(self):
        imp(self.store, self.reg, "en")
        before = len(merge_language(self.store, self.reg, "en"))
        r = import_source(self.store, self.reg, ADAPTERS["fixture"], "fixture", "en", lambda: iter([]), POLICY)
        self.assertEqual(r["status"], "failed")

        def boom():
            raise OSError("network down")
        r = import_source(self.store, self.reg, ADAPTERS["fixture"], "fixture", "en", boom, POLICY)
        self.assertEqual(r["status"], "failed")
        self.assertEqual(len(merge_language(self.store, self.reg, "en")), before)

    def test_unchanged_source_detected(self):
        imp(self.store, self.reg, "en")
        self.assertEqual(imp(self.store, self.reg, "en")["status"], "unchanged")

    def test_suitability_is_per_game(self):
        imp(self.store, self.reg, "en")
        es = {e.lemma: e for e in merge_language(self.store, self.reg, "en")}
        self.assertTrue(es["apple"].suitability["crossword"])
        self.assertFalse(es["bird"].suitability["crossword"])      # нет определения
        self.assertTrue(es["bird"].suitability["wordsearch"])
        self.assertFalse(es["garden"].suitability["crossword"])    # определение выдаёт ответ
        self.assertTrue(es["apple"].suitability["wordlogic"])
        self.assertFalse(es["table"].suitability["wordlogic"])     # нет категории

    def test_frequency_source_cannot_create_words(self):
        imp(self.store, self.reg, "en")
        import_source(self.store, self.reg, ADAPTERS["wordfreq"], "wordfreq", "en",
                      lambda: ["xylophone\t3.0\n", "apple\t6.5\n"])
        es = {e.lemma: e for e in merge_language(self.store, self.reg, "en")}
        self.assertNotIn("xylophone", es)
        self.assertEqual(es["apple"].freq, 4.8)       # приоритет словарного источника
        self.assertIn("wordfreq", es["apple"].sources)


class Adapters(unittest.TestCase):
    def test_kaikki_skips_inflected_forms_and_other_languages(self):
        lines = [json.dumps({"word": "run", "lang_code": "en", "pos": "verb", "senses": [{"glosses": ["to move fast"]}],
                             "forms": [{"form": "ran"}]}),
                 json.dumps({"word": "ran", "lang_code": "en", "pos": "verb", "senses": [{"form_of": [{"word": "run"}], "glosses": ["past of run"]}]}),
                 json.dumps({"word": "бег", "lang_code": "ru", "senses": [{"glosses": ["x"]}]})]
        out = list(KaikkiAdapter().parse(lines, "en"))
        self.assertEqual([e.word for e in out], ["run", "ran"])
        self.assertTrue(out[1].form_of)
        self.assertEqual(out[0].forms, ["ran"])

    def test_hunspell(self):
        out = list(HunspellAdapter().parse(["3\n", "cat/S\n", "dog\n", "bird/MS\n"], "en"))
        self.assertEqual([e.word for e in out], ["cat", "dog", "bird"])

    def test_tsv_and_wikidata(self):
        self.assertEqual(list(FrequencyTsvAdapter().parse(["a\t5.5\n"], "en"))[0].freq, 5.5)
        e = list(WikidataAdapter().parse([json.dumps({"label": "apple", "categories": ["fruit"],
                                                      "relations": [{"type": "p31", "target": "fruit"}]})], "en"))[0]
        self.assertEqual(e.relations, [("p31", "fruit")])


class Fetch(unittest.TestCase):
    class Resp:
        def __init__(self, d): self.d = d
        def __enter__(self): return self
        def __exit__(self, *a): return False
        def read(self, n=-1): return self.d[:n] if n >= 0 else self.d

    def test_retry_with_backoff_then_success(self):
        calls, sleeps = [], []

        def opener(req, timeout):
            calls.append(timeout)
            if len(calls) < 3:
                raise urllib.error.URLError("timeout")
            return self.Resp(b"ok")
        self.assertEqual(fetch_bytes("http://x", opener=opener, sleep=sleeps.append, backoff=1), b"ok")
        self.assertEqual(sleeps, [1, 2])

    def test_gives_up_and_no_retry_on_4xx(self):
        def always_fail(req, timeout): raise urllib.error.URLError("down")
        with self.assertRaises(FetchError):
            fetch_bytes("http://x", opener=always_fail, sleep=lambda s: None, retries=2)

        n = []
        def forbidden(req, timeout):
            n.append(1)
            raise urllib.error.HTTPError("http://x", 403, "no", {}, None)
        with self.assertRaises(FetchError):
            fetch_bytes("http://x", opener=forbidden, sleep=lambda s: None)
        self.assertEqual(len(n), 1)

    def test_empty_and_oversize(self):
        with self.assertRaises(FetchError):
            fetch_bytes("http://x", opener=lambda r, timeout: self.Resp(b""), sleep=lambda s: None, retries=0)
        with self.assertRaises(FetchError):
            fetch_bytes("http://x", opener=lambda r, timeout: self.Resp(b"x" * 100), max_bytes=10, retries=0)

    def test_rate_limiter(self):
        t, slept = [0.0], []
        lim = RateLimiter(2.0, clock=lambda: t[0], sleep=lambda s: (slept.append(s), t.__setitem__(0, t[0] + s)))
        lim.wait(); lim.wait()
        self.assertEqual(slept, [2.0])


def built(reg, lang="en", version=1, types=("wordsearch", "crossword")):
    store = Store()
    imp(store, reg, lang)
    es = merge_language(store, reg, lang)
    return [build_pack(t, lang, es, version, "2026-11-22T00:00:00Z", reg) for t in types]


class Packs(unittest.TestCase):
    def setUp(self):
        self.reg = SourceRegistry()

    def test_deterministic_rebuild_and_checksum(self):
        a, b = built(self.reg), built(self.reg)
        self.assertEqual([p.sha256 for p in a], [p.sha256 for p in b])
        self.assertEqual(a[0].sha256, hashlib.sha256(a[0].gz).hexdigest())
        self.assertLess(len(a[0].gz), len(a[0].raw) + 40)

    def test_valid_pack_and_attribution(self):
        doc = validate_pack_bytes(built(self.reg)[1].gz)
        self.assertEqual(doc["limits"]["crosswordMaxWords"], 10)
        self.assertTrue(doc["attribution"])
        self.assertTrue(all(len(r) == 3 and r[1] for r in doc["rows"]))

    def test_rejects_corrupted_checksum_schema_duplicate_language(self):
        p = built(self.reg)[0]
        with self.assertRaises(PackError): validate_pack_bytes(p.gz, "0" * 64)
        with self.assertRaises(PackError): validate_pack_bytes(b"not gzip")
        doc = json.loads(p.raw)

        def pack(d): return gzip.compress(json.dumps(d).encode())
        with self.assertRaises(PackError): validate_pack_bytes(pack({**doc, "schema": 99}))
        with self.assertRaises(PackError): validate_pack_bytes(pack({**doc, "language": "xx"}))
        with self.assertRaises(PackError): validate_pack_bytes(pack({**doc, "rows": doc["rows"] + [doc["rows"][0]]}))
        with self.assertRaises(PackError): validate_pack_bytes(pack({**doc, "language": "ru"}))   # латиница в ru
        with self.assertRaises(PackError): validate_pack_bytes(pack({**doc, "attribution": []}))
        with self.assertRaises(PackError): validate_pack_bytes(pack({**doc, "minClientVersion": 5}), client_version=1)

    def test_empty_pack_not_built(self):
        with self.assertRaises(PackError):
            build_pack("wordlogic", "en", [], 1, "2026-01-01T00:00:00Z", self.reg)

    def test_chinese_configured_but_games_unavailable(self):
        cfg = languages.get("zh-CN")
        self.assertFalse(cfg.wordsearch or cfg.crossword)

    def test_status_transitions(self):
        self.assertEqual(advance(Status.APPROVED, Status.PUBLISHED), Status.PUBLISHED)
        with self.assertRaises(ValueError): advance(Status.DRAFT, Status.PUBLISHED)
        with self.assertRaises(ValueError): advance(Status.REJECTED, Status.APPROVED)


class Publishing(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.obj = LocalObjectStore(self.tmp.name)
        self.reg = SourceRegistry()
        self.pub = Publisher(self.obj, self.reg)

    def test_requires_approval(self):
        with self.assertRaises(PublishError):
            self.pub.publish("staging", built(self.reg), "t")

    def test_production_blocked_until_license_approved(self):
        reg = SourceRegistry([SourceInfo("fixture", "f", "u", 1, ["words"], ["jsonl"], ["en"], "?", None, None, None)])
        packs = built(SourceRegistry())
        with self.assertRaises(PublishError) as c:
            Publisher(self.obj, reg).publish("production", packs, "t", approved=True)
        self.assertIn("license", str(c.exception))
        self.assertIsNone(self.pub.current("production"))      # манифест не тронут

    def test_publish_then_rollback_and_immutable_files(self):
        m1 = self.pub.publish("staging", built(self.reg, version=1), "t1", approved=True)
        m2 = self.pub.publish("staging", built(self.reg, version=2), "t2", approved=True)
        self.assertEqual((m1["seq"], m2["seq"]), (1, 2))
        files_before = {p.name for p in Path(self.tmp.name).rglob("*.json.gz")}
        r = self.pub.rollback("staging")
        self.assertEqual({p["dataVersion"] for p in r["packs"]}, {1})
        self.assertEqual(r["seq"], 3)
        self.assertEqual({p.name for p in Path(self.tmp.name).rglob("*.json.gz")}, files_before)   # ничего не удалено
        with self.assertRaises(PublishError):
            self.pub.publish("staging", built(self.reg, version=1), "t", approved=True)   # версия должна расти

    def test_channels_isolated(self):
        self.pub.publish("staging", built(self.reg), "t", approved=True)
        self.assertIsNone(self.pub.current("production"))


class ClientProtocol(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.obj = LocalObjectStore(Path(self.tmp.name) / "srv")
        self.reg = SourceRegistry()
        self.pub = Publisher(self.obj, self.reg)
        self.cache = ClientCache(Path(self.tmp.name) / "client", "en")

    def test_install_skip_unchanged_update_and_failed_update_keeps_previous(self):
        self.pub.publish("staging", built(self.reg, version=1), "t", approved=True)
        r1 = self.cache.sync(self.obj.get, "staging")
        self.assertEqual(sorted(r1["installed"]), ["crossword-en", "wordsearch-en"])
        r2 = self.cache.sync(self.obj.get, "staging")
        self.assertEqual(r2["installed"], [])
        self.assertEqual(len(r2["skipped"]), 2)

        self.pub.publish("staging", built(self.reg, version=2), "t2", approved=True)
        calls = []

        def corrupt(key):
            d = self.obj.get(key)
            calls.append(key)
            return d[:-5] + b"xxxxx" if key.endswith(".json.gz") and d else d
        r3 = self.cache.sync(corrupt, "staging")
        self.assertEqual(len(r3["failed"]), 2)
        self.assertEqual(self.cache.installed()["wordsearch-en"]["dataVersion"], 1)   # прежняя версия цела

        def offline(key): raise OSError("no network")
        self.assertTrue(self.cache.sync(offline, "staging")["failed"])
        self.assertEqual(self.cache.installed()["wordsearch-en"]["dataVersion"], 1)

        self.assertEqual(len(self.cache.sync(self.obj.get, "staging")["installed"]), 2)

    def test_language_never_substituted(self):
        store = Store()
        imp(store, self.reg, "en")
        self.pub.publish("staging", built(self.reg), "t", approved=True)
        ru = ClientCache(Path(self.tmp.name) / "ru", "ru")
        r = ru.sync(self.obj.get, "staging")
        self.assertEqual(r["installed"], [])
        self.assertEqual(ru.installed(), {})


if __name__ == "__main__":
    unittest.main()
