# Logic Mix — Firebase Backend: архитектура (Этап 1)

Статус: **проект на утверждение**. Код backend не пишется, Firebase не развёртывается, Android-репозиторий не меняется.
Источник фактов: `BACKEND_INTEGRATION_HANDOFF.md` (аудит ветки `feature/stage-b`, сверен с кодом).
Метки: ✅ существует в клиенте · 🟡 частично · 💡 предложение · 🆕 новый компонент · ❓ требует решения.
Точные тарифы и квоты Firebase здесь **не приводятся** — их нужно сверить в официальной документации (раздел 18).

---

## 1. Executive Summary

- Клиент сейчас полностью автономен: нет сети, нет `INTERNET`, нет JSON/сериализации, нет WorkManager. Задания — зашитый Kotlin, txt-словари и детерминированные генераторы.
- Рекомендуемая модель — **«контент-пакеты + манифест»**: сервер раз в период собирает проверенные задания в компактные JSON-пакеты (gzip) в Cloud Storage, клиент скачивает манифест и только изменившиеся пакеты. Firestore — каталог/аудит, а **не** то, что читает каждый клиент.
- Генерация и тяжёлая валидация — **Python на Cloud Run Jobs** (запуск Cloud Scheduler). Cloud Functions (TypeScript, 2nd gen) — тонкий API и триггеры. Публичный API клиента — по возможности просто статические файлы (CDN-кэшируемые) из Storage, без вызова функций.
- Категории делятся на три класса:
  1. **Каталог нужен**: числовые последовательности, логика слов, мини-кроссворды, визуальная логика, филворды (языковые и «рукописные» задания).
  2. **Каталог желателен, но малый**: спички (60 уровней сейчас генерируются клиентом).
  3. **Каталог не нужен**: Sudoku, Orb Lines, Two Dots — локальные детерминированные генераторы; максимум — серверные конфиги сложности через Remote Config.
- Главные риски: ключ прогресса по индексу уровня в 3 категориях; два разных «пространства» ID; языковое качество; отсутствие в клиенте любой сетевой инфраструктуры; пересчёт отдельных скрытых зависимостей (подсказки, XP).
- Минимальный старт: Firestore + Storage + Scheduler + один Cloud Run Job + Remote Config. Plan Blaze, скорее всего, понадобится (Cloud Run/Scheduler/Secret Manager/Functions 2nd gen) — проверить в документации; поставить бюджетные алерты.

---

## 2. Анализ существующей Android-архитектуры (по аудиту)

| Категория | Источник | Язык | ID | Хранение прогресса | Класс |
|---|---|---|---|---|---|
| Matchstick | генератор 60 уровней (`MatchPuzzles.build`, seed от индекса) | нет | `matchstick-N` | Room `solved_puzzles`, `firstUnsolved` | ✅ ID-ориентированный |
| Number Sequence | Kotlin-пак 13 задач | нет в модели (строки готовые) | `ns-NNN` | Room, `firstUnsolved` | ✅ ID-ориентированный |
| Word Logic | пак RU/EN по 10 | да | `wl-<lang>-NN` | DataStore индекс `word_logic.level` | 🟡 индекс |
| Mini Crossword | 7 языков × 5 сеток | да | `cw-<lang>-NN` | DataStore индекс `mini_crossword_<tag>.level` | 🟡 индекс |
| Visual Logic | пак из функций-правил | нет | `vl-NN` | DataStore индекс | 🟡 индекс |
| Word Search | генератор от txt-словарей, 12 ур./язык | да | `ws-<lang>-NN` | `WordSearchStore` per-language | ✅ ID+индекс |
| Sudoku | генератор (difficulty, seed) | нет | `sudoku-<diff>-<seed>` | `SudokuStore` одна партия | ✅ |
| Orb Lines | генератор от seed | нет | `orb-<seed>` | `OrbStore` | ✅ |
| Two Dots | генератор от seed | нет | `twodots-<mode>-<id>` | `TwoDotsStore` | ✅ |

Режимы: `GameMode {CAREER, ENDLESS, DAILY}` существует, играется **только CAREER**; XP начисляется только за первое верное решение в CAREER (`ProgressRepository.recordAttempt`, идемпотентность через PK `solved_puzzles(profileId, puzzleId)` и `xp_transactions.id = "$profileId:$puzzleId:FIRST_SOLVE"`).

