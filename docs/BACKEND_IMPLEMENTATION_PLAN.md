# Logic Mix — План реализации backend (Этап A, v1)

Статус: **план на утверждение. Код не пишется, Firebase не создаётся.** Основа — `LOGIC_MIX_FIREBASE_BACKEND_ARCHITECTURE.md` (v2, §1a). Метки: «проверено» — по чтению Kotlin-кода; «предположение» — не проверялось (Gradle/Android SDK в среде недоступны, тесты проекта не запускались).

## 1. Этапы

| Этап | Задачи | Результат / критерий готовности | Подтверждение |
|---|---|---|---|
| A | Архитектура v2, план, чек-лист Firebase | документы в `docs/` | — |
| B | `backend/schemas` (JSON Schema), манифест, пакеты `ns` ru/en (≥10 заданий каждый), `validate_pack`, `build_pack` (gzip, SHA-256), замер размера gz/raw | `pytest backend/tests` зелёный; невалидные пакеты отклоняются | да |
| C | Локальный mock-источник (каталог файлов + манифест, имитация ETag/304), клиент-симулятор синхронизации на Python; тесты: загрузка, ошибки, несовместимость, откат, неизменившийся пакет не качается | тесты зелёные без Firebase | да |
| D | Android: `PuzzleSource`, кэш, `ContentSyncWorker`, переход `GameViewModel` на ID; миграции (Room — только отдельно и с тестами) | сборка и `./gradlew test` (нужен Android SDK) | да, миграция — отдельно |
| E | После создания проекта владельцем: Storage, Firestore, Rules, App Check, `google-services.json` на стороне владельца | чек-лист `FIREBASE_SETUP_CHECKLIST.md` | да |
| F | Сквозная проверка: публикация → загрузка → установка → игра → прогресс (staging → production) | чек-лист на устройстве | да |
| G | Остальные категории и языки (uk, it, de, fr, es), Word Logic на 7 языках | пакеты по очереди en→ru→uk→it→de→fr→es | да |
| H | Daily (экран, XP-интеграция), бонусы, автоматическое пополнение | см. архитектуру §1d | да |

Следующий крупный этап — только после отдельного подтверждения владельца.

## 2. Структура backend (предложение)

```text
backend/            Python, не смешивается с Kotlin
  schemas/  validators/  generators/  packaging/  importers/
  manifests/  samples/{ru,en}/  tests/  scripts/  README.md
docs/
```
Android-код остаётся в `app/`.

## 3. Первый прототип RU + EN

**Категория: числовые последовательности (NUMBER_SEQUENCE).** Обоснование (проверено): структура простая (`id`, `sequence: List<Int>`, `answer: Int`, `difficulty`, `hints`, `explanation`); выбор уровня уже по ID (`firstUnsolved(solvedIds)`, `byId`), прогресс в Room `solved_puzzles` — индексных сохранений нет; валидатор правил уже есть в `NumberSequenceEngine.validateDefinition` (≥3 числа, сложность 1..100).
Оговорка (проверено): сейчас в клиенте у категории нет языкового текста — `prompt="next"` не показывается, подсказки — нейтральные формулы. Ru/en-различие в прототипе — текст `h` и `e` («Каждое число больше на 4» / «Each number grows by 4»).

Формат пакета (предложение, пример валидного):
```json
{"schema":1,"packId":"ns-en","category":"ns","language":"en","packVersion":1,
 "publishedAt":"2026-01-01T00:00:00Z",
 "source":{"type":"original","generator":"handmade","version":"1"},
 "puzzles":[{"id":"ns-x-000001","d":1,"seq":[3,7,11,15],"a":19,
  "h":["Look at the gap between neighbours"],"e":"Each number grows by 4."}]}
```
- ID одинаков в ru и en (нейтральное задание) → XP не дублируется.
- SHA-256 и размер хранятся в манифесте (для `.json.gz` и несжатого), не внутри файла.
- Валидация: JSON, схема, версия схемы, язык и алфавит текста (кириллица для ru), дубли ID и последовательностей, ответ совпадает с правилом (проверка по реестру правил), длина полей, контрольная сумма.
- Рекомендация: самодостаточный пак на язык (дубль чисел ≈ 100 Б/задание, после gzip мало). Размер будет измерен на этапе B.
- Объём: ~3–5 дней работы без Android.

