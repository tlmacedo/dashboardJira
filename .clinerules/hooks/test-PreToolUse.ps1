# Testes do hook PreToolUse do Cline no Windows (.clinerules/hooks/PreToolUse.ps1).
# Sem Pester: script simples, roda no Windows PowerShell 5.1 e no PowerShell 7 (pwsh).
#
# Cada caso chama o hook como o Cline chama (processo novo, -NoProfile -NonInteractive -ExecutionPolicy Bypass
# -File), com o JSON no stdin em UTF-8 e a flag JIRA_LIVE_ENABLED definida AQUI, por caso, no ambiente do
# processo filho (nunca na linha de comando). Confere: código de saída 0, stdout com UM objeto JSON e
# `cancel` booleano igual ao esperado (true = bloqueado, false = liberado).
#
# Uso (rode nos DOIS PowerShell, a partir da raiz do projeto):
#   powershell.exe -NoProfile -ExecutionPolicy Bypass -File .clinerules\hooks\test-PreToolUse.ps1
#   pwsh -NoProfile -ExecutionPolicy Bypass -File .clinerules\hooks\test-PreToolUse.ps1
# Opcional: -Executavel <caminho do powershell.exe ou pwsh.exe> para testar outro interpretador.
# Termina com "falhas: 0" e código 0 quando tudo passa.
#
# Os casos espelham .clinerules/hooks/test-PreToolUse.sh e .claude/hooks/test-guard.sh (o hook .ps1 reescreve
# as regras do guard.sh); ao mudar um, mude os outros.
param([string]$Executavel = '')

$ErrorActionPreference = 'Stop'
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
$hookOriginal = Join-Path $dir 'PreToolUse.ps1'
if ([string]::IsNullOrEmpty($Executavel)) { $Executavel = (Get-Process -Id $PID).Path }
$utf8 = New-Object System.Text.UTF8Encoding($false)
$script:falhas = 0
$script:total = 0

# Plano 14, Tarefa 0: a flag vem do app\.env da raiz do repositório (raiz deduzida do caminho do hook). Os testes
# montam uma raiz de mentira numa pasta temporária (cópia do hook em .clinerules\hooks e um app\.env de teste);
# o app\.env real do repositório NUNCA é lido nem escrito. A flag do AMBIENTE é removida por padrão.
$tmp = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), 'hooktest-' + [guid]::NewGuid().ToString('N'))
[void][System.IO.Directory]::CreateDirectory([System.IO.Path]::Combine($tmp, '.clinerules', 'hooks'))
[void][System.IO.Directory]::CreateDirectory([System.IO.Path]::Combine($tmp, 'app'))
$hook = [System.IO.Path]::Combine($tmp, '.clinerules', 'hooks', 'PreToolUse.ps1')
[System.IO.File]::Copy($hookOriginal, $hook, $true)
$arqEnv = [System.IO.Path]::Combine($tmp, 'app', '.env')

Write-Output "interpretador: $Executavel ($($PSVersionTable.PSVersion) neste processo)"

# o arquivo precisa ter BOM para o PowerShell 5.1 ler os acentos corretamente
foreach ($f in @($hookOriginal, $MyInvocation.MyCommand.Path)) {
    $b = [System.IO.File]::ReadAllBytes($f)
    if (-not ($b.Length -ge 3 -and $b[0] -eq 0xEF -and $b[1] -eq 0xBB -and $b[2] -eq 0xBF)) {
        Write-Output "FALHA arquivo sem BOM UTF-8: $f"
        $script:falhas++
    }
}

function Rodar-Hook([string]$flag, [string]$json) {
    # flag '-' = sem app\.env; senão o app\.env de teste recebe só a linha da flag. O ambiente do filho fica sem a flag.
    $conteudo = $null
    if ($flag -ne '-') { $conteudo = "JIRA_LIVE_ENABLED=$flag`n" }
    return (Rodar-Hook-Bruto $conteudo $null $json)
}

function Rodar-Hook-Bruto($conteudoArquivo, $ambiente, [string]$json) {
    # $conteudoArquivo: texto bruto do app\.env de teste ($null = sem arquivo, 'DIR' = é uma pasta);
    # $ambiente: valor da flag no ambiente do filho ($null = ausente)
    if (Test-Path -LiteralPath $arqEnv) { Remove-Item -LiteralPath $arqEnv -Recurse -Force }
    if ($conteudoArquivo -eq 'DIR') { [void][System.IO.Directory]::CreateDirectory($arqEnv) }
    elseif ($null -ne $conteudoArquivo) { [System.IO.File]::WriteAllText($arqEnv, [string]$conteudoArquivo, $utf8) }
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $Executavel
    $psi.Arguments = '-NoProfile -NonInteractive -ExecutionPolicy Bypass -File "' + $hook + '"'
    $psi.UseShellExecute = $false
    $psi.RedirectStandardInput = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.StandardOutputEncoding = $utf8
    $psi.StandardErrorEncoding = $utf8
    $psi.CreateNoWindow = $true
    $psi.WorkingDirectory = $dir
    if ($psi.EnvironmentVariables.ContainsKey('JIRA_LIVE_ENABLED')) { $psi.EnvironmentVariables.Remove('JIRA_LIVE_ENABLED') }
    if ($null -ne $ambiente) { $psi.EnvironmentVariables['JIRA_LIVE_ENABLED'] = [string]$ambiente }
    $p = [System.Diagnostics.Process]::Start($psi)
    $bytes = $utf8.GetBytes($json)
    $p.StandardInput.BaseStream.Write($bytes, 0, $bytes.Length)
    $p.StandardInput.BaseStream.Flush()
    $p.StandardInput.Close()
    $tarefaErro = $p.StandardError.ReadToEndAsync()
    $saida = $p.StandardOutput.ReadToEnd()
    if (-not $p.WaitForExit(30000)) { try { $p.Kill() } catch { }; return @{ codigo = -1; saida = $saida; erro = 'tempo esgotado (30 s)' } }
    return @{ codigo = $p.ExitCode; saida = $saida; erro = $tarefaErro.Result }
}

function Caso([string]$flag, [string]$json, [bool]$esperado, [string]$descricao) {
    Verificar (Rodar-Hook $flag $json) "flag=$flag" $esperado $descricao
}
function CasoArq($conteudo, [string]$json, [bool]$esperado, [string]$descricao) {
    # conteúdo bruto do app\.env de teste; ambiente sem a flag
    Verificar (Rodar-Hook-Bruto $conteudo $null $json) 'arquivo' $esperado $descricao
}
function CasoAmb([string]$ambiente, $conteudo, [string]$json, [bool]$esperado, [string]$descricao) {
    # valor da flag no AMBIENTE do processo filho + conteúdo bruto do app\.env de teste
    Verificar (Rodar-Hook-Bruto $conteudo $ambiente $json) "ambiente=$ambiente" $esperado $descricao
}
function Verificar($r, [string]$rotulo, [bool]$esperado, [string]$descricao) {
    $script:total++
    $obtido = $null
    $ok = $false
    try {
        $linhas = @($r.saida -split "`r?`n" | Where-Object { $_.Trim() -ne '' })
        if ($linhas.Count -eq 1) {
            $o = $linhas[0] | ConvertFrom-Json
            $c = $o.PSObject.Properties['cancel']
            if ($null -ne $c -and $c.Value -is [bool]) {
                $obtido = $c.Value
                $ok = ($r.codigo -eq 0 -and $obtido -eq $esperado -and $null -ne $o.PSObject.Properties['errorMessage'] -and $null -ne $o.PSObject.Properties['contextModification'])
            }
        }
    } catch { $ok = $false }
    if ($ok) { Write-Output "ok    [$rotulo] $descricao" }
    else {
        Write-Output "FALHA [$rotulo] $descricao (exit $($r.codigo), cancel=$obtido, esperado $esperado; saída: $($r.saida.Trim()); erro: $($r.erro.Trim()))"
        $script:falhas++
    }
}

