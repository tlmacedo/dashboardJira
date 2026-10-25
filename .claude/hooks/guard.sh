#!/bin/sh
# Trava do projeto: lê o JSON do hook no stdin e bloqueia (exit 2 + mensagem) ações proibidas.
# Modos: jira (ferramentas MCP e comandos que chegam ao Jira) | arquivos (segredos e configs com dados reais).
# Regra do projeto (temporária): nenhuma consulta ao Jira DC da empresa sem autorização expressa por pesquisa.
modo="$1"
payload=$(cat)
negar() { echo "BLOQUEADO: $1" >&2; exit 2; }

case "$modo" in
jira)
  ferramenta=$(printf '%s' "$payload" | jq -r '.tool_name // ""')
  case "$ferramenta" in
    mcp__*tlassian*) negar "ferramenta do Atlassian/Jira ($ferramenta). Regra do projeto: nenhuma consulta ao Jira sem autorização expressa do usuário para cada pesquisa." ;;
  esac
  cmd=$(printf '%s' "$payload" | jq -r '.tool_input.command // ""')
  # comandos de rede que mencionam Jira/Atlassian ou a URL do Jira
  if printf '%s' "$cmd" | grep -Eiq '(^|[^[:alnum:]_-])(curl|wget|http|https|httpie|xh)([[:space:]]|$)' &&
     printf '%s' "$cmd" | grep -Eiq 'jira|atlassian'; then
    negar "comando de rede para o Jira. Regra do projeto: nenhuma consulta ao Jira sem autorização expressa."
  fi
  # comandos do app que conectam ao Jira real
  if printf '%s' "$cmd" | grep -Eiq 'jiradash[[:space:]]+(check|sync)|JIRA_LIVE_ENABLED=(1|true)'; then
    negar "comando que conecta ao Jira real. Peça autorização expressa ao usuário para essa pesquisa."
  fi
  ;;
arquivos)
  caminho=$(printf '%s' "$payload" | jq -r '.tool_input.file_path // .tool_input.notebook_path // ""')
  if [ -n "$caminho" ]; then
    base=$(basename "$caminho")
    case "$base" in
      .env.example|.env.sample) : ;;
      .env|.env.*) negar "edição de arquivo de ambiente ($base): contém segredos." ;;
    esac
    case "$caminho" in
      */config/equipes.yaml|*/config/ausencias.yaml|*/config/papeis.yaml|*/config/squads.yaml)
        negar "config com dados pessoais reais ($base). Edite só os modelos .exemplo/.demo." ;;
    esac
  fi
  cmd=$(printf '%s' "$payload" | jq -r '.tool_input.command // ""')
  if printf '%s' "$cmd" | grep -Eq 'git[[:space:]]+(add|commit)' &&
     printf '%s' "$cmd" | grep -Eq '(^|[[:space:]/])\.env([[:space:]]|$|\.[a-z]+)|config/(equipes|ausencias|papeis|squads)\.yaml'; then
    case "$cmd" in *.env.example*) : ;; *) negar "commit/add de segredo ou config com dados reais." ;; esac
  fi
  ;;
esac
exit 0
