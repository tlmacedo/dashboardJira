#!/bin/sh
# Garante que o Docker e os serviços do projeto estejam ativos.
# 1) Se o daemon do Docker não responde, abre o Docker Desktop em segundo plano e espera.
# 2) Se existir app/docker-compose.yml, sobe o serviço de banco (postgres) em segundo plano.
# Uso: sh .claude/hooks/ensure-docker.sh [--espera SEGUNDOS]   (padrão: 120)
# Saída: mensagens curtas em pt-BR; código 0 = Docker ativo (o postgres do compose só sobe com POSTGRES_PASSWORD
# definida; sem ela, avisa e segue com 0), 1 = Docker não ficou pronto a tempo ou o postgres falhou com a senha definida.
espera=120
[ "$1" = "--espera" ] && espera="${2:-120}"
raiz=$(cd "$(dirname "$0")/../.." && pwd)

pronto() { docker info >/dev/null 2>&1; }

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker não está instalado."; exit 1
fi

if pronto; then
  echo "Docker já está ativo."
else
  echo "Docker parado; iniciando em segundo plano..."
  case "$(uname -s)" in
    Darwin) open -ga Docker ;;                         # abre sem trazer a janela para frente
    Linux)  (systemctl --user start docker-desktop 2>/dev/null || sudo -n systemctl start docker 2>/dev/null) ;;
  esac
  i=0
  while [ "$i" -lt "$espera" ]; do
    pronto && break
    sleep 2; i=$((i+2))
  done
  if pronto; then echo "Docker ativo após ${i}s."; else echo "Docker não respondeu em ${espera}s."; exit 1; fi
fi

# serviços do projeto (banco), só quando o compose já existe
compose="$raiz/app/docker-compose.yml"
if [ -f "$compose" ]; then
  if [ -z "${POSTGRES_PASSWORD:-}" ]; then
    # Sem a senha o compose não sobe; o Docker ativo basta (os testes usam Testcontainers).
    echo "postgres do compose não iniciado: defina POSTGRES_PASSWORD se precisar dele; os testes usam Testcontainers."
  elif docker compose -f "$compose" up -d postgres >/dev/null 2>&1; then
    echo "Serviço postgres do projeto ativo."
  else
    echo "Não foi possível subir o postgres do projeto (confira POSTGRES_PASSWORD no ambiente)."; exit 1
  fi
fi
exit 0
