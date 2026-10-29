#!/bin/sh
# Gera o SESSION_SECRET (48 bytes aleatorios em base64) direto no arquivo de variaveis, SEM imprimir o valor.
# Uso:  sh gerar-session-secret.sh /etc/painel/painel.env [--rotacionar]
#   - sem --rotacionar: recusa se SESSION_SECRET ja tem valor (codigo 3);
#   - com --rotacionar: o valor atual passa para SESSION_SECRET_ANTERIOR (aceito so para ler cookies por 8 horas) e um novo
#     entra em SESSION_SECRET, como em docs/operacao/rotacao-de-segredos.md. Reinicie o painel depois; apague a linha
#     SESSION_SECRET_ANTERIOR passadas 8 horas e reinicie de novo.
# Saida: SESSION_SECRET_GERADO ou SESSION_SECRET_ROTACIONADO. O arquivo mantem dono e permissao (so o conteudo muda).
set -eu

ARQUIVO="${1:-}"
[ -n "$ARQUIVO" ] || { echo "uso: sh gerar-session-secret.sh <painel.env> [--rotacionar]" >&2; exit 2; }
[ -f "$ARQUIVO" ] || { echo "ERRO: arquivo de variaveis nao encontrado" >&2; exit 2; }
ROTACIONAR=0
[ "${2:-}" != "--rotacionar" ] || ROTACIONAR=1

ATUAL="$(sed -n 's/^SESSION_SECRET=//p' "$ARQUIVO" | head -n 1 | tr -d '\r')"
if [ -n "$ATUAL" ] && [ "$ROTACIONAR" -eq 0 ]; then
    echo "ERRO: SESSION_SECRET ja tem valor; use --rotacionar para trocar (o valor atual vira SESSION_SECRET_ANTERIOR)" >&2
    exit 3
fi

if command -v openssl >/dev/null 2>&1; then
    SEGREDO="$(openssl rand -base64 48 | tr -d '\n')"
else
    SEGREDO="$(head -c 48 /dev/urandom | base64 | tr -d '\n')"
fi

# O temporario nasce NA MESMA pasta do painel.env (/etc/painel, so do root): o root nunca escreve em pasta do servico aqui.
TMP="$(mktemp "$ARQUIVO.XXXXXX")"
trap 'rm -f "$TMP"' EXIT
chmod 600 "$TMP"
# Tudo do arquivo, menos as linhas de segredo da sessao; as novas entram no fim.
grep -v -e '^SESSION_SECRET=' -e '^SESSION_SECRET_ANTERIOR=' "$ARQUIVO" > "$TMP" || true
if [ -n "$ATUAL" ]; then
    printf 'SESSION_SECRET_ANTERIOR=%s\n' "$ATUAL" >> "$TMP"
fi
printf 'SESSION_SECRET=%s\n' "$SEGREDO" >> "$TMP"
# Troca o arquivo de uma vez (mv no mesmo diretorio e atomico: uma queda no meio nunca deixa o painel.env truncado), com o mesmo
# dono e a mesma permissao do original.
chown --reference="$ARQUIVO" "$TMP" 2>/dev/null || true
chmod --reference="$ARQUIVO" "$TMP" 2>/dev/null || true
mv -f "$TMP" "$ARQUIVO"

if [ -n "$ATUAL" ]; then
    echo "SESSION_SECRET_ROTACIONADO"
else
    echo "SESSION_SECRET_GERADO"
fi
