# Volta o painel para a versao anterior (a de <Raiz>\dados\versao-anterior.txt, gravada por atualizar.ps1) (Plano 13, T10).
# Uso (PowerShell como administrador):
#   powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy\windows\reverter.ps1 -Raiz 'C:\Painel' [-RestaurarBanco] [-Backup <arquivo>] [-SemServico]
#
# Sem -RestaurarBanco so o pacote volta (serve quando a versao nova NAO trouxe migracao, ou trouxe uma compativel com a
# anterior). COM -RestaurarBanco o banco tambem volta para o backup feito antes da atualizacao (backup= do versao-anterior.txt,
# ou o -Backup indicado); tudo gravado DEPOIS desse backup se perde, e o banco atual NAO e apagado: ele e guardado ao lado como
# <banco>.migrado-<data> (com os -wal e -shm). -RestaurarBanco e OBRIGATORIO quando a versao nova declarou "migracao sem volta"
# ou quando a anterior nao sobe sobre o esquema novo. Antes de tocar no banco atual, a copia e conferida com
# `painel verificar-restauracao` (Plano 12).
# -SemServico pula parar/iniciar/fumaca (so serve ao ensaio da troca de pasta).
# Este arquivo e SO ASCII de proposito: o Windows PowerShell 5.1 le arquivo sem BOM como ANSI e estragaria os acentos.
param(
    [Parameter(Mandatory = $true)][string]$Raiz,
    [switch]$RestaurarBanco,
    [string]$Backup = '',
    [switch]$SemServico,
    [string]$Servico = 'Painel',
    [string]$Url = 'http://127.0.0.1:8080'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\comum.ps1"

$app = Join-Path $Raiz 'app'
$dados = Join-Path $Raiz 'dados'
$arquivoEnv = Join-Path $dados 'painel.env'
$registro = Join-Path $dados 'versao-anterior.txt'

if (-not (Test-Path -LiteralPath $app)) { throw "pasta do painel nao encontrada: $app" }
if (-not (Test-Path -LiteralPath $registro)) { throw "falta $registro: nenhuma atualizacao registrada, nao ha versao anterior para voltar" }
$anterior = Ler-ValorDoArquivo -Arquivo $registro -Nome 'versao'
if ($anterior -eq '') { throw 'o arquivo versao-anterior.txt nao informa a versao' }
if (-not (Test-Path -LiteralPath (Join-Path $app $anterior))) { throw "a pasta da versao anterior ($anterior) nao existe em $app" }

$copia = ''
if ($RestaurarBanco) {
    $copia = $Backup
    if ($copia -eq '') { $copia = Ler-ValorDoArquivo -Arquivo $registro -Nome 'backup' }
    if ($copia -eq '') { throw 'nao ha backup registrado em versao-anterior.txt; informe -Backup <arquivo>' }
    if (-not [System.IO.Path]::IsPathRooted($copia)) { $copia = Join-Path (Ler-ValorDoArquivo -Arquivo $arquivoEnv -Nome 'BACKUP_DIR') $copia }
    if (-not (Test-Path -LiteralPath $copia)) { throw 'a copia de seguranca nao foi encontrada' }
    # Confere a copia ANTES de mexer no banco atual (restaura num temporario e compara tabela a tabela).
    $guardada = $env:PAINEL_ENV_FILE
    $env:PAINEL_ENV_FILE = $arquivoEnv
    try {
        $conferencia = (& (Join-Path $app "$anterior\bin\painel.bat") verificar-restauracao $copia 2>&1 | Out-String)
    } finally {
        $env:PAINEL_ENV_FILE = $guardada
    }
    if ($conferencia -notmatch 'RESTAURACAO OK') { throw 'a copia nao passou em verificar-restauracao; nada foi alterado' }
    Write-Host 'OK copia conferida'
}

if (-not $SemServico) {
    Parar-Painel -Servico $Servico
    Write-Host 'OK servico parado'
}

if ($RestaurarBanco) {
    $banco = Ler-ValorDoArquivo -Arquivo $arquivoEnv -Nome 'DATABASE_PATH'
    if ($banco -eq '') { throw 'DATABASE_PATH nao esta definido no arquivo de variaveis' }
    $data = Get-Date -Format 'yyyyMMdd-HHmmss'
    # Nada e apagado: o banco atual e os auxiliares do WAL saem juntos para o lado, com a data.
    foreach ($sufixo in @('', '-wal', '-shm')) {
        if (Test-Path -LiteralPath "$banco$sufixo") { Move-Item -LiteralPath "$banco$sufixo" -Destination "$banco$sufixo.migrado-$data" }
    }
    Copy-Item -LiteralPath $copia -Destination $banco
    try { Set-Acl -LiteralPath $banco -AclObject (Get-Acl -LiteralPath "$banco.migrado-$data") } catch { Write-Host 'AVISO: confira a permissao do banco restaurado (a ACL do anterior nao foi copiada)' }
    Write-Host "OK banco restaurado (o anterior ficou em $banco.migrado-$data; o que foi gravado depois do backup so existe la)"
}

Trocar-Atual -App $app -Versao $anterior
Write-Host "OK atual aponta para $anterior"

if (-not $SemServico) {
    Iniciar-Painel -Servico $Servico
    if (-not (Esperar-Saude -Url $Url -TimeoutSeg 120)) { throw '/api/health nao respondeu em 120 s depois da reversao' }
    if (-not (Conferir-Fumaca -Url $Url -Versao $anterior)) { throw 'a fumaca nao passou depois da reversao' }
    Write-Host "OK servico iniciado, saudavel e na versao $anterior"
    if ($RestaurarBanco) { Write-Host 'Rode agora: painel backup-agora (o registro de backups voltou ao estado da copia).' }
}

Write-Host "REVERSAO_OK $anterior"
