# Hook PreToolUse do Cline/ClineSR no Windows 11 para o Painel de Squads.
#
# No Windows o Cline só executa `.clinerules/hooks/PreToolUse.ps1` (o arquivo sem extensão é ignorado).
# Ele o chama assim (código da extensão, apps/vscode/src/core/hooks/HookProcess.ts, branch main):
#   <pwsh ou powershell.exe> -NoProfile -NonInteractive -ExecutionPolicy Bypass -File PreToolUse.ps1
# com o JSON da ferramenta no stdin e lendo a resposta JSON do stdout (tempo máximo padrão: 30 s).
# Prefere o PowerShell 7 (pwsh) e cai para o Windows PowerShell 5.1; este script funciona nos dois.
#
# Este arquivo é salvo em UTF-8 COM BOM: sem o BOM o PowerShell 5.1 lê o arquivo como ANSI e
# letras acentuadas viram outros caracteres (alguns viram aspas e quebram o script). Não remova o BOM.
#
# Diferença para o hook do macOS/Linux (`PreToolUse`, bash): lá o adaptador chama o `.claude/hooks/guard.sh`.
# Aqui não há `sh` nem `jq` garantidos, então as regras do guard.sh foram REESCRITAS em PowerShell.
# São duas cópias: ao mudar o guard.sh, atualize também este arquivo e o `test-PreToolUse.ps1` (os casos
# espelham o `.claude/hooks/test-guard.sh`). Verificado contra o guard.sh da Tarefa 11 do Plano 4A (26/10/2026).
#
# Entrada aceita (JSON no stdin):
#   - extensão VS Code: .preToolUse.toolName + .preToolUse.parameters (valores podem vir como texto JSON)
#   - CLI/SDK do Cline: .tool_call.name + .tool_call.input
# Saída (stdout, UTF-8 sem BOM): {"cancel": bool, "contextModification": "", "errorMessage": "..."}; sempre exit 0.
#
# Flag JIRA_LIVE_ENABLED, lida do ambiente do processo do Cline (NÃO do app\.env nem do comando):
#   - ausente, false ou inválida -> trava do Jira ATIVA: comandos de rede, variáveis JIRA_*, URLs com
#     jira/atlassian, MCP do Atlassian e navegador/web_fetch para URLs com jira/atlassian são bloqueados;
#   - true (sem diferenciar maiúsculas, como o Settings.kt do app) -> nada disso é bloqueado, exceto
#     exibir token ou senha do Jira no terminal (Plano 4A, Decisão 10).
# Valem SEMPRE, com qualquer valor da flag: escrever, versionar ou LER `.env` e `config/{equipes,ausencias,
# papeis,squads}.yaml` (os modelos `.env.example`, `.env.sample` e `*.exemplo.yaml` ficam liberados),
# `git add -f` e listar todas as variáveis de ambiente (expõe tokens). As configs reais e o
# Filtros_Status_tool.txt* são protegidos pelo NOME, em qualquer pasta.
#
# Busca e listagem (search_files, list_files, list_code_definition_names e equivalentes da CLI): o caminho
# passa por Caminho-Protegido; a busca de conteúdo só é liberada com escopo seguro (Padrao-Seguro), porque
# o resultado traz linhas dos arquivos (.env e configs reais iriam para o modelo). Regra em hooks.md.
#
# Normalização de caminhos (ler, escrever e buscar), só com operações de texto (o pwsh no macOS/Linux não
# trata `\` como separador em [IO.Path]): minúsculas, `\` -> `/`, sem prefixo de dispositivo (\\?\, \\.\,
# \??\), sem letra de unidade, qualquer `:` restante bloqueia (fluxo alternativo `.env::$DATA`), `..`
# resolvido, pontos e espaços do fim de cada segmento removidos (`.env.` = `.env`) e segmento com nome
# curto 8.3 (`ENV~1`) bloqueia.
#
# Limite honesto (igual ao guard.sh): trava contra acidente do assistente, não contra má-fé.
# Falha fechada: JSON ilegível ou qualquer erro interno cancela a ferramenta.

$ErrorActionPreference = 'Stop'