Модели (✅): `NumberSequencePuzzle(id, difficulty, prompt, sequence: List<Int>, answer: Int, explanation, hints)`; `MatchPuzzle(index, start, maxMoves)` (маска 23 бита); `WordLogicPuzzle`; `CrosswordPuzzle/CrosswordEntry`; `VisualPuzzle/Tile/VisualOption`; `WordSearchLevel/Placement/Cell`; `SudokuPuzzle`; `OrbGame`; `TwoDotsGame`.

---

## 3. Проверка полноты и противоречий аудита

1. **Языки.** Запрос: 7 языков (en, uk, ru, it, es, de, fr). В клиенте `ContentLanguage` содержит **8**: добавлен `ZH_CN("zh-CN")`; для zh-CN скрыты Word Search и Mini Crossword (`CategoryAvailability`). ❓ Решение: сервер хранит `zh-CN` для нейтральных категорий, словесные — 7 языков.
2. **Реальное покрытие языков:** Word Logic — только RU и EN (остальные откатываются в EN, `isFallback`). Это нарушает требование «не EN при UK-интерфейсе» — уже существующий fallback, надо решить (раздел 12).
3. **Дублируются понятия «уровень» и «ID»:** три категории выбирают по индексу списка; две — по `solved_puzzles`. Единой модели нет.
4. **Аудит не фиксирует (нужны Kotlin-файлы):** точный формат `NumberSequencePack` (нужен для расчёта «обоснования» правил); таблица палитры цветов `Tile.color`; текст подсказок Visual Logic (по `rule`, не в задании); как `CategoryGames.store.levelIndex` ведёт себя при `index >= size` (есть: `allDone`).
5. **JSON-примеры в аудите** — предлагаемые, а не реальные. В примере спичек `startMask: 0` — заглушка; в архитектуре используем реальную 23-битную маску.
6. **Спички:** в модели нет `difficulty`; сложность = `maxMoves` (1 или 2). Типы задач кроме `A ± B = C` в клиенте **не существуют** → новых типов не вводим (требование «не вводить новые механики»).
7. **«Допустимые перемещения» (запрос 3.1)** клиент не хранит — выводятся движком. Хранить в пакете не нужно.
8. **Daily/Endless не реализованы** — серверный Daily пока только 💡.
9. **Нет версионирования состояния** (`encode()`-строки без версии, кроме `td1`) — меняя формат задания, нельзя ломать сохранённые состояния.
10. **Подсказки** Number Sequence и Word Logic лежат внутри задания, у остальных — вычисляются движком.

---

## 4. Выбор сервисов Firebase / GCP

| Сервис | Роль | Решение |
|---|---|---|
| Cloud Storage | пакеты, манифесты, словари, партии генерации | ✅ основа доставки |
| Firestore | каталог заданий, статусы, аудит, fingerprint-индекс, джобы | ✅ только серверная сторона; клиент не читает |
| Cloud Functions 2nd gen (TypeScript) | админ-API (callable), триггеры Storage/Firestore, публикация манифеста | ✅ тонкий слой |
| Cloud Run Jobs (Python) | генераторы, валидаторы, импортёры, дедупликация | ✅ |
| Cloud Scheduler | расписание запусков job | ✅ |
| Remote Config | флаги: включить пакет, интервал проверки, сложность Orb/TwoDots | ✅ малые параметры |
| App Check (Play Integrity) | защита callable и, при необходимости, прямых запросов | ✅ |
| Authentication | не вводим; админам — IAM/custom claims | ⏸ отдельный этап для sync прогресса |
| Secret Manager | ключи внешних API | ✅ |
| Firebase Hosting / CDN | опционально для раздачи манифеста | ❓ |

**Язык:** гибрид. Python — генерация/валидация (богатая экосистема, лёгкий порт Kotlin-алгоритмов). TypeScript — Functions и Firebase Rules тесты. Общий контракт — JSON Schema.

**Почему не «клиент читает Firestore»:** стоимость reads растёт с числом пользователей; статические gzip-файлы кэшируются и дешевле.

---

## 5. Схема взаимодействия

```
Scheduler ──► Cloud Run Job (generate/import) ──► Firestore: puzzles(status=Generated)
                         │                                  │
                         ▼                                  ▼
                 Cloud Run Job (validate+dedup) ──► status=Validated / Rejected
                         │
        (опц. ручное одобрение: CLI) ──► Approved
                         ▼
                 Publisher job ──► Storage: packs/<cat>/<lang>/<ver>.json.gz + manifest.json
                         │
 Android ◄── GET manifest (If-None-Match) ◄── Storage/CDN
 Android ──► GET pack by URL + sha256 verify ──► Room/файлы ──► игра офлайн
```

