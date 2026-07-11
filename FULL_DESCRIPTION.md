# FRCTL — open apps without borders

FRCTL is a serverless Android marketplace for independent open-source applications. The client discovers public Android projects directly through GitHub, displays a ready-to-browse catalog, loads full descriptions from repository README files and resolves installable APK assets only when an app is opened.

No FRCTL backend, database or tracking service is required. Anonymous browsing works immediately. Optional GitHub authorization raises API limits, while an encrypted app-private preference stores access locally. If a GitHub release is unavailable, FRCTL can route to a Hugging Face dataset mirror.

The interface follows the device language automatically and currently includes English, Russian and Simplified Chinese. Catalog responses are cached on-device for faster startup and graceful operation during API limits or temporary network failures.

FRCTL does not review or sign third-party APK files. Users should verify publishers, signatures and hashes before installation.
