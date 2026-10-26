#!/bin/sh
# Testes do guard.sh: cada caso informa o código de saída esperado (2 = bloqueado, 0 = liberado).
# Uso: sh .claude/hooks/test-guard.sh
dir=$(dirname "$0")
falhas=0
resultado() { # esperado, descrição, código obtido
  if [ "$3" = "$1" ]; then echo "ok    $2"; else echo "FALHA $2 (saiu $3, esperado $1)"; falhas=$((falhas+1)); fi
}
# A flag do ambiente é REMOVIDA por padrão: nenhum caso depende do terminal de quem roda o teste.
caso() { # modo, JSON, esperado, descrição
  printf '%s' "$2" | env -u JIRA_LIVE_ENABLED "$dir/guard.sh" "$1" >/dev/null 2>&1
  resultado "$3" "$4" $?
}
casov() { # valor da flag, modo, JSON, esperado, descrição (a flag é definida explicitamente)
  printf '%s' "$3" | env JIRA_LIVE_ENABLED="$1" "$dir/guard.sh" "$2" >/dev/null 2>&1
  resultado "$4" "$5" $?
}
bash_json() { jq -nc --arg c "$1" '{tool_name:"Bash",tool_input:{command:$c}}'; }
j() { caso jira "$(bash_json "$1")" "$2" "jira: $1"; }
a() { caso arquivos "$(bash_json "$1")" "$2" "arquivos: $1"; }
jt() { casov true jira "$(bash_json "$1")" "$2" "jira [flag=true]: $1"; }     # flag true

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
j 'SYNC_INTERVALO_MIN=10 ./gradlew test' 0
j 'DASHBOARD_DEMO_DATA=true docker compose up --build -d' 0
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

# Travado (flag false ou inválida): nada fica mais permissivo do que sempre foi.
URL_J='https://jira.exemplo.invalid/rest/api/2/myself'
MCP_ATL='{"tool_name":"mcp__claude_ai_Atlassian_MCP__searchJiraIssuesUsingJql","tool_input":{}}'
for v in false sim; do
  casov "$v" jira "$(bash_json "curl -s $URL_J")" 2 "trava [flag=$v]: curl para o Jira"
  casov "$v" jira "$MCP_ATL" 2 "trava [flag=$v]: MCP Atlassian"
  casov "$v" jira "$(bash_json 'JIRA_LIVE_ENABLED=true ./gradlew test')" 2 "trava [flag=$v]: ligar a flag dentro do comando"
  casov "$v" jira "$(bash_json 'echo $JIRA_SAMSUNG_TOKEN')" 2 "trava [flag=$v]: exibir token"
  casov "$v" jira "$(bash_json 'JIRA_LIVE_ENABLED=false ./gradlew test')" 0 "trava [flag=$v]: comando comum com a flag false no comando"
done
# Valores que NÃO liberam: ausente, vazia, 1, yes, sim, true com lixo, espaço no meio, true seguido de ponto e vírgula.
for v in "" 1 yes sim "true;" "tr ue" "truee" "0" "false"; do
  casov "$v" jira "$(bash_json "curl -s $URL_J")" 2 "trava [flag='$v']: curl para o Jira"
  casov "$v" jira "$MCP_ATL" 2 "trava [flag='$v']: MCP Atlassian"
done
caso jira "$(bash_json "curl -s $URL_J")" 2 "trava [flag ausente]: curl para o Jira"
caso jira "$MCP_ATL" 2 "trava [flag ausente]: MCP Atlassian"
caso jira "$(bash_json 'JIRA_LIVE_ENABLED=true ./gradlew test')" 2 "trava [flag ausente]: ligar a flag dentro do comando"
casov false jira "$(bash_json 'env JIRA_LIVE_ENABLED=true curl -s https://x.invalid/a')" 2 "trava [flag=false]: env liga a flag no comando"
casov false jira "$(bash_json 'export JIRA_LIVE_ENABLED=true')" 2 "trava [flag=false]: export liga a flag"
casov false jira "$(bash_json '(JIRA_LIVE_ENABLED=true curl -s https://x.invalid/a)')" 2 "trava [flag=false]: ligar em subshell"
casov false jira "$(bash_json 'JIRA_LIVE_ENABLED="true" curl -s https://x.invalid/a')" 2 "trava [flag=false]: ligar com aspas"
casov "" jira "$(bash_json 'JIRA_LIVE_ENABLED=true curl -s https://x.invalid/a')" 2 "trava [flag vazia]: ligar a flag dentro do comando"

