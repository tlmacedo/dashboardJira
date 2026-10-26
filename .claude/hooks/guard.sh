#!/bin/sh
# Trava do projeto: lê o JSON do hook no stdin e bloqueia (exit 2 + mensagem) ações proibidas.
# Modos: jira (ferramentas MCP e comandos que chegam ao Jira) | arquivos (segredos e configs com dados reais).
# Regra do projeto: a flag JIRA_LIVE_ENABLED é a única trava do acesso ao Jira, para pessoas e IAs.
# Com ela diferente de true, nenhum acesso ao Jira por nenhum caminho; só o usuário liga a flag.
#
# Leitura da flag: o hook lê JIRA_LIVE_ENABLED do PRÓPRIO AMBIENTE (o do processo do Claude Code), com a
# mesma regra do Settings do app: espaços nas pontas ignorados, true/false sem diferenciar maiúsculas.
# SÓ "true" libera o modo `jira`; ausente, vazia, false ou valor inválido = travado.
#   - Travado: bloqueia ferramentas MCP da Atlassian, variáveis JIRA_* (exceto a própria flag), URLs com
#     jira/atlassian, ferramentas de rede junto de "jira"/"atlassian", `jiradash check|sync|serve` e ligar a
#     flag dentro do comando (atribuir 1 ou true à flag no comando: a flag do comando nunca vale, só a do ambiente).
#   - Liberado (flag true): as regras acima deixam de bloquear. Continua barrado exibir segredo no terminal
#     (echo/printf/printenv/env/set/declare citando JIRA_*TOKEN|PASS|SENHA|SECRET, ou env/printenv/set
#     canalizado para grep de "jira"). Usar o token como argumento de outro comando é liberado.
#   - Modo `arquivos`: não muda, com qualquer valor da flag.
#   - Nos DOIS modos, com qualquer valor da flag: o texto que a ferramenta grava (Write content, Edit
#     new_string, edits[].new_string, NotebookEdit new_source) não pode ligar a flag, em QUALQUER arquivo.
#
# Arquivos que só o usuário altera (modo arquivos; ler continua liberado): .claude/settings*.json, todo
# *compose*.y*ml (inclusive o app/docker-compose.yml comum) e *compose*override*: todos ligam a flag.
#   - Write/Edit: o caminho é NORMALIZADO antes de casar (minúsculas, '\' -> '/', '//' colapsado, '.' e '..'
#     resolvidos, ponto/espaço final de cada segmento removido, prefixo de dispositivo e unidade retirados,
#     ':' depois da unidade bloqueia, nome curto 8.3 no arquivo, em CLAUDE~1 ou dentro de .claude bloqueia;
#     caminho relativo é resolvido contra o cwd do payload).
#   - Bash: NEGA POR PADRÃO. Se o comando cita esses arquivos (ou .claude junto de settings, que cobre
#     `cd .claude` + escrita em settings*), só passa se todo trecho for leitura conhecida (lista em LEITORES
#     e as ressalvas de less/sed/awk/git/docker) e não houver redirecionamento de escrita.
#
# Como o usuário liga a flag do hook (nunca o assistente): (a) exportar a variável no terminal antes de abrir
# o Claude Code, ou (b) `env` em .claude/settings.local.json (ignorado pelo git). Nunca no .claude/settings.json
# versionado. Vale depois de reabrir a sessão. ATENÇÃO: nas duas formas a flag entra no ambiente do próprio
# Claude Code e é herdada por TODO comando que ele roda (o docker compose a repassa ao painel, o gradlew a
# recebe): ligar a flag do hook liga a do app em tudo o que o Claude Code lançar.
#
# Limite honesto: é uma proteção contra ações acidentais do assistente, não uma fronteira de segurança contra
# quem quer burlar. Controle de rede de verdade exige sandbox ou lista de destinos permitidos no ambiente.
# Limites que NÃO serão fechados: (1) com a flag true, formas indiretas de exibir segredo (bash -c, env sem
# pipe, set -x, cat .env etc.) não são todas cobertas; (2) o WebFetch do Claude não passa por este hook;
# (3) com a flag false, atribuição calculada da flag (=$'true', ${X:-true}) não é coberta; (4) nomes montados
# por variável ou glob (cp x .cl*/set*, F=.cla; cp x ${F}ude/...) e interpretador que monta o caminho sozinho
# não são vistos; (5) falsos positivos aceitos: settings*.json de qualquer pasta (.vscode/settings.json),
# '>' dentro de aspas num comando que cita esses arquivos, e documentação que escreve a flag ligada na forma
# de atribuição (escreva "a flag em true"); (6) o matcher do .claude/settings.json decide quais ferramentas
# passam por aqui (hoje Write|Edit|NotebookEdit|Bash); só o usuário o altera.
modo="$1"
payload=$(cat)
negar() { echo "BLOQUEADO: $1" >&2; exit 2; }
# valor da flag: sem espaços nas pontas e em minúsculas (espaço no meio, como "tr ue", NÃO vira true)
flag=$(printf '%s' "${JIRA_LIVE_ENABLED:-}" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//' | tr '[:upper:]' '[:lower:]')
MSG_TRAVA="com JIRA_LIVE_ENABLED=false nenhum acesso ao Jira; só o usuário liga a flag"
NL='
'
CR=$(printf '\r')

# caminhos protegidos (com ou sem barra inicial); .env.example e .env.sample ficam de fora
PROTEGIDOS='(^|[[:space:]"'"'"'=/])(\.env(\.[A-Za-z0-9_-]+)?|config/(equipes|ausencias|papeis|squads)\.yaml)([[:space:]"'"'"';|&)]|$)'
# escrita, cópia, remoção ou versionamento (para .env e configs reais)
ESCRITA='(>|>>|tee[[:space:]]|sed[[:space:]]+(-[a-z]*i|--in-place)|(^|[[:space:];&|])(cp|mv|rm|ln|truncate|dd|install|chmod|python[0-9.]*|perl|node|ruby|php|pwsh|powershell|patch|rsync)[[:space:]]|git[[:space:]]+(add|commit|stash|checkout|restore|rm|mv|apply))'
# texto que liga a flag: JIRA_LIVE_ENABLED, depois ]/aspas/espaços/vírgula/:/= (e o -Value do PowerShell),
# valor 1 ou true. Usado SEM diferenciar maiúsculas no conteúdo gravado e em comando que escreve arquivo.
LIGA_FLAG='JIRA_LIVE_ENABLED[]"'"'"'[:space:],:=]+(-value[[:space:]]+)?["'"'"']?[[:space:]]*(1|true)'
# escrita genérica em comando (para o texto que liga a flag)
ESCRITA_GERAL='>|tee[[:space:]]|sed[[:space:]]+(-[a-z]*i|--in-place)|(^|[[:space:];&|(])(cp|mv|dd|install|python[0-9.]*|perl|node|ruby|php|pwsh|powershell|patch|rsync|set-content|add-content|out-file)[[:space:]]'
# 2>&1, >&2 e saída para /dev/null não gravam arquivo
REDIR_INOFENSIVA='s/[0-9]*>&[0-9-]+/ /g; s#[0-9&]*>>?[[:space:]]*/dev/null# #g'
# citações (comando já normalizado: minúsculas, '/', sem '//' nem '/./') dos arquivos que só o usuário altera
CITA_SETTINGS='settings[^[:space:]"'"'"';|&)<>/]*\.json'
CITA_CLAUDE='\.claude|claude~[0-9]'
CITA_SETTINGS_NOME='settings|settin~[0-9]'
CITA_COMPOSE='compose[^[:space:]"'"'"';|&)<>/]*(\.y[a-z]*ml|override)|(docker|compos)[a-z0-9_-]{0,6}~[0-9]'

eh_modelo() { case "$1" in .env.example|.env.sample) return 0 ;; *) return 1 ;; esac; }

