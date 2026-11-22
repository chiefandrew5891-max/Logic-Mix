"""Конфигурация языков. Новый язык = новая запись здесь, ядро не меняется."""
from __future__ import annotations

import re
from dataclasses import dataclass


@dataclass(frozen=True)
class LanguageConfig:
    code: str                 # тег приложения: en, ru, ..., zh-CN
    alphabet: str             # допустимые буквы (нижний регистр) или regex-класс при is_regex
    fold: dict                # свёртка для игровой формы (например ё→е); хранится отдельно от оригинала
    allow_chars: str = ""     # допустимые небуквенные символы внутри слова (апостроф, дефис)
    game_min: int = 3
    game_max: int = 10
    wordsearch: bool = True   # допускается ли категория на языке (согласовано с CategoryAvailability)
    crossword: bool = True

    def letter_re(self) -> re.Pattern:
        return re.compile(f"^[{self.alphabet}]+(?:[{re.escape(self.allow_chars)}][{self.alphabet}]+)*$"
                          if self.allow_chars else f"^[{self.alphabet}]+$")


LANGUAGES: dict[str, LanguageConfig] = {c.code: c for c in [
    LanguageConfig("en", "a-z", {}),
    LanguageConfig("ru", "а-яё", {"ё": "е"}, "-"),
    LanguageConfig("uk", "а-щьюяєіїґ", {}, "'’-"),
    LanguageConfig("it", "a-zàèéìíòóùú", {}),
    LanguageConfig("de", "a-zäöüß", {}),
    LanguageConfig("fr", "a-zàâæçéèêëîïôœùûüÿ", {}, "-"),
    LanguageConfig("es", "a-záéíóúüñ", {}),
    # Интерфейс сохраняется; WORD_SEARCH и MINI_CROSSWORD скрыты клиентским CategoryAvailability.
    LanguageConfig("zh-CN", "\u4e00-\u9fff", {}, wordsearch=False, crossword=False, game_min=2, game_max=4),
]}


def get(code: str) -> LanguageConfig:
    if code not in LANGUAGES:
        raise KeyError(f"language not configured: {code}")
    return LANGUAGES[code]