# Liberado (flag true no ambiente do hook, sem diferenciar maiúsculas e ignorando espaços nas pontas)
jt "curl -s $URL_J" 0
casov true jira "$MCP_ATL" 0 "liberado [flag=true]: MCP Atlassian"
jt 'JIRA_LIVE_ENABLED=true ./gradlew test' 0
jt 'curl -s -H "Authorization: Bearer $JIRA_SAMSUNG_TOKEN" https://jira.exemplo.invalid/rest/api/2/myself' 0
jt 'python -m jiradash sync' 0
# continua barrado mesmo com a flag true: exibir segredo no terminal
jt 'echo $JIRA_SAMSUNG_TOKEN' 2
jt 'printenv JIRA_SIDIA_TOKEN' 2
jt 'printf "%s" "$JIRA_SIDIA_PASSWORD"' 2
jt 'declare -p JIRA_SIDIA_SECRET' 2
jt 'ls && echo $JIRA_SAMSUNG_TOKEN' 2
jt 'ls | env | grep -i jira' 2
jt 'echo ok; env | grep -i jira' 2
jt 'env | grep -i jira' 2
jt 'printenv | grep -i jira' 2
jt 'set | grep jira' 2
jt '(echo $JIRA_SAMSUNG_TOKEN)' 2
jt 'echo "ola" && echo $JIRA_SAMSUNG_TOKEN' 2
jt 'ls -la' 0
for v in " TRUE " " true" "True" "TRUE"; do
  casov "$v" jira "$(bash_json "curl -s $URL_J")" 0 "liberado [flag='$v']: curl para o Jira"
  casov "$v" jira "$(bash_json 'echo $JIRA_SAMSUNG_TOKEN')" 2 "trava [flag='$v']: exibir token continua barrado"
done
# modo arquivos: nada muda com a flag true (nem com qualquer outro valor)
for v in true false sim ""; do
  casov "$v" arquivos "$(bash_json 'git add .env')" 2 "arquivos [flag='$v']: git add .env"
  casov "$v" arquivos '{"tool_name":"Edit","tool_input":{"file_path":".env"}}' 2 "arquivos [flag='$v']: Edit .env"
  casov "$v" arquivos "$(bash_json 'git add .env.example')" 0 "arquivos [flag='$v']: git add .env.example"
done

# Ligar a flag por JSON/YAML/dotenv e escrever em settings do Claude / override do compose (qualquer flag)
JSON_ON='printf '"'"'{"env":{"JIRA_LIVE_ENABLED":"true"}}'"'"' > .claude/settings.local.json'
for v in false true ""; do
  casov "$v" arquivos "$(bash_json "$JSON_ON")" 2 "arquivos [flag='$v']: printf JSON liga a flag em settings.local.json"
  casov "$v" arquivos '{"tool_name":"Write","tool_input":{"file_path":".claude/settings.local.json"}}' 2 "arquivos [flag='$v']: Write em settings.local.json"
  casov "$v" arquivos '{"tool_name":"Edit","tool_input":{"file_path":"/x/y/.claude/settings.json"}}' 2 "arquivos [flag='$v']: Edit em settings.json"
  casov "$v" arquivos '{"tool_name":"Write","tool_input":{"file_path":"app/docker-compose.override.yml"}}' 2 "arquivos [flag='$v']: Write em docker-compose.override.yml"
  casov "$v" arquivos '{"tool_name":"Write","tool_input":{"file_path":"app/compose.override.yaml"}}' 2 "arquivos [flag='$v']: Write em compose.override.yaml"
  casov "$v" arquivos "$(bash_json 'tee app/docker-compose.override.yml < x.yml')" 2 "arquivos [flag='$v']: tee no override"
  casov "$v" arquivos "$(bash_json 'cp x.json .claude/settings.local.json')" 2 "arquivos [flag='$v']: cp para settings.local.json"
  casov "$v" arquivos "$(bash_json 'sed -i s/a/b/ .claude/settings.json')" 2 "arquivos [flag='$v']: sed -i em settings.json"
  casov "$v" arquivos "$(bash_json 'cat .claude/settings.json')" 0 "arquivos [flag='$v']: ler settings.json continua liberado"
  casov "$v" arquivos '{"tool_name":"Write","tool_input":{"file_path":"docs/x.json"}}' 0 "arquivos [flag='$v']: Write em docs/x.json liberado"
  casov "$v" arquivos '{"tool_name":"Write","tool_input":{"file_path":"app/docker-compose.yml"}}' 0 "arquivos [flag='$v']: Write em docker-compose.yml liberado"
done
for v in false sim ""; do
  casov "$v" jira "$(bash_json "$JSON_ON")" 2 "trava [flag='$v']: forma JSON liga a flag no comando"
  casov "$v" jira "$(bash_json 'echo "JIRA_LIVE_ENABLED: true" >> x.yml')" 2 "trava [flag='$v']: forma YAML liga a flag"
  casov "$v" jira "$(bash_json 'echo JIRA_LIVE_ENABLED = "true" >> x.env')" 2 "trava [flag='$v']: forma dotenv liga a flag"
done
casov false jira "$(bash_json 'echo "JIRA_LIVE_ENABLED: false" >> x.yml')" 0 "trava [flag=false]: escrever false é liberado"

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
