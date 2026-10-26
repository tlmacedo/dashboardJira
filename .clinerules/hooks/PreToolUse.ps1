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
# São duas cópias: ao mudar o guard.sh (por exemplo, Plano 4A, Tarefa 11), atualize também este arquivo
# e o `test-PreToolUse.ps1` (os casos espelham o `.claude/hooks/test-guard.sh`).
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
# `git add -f` e listar todas as variáveis de ambiente (expõe tokens).
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
$PADRAO_LIGAR_FLAG = 'jiradash\s+(check|sync|serve)|JIRA_LIVE_ENABLED\s*=\s*["'']?\s*(1|true)|JIRA_LIVE_ENABLED["''\s,]+(-value\s+)?["'']?\s*(1|true)'
$PADRAO_TOKEN = 'JIRA_[A-Za-z0-9_]*(TOKEN|PASS|SENHA|SECRET|PAT)'
$PADRAO_EXIBIR = '(^|[^A-Za-z0-9_-])(echo|printf|printenv|env|set|declare|typeset|export|cat|type|write-output|write-host|write-information|out-host|out-string|get-childitem|gci|dir|ls|get-item|gi|get-content|gc)(\s|$)'
# listar todo o ambiente mostra os tokens: barrado com qualquer valor da flag
$PADRAO_LISTAR_AMBIENTE = '(^\s*|[;&|(]\s*|cmd(\.exe)?\s+/c\s+)(env|printenv|set|export\s+-p|declare\s+-[px]|compgen\s+-v)\s*($|[;&|>)])|\b(get-childitem|gci|dir|ls|get-item|gi)\s+(-path\s+)?env:[\\/]?\*?\s*($|[;&|>)])|\[(system\.)?environment\]::getenvironmentvariables'

# caminhos protegidos (com ou sem barra inicial, barra normal ou invertida)
$PADRAO_PROTEGIDOS = '(^|[\s"''=/\\:])(\.env(\.[A-Za-z0-9_-]+)?|config[/\\](equipes|ausencias|papeis|squads)\.yaml)([\s"'';|&)]|$)'
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
    if ([string]::IsNullOrWhiteSpace($caminho)) { return $null }
    $norm = $caminho.Trim() -replace '\\', '/'
    $base = ($norm -split '/')[-1]
    if ((Casa $base '^\.env(\..*)?$') -and -not (@('.env.example', '.env.sample') -contains $base.ToLowerInvariant())) {
        return "arquivo de ambiente ($base): contém segredos."
    }
    if (Casa $norm '(^|/)config/(equipes|ausencias|papeis|squads)\.yaml$') {
        return "config com dados pessoais reais ($base). Use só os modelos .exemplo/.demo."
    }
    return $null
}

function Regra-Arquivos([string]$cmd) {
    # tokens exatamente iguais aos modelos não contam como protegidos
    $semModelos = [regex]::Replace($cmd, $PADRAO_MODELOS, '$1 $3', $OPC_I)
    if (Casa $semModelos $PADRAO_PROTEGIDOS) {
        if (Casa $semModelos $PADRAO_ESCRITA) { return 'comando altera ou versiona segredo/config com dados reais.' }
        if (Casa $semModelos $PADRAO_LEITURA) { return 'comando lê segredo/config com dados reais (o conteúdo iria para o modelo).' }
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
    $parametros = Ler-Prop $chamada 'input'
    if ($null -eq $parametros) { $parametros = Ler-Prop $pre 'parameters' }
    $parametros = Desfazer-Json $parametros

    $flag = ([string]$env:JIRA_LIVE_ENABLED) -replace '\s', ''
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
        '^(read_file|write_to_file|replace_in_file|editor|new_rule)$' {
            foreach ($campo in @('path', 'file_path', 'filePath')) {
                $m = Caminho-Protegido ([string](Ler-Prop $parametros $campo))
                if ($m) {
                    if ($ferramenta -eq 'read_file') { Bloquear "BLOQUEADO: leitura de $m" }
                    Bloquear "BLOQUEADO: edição de $m"
                }
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
                    if ($m) { Bloquear "BLOQUEADO: edição de $m" }
                }
            }
            Liberar
        }
        default {
            # busca (search_files, web_search), listagem e demais ferramentas: liberadas
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
