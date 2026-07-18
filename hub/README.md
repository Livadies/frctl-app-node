# FRCTL Hub MVP

FRCTL Hub is the safe first product slice of an open shell for remote workspaces, OSS connectors, sandboxes, and AI workflows.

**Live public demo:** https://livadies-frctl-hub.static.hf.space/

## Modules

| Directory | Purpose |
|---|---|
| `hf-static/` | Free, self-contained Hugging Face Static Space |
| `space/frctl_hub/` | Local Python control plane and policy engine |
| `space/static/` | Web shell for the local control plane |
| `space/tests/` | Unit and HTTP tests |
| `node/` | Local Windows node for allowlisted launches |
| `windows/` | Native Windows shell and standalone EXE build |
| `infra/` | Docker Compose and Kubernetes manifests |
| `docs/` | Architecture, FSTEC gap, non-profit model, and investor demo |

## MVP capabilities

- Chromium/Gecko-compatible web interface;
- marketplace for SSH/PuTTY, RustDesk, Browser Workspace, and Ephemeral Sandbox;
- public `dry-run` planning that never executes remote commands;
- rejection of embedded credentials, query strings, and unsupported target schemes;
- a small local next-action helper;
- an opt-in, strictly allowlisted telemetry prototype in the public control-plane demo;
- SHA-256 receipt chains in the demo and HMAC-SHA-256 audit chains in FRCTL Node;
- read-only containers with dropped capabilities and no Kubernetes service-account token;
- local FRCTL Node support for SSH/PuTTY, RustDesk, Docker Sandbox, and Browser Workspace.

The Android 1.5 application has a stricter privacy boundary: it contains no telemetry and keeps local-AI prompts and personalization data exclusively on the phone.

## Run the local Node

```powershell
cd hub\node
.\install.ps1
.\run-node.cmd
```

Open `http://127.0.0.1:7878/`. See [the Node security and usage guide](node/README.md).

## Run the local control plane

```powershell
cd hub\space
python -m frctl_hub.server
```

Open `http://127.0.0.1:7860/`.

## Tests

```powershell
cd hub\space
python -m unittest discover -s tests -v

cd ..\node
python -m unittest discover -s tests -v
```

## Restricted container

```powershell
docker compose -f hub\infra\docker-compose.yml up --build
```

The container listens only on `127.0.0.1:7860`, has no Linux capabilities, and uses a read-only root filesystem.

## Free-service boundaries

- Hugging Face Static Space hosts the public browser demo without a server or secrets.
- GitHub Actions tests the project and builds artifacts, SBOMs, and provenance; it is not a 24/7 server.
- GitHub Releases can distribute versioned artifacts and manifests.
- The local FRCTL Node is the only component allowed to launch PuTTY, RustDesk, or containers, and each launch requires confirmation.

A paid Docker Space is not required to evaluate this MVP. Real sessions and workloads use the user's own computer or server.

## Investor handoff

Share these two links:

1. Demo: https://livadies-frctl-hub.static.hf.space/
2. Verification scenario: https://huggingface.co/spaces/livadies/frctl-hub/blob/main/INVESTOR_DEMO_RU.md

The repository also contains [the detailed local investor demo](docs/INVESTOR_DEMO_RU.md).
