# Sobe o painel no Windows (PowerShell 5.1 ou 7).
# Requisitos: JDK 21 e Node 22 com pnpm. Uso (a partir de app\):  .\iniciar.ps1  [-SemBuildDoFront]
# Variáveis: copie .env.example para .env e preencha. O banco é o arquivo data\painel.db.
param([switch]$SemBuildDoFront)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if (-not $SemBuildDoFront -or -not (Test-Path 'frontend\dist\index.html')) {
    Write-Host '== Frontend: instalando dependências e gerando o build =='
    Push-Location frontend
    try {
        pnpm install --frozen-lockfile
        if ($LASTEXITCODE -ne 0) { throw 'pnpm install falhou' }
        pnpm build
        if ($LASTEXITCODE -ne 0) { throw 'pnpm build falhou' }
    } finally { Pop-Location }
}

Write-Host '== Backend: iniciando em http://127.0.0.1:8080 (Ctrl+C para parar) =='
Push-Location backend
try { .\gradlew.bat run } finally { Pop-Location }
