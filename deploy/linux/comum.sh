#!/bin/sh
# Funcoes comuns dos scripts de implantacao do Painel de Squads (Linux). Use com:  . "$(dirname "$0")/comum.sh"
set -eu

# Deixa o arquivo de variaveis (painel.env) legivel so pelo dono (root) e pelo grupo do servico (leitura): o processo le, nao altera.
# Uso (como root): restringir_arquivo_de_variaveis /etc/painel/painel.env
restringir_arquivo_de_variaveis() {
    chown root:painel "$1"
    chmod 640 "$1"
}

# Sai com 0 se o modo do arquivo e 640 ou mais restrito (nada de escrita pelo grupo nem qualquer acesso a "outros").
# Uso: arquivo_de_variaveis_protegido /etc/painel/painel.env
arquivo_de_variaveis_protegido() {
    [ -f "$1" ] || return 1
    modo="$(stat -c '%a' "$1" 2>/dev/null || stat -f '%Lp' "$1")"
    # completa com zeros a esquerda (ex.: 640, 600, 400)
    while [ "${#modo}" -lt 3 ]; do modo="0$modo"; done
    dono="${modo%??}"
    resto="${modo#?}"
    grupo="${resto%?}"
    outros="${resto#?}"
    : "$dono"
    [ "$outros" = "0" ] || return 1
    # grupo: so leitura (4) ou nada (0)
    case "$grupo" in 0 | 4) return 0 ;; *) return 1 ;; esac
}
