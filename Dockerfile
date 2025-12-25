FROM debian:bookworm-slim
LABEL org.opencontainers.image.title="ARD Audiothek RSS Adapter" \
      org.opencontainers.image.description="Slim Ktor server that exposes ARD Audiothek shows as RSS feeds" \
      org.opencontainers.image.source="https://github.com/ARD-Audiothek/ARD-Audiothek-RSS-Adapter"

ENV APP_HOME=/opt/ard-audiothek-rss-adapter \
    APP_OPTS=""

WORKDIR ${APP_HOME}

RUN useradd --system --home "${APP_HOME}" --shell /usr/sbin/nologin app \
    && apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl \
    && rm -rf /var/lib/apt/lists/*

COPY target/ard-audiothek-rss-adapter app

RUN chown -R app:app ${APP_HOME} && chmod 500 ${APP_HOME}/app

EXPOSE 8411
USER app

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8411/health >/dev/null || exit 1

ENTRYPOINT ["/bin/sh", "-c", "exec ${APP_HOME}/app $APP_OPTS"]
