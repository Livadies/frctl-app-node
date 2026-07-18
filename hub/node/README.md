# FRCTL Node 0.6 Product Alpha

FRCTL Node is the local trusted component of FRCTL Hub. It launches only pre-approved clients on the user's computer, listens exclusively on `127.0.0.1`, displays a native confirmation dialog before every launch, and stores a local HMAC-SHA-256 tamper-evident audit log.

## Working capabilities

- Windows marketplace for GitHub Android apps and Hugging Face models, with categories, search, five-minute refresh, and offline cache;
- OpenSSH and PuTTY sessions to allowlisted hosts;
- RustDesk connections by ID without putting a password on the command line;
- disposable Docker sandboxes without networking, host mounts, or Linux capabilities;
- Browser Workspace for allowlisted local or corporate URLs;
- local status panel and exact launch-plan preview;
- rejection of credentials, query strings, arbitrary commands, and unknown parameters;
- mandatory native confirmation for every launch;
- a local audit chain that does not store the target or command in plaintext;
- fixed, allowlisted SSH workflows with pinned host-key fingerprints;
- returned remote-system output and a hashed result receipt.

Node is not an unattended remote-administration server and does not bypass authentication. OpenSSH, PuTTY, or RustDesk performs the actual sign-in. Sensitive RustDesk deployments should use a self-hosted RustDesk server.

## Requirements

- Windows 10 or Windows 11;
- Python 3.11 or newer;
- at least one available client: built-in OpenSSH, PuTTY, RustDesk, or Docker Desktop.

A portable `putty.exe` is also discovered on the normal or OneDrive Desktop. A PPK file can be selected only after its exact path is added to `allowed_identity_files`. Node passes the path to PuTTY but does not read or store the key contents; previews redact the full path.

Automated workflows use official Plink 0.84. The installer downloads the pinned version and verifies its SHA-256 and Simon Tatham digital signature. For offline installation, use `.\install.ps1 -SkipPlink` and configure a trusted `plink.exe` manually.

## Built-in SSH workflows

- `diagnostics` is read-only and returns the user, hostname, OS, architecture, time, uptime, and disk information;
- `proof-file` creates only `/tmp/frctl-proof.txt` with owner permissions and reads it back.

The API cannot accept an arbitrary command. Every server requires a pinned `host:port → fingerprint` record in `ssh_host_keys`, and a PPK path must be present in `allowed_identity_files`. Node rebuilds the plan and shows a separate system confirmation before execution.

No third-party Python packages are required at runtime.

## Install and run

From PowerShell in this directory:

```powershell
.\install.ps1
```

The installer validates Python, creates a configuration, and adds an **FRCTL Node** Start-menu shortcut. Autostart is not enabled.

Run without installation:

```powershell
.\run-node.cmd
```

Open `http://127.0.0.1:7878/`.

Remove the shortcut:

```powershell
.\uninstall.ps1
```

Remove the shortcut, local settings, and audit data:

```powershell
.\uninstall.ps1 -RemoveData
```

## Configure the allowlist

The first launch creates:

```text
%LOCALAPPDATA%\FRCTL\node-config.json
```

Use `node-config.example.json` as a reference. Exact hostnames, `*.local`, and private CIDR ranges are supported. Restart Node after a change. The listen address cannot be exposed externally; configuration rejects anything except loopback.

The safer way to add an SSH host and PPK is the configuration wizard. Verify the fingerprint with the server administrator over a separate trusted channel first:

```powershell
.\configure-ssh-target.ps1 `
  -Target server.example `
  -Port 22 `
  -User operator `
  -IdentityFile "C:\Keys\server.ppk" `
  -HostKey "ssh-ed25519 255 SHA256:VERIFIED_FINGERPRINT"
```

The wizard does not copy or read the key. It stores only the PPK path, pinned user, and verified fingerprint in the local allowlist.

## Verify the build

```powershell
python -m unittest discover -s tests -v
node --check static\app.js
```

Then verify the interface:

1. select SSH and target `localhost`;
2. select **Preview plan** and confirm that no password appears;
3. select **Launch**, then cancel the native dialog; the audit log should show `denied`;
4. confirm that `example.com` is rejected until it is allowlisted.

Launch tests use a fake launcher and never open real connections.

Build the portable archive:

```powershell
.\build-release.ps1
```

The result is written to `dist\FRCTL-Node-0.6.0-win.zip`.

## Local files and security boundary

| Component | Location |
|---|---|
| Configuration | `%LOCALAPPDATA%\FRCTL\node-config.json` |
| Audit log | `%LOCALAPPDATA%\FRCTL\node-audit.jsonl` |
| Local audit HMAC key | `%LOCALAPPDATA%\FRCTL\audit.key` |
| Marketplace cache | `%LOCALAPPDATA%\FRCTL\marketplace-cache.json` |
| Local panel | `http://127.0.0.1:7878/` |

The API uses a random per-process cookie, strict `Origin` checks, a dedicated request header, no CORS, and bounded JSON bodies. These controls reduce the risk of a malicious web page calling Node, but they do not replace Windows account and endpoint security.

The panel receives updates from same-origin `GET /api/audit/stream`. `GET /api/audit` performs full HMAC-chain verification, and `GET /api/audit/export` returns JSONL only when the log is intact. All routes require the current process cookie. No more than four audit streams can be open concurrently; excess clients receive HTTP 429.

## Local-malware boundary

Loopback, the process cookie, `Origin`, and `X-FRCTL-Request` do not protect against software already running under the same Windows account. Such a process may be able to read user files, inspect memory, or control windows.

The final safety boundary is the separate native confirmation dialog. If `tkinter`/Tcl/Tk is missing or cannot show the dialog, Node fails closed and rejects every launch. `/api/status` reports `native_confirmation: false` and `confirmation_fail_closed: true`. Install Python with Tcl/Tk or use the packaged Windows build; do not bypass confirmation.

The audit log uses a local 256-bit HMAC-SHA-256 key created with the strongest available user-only permissions. This detects log rewriting when the key remains protected, but cannot defeat malware that already has access to the user's files.

Verify the chain from PowerShell:

```powershell
python -m frctl_node --verify-audit
```

Exit code `0` means the chain is intact. Node refuses to append new records to a damaged chain.

## Compliance status

FRCTL Node is a working engineering build, not a certified information-security product. Any FSTEC compliance claim requires a formal threat model, specifications and operational documentation, secure-development and vulnerability-management processes, an accredited test laboratory, and official conformity assessment.
