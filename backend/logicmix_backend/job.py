"""Точка входа Cloud Run Job. Конфигурация — только переменные окружения (секретов в коде нет).

LM_CHANNEL      staging|production (по умолчанию staging)
LM_BUCKET       имя бакета Cloud Storage (обязательно)
LM_LANGS        языки через запятую, например "en,ru"
LM_SOURCES_DIR  каталог (смонтированный/скачанный) с выгрузками: <source>/<lang>.jsonl[.gz]
LM_DB           путь к SQLite (в облаке — временный; durable-состояние хранится в Storage, см. docs)
LM_DATA_VERSION целое число версии данных
LM_APPROVE      "1" — только если владелец включил автопубликацию

Job ничего не публикует в production, пока источники не approved в реестре (проверка в Publisher).
"""
from __future__ import annotations

import datetime as dt
import logging
import os
import sys
from pathlib import Path

from .adapters.sources import ADAPTERS, open_text
from .packs import build_pack, PackError
from .pipeline import import_source, merge_language
from .publisher import Publisher, GcsObjectStore
from .registry import SourceRegistry
from .store import Store

log = logging.getLogger("logicmix.job")


def run(env=os.environ) -> int:
    logging.basicConfig(level=logging.INFO)
    channel = env.get("LM_CHANNEL", "staging")
    bucket = env["LM_BUCKET"]
    langs = [l for l in env.get("LM_LANGS", "en,ru").split(",") if l]
    src_dir = Path(env.get("LM_SOURCES_DIR", "/data/sources"))
    store = Store(env.get("LM_DB", "/tmp/logicmix.sqlite"))
    reg = SourceRegistry()
    for lang in langs:
        for src in reg.all():
            for ext in (".jsonl.gz", ".jsonl", ".tsv", ".dic"):
                f = src_dir / src.id / f"{lang}{ext}"
                if f.exists() and src.id in ADAPTERS:
                    res = import_source(store, reg, ADAPTERS[src.id], src.id, lang, lambda f=f: open_text(str(f)))
                    log.info("%s/%s: %s", src.id, lang, res["status"])
    now = dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    packs, failures = [], 0
    for lang in langs:
        entries = merge_language(store, reg, lang)
        for t in ("wordsearch", "crossword"):
            try:
                packs.append(build_pack(t, lang, entries, int(env["LM_DATA_VERSION"]), now, reg))
            except PackError as e:
                failures += 1
                log.error("skip %s/%s: %s", t, lang, e)
    if packs:
        Publisher(GcsObjectStore(bucket), reg).publish(channel, packs, now, approved=env.get("LM_APPROVE") == "1")
    return 1 if failures and not packs else 0


if __name__ == "__main__":
    sys.exit(run())
