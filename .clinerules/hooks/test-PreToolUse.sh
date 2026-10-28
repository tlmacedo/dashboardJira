#!/bin/sh
# Testes do hook PreToolUse do Cline (.clinerules/hooks/PreToolUse).
# Cada caso informa o valor da flag, o JSON de entrada e o resultado esperado
# (cancel true = bloqueado, false = liberado).
# Plano 14, Tarefa 0: a flag vem do app/.env da raiz do repositório (raiz deduzida do caminho do hook). Os testes
# montam uma raiz de mentira num diretório temporário (cópias do PreToolUse e do guard.sh e um app/.env de
# teste); o app/.env real do repositório NUNCA é lido nem escrito. A flag do AMBIENTE é removida por padrão.
# Uso: sh .clinerules/hooks/test-PreToolUse.sh   (precisa de jq e bash)
# No Windows o Cline usa o PreToolUse.ps1; os testes dele estão em test-PreToolUse.ps1 (mesmos casos).
dir=$(cd "$(dirname "$0")" && pwd)
falhas=0
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/.clinerules/hooks" "$TMP/.claude/hooks" "$TMP/app"
cp "$dir/PreToolUse" "$TMP/.clinerules/hooks/PreToolUse"
cp "$dir/../../.claude/hooks/guard.sh" "$TMP/.claude/hooks/guard.sh"
chmod +x "$TMP/.clinerules/hooks/PreToolUse" "$TMP/.claude/hooks/guard.sh"
hook="$TMP/.clinerules/hooks/PreToolUse"
ARQ="$TMP/app/.env"