# Texto gravado pela ferramenta (Write/Edit/MultiEdit/NotebookEdit): não pode ligar a flag, em nenhum arquivo.
# Falha fechada: JSON ilegível nega.
checar_conteudo() {
  conteudo=$(printf '%s' "$payload" | jq -r '[.tool_input.content?, .tool_input.new_string?, (.tool_input.edits? // [] | .[]? | .new_string?), .tool_input.new_source?] | map(strings) | join("\n")') ||
    negar "erro ao ler o conteúdo da ferramenta (falha fechada)."
  if [ -n "$conteudo" ] && printf '%s' "$conteudo" | grep -Eiq "$LIGA_FLAG"; then
    negar "a ferramenta grava texto que liga JIRA_LIVE_ENABLED (em qualquer arquivo). Só o usuário liga a flag."
  fi
}

# Normaliza um caminho. Resultado em $norm (segmentos unidos por '/', sem barra inicial); se o caminho deve
# ser bloqueado de saída (quebra de linha, ':', nome curto 8.3), o motivo fica em $motivo.
normalizar() {
  norm=""; motivo=""
  case "$1" in *"$NL"*|*"$CR"*) motivo="caminho com quebra de linha"; return ;; esac
  n=$(printf '%s' "$1" | tr '\\' '/' | tr '[:upper:]' '[:lower:]' | sed -E 's#^//[?.]/unc/#//#; s#^(//[?.]/|/[?][?]/)##; s#^[a-z]:##')
  case "$n" in *:*) motivo="caminho com ':' (fluxo alternativo do NTFS ou dispositivo)"; return ;; esac
  ifs_antigo=$IFS
  set -f
  IFS=/
  for seg in $n; do
    IFS=$ifs_antigo
    # espaços nas pontas; '.' e vazio somem; '..' volta um nível
    seg="${seg#"${seg%%[![:space:]]*}"}"
    seg="${seg%"${seg##*[![:space:]]}"}"
    case "$seg" in
      ''|.) continue ;;
      ..) case "$norm" in */*) norm="${norm%/*}" ;; *) norm="" ;; esac; continue ;;
    esac
    # o Windows ignora pontos e espaços no fim do nome (.env. e ".env " = .env)
    while :; do case "$seg" in *[.[:space:]]) seg="${seg%?}" ;; *) break ;; esac; done
    [ -n "$seg" ] || continue
    norm="${norm:+$norm/}$seg"
  done
  # nome curto 8.3: da pasta .claude (claude~1), de qualquer segmento dentro dela, ou do nome do arquivo
  sob_claude=""
  IFS=/
  for seg in $norm; do
    case "$seg" in claude~[0-9]*) motivo="nome curto 8.3 ($seg) pode ser a pasta .claude" ;; esac
    if [ -n "$sob_claude" ]; then
      case "$seg" in *~[0-9]*) motivo="nome curto 8.3 ($seg) dentro de .claude" ;; esac
    fi
    if [ "$seg" = .claude ]; then sob_claude=1; fi
  done
  IFS=$ifs_antigo
  set +f
  case "${norm##*/}" in *~[0-9]*) motivo="nome curto 8.3 (${norm##*/}) pode apontar para arquivo protegido" ;; esac
}

# Bash: se o comando cita settings do Claude, compose ou override, NEGA POR PADRÃO; só passa leitura conhecida.
so_usuario_comando() {
  t=$(printf '%s' "$1" | tr '\\' '/' | tr '[:upper:]' '[:lower:]' | sed -E 's#/+#/#g; s#/\./#/#g; s#/\./#/#g')
  cita=""
  if printf '%s' "$t" | grep -Eq "$CITA_SETTINGS|$CITA_COMPOSE"; then
    cita=1
  elif printf '%s' "$t" | grep -Eq "$CITA_CLAUDE" && printf '%s' "$t" | grep -Eq "$CITA_SETTINGS_NOME"; then
    cita=1
  fi
  [ -n "$cita" ] || return 0
  msg="comando cita settings do Claude, compose ou override do compose e não é leitura conhecida: só o usuário altera esses arquivos"
  s=$(printf '%s' "$t" | sed -E "$REDIR_INOFENSIVA")
  case "$s" in *">"*) negar "$msg (redirecionamento)." ;; esac
  # trechos separados por ; & | ( ) ` e quebra de linha; cada um precisa começar por leitura conhecida
  # (chaves não separam, para não partir scripts de awk; `{ cp ...; }` começa por '{' e é negado)
  trechos=$(printf '%s\n' "$s" | tr ';&|()`' '\n\n\n\n\n\n')
  while IFS= read -r g; do
    g="${g#"${g%%[![:space:]]*}"}"
    [ -n "$g" ] || continue
    # atribuições no início (VAR=valor cmd) não são o comando
    g=$(printf '%s' "$g" | sed -E 's/^([a-z_][a-z0-9_]*=[^[:space:]]*[[:space:]]+)+//')
    w="${g%%[[:space:]]*}"
    w=$(printf '%s' "$w" | tr -d "\"'")
    w="${w#\\}"
    w="${w##*/}"
    w="${w%.exe}"
    case "$w" in ''|'$') continue ;; esac
    case "$w" in
      cat|ls|grep|egrep|fgrep|rg|head|tail|diff|wc|stat|file|more|jq|cmp|readlink|realpath|basename|dirname|nl|shasum|sha256sum|md5|md5sum) ;;
      cd|pushd|popd|echo|printf|true|test) ;;
      type|get-content|gc|select-string|sls|findstr) ;;
      less)
        if printf '%s' "$g" | grep -Eq '(^|[[:space:]])(-[a-z]*o|--log-file)'; then negar "$msg (less -o grava arquivo)."; fi ;;
      sed)
        if printf '%s' "$g" | grep -Eq '(^|[[:space:]])(-[a-z]*i|--in-place)'; then negar "$msg (sed -i)."; fi
        # comandos w/W (grava) e e (executa) no script, depois de tirar as opções
        if printf '%s' "$g" | sed -E 's/[[:space:]]-[a-z]+/ /g' | grep -Eq "[;{}/0-9\$[:space:]'\"][we]([[:space:]'\"]|\$)"; then
          negar "$msg (sed com w ou e)."
        fi ;;
      awk|gawk|nawk)
        if printf '%s' "$g" | grep -Eq 'system|inplace|(^|[[:space:]])-i'; then negar "$msg (awk com system ou -i)."; fi ;;
      git)
        if printf '%s' "$g" | grep -q -- '--output'; then negar "$msg (git --output)."; fi
        sub=$(printf '%s' "$g" | sed -E 's/^git//; s/^([[:space:]]+(-c[[:space:]]+[^[:space:]]+|-[^[:space:]]+))*//; s/^[[:space:]]+//')
        sub="${sub%%[[:space:]]*}"
        case "$sub" in
          diff|log|show|status|blame|ls-files|grep|rev-parse|cat-file|shortlog) ;;
          *) negar "$msg (git $sub)." ;;
        esac ;;
      docker|docker-compose)
        if printf '%s' "$g" | grep -Eq '(^|[[:space:]])(cp|-o|--output)([[:space:]]|=|$)'; then negar "$msg (docker cp/-o)."; fi ;;
      *) negar "$msg ($w)." ;;
    esac
  done <<EOF