---

## 6. Схема Firestore (серверная)

Принцип: **документы маленькие, без массивов растущей длины, без чтения клиентом**.

- `puzzles/{puzzleId}` — каталог (1 документ = 1 задание, без payload, payload в Storage при публикации; для маленьких категорий допускается payload до ~50 КБ):
  `category, language|null, difficulty, schemaVersion, contentVersion, status, fingerprint, fingerprintAlgo, source{type, sourceId, licenseId}, generator{name, version, seed, params}, validation{status, validatedAt, validatorVersion, checks[]}, createdAt, publishedInPack|null, retiredAt|null, retireReason|null`.
- `fingerprints/{sha256}` → `{puzzleId, category}` — уникальность через транзакцию create-if-absent.
- `packs/{packId}` — `category, language, packVersion, schemaVersion, minAppVersion, count, sha256, sizeBytes, storagePath, status(draft|published|withdrawn), createdAt, supersedes`.
- `manifests/current` (и `manifests/history/{ver}`) — каноническая копия манифеста.
- `sources/{sourceId}` — `name, url, license, restrictions, retrievedAt, dataVersion, attributionRequired, robotsChecked`.
- `jobs/{jobId}` — журнал запусков: тип, категория, язык, счётчики generated/rejected/duplicate, длительность, ошибки; TTL-политика.
- `stats/{yyyymm}` — агрегаты по категории/языку (инкремент пакетно, не на каждое задание).
- `reviews/{id}` — очередь ручной проверки языковых заданий.

Индексы (составные): `puzzles(category, language, status, difficulty)`, `puzzles(status, createdAt)`, `puzzles(fingerprint)`, `packs(category, language, status, packVersion desc)`. Лимит роста: запись каталога — одна на задание; для генераторных категорий (Sudoku/Orb/TwoDots) каталог **не ведётся**.

---

## 7. Структура Cloud Storage

```
gs://<bucket>/
  public/v1/manifest.json                       # no-cache/короткий TTL, ETag
  public/v1/packs/<category>/<lang|_>/<packVersion>.json.gz   # immutable, long cache
  private/dictionaries/<lang>/<name>@<ver>.txt
  private/sources/<sourceId>/<date>/...
  private/batches/<jobId>/*.jsonl
  private/exports/
```
`public/` — чтение всем; `private/` — только сервисные аккаунты. Файлы пакетов **иммутабельны**: имя содержит версию; откат = новый манифест, указывающий на предыдущую версию.

---

## 8. Cloud Functions vs Cloud Run

| Задача | Где | Почему |
|---|---|---|
| Админ-команды (publish/retire/rollback) | Functions callable (TS) | короткие, нужен IAM/App Check |
| Обновление манифеста при публикации | Functions/Publisher job | атомарная запись |
| Триггер Storage на проверку sha256/schema нового пакета | Functions | событие |
| Генерация, солверы (Sudoku/Matchstick/Crossword), импорт | Cloud Run Jobs (Python) | минуты и CPU, таймауты Functions тесны |
| Расписание | Scheduler → Run Job | |
| Клиентская раздача | Storage/CDN статически | без вычислений |

💡 Публичных динамических эндпоинтов можно избежать вообще; опциональный `GET /v1/updates` — функция-обёртка над манифестом.

---

## 9. Генерация по категориям

