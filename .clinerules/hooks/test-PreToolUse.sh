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

# --- coerência com o guard.sh da Tarefa 11 (mesma leitura da flag, mesmas bordas) ---
caso " true " "$(cmd "curl -s $URL")" false "liberado: espaços nas pontas são ignorados"
caso True "$(cmd "curl -s $URL")" false "liberado: True (maiúscula inicial)"
for f in "tr ue" "true;" yes sim 0 truee; do
  caso "$f" "$(cmd "curl -s $URL")" true "trava: flag '$f' não libera (só true libera)"
  caso "$f" "$(mcp 'claude_ai_Atlassian_MCP' 'searchJiraIssuesUsingJql')" true "trava: MCP com flag '$f'"
done
caso false "$(cmd 'env JIRA_LIVE_ENABLED=true curl -s https://x.invalid/a')" true "trava: env liga a flag dentro do comando"
caso false "$(cmd 'export JIRA_LIVE_ENABLED=true')" true "trava: export liga a flag dentro do comando"
caso false "$(cmd '(JIRA_LIVE_ENABLED=true curl -s https://x.invalid/a)')" true "trava: ligar a flag em subshell"
caso false "$(cmd 'JIRA_LIVE_ENABLED="true" curl -s https://x.invalid/a')" true "trava: ligar a flag com aspas"
caso - "$(cmd 'JIRA_LIVE_ENABLED=true curl -s https://x.invalid/a')" true "trava: flag ausente, ligar dentro do comando"
caso true "$(cmd 'curl -s -H "Authorization: Bearer $JIRA_SAMSUNG_TOKEN" https://jira.exemplo.invalid/rest/api/2/myself')" false "liberado: token como argumento de outro comando com a flag true"
caso true "$(cmd 'JIRA_LIVE_ENABLED=true ./gradlew test')" false "liberado: ligar a flag no comando com a flag true no ambiente (igual ao guard.sh)"
caso true "$(cmd 'ls && echo $JIRA_SAMSUNG_TOKEN')" true "trava mesmo com true: exibir token depois de &&"
caso true "$(cmd 'env | grep -i jira')" true "trava mesmo com true: env canalizado para grep de jira"
caso " TRUE " "$(cmd 'echo $JIRA_SAMSUNG_TOKEN')" true "trava mesmo com TRUE e espaços: exibir token"

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

# --- 1) busca e listagem (search_files, list_files, list_code_definition_names) ---
sf() { jq -nc --arg p "$1" --arg r "$2" --arg g "$3" '{preToolUse:{toolName:"search_files",parameters:({path:$p,regex:$r} + (if $g == "-" then {} else {file_pattern:$g} end))}}'; }
lst() { jq -nc --arg t "$1" --arg p "$2" '{preToolUse:{toolName:$t,parameters:{path:$p,recursive:"true"}}}'; }
for f in - true; do
  caso "$f" "$(sf . 'JIRA_' '.env')" true "busca: file_pattern '.env'"
  caso "$f" "$(sf . 'TOKEN' '.env*')" true "busca: file_pattern '.env*'"
  caso "$f" "$(sf src '\.env' '*.kt')" true "busca: regex '\\.env' (mesmo com pattern seguro)"
  caso "$f" "$(sf app/config 'id:' '*.yaml')" true "busca: file_pattern '*.yaml' em app/config"
  caso "$f" "$(sf . 'TODO' '-')" true "busca: search_files na raiz sem file_pattern"
  caso "$f" "$(sf . 'JIRA_' '-')" true "busca: search_files '.' sem pattern (antes liberado)"
  caso "$f" "$(sf app/config 'x' '-')" true "busca: app/config sem file_pattern"
  caso "$f" "$(sf . 'x' 'papeis.yaml')" true "busca: file_pattern papeis.yaml exato"
  caso "$f" "$(sf . 'x' '*')" true "busca: file_pattern '*'"
  caso "$f" "$(sf . 'x' '*.{kt,yaml}')" true "busca: grupo com extensão insegura"
  caso "$f" "$(sf . 'x' '!*.kt')" true "busca: negação no pattern"
  caso "$f" "$(sf 'app/.ENV' 'x' '-')" true "busca: path .ENV"
  caso "$f" "$(sf . 'x' 'Filtros_Status_tool.txt')" true "busca: arquivo de filtros"
  caso "$f" "$(lst list_files 'app/.env')" true "listagem: list_files em .env"
  caso "$f" "$(lst list_code_definition_names 'config/Papeis.YAML')" true "listagem: definições em Papeis.YAML"
