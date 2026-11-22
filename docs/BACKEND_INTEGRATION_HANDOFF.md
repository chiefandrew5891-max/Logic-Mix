# Logic Mix — Backend Integration Handoff

> **Примечание (2026-11-22):** документ — снимок аудита Android-кода. Целевая архитектура и приоритеты — в `LOGIC_MIX_FIREBASE_BACKEND_ARCHITECTURE.md` §0 (серверной генерации математических и визуальных задач нет; языков интерфейса восемь).


Документ для разработчика/ИИ-модели, которая будет делать внешний backend. Составлен по коду ветки `feature/stage-b`.

**Легенда:** ✅ РЕАЛИЗОВАНО (проверено по коду) · 💡 ПРЕДЛАГАЕТСЯ (в коде этого нет).

Корень кода: `app/src/main/java/com/akynex/logicmix/` (далее `…/`).

---

## 1. Общая картина (✅)

- Android, Kotlin, Jetpack Compose, Hilt, Room 2.8.4, DataStore Preferences 1.1.7.
- **Сети нет:** в `AndroidManifest.xml` нет `INTERNET`; в зависимостях нет HTTP-клиента, сериализации (kotlinx.serialization/Moshi/Gson), WorkManager.
- **Внешних заданий нет.** Все задания — зашитый Kotlin-код, ресурсные txt-словари либо детерминированные генераторы. JSON для заданий нигде не используется.
- Состояние незавершённого уровня сериализуется **самописными строками** (`a;b;c`, `td1|…`), а не JSON.
- `GameMode {CAREER, ENDLESS, DAILY}` существует в модели, но играется только **CAREER** (`MainActivity.kt`: `if (mode == GameMode.CAREER) Routes.GAME else Routes.COMING_SOON`). Daily/Endless ведут на экран «скоро».
- Все игры пишут результат через `ProgressRepository.recordAttempt(..., GameMode.CAREER, ...)`.

### 1.1 Категории и языки

`GameCategory` (порядок = порядок на экране; в хранилище — по `name`):
`MATCHSTICK, NUMBER_SEQUENCE, WORD_LOGIC, MINI_CROSSWORD, VISUAL_LOGIC, WORD_SEARCH, SUDOKU, ORB_LINES, TWO_DOTS`.

`ContentLanguage` (`…/domain/content/ContentLanguage.kt`): `EN("en"), UK("uk"), RU("ru"), IT("it"), ES("es"), DE("de"), FR("fr"), ZH_CN("zh-CN")`. `ContentLanguage.forInterface(AppLanguage)` падает в EN. `CategoryAvailability` скрывает WORD_SEARCH и MINI_CROSSWORD для zh-CN.

### 1.2 Сводная таблица

| Категория | Источник заданий | Язык | Модель | Файлы | ID задания | Проверка | «Уже проходили» |
|---|---|---|---|---|---|---|---|
| Числовые последовательности | зашитый пак, 13 задач | нет | `NumberSequencePuzzle` | `…/domain/game/NumberSequence.kt`, `NumberSequencePack.kt`; `…/feature/game/GameViewModel.kt` | `ns-001`… | `NumberSequenceEngine.checkAnswer` | Room `solved_puzzles` (`firstUnsolved`) |
| Спички | **генератор**, 60 детерминированных уровней | нет | `MatchPuzzle(index,start,maxMoves)` | `…/domain/matchstick/MatchEngine.kt`; `…/feature/matchstick/MatchstickViewModel.kt` | `matchstick-N` (1..60) | `MatchEquation.isCorrect(mask)` | Room `solved_puzzles` (`firstUnsolved`) |
| Логика слов | зашитые паки RU и EN (по 10) | да | `WordLogicPuzzle` | `…/domain/wordlogic/WordLogic.kt`, `WordLogicPacks.kt`; `…/feature/wordlogic/` | `wl-<lang>-NN` | `optionId == correctId` | индекс уровня в DataStore `category_games` + Room |
| Мини-кроссворды | зашитые паки, 7 языков × 5 сеток | да | `CrosswordPuzzle` | `…/domain/crossword/Crossword.kt`, `CrosswordPacks.kt`; `…/feature/crossword/CrosswordViewModel.kt` | `cw-<lang>-NN` | клетка→буква == решение | индекс в DataStore (ключ с языком) + Room |
| Визуальная логика | зашитый пак, строится из функций-правил | нет | `VisualPuzzle` | `…/domain/visual/VisualLogic.kt`, `VisualPacks.kt`; `…/feature/visual/VisualViewModel.kt` | `vl-NN` | `optionId == correctId` | индекс в DataStore + Room |
| Филворды | **генератор** из txt-словарей, 12 уровней/язык | да | `WordSearchLevel` | `…/domain/wordsearch/WordSearchEngine.kt`; `app/src/main/resources/wordsearch/<tag>.txt`; `…/core/data/WordSearchStore.kt` | `ws-<lang>-NN` | `WordSearchChecker` | `WordSearchStore` (per language) + Room |
| Sudoku | **генератор** (difficulty, seed) | нет | `SudokuPuzzle` | `…/domain/sudoku/SudokuEngine.kt`, `SudokuGame.kt`; `…/core/data/SudokuStore.kt` | `sudoku-<difficulty>-<seed>` | `values == solution` | только текущая партия; XP в Room |
| Orb Lines | **генератор** по seed, партия без уровней | нет | `OrbGame` | `…/domain/orb/OrbEngine.kt`; `…/core/data/OrbStore.kt`; `…/feature/orb/OrbViewModel.kt` | `orb-<seed>` | счёт ≥ `OrbRules.XP_SCORE`=100 даёт XP | `solved_puzzles` |
| Two Dots | **генератор** по seed | нет | `TwoDotsGame` | `…/domain/twodots/TwoDotsGame.kt`; `…/core/data/TwoDotsStore.kt`; `…/feature/twodots/TwoDotsViewModel.kt` | `twodots-<mode>-<id>` | счёт ≥ `TwoDotsRules.XP_SCORE`=300 | `solved_puzzles` |

