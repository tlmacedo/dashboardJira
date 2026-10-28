#!/bin/sh
# Verifica o pacote de produção montado por montar-pacote.sh. Sai com código diferente de 0 e uma mensagem curta no
# primeiro problema.
# Uso (a partir de app/):  sh scripts/verificar-pacote.sh [pasta do pacote]   (padrão: pacote/painel)
#
#  1. Conteúdo: nenhum YAML real, arquivo de variáveis (além do modelo), banco, trava, chave/certificado nem pasta legacy/.
#  2. Estrutura: bin/painel, bin/painel.bat, o jar do painel em lib/ e frontend/dist/index.html.
#  3. Fumaça (usa a porta 8080 e pastas temporárias; aborta com aviso se a porta já estiver em uso):
#     a) modo produção SEM OIDC: o processo termina com código diferente de 0 e a saída cita OIDC_ISSUER, sem nenhum
#        valor de segredo;
#     b) modo desenvolvimento: GET /api/health responde {"ok":true}; o processo é encerrado no fim.
# O arquivo de variáveis dos testes NÃO tem a chave do acesso ao Jira (linha ausente = desligado) e o script nunca a liga.
set -eu

AQUI="$(cd "$(dirname "$0")" && pwd)"
APP="$(cd "$AQUI/.." && pwd)"
PACOTE="${1:-$APP/pacote/painel}"
PACOTE="$(cd "$PACOTE" 2>/dev/null && pwd)" || { echo "FALHA: pacote não encontrado (rode: sh scripts/montar-pacote.sh)" >&2; exit 1; }

falhar() { echo "FALHA: $*" >&2; exit 1; }

