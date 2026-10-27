<#
.SYNOPSIS
  Diagnostico de conexao e consulta ao Jira. Mostra CADA requisicao e CADA resposta.

.DESCRIPTION
  1) ambiente, DNS, TCP e certificado TLS  2) serverInfo (sem login)  3) login (GET myself)
  4) projetos visiveis  5) localiza o usuario  6) consulta: tasks concluidas por ele nesta semana.
  Grava tudo em diagnostico_jira_AAAAMMDD_HHMMSS.txt na pasta atual (o token NUNCA e impresso, mas o log tem e-mails, nomes e titulos de tasks: ele esta no .gitignore; nunca o compartilhe nem o commite).
  So roda com a flag do Jira ligada em app\.env (apenas o usuario liga).

.EXAMPLE
  # defina antes (nao passe o token como parametro: o historico do PowerShell gravaria):
  $env:JIRA_BASE_URL = 'https://suaempresa.atlassian.net'     # ou https://jira.suaempresa.com.br
  $env:JIRA_EMAIL    = 'voce@suaempresa.com'                   # so Cloud
  $env:JIRA_TOKEN    = 'SEU_TOKEN'                             # Cloud: API token | Data Center: Personal Access Token
  .\docs\jira\diagnostico-conexao.ps1
  # sem variaveis, o script pergunta. Outras opcoes: -User usuario.exemplo -Since 2026-10-19 -FullBody
#>
param(
  [string]$BaseUrl = $env:JIRA_BASE_URL,
  [string]$Flavor  = $(if ($env:JIRA_FLAVOR) { $env:JIRA_FLAVOR } else { 'auto' }),   # auto | cloud | dc
  [string]$Email   = $env:JIRA_EMAIL,
  [string]$Token   = $env:JIRA_TOKEN,
  [string]$AuthMode = 'auto',                  # auto | basic | bearer  (auto: Cloud=basic, Data Center=bearer)
  [string]$User    = 'usuario.exemplo',         # usuario a consultar
  [string]$UserRef = '',                       # forca accountId (Cloud) ou username (DC) no JQL
  [string]$Since   = '',                       # AAAA-MM-DD; padrao = segunda-feira desta semana
  [int]$MaxBody    = 3000,                     # limite de caracteres de cada corpo impresso
  [int]$TimeoutSec = 30,
  [switch]$FullBody                            # imprime corpos completos
)

# ---------------------------------------------------------------- preparo
$ErrorActionPreference = 'Continue'
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch {}
Add-Type -AssemblyName System.Net.Http
$script:n = 0
$script:steps = New-Object System.Collections.ArrayList
$logFile = Join-Path (Get-Location) ('diagnostico_jira_{0:yyyyMMdd_HHmmss}.txt' -f (Get-Date))
try { Start-Transcript -Path $logFile -Force | Out-Null } catch { Write-Host "Aviso: nao foi possivel gravar o log: $($_.Exception.Message)" -ForegroundColor Yellow }

# TRAVA: so roda com a flag do Jira ligada em app\.env (unica trava do projeto). Le SOMENTE essa linha; nenhuma credencial vem do arquivo.
$flagLigada = $false
foreach ($p in @("$PSScriptRoot\..\..\app\.env", '.\app\.env', '.\.env')) {
  if (Test-Path $p) {
    foreach ($line in Get-Content $p -ErrorAction SilentlyContinue) {
      if ($line -match '^\s*JIRA_LIVE_ENABLED\s*=\s*"?(\w+)"?\s*(#.*)?$') { $flagLigada = ($Matches[1] -eq 'true') }
    }
    break
  }
}
if (-not $flagLigada) {
  Write-Host 'Flag do Jira desligada (ou app\.env ausente): o acesso ao Jira esta proibido. O usuario liga a flag a mao em app\.env e roda o script de novo.' -ForegroundColor Yellow
  try { Stop-Transcript | Out-Null } catch { }
  Remove-Item -Path $logFile -ErrorAction SilentlyContinue
  exit 2
}