# ---------- montadores de JSON ----------
function J($obj) { return ($obj | ConvertTo-Json -Compress -Depth 10) }
function Cmd([string]$c) { J ([ordered]@{ taskId = 't'; preToolUse = [ordered]@{ toolName = 'execute_command'; parameters = [ordered]@{ command = $c; requires_approval = 'false' } } }) }
function CmdTxt([string]$c) { J ([ordered]@{ taskId = 't'; preToolUse = [ordered]@{ toolName = 'execute_command'; parameters = (J ([ordered]@{ command = $c })) } }) }
function CmdCli([string]$c) { J ([ordered]@{ tool_call = [ordered]@{ name = 'run_commands'; input = [ordered]@{ commands = @($c) } } }) }
function Esc([string]$t, [string]$p) { J ([ordered]@{ preToolUse = [ordered]@{ toolName = $t; parameters = [ordered]@{ path = $p; content = 'x' } } }) }
function Ler([string]$p) { J ([ordered]@{ preToolUse = [ordered]@{ toolName = 'read_file'; parameters = [ordered]@{ path = $p } } }) }
function Mcp([string]$s, [string]$t, [string]$args1 = '{}') { J ([ordered]@{ preToolUse = [ordered]@{ toolName = 'use_mcp_tool'; parameters = [ordered]@{ server_name = $s; tool_name = $t; arguments = $args1 } } }) }
function Web([string]$t, [string]$u) { J ([ordered]@{ preToolUse = [ordered]@{ toolName = $t; parameters = [ordered]@{ url = $u } } }) }
function Nav([string]$u) { J ([ordered]@{ preToolUse = [ordered]@{ toolName = 'browser_action'; parameters = [ordered]@{ action = 'launch'; url = $u } } }) }
function Busca([string]$q) { J ([ordered]@{ preToolUse = [ordered]@{ toolName = 'web_search'; parameters = [ordered]@{ query = $q } } }) }

$URL = 'https://jira.exemplo.invalid/rest/api/2/search'

# ---------- trava ativa (flag ausente, false ou inválida) ----------
foreach ($f in @('-', 'false', 'FALSE', 'talvez', ' ', '1')) {
    Caso $f (Cmd "curl -s $URL") $true 'trava: curl para o Jira'
    Caso $f (Cmd 'JIRA_LIVE_ENABLED=true ./gradlew test') $true 'trava: ligar a flag dentro do comando'
    Caso $f (Mcp 'claude_ai_Atlassian_MCP' 'searchJiraIssuesUsingJql') $true 'trava: MCP do Atlassian'
    Caso $f (Web 'web_fetch' 'https://sidia.x.atlassian.net/wiki/x') $true 'trava: web_fetch para o Cloud da Atlassian com sidia'
    Caso $f (Nav 'https://JIRA.empresa.invalid/browse/X-1') $true 'trava: navegador com host jira (sem diferenciar maiúsculas)'
    Caso $f (Nav 'https://empresa.invalid/JIRA/sidia/browse/X-1') $true 'trava: navegador com jira e sidia no caminho'
    # 28/10/2026: documentação pública e palavra jira solta passam
    Caso $f (Web 'web_fetch' 'https://docs.atlassian.com/jira/rest') $false 'liberado: web_fetch para documentação pública da Atlassian'
    Caso $f (Nav 'https://empresa.invalid/JIRA/browse/X-1') $false 'liberado: navegador com JIRA só no caminho (sem sidia)'
}
Caso '-' (CmdTxt "curl -s $URL") $true 'trava: parâmetros em texto JSON'
Caso '-' (CmdCli "wget $URL") $true 'trava: formato da CLI (run_commands)'
Caso '-' (Cmd 'echo $JIRA_TOKEN') $true 'trava: variável de conexão do Jira'
Caso '-' (Mcp 'jira-local' 'search') $true 'trava: servidor MCP com jira no nome'
Caso '-' (Mcp 'fetch' 'fetch' '{"url":"https://sidia.x.atlassian.net/wiki"}') $true 'trava: MCP de fetch com URL do Cloud da Atlassian com sidia'
Caso 'false' (Cmd 'JIRA_LIVE_ENABLED=false ./gradlew test') $false 'liberado: flag false no comando não liga nada'
# ligar a flag por arquivo de configuração: só o usuário escreve em settings do Claude e override do compose
$JsonOn = 'printf ''{"env":{"JIRA_LIVE_ENABLED":"true"}}'' > .claude/settings.local.json'
foreach ($f in @('-', 'false', 'true')) {
    Caso $f (Cmd $JsonOn) $true 'settings: printf JSON liga a flag'
    Caso $f (Esc 'write_to_file' '.claude/settings.local.json') $true 'settings: write_to_file em settings.local.json'
    Caso $f (Esc 'replace_in_file' 'C:\x\.claude\settings.json') $true 'settings: replace_in_file em settings.json (barras do Windows)'
    Caso $f (Esc 'write_to_file' 'app/docker-compose.override.yml') $true 'compose: write_to_file no override'
    Caso $f (Cmd 'tee app/docker-compose.override.yml < x.yml') $true 'compose: tee no override'
    Caso $f (Cmd 'cp x.json .claude/settings.local.json') $true 'settings: cp para settings.local.json'
    Caso $f (Ler '.claude/settings.json') $false 'settings: ler settings.json liberado'
    Caso $f (Cmd 'cat .claude/settings.json') $false 'settings: cat settings.json liberado'
    Caso $f (Esc 'write_to_file' 'docs/x.json') $false 'settings: docs/x.json liberado'
}
Caso 'false' (Cmd 'echo "JIRA_LIVE_ENABLED: true" >> x.yml') $true 'trava: forma YAML liga a flag'

