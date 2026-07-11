# FRCTL — Frictionless App Engine: Bypass Edition

FRCTL is a serverless, open-source Android marketplace that discovers installable APK releases on GitHub and transparently falls back to Hugging Face dataset mirrors. Search, parsing, caching preferences, README rendering, and installation routing all run on-device.

## Architecture

- Kotlin + Jetpack Compose, MVVM/Clean separation
- raw-response heuristic parsing: strict `.apk` URLs are extracted without binding release JSON to brittle DTOs
- GitHub Search and Releases routes with optional `Bearer`, `token`, or raw `Authorization` values
- Hugging Face `frctl-mirror` dataset fallback
- token stored only in app-private Android DataStore
- RU, EN and Simplified Chinese resources; README Markdown rendered locally
- system browser/package installer handles APK downloads; FRCTL operates no backend

## Build and test

```shell
./gradlew test assembleDebug
./gradlew connectedDebugAndroidTest
```

The debug artifact is `app/build/outputs/apk/debug/app-debug.apk`.

## Security

Never commit access tokens. Users provide optional GitHub credentials locally. APKs are third-party binaries: verify publisher signatures and hashes before installation.

License: MIT.
