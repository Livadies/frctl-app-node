---
title: FRCTL Hub
emoji: 🧩
colorFrom: green
colorTo: blue
sdk: static
app_file: index.html
fullWidth: true
header: mini
license: apache-2.0
short_description: Open local-first shell for apps and remote environments
---

# FRCTL Hub 0.6 — public demo

FRCTL Hub is an open interface for discovering apps and AI models, safely planning connections, presenting isolated workspaces, and demonstrating agent workflows.

## What to verify

1. Select Russian, English, Chinese, German, or Spanish in the upper-right corner.
2. Wait for `LIVE CATALOG`; the page loads 24 GitHub projects and 24 Hugging Face models.
3. Change categories or search for a model.
4. Select `RustDesk`, enter `10.0.0.12:21118`, and create a dry-run plan.
5. Enter `root:password@server.example`; the target must be rejected because embedded credentials are forbidden.
6. After the first load, the catalog remains in a five-minute browser cache and the planner itself can operate offline.

The public Space receives no tokens, passwords, planner addresses, or local history. It does not execute remote commands. Real execution is available only through a separately installed FRCTL Node after explicit user confirmation.

Detailed scenario: [INVESTOR_DEMO_RU.md](INVESTOR_DEMO_RU.md).

FRCTL is an engineering product alpha, not a certified information-security product.
