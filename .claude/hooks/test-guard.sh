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
  # compose comum também liga a flag (environment:); desde a onda final do 4A só o usuário o edita
  casov "$v" arquivos '{"tool_name":"Write","tool_input":{"file_path":"app/docker-compose.yml"}}' 2 "arquivos [flag='$v']: Write em docker-compose.yml bloqueado (só o usuário edita)"
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

# ===== Onda final do Plano 4A: normalização, conteúdo e negação por padrão =====
# O valor "true" é montado por variável: o literal da flag ligada não aparece neste arquivo (o hook barra gravá-lo).
T=true
w_json() { jq -nc --arg t "$1" --arg p "$2" '{tool_name:$t,tool_input:{file_path:$p}}'; }
conteudo_json() { jq -nc --arg p "$1" --arg c "$2" '{tool_name:"Write",tool_input:{file_path:$p,content:$c}}'; }
edit_json() { jq -nc --arg s "$1" '{tool_name:"Edit",tool_input:{file_path:"docs/x.md",old_string:"a",new_string:$s}}'; }
multi_json() { jq -nc --arg s "$1" '{tool_name:"MultiEdit",tool_input:{file_path:"docs/x.md",edits:[{old_string:"a",new_string:"b"},{old_string:"c",new_string:$s}]}}'; }
nb_json() { jq -nc --arg s "$1" '{tool_name:"NotebookEdit",tool_input:{notebook_path:"docs/n.ipynb",new_source:$s}}'; }

