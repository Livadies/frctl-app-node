FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PORT=7860

WORKDIR /opt/frctl
COPY frctl_hub ./frctl_hub
COPY static ./static

RUN useradd --create-home --uid 1000 frctl \
    && chown -R frctl:frctl /opt/frctl

USER frctl
EXPOSE 7860
CMD ["python", "-m", "frctl_hub.server"]

