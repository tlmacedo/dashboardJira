#!/bin/sh
# Trava do projeto: lê o JSON do hook no stdin e bloqueia (exit 2 + mensagem) ações proibidas.
# Modos: jira (ferramentas MCP e comandos que chegam ao Jira) | arquivos (segredos e configs com dados reais).
# Regra do projeto (temporária): nenhuma consulta ao Jira DC da empresa sem autorização expressa por pesquisa.
# Limite honesto: é uma proteção contra ações acidentais do assistente, não uma fronteira de segurança contra
# quem quer burlar. Controle de rede de verdade exige sandbox ou lista de destinos permitidos no ambiente.
modo="$1"
payload=$(cat)
negar() { echo "BLOQUEADO: $1" >&2; exit 2; }

# caminhos protegidos (com ou sem barra inicial); .env.example e .env.sample ficam de fora
PROTEGIDOS='(^|[[:space:]"'"'"'=/])(\.env(\.[A-Za-z0-9_-]+)?|config/(equipes|ausencias|papeis|squads)\.yaml)([[:space:]"'"'"';|&)]|$)'

eh_modelo() { case "$1" in .env.example|.env.sample) return 0 ;; *) return 1 ;; esac; }

case "$modo" in
jira)
  ferramenta=$(printf '%s' "$payload" | jq -r '.tool_name // ""')
  case "$ferramenta" in
    mcp__*tlassian*) negar "ferramenta do Atlassian/Jira ($ferramenta). Regra do projeto: nenhuma consulta ao Jira sem autorização expressa do usuário para cada pesquisa." ;;
  esac
  cmd=$(printf '%s' "$payload" | jq -r '.tool_input.command // ""')
  # 1) uso das credenciais/URL do Jira em qualquer comando
  if printf '%s' "$cmd" | grep -Eq 'JIRA_(BASE_URL|TOKEN|EMAIL|SIDIA)[A-Z_]*'; then
    negar "comando usa variáveis de conexão do Jira. Peça autorização expressa para essa pesquisa."
  fi
  # 2) qualquer cliente de rede/linguagem com URL que cite Jira/Atlassian
  if printf '%s' "$cmd" | grep -Eiq 'https?://[^[:space:]"'"'"']*(jira|atlassian)'; then
    negar "comando referencia o endereço do Jira. Regra do projeto: nenhuma consulta sem autorização expressa."
  fi
  # 3) ferramentas de rede que mencionam Jira/Atlassian
  if printf '%s' "$cmd" | grep -Eiq '(^|[^[:alnum:]_-])(curl|wget|http|https|httpie|xh|nc|ncat|telnet|ssh|ftp|openssl)([[:space:]]|$)' &&
     printf '%s' "$cmd" | grep -Eiq 'jira|atlassian'; then
    negar "comando de rede para o Jira. Regra do projeto: nenhuma consulta ao Jira sem autorização expressa."
  fi
  # 4) comandos do app que conectam ao Jira real
  if printf '%s' "$cmd" | grep -Eiq 'jiradash[[:space:]]+(check|sync|serve)|JIRA_LIVE_ENABLED=(1|true)'; then
    negar "comando que conecta ao Jira real. Peça autorização expressa ao usuário para essa pesquisa."
  fi
  ;;
arquivos)
  caminho=$(printf '%s' "$payload" | jq -r '.tool_input.file_path // .tool_input.notebook_path // ""')
  if [ -n "$caminho" ]; then
    base=$(basename "$caminho")
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
    # git add forçado ignora o .gitignore
    if printf '%s' "$cmd" | grep -Eq 'git[[:space:]]+add[[:space:]].*(-f|--force)'; then
      negar "git add forçado ignora o .gitignore (segredos e configs reais)."
    fi
  fi
  ;;
esac
exit 0
