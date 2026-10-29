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

# ---------------------------------------------------------------------------------------------------------------------
# Atualizacao e reversao por troca de pasta de versao (Plano 13, T10). Disposicao: <Raiz>\app\<versao>\ (pacote extraido,
# somente leitura), <Raiz>\app\atual (juncao para a versao ativa) e <Raiz>\dados (painel.env, config, banco, versao-anterior.txt).
# ---------------------------------------------------------------------------------------------------------------------

function Calcular-Sha256 {
    param([Parameter(Mandatory = $true)][string]$Arquivo)
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Arquivo).Hash.ToLower()
}

# Valor de NOME=valor num arquivo (sem aspas); vazio se nao houver.
function Ler-ValorDoArquivo {
    param([Parameter(Mandatory = $true)][string]$Arquivo, [Parameter(Mandatory = $true)][string]$Nome)
    if (-not (Test-Path -LiteralPath $Arquivo)) { return '' }
    foreach ($linha in [System.IO.File]::ReadAllLines($Arquivo)) {
        if ($linha.StartsWith("$Nome=")) { return $linha.Substring($Nome.Length + 1).Trim().Trim('"').Trim("'") }
    }
    return ''
}

# Confere o SHA-256 do zip contra o arquivo .sha256 ao lado ("<hash>  <nome>").
function Confirmar-Sha256 {
    param([Parameter(Mandatory = $true)][string]$Zip)
    $nomeDoZip = Split-Path -Leaf $Zip
    $arquivoHash = "$Zip.sha256"
    if (-not (Test-Path -LiteralPath $arquivoHash)) { throw "falta o arquivo $nomeDoZip.sha256 ao lado do zip" }
    $linha = ([string](Get-Content -LiteralPath $arquivoHash -TotalCount 1)).Trim()
    $partes = $linha -split '\s+', 2
    $esperado = $partes[0].ToLower()
    $nomeNoArquivo = ''
    if ($partes.Count -gt 1) { $nomeNoArquivo = $partes[1].TrimStart('*') }
    if ($nomeNoArquivo -ne $nomeDoZip) { throw 'o arquivo .sha256 nao e deste zip' }
    if ($esperado -ne (Calcular-Sha256 $Zip)) { throw 'o SHA-256 do zip nao confere com o arquivo .sha256' }
}

# Versao (AAAA.MM.N) pelo nome painel-AAAA.MM.N.zip.
function Obter-VersaoDoZip {
    param([Parameter(Mandatory = $true)][string]$Zip)
    $nome = Split-Path -Leaf $Zip
    if ($nome -match '^painel-([0-9]{4}\.(0[1-9]|1[0-2])\.[1-9][0-9]*)\.zip$') { return $Matches[1] }
    throw 'nome do pacote fora do padrao painel-AAAA.MM.N.zip'
}

# Extrai o pacote em <App>\<versao>.tmp, confere a estrutura e renomeia para <App>\<versao>. RECUSA se a pasta da versao ja
# existir: a versao em uso nunca e sobrescrita.
function Expandir-Pacote {
    param([Parameter(Mandatory = $true)][string]$Zip, [Parameter(Mandatory = $true)][string]$App)
    $versao = Obter-VersaoDoZip $Zip
    $destino = Join-Path $App $versao
    if (Test-Path -LiteralPath $destino) { throw "a pasta da versao $versao ja existe em $App (a versao em uso nunca e sobrescrita)" }
    $tmp = "$destino.tmp"
    if (Test-Path -LiteralPath $tmp) { Remove-Item -LiteralPath $tmp -Recurse -Force }
    New-Item -ItemType Directory -Force $tmp | Out-Null
    try {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        [System.IO.Compression.ZipFile]::ExtractToDirectory($Zip, $tmp)
        $interna = Join-Path $tmp "painel-$versao"
        if (-not (Test-Path -LiteralPath (Join-Path $interna 'bin\painel.bat'))) { throw "pacote sem painel-$versao\bin\painel.bat" }
        Move-Item -LiteralPath $interna -Destination $destino
    } finally {
        if (Test-Path -LiteralPath $tmp) { Remove-Item -LiteralPath $tmp -Recurse -Force }
    }
}

# Nome da versao para onde <App>\atual aponta, ou vazio se nao houver.
function Obter-VersaoAtual {
    param([Parameter(Mandatory = $true)][string]$App)
    $atual = Join-Path $App 'atual'
    if (-not (Test-Path -LiteralPath $atual)) { return '' }
    $alvo = @((Get-Item -LiteralPath $atual).Target) | Select-Object -First 1
    if (-not $alvo) { return '' }
    return (Split-Path -Leaf ([string]$alvo))
}