# ---------- onda final do Plano 4A: normalização, conteúdo e negação por padrão (espelha test-guard.sh) ----------
# O valor "true" é montado por variável: o literal da flag ligada não aparece neste arquivo.
$T = 'true'
function EscC([string]$t, [string]$p, [string]$k, [string]$c) {
    $par = [ordered]@{ path = $p }
    $par[$k] = $c
    J ([ordered]@{ preToolUse = [ordered]@{ toolName = $t; parameters = $par } })
}
foreach ($f in @('-', 'false', 'true')) {
    foreach ($p in @('.claude//settings.local.json', '/x/.claude/./settings.json', '/x/.claude/hooks/../settings.json',
            '.CLAUDE/Settings.Local.JSON', 'C:\x\.claude\settings.json.', '/x/CLAUDE~1/settings.json', '/x/.claude/SETTIN~1.JSO',
            'app//Docker-Compose.Override.YML', 'app/docker-compose.override.yml:x', 'app/docker-compose.yml', 'app\compose.yaml ', 'app/DOCKER~1.YML')) {
        Caso $f (Esc 'write_to_file' $p) $true "normalização: write_to_file em '$p'"
    }
    Caso $f '{"preToolUse":{"toolName":"apply_patch","parameters":{"input":"*** Begin Patch\n*** Update File: app//docker-compose.yml\n@@\n-a\n+b\n*** End Patch"}}}' $true 'normalização: apply_patch no compose'
    foreach ($p in @('.claude/hooks/x.sh', 'docs/x.json', 'docs/compose-notas.md', '.claude/skills/a.md')) {
        Caso $f (Esc 'write_to_file' $p) $false "não regressão: write_to_file em '$p'"
    }
    foreach ($c in @('cd .claude && cp /tmp/x settings.local.json', 'pushd .claude; mv x settings.json', 'cp x .claude//settings.json',
            'cp x settings.local.json', 'cp x CLAUDE~1/SETTIN~1.JSO', 'Copy-Item x .CLAUDE\SETTINGS.JSON')) {
        Caso $f (Cmd $c) $true "cd/normalização: $c"
    }
    # conteúdo
    Caso $f (EscC 'write_to_file' 'docs/x.md' 'content' ('{"JIRA_LIVE_ENABLED": "' + $T + '"}')) $true 'conteúdo: write_to_file content JSON'
    Caso $f (EscC 'replace_in_file' 'docs/x.md' 'diff' ("------- SEARCH`na`n=======`nJIRA_LIVE_ENABLED=$T`n+++++++ REPLACE")) $true 'conteúdo: replace_in_file diff'
    Caso $f (EscC 'editor' 'docs/x.md' 'new_str' "JIRA_LIVE_ENABLED: $T") $true 'conteúdo: editor new_str'
    Caso $f (EscC 'replace_in_file' 'docs/x.md' 'replace' "jira_live_enabled = $T") $true 'conteúdo: replace (minúsculas)'
    Caso $f (J ([ordered]@{ preToolUse = [ordered]@{ toolName = 'apply_patch'; parameters = [ordered]@{ input = "*** Begin Patch`n*** Add File: docs/x.md`n+JIRA_LIVE_ENABLED=$T`n*** End Patch" } } })) $true 'conteúdo: apply_patch'
    Caso $f (Cmd "echo JIRA_LIVE_ENABLED=$T >> docs/x.md") $true 'conteúdo: comando grava a flag ligada'
    Caso $f (EscC 'write_to_file' 'docs/x.md' 'content' 'JIRA_LIVE_ENABLED=false') $false 'não regressão: conteúdo com a flag false'
    Caso $f (EscC 'write_to_file' 'docs/x.md' 'content' 'JIRA_LIVE_ENABLED: ${JIRA_LIVE_ENABLED:-false}') $false 'não regressão: padrão do compose'
    # negação por padrão
    foreach ($c in @('python3 -c "open(''.claude/settings.json'',''w'')"', 'perl -pi -e s/a/b/ .claude/settings.json', 'node -e x app/docker-compose.yml',
            'ruby -e x app/compose.yaml', 'php -r x app/compose.yaml', 'pwsh -c x .claude/settings.json', 'powershell -c x app/docker-compose.yml',
            'git checkout -- app/docker-compose.yml', 'git restore app/docker-compose.override.yml', 'git apply --include=app/docker-compose.yml p.diff',
            'git stash pop && cat app/docker-compose.yml', 'patch app/docker-compose.yml < p.diff', 'rsync x .claude/settings.json',
            'curl -o app/docker-compose.override.yml https://example.com/x', 'wget -O .claude/settings.local.json https://example.com/x',
            'dd if=x of=app/docker-compose.yml', 'install x .claude/settings.json', 'ln -sf x .claude/settings.local.json',
            'sed -i s/a/b/ app/docker-compose.yml', 'echo x > app/docker-compose.yml', 'echo x >> .claude/settings.json',
            'echo x &> app/docker-compose.yml', 'echo x 1> app/docker-compose.yml', 'Set-Content app\docker-compose.yml x',
            'cp a .vscode/settings.json', "sed -n 's/a/b/w out' app/docker-compose.yml", 'gawk -i inplace 1 app/docker-compose.yml',
            'git diff --output=x app/docker-compose.yml', 'docker compose -f app/docker-compose.yml config -o x', 'less -o log app/docker-compose.yml',
            'sort -o app/docker-compose.yml x', 'bash -c "cp x .claude/settings.json"', 'cat $(cp x .claude/settings.json)')) {
        Caso $f (Cmd $c) $true "nega por padrão: $c"
    }
    foreach ($c in @('cat .claude/settings.json', 'grep -n env app/docker-compose.yml', 'head -5 app/docker-compose.yml', 'sed -n 1,5p app/docker-compose.yml',
            'git diff app/docker-compose.yml', 'cd app && docker compose -f docker-compose.yml up -d', 'Select-String env app\docker-compose.yml',
            'cat app/docker-compose.yml 2>/dev/null', 'git show HEAD:.claude/settings.json', 'awk ''{print $1}'' app/docker-compose.yml')) {
        Caso $f (Cmd $c) $false "não regressão: $c"
    }
}
Caso '-' (Ler 'app/docker-compose.yml') $false 'não regressão: ler o compose'
Caso 'true' (Cmd "JIRA_LIVE_ENABLED=$T ./gradlew test 2>&1") $false 'não regressão: flag no comando sem escrita, com a flag true'
# coerência com o guard.sh da Tarefa 11 (mesma leitura da flag, mesmas bordas)
Caso ' true ' (Cmd "curl -s $URL") $false 'liberado: espaços nas pontas são ignorados'
Caso 'True' (Cmd "curl -s $URL") $false 'liberado: True (maiúscula inicial)'
foreach ($f in @('tr ue', 'true;', 'yes', 'sim', '0', 'truee')) {
    Caso $f (Cmd "curl -s $URL") $true "trava: flag '$f' não libera (só true libera)"
    Caso $f (Mcp 'claude_ai_Atlassian_MCP' 'searchJiraIssuesUsingJql') $true "trava: MCP com flag '$f'"
}
Caso 'false' (Cmd 'env JIRA_LIVE_ENABLED=true curl -s https://x.invalid/a') $true 'trava: env liga a flag dentro do comando'
Caso 'false' (Cmd 'export JIRA_LIVE_ENABLED=true') $true 'trava: export liga a flag dentro do comando'
Caso 'false' (Cmd '(JIRA_LIVE_ENABLED=true curl -s https://x.invalid/a)') $true 'trava: ligar a flag em subshell'
Caso 'false' (Cmd 'JIRA_LIVE_ENABLED="true" curl -s https://x.invalid/a') $true 'trava: ligar a flag com aspas'
Caso '-' (Cmd 'JIRA_LIVE_ENABLED=true curl -s https://x.invalid/a') $true 'trava: flag ausente, ligar dentro do comando'
Caso 'true' (Cmd 'curl -s -H "Authorization: Bearer $JIRA_SAMSUNG_TOKEN" https://jira.exemplo.invalid/rest/api/2/myself') $false 'liberado: token como argumento de outro comando com a flag true'
Caso 'true' (Cmd 'JIRA_LIVE_ENABLED=true ./gradlew test') $false 'liberado: ligar a flag no comando com a flag true no ambiente (igual ao guard.sh)'
Caso 'true' (Cmd 'ls && echo $JIRA_SAMSUNG_TOKEN') $true 'trava mesmo com true: exibir token depois de &&'
Caso 'true' (Cmd 'env | grep -i jira') $true 'trava mesmo com true: env canalizado para grep de jira'
Caso ' TRUE ' (Cmd 'echo $JIRA_SAMSUNG_TOKEN') $true 'trava mesmo com TRUE e espaços: exibir token'
# extras do Windows/PowerShell
Caso '-' (Cmd "Invoke-WebRequest -Uri $URL") $true 'trava: Invoke-WebRequest'
Caso '-' (Cmd 'iwr jira.exemplo.invalid') $true 'trava: iwr sem esquema na URL'
Caso '-' (Cmd 'irm https://sidia.x.atlassian.net/rest/api/3/myself') $true 'trava: Invoke-RestMethod'
Caso '-' (Cmd 'curl.exe -s jira.exemplo.invalid') $true 'trava: curl.exe'
Caso '-' (Cmd '(New-Object Net.WebClient).DownloadString("http://jira.exemplo.invalid")') $true 'trava: Net.WebClient'
Caso '-' (Cmd 'Write-Output $env:JIRA_SAMSUNG_TOKEN') $true 'trava: $env:JIRA_* no PowerShell'
Caso '-' (Cmd 'echo $env:jira_token') $true 'trava: variável em minúsculas (o Windows não diferencia)'
Caso '-' (Cmd 'echo %JIRA_SIDIA_URL%') $true 'trava: variável no cmd.exe'
Caso '-' (Cmd '$env:JIRA_LIVE_ENABLED="true"') $true 'trava: ligar a flag no PowerShell'
Caso '-' (Cmd 'setx JIRA_LIVE_ENABLED true') $true 'trava: ligar a flag com setx'
Caso '-' (Cmd "[Environment]::SetEnvironmentVariable('JIRA_LIVE_ENABLED','true','User')") $true 'trava: ligar a flag com SetEnvironmentVariable'
Caso '-' (Cmd 'Set-Item env:JIRA_LIVE_ENABLED -Value true') $true 'trava: ligar a flag com Set-Item'
Caso '-' (Cmd 'set JIRA_LIVE_ENABLED=1') $true 'trava: ligar a flag no cmd.exe'
Caso '-' (Nav 'file:///C:/dev/dashboardJira/docs/design/prototipo/a.html') $false 'liberado (28/10): pasta com Jira no nome não bloqueia mais'

