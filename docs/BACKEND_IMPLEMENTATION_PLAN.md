# Logic Mix — План реализации backend (Этап A, v1)

Статус: **план на утверждение. Код не пишется, Firebase не создаётся.** Основа — `LOGIC_MIX_FIREBASE_BACKEND_ARCHITECTURE.md` (v2, §1a). Метки: «проверено» — по чтению Kotlin-кода; «предположение» — не проверялось (Gradle/Android SDK в среде недоступны, тесты проекта не запускались).

## 1. Этапы (редакция мастер-промпта 2026-11-22)

| Этап | Состояние | Содержание |
|---|---|---|
| A | сделано | документация v3 |
| B | **сделано** (`backend/`, 27 тестов) | реестр, адаптеры, нормализация, дедупликация, SQLite, пакеты, SHA-256, манифест, публикатор, откат, референс клиента, CLI |
| C | заблокировано | реальный импорт Kaikki/Wikidata ru+en: хост kaikki.org недоступен из среды разработки; лицензии не проверены |
| D | частично | шаблоны Cloud Run Job/Scheduler/Storage и Firestore Rules, `job.py` — готовы, не применены; нужен проект владельца |
| E | не начат | Android: `PuzzleSource`, кэш, WorkManager, переход Word Logic на ключ по языку, `dictVersion` для Word Search |
| F | не начат | Mini Crossword: генератор ≤ 10 слов с динамическим размером, pan/zoom поля |
| G | не начат | Visual Logic: 13 семейств с независимым верификатором |
| H | не начат | Number Logic: семейства выражений и закономерностей |
| I | не начат | Daily, AdMob (отдельная подсистема), остальные языки |

Android-этапы требуют Android SDK/Gradle (в среде разработки недоступны) — выполнять там, где сборка возможна.

## 2. Структура backend (предложение)

```text
backend/            Python, не смешивается с Kotlin
  schemas/  validators/  generators/  packaging/  importers/
  manifests/  samples/{ru,en}/  tests/  scripts/  README.md
docs/
```
Android-код остаётся в `app/`.

## 3. Прототип (пересмотрено)

Прототип на числовых последовательностях **отменён**: серверной генерации математических задач нет. Вертикаль доставки — языковые пакеты `wordsearch-<lang>` и `crossword-<lang>` для ru/en (fixtures → `backend/tests/fixtures`, реальные данные — этап C). Формат пакета, манифест и протокол клиента — архитектура §0.4, §0.8; тесты — `backend/tests/test_backend.py`.

## 4. Риски для существующих сохранений

1. Индексные указатели `<key>.level` в DataStore `category_games`: Word Logic (`word_logic`), Mini Crossword (`mini_crossword_<tag>`), Visual Logic (`visual_logic`) — проверено. Менять порядок встроенных заданий нельзя; серверные только дописываются в конец.
2. `word_logic.level` один на все языки (проверено) — смена языка использует общий индекс; нужен ключ с тегом языка (миграция DataStore, подтверждение).
3. Филворды: `seed="ws:<tag>:<index>"`, слова из словаря — правка словаря меняет уровень (проверено). Встроенные 12 уровней не трогать; новые — с `dictVersion`.
4. `GameViewModel` использует `NumberSequencePack.puzzles.size` и `indexOf(puzzle)+1` — заменить при подключении пакетов.
5. Состояния `encode()` без версии формата (кроме Two Dots `td1`) — не менять формат без версии.
6. Room v2, есть только `MIGRATION_1_2`; новая миграция — отдельно, с тестами, без `fallbackToDestructiveMigration`.
7. Пак нельзя удалять, пока на него ссылается незавершённое состояние.
8. Повторное начисление XP при смене ID — запрещена смена ID; смена смысла = новый ID.
9. `zh-CN` сохраняется: WORD_SEARCH и MINI_CROSSWORD скрываются только через `CategoryAvailability`; нельзя добавлять скрытие в обход него. Для WORD_LOGIC на zh — сообщение «нет заданий», без подмены на EN.
10. Обновление пакета во время игры: применять только при следующем запуске/выходе в меню.

## 5. Android-файлы, которые придётся менять (пути под `app/src/main/java/com/akynex/logicmix/`)

- Сборка: `app/build.gradle.kts`, `gradle/libs.versions.toml`, `AndroidManifest.xml` (INTERNET, этап D/E).
- Локаль (zh-CN сохраняется, ничего не удаляется): `domain/content/CategoryAvailability.kt` (единый механизм скрытия; проверить, что его используют все режимы), `domain/content/ContentLanguage.kt` и `WordLogicPacks.kt` (поведение при отсутствии пака), при необходимости `LetterKeyboard.kt`.
- Контент: `NumberSequencePack.kt`, `GameViewModel.kt`, `WordLogicPacks.kt` (убрать тихий EN-fallback), `CrosswordPacks.kt`, `VisualPacks.kt`, `CrosswordViewModel.kt`, `VisualViewModel.kt`, `WordLogicViewModel`, `CategoryStore.kt`.
- Прогресс: `ProgressRepository.kt` (XP для DAILY), `core/database/*` (миграция), `WordSearchStore.kt`, `WordSearchEngine.kt`.
- Новое: `PuzzleSource`, кэш пакетов, `ContentSyncWorker` (WorkManager), экран Daily, маршруты в `MainActivity.kt`.
- Строки ошибок «нет контента для языка» во всех 7 `values-*`.

## 6. Реестр источников (решение №9)

Источник в коде: `backend/logicmix_backend/registry.py`; просмотр: `python3 -m logicmix_backend.cli sources`. Статусы лицензий **не проверены**, пока не заполнены `license_status=approved` и `license_checked_on`. Production блокируется программно.

| Приоритет | Источник | Даёт | Статус |
|---|---|---|---|
| 1 | Kaikki.org / Wiktionary | слова, леммы, POS, определения, формы | needs_review (CC BY-SA/ShareAlike, заявлено) |
| 2 | Wikidata | категории, связи | needs_review |
| 3 | LibreOffice Dictionaries | орфография, формы | needs_review (лицензия на каждый словарь) |
| 4 | wordfreq | частотность | needs_review |
| — | fixtures проекта | тесты | approved (только тесты/staging) |

## 7. Оценки и допущения

Расходы — архитектура §18 (допущения явные, тарифы не проверены). Размер пакетов не измерен.
