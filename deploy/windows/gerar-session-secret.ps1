# Gera o SESSION_SECRET (48 bytes aleatorios em base64) direto no arquivo de variaveis, SEM imprimir o valor.
# Uso:  powershell.exe -NoProfile -ExecutionPolicy Bypass -File gerar-session-secret.ps1 -Arquivo C:\Painel\dados\painel.env [-Rotacionar]
#   - sem -Rotacionar: recusa se SESSION_SECRET ja tem valor (codigo 3);
#   - com -Rotacionar: o valor atual passa para SESSION_SECRET_ANTERIOR (aceito so para ler cookies por 8 horas) e um novo
#     entra em SESSION_SECRET, como em docs\operacao\rotacao-de-segredos.md. Reinicie o painel depois; apague a linha
#     SESSION_SECRET_ANTERIOR passadas 8 horas e reinicie de novo.
# Saida: SESSION_SECRET_GERADO ou SESSION_SECRET_ROTACIONADO. O arquivo mantem a ACL (so o conteudo muda).
# Este arquivo e SO ASCII de proposito: o Windows PowerShell 5.1 le arquivo sem BOM como ANSI e estragaria os acentos.
param(
    [Parameter(Mandatory = $true)][string]$Arquivo,
    [switch]$Rotacionar
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $Arquivo)) {
    [Console]::Error.WriteLine('ERRO: arquivo de variaveis nao encontrado')
    exit 2
}
$utf8SemBom = New-Object System.Text.UTF8Encoding($false)
$linhas = @([System.IO.File]::ReadAllLines($Arquivo, $utf8SemBom))

$linhaAtual = $linhas | Where-Object { $_ -like 'SESSION_SECRET=*' } | Select-Object -First 1
$atual = ''
if ($linhaAtual) { $atual = $linhaAtual.Substring('SESSION_SECRET='.Length).Trim() }
$foiRotacao = ($atual -ne '')
if ($atual -ne '' -and -not $Rotacionar) {
    [Console]::Error.WriteLine('ERRO: SESSION_SECRET ja tem valor; use -Rotacionar para trocar (o valor atual vira SESSION_SECRET_ANTERIOR)')
    exit 3
}

$bytes = New-Object byte[] 48
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
$novo = [Convert]::ToBase64String($bytes)

# Tudo do arquivo, menos as linhas de segredo da sessao; as novas entram no fim.
$resto = @($linhas | Where-Object { $_ -notlike 'SESSION_SECRET=*' -and $_ -notlike 'SESSION_SECRET_ANTERIOR=*' })
if ($atual -ne '') { $resto += "SESSION_SECRET_ANTERIOR=$atual" }
$resto += "SESSION_SECRET=$novo"
[System.IO.File]::WriteAllLines($Arquivo, [string[]]$resto, $utf8SemBom)

if ($foiRotacao) { Write-Host 'SESSION_SECRET_ROTACIONADO' } else { Write-Host 'SESSION_SECRET_GERADO' }
