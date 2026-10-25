# Painel de Squads: imagem em dois estágios de build + imagem final enxuta.
# Contexto de build: a pasta app/ (docker compose já usa esse contexto).

# --- Estágio 1: build do frontend (pnpm + Vite) ---
FROM node:22-alpine AS frontend
WORKDIR /build
RUN corepack enable && corepack prepare pnpm@9.12.3 --activate
COPY frontend/package.json frontend/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY frontend/ ./
RUN pnpm build

# --- Estágio 2: build do backend (Gradle, JDK 21) ---
FROM gradle:9.2.1-jdk21 AS backend
WORKDIR /build
COPY backend/ ./
RUN gradle --no-daemon installDist -x test

# --- Imagem final: somente JRE 21, sem segredos e sem root ---
FROM eclipse-temurin:21-jre
# Usuário sem privilégios; nenhuma senha ou token é gravado na imagem (tudo vem do ambiente).
RUN groupadd --system --gid 10001 painel \
 && useradd --system --uid 10001 --gid painel --no-create-home --shell /usr/sbin/nologin painel
WORKDIR /app
COPY --from=backend /build/build/install/painel/ /app/
COPY --from=frontend /build/dist/ /app/static/
# Configuração: entram só os modelos (.exemplo/.demo) e cards/, porque o .dockerignore filtra os arquivos
# reais com pessoas (papeis, equipes, ausencias, squads). Em produção os YAML reais entram por volume somente leitura.
COPY config/ /app/config/
ENV PAINEL_STATIC_DIR=/app/static \
    PAINEL_CONFIG_DIR=/app/config \
    JIRA_LIVE_ENABLED=false \
    DASHBOARD_DEV_MODE=true
USER painel
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=20s --retries=5 \
  CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /api/health HTTP/1.0\\r\\n\\r\\n' >&3 && grep -q '\"ok\":true' <&3"]
ENTRYPOINT ["/app/bin/painel"]
