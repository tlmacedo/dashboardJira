#!/bin/sh
# Testes do hook PreToolUse do Cline (.clinerules/hooks/PreToolUse).
# Cada caso informa a flag no ambiente do hook, o JSON de entrada e o resultado esperado
# (cancel true = bloqueado, false = liberado). A flag é definida AQUI, por caso, e nunca na linha de comando.
# Uso: sh .clinerules/hooks/test-PreToolUse.sh   (precisa de jq e bash)
# No Windows o Cline usa o PreToolUse.ps1; os testes dele estão em test-PreToolUse.ps1 (mesmos casos).
dir=$(cd "$(dirname "$0")" && pwd)
hook="$dir/PreToolUse"
falhas=0

caso() { # flag ("-" = ausente), JSON, esperado (true|false), descrição
  if [ "$1" = "-" ]; then
    saida=$(printf '%s' "$2" | env -u JIRA_LIVE_ENABLED bash "$hook" 2>/dev/null)
  else
    saida=$(printf '%s' "$2" | env JIRA_LIVE_ENABLED="$1" bash "$hook" 2>/dev/null)
  fi
  r=$?
  obtido=$(printf '%s' "$saida" | jq -r '.cancel' 2>/dev/null)
  if [ "$r" = 0 ] && [ "$obtido" = "$3" ]; then
    echo "ok    [flag=$1] $4"
  else
    echo "FALHA [flag=$1] $4 (exit $r, cancel=$obtido, esperado $3; saída: $saida)"
    falhas=$((falhas+1))
  fi
}
# payload da extensão VS Code: parâmetros como objeto
cmd() { jq -nc --arg c "$1" '{taskId:"t",preToolUse:{toolName:"execute_command",parameters:{command:$c,requires_approval:"false"}}}'; }
# payload da extensão com parâmetros em texto JSON (o hook precisa desfazer)
cmd_txt() { jq -nc --arg c "$1" '{taskId:"t",preToolUse:{toolName:"execute_command",parameters:({command:$c}|tojson)}}'; }
# payload da CLI/SDK
cmd_cli() { jq -nc --arg c "$1" '{tool_call:{name:"run_commands",input:{commands:[$c]}}}'; }
esc() { jq -nc --arg t "$1" --arg p "$2" '{preToolUse:{toolName:$t,parameters:{path:$p,content:"x"}}}'; }
mcp() { jq -nc --arg s "$1" --arg t "$2" --arg a "${3:-{\}}" '{preToolUse:{toolName:"use_mcp_tool",parameters:{server_name:$s,tool_name:$t,arguments:$a}}}'; }
ler() { jq -nc --arg p "$1" '{preToolUse:{toolName:"read_file",parameters:{path:$p}}}'; }
web() { jq -nc --arg t "$1" --arg u "$2" '{preToolUse:{toolName:$t,parameters:{url:$u}}}'; }
nav() { jq -nc --arg u "$1" '{preToolUse:{toolName:"browser_action",parameters:{action:"launch",url:$u}}}'; }
busca() { jq -nc --arg q "$1" '{preToolUse:{toolName:"web_search",parameters:{query:$q}}}'; }

URL='https://jira.exemplo.invalid/rest/api/2/search'

# --- trava ativa (flag ausente, false ou inválida) ---
for f in - false FALSE talvez " " 1; do
  caso "$f" "$(cmd "curl -s $URL")" true "trava: curl para o Jira"
  caso "$f" "$(cmd 'JIRA_LIVE_ENABLED=true ./gradlew test')" true "trava: ligar a flag dentro do comando"
  caso "$f" "$(mcp 'claude_ai_Atlassian_MCP' 'searchJiraIssuesUsingJql')" true "trava: MCP do Atlassian"
  caso "$f" "$(web web_fetch 'https://docs.atlassian.com/jira/rest')" true "trava: web_fetch para documentação pública da Atlassian (efeito aceito pelo usuário)"
  caso "$f" "$(nav 'https://empresa.invalid/JIRA/browse/X-1')" true "trava: navegador com JIRA no caminho (sem diferenciar maiúsculas)"
done
caso - "$(mcp fetch fetch '{"url":"https://x.atlassian.net/wiki"}')" true "trava: MCP de fetch com URL da Atlassian"
caso - "$(cmd 'Invoke-WebRequest -Uri https://jira.exemplo.invalid/x')" true "trava: Invoke-WebRequest"
caso - "$(cmd 'iwr jira.exemplo.invalid')" true "trava: iwr sem esquema na URL"
caso - "$(cmd 'setx JIRA_LIVE_ENABLED true')" true "trava: ligar a flag com setx"
caso - "$(cmd "[Environment]::SetEnvironmentVariable('JIRA_LIVE_ENABLED','true','User')")" true "trava: ligar a flag com SetEnvironmentVariable"
caso - "$(nav 'file:///Users/x/dashboardJira/docs/design/prototipo/a.html')" true "falso positivo aceito: pasta com Jira no nome (clone em pasta sem jira)"
caso - "$(cmd_txt "curl -s $URL")" true "trava: parâmetros em texto JSON"
caso - "$(cmd_cli "wget $URL")" true "trava: formato da CLI (run_commands)"
caso - "$(cmd 'echo $JIRA_TOKEN')" true "trava: variável de conexão do Jira"
caso - "$(mcp 'jira-local' 'search')" true "trava: servidor MCP com jira no nome"
caso false "$(cmd 'JIRA_LIVE_ENABLED=false ./gradlew test')" false "liberado: flag false no comando não liga nada"

