# Backend Logic Mix (Python, stdlib)

Не смешивается с Android-кодом. Локально не требует Firebase, сети и учётных данных.

```bash
cd backend
python3 -m unittest discover -s tests -t .                       # тесты
python3 -m logicmix_backend.cli sources                          # реестр источников и статус лицензий
python3 -m logicmix_backend.cli --db work/lm.sqlite import --source fixture --lang ru --file tests/fixtures/ru_fixture.jsonl
python3 -m logicmix_backend.cli --db work/lm.sqlite build --lang ru --data-version 1 --out work/storage --approve
python3 -m logicmix_backend.cli rollback --channel staging --out work/storage
```

## Модули
`languages` (конфиг языков, 8 включая zh-CN) · `registry` (источники и лицензии) · `adapters/sources` (Kaikki JSONL, Hunspell, TSV частот, Wikidata, fixtures) ·
`fetch` (timeout/retry/backoff/rate limit/лимит размера) · `normalize` (NFC, алфавит, блок-лист, пригодность по играм) · `store` (SQLite) ·
`pipeline` (импорт с изоляцией сбоев, слияние) · `packs` (gzip JSON + SHA-256 + валидация) · `publisher` (файлы → манифест, rollback, staging/production) ·
`client_reference` (эталон протокола синхронизации для Android) · `job` (вход Cloud Run Job) · `deploy/` (шаблоны, не применены).

## Правила
- Источник попадает в production только при `license_status=approved` и дате проверки. Сейчас approved только fixtures.
- Словарные источники создают слова; частотность и Wikidata лишь дополняют существующие.
- Пакеты не удаляются, откат — новый манифест на прежние пакеты.
- `private/**` в бакете (история манифестов) закрыт правилами; `public/**` — чтение для всех.
- Формат ID заданий — см. `docs/LOGIC_MIX_FIREBASE_BACKEND_ARCHITECTURE.md` §1f.
