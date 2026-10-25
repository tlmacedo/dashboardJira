#!/bin/sh
# Testes do guard.sh: cada caso informa o código de saída esperado (2 = bloqueado, 0 = liberado).
# Uso: sh .claude/hooks/test-guard.sh
dir=$(dirname "$0")
falhas=0
caso() { # modo, JSON, esperado, descrição
  printf '%s' "$2" | "$dir/guard.sh" "$1" >/dev/null 2>&1
  r=$?
  if [ "$r" = "$3" ]; then echo "ok    $4"; else echo "FALHA $4 (saiu $r, esperado $3)"; falhas=$((falhas+1)); fi
}
bash_json() { jq -nc --arg c "$1" '{tool_name:"Bash",tool_input:{command:$c}}'; }
j() { caso jira "$(bash_json "$1")" "$2" "jira: $1"; }
a() { caso arquivos "$(bash_json "$1")" "$2" "arquivos: $1"; }

j 'curl -s https://jira.exemplo.com/rest/api/2/search' 2
j 'python3 -c "import urllib.request as u;u.urlopen(\"https://x.atlassian.net/a\")"' 2
j 'echo $JIRA_TOKEN' 2
j 'echo $JIRA_SAMSUNG_TOKEN' 2
j 'printenv JIRA_SIDIA_URL' 2
j 'JIRA_LIVE_ENABLED=false ./gradlew test' 0
j 'JIRA_LIVE_ENABLED=true ./gradlew test' 2
j 'JIRA_LIVE_ENABLED=1 ./gradlew test' 2
j 'JIRA_LIVE_ENABLED="true" ./gradlew test' 2
j "JIRA_LIVE_ENABLED='true' ./gradlew test" 2
j 'JIRA_LIVE_ENABLED = true ./gradlew test' 2
j 'export JIRA_LIVE_ENABLED=" true"' 2
j 'JIRA_LIVE_ENABLED=TRUE ./gradlew test' 2
j 'env JIRA_LIVE_ENABLED=true ./gradlew test' 2
j 'JIRA_LIVE_ENABLED_X=1 ./gradlew test' 2
j 'JIRA_LIVE_ENABLED="false" ./gradlew test' 0
j 'python -m jiradash serve' 2
j 'ls -la' 0
j 'curl -s https://example.com/x.png' 0
j 'python -m pytest tests/test_jira_client.py' 0
caso jira '{"tool_name":"mcp__claude_ai_Atlassian_MCP__searchJiraIssuesUsingJql","tool_input":{}}' 2 "jira: ferramenta MCP Atlassian"

# Falsos positivos ACEITOS: o guard erra para o lado seguro e bloqueia comandos inofensivos.
caso jira "$(bash_json 'grep -n JIRA_SAMSUNG_TOKEN app/backend/src/main/kotlin/br/sidia/painel/config/Settings.kt')" 2 "falso positivo aceito: grep que cita a variável do token Samsung é bloqueado"
caso jira "$(bash_json 'curl -s http://127.0.0.1:8080/api/health # em /Volumes/Development/SIDIA/dashboardJira')" 2 "falso positivo aceito: curl local junto de caminho com dashboardJira é bloqueado"

# Limites DOCUMENTADOS: burlas deliberadas que o guard não consegue ver (o guard é trava de acidente, não de má-fé).
caso jira "$(bash_json 'JIRA_LIVE_ENABLED=$(echo true) ./gradlew test')" 0 "limite documentado: JIRA_LIVE_ENABLED=\$(echo true) passa (valor calculado em tempo de execução)"

a 'echo x > .env' 2
a 'git add .env' 2
a 'git add -f .env.example' 2
a 'git add .env.example' 0
a 'git add docs/superpowers/plans/plano-01-fundacao.md' 0
a 'git add --force x' 2
a 'git add x -f' 2
a 'git add -fA' 2
a 'git add -Af x' 2
a 'git add --forc x' 2
a "git add '-f' x" 2
a 'git add -A' 0
a 'git add .env.example .env.production' 2
a 'cp .env.example .env' 2
a 'sed -i s/a/b/ config/equipes.yaml' 2
a 'cat config/equipes.exemplo.yaml' 0
a 'git status' 0
a 'git commit -m "docs: nota sobre arquivos de ambiente"' 0
caso arquivos '{"tool_name":"Edit","tool_input":{"file_path":"/x/app/.env"}}' 2 "arquivos: Edit .env"
caso arquivos '{"tool_name":"Edit","tool_input":{"file_path":".env.example"}}' 0 "arquivos: Edit .env.example"
caso arquivos '{"tool_name":"Write","tool_input":{"file_path":"config/equipes.yaml"}}' 2 "arquivos: Write equipes.yaml"

echo "falhas: $falhas"
[ "$falhas" = 0 ]
