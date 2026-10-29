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
    resto="${modo#?}"
    grupo="${resto%?}"
    outros="${resto#?}"
    [ "$outros" = "0" ] || return 1
    # grupo: so leitura (4) ou nada (0)
    case "$grupo" in 0 | 4) return 0 ;; *) return 1 ;; esac
}

# ---------------------------------------------------------------------------------------------------------------------
# Atualizacao e reversao por troca de pasta de versao (Plano 13, T10). Disposicao: <raiz>/<versao>/ (pacote extraido, somente
# leitura), <raiz>/atual (link simbolico para a versao ativa), dados em PAINEL_DADOS (padrao /var/lib/painel).
# ---------------------------------------------------------------------------------------------------------------------

erro() {
    echo "ERRO: $*" >&2
    exit 1
}

calcula_sha256() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | cut -d ' ' -f 1
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | cut -d ' ' -f 1
    else
        erro "precisa de sha256sum ou shasum"
    fi
}

# Valor de NOME=valor num arquivo (sem aspas nem CR). Uso: valor_do_arquivo <arquivo> <NOME>
valor_do_arquivo() {
    sed -n "s/^$2=//p" "$1" 2>/dev/null | head -n 1 | tr -d '\r' | sed -e 's/^"\(.*\)"$/\1/' -e "s/^'\(.*\)'\$/\1/"
}

# Confere o SHA-256 do zip contra o arquivo .sha256 ao lado ("<hash>  <nome>"). Uso: confirmar_sha256 <zip>
confirmar_sha256() {
    [ -f "$1.sha256" ] || erro "falta o arquivo $(basename "$1").sha256 ao lado do zip"
    esperado="$(head -n 1 "$1.sha256" | cut -d ' ' -f 1 | tr 'A-F' 'a-f' | tr -d '\r')"
    nome="$(head -n 1 "$1.sha256" | sed 's/^[^ ]*  *\*\{0,1\}//' | tr -d '\r')"
    [ "$nome" = "$(basename "$1")" ] || erro "o arquivo .sha256 nao e deste zip"
    [ "$esperado" = "$(calcula_sha256 "$1")" ] || erro "o SHA-256 do zip nao confere com o arquivo .sha256"
}

# Versao (AAAA.MM.N) pelo nome painel-AAAA.MM.N.zip. Uso: versao_do_zip <zip>
versao_do_zip() {
    base="$(basename "$1")"
    printf '%s' "$base" | grep -Eq '^painel-[0-9]{4}\.(0[1-9]|1[0-2])\.[1-9][0-9]*\.zip$' || erro "nome do pacote fora do padrao painel-AAAA.MM.N.zip"
    base="${base#painel-}"
    printf '%s\n' "${base%.zip}"
}

# Extrai um zip numa pasta (unzip; sem ele, o jar do JDK, que a maquina ja tem).
extrair_zip() {
    if command -v unzip >/dev/null 2>&1; then
        unzip -q "$1" -d "$2"
    elif command -v jar >/dev/null 2>&1; then
        (cd "$2" && jar xf "$1")
    else
        erro "precisa de unzip ou do jar do JDK para extrair o pacote"
    fi
}

# Extrai o pacote em <raiz>/<versao>.tmp, confere a estrutura e renomeia para <raiz>/<versao>. RECUSA se a pasta da versao ja
# existir: a versao em uso nunca e sobrescrita. Uso: expandir_pacote <zip> <raiz>
expandir_pacote() {
    versao="$(versao_do_zip "$1")"
    destino="$2/$versao"
    [ ! -e "$destino" ] || erro "a pasta da versao $versao já existe em $2 (a versao em uso nunca e sobrescrita)"
    tmp="$destino.tmp"
    rm -rf "$tmp"
    mkdir -p "$tmp"
    if ! extrair_zip "$1" "$tmp"; then
        rm -rf "$tmp"
        erro "nao foi possivel extrair o pacote"
    fi
    if [ ! -f "$tmp/painel-$versao/bin/painel" ]; then
        rm -rf "$tmp"
        erro "pacote sem painel-$versao/bin/painel"
    fi
    mv "$tmp/painel-$versao" "$destino"
    rmdir "$tmp"
}

