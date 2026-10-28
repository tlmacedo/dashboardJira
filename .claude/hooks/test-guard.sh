#!/bin/sh
# Testes do guard.sh: cada caso informa o código de saída esperado (2 = bloqueado, 0 = liberado).
# Uso: sh .claude/hooks/test-guard.sh
dir=$(cd "$(dirname "$0")" && pwd)
falhas=0
resultado() { # esperado, descrição, código obtido
  if [ "$3" = "$1" ]; then echo "ok    $2"; else echo "FALHA $2 (saiu $3, esperado $1)"; falhas=$((falhas+1)); fi
}
# Plano 14, Tarefa 0: a flag vem do arquivo app/.env da RAIZ DO REPOSITÓRIO, deduzida do caminho do script.
# Os testes montam uma raiz de mentira num diretório temporário (cópia do guard.sh em .claude/hooks e um
# app/.env de teste); o app/.env real do repositório NUNCA é lido nem escrito pelos testes.
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/.claude/hooks" "$TMP/app"
cp "$dir/guard.sh" "$TMP/.claude/hooks/guard.sh"
chmod +x "$TMP/.claude/hooks/guard.sh"
guard="$TMP/.claude/hooks/guard.sh"
ARQ="$TMP/app/.env"
# A flag do AMBIENTE é REMOVIDA por padrão: ela não vale nem liga nada; nenhum caso depende do terminal de quem roda.
caso() { # modo, JSON, esperado, descrição (sem arquivo de variáveis: flag desligada)
  rm -rf "$ARQ"
  printf '%s' "$2" | env -u JIRA_LIVE_ENABLED "$guard" "$1" >/dev/null 2>&1
  resultado "$3" "$4" $?
}
casov() { # valor da flag NO ARQUIVO, modo, JSON, esperado, descrição
  rm -rf "$ARQ"
  printf 'JIRA_LIVE_ENABLED=%s\n' "$1" > "$ARQ"
  printf '%s' "$3" | env -u JIRA_LIVE_ENABLED "$guard" "$2" >/dev/null 2>&1
  resultado "$4" "$5" $?
}
casoa() { # conteúdo bruto do arquivo (printf %b), modo, JSON, esperado, descrição
  rm -rf "$ARQ"
  printf '%b' "$1" > "$ARQ"
  printf '%s' "$3" | env -u JIRA_LIVE_ENABLED "$guard" "$2" >/dev/null 2>&1
  resultado "$4" "$5" $?
}
casoe() { # valor da flag NO AMBIENTE, conteúdo do arquivo ("-" = sem arquivo), modo, JSON, esperado, descrição
  rm -rf "$ARQ"
  [ "$2" = "-" ] || printf '%b' "$2" > "$ARQ"
  printf '%s' "$4" | env JIRA_LIVE_ENABLED="$1" "$guard" "$3" >/dev/null 2>&1
  resultado "$5" "$6" $?
}
bash_json() { jq -nc --arg c "$1" '{tool_name:"Bash",tool_input:{command:$c}}'; }
j() { caso jira "$(bash_json "$1")" "$2" "jira: $1"; }
a() { caso arquivos "$(bash_json "$1")" "$2" "arquivos: $1"; }
jt() { casov true jira "$(bash_json "$1")" "$2" "jira [flag=true]: $1"; }     # flag true

j 'curl -s https://jira.exemplo.com/rest/api/2/search' 2
j 'python3 -c "import urllib.request as u;u.urlopen(\"https://sidia.x.atlassian.net/a\")"' 2
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
caso jira "$(bash_json 'JIRA_X=1 true')" 2 "falso positivo aceito: qualquer variável JIRA_* no comando é bloqueada"
# (28/10/2026) o antigo falso positivo "curl local junto de caminho com dashboardJira" deixou de existir: a palavra
# jira solta não bloqueia mais; os casos novos estão no fim deste arquivo.
caso jira "$(bash_json 'curl -s http://127.0.0.1:8080/api/health # em /Volumes/Development/SIDIA/dashboardJira')" 0 "regra nova: curl local junto de caminho com dashboardJira é liberado"

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

