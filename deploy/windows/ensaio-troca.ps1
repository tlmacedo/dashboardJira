# Ensaio da troca de versao e da reversao, sem servico e sem tocar em nada fora de uma pasta temporaria (Plano 13, T10).
# Uso:  powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy\windows\ensaio-troca.ps1
# Cria dois pacotes de mentira (2026.11.1 e 2026.11.2, so com bin\painel.bat de marcador), roda atualizar.ps1 -SemServico duas
# vezes e reverter.ps1 -SemServico, e confere para onde aponta "atual" em cada etapa.
# Saida final: TROCA_OK e REVERSAO_OK. A pasta temporaria e removida no finally, mesmo em caso de erro.
# Este arquivo e SO ASCII de proposito: o Windows PowerShell 5.1 le arquivo sem BOM como ANSI e estragaria os acentos.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\comum.ps1"
Add-Type -AssemblyName System.IO.Compression.FileSystem

$ensaio = Join-Path ([System.IO.Path]::GetTempPath()) ('painel-ensaio-' + [Guid]::NewGuid().ToString('N'))
$recebidos = Join-Path $ensaio 'recebidos'
$fonte = Join-Path $ensaio 'fonte'
try {
    New-Item -ItemType Directory -Force (Join-Path $ensaio 'app'), (Join-Path $ensaio 'dados'), $recebidos, $fonte | Out-Null

    # Pacote de mentira: zip com a pasta raiz painel-<versao>\ e o .sha256 (hash, dois espacos, nome).
    function Criar-Pacote([string]$Versao) {
        $pasta = Join-Path $fonte "painel-$Versao"
        New-Item -ItemType Directory -Force (Join-Path $pasta 'bin') | Out-Null
        Set-Content -LiteralPath (Join-Path $pasta 'VERSAO.txt') -Encoding ASCII -Value "versao=$Versao"
        Set-Content -LiteralPath (Join-Path $pasta 'bin\painel.bat') -Encoding ASCII -Value "@echo $Versao"
        $zip = Join-Path $recebidos "painel-$Versao.zip"
        [System.IO.Compression.ZipFile]::CreateFromDirectory($pasta, $zip, [System.IO.Compression.CompressionLevel]::Optimal, $true)
        [System.IO.File]::WriteAllText("$zip.sha256", ((Calcular-Sha256 $zip) + "  painel-$Versao.zip`n"), (New-Object System.Text.ASCIIEncoding))
        return $zip
    }

    function Conferir-Atual([string]$Esperada) {
        $achada = Obter-VersaoAtual -App (Join-Path $ensaio 'app')
        if ($achada -ne $Esperada) { throw "atual deveria apontar para $Esperada, mas aponta para $achada" }
    }

    $zip1 = Criar-Pacote '2026.11.1'
    $zip2 = Criar-Pacote '2026.11.2'

    & (Join-Path $PSScriptRoot 'atualizar.ps1') -Raiz $ensaio -Pacote $zip1 -SemServico | Out-Null
    Conferir-Atual '2026.11.1'
    & (Join-Path $PSScriptRoot 'atualizar.ps1') -Raiz $ensaio -Pacote $zip2 -SemServico | Out-Null
    Conferir-Atual '2026.11.2'
    if (-not (Test-Path (Join-Path $ensaio 'app\2026.11.1\bin\painel.bat'))) { throw 'a versao anterior nao pode sumir na troca' }
    Write-Host 'TROCA_OK'

    & (Join-Path $PSScriptRoot 'reverter.ps1') -Raiz $ensaio -SemServico | Out-Null
    Conferir-Atual '2026.11.1'
    if (-not (Test-Path (Join-Path $ensaio 'app\2026.11.2\bin\painel.bat'))) { throw 'a versao revertida continua em disco' }
    Write-Host 'REVERSAO_OK'
} finally {
    # A juncao "atual" sai primeiro, sem recursao, para a remocao nunca seguir o ponteiro para fora da pasta temporaria.
    $juncao = Join-Path $ensaio 'app\atual'
    if (Test-Path -LiteralPath $juncao) { [System.IO.Directory]::Delete($juncao, $false) }
    if (Test-Path -LiteralPath $ensaio) { Remove-Item -LiteralPath $ensaio -Recurse -Force }
}