| Категория | Источник | Подход |
|---|---|---|
| Matchstick | порт `MatchPuzzles.build` + `MatchSolver` на Python | тот же алгоритм → одинаковые результаты; расширение 60→N: `index`-seed вне 0..59, маска `startMask`+`maxMoves`; проверка `wellFormed`, `!isCorrect`, `minMoves == maxMoves`. Контрольный набор: первые 60 должны совпасть с Kotlin (golden-тест). |
| Number Sequence | генератор по типам правил: арифметическая/геометрическая, квадраты/кубы, Фибоначчи-подобные, чередование, разности разностей, простые числа и т. п. | Однозначность: перебор конкурирующих правил из реестра (полином ≤ k-й степени, рекуррентность порядка ≤2 и пр.) — публиковать только если все «разумные» правила дают один ответ. Сложность 1..100 по классу правила + размерам чисел. Клиентский `validateDefinition` — обязательная совместимость (≥3 числа, `answer: Int`). |
| Word Logic | полуавтомат: кандидаты из открытых лексических ресурсов + ручная проверка | Не машинный перевод. Для каждого языка — отдельный набор, `reviews` обязательны (`Approved`) |
| Mini Crossword | сетка из лексикона языка + определения из открытых данных/ручные | Генератор расстановки + проверка `problems()`-подобных правил (границы, конфликты, случайные слова, связность). Определения — только с лицензией или авторские; ручное ревью |
| Visual Logic | параметризованные правила `VisualRule` | Порт генератора функций-правил; проверка «ровно один вариант равен ответу», 4 варианта, без дублей. Палитра `Tile.color` — индексы клиента |
| Word Search | словари `#core/#extra` на язык | Сейчас клиент сам генерирует из словаря → 💡 сервер может доставлять **словари**, а не уровни (минимум трафика), либо готовые уровни `placements`. Важно: правка словаря меняет уровни — версионировать словарь |
| Sudoku | не нужен каталог | ✅ клиентский генератор; сервер — по желанию `seed`-списки/«ежедневный» seed |
| Orb Lines | не нужен | конфиг (Remote Config): `startBalls, spawn, minLine, colors` — ❓ только если клиент их параметризует (сейчас константы `OrbRules`) |
| Two Dots | не нужен | аналогично (`MOVES`, `COLORS`); Daily-seed возможен |

---

## 10. Проверка решений

Принцип: **два независимых механизма** — (а) порт клиентской проверки (golden-тесты против Kotlin-результатов), (б) независимый решатель на сервере.

- Matchstick: брутфорс по `correctStates`; сравнение с `maxMoves`.
- Number Sequence: набор конкурирующих гипотез; решение должно воспроизводить все числа и давать ровно один ответ; ответ в диапазоне Int.
- Word Logic: структура (4 варианта, `correctId` ∈ options, дубли текста), лингвистические проверки словарём, ручное ревью.
- Crossword: буквы на пересечениях, алфавит языка (нормализация NFC, upper), отсутствие «случайных» слов, связность.
- Visual: исполнение правила → ответ; ровно один вариант совпадает.
- Word Search: путь по клиентским правилам (ортогональные соседи, без повторов), слова ≥3 буквы, нет неучтённых случайных слов из словаря (насколько возможно).
- Sudoku (если когда-либо понадобится): `countSolutions == 1`.

Статусы: `Draft → Generated → ValidationPending → Validated | Rejected → Approved → Published → Retired`. В пакет попадают только `Approved` (для кода-генераторных категорий без ручной стадии — `Validated` автоматом при включённом флаге доверия).

---

## 11. Импорт и дедупликация

**Источники:** A — собственные генераторы; B — открытые словари/корпуса (записывается лицензия, URL, дата, версия, атрибуция в `sources/`); C — ручной импорт JSON/CSV (тот же конвейер). Без скрейпинга сайтов; любой веб-источник — только после проверки лицензии/ToS/robots.txt и согласования (❓).

**Fingerprint (sha256 от канонической формы):**
- Number Sequence: `(sequence, answer)` + `ruleId`.
- Matchstick: `(startMask, maxMoves)`.
- Crossword: нормализованное множество `(row, col, direction, ANSWER)` + симметрии (поворот/отражение) → минимальное представление.
- Visual: `(rule, tiles, gapIndex, columns)` + множество options без учёта порядка.
- Word Search: `letters` + сортированные `placements`; доп. «мягкий» fingerprint — набор слов.
- Sudoku (если нужно): каноническая форма относительно перестановок цифр/строк/колонок/транспозиции (при честной оценке — приближённая; полная канонизация дорога).
Мягкая похожесть (набор слов у кроссвордов > порога) → флаг `nearDuplicate`, а не гарантия уникальности.

---

## 12. Локализация

- Языковая категория → `language` обязателен; один документ = один язык; сервер отклоняет символы вне алфавита языка (UK: і ї є ґ; RU: ё/ъ по правилам; de: ä ö ü ß; fr/es/it: диакритика).
- Нормализация: Unicode NFC, `uppercase` по локали (`ß`, `İ` — осознанные правила), без смешения скриптов.
- Словари: на язык отдельный файл с версией и лицензией; список запрещённых/нежелательных слов отдельно.
- Выбор клиентом: `ContentLanguage.forInterface(AppLanguage)`. 🆕 Явный контракт fallback: если пака языка нет — клиент показывает встроенный; fallback на EN допустим только как документированное правило (❓ решить: скрывать категорию/предлагать выбор вместо тихого EN).
- Качество: статус `Approved` для словесных наборов только после проверки носителем (CLI-выгрузка очереди `reviews`).
- zh-CN: нейтральные категории да; словесные скрыты.

