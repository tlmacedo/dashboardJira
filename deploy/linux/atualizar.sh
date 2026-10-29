#!/bin/sh
# Atualiza o painel para uma nova versao por troca de pasta, com backup antes (Plano 13, T10). NAO reverte sozinho.
# Uso (como root):  sh deploy/linux/atualizar.sh /opt/painel /caminho/painel-2026.11.1.zip [--sem-servico]
#
# Ordem (cada passo imprime "OK <passo>"):
#   1. confere o SHA-256 do zip contra o .sha256 ao lado;
#   2. extrai numa pasta nova <raiz>/<versao> (o pacote em uso fica intacto; recusa se a pasta da versao ja existir);
#   3. PARA o servico e espera o fim do processo (a trava <banco>.lock some);
#   4. BACKUP do banco (painel backup-agora, Plano 12); se falhar, religa a versao atual, remove a pasta extraida e aborta;
#   5. registra a versao anterior e o nome do backup em <raiz>/versao-anterior.txt (pasta que so root escreve; so depois do backup);
#   6. troca o link <raiz>/atual para a versao nova;
#   7. INICIA o servico e espera /api/health (as migracoes rodam na subida);
#   8. fumaca (a saude responde e /api/meta informa a versao nova).
# Falha no 7 ou no 8: imprime "FALHOU: execute reverter" com o comando exato e sai com 1. NAO ha reversao automatica: depois
# de uma migracao, restaurar o banco e decisao humana (ver docs/implantacao/reversao.md).
# --sem-servico pula 3, 4, 7 e 8 (so serve ao ensaio da troca de pasta).
# Variaveis opcionais: PAINEL_ENV_FILE (/etc/painel/painel.env), PAINEL_SERVICO (painel),
# PAINEL_URL (http://127.0.0.1:8080). Precisa de unzip (ou do jar do JDK), curl (ou wget) e sha256sum.
set -eu

AQUI="$(cd "$(dirname "$0")" && pwd)"
. "$AQUI/comum.sh"

uso() {
    echo "uso: sh atualizar.sh <raiz do painel> <painel-AAAA.MM.N.zip> [--sem-servico]" >&2
    exit 2
}

RAIZ=""
ZIP=""
SEM_SERVICO=0
for argumento in "$@"; do
    case "$argumento" in
        --sem-servico) SEM_SERVICO=1 ;;
        -*) uso ;;
        *)
            if [ -z "$RAIZ" ]; then RAIZ="$argumento"; elif [ -z "$ZIP" ]; then ZIP="$argumento"; else uso; fi
            ;;
    esac
done
[ -n "$RAIZ" ] && [ -n "$ZIP" ] || uso
ARQUIVO_ENV="${PAINEL_ENV_FILE:-/etc/painel/painel.env}"
SERVICO="${PAINEL_SERVICO:-painel}"
URL="${PAINEL_URL:-http://127.0.0.1:8080}"
[ -d "$RAIZ" ] || erro "pasta do painel nao encontrada"
[ -f "$ZIP" ] || erro "zip nao encontrado"

falhou_depois_da_troca() {
    echo "FALHOU: $1" >&2
    echo "FALHOU: execute reverter -> sh $AQUI/reverter.sh $RAIZ   (acrescente --restaurar-banco se a versao nova trouxe migracao sem volta)" >&2
    exit 1
}

# TOCTOU: o zip e o .sha256 sao lidos UMA vez para uma pasta temporaria 0700 so do root (cp -P nao segue links) e dai em diante a
# conferencia do SHA-256 e a extracao usam so a copia; quem escrevesse na pasta de recebidos nao poderia trocar o zip entre uma
# coisa e outra.
PASTA_DO_ZIP="$(mktemp -d)"
chmod 700 "$PASTA_DO_ZIP"
trap 'rm -rf "$PASTA_DO_ZIP"' EXIT
NOME_DO_ZIP="$(basename "$ZIP")"
cp -P -- "$ZIP" "$PASTA_DO_ZIP/$NOME_DO_ZIP" || erro "nao foi possivel ler o zip"
[ ! -f "$ZIP.sha256" ] || cp -P -- "$ZIP.sha256" "$PASTA_DO_ZIP/$NOME_DO_ZIP.sha256" || erro "nao foi possivel ler o .sha256"
ZIP="$PASTA_DO_ZIP/$NOME_DO_ZIP"
[ -f "$ZIP" ] && [ ! -L "$ZIP" ] || erro "o zip e um link simbolico (recusado)"

confirmar_sha256 "$ZIP"
echo "OK sha256"
VERSAO="$(versao_do_zip "$ZIP")"

expandir_pacote "$ZIP" "$RAIZ"
echo "OK extraido em $RAIZ/$VERSAO"

ANTERIOR=""
if [ -L "$RAIZ/atual" ]; then
    ANTERIOR="$(basename "$(readlink "$RAIZ/atual")")"
    validar_versao "$ANTERIOR"
fi

COPIA=""
if [ "$SEM_SERVICO" -eq 0 ]; then
    parar_painel "$SERVICO"
    echo "OK servico parado"
    if [ -n "$ANTERIOR" ]; then
        if ! COPIA="$(fazer_backup "$RAIZ" "$ARQUIVO_ENV")"; then
            echo "FALHOU: o backup antes da troca nao foi feito; religando a versao atual (nada foi trocado; a pasta $VERSAO extraida foi removida)" >&2
            religar_painel "$SERVICO" || true
            # A pasta foi criada por ESTA execucao (expandir_pacote recusa uma que ja exista): remover e seguro e permite repetir.
            rm -rf "${RAIZ:?}/$VERSAO"
            exit 1
        fi
        nome_de_backup_valido "$COPIA" || erro "o backup devolveu um nome inesperado"
        echo "OK backup $COPIA"
    fi
fi

# O registro so e gravado depois do backup (uma falha nao perde o registro da atualizacao anterior), na pasta que so root escreve.
if [ -n "$ANTERIOR" ]; then
    gravar_registro "$RAIZ" "$ANTERIOR" "$COPIA"
    echo "OK versao anterior registrada ($ANTERIOR)"
fi

trocar_atual "$RAIZ" "$VERSAO"
echo "OK atual aponta para $VERSAO"

if [ "$SEM_SERVICO" -eq 0 ]; then
    iniciar_painel "$SERVICO" || falhou_depois_da_troca "o servico nao iniciou"
    esperar_saude "$URL" 120 || falhou_depois_da_troca "/api/health nao respondeu em 120 s"
    echo "OK servico iniciado e saudavel"
    conferir_fumaca "$URL" "$VERSAO" || falhou_depois_da_troca "a fumaca nao passou"
    echo "OK fumaca"
fi

echo "ATUALIZACAO_OK $VERSAO"
