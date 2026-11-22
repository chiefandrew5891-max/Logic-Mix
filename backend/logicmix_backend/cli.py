"""CLI: python -m logicmix_backend.cli <команда>. Локально работает без Firebase и учётных данных."""
from __future__ import annotations

import argparse
import datetime as dt
import json
import sys
from pathlib import Path

from . import languages
from .adapters.sources import ADAPTERS, open_text
from .normalize import Policy
from .packs import build_pack
from .pipeline import import_source, merge_language
from .publisher import Publisher, LocalObjectStore, GcsObjectStore
from .registry import SourceRegistry
from .store import Store


def _policy(path: str | None) -> Policy:
    if not path:
        return Policy()
    words = {w.strip().casefold() for w in Path(path).read_text("utf-8").splitlines() if w.strip() and not w.startswith("#")}
    return Policy(blocklist=frozenset(words))


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(prog="logicmix-backend")
    ap.add_argument("--db", default="backend/work/logicmix.sqlite")
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("sources", help="показать реестр источников и статус лицензий")
    i = sub.add_parser("import", help="импортировать локальный файл выгрузки")
    i.add_argument("--source", required=True)
    i.add_argument("--lang", required=True)
    i.add_argument("--file", required=True)
    i.add_argument("--blocklist")
    b = sub.add_parser("build", help="собрать пакеты и опубликовать в локальное/GCS-хранилище")
    b.add_argument("--lang", required=True)
    b.add_argument("--types", default="wordsearch,crossword")
    b.add_argument("--channel", default="staging", choices=["staging", "production"])
    b.add_argument("--out", default="backend/work/storage")
    b.add_argument("--gcs-bucket", help="если указан — публикация в Cloud Storage (нужны учётные данные окружения)")
    b.add_argument("--data-version", type=int, required=True)
    b.add_argument("--approve", action="store_true", help="подтверждение публикации (approved)")
    r = sub.add_parser("rollback")
    r.add_argument("--channel", required=True, choices=["staging", "production"])
    r.add_argument("--out", default="backend/work/storage")
    a = ap.parse_args(argv)
    reg = SourceRegistry()

    if a.cmd == "sources":
        for s in reg.all():
            print(f"{s.id:12} {s.license_status:13} commercial={s.commercial_use} checked={s.license_checked_on}  {s.name}")
        return 0
    if a.cmd == "import":
        Path(a.db).parent.mkdir(parents=True, exist_ok=True)
        res = import_source(Store(a.db), reg, ADAPTERS[a.source], a.source, a.lang,
                            lambda: open_text(a.file), _policy(a.blocklist))
        print(json.dumps(res, ensure_ascii=False))
        return 0 if res["status"] != "failed" else 1
    if a.cmd == "build":
        store = Store(a.db)
        entries = merge_language(store, reg, a.lang)
        now = dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        packs = [build_pack(t, a.lang, entries, a.data_version, now, reg) for t in a.types.split(",")]
        for p in packs:
            print(f"{p.pack_id}: raw={len(p.raw)}B gz={len(p.gz)}B sha256={p.sha256}")
        obj = GcsObjectStore(a.gcs_bucket) if a.gcs_bucket else LocalObjectStore(a.out)
        m = Publisher(obj, reg).publish(a.channel, packs, now, approved=a.approve)
        print(f"published {a.channel} manifest seq={m['seq']}")
        return 0
    if a.cmd == "rollback":
        m = Publisher(LocalObjectStore(a.out), reg).rollback(a.channel)
        print(f"rolled back, manifest seq={m['seq']}")
        return 0
    return 2


if __name__ == "__main__":
    sys.exit(main())
