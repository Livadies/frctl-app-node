# FRCTL for Windows 0.4

FRCTL for Windows is the native desktop shell of the FRCTL platform. One window starts a local loopback node and provides:

- a refreshable GitHub Android-app catalog;
- a Hugging Face model catalog;
- categories and search;
- local OpenSSH/PuTTY, RustDesk, Docker, and Browser Workspace launchers;
- fixed SSH workflows and SHA-256/HMAC audit receipts.

Public catalog services never receive private keys. Connections and native confirmation dialogs run only in the local process bound to `127.0.0.1`.

## Install a prepared build

Extract `FRCTL-Windows-0.4.0.zip`, then run:

```powershell
.\install-windows.ps1
```

Open **FRCTL** from the Start menu. End users do not need Python. Microsoft Edge WebView2 Runtime is required and is already included with current Windows 10 and Windows 11 installations.

## Build from source

```powershell
.\build-windows.ps1
```

The script creates an isolated build environment, installs pinned pywebview and PyInstaller dependencies, and produces the standalone executable under `dist`.

## Security status

The Windows build is a product alpha. It listens only on loopback, uses explicit allowlists and user confirmation, and does not bypass SSH or RustDesk authentication. It is not an FSTEC-certified security product, and the EXE is not yet signed with a commercial Authenticode certificate.