---

## 2. Общие подсистемы (✅)

### 2.1 Прогресс и XP — `…/core/data/ProgressRepository.kt`, `…/core/database/*`, `…/domain/progression/*`
- Room БД `logic_mix.db`, версия 2, `exportSchema=true`, только явные миграции (`LogicMixMigrations.MIGRATION_1_2`), деструктивный fallback запрещён.
- Таблицы: `profiles`, `progress`, `solved_puzzles` (PK `profileId, puzzleId`), `attempts`, `xp_transactions`.
- Единственный профиль: `LocalProfileRepository.LOCAL_PROFILE_ID`.
- `recordAttempt(profileId, puzzleId, mode, outcome, startedAt, finishedAt, hintsUsed)`: XP только за **первое правильное решение в CAREER**; дубль отсекается `insertIfAbsent`. `xp_transactions.id = "$profileId:$puzzleId:FIRST_SOLVE"`.
- `Progression`: `BASE_XP=20`, `NO_HINT_BONUS=5` (при 0 подсказок), уровни 1..99, `requiredXp(level)=round((100+35*(level-1)^1.35)/50)*50`.
- Бонусные слова филвордов: `"$profileId:$levelId:EXTRA_WORD:WORD"`, `WordSearchRewards.EXTRA_WORD_XP=2`, `MAX_HINTS=3`.

**Следствие:** `puzzleId` — ключ идемпотентности XP. ID внешнего задания должен быть глобально уникальным и **неизменяемым**.

### 2.2 Хранилища уровня — `…/core/data/CategoryStore.kt`
`CategoryGames` + `CategoryStore` (DataStore `category_games`): ключи `<key>.level` (индекс текущего уровня) и `<key>.state.<puzzleId>` (строка состояния). Ключи: `word_logic`, `mini_crossword_<tag>` (язык в ключе), для визуальной логики — константа `KEY` в `VisualViewModel`. `complete(puzzleId, startedAt, hintsUsed)` → `recordAttempt`. **Уровень = индекс в списке** — при замене списка индексы «поедут» (§6).

`GameSessionStore` (`game_session`) — puzzle_id, hints_used, started_at для числовых последовательностей.

### 2.3 Подсказки
У каждой игры свои; `hintsUsed` передаётся в `recordAttempt` и влияет только на `NO_HINT_BONUS`. Рестарт счётчик подсказок не обнуляет.

---

## 3. По категориям

