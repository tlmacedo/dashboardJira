# Procura nos logs do painel o VALOR de cada segredo do arquivo de variaveis (nome com SECRET, PASSWORD ou TOKEN e valor nao
# vazio). Imprime so LOG_LIMPO ou, para cada achado, LOG_COM_SEGREDO <NOME_DA_VARIAVEL>; nunca o valor.
# Uso:  powershell.exe -NoProfile -ExecutionPolicy Bypass -File verificar-logs.ps1 -Raiz 'C:\Painel'
#   le C:\Painel\dados\painel.env e C:\Painel\logs\*.log (o arquivo e a pasta podem ser trocados com -Arquivo e -PastaDeLogs).
# Codigo de saida: 0 limpo, 1 segredo encontrado, 2 arquivo ausente.
# Este arquivo e SO ASCII de proposito: o Windows PowerShell 5.1 le arquivo sem BOM como ANSI e estragaria os acentos.
param(
    [string]$Raiz = 'C:\Painel',
    [string]$Arquivo = '',
    [string]$PastaDeLogs = ''
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($Arquivo -eq '') { $Arquivo = Join-Path $Raiz 'dados\painel.env' }
if ($PastaDeLogs -eq '') { $PastaDeLogs = Join-Path $Raiz 'logs' }
if (-not (Test-Path -LiteralPath $Arquivo)) {
    [Console]::Error.WriteLine('ERRO: arquivo de variaveis nao encontrado')
    exit 2
}
$logs = @()
if (Test-Path -LiteralPath $PastaDeLogs) { $logs = @(Get-ChildItem -LiteralPath $PastaDeLogs -Filter '*.log' -File | ForEach-Object { $_.FullName }) }

$achou = $false
foreach ($linha in [System.IO.File]::ReadAllLines($Arquivo)) {
    if (-not ($linha -match '^([A-Za-z0-9_]*(SECRET|PASSWORD|TOKEN)[A-Za-z0-9_]*)=(.*)$')) { continue }
    $nome = $Matches[1]
    $valor = $Matches[3].Trim()
    if ($valor -eq '' -or $logs.Count -eq 0) { continue }
    # -Quiet devolve so verdadeiro/falso: o valor nunca vai para a tela.
    if (Select-String -LiteralPath $logs -SimpleMatch -Pattern $valor -Quiet) {
        Write-Host "LOG_COM_SEGREDO $nome"
        $achou = $true
    }
}
if ($achou) { exit 1 }
Write-Host 'LOG_LIMPO'
exit 0
