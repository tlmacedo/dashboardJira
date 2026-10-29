#!/bin/sh
# Procura nos logs do painel o VALOR de cada segredo do arquivo de variaveis (nome com SECRET, PASSWORD ou TOKEN e valor nao
# vazio; aspas ao redor do valor sao tiradas). Imprime so LOG_LIMPO ou, para cada achado, LOG_COM_SEGREDO <NOME_DA_VARIAVEL>;
# nunca o valor. Se o log NAO puder ser lido (journal sem permissao, vazio ou arquivo ausente), imprime LOG_NAO_LIDO: nada foi
# conferido e isso nunca vira LOG_LIMPO.
# Uso:  sh verificar-logs.sh [/etc/painel/painel.env] [arquivo-de-log]
#   sem o segundo argumento le o journal do servico (journalctl -u "$PAINEL_SERVICO" --no-pager; padrao: painel).
# Codigo de saida: 0 limpo, 1 segredo encontrado, 2 uso/arquivo ausente, 3 log nao lido (nada conferido).
set -eu

ARQUIVO="${1:-/etc/painel/painel.env}"
LOG="${2:-}"
SERVICO="${PAINEL_SERVICO:-painel}"
[ -f "$ARQUIVO" ] || { echo "ERRO: arquivo de variaveis nao encontrado" >&2; exit 2; }

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
if [ -n "$LOG" ]; then
    [ -f "$LOG" ] || { echo "ERRO: arquivo de log nao encontrado" >&2; exit 2; }
    FONTE="$LOG"
else
    FONTE="$TMP/journal.txt"
    journalctl -u "$SERVICO" --no-pager > "$FONTE" 2>/dev/null || : > "$FONTE"
fi
if [ ! -s "$FONTE" ]; then
    echo "LOG_NAO_LIDO"
    exit 3
fi

ACHOU=0
# Cada valor vai para um arquivo temporario (modo 600) e o grep le o padrao de la: o valor nunca aparece em argumento de processo.
grep -E '^[A-Za-z0-9_]*(SECRET|PASSWORD|TOKEN)[A-Za-z0-9_]*=' "$ARQUIVO" | tr -d '\r' > "$TMP/segredos.txt" || true
while IFS= read -r linha; do
    nome="${linha%%=*}"
    valor="${linha#*=}"
    # tira UM par de aspas ao redor (o painel faz o mesmo ao ler o arquivo)
    case "$valor" in
        \"*\") valor="${valor#\"}"; valor="${valor%\"}" ;;
        \'*\') valor="${valor#\'}"; valor="${valor%\'}" ;;
    esac
    [ -n "$valor" ] || continue
    (umask 077; printf '%s\n' "$valor" > "$TMP/padrao.txt")
    if grep -qF -f "$TMP/padrao.txt" "$FONTE"; then
        echo "LOG_COM_SEGREDO $nome"
        ACHOU=1
    fi
done < "$TMP/segredos.txt"

if [ "$ACHOU" -eq 0 ]; then
    echo "LOG_LIMPO"
    exit 0
fi
exit 1
