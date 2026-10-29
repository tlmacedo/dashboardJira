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
# Com um ZIP (painel-AAAA.MM.N.zip, com o painel-AAAA.MM.N.zip.sha256 ao lado) confere ANTES de extrair: nome, tamanho
# (no máximo 200 MB), SHA-256, pasta raiz e nomes proibidos; depois extrai numa pasta temporária e roda as conferências
# acima sobre o conteúdo extraído. Precisa de unzip e de sha256sum (ou shasum).
set -eu

AQUI="$(cd "$(dirname "$0")" && pwd)"
APP="$(cd "$AQUI/.." && pwd)"
ALVO="${1:-$APP/pacote/painel}"
ZIPTMP=""
trap 'rm -rf "$ZIPTMP"' EXIT

falhar() { echo "FALHA: $*" >&2; exit 1; }

# Nome proibido (já em minúsculas) num componente de caminho; $2 é a entrada inteira, para a mensagem.
confere_nome() {
    case "$1" in
        .env.example) ;;
        .env | *.env | .env.*) falhar "arquivo de variáveis (.env, painel.env) dentro do pacote: $2" ;;
        *.db | *.db-wal | *.db-shm | *.lock | *.rotacao) falhar "arquivo de banco ou de trava (.db, .db-wal, .db-shm, *.lock) dentro do pacote: $2" ;;
        *.pem | *.key | *.p12 | *.pfx | *.jks | *.keystore) falhar "chave ou certificado dentro do pacote: $2" ;;
        papeis.yaml | equipes.yaml | ausencias.yaml | squads.yaml | status_map.yaml | jira_campos.yaml | calendario.yaml | vinculos.yaml | labels_atraso.yaml | auditoria.yaml)
            falhar "configuração real (não é modelo) dentro do pacote: $2" ;;
        filtros_status_tool.txt) falhar "arquivo de filtros do Jira (Filtros_Status_tool.txt) dentro do pacote: $2" ;;
        legacy | node_modules | .git) falhar "pasta proibida (legacy, node_modules, .git) dentro do pacote: $2" ;;
    esac
}

calcula_sha256() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | cut -d ' ' -f 1
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | cut -d ' ' -f 1
    else
        falhar "precisa de sha256sum ou shasum para conferir o SHA-256"
    fi
}

case "$ALVO" in
    *.zip | *.ZIP)
        echo "== Zip =="
        command -v unzip >/dev/null 2>&1 || falhar "precisa de unzip para verificar o zip"
        [ -f "$ALVO" ] || falhar "zip não encontrado: $ALVO"
        ZIP="$(cd "$(dirname "$ALVO")" && pwd)/$(basename "$ALVO")"
        NOME="$(basename "$ZIP")"
        printf '%s' "$NOME" | grep -Eq '^painel-[0-9]{4}\.(0[1-9]|1[0-2])\.[1-9][0-9]*\.zip$' \
            || falhar "nome do pacote fora do padrão painel-AAAA.MM.N.zip"
        TAMANHO="$(wc -c < "$ZIP" | tr -d ' ')"
        [ "$TAMANHO" -le 209715200 ] || falhar "o zip passa de 200 MB (limite do pacote)"
        [ -f "$ZIP.sha256" ] || falhar "falta o arquivo $NOME.sha256 ao lado do zip"
        ESPERADO="$(head -n 1 "$ZIP.sha256" | cut -d ' ' -f 1 | tr 'A-F' 'a-f' | tr -d '\r')"
        NOME_NO_ARQUIVO="$(head -n 1 "$ZIP.sha256" | sed 's/^[^ ]*  *\*\{0,1\}//' | tr -d '\r')"
        [ "$NOME_NO_ARQUIVO" = "$NOME" ] || falhar "o arquivo .sha256 não é deste zip"
        [ "$ESPERADO" = "$(calcula_sha256 "$ZIP")" ] || falhar "o SHA-256 do zip não confere com o arquivo .sha256"
        echo "ok: SHA-256 confere"
        ZIPTMP="$(mktemp -d)"
        RAIZ="${NOME%.zip}"
        unzip -Z1 "$ZIP" > "$ZIPTMP/entradas.txt"
        # Tamanho TOTAL extraido (um zip pequeno pode expandir para gigabytes): no maximo 500 MB.
        EXTRAIDO="$(unzip -Zt "$ZIP" | sed -n 's/.* \([0-9][0-9]*\) bytes uncompressed.*/\1/p' | head -n 1)"
        [ -n "$EXTRAIDO" ] || falhar "nao foi possivel medir o tamanho extraido do zip"
        [ "$EXTRAIDO" -le 524288000 ] || falhar "o tamanho extraido do zip passa de 500 MB"
        while IFS= read -r entrada; do
            [ -n "$entrada" ] || continue
            # Nada de barra invertida (no Windows vira separador) nem de caminho absoluto ou unidade no nome da entrada.
            case "$entrada" in
                *'\'*) falhar "entrada com barra invertida no nome: $entrada" ;;
                /* | [A-Za-z]:*) falhar "entrada com caminho absoluto: $entrada" ;;
            esac
            case "$entrada" in
                "$RAIZ"/*) ;;
                *) falhar "entrada fora da pasta raiz $RAIZ/: $entrada" ;;
            esac
            resto="${entrada%/}"
            while [ -n "$resto" ]; do
                componente="${resto##*/}"
                [ "$componente" != ".." ] || falhar "entrada com '..' no caminho: $entrada"
                confere_nome "$(printf '%s' "$componente" | tr 'A-Z' 'a-z')" "$entrada"
                case "$resto" in */*) resto="${resto%/*}" ;; *) resto="" ;; esac
            done
        done < "$ZIPTMP/entradas.txt"
        echo "ok: nenhuma entrada proibida"
        unzip -q "$ZIP" -d "$ZIPTMP/extraido"
        ALVO="$ZIPTMP/extraido/$RAIZ"
        ;;
esac

PACOTE="$(cd "$ALVO" 2>/dev/null && pwd)" || { echo "FALHA: pacote não encontrado (rode: sh scripts/montar-pacote.sh)" >&2; exit 1; }

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
# Nomes proibidos em QUALQUER lugar do pacote (a mesma lista usada para as entradas do zip).
find "$PACOTE" -mindepth 1 | while IFS= read -r caminho; do
    confere_nome "$(printf '%s' "${caminho##*/}" | tr 'A-Z' 'a-z')" "${caminho#"$PACOTE"/}"
done

# ---------------------------------------------------------------- 2. estrutura
echo "== Estrutura =="
[ -f "$PACOTE/bin/painel" ] || falhar "falta bin/painel"
[ -f "$PACOTE/bin/painel.bat" ] || falhar "falta bin/painel.bat"
ls "$PACOTE"/lib/painel-*.jar >/dev/null 2>&1 || falhar "falta o jar do painel em lib/"
[ -f "$PACOTE/frontend/dist/index.html" ] || falhar "falta frontend/dist/index.html"
[ -f "$PACOTE/config/papeis.exemplo.yaml" ] || falhar "falta config/papeis.exemplo.yaml"
[ -f "$PACOTE/VERSAO.txt" ] || falhar "falta VERSAO.txt"
[ -f "$PACOTE/docs/implantacao.md" ] || falhar "falta docs/implantacao.md"
[ -f "$PACOTE/implantacao/windows/atualizar.ps1" ] || falhar "falta implantacao/windows/atualizar.ps1"
[ -f "$PACOTE/implantacao/linux/atualizar.sh" ] || falhar "falta implantacao/linux/atualizar.sh"

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
    rm -rf "$TMP" "$ZIPTMP"
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