# ===== 28/10/2026: só ENDEREÇOS de Jira são bloqueados (a palavra solta não) =====
# Com a flag desligada (sem arquivo). Rótulo com jira, jira+sidia em host+caminho e *.atlassian.net com sidia
# bloqueiam; documentação pública (atlassian.com e subdomínios) é liberada pelo host REAL.
# 1) palavra jira solta: liberada
for c in 'cd /Volumes/Development/SIDIA/dashboardJira && ls' 'ls dashboardJira/docs/jira' 'cat docs/jira/checklist-primeira-conexao.md' \
         'git clone https://github.com/dono/dashboardJira.git' 'git -C dashboardJira status' 'cd dashboardJira && curl -s http://localhost:8080/api/health' \
         'curl -s http://127.0.0.1:8080/api/health' 'curl -s -o jira-saida.txt http://127.0.0.1:8080/api/health' \
         'wget -q -O jira.json http://127.0.0.1:8080/x' 'echo jira atlassian' 'grep -rn jira docs' 'git log --oneline -- docs/jira' \
         'curl -s -H "X-Projeto: dashboardJira" http://127.0.0.1:8080/x' 'ls /home/x/dashboardJira/app' 'ssh-add -l' \
         'python -m pytest tests/test_jira_client.py' 'curl -s file:///Users/x/dashboardJira/docs/a.html' \
         'curl -d "{\"nome\":\"jira\"}" http://127.0.0.1:8080/x' 'cat jira.md' 'cd app/backend && ./gradlew test --tests "*JiraClient*"' \
         'curl -s localhost:8080/api/jira-status' 'curl -s http://127.0.0.1:8080/jira.json' 'git add docs/jira/checklist-primeira-conexao.md' \
         'git commit --author="Fulano <fulano@exemplo.invalid>" -m x' 'git clone git@github.com:dono/dashboardJira.git'; do
  j "$c" 0
done
# 2) URL/host de Jira: bloqueada (com e sem esquema, porta, userinfo, maiúsculas, escapes, ponto final)
for c in 'curl -s https://jira.exemplo.invalid/rest/api/2/myself' 'curl https://meu-jira.x.invalid/a' 'curl http://jira-lab.invalid:8080/' \
         'curl jira.exemplo.invalid/x' 'curl jira-lab.invalid:8080' 'wget https://JIRA.Exemplo.Invalid/' 'curl https://jira.exemplo.invalid./x' \
         'curl https://usuario:senha@jira.x.invalid/' 'curl https://a@jira.x.invalid' 'curl https://jira.x.invalid:8443/a' 'CURL HTTPS://JIRA.X.INVALID' \
         'curl https://jira%2Eexemplo.invalid/x' 'curl https://%6aira.x.invalid/' 'curl https://jira%252Ex.invalid/' 'curl https://jira.x.invalid%2Fa.invalid' \
         'curl https://x.invalid/jira/sidia' 'curl https://tracker.sidia.invalid/jira/browse/X' 'curl https://sidia.invalid/rest/jira/2' \
         'curl https://sidia.x.atlassian.net/x' 'curl https://SIDIA.x.Atlassian.NET/x' 'curl https://x.atlassian.net/sidia' \
         'curl https://x.atlassian.net/?q=sidia' 'ssh jira-lab' 'ssh -p 22 usuario@jira.x.invalid' 'nc -z jira-lab 8080' 'telnet jira.x.invalid 80' \
         'iwr jira.exemplo.invalid' 'Invoke-RestMethod -Uri https://jira.x.invalid/rest' 'Invoke-WebRequest jira.x.invalid' 'curl.exe https://jira.x.invalid' \
         'python -c "import urllib.request as u;u.urlopen(\"https://jira.x.invalid/a\")"' 'git clone https://jira.x.invalid/r.git' \
         'curl -s -H "Host: jira.x.invalid" http://10.0.0.1/' 'curl --resolve jira.x.invalid:443:10.0.0.1 https://10.0.0.1/' 'curl --url=jira.x.invalid/a' \
         'git clone git@jira.x.invalid:r.git' 'scp arquivo usuario@jira-lab:/tmp/' 'curl -x jira.x.invalid:3128 http://x.invalid' \
         'curl -s "https://jira.x.invalid/a" -o jira.json''curl http://[::1]/ http://jira/' 'echo ok; curl http://jira.x.invalid'; do
  j "$c" 2