### 3.1 Числовые последовательности (✅ зашито)
```kotlin
data class NumberSequencePuzzle(
    val id: String, val difficulty: Int /*1..100*/, val prompt: String,
    val sequence: List<Int>, val answer: Int, val explanation: String,
    val hints: List<String> = emptyList(),
)
```
- id `ns-%03d`, минимум 3 числа (`MIN_SEQUENCE_LENGTH`). `NumberSequenceEngine.validateDefinition` → `ValidationResult.Valid|Invalid(errors)`.
- `checkAnswer` → `SolveResult.Correct|Incorrect|InvalidInput` (целое; допускается «+» и Unicode-минус). `getHint(hintsUsed)`.
- Выбор: `NumberSequencePack.firstUnsolved(solvedIds)`, `byId` — уже ID-ориентированная схема.
- `prompt/explanation/hints` — готовые строки; языка в модели нет.

Предлагаемый JSON (💡):
```json
{ "schema": 1, "category": "NUMBER_SEQUENCE", "id": "ns-ext-0014", "version": 1,
  "difficulty": 12, "language": null,
  "payload": { "prompt": "…", "sequence": [2, 4, 8, 16], "answer": 32,
               "explanation": "×2", "hints": ["Look at ratios", "Multiply by 2"] } }
```

### 3.2 Спички (✅ генератор)
- Состояние — 23-битная маска слотов: 0..6 цифра A, 7..8 оператор (7 горизонтальный, 8 вертикальный), 9..15 цифра B, 16..22 цифра C; сегменты a..g = биты 0..6; `MatchSlots.digitMasks = [63,6,91,79,102,109,125,7,127,111]`. Форма `A ± B = C`, цифры одиночные, «=» фиксирован.
- `MatchPuzzles.build(index)` детерминированно (`Random(index*7919+13)`): верное уравнение → перенос k спичек (k=1 при index<30, иначе 2) → оставляется, если `MatchSolver.minMoves == k`. `COUNT=60`.
- `MatchPuzzle(index, start, maxMoves)`, `id = "matchstick-${index+1}"`; решение вычисляет `MatchSolver.solution`.
- Проверка `MatchEquation.isCorrect`; подсказка `MatchGame.hintMove()`.

Предлагаемый JSON (💡):
```json
{ "schema": 1, "category": "MATCHSTICK", "id": "matchstick-ext-0001", "version": 1,
  "difficulty": 2, "language": null,
  "payload": { "startMask": 0, "maxMoves": 1 } }
```
`startMask` — 23-битное целое (значение выше — заглушка); сервер обязан проверять `wellFormed`, `!isCorrect` и `minMoves == maxMoves`.

### 3.3 Логика слов (✅ зашито, RU/EN)
```kotlin
WordLogicPuzzle(id /*wl-<lang>-NN*/, type /*ODD_ONE_OUT|ANALOGY|PAIR|COMMON_CATEGORY|SEQUENCE*/,
  language, difficulty, stem, options /*4 × WordOption(id "o1".."o4", text)*/, correctId, hint, explanation)
```
- `problems()` валидирует. Ответ сверяется по **id**, не по тексту.
- Состояние `WordLogicGame.encode()` = `hintsUsed;hintShown;wrong;eliminated`.
- Паки только RU/EN; остальные языки идут через `resolve()` в EN (флаг `isFallback`).

```json
{ "schema": 1, "category": "WORD_LOGIC", "id": "wl-uk-011", "version": 1,
  "language": "UK", "difficulty": 3,
  "payload": { "type": "ODD_ONE_OUT", "stem": "Знайдіть зайве слово",
    "options": [{"id":"o1","text":"яблуко"},{"id":"o2","text":"груша"},{"id":"o3","text":"стіл"},{"id":"o4","text":"слива"}],
    "correctId": "o3", "hint": "Три слова — фрукти", "explanation": "Стіл — меблі" } }
```

### 3.4 Мини-кроссворды (✅ зашито, 7 языков)
```kotlin
CrosswordPuzzle(id /*cw-<lang>-NN*/, language, size, difficulty,
  entries /*List<CrosswordEntry(row, col, direction ACROSS|DOWN, answer, clue)>*/)
```
- Языки ru, uk, en, it, es, de, fr — по 5 сеток (`CrosswordPacks`). Для zh `forLanguage` = null.
- Решение выводится из `entries` (карта клетка→буква); номера клеток вычисляются. `problems()` проверяет границы, конфликты букв, случайные слова, связность.
- Состояние `CrosswordGame.encode()` = `letters;revealed;hintsUsed`. Ключ прогресса `mini_crossword_<tag>` — языки не смешиваются.

