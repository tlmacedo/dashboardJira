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
# Flag JIRA_LIVE_ENABLED (Plano 14, Tarefa 0): lida do arquivo app\.env da raiz do repositório (raiz deduzida
# do caminho deste script, nunca de variável de ambiente), igual ao painel (ArquivoEnv.kt): só a linha da flag,
# NOME=valor, aspas e comentário final opcionais, a ÚLTIMA ocorrência vale. O ambiente do processo do Cline e o
# comando NÃO ligam nem desligam a flag. Arquivo ausente, ilegível, sem a linha ou valor diferente de true
# (espaços nas pontas ignorados, sem diferenciar maiúsculas) = trava ATIVA (falha fechada). Este hook lê o
# arquivo só para si e nunca imprime o conteúdo; a IA continua proibida de ler, escrever ou versionar o arquivo.
#   - trava ativa: variáveis JIRA_*, ENDEREÇOS de Jira (abaixo) em comandos, MCP do Atlassian, navegador e
#     web_fetch são bloqueados;
#   - true -> nada disso é bloqueado, exceto exibir token ou senha do Jira no terminal (Plano 4A, Decisão 10).
# Regra de endereço (decisão de 28/10/2026): a palavra "jira" solta (pasta dashboardJira, nome de arquivo,
# repositório, texto) NÃO bloqueia. Bloqueia URL/host com "jira" em algum rótulo, "jira"+"sidia" em
# host+caminho ou *.atlassian.net com "sidia" (com ou sem esquema, porta, userinfo, maiúsculas, escapes %,
# ponto final). Documentação pública (atlassian.com e subdomínios) é liberada; vale o host REAL (depois do
# userinfo) e a URL aninhada em query é avaliada à parte. Espelho de AWK_URL do guard.sh.
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
# curto 8.3 (`ENV~1`) bloqueia. Também bloqueiam: `CLAUDE~1` (a pasta .claude) e 8.3 dentro de .claude.
#
# Arquivos que só o usuário altera (ligam a flag; ler continua liberado), casados DEPOIS da normalização:
# `.claude/settings*.json`, `*compose*override*` e todo `*compose*.y*ml` (inclusive app/docker-compose.yml).
# Comandos que citam esses arquivos: negados por padrão; só passa leitura conhecida (Regra-SoUsuario,
# espelho do guard.sh). Conteúdo: escrever texto que liga a flag em QUALQUER arquivo é bloqueado.
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

# ---------- flag lida do app\.env (espelho do bloco de leitura da flag do guard.sh e do ArquivoEnv.kt) ----------
function Ler-Flag-Do-Arquivo {
    # $true só se a ÚLTIMA linha da flag no app\.env da raiz do repositório vale true. Qualquer problema = $false.
    try {
        # raiz do repositório pelo caminho deste script (.clinerules\hooks -> ..\..); nunca por variável de ambiente
        $raiz = [System.IO.Path]::GetFullPath([System.IO.Path]::Combine($PSScriptRoot, '..', '..'))
        $arq = [System.IO.Path]::Combine($raiz, 'app', '.env')
        if (-not [System.IO.File]::Exists($arq)) { return $false }
        # UTF-8 estrito: byte inválido lança exceção (o painel também falha ao ler) e cai no catch = desligada
        $linhas = [System.IO.File]::ReadAllLines($arq, (New-Object System.Text.UTF8Encoding($false, $true)))
        $valor = $null
        foreach ($bruta in $linhas) {
            $l = ([string]$bruta).Trim()
            if ($l.StartsWith([string][char]0xFEFF)) { $l = $l.Substring(1) }
            if ($l -eq '' -or $l.StartsWith('#')) { continue }
            if ($l.StartsWith('export ')) { $l = $l.Substring(7).Trim() }
            $i = $l.IndexOf('=')
            if ($i -le 0) { continue }
            if ($l.Substring(0, $i).Trim() -cne 'JIRA_LIVE_ENABLED') { continue }
            $v = $l.Substring($i + 1).Trim()
            $aspa = ($v.Length -ge 2) -and (($v[0] -eq [char]34) -or ($v[0] -eq [char]39)) -and ($v[$v.Length - 1] -eq $v[0])
            if ($aspa) { $v = $v.Substring(1, $v.Length - 2) }
            else {
                $j = $v.IndexOf(' #')
                if ($j -ge 0) { $v = $v.Substring(0, $j) }
                $v = $v.Trim()
            }
            $valor = $v
        }
        if ($null -eq $valor) { return $false }
        return [string]::Equals($valor.Trim(), 'true', [System.StringComparison]::OrdinalIgnoreCase)
    }
    catch { return $false }
}