# ---------- liberado (flag true, sem diferenciar maiúsculas) ----------
Caso 'true' (Cmd "curl -s $URL") $false 'liberado: curl para o Jira com a flag true'
Caso 'TRUE' (Cmd "curl -s $URL") $false 'liberado: TRUE vale como true (igual ao Settings.kt)'
Caso ' true ' (Cmd "Invoke-WebRequest $URL") $false 'liberado: espaços em volta da flag são ignorados'
Caso 'true' (Mcp 'claude_ai_Atlassian_MCP' 'searchJiraIssuesUsingJql') $false 'liberado: MCP do Atlassian com a flag true'
Caso 'true' (Web 'web_fetch' 'https://docs.atlassian.com/jira/rest') $false 'liberado: web_fetch da Atlassian com a flag true'
Caso 'true' (Nav 'https://jira.exemplo.invalid/browse/X-1') $false 'liberado: navegador no Jira com a flag true'
Caso 'true' (Cmd 'echo $JIRA_SAMSUNG_TOKEN') $true 'trava mesmo com true: exibir token (Plano 4A, Decisão 10)'
Caso 'true' (Cmd 'printenv JIRA_SIDIA_PASSWORD') $true 'trava mesmo com true: exibir senha'
Caso 'true' (Cmd '$env:JIRA_SAMSUNG_TOKEN') $true 'trava mesmo com true: variável sozinha imprime o valor no PowerShell'
Caso 'true' (Cmd 'Get-ChildItem env:JIRA_SIDIA_TOKEN') $true 'trava mesmo com true: Get-ChildItem env: do token'
Caso 'true' (Cmd 'cd app/backend && ./gradlew run') $false 'liberado: subir o app com a flag true'
Caso 'true' (Cmd 'cd app\backend; .\gradlew.bat run') $false 'liberado: subir o app no Windows com a flag true'

# ---------- comandos comuns sempre liberados ----------
Caso '-' (Cmd 'ls -la') $false 'liberado: ls'
Caso '-' (Cmd 'cd app/backend && ./gradlew test') $false 'liberado: testes do backend'
Caso '-' (Cmd 'cd app\backend; .\gradlew.bat test') $false 'liberado: testes do backend no Windows'
Caso '-' (Cmd 'cd app\frontend; pnpm vitest run') $false 'liberado: testes do frontend'
Caso '-' (Cmd 'curl -s https://example.com/x.png') $false 'liberado: curl fora do Jira'
Caso '-' (Cmd 'Invoke-WebRequest https://docs.cline.bot/') $false 'liberado: Invoke-WebRequest fora do Jira'
Caso '-' (Cmd 'python -m pytest tests/test_jira_client.py') $false 'liberado: nome de arquivo com jira_ (não é variável)'
Caso '-' (Cmd 'env FOO=1 ./gradlew test') $false 'liberado: env como prefixo de comando'
Caso '-' (Cmd 'printenv PATH') $false 'liberado: printenv de uma variável comum'
Caso '-' (Cmd 'Get-ChildItem env:PATH') $false 'liberado: Get-ChildItem env: de uma variável comum'
Caso '-' (Mcp 'context7' 'get-library-docs') $false 'liberado: outro servidor MCP'
Caso '-' (Web 'web_fetch' 'https://docs.cline.bot/') $false 'liberado: web_fetch fora do Jira'
Caso '-' (Nav 'http://127.0.0.1:8080/') $false 'liberado: navegador no painel local'
Caso '-' (Busca 'jira rest api search jql') $false 'liberado: web_search (busca genérica) mesmo citando jira'
Caso '-' '{"preToolUse":{"toolName":"search_files","parameters":{"path":".","regex":"JIRA_"}}}' $true 'busca: search_files na raiz sem file_pattern (antes liberado)'
Caso '-' (Ler 'app/.env.example') $false 'liberado: leitura do modelo .env.example'
Caso '-' (Ler 'config/papeis.exemplo.yaml') $false 'liberado: leitura do modelo papeis.exemplo.yaml'
Caso '-' (Cmd 'Get-Content config\equipes.exemplo.yaml') $false 'liberado: leitura do modelo no PowerShell'

# ---------- listar todo o ambiente: barrado com qualquer valor da flag ----------
foreach ($f in @('-', 'true')) {
    Caso $f (Cmd 'env') $true 'ambiente: env sozinho'
    Caso $f (Cmd 'printenv') $true 'ambiente: printenv sozinho'
    Caso $f (Cmd 'set') $true 'ambiente: set sozinho (cmd.exe)'
    Caso $f (Cmd 'cmd /c set') $true 'ambiente: cmd /c set'
    Caso $f (Cmd 'Get-ChildItem env:') $true 'ambiente: Get-ChildItem env:'
    Caso $f (Cmd 'gci env:* | Sort-Object Name') $true 'ambiente: gci env:*'
    Caso $f (Cmd '[Environment]::GetEnvironmentVariables()') $true 'ambiente: GetEnvironmentVariables'
}

# ---------- arquivos protegidos: valem com QUALQUER valor da flag ----------
foreach ($f in @('-', 'true')) {
    Caso $f (Esc 'write_to_file' 'app/.env') $true 'arquivos: escrever .env'
    Caso $f (Esc 'write_to_file' 'app\.env') $true 'arquivos: escrever .env com barra invertida'
    Caso $f (Esc 'replace_in_file' 'config/papeis.yaml') $true 'arquivos: editar papeis.yaml'
    Caso $f (Esc 'replace_in_file' 'C:\dev\painel\config\Squads.yaml') $true 'arquivos: editar squads.yaml (caminho Windows, maiúsculas)'
    Caso $f (Ler '.env') $true 'arquivos: ler .env'
    Caso $f (Ler 'app\.env.local') $true 'arquivos: ler .env.local'
    Caso $f (Ler 'config/ausencias.yaml') $true 'arquivos: ler ausencias.yaml'
    Caso $f (Cmd 'git add -f .env.example') $true 'arquivos: git add forçado'
    Caso $f (Cmd 'echo x > .env') $true 'arquivos: redirecionar para .env'
    Caso $f (Cmd 'Set-Content -Path app\.env -Value x') $true 'arquivos: Set-Content em .env'
    Caso $f (Cmd 'Copy-Item app\.env.example app\.env') $true 'arquivos: Copy-Item para .env'
    Caso $f (Cmd 'Get-Content app\.env') $true 'arquivos: Get-Content .env'
    Caso $f (Cmd 'type config\papeis.yaml') $true 'arquivos: type papeis.yaml'
    Caso $f (Cmd 'cat .env') $true 'arquivos: cat .env'
    Caso $f (Cmd 'Select-String TOKEN app\.env') $true 'arquivos: Select-String em .env'
}
Caso '-' (Esc 'write_to_file' 'app/.env.example') $false 'arquivos: modelo .env.example liberado'
Caso '-' (Esc 'write_to_file' 'docs/ferramentas-claude-para-o-cline/README.md') $false 'arquivos: doc comum liberada'
Caso '-' '{"preToolUse":{"toolName":"apply_patch","parameters":{"input":"*** Begin Patch\n*** Update File: config/equipes.yaml\n@@\n-a\n+b\n*** End Patch"}}}' $true 'arquivos: apply_patch em equipes.yaml'
Caso '-' '{"preToolUse":{"toolName":"apply_patch","parameters":{"input":"*** Begin Patch\r\n*** Add File: app\\.env\r\n+X=1\r\n*** End Patch"}}}' $true 'arquivos: apply_patch criando app\.env (CRLF)'