```json
{ "schema": 1, "category": "MINI_CROSSWORD", "id": "cw-uk-006", "version": 1,
  "language": "UK", "difficulty": 2,
  "payload": { "size": 5, "entries": [
    {"row":0,"col":0,"direction":"ACROSS","answer":"МАМА","clue":"Рідна людина"},
    {"row":0,"col":0,"direction":"DOWN","answer":"МОРЕ","clue":"Солона вода"} ] } }
```
Все `answer` и `clue` — на языке `language`.

### 3.5 Визуальная логика (✅ зашито, без языка)
```kotlin
enum class ShapeKind { CIRCLE, SQUARE, TRIANGLE, DIAMOND, ARROW, LINE }
Tile(kind, color /*индекс палитры*/, rotation /*град. по часовой, кратно 90*/, count, slot /*-1 центр, 0..3 углы от верх-лев по часовой*/)
enum class VisualRule { SHAPE, COUNT, ROTATION, COLOR, POSITION, COMBINED, MATRIX }
VisualOption(id, tile)
VisualPuzzle(id /*vl-NN*/, rule, difficulty, tiles, gapIndex, columns, options /*ровно 4*/, correctId)
```
- `problems()`: gap в сетке; 4 варианта; нет одинаковых; ровно один вариант равен `tiles[gapIndex]`; `correctId` указывает на него; сетка прямоугольная.
- Текст подсказки выбирается по `rule` (в задании не хранится, перевода не требуется).
- Состояние `hintsUsed;hintShown;wrong;eliminated`.

```json
{ "schema": 1, "category": "VISUAL_LOGIC", "id": "vl-ext-0001", "version": 1, "difficulty": 2, "language": null,
  "payload": { "rule": "ROTATION", "columns": 4, "gapIndex": 3,
    "tiles": [ {"kind":"ARROW","color":0,"rotation":0,"count":1,"slot":-1},
               {"kind":"ARROW","color":0,"rotation":90,"count":1,"slot":-1},
               {"kind":"ARROW","color":0,"rotation":180,"count":1,"slot":-1},
               {"kind":"ARROW","color":0,"rotation":270,"count":1,"slot":-1} ],
    "options": [ {"id":"v1","tile":{"kind":"ARROW","color":0,"rotation":270,"count":1,"slot":-1}},
                 {"id":"v2","tile":{"kind":"ARROW","color":0,"rotation":0,"count":1,"slot":-1}},
                 {"id":"v3","tile":{"kind":"ARROW","color":0,"rotation":90,"count":1,"slot":-1}},
                 {"id":"v4","tile":{"kind":"ARROW","color":0,"rotation":180,"count":1,"slot":-1}} ],
    "correctId": "v1" } }
```

### 3.6 Филворды (✅ генератор + txt-словари)
- Словари `app/src/main/resources/wordsearch/{de,en,es,fr,it,ru,uk}.txt`: секции `#core` / `#extra`, по слову в строке (приводится к UPPER, только буквы). `WordDictionary(language, core, extra)`; загрузка `WordDictionaries.load`.
- 4 тира × `PER_DIFFICULTY=3` = 12 уровней на язык: размер 6/8/10/12, слов 4/6/8/10 (`WordSearchLevels`).
- `levelId = "ws-<tag>-NN"`, `seedFor = "ws:<tag>:<index>".hashCode()`. Слова: `pool.sorted().shuffled(Random(seed))` — **правка словаря меняет сгенерированные уровни**.
- `WordSearchLevel(id, language, index, size, seed, difficulty, letters /*row-major*/, placements /*List<Placement(word, path: List<Cell(row,col)>)>*/)`.
- Путь: соседние по горизонтали/вертикали клетки без повторов (`WordSearchRules.isValidPath`), мин. длина слова 3. `WordSearchChecker` → `Main/Extra/Duplicate/Unknown/TooShort/InvalidPath`. Валидатор уровня: `WordSearchVerifier.errors(level)`.
- Прогресс per-language: ключи `ws.<tag>.<index>.<field>` в `WordSearchStore`.

```json
{ "schema": 1, "category": "WORD_SEARCH", "id": "ws-uk-013", "version": 1,
  "language": "UK", "difficulty": 4,
  "payload": { "size": 6, "letters": "<36 букв row-major>",
    "placements": [ { "word": "МОРЕ", "path": [ {"row":0,"col":0},{"row":0,"col":1},{"row":0,"col":2},{"row":0,"col":3} ] } ] } }
```

