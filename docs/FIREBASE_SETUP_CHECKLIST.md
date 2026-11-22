# Logic Mix — чек-лист подключения Firebase (для владельца)

Статус: **подготовка. Firebase-проект ещё не создан; делать это стоит только после подтверждения Этапа E.**
Правила: **не присылайте в чат и не коммитьте** Service Account JSON, API-ключи, платёжные данные. `google-services.json` не содержит секретов администратора, но коммитить его — только по отдельному решению (по умолчанию в `.gitignore`). Один проект; `staging` и `production` разделяются путями и метаданными.

Где выполняется: **[Console]** Firebase/Google Cloud Console · **[CLI]** firebase / gcloud · **[AS]** Android Studio.

## Порядок

1. **[Console]** Создать один Firebase-проект. Записать Project ID (его можно сообщить, он не секретен).
2. **[Console]** Подключить тариф Blaze и привязать платёжный аккаунт (данные — только в Google).
3. **[Console]** Сразу настроить бюджет и уведомления (Billing → Budgets & alerts): пороги, например 50/90/100 %. Сумму выбирает владелец.
4. **[Console]** Зарегистрировать Android-приложения: release (`applicationId` из `app/build.gradle.kts`) и debug (если у него другой id/суффикс). Добавить SHA-256 сертификатов подписи (debug и release).
5. **[Console]** Включить Cloud Storage; выбрать регион (неизменяем — решить заранее; ориентир — ближе к аудитории).
6. **[Console]** Включить Cloud Firestore (режим production, правила «всё запрещено» до настройки).
7. **[CLI]** Установить правила безопасности из репозитория (`firebase deploy --only firestore:rules,storage`): чтение `public/{staging,production}/**` публично; запись — запрещена всем клиентам; Firestore для клиентов закрыт.
8. **[Console]** Включить Firebase App Check (Play Integrity) для Storage/Firestore; сначала в режиме мониторинга.
9. **[Console]** IAM: отдельные сервисные аккаунты — `publisher-staging`, `publisher-production`, `generator` (Cloud Run Job). Минимальные роли; production-публикация — только отдельному аккаунту. Ключи не создавать: использовать Workload Identity / impersonation.
10. **[AS]** Положить `google-services.json` в `app/` (локально), добавить в `.gitignore`; подключить Google Services plugin и Firebase BoM — это делает разработчик на этапе D/E.
11. **[AS]** Добавить разрешение `INTERNET`; debug-сборка → канал `staging`, release → `production` (`BuildConfig`).
12. **[CLI]** Загрузить тестовый пакет в `public/staging/v1/` и проверить скачивание debug-сборкой; production не трогать.
13. **[Console]** Мониторинг: Storage egress, число запросов, расходы; проверить, что алерты приходят.
14. Откат и публикация: promote staging → production тем же файлом (sha256 совпадает); откат — вернуть предыдущий `packVersion` в манифесте production.

## Что нужно сообщить разработчику

Только: Project ID, регион Storage, названия сервисных аккаунтов, подтверждение этапа. Ничего секретного.

## Не проверено

Актуальные тарифы, квоты и точные названия пунктов консоли нужно сверить в документации Google на момент настройки.
