"""Эталонная реализация клиентского протокола синхронизации (Python-референс для будущего Kotlin-кода и тестов контракта):
манифест → сравнение версий → скачивание только изменившихся пакетов → SHA-256/схема → атомарная установка → откат при сбое."""
from __future__ import annotations

import json
import os
from pathlib import Path
from typing import Callable

from .packs import validate_pack_bytes, PackError


class ClientCache:
    def __init__(self, root: str | Path, language: str, client_version: int = 1):
        self.root, self.language, self.client_version = Path(root), language, client_version
        self.root.mkdir(parents=True, exist_ok=True)

    def installed(self) -> dict:
        p = self.root / "installed.json"
        return json.loads(p.read_text("utf-8")) if p.exists() else {}

    def _save(self, d: dict) -> None:
        tmp = self.root / "installed.json.tmp"
        tmp.write_text(json.dumps(d, sort_keys=True), "utf-8")
        os.replace(tmp, self.root / "installed.json")

    def sync(self, get: Callable[[str], bytes | None], channel: str) -> dict:
        """get(relative_key) -> bytes|None. Возвращает отчёт; исключения сети не прерывают работу с кэшем."""
        report = {"installed": [], "skipped": [], "failed": []}
        try:
            raw = get(f"public/{channel}/v1/manifest.json")
            manifest = json.loads(raw) if raw else None
        except Exception as e:
            report["failed"].append(("manifest", str(e)))
            return report
        if not manifest:
            report["failed"].append(("manifest", "empty"))
            return report
        state = self.installed()
        for p in manifest.get("packs", []):
            if p["language"] != self.language:      # только нужный язык; подмены другим языком нет
                continue
            cur = state.get(p["packId"])
            if cur and cur["dataVersion"] >= p["dataVersion"] and cur["sha256"] == p["sha256"]:
                report["skipped"].append(p["packId"])
                continue
            try:
                data = get(f"public/{channel}/v1/{p['path']}")
                if data is None:
                    raise PackError("download failed")
                validate_pack_bytes(data, p["sha256"], self.client_version)
                target = self.root / "packs" / p["packId"]
                target.mkdir(parents=True, exist_ok=True)
                f = target / f"{p['dataVersion']}.json.gz"
                tmp = f.with_name(f.name + ".tmp")
                tmp.write_bytes(data)
                os.replace(tmp, f)                       # файл установлен целиком или не установлен
                state[p["packId"]] = {"dataVersion": p["dataVersion"], "sha256": p["sha256"], "file": str(f.relative_to(self.root))}
                self._save(state)                         # указатель переключается последним
                report["installed"].append(p["packId"])
            except Exception as e:                        # прежняя версия остаётся рабочей
                report["failed"].append((p["packId"], str(e)))
        return report