verificar() { # rótulo, esperado, descrição (usa $saida e $r, já capturados pelo chamador)
  obtido=$(printf '%s' "$saida" | jq -r '.cancel' 2>/dev/null)
  if [ "$r" = 0 ] && [ "$obtido" = "$2" ]; then
    echo "ok    [$1] $3"
  else
    echo "FALHA [$1] $3 (exit $r, cancel=$obtido, esperado $2; saída: $saida)"
    falhas=$((falhas+1))
  fi
}
caso() { # flag NO ARQUIVO ("-" = sem arquivo), JSON, esperado (true|false), descrição
  rm -rf "$ARQ"
  [ "$1" = "-" ] || printf 'JIRA_LIVE_ENABLED=%s\n' "$1" > "$ARQ"
  saida=$(printf '%s' "$2" | env -u JIRA_LIVE_ENABLED bash "$hook" 2>/dev/null)
  r=$?
  verificar "flag=$1" "$3" "$4"
}
casoa() { # conteúdo bruto do arquivo (printf %b), JSON, esperado, descrição
  rm -rf "$ARQ"
  printf '%b' "$1" > "$ARQ"
  saida=$(printf '%s' "$2" | env -u JIRA_LIVE_ENABLED bash "$hook" 2>/dev/null)
  r=$?
  verificar "arquivo" "$3" "$4"
}
casoe() { # valor NO AMBIENTE, conteúdo do arquivo ("-" = sem arquivo), JSON, esperado, descrição
  rm -rf "$ARQ"
  [ "$2" = "-" ] || printf '%b' "$2" > "$ARQ"
  saida=$(printf '%s' "$3" | env JIRA_LIVE_ENABLED="$1" bash "$hook" 2>/dev/null)
  r=$?
  verificar "ambiente=$1" "$4" "$5"
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
  caso "$f" "$(web web_fetch 'https://sidia.atlassian.net/wiki/x')" true "trava: web_fetch para o Cloud da Atlassian com sidia"
  caso "$f" "$(nav 'https://JIRA.empresa.invalid/browse/X-1')" true "trava: navegador com host jira (sem diferenciar maiúsculas)"
  caso "$f" "$(nav 'https://empresa.invalid/JIRA/sidia/browse/X-1')" true "trava: navegador com jira e sidia no caminho"
  # 28/10/2026: documentação pública e palavra jira solta passam
  caso "$f" "$(web web_fetch 'https://docs.atlassian.com/jira/rest')" false "liberado: web_fetch para documentação pública da Atlassian"
  caso "$f" "$(nav 'https://empresa.invalid/JIRA/browse/X-1')" false "liberado: navegador com JIRA só no caminho (sem sidia)"
done
caso - "$(mcp fetch fetch '{"url":"https://sidia.atlassian.net/wiki"}')" true "trava: MCP de fetch com URL do Cloud da Atlassian com sidia"
caso - "$(cmd 'Invoke-WebRequest -Uri https://jira.exemplo.invalid/x')" true "trava: Invoke-WebRequest"
caso - "$(cmd 'iwr jira.exemplo.invalid')" true "trava: iwr sem esquema na URL"
caso - "$(cmd 'setx JIRA_LIVE_ENABLED true')" true "trava: ligar a flag com setx"
caso - "$(cmd "[Environment]::SetEnvironmentVariable('JIRA_LIVE_ENABLED','true','User')")" true "trava: ligar a flag com SetEnvironmentVariable"
caso - "$(nav 'file:///Users/x/dashboardJira/docs/design/prototipo/a.html')" false "liberado (28/10): pasta com Jira no nome não bloqueia mais"
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

# --- ligar a flag por arquivo de configuração: só o usuário escreve em settings do Claude e override do compose ---
JSON_ON='printf '"'"'{"env":{"JIRA_LIVE_ENABLED":"true"}}'"'"' > .claude/settings.local.json'
for f in - false true; do
  caso "$f" "$(cmd "$JSON_ON")" true "settings: printf JSON liga a flag"
  caso "$f" "$(esc write_to_file '.claude/settings.local.json')" true "settings: write_to_file em settings.local.json"
  caso "$f" "$(esc replace_in_file 'C:\x\.claude\settings.json')" true "settings: replace_in_file em settings.json (barras do Windows)"
  caso "$f" "$(esc write_to_file 'app/docker-compose.override.yml')" true "compose: write_to_file no override"
  caso "$f" "$(cmd 'tee app/docker-compose.override.yml < x.yml')" true "compose: tee no override"
  caso "$f" "$(cmd 'cp x.json .claude/settings.local.json')" true "settings: cp para settings.local.json"
  caso "$f" "$(ler '.claude/settings.json')" false "settings: ler settings.json liberado"
  caso "$f" "$(cmd 'cat .claude/settings.json')" false "settings: cat settings.json liberado"
  caso "$f" "$(esc write_to_file 'docs/x.json')" false "settings: docs/x.json liberado"
done

# --- onda final do Plano 4A: normalização, conteúdo e negação por padrão (espelha test-guard.sh) ---
# O valor "true" é montado por variável: o literal da flag ligada não aparece neste arquivo.
T=true
escc() { jq -nc --arg t "$1" --arg p "$2" --arg k "$3" --arg c "$4" '{preToolUse:{toolName:$t,parameters:({path:$p} + {($k):$c})}}'; }
for f in - false true; do
  for p in '.claude//settings.local.json' '/x/.claude/./settings.json' '/x/.claude/hooks/../settings.json' \
           '.CLAUDE/Settings.Local.JSON' 'C:\x\.claude\settings.json.' '/x/CLAUDE~1/settings.json' '/x/.claude/SETTIN~1.JSO' \
           'app//Docker-Compose.Override.YML' 'app/docker-compose.override.yml:x' 'app/docker-compose.yml' 'app\compose.yaml ' 'app/DOCKER~1.YML'; do
    caso "$f" "$(esc write_to_file "$p")" true "normalização: write_to_file em '$p'"
  done
  caso "$f" '{"preToolUse":{"toolName":"apply_patch","parameters":{"input":"*** Begin Patch\n*** Update File: app//docker-compose.yml\n@@\n-a\n+b\n*** End Patch"}}}' true "normalização: apply_patch no compose"
  for p in '.claude/hooks/x.sh' 'docs/x.json' 'docs/compose-notas.md' '.claude/skills/a.md'; do
    caso "$f" "$(esc write_to_file "$p")" false "não regressão: write_to_file em '$p'"
  done
  for c in 'cd .claude && cp /tmp/x settings.local.json' 'pushd .claude; mv x settings.json' 'cp x .claude//settings.json' \
           'cp x settings.local.json' 'cp x CLAUDE~1/SETTIN~1.JSO' 'Copy-Item x .CLAUDE\SETTINGS.JSON'; do
    caso "$f" "$(cmd "$c")" true "cd/normalização: $c"
  done
  # conteúdo
  caso "$f" "$(escc write_to_file docs/x.md content "{\"JIRA_LIVE_ENABLED\": \"$T\"}")" true "conteúdo: write_to_file content JSON"
  caso "$f" "$(escc replace_in_file docs/x.md diff "------- SEARCH
a
=======
JIRA_LIVE_ENABLED=$T
+++++++ REPLACE")" true "conteúdo: replace_in_file diff"
  caso "$f" "$(escc editor docs/x.md new_str "JIRA_LIVE_ENABLED: $T")" true "conteúdo: editor new_str"
  caso "$f" "$(escc replace_in_file docs/x.md replace "jira_live_enabled = $T")" true "conteúdo: replace (minúsculas)"
  caso "$f" "$(jq -nc --arg c "*** Begin Patch
*** Add File: docs/x.md
+JIRA_LIVE_ENABLED=$T
*** End Patch" '{preToolUse:{toolName:"apply_patch",parameters:{input:$c}}}')" true "conteúdo: apply_patch"
  caso "$f" "$(cmd "echo JIRA_LIVE_ENABLED=$T >> docs/x.md")" true "conteúdo: comando grava a flag ligada"
  caso "$f" "$(escc write_to_file docs/x.md content 'JIRA_LIVE_ENABLED=false')" false "não regressão: conteúdo com a flag false"
  caso "$f" "$(escc write_to_file docs/x.md content 'JIRA_LIVE_ENABLED: ${JIRA_LIVE_ENABLED:-false}')" false "não regressão: padrão do compose"
  # negação por padrão
  for c in "python3 -c \"open('.claude/settings.json','w')\"" 'perl -pi -e s/a/b/ .claude/settings.json' 'node -e x app/docker-compose.yml' \
           'ruby -e x app/compose.yaml' 'php -r x app/compose.yaml' 'pwsh -c x .claude/settings.json' 'powershell -c x app/docker-compose.yml' \
           'git checkout -- app/docker-compose.yml' 'git restore app/docker-compose.override.yml' 'git apply --include=app/docker-compose.yml p.diff' \
           'git stash pop && cat app/docker-compose.yml' 'patch app/docker-compose.yml < p.diff' 'rsync x .claude/settings.json' \
           'curl -o app/docker-compose.override.yml https://example.com/x' 'wget -O .claude/settings.local.json https://example.com/x' \
           'dd if=x of=app/docker-compose.yml' 'install x .claude/settings.json' 'ln -sf x .claude/settings.local.json' \
           'sed -i s/a/b/ app/docker-compose.yml' 'echo x > app/docker-compose.yml' 'echo x >> .claude/settings.json' \
           'echo x &> app/docker-compose.yml' 'echo x 1> app/docker-compose.yml' 'Set-Content app\docker-compose.yml x' \
           'cp a .vscode/settings.json'; do
    caso "$f" "$(cmd "$c")" true "nega por padrão: $c"
  done
  for c in 'cat .claude/settings.json' 'grep -n env app/docker-compose.yml' 'head -5 app/docker-compose.yml' 'sed -n 1,5p app/docker-compose.yml' \
           'git diff app/docker-compose.yml' 'cd app && docker compose -f docker-compose.yml up -d' 'Select-String env app\docker-compose.yml'; do
    caso "$f" "$(cmd "$c")" false "não regressão: $c"
  done
done
caso - "$(ler 'app/docker-compose.yml')" false "não regressão: ler o compose"
caso true "$(cmd "JIRA_LIVE_ENABLED=$T ./gradlew test 2>&1")" false "não regressão: flag no comando sem escrita, com a flag true"
caso false "$(cmd 'echo "JIRA_LIVE_ENABLED: true" >> x.yml')" true "trava: forma YAML liga a flag"

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
# chaves com alternativa vazia (o `read` descartava o campo vazio e *{.kt,} passava; o vazio expande para *)
for g in '*{.kt,}' '{,.kt}' '*{.kt,,.md}' '*{}' '*{,}' '{.kt}{,.md}' '*{.kt' '*.kt}' '*{{.kt,}}'; do
  caso - "$(sf . 'x' "$g")" true "busca: chaves inseguras '$g'"
done
caso - "$(sf . 'x' '*.{kt,tsx}')" false "não regressão: '*.{kt,tsx}'"
caso - "$(sf . 'x' '*{.kt,.md}')" false "não regressão: '*{.kt,.md}'"
# nome exato sem curinga: pode ser PASTA; só extensão segura ou a lista curta de arquivos conhecidos
for g in 'config' 'app/config' 'secrets' 'papeis.yaml' '.env' 'Config' '{config,README.md}'; do
  caso - "$(sf . 'x' "$g")" true "busca: nome exato sem extensão segura '$g'"
done
for g in 'Dockerfile' 'gradlew' 'gradlew.bat' 'README' 'README.md' '.gitignore' 'build.gradle.kts' '{Makefile,LICENSE}'; do
  caso - "$(sf . 'x' "$g")" false "não regressão: nome exato seguro '$g'"
done
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
caso - "$(esc write_to_file 'C:\Users\USUARIO~1\x\README.md')" false "não regressão: pasta com nome curto 8.3 (TEMP do Windows)"

# ===== 28/10/2026: só ENDEREÇOS de Jira são bloqueados (a palavra solta não) =====
# 1) palavra jira solta: liberada (flag desligada)
caso - "$(cmd 'cd /Volumes/Development/SIDIA/dashboardJira && ls')" false "palavra solta: caminho com dashboardJira"
caso - "$(cmd 'git clone https://github.com/dono/dashboardJira.git')" false "palavra solta: git clone do repositório"
caso - "$(cmd 'curl -s http://127.0.0.1:8080/api/health')" false "palavra solta: curl local"
caso - "$(cmd 'cd dashboardJira && curl -s http://localhost:8080/api/health')" false "palavra solta: curl local numa pasta dashboardJira"
caso - "$(cmd 'curl -s -o jira-saida.txt http://127.0.0.1:8080/api/health')" false "palavra solta: arquivo de saída com jira no nome"
caso - "$(cmd 'cat docs/jira/checklist-primeira-conexao.md')" false "palavra solta: cat de arquivo em pasta jira"
caso - "$(cmd 'echo jira atlassian')" false "palavra solta: texto"
caso - "$(cmd 'Start-Process notepad jira.md')" false "palavra solta: Start-Process com arquivo jira.md"
caso - "$(cmd 'Invoke-WebRequest http://127.0.0.1:8080/api/health -OutFile jira.json')" false "palavra solta: Invoke-WebRequest local, arquivo jira.json"
caso - "$(nav 'http://127.0.0.1:8080/dashboardJira')" false "palavra solta: navegador no painel local com Jira no caminho"
caso - "$(web web_fetch 'https://github.com/dono/dashboardJira')" false "palavra solta: web_fetch do repositório"
caso - "$(ler 'docs/jira/checklist-primeira-conexao.md')" false "palavra solta: ler arquivo em pasta jira"
caso - "$(mcp context7 get-library-docs '{"library":"jira-client"}')" false "palavra solta: argumento MCP sem URL"
# 2) URL/host de Jira: bloqueada
for c in 'curl -s https://jira.exemplo.invalid/rest/api/2/myself' 'curl https://meu-jira.x.com/a' 'curl http://jira-lab:8080/' 'curl jira.exemplo.invalid/x' \
         'curl jira-lab:8080' 'wget https://JIRA.Exemplo.Invalid/' 'curl https://jira.exemplo.invalid./x' 'curl https://u:s@jira.x.com/' \
         'curl https://jira%2Eexemplo.invalid/x' 'curl https://x.com/jira/sidia' 'curl https://sidia.atlassian.net/x' 'ssh jira-lab' \
         'ssh usuario@jira.x.com' 'nc -z jira-lab 8080' 'iwr jira.exemplo.invalid' 'Invoke-RestMethod -Uri https://jira.x/rest' \
         'Invoke-WebRequest jira.x.com' 'curl.exe https://jira.x.com' '(New-Object Net.WebClient).DownloadString("jira.x.com")' \
         'curl https://confluence.atlassian.com@jira.x.com/' 'curl https://atlassian.com.jira.x.com/' 'curl https://jira.x.com/atlassian.com' \
         'curl https://docs.atlassian.com/x?redirect=https://jira.x.com/' 'curl https://docs.atlassian.com/x?redirect=https%3A%2F%2Fjira.x.com' \
         'curl https://docs.atlassian.com\@jira.x.com/'; do
  caso - "$(cmd "$c")" true "URL de Jira em comando: $c"
  caso false "$(cmd "$c")" true "URL de Jira em comando (flag false): $c"
done
# 2b) correções da revisão adversarial (28/10/2026): barra invertida no userinfo, host sem ponto, --connect-to, git/socat, Host:
BS='\'
for c in "curl 'http://x${BS}@jira.corp/rest'" "wget 'http://a${BS}@jira.corp'" "Invoke-WebRequest 'http://a${BS}@jira.corp'" \
         'curl https://evil.com%5c@jira.corp/' 'curl jira/rest/api/2/search' 'wget jira/x' 'iwr jira/rest' 'curl --proxy jira http://x.com' \
         'curl --connect-to ::jira.corp:443 https://example.com' 'curl --connect-to example.com:443:jira.corp:443 https://example.com' \
         'curl --resolve example.com:443:jira.corp https://example.com' 'git clone jira.corp:x.git' 'git clone jira:x.git' \
         'git remote add o jira.corp:x' 'rsync -av x jira.corp:/tmp' 'socat - TCP:jira.corp:80' "curl -H 'Host:jira.corp' http://10.0.0.1"; do
  caso - "$(cmd "$c")" true "burla corrigida: $c"
done
for c in "curl 'http://x${BS}@example.com/'" 'git push origin HEAD:feat/hooks-url-jira' 'git commit -m "guia do jira"' 'git push origin jira-fix' \
         'curl --connect-to example.com:443:example.org:443 https://example.com' 'curl -H "Host: example.com" http://10.0.0.1' 'socat - TCP:example.com:80'; do
  caso - "$(cmd "$c")" false "não regressão da burla: $c"
done
for u in 'https://jira.x.com/a' 'jira.x.com/a' 'JIRA-LAB:8080' 'https://u@jira.x.com' 'https://x.com/jira/sidia' 'https://sidia.atlassian.net/x' 'https://SIDIA.ATLASSIAN.NET' \
         'https://jira%2Ex.com/' 'https://confluence.atlassian.com@jira.x.com/' 'https://atlassian.com.jira.x.com/' 'https://jira.x.com/atlassian.com' \
         'https://docs.atlassian.com/x?redirect=https://jira.x.com/' 'https://docs.atlassian.com/x?redirect=https%3A%2F%2Fjira.x.com' \
         'https://docs.atlassian.com\@jira.x.com/' 'https://jira.x.com./'; do
  caso - "$(web web_fetch "$u")" true "web_fetch bloqueado: $u"
  caso - "$(nav "$u")" true "navegador bloqueado: $u"
done
caso - "$(mcp fetch fetch '{"url":"jira.exemplo.invalid/x"}')" true "MCP de fetch: url de Jira sem esquema"
caso - "$(mcp fetch fetch '{"opcoes":{"url":"https://jira.exemplo.invalid/x"}}')" true "MCP de fetch: url aninhada"
caso - "$(mcp fetch fetch '{"texto":"veja https://jira.exemplo.invalid/x"}')" true "MCP de fetch: URL com esquema em texto livre"
caso - "$(mcp fetch fetch '{"texto":"veja jira.md"}')" false "MCP de fetch: texto livre com jira.md (sem esquema) liberado"
caso - "$(jq -nc '{preToolUse:{toolName:"access_mcp_resource",parameters:{server_name:"fetch",uri:"https://jira.exemplo.invalid/x"}}}')" true "MCP: recurso com URI de Jira"
caso - "$(jq -nc '{preToolUse:{toolName:"access_mcp_resource",parameters:{server_name:"docs",uri:"https://docs.atlassian.com/jira"}}}')" false "MCP: recurso com URI de documentação pública"
# 3) documentação pública da Atlassian: liberada (mesmo com jira no caminho); o host real decide
for u in 'https://confluence.atlassian.com/jirasoftwareserver/jira-software-server-documentation-939937427.html' \
         'https://support.atlassian.com/jira-software-cloud/docs/x' 'https://developer.atlassian.com/server/jira/platform/rest/' \
         'https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/' 'https://community.atlassian.com/t5/Jira/x' \
         'https://www.atlassian.com/software/jira' 'https://DOCS.Atlassian.COM/jira/' 'https://www.atlassian.com./software/jira' \
         'https://jira.x.com@docs.atlassian.com/' 'https://x.atlassian.net/rest'; do
  caso - "$(web web_fetch "$u")" false "web_fetch liberado: $u"
  caso - "$(nav "$u")" false "navegador liberado: $u"
  caso - "$(cmd "curl -s $u")" false "curl liberado: $u"
done
caso - "$(cmd 'Invoke-WebRequest https://confluence.atlassian.com/jirasoftwareserver')" false "documentação: Invoke-WebRequest"
caso - "$(cmd 'iwr confluence.atlassian.com/jirasoftwareserver')" false "documentação: iwr sem esquema"
# 4) com a flag ligada no arquivo, tudo isso passa
for u in 'https://jira.x.com/a' 'jira.x.com/a' 'https://sidia.atlassian.net/x' 'https://x.com/jira/sidia'; do
  caso true "$(web web_fetch "$u")" false "flag ligada: web_fetch $u"
  caso true "$(nav "$u")" false "flag ligada: navegador $u"
  caso true "$(cmd "curl -s $u")" false "flag ligada: curl $u"
done

# ===== Plano 14, Tarefa 0: a flag vem do app/.env da raiz do repositório =====
CURL_J="curl -s $URL"
# flagVemDoArquivoDeVariaveis
casoa "JIRA_LIVE_ENABLED=$T\n" "$(cmd "$CURL_J")" false "arquivo: flag ligada libera"
casoa "JIRA_LIVE_ENABLED=false\n" "$(cmd "$CURL_J")" true "arquivo: flag false bloqueia"
casoa "OUTRA=1\n" "$(cmd "$CURL_J")" true "arquivo: sem a linha da flag bloqueia"
casoa "" "$(cmd "$CURL_J")" true "arquivo: vazio bloqueia"
for v in "\"$T\"" "'$T'" "$T # comentário" "TRUE" " $T " "\" $T \"" "'TrUe'"; do
  casoa "JIRA_LIVE_ENABLED=$v\n" "$(cmd "$CURL_J")" false "arquivo: valor [$v] liga"
done
casoa "export JIRA_LIVE_ENABLED=$T\n" "$(cmd "$CURL_J")" false "arquivo: prefixo export liga"
casoa "JIRA_LIVE_ENABLED=$T\r\n" "$(cmd "$CURL_J")" false "arquivo: CRLF liga"
casoa "\357\273\277JIRA_LIVE_ENABLED=$T\n" "$(cmd "$CURL_J")" false "arquivo: BOM liga"
casoa "A=1\nJIRA_LIVE_ENABLED=$T\nC=3\n" "$(cmd "$CURL_J")" false "arquivo: linha da flag entre outras liga"
for v in sim 1 0 "tr ue" "${T};" "${T}#x" "" "\"$T" "\"$T\" # c"; do
  casoa "JIRA_LIVE_ENABLED=$v\n" "$(cmd "$CURL_J")" true "arquivo: valor inválido [$v] bloqueia"
done
casoa "# JIRA_LIVE_ENABLED=$T\n" "$(cmd "$CURL_J")" true "arquivo: linha comentada não liga"
casoa "JIRA_LIVE_ENABLED_X=$T\n" "$(cmd "$CURL_J")" true "arquivo: nome parecido não liga"
casoa "JIRA_LIVE_ENABLED=$T\nJIRA_LIVE_ENABLED=false\n" "$(cmd "$CURL_J")" true "arquivo: a última ocorrência vale (desliga)"
casoa "JIRA_LIVE_ENABLED=false\nJIRA_LIVE_ENABLED=$T\n" "$(cmd "$CURL_J")" false "arquivo: a última ocorrência vale (liga)"
# ambienteDoProcessoNaoLiga / ambienteDoProcessoNaoDesliga (em todas as ferramentas que olham a flag)
casoe "$T" "JIRA_LIVE_ENABLED=false\n" "$(cmd "$CURL_J")" true "ambiente ligado e arquivo false: bloqueia comando"
casoe "$T" "-" "$(cmd "$CURL_J")" true "ambiente ligado e sem arquivo: bloqueia comando"
casoe "$T" "-" "$(web web_fetch 'https://jira.x.com/a')" true "ambiente ligado e sem arquivo: bloqueia web_fetch"
casoe "$T" "-" "$(nav 'https://jira.x.com/a')" true "ambiente ligado e sem arquivo: bloqueia navegador"
casoe "$T" "-" "$(mcp 'claude_ai_Atlassian_MCP' 'searchJiraIssuesUsingJql')" true "ambiente ligado e sem arquivo: bloqueia MCP do Atlassian"
casoe "$T" "-" "$(cmd "setx JIRA_LIVE_ENABLED $T")" true "ambiente ligado e sem arquivo: ligar a flag por setx continua barrado"
casoe "false" "JIRA_LIVE_ENABLED=$T\n" "$(cmd "$CURL_J")" false "ambiente false e arquivo ligado: libera comando"
casoe "false" "JIRA_LIVE_ENABLED=$T\n" "$(web web_fetch 'https://jira.x.com/a')" false "ambiente false e arquivo ligado: libera web_fetch"
casoe "" "JIRA_LIVE_ENABLED=$T\n" "$(nav 'https://jira.x.com/a')" false "ambiente vazio e arquivo ligado: libera navegador"
casoe "false" "JIRA_LIVE_ENABLED=$T\n" "$(mcp 'claude_ai_Atlassian_MCP' 'searchJiraIssuesUsingJql')" false "ambiente false e arquivo ligado: libera MCP"
casoe "false" "JIRA_LIVE_ENABLED=$T\n" "$(cmd 'echo $JIRA_SAMSUNG_TOKEN')" true "ambiente false e arquivo ligado: exibir token continua barrado"
# arquivoIlegivelFalhaFechado
rm -rf "$ARQ"; mkdir "$ARQ"
saida=$(printf '%s' "$(cmd "$CURL_J")" | env -u JIRA_LIVE_ENABLED bash "$hook" 2>/dev/null); r=$?
verificar "arquivo é diretório" true "arquivo ilegível (é um diretório): bloqueia"
saida=$(printf '%s' "$(cmd "$CURL_J")" | env JIRA_LIVE_ENABLED="$T" bash "$hook" 2>/dev/null); r=$?
verificar "arquivo é diretório, ambiente ligado" true "arquivo ilegível e ambiente ligado: bloqueia"
# a raiz vem do caminho do hook, não do diretório corrente nem do ambiente
OUTRO="$TMP/outro"; mkdir -p "$OUTRO/app"
printf 'JIRA_LIVE_ENABLED=%s\n' "$T" > "$OUTRO/app/.env"
rm -rf "$ARQ"; printf 'JIRA_LIVE_ENABLED=false\n' > "$ARQ"
saida=$(cd "$OUTRO" && printf '%s' "$(cmd "$CURL_J")" | env -u JIRA_LIVE_ENABLED CLAUDE_PROJECT_DIR="$OUTRO" bash "$hook" 2>/dev/null); r=$?
verificar "cwd com outro app/.env" true "raiz pelo caminho do hook: app/.env do diretório corrente não vale"
# o hook nunca imprime o conteúdo do arquivo
rm -rf "$ARQ"; printf 'JIRA_SAMSUNG_TOKEN=segredo-do-teste-123\nJIRA_LIVE_ENABLED=false\nOUTRA=valor-outra-456\n' > "$ARQ"
saida=$(printf '%s' "$(cmd "$CURL_J")" | env -u JIRA_LIVE_ENABLED bash "$hook" 2>&1); r=$?
case "$saida" in
  *segredo-do-teste-123*|*valor-outra-456*) echo "FALHA o hook imprimiu o conteúdo do arquivo: $saida"; falhas=$((falhas+1)) ;;
  *) echo "ok    o hook não imprime o conteúdo do arquivo" ;;