---

## 13. API-контракты (💡 новые)

Предпочтительно статическая раздача.

`GET /public/v1/manifest.json` (ETag)
```json
{
  "apiVersion": 1, "generatedAt": "2026-11-22T00:00:00Z", "manifestVersion": 17,
  "packs": [
    { "packId": "word_search-uk-0003", "category": "WORD_SEARCH", "language": "uk",
      "packVersion": 3, "schemaVersion": 1, "minAppVersion": 20, "count": 12,
      "sizeBytes": 18234, "sha256": "…", "url": "packs/WORD_SEARCH/uk/3.json.gz",
      "supersedes": 2, "status": "published" }
  ],
  "retired": ["cw-uk-004"]
}
```
Клиент хранит `manifestVersion`, фильтрует по `schemaVersion ≤ поддерживаемой`, `minAppVersion ≤ versionCode`, языку интерфейса.

Конверт пакета:
```json
{ "schema": 1, "packId": "…", "category": "MINI_CROSSWORD", "language": "uk", "packVersion": 3,
  "puzzles": [ { "id": "cw-uk-x-000123", "contentVersion": 1, "difficulty": 2, "payload": { } } ] }
```
Ошибки: HTTP 304 (не изменено), 404 (пак снят), 5xx/таймаут → повтор с экспоненциальной паузой; несовпадение sha256 → отбросить, оставить прежнюю версию.
Опционально `GET /v1/updates?…` (Functions) — только если понадобится серверная фильтрация; защищён App Check.

JSON Schema по категориям (Draft 2020-12; `additionalProperties: false`; версия в `$id`):
- `number_sequence.v1`: `prompt:string`, `sequence:int[3..12]`, `answer:int`, `explanation:string`, `hints:string[]`, `difficulty:1..100`. (повторяет `NumberSequencePuzzle`).
- `matchstick.v1`: `startMask:int 0..2^23-1`, `maxMoves:1..3`.
- `word_logic.v1`: `type ∈ {ODD_ONE_OUT, ANALOGY, PAIR, COMMON_CATEGORY, SEQUENCE}`, `stem`, `options[4]{id,text}`, `correctId`, `hint`, `explanation`, `language`.
- `mini_crossword.v1`: `size`, `entries[]{row,col,direction∈{ACROSS,DOWN},answer,clue}`, `language`.
- `visual_logic.v1`: `rule`, `columns`, `gapIndex`, `tiles[]`, `options[4]`, `correctId`; `Tile{kind,color,rotation∈{0,90,180,270},count,slot∈-1..3}`.
- `word_search.v1`: `size`, `letters(size²)`, `placements[]{word,path[]{row,col}}`, `language` (или `dictionary.v1` для режима «словарь»).
- `sudoku.v1`, `orb_lines.v1`, `two_dots.v1`: зарезервированы; нужны только если принято решение о внешних стартовых позициях (❓).
Каждая схема: пример валидный/невалидный будет добавлен на Этапе 2.

---

## 14. Публикация и синхронизация

1. Publisher собирает `Approved` задания категории/языка → пак `N+1` (иммутабельный), считает sha256, пишет в Storage, затем документ `packs/`, затем **последним** обновляет `manifest.json` (атомарный переключатель).
2. Откат: манифест снова указывает на предыдущий `packVersion`; ранее скачанные клиентом пакеты не удаляются.
3. Отзыв задания: ID попадает в `retired[]`; клиент не выдаёт его **новым** игрокам, у уже решивших остаётся в `solved_puzzles`.
4. Правка содержимого = новый `contentVersion` **и** новый ID, если меняется смысл/ответ (ID не меняет смысл).
5. Миграция схем: `schemaVersion` повышается, старые клиенты продолжают получать пакеты старой схемы (2 параллельные ветки на переходный период).

---

## 15. Офлайн-стратегия

