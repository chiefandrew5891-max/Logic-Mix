from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum


class Status(str, Enum):
    DRAFT = "draft"
    GENERATED = "generated"
    VALIDATED = "validated"
    APPROVED = "approved"
    PUBLISHED = "published"
    RETIRED = "retired"
    REJECTED = "rejected"


_NEXT = {
    Status.DRAFT: {Status.GENERATED, Status.REJECTED},
    Status.GENERATED: {Status.VALIDATED, Status.REJECTED},
    Status.VALIDATED: {Status.APPROVED, Status.REJECTED},
    Status.APPROVED: {Status.PUBLISHED, Status.REJECTED},
    Status.PUBLISHED: {Status.RETIRED},
    Status.RETIRED: set(),
    Status.REJECTED: set(),
}


def advance(cur: Status, nxt: Status) -> Status:
    if nxt not in _NEXT[cur]:
        raise ValueError(f"illegal transition {cur.value} -> {nxt.value}")
    return nxt


@dataclass
class RawEntry:
    """Запись, как её отдаёт адаптер (до нормализации)."""
    lang: str
    word: str
    pos: str | None = None
    definitions: list[str] = field(default_factory=list)
    forms: list[str] = field(default_factory=list)
    freq: float | None = None                    # Zipf-оценка, если источник её даёт
    relations: list[tuple[str, str]] = field(default_factory=list)  # (тип, целевое слово/понятие)
    categories: list[str] = field(default_factory=list)
    form_of: bool = False                        # запись — словоформа другой леммы


@dataclass
class Entry:
    lang: str
    lemma: str            # нормализованная лемма (NFC, нижний регистр)
    original: str
    game: str             # игровая форма (верхний регистр, со свёрткой)
    pos: str | None
    definitions: list[str]
    forms: list[str]
    freq: float | None
    categories: list[str]
    relations: list[tuple[str, str]]
    suitability: dict     # {"wordsearch": bool, "crossword": bool, "wordlogic": bool}
    sources: list[str]
