# Monta o pacote de producao do painel em app\pacote\painel\ (sem conteiner, sem servidor de banco).
# Uso (a partir de app\):  powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\montar-pacote.ps1
#
# O pacote leva: bin\ (scripts de subida), lib\ (jars), frontend\dist\ (as telas), config\ (SO os modelos *.exemplo.yaml,
# *.demo.yaml e as fichas de cards\), o modelo de variaveis, VERSAO.txt e LEIA-ME.txt. Configuracao real, arquivo de
# variaveis real e arquivo de banco NUNCA entram: ficam FORA da pasta do pacote (ver docs\implantacao.md).
#
# Requisitos: JDK 21, Node 22 e pnpm. Em maquina sem acesso direto aos registros (proxy ou espelho da empresa), defina
# PAINEL_PNPM_ARGS e PAINEL_GRADLE_ARGS (por exemplo --offline quando as dependencias ja estao no cache).
# Este arquivo e SO ASCII de proposito: o Windows PowerShell 5.1 le arquivo sem BOM como ANSI e estragaria os acentos.
$ErrorActionPreference = 'Stop'
$aqui = Split-Path -Parent $MyInvocation.MyCommand.Path
$app = Split-Path -Parent $aqui
$destino = Join-Path $app 'pacote\painel'

function Separar($texto) {
    if ($texto) { return @($texto -split '\s+' | Where-Object { $_ }) }
    return @()
}

Write-Host '== Frontend: instalando dependencias e gerando o build =='
Push-Location (Join-Path $app 'frontend')
try {
    $extra = Separar $env:PAINEL_PNPM_ARGS
    pnpm install --frozen-lockfile @extra
    if ($LASTEXITCODE -ne 0) { throw 'pnpm install falhou' }
    pnpm build
    if ($LASTEXITCODE -ne 0) { throw 'pnpm build falhou' }
} finally {
    Pop-Location
}

Write-Host '== Backend: gerando o pacote com installDist =='
Push-Location (Join-Path $app 'backend')
try {
    $extra = Separar $env:PAINEL_GRADLE_ARGS
    & .\gradlew.bat installDist @extra
    if ($LASTEXITCODE -ne 0) { throw 'gradlew installDist falhou' }
} finally {
    Pop-Location
}
$instalado = Join-Path $app 'backend\build\install\painel'
if (-not (Test-Path (Join-Path $instalado 'bin\painel.bat')) -or -not (Test-Path (Join-Path $app 'frontend\dist\index.html'))) {
    throw "o build nao gerou $instalado\bin\painel.bat ou frontend\dist\index.html"
}

Write-Host "== Montando $destino =="
# So se apaga o que esta dentro de app\pacote\ (a conferencia vem antes de qualquer remocao).
$limite = (Join-Path $app 'pacote') + '\'
if (-not $destino.StartsWith($limite, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "destino fora de app\pacote\: $destino"
}
if (Test-Path $destino) { Remove-Item -Recurse -Force $destino }
foreach ($sub in @('bin', 'lib', 'frontend\dist', 'config\cards')) {
    New-Item -ItemType Directory -Force (Join-Path $destino $sub) | Out-Null
}

Copy-Item (Join-Path $instalado 'bin\painel'), (Join-Path $instalado 'bin\painel.bat') (Join-Path $destino 'bin')
Copy-Item (Join-Path $instalado 'lib\*.jar') (Join-Path $destino 'lib')
Copy-Item (Join-Path $app 'frontend\dist\*') (Join-Path $destino 'frontend\dist') -Recurse

# Configuracao: SOMENTE os modelos e as fichas versionadas. Nenhum YAML real e copiado, mesmo que exista na maquina.
Copy-Item (Join-Path $app 'config\*.exemplo.yaml') (Join-Path $destino 'config')
$demo = Join-Path $app 'config\*.demo.yaml'
if (Test-Path $demo) { Copy-Item $demo (Join-Path $destino 'config') }
Copy-Item (Join-Path $app 'config\cards\*.card.yaml') (Join-Path $destino 'config\cards')
Copy-Item (Join-Path $app '.env.example') (Join-Path $destino '.env.example')
Copy-Item (Join-Path $aqui 'LEIA-ME-pacote.txt') (Join-Path $destino 'LEIA-ME.txt')

$versao = (Select-String -Path (Join-Path $app 'backend\build.gradle.kts') -Pattern '^version = "(.*)"$' | Select-Object -First 1).Matches[0].Groups[1].Value
$commit = 'desconhecido'
try {
    $c = & git -C $app rev-parse --short HEAD 2>$null
    if ($LASTEXITCODE -eq 0 -and $c) { $commit = "$c".Trim() }
} catch {
    $commit = 'desconhecido'
}
$quando = (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd'T'HH:mm:ss'Z'")
Set-Content -Path (Join-Path $destino 'VERSAO.txt') -Encoding ASCII -Value @("versao=$versao", "commit=$commit", "montado_em=$quando")

Write-Host "Pacote pronto em $destino (versao $versao, commit $commit)"
Write-Host 'Proximo passo: powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\verificar-pacote.ps1'