### 3.7 Sudoku (✅ генератор)
- `SudokuDifficulty {EASY(42), MEDIUM(34), HARD(28), EXPERT(24)}` (число = количество подсказок). `SudokuGenerator.generate(difficulty, seed)` детерминирован; единственное решение (`SudokuSolver.countSolutions`); EASY/MEDIUM решаются «singles».
- `SudokuPuzzle(givens: IntArray(81), solution: IntArray(81), difficulty, seed)`, `id = "sudoku-<difficulty lowercase>-<seed>"`.
- Победа: `values == solution`. Состояние `values;notes;hints`, undo не хранится. `SudokuStore` хранит `SudokuSaved(difficulty, seed, game, elapsedSec)`: **одна** незавершённая партия.
- Seed = `System.currentTimeMillis()`.

```json
{ "schema": 1, "category": "SUDOKU", "id": "sudoku-ext-000123", "version": 1,
  "difficulty": "HARD", "language": null,
  "payload": { "givens": "<81 цифра, 0 = пусто>", "solution": "<81 цифра>" } }
```
Для внешних задач `SudokuStore` придётся расширить (хранить `givens`, а не seed).

### 3.8 Orb Lines (✅ генератор)
- Поле 9×9, 7 цветов, линия ≥ 5 (`MIN_LINE`), `SPAWN=3`, `START_BALLS=5`, очки `scoreFor(n)=n*(n-3)` при n ≥ 5; XP при счёте ≥ `XP_SCORE=100`.
- Весь ход детерминирован от seed (внутренний `Roll`), `OrbGame.new(seed)`. Состояние `cells;next;score;rng;hintsUsed`. Партия длится до game over; «задание» = seed. `OrbStore` хранит партию, seed, start, best.
- Библиотеки заданий нет; backend может отдавать только seed (💡).

### 3.9 Two Dots (✅ генератор)
- Поле 6×6, 5 цветов, `MOVES=20`, `XP_SCORE=300`, `DOT_POINTS=10`, `LOOP_BONUS=100`. `TwoDotsMode {CLASSIC, ENDLESS}`; CLASSIC заканчивается при 0 ходов.
- `TwoDotsGame(mode, id, seed, cells, score, movesLeft, moves, hintsUsed)`, `xpId = "twodots-<mode lowercase>-<id>"`. Состояние `td1|MODE|id|seed|score|movesLeft|moves|hintsUsed|cells`. `TwoDotsGame.new(mode, seed)` принимает только seed.
- Backend мог бы отдавать стартовую раскладку `cells` (36 цифр 0..4), но принять её сейчас нечем (💡).

---

## 4. Что нужно изменить в приложении для внешних заданий (💡)

Ничего из этого не реализовано.

1. **Зависимости:** `kotlinx.serialization-json`, HTTP-клиент (OkHttp/Ktor), WorkManager; разрешение `INTERNET`.
2. **Хранилище паков:** Room-миграция 2→3 (явная) с таблицами `content_pack(packId, category, language, version, schema, etag, installedAt)` и `content_puzzle(puzzleId PK, packId, category, language, difficulty, orderIndex, payloadJson, status)`.
3. **Абстракция источника:** `PuzzleSource` с реализациями «зашитая» и «кэш backend». Сейчас ViewModel'и берут паки напрямую (`CrosswordPacks.forLanguage`, `VisualPacks.all`, `NumberSequencePack`).
4. **Парсеры JSON → доменные модели** с вызовом `problems()` / `validateDefinition` / `WordSearchVerifier`; невалидное отбрасывать.
5. **Выбор уровня по ID** (как у числовых/спичек: `firstUnsolved`), а не по индексу.
6. Для Sudoku/Orb/Two Dots — принимать внешнюю стартовую позицию.
7. Алгоритмы проверки не менять.

---

## 5. Предлагаемый backend (💡)

### 5.1 Конверт задания
```json
{ "schema": 1, "id": "wl-uk-011", "category": "WORD_LOGIC", "language": "UK",
  "difficulty": 3, "version": 1, "contentHash": "sha256:…", "status": "published", "payload": {} }
```
- `id` глобально уникален и неизменяем; для внешних — отличимый префикс (`ext`), чтобы не пересечься с зашитыми.
- `version` растёт при правке; XP по ID повторно не выдаётся.
- `language` — `null` для нейтральных категорий; для языковых — `ContentLanguage.tag`.

### 5.2 API
- `GET /v1/manifest?category=&language=&since=<packVersion>` — паки, версии, хэши.
- `GET /v1/packs/{packId}` (ETag / `If-None-Match`) — массив конвертов.
- `GET /v1/puzzles?category=&language=&afterId=&limit=`; `GET /v1/puzzles/{id}`.
- gzip; хэш-манифест или подпись пака (Ed25519).