esac
# leituraDaIaAoArquivoContinuaBarrada: o hook lê o arquivo para si, mas a IA continua barrada (qualquer valor)
for f in - false true; do
  caso "$f" "$(ler 'app/.env')" true "IA [flag=$f]: read_file em app/.env"
  caso "$f" "$(ler '/x/repo/app/.ENV')" true "IA [flag=$f]: read_file em app/.ENV"
  caso "$f" "$(cmd 'cat app/.env')" true "IA [flag=$f]: cat app/.env"
  caso "$f" "$(cmd 'Get-Content app\.env')" true "IA [flag=$f]: Get-Content app\\.env"
  caso "$f" "$(cmd 'grep JIRA_LIVE_ENABLED app/.env')" true "IA [flag=$f]: grep na flag de app/.env"
  caso "$f" "$(esc write_to_file 'app/.env')" true "IA [flag=$f]: escrever app/.env"
  caso "$f" "$(cmd 'ln -sf /tmp/x app/.env')" true "IA [flag=$f]: symlink para app/.env"
  caso "$f" "$(cmd "echo JIRA_LIVE_ENABLED=$T >> app/.env")" true "IA [flag=$f]: ligar a flag em app/.env por echo"
  caso "$f" "$(escc write_to_file docs/x.md content "JIRA_LIVE_ENABLED=$T")" true "IA [flag=$f]: gravar texto que liga a flag"
  caso "$f" "$(cmd 'git add app/.env')" true "IA [flag=$f]: git add app/.env"
  caso "$f" "$(sf app 'JIRA_LIVE' '-')" true "IA [flag=$f]: buscar em app sem file_pattern"
done

echo "falhas: $falhas"
[ "$falhas" = 0 ]