done
# 3) documentação pública da Atlassian: liberada, mesmo com jira no caminho ou no rótulo do subdomínio atlassian.com
for c in 'curl -s https://confluence.atlassian.com/jirasoftwareserver/jira-software-server-documentation-939937427.html' \
         'curl -s https://support.atlassian.com/jira-software-cloud/docs/x' 'curl https://developer.atlassian.com/server/jira/platform/rest/' \
         'curl https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/' 'curl https://community.atlassian.com/t5/Jira/x' \
         'curl https://www.atlassian.com/software/jira' 'wget https://DOCS.Atlassian.COM/jira/' 'curl https://www.atlassian.com./software/jira' \
         'curl confluence.atlassian.com/jirasoftwareserver' 'curl https://confluence.atlassian.com:443/jirasoftwareserver' \
         'curl https://jira.x.invalid@docs.atlassian.com/' 'curl https://jira.atlassian.com/browse/JRASERVER-1' \
         'curl https://x.atlassian.net/rest' 'curl https://suaempresa.atlassian.net/jira/software'; do
  j "$c" 0
done
# 4) tentativas de burla da decisão pelo host real
for c in 'curl https://confluence.atlassian.com@jira.x.invalid/' 'curl https://docs.atlassian.com:443@jira.x.invalid/' \
         'curl https://atlassian.com.jira.x.invalid/' 'curl https://docs.atlassian.com.jira.x.invalid/jirasoftwareserver' 'curl https://jira.x.invalid/atlassian.com' \
         'curl "https://jira.x.invalid/?u=confluence.atlassian.com"' 'curl https://docs.atlassian.com/x?redirect=https://jira.x.invalid/' \
         'curl https://docs.atlassian.com/x?redirect=https%3A%2F%2Fjira.x.invalid%2F' 'curl https://docs.atlassian.com%40jira.x.invalid/' \
         'curl https://docs.atlassian.com\@jira.x.invalid/' 'curl https://notatlassian.example/jira/sidia' 'curl https://x.invalid/atlassian.com/jira/sidia' \
         'curl https://x.atlassian.com.sidia.x.atlassian.net/' 'curl https://jira.x.invalid\.atlassian.com/' 'curl "https://docs.atlassian.com/x?next=jira.x.invalid" https://jira.x.invalid'; do
  j "$c" 2
done
# 4b) correções da revisão adversarial (28/10/2026): barra invertida no userinfo, host sem ponto, --connect-to,
# git/rsync/socat e "Host:" sem espaço
BS='\'
for c in "curl 'http://x${BS}@jira.corp.invalid/rest'" "wget 'http://x${BS}@jira.corp.invalid'" "Invoke-WebRequest 'http://x${BS}@jira.corp.invalid'" \
         'curl https://evil.invalid%5c@jira.corp.invalid/' 'curl https://evil.invalid%5C@jira.corp.invalid/' "curl http://x${BS}@jira.corp.invalid" \
         "curl http://jira.x.invalid${BS}@evil.invalid/"  "curl http://docs.atlassian.com${BS}jira.x.invalid/" \
         'curl jira/rest/api/2/search' 'wget jira/x' 'iwr jira/rest' 'curl --proxy jira http://x.invalid' 'curl -x jira http://x.invalid' \
         'curl -xjira.corp.invalid:3128 http://x.invalid' 'curl --preproxy jira http://x.invalid' 'curl jira-lab/x' 'echo ok; curl JIRA/x' \
         'curl --connect-to ::jira.corp.invalid:443 https://example.com' 'curl --connect-to example.com:443:jira.corp.invalid:443 https://example.com' \
         'curl --connect-to=::jira.corp.invalid:443 https://example.com' 'curl --connect-to jira.corp.invalid:443:example.com:443 https://example.com' \
         'curl --resolve jira.corp.invalid:443:10.0.0.1 https://x.invalid' 'curl --resolve example.com:443:jira.corp.invalid https://example.com' \
         'git clone jira.corp.invalid:x.git' 'git clone jira:x.git' 'git remote add o jira.corp.invalid:x' 'git remote set-url o jira:x' \
         'git ls-remote jira.corp.invalid:x' 'git fetch jira.corp.invalid:x' 'git pull jira.corp.invalid:x' 'git push jira.corp.invalid:x main' \
         'rsync -av x jira.corp.invalid:/tmp' 'scp x jira:/tmp' 'sftp jira.corp.invalid' 'socat - TCP:jira.corp.invalid:80' 'socat TCP4:jira:80 -' \
         "curl -H 'Host:jira.corp.invalid' http://10.0.0.1" 'curl -H Host:jira.corp.invalid http://10.0.0.1' 'curl -H "Host:jira" http://10.0.0.1'; do
  j "$c" 2