## 4. Риски для существующих сохранений

1. Индексные указатели `<key>.level` в DataStore `category_games`: Word Logic (`word_logic`), Mini Crossword (`mini_crossword_<tag>`), Visual Logic (`visual_logic`) — проверено. Менять порядок встроенных заданий нельзя; серверные только дописываются в конец.
2. `word_logic.level` один на все языки (проверено) — смена языка использует общий индекс; нужен ключ с тегом языка (миграция DataStore, подтверждение).
3. Филворды: `seed="ws:<tag>:<index>"`, слова из словаря — правка словаря меняет уровень (проверено). Встроенные 12 уровней не трогать; новые — с `dictVersion`.
4. `GameViewModel` использует `NumberSequencePack.puzzles.size` и `indexOf(puzzle)+1` — заменить при подключении пакетов.
5. Состояния `encode()` без версии формата (кроме Two Dots `td1`) — не менять формат без версии.
6. Room v2, есть только `MIGRATION_1_2`; новая миграция — отдельно, с тестами, без `fallbackToDestructiveMigration`.
7. Пак нельзя удалять, пока на него ссылается незавершённое состояние.
8. Повторное начисление XP при смене ID — запрещена смена ID; смена смысла = новый ID.
9. Удаление zh: `AppLanguage.fromStored` вернёт `SYSTEM` для неизвестного значения (проверено); per-app локаль AppCompat на устройстве — предположение.
10. Обновление пакета во время игры: применять только при следующем запуске/выходе в меню.

## 5. Android-файлы, которые придётся менять (пути под `app/src/main/java/io/github/chiefandrew5891max/logicmix/`)

- Сборка: `app/build.gradle.kts`, `gradle/libs.versions.toml`, `AndroidManifest.xml` (INTERNET, этап D/E).
- Локаль (решение №1): `core/locale/AppLanguage.kt`, `domain/content/ContentLanguage.kt`, `domain/content/CategoryAvailability.kt`, `core/designsystem/LetterKeyboard.kt`, `feature/settings/SettingsScreen.kt`, `feature/gamestyle/GameStyleViewModel.kt` (комментарий), `res/xml/locales_config.xml`, `res/values-zh-rCN/`, `res/values/language_names.xml`, тесты (`SettingsRepositoryTest`, `LanguageAndThemeTest`, `CrosswordTest`, `WordSearchTest`, `WordLogicTest`, `HomeStateTest`, `CategoryGridTest`).
- Контент: `NumberSequencePack.kt`, `GameViewModel.kt`, `WordLogicPacks.kt` (убрать тихий EN-fallback), `CrosswordPacks.kt`, `VisualPacks.kt`, `CrosswordViewModel.kt`, `VisualViewModel.kt`, `WordLogicViewModel`, `CategoryStore.kt`.
- Прогресс: `ProgressRepository.kt` (XP для DAILY), `core/database/*` (миграция), `WordSearchStore.kt`, `WordSearchEngine.kt`.
- Новое: `PuzzleSource`, кэш пакетов, `ContentSyncWorker` (WorkManager), экран Daily, маршруты в `MainActivity.kt`.
- Строки ошибок «нет контента для языка» во всех 7 `values-*`.

## 6. Реестр источников контента (решение №9)

**Ни один источник не одобрен: лицензии не проверялись.** Перед использованием заполнить поля: URL, языки, тип, лицензия, коммерческое использование, атрибуция, способ получения, ограничения.

| Кандидат | Тип | Статус |
|---|---|---|
| Wiktionary / Wikidata | слова, определения | НЕ ПРОВЕРЕНО |
| OpenCorpora (ru) | морфология, слова | НЕ ПРОВЕРЕНО |
| Hunspell-словари | орфография | НЕ ПРОВЕРЕНО |
| Tatoeba | предложения | НЕ ПРОВЕРЕНО |
| wordfreq / FrequencyWords | частоты слов | НЕ ПРОВЕРЕНО |
| Собственные алгоритмы и шаблоны | задания | разрешено (свой код) |

Для прототипа используется только небольшой собственный набор.

## 7. Оценки и допущения

Расходы — архитектура §18 (допущения явные, тарифы не проверены). Размер пакетов не измерен.