# ---------- endereço de Jira (espelho do AWK_URL do guard.sh) ----------
$REDE_FERRAMENTAS = @{}
foreach ($x in 'curl wget http https httpie xh nc ncat telnet ssh scp sftp ftp openssl socat aria2c lynx w3m rsync invoke-webrequest invoke-restmethod iwr irm start-bitstransfer bitsadmin certutil test-netconnection tnc resolve-dnsname nslookup ping dig'.Split(' ')) { $REDE_FERRAMENTAS[$x] = $true }
$ALVO_FERRAMENTAS = @{}
foreach ($x in 'ssh scp sftp nc ncat telnet ftp socat ping dig nslookup tnc test-netconnection resolve-dnsname'.Split(' ')) { $ALVO_FERRAMENTAS[$x] = $true }
# valor de opção que é arquivo ou corpo (curl -o jira-saida.txt, -d, -OutFile...) não é host
$PADRAO_OPCAO_COM_ARQUIVO = '^(-o|--output|--output-document|--upload-file|-d|--data|--data-raw|--data-binary|--cacert|--cert|--key|--config|-outfile|-infile|-body|-path|-filepath)$'

function Decodifica-Percent([string]$s) {
    # %XX -> caractere (controles e espaço viram espaço); o texto já vem em minúsculas
    $sb = New-Object System.Text.StringBuilder
    $i = 0
    while ($i -lt $s.Length) {
        if ($s[$i] -eq '%' -and ($i + 2) -lt $s.Length) {
            $h = $s.Substring($i + 1, 2)
            if ($h -match '^[0-9a-f]{2}$') {
                $c = [Convert]::ToInt32($h, 16)
                if ($c -gt 32 -and $c -lt 127) { [void]$sb.Append([char]$c) } else { [void]$sb.Append(' ') }
                $i += 3
                continue
            }
        }
        [void]$sb.Append($s[$i])
        $i++
    }
    return $sb.ToString()
}

function Host-Termina([string]$h, [string]$sufixo) {
    return ($h.Length -gt $sufixo.Length) -and $h.EndsWith($sufixo, [System.StringComparison]::Ordinal)
}

function Separa-Endereco([string]$u) {
    # "usuario@host:porta/caminho" -> Hospedeiro, Resto, TemPorta, TemUser (o host real vem depois do último @)
    $m = [regex]::Match($u, '[/?#\\]')
    if ($m.Success) { $auth = $u.Substring(0, $m.Index); $resto = $u.Substring($m.Index) } else { $auth = $u; $resto = '' }
    $temUser = $false
    $i = $auth.LastIndexOf('@')
    if ($i -ge 0) { $auth = $auth.Substring($i + 1); $temUser = $true }
    $temPorta = $false
    $hosp = ''
    if (-not $auth.StartsWith('[')) {
        $j = $auth.IndexOf(':')
        if ($j -ge 0) { $temPorta = $true; $auth = $auth.Substring(0, $j) }
        $hosp = $auth.TrimEnd('.')
    }
    return [pscustomobject]@{ Hospedeiro = $hosp; Resto = $resto; TemPorta = $temPorta; TemUser = $temUser }
}

