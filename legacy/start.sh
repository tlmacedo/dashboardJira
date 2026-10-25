#!/usr/bin/env bash
# Início rápido (Linux/macOS): cria o ambiente, instala dependências e sobe o painel.
#   ./start.sh          -> usa o modo definido em .env (sem .env: demonstração com dados fictícios)
#   ./start.sh demo     -> força a demonstração
set -euo pipefail
cd "$(dirname "$0")"
PY=${PYTHON:-python3}
[ -d .venv ] || "$PY" -m venv .venv
# shellcheck disable=SC1091
source .venv/bin/activate
pip install -q -r backend/requirements.txt
cd backend
export PYTHONPATH="$PWD"
if [ "${1:-}" = "demo" ] || [ ! -f ../.env ]; then
  echo ">> Modo DEMONSTRAÇÃO (dados fictícios). Para usar o seu Jira: python -m jiradash init"
  export JIRADASH_MODE=demo
fi
echo ">> Abrindo em http://localhost:8000"
exec python -m uvicorn jiradash.main:app --host 0.0.0.0 --port 8000
