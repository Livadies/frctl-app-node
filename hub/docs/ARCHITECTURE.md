# FRCTL Hub architecture

## Product boundary

FRCTL Hub is split into four trust zones:

1. **Browser shell** — Chromium/Gecko-compatible UI, normalized GitHub/Hugging Face catalog, local sequence model, consent control.
2. **Public demo control plane** — catalog, validation, dry-run planning, redacted counters; no secrets and no remote execution.
3. **Windows shell and local node (implemented as a separate trust boundary)** — the only components allowed to invoke installed OpenSSH/PuTTY/RustDesk/container runtimes after interactive approval.
4. **Managed target** — a user-owned host enrolled with short-lived workload identity and an explicit organization policy.

GitHub provides source control, public CI, provenance and release distribution. The free Hugging Face deployment is a Static Space: validation, planning, learning and optional counters remain in browser storage. The Docker control plane is reserved for local/self-hosted use or a future paid Space and remains outside the credential boundary.

## Data flows

- Connector selection and the local next-action model remain in browser storage.
- Public catalog data is fetched only from fixed GitHub and Hugging Face HTTPS endpoints, normalized to the same bounded schema and cached for five minutes.
- A public workflow request contains connector, action and a target without credentials.
- A local SSH workflow contains only a built-in workflow id, allowlisted target, pinned user and an allowlisted local PPK path. The target host-key fingerprint is pinned locally.
- The public service returns a non-executable plan.
- Telemetry is sent only after consent and is reduced to an allowlisted event name and bounded properties.
- Audit receipts use a SHA-256 hash chain. This detects local tampering but is not distributed consensus, cryptocurrency, or proof of work.

The Node binds only to `127.0.0.1`. Its browser API requires a per-process HttpOnly session cookie, exact same-origin requests and a dedicated request header. It accepts only structured connector fields, validates targets against a local allowlist and never accepts a password, token or arbitrary command. Every launch is repeated through policy validation and a native confirmation dialog; the browser-side plan is not an authorization token.

## Security invariants in MVP

- Remote execution is absent from the public Hub, not hidden behind a UI flag.
- No OAuth client secret, SSH key, access token or cookie is accepted.
- Same-origin API, restrictive CSP, bounded request bodies and read-only container filesystem.
- Kubernetes service-account token is not mounted; Linux capabilities are dropped.
- FSTEC certification is explicitly reported as `not-certified`.

## Local Node security invariants

- loopback-only listener and Host/Origin checks;
- deny-by-default connector/action/field allowlists;
- no credentials or arbitrary shell input;
- OpenSSH host-key confirmation remains enabled;
- RustDesk receives only a peer ID and performs its own authentication;
- Docker profile has no network, host mounts or capabilities and has CPU/RAM/PID limits;
- audit stores target and command hashes rather than clear-text values;
- no unattended startup and no launch without a native user confirmation.
- automatic SSH execution is limited to built-in workflows; arbitrary commands, unpinned host keys, users and identities are rejected;
- workflow output is bounded and sanitized, while the audit stores only its SHA-256 evidence hash.
