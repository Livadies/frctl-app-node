# FRCTL — open apps and remote-work environments

FRCTL is an open marketplace shell for Android applications, Hugging Face models, local SSH/PuTTY and RustDesk connections, isolated workspaces, and controlled workflows. Public catalog browsing is free and does not execute anything on the user's computer. Sensitive actions are available only through the local FRCTL Node and require user confirmation.

## What works today

- **Android 1.5.0:** a live GitHub/Hugging Face catalog, Room offline cache, favorites, update checks, five interface languages, private on-device recommendations, and local AI;
- **FRCTL for Windows 0.4.0:** the same marketplace plus local SSH/PuTTY, RustDesk, Docker Sandbox, and Browser Workspace launchers;
- **FRCTL Node 0.6.0:** allowlists, pinned SSH host keys, workflows, native confirmation, HMAC audit chains, server-sent audit updates, and verified export;
- **Hugging Face Space:** a free browser showcase with live cards, filters, safe dry-runs, and no privileged execution;
- local Python Hub, Docker Compose, and restricted Kubernetes manifests;
- Android, Hub, and Node regression tests plus APK builds in GitHub Actions.

## Local AI on Android

FRCTL can download a small, explicitly allowlisted model from `huggingface.co` and run it through the MediaPipe LLM Inference API directly on the phone.

- Model downloads support progress, cancellation, resume, storage/RAM checks, and mandatory SHA-256 verification.
- After the model is downloaded, chat and README summaries work without a network connection, including in airplane mode.
- Chat history stays in memory and is cleared when the chat screen closes.
- Prompts, summaries, and interest history are never sent to FRCTL, GitHub, or Hugging Face.
- The default setting permits model downloads only on an unmetered network.
- Device requirements depend on the selected model; the smallest catalog entry is intended for lower-memory devices, while larger models require several gigabytes of free RAM and storage.

Open the Android home screen and select **Run AI on your phone**. Models marked **Runs on-device** are supported. Once a model is ready, the hero card becomes a direct shortcut to the private offline chat.

## Try the public showcase

Open the [public FRCTL Hub](https://livadies-frctl-hub.static.hf.space/). The catalog should show GitHub and Hugging Face cards with a `LIVE CATALOG` status. Select a category or search for an app or model. The connection form produces a safe dry-run only; a target containing embedded credentials such as `root:password@server.example` is rejected.

## Build Android

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio1\jbr"
.\gradlew.bat testDebugUnitTest assembleDebug
```

Release signing values are read only from `FRCTL_KEYSTORE_PATH`, `FRCTL_KEYSTORE_PASSWORD`, `FRCTL_KEY_ALIAS`, and `FRCTL_KEY_PASSWORD`. Signing secrets are not stored in this repository.

## Build Windows

```powershell
cd hub\windows
.\build-windows.ps1
```

The distributable archive is written to `hub/windows/dist/FRCTL-Windows-0.4.0.zip`. See [the Windows guide](hub/windows/README.md).

## Run FRCTL Node

```powershell
cd hub\node
.\install.ps1
.\run-node.cmd
```

The panel listens locally at `http://127.0.0.1:7878/`. It does not accept passwords, tokens, or arbitrary shell commands through its API. See [the Node guide](hub/node/README.md).

## Component boundaries

```text
GitHub API ─┐
            ├─ normalized catalog ─┬─ Android 1.5
HF API ─────┘                      ├─ Windows 0.4 / Node 0.6
                                   └─ public Hugging Face Space

Public Space ── browsing and dry-run only
Local Node ──── user confirmation ── allowlisted client/server ── local audit
```

GitHub and Hugging Face provide the public catalog within their free-service limits. GitHub Actions can test and build a public project, but it is not a permanently running server. Real remote sessions, containers, and local-model inference use the user's computer, phone, or server.

## Current limitations

- This is a working product alpha, not a certified information-security product.
- FSTEC compliance requires a threat model, formal documentation, a secure development lifecycle, and evaluation by an accredited laboratory.
- P2P resource pooling, settlement, a blockchain registry, and general OAuth2 infrastructure are not implemented yet.
- The Windows executable is not signed with a commercial Authenticode certificate.
- Third-party apps and models still require license, publisher, signature, and checksum review.

## Documentation and recovery

- [Full project recovery guide](RECOVERY_RU.md)
- [Architecture and trust boundaries](hub/docs/ARCHITECTURE.md)
- [Investor demo](hub/docs/INVESTOR_DEMO_RU.md)
- [FSTEC certification gap](hub/docs/FSTEC_GAP.md)
- [Non-profit organizational model](hub/docs/NKO_MODEL.md)

License: [LICENSE](LICENSE).
