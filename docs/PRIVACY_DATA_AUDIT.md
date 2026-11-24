# Privacy data audit (Logic Mix Android)

Based on the code in `feature/stage-b`. This is an engineering audit, not legal advice.

| Area | Fact | Source |
|---|---|---|
| Permissions | Only `INTERNET`, `VIBRATE` | `AndroidManifest.xml` |
| Accounts | None; progress, XP, Arcade and settings are local (Room/DataStore) | `core/data` |
| Firebase Analytics | Disabled by manifest defaults; enabled only by the "Anonymous analytics" toggle (default off). Events pass `GuardedAnalyticsTracker` only if `consent.analyticsAllowed`. `arcade_*` events use the same tracker | `core/analytics/*`, manifest meta-data |
| Crashlytics | Disabled by default; follows the analytics toggle | `CrashReporter` |
| Remote Config | `fetchAndActivate` on creation, 12 h interval, **not gated by analytics consent** | `core/config/FirebaseConfigSource.kt` |
| AdMob / UMP | UMP `gatherConsent` at startup; ads requested only if `canRequestAds`; `MobileAds.initialize` is lazy. Rewarded ad for hints. Test mode if no own unit IDs | `core/monetization`, `core/ads/AdMobRewardedGateway.kt`, `core/consent` |
| Privacy options | Settings entry shown when `privacyOptionsRequired` (kept as is) | `UmpConsentManager`, `SettingsScreen` |
| Play In-App Review | `com.google.android.play:review`. Local counters (first launch, completed tasks, sessions, attempt dates) in `review_prompt` SharedPreferences; limits from Remote Config (`review_*`). Google does not report whether the dialog showed or any rating; no `review_submitted` event exists. Analytics events (`review_eligible`, `review_request_attempt`, `review_flow_finished`, `review_request_error`, `rate_app_clicked`, `review_store_opened`) pass the consent guard. Data Safety: the Play Core review library collects no additional data declared by us; re-check the SDK disclosure when the app is on Play Console | `core/review/*`, `domain/review/*` |
| Google Play Games Services v2 | `com.google.android.gms:play-services-games-v2`. Optional sign-in; gamer name and avatar are read from Google and shown locally (not stored on our side, not sent to Firebase/analytics). Scores (total XP, arcade best, Lines/Two Dots best, per-category XP) are submitted to Play Games leaderboards only when signed in. Local files: `play_games` SharedPreferences (category XP, last confirmed score per board). Disabled until `game_services_project_id` is set. Data Safety: declare the Play Games SDK data (player identifiers/gameplay scores) once published | `core/games/*`, `domain/games/*` |
| Content sync | `ContentSyncConfig.BASE_URL` is empty, so sync is effectively off | `core/content/ContentSyncWorker.kt` |

## Gaps / contradictions to resolve (OWNER ACTION REQUIRED)
1. Remote Config runs without analytics consent. Either gate it or keep the policy wording (section 5 states it honestly).
2. Play Console **Data safety** form must match this table (device/other IDs, app interactions, crash logs, diagnostics; ads SDK data).
3. AdMob **Privacy & messaging**: configure the GDPR/US consent messages and publish them; otherwise UMP shows nothing.
4. Retention periods are not defined by us; the policy defers to Google's terms. Provide real periods if you set them.
5. No in-app data deletion path exists; the policy points to the contact email.
6. If content sync is enabled later, update policy section 7.
