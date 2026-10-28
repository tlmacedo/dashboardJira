#!/bin/sh
# Atalho para os subcomandos de operação do painel: validar-config, backup-agora, verificar-restauracao.
# Localiza o pacote em PAINEL_HOME (padrão: app/backend/build/install/painel, gerado por ./gradlew installDist),
# entra em app/ (para que config/, data/ e o arquivo de variáveis relativos funcionem) e repassa os argumentos
# e o código de saída. Não usa contêiner nem rede.
set -eu
AQUI="$(cd "$(dirname "$0")" && pwd)"
APP="$(cd "$AQUI/.." && pwd)"
PAINEL_HOME="${PAINEL_HOME:-$APP/backend/build/install/painel}"
if [ ! -x "$PAINEL_HOME/bin/painel" ]; then
    echo "Pacote do painel não encontrado em PAINEL_HOME (rode: cd app/backend && ./gradlew installDist)." >&2
    exit 2
fi
cd "$APP"
exec "$PAINEL_HOME/bin/painel" "$@"