# ---------- saída ----------
function Responder([bool]$cancelar, [string]$mensagem) {
    $obj = [ordered]@{ cancel = $cancelar; contextModification = ''; errorMessage = $mensagem }
    $json = $obj | ConvertTo-Json -Compress
    $bytes = (New-Object System.Text.UTF8Encoding($false)).GetBytes($json + "`n")
    $saida = [Console]::OpenStandardOutput()
    $saida.Write($bytes, 0, $bytes.Length)
    $saida.Flush()
    exit 0
}
function Bloquear([string]$mensagem) { Responder $true $mensagem }
function Liberar { Responder $false '' }

# ---------- utilitários ----------
$OPC_I = $null
$OPC_C = $null

function Casa([string]$texto, [string]$padrao) {
    # sem diferenciar maiúsculas (caminhos e comandos do Windows não diferenciam)
    if ($null -eq $texto) { return $false }
    return [regex]::IsMatch($texto, $padrao, $OPC_I)
}

function Ler-Prop($obj, [string]$nome) {
    # lê uma propriedade sem quebrar quando ela não existe
    if ($null -eq $obj) { return $null }
    if ($obj -is [System.Management.Automation.PSCustomObject]) {
        $p = $obj.PSObject.Properties[$nome]
        if ($null -ne $p) { return $p.Value }
    }
    return $null
}

function Desfazer-Json($valor) {
    # valores que vieram como texto JSON ('{"command":"..."}') viram objeto; o resto fica como está
    if ($valor -is [string]) {
        $t = $valor.Trim()
        if ($t.StartsWith('{') -or $t.StartsWith('[')) {
            try { return ($t | ConvertFrom-Json) } catch { return $valor }
        }
    }
    return $valor
}

function Coletar-Textos($valor, $lista, [int]$profundidade) {
    # todos os textos de um valor (objeto, lista ou texto), até 8 níveis
    if ($null -eq $valor -or $profundidade -gt 8) { return }
    $valor = Desfazer-Json $valor
    if ($valor -is [string]) { [void]$lista.Add($valor); return }
    if ($valor -is [System.Management.Automation.PSCustomObject]) {
        foreach ($p in $valor.PSObject.Properties) { Coletar-Textos $p.Value $lista ($profundidade + 1) }
        return
    }
    if ($valor -is [System.Collections.IEnumerable]) {
        foreach ($item in $valor) { Coletar-Textos $item $lista ($profundidade + 1) }
        return
    }
    [void]$lista.Add([string]$valor)
}

# ---------- regras (espelham o .claude/hooks/guard.sh, mais os extras do Cline) ----------
$PADRAO_URL_JIRA = 'https?://[^\s"'']*(jira|atlassian)'
$PADRAO_REDE_CLASSICA = '(^|[^A-Za-z0-9_-])(curl|wget|http|https|httpie|xh|nc|ncat|telnet|ssh|ftp|openssl)(\.exe)?(\s|$)'
# extras do Windows/PowerShell (não existem no guard.sh)
$PADRAO_REDE_WINDOWS = '\b(invoke-webrequest|invoke-restmethod|iwr|irm|start-bitstransfer|bitsadmin|certutil|test-netconnection|tnc|resolve-dnsname|nslookup|ping|webclient|httpclient|xmlhttp|winhttp|start-process)\b'
$PADRAO_LIGAR_FLAG = 'jiradash\s+(check|sync|serve)|JIRA_LIVE_ENABLED\s*=\s*["'']?\s*(1|true)|JIRA_LIVE_ENABLED["''\s,:=]+(-value\s+)?["'']?\s*(1|true)'
$PADRAO_TOKEN = 'JIRA_[A-Za-z0-9_]*(TOKEN|PASS|SENHA|SECRET|PAT)'
$PADRAO_EXIBIR = '(^|[^A-Za-z0-9_-])(echo|printf|printenv|env|set|declare|typeset|export|cat|type|write-output|write-host|write-information|out-host|out-string|get-childitem|gci|dir|ls|get-item|gi|get-content|gc)(\s|$)'
# listar todo o ambiente mostra os tokens: barrado com qualquer valor da flag
$PADRAO_LISTAR_AMBIENTE = '(^\s*|[;&|(]\s*|cmd(\.exe)?\s+/c\s+)(env|printenv|set|export\s+-p|declare\s+-[px]|compgen\s+-v)\s*($|[;&|>)])|\b(get-childitem|gci|dir|ls|get-item|gi)\s+(-path\s+)?env:[\\/]?\*?\s*($|[;&|>)])|\[(system\.)?environment\]::getenvironmentvariables'

