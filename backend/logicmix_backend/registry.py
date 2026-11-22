"""Реестр источников. Источник — кандидат, пока license_status != approved.

Поля лицензии ниже — **предварительные сведения**, которые необходимо перепроверить по первоисточнику
(license_checked_on=None означает «не проверено»). В production попадают только пакеты из approved-источников.
"""
from __future__ import annotations

import json
from dataclasses import dataclass, field, asdict
from pathlib import Path


@dataclass
class SourceInfo:
    id: str
    name: str
    url: str
    priority: int
    provides: list[str]           # words, definitions, forms, spelling, relations, frequency
    formats: list[str]
    languages: list[str]
    license_claimed: str          # заявленное, непроверенное
    share_alike: bool | None
    attribution_required: bool | None
    commercial_use: bool | None   # None = не установлено
    license_status: str = "needs_review"   # needs_review | approved | rejected
    license_checked_on: str | None = None  # дата ручной проверки
    notes: str = ""

    def approved(self) -> bool:
        return self.license_status == "approved" and bool(self.license_checked_on)


DEFAULT_SOURCES = [
    SourceInfo("kaikki", "Kaikki.org (Wiktionary extract)", "https://kaikki.org/dictionary/", 1,
               ["words", "definitions", "forms", "pos"], ["jsonl", "jsonl.gz"],
               ["en", "ru", "uk", "it", "de", "fr", "es", "zh-CN"],
               "Wiktionary: CC BY-SA 4.0 (+GFDL) — заявлено, не проверено", True, True, None,
               notes="ShareAlike: производные данные, вероятно, должны распространяться на тех же условиях; "
                     "требуется юридическая оценка до production. Проверить условия автоматического доступа."),
    SourceInfo("wikidata", "Wikidata", "https://www.wikidata.org/wiki/Wikidata:Data_access", 2,
               ["relations", "categories"], ["json", "jsonl"], ["en", "ru", "uk", "it", "de", "fr", "es", "zh-CN"],
               "CC0 для структурированных данных — заявлено, не проверено", False, False, None,
               notes="Соблюдать лимиты запросов и User-Agent; использовать дампы, а не массовый SPARQL."),
    SourceInfo("libreoffice", "LibreOffice Dictionaries", "https://github.com/LibreOffice/dictionaries", 3,
               ["spelling", "forms"], ["hunspell"], ["en", "ru", "uk", "it", "de", "fr", "es"],
               "Лицензия зависит от словаря (GPL/LGPL/MPL/др.) — не проверено", None, True, None,
               notes="Проверять лицензию каждого языкового словаря отдельно; часть может быть copyleft."),
    SourceInfo("wordfreq", "wordfreq", "https://pypi.org/project/wordfreq/", 4,
               ["frequency"], ["python-api", "tsv"], ["en", "ru", "uk", "it", "de", "fr", "es", "zh-CN"],
               "Код Apache-2.0; данные — смесь источников с собственными условиями — не проверено", None, True, None,
               notes="Даёт только частотность; определений не содержит."),
    SourceInfo("fixture", "Тестовые fixtures проекта", "local://tests/fixtures", 99,
               ["words", "definitions", "frequency", "relations"], ["jsonl"], ["en", "ru"],
               "Собственные данные проекта (написаны для тестов)", False, False, True,
               license_status="approved", license_checked_on="2026-11-22",
               notes="Только для тестов и staging-прототипа; не является языковой библиотекой."),
]


class SourceRegistry:
    def __init__(self, sources: list[SourceInfo] | None = None):
        self._s = {s.id: s for s in (sources if sources is not None else DEFAULT_SOURCES)}

    def get(self, sid: str) -> SourceInfo:
        return self._s[sid]

    def all(self) -> list[SourceInfo]:
        return sorted(self._s.values(), key=lambda s: s.priority)

    def production_blockers(self, source_ids: list[str]) -> list[str]:
        return [i for i in source_ids if not self._s[i].approved()]

    def dump(self, path: Path) -> None:
        path.write_text(json.dumps([asdict(s) for s in self.all()], ensure_ascii=False, indent=2), "utf-8")
