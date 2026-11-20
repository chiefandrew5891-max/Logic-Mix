# Word-search dictionaries

Each language has its own file `app/src/main/resources/wordsearch/<tag>.txt` (`#core` = words the generator may
place, `#extra` = additional accepted words). Languages: en, uk, ru, it, es, de, fr. There is no zh-CN file:
the category is hidden for Chinese (see `CategoryAvailability`).

## Current status (honest)

The committed files are **small hand-curated seed lists of common everyday words** (about 48 core and 25 extra
words per language, written natively per language, not translated from one another). They are enough for the
12 levels per language, but they are **not** a full dictionary: valid words outside the list are rejected as
"not in the dictionary".

`build_dictionary.py` is the pipeline for real dictionaries. It was **not run** for the committed files:
kaikki.org was not reachable from the build sandbox, so source, version and licence below must be confirmed
before any generated dictionary is committed.

## Planned source and licence (to verify before use)

- Kaikki.org (wiktextract) machine-readable Wiktionary data: https://kaikki.org/dictionary/rawdata.html
- Underlying text is Wiktionary, licensed CC BY-SA 4.0 (and GFDL). Attribution is required and derivative word lists
  fall under share-alike: needs a legal check before commercial use; record the dump date/version here.
- Frequency ranking: e.g. hermitdave/FrequencyWords (OpenSubtitles-derived, check its licence first).
- Do not put the raw dump into the repository or the APK; commit only the compact output.

## Rules applied by the script

Only lowercase (non-proper) entries; letters only (no spaces, hyphens, apostrophes); drops names, prefixes, symbols,
abbreviations and entries whose senses are all offensive/vulgar/archaic/dialectal; optional blocklist file; ranks by
frequency then length; core = nouns/adjectives of 3-8 letters. Normalisation: accents stripped for en/it/fr/es
(es keeps ñ), de keeps ä/ö/ü and drops words with ß, ru maps ё to е, uk keeps ґ є і ї.
