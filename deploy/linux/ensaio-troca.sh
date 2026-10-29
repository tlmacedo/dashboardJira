#!/bin/sh
# Ensaio da troca de versao e da reversao, sem servico e sem tocar em nada fora de uma pasta temporaria (Plano 13, T10).
# Uso:  sh deploy/linux/ensaio-troca.sh
# Cria dois pacotes de mentira (2026.11.1 e 2026.11.2, so com bin/painel de marcador), roda atualizar.sh --sem-servico duas
# vezes e reverter.sh --sem-servico, e confere para onde aponta "atual" em cada etapa.
# Saida final: TROCA_OK e REVERSAO_OK. A pasta temporaria e removida ao fim, mesmo em caso de erro.
set -eu

AQUI="$(cd "$(dirname "$0")" && pwd)"
. "$AQUI/comum.sh"

ENSAIO="$(mktemp -d)"
trap 'rm -rf "$ENSAIO"' EXIT
RAIZ="$ENSAIO/app"
RECEBIDOS="$ENSAIO/recebidos"
FONTE="$ENSAIO/fonte"
mkdir -p "$RAIZ" "$RECEBIDOS" "$FONTE" "$ENSAIO/dados"
PAINEL_DADOS="$ENSAIO/dados"
export PAINEL_DADOS

# Pacote de mentira: zip com a pasta raiz painel-<versao>/ (zip, ou o jar do JDK quando o zip nao existe) e o .sha256.
criar_pacote() {
    mkdir -p "$FONTE/painel-$1/bin"
    printf 'versao=%s\n' "$1" > "$FONTE/painel-$1/VERSAO.txt"
    printf '#!/bin/sh\necho %s\n' "$1" > "$FONTE/painel-$1/bin/painel"
    if command -v zip >/dev/null 2>&1; then
        (cd "$FONTE" && zip -qr "$RECEBIDOS/painel-$1.zip" "painel-$1")
    else
        (cd "$FONTE" && jar cfM "$RECEBIDOS/painel-$1.zip" "painel-$1")
    fi
    printf '%s  painel-%s.zip\n' "$(calcula_sha256 "$RECEBIDOS/painel-$1.zip")" "$1" > "$RECEBIDOS/painel-$1.zip.sha256"
}

destino_de_atual() {
    basename "$(readlink "$RAIZ/atual")"
}

conferir_atual() {
    [ "$(destino_de_atual)" = "$1" ] || erro "atual deveria apontar para $1, mas aponta para $(destino_de_atual)"
}

criar_pacote 2026.11.1
criar_pacote 2026.11.2

sh "$AQUI/atualizar.sh" "$RAIZ" "$RECEBIDOS/painel-2026.11.1.zip" --sem-servico > /dev/null
conferir_atual 2026.11.1
sh "$AQUI/atualizar.sh" "$RAIZ" "$RECEBIDOS/painel-2026.11.2.zip" --sem-servico > /dev/null
conferir_atual 2026.11.2
[ -f "$RAIZ/2026.11.1/bin/painel" ] || erro "a versao anterior nao pode sumir na troca"
echo "TROCA_OK"

sh "$AQUI/reverter.sh" "$RAIZ" --sem-servico > /dev/null
conferir_atual 2026.11.1
[ -f "$RAIZ/2026.11.2/bin/painel" ] || erro "a versao revertida continua em disco"
echo "REVERSAO_OK"