done
caso - "$(sf app/backend/src 'fun ' '*.kt')" false "não regressão: buscar em src com '*.kt'"
caso - "$(sf . 'TODO:' '*.{kt,tsx,md}')" false "não regressão: grupo de extensões seguras (regex com ':')"
caso - "$(sf app/config 'id' '*.exemplo.yaml')" false "não regressão: modelos .exemplo.yaml"
caso - "$(sf . 'JIRA_' '.env.example')" false "não regressão: buscar só no .env.example"
caso - "$(sf docs/README.md 'x' '-')" false "não regressão: busca num arquivo .md sem pattern"
caso - "$(lst list_files '.')" false "não regressão: list_files na raiz (só nomes)"
caso - "$(lst list_code_definition_names 'app/backend/src')" false "não regressão: definições do código"
caso - "$(ler 'reports/Filtros JQL para cards Jira.md')" false "não regressão: relatório com Filtros no nome"

# --- 2) e 3) escrita/leitura sem diferenciar maiúsculas e aliases do Windows ---
for f in - true; do
  caso "$f" "$(esc write_to_file '.ENV')" true "escrita: '.ENV'"
  caso "$f" "$(esc write_to_file '.Env.')" true "escrita: '.Env.' (ponto final ignorado no Windows)"
  caso "$f" "$(esc replace_in_file 'Papeis.YAML')" true "escrita: 'Papeis.YAML'"
  caso "$f" "$(esc write_to_file 'app\config\EQUIPES.yaml ')" true "escrita: EQUIPES.yaml com espaço final"
  caso "$f" "$(esc write_to_file '.env::$DATA')" true "escrita: '.env::\$DATA' (fluxo alternativo)"
  caso "$f" "$(esc write_to_file 'README.md:oculto')" true "escrita: ':' depois da unidade"
  caso "$f" "$(esc write_to_file '.env.')" true "escrita: '.env.'"
  caso "$f" "$(esc write_to_file 'app\ENV~1')" true "escrita: nome curto 'ENV~1'"
  caso "$f" "$(esc write_to_file '\\?\C:\x\.env')" true "escrita: prefixo '\\\\?\\'"
  caso "$f" "$(esc write_to_file '\\.\C:\x\.env')" true "escrita: prefixo '\\\\.\\'"
  caso "$f" "$(esc write_to_file 'app\x\..\..\.env')" true "escrita: '..' resolvido"
  caso "$f" "$(ler '..\.env')" true "leitura: '..\\.env'"
  caso "$f" "$(ler 'C:/x\app/.ENV.local')" true "leitura: barras mistas e maiúsculas"
  caso "$f" "$(ler 'Filtros_Status_tool.txt.bak')" true "leitura: arquivo de filtros"
  caso "$f" "$(ler 'C:\x\.env::$DATA')" true "leitura: fluxo alternativo"
  caso "$f" "$(cmd 'cat .ENV')" true "comando: cat .ENV"
  caso "$f" "$(cmd 'echo x > Papeis.YAML')" true "comando: redirecionar para Papeis.YAML"
  caso "$f" "$(cmd 'type .env::$DATA')" true "comando: type .env::\$DATA"
  caso "$f" "$(cmd 'type ENV~1')" true "comando: type ENV~1"
done
caso - "$(esc write_to_file '\\?\C:\x\README.md')" false "não regressão: prefixo '\\\\?\\' em arquivo comum"
caso - "$(esc write_to_file 'C:\x\app\.env.example')" false "não regressão: escrever .env.example com unidade"
caso - "$(ler '.env.example')" false "não regressão: ler .env.example"
caso - "$(cmd 'git log HEAD~1')" false "não regressão: HEAD~1 não é nome curto protegido"

# --- 4) falha fechada ---
caso - 'isto não é json' true "falha fechada: entrada inválida"
caso - '[1,2]' true "falha fechada: JSON que não é objeto"
caso - '{"tool_call":"x"}' true "falha fechada: tool_call em formato inesperado"
caso - '{"preToolUse":{"toolName":"read_file","parameters":7}}' false "falha fechada: parâmetros numéricos (sem caminho, nada a proteger)"
caso - '{"preToolUse":{"toolName":"search_files","parameters":"{nao json"}}' true "falha fechada: search_files com parâmetros ilegíveis"
# comando grande: com `printf | grep -q` e pipefail, o SIGPIPE virava liberação
grande=$(yes x | head -n 150000)
caso - "$(cmd "$(printf 'cat .env\n%s' "$grande")")" true "falha fechada: comando grande (SIGPIPE) lendo .env"
caso true "$(cmd "$(printf 'echo $JIRA_SAMSUNG_TOKEN\n%s' "$grande")")" true "falha fechada: token em comando grande"
caso - "$(esc write_to_file 'C:\Users\THIAGO~1\x\README.md')" false "não regressão: pasta com nome curto 8.3 (TEMP do Windows)"

echo "falhas: $falhas"
[ "$falhas" = 0 ]
