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
#     flag dentro do comando (JIRA_LIVE_ENABLED=true ...: a flag do comando nunca vale, só a do ambiente).
#   - Liberado (flag true): as regras acima deixam de bloquear. Continua barrado exibir segredo no terminal
#     (echo/printf/printenv/env/set/declare citando JIRA_*TOKEN|PASS|SENHA|SECRET, ou env/printenv/set
#     canalizado para grep de "jira"). Usar o token como argumento de outro comando é liberado.
#   - Modo `arquivos`: não muda, com qualquer valor da flag.
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
# (3) com a flag false, atribuição calculada da flag (=$'true', ${X:-true}) não é coberta.
# Escrita em .claude/settings*.json e em overrides do compose é barrada por caminho (modo arquivos), pois
# esses arquivos ligam a flag; só o usuário os edita.
modo="$1"
payload=$(cat)
negar() { echo "BLOQUEADO: $1" >&2; exit 2; }
# valor da flag: sem espaços nas pontas e em minúsculas (espaço no meio, como "tr ue", NÃO vira true)
flag=$(printf '%s' "${JIRA_LIVE_ENABLED:-}" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//' | tr '[:upper:]' '[:lower:]')
MSG_TRAVA="com JIRA_LIVE_ENABLED=false nenhum acesso ao Jira; só o usuário liga a flag"

# caminhos protegidos (com ou sem barra inicial); .env.example e .env.sample ficam de fora
PROTEGIDOS='(^|[[:space:]"'"'"'=/])(\.env(\.[A-Za-z0-9_-]+)?|config/(equipes|ausencias|papeis|squads)\.yaml)([[:space:]"'"'"';|&)]|$)'

eh_modelo() { case "$1" in .env.example|.env.sample) return 0 ;; *) return 1 ;; esac; }

case "$modo" in
jira)
  ferramenta=$(printf '%s' "$payload" | jq -r '.tool_name // ""')
  cmd=$(printf '%s' "$payload" | jq -r '.tool_input.command // ""')
  if [ "$flag" = "true" ]; then
    # Flag ligada pelo usuário no ambiente: as regras de acesso não valem. Só barra exibir segredo no terminal
    # (echo/printf/printenv/env/set/declare no início ou depois de ; && || | ( com variável de segredo do Jira).
    if printf '%s' "$cmd" | grep -Eq '(^|[;&|(`])[[:space:]]*(echo|printf|printenv|env|set|declare)[[:space:]]([^;&|]*)JIRA_[A-Za-z0-9_]*(TOKEN|PASS|SENHA|SECRET)'; then
      negar "comando exibe token ou senha do Jira no terminal (vale mesmo com JIRA_LIVE_ENABLED=true). Use o token só como argumento de outro comando."
    fi
    # env/printenv/set sem argumento canalizado para grep de "jira" despeja o ambiente com os tokens
    if printf '%s' "$cmd" | grep -Eq '(^|[;&|(`])[[:space:]]*(env|printenv|set)[[:space:]]*\|[[:space:]]*grep[^;&|]*[Jj][Ii][Rr][Aa]'; then
      negar "comando despeja o ambiente filtrando por jira e mostraria tokens (vale mesmo com JIRA_LIVE_ENABLED=true)."
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
    #    Reconhece também as formas JSON/YAML/dotenv ("JIRA_LIVE_ENABLED": "true", JIRA_LIVE_ENABLED: true).
    if printf '%s' "$cmd" | grep -Eiq 'jiradash[[:space:]]+(check|sync|serve)|JIRA_LIVE_ENABLED["'"'"']?[[:space:]]*[:=][[:space:]]*["'"'"']?[[:space:]]*(1|true)'; then
      negar "comando que conecta ao Jira real ou liga a flag: $MSG_TRAVA."
    fi
  fi
  ;;
arquivos)
  caminho=$(printf '%s' "$payload" | jq -r '.tool_input.file_path // .tool_input.notebook_path // ""')
  if [ -n "$caminho" ]; then
    base=$(basename "$caminho")
    # arquivos que ligam a flag (settings do Claude e override do compose): só o usuário edita, com qualquer conteúdo
    baixo=$(printf '%s' "$caminho" | tr '[:upper:]' '[:lower:]')
    case "$baixo" in
      .claude/settings*.json|*/.claude/settings*.json|*compose*override*|*compose*.override*)
        negar "escrita em $base: só o usuário altera esse arquivo (ele pode ligar JIRA_LIVE_ENABLED)." ;;
    esac
    if ! eh_modelo "$base"; then
      case "$base" in
        .env|.env.*) negar "edição de arquivo de ambiente ($base): contém segredos." ;;
      esac
    fi
    case "$caminho" in
      */config/equipes.yaml|*/config/ausencias.yaml|*/config/papeis.yaml|*/config/squads.yaml|config/equipes.yaml|config/ausencias.yaml|config/papeis.yaml|config/squads.yaml)
        negar "config com dados pessoais reais ($base). Edite só os modelos .exemplo/.demo." ;;
    esac
  fi
  cmd=$(printf '%s' "$payload" | jq -r '.tool_input.command // ""')
  if [ -n "$cmd" ]; then
    # tokens exatamente iguais aos modelos não contam como protegidos
    sem_modelos=$(printf '%s' "$cmd" | sed -E 's/(^|[[:space:]"'"'"'=/])\.env\.(example|sample)([[:space:]"'"'"';|&)]|$)/\1 \3/g')
    if printf '%s' "$sem_modelos" | grep -Eq "$PROTEGIDOS"; then
      # escrita, cópia, remoção ou versionamento dos protegidos
      if printf '%s' "$sem_modelos" | grep -Eq '(>|>>|tee[[:space:]]|sed[[:space:]]+(-[a-z]*i|--in-place)|(^|[[:space:];&|])(cp|mv|rm|ln|truncate|dd|install|chmod)[[:space:]]|git[[:space:]]+(add|commit|stash|checkout|restore|rm|mv))'; then
        negar "comando altera ou versiona segredo/config com dados reais."
      fi
    fi
    # settings do Claude e override do compose: qualquer escrita por comando é barrada (ler continua liberado)
    if printf '%s' "$cmd" | grep -Eiq '\.claude/settings[^[:space:]"'"'"';|&)]*\.json|compose[^[:space:]"'"'"';|&)]*override'; then
      if printf '%s' "$cmd" | grep -Eq '(>|>>|tee[[:space:]]|sed[[:space:]]+(-[a-z]*i|--in-place)|(^|[[:space:];&|(])(cp|mv|rm|ln|truncate|dd|install|chmod|touch)[[:space:]])'; then
        negar "comando escreve em settings do Claude ou override do compose: só o usuário altera esse arquivo."
      fi
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