# Aponta <App>\atual (juncao) para <App>\<versao>. A juncao antiga e removida SEM recursao (so o ponteiro, nunca a pasta).
function Trocar-Atual {
    param([Parameter(Mandatory = $true)][string]$App, [Parameter(Mandatory = $true)][string]$Versao)
    $alvo = Join-Path $App $Versao
    if (-not (Test-Path -LiteralPath $alvo)) { throw "a pasta da versao $Versao nao existe em $App" }
    $atual = Join-Path $App 'atual'
    if (Test-Path -LiteralPath $atual) { [System.IO.Directory]::Delete($atual, $false) }
    New-Item -ItemType Junction -Path $atual -Target $alvo | Out-Null
}

# ---------------------------------------------------------------------------------------------------------------------
# Registro da versao anterior. FICA EM <App>\versao-anterior.txt (pasta que so administradores escrevem), NAO na pasta de dados do
# servico: o reverter executa como administrador o <App>\<versao>\bin\painel.bat, entao quem escreve o registro decide o que
# roda com privilegio. Mesmo assim tudo o que e lido dele e validado (defesa em profundidade).
# ---------------------------------------------------------------------------------------------------------------------

# Uma unica linha, sem caracteres de controle.
function Test-TextoDeUmaLinha {
    param([string]$Texto)
    return (-not ($Texto -match '[\x00-\x1f\x7f]'))
}

# Versao AAAA.MM.N (nada de .., barras, espacos, traco inicial, quebra de linha).
function Test-VersaoValida {
    param([string]$Versao)
    return ((Test-TextoDeUmaLinha $Versao) -and ($Versao -match '^[0-9]{4}\.(0[1-9]|1[0-2])\.[1-9][0-9]*$'))
}

# Nome de arquivo de backup do registro: so o nome (sem barras, sem .., sem traco inicial, sem espaco nem controle).
function Test-NomeDeBackupValido {
    param([string]$Nome)
    if (-not (Test-TextoDeUmaLinha $Nome)) { return $false }
    if ($Nome.Contains('..')) { return $false }
    return ($Nome -match '^[A-Za-z0-9][A-Za-z0-9._-]*$')
}

