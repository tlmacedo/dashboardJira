#!/bin/sh
# Sobe o painel em Linux/macOS, sem Docker nem servidor de banco.
# Requisitos: JDK 21 e Node 22 com pnpm. Uso (a partir de app/):  sh iniciar.sh [--sem-build-do-front]
# Variáveis: copie .env.example para .env e preencha. O banco é o arquivo data/painel.db.
set -eu
cd "$(dirname "$0")"

if [ "${1:-}" != "--sem-build-do-front" ] || [ ! -f frontend/dist/index.html ]; then
  echo "== Frontend: instalando dependências e gerando o build =="
  (cd frontend && pnpm install --frozen-lockfile && pnpm build)
fi

echo "== Backend: iniciando em http://127.0.0.1:8080 (Ctrl+C para parar) =="
cd backend
exec ./gradlew run
