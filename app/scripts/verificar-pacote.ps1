# Verifica o pacote de producao montado por montar-pacote.ps1. Sai com codigo diferente de 0 e uma mensagem curta no
# primeiro problema.
# Uso (a partir de app\):  powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\verificar-pacote.ps1 [pasta do pacote]
#
#  1. Conteudo: nenhum YAML real, arquivo de variaveis (alem do modelo), banco, trava, chave/certificado nem pasta legacy.
#  2. Estrutura: bin\painel, bin\painel.bat, o jar do painel em lib\ e frontend\dist\index.html.
#  3. Fumaca (usa a porta 8080 e pastas temporarias; aborta com aviso se a porta ja estiver em uso):
#     a) modo producao SEM OIDC: o processo termina com codigo diferente de 0 e a saida cita OIDC_ISSUER, sem nenhum
#        valor de segredo;
#     b) modo desenvolvimento: GET /api/health responde {"ok":true}; a arvore do processo e encerrada no fim.
# O arquivo de variaveis dos testes NAO tem a chave do acesso ao Jira (linha ausente = desligado) e o script nunca a liga.
# Este arquivo e SO ASCII de proposito: o Windows PowerShell 5.1 le arquivo sem BOM como ANSI e estragaria os acentos.
# Com um ZIP (painel-AAAA.MM.N.zip, com o painel-AAAA.MM.N.zip.sha256 ao lado) confere ANTES de extrair: nome, tamanho
# (no maximo 200 MB), SHA-256, pasta raiz e nomes proibidos; depois extrai numa pasta temporaria e roda as conferencias
# acima sobre o conteudo extraido.
$ErrorActionPreference = 'Stop'
$aqui = Split-Path -Parent $MyInvocation.MyCommand.Path
$app = Split-Path -Parent $aqui
$alvo = if ($args.Count -gt 0) { $args[0] } else { Join-Path $app 'pacote\painel' }
$zipTmp = $null

function Falhar($mensagem) {
    [Console]::Error.WriteLine("FALHA: $mensagem")
    if ($script:zipTmp) { Remove-Item -Recurse -Force $script:zipTmp -ErrorAction SilentlyContinue }
    exit 1
}

# Nome proibido num componente de caminho; $entrada e a entrada inteira, para a mensagem.
$configsReais = @('papeis.yaml', 'equipes.yaml', 'ausencias.yaml', 'squads.yaml', 'status_map.yaml', 'jira_campos.yaml', 'calendario.yaml', 'vinculos.yaml', 'labels_atraso.yaml', 'auditoria.yaml')
function ConfereNome($nome, $entrada) {
    $n = $nome.ToLower()
    if ($n -eq '.env.example') { return }
    if ($n -eq '.env' -or $n -like '*.env' -or $n -like '.env.*') { Falhar "arquivo de variaveis (.env, painel.env) dentro do pacote: $entrada" }
    if ($n -like '*.db' -or $n -like '*.db-wal' -or $n -like '*.db-shm' -or $n -like '*.lock' -or $n -like '*.rotacao') { Falhar "arquivo de banco ou de trava (.db, .db-wal, .db-shm, *.lock) dentro do pacote: $entrada" }
    if ($n -like '*.pem' -or $n -like '*.key' -or $n -like '*.p12' -or $n -like '*.pfx' -or $n -like '*.jks' -or $n -like '*.keystore') { Falhar "chave ou certificado dentro do pacote: $entrada" }
    if ($configsReais -contains $n) { Falhar "configuracao real (nao e modelo) dentro do pacote: $entrada" }
    if ($n -eq 'filtros_status_tool.txt') { Falhar "arquivo de filtros do Jira (Filtros_Status_tool.txt) dentro do pacote: $entrada" }
    if ($n -eq 'legacy' -or $n -eq 'node_modules' -or $n -eq '.git') { Falhar "pasta proibida (legacy, node_modules, .git) dentro do pacote: $entrada" }
}