# protegidos em comandos (igual ao PreToolUse do bash): .env e .env.*, as configs reais em qualquer pasta,
# o arquivo de filtros e os nomes curtos 8.3 deles (ENV~1, PAPEIS~1.YAM...; prefixos específicos para não
# pegar HEAD~1). Terminadores incluem ':' (.env::$DATA) e '.' (.env. = .env no Windows).
$PADRAO_PROTEGIDOS = '(^|[\s"''=/\\:])(\.env(\.[A-Za-z0-9_-]+)?|(equipes|ausencias|papeis|squads)\.ya?ml|(env|equipe|ausenc|papeis|squads|filtro)[A-Za-z0-9_]{0,6}~[0-9]+[^\s"'';|&)]*)([\s"'';|&):.]|$)|filtros_status_tool\.txt'
# extensões seguras para buscas de conteúdo (ver Padrao-Seguro e hooks.md)
$PADRAO_EXT_SEGURA = '\.(kt|kts|java|gradle|ts|tsx|js|jsx|mjs|cjs|json|css|scss|html|md|sql|py|sh|ps1|xml|svg|toml|exemplo\.ya?ml|demo\.ya?ml|example|sample)$'
# nome protegido citado num texto qualquer (regex de busca); modelos .env.example/.env.sample retirados antes
$PADRAO_NOME_PROTEGIDO = '(^|[^a-z0-9_])\.env(\.[a-z0-9_*-]+)?([^a-z0-9_]|$)|(equipes|ausencias|papeis|squads)\.ya?ml|filtros_status_tool'
$MSG_BUSCA = "Forma permitida (documentada em docs/ferramentas-claude-para-o-cline/hooks.md, não é contorno): refazer a busca com file_pattern restrito a extensões seguras (ex.: '*.kt', '*.tsx', '*.md', '*.exemplo.yaml') ou a um nome de arquivo exato com extensão segura (ou Dockerfile, Makefile, gradlew, gradlew.bat, README, LICENSE, .gitignore, .gitattributes, .editorconfig); nome sem extensão segura, como 'config' ou 'app', pode ser uma pasta e é bloqueado. Qualquer outra busca: pergunte ao usuário, só ele autoriza (rodando a busca fora do Cline)."
$PADRAO_MODELOS = '(^|[\s"''=/\\])\.env\.(example|sample)([\s"'';|&)]|$)'
$PADRAO_ESCRITA = '(>|>>|tee\s|sed\s+(-[a-z]*i|--in-place)|(^|[\s;&|(])(cp|mv|rm|ln|truncate|dd|install|chmod|set-content|sc|add-content|ac|out-file|copy-item|copy|cpi|move-item|move|mi|remove-item|del|erase|ri|rd|rmdir|new-item|ni|rename-item|ren|rni|clear-content|clc|tee-object|xcopy|robocopy|icacls|attrib)\s|git\s+(add|commit|stash|checkout|restore|rm|mv))'
# leitura (extra do Cline: o texto lido iria para o modelo de linguagem)
$PADRAO_LEITURA = '(^|[\s;&|(])(cat|type|get-content|gc|more|less|head|tail|select-string|sls|findstr|grep|egrep|rg|bat|nl|od|xxd|strings|source|import-csv|format-hex|notepad|code)(\.exe)?\s|(^|[;&|]\s*)\.\s+\S*\.env'
$PADRAO_GIT_ADD_FORCADO = 'git\s+add\s([^;&|]*\s)?(-[A-Za-z]*f[A-Za-z]*|--fo[a-z]*)([\s=]|$)'

