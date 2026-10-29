# Atualiza o painel para uma nova versao por troca de pasta, com backup antes (Plano 13, T10). NAO reverte sozinho.
# Uso (PowerShell como administrador):
#   powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy\windows\atualizar.ps1 -Raiz 'C:\Painel' -Pacote 'C:\Painel\recebidos\painel-2026.11.1.zip' [-SemServico]
#
# Ordem (cada passo imprime "OK <passo>"):
#   1. confere o SHA-256 do zip contra o .sha256 ao lado;
#   2. extrai numa pasta nova <Raiz>\app\<versao> (o pacote em uso fica intacto; recusa se a pasta da versao ja existir);
#   3. PARA o servico (ou a tarefa) e espera o fim do processo (a trava <banco>.lock some);
#   4. BACKUP do banco (painel backup-agora, Plano 12); se falhar, religa a versao atual, remove a pasta extraida e aborta;
#   5. registra a versao anterior e o nome do backup em <Raiz>\app\versao-anterior.txt (pasta que so administradores
#      escrevem; so depois do backup);
#   6. troca a juncao <Raiz>\app\atual para a versao nova;
#   7. INICIA o servico e espera /api/health (as migracoes rodam na subida);
#   8. fumaca (a saude responde e /api/meta informa a versao nova).
# Falha no 7 ou no 8: imprime "FALHOU: execute reverter" com o comando exato e sai com 1. NAO ha reversao automatica: depois
# de uma migracao, restaurar o banco e decisao humana (ver docs\implantacao\reversao.md).
# -SemServico pula 3, 4, 7 e 8 (so serve ao ensaio da troca de pasta).
# Este arquivo e SO ASCII de proposito: o Windows PowerShell 5.1 le arquivo sem BOM como ANSI e estragaria os acentos.
param(
    [Parameter(Mandatory = $true)][string]$Raiz,
    [Parameter(Mandatory = $true)][string]$Pacote,
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

if (-not (Test-Path -LiteralPath $app)) { throw "pasta do painel nao encontrada: $app" }
if (-not (Test-Path -LiteralPath $Pacote)) { throw 'zip nao encontrado' }
$Pacote = (Resolve-Path -LiteralPath $Pacote).Path

function Falhar-DepoisDaTroca([string]$Motivo) {
    [Console]::Error.WriteLine("FALHOU: $Motivo")
    [Console]::Error.WriteLine("FALHOU: execute reverter -> powershell.exe -NoProfile -ExecutionPolicy Bypass -File $PSScriptRoot\reverter.ps1 -Raiz '$Raiz'   (acrescente -RestaurarBanco se a versao nova trouxe migracao sem volta)")
    exit 1
}

Confirmar-Sha256 -Zip $Pacote
Write-Host 'OK sha256'
$versao = Obter-VersaoDoZip -Zip $Pacote

Expandir-Pacote -Zip $Pacote -App $app
Write-Host "OK extraido em $app\$versao"

$anterior = Obter-VersaoAtual -App $app
if ($anterior -ne '' -and -not (Test-VersaoValida $anterior)) { throw 'a versao atual (juncao) tem um nome inesperado' }

$copia = ''
if (-not $SemServico) {
    Parar-Painel -Servico $Servico
    Write-Host 'OK servico parado'
    if ($anterior -ne '') {
        try {
            $copia = Fazer-Backup -App $app -ArquivoEnv $arquivoEnv
        } catch {
            [Console]::Error.WriteLine('FALHOU: o backup antes da troca nao foi feito; religando a versao atual (nada foi trocado; a pasta extraida foi removida)')
            Religar-Painel -Servico $Servico
            # A pasta foi criada por ESTA execucao (Expandir-Pacote recusa uma que ja exista): remover e seguro e permite repetir.
            Remove-Item -LiteralPath (Join-Path $app $versao) -Recurse -Force
            exit 1
        }
        if (-not (Test-NomeDeBackupValido $copia)) { throw 'o backup devolveu um nome inesperado' }
        Write-Host "OK backup $copia"
    }
}

# O registro so e gravado depois do backup (uma falha nao perde o registro da atualizacao anterior), na pasta que so
# administradores escrevem.
if ($anterior -ne '') {
    Gravar-Registro -App $app -Anterior $anterior -Backup $copia
    Write-Host "OK versao anterior registrada ($anterior)"
}

Trocar-Atual -App $app -Versao $versao
Write-Host "OK atual aponta para $versao"

if (-not $SemServico) {
    try { Iniciar-Painel -Servico $Servico } catch { Falhar-DepoisDaTroca 'o servico nao iniciou' }
    if (-not (Esperar-Saude -Url $Url -TimeoutSeg 120)) { Falhar-DepoisDaTroca '/api/health nao respondeu em 120 s' }
    Write-Host 'OK servico iniciado e saudavel'
    if (-not (Conferir-Fumaca -Url $Url -Versao $versao)) { Falhar-DepoisDaTroca 'a fumaca nao passou' }
    Write-Host 'OK fumaca'
}

Write-Host "ATUALIZACAO_OK $versao"