done
for c in "curl 'http://x${BS}@docs.atlassian.com/x'" "curl http://x${BS}@example.com/" "curl http://example.com${BS}@x.invalid/" \
         'git push origin HEAD:feat/hooks-url-jira' 'git commit -m "guia do jira"' 'git push origin jira-fix' 'git checkout -B work-hooks-fix origin/feat/hooks-url-jira' \
         'git fetch origin' 'git remote -v' 'git clone https://github.com/dono/dashboardJira.git' 'git pull --ff-only' \
         'curl --connect-to example.com:443:example.org:443 https://example.com' 'curl --resolve example.com:443:10.0.0.1 https://example.com' \
         'curl -H "Host: example.com" http://10.0.0.1' 'curl -H Host:localhost http://10.0.0.1' 'socat - TCP:example.com:80' \
         'curl --proxy proxy.exemplo.com:3128 http://x.invalid' 'curl -s localhost:8080/api/jira-status' 'rsync -av docs/jira /tmp/copia' 'ssh-add -l'; do
  j "$c" 0
done
# 5) WebFetch do Claude Code (vale se o matcher o incluir): a URL pode vir sem esquema
web_json() { jq -nc --arg u "$1" '{tool_name:"WebFetch",tool_input:{url:$u,prompt:"resuma"}}'; }
caso jira "$(web_json 'jira.exemplo.invalid/x')" 2 "WebFetch: host jira sem esquema"
caso jira "$(web_json 'https://jira.exemplo.invalid/x')" 2 "WebFetch: host jira"
caso jira "$(web_json 'https://empresa.invalid/jira/sidia')" 2 "WebFetch: jira+sidia no caminho"
caso jira "$(web_json 'https://docs.atlassian.com/software/jira/docs/api/')" 0 "WebFetch: documentação pública liberada"
caso jira "$(web_json 'https://empresa.invalid/jira/browse/X-1')" 0 "WebFetch: jira só no caminho, sem sidia, liberado"
casov "$T" jira "$(web_json 'https://jira.exemplo.invalid/x')" 0 "WebFetch: flag ligada libera"
# 6) modos url/urlesq (usados pelo adaptador do Cline) e modo flag
caso url 'jira.exemplo.invalid/x' 2 "modo url: host sem esquema"
caso url 'https://docs.atlassian.com/jira' 0 "modo url: documentação pública"
caso url 'file:///Users/x/dashboardJira/docs/a.html' 0 "modo url: arquivo local"
caso urlesq 'um texto qualquer jira.md e jira.x.invalid' 0 "modo urlesq: sem esquema não conta"
caso urlesq 'veja https://jira.x.invalid/a' 2 "modo urlesq: URL com esquema conta"
casov "$T" url 'jira.exemplo.invalid/x' 0 "modo url: flag ligada libera"
casov "$T" flag '' 0 "modo flag: ligada responde 0"
casov false flag '' 1 "modo flag: desligada responde 1"
caso flag '' 1 "modo flag: sem arquivo responde 1"