- Встроенные паки/генераторы остаются; загруженные — добавляются **после** встроенных (не ломая индексы).
- Скачивание: WorkManager (unmetered/любая сеть по Remote Config), проверка sha256 и JSON-схемы **до** активации; запись во временный файл, затем атомарный rename; ошибка → прежний пак остаётся.
- Текущая игра не зависит от сети. Манифест проверяется не чаще интервала Remote Config.

---

## 16. Совместимость с XP и сохранениями

- `puzzleId` — ключ XP-идемпотентности; ID иммутабельны, формат внешних: `<prefix>-<lang?>-x-<NNNNNN>` (суффикс `x` отделяет от встроенных `cw-uk-01`).
- Для категорий с индексом (WordLogic/Crossword/Visual) клиенту нужен переход на `firstUnsolved` по ID (как в Matchstick/NumberSequence) или формулу «встроенные N штук → затем внешние по orderIndex» (индекс не ломается, пока встроенный список неизменен).
- Состояние `encode()` ссылается на `puzzle.id`; пакет должен хранить задания, пока на них есть незавершённое состояние (клиент не удаляет паки с активными состояниями).
- XP остаётся локальным. Серверной синхронизации прогресса нет (отдельный этап c Authentication).
- Daily/Endless: ❓ после реализации в клиенте; сейчас XP только в CAREER.

---

## 17. Безопасность

| Компонент | Доступ |
|---|---|
| `public/v1/**` в Storage | чтение всем; запись — никому из клиентов |
| `private/**`, Firestore | клиентам запрещено (Rules `allow read, write: if false`); доступ — сервисные аккаунты |
| Админ-callable | IAM + custom claim `admin`, App Check |
| Cloud Run Jobs | отдельный сервисный аккаунт на job, минимальные роли (Storage objectAdmin на нужный префикс, Datastore user) |
| Секреты | Secret Manager, не в репозитории и не в APK |
| Клиент | App Check (Play Integrity), без ключей записи |

Ввод: все внешние данные — только через схемы; парсеры без исполнения кода; лимиты размера, таймауты, allow-list доменов.

---

## 18. Оценка расходов (без точных тарифов)

Проверить в официальной документации: бесплатные квоты Firestore, Cloud Storage egress, Cloud Run Jobs, Scheduler, Functions 2nd gen, Secret Manager; требование плана Blaze для Functions/Run/Scheduler; бюджетные алерты (Cloud Billing Budgets).

Допущения: пакет ≈ 20–200 КБ gzip; 1 манифест (≈ 2–10 КБ) на проверку; проверка не чаще 1 раза/сутки/устройство; 8 языков × 6 языковых категорий пакетов; генерация 1 раз/неделю.

| MAU | запросы манифеста/мес | скачиваний пакетов/мес (оценка 3 на пользователя) | Firestore reads от клиентов | вывод |
|---|---|---|---|---|
| 100 | ~3 тыс. | ~300 | 0 | ≈ в бесплатных квотах (проверить) |
| 1 000 | ~30 тыс. | ~3 тыс. | 0 | вероятно бесплатно/центы |
| 10 000 | ~300 тыс. | ~30 тыс. (≈ 1–5 ГБ egress) | 0 | малые расходы; смотреть egress |
| 100 000 | ~3 млн | ~300 тыс. (≈ 10–50 ГБ) | 0 | заметный egress → CDN/кэш и дельта-пакеты |
Основные статьи: egress Storage и время Cloud Run Jobs. Firestore от клиентов не читается → линейного роста reads нет. Меры: gzip, immutable-кэш, ETag, интервал проверок, бюджетные алерты (напр. 50/90/100 %).

---

## 19. План тестирования

Unit: генераторы, валидаторы по категориям, дедупликация, нормализация языков. **Golden-тесты**: Python-порт против эталонных выходов Kotlin (Matchstick первые 60 уровней, проверки `problems()`). Схемы: валидные/невалидные примеры. Эмулятор Firebase: Rules, публикация, манифест, откат, повторная публикация. Интеграция: повреждённый пакет, обрыв сети, старая схема, `minAppVersion`. Безопасность: Rules-тесты. Локализация: алфавиты, NFC.
В отчётах — явно «запускалось / не запускалось».

---

## 20. Дорожная карта

