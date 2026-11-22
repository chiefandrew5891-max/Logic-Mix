"""Адаптеры источников. Все читают поток строк/байт и отдают RawEntry лениво (ограниченная память)."""
from __future__ import annotations

import gzip
import json
from typing import Iterable, Iterator, Protocol

from ..models import RawEntry


class SourceAdapter(Protocol):
    source_id: str

    def parse(self, lines: Iterable[str], lang: str) -> Iterator[RawEntry]: ...


def open_text(path: str) -> Iterator[str]:
    opener = gzip.open if path.endswith(".gz") else open
    with opener(path, "rt", encoding="utf-8") as f:   # type: ignore[operator]
        yield from f


class KaikkiAdapter:
    """JSONL Kaikki.org: {word, pos, lang_code, senses:[{glosses:[..]}], forms:[{form}]}.
    Записи-словоформы (form_of / alt_of в senses) помечаются, а не теряются."""
    source_id = "kaikki"

    def parse(self, lines, lang):
        for line in lines:
            line = line.strip()
            if not line:
                continue
            try:
                o = json.loads(line)
            except json.JSONDecodeError:
                yield RawEntry(lang, "")   # пустая запись → будет отброшена и учтена в rejects
                continue
            if not isinstance(o, dict) or o.get("lang_code", lang) != lang:
                continue
            senses = o.get("senses") or []
            defs = [g for s in senses for g in (s.get("glosses") or []) if isinstance(g, str)]
            is_form = bool(senses) and all(("form_of" in s or "alt_of" in s) for s in senses)
            forms = [f["form"] for f in (o.get("forms") or []) if isinstance(f, dict) and f.get("form")]
            yield RawEntry(lang, str(o.get("word", "")), o.get("pos"), defs, forms, form_of=is_form)


class HunspellAdapter:
    """Читает .dic (первая строка — число записей; далее `слово/флаги`). Аффиксы .aff не разворачиваются:
    адаптер даёт орфографически допустимые базовые формы (расширение флагов — отдельная задача)."""
    source_id = "libreoffice"

    def parse(self, lines, lang):
        it = iter(lines)
        first = next(it, None)
        if first is None:
            return
        if not first.strip().isdigit():
            it = iter([first, *it])
        for line in it:
            w = line.strip().split("/")[0].split("\t")[0]
            if w:
                yield RawEntry(lang, w)


class FrequencyTsvAdapter:
    """TSV `слово<TAB>zipf` (экспорт wordfreq). Определений не даёт."""
    source_id = "wordfreq"

    def parse(self, lines, lang):
        for line in lines:
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 2 or not parts[0]:
                continue
            try:
                yield RawEntry(lang, parts[0], freq=float(parts[1]))
            except ValueError:
                yield RawEntry(lang, "")


class WikidataAdapter:
    """JSONL `{label, categories:[..], relations:[{type, target}]}` (предобработанная выгрузка Wikidata)."""
    source_id = "wikidata"

    def parse(self, lines, lang):
        for line in lines:
            line = line.strip()
            if not line:
                continue
            try:
                o = json.loads(line)
            except json.JSONDecodeError:
                yield RawEntry(lang, "")
                continue
            rel = [(r["type"], r["target"]) for r in o.get("relations", []) if "type" in r and "target" in r]
            yield RawEntry(lang, str(o.get("label", "")), categories=list(o.get("categories", [])), relations=rel)


class FixtureAdapter(KaikkiAdapter):
    """Fixtures используют формат Kaikki + необязательные поля freq/categories/relations."""
    source_id = "fixture"

    def parse(self, lines, lang):
        for line in lines:
            line = line.strip()
            if not line:
                continue
            try:
                o = json.loads(line)
            except json.JSONDecodeError:
                yield RawEntry(lang, "")
                continue
            yield RawEntry(lang, str(o.get("word", "")), o.get("pos"), list(o.get("definitions", [])),
                           list(o.get("forms", [])), o.get("freq"),
                           [tuple(r) if isinstance(r, list) else (r["type"], r["target"]) for r in o.get("relations", [])],
                           list(o.get("categories", [])))


ADAPTERS = {a.source_id: a for a in (KaikkiAdapter(), HunspellAdapter(), FrequencyTsvAdapter(),
                                     WikidataAdapter(), FixtureAdapter())}