function Write-Section([string]$t) {
  Write-Host ''
  Write-Host ('=' * 100) -ForegroundColor Cyan
  Write-Host "  $t" -ForegroundColor Cyan
  Write-Host ('=' * 100) -ForegroundColor Cyan
}
function Add-Step([string]$etapa, [string]$res, [string]$det) {
  [void]$script:steps.Add([pscustomobject]@{ Etapa = $etapa; Resultado = $res; Detalhe = $det })
}
function Format-Json([string]$text) {
  # reindenta o JSON exatamente como recebido (sem reinterpretar), para ler e comparar com o que o servidor mandou
  $sb = New-Object System.Text.StringBuilder
  $ind = 0; $inStr = $false; $esc = $false
  foreach ($ch in $text.ToCharArray()) {
    if ($inStr) {
      [void]$sb.Append($ch)
      if ($esc) { $esc = $false } elseif ($ch -eq '\') { $esc = $true } elseif ($ch -eq '"') { $inStr = $false }
      continue
    }
    switch ($ch) {
      '"' { $inStr = $true; [void]$sb.Append($ch) }
      { $_ -eq '{' -or $_ -eq '[' } { $ind++; [void]$sb.Append($ch).Append("`n").Append(' ' * (2 * $ind)) }
      { $_ -eq '}' -or $_ -eq ']' } { $ind = [Math]::Max(0, $ind - 1); [void]$sb.Append("`n").Append(' ' * (2 * $ind)).Append($ch) }
      ',' { [void]$sb.Append($ch).Append("`n").Append(' ' * (2 * $ind)) }
      ':' { [void]$sb.Append(': ') }
      default { if (-not [char]::IsWhiteSpace($ch)) { [void]$sb.Append($ch) } }
    }
  }
  return $sb.ToString()
}
function Format-Body([string]$text) {
  if ([string]::IsNullOrWhiteSpace($text)) { return '(corpo vazio)' }
  $out = $text.Trim()
  if ($out.StartsWith('{') -or $out.StartsWith('[')) { try { $out = Format-Json $out } catch { } }
  if (-not $FullBody -and $out.Length -gt $MaxBody) {
    $out = $out.Substring(0, $MaxBody) + "`n... [cortado: $($out.Length) caracteres no total; use -FullBody para ver tudo]"
  }
  return $out
}
function Get-Array($x) { if ($null -eq $x) { return @() } return @(@($x) | Where-Object { $null -ne $_ }) }
function Convert-JiraDate($v) {
  if ($null -eq $v -or "$v" -eq '') { return '' }
  if ($v -is [datetime]) { return $v.ToLocalTime().ToString('dd/MM/yyyy HH:mm') }
  try {
    $t = ("$v" -replace '([+-]\d{2})(\d{2})$', '$1:$2')
    return ([datetimeoffset]::Parse($t, [Globalization.CultureInfo]::InvariantCulture)).LocalDateTime.ToString('dd/MM/yyyy HH:mm')
  } catch { return "$v" }
}
function Mask-Header([string]$k, [string]$v) {
  if ($k -match 'authorization|cookie|token') {
    $scheme = ($v -split ' ')[0]
    return "$scheme ***oculto*** ($($v.Length) caracteres)"
  }
  return $v
}

# ---------------------------------------------------------------- HTTP com log completo
function Invoke-Raw {
  param([string]$Method, [string]$Url, [hashtable]$Headers = @{}, [string]$Body = $null)
  $script:n++
  $id = $script:n
  Write-Host ''
  Write-Host ">>> REQUISICAO #$id  $Method $Url" -ForegroundColor Yellow
  Write-Host "    Hora: $(Get-Date -Format 'HH:mm:ss.fff')"
  foreach ($k in $Headers.Keys) { Write-Host "    $k`: $(Mask-Header $k ([string]$Headers[$k]))" }
  if ($Body) { Write-Host '    Corpo enviado:'; (Format-Body $Body) -split "`n" | ForEach-Object { Write-Host "      $_" } }

  $handler = New-Object System.Net.Http.HttpClientHandler
  try {
    $px = [System.Net.WebRequest]::GetSystemWebProxy()
    $px.Credentials = [System.Net.CredentialCache]::DefaultCredentials
    $handler.Proxy = $px; $handler.UseProxy = $true
  } catch { }
  $client = New-Object System.Net.Http.HttpClient($handler)
  $client.Timeout = [TimeSpan]::FromSeconds($TimeoutSec)
  $req = New-Object System.Net.Http.HttpRequestMessage((New-Object System.Net.Http.HttpMethod($Method)), $Url)
  [void]$req.Headers.TryAddWithoutValidation('Accept', 'application/json')
  foreach ($k in $Headers.Keys) { [void]$req.Headers.TryAddWithoutValidation($k, [string]$Headers[$k]) }
  if ($Body) { $req.Content = New-Object System.Net.Http.StringContent($Body, [System.Text.Encoding]::UTF8, 'application/json') }

  $sw = [System.Diagnostics.Stopwatch]::StartNew()
  $res = @{ Id = $id; Ok = $false; Status = 0; Reason = ''; Body = ''; Json = $null; Ms = 0; Error = $null; FinalUrl = $Url; Headers = @{} }
  try {
    $resp = $client.SendAsync($req).GetAwaiter().GetResult()
    $res.Status = [int]$resp.StatusCode
    $res.Reason = [string]$resp.ReasonPhrase
    $res.Body = $resp.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    $sw.Stop(); $res.Ms = $sw.ElapsedMilliseconds
    $res.Ok = $resp.IsSuccessStatusCode
    if ($resp.RequestMessage -and $resp.RequestMessage.RequestUri) { $res.FinalUrl = $resp.RequestMessage.RequestUri.ToString() }
    $color = if ($res.Ok) { 'Green' } else { 'Red' }
    Write-Host "<<< RESPOSTA #$id  HTTP $($res.Status) $($res.Reason)   ($($res.Ms) ms)" -ForegroundColor $color
    if ($res.FinalUrl -ne $Url) { Write-Host "    ATENCAO: redirecionado para: $($res.FinalUrl)" -ForegroundColor Yellow }
    foreach ($h in $resp.Headers) { $res.Headers[$h.Key] = ($h.Value -join ', '); Write-Host "    $($h.Key): $(Mask-Header $h.Key ($h.Value -join ', '))" }
    foreach ($h in $resp.Content.Headers) { $res.Headers[$h.Key] = ($h.Value -join ', '); Write-Host "    $($h.Key): $($h.Value -join ', ')" }
    Write-Host '    Corpo recebido:'
    (Format-Body $res.Body) -split "`n" | ForEach-Object { Write-Host "      $_" }
    if ($res.Body -and $res.Body.TrimStart().StartsWith('<')) {
      Write-Host '    ATENCAO: a resposta e HTML, nao JSON. Normalmente e pagina de login/SSO, bloqueio de proxy ou URL errada.' -ForegroundColor Yellow
    }
    try { $res.Json = $res.Body | ConvertFrom-Json } catch { }
  } catch {
    $sw.Stop(); $res.Ms = $sw.ElapsedMilliseconds
    $e = $_.Exception; $chain = @()
    while ($e) { $chain += ("{0}: {1}" -f $e.GetType().FullName, $e.Message); $e = $e.InnerException }
    $res.Error = ($chain -join ' | ')
    Write-Host "<<< SEM RESPOSTA #$id  ($($res.Ms) ms)  a requisicao falhou antes de receber HTTP" -ForegroundColor Red
    $chain | ForEach-Object { Write-Host "    $_" -ForegroundColor Red }
  } finally { $client.Dispose() }
  return $res
}

function Get-Hint($r) {
  if ($r.Error) {
    if ($r.Error -match 'SSL|TLS|certificate|certificado|trust|confian') { return 'Falha de TLS/certificado. Em rede corporativa costuma ser inspecao SSL (CA interna nao confiada) ou certificado vencido. Faca a JVM/Node/Windows confiarem na CA da empresa (docs/superpowers/plans/2026-10-27-plano-00-pc-da-empresa.md, Tarefa 2). Nunca desative a verificacao.' }
    if ($r.Error -match 'No such host|nome|resolved|resolu|NameResolution|remote name') { return 'DNS nao resolveu o host: confira o endereco digitado e se a VPN esta conectada.' }
    if ($r.Error -match 'timeout|timed out|Timeout|cancel') { return 'Tempo esgotado: VPN desconectada, firewall ou proxy bloqueando. Teste o endereco no navegador na mesma maquina.' }
    if ($r.Error -match 'refused|recusad') { return 'Conexao recusada: porta/endereco errados (Data Center interno costuma usar 8080/443).' }
    return 'Falha de rede antes do HTTP. Veja a cadeia de erros acima.'
  }
  switch ($r.Status) {
    401 { return 'Credencial recusada. Cloud: use e-mail + API token (nao a senha). Data Center: use Personal Access Token (Bearer) ou -AuthMode basic com usuario/senha. Veja o cabecalho X-Seraph-LoginReason/X-Authentication-Denied-Reason acima.' }
    403 { return 'Autenticou, mas sem permissao (ou captcha apos tentativas erradas no Data Center: entre no Jira pelo navegador e resolva). Confira Browse Projects.' }
    404 { return 'Endereco nao existe: contexto errado (ex.: https://host/jira), flavor errado (cloud x dc) ou recurso/usuario inexistente.' }
    407 { return 'Proxy exige autenticacao. Defina o proxy/credenciais do Windows ou peca a TI a liberacao do host do Jira.' }
    410 { return 'Endpoint removido (Cloud). O script tenta o endpoint novo automaticamente.' }
    429 { return 'Limite de requisicoes. Veja Retry-After e tente novamente depois.' }
    default { if ($r.Status -ge 500) { return 'Erro no servidor do Jira (5xx). Tente de novo e envie o corpo da resposta a TI/Atlassian.' } }
  }
  return ''
}

function Test-TlsProbe([string]$hostName, [int]$port) {
  $tcp = New-Object System.Net.Sockets.TcpClient
  try {
    $iar = $tcp.BeginConnect($hostName, $port, $null, $null)
    if (-not $iar.AsyncWaitHandle.WaitOne(10000)) { throw "timeout de 10 s ao conectar em ${hostName}:$port" }
    $tcp.EndConnect($iar)
    Write-Host "    TCP ${hostName}:$port conectou (local $($tcp.Client.LocalEndPoint) -> remoto $($tcp.Client.RemoteEndPoint))" -ForegroundColor Green
    $script:sslErr = 'None'
    $cb = [System.Net.Security.RemoteCertificateValidationCallback]{ param($s, $c, $ch, $e) $script:sslErr = [string]$e; $true }
    $ssl = New-Object System.Net.Security.SslStream($tcp.GetStream(), $false, $cb)
    $ssl.AuthenticateAsClient($hostName)
    $cert = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2($ssl.RemoteCertificate)
    Write-Host "    TLS: protocolo $($ssl.SslProtocol), cifra $($ssl.CipherAlgorithm)"
    Write-Host "    Certificado: assunto = $($cert.Subject)"
    Write-Host "                 emissor = $($cert.Issuer)"
    Write-Host "                 validade = $($cert.NotBefore.ToString('dd/MM/yyyy')) ate $($cert.NotAfter.ToString('dd/MM/yyyy'))"
    $c2 = if ($script:sslErr -eq 'None') { 'Green' } else { 'Red' }
    Write-Host "    Erros de validacao do certificado: $($script:sslErr)" -ForegroundColor $c2
    if ($script:sslErr -ne 'None') { Write-Host '    -> certificado nao confiavel neste Windows (CA corporativa/inspecao SSL?).' -ForegroundColor Yellow }
    return @{ Ok = ($script:sslErr -eq 'None'); Detail = "TLS $($ssl.SslProtocol); emissor $($cert.Issuer); erros: $($script:sslErr)" }
  } catch {
    Write-Host "    FALHOU: $($_.Exception.Message)" -ForegroundColor Red
    return @{ Ok = $false; Detail = $_.Exception.Message }
  } finally { $tcp.Close() }
}

# ================================================================ INICIO
try {
Write-Section '0. AMBIENTE'
Write-Host "Data/hora local : $(Get-Date -Format 'dd/MM/yyyy HH:mm:ss')  (UTC$((Get-TimeZone).BaseUtcOffset))"
Write-Host "PowerShell      : $($PSVersionTable.PSVersion)  ($($PSVersionTable.PSEdition))"
Write-Host "Sistema         : $([System.Environment]::OSVersion.VersionString)"
Write-Host "Usuario Windows : $env:USERDOMAIN\$env:USERNAME"
Write-Host "Log             : $logFile"

if (-not $BaseUrl) { $BaseUrl = Read-Host 'URL do Jira (ex.: https://suaempresa.atlassian.net ou https://jira.suaempresa.com.br)' }
$BaseUrl = $BaseUrl.Trim().TrimEnd('/')
if ($BaseUrl -notmatch '^https?://') { $BaseUrl = 'https://' + $BaseUrl }
$uri = [Uri]$BaseUrl
if ($Flavor -eq 'auto') { $Flavor = if ($uri.Host -like '*.atlassian.net') { 'cloud' } else { 'dc' } }
$Flavor = $Flavor.ToLower()
Write-Host "URL base        : $BaseUrl"
Write-Host "Tipo (flavor)   : $Flavor  (cloud = atlassian.net | dc = Data Center/Server)"
try {
  $sp = [System.Net.WebRequest]::GetSystemWebProxy().GetProxy($uri)
  $proxyTxt = if ($sp -and $sp.ToString() -ne $uri.ToString()) { $sp.ToString() } else { '(sem proxy para este host)' }
} catch { $proxyTxt = '(nao foi possivel detectar)' }
Write-Host "Proxy do sistema: $proxyTxt"
foreach ($pv in 'HTTP_PROXY', 'HTTPS_PROXY', 'NO_PROXY') { if (Get-Item "Env:$pv" -ErrorAction SilentlyContinue) { Write-Host "$pv = $((Get-Item "Env:$pv").Value)" } }
if ($Flavor -eq 'cloud') { $api = "$BaseUrl/rest/api/3" } else { $api = "$BaseUrl/rest/api/2" }

# ---------------------------------------------------------------- 1. rede
Write-Section '1. REDE: DNS, TCP e certificado TLS'
$port = if ($uri.Port -gt 0) { $uri.Port } else { 443 }
try {
  $ips = [System.Net.Dns]::GetHostAddresses($uri.Host) | ForEach-Object { $_.ToString() }
  Write-Host "    DNS $($uri.Host) -> $($ips -join ', ')" -ForegroundColor Green
  Add-Step '1a DNS' 'OK' ($ips -join ', ')
} catch {
  Write-Host "    DNS FALHOU: $($_.Exception.Message)" -ForegroundColor Red
  Write-Host '    -> confira o endereco e a VPN.' -ForegroundColor Yellow
  Add-Step '1a DNS' 'FALHOU' $_.Exception.Message
}
if ($uri.Scheme -eq 'https') {
  $t = Test-TlsProbe $uri.Host $port
  Add-Step '1b TCP/TLS' $(if ($t.Ok) { 'OK' } else { 'FALHOU' }) $t.Detail
} else { Write-Host '    (URL http: sem TLS)' -ForegroundColor Yellow }

# ---------------------------------------------------------------- 2. serverInfo (sem login)
Write-Section '2. serverInfo (sem credenciais): confirma que o endereco e um Jira e qual o tipo/versao'
$r = Invoke-Raw 'GET' "$api/serverInfo"
if ($r.Status -eq 401 -or $r.Status -eq 403) { Write-Host '    OBS: este Jira exige login ate para serverInfo; isso e normal. A prova de verdade e a etapa 3.' -ForegroundColor Yellow }
else { $h = Get-Hint $r; if ($h) { Write-Host "    DICA: $h" -ForegroundColor Yellow } }
if ($r.Ok -and $r.Json) {
  Write-Host "    Servidor: $($r.Json.serverTitle) | versao $($r.Json.version) | deploymentType $($r.Json.deploymentType) | baseUrl $($r.Json.baseUrl)" -ForegroundColor Green
  $det = "versao $($r.Json.version), $($r.Json.deploymentType)"
  if ($r.Json.deploymentType -eq 'Cloud' -and $Flavor -ne 'cloud') { Write-Host '    ATENCAO: o servidor e Cloud mas o script esta como dc. Rode com -Flavor cloud.' -ForegroundColor Red }
  if ($r.Json.deploymentType -ne 'Cloud' -and $Flavor -eq 'cloud') { Write-Host '    ATENCAO: o servidor NAO e Cloud. Rode com -Flavor dc.' -ForegroundColor Red }
  if ($r.Json.baseUrl -and ($r.Json.baseUrl.TrimEnd('/') -ne $BaseUrl)) { Write-Host "    OBS: o Jira se anuncia como $($r.Json.baseUrl); use essa URL se der redirecionamento." -ForegroundColor Yellow }
  Add-Step '2 serverInfo' 'OK' $det
} elseif ($r.Status -eq 401 -or $r.Status -eq 403) { Add-Step '2 serverInfo' 'AVISO' "HTTP $($r.Status) (exige login; normal)" }
else { Add-Step '2 serverInfo' 'FALHOU' $(if ($r.Error) { $r.Error } else { "HTTP $($r.Status)" }) }

# ---------------------------------------------------------------- 3. login
Write-Section '3. LOGIN: GET /myself (prova que a credencial funciona)'
if ($AuthMode -eq 'auto') { $AuthMode = if ($Flavor -eq 'cloud') { 'basic' } else { 'bearer' } }
if (-not $Token) {
  $sec = Read-Host 'Token (API token no Cloud / Personal Access Token no Data Center) - nao aparece na tela' -AsSecureString
  $Token = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto([System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec))
}
if ($AuthMode -eq 'basic' -and -not $Email) { $Email = Read-Host 'E-mail (Cloud) ou usuario (Data Center) para autenticacao Basic' }
Write-Host "Modo de autenticacao: $AuthMode"
$auth = @{}
if ($AuthMode -eq 'basic') {
  $auth['Authorization'] = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${Email}:${Token}"))
} else { $auth['Authorization'] = "Bearer $Token" }
if ($Token.Length -lt 10) { Write-Host "    ATENCAO: o token tem so $($Token.Length) caracteres; parece truncado/errado." -ForegroundColor Yellow }
if ($Token -match '\s') { Write-Host '    ATENCAO: o token contem espacos/quebra de linha; copie de novo sem espacos.' -ForegroundColor Yellow }
$r = Invoke-Raw 'GET' "$api/myself" $auth
$h = Get-Hint $r; if ($h) { Write-Host "    DICA: $h" -ForegroundColor Yellow }
$loginOk = $r.Ok
if ($r.Ok -and $r.Json) {
  Write-Host "    Autenticado como: $($r.Json.displayName)  (name=$($r.Json.name) accountId=$($r.Json.accountId) email=$($r.Json.emailAddress))" -ForegroundColor Green
  Add-Step '3 login (/myself)' 'OK' "$($r.Json.displayName)"
} else { Add-Step '3 login (/myself)' 'FALHOU' $(if ($r.Error) { $r.Error } else { "HTTP $($r.Status)" }) }

if ($loginOk) {
  # ---------------------------------------------------------------- 4. projetos
  Write-Section '4. PROJETOS VISIVEIS para esta credencial (se vier vazio, a conta nao enxerga nenhum projeto)'
  $pUrl = if ($Flavor -eq 'cloud') { "$api/project/search?maxResults=10" } else { "$api/project" }
  $r = Invoke-Raw 'GET' $pUrl $auth
  $h = Get-Hint $r; if ($h) { Write-Host "    DICA: $h" -ForegroundColor Yellow }
  if ($r.Ok -and $r.Json) {
    $lst = if ($r.Json -is [System.Array]) { Get-Array $r.Json } else { Get-Array $r.Json.values }
    $cnt = $lst.Count
    Write-Host "    Projetos recebidos (ate 10 no Cloud): $cnt -> $(($lst | Select-Object -First 15 | ForEach-Object { $_.key }) -join ', ')" -ForegroundColor Green
    Add-Step '4 projetos' $(if ($cnt -gt 0) { 'OK' } else { 'VAZIO' }) "$cnt projetos"
  } else { Add-Step '4 projetos' 'FALHOU' $(if ($r.Error) { $r.Error } else { "HTTP $($r.Status)" }) }

  # ---------------------------------------------------------------- 5. usuario
  Write-Section "5. LOCALIZAR O USUARIO '$User' (Cloud usa accountId; Data Center usa username)"
  $ref = $UserRef
  if (-not $ref) {
    if ($Flavor -eq 'cloud') {
      $r = Invoke-Raw 'GET' ("$api/user/search?query=" + [Uri]::EscapeDataString($User) + '&maxResults=10') $auth
      if ($r.Ok -and $r.Json) {
        $us = Get-Array $r.Json
        if ($us.Count -eq 0) { Write-Host "    Nenhum usuario encontrado para '$User'. Tente -User com parte do nome ou e-mail, ou passe -UserRef <accountId>." -ForegroundColor Red }
        else {
          $us | ForEach-Object { Write-Host "    candidato: $($_.displayName) | accountId=$($_.accountId) | ativo=$($_.active) | email=$($_.emailAddress)" }
          $pick = $us | Where-Object { $_.active } | Select-Object -First 1
          if (-not $pick) { $pick = $us[0] }
          $ref = $pick.accountId
          if ($us.Count -gt 1) { Write-Host "    ATENCAO: ha $($us.Count) candidatos; usando o primeiro ativo. Se nao for ele, rode com -UserRef <accountId>." -ForegroundColor Yellow }
        }
      }
    } else {
      $r = Invoke-Raw 'GET' ("$api/user?username=" + [Uri]::EscapeDataString($User)) $auth
      if ($r.Ok -and $r.Json) { $ref = $r.Json.name; Write-Host "    Usuario: $($r.Json.displayName) | name=$($r.Json.name) | key=$($r.Json.key) | ativo=$($r.Json.active)" -ForegroundColor Green }
      else {
        Write-Host '    Usuario nao encontrado pelo username exato; procurando parecidos...' -ForegroundColor Yellow
        $r2 = Invoke-Raw 'GET' ("$api/user/search?username=" + [Uri]::EscapeDataString($User) + '&maxResults=10') $auth
        $c2 = Get-Array $r2.Json
        if ($r2.Ok -and $c2.Count -gt 0) {
          $c2 | ForEach-Object { Write-Host "    candidato: $($_.displayName) | name=$($_.name) | ativo=$($_.active)" }
          $ref = $c2[0].name
        }
      }
    }
  } else { Write-Host "    Usando -UserRef informado: $ref" }
  if ($ref) { Add-Step '5 usuario' 'OK' $ref } else { Add-Step '5 usuario' 'NAO ACHOU' $User; Write-Host '    Sem usuario nao da para montar o JQL. Veja os candidatos/erros acima.' -ForegroundColor Red }

  # ---------------------------------------------------------------- 6. consulta
  if ($ref) {
    Write-Section "6. CONSULTA: tasks concluidas de '$User' nesta semana"
    if ($Since) { $monday = [datetime]::ParseExact($Since, 'yyyy-MM-dd', $null) }
    else { $d = Get-Date; $monday = $d.Date.AddDays(-(([int]$d.DayOfWeek + 6) % 7)) }
    $sinceTxt = $monday.ToString('yyyy-MM-dd')
    Write-Host "Inicio da semana usado: $sinceTxt (segunda-feira; use -Since AAAA-MM-DD para outro dia)"
    $jql = "assignee = `"$ref`" AND statusCategory = Done AND resolved >= `"$sinceTxt`" ORDER BY resolved DESC"
    Write-Host "JQL: $jql" -ForegroundColor Magenta
    $fields = @('summary', 'issuetype', 'status', 'resolution', 'resolutiondate', 'created', 'duedate', 'assignee', 'project')

    function Search-Issues([string]$q, [int]$max) {
      if ($Flavor -eq 'cloud') {
        $body = (@{ jql = $q; fields = $fields; maxResults = $max } | ConvertTo-Json -Depth 5 -Compress)
        $hdr = @{} + $auth; $hdr['Content-Type'] = 'application/json'
        $x = Invoke-Raw 'POST' "$api/search/jql" $hdr $body
        if ($x.Status -eq 404 -or $x.Status -eq 410) {
          Write-Host '    endpoint /search/jql indisponivel; tentando o antigo /search' -ForegroundColor Yellow
          $x = Invoke-Raw 'POST' "$api/search" $hdr $body
        }
        return $x
      } else {
        $qs = 'jql=' + [Uri]::EscapeDataString($q) + '&maxResults=' + $max + '&fields=' + ($fields -join ',')
        return (Invoke-Raw 'GET' "$api/search?$qs" $auth)
      }
    }
    function Show-Issues($j) {
      $rows = Get-Array $j.issues | ForEach-Object {
        [pscustomobject]@{
          Chave = $_.key; Tipo = $_.fields.issuetype.name; Status = $_.fields.status.name; Resolucao = $_.fields.resolution.name
          Resolvida = (Convert-JiraDate $_.fields.resolutiondate)
          Resumo = $(if ($_.fields.summary.Length -gt 60) { $_.fields.summary.Substring(0, 60) } else { $_.fields.summary })
        }
      }
      if ($rows) { $rows | Format-Table -AutoSize | Out-String -Width 250 | Write-Host }
      return $rows
    }

    $r = Search-Issues $jql 100
    $h = Get-Hint $r; if ($h) { Write-Host "    DICA: $h" -ForegroundColor Yellow }
    if ($r.Ok -and $r.Json) {
      $found = (Get-Array $r.Json.issues).Count
      $tot = if ($r.Json.total -ne $null) { $r.Json.total } else { '(Cloud nao informa total)' }
      Write-Host "RESULTADO: $found issues retornadas (total informado: $tot)" -ForegroundColor Green
      $rows = Show-Issues $r.Json
      if ($rows) { Write-Host 'Por tipo:'; $rows | Group-Object Tipo | Sort-Object Count -Descending | ForEach-Object { Write-Host "   $($_.Name): $($_.Count)" } }
      Add-Step '6 consulta da semana' $(if ($found -gt 0) { 'OK' } else { 'SEM RESULTADOS' }) "$found issues"
      if ($found -eq 0) {
        Write-Host ''
        Write-Host 'Nenhuma concluida na semana. Teste para saber se e problema de filtro ou de usuario: as 5 ultimas concluidas dele, sem filtro de data:' -ForegroundColor Yellow
        $q2 = "assignee = `"$ref`" AND statusCategory = Done ORDER BY resolved DESC"
        Write-Host "JQL: $q2" -ForegroundColor Magenta
        $r2 = Search-Issues $q2 5
        if ($r2.Ok -and $r2.Json) { [void](Show-Issues $r2.Json); Add-Step '6b ultimas concluidas (sem data)' 'OK' "$((Get-Array $r2.Json.issues).Count) issues" }
        else { Add-Step '6b ultimas concluidas (sem data)' 'FALHOU' "HTTP $($r2.Status)" }
        $q3 = "assignee = `"$ref`" ORDER BY updated DESC"
        Write-Host "E as 5 mais recentes em qualquer status: $q3" -ForegroundColor Magenta
        $r3 = Search-Issues $q3 5
        if ($r3.Ok -and $r3.Json) { [void](Show-Issues $r3.Json) }
      }
    } else {
      Add-Step '6 consulta da semana' 'FALHOU' $(if ($r.Error) { $r.Error } else { "HTTP $($r.Status)" })
      if ($r.Body -match 'errorMessages|errors') { Write-Host '    O Jira explicou o erro no corpo acima (ex.: usuario inexistente, JQL invalido, campo sem permissao).' -ForegroundColor Yellow }
    }
  }
} else {
  Write-Host ''
  Write-Host 'Sem login valido nao ha como continuar. Corrija a etapa 3 (veja a DICA acima) e rode de novo.' -ForegroundColor Red
}

# ---------------------------------------------------------------- resumo
Write-Section 'RESUMO DAS ETAPAS'
$script:steps | Format-Table -AutoSize | Out-String -Width 250 | Write-Host
$falhas = @($script:steps | Where-Object { $_.Resultado -match 'FALHOU|NAO ACHOU' })
if ($falhas.Count -eq 0) { Write-Host 'Todas as etapas responderam. Se o painel ainda nao carrega, o problema esta no painel/configuracao (equipes.yaml, status_map, JIRADASH_MODE), nao na conexao.' -ForegroundColor Green }
else { Write-Host "Primeira etapa com problema: $($falhas[0].Etapa) -> $($falhas[0].Detalhe)" -ForegroundColor Red }
Write-Host ''
Write-Host "Log completo gravado em: $logFile" -ForegroundColor Cyan
Write-Host 'Antes de compartilhar o log, revise: o token e ocultado, mas ha e-mails, nomes e titulos de tasks.' -ForegroundColor DarkYellow
} finally { try { Stop-Transcript | Out-Null } catch { } }