# --- liberado (flag true, sem diferenciar maiúsculas) ---
caso true "$(cmd "curl -s $URL")" false "liberado: curl para o Jira com a flag true"
caso TRUE "$(cmd "curl -s $URL")" false "liberado: TRUE vale como true (igual ao Settings.kt)"
caso true "$(mcp 'claude_ai_Atlassian_MCP' 'searchJiraIssuesUsingJql')" false "liberado: MCP do Atlassian com a flag true"
caso true "$(web web_fetch 'https://docs.atlassian.com/jira/rest')" false "liberado: web_fetch da Atlassian com a flag true"
caso true "$(nav 'https://jira.exemplo.invalid/browse/X-1')" false "liberado: navegador no Jira com a flag true"
caso true "$(cmd 'Invoke-WebRequest https://jira.exemplo.invalid/x')" false "liberado: Invoke-WebRequest com a flag true"
caso true "$(cmd 'echo $JIRA_SAMSUNG_TOKEN')" true "trava mesmo com true: exibir token (Plano 4A, Decisão 10)"
caso true "$(cmd 'printenv JIRA_SIDIA_PASSWORD')" true "trava mesmo com true: exibir senha"
caso true "$(cmd 'cd app/backend && ./gradlew run')" false "liberado: subir o app com a flag true"

# --- comandos comuns sempre liberados ---
caso - "$(cmd 'ls -la')" false "liberado: ls"
caso - "$(cmd 'cd app/backend && ./gradlew test')" false "liberado: testes do backend"
caso - "$(mcp 'context7' 'get-library-docs')" false "liberado: outro servidor MCP"
caso - '{"preToolUse":{"toolName":"read_file","parameters":{"path":"README.md"}}}' false "liberado: leitura comum"
caso - "$(ler 'app/.env.example')" false "liberado: leitura do modelo .env.example"
caso - "$(ler 'config/papeis.exemplo.yaml')" false "liberado: leitura do modelo papeis.exemplo.yaml"
caso - "$(cmd 'cat config/equipes.exemplo.yaml')" false "liberado: cat do modelo"
caso - "$(web web_fetch 'https://docs.cline.bot/')" false "liberado: web_fetch fora do Jira"
caso - "$(nav 'http://127.0.0.1:8080/')" false "liberado: navegador no painel local"
caso - "$(busca 'jira rest api search jql')" false "liberado: web_search (busca genérica) mesmo citando jira"
caso - "$(cmd 'env FOO=1 ./gradlew test')" false "liberado: env como prefixo de comando"
caso - "$(cmd 'printenv PATH')" false "liberado: printenv de uma variável comum"

# --- listar todo o ambiente: barrado com qualquer valor da flag ---
for f in - true; do
  caso "$f" "$(cmd 'env')" true "ambiente: env sozinho"
  caso "$f" "$(cmd 'printenv')" true "ambiente: printenv sozinho"
  caso "$f" "$(cmd 'set')" true "ambiente: set sozinho"
  caso "$f" "$(cmd 'Get-ChildItem env:')" true "ambiente: Get-ChildItem env:"
done

# --- arquivos protegidos: valem com QUALQUER valor da flag ---
for f in - true; do
  caso "$f" "$(esc write_to_file 'app/.env')" true "arquivos: escrever .env"
  caso "$f" "$(esc replace_in_file 'config/papeis.yaml')" true "arquivos: editar papeis.yaml"
  caso "$f" "$(cmd 'git add -f .env.example')" true "arquivos: git add forçado"
  caso "$f" "$(cmd 'echo x > .env')" true "arquivos: redirecionar para .env"
  caso "$f" "$(ler '.env')" true "arquivos: ler .env (extra do Cline)"
  caso "$f" "$(ler 'app\.env.local')" true "arquivos: ler .env.local com barra invertida"
  caso "$f" "$(ler 'config/ausencias.yaml')" true "arquivos: ler ausencias.yaml"
  caso "$f" "$(cmd 'cat .env')" true "arquivos: cat .env"
  caso "$f" "$(cmd 'Get-Content app\.env')" true "arquivos: Get-Content .env"
  caso "$f" "$(cmd 'type config\papeis.yaml')" true "arquivos: type papeis.yaml"
done
caso - "$(esc write_to_file 'app/.env.example')" false "arquivos: modelo .env.example liberado"
caso - "$(esc write_to_file 'docs/ferramentas-claude-para-o-cline/README.md')" false "arquivos: doc comum liberada"
caso - '{"preToolUse":{"toolName":"apply_patch","parameters":{"input":"*** Begin Patch\n*** Update File: config/equipes.yaml\n@@\n-a\n+b\n*** End Patch"}}}' true "arquivos: apply_patch em equipes.yaml"

# --- falha fechada ---
caso - 'isto não é json' true "falha fechada: entrada inválida"

echo "falhas: $falhas"
[ "$falhas" = 0 ]