# ===== Plano 14, Tarefa 0: a flag vem do app/.env da raiz do repositório =====
CURL_J='{"tool_name":"Bash","tool_input":{"command":"curl -s https://jira.exemplo.invalid/rest/api/2/myself"}}'
# flagVemDoArquivoDeVariaveis
casoa "JIRA_LIVE_ENABLED=$T\n" jira "$CURL_J" 0 "arquivo: flag ligada libera"
casoa "JIRA_LIVE_ENABLED=false\n" jira "$CURL_J" 2 "arquivo: flag false bloqueia"
casoa "OUTRA=1\n" jira "$CURL_J" 2 "arquivo: sem a linha da flag bloqueia"
casoa "" jira "$CURL_J" 2 "arquivo: vazio bloqueia"
# sintaxe igual à do ArquivoEnv.kt
for v in "\"$T\"" "'$T'" "$T # comentário" "TRUE" "True" " $T " "\" $T \"" "'TrUe'"; do
  casoa "JIRA_LIVE_ENABLED=$v\n" jira "$CURL_J" 0 "arquivo: valor [$v] liga"
done
casoa "export JIRA_LIVE_ENABLED=$T\n" jira "$CURL_J" 0 "arquivo: prefixo export liga"
casoa "  JIRA_LIVE_ENABLED = $T  \n" jira "$CURL_J" 0 "arquivo: espaços ao redor do = e nas pontas ligam"
casoa "JIRA_LIVE_ENABLED=$T\r\n" jira "$CURL_J" 0 "arquivo: fim de linha CRLF liga"
casoa "\357\273\277JIRA_LIVE_ENABLED=$T\n" jira "$CURL_J" 0 "arquivo: BOM no começo liga"
casoa "A=1\n\nB=2\nJIRA_LIVE_ENABLED=$T\nC=3\n" jira "$CURL_J" 0 "arquivo: linha da flag no meio de outras liga"
casoa "JIRA_LIVE_ENABLED=$T" jira "$CURL_J" 0 "arquivo: sem quebra de linha final liga"
for v in sim yes 1 0 "tr ue" "${T};" "${T}#x" "${T}x" "x${T}" "" "\"$T" "$T\"" "'$T\"" "\"$T\" # c" "$T,"; do
  casoa "JIRA_LIVE_ENABLED=$v\n" jira "$CURL_J" 2 "arquivo: valor inválido [$v] bloqueia"