# ---------- espelho do .claude/hooks/test-guard.sh (modo jira com flag ausente; modo arquivos) ----------
$espelhoJira = @(
    @('curl -s https://jira.exemplo.com/rest/api/2/search', $true),
    @('python3 -c "import urllib.request as u;u.urlopen(\"https://sidia.x.atlassian.net/a\")"', $true),
    @('echo $JIRA_TOKEN', $true),
    @('echo $JIRA_SAMSUNG_TOKEN', $true),
    @('printenv JIRA_SIDIA_URL', $true),
    @('JIRA_LIVE_ENABLED=false ./gradlew test', $false),
    @('JIRA_LIVE_ENABLED=true ./gradlew test', $true),
    @('JIRA_LIVE_ENABLED=1 ./gradlew test', $true),
    @('JIRA_LIVE_ENABLED="true" ./gradlew test', $true),
    @("JIRA_LIVE_ENABLED='true' ./gradlew test", $true),
    @('JIRA_LIVE_ENABLED = true ./gradlew test', $true),
    @('export JIRA_LIVE_ENABLED=" true"', $true),
    @('JIRA_LIVE_ENABLED=TRUE ./gradlew test', $true),
    @('env JIRA_LIVE_ENABLED=true ./gradlew test', $true),
    @('JIRA_LIVE_ENABLED_X=1 ./gradlew test', $true),
    @('JIRA_LIVE_ENABLED="false" ./gradlew test', $false),
    @('python -m jiradash serve', $true),
    @('ls -la', $false),
    @('curl -s https://example.com/x.png', $false),
    @('python -m pytest tests/test_jira_client.py', $false),
    @('grep -n JIRA_SAMSUNG_TOKEN app/backend/src/main/kotlin/br/sidia/painel/config/Settings.kt', $true),
    @('curl -s http://127.0.0.1:8080/api/health # em C:\dev\dashboardJira', $false),
    @('JIRA_LIVE_ENABLED=$(echo true) ./gradlew test', $false)
)
foreach ($e in $espelhoJira) { Caso '-' (Cmd $e[0]) $e[1] "espelho guard jira: $($e[0])" }
$espelhoArquivos = @(
    @('echo x > .env', $true),
    @('git add .env', $true),
    @('git add -f .env.example', $true),
    @('git add .env.example', $false),
    @('git add docs/superpowers/plans/plano-01-fundacao.md', $false),
    @('git add --force x', $true),
    @('git add x -f', $true),
    @('git add -fA', $true),
    @('git add -Af x', $true),
    @('git add --forc x', $true),
    @("git add '-f' x", $true),
    @('git add -A', $false),
    @('git add .env.example .env.production', $true),
    @('cp .env.example .env', $true),
    @('sed -i s/a/b/ config/equipes.yaml', $true),
    @('cat config/equipes.exemplo.yaml', $false),
    @('git status', $false),
    @('git commit -m "docs: nota sobre arquivos de ambiente"', $false)
)
foreach ($e in $espelhoArquivos) { Caso 'true' (Cmd $e[0]) $e[1] "espelho guard arquivos: $($e[0])" }
Caso '-' (Esc 'write_to_file' '/x/app/.env') $true 'espelho guard arquivos: Edit .env'
Caso '-' (Esc 'write_to_file' '.env.example') $false 'espelho guard arquivos: Edit .env.example'
Caso '-' (Esc 'write_to_file' 'config/equipes.yaml') $true 'espelho guard arquivos: Write equipes.yaml'

# ---------- 1) busca e listagem (search_files, list_files, list_code_definition_names) ----------
function Sf([string]$p, [string]$r, [string]$g) {
    $par = [ordered]@{ path = $p; regex = $r }
    if ($g -ne '-') { $par['file_pattern'] = $g }
    J ([ordered]@{ preToolUse = [ordered]@{ toolName = 'search_files'; parameters = $par } })
}
function Lst([string]$t, [string]$p) { J ([ordered]@{ preToolUse = [ordered]@{ toolName = $t; parameters = [ordered]@{ path = $p; recursive = 'true' } } }) }
foreach ($f in @('-', 'true')) {
    Caso $f (Sf '.' 'JIRA_' '.env') $true "busca: file_pattern '.env'"
    Caso $f (Sf '.' 'TOKEN' '.env*') $true "busca: file_pattern '.env*'"
    Caso $f (Sf 'src' '\.env' '*.kt') $true "busca: regex '\.env' (mesmo com pattern seguro)"
    Caso $f (Sf 'app\config' 'id:' '*.yaml') $true "busca: file_pattern '*.yaml' em app\config"
    Caso $f (Sf '.' 'TODO' '-') $true 'busca: search_files na raiz sem file_pattern'
    Caso $f (Sf 'app/config' 'x' '-') $true 'busca: app/config sem file_pattern'
    Caso $f (Sf '.' 'x' 'papeis.yaml') $true 'busca: file_pattern papeis.yaml exato'
    Caso $f (Sf '.' 'x' '*') $true "busca: file_pattern '*'"
    Caso $f (Sf '.' 'x' '*.{kt,yaml}') $true 'busca: grupo com extensão insegura'
    Caso $f (Sf '.' 'x' '!*.kt') $true 'busca: negação no pattern'
    Caso $f (Sf 'app\.ENV' 'x' '-') $true 'busca: path .ENV'
    Caso $f (Sf '.' 'x' 'Filtros_Status_tool.txt') $true 'busca: arquivo de filtros'
    Caso $f (Lst 'list_files' 'app\.env') $true 'listagem: list_files em .env'
    Caso $f (Lst 'list_code_definition_names' 'config\Papeis.YAML') $true 'listagem: definições em Papeis.YAML'
}
Caso '-' (Sf 'app\backend\src' 'fun ' '*.kt') $false "não regressão: buscar em src com '*.kt'"
Caso '-' (Sf '.' 'TODO:' '*.{kt,tsx,md}') $false "não regressão: grupo de extensões seguras (regex com ':')"
# chaves com alternativa vazia (no bash o `read` descartava o vazio e *{.kt,} passava; o vazio expande para *)
foreach ($g in @('*{.kt,}', '{,.kt}', '*{.kt,,.md}', '*{}', '*{,}', '{.kt}{,.md}', '*{.kt', '*.kt}', '*{{.kt,}}')) {
    Caso '-' (Sf '.' 'x' $g) $true "busca: chaves inseguras '$g'"
}
Caso '-' (Sf '.' 'x' '*.{kt,tsx}') $false "não regressão: '*.{kt,tsx}'"
Caso '-' (Sf '.' 'x' '*{.kt,.md}') $false "não regressão: '*{.kt,.md}'"
# nome exato sem curinga: pode ser PASTA; só extensão segura ou a lista curta de arquivos conhecidos
foreach ($g in @('config', 'app/config', 'secrets', 'papeis.yaml', '.env', 'Config', '{config,README.md}')) {
    Caso '-' (Sf '.' 'x' $g) $true "busca: nome exato sem extensão segura '$g'"
}
foreach ($g in @('Dockerfile', 'gradlew', 'gradlew.bat', 'README', 'README.md', '.gitignore', 'build.gradle.kts', '{Makefile,LICENSE}')) {
    Caso '-' (Sf '.' 'x' $g) $false "não regressão: nome exato seguro '$g'"
}
Caso '-' (Sf 'app\config' 'id' '*.exemplo.yaml') $false 'não regressão: modelos .exemplo.yaml'
Caso '-' (Sf '.' 'JIRA_' '.env.example') $false 'não regressão: buscar só no .env.example'
Caso '-' (Sf 'docs\README.md' 'x' '-') $false 'não regressão: busca num arquivo .md sem pattern'
Caso '-' (Lst 'list_files' '.') $false 'não regressão: list_files na raiz (só nomes)'
Caso '-' (Lst 'list_code_definition_names' 'app\backend\src') $false 'não regressão: definições do código'
Caso '-' (Ler 'reports\Filtros JQL para cards Jira.md') $false 'não regressão: relatório com Filtros no nome'

