# Atalho para os subcomandos de operacao do painel: validar-config, backup-agora, verificar-restauracao.
# Localiza o pacote em PAINEL_HOME (padrao: app\backend\build\install\painel, gerado por .\gradlew.bat installDist),
# entra em app\ (para que config, data e o arquivo de variaveis relativos funcionem) e repassa os argumentos
# e o codigo de saida. Nao usa conteiner nem rede.
# Este arquivo e SO ASCII de proposito: o Windows PowerShell 5.1 le arquivo sem BOM como ANSI e estragaria os acentos.
$ErrorActionPreference = 'Stop'
$aqui = Split-Path -Parent $MyInvocation.MyCommand.Path
$app = Split-Path -Parent $aqui
$pacote = if ($env:PAINEL_HOME) { $env:PAINEL_HOME } else { Join-Path $app 'backend\build\install\painel' }
$exe = Join-Path $pacote 'bin\painel.bat'
if (-not (Test-Path $exe)) {
    # Write-Error com 'Stop' lancaria excecao e sairia com 1; o codigo de uso incorreto/ambiente e 2.
    [Console]::Error.WriteLine('Pacote do painel nao encontrado em PAINEL_HOME (rode: cd app\backend; .\gradlew.bat installDist).')
    exit 2
}
Set-Location $app
& $exe @args
exit $LASTEXITCODE
