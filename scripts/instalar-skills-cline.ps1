# Instala (ou atualiza) as skills de terceiros que o Claude Code usava neste projeto, para o Cline/ClineSR
# no Windows 11: superpowers (obra), mattpocock-skills (mattpocock) e impeccable (pbakaus).
#
# O que faz, e só isso:
#   1. `git clone --depth 1 --filter=blob:none --sparse` de cada repositório público para uma pasta de cache
#      (padrão %USERPROFILE%\.cline\skill-repos\<nome>), baixando só as pastas de skills; se já existir,
#      `git pull --ff-only`.
#   2. Copia cada pasta de skill (a que tem SKILL.md) para o diretório global de skills do Cline
#      (padrão %USERPROFILE%\.cline\skills\<skill>), porque o Cline só procura skills UM nível abaixo desse
#      diretório e exige que a pasta tenha o mesmo nome do campo `name` do SKILL.md.
# Não executa nada dos repositórios (nem npm/npx, nem instaladores, nem scripts das skills).
#
# Idempotente: rodar de novo atualiza. Cada skill copiada recebe o arquivo `.origem-instalar-skills-cline`
# (repositório e commit). Uma pasta de mesmo nome SEM esse arquivo (instalada à mão) não é tocada: o script
# avisa e pula.
#
# Uso (PowerShell 5.1 ou 7, a partir da raiz do projeto; a política padrão do Windows bloqueia rodar .ps1 direto):
#   powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\instalar-skills-cline.ps1
# Opções: -Destino <pasta de skills>  (ex.: .cline\skills para instalar só neste projeto; NÃO versione)
#         -Cache <pasta dos clones>   -Somente superpowers,impeccable   (subconjunto)
# Requer: Git for Windows no PATH. Depois: reabra o VS Code e confira a aba Skills do Cline.
#
# Este arquivo é salvo em UTF-8 com BOM (necessário para o PowerShell 5.1 ler os acentos).
param(
    [string]$Destino = (Join-Path $env:USERPROFILE '.cline\skills'),
    [string]$Cache = (Join-Path $env:USERPROFILE '.cline\skill-repos'),
    [string[]]$Somente = @()
)

$ErrorActionPreference = 'Stop'
$MARCA = '.origem-instalar-skills-cline'

# repositórios públicos conferidos em 25/10/2026 (licenças: MIT, MIT, Apache-2.0)
$repositorios = @(
    [ordered]@{ nome = 'superpowers'; url = 'https://github.com/obra/superpowers.git'; esparso = @('skills') },
    [ordered]@{ nome = 'mattpocock-skills'; url = 'https://github.com/mattpocock/skills.git'; esparso = @('skills', '.claude-plugin') },
    [ordered]@{ nome = 'impeccable'; url = 'https://github.com/pbakaus/impeccable.git'; esparso = @('.agents/skills/impeccable') }
)

function Executar-Git([string[]]$argumentos) {
    & git @argumentos
    if ($LASTEXITCODE -ne 0) { throw "git $($argumentos -join ' ') falhou (código $LASTEXITCODE)" }
}

function Pastas-De-Skill($repo, [string]$raiz) {
    # devolve as pastas de skill de cada repositório
    switch ($repo.nome) {
        'superpowers' {
            return @(Get-ChildItem -Directory (Join-Path $raiz 'skills') | Where-Object { Test-Path (Join-Path $_.FullName 'SKILL.md') } | ForEach-Object { $_.FullName })
        }
        'mattpocock-skills' {
            # só as skills que o plugin do Claude publica (a lista do plugin.json); ficam de fora as pastas
            # deprecated, in-progress e misc, que não fazem parte do plugin
            $manifesto = Get-Content -Raw -Encoding UTF8 (Join-Path $raiz '.claude-plugin\plugin.json') | ConvertFrom-Json
            return @($manifesto.skills | ForEach-Object { Join-Path $raiz (($_ -replace '^\./', '') -replace '/', '\') } | Where-Object { Test-Path (Join-Path $_ 'SKILL.md') })
        }
        'impeccable' {
            return @(Join-Path $raiz '.agents\skills\impeccable')
        }
    }
}

function Nome-Da-Skill([string]$pasta) {
    # campo `name` do frontmatter do SKILL.md
    foreach ($linha in (Get-Content -Encoding UTF8 (Join-Path $pasta 'SKILL.md') -TotalCount 15)) {
        if ($linha -match '^name:\s*["'']?([^"'']+?)["'']?\s*$') { return $Matches[1] }
    }
    return $null
}

if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw 'git não encontrado no PATH. Instale o Git for Windows e abra um terminal novo.' }
New-Item -ItemType Directory -Force -Path $Destino, $Cache | Out-Null

$instaladas = @{}
$avisos = New-Object System.Collections.ArrayList
foreach ($repo in $repositorios) {
    if ($Somente.Count -gt 0 -and -not ($Somente -contains $repo.nome)) { continue }
    $clone = Join-Path $Cache $repo.nome
    Write-Output "== $($repo.nome) ($($repo.url))"
    if (Test-Path (Join-Path $clone '.git')) {
        $remoto = (& git -C $clone remote get-url origin).Trim()
        if ($remoto -ne $repo.url) { throw "O clone $clone aponta para $remoto, não para $($repo.url). Confira e apague a pasta se for o caso." }
        Executar-Git @('-C', $clone, 'pull', '--ff-only')
    }
    else {
        Executar-Git @('clone', '--depth', '1', '--filter=blob:none', '--sparse', $repo.url, $clone)
    }
    Executar-Git (@('-C', $clone, 'sparse-checkout', 'set') + $repo.esparso)
    $commit = (& git -C $clone rev-parse --short HEAD).Trim()

    foreach ($origem in (Pastas-De-Skill $repo $clone)) {
        $pastaNome = Split-Path -Leaf $origem
        $nome = Nome-Da-Skill $origem
        if ($nome -ne $pastaNome) { [void]$avisos.Add("$($repo.nome)/${pastaNome}: name '$nome' difere da pasta; o Cline ignoraria. Pulada."); continue }
        if ($instaladas.ContainsKey($nome)) { [void]$avisos.Add("${nome}: já veio de $($instaladas[$nome]); a de $($repo.nome) foi pulada."); continue }
        $alvo = Join-Path $Destino $nome
        if (Test-Path $alvo) {
            if (-not (Test-Path (Join-Path $alvo $MARCA))) { [void]$avisos.Add("${nome}: $alvo já existe e não foi instalada por este script. Não foi tocada."); continue }
            Remove-Item -Recurse -Force $alvo
        }
        Copy-Item -Recurse -Force $origem $alvo
        Set-Content -Encoding UTF8 -Path (Join-Path $alvo $MARCA) -Value "$($repo.url) $commit"
        $instaladas[$nome] = $repo.nome
        Write-Output "   ok  $nome"
    }
}

Write-Output ''
Write-Output "Skills instaladas ou atualizadas: $($instaladas.Count) em $Destino"
foreach ($a in $avisos) { Write-Output "AVISO: $a" }
Write-Output 'Reabra o VS Code e confira a aba Skills do Cline. Regras de precedência: docs\ferramentas-claude-para-o-cline\skills.md'