# Aponta <raiz>/atual para <raiz>/<versao> (link relativo, troca de uma vez). Uso: trocar_atual <raiz> <versao>
trocar_atual() {
    [ -d "$1/$2" ] || erro "a pasta da versao $2 nao existe em $1"
    rm -f "$1/atual.novo"
    ln -s "$2" "$1/atual.novo"
    if ! mv -T "$1/atual.novo" "$1/atual" 2>/dev/null; then
        rm -f "$1/atual"
        mv "$1/atual.novo" "$1/atual"
    fi
}

# Para o servico e espera o fim do processo (a trava <banco>.lock some com ele). Uso: parar_painel <servico>
parar_painel() {
    systemctl stop "$1"
    n=0
    while systemctl is-active --quiet "$1"; do
        n=$((n + 1))
        [ "$n" -le 60 ] || erro "o servico $1 nao parou em 60 s"
        sleep 1
    done
}

iniciar_painel() {
    systemctl start "$1"
}

# Religa a versao ainda atual depois de uma falha antes da troca.
religar_painel() {
    iniciar_painel "$1"
}

baixar() {
    if command -v curl >/dev/null 2>&1; then
        curl -fsS --max-time 3 "$1"
    elif command -v wget >/dev/null 2>&1; then
        wget -q -T 3 -O - "$1"
    else
        erro "precisa de curl ou wget"
    fi
}

# Espera GET <url>/api/health responder {"ok":true}. Uso: esperar_saude <url> <segundos>
esperar_saude() {
    n=0
    while [ "$n" -lt "$2" ]; do
        corpo="$(baixar "$1/api/health" 2>/dev/null || true)"
        case "$corpo" in *'"ok":true'*) return 0 ;; esac
        n=$((n + 1))
        sleep 1
    done
    return 1
}

# REGRA DE DESENHO (classe de falhas "root operando em pasta do servico"): o root NUNCA escreve, move, apaga, muda dono/modo nem
# segue caminho dentro de pastas que a conta do servico controla (dados, backups, logs). Tudo o que precisa acontecer la roda COM
# A IDENTIDADE DO SERVICO (como_servico): o arquivo ja nasce com o dono certo e um link plantado so alcanca o que o proprio servico
# ja alcanca. O usuario do servico vem do AMBIENTE do root que roda o script (PAINEL_USUARIO, padrao "painel"), nunca de um arquivo
# ou pasta que o servico escreva. O root so mexe em <raiz> (so dele) e nas pastas temporarias criadas dentro de <raiz>.
usuario_do_servico() {
    printf '%s' "${PAINEL_USUARIO:-painel}"
}

# Roda o comando como a conta do servico quando o script esta rodando como root; sem ser root nao ha privilegio a baixar e roda direto.
# Uso: como_servico <comando> [argumentos...]
como_servico() {
    if [ "$(id -u)" -ne 0 ]; then
        "$@"
        return
    fi
    usuario="$(usuario_do_servico)"
    # Nome comum de conta (nada de numero, traco inicial, espaco) e NUNCA uid 0: "baixar privilegio" para root nao baixa nada.
    printf '%s' "$usuario" | grep -Eq '^[A-Za-z_][A-Za-z0-9_.-]*$' || erro "PAINEL_USUARIO invalido (esperado o nome da conta do servico)"
    id -u "$usuario" >/dev/null 2>&1 || erro "o usuario do servico ($usuario) nao existe (defina PAINEL_USUARIO)"
    [ "$(id -u "$usuario")" -ne 0 ] || erro "o usuario do servico nao pode ser root (uid 0): defina PAINEL_USUARIO com a conta sem privilegio"
    if command -v runuser >/dev/null 2>&1; then
        runuser -u "$usuario" -- "$@"
    elif command -v setpriv >/dev/null 2>&1; then
        setpriv --reuid="$usuario" --regid="$usuario" --init-groups -- "$@"
    else
        erro "precisa de runuser ou setpriv para rodar como o usuario do servico"
    fi
}

# Backup do banco pelo mecanismo do Plano 12 (painel backup-agora, da versao ATUAL), COMO A CONTA DO SERVICO (a copia nasce com o
# dono certo e nenhum -wal/-shm de root fica na pasta do banco). Imprime o nome do arquivo da copia.
# Uso: fazer_backup <raiz> <arquivo de variaveis>
fazer_backup() {
    saida="$(como_servico env PAINEL_ENV_FILE="$2" "$1/atual/bin/painel" backup-agora)" || { echo "$saida" >&2; return 1; }
    nome="$(printf '%s\n' "$saida" | sed -n 's/^BACKUP OK \([^ ]*\) .*$/\1/p' | head -n 1)"
    [ -n "$nome" ] || { echo "$saida" >&2; return 1; }
    printf '%s\n' "$nome"
}