function Regra-Jira([string]$cmd) {
    # 1) variáveis de conexão do Jira (exceto a própria flag). Duas leituras:
    #    igual ao guard.sh (maiúsculas exatas) e sem diferenciar maiúsculas (no Windows $env:jira_token funciona)
    foreach ($m in [regex]::Matches($cmd, 'JIRA_[A-Za-z0-9_]+', $OPC_C)) {
        if (-not [string]::Equals($m.Value, 'JIRA_LIVE_ENABLED', [System.StringComparison]::Ordinal)) {
            return 'comando usa variáveis de conexão do Jira (JIRA_*) com JIRA_LIVE_ENABLED diferente de true.'
        }
    }
    foreach ($m in [regex]::Matches($cmd, '(?<![A-Za-z0-9_])JIRA_[A-Za-z0-9_]+', $OPC_I)) {
        if (-not [string]::Equals($m.Value, 'JIRA_LIVE_ENABLED', [System.StringComparison]::OrdinalIgnoreCase)) {
            return 'comando usa variáveis de conexão do Jira (JIRA_*) com JIRA_LIVE_ENABLED diferente de true.'
        }
    }
    # 2) qualquer URL que cite Jira/Atlassian
    if (Casa $cmd $PADRAO_URL_JIRA) { return 'comando referencia um endereço com jira/atlassian e JIRA_LIVE_ENABLED não é true.' }
    # 3) ferramenta de rede junto da palavra Jira/Atlassian
    if (((Casa $cmd $PADRAO_REDE_CLASSICA) -or (Casa $cmd $PADRAO_REDE_WINDOWS)) -and (Casa $cmd 'jira|atlassian')) {
        return 'comando de rede para o Jira com JIRA_LIVE_ENABLED diferente de true.'
    }
    # 4) comandos do app que conectam ao Jira real ou ligam a flag
    if (Casa $cmd $PADRAO_LIGAR_FLAG) { return 'comando liga JIRA_LIVE_ENABLED ou conecta ao Jira real. Só o usuário muda a flag.' }
    return $null
}

function Regra-Token([string]$cmd) {
    # Plano 4A, Decisão 10: exibir token ou senha do Jira é barrado mesmo com a flag true
    $citaToken = [regex]::IsMatch($cmd, $PADRAO_TOKEN, $OPC_I)
    if (-not $citaToken) { return $null }
    if (Casa $cmd $PADRAO_EXIBIR) { return 'comando exibe token ou senha do Jira (vale mesmo com a flag true).' }
    # no PowerShell, a variável sozinha já imprime o valor ($env:JIRA_TOKEN)
    if (Casa $cmd ('^\s*["'']?\$(env:)?' + $PADRAO_TOKEN + '[A-Za-z0-9_]*["'']?\s*$')) {
        return 'comando exibe token ou senha do Jira (vale mesmo com a flag true).'
    }
    return $null
}

function Regra-Ambiente([string]$cmd) {
    if (Casa $cmd $PADRAO_LISTAR_AMBIENTE) { return 'comando lista todas as variáveis de ambiente (expõe tokens).' }
    return $null
}

