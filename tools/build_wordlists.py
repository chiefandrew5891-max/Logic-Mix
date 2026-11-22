#!/usr/bin/env python3
"""Builds the Cyrillic word-search word lists (offline tool, not part of the app build).

Inputs (install once): hunspell-ru, hunspell-uk (LibreOffice dictionaries), `pip install wordfreq pymorphy3
pymorphy3-dicts-ru pymorphy3-dicts-uk`. pymorphy3 and wordfreq are only used to *choose* words and are not shipped.

Outputs in app/src/main/resources/wordsearch/:
  <tag>.txt        "#core" = nouns for the generator (rewritten here), "#extra" = kept as is
  <tag>.words.gz   bonus-word dictionary: every spelling form accepted as a bonus word (see PackedWords in the app)

Usage: python3 tools/build_wordlists.py ru uk
Licenses: ru_RU dictionary (c) 1997-2008 Alexander I. Lebedev, BSD-style, modified versions must be marked (see
app/src/main/resources/wordsearch/LICENSE-wordlists.txt).
"""
import gzip, re, subprocess, sys
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "app/src/main/resources/wordsearch"
LANG = {
    "ru": dict(dic="ru_RU", letters="АБВГДЕЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ", norm={"Ё": "Е"}, morph="ru"),
    "uk": dict(dic="uk_UA", letters="АБВГҐДЕЄЖЗИІЇЙКЛМНОПРСТУФХЦЧШЩЬЮЯ", norm={}, morph="uk"),
}
MIN_LEN, MAX_BONUS_LEN, MAX_CORE_LEN = 3, 14, 9
CORE_MIN_ZIPF = 3.4
BLOCK_TAGS = {"Name", "Surn", "Patr", "Geox", "Orgn", "Abbr", "Slng", "Arch", "Infr", "Erro", "Trad", "Dist", "Poss"}
# Words that do not suit a calm puzzle for all ages.
BLOCK_WORDS = set("""СЕКС ПОРНО СМЕРТЬ УБИЙСТВО ТРУП ВОЙНА НАРКОТИК ТЕРРОР НАСИЛИЕ ПОХОРОНЫ САМОУБИЙСТВО КРОВЬ БОМБА ВЗРЫВ ОРУЖИЕ
ПИСТОЛЕТ ВИНТОВКА ГРОБ КЛАДБИЩЕ ТЮРЬМА ВОР ЖОПА ЗАДНИЦА ДЕРЬМО ГОВНО БЛЯДЬ СУКА ХЕР ШЛЮХА ПЬЯНКА ВОДКА ПИВО АЛКОГОЛЬ СИГАРЕТА
СМЕРТЬ УБИВСТВО ВІЙНА НАРКОТИК ТЕРОР НАСИЛЛЯ ПОХОРОН САМОГУБСТВО КРОВ ВИБУХ ЗБРОЯ ПІСТОЛЕТ ГВИНТІВКА ТРУНА ЦВИНТАР""".split())


def norm(word, cfg):
    word = word.upper()
    for a, b in cfg["norm"].items():
        word = word.replace(a, b)
    return word


def forms(cfg, tag):
    d = Path("/usr/share/hunspell")
    text = subprocess.run(["unmunch", str(d / f"{cfg['dic']}.dic"), str(d / f"{cfg['dic']}.aff")], capture_output=True, check=True).stdout.decode()
    letters = set(cfg["letters"])
    out = set()
    for raw in text.splitlines():
        w = raw.strip()
        # Lower case only: names, abbreviations and proper nouns are capitalised in the dictionary.
        if not w or w != w.lower():
            continue
        u = norm(w, cfg)
        if MIN_LEN <= len(u) <= MAX_BONUS_LEN and all(c in letters for c in u):
            out.add(u)
    return out


def core_nouns(cfg, valid):
    import pymorphy3
    from wordfreq import top_n_list, zipf_frequency
    morph = pymorphy3.MorphAnalyzer(lang=cfg["morph"])
    tag = cfg["morph"]
    result = set()
    for w in top_n_list(tag, 120000):
        if not (MIN_LEN <= len(w) <= MAX_CORE_LEN) or zipf_frequency(w, tag) < CORE_MIN_ZIPF:
            continue
        u = norm(w, cfg)
        if u not in valid or u in BLOCK_WORDS or not all(c in cfg["letters"] for c in u):
            continue
        for p in morph.parse(w):
            t = p.tag
            if t.POS == "NOUN" and p.normal_form == w and "nomn" in t and "plur" not in t and not (BLOCK_TAGS & set(t.grammemes)):
                result.add(u)
                break
    return result


def write_bonus(tag, cfg, words):
    letters = cfg["letters"]
    code = {c: chr(0x30 + i) for i, c in enumerate(letters)}
    body = "\n".join("".join(code[c] for c in w) for w in sorted(words, key=lambda w: "".join(code[c] for c in w)))
    with gzip.GzipFile(OUT / f"{tag}.words.gz", "wb", compresslevel=9, mtime=0) as f:
        f.write(letters.encode("utf-8") + b"\n" + body.encode("latin-1"))


def rewrite_core(tag, core):
    path = OUT / f"{tag}.txt"
    old = path.read_text(encoding="utf-8").split("#extra")
    keep = [l.strip() for l in old[0].splitlines() if l.strip() and l.strip() != "#core"]
    merged = sorted(set(keep) | core, key=lambda w: (len(w), w))
    extra = ("#extra" + old[1]) if len(old) > 1 else "#extra\n"
    path.write_text("#core\n" + "\n".join(merged) + "\n" + extra.rstrip("\n") + "\n", encoding="utf-8")
    return len(merged)


if __name__ == "__main__":
    for tag in sys.argv[1:]:
        cfg = LANG[tag]
        valid = forms(cfg, tag)
        core = core_nouns(cfg, valid)
        if tag == "ru":  # the Ukrainian bonus list (2M forms, 5 MB) is not shipped yet
            write_bonus(tag, cfg, valid)
        print(tag, "bonus forms:", len(valid), "core nouns:", rewrite_core(tag, core))