$trechos
EOF
}

case "$modo" in
jira)
  ferramenta=$(printf '%s' "$payload" | jq -r '.tool_name // ""')
  cmd=$(printf '%s' "$payload" | jq -r '.tool_input.command // ""')
  checar_conteudo
  if [ "$flag" = "true" ]; then
    # Flag ligada pelo usuário no ambiente: as regras de acesso não valem. Só barra exibir segredo no terminal
    # (echo/printf/printenv/env/set/declare no início ou depois de ; && || | ( com variável de segredo do Jira).
    if printf '%s' "$cmd" | grep -Eq '(^|[;&|(`])[[:space:]]*(echo|printf|printenv|env|set|declare)[[:space:]]([^;&|]*)JIRA_[A-Za-z0-9_]*(TOKEN|PASS|SENHA|SECRET)'; then
      negar "comando exibe token ou senha do Jira no terminal (vale mesmo com a flag em true). Use o token só como argumento de outro comando."
    fi
    # env/printenv/set sem argumento canalizado para grep de "jira" despeja o ambiente com os tokens
    if printf '%s' "$cmd" | grep -Eq '(^|[;&|(`])[[:space:]]*(env|printenv|set)[[:space:]]*\|[[:space:]]*grep[^;&|]*[Jj][Ii][Rr][Aa]'; then
      negar "comando despeja o ambiente filtrando por jira e mostraria tokens (vale mesmo com a flag em true)."
    fi
  else
    case "$ferramenta" in
      mcp__*tlassian*) negar "ferramenta do Atlassian/Jira ($ferramenta): $MSG_TRAVA." ;;
    esac
    # 1) uso das credenciais/URL do Jira em qualquer comando: barra QUALQUER JIRA_<letras/números/sublinhado>
    #    exceto a própria flag (JIRA_LIVE_ENABLED), que tem a regra 4 (ligar dentro do comando = bloqueado).
    if printf '%s' "$cmd" | grep -Eo 'JIRA_[A-Za-z0-9_]+' | grep -qvx 'JIRA_LIVE_ENABLED'; then
      negar "comando usa variáveis de conexão do Jira: $MSG_TRAVA."
    fi
    # 2) qualquer cliente de rede/linguagem com URL que cite Jira/Atlassian
    if printf '%s' "$cmd" | grep -Eiq 'https?://[^[:space:]"'"'"']*(jira|atlassian)'; then
      negar "comando referencia o endereço do Jira: $MSG_TRAVA."
    fi
    # 3) ferramentas de rede que mencionam Jira/Atlassian
    if printf '%s' "$cmd" | grep -Eiq '(^|[^[:alnum:]_-])(curl|wget|http|https|httpie|xh|nc|ncat|telnet|ssh|ftp|openssl)([[:space:]]|$)' &&
       printf '%s' "$cmd" | grep -Eiq 'jira|atlassian'; then
      negar "comando de rede para o Jira: $MSG_TRAVA."
    fi
    # 4) comandos do app que conectam ao Jira real e ligar a flag dentro do comando
    #    Reconhece também as formas JSON/YAML/dotenv (chave entre aspas, ':' ou '=', valor 1 ou true).
    if printf '%s' "$cmd" | grep -Eiq 'jiradash[[:space:]]+(check|sync|serve)|JIRA_LIVE_ENABLED["'"'"']?[[:space:]]*[:=][[:space:]]*["'"'"']?[[:space:]]*(1|true)'; then
      negar "comando que conecta ao Jira real ou liga a flag: $MSG_TRAVA."
    fi
  fi
  ;;
