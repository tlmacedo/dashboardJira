# Atalho para os subcomandos de operação do painel: validar-config, backup-agora, verificar-restauracao.
# Localiza o pacote em PAINEL_HOME (padrão: app\backend\build\install\painel, gerado por .\gradlew.bat installDist),
# entra em app\ (para que config, data e o arquivo de variáveis relativos funcionem) e repassa os argumentos
# e o código de saída. Não usa contêiner nem rede.
$ErrorActionPreference = 'Stop'
$aqui = Split-Path -Parent $MyInvocation.MyCommand.Path
$app = Split-Path -Parent $aqui
$pacote = if ($env:PAINEL_HOME) { $env:PAINEL_HOME } else { Join-Path $app 'backend\build\install\painel' }
$exe = Join-Path $pacote 'bin\painel.bat'
if (-not (Test-Path $exe)) {
    Write-Error 'Pacote do painel não encontrado em PAINEL_HOME (rode: cd app\backend; .\gradlew.bat installDist).'
    exit 2
}
Set-Location $app
& $exe @args
exit $LASTEXITCODE