function Endereco-DeJira([string]$hosp, [string]$resto) {
    # $true = endereço de Jira (bloquear). Texto já em minúsculas.
    if ($hosp -eq '') { return $false }
    if ($hosp -eq 'atlassian.com' -or (Host-Termina $hosp '.atlassian.com')) {
        # documentação pública; mas "@" ou "\" no resto com jira pode esconder outro host
        if ($resto -match '[@\\]' -and $resto.Contains('jira')) { return $true }
        return $false
    }
    foreach ($rotulo in $hosp.Split('.')) { if ($rotulo.Contains('jira')) { return $true } }
    $hp = $resto
    $q = $hp.IndexOfAny([char[]]@([char]63, [char]35))
    if ($q -ge 0) { $hp = $hp.Substring(0, $q) }
    $hp = $hosp + $hp
    if ($hp.Contains('jira') -and $hp.Contains('sidia')) { return $true }
    if (($hosp -eq 'atlassian.net' -or (Host-Termina $hosp '.atlassian.net')) -and ($hosp + $resto).Contains('sidia')) { return $true }
    return $false
}

function Base-Palavra([string]$w) {
    $b = [regex]::Replace($w, '^.*[/\\]', '')
    return [regex]::Replace($b, '\.exe$', '')
}

function Texto-Cita-Jira([string]$texto, [string]$rede) {
    # $true = o texto cita endereço de Jira. rede: '1' = procura host sem esquema em qualquer palavra;
    # '0' = só URLs com esquema; 'auto' = host sem esquema só se houver ferramenta de rede no texto.
    if ([string]::IsNullOrEmpty($texto)) { return $false }
    $t = $texto.ToLowerInvariant()
    $t = Decodifica-Percent (Decodifica-Percent (Decodifica-Percent $t))
    $t = [regex]::Replace($t, '[\r\n]+', ' ')
    # A) URLs com esquema (a URL aninhada na query é varrida à parte)
    $rx = New-Object System.Text.RegularExpressions.Regex('[a-z][a-z0-9+.-]*://[^\s"''<>`|;&()]*')
    $s = $t
    while ($true) {
        $m = $rx.Match($s)
        if (-not $m.Success) { break }
        $k = $m.Value.IndexOf('://')
        $e = Separa-Endereco ($m.Value.Substring($k + 3))
        if ($e.Hospedeiro -ne '' -and (Endereco-DeJira $e.Hospedeiro $e.Resto)) { return $true }
        $s = $s.Substring($m.Index + $k + 3)
    }
    # B) host sem esquema
    $palavras = [regex]::Split($t, '[\s"''`;|&()<>,=]+')
    $r = ($rede -eq '1')
    if ($rede -eq 'auto') {
        foreach ($w in $palavras) { if ($REDE_FERRAMENTAS.ContainsKey((Base-Palavra $w))) { $r = $true; break } }
        if ($t -match 'webclient|httpclient|xmlhttp|winhttp') { $r = $true }
    }
    $janela = 0
    $ant = ''
    foreach ($w in $palavras) {
        if ($w -eq '') { continue }
        # com ferramenta de rede, todas as palavras; sem ela, só as de forma usuario@host (git clone git@host:repo)
        if (-not $r -and -not $w.Contains('@')) { continue }
        $salta = ($ant -match $PADRAO_OPCAO_COM_ARQUIVO)
        $ant = $w
        if ($salta) { continue }
        if ($ALVO_FERRAMENTAS.ContainsKey((Base-Palavra $w))) { $janela = 4; continue }
        if ($w.Contains('://')) { continue }
        if ($w -match '^-' -or $w -match '^[/.~$]' -or $w.Contains('\') -or $w -match '^[a-z]:[/\\]' -or $w -match '^[0-9]+$') { continue }
        $e = Separa-Endereco $w
        $alvo = ($janela -gt 0)
        if ($janela -gt 0) { $janela-- }
        if ($e.Hospedeiro -eq '') { continue }
        if ($alvo -or $e.Hospedeiro.Contains('.') -or $e.TemPorta -or $e.TemUser) {
            if (Endereco-DeJira $e.Hospedeiro $e.Resto) { return $true }
        }
    }
    return $false
}

function Coletar-Urls($valor, $lista, [int]$profundidade) {
    # textos dos campos url/uri/href/link, em qualquer nível (espelho do jq do adaptador bash)
    if ($null -eq $valor -or $profundidade -gt 8) { return }
    $valor = Desfazer-Json $valor
    if ($valor -is [System.Management.Automation.PSCustomObject]) {
        foreach ($p in $valor.PSObject.Properties) {
            if (@('url', 'uri', 'href', 'link') -contains $p.Name -and $p.Value -is [string]) { [void]$lista.Add($p.Value) }
            Coletar-Urls $p.Value $lista ($profundidade + 1)
        }
        return
    }
    if ($valor -is [string]) { return }
    if ($valor -is [System.Collections.IEnumerable]) {
        foreach ($item in $valor) { Coletar-Urls $item $lista ($profundidade + 1) }
    }
}

# ---------- regras (espelham o .claude/hooks/guard.sh, mais os extras do Cline) ----------
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
# interpretadores e outras ferramentas que escrevem arquivos (somam-se a $PADRAO_ESCRITA para .env e configs)
$PADRAO_ESCRITA_EXTRA = '(^|[\s;&|(])(python[0-9.]*|perl|node|ruby|php|pwsh|powershell|patch|rsync)(\.exe)?\s|git\s+apply'
# texto que liga a flag (comando, conteúdo gravado); espelho de LIGAR_FLAG_EXTRA do PreToolUse do bash
$PADRAO_LIGA_FLAG = 'JIRA_LIVE_ENABLED[\]"''\s,:=]+(-value\s+)?["'']?\s*(1|true)'
# arquivos que só o usuário altera, citados num comando já normalizado (minúsculas, '/', sem '//' nem '/./'):
# qualquer settings*.json (inclusive .vscode/settings.json: falso positivo aceito), .claude junto de settings
# (cd .claude e depois settings.local.json), compose *.y*ml e override, e nomes curtos 8.3 deles
$PADRAO_CITA_SETTINGS = 'settings[^\s"'';|&)<>/]*\.json'
$PADRAO_CITA_CLAUDE = '\.claude|claude~[0-9]'
$PADRAO_CITA_SETTINGS_NOME = 'settings|settin~[0-9]'
$PADRAO_CITA_COMPOSE = 'compose[^\s"'';|&)<>/]*(\.y[a-z]*ml|override)|(docker|compos)[a-z0-9_-]{0,6}~[0-9]'
# comandos de leitura conhecidos (o resto é negado por padrão quando o comando cita esses arquivos)
$LEITORES = @('cat', 'ls', 'grep', 'egrep', 'fgrep', 'rg', 'head', 'tail', 'diff', 'wc', 'stat', 'file', 'more',
    'jq', 'cmp', 'readlink', 'realpath', 'basename', 'dirname', 'nl', 'shasum', 'sha256sum', 'md5', 'md5sum',
    'cd', 'pushd', 'popd', 'echo', 'printf', 'true', 'test',
    'type', 'get-content', 'gc', 'select-string', 'sls', 'findstr')
$GIT_LEITURA = @('diff', 'log', 'show', 'status', 'blame', 'ls-files', 'grep', 'rev-parse', 'cat-file', 'shortlog')

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
    # 2) endereço de Jira no comando (host com "jira" no rótulo, jira+sidia em host+caminho, *.atlassian.net
    #    com sidia; documentação pública atlassian.com liberada). Host sem esquema só junto de ferramenta de
    #    rede. A palavra "jira" solta NÃO bloqueia.
    if (Texto-Cita-Jira $cmd 'auto') { return 'comando referencia endereço de Jira e a flag do Jira não está ligada.' }
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
    # Deixa em $script:norm o caminho normalizado (segmentos unidos por '/'), usado por So-Usuario-Altera.
    $script:norm = ''
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
    # nome curto 8.3 da pasta .claude (CLAUDE~1) ou de qualquer segmento dentro dela (SETTIN~1.JSO)
    $sobClaude = $false
    foreach ($s in $partes) {
        if ($s -match '^claude~[0-9]') { return "nome curto 8.3 ($s) pode ser a pasta .claude." }
        if ($sobClaude -and $s -match '~[0-9]') { return "nome curto 8.3 ($s) dentro de .claude." }
        if ($s -eq '.claude') { $sobClaude = $true }
    }
    $script:norm = ($partes -join '/')
    $base = [string]$partes[$partes.Count - 1]
    # nome curto 8.3 só importa no último segmento (a proteção é pelo nome do arquivo; pastas como
    # C:\Users\USUARIO~1\... continuam liberadas)
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

function So-Usuario-Altera([string]$norm) {
    # caminho JÁ normalizado por Caminho-Protegido: settings do Claude, compose e override do compose
    if ([string]::IsNullOrEmpty($norm)) { return $false }
    $base = ($norm -split '/')[-1]
    if (('/' + $norm) -like '*/.claude/settings*.json') { return $true }
    if (($base -like '*compose*override*') -or ($base -like '*compose*.y*ml')) { return $true }
    return $false
}

function Tirar-Redirecoes-Inofensivas([string]$t) {
    # 2>&1, >&2 e saída para /dev/null não gravam arquivo
    $t = [regex]::Replace($t, '[0-9]*>&[0-9-]+', ' ')
    return [regex]::Replace($t, '[0-9&]*>>?\s*/dev/null', ' ')
}

function Regra-SoUsuario([string]$cmd) {
    # Nega por padrão: se o comando cita settings do Claude, compose ou override, só passa leitura conhecida.
    # Espelho do guard.sh (modo arquivos). Devolve o motivo ou $null.
    $t = $cmd.ToLowerInvariant().Replace('\', '/')
    $t = [regex]::Replace($t, '/+', '/')
    $t = [regex]::Replace($t, '/\./', '/')
    $t = [regex]::Replace($t, '/\./', '/')
    $cita = (Casa $t $PADRAO_CITA_SETTINGS) -or ((Casa $t $PADRAO_CITA_CLAUDE) -and (Casa $t $PADRAO_CITA_SETTINGS_NOME)) -or (Casa $t $PADRAO_CITA_COMPOSE)
    if (-not $cita) { return $null }
    $msg = 'comando cita settings do Claude, compose ou override do compose e não é leitura conhecida: só o usuário altera esses arquivos'
    $s = Tirar-Redirecoes-Inofensivas $t
    if ($s.Contains('>')) { return "$msg (redirecionamento)." }
    # chaves não separam (não partir scripts de awk); `{ cp ...; }` começa por '{' e é negado
    foreach ($seg in [regex]::Split($s, '[;&|()`\r\n]')) {
        $g = $seg.Trim()
        if ($g -eq '' -or $g -eq '$') { continue }
        # atribuições no início (VAR=valor cmd) não são o comando
        $g = [regex]::Replace($g, '^([a-z_][a-z0-9_]*=\S*\s+)+', '')
        if ($g -eq '') { continue }
        $w = ($g -split '\s+')[0]
        $w = $w.Trim('"', "'").TrimStart('\')
        $w = ($w -split '/')[-1]
        if ($w.EndsWith('.exe')) { $w = $w.Substring(0, $w.Length - 4) }
        if ($w -eq '' -or $w -eq '$') { continue }
        if ($LEITORES -contains $w) { continue }
        if ($w -eq 'less') {
            if ($g -match '(^|\s)(-[a-z]*o|--log-file)') { return "$msg (less -o grava arquivo)." }
            continue
        }
        if ($w -eq 'sed') {
            if ($g -match '(^|\s)(-[a-z]*i|--in-place)') { return "$msg (sed -i)." }
            # comandos w/W (grava) e e (executa) no script, depois de tirar as opções
            $sc = [regex]::Replace($g, '\s-[a-z]+', ' ')
            if ($sc -match '[;{}/0-9$\s''"][we]([\s''"]|$)') { return "$msg (sed com w ou e)." }
            continue
        }
        if ($w -eq 'awk' -or $w -eq 'gawk' -or $w -eq 'nawk') {
            if ($g -match 'system|inplace|(^|\s)-i') { return "$msg (awk com system ou -i)." }
            continue
        }
        if ($w -eq 'git') {
            if ($g -match '--output') { return "$msg (git --output)." }
            $r = [regex]::Replace($g, '^git', '')
            $r = [regex]::Replace($r, '^(\s+(-c\s+\S+|-\S+))*', '')
            $sub = ($r.Trim() -split '\s+')[0]
            if ($GIT_LEITURA -contains $sub) { continue }
            return "$msg (git $sub)."
        }
        if ($w -eq 'docker' -or $w -eq 'docker-compose') {
            if ($g -match '(^|\s)(cp|-o|--output)(\s|=|$)') { return "$msg (docker cp/-o)." }
            continue
        }
        return "$msg ($w)."
    }
    return $null
}

function Regra-Arquivos([string]$cmd) {
    # tokens exatamente iguais aos modelos não contam como protegidos
    $semModelos = [regex]::Replace($cmd, $PADRAO_MODELOS, '$1 $3', $OPC_I)
    if (Casa $semModelos $PADRAO_PROTEGIDOS) {
        if ((Casa $semModelos $PADRAO_ESCRITA) -or (Casa $semModelos $PADRAO_ESCRITA_EXTRA)) { return 'comando altera ou versiona segredo/config com dados reais.' }
        if (Casa $semModelos $PADRAO_LEITURA) { return 'comando lê segredo/config com dados reais (o conteúdo iria para o modelo).' }
    }
    # settings do Claude, compose e override: nega por padrão (só leitura conhecida passa)
    $m = Regra-SoUsuario $cmd
    if ($m) { return $m }
    # gravar texto que liga a flag em qualquer arquivo (vale com qualquer valor da flag)
    $semRedir = Tirar-Redirecoes-Inofensivas $cmd
    if ((Casa $cmd $PADRAO_LIGA_FLAG) -and ((Casa $semRedir $PADRAO_ESCRITA) -or (Casa $semRedir $PADRAO_ESCRITA_EXTRA))) {
        return 'comando grava texto que liga JIRA_LIVE_ENABLED num arquivo. Só o usuário liga a flag.'
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

    # a flag vem SÓ do app\.env da raiz do repositório (o ambiente do processo não vale); erro = desligada
    $jiraLivre = [bool](Ler-Flag-Do-Arquivo)

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
                # nome do servidor/ferramenta MCP do Atlassian ou de Jira (acesso real): continua barrado
                $alvo = @((Ler-Prop $parametros 'server_name'), (Ler-Prop $parametros 'tool_name')) -join ' '
                if (Casa $alvo 'atlassian|jira') {
                    Bloquear "BLOQUEADO: ferramenta MCP do Atlassian/Jira ($($alvo.Trim())) com a flag do Jira desligada."
                }
                # URI do recurso e campos de URL (url/uri/href/link, em qualquer nível) dos argumentos: host com ou sem esquema
                $urls = New-Object System.Collections.ArrayList
                $uriMcp = Ler-Prop $parametros 'uri'
                if ($uriMcp -is [string]) { [void]$urls.Add($uriMcp) }
                Coletar-Urls (Ler-Prop $parametros 'arguments') $urls 0
                if ($urls.Count -gt 0 -and (Texto-Cita-Jira ($urls -join "`n") '1')) {
                    Bloquear 'BLOQUEADO: ferramenta MCP com endereço de Jira e a flag do Jira desligada.'
                }
                # qualquer outro texto dos argumentos (ex.: servidor MCP de 'fetch'): só URLs com esquema
                $textos = New-Object System.Collections.ArrayList
                Coletar-Textos (Ler-Prop $parametros 'arguments') $textos 0
                if ($textos.Count -gt 0 -and (Texto-Cita-Jira ($textos -join "`n") '0')) {
                    Bloquear 'BLOQUEADO: ferramenta MCP com URL de Jira e a flag do Jira desligada.'
                }
            }
            Liberar
        }
        '^(browser_action|web_fetch)$' {
            # decisão do usuário (28/10, substitui a de 25/10): com a flag desligada, só endereço de Jira é barrado
            # (host com "jira", jira+sidia, *.atlassian.net com sidia); documentação pública da Atlassian é liberada
            if (-not $jiraLivre) {
                $urls = New-Object System.Collections.ArrayList
                foreach ($campo in @('url', 'uri', 'href', 'link')) {
                    $u = Ler-Prop $parametros $campo
                    if ($u -is [string]) { [void]$urls.Add($u) }
                }
                if ($urls.Count -gt 0 -and (Texto-Cita-Jira ($urls -join "`n") '1')) {
                    Bloquear "BLOQUEADO: $ferramenta para endereço de Jira com a flag do Jira desligada (documentação pública em atlassian.com é liberada)."
                }
                $textos = New-Object System.Collections.ArrayList
                Coletar-Textos $parametros $textos 0
                if ($textos.Count -gt 0 -and (Texto-Cita-Jira ($textos -join "`n") '0')) {
                    Bloquear "BLOQUEADO: $ferramenta com URL de Jira e a flag do Jira desligada."
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
            if ($ferramenta -notmatch '^read_') {
                # conteúdo gravado (content, diff, replace, new_str...): ligar a flag é barrado em qualquer arquivo
                $textos = New-Object System.Collections.ArrayList
                Coletar-Textos $parametros $textos 0
                # também o texto cru (Coletar-Textos desfaz JSON e separaria chave e valor de {"chave": "valor"})
                foreach ($pp in $parametros.PSObject.Properties) { if ($pp.Value -is [string]) { [void]$textos.Add($pp.Value) } }
                foreach ($t in $textos) {
                    if (Casa $t $PADRAO_LIGA_FLAG) { Bloquear "BLOQUEADO: $ferramenta grava texto que liga JIRA_LIVE_ENABLED (em qualquer arquivo). Só o usuário liga a flag." }
                }
            }
            foreach ($p in $lidos) {
                $m = Caminho-Protegido ([string]$p)
                if ($m) {
                    if ($ferramenta -match '^read_') { Bloquear "BLOQUEADO: leitura de arquivo protegido ($p): $m" }
                    Bloquear "BLOQUEADO: escrita em arquivo protegido ($p): $m"
                }
                if ($ferramenta -notmatch '^read_') {
                    # settings do Claude, compose e override ligam a flag: só o usuário escreve neles (caminho normalizado)
                    if (So-Usuario-Altera $script:norm) {
                        Bloquear "BLOQUEADO: escrita em ${p}: só o usuário altera esse arquivo (settings do Claude, compose e override do compose podem ligar JIRA_LIVE_ENABLED)."
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
            # conteúdo do patch: ligar a flag é barrado em qualquer arquivo
            if (Casa $patch $PADRAO_LIGA_FLAG) { Bloquear 'BLOQUEADO: apply_patch grava texto que liga JIRA_LIVE_ENABLED (em qualquer arquivo). Só o usuário liga a flag.' }
            foreach ($linha in ($patch -split "`r?`n")) {
                $mm = [regex]::Match($linha, '^\*\*\* (Add File|Update File|Delete File|Move to): (.+)$')
                if ($mm.Success) {
                    $m = Caminho-Protegido $mm.Groups[2].Value
                    if ($m) { Bloquear "BLOQUEADO: escrita em arquivo protegido ($($mm.Groups[2].Value)): $m" }
                    if (So-Usuario-Altera $script:norm) {
                        Bloquear "BLOQUEADO: escrita em $($mm.Groups[2].Value): só o usuário altera esse arquivo (settings do Claude, compose e override do compose podem ligar JIRA_LIVE_ENABLED)."
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