arquivos)
  checar_conteudo
  caminho=$(printf '%s' "$payload" | jq -r '.tool_input.file_path // .tool_input.notebook_path // ""') ||
    negar "erro ao ler o caminho (falha fechada)."
  if [ -n "$caminho" ]; then
    # caminho relativo: resolvido contra o cwd do payload (cd .claude e depois settings.local.json)
    cwd=$(printf '%s' "$payload" | jq -r '.cwd // ""')
    case "$caminho" in
      /*|\\*|[A-Za-z]:*) ;;
      *) [ -z "$cwd" ] || caminho="$cwd/$caminho" ;;
    esac
    normalizar "$caminho"
    [ -z "$motivo" ] || negar "escrita em $caminho: $motivo."
    base="${norm##*/}"
    # arquivos que ligam a flag (settings do Claude, compose e override): só o usuário edita, com qualquer conteúdo
    case "/$norm" in
      */.claude/settings*.json) negar "escrita em $base: só o usuário altera esse arquivo (ele pode ligar JIRA_LIVE_ENABLED)." ;;
    esac
    case "$base" in
      *compose*override*|*compose*.y*ml) negar "escrita em $base: só o usuário altera o compose (ele repassa JIRA_LIVE_ENABLED ao painel)." ;;
    esac
    if ! eh_modelo "$base"; then
      case "$base" in
        .env|.env.*) negar "edição de arquivo de ambiente ($base): contém segredos." ;;
      esac
    fi
    case "/$norm" in
      */config/equipes.yaml|*/config/ausencias.yaml|*/config/papeis.yaml|*/config/squads.yaml)
        negar "config com dados pessoais reais ($base). Edite só os modelos .exemplo/.demo." ;;
    esac
  fi
  cmd=$(printf '%s' "$payload" | jq -r '.tool_input.command // ""')
  if [ -n "$cmd" ]; then
    # tokens exatamente iguais aos modelos não contam como protegidos
    sem_modelos=$(printf '%s' "$cmd" | sed -E 's/(^|[[:space:]"'"'"'=/])\.env\.(example|sample)([[:space:]"'"'"';|&)]|$)/\1 \3/g')
    if printf '%s' "$sem_modelos" | grep -Eq "$PROTEGIDOS"; then
      if printf '%s' "$sem_modelos" | grep -Eq "$ESCRITA"; then
        negar "comando altera ou versiona segredo/config com dados reais."
      fi
    fi
    # settings do Claude, compose e override: nega por padrão (ler continua liberado)
    so_usuario_comando "$cmd"
    # gravar texto que liga a flag em qualquer arquivo (vale com qualquer valor da flag)
    if printf '%s' "$cmd" | grep -Eiq "$LIGA_FLAG" &&
       printf '%s' "$cmd" | sed -E "$REDIR_INOFENSIVA" | grep -Eiq "$ESCRITA_GERAL"; then
      negar "comando grava texto que liga JIRA_LIVE_ENABLED num arquivo. Só o usuário liga a flag."
    fi
    # git add forçado ignora o .gitignore
    # sem aspas; -f isolado ou agrupado (-fA, -Af) e --force/--fo/--forc, sempre no início de um argumento
    sem_aspas=$(printf '%s' "$cmd" | tr -d "\"'")
    if printf '%s' "$sem_aspas" | grep -Eq 'git[[:space:]]+add[[:space:]]([^;&|]*[[:space:]])?(-[A-Za-z]*f[A-Za-z]*|--fo[a-z]*)([[:space:]=]|$)'; then
      negar "git add forçado ignora o .gitignore (segredos e configs reais)."
    fi
  fi
  ;;
esac
exit 0