# ---------- 2) e 3) maiúsculas e aliases do Windows (escrita, leitura e comandos) ----------
foreach ($f in @('-', 'true')) {
    Caso $f (Esc 'write_to_file' '.ENV') $true "escrita: '.ENV'"
    Caso $f (Esc 'write_to_file' '.Env.') $true "escrita: '.Env.' (ponto final ignorado no Windows)"
    Caso $f (Esc 'replace_in_file' 'Papeis.YAML') $true "escrita: 'Papeis.YAML'"
    Caso $f (Esc 'write_to_file' 'app\config\EQUIPES.yaml ') $true 'escrita: EQUIPES.yaml com espaço final'
    Caso $f (Esc 'write_to_file' '.env::$DATA') $true "escrita: '.env::`$DATA' (fluxo alternativo)"
    Caso $f (Esc 'write_to_file' 'README.md:oculto') $true "escrita: ':' depois da unidade"
    Caso $f (Esc 'write_to_file' '.env.') $true "escrita: '.env.'"
    Caso $f (Esc 'write_to_file' 'app\ENV~1') $true "escrita: nome curto 'ENV~1'"
    Caso $f (Esc 'write_to_file' '\\?\C:\x\.env') $true "escrita: prefixo '\\?\'"
    Caso $f (Esc 'write_to_file' '\\.\C:\x\.env') $true "escrita: prefixo '\\.\'"
    Caso $f (Esc 'write_to_file' 'app\x\..\..\.env') $true "escrita: '..' resolvido"
    Caso $f (Ler '..\.env') $true "leitura: '..\.env'"
    Caso $f (Ler 'C:/x\app/.ENV.local') $true 'leitura: barras mistas e maiúsculas'
    Caso $f (Ler 'Filtros_Status_tool.txt.bak') $true 'leitura: arquivo de filtros'
    Caso $f (Ler 'C:\x\.env::$DATA') $true 'leitura: fluxo alternativo'
    Caso $f (Cmd 'cat .ENV') $true 'comando: cat .ENV'
    Caso $f (Cmd 'echo x > Papeis.YAML') $true 'comando: redirecionar para Papeis.YAML'
    Caso $f (Cmd 'type .env::$DATA') $true "comando: type .env::`$DATA"
    Caso $f (Cmd 'type ENV~1') $true 'comando: type ENV~1'
}
Caso '-' (Esc 'write_to_file' '\\?\C:\x\README.md') $false "não regressão: prefixo '\\?\' em arquivo comum"
Caso '-' (Esc 'write_to_file' 'C:\x\app\.env.example') $false 'não regressão: escrever .env.example com unidade'
Caso '-' (Ler '.env.example') $false 'não regressão: ler .env.example'
Caso '-' (Cmd 'git log HEAD~1') $false 'não regressão: HEAD~1 não é nome curto protegido'

# ---------- 4) falha fechada ----------
Caso '-' '{"tool_call":"x"}' $true 'falha fechada: tool_call em formato inesperado'
Caso '-' '{"preToolUse":{"toolName":"read_file","parameters":7}}' $false 'falha fechada: parâmetros numéricos (sem caminho, nada a proteger)'
Caso '-' '{"preToolUse":{"toolName":"search_files","parameters":"{nao json"}}' $true 'falha fechada: search_files com parâmetros ilegíveis'
# comando grande (espelho do teste do bash, onde o SIGPIPE virava liberação)
$grande = (@('x') * 150000) -join "`n"
Caso '-' (Cmd ("cat .env`n" + $grande)) $true 'falha fechada: comando grande lendo .env'
Caso 'true' (Cmd ('echo $JIRA_SAMSUNG_TOKEN' + "`n" + $grande)) $true 'falha fechada: token em comando grande'
Caso '-' (Esc 'write_to_file' 'C:\Users\USUARIO~1\x\README.md') $false 'não regressão: pasta com nome curto 8.3 (TEMP do Windows)'
Caso '-' 'isto não é json' $true 'falha fechada: entrada inválida'
Caso '-' '' $true 'falha fechada: entrada vazia'
Caso '-' '[1,2]' $true 'falha fechada: JSON que não é objeto'
Caso '-' '{"preToolUse":{"toolName":"read_file","parameters":{"path":"README.md"}}}' $false 'liberado: leitura comum'

