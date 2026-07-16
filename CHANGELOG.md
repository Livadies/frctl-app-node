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
