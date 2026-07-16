# Восстановление FRCTL после сбоя или на новом компьютере

## 1. Забрать исходники

```powershell
git clone https://github.com/Livadies/frctl-app-node.git
cd frctl-app-node
```

В репозитории нет паролей, токенов, приватных ключей и локальных настроек серверов. Их нужно восстановить отдельно из доверенного хранилища.

## 2. Проверить Android

Установите JDK 17 и Android SDK, затем:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

Debug APK появится в `app/build/outputs/apk/debug/`. Для подписанного релиза задайте четыре переменные `FRCTL_KEYSTORE_*` и выполните `assembleRelease`.

## 3. Собрать Windows‑приложение

Требуется Python 3.11+:

```powershell
cd hub\windows
.\build-windows.ps1
```

В `hub/windows/dist/` появятся автономный EXE и ZIP. Python конечному пользователю не нужен.

## 4. Запустить локальный Node из исходников

```powershell
cd hub\node
python -m unittest discover -s tests -v
.\run-node.cmd
```

Локальные настройки создаются в `%LOCALAPPDATA%\FRCTL\node-config.json`. SSH‑ключи не копируются в проект: в конфигурации хранится только разрешённый путь и проверенный fingerprint.

## 5. Проверить браузерный Space

```powershell
cd hub\hf-static
python -m http.server 8787 --bind 127.0.0.1
```

Откройте http://127.0.0.1:8787/. Должны загрузиться 24 GitHub‑проекта и 24 модели Hugging Face. При отсутствии сети используется последний локальный кэш браузера.

## 6. Повторно опубликовать

- GitHub: отправьте проверенную ветку обычным `git push` и создайте pull request в `main`.
- Hugging Face: синхронизируйте только содержимое `hub/hf-static/` с репозиторием Space `livadies/frctl-hub`.
- никогда не добавляйте токены в URL remote, файлы проекта, README, команды CI или историю Git;
- секреты для CI задавайте через GitHub Actions Secrets / Hugging Face Space Secrets.

## Контрольная проверка

```powershell
.\gradlew.bat testDebugUnitTest
cd hub\node
python -m unittest discover -s tests -v
cd ..\space
python -m unittest discover -s tests -v
```

Ожидается: Android 4 теста, Node 28 тестов, Hub 8 тестов. Затем проверьте `node --check hub/hf-static/app.js` и соберите оба клиентских пакета.