if ($alvo -like '*.zip') {
    Write-Host '== Zip =='
    if (-not (Test-Path $alvo)) { Falhar "zip nao encontrado: $alvo" }
    $zip = (Resolve-Path $alvo).Path
    $nomeDoZip = Split-Path -Leaf $zip
    if ($nomeDoZip -notmatch '^painel-[0-9]{4}\.(0[1-9]|1[0-2])\.[1-9][0-9]*\.zip$') { Falhar 'nome do pacote fora do padrao painel-AAAA.MM.N.zip' }
    if ((Get-Item $zip).Length -gt 209715200) { Falhar 'o zip passa de 200 MB (limite do pacote)' }
    $arquivoHash = "$zip.sha256"
    if (-not (Test-Path $arquivoHash)) { Falhar "falta o arquivo $nomeDoZip.sha256 ao lado do zip" }
    $linha = ([string](Get-Content -Path $arquivoHash -TotalCount 1)).Trim()
    $partes = $linha -split '\s+', 2
    $esperado = $partes[0].ToLower()
    $nomeNoArquivo = if ($partes.Count -gt 1) { $partes[1].TrimStart('*') } else { '' }
    if ($nomeNoArquivo -ne $nomeDoZip) { Falhar 'o arquivo .sha256 nao e deste zip' }
    $calculado = (Get-FileHash -Algorithm SHA256 -Path $zip).Hash.ToLower()
    if ($esperado -ne $calculado) { Falhar 'o SHA-256 do zip nao confere com o arquivo .sha256' }
    Write-Host 'ok: SHA-256 confere'
    $raiz = $nomeDoZip.Substring(0, $nomeDoZip.Length - 4)
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $arquivoZip = [System.IO.Compression.ZipFile]::OpenRead($zip)
    try { $entradas = @($arquivoZip.Entries | ForEach-Object { $_.FullName }) } finally { $arquivoZip.Dispose() }
    foreach ($entrada in $entradas) {
        if (-not $entrada.StartsWith("$raiz/")) { Falhar "entrada fora da pasta raiz $raiz/: $entrada" }
        foreach ($componente in ($entrada.TrimEnd('/') -split '/')) { ConfereNome $componente $entrada }
    }
    Write-Host 'ok: nenhuma entrada proibida'
    $zipTmp = Join-Path ([System.IO.Path]::GetTempPath()) ('painel-zip-' + [Guid]::NewGuid().ToString('N'))
    [System.IO.Compression.ZipFile]::ExtractToDirectory($zip, (Join-Path $zipTmp 'extraido'))
    $alvo = Join-Path $zipTmp "extraido\$raiz"
}

if (-not (Test-Path $alvo)) { Falhar 'pacote nao encontrado (rode: scripts\montar-pacote.ps1)' }
$pacote = (Resolve-Path $alvo).Path

