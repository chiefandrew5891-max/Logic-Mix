# Logic Mix — аналитика, реклама, согласие

Статус: код написан, **Gradle-сборка и работа на устройстве не проверялись** (в среде нет Android SDK/Gradle). Проверены только JUnit-тесты чистого ядра на fake-объектах. Версии библиотек (BoM 34.4.0, play-services-ads 24.6.0, UMP 3.2.0) нужно сверить при первой сборке.

## Архитектура
- `core/analytics` — `AnalyticsTracker`, `GuardedAnalyticsTracker` (валидация, дедупликация, try/catch, выключатель), `GameAnalytics` (состояние сессии во ViewModel), `FirebaseAnalyticsSink`, `CrashReporter`.
- `core/config` — `AppConfig` (безопасные дефолты, `sanitize`), `FirebaseConfigSource`.
- `core/consent` — `ConsentController`, `SharedPrefsConsentStore`, `UmpConsentManager`.
- `core/ads` — `RewardedAdController` (чистая логика), `AdAnalytics`, `AdMobRewardedGateway` (SDK), `ui/RewardedOfferDialog`.
- `core/monetization/Monetization` — сборка всех частей (Hilt singleton).

## События (Firebase)
Игровые: `category_open`, `game_start`, `puzzle_start`, `puzzle_complete`, `puzzle_fail`, `puzzle_skip`*, `puzzle_abandon` (`exit_reason`: explicit/switched), `hint_request`, `hint_used`, `difficulty_select`, `game_mode_select`, `level_restart`, `xp_earned`, `session_end`.
`*` пропуска в играх сейчас нет — событие подготовлено.

Реклама: `ad_offer_shown`, `ad_offer_accept`, `ad_offer_decline`, `ad_request`, `ad_loaded`, `ad_load_failed`, `ad_impression_confirmed`, `ad_clicked`, `ad_opened`, `ad_dismissed`, `ad_reward_earned`, `ad_reward_granted`, `ad_revenue_paid`.
Отличия от ТЗ: `ad_impression` зарезервировано Firebase (автособытие AdMob↔Firebase), поэтому `ad_impression_confirmed`; `ad_revenue_paid` по умолчанию выключено (`log_ad_revenue_in_app=false`), чтобы не удвоить доход, приходящий через связку AdMob↔Firebase.

Параметры: `game_category`, `puzzle_family`, `game_mode`, `difficulty`, `level_index`, `duration_ms`, `attempt_count`, `hint_count`, `result`, `content_language`, `generator_version`, `content_pack_version`, `ad_format`, `placement`. `puzzle_id` намеренно не отправляется (высокая кардинальность) — используйте `puzzle_family` + `level_index`.
`session_end` отправляется только при явном выходе из категории; уход в фон и убийство процесса не считаются отказом.

## Консоль Firebase
1. Положить `app/google-services.json` (проект `logicmix`); плагины подключатся сами.
2. Analytics → Custom definitions: зарегистрировать параметры как custom dimensions (`game_category`, `puzzle_family`, `game_mode`, `difficulty`, `content_language`, `generator_version`, `ad_format`, `placement`) и metrics (`duration_ms`, `hint_count`, `attempt_count`).
3. Включить Crashlytics; связать AdMob↔Firebase (revenue и `ad_impression`).
4. Remote Config: ключи из `AppConfig.Keys` (все необязательны, есть дефолты).
5. BigQuery export включать только после подтверждения владельца (платный).

## DebugView
```
adb shell setprop debug.firebase.analytics.app io.github.chiefandrew5891max.logicmix
```
Сначала включить «Анонимная аналитика» в Настройках (по умолчанию выключено).

## AdMob
Debug и release по умолчанию используют **официальные тестовые ID Google**. `assembleRelease/bundleRelease` падает без `-PADMOB_APP_ID=… -PADMOB_REWARDED_UNIT_ID=…`. Нужны от владельца: AdMob App ID, Rewarded Ad Unit ID (позже Interstitial/Native).
Экономика подсказок не менялась: все подсказки бесплатны, поэтому rewarded-предложение (`RewardedAdController` + `RewardedOfferDialog`) подготовлено, но в игровой процесс не подключено до утверждения экономики. Interstitial/Native: политика/конфиг-флаги готовы, SDK-шлюзы не реализованы.

## Согласие и приватность
- Все согласия по умолчанию выключены; Consent Mode: `analytics_storage` управляется переключателем в Настройках, `ad_*` — через Google UMP (TCF).
- Реклама не запрашивается и `MobileAds` не инициализируется, пока UMP не разрешил (`canRequestAds`).
- «Параметры конфиденциальности рекламы» в Настройках появляются, если UMP требует.
- Data Safety (черновик, требует проверки): Device/Other IDs (Advertising ID), App interactions, Crash logs, Diagnostics. Юридическое соответствие GDPR не проверялось.