for v in false true ""; do
  # 1) normalização do caminho (Write/Edit)
  for p in '.claude//settings.local.json' '/x/.claude/./settings.json' '/x/.claude/hooks/../settings.json' \
           '.CLAUDE/Settings.Local.JSON' '.claude\settings.json' 'C:\x\.claude\settings.json' '.claude/settings.json.' \
           '.claude /settings.json ' '/x/CLAUDE~1/settings.json' '/x/.claude/SETTIN~1.JSO' '/x/.claude/a/B~1' \
           'app//Docker-Compose.Override.YML' 'app/docker-compose.override.yml:x' 'app/./compose.override.yaml.' \
           'app/docker-compose.yml' 'app/compose.yaml' 'app/DOCKER~1.YML' 'app/.ENV' 'app/.env ' 'C:\x\app\.env'; do
    casov "$v" arquivos "$(w_json Write "$p")" 2 "normalização [flag='$v']: Write em '$p'"
  done
  casov "$v" arquivos '{"tool_name":"Write","cwd":"/x/.claude","tool_input":{"file_path":"settings.local.json"}}' 2 "normalização [flag='$v']: caminho relativo com cwd .claude"
  for p in '.claude/hooks/x.sh' 'docs/x.json' '/x/.claude/settings-notas.md' 'app/.env.example' 'docs/compose-notas.md' '/x/.claude/skills/a.md'; do
    casov "$v" arquivos "$(w_json Write "$p")" 0 "não regressão [flag='$v']: Write em '$p'"
  done
  # 1) comandos que mudam de diretório ou normalizam o caminho
  for c in 'cd .claude && cp /tmp/x settings.local.json' 'pushd .claude; mv x settings.json' 'cp x .claude//settings.json' \
           'cp x ./.claude/./settings.local.json' 'cp x .CLAUDE/SETTINGS.JSON' 'cp x .claude\settings.json' \
           'cp x settings.local.json' 'cp x CLAUDE~1/SETTIN~1.JSO' 'cd .claude/hooks/.. && touch settings.json' \
           'cp x app//docker-compose.override.yml' 'cp x app/DOCKER~1.YML'; do
    casov "$v" arquivos "$(bash_json "$c")" 2 "cd/normalização [flag='$v']: $c"
  done

  # 2) conteúdo que liga a flag, em qualquer arquivo e com qualquer ferramenta de escrita
  casov "$v" arquivos "$(conteudo_json docs/x.md "{\"JIRA_LIVE_ENABLED\": \"$T\"}")" 2 "conteúdo [flag='$v']: Write JSON liga a flag"
  casov "$v" arquivos "$(conteudo_json docs/x.env.md "JIRA_LIVE_ENABLED=$T")" 2 "conteúdo [flag='$v']: Write dotenv liga a flag"
  casov "$v" arquivos "$(conteudo_json docs/x.yml "  JIRA_LIVE_ENABLED: '$T'")" 2 "conteúdo [flag='$v']: Write YAML liga a flag"
  casov "$v" arquivos "$(conteudo_json docs/x.sh "export JIRA_LIVE_ENABLED=1")" 2 "conteúdo [flag='$v']: Write com valor 1"
  casov "$v" arquivos "$(edit_json "jira_live_enabled = $T")" 2 "conteúdo [flag='$v']: Edit new_string (minúsculas)"
  casov "$v" arquivos "$(multi_json "JIRA_LIVE_ENABLED=$T")" 2 "conteúdo [flag='$v']: MultiEdit edits[].new_string"
  casov "$v" arquivos "$(nb_json "os.environ['JIRA_LIVE_ENABLED'] = '$T'")" 2 "conteúdo [flag='$v']: NotebookEdit new_source"
  casov "$v" jira "$(conteudo_json docs/x.md "JIRA_LIVE_ENABLED=$T")" 2 "conteúdo [flag='$v']: modo jira também procura no conteúdo"
  casov "$v" arquivos "$(bash_json "echo JIRA_LIVE_ENABLED=$T >> docs/x.md")" 2 "conteúdo [flag='$v']: comando grava a flag ligada num arquivo"
  casov "$v" arquivos "$(bash_json "printf 'JIRA_LIVE_ENABLED: $T' | tee x.yml")" 2 "conteúdo [flag='$v']: tee grava a flag ligada"
  casov "$v" arquivos "$(conteudo_json docs/x.md 'JIRA_LIVE_ENABLED=false')" 0 "não regressão [flag='$v']: Write com a flag false"
  casov "$v" arquivos "$(conteudo_json docs/x.md 'JIRA_LIVE_ENABLED: ${JIRA_LIVE_ENABLED:-false}')" 0 "não regressão [flag='$v']: Write com o padrão do compose"
  casov "$v" arquivos "$(edit_json 'texto comum')" 0 "não regressão [flag='$v']: Edit sem edits[] e texto comum"
  casov "$v" arquivos "$(multi_json 'texto comum')" 0 "não regressão [flag='$v']: MultiEdit com texto comum"
  casov "$v" arquivos '{"tool_name":"Edit","tool_input":{"file_path":"docs/x.md","edits":"x"}}' 0 "não regressão [flag='$v']: edits em formato inesperado não quebra"
  casov "$v" arquivos "$(bash_json "JIRA_LIVE_ENABLED=$T ./gradlew test 2>&1")" 0 "não regressão [flag='$v']: modo arquivos não barra a flag no comando sem escrita"
  casov "$v" arquivos "$(bash_json "JIRA_LIVE_ENABLED=$T ./gradlew test >/dev/null")" 0 "não regressão [flag='$v']: saída para /dev/null não é escrita"

  # 3) negação por padrão: comando que cita settings, compose ou override só passa se for leitura conhecida
  for c in "python3 -c \"open('.claude/settings.json','w').write('x')\"" 'python -c x app/docker-compose.yml' \
           'perl -pi -e s/a/b/ .claude/settings.json' 'node -e "x" app/docker-compose.override.yml' 'ruby -e x .claude/settings.local.json' \
           'php -r x app/compose.yaml' 'pwsh -c Set-Content .claude/settings.json x' 'powershell -c x app/docker-compose.yml' \
           'git checkout -- app/docker-compose.yml' 'git restore app/docker-compose.override.yml' 'git apply --include=app/docker-compose.yml p.diff' \
           'git stash pop && cat app/docker-compose.yml' 'git -C app checkout docker-compose.yml' 'patch app/docker-compose.yml < p.diff' \
           'rsync x .claude/settings.json' 'curl -o app/docker-compose.override.yml https://example.com/x' \
           'wget -O .claude/settings.local.json https://example.com/x' 'dd if=x of=app/docker-compose.yml' 'install x .claude/settings.json' \
           'ln -sf x .claude/settings.local.json' "sed -i '' s/a/b/ app/docker-compose.yml" 'sed -I s/a/b/ app/docker-compose.yml' \
           'sed --in-place s/a/b/ .claude/settings.json' 'echo x > app/docker-compose.yml' 'echo x >> .claude/settings.json' \
           'echo x &> app/docker-compose.yml' 'echo x 1> app/docker-compose.yml' "sed -n 's/a/b/w out' app/docker-compose.yml" \
           "awk '{system(\"x\")}' app/docker-compose.yml" 'gawk -i inplace 1 app/docker-compose.yml' 'git diff --output=x app/docker-compose.yml' \
           'docker compose -f app/docker-compose.yml cp x:/a b' 'docker compose -f app/docker-compose.yml config -o x' \
           'less -o log app/docker-compose.yml' 'sort -o app/docker-compose.yml x' 'cat x | tee app/docker-compose.yml' \
           "sh -c 'cp x .claude/settings.json'" 'bash -c "cp x .claude/settings.json"' 'echo x | xargs -I{} cp {} .claude/settings.json' \
           'cat $(cp x .claude/settings.json)' 'yq -i .a=1 app/docker-compose.yml' 'truncate -s0 app/docker-compose.yml' \
           'chmod 000 .claude/settings.json' 'rm .claude/settings.local.json' 'Set-Content .claude\settings.json x' \
           'cp a .vscode/settings.json' 'git add app/docker-compose.yml' 'git commit -m "compose.yml"'; do
    casov "$v" arquivos "$(bash_json "$c")" 2 "nega por padrão [flag='$v']: $c"
  done
  for c in 'cat .claude/settings.json' 'ls .claude' 'grep -n env app/docker-compose.yml' 'head -5 app/docker-compose.yml' \
           'tail -n 3 .claude/settings.json' 'diff a app/docker-compose.yml' 'wc -l app/docker-compose.yml' 'stat .claude/settings.json' \
           'file app/docker-compose.yml' 'less app/docker-compose.yml' 'more app/docker-compose.yml' 'sed -n 1,5p app/docker-compose.yml' \
           "sed -n -e 's/a/b/p' app/docker-compose.yml" "awk '{print \$1}' app/docker-compose.yml" 'cat app/docker-compose.yml 2>/dev/null' \
           'cat .claude/settings.json 2>&1 | head' 'git diff app/docker-compose.yml' 'git log -- app/docker-compose.yml' \
           'git show HEAD:.claude/settings.json' 'cat .claude/settings.json | jq .hooks' 'cd app && docker compose -f docker-compose.yml up -d' \
           'grep -n settings .claude/hooks/guard.sh' 'cd app && docker compose up --build -d' 'rg compose docs' \
           'git add .claude/hooks/guard.sh .claude/hooks/test-guard.sh .clinerules/hooks/PreToolUse .clinerules/hooks/PreToolUse.ps1 .clinerules/hooks/test-PreToolUse.sh .clinerules/hooks/test-PreToolUse.ps1 docs/ferramentas-claude-para-o-cline/hooks.md HANDOFF.md' \
           'git commit -F /private/tmp/claude-501/scratchpad/msg-commit.txt'; do
    casov "$v" arquivos "$(bash_json "$c")" 0 "não regressão [flag='$v']: $c"
  done
done

echo "falhas: $falhas"
[ "$falhas" = 0 ]
