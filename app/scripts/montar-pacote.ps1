# Monta o pacote de producao do painel em app\pacote\painel\ (sem conteiner, sem servidor de banco).
# Uso (a partir de app\):
#   powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\montar-pacote.ps1
#       monta so a pasta (versao 0.0.0-dev, para ensaio local)
#   powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\montar-pacote.ps1 -Versao 2026.11.1
#       monta a pasta E o zip app\pacote\painel-2026.11.1.zip com o .sha256 ao lado (versao AAAA.MM.N, mes 01 a 12,
#       N >= 1), e ja o verifica
#
# O pacote leva: bin\ (scripts de subida), lib\ (jars), frontend\dist\ (as telas), config\ (SO os modelos *.exemplo.yaml,
# *.demo.yaml e as fichas de cards\), o modelo de variaveis, implantacao\ (scripts de atualizacao e reversao, modelos) e docs\
# (o guia de implantacao), VERSAO.txt e LEIA-ME.txt. Configuracao real, arquivo de variaveis real e arquivo de banco NUNCA
# entram: ficam FORA da pasta do pacote (ver docs\implantacao.md).
#
# Requisitos: JDK 21, Node 22 e pnpm. Em maquina sem acesso direto aos registros (proxy ou espelho da empresa), defina
# PAINEL_PNPM_ARGS e PAINEL_GRADLE_ARGS (por exemplo --offline quando as dependencias ja estao no cache).
# Este arquivo e SO ASCII de proposito: o Windows PowerShell 5.1 le arquivo sem BOM como ANSI e estragaria os acentos.
param(
    [string]$Versao = ''
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$aqui = Split-Path -Parent $MyInvocation.MyCommand.Path
$app = Split-Path -Parent $aqui
$repo = Split-Path -Parent $app
$destino = Join-Path $app 'pacote\painel'

function Separar($texto) {
    if ($texto) { return @($texto -split '\s+' | Where-Object { $_ }) }
    return @()
}

$paramsDaVersao = @()
if ($Versao -ne '') {
    if ($Versao -notmatch '^[0-9]{4}\.(0[1-9]|1[0-2])\.[1-9][0-9]*$') {
        throw 'versao invalida (esperado AAAA.MM.N, mes 01 a 12, N >= 1; exemplo: 2026.11.1)'
    }
    $commitCurto = 'desconhecido'
    try {
        $c = & git -C $app rev-parse --short=7 HEAD 2>$null
        if ($LASTEXITCODE -eq 0 -and $c) { $commitCurto = "$c".Trim() }
    } catch {
        $commitCurto = 'desconhecido'
    }
    $construidaEm = (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd'T'HH:mm:ss'Z'")
    $paramsDaVersao = @("-Pversao=$Versao", "-Pcommit=$commitCurto", "-PconstruidaEm=$construidaEm")
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
    & .\gradlew.bat installDist @paramsDaVersao @extra
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

# Implantacao: scripts de atualizacao/reversao/segredos (deploy\) e o guia (docs\). So o que existe no repositorio.
foreach ($sub in @('windows', 'linux', 'modelos')) {
    $origem = Join-Path $repo "deploy\$sub"
    if (Test-Path $origem) {
        $alvo = Join-Path $destino "implantacao\$sub"
        New-Item -ItemType Directory -Force $alvo | Out-Null
        Copy-Item (Join-Path $origem '*') $alvo -Recurse
    }
}
New-Item -ItemType Directory -Force (Join-Path $destino 'docs') | Out-Null
$guia = Join-Path $repo 'docs\implantacao.md'
if (Test-Path $guia) { Copy-Item $guia (Join-Path $destino 'docs') }
$guias = Join-Path $repo 'docs\implantacao'
if (Test-Path $guias) {
    New-Item -ItemType Directory -Force (Join-Path $destino 'docs\implantacao') | Out-Null
    Copy-Item (Join-Path $guias '*') (Join-Path $destino 'docs\implantacao') -Recurse
}

# VERSAO.txt: a versao, o commit e a data vem do mesmo recurso que o painel le (gerado pelo Gradle).
$propriedades = Join-Path $app 'backend\build\generated\versao\versao.properties'
function Propriedade($nome) {
    $linha = Select-String -Path $propriedades -Pattern "^$nome=(.*)$" | Select-Object -First 1
    if ($linha) { return $linha.Matches[0].Groups[1].Value.Trim() }
    return ''
}
Set-Content -Path (Join-Path $destino 'VERSAO.txt') -Encoding ASCII -Value @(
    "versao=$(Propriedade 'versao')", "commit=$(Propriedade 'commit')", "montado_em=$(Propriedade 'construidaEm')"
)

Write-Host "Pacote pronto em $destino (versao $(Propriedade 'versao'), commit $(Propriedade 'commit'))"

if ($Versao -eq '') {
    Write-Host 'Proximo passo: powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\verificar-pacote.ps1'
    exit 0
}

Write-Host '== Zip e SHA-256 =='
Push-Location (Join-Path $app 'backend')
try {
    $extra = Separar $env:PAINEL_GRADLE_ARGS
    & .\gradlew.bat montarPacote @paramsDaVersao @extra
    if ($LASTEXITCODE -ne 0) { throw 'gradlew montarPacote falhou' }
} finally {
    Pop-Location
}
$zip = Join-Path $app "pacote\painel-$Versao.zip"
if (-not (Test-Path $zip)) { throw "o Gradle nao gerou $zip" }
$hash = (Get-FileHash -Algorithm SHA256 -Path $zip).Hash.ToLower()
# Formato aceito por `sha256sum -c`: o hash em minusculas, dois espacos e o nome do arquivo (ASCII, sem BOM).
[System.IO.File]::WriteAllText("$zip.sha256", "$hash  painel-$Versao.zip`n", (New-Object System.Text.ASCIIEncoding))

& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $aqui 'verificar-pacote.ps1') $zip
if ($LASTEXITCODE -ne 0) { throw 'verificar-pacote falhou' }
Write-Host "PACOTE_OK painel-$Versao.zip $hash"