# ---------------------------------------------------------------- 1. conteúdo
echo "== Conteúdo =="
proibido="$(find "$PACOTE" \( -iname '.env' -o -iname '.env.*' -o -iname '*.env' \) ! -iname '.env.example' -print -quit)"
[ -z "$proibido" ] || falhar "arquivo de variáveis dentro do pacote: ${proibido#"$PACOTE"/}"
proibido="$(find "$PACOTE" \( -name '*.db' -o -name '*.db-wal' -o -name '*.db-shm' -o -name '*.lock' -o -name '*.rotacao' \) -print -quit)"
[ -z "$proibido" ] || falhar "arquivo de banco ou de trava dentro do pacote: ${proibido#"$PACOTE"/}"
proibido="$(find "$PACOTE" \( -iname '*.pem' -o -iname '*.key' -o -iname '*.p12' -o -iname '*.pfx' -o -iname '*.jks' -o -iname '*.keystore' \) -print -quit)"
[ -z "$proibido" ] || falhar "chave ou certificado dentro do pacote: ${proibido#"$PACOTE"/}"
proibido="$(find "$PACOTE" -name legacy -print -quit)"
[ -z "$proibido" ] || falhar "pasta legacy/ dentro do pacote"
proibido="$(find "$PACOTE" \( -iname 'Dockerfile*' -o -iname 'docker-compose*' -o -iname 'compose*.yml' -o -iname 'compose*.yaml' -o -name '.dockerignore' \) -print -quit)"
[ -z "$proibido" ] || falhar "artefato de contêiner dentro do pacote: ${proibido#"$PACOTE"/}"
# config/: só modelos (*.exemplo.yaml, *.demo.yaml) e as fichas de cards/ (*.card.yaml). Qualquer outro arquivo é configuração real.
if [ -d "$PACOTE/config" ]; then
    proibido="$(find "$PACOTE/config" -type f ! -name '*.exemplo.yaml' ! -name '*.demo.yaml' ! -path "$PACOTE/config/cards/*.card.yaml" -print -quit)"
    [ -z "$proibido" ] || falhar "configuração real (não é modelo) dentro do pacote: ${proibido#"$PACOTE"/}"
fi

# ---------------------------------------------------------------- 2. estrutura
echo "== Estrutura =="
[ -f "$PACOTE/bin/painel" ] || falhar "falta bin/painel"
[ -f "$PACOTE/bin/painel.bat" ] || falhar "falta bin/painel.bat"
ls "$PACOTE"/lib/painel-*.jar >/dev/null 2>&1 || falhar "falta o jar do painel em lib/"
[ -f "$PACOTE/frontend/dist/index.html" ] || falhar "falta frontend/dist/index.html"

# ---------------------------------------------------------------- 3. fumaça
echo "== Fumaça =="
if command -v curl >/dev/null 2>&1; then
    consulta() { curl -s --max-time 2 "http://127.0.0.1:8080/api/health" 2>/dev/null || true; }
elif command -v wget >/dev/null 2>&1; then
    consulta() { wget -q -T 2 -O - "http://127.0.0.1:8080/api/health" 2>/dev/null || true; }
else
    falhar "precisa de curl ou wget para a verificação"
fi
if [ -n "$(consulta)" ]; then
    echo "AVISO: a porta 8080 já está em uso; encerre o painel que está rodando e tente de novo." >&2
    exit 3
fi

TMP="$(mktemp -d)"
PID=""
limpar() {
    if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then
        kill "$PID" 2>/dev/null || true
        sleep 1
        kill -9 "$PID" 2>/dev/null || true
    fi
    rm -rf "$TMP"
}
trap limpar EXIT INT TERM

# Valores FICTÍCIOS só para provar que a recusa não os mostra: 48 bytes (base64) e um texto qualquer.
SEGREDO_DE_TESTE="$(head -c 48 /dev/zero | tr '\0' 'q' | base64 | tr -d '\n')"
CLIENTE_DE_TESTE="cliente-ficticio-de-teste-0001"
mkdir -p "$TMP/config" "$TMP/dados"

# Sobe o painel com o ambiente LIMPO (nada herdado do terminal: nenhuma variável do Jira, nenhuma do painel).
subir() { # $1 = arquivo de variáveis, $2 = arquivo de saída
    # `exec` mantém o mesmo processo até o java: o PID guardado é o que se encerra no fim.
    (
        cd "$PACOTE"
        exec env -i PATH="$PATH" HOME="${HOME:-/tmp}" ${JAVA_HOME:+JAVA_HOME="$JAVA_HOME"} \
            PAINEL_ENV_FILE="$1" DATABASE_PATH="$TMP/dados/painel.db" PAINEL_CONFIG_DIR="$2" \
            PAINEL_STATIC_DIR="$PACOTE/frontend/dist" ./bin/painel
    ) > "$3" 2>&1 &
    PID=$!
}

# a) produção sem OIDC_ISSUER: tem de terminar com erro citando só o NOME da variável
cat > "$TMP/producao.env" <<EOF
DASHBOARD_DEV_MODE=false
PAINEL_METRICAS_PORTA=0
SESSION_SECRET=$SEGREDO_DE_TESTE
OIDC_CLIENT_ID=painel-de-teste
OIDC_CLIENT_SECRET=$CLIENTE_DE_TESTE
OIDC_REDIRECT_URI=https://painel.exemplo.invalid/auth/callback
EOF
subir "$TMP/producao.env" "$TMP/config" "$TMP/saida-producao.txt"
espera=0
while kill -0 "$PID" 2>/dev/null; do
    espera=$((espera + 1))
    [ "$espera" -le 90 ] || falhar "produção sem OIDC_ISSUER continuou rodando depois de 90 s (deveria recusar a subida)"
    sleep 1
done
if wait "$PID"; then codigo=0; else codigo=$?; fi
PID=""
[ "$codigo" -ne 0 ] || falhar "produção sem OIDC_ISSUER terminou com código 0 (deveria recusar a subida)"
grep -q "OIDC_ISSUER" "$TMP/saida-producao.txt" || falhar "a recusa não cita OIDC_ISSUER; saída em $TMP/saida-producao.txt"
if grep -q -e "$SEGREDO_DE_TESTE" -e "$CLIENTE_DE_TESTE" "$TMP/saida-producao.txt"; then
    falhar "a saída da recusa mostrou um valor de segredo"
fi
echo "ok: produção sem OIDC_ISSUER recusa subir (código $codigo)"

# b) desenvolvimento: /api/health responde (modelos de configuração do próprio pacote)
cat > "$TMP/dev.env" <<EOF
DASHBOARD_DEV_MODE=true
PAINEL_METRICAS_PORTA=0
EOF
subir "$TMP/dev.env" "$PACOTE/config" "$TMP/saida-dev.txt"
resposta=""
espera=0
while [ "$espera" -lt 90 ]; do
    resposta="$(consulta)"
    [ -z "$resposta" ] || break
    kill -0 "$PID" 2>/dev/null || { cat "$TMP/saida-dev.txt" >&2; falhar "o painel em modo desenvolvimento terminou antes de responder"; }
    espera=$((espera + 1))
    sleep 1
done
[ "$resposta" = '{"ok":true}' ] || { cat "$TMP/saida-dev.txt" >&2; falhar "GET /api/health não respondeu {\"ok\":true} (veio: ${resposta:-nada})"; }
echo "ok: /api/health responde em modo desenvolvimento"

echo "PACOTE VERIFICADO: $PACOTE"
