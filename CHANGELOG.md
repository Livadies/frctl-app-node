# FRCTL — Changelog & Build Notes

## Подпись APK

Ключ и пароли не хранятся в репозитории. Release-сборка получает `FRCTL_KEYSTORE_PATH`, `FRCTL_KEYSTORE_PASSWORD`, `FRCTL_KEY_ALIAS` и `FRCTL_KEY_PASSWORD` из переменных окружения или локальных Gradle properties.

---

## Сборка release APK

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio1\jbr"
.\gradlew.bat assembleRelease
```

Результат: `app\build\outputs\apk\release\app-release.apk`

---

## История версий

### v1.4.0 / FRCTL Node 0.6 — UI and i18n phase 3 — 2026-07-18

- the Android monolith was split into home, search, detail, settings, reusable components and theme modules;
- Material You dynamic color and the FRCTL neon theme can be selected in settings;
- Android, the public Hugging Face interface and the local Node panel support RU/EN/ZH/DE/ES selection;
- Android adds skeleton loading, pull-to-refresh, animated list placement, predictive back, shared app-icon transitions and haptic feedback;
- repository icons try the F-Droid fastlane convention first and fall back to the owner avatar;
- Room schema 1→2 migration preserves existing favorites and installed entries;
- Android version raised to 1.4.0 (6), Node version raised to 0.6.0.

### v1.3.0 / FRCTL Node 0.5 — Feature phase 2 — 2026-07-18

- Android catalog cache moved from temporary files to a persistent Room database with TTL and a dated offline banner;
- favorites and a local installed list survive restarts; WorkManager checks tracked GitHub releases daily and can notify about updates;
- search uses a 300 ms debounce, persists the last eight queries and shows useful empty-state suggestions;
- GitHub repository topics are the primary category signal in the Node marketplace, with keywords retained as fallback;
- the Node dashboard receives audit updates over same-origin SSE, verifies integrity on demand and exports verified JSONL;
- Android version raised to 1.3.0 (5), Node version raised to 0.5.0.

### Correctness phase 1 — 2026-07-18

- Android API responses are decoded with `kotlinx.serialization` and tolerate unknown, reordered and nullable fields;
- GitHub Device Flow permanently increases its polling interval after `slow_down` and handles `authorization_pending` explicitly;
- GitHub quota exhaustion is distinguished from other HTTP 403 responses;
- FRCTL Node returns a valid degraded/offline empty catalog when the network and disk cache are unavailable;
- Plink receives the pinned `SHA256:…` fingerprint format documented for `-hostkey`;
- binary audit-key creation is made reliable on Windows.

### Security phase 0 — 2026-07-18

- GitHub token encrypted with a non-exportable Android Keystore AES/GCM key, with one-time plaintext migration;
- APK download confirmation shows source, publisher, trusted-publisher status and published SHA-256 when available;
- Hugging Face mirrors use collision-resistant `owner__repository.apk` names;
- FRCTL Node audit chain upgraded to HMAC-SHA-256 with a protected local key and fail-closed verification;
- native-confirmation availability is exposed in `/api/status`, and the threat model documents local-malware limits.

### v1.2.0 (versionCode 4) — 2026-07-17

- категории Android, ИИ, безопасность, удалённый доступ, инструменты и медиа;
- каталог популярных моделей Hugging Face;
- смешанный поиск по GitHub-приложениям и HF-моделям;
- foreground-обновление каталога каждые пять минут и ручной refresh;
- время последнего обновления, офлайн-кэш и источник каждой позиции;
- секреты подписи удалены из конфигурации проекта.

### v1.1.1 (versionCode 3) — 2026-07-12

- **Удалено** разрешение `REQUEST_INSTALL_PACKAGES` из `AndroidManifest.xml`
  - Причина: модерация отклонила v1.1.0 из-за этого разрешения
  - Функциональность не затронута — кнопка «Установить» открывает APK-ссылку через браузер (`ACTION_VIEW`), а не устанавливает APK программно
- Добавлен `signingConfig` для release-сборки в `build.gradle.kts`

### v1.1.0 (versionCode 2)

- Обновлённый дизайн главного экрана
- Экран деталей приложения

### v1.0.0 (versionCode 1)

- Первый релиз