| Этап | Задачи | Файлы | Критерий завершения | Риски |
|---|---|---|---|---|
| 1 | эта архитектура, решение ❓ | этот документ | утверждение | неполный аудит |
| 2 | репозиторий backend, Firebase-проект (после разрешения), схемы, Rules, CI, эмулятор | `schemas/`, `firestore.rules`, `storage.rules`, `.github/` | схемы валидируют примеры | согласование ID |
| 3 | генераторы+валидаторы Matchstick, NumberSeq, Visual (нейтральные) + golden-тесты | `generators/`, `validators/` | golden совпадают с Kotlin | расхождение порта |
| 4 | словарные категории, импортёры, дедупликация, ревью-очередь | `importers/`, `localization/` | пак UK/RU кроссвордов | лицензии, качество |
| 5 | publisher, манифест, пакеты, откат | `publishing/`, `functions/` | пакет скачивается с проверкой sha256 | атомарность |
| 6 | спецификация для Android + прототип клиентской интеграции (по отдельному разрешению) | `docs/android/` | офлайн-тест на устройстве | индексы прогресса |
| 7 | Scheduler, Remote Config, мониторинг, алерты | `scripts/` | еженедельный цикл | стоимость |
| 8 | финальные тесты | | отчёт | |

---

## 21. Риски и ограничения

- Клиент без сетевого слоя — основной объём работ на Android.
- Индексный прогресс в 3 категориях.
- Нет настоящей Daily/Endless.
- Качество языковых заданий требует людей.
- Однозначность числовых рядов — не доказуема полностью (честно: «разумный набор правил»).
- Невозможность абсолютной семантической дедупликации.
- Нет доступа к Firebase-консоли в этой сессии; расходы оценены качественно.

---

## 22. Вопросы для решения

1. zh-CN: оставить нейтральные категории с `zh-CN`, словесные скрытыми?
2. Fallback языка: скрывать категорию, предлагать выбор, или EN с явным уведомлением?
3. Новые Word Logic: в первую очередь UK и RU? Кто проводит ревью носителем (вы/внешний)?
4. Нужен ли серверный Daily (даже без Endless)?
5. Word Search: доставлять словари или готовые уровни?
6. Sudoku/Orb/TwoDots — подтвердить «каталога нет, только конфиги».
7. Формат внешних ID (`…-x-NNNNNN`) — подходит?
8. Разрешён ли план Blaze и лимит бюджета (сумма алерта)?
9. Допускается ли использование конкретных открытых словарей (список лицензий утвердить)?
10. Ручное одобрение (`Approved`) обязательно для всех категорий или только языковых?
11. Один Firebase-проект или dev/prod?

---

# Что потребуется от Android-разработчика для подключения backend

**Файлы запросить (Kotlin), чтобы закрыть пробелы аудита:**
`NumberSequencePack.kt`, `WordLogicPacks.kt`/`resolve()`, `CrosswordPacks.kt`, `VisualPacks.kt`, `CategoryStore.kt`, `ProgressRepository.kt`, `LogicMixDatabase.kt`+схемы Room (`app/schemas`), `MainActivity.kt`, `core/locale/*`, `SettingsRepository.kt`, `app/build.gradle.kts`, `libs.versions.toml`, `AndroidManifest.xml`.

**Изменения клиента (💡, ничего не реализовано):**
1. Зависимости: `kotlinx.serialization-json`, HTTP-клиент (OkHttp), WorkManager, Firebase BoM (Remote Config, App Check), разрешение `INTERNET`.
2. Room-миграция 2→3 (явная): таблицы `content_pack`, `content_puzzle` (либо файлы в `filesDir` с атомарной записью).
3. Интерфейс `PuzzleSource<T>` с реализациями «встроенная» и «кэш пакетов»; ViewModel'и (`CrosswordViewModel`, `VisualViewModel`, `WordLogicViewModel`, `GameViewModel`, `MatchstickViewModel`, `WordSearchViewModel`) переключить на него.
4. JSON→доменные модели с вызовом `problems()`, `validateDefinition`, `WordSearchVerifier`; невалидное отбрасывать.
5. Выбор уровня по ID (`firstUnsolved`) для WordLogic/Crossword/Visual либо правило «встроенные → внешние».
6. `ContentSyncWorker` (WorkManager): манифест по ETag, скачивание, sha256, атомарная активация, повтор.
7. Контракт fallback языков (решение п. 2).
8. Не удалять пакеты, на которые ссылаются сохранённые состояния; учесть `retired`.
9. (Опционально) принимать внешний seed/стартовую позицию для Sudoku/Orb/TwoDots и Daily-режим с разрешением XP для `GameMode.DAILY`.
10. Параметризация констант Orb/TwoDots через Remote Config — только если нужно.
