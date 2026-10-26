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
$hook = Join-Path $dir 'PreToolUse.ps1'
if ([string]::IsNullOrEmpty($Executavel)) { $Executavel = (Get-Process -Id $PID).Path }
$utf8 = New-Object System.Text.UTF8Encoding($false)
$script:falhas = 0
$script:total = 0

Write-Output "interpretador: $Executavel ($($PSVersionTable.PSVersion) neste processo)"

# o arquivo precisa ter BOM para o PowerShell 5.1 ler os acentos corretamente
foreach ($f in @($hook, $MyInvocation.MyCommand.Path)) {
    $b = [System.IO.File]::ReadAllBytes($f)
    if (-not ($b.Length -ge 3 -and $b[0] -eq 0xEF -and $b[1] -eq 0xBB -and $b[2] -eq 0xBF)) {
        Write-Output "FALHA arquivo sem BOM UTF-8: $f"
        $script:falhas++
    }
}

function Rodar-Hook([string]$flag, [string]$json) {
    # flag '-' = variável ausente no processo filho
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
    if ($flag -ne '-') { $psi.EnvironmentVariables['JIRA_LIVE_ENABLED'] = $flag }
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
    $script:total++
    $r = Rodar-Hook $flag $json
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
    if ($ok) { Write-Output "ok    [flag=$flag] $descricao" }
    else {
        Write-Output "FALHA [flag=$flag] $descricao (exit $($r.codigo), cancel=$obtido, esperado $esperado; saída: $($r.saida.Trim()); erro: $($r.erro.Trim()))"
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
    Caso $f (Web 'web_fetch' 'https://docs.atlassian.com/jira/rest') $true 'trava: web_fetch para documentação pública da Atlassian (efeito aceito pelo usuário)'
    Caso $f (Nav 'https://empresa.invalid/JIRA/browse/X-1') $true 'trava: navegador com JIRA no caminho (sem diferenciar maiúsculas)'
}
Caso '-' (CmdTxt "curl -s $URL") $true 'trava: parâmetros em texto JSON'
Caso '-' (CmdCli "wget $URL") $true 'trava: formato da CLI (run_commands)'
Caso '-' (Cmd 'echo $JIRA_TOKEN') $true 'trava: variável de conexão do Jira'
Caso '-' (Mcp 'jira-local' 'search') $true 'trava: servidor MCP com jira no nome'
Caso '-' (Mcp 'fetch' 'fetch' '{"url":"https://x.atlassian.net/wiki"}') $true 'trava: MCP de fetch com URL da Atlassian'
Caso 'false' (Cmd 'JIRA_LIVE_ENABLED=false ./gradlew test') $false 'liberado: flag false no comando não liga nada'
# extras do Windows/PowerShell
Caso '-' (Cmd "Invoke-WebRequest -Uri $URL") $true 'trava: Invoke-WebRequest'
Caso '-' (Cmd 'iwr jira.exemplo.invalid') $true 'trava: iwr sem esquema na URL'
Caso '-' (Cmd 'irm https://x.atlassian.net/rest/api/3/myself') $true 'trava: Invoke-RestMethod'
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
Caso '-' (Nav 'file:///C:/dev/dashboardJira/docs/design/prototipo/a.html') $true 'falso positivo aceito: pasta com Jira no nome (clone em pasta sem jira)'

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
Caso '-' '{"preToolUse":{"toolName":"search_files","parameters":{"path":".","regex":"JIRA_"}}}' $false 'liberado: search_files'
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
    @('python3 -c "import urllib.request as u;u.urlopen(\"https://x.atlassian.net/a\")"', $true),
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
    @('curl -s http://127.0.0.1:8080/api/health # em C:\dev\dashboardJira', $true),
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

# ---------- falha fechada ----------
Caso '-' 'isto não é json' $true 'falha fechada: entrada inválida'
Caso '-' '' $true 'falha fechada: entrada vazia'
Caso '-' '[1,2]' $true 'falha fechada: JSON que não é objeto'
Caso '-' '{"preToolUse":{"toolName":"read_file","parameters":{"path":"README.md"}}}' $false 'liberado: leitura comum'

Write-Output "casos: $($script:total)"
Write-Output "falhas: $($script:falhas)"
if ($script:falhas -eq 0) { exit 0 } else { exit 1 }
