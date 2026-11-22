"""Импорт одного источника на один язык и слияние данных нескольких источников."""
from __future__ import annotations

import hashlib
import logging
from collections import Counter
from typing import Iterable

from . import languages
from .models import Entry, RawEntry
from .normalize import Policy, Rejected, normalize, classify
from .registry import SourceRegistry
from .store import Store

log = logging.getLogger("logicmix.pipeline")


def import_source(store: Store, registry: SourceRegistry, adapter, source_id: str, lang: str,
                  lines_factory, policy: Policy | None = None, source_version: str | None = None) -> dict:
    """Изолирует сбой: при исключении запуск помечается failed, предыдущий успешный запуск остаётся актуальным.
    lines_factory() -> Iterable[str] (может бросить исключение: источник недоступен)."""
    policy = policy or Policy()
    cfg = languages.get(lang)
    registry.get(source_id)
    run = store.start_run(source_id, lang, source_version)
    h, rejects, acc = hashlib.sha256(), Counter(), 0
    batch: dict[tuple, Entry] = {}
    try:
        def tee() -> Iterable[str]:
            for ln in lines_factory():
                h.update(ln.encode("utf-8"))
                yield ln
        for raw in adapter.parse(tee(), lang):
            try:
                e = normalize(raw, cfg, policy)
            except Rejected as r:
                rejects[r.reason] += 1
                continue
            e.sources = [source_id]
            k = (e.lemma, e.pos)
            if k in batch:
                _merge(batch[k], e)
                rejects["duplicate_merged"] += 1
            else:
                batch[k] = e
        if not batch:
            raise ValueError("source produced no valid entries")
        digest = h.hexdigest()
        if digest == store.last_checksum(source_id, lang):
            store.finish_run(run, "unchanged", 0, {}, digest)
            store.purge_run(run)
            return {"run": run, "status": "unchanged"}
        store.add_entries(run, batch.values())
        acc = len(batch)
        store.finish_run(run, "ok", acc, dict(rejects), digest)
        return {"run": run, "status": "ok", "accepted": acc, "rejects": dict(rejects)}
    except Exception as ex:          # изоляция сбоя одного источника
        log.error("import %s/%s failed: %s", source_id, lang, ex)
        store.purge_run(run)
        store.finish_run(run, "failed", 0, dict(rejects), error=f"{type(ex).__name__}: {ex}")
        return {"run": run, "status": "failed", "error": str(ex)}


def _merge(a: Entry, b: Entry) -> None:
    for d in b.definitions:
        if d.casefold() not in {x.casefold() for x in a.definitions}:
            a.definitions.append(d)
    a.forms = sorted(set(a.forms) | set(b.forms))
    a.categories = sorted(set(a.categories) | set(b.categories))
    a.relations = list(dict.fromkeys(a.relations + b.relations))
    a.freq = a.freq if a.freq is not None else b.freq
    a.sources = sorted(set(a.sources) | set(b.sources))


def merge_language(store: Store, registry: SourceRegistry, lang: str, policy: Policy | None = None) -> list[Entry]:
    """Объединяет последние успешные запуски всех источников языка. Определения — только из источников,
    которые их дают; частотность и категории дополняют записи, но не создают их без словаря/определений."""
    policy = policy or Policy()
    cfg = languages.get(lang)
    merged: dict[str, Entry] = {}
    def _enrich(s):
        return "words" not in s.provides and "spelling" not in s.provides
    for src in sorted(registry.all(), key=lambda s: (_enrich(s), s.priority)):   # словари первыми
        run = store.last_ok_run(src.id, lang)
        if run is None:
            continue
        enrich_only = _enrich(src)
        for (l, lemma, original, game, pos, defs, forms, freq, cats, rels) in store.records(run):
            import json as _j
            e = Entry(l, lemma, original, game, pos, _j.loads(defs), _j.loads(forms), freq, _j.loads(cats),
                      [tuple(r) for r in _j.loads(rels)], {}, [src.id])
            cur = merged.get(lemma)
            if cur is None:
                if not enrich_only:      # частотность/семантика не создают слова, отсутствующие в словаре
                    merged[lemma] = e
            else:
                _merge(cur, e)
                cur.pos = cur.pos or e.pos
    out = []
    for e in merged.values():
        e.suitability = classify(e, cfg, policy)
        out.append(e)
    return sorted(out, key=lambda e: e.lemma)
