#!/bin/sh
# Volta o painel para a versao anterior (a de <raiz>/versao-anterior.txt, gravada por atualizar.sh numa pasta que so root
# escreve; mesmo assim a versao e o nome do backup sao validados antes de virarem caminho) (Plano 13, T10).
# Uso (como root):  sh deploy/linux/reverter.sh /opt/painel [--restaurar-banco] [--backup <arquivo>] [--sem-servico]
#
# Sem --restaurar-banco so o pacote volta (serve quando a versao nova NAO trouxe migracao, ou trouxe uma compativel com a
# anterior). COM --restaurar-banco o banco tambem volta para o backup feito antes da atualizacao (backup= do versao-anterior.txt,
# ou o --backup indicado); tudo gravado DEPOIS desse backup se perde, e o banco atual NAO e apagado: ele e guardado ao lado como
# <banco>.migrado-<data> (com os -wal e -shm). --restaurar-banco e OBRIGATORIO quando a versao nova declarou "migracao sem volta"
# ou quando a anterior nao sobe sobre o esquema novo. Antes de tocar no banco atual, a copia e conferida com
# `painel verificar-restauracao` (Plano 12).
# --sem-servico pula parar/iniciar/fumaca (so serve ao ensaio da troca de pasta).
# Variaveis opcionais: PAINEL_ENV_FILE (/etc/painel/painel.env), PAINEL_SERVICO (painel),
# PAINEL_URL (http://127.0.0.1:8080).
set -eu

AQUI="$(cd "$(dirname "$0")" && pwd)"
. "$AQUI/comum.sh"

uso() {
    echo "uso: sh reverter.sh <raiz do painel> [--restaurar-banco] [--backup <arquivo>] [--sem-servico]" >&2
    exit 2
}

RAIZ=""
RESTAURAR=0
BACKUP_INFORMADO=""
SEM_SERVICO=0
while [ "$#" -gt 0 ]; do
    case "$1" in
        --restaurar-banco) RESTAURAR=1 ;;
        --sem-servico) SEM_SERVICO=1 ;;
        --backup)
            [ "$#" -ge 2 ] || uso
            BACKUP_INFORMADO="$2"
            shift
            ;;
        -*) uso ;;
        *) if [ -z "$RAIZ" ]; then RAIZ="$1"; else uso; fi ;;
    esac
    shift
done
[ -n "$RAIZ" ] || uso
ARQUIVO_ENV="${PAINEL_ENV_FILE:-/etc/painel/painel.env}"
SERVICO="${PAINEL_SERVICO:-painel}"
URL="${PAINEL_URL:-http://127.0.0.1:8080}"
# O registro fica na pasta que so root escreve (a conta do servico escreve em /var/lib/painel e nao decide o que root executa).
REGISTRO="$RAIZ/versao-anterior.txt"

[ -d "$RAIZ" ] || erro "pasta do painel nao encontrada"
[ -f "$REGISTRO" ] || erro "falta $REGISTRO (versao-anterior): nenhuma atualizacao registrada, nao ha versao anterior para voltar"
ANTERIOR="$(valor_do_arquivo "$REGISTRO" versao)"
[ -n "$ANTERIOR" ] || erro "o arquivo versao-anterior.txt nao informa a versao"
# Nada do registro vira caminho antes de passar: formato AAAA.MM.N, pasta propria (sem link) dentro de $RAIZ, bin/painel proprio.
pasta_da_versao_valida "$RAIZ" "$ANTERIOR"

COPIA=""
if [ "$RESTAURAR" -eq 1 ]; then
    PASTA_DE_BACKUP="$(valor_do_arquivo "$ARQUIVO_ENV" BACKUP_DIR)"
    [ -n "$PASTA_DE_BACKUP" ] && [ -d "$PASTA_DE_BACKUP" ] || erro "BACKUP_DIR nao esta definido (ou nao existe) no arquivo de variaveis"
    if [ -n "$BACKUP_INFORMADO" ]; then
        COPIA="$BACKUP_INFORMADO"
        texto_de_uma_linha "$COPIA" || erro "caminho de backup invalido"
        case "$COPIA" in -* | *..*) erro "caminho de backup invalido" ;; esac
        case "$COPIA" in /*) ;; *) COPIA="$PASTA_DE_BACKUP/$COPIA" ;; esac
    else
        NOME_DO_BACKUP="$(valor_do_arquivo "$REGISTRO" backup)"
        [ -n "$NOME_DO_BACKUP" ] || erro "nao ha backup registrado em versao-anterior.txt; informe --backup <arquivo>"
        nome_de_backup_valido "$NOME_DO_BACKUP" || erro "o nome do backup no registro e invalido"
        COPIA="$PASTA_DE_BACKUP/$NOME_DO_BACKUP"
    fi
    [ -f "$COPIA" ] || erro "a copia de seguranca nao foi encontrada"
    # A copia (resolvida, sem links) tem de estar DENTRO de BACKUP_DIR.
    caminho_dentro_de "$COPIA" "$PASTA_DE_BACKUP" || erro "a copia de seguranca esta fora de BACKUP_DIR"
    # Confere a copia ANTES de mexer no banco atual (restaura num temporario e compara tabela a tabela).
    if ! PAINEL_ENV_FILE="$ARQUIVO_ENV" "$RAIZ/$ANTERIOR/bin/painel" verificar-restauracao "$COPIA" | grep -q "RESTAURACAO OK"; then
        erro "a copia nao passou em verificar-restauracao; nada foi alterado"
    fi
    echo "OK copia conferida"
fi

if [ "$SEM_SERVICO" -eq 0 ]; then
    parar_painel "$SERVICO"
    echo "OK servico parado"
fi

if [ "$RESTAURAR" -eq 1 ]; then
    BANCO="$(valor_do_arquivo "$ARQUIVO_ENV" DATABASE_PATH)"
    [ -n "$BANCO" ] || erro "DATABASE_PATH nao esta definido no arquivo de variaveis"
    DATA="$(date +%Y%m%d-%H%M%S)"
    # Nada e apagado: o banco atual e os auxiliares do WAL saem juntos para o lado, com a data.
    for sufixo in "" "-wal" "-shm"; do
        if [ -e "$BANCO$sufixo" ]; then
            mv "$BANCO$sufixo" "$BANCO$sufixo.migrado-$DATA"
        fi
    done
    cp "$COPIA" "$BANCO"
    chown --reference="$BANCO.migrado-$DATA" "$BANCO" 2>/dev/null || true
    chmod --reference="$BANCO.migrado-$DATA" "$BANCO" 2>/dev/null || true
    echo "OK banco restaurado (o anterior ficou em $BANCO.migrado-$DATA; o que foi gravado depois do backup so existe la)"
fi

trocar_atual "$RAIZ" "$ANTERIOR"
echo "OK atual aponta para $ANTERIOR"

if [ "$SEM_SERVICO" -eq 0 ]; then
    iniciar_painel "$SERVICO"
    esperar_saude "$URL" 120 || erro "/api/health nao respondeu em 120 s depois da reversao"
    conferir_fumaca "$URL" "$ANTERIOR" || erro "a fumaca nao passou depois da reversao"
    echo "OK servico iniciado, saudavel e na versao $ANTERIOR"
    if [ "$RESTAURAR" -eq 1 ]; then
        echo "Rode agora: painel backup-agora (o registro de backups voltou ao estado da copia)."
    fi
fi

echo "REVERSAO_OK $ANTERIOR"