function Caminho-Protegido([string]$caminho) {
    # devolve o motivo (texto) se o caminho for protegido, ou $null se liberado. Ler, escrever e buscar.
    if ([string]::IsNullOrWhiteSpace($caminho)) { return $null }
    if ($caminho -match "[`r`n]") { return 'caminho com quebra de linha.' }
    # minúsculas, barras normais, sem prefixo de dispositivo (\\?\UNC\, \\?\, \\.\, \??\) e sem letra de unidade
    $n = $caminho.ToLowerInvariant().Replace('\', '/')
    $n = [regex]::Replace($n, '^//[?.]/unc/', '//')
    $n = [regex]::Replace($n, '^(//[?.]/|/[?][?]/)', '')
    $n = [regex]::Replace($n, '^[a-z]:', '')
    # ':' depois da unidade: fluxo alternativo do NTFS (.env::$DATA) ou caminho de dispositivo
    if ($n.Contains(':')) { return "caminho com ':' (fluxo alternativo do NTFS, como .env::`$DATA, ou dispositivo): $caminho." }
    $partes = New-Object System.Collections.ArrayList
    foreach ($seg in $n.Split('/')) {
        # espaços nas pontas; '.' e vazio somem; '..' volta um nível
        $s = $seg.Trim()
        if ($s -eq '' -or $s -eq '.') { continue }
        if ($s -eq '..') {
            if ($partes.Count -gt 0) { $partes.RemoveAt($partes.Count - 1) }
            continue
        }
        # o Windows ignora pontos e espaços no fim do nome (.env. e ".env " = .env)
        $s = $s.TrimEnd([char[]]@('.', ' ', "`t"))
        if ($s -eq '') { continue }
        [void]$partes.Add($s)
    }
    if ($partes.Count -eq 0) { return $null }
    $base = [string]$partes[$partes.Count - 1]
    # nome curto 8.3 só importa no último segmento (a proteção é pelo nome do arquivo; pastas como
    # C:\Users\THIAGO~1\... continuam liberadas)
    if ($base -match '~[0-9]') { return "nome curto 8.3 ($base) pode apontar para .env ou config real." }
    if (@('.env.example', '.env.sample') -contains $base) { return $null }
    if ($base -match '^\.env(\..*)?$') { return "arquivo de ambiente ($base): contém segredos." }
    if ($base -match '^(equipes|ausencias|papeis|squads)\.ya?ml$') {
        return "config com dados pessoais reais ($base). Use só os modelos .exemplo/.demo."
    }
    if ($base -match '^filtros_status_tool\.txt') { return "arquivo de filtros do Jira ($base)." }
    return $null
}

function Nome-Protegido([string]$texto) {
    # texto (regex de busca) que cita nome protegido
    if ([string]::IsNullOrEmpty($texto)) { return $false }
    $t = $texto.ToLowerInvariant().Replace('\', '')
    $t = [regex]::Replace($t, '\.env\.(example|sample)', '')
    return [regex]::IsMatch($t, $PADRAO_NOME_PROTEGIDO)
}

function Padrao-Seguro([string]$padrao) {
    # file_pattern de busca seguro? Aceita: nome exato não protegido, ou glob terminado numa extensão
    # segura ($PADRAO_EXT_SEGURA), com no máximo um grupo {a,b}. Negação (!), ':' e o resto: inseguros.
    $p = $padrao.Replace('\', '/').ToLowerInvariant()
    if ($p -eq '' -or $p.StartsWith('!') -or $p.Contains(':')) { return $false }
    $alts = New-Object System.Collections.ArrayList
    if ($p -match '[{}]') {
        $i = $p.IndexOf('{')
        $j = $p.IndexOf('}')
        if ($i -lt 0 -or $j -lt $i) { return $false }
        $pre = $p.Substring(0, $i)
        $meio = $p.Substring($i + 1, $j - $i - 1)
        $pos = $p.Substring($j + 1)
        # um único grupo, sem aninhamento
        if (($pre + $meio + $pos) -match '[{}]') { return $false }
        # alternativa vazia expande para o resto do glob (*{.kt,} = *.kt OU *, que casa .env): bloqueia.
        # O Split do .NET preserva vazios (o "*" cairia na checagem de extensão), mas a regra explícita
        # mantém o mesmo comportamento do PreToolUse do bash, cujo `read` descarta vazios.
        if ($meio -eq '' -or $meio.StartsWith(',') -or $meio.EndsWith(',') -or $meio.Contains(',,')) { return $false }
        foreach ($a in $meio.Split(',')) { [void]$alts.Add($pre + $a + $pos) }
    }
    else { [void]$alts.Add($p) }
    if ($alts.Count -eq 0) { return $false }
    foreach ($alt in $alts) {
        $base = ($alt -split '/')[-1]
        if ([string]::IsNullOrEmpty($base)) { return $false }
        if ($base -match '[*?\[\]]') {
            # glob: precisa terminar numa extensão segura e não citar nome protegido (.env*.md...)
            if (-not [regex]::IsMatch($base, $PADRAO_EXT_SEGURA)) { return $false }
            if (Nome-Protegido $base) { return $false }
        }
        else {
            if (Caminho-Protegido $alt) { return $false }
            # sem curinga: só com extensão segura ou na lista curta de arquivos conhecidos. Um nome solto
            # (config, app, secrets) pode ser de PASTA, e o ripgrep buscaria tudo dentro dela.
            if (-not [regex]::IsMatch($base, $PADRAO_EXT_SEGURA) -and
                -not (@('dockerfile', 'makefile', 'gradlew', 'gradlew.bat', 'readme', 'license', '.gitignore', '.gitattributes', '.editorconfig') -contains $base)) {
                return $false
            }
        }
    }
    return $true
}

function Primeiro-Texto($obj, [string[]]$campos) {
    foreach ($c in $campos) {
        $v = Ler-Prop $obj $c
        if ($v -is [string] -and $v -ne '') { return $v }
    }
    return ''
}

function Checar-Busca([string]$ferramenta, $parametros) {
    # busca de conteúdo (search_files e equivalentes): devolve o motivo do bloqueio ou $null se liberada
    $caminho = ''
    $padrao = ''
    $regex = ''
    if ($parametros -is [System.Management.Automation.PSCustomObject]) {
        $caminho = Primeiro-Texto $parametros @('path', 'directory', 'cwd')
        $padrao = Primeiro-Texto $parametros @('file_pattern', 'filePattern', 'glob', 'include')
        $textos = New-Object System.Collections.ArrayList
        foreach ($c in @('regex', 'query', 'pattern', 'queries')) { Coletar-Textos (Ler-Prop $parametros $c) $textos 0 }
        $regex = $textos -join ' '
    }
    elseif ($parametros -is [string]) { $regex = $parametros }
    if ($caminho -ne '') {
        $m = Caminho-Protegido $caminho
        if ($m) { return "$ferramenta em caminho protegido ($caminho): $m" }
    }
    if ($regex -ne '' -and (Nome-Protegido $regex)) {
        return "$ferramenta com regex que cita .env, config real ou Filtros_Status_tool.txt (o resultado mostraria esses arquivos). Pergunte ao usuário."
    }
    if ($padrao -ne '') {
        if (Padrao-Seguro $padrao) { return $null }
        return "$ferramenta com file_pattern '$padrao', que pode incluir .env ou configs reais (papeis/equipes/ausencias/squads.yaml, Filtros_Status_tool.txt). $MSG_BUSCA"
    }
    # sem file_pattern: só um arquivo único com extensão segura
    if ($caminho -ne '') {
        $base = ($caminho.Replace('\', '/') -split '/')[-1].ToLowerInvariant()
        if ([regex]::IsMatch($base, $PADRAO_EXT_SEGURA) -and -not (Nome-Protegido $base)) { return $null }
    }
    $onde = $caminho
    if ($onde -eq '') { $onde = '.' }
    return "$ferramenta sem file_pattern em '$onde': a busca abrangeria .env e configs reais e mostraria o conteúdo ao modelo. $MSG_BUSCA"
}

function Regra-Arquivos([string]$cmd) {
    # tokens exatamente iguais aos modelos não contam como protegidos
    $semModelos = [regex]::Replace($cmd, $PADRAO_MODELOS, '$1 $3', $OPC_I)
    if (Casa $semModelos $PADRAO_PROTEGIDOS) {
        if (Casa $semModelos $PADRAO_ESCRITA) { return 'comando altera ou versiona segredo/config com dados reais.' }
        if (Casa $semModelos $PADRAO_LEITURA) { return 'comando lê segredo/config com dados reais (o conteúdo iria para o modelo).' }
    }
    # settings do Claude e override do compose: escrever por comando é barrado (ler continua liberado)
    $cmdBarras = $cmd -replace '\\', '/'
    if ((Casa $cmdBarras '\.claude/settings[^\s"'';|&)]*\.json|compose[^\s"'';|&)]*override') -and (Casa $cmd $PADRAO_ESCRITA)) {
        return 'comando escreve em settings do Claude ou override do compose: só o usuário altera esse arquivo.'
    }
    $semAspas = $cmd -replace '["'']', ''
    if (Casa $semAspas $PADRAO_GIT_ADD_FORCADO) { return 'git add forçado ignora o .gitignore (segredos e configs reais).' }
    return $null
}

# ---------- principal ----------
try {
    # dentro do try: qualquer falha aqui também cai na falha fechada
    $OPC_I = [System.Text.RegularExpressions.RegexOptions]::IgnoreCase
    $OPC_C = [System.Text.RegularExpressions.RegexOptions]::None
    $entradaPadrao = [Console]::OpenStandardInput()
    $memoria = New-Object System.IO.MemoryStream
    $entradaPadrao.CopyTo($memoria)
    $texto = (New-Object System.Text.UTF8Encoding($false)).GetString($memoria.ToArray())
    $texto = $texto.TrimStart([char]0xFEFF).Trim()
    if ([string]::IsNullOrWhiteSpace($texto)) { Bloquear 'BLOQUEADO: entrada do hook vazia (falha fechada).' }

    $entrada = $null
    try { $entrada = $texto | ConvertFrom-Json } catch { $entrada = $null }
    if ($entrada -isnot [System.Management.Automation.PSCustomObject]) {
        Bloquear 'BLOQUEADO: entrada do hook não é JSON válido (falha fechada).'
    }

    $chamada = Ler-Prop $entrada 'tool_call'
    $pre = Ler-Prop $entrada 'preToolUse'
    $ferramenta = [string](Ler-Prop $chamada 'name')
    if ([string]::IsNullOrEmpty($ferramenta)) { $ferramenta = [string](Ler-Prop $pre 'toolName') }
    if ([string]::IsNullOrEmpty($ferramenta)) { Bloquear 'BLOQUEADO: entrada sem nome de ferramenta reconhecível (falha fechada).' }
    $parametros = Ler-Prop $chamada 'input'
    if ($null -eq $parametros) { $parametros = Ler-Prop $pre 'parameters' }
    $parametros = Desfazer-Json $parametros

    # mesma leitura do guard.sh e do Settings.kt: espaços só nas pontas ("tr ue" não vira true)
    $flag = ([string]$env:JIRA_LIVE_ENABLED).Trim()
    $jiraLivre = [string]::Equals($flag, 'true', [System.StringComparison]::OrdinalIgnoreCase)

    switch -Regex ($ferramenta) {
        '^(execute_command|run_commands)$' {
            # um ou vários comandos; cada um passa por todas as regras
            $comandos = New-Object System.Collections.ArrayList
            $brutos = @()
            if ($parametros -is [string]) { $brutos = @($parametros) }
            elseif ($parametros -is [System.Management.Automation.PSCustomObject]) {
                $v = Ler-Prop $parametros 'command'
                if ($null -eq $v) { $v = Ler-Prop $parametros 'commands' }
                if ($null -eq $v) { $v = Ler-Prop $parametros 'cmd' }
                $v = Desfazer-Json $v
                if ($null -ne $v) { $brutos = @($v) }
            }
            elseif ($parametros -is [System.Collections.IEnumerable]) { $brutos = @($parametros) }
            foreach ($b in $brutos) {
                $b = Desfazer-Json $b
                if ($b -is [string]) { [void]$comandos.Add($b) }
                elseif ($b -is [System.Management.Automation.PSCustomObject]) {
                    $partes = New-Object System.Collections.ArrayList
                    $c = Ler-Prop $b 'command'
                    if ($null -ne $c) { [void]$partes.Add([string]$c) }
                    foreach ($a in @(Desfazer-Json (Ler-Prop $b 'args'))) { if ($null -ne $a) { [void]$partes.Add([string]$a) } }
                    [void]$comandos.Add(($partes -join ' '))
                }
            }
            foreach ($cmd in $comandos) {
                if ([string]::IsNullOrWhiteSpace($cmd)) { continue }
                $m = $null
                if (-not $jiraLivre) { $m = Regra-Jira $cmd }
                if (-not $m) { $m = Regra-Token $cmd }
                if (-not $m) { $m = Regra-Ambiente $cmd }
                if (-not $m) { $m = Regra-Arquivos $cmd }
                if ($m) { Bloquear "BLOQUEADO: $m" }
            }
            Liberar
        }
        '^(use_mcp_tool|access_mcp_resource|load_mcp_documentation)$' {
            if (-not $jiraLivre) {
                $alvo = @((Ler-Prop $parametros 'server_name'), (Ler-Prop $parametros 'tool_name'), (Ler-Prop $parametros 'uri')) -join ' '
                if (Casa $alvo 'atlassian|jira') {
                    Bloquear "BLOQUEADO: ferramenta MCP do Atlassian/Jira ($($alvo.Trim())) com JIRA_LIVE_ENABLED diferente de true."
                }
                # argumentos com URL do Jira (ex.: servidor MCP de 'fetch')
                $textos = New-Object System.Collections.ArrayList
                Coletar-Textos (Ler-Prop $parametros 'arguments') $textos 0
                foreach ($t in $textos) {
                    if (Casa $t $PADRAO_URL_JIRA) { Bloquear 'BLOQUEADO: ferramenta MCP com URL do Jira/Atlassian e JIRA_LIVE_ENABLED diferente de true.' }
                }
            }
            Liberar
        }
        '^(browser_action|web_fetch)$' {
            # decisão do usuário (25/10): com a flag diferente de true, nenhuma URL com jira/atlassian,
            # nem a documentação pública do Jira (o usuário cola trechos ou usa web_search)
            if (-not $jiraLivre) {
                foreach ($campo in @('url', 'uri', 'href', 'link')) {
                    $u = [string](Ler-Prop $parametros $campo)
                    if (Casa $u 'jira|atlassian') {
                        Bloquear "BLOQUEADO: $ferramenta para URL com jira/atlassian e JIRA_LIVE_ENABLED diferente de true. Para documentação pública do Jira, peça ao usuário um trecho ou use web_search."
                    }
                }
                $textos = New-Object System.Collections.ArrayList
                Coletar-Textos $parametros $textos 0
                foreach ($t in $textos) {
                    if (Casa $t $PADRAO_URL_JIRA) {
                        Bloquear "BLOQUEADO: $ferramenta com URL que cita jira/atlassian e JIRA_LIVE_ENABLED diferente de true."
                    }
                }
            }
            Liberar
        }
        '^(read_file|read_many_files|write_to_file|replace_in_file|editor|new_rule)$' {
            $lidos = New-Object System.Collections.ArrayList
            if ($parametros -is [string]) { [void]$lidos.Add($parametros) }
            else {
                foreach ($campo in @('path', 'file_path', 'filePath', 'paths')) { Coletar-Textos (Ler-Prop $parametros $campo) $lidos 0 }
            }
            foreach ($p in $lidos) {
                $m = Caminho-Protegido ([string]$p)
                if ($m) {
                    if ($ferramenta -match '^read_') { Bloquear "BLOQUEADO: leitura de arquivo protegido ($p): $m" }
                    Bloquear "BLOQUEADO: escrita em arquivo protegido ($p): $m"
                }
                if ($ferramenta -notmatch '^read_') {
                    # settings do Claude e override do compose ligam a flag: só o usuário escreve neles
                    $pn = (([string]$p) -replace '\\', '/').ToLowerInvariant().TrimEnd('.', ' ')
                    if (($pn -like '*compose*override*') -or ($pn -like '.claude/settings*.json') -or ($pn -like '*/.claude/settings*.json')) {
                        Bloquear "BLOQUEADO: escrita em ${p}: só o usuário altera esse arquivo (settings do Claude e override do compose podem ligar JIRA_LIVE_ENABLED)."
                    }
                }
            }
            Liberar
        }
        '^(search_files|grep|grep_search|codebase_search|search_codebase)$' {
            # busca de conteúdo: o resultado traz linhas dos arquivos (regra em hooks.md)
            $m = Checar-Busca $ferramenta $parametros
            if ($m) { Bloquear "BLOQUEADO: $m" }
            Liberar
        }
        '^(list_files|list_code_definition_names|glob|list_dir|list_directory)$' {
            # listagem: só nomes (list_code_definition_names só lê código-fonte); o caminho não pode ser protegido
            $alvos = New-Object System.Collections.ArrayList
            if ($parametros -is [string]) { [void]$alvos.Add($parametros) }
            else {
                foreach ($campo in @('path', 'directory', 'cwd', 'pattern', 'glob')) { Coletar-Textos (Ler-Prop $parametros $campo) $alvos 0 }
            }
            foreach ($p in $alvos) {
                $m = Caminho-Protegido ([string]$p)
                if ($m) { Bloquear "BLOQUEADO: $ferramenta em caminho protegido ($p): $m" }
            }
            Liberar
        }
        '^apply_patch$' {
            # caminhos nos cabeçalhos do patch
            $patch = ''
            if ($parametros -is [string]) { $patch = $parametros }
            else {
                $patch = Ler-Prop $parametros 'input'
                if ($null -eq $patch) { $patch = Ler-Prop $parametros 'patch' }
                $patch = [string]$patch
            }
            foreach ($linha in ($patch -split "`r?`n")) {
                $mm = [regex]::Match($linha, '^\*\*\* (Add File|Update File|Delete File|Move to): (.+)$')
                if ($mm.Success) {
                    $m = Caminho-Protegido $mm.Groups[2].Value
                    if ($m) { Bloquear "BLOQUEADO: escrita em arquivo protegido ($($mm.Groups[2].Value)): $m" }
                    $pn = ($mm.Groups[2].Value -replace '\\', '/').ToLowerInvariant().TrimEnd('.', ' ')
                    if (($pn -like '*compose*override*') -or ($pn -like '.claude/settings*.json') -or ($pn -like '*/.claude/settings*.json')) {
                        Bloquear "BLOQUEADO: escrita em $($mm.Groups[2].Value): só o usuário altera esse arquivo (settings do Claude e override do compose podem ligar JIRA_LIVE_ENABLED)."
                    }
                }
            }
            Liberar
        }
        default {
            # web_search e demais ferramentas: liberadas
            Liberar
        }
    }
    Liberar
}
catch {
    # falha fechada: qualquer erro inesperado cancela a ferramenta
    $detalhe = $_.Exception.Message
    try { Bloquear "BLOQUEADO: erro interno do hook (falha fechada): $detalhe" }
    catch {
        # último recurso: JSON fixo em ASCII, sem depender de .NET (funciona até em modo de linguagem restrito)
        Write-Output '{"cancel":true,"contextModification":"","errorMessage":"BLOQUEADO: erro interno do hook (falha fechada)."}'
        exit 0
    }
}