# Fumaca minima depois de iniciar: a saude responde e /api/meta informa a versao esperada; se existir fumaca.sh ao lado
# (Tarefa 11), ela tambem roda. Uso: conferir_fumaca <url> <versao>
conferir_fumaca() {
    esperar_saude "$1" 30 || return 1
    meta="$(baixar "$1/api/meta" 2>/dev/null || true)"
    case "$meta" in *"\"versao\":\"$2\""*) ;; *) echo "a versao publicada em /api/meta nao e $2" >&2; return 1 ;; esac
    if [ -f "${AQUI:-.}/fumaca.sh" ]; then
        sh "${AQUI:-.}/fumaca.sh" "$1" "$2" || return 1
    fi
    return 0
}

# ---------------------------------------------------------------------------------------------------------------------
# Registro da versao anterior. FICA EM <raiz>/versao-anterior.txt (pasta que so root escreve), NAO na pasta de dados do servico:
# o reverter executa como root o <raiz>/<versao>/bin/painel, entao quem escreve o registro decide o que roda como root.
# Mesmo assim tudo o que e lido dele e validado (defesa em profundidade).
# ---------------------------------------------------------------------------------------------------------------------

# Uma unica linha, sem caracteres de controle. Uso: texto_de_uma_linha <texto>
texto_de_uma_linha() {
    [ "$(printf '%s' "$1" | wc -l | tr -d ' ')" = "0" ] || return 1
    printf '%s' "$1" | LC_ALL=C grep -q '[[:cntrl:]]' && return 1
    return 0
}

# Versao AAAA.MM.N (nada de .., barras, espacos, traco inicial). Uso: validar_versao <versao>
validar_versao() {
    texto_de_uma_linha "$1" && printf '%s' "$1" | grep -Eq '^[0-9]{4}\.(0[1-9]|1[0-2])\.[1-9][0-9]*$' \
        || erro "versao invalida no registro da versao anterior"
}

# Nome de arquivo de backup do registro: so o nome (sem barras, sem .., sem traco inicial, sem espaco nem controle).
nome_de_backup_valido() {
    texto_de_uma_linha "$1" || return 1
    case "$1" in -* | *..*) return 1 ;; esac
    printf '%s' "$1" | grep -Eq '^[A-Za-z0-9][A-Za-z0-9._-]*$'
}

# O caminho resolvido (sem links) esta DENTRO da pasta indicada? Uso: caminho_dentro_de <arquivo> <pasta>
caminho_dentro_de() {
    dir="$(cd "$2" 2>/dev/null && pwd -P)" || return 1
    pai="$(cd "$(dirname "$1")" 2>/dev/null && pwd -P)" || return 1
    case "$pai/" in "$dir"/*) return 0 ;; *) return 1 ;; esac
}

# Confere que <raiz>/<versao> e uma pasta de verdade (nao um link), dentro de <raiz>, com bin/painel. Uso: pasta_da_versao_valida <raiz> <versao>
pasta_da_versao_valida() {
    validar_versao "$2"
    [ -d "$1/$2" ] && [ ! -L "$1/$2" ] || erro "a pasta da versao $2 nao e uma pasta propria de $1"
    base="$(cd "$1" && pwd -P)"
    real="$(cd "$1/$2" && pwd -P)"
    [ "$real" = "$base/$2" ] || erro "a pasta da versao $2 sai de $1"
    # "bin" e "bin/painel" tambem tem de ser proprios (um link em bin levaria a execucao para fora da pasta da versao).
    [ -d "$real/bin" ] && [ ! -L "$real/bin" ] || erro "a versao $2 nao tem uma pasta bin propria"
    [ -f "$real/bin/painel" ] && [ ! -L "$real/bin/painel" ] || erro "a versao $2 nao tem bin/painel proprio"
}

# Grava o registro de forma atomica (temporario + mv). Uso: gravar_registro <raiz> <versao-anterior> [<nome-do-backup>]
gravar_registro() {
    tmp="$1/versao-anterior.txt.novo"
    {
        printf 'versao=%s\n' "$2"
        [ -z "${3:-}" ] || printf 'backup=%s\n' "$3"
    } > "$tmp"
    mv -f "$tmp" "$1/versao-anterior.txt"
}
