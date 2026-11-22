"""SQLite-хранилище обработки (разработка/тесты; в облаке — тот же интерфейс над постоянным хранилищем,
т.к. файловая система Cloud Run Job временная)."""
from __future__ import annotations

import json
import sqlite3
import time
from typing import Iterable

from .models import Entry

SCHEMA = """
CREATE TABLE IF NOT EXISTS runs(
  id INTEGER PRIMARY KEY, source_id TEXT NOT NULL, lang TEXT NOT NULL, source_version TEXT,
  started REAL NOT NULL, status TEXT NOT NULL, accepted INTEGER DEFAULT 0, rejected INTEGER DEFAULT 0,
  checksum TEXT, error TEXT);
CREATE TABLE IF NOT EXISTS records(
  run_id INTEGER NOT NULL REFERENCES runs(id), lang TEXT NOT NULL, lemma TEXT NOT NULL, original TEXT NOT NULL,
  game TEXT NOT NULL, pos TEXT, definitions TEXT NOT NULL, forms TEXT NOT NULL, freq REAL,
  categories TEXT NOT NULL, relations TEXT NOT NULL, PRIMARY KEY(run_id, lemma, pos));
CREATE TABLE IF NOT EXISTS rejects(run_id INTEGER NOT NULL, reason TEXT NOT NULL, n INTEGER NOT NULL,
  PRIMARY KEY(run_id, reason));
CREATE TABLE IF NOT EXISTS packs(
  pack_id TEXT NOT NULL, data_version INTEGER NOT NULL, status TEXT NOT NULL, sha256 TEXT, size INTEGER,
  channel TEXT, created REAL NOT NULL, PRIMARY KEY(pack_id, data_version, channel));
"""


class Store:
    def __init__(self, path: str = ":memory:"):
        self.db = sqlite3.connect(path)
        self.db.executescript(SCHEMA)

    def start_run(self, source_id: str, lang: str, source_version: str | None = None) -> int:
        cur = self.db.execute("INSERT INTO runs(source_id,lang,source_version,started,status) VALUES(?,?,?,?, 'running')",
                              (source_id, lang, source_version, time.time()))
        self.db.commit()
        return cur.lastrowid

    def finish_run(self, run_id: int, status: str, accepted: int, rejects: dict, checksum: str | None = None,
                   error: str | None = None) -> None:
        self.db.execute("UPDATE runs SET status=?,accepted=?,rejected=?,checksum=?,error=? WHERE id=?",
                        (status, accepted, sum(rejects.values()), checksum, error, run_id))
        self.db.executemany("INSERT OR REPLACE INTO rejects VALUES(?,?,?)", [(run_id, k, v) for k, v in rejects.items()])
        self.db.commit()

    def add_entries(self, run_id: int, entries: Iterable[Entry]) -> None:
        self.db.executemany(
            "INSERT OR REPLACE INTO records VALUES(?,?,?,?,?,?,?,?,?,?,?)",
            [(run_id, e.lang, e.lemma, e.original, e.game, e.pos, json.dumps(e.definitions, ensure_ascii=False),
              json.dumps(e.forms, ensure_ascii=False), e.freq, json.dumps(e.categories, ensure_ascii=False),
              json.dumps(e.relations, ensure_ascii=False)) for e in entries])
        self.db.commit()

    def purge_run(self, run_id: int) -> None:
        self.db.execute("DELETE FROM records WHERE run_id=?", (run_id,))
        self.db.commit()

    def last_ok_run(self, source_id: str, lang: str) -> int | None:
        r = self.db.execute("SELECT id FROM runs WHERE source_id=? AND lang=? AND status='ok' ORDER BY id DESC LIMIT 1",
                            (source_id, lang)).fetchone()
        return r[0] if r else None

    def last_checksum(self, source_id: str, lang: str) -> str | None:
        r = self.db.execute("SELECT checksum FROM runs WHERE source_id=? AND lang=? AND status='ok' ORDER BY id DESC LIMIT 1",
                            (source_id, lang)).fetchone()
        return r[0] if r else None

    def records(self, run_id: int):
        for row in self.db.execute("SELECT lang,lemma,original,game,pos,definitions,forms,freq,categories,relations "
                                   "FROM records WHERE run_id=?", (run_id,)):
            yield row

    def run_info(self, run_id: int):
        return self.db.execute("SELECT source_id,lang,status,accepted,rejected,error FROM runs WHERE id=?", (run_id,)).fetchone()

    def reject_counts(self, run_id: int) -> dict:
        return dict(self.db.execute("SELECT reason,n FROM rejects WHERE run_id=?", (run_id,)).fetchall())
