# Funcoes comuns dos scripts de implantacao do Painel de Squads (Windows). Use com:  . "$PSScriptRoot\comum.ps1"
# Este arquivo e SO ASCII de proposito: o Windows PowerShell 5.1 le arquivo sem BOM como ANSI e estragaria os acentos.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# SIDs no lugar de nomes, para valer em Windows de qualquer idioma.
$script:SidAdministradores = '*S-1-5-32-544'
# Grupos que NUNCA podem ter acesso ao arquivo de variaveis: Everyone, BUILTIN\Users e Authenticated Users.
$script:SidsProibidosNoArquivoDeVariaveis = @('S-1-1-0', 'S-1-5-32-545', 'S-1-5-11')
$script:IcaclsSidsProibidos = @('*S-1-1-0', '*S-1-5-32-545', '*S-1-5-11')

# Deixa o arquivo de variaveis (painel.env) legivel SO pelos administradores (controle total) e pela conta do servico (leitura).
# A heranca e removida: nada de Everyone, Users nem Authenticated Users. A conta so le (o painel nao altera o arquivo).
function Restringir-ArquivoDeVariaveis {
    param(
        [Parameter(Mandatory = $true)][string]$Arquivo,
        [Parameter(Mandatory = $true)][string]$Conta
    )
    if (-not (Test-Path -LiteralPath $Arquivo)) { throw "arquivo de variaveis nao encontrado" }
    & icacls $Arquivo /inheritance:r /grant:r "$($script:SidAdministradores):F" "${Conta}:R" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'icacls falhou ao restringir o arquivo de variaveis' }
    # Cinto e suspensorio: tira qualquer entrada dos grupos amplos (nao falha se nao houver).
    & icacls $Arquivo /remove:g @($script:IcaclsSidsProibidos) | Out-Null
    & icacls $Arquivo /remove:d @($script:IcaclsSidsProibidos) | Out-Null
}

# $true se nenhum dos SIDs proibidos tem entrada na ACL do arquivo (a verificacao-de-instalacao usa isto).
function Test-ArquivoDeVariaveisProtegido {
    param([Parameter(Mandatory = $true)][string]$Arquivo)
    if (-not (Test-Path -LiteralPath $Arquivo)) { return $false }
    $acl = Get-Acl -LiteralPath $Arquivo
    foreach ($regra in $acl.Access) {
        $sid = $null
        try { $sid = $regra.IdentityReference.Translate([System.Security.Principal.SecurityIdentifier]).Value } catch { $sid = $null }
        if ($sid -and ($script:SidsProibidosNoArquivoDeVariaveis -contains $sid)) { return $false }
    }
    return $true
}
