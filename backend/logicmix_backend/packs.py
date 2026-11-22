"""Сборка пакетов: детерминированный JSON + gzip, SHA-256, валидация."""
from __future__ import annotations

import gzip
import hashlib
import json
import re
from dataclasses import dataclass, field

from . import languages
from .models import Entry
from .registry import SourceRegistry

SCHEMA_VERSION = 1
SUPPORTED_SCHEMAS = {1}
PACK_TYPES = {"wordsearch", "crossword", "wordlogic"}
MAX_PACK_BYTES = 8 * 1024 * 1024
MAX_CROSSWORD_WORDS = 10        # потолок слов в одном кроссворде (для генератора клиента; записывается в пакет)
_ID = re.compile(r"^(ws|cw|wl)-[a-zA-Z-]+$")


class PackError(Exception):
    pass


@dataclass
class BuiltPack:
    pack_id: str
    ptype: str
    language: str
    data_version: int
    gz: bytes
    raw: bytes
    sources: list[str]
    attribution: list[dict] = field(default_factory=list)
    min_client: int = 1

    @property
    def sha256(self) -> str:
        return hashlib.sha256(self.gz).hexdigest()

    @property
    def raw_sha256(self) -> str:
        return hashlib.sha256(self.raw).hexdigest()


def _tier(freq):
    if freq is None:
        return 2
    return 1 if freq >= 5 else 2 if freq >= 4 else 3


def _rows(ptype: str, entries: list[Entry]) -> list:
    if ptype == "wordsearch":
        return [[e.game, _tier(e.freq)] for e in entries if e.suitability.get("wordsearch")]
    if ptype == "crossword":
        # одно, самое короткое определение; клиент обязан соблюдать потолок слов в сетке
        return [[e.game, min(e.definitions, key=len), _tier(e.freq)] for e in entries if e.suitability.get("crossword")]
    if ptype == "wordlogic":
        return [[e.game, sorted(e.categories), sorted(f"{t}:{x}" for t, x in e.relations)]
                for e in entries if e.suitability.get("wordlogic")]
    raise PackError(f"unknown pack type {ptype}")


def build_pack(ptype: str, lang: str, entries: list[Entry], data_version: int, published_at: str,
               registry: SourceRegistry, min_client: int = 1) -> BuiltPack:
    languages.get(lang)
    rows = _rows(ptype, entries)
    if not rows:
        raise PackError(f"no suitable entries for {ptype}/{lang}")      # пустой пакет не публикуется
    used = sorted({s for e in entries for s in e.sources})
    attribution = [{"source": s, "name": registry.get(s).name, "url": registry.get(s).url,
                    "license": registry.get(s).license_claimed,
                    "licenseStatus": registry.get(s).license_status} for s in used]
    pid = f"{ptype}-{lang}"
    doc = {"schema": SCHEMA_VERSION, "packId": pid, "type": ptype, "language": lang, "dataVersion": data_version,
           "publishedAt": published_at, "minClientVersion": min_client, "sources": used, "attribution": attribution,
           "limits": {"crosswordMaxWords": MAX_CROSSWORD_WORDS} if ptype == "crossword" else {}, "rows": rows}
    raw = json.dumps(doc, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    gz = gzip.compress(raw, 9, mtime=0)      # mtime=0 → воспроизводимая сборка
    if len(gz) > MAX_PACK_BYTES:
        raise PackError("pack too large")
    return BuiltPack(pid, ptype, lang, data_version, gz, raw, used, attribution, min_client)


def validate_pack_bytes(gz: bytes, expected_sha256: str | None = None, client_version: int | None = None) -> dict:
    """Проверка, которую выполняют и backend перед публикацией, и (в будущем) клиент перед установкой."""
    if expected_sha256 and hashlib.sha256(gz).hexdigest() != expected_sha256:
        raise PackError("checksum mismatch")
    if len(gz) > MAX_PACK_BYTES:
        raise PackError("pack too large")
    try:
        doc = json.loads(gzip.decompress(gz).decode("utf-8"))
    except Exception as e:
        raise PackError(f"corrupted pack: {e}") from e
    for k in ("schema", "packId", "type", "language", "dataVersion", "publishedAt", "sources", "attribution", "rows"):
        if k not in doc:
            raise PackError(f"missing field {k}")
    if doc["schema"] not in SUPPORTED_SCHEMAS:
        raise PackError(f"unsupported schema {doc['schema']}")
    if doc["type"] not in PACK_TYPES:
        raise PackError("unknown type")
    if client_version is not None and doc.get("minClientVersion", 1) > client_version:
        raise PackError("client too old for pack")
    try:
        cfg = languages.get(doc["language"])
    except KeyError as e:
        raise PackError(str(e)) from e
    if not doc["rows"]:
        raise PackError("empty pack")
    if not doc["attribution"]:
        raise PackError("attribution missing")
    seen = set()
    rx = cfg.letter_re()
    for row in doc["rows"]:
        w = row[0]
        if w in seen:
            raise PackError(f"duplicate {w}")
        seen.add(w)
        if not rx.match(w.casefold()):
            raise PackError(f"word outside alphabet of {cfg.code}: {w}")
        if doc["type"] == "crossword" and (len(row) < 3 or not str(row[1]).strip()):
            raise PackError(f"crossword entry without clue: {w}")
    return doc
