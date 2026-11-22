"""Публикация: файлы пакетов сначала, манифест последним (атомарно). Откат = возврат манифеста.
Пакеты никогда не удаляются (на них могут ссылаться незавершённые игры на устройствах)."""
from __future__ import annotations

import json
import os
from pathlib import Path
from typing import Protocol

from .models import Status, advance
from .packs import BuiltPack, validate_pack_bytes, PackError, SCHEMA_VERSION
from .registry import SourceRegistry

CHANNELS = ("staging", "production")
MANIFEST_SCHEMA = 1


class PublishError(Exception):
    pass


class ObjectStore(Protocol):
    def put(self, key: str, data: bytes, cache_control: str) -> None: ...
    def get(self, key: str) -> bytes | None: ...


class LocalObjectStore:
    """Файловая имитация Cloud Storage; запись атомарна (tmp + replace)."""

    def __init__(self, root: str | Path):
        self.root = Path(root)

    def put(self, key, data, cache_control="no-cache"):
        p = self.root / key
        p.parent.mkdir(parents=True, exist_ok=True)
        tmp = p.with_name(p.name + ".tmp")
        tmp.write_bytes(data)
        os.replace(tmp, p)

    def get(self, key):
        p = self.root / key
        return p.read_bytes() if p.exists() else None


class GcsObjectStore:
    """Cloud Storage. Импорт google-cloud-storage — лениво; в тестах и локально не требуется.
    Учётные данные берутся из окружения Cloud Run (service account), не из репозитория."""

    def __init__(self, bucket: str):
        from google.cloud import storage   # type: ignore
        self._b = storage.Client().bucket(bucket)

    def put(self, key, data, cache_control="no-cache"):
        blob = self._b.blob(key)
        blob.cache_control = cache_control
        blob.upload_from_string(data, content_type="application/octet-stream" if key.endswith(".gz") else "application/json")

    def get(self, key):
        blob = self._b.blob(key)
        return blob.download_as_bytes() if blob.exists() else None


def pack_key(channel: str, p: BuiltPack) -> str:
    return f"public/{channel}/v1/packs/{p.pack_id}/{p.data_version}-{p.sha256[:12]}.json.gz"


class Publisher:
    def __init__(self, store: ObjectStore, registry: SourceRegistry):
        self.store, self.registry = store, registry

    def _manifest_key(self, channel): return f"public/{channel}/v1/manifest.json"
    def _history_key(self, channel, seq): return f"private/{channel}/manifest-history/{seq:06d}.json"

    def current(self, channel: str) -> dict | None:
        raw = self.store.get(self._manifest_key(channel))
        return json.loads(raw) if raw else None

    def publish(self, channel: str, packs: list[BuiltPack], generated_at: str,
                approved: bool = False) -> dict:
        if channel not in CHANNELS:
            raise PublishError(f"unknown channel {channel}")
        # статусный путь; approved=True означает подтверждение владельцем/автоконвейером
        st = advance(advance(advance(Status.DRAFT, Status.GENERATED), Status.VALIDATED), Status.APPROVED) if approved else Status.VALIDATED
        if st != Status.APPROVED:
            raise PublishError("pack is not approved")
        prev = self.current(channel) or {"schema": MANIFEST_SCHEMA, "channel": channel, "seq": 0, "packs": []}
        entries = {p["packId"]: p for p in prev["packs"]}
        for p in packs:
            blockers = self.registry.production_blockers(p.sources) if channel == "production" else []
            if blockers:
                raise PublishError(f"license not approved for production: {', '.join(blockers)}")
            try:
                validate_pack_bytes(p.gz, p.sha256)
            except PackError as e:
                raise PublishError(f"invalid pack {p.pack_id}: {e}") from e
            old = entries.get(p.pack_id)
            if old and old["dataVersion"] >= p.data_version:
                raise PublishError(f"{p.pack_id}: dataVersion must increase ({old['dataVersion']} -> {p.data_version})")
        # 1) файлы пакетов
        for p in packs:
            self.store.put(pack_key(channel, p), p.gz, "public, max-age=31536000, immutable")
            entries[p.pack_id] = {
                "packId": p.pack_id, "type": p.ptype, "language": p.language, "dataVersion": p.data_version,
                "schemaVersion": SCHEMA_VERSION, "path": pack_key(channel, p).split(f"public/{channel}/v1/")[1],
                "size": len(p.gz), "rawSize": len(p.raw), "sha256": p.sha256, "rawSha256": p.raw_sha256,
                "minClientVersion": p.min_client, "status": Status.PUBLISHED.value, "sources": p.sources}
        manifest = {"schema": MANIFEST_SCHEMA, "channel": channel, "seq": prev["seq"] + 1,
                    "generatedAt": generated_at, "packs": [entries[k] for k in sorted(entries)]}
        data = json.dumps(manifest, ensure_ascii=False, sort_keys=True, indent=1).encode("utf-8")
        # 2) история, затем 3) манифест последним
        self.store.put(self._history_key(channel, manifest["seq"]), data, "no-store")
        self.store.put(self._manifest_key(channel), data, "no-cache")
        return manifest

    def rollback(self, channel: str) -> dict:
        cur = self.current(channel)
        if not cur or cur["seq"] < 2:
            raise PublishError("nothing to roll back to")
        base = cur.get("rolledBackFrom", cur["seq"])   # откат отката не возвращает на плохую версию
        if base < 2:
            raise PublishError("nothing to roll back to")
        prev_raw = self.store.get(self._history_key(channel, base - 1))
        if prev_raw is None:
            raise PublishError("history missing")
        prev = json.loads(prev_raw)
        # новый seq, чтобы клиенты с условными запросами увидели изменение; содержимое — прежнее
        prev["seq"] = cur["seq"] + 1
        prev["rolledBackFrom"] = cur["seq"]
        data = json.dumps(prev, ensure_ascii=False, sort_keys=True, indent=1).encode("utf-8")
        self.store.put(self._history_key(channel, prev["seq"]), data, "no-store")
        self.store.put(self._manifest_key(channel), data, "no-cache")
        return prev