# O caminho completo esta DENTRO da pasta indicada (sem sair por .. nem por link)?
function Test-CaminhoDentroDe {
    param([string]$Arquivo, [string]$Pasta)
    try {
        $a = [System.IO.Path]::GetFullPath($Arquivo)
        $p = [System.IO.Path]::GetFullPath($Pasta).TrimEnd('\') + '\'
    } catch { return $false }
    return $a.StartsWith($p, [System.StringComparison]::OrdinalIgnoreCase)
}

# <App>\<versao> e uma pasta de verdade (nao uma juncao nem link), direta dentro de <App>, com bin\painel.bat proprio.
function Test-PastaDaVersaoValida {
    param([string]$App, [string]$Versao)
    if (-not (Test-VersaoValida $Versao)) { return $false }
    $pasta = Join-Path $App $Versao
    if (-not (Test-Path -LiteralPath $pasta -PathType Container)) { return $false }
    $item = Get-Item -LiteralPath $pasta
    if ($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) { return $false }
    if (-not (Test-CaminhoDentroDe $item.FullName $App)) { return $false }
    # "bin" e "bin\painel.bat" tambem tem de ser proprios (uma juncao em bin levaria a execucao para fora da pasta da versao).
    $bin = Join-Path $pasta 'bin'
    if (-not (Test-Path -LiteralPath (Join-Path $pasta 'bin') -PathType Container)) { return $false }
    if ((Get-Item -LiteralPath $bin).Attributes -band [System.IO.FileAttributes]::ReparsePoint) { return $false }
    $bat = Join-Path $pasta 'bin\painel.bat'
    if (-not (Test-Path -LiteralPath $bat -PathType Leaf)) { return $false }
    if ((Get-Item -LiteralPath $bat).Attributes -band [System.IO.FileAttributes]::ReparsePoint) { return $false }
    return $true
}

# Grava o registro de forma atomica (temporario + Move-Item). O backup e opcional.
function Gravar-Registro {
    param([Parameter(Mandatory = $true)][string]$App, [Parameter(Mandatory = $true)][string]$Anterior, [string]$Backup = '')
    $linhas = @("versao=$Anterior")
    if ($Backup -ne '') { $linhas += "backup=$Backup" }
    $tmp = Join-Path $App 'versao-anterior.txt.novo'
    Set-Content -LiteralPath $tmp -Encoding ASCII -Value $linhas
    Move-Item -LiteralPath $tmp -Destination (Join-Path $App 'versao-anterior.txt') -Force
}

# O painel roda como servico (NSSM) ou como tarefa agendada (Agendador de Tarefas); o mesmo nome serve aos dois.
function Parar-Painel {
    param([Parameter(Mandatory = $true)][string]$Servico)
    $s = Get-Service -Name $Servico -ErrorAction SilentlyContinue
    if ($s) {
        Stop-Service -Name $Servico
        for ($i = 0; $i -lt 60; $i++) {
            if ((Get-Service -Name $Servico).Status -eq 'Stopped') { return }
            Start-Sleep -Seconds 1
        }
        throw "o servico $Servico nao parou em 60 s"
    }
    $t = Get-ScheduledTask -TaskName $Servico -ErrorAction SilentlyContinue
    if (-not $t) { throw "nao ha servico nem tarefa agendada chamada $Servico" }
    # Parada brusca: o processo e encerrado (o SQLite em WAL tolera; a trava de arquivo some sozinha).
    Stop-ScheduledTask -TaskName $Servico
    for ($i = 0; $i -lt 60; $i++) {
        if ((Get-ScheduledTask -TaskName $Servico).State -ne 'Running') { return }
        Start-Sleep -Seconds 1
    }
    throw "a tarefa $Servico nao parou em 60 s"
}

function Iniciar-Painel {
    param([Parameter(Mandatory = $true)][string]$Servico)
    if (Get-Service -Name $Servico -ErrorAction SilentlyContinue) { Start-Service -Name $Servico } else { Start-ScheduledTask -TaskName $Servico }
}

# Religa a versao ainda atual depois de uma falha antes da troca.
function Religar-Painel {
    param([Parameter(Mandatory = $true)][string]$Servico)
    Iniciar-Painel -Servico $Servico
}

function Baixar-Texto {
    param([Parameter(Mandatory = $true)][string]$Url)
    try { return [string](Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 -Uri $Url).Content } catch { return '' }
}

# Espera GET <Url>/api/health responder {"ok":true}.
function Esperar-Saude {
    param([Parameter(Mandatory = $true)][string]$Url, [int]$TimeoutSeg = 120)
    for ($i = 0; $i -lt $TimeoutSeg; $i++) {
        if ((Baixar-Texto "$Url/api/health") -match '"ok":\s*true') { return $true }
        Start-Sleep -Seconds 1
    }
    return $false
}

# Backup do banco pelo mecanismo do Plano 12 (painel backup-agora, da versao ATUAL). Devolve o nome do arquivo da copia.
function Fazer-Backup {
    param([Parameter(Mandatory = $true)][string]$App, [Parameter(Mandatory = $true)][string]$ArquivoEnv)
    $guardada = $env:PAINEL_ENV_FILE
    $env:PAINEL_ENV_FILE = $ArquivoEnv
    # No Windows PowerShell 5.1, com 'Stop', qualquer linha no stderr do programa (ex.: "Picked up JAVA_TOOL_OPTIONS", comum em PC
    # corporativo) vira erro e interrompe o script; aqui o stderr e so texto (o resultado vem do codigo de saida e da linha BACKUP OK).
    $acaoAnterior = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $saida = (& (Join-Path $App 'atual\bin\painel.bat') backup-agora 2>&1 | Out-String)
        $codigo = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $acaoAnterior
        $env:PAINEL_ENV_FILE = $guardada
    }
    if ($saida -match '(?m)^BACKUP OK (\S+) ') { return $Matches[1] }
    throw "o backup nao foi feito (codigo $codigo)"
}

# Fumaca minima depois de iniciar: a saude responde e /api/meta informa a versao esperada; se existir fumaca.ps1 ao lado
# (Tarefa 11), ela tambem roda.
function Conferir-Fumaca {
    param([Parameter(Mandatory = $true)][string]$Url, [Parameter(Mandatory = $true)][string]$Versao)
    if (-not (Esperar-Saude -Url $Url -TimeoutSeg 30)) { return $false }
    if ((Baixar-Texto "$Url/api/meta") -notmatch ('"versao":\s*"' + [regex]::Escape($Versao) + '"')) {
        [Console]::Error.WriteLine("a versao publicada em /api/meta nao e $Versao")
        return $false
    }
    $fumaca = Join-Path $PSScriptRoot 'fumaca.ps1'
    if (Test-Path -LiteralPath $fumaca) {
        & $fumaca -UrlBase $Url -VersaoEsperada $Versao
        if (-not $?) { return $false }
    }
    return $true
}
