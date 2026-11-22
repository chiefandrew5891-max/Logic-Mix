"""Нормализация, валидация и классификация пригодности. Правила берутся из LanguageConfig."""
from __future__ import annotations

import unicodedata
from dataclasses import dataclass

from .languages import LanguageConfig
from .models import RawEntry, Entry

_BAD_CTRL = {"Cc", "Cf", "Co", "Cs", "Cn"}
MAX_DEF = 300


@dataclass
class Policy:
    blocklist: frozenset[str] = frozenset()   # нежелательные слова (нормализованные), конфигурируется файлом
    allowed_pos: frozenset[str] = frozenset({"noun", "verb", "adj", "adv", "name"})


class Rejected(Exception):
    def __init__(self, reason: str):
        super().__init__(reason)
        self.reason = reason


def clean_text(s: str) -> str:
    return unicodedata.normalize("NFC", s).strip()


def has_broken_chars(s: str) -> bool:
    return "\ufffd" in s or any(unicodedata.category(c) in _BAD_CTRL for c in s)


def normalize(raw: RawEntry, cfg: LanguageConfig, policy: Policy) -> Entry:
    if raw.lang != cfg.code:
        raise Rejected("language_mismatch")
    original = clean_text(raw.word)
    if not original:
        raise Rejected("empty")
    if has_broken_chars(original):
        raise Rejected("broken_chars")
    lemma = original.casefold()
    if not cfg.letter_re().match(lemma):
        raise Rejected("alphabet")
    if raw.form_of:
        raise Rejected("inflected_form")
    if lemma in policy.blocklist:
        raise Rejected("blocklist")
    defs, seen = [], set()
    for d in raw.definitions:
        d = clean_text(d)
        if d and not has_broken_chars(d) and len(d) <= MAX_DEF and d.casefold() not in seen:
            seen.add(d.casefold())
            defs.append(d)
    forms = sorted({f.casefold() for f in map(clean_text, raw.forms) if f and cfg.letter_re().match(f.casefold())})
    game = lemma.upper()
    for a, b in cfg.fold.items():
        game = game.replace(a.upper(), b.upper())
    game = "".join(c for c in game if c.isalpha())   # дефисы/апострофы в игровой форме не используются
    pos = raw.pos.lower() if raw.pos else None
    return Entry(cfg.code, lemma, original, game, pos, defs, forms, raw.freq, list(raw.categories),
                 list(raw.relations), {}, [])


def classify(e: Entry, cfg: LanguageConfig, policy: Policy) -> dict:
    """Наличие слова в словаре не делает его пригодным для любой игры."""
    n = len(e.game)
    simple = e.lemma.isalpha() and (e.pos is None or e.pos in policy.allowed_pos)   # без дефисов/апострофов
    in_range = cfg.game_min <= n <= cfg.game_max
    return {
        "wordsearch": cfg.wordsearch and simple and in_range and n >= 4,
        "crossword": cfg.crossword and simple and in_range and bool(e.definitions)
                     and not _def_leaks_answer(e),
        "wordlogic": bool(e.categories) and simple,
    }


def _def_leaks_answer(e: Entry) -> bool:
    """Определение не должно содержать само слово (иначе подсказка выдаёт ответ)."""
    return all(e.lemma in d.casefold() for d in e.definitions)