done
casoa "# JIRA_LIVE_ENABLED=$T\n" jira "$CURL_J" 2 "arquivo: linha comentada não liga"
casoa "  # JIRA_LIVE_ENABLED=$T\n" jira "$CURL_J" 2 "arquivo: comentário indentado não liga"
casoa "JIRA_LIVE_ENABLED_X=$T\n" jira "$CURL_J" 2 "arquivo: nome parecido (sufixo) não liga"
casoa "XJIRA_LIVE_ENABLED=$T\n" jira "$CURL_J" 2 "arquivo: nome parecido (prefixo) não liga"
casoa "jira_live_enabled=$T\n" jira "$CURL_J" 2 "arquivo: nome em minúsculas não liga (igual ao painel)"
casoa "JIRA_LIVE_ENABLED:$T\n" jira "$CURL_J" 2 "arquivo: dois-pontos no lugar de = não liga"
casoa "SEGREDO=JIRA_LIVE_ENABLED=$T\n" jira "$CURL_J" 2 "arquivo: nome citado dentro de outro valor não liga"
# a ÚLTIMA ocorrência vale
casoa "JIRA_LIVE_ENABLED=$T\nJIRA_LIVE_ENABLED=false\n" jira "$CURL_J" 2 "arquivo: ligada e depois desligada bloqueia"
casoa "JIRA_LIVE_ENABLED=false\nJIRA_LIVE_ENABLED=$T\n" jira "$CURL_J" 0 "arquivo: desligada e depois ligada libera"
casoa "JIRA_LIVE_ENABLED=$T\nJIRA_LIVE_ENABLED=\n" jira "$CURL_J" 2 "arquivo: ligada e depois vazia bloqueia"
casoa "JIRA_LIVE_ENABLED=$T\n# JIRA_LIVE_ENABLED=false\n" jira "$CURL_J" 0 "arquivo: comentário depois não desliga"
# ambienteDoProcessoNaoLiga / ambienteDoProcessoNaoDesliga
casoe "$T" "JIRA_LIVE_ENABLED=false\n" jira "$CURL_J" 2 "ambiente ligado e arquivo false: bloqueia"
casoe "$T" "-" jira "$CURL_J" 2 "ambiente ligado e sem arquivo: bloqueia"
casoe "$T" "OUTRA=1\n" jira "$CURL_J" 2 "ambiente ligado e arquivo sem a flag: bloqueia"
casoe "$T" "JIRA_LIVE_ENABLED=\n" jira "$CURL_J" 2 "ambiente ligado e arquivo com valor vazio: bloqueia"
casoe "1" "JIRA_LIVE_ENABLED=sim\n" jira "$CURL_J" 2 "ambiente 1 e arquivo inválido: bloqueia"
casoe "false" "JIRA_LIVE_ENABLED=$T\n" jira "$CURL_J" 0 "ambiente false e arquivo ligado: libera"
casoe "" "JIRA_LIVE_ENABLED=$T\n" jira "$CURL_J" 0 "ambiente vazio e arquivo ligado: libera"
casoe "lixo" "JIRA_LIVE_ENABLED=$T\n" jira "$CURL_J" 0 "ambiente inválido e arquivo ligado: libera"
casoe "$T" "-" flag '' 1 "modo flag: ambiente ligado e sem arquivo responde 1"
# arquivoIlegivelFalhaFechado: ausente, não é arquivo comum, valor inválido
rm -rf "$ARQ"; mkdir "$ARQ"
printf '%s' "$CURL_J" | env -u JIRA_LIVE_ENABLED "$guard" jira >/dev/null 2>&1
resultado 2 "arquivo ilegível (é um diretório): bloqueia" $?
printf '%s' "$CURL_J" | env JIRA_LIVE_ENABLED="$T" "$guard" jira >/dev/null 2>&1
resultado 2 "arquivo ilegível (é um diretório) e ambiente ligado: bloqueia" $?
rm -rf "$ARQ"
printf '\377\376JIRA_LIVE_ENABLED=\000%s\n' "$T" > "$ARQ"
printf '%s' "$CURL_J" | env -u JIRA_LIVE_ENABLED "$guard" jira >/dev/null 2>&1
resultado 2 "arquivo binário/lixo: bloqueia" $?
# a raiz vem do caminho do script, não do diretório corrente nem de variável do ambiente
OUTRO="$TMP/outro"; mkdir -p "$OUTRO/app"
printf 'JIRA_LIVE_ENABLED=%s\n' "$T" > "$OUTRO/app/.env"
rm -rf "$ARQ"; printf 'JIRA_LIVE_ENABLED=false\n' > "$ARQ"
printf '%s' "$CURL_J" | (cd "$OUTRO" && env -u JIRA_LIVE_ENABLED CLAUDE_PROJECT_DIR="$OUTRO" PWD="$OUTRO" "$guard" jira) >/dev/null 2>&1
resultado 2 "raiz pelo caminho do script: app/.env do diretório corrente e de CLAUDE_PROJECT_DIR não vale" $?
printf '%s' "$CURL_J" | (cd "$OUTRO" && env -u JIRA_LIVE_ENABLED sh "$guard" jira) >/dev/null 2>&1
resultado 2 "raiz pelo caminho do script: chamado por sh de outro diretório" $?
rm -rf "$ARQ"; printf 'JIRA_LIVE_ENABLED=%s\n' "$T" > "$ARQ"
printf '%s' "$CURL_J" | (cd "$OUTRO" && env -u JIRA_LIVE_ENABLED sh "$guard" jira) >/dev/null 2>&1
resultado 0 "raiz pelo caminho do script: arquivo da raiz do script vale mesmo chamado de outro diretório" $?
# o hook nunca imprime o conteúdo do arquivo
rm -rf "$ARQ"; printf 'JIRA_SAMSUNG_TOKEN=segredo-do-teste-123\nJIRA_LIVE_ENABLED=false\nOUTRA=valor-outra-456\n' > "$ARQ"
saida=$(printf '%s' "$CURL_J" | env -u JIRA_LIVE_ENABLED "$guard" jira 2>&1)
case "$saida" in *segredo-do-teste-123*|*valor-outra-456*) resultado ok "hook não imprime o arquivo (vazou)" falhou ;; *) resultado ok "hook não imprime o conteúdo do arquivo" ok ;; esac
saida=$(printf '%s' "$CURL_J" | env -u JIRA_LIVE_ENABLED "$guard" flag 2>&1)
[ -z "$saida" ] && resultado ok "modo flag não imprime nada" ok || resultado ok "modo flag não imprime nada" falhou
# leituraDaIaAoArquivoContinuaBarrada: o hook lê o arquivo para si, mas a IA continua barrada
for v in false "$T" ""; do
  casov "$v" arquivos "$(bash_json 'cat app/.env > /tmp/x')" 2 "IA [flag='$v']: copiar app/.env por redirecionamento"
  casov "$v" arquivos "$(bash_json 'ln -sf /tmp/x app/.env')" 2 "IA [flag='$v']: symlink para app/.env"
  casov "$v" arquivos "$(bash_json 'cp /tmp/x app/.env')" 2 "IA [flag='$v']: cp para app/.env"
  casov "$v" arquivos "$(bash_json 'sed -i s/a/b/ app/.env')" 2 "IA [flag='$v']: sed -i em app/.env"
  casov "$v" arquivos "$(bash_json 'git add app/.env')" 2 "IA [flag='$v']: git add app/.env"
  casov "$v" arquivos "$(bash_json "echo JIRA_LIVE_ENABLED=$T >> app/.env")" 2 "IA [flag='$v']: ligar a flag em app/.env por echo"
  casov "$v" arquivos '{"tool_name":"Write","tool_input":{"file_path":"app/.env","content":"A=1"}}' 2 "IA [flag='$v']: Write em app/.env"
  casov "$v" arquivos '{"tool_name":"Edit","tool_input":{"file_path":"/x/app/.ENV","old_string":"a","new_string":"b"}}' 2 "IA [flag='$v']: Edit em app/.ENV"
  casov "$v" arquivos "$(conteudo_json docs/x.md "JIRA_LIVE_ENABLED=$T")" 2 "IA [flag='$v']: gravar o texto que liga a flag em qualquer arquivo"
  casov "$v" arquivos '{"tool_name":"Write","tool_input":{"file_path":"app/.env.example","content":"A=1"}}' 0 "IA [flag='$v']: modelo .env.example continua liberado"
done
# a variável do ambiente com valor ligado não abre exceção para ligar a flag no comando
casoe "$T" "JIRA_LIVE_ENABLED=false\n" jira "$(bash_json "JIRA_LIVE_ENABLED=$T ./gradlew test")" 2 "ambiente ligado e arquivo false: ligar a flag no comando continua barrado"
# com a flag ligada no arquivo, o comportamento antigo se mantém (nada de acesso é bloqueado, token no terminal sim)
casoa "JIRA_LIVE_ENABLED=$T\n" jira "$(bash_json 'echo $JIRA_SAMSUNG_TOKEN')" 2 "arquivo ligado: exibir token continua barrado"
casoa "JIRA_LIVE_ENABLED=$T\n" jira "$MCP_ATL" 0 "arquivo ligado: MCP Atlassian liberado"
casoa "JIRA_LIVE_ENABLED=false\n" jira "$MCP_ATL" 2 "arquivo false: MCP Atlassian bloqueado"
casoa "JIRA_LIVE_ENABLED=$T\n" jira "$(bash_json 'curl -s https://sidia.x.atlassian.net/x')" 0 "arquivo ligado: URL de Jira liberada"

echo "falhas: $falhas"
[ "$falhas" = 0 ]