### 5.3 Версионирование
- `schema` — версия формата payload (неизвестную мажорную клиент отвергает).
- `packVersion` — монотонный номер библиотеки; клиент хранит последний применённый.
- `minAppVersion` в манифесте.

### 5.4 Конвейер на сервере
1. Приём из разрешённых источников / генерация.
2. Валидация **теми же правилами**, что в приложении (порт `problems()`, `MatchSolver`, `SudokuSolver.countSolutions`, `WordSearchVerifier`).
3. Дедупликация по `contentHash` канонической формы (кроссворды — множество `answer`+позиции; судоку — `givens`; спички — `startMask`; визуальные — `tiles`+`options`).
4. Классификация: категория, язык, сложность.
5. БД и публикация паков.

### 5.5 Локализация
- Одно задание — один язык; `language` обязателен для WORD_LOGIC, MINI_CROSSWORD, WORD_SEARCH.
- Клиент выбирает контент по `ContentLanguage.forInterface(...)`; прогресс уже хранится отдельно на язык.
- UK: слова и подсказки только украинские (алфавит с І, Ї, Є, Ґ); RU — русские; без смешения и транслитерации. Валидация алфавита на сервере.
- Нейтральные (спички, числа, визуальные, судоку) — `language: null`.
- zh-CN: Word Search и Mini Crossword скрыты.

---

## 6. Совместимость (💡 рекомендации)

- **Career:** сейчас порядок = зашитый список, уровень = индекс (`<key>.level`). Для внешних заданий хранить решённые ID и выбирать `firstUnsolved` по `orderIndex`. Зашитый пак остаётся первым, внешние добавляются после — существующие индексы игроков не ломаются.
- **Endless/Daily:** в коде заглушка. Daily: `GET /v1/daily?date=` с ID `daily-YYYY-MM-DD-<category>`; потребуется разрешить XP для `GameMode.DAILY` (сейчас `ProgressRepository.recordAttempt` начисляет только в CAREER).
- **XP/награды:** только локально по `puzzleId`; `Progression` не менять.
- **Прогресс:** остаётся локальным (Room + DataStore); существующие ключи не мигрировать.
- **Офлайн:** выданные задания копируются в локальный кэш; без кэша — зашитый пак.
- **Подсказки:** внутри задания (`hints`, `hint`, `correctId`) или вычисляются движком (спички, судоку, орбы, Two Dots).
- **Проверка решений:** остаётся на клиенте; сервер лишь пред-валидирует.
- **Повторы:** клиент фильтрует `id NOT IN solved_puzzles`; опционально сервер принимает список недавно решённых ID.
- **Обновление без Google Play:** паки через API (§5.2), WorkManager сверяет `manifest`.
- **Откат:** `status: "retired"`; решённое остаётся в `solved_puzzles`.
- **Безопасность:** HTTPS, подпись пака, лимит размера, невалидные задания игнорируются.

---

## 7. Известные ограничения (✅)

1. Нет сети, сериализации, WorkManager, `INTERNET`.
2. Паки зашиты в Kotlin; WORD_LOGIC только RU/EN, остальные языки получают EN (`isFallback`).
3. Уровень = индекс списка (кроссворды, логика слов, визуальная логика) — хрупко при замене списка.
4. Sudoku хранит seed+difficulty, не givens; Orb Lines и Two Dots — только seed.
5. Состояния — самописные строки без версии (кроме префикса `td1` у Two Dots).
6. Daily/Endless не реализованы.
7. Правка txt-словаря филвордов меняет сгенерированные уровни.

---

## 8. Рекомендуемый порядок интеграции

1. Зафиксировать JSON-схемы (§3, §5.1), на сервере реализовать валидаторы по правилам приложения.
2. Backend: хранилище, дедупликация, `manifest` + `packs` с ETag.
3. Приложение: зависимости, `INTERNET`, kotlinx.serialization, миграция Room 2→3.
4. `PuzzleSource` + парсеры; сначала нейтральные категории (числа, спички, визуальная логика).
5. Языковые: логика слов, кроссворды, филворды (с проверкой языка).
6. Выбор уровня по ID вместо индекса.
7. Фоновая синхронизация, офлайн-кэш, откат.
8. Sudoku/Orb/Two Dots с внешней стартовой позицией (по необходимости).
9. Daily/Endless поверх API.
