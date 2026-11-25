# Google Play Games Services: ручная настройка

Код готов, но намеренно **не содержит выдуманных ID**. Пока ID не внесены, Play Games выключены: приложение работает как гостевое, карточка «Рейтинг» скрыта.

Package name: `com.akynex.logicmix`. Firebase-проект и applicationId не меняются. Play Games отделены от Firebase.

## 1. Play Console
1. Приложение должно быть создано в Play Console (Internal testing достаточно).
2. **Grow users → Play Games Services → Setup and management → Configuration → Create new**: «Yes, my game already uses Google APIs» → выберите существующий Google Cloud/Firebase-проект приложения (не создавайте новый) → введите название и описание игры.
3. Сохраните **Application ID** (числовой, например `1234567890`, берётся из консоли, не придумывать).
4. **Credentials → Add credential → Android**: привяжите приложение `com.akynex.logicmix` и OAuth-клиент с SHA-1.
   Нужны SHA-1 отладочного ключа (`./gradlew signingReport`) и ключа подписи Play (App signing, Release → Setup → App signing).
5. **Testers**: добавьте свои аккаунты (пока игра не опубликована, PGS работает только для тестеров).

## 2. Таблицы лидеров (11 штук)
**Leaderboards → Create leaderboard**. Для каждой: формат Numeric, порядок **Larger is better**, без ограничений значений.

| Ключ в файле | Название (рекомендация) | Метрика, отправляемая приложением |
|---|---|---|
| `leaderboard_total_xp` | World ranking — total XP | общий XP |
| `leaderboard_arcade` | Arcade | лучший результат Arcade |
| `leaderboard_number_logic` | Number Logic | XP категории |
| `leaderboard_visual_logic` | Visual Logic | XP категории |
| `leaderboard_mini_crossword` | Mini Crossword | XP категории |
| `leaderboard_word_search` | Fillwords | XP категории |
| `leaderboard_word_logic` | Word Logic | XP категории |
| `leaderboard_sudoku` | Sudoku | XP категории |
| `leaderboard_matchstick` | Matchsticks | XP категории |
| `leaderboard_orb_lines` | Lines | лучший счёт |
| `leaderboard_two_dots` | Two Dots | лучший счёт (максимум по режимам) |

XP категории считается только с версии, в которой появились рейтинги (старый XP по категориям не восстановить).
Названия можно локализовать на 8 языков в самой консоли.

## 3. Куда вписать ID
- **Application ID** → `app/src/main/res/values/game_services.xml`, строка `game_services_project_id` (она же уходит в manifest `com.google.android.gms.games.APP_ID`).
- **ID таблиц** (вид `CgkI...`) → `app/src/main/assets/games/leaderboards.properties`, после `=`. Пустое значение скрывает таблицу.

## 4. Публикация
Когда проверите на тестовом аккаунте: Play Console → Play Games Services → **Publish**. Затем актуализируйте Data Safety (идентификаторы игрока, игровые результаты) и сверьте политику конфиденциальности.

## 5. Проверка на устройстве
Вход при запуске, имя и аватар на главном экране, кнопка «Подключить» в настройках, отправка результата (завершите задание), экран «Рейтинг» (своя позиция, топ-10, «Открыть в Google Play Играх»), поведение в авиарежиме и повтор отправки после возвращения сети.

## Статус (2026-11-25)
Application ID `178630214959` и 11 ID таблиц внесены (`res/values/game_services.xml`, `assets/games/leaderboards.properties`). Application ID извлечён из самих ID таблиц; сверьте его в Play Console → Configuration. Соответствие: fillwords=word_search, matchsticks=matchstick, lines=orb_lines. Осталось: OAuth-клиент Android с SHA-1 и добавление тестеров, пока игра не опубликована.
