# FRCTL Hub MVP

FRCTL Hub — безопасный первый срез будущей оболочки для удалённых рабочих мест, OSS‑коннекторов, песочниц и ИИ‑workflow.

**Рабочая публичная демоверсия:** https://livadies-frctl-hub.static.hf.space/

## Состав модуля

| Каталог | Назначение |
|---|---|
| `hf-static/` | Бесплатная автономная демоверсия Hugging Face Static Space |
| `space/frctl_hub/` | Локальный Python control plane и policy engine |
| `space/static/` | Веб‑оболочка для локального control plane |
| `space/tests/` | Unit- и HTTP‑тесты |
| `node/` | Рабочий локальный Node для разрешённых запусков на Windows |
| `windows/` | Автономная нативная Windows‑оболочка и сборка EXE |
| `infra/` | Docker Compose и Kubernetes |
| `docs/` | Архитектура, ФСТЭК, НКО и инвесторская проверка |

## Возможности MVP

- Chromium/Gecko‑совместимый интерфейс;
- каталог SSH/PuTTY, RustDesk, Browser Workspace и Ephemeral Sandbox;
- только `dry-run`: публичная часть не выполняет удалённые команды;
- запрет credentials, query string и неподдерживаемых схем в target;
- локальный микроагент следующего действия;
- opt‑in телеметрия со строгим allowlist;
- SHA‑256 hash chain аудиторских квитанций;
- read-only контейнер, dropped capabilities и отключённый Kubernetes service-account token.
- локальный FRCTL Node с SSH/PuTTY, RustDesk, Docker Sandbox и Browser Workspace.

## Запуск локального FRCTL Node

```powershell
cd hub\node
.\install.ps1
.\run-node.cmd
```

Откройте http://127.0.0.1:7878. Подробная инструкция и модель безопасности: [node/README.md](node/README.md).

## Запуск локального control plane

```powershell
cd hub\space
python -m frctl_hub.server
```

Откройте http://127.0.0.1:7860.

## Автотесты

```powershell
cd hub\space
python -m unittest discover -s tests -v
```

Для control plane ожидается `Ran 8 tests ... OK`. Для Node:

```powershell
cd hub\node
python -m unittest discover -s tests -v
```

Ожидаемый результат Node: `Ran 28 tests ... OK`.

## Контейнер

```powershell
docker compose -f hub\infra\docker-compose.yml up --build
```

Контейнер слушает только `127.0.0.1:7860`, запускается без Linux capabilities и с read-only root filesystem.

## Разделение бесплатной инфраструктуры

- Hugging Face Static Space — публичная браузерная демонстрация без сервера и секретов.
- GitHub Actions — тесты, сборка контейнера, SBOM и provenance для публичного репозитория.
- GitHub Releases — подписанные версии и манифесты.
- Локальный FRCTL Node — будущий единственный компонент с правом запуска PuTTY/RustDesk и контейнеров.

Платный Docker Space не нужен для проверки MVP. Бесплатный Static Space обеспечивает интерактивную демонстрацию, а локальный control plane проверяется тестами и запуском на машине владельца.

## Что отправить инвестору

Отправьте два URL:

1. Демо: https://livadies-frctl-hub.static.hf.space/
2. Инструкция: https://huggingface.co/spaces/livadies/frctl-hub/blob/main/INVESTOR_DEMO_RU.md

Подробный локальный документ: [docs/INVESTOR_DEMO_RU.md](docs/INVESTOR_DEMO_RU.md).