# ---------- 28/10/2026: só ENDEREÇOS de Jira são bloqueados (a palavra solta não) ----------
# 1) palavra jira solta: liberada (flag desligada)
foreach ($c in @('cd /Volumes/Development/SIDIA/dashboardJira && ls', 'git clone https://github.com/dono/dashboardJira.git',
        'curl -s http://127.0.0.1:8080/api/health', 'cd dashboardJira && curl -s http://localhost:8080/api/health',
        'curl -s -o jira-saida.txt http://127.0.0.1:8080/api/health', 'cat docs/jira/checklist-primeira-conexao.md', 'echo jira atlassian',
        'Start-Process notepad jira.md', 'Invoke-WebRequest http://127.0.0.1:8080/api/health -OutFile jira.json',
        'cd C:\dev\dashboardJira; Invoke-WebRequest http://127.0.0.1:8080/api/health')) {
    Caso '-' (Cmd $c) $false "palavra solta: $c"
}
Caso '-' (Nav 'http://127.0.0.1:8080/dashboardJira') $false 'palavra solta: navegador no painel local com Jira no caminho'
Caso '-' (Web 'web_fetch' 'https://github.com/dono/dashboardJira') $false 'palavra solta: web_fetch do repositório'
Caso '-' (Ler 'docs/jira/checklist-primeira-conexao.md') $false 'palavra solta: ler arquivo em pasta jira'
Caso '-' (Mcp 'context7' 'get-library-docs' '{"library":"jira-client"}') $false 'palavra solta: argumento MCP sem URL'
# 2) URL/host de Jira: bloqueada (com e sem esquema, porta, userinfo, maiúsculas, escapes, ponto final, burlas)
foreach ($c in @('curl -s https://jira.exemplo.invalid/rest/api/2/myself', 'curl https://meu-jira.x.invalid/a', 'curl http://jira-lab.invalid:8080/',
        'curl jira.exemplo.invalid/x', 'curl jira-lab.invalid:8080', 'wget https://JIRA.Exemplo.Invalid/', 'curl https://jira.exemplo.invalid./x',
        'curl https://u:s@jira.x.invalid/', 'curl https://jira%2Eexemplo.invalid/x', 'curl https://x.invalid/jira/sidia', 'curl https://sidia.x.atlassian.net/x',
        'ssh jira-lab', 'ssh usuario@jira.x.invalid', 'nc -z jira-lab 8080', 'iwr jira.exemplo.invalid', 'Invoke-RestMethod -Uri https://jira.x.invalid/rest',
        'Invoke-WebRequest jira.x.invalid', 'curl.exe https://jira.x.invalid', '(New-Object Net.WebClient).DownloadString("jira.x.invalid")',
        'git clone git@jira.x.invalid:r.git', 'curl https://confluence.atlassian.com@jira.x.invalid/', 'curl https://atlassian.com.jira.x.invalid/',
        'curl https://jira.x.invalid/atlassian.com', 'curl https://docs.atlassian.com/x?redirect=https://jira.x.invalid/',
        'curl https://docs.atlassian.com/x?redirect=https%3A%2F%2Fjira.x.invalid', 'curl https://docs.atlassian.com\@jira.x.invalid/')) {
    Caso '-' (Cmd $c) $true "URL de Jira em comando: $c"
    Caso 'false' (Cmd $c) $true "URL de Jira em comando (flag false): $c"
}
# 2b) correções da revisão adversarial (28/10/2026): barra invertida no userinfo, host sem ponto, --connect-to, git/socat, Host:
foreach ($c in @("curl 'http://x\@jira.corp.invalid/rest'", "wget 'http://x\@jira.corp.invalid'", "Invoke-WebRequest 'http://x\@jira.corp.invalid'",
        'curl https://evil.invalid%5c@jira.corp.invalid/', 'curl http://jira.x.invalid\@evil.invalid/', 'curl http://docs.atlassian.com\jira.x.invalid/',
        'curl jira/rest/api/2/search', 'wget jira/x', 'iwr jira/rest', 'curl --proxy jira http://x.invalid', 'curl -x jira http://x.invalid',
        'curl -xjira.corp.invalid:3128 http://x.invalid', 'curl jira-lab/x',
        'curl --connect-to ::jira.corp.invalid:443 https://example.com', 'curl --connect-to example.com:443:jira.corp.invalid:443 https://example.com',
        'curl --connect-to=::jira.corp.invalid:443 https://example.com', 'curl --resolve example.com:443:jira.corp.invalid https://example.com',
        'git clone jira.corp.invalid:x.git', 'git clone jira:x.git', 'git remote add o jira.corp.invalid:x', 'git ls-remote jira.corp.invalid:x',
        'rsync -av x jira.corp.invalid:/tmp', 'scp x jira:/tmp', 'socat - TCP:jira.corp.invalid:80', 'socat TCP4:jira:80 -',
        "curl -H 'Host:jira.corp.invalid' http://10.0.0.1", 'curl -H Host:jira.corp.invalid http://10.0.0.1')) {
    Caso '-' (Cmd $c) $true "burla corrigida: $c"
}
foreach ($c in @("curl 'http://x\@docs.atlassian.com/x'", "curl http://example.com\@x.invalid/", 'git push origin HEAD:feat/hooks-url-jira',
        'git commit -m "guia do jira"', 'git push origin jira-fix', 'git clone https://github.com/dono/dashboardJira.git',
        'curl --connect-to example.com:443:example.org:443 https://example.com', 'curl -H "Host: example.com" http://10.0.0.1',
        'curl -H Host:localhost http://10.0.0.1', 'socat - TCP:example.com:80', 'curl --proxy proxy.exemplo.com:3128 http://x.invalid')) {
    Caso '-' (Cmd $c) $false "não regressão da burla: $c"
}
foreach ($u in @('https://jira.x.invalid/a', 'jira.x.invalid/a', 'JIRA-LAB:8080', 'https://u@jira.x.invalid', 'https://x.invalid/jira/sidia', 'https://sidia.x.atlassian.net/x',
        'https://SIDIA.x.ATLASSIAN.NET', 'https://jira%2Ex.invalid/', 'https://confluence.atlassian.com@jira.x.invalid/', 'https://atlassian.com.jira.x.invalid/',
        'https://jira.x.invalid/atlassian.com', 'https://docs.atlassian.com/x?redirect=https://jira.x.invalid/',
        'https://docs.atlassian.com/x?redirect=https%3A%2F%2Fjira.x.invalid', 'https://docs.atlassian.com\@jira.x.invalid/', 'https://jira.x.invalid./')) {
    Caso '-' (Web 'web_fetch' $u) $true "web_fetch bloqueado: $u"
    Caso '-' (Nav $u) $true "navegador bloqueado: $u"
}
Caso '-' (Mcp 'fetch' 'fetch' '{"url":"jira.exemplo.invalid/x"}') $true 'MCP de fetch: url de Jira sem esquema'
Caso '-' (Mcp 'fetch' 'fetch' '{"opcoes":{"url":"https://jira.exemplo.invalid/x"}}') $true 'MCP de fetch: url aninhada'
Caso '-' (Mcp 'fetch' 'fetch' '{"texto":"veja https://jira.exemplo.invalid/x"}') $true 'MCP de fetch: URL com esquema em texto livre'
Caso '-' (Mcp 'fetch' 'fetch' '{"texto":"veja jira.md"}') $false 'MCP de fetch: texto livre com jira.md (sem esquema) liberado'
Caso '-' '{"preToolUse":{"toolName":"access_mcp_resource","parameters":{"server_name":"fetch","uri":"https://jira.exemplo.invalid/x"}}}' $true 'MCP: recurso com URI de Jira'
Caso '-' '{"preToolUse":{"toolName":"access_mcp_resource","parameters":{"server_name":"docs","uri":"https://docs.atlassian.com/jira"}}}' $false 'MCP: recurso com URI de documentação pública'
# 3) documentação pública da Atlassian: liberada (mesmo com jira no caminho); o host real decide
foreach ($u in @('https://confluence.atlassian.com/jirasoftwareserver/jira-software-server-documentation-939937427.html',
        'https://support.atlassian.com/jira-software-cloud/docs/x', 'https://developer.atlassian.com/server/jira/platform/rest/',
        'https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/', 'https://community.atlassian.com/t5/Jira/x',
        'https://www.atlassian.com/software/jira', 'https://DOCS.Atlassian.COM/jira/', 'https://www.atlassian.com./software/jira',
        'https://jira.x.invalid@docs.atlassian.com/', 'https://x.atlassian.net/rest')) {
    Caso '-' (Web 'web_fetch' $u) $false "web_fetch liberado: $u"
    Caso '-' (Nav $u) $false "navegador liberado: $u"
    Caso '-' (Cmd "curl -s $u") $false "curl liberado: $u"
}
Caso '-' (Cmd 'Invoke-WebRequest https://confluence.atlassian.com/jirasoftwareserver') $false 'documentação: Invoke-WebRequest'
Caso '-' (Cmd 'iwr confluence.atlassian.com/jirasoftwareserver') $false 'documentação: iwr sem esquema'
# 4) com a flag ligada no arquivo, tudo isso passa
foreach ($u in @('https://jira.x.invalid/a', 'jira.x.invalid/a', 'https://sidia.x.atlassian.net/x', 'https://x.invalid/jira/sidia')) {
    Caso 'true' (Web 'web_fetch' $u) $false "flag ligada: web_fetch $u"
    Caso 'true' (Nav $u) $false "flag ligada: navegador $u"
    Caso 'true' (Cmd "curl -s $u") $false "flag ligada: curl $u"
}

