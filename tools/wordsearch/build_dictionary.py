#!/usr/bin/env python3
"""Builds a compact word-search dictionary for one language from a kaikki.org (wiktextract) JSONL dump.

Development-time tool: the raw dump (hundreds of MB to GB) is never shipped in the APK. Only the small
output file is copied to app/src/main/resources/wordsearch/<tag>.txt.

Usage:
  python3 build_dictionary.py --lang it --kaikki kaikki.org-dictionary-Italian.jsonl \
      --freq it_50k.txt --core-size 400 --out ../../app/src/main/resources/wordsearch/it.txt

--freq is an optional "word count" list (one pair per line) used to rank words by how common they are.
Without it words are ranked by length only, which is NOT good enough for a release dictionary.
"""
import argparse
import json
import re
import sys
import unicodedata

ALPHABETS = {
    "en": re.compile(r"^[A-Z]+$"),
    "it": re.compile(r"^[A-Z]+$"),
    "fr": re.compile(r"^[A-Z]+$"),
    "es": re.compile(r"^[A-ZÑ]+$"),
    "de": re.compile(r"^[A-ZÄÖÜ]+$"),
    "ru": re.compile(r"^[А-Я]+$"),
    "uk": re.compile(r"^[А-ЩЬЮЯЄІЇҐ]+$"),
}
CORE_POS = {"noun", "adj"}
BAD_TAGS = {
    "vulgar", "offensive", "derogatory", "slur", "pejorative", "obscene", "abbreviation", "initialism",
    "acronym", "archaic", "obsolete", "dialectal", "regional", "rare", "slang", "misspelling",
    "alternative", "form-of", "alt-of", "abbreviation-of",
}
BAD_POS = {"name", "proper-noun", "prefix", "suffix", "infix", "symbol", "character", "abbrev", "contraction"}


def normalize(lang, word):
    word = unicodedata.normalize("NFC", word.strip().lower())
    if lang == "ru":
        word = word.replace("ё", "е")
    if lang in ("it", "fr", "en", "es"):
        keep_n_tilde = lang == "es"
        out = []
        for ch in unicodedata.normalize("NFD", word):
            if unicodedata.combining(ch):
                if keep_n_tilde and ch == "\u0303" and out and out[-1] == "n":
                    out[-1] = "ñ"
                continue
            out.append(ch)
        word = "".join(out)
    return word.upper()


def load_freq(path):
    freq = {}
    if not path:
        return freq
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            parts = line.split()
            if len(parts) == 2 and parts[1].isdigit():
                freq[parts[0].lower()] = int(parts[1])
    return freq


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--lang", required=True, choices=sorted(ALPHABETS))
    ap.add_argument("--kaikki", required=True)
    ap.add_argument("--freq")
    ap.add_argument("--core-size", type=int, default=400)
    ap.add_argument("--extra-size", type=int, default=4000)
    ap.add_argument("--blocklist", help="file with one unwanted word per line")
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    alphabet = ALPHABETS[args.lang]
    freq = load_freq(args.freq)
    blocked = set()
    if args.blocklist:
        with open(args.blocklist, encoding="utf-8") as fh:
            blocked = {normalize(args.lang, w) for w in fh if w.strip()}

    candidates = {}  # normalized word -> (is_core_pos, frequency)
    with open(args.kaikki, encoding="utf-8") as fh:
        for line in fh:
            entry = json.loads(line)
            raw = entry.get("word", "")
            if entry.get("lang_code") not in (None, args.lang) or not raw.isalpha():
                continue
            pos = entry.get("pos", "")
            if pos in BAD_POS:
                continue
            tags = set()
            for sense in entry.get("senses", []):
                tags.update(sense.get("tags", []))
            if tags & BAD_TAGS and not any(not (s.get("tags") and set(s["tags"]) & BAD_TAGS) for s in entry.get("senses", [])):
                continue  # every sense is flagged
            if raw[:1].isupper():
                continue  # proper nouns and sentence-cased entries
            word = normalize(args.lang, raw)
            if not alphabet.match(word) or word in blocked:
                continue
            prev = candidates.get(word, (False, 0))
            candidates[word] = (prev[0] or pos in CORE_POS, freq.get(raw.lower(), prev[1]))

    def rank(item):
        word, (_, f) = item
        return (-f, len(word), word)

    core = [w for w, (is_core, f) in sorted(candidates.items(), key=rank)
            if is_core and 3 <= len(w) <= 8 and (f > 0 or not freq)][: args.core_size]
    core_set = set(core)
    extra = [w for w, _ in sorted(candidates.items(), key=rank)
             if w not in core_set and 3 <= len(w) <= 12][: args.extra_size]

    with open(args.out, "w", encoding="utf-8") as out:
        out.write("#core\n" + "\n".join(core) + "\n#extra\n" + "\n".join(extra) + "\n")
    print(f"{args.lang}: {len(core)} core, {len(extra)} extra -> {args.out}", file=sys.stderr)


if __name__ == "__main__":
    main()
