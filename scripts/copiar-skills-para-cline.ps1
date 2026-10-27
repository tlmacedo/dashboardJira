# Copia as skills de skills-para-cline\ para o diretório de skills do Cline/ClineSR no Windows 11.
# O Cline procura skills UM nível abaixo do diretório (<diretório>\<skill>\SKILL.md) e exige que a pasta tenha o
# mesmo nome do campo `name` do SKILL.md; as pastas de skills-para-cline\ já estão nesse formato.
#
# O que faz, e só isso: copia cada pasta de skill (a que tem SKILL.md) para o destino. Não executa nenhum script
# das skills, não baixa nada e não acessa rede. Uma pasta de mesmo nome que NÃO tenha o arquivo
# `.origem-copiada-do-claude` (instalada à mão ou por outro script) não é tocada: o script avisa e pula.
#
# Uso (PowerShell 5.1 ou 7, a partir da raiz do projeto):
#   powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\copiar-skills-para-cline.ps1
# Opções: -Destino <pasta de skills>   (padrão %USERPROFILE%\.cline\skills; use .cline\skills só para este projeto)
#         -Somente tdd,grilling        (subconjunto)
# Depois: reabra o VS Code e confira a aba Skills do Cline.
#
# Este arquivo é salvo em UTF-8 com BOM (necessário para o PowerShell 5.1 ler os acentos).
param(
    [string]$Destino = (Join-Path $env:USERPROFILE '.cline\skills'),
    [string[]]$Somente = @()
)

$ErrorActionPreference = 'Stop'
$MARCA = '.origem-copiada-do-claude'
$raiz = Split-Path -Parent $PSScriptRoot
$origem = Join-Path $raiz 'skills-para-cline'
if (-not (Test-Path $origem)) { throw "Pasta não encontrada: $origem (rode a partir de um clone do projeto)." }

New-Item -ItemType Directory -Force -Path $Destino | Out-Null
$copiadas = 0; $puladas = 0; $atualizadas = 0
foreach ($pasta in Get-ChildItem -Directory $origem | Where-Object { $_.Name -ne 'LICENSES' }) {
    if (-not (Test-Path (Join-Path $pasta.FullName 'SKILL.md'))) { continue }
    if ($Somente.Count -gt 0 -and ($Somente -notcontains $pasta.Name)) { continue }
    $alvo = Join-Path $Destino $pasta.Name
    if (Test-Path $alvo) {
        if (-not (Test-Path (Join-Path $alvo $MARCA))) {
            Write-Host ("PULADA  {0}: já existe em {1} e não veio deste script (não foi tocada)." -f $pasta.Name, $Destino) -ForegroundColor Yellow
            $puladas++; continue
        }
        Remove-Item -Recurse -Force $alvo
        $atualizadas++
    } else { $copiadas++ }
    Copy-Item -Recurse -Path $pasta.FullName -Destination $alvo
}
# licenças junto das skills copiadas
$licencas = Join-Path $origem 'LICENSES'
if ((Test-Path $licencas) -and (($copiadas + $atualizadas) -gt 0)) {
    $dest = Join-Path $Destino '_LICENSES-skills-copiadas'
    New-Item -ItemType Directory -Force -Path $dest | Out-Null
    Copy-Item -Force -Path (Join-Path $licencas '*') -Destination $dest
}
Write-Host ("Concluído: {0} novas, {1} atualizadas, {2} puladas. Destino: {3}" -f $copiadas, $atualizadas, $puladas, $Destino) -ForegroundColor Green
Write-Host 'Reabra o VS Code e confira a aba Skills do Cline.'