# ---------- Plano 14, Tarefa 0: a flag vem do app\.env da raiz do repositório ----------
$CurlJ = "curl -s $URL"
# flagVemDoArquivoDeVariaveis
CasoArq "JIRA_LIVE_ENABLED=$T`n" (Cmd $CurlJ) $false 'arquivo: flag ligada libera'
CasoArq "JIRA_LIVE_ENABLED=false`n" (Cmd $CurlJ) $true 'arquivo: flag false bloqueia'
CasoArq "OUTRA=1`n" (Cmd $CurlJ) $true 'arquivo: sem a linha da flag bloqueia'
CasoArq '' (Cmd $CurlJ) $true 'arquivo: vazio bloqueia'
foreach ($v in @("`"$T`"", "'$T'", "$T # comentário", 'TRUE', " $T ", "`" $T `"", "'TrUe'")) {
    CasoArq "JIRA_LIVE_ENABLED=$v`n" (Cmd $CurlJ) $false "arquivo: valor [$v] liga"
}
CasoArq "export JIRA_LIVE_ENABLED=$T`n" (Cmd $CurlJ) $false 'arquivo: prefixo export liga'
CasoArq "  JIRA_LIVE_ENABLED = $T  `n" (Cmd $CurlJ) $false 'arquivo: espaços ao redor do = e nas pontas ligam'
CasoArq "JIRA_LIVE_ENABLED=$T`r`n" (Cmd $CurlJ) $false 'arquivo: CRLF liga'
CasoArq ([string][char]0xFEFF + "JIRA_LIVE_ENABLED=$T`n") (Cmd $CurlJ) $false 'arquivo: BOM liga'
CasoArq "A=1`n`nB=2`nJIRA_LIVE_ENABLED=$T`nC=3`n" (Cmd $CurlJ) $false 'arquivo: linha da flag entre outras liga'
CasoArq "JIRA_LIVE_ENABLED=$T" (Cmd $CurlJ) $false 'arquivo: sem quebra de linha final liga'
# StartsWith(BOM) por cultura casa com qualquer texto e comeria o 1o caractere de toda linha: sem BOM, a 1a linha tem de valer
CasoArq "JIRA_LIVE_ENABLED=$T`nOUTRA=1`n" (Cmd $CurlJ) $false 'arquivo: sem BOM, o 1o caractere da linha da flag não é comido'
foreach ($v in @('sim', 'yes', '1', '0', 'tr ue', "$T;", "$T#x", "${T}x", "x$T", '', "`"$T", "$T`"", "`"$T`" # c", "$T,")) {
    CasoArq "JIRA_LIVE_ENABLED=$v`n" (Cmd $CurlJ) $true "arquivo: valor inválido [$v] bloqueia"
}
CasoArq "# JIRA_LIVE_ENABLED=$T`n" (Cmd $CurlJ) $true 'arquivo: linha comentada não liga'
CasoArq "  # JIRA_LIVE_ENABLED=$T`n" (Cmd $CurlJ) $true 'arquivo: comentário indentado não liga'
CasoArq "JIRA_LIVE_ENABLED_X=$T`n" (Cmd $CurlJ) $true 'arquivo: nome parecido (sufixo) não liga'
CasoArq "XJIRA_LIVE_ENABLED=$T`n" (Cmd $CurlJ) $true 'arquivo: nome parecido (prefixo) não liga'
CasoArq "jira_live_enabled=$T`n" (Cmd $CurlJ) $true 'arquivo: nome em minúsculas não liga (igual ao painel)'
CasoArq "JIRA_LIVE_ENABLED:$T`n" (Cmd $CurlJ) $true 'arquivo: dois-pontos no lugar de = não liga'
# a ÚLTIMA ocorrência vale
CasoArq "JIRA_LIVE_ENABLED=$T`nJIRA_LIVE_ENABLED=false`n" (Cmd $CurlJ) $true 'arquivo: ligada e depois desligada bloqueia'
CasoArq "JIRA_LIVE_ENABLED=false`nJIRA_LIVE_ENABLED=$T`n" (Cmd $CurlJ) $false 'arquivo: desligada e depois ligada libera'
CasoArq "JIRA_LIVE_ENABLED=$T`nJIRA_LIVE_ENABLED=`n" (Cmd $CurlJ) $true 'arquivo: ligada e depois vazia bloqueia'
CasoArq "JIRA_LIVE_ENABLED=$T`n# JIRA_LIVE_ENABLED=false`n" (Cmd $CurlJ) $false 'arquivo: comentário depois não desliga'
# ambienteDoProcessoNaoLiga / ambienteDoProcessoNaoDesliga
CasoAmb $T "JIRA_LIVE_ENABLED=false`n" (Cmd $CurlJ) $true 'ambiente ligado e arquivo false: bloqueia comando'
CasoAmb $T $null (Cmd $CurlJ) $true 'ambiente ligado e sem arquivo: bloqueia comando'
CasoAmb $T $null (Web 'web_fetch' 'https://jira.x.invalid/a') $true 'ambiente ligado e sem arquivo: bloqueia web_fetch'
CasoAmb $T $null (Nav 'https://jira.x.invalid/a') $true 'ambiente ligado e sem arquivo: bloqueia navegador'
CasoAmb $T $null (Mcp 'claude_ai_Atlassian_MCP' 'searchJiraIssuesUsingJql') $true 'ambiente ligado e sem arquivo: bloqueia MCP do Atlassian'
CasoAmb $T $null (Cmd "setx JIRA_LIVE_ENABLED $T") $true 'ambiente ligado e sem arquivo: ligar a flag por setx continua barrado'
CasoAmb $T "JIRA_LIVE_ENABLED=`n" (Cmd $CurlJ) $true 'ambiente ligado e arquivo com valor vazio: bloqueia'
CasoAmb 'false' "JIRA_LIVE_ENABLED=$T`n" (Cmd $CurlJ) $false 'ambiente false e arquivo ligado: libera comando'
CasoAmb 'false' "JIRA_LIVE_ENABLED=$T`n" (Web 'web_fetch' 'https://jira.x.invalid/a') $false 'ambiente false e arquivo ligado: libera web_fetch'
CasoAmb '' "JIRA_LIVE_ENABLED=$T`n" (Nav 'https://jira.x.invalid/a') $false 'ambiente vazio e arquivo ligado: libera navegador'
CasoAmb 'false' "JIRA_LIVE_ENABLED=$T`n" (Mcp 'claude_ai_Atlassian_MCP' 'searchJiraIssuesUsingJql') $false 'ambiente false e arquivo ligado: libera MCP'
CasoAmb 'false' "JIRA_LIVE_ENABLED=$T`n" (Cmd 'echo $JIRA_SAMSUNG_TOKEN') $true 'ambiente false e arquivo ligado: exibir token continua barrado'
# arquivoIlegivelFalhaFechado: não é arquivo comum, lixo binário
CasoArq 'DIR' (Cmd $CurlJ) $true 'arquivo ilegível (é uma pasta): bloqueia'
CasoAmb $T 'DIR' (Cmd $CurlJ) $true 'arquivo ilegível (é uma pasta) e ambiente ligado: bloqueia'
CasoArq ([string][char]0xFFFD + "JIRA_LIVE_ENABLED=$T`n") (Cmd $CurlJ) $true 'arquivo com lixo antes do nome da flag: bloqueia'
# o hook nunca imprime o conteúdo do arquivo (a saída é só o JSON de decisão)
$rSeg = Rodar-Hook-Bruto "JIRA_SAMSUNG_TOKEN=segredo-do-teste-123`nJIRA_LIVE_ENABLED=false`nOUTRA=valor-outra-456`n" $null (Cmd $CurlJ)
$script:total++
if (($rSeg.saida + $rSeg.erro) -match 'segredo-do-teste-123|valor-outra-456') { Write-Output 'FALHA o hook imprimiu o conteúdo do arquivo'; $script:falhas++ }
else { Write-Output 'ok    o hook não imprime o conteúdo do arquivo' }
# leituraDaIaAoArquivoContinuaBarrada: o hook lê o arquivo para si, mas a IA continua barrada (qualquer valor)
foreach ($f in @('-', 'false', 'true')) {
    Caso $f (Ler 'app\.env') $true "IA [flag=$f]: read_file em app\.env"
    Caso $f (Ler '/x/repo/app/.ENV') $true "IA [flag=$f]: read_file em app/.ENV"
    Caso $f (Cmd 'cat app/.env') $true "IA [flag=$f]: cat app/.env"
    Caso $f (Cmd 'Get-Content app\.env') $true "IA [flag=$f]: Get-Content app\.env"
    Caso $f (Cmd 'Select-String JIRA_LIVE_ENABLED app\.env') $true "IA [flag=$f]: Select-String na flag de app\.env"
    Caso $f (Esc 'write_to_file' 'app\.env') $true "IA [flag=$f]: escrever app\.env"
    Caso $f (Cmd 'ln -sf /tmp/x app/.env') $true "IA [flag=$f]: symlink para app/.env"
    Caso $f (Cmd 'New-Item -ItemType SymbolicLink -Path app\.env -Target x') $true "IA [flag=$f]: New-Item SymbolicLink em app\.env"
    Caso $f (Cmd "echo JIRA_LIVE_ENABLED=$T >> app/.env") $true "IA [flag=$f]: ligar a flag em app/.env por echo"
    Caso $f (EscC 'write_to_file' 'docs/x.md' 'content' "JIRA_LIVE_ENABLED=$T") $true "IA [flag=$f]: gravar texto que liga a flag"
    Caso $f (Cmd 'git add app/.env') $true "IA [flag=$f]: git add app/.env"
}

# limpeza da raiz de mentira
try { Remove-Item -LiteralPath $tmp -Recurse -Force } catch { }
Write-Output "casos: $($script:total)"
Write-Output "falhas: $($script:falhas)"
if ($script:falhas -eq 0) { exit 0 } else { exit 1 }