function Relativo($caminho) { return $caminho.Substring($pacote.Length).TrimStart('\') }

# ---------------------------------------------------------------- 1. conteudo
Write-Host '== Conteudo =='
$todos = @(Get-ChildItem -Path $pacote -Recurse -Force)
$achado = $todos | Where-Object { -not $_.PSIsContainer -and ($_.Name -eq '.env' -or $_.Name -like '*.env' -or ($_.Name -like '.env.*' -and $_.Name -ne '.env.example')) } | Select-Object -First 1
if ($achado) { Falhar "arquivo de variaveis dentro do pacote: $(Relativo $achado.FullName)" }
$achado = $todos | Where-Object { -not $_.PSIsContainer -and ($_.Name -like '*.db' -or $_.Name -like '*.db-wal' -or $_.Name -like '*.db-shm' -or $_.Name -like '*.lock' -or $_.Name -like '*.rotacao') } | Select-Object -First 1
if ($achado) { Falhar "arquivo de banco ou de trava dentro do pacote: $(Relativo $achado.FullName)" }
$achado = $todos | Where-Object { -not $_.PSIsContainer -and ($_.Extension -in @('.pem', '.key', '.p12', '.pfx', '.jks', '.keystore')) } | Select-Object -First 1
if ($achado) { Falhar "chave ou certificado dentro do pacote: $(Relativo $achado.FullName)" }
$achado = $todos | Where-Object { $_.Name -eq 'legacy' } | Select-Object -First 1
if ($achado) { Falhar 'pasta legacy dentro do pacote' }
$achado = $todos | Where-Object { $_.Name -like 'Dockerfile*' -or $_.Name -like 'docker-compose*' -or $_.Name -like 'compose*.yml' -or $_.Name -like 'compose*.yaml' -or $_.Name -eq '.dockerignore' } | Select-Object -First 1
if ($achado) { Falhar "artefato de conteiner dentro do pacote: $(Relativo $achado.FullName)" }
# config\: so modelos (*.exemplo.yaml, *.demo.yaml) e as fichas de cards\ (*.card.yaml). Qualquer outro arquivo e configuracao real.
$config = Join-Path $pacote 'config'
if (Test-Path $config) {
    $cards = Join-Path $config 'cards'
    $achado = Get-ChildItem -Path $config -Recurse -Force -File | Where-Object {
        -not ($_.Name -like '*.exemplo.yaml' -or $_.Name -like '*.demo.yaml' -or ($_.DirectoryName -eq $cards -and $_.Name -like '*.card.yaml'))
    } | Select-Object -First 1
    if ($achado) { Falhar "configuracao real (nao e modelo) dentro do pacote: $(Relativo $achado.FullName)" }
}
# Nomes proibidos em QUALQUER lugar do pacote (a mesma lista usada para as entradas do zip).
foreach ($item in $todos) { ConfereNome $item.Name (Relativo $item.FullName) }

# ---------------------------------------------------------------- 2. estrutura
Write-Host '== Estrutura =='
if (-not (Test-Path (Join-Path $pacote 'bin\painel'))) { Falhar 'falta bin\painel' }
if (-not (Test-Path (Join-Path $pacote 'bin\painel.bat'))) { Falhar 'falta bin\painel.bat' }
if (-not (Get-ChildItem -Path (Join-Path $pacote 'lib') -Filter 'painel-*.jar' -ErrorAction SilentlyContinue)) { Falhar 'falta o jar do painel em lib\' }
if (-not (Test-Path (Join-Path $pacote 'frontend\dist\index.html'))) { Falhar 'falta frontend\dist\index.html' }
if (-not (Test-Path (Join-Path $pacote 'config\papeis.exemplo.yaml'))) { Falhar 'falta config\papeis.exemplo.yaml' }
if (-not (Test-Path (Join-Path $pacote 'VERSAO.txt'))) { Falhar 'falta VERSAO.txt' }
if (-not (Test-Path (Join-Path $pacote 'docs\implantacao.md'))) { Falhar 'falta docs\implantacao.md' }
if (-not (Test-Path (Join-Path $pacote 'implantacao\windows\atualizar.ps1'))) { Falhar 'falta implantacao\windows\atualizar.ps1' }
if (-not (Test-Path (Join-Path $pacote 'implantacao\linux\atualizar.sh'))) { Falhar 'falta implantacao\linux\atualizar.sh' }

# ---------------------------------------------------------------- 3. fumaca
Write-Host '== Fumaca =='
function Consultar {
    try {
        $r = Invoke-WebRequest -UseBasicParsing -TimeoutSec 2 -Uri 'http://127.0.0.1:8080/api/health'
        return [string]$r.Content
    } catch {
        return ''
    }
}
if ((Consultar) -ne '') {
    [Console]::Error.WriteLine('AVISO: a porta 8080 ja esta em uso; encerre o painel que esta rodando e tente de novo.')
    exit 3
}

$tmp = Join-Path ([System.IO.Path]::GetTempPath()) ('painel-pacote-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force (Join-Path $tmp 'config'), (Join-Path $tmp 'dados') | Out-Null
$processo = $null

function Encerrar {
    if ($script:processo -and -not $script:processo.HasExited) {
        & taskkill /T /F /PID $script:processo.Id 2>&1 | Out-Null
    }
    $script:processo = $null
}

# Valores FICTICIOS so para provar que a recusa nao os mostra: 48 bytes (base64) e um texto qualquer.
$segredoDeTeste = [Convert]::ToBase64String([byte[]](,0x71 * 48))
$clienteDeTeste = 'cliente-ficticio-de-teste-0001'

# Sobe o painel com o ambiente LIMPO (nenhuma variavel do Jira nem do painel herdada do terminal).
function Subir($arquivoEnv, $pastaConfig, $saida, $erro) {
    $guardadas = @{}
    foreach ($v in @(Get-ChildItem Env: | Where-Object { $_.Name -match '^(JIRA_|PAINEL_|DASHBOARD_|OIDC_|SESSION_|DATABASE_|SYNC_|BACKUP_|RETENTION_|ACCESS_LOG_)' })) {
        $guardadas[$v.Name] = $v.Value
        [Environment]::SetEnvironmentVariable($v.Name, $null, 'Process')
    }
    try {
        $env:PAINEL_ENV_FILE = $arquivoEnv
        $env:DATABASE_PATH = Join-Path $tmp 'dados\painel.db'
        $env:PAINEL_CONFIG_DIR = $pastaConfig
        $env:PAINEL_STATIC_DIR = Join-Path $pacote 'frontend\dist'
        $p = Start-Process -FilePath (Join-Path $pacote 'bin\painel.bat') -WorkingDirectory $pacote -PassThru -NoNewWindow `
            -RedirectStandardOutput $saida -RedirectStandardError $erro
        $null = $p.Handle  # sem isto o ExitCode pode vir vazio
        return $p
    } finally {
        foreach ($nome in @('PAINEL_ENV_FILE', 'DATABASE_PATH', 'PAINEL_CONFIG_DIR', 'PAINEL_STATIC_DIR')) {
            [Environment]::SetEnvironmentVariable($nome, $null, 'Process')
        }
        foreach ($k in $guardadas.Keys) { [Environment]::SetEnvironmentVariable($k, $guardadas[$k], 'Process') }
    }
}

try {
    # a) producao sem OIDC_ISSUER: tem de terminar com erro citando so o NOME da variavel
    Set-Content -Path (Join-Path $tmp 'producao.env') -Encoding ASCII -Value @(
        'DASHBOARD_DEV_MODE=false', 'PAINEL_METRICAS_PORTA=0', "SESSION_SECRET=$segredoDeTeste",
        'OIDC_CLIENT_ID=painel-de-teste', "OIDC_CLIENT_SECRET=$clienteDeTeste",
        'OIDC_REDIRECT_URI=https://painel.exemplo.invalid/auth/callback'
    )
    $saidaA = Join-Path $tmp 'saida-producao.txt'
    $erroA = Join-Path $tmp 'erro-producao.txt'
    $processo = Subir (Join-Path $tmp 'producao.env') (Join-Path $tmp 'config') $saidaA $erroA
    if (-not $processo.WaitForExit(90000)) { Falhar 'producao sem OIDC_ISSUER continuou rodando depois de 90 s (deveria recusar a subida)' }
    $codigo = $processo.ExitCode
    $processo = $null
    if ($codigo -eq 0) { Falhar 'producao sem OIDC_ISSUER terminou com codigo 0 (deveria recusar a subida)' }
    $texto = ((Get-Content $saidaA, $erroA -Raw -ErrorAction SilentlyContinue) -join "`n")
    if ($texto -notmatch 'OIDC_ISSUER') { Falhar 'a recusa nao cita OIDC_ISSUER' }
    if ($texto.Contains($segredoDeTeste) -or $texto.Contains($clienteDeTeste)) { Falhar 'a saida da recusa mostrou um valor de segredo' }
    Write-Host "ok: producao sem OIDC_ISSUER recusa subir (codigo $codigo)"

    # b) desenvolvimento: /api/health responde (modelos de configuracao do proprio pacote)
    Set-Content -Path (Join-Path $tmp 'dev.env') -Encoding ASCII -Value @('DASHBOARD_DEV_MODE=true', 'PAINEL_METRICAS_PORTA=0')
    $saidaB = Join-Path $tmp 'saida-dev.txt'
    $erroB = Join-Path $tmp 'erro-dev.txt'
    $processo = Subir (Join-Path $tmp 'dev.env') (Join-Path $pacote 'config') $saidaB $erroB
    $resposta = ''
    for ($i = 0; $i -lt 90; $i++) {
        $resposta = Consultar
        if ($resposta -ne '') { break }
        if ($processo.HasExited) { Falhar 'o painel em modo desenvolvimento terminou antes de responder' }
        Start-Sleep -Seconds 1
    }
    if ($resposta -ne '{"ok":true}') { Falhar "GET /api/health nao respondeu {`"ok`":true} (veio: $resposta)" }
    Write-Host 'ok: /api/health responde em modo desenvolvimento'
} finally {
    Encerrar
    Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
    if ($zipTmp) { Remove-Item -Recurse -Force $zipTmp -ErrorAction SilentlyContinue }
}

Write-Host "PACOTE VERIFICADO: $pacote"
