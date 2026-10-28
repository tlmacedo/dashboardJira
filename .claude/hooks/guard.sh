#!/bin/sh
# Trava do projeto: lê o JSON do hook no stdin e bloqueia (exit 2 + mensagem) ações proibidas.
# Modos: jira (ferramentas MCP e comandos que chegam ao Jira) | arquivos (segredos e configs com dados reais)
#        | url / urlesq (texto de ferramenta de navegador/web: com e sem esquema; usado pelo adaptador do Cline)
#        | flag (só responde: saída 0 se a flag está ligada no app/.env, 1 se não).
# Regra do projeto: a flag JIRA_LIVE_ENABLED é a única trava do acesso ao Jira, para pessoas e IAs.
# Com ela diferente de true, nenhum acesso ao Jira por nenhum caminho; só o usuário liga a flag.
#
# Leitura da flag (Plano 14, Tarefa 0): o hook lê a flag do arquivo app/.env da RAIZ DO REPOSITÓRIO (raiz
# deduzida do caminho deste script, nunca de variável do ambiente), igual ao painel (ArquivoEnv.kt): só a
# linha da flag, NOME=valor, aspas e comentário final opcionais, a ÚLTIMA ocorrência vale. O ambiente do
# processo NÃO liga nem desliga a flag. Arquivo ausente, ilegível, sem a linha ou valor diferente de true
# (espaços nas pontas ignorados, sem diferenciar maiúsculas) = travado (falha fechada). O hook nunca imprime
# o conteúdo do arquivo; a IA continua proibida de ler, escrever ou versionar o arquivo.
#   - Travado: bloqueia ferramentas MCP da Atlassian, variáveis JIRA_* (exceto a própria flag), ENDEREÇOS de
#     Jira (ver abaixo), `jiradash check|sync|serve` e ligar a flag dentro do comando (atribuir 1 ou true à
#     flag no comando: a flag do comando nunca vale, só a do arquivo).
#   - Endereço de Jira (decisão de 28/10/2026): a palavra "jira" solta (pasta dashboardJira, nome de arquivo,
#     repositório, texto) NÃO bloqueia mais. Bloqueia só URL/host: (1) host (com ou sem esquema, porta,
#     userinfo, maiúsculas, escapes %, ponto final) com "jira" em algum rótulo; (2) host+caminho com "jira"
#     e "sidia" juntos; (3) host *.atlassian.net com "sidia" na URL. Documentação pública (atlassian.com e
#     subdomínios, como confluence.atlassian.com e docs.atlassian.com) é liberada; a decisão é pelo host
#     REAL (depois do userinfo), e URL aninhada em query (?redirect=...) é avaliada à parte. Barra invertida no
#     userinfo (x\@jira.corp) avalia os DOIS hosts; host sem ponto (curl jira/rest) conta junto de ferramenta de
#     comando; --connect-to/--resolve/--proxy/-x/Host:valor e git/rsync/scp/sftp/socat entram na regra.
#   - Liberado (flag true): as regras acima deixam de bloquear. Continua barrado exibir segredo no terminal
#     (echo/printf/printenv/env/set/declare citando JIRA_*TOKEN|PASS|SENHA|SECRET, ou env/printenv/set
#     canalizado para grep de "jira"). Usar o token como argumento de outro comando é liberado.
#   - Modo `arquivos`: não muda, com qualquer valor da flag.
#   - Nos DOIS modos, com qualquer valor da flag: o texto que a ferramenta grava (Write content, Edit
#     new_string, edits[].new_string, NotebookEdit new_source) não pode ligar a flag, em QUALQUER arquivo.
#
# Arquivos que só o usuário altera (modo arquivos; ler continua liberado): .claude/settings*.json, todo
# *compose*.y*ml (inclusive o app/docker-compose.yml comum) e *compose*override*: todos ligam a flag.
#   - Write/Edit: o caminho é NORMALIZADO antes de casar (minúsculas, '\' -> '/', '//' colapsado, '.' e '..'
#     resolvidos, ponto/espaço final de cada segmento removido, prefixo de dispositivo e unidade retirados,
#     ':' depois da unidade bloqueia, nome curto 8.3 no arquivo, em CLAUDE~1 ou dentro de .claude bloqueia;
#     caminho relativo é resolvido contra o cwd do payload).
#   - Bash: NEGA POR PADRÃO. Se o comando cita esses arquivos (ou .claude junto de settings, que cobre
#     `cd .claude` + escrita em settings*), só passa se todo trecho for leitura conhecida (lista em LEITORES
#     e as ressalvas de less/sed/awk/git/docker) e não houver redirecionamento de escrita.
#
# Como o usuário liga a flag do hook (nunca o assistente): editando a linha da flag em app/.env (a mesma chave
# do painel: uma só trava). Vale já no próximo comando, sem reabrir a sessão.
#
# Limite honesto: é uma proteção contra ações acidentais do assistente, não uma fronteira de segurança contra
# quem quer burlar. Controle de rede de verdade exige sandbox ou lista de destinos permitidos no ambiente.
# Limites que NÃO serão fechados: (1) com a flag true, formas indiretas de exibir segredo (bash -c, env sem
# pipe, set -x, cat .env etc.) não são todas cobertas; (2) o WebFetch do Claude só passa por este hook se o
# usuário incluir WebFetch no matcher do .claude/settings.json (o modo jira já olha tool_input.url);
# (2b) host montado em tempo de execução (variável, concatenação, DNS/redirecionamento do servidor) e host
# sem esquema fora de comando de rede (ex.: dentro de python -c) não são vistos;
# (3) com a flag false, atribuição calculada da flag (=$'true', ${X:-true}) não é coberta; (4) nomes montados
# por variável ou glob (cp x .cl*/set*, F=.cla; cp x ${F}ude/...) e interpretador que monta o caminho sozinho
# não são vistos; (5) falsos positivos aceitos: settings*.json de qualquer pasta (.vscode/settings.json),
# '>' dentro de aspas num comando que cita esses arquivos, e documentação que escreve a flag ligada na forma
# de atribuição (escreva "a flag em true"); (6) o matcher do .claude/settings.json decide quais ferramentas
# passam por aqui (hoje Write|Edit|NotebookEdit|Bash); só o usuário o altera.
modo="$1"
negar() { echo "BLOQUEADO: $1" >&2; exit 2; }

# Programa awk que lê a linha da flag do arquivo de variáveis (espelho de ArquivoEnv.interpretar, só a flag).
AWK_FLAG='
function corta(s) { gsub(/^[ \t]+/, "", s); gsub(/[ \t]+$/, "", s); return s }
{
  l = $0
  sub(/\r$/, "", l)
  l = corta(l)
  if (substr(l, 1, 3) == "\357\273\277") l = substr(l, 4)
  if (l == "" || substr(l, 1, 1) == "#") next
  if (substr(l, 1, 7) == "export ") l = substr(l, 8)
  l = corta(l)
  i = index(l, "=")
  if (i <= 1) next
  if (corta(substr(l, 1, i - 1)) != "JIRA_LIVE_ENABLED") next
  x = corta(substr(l, i + 1))
  c = substr(x, 1, 1)
  if (length(x) >= 2 && (c == "\042" || c == "\047") && substr(x, length(x), 1) == c) {
    x = substr(x, 2, length(x) - 2)
  } else {
    j = index(x, " #")
    if (j > 0) x = substr(x, 1, j - 1)
    x = corta(x)
  }
  v = x; achou = 1
}
END { if (achou) print v }
'

# Raiz do repositório pelo caminho deste script (.claude/hooks/guard.sh -> ../..); nunca por variável de ambiente.
raiz_repo=$(cd "$(dirname "$0")/../.." 2>/dev/null && pwd) || raiz_repo=""
# Valor da flag lido SÓ de <raiz>/app/.env: sem espaços nas pontas e em minúsculas (espaço no meio, como
# "tr ue", NÃO vira true). Qualquer problema (sem raiz, sem arquivo, ilegível, awk falhou) = vazio = travado.
flag=""
if [ -n "$raiz_repo" ] && [ -f "$raiz_repo/app/.env" ] && [ -r "$raiz_repo/app/.env" ]; then
  flag=$(LC_ALL=C awk "$AWK_FLAG" "$raiz_repo/app/.env" 2>/dev/null | tail -n 1 | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//' | tr '[:upper:]' '[:lower:]') || flag=""
fi
# modo flag: responde só pelo código de saída (0 = ligada), sem imprimir nada
if [ "$modo" = "flag" ]; then
  [ "$flag" = "true" ] && exit 0
  exit 1
fi
payload=$(cat)
MSG_TRAVA="com a flag JIRA_LIVE_ENABLED desligada nenhum acesso ao Jira; só o usuário liga a flag"

# Programa awk que decide se um texto cita endereço de Jira (saída 3 = cita; 0 = não cita). Entrada: o texto.
# Variável rede: 1 = procura também host sem esquema em qualquer palavra; 0 = só URLs com esquema;
# auto = procura host sem esquema só se o texto tiver ferramenta de rede (curl, wget, ssh, iwr...).
AWK_URL='
function decodifica(s,   r, c, hex) {
  hex = "0123456789abcdef"; r = ""
  while (match(s, /%[0-9a-f][0-9a-f]/)) {
    c = (index(hex, substr(s, RSTART + 1, 1)) - 1) * 16 + (index(hex, substr(s, RSTART + 2, 1)) - 1)
    r = r substr(s, 1, RSTART - 1) ((c > 32 && c < 127) ? sprintf("%c", c) : " ")
    s = substr(s, RSTART + RLENGTH)
  }
  return r s
}
function termina(h, suf) { return length(h) > length(suf) && substr(h, length(h) - length(suf) + 1) == suf }
# um candidato a host de uma autoridade: tira o userinfo (até o ÚLTIMO @), corta na barra invertida e na porta.
# Define TEMUSER e TEMPORTA; devolve o host ("" se for IPv6 entre colchetes).
function host_de(a,   i) {
  TEMUSER = 0; TEMPORTA = 0
  while ((i = index(a, "@")) > 0) { a = substr(a, i + 1); TEMUSER = 1 }
  i = index(a, "\\")
  if (i > 0) a = substr(a, 1, i - 1)
  if (substr(a, 1, 1) == "[") return ""
  i = index(a, ":")
  if (i > 0) { TEMPORTA = 1; a = substr(a, 1, i - 1) }
  sub(/\.+$/, "", a)
  return a
}
# separa "userinfo@host:porta/caminho" em CH[1..NH] (hosts candidatos), RESTO, TEMPORTA, TEMUSER.
# A autoridade termina só em / ? # ; a barra invertida fica DENTRO dela (curl trata "x\@jira.corp" como host
# jira.corp; navegadores tratam "\" como "/"). Havendo "\", os DOIS hosts (o do curl e o de antes da barra)
# são candidatos, e o texto depois da primeira barra invertida entra no RESTO.
function separa(u,   n, auth, b, tp, tu) {
  n = match(u, /[\/?#]/)
  if (n == 0) { auth = u; RESTO = "" } else { auth = substr(u, 1, n - 1); RESTO = substr(u, n) }
  NH = 0
  CH[++NH] = host_de(auth); tp = TEMPORTA; tu = TEMUSER
  b = index(auth, "\\")
  if (b > 0) {
    CH[++NH] = host_de(substr(auth, 1, b - 1)); tp = tp || TEMPORTA; tu = tu || TEMUSER
    RESTO = substr(auth, b) RESTO
  }
  TEMPORTA = tp; TEMUSER = tu
}
# 1 = endereço de Jira (bloquear)
function decide(host, rest,   n, i, p, hp) {
  if (host == "") return 0
  if (host == "atlassian.com" || termina(host, ".atlassian.com")) {
    # documentação pública; mas "@" ou "\" no resto com jira pode esconder outro host
    if (rest ~ /[@\\]/ && rest ~ /jira/) return 1
    return 0
  }
  n = split(host, p, ".")
  for (i = 1; i <= n; i++) if (p[i] ~ /jira/) return 1
  hp = rest; sub(/[?#].*$/, "", hp); hp = host hp
  if (hp ~ /jira/ && hp ~ /sidia/) return 1
  if ((host == "atlassian.net" || termina(host, ".atlassian.net")) && (host rest) ~ /sidia/) return 1
  return 0
}
function base(w) { sub(/^.*[\/\\]/, "", w); sub(/\.exe$/, "", w); return w }
# host sem ponto/porta/usuario: só vale se um rótulo (separado por . ou -) for exatamente "jira"
# (jira, jira-lab, meu-jira); "dashboardjira" e "jira.md" (com ponto) seguem as outras regras.
function estrito(h,   n, m, i, k, P, Q) {
  n = split(h, P, ".")
  for (i = 1; i <= n; i++) { m = split(P[i], Q, "-"); for (k = 1; k <= m; k++) if (Q[k] == "jira") return 1 }
  return 0
}
# valor "a:b:c:d" de --connect-to / --resolve: examina o 1o e o 3o campo como hosts
function campos(w,   F, f, j) {
  split(w, F, ":")
  for (j = 1; j <= 3; j += 2) {
    f = F[j]; sub(/^\+/, "", f); sub(/\.+$/, "", f)
    if (f != "" && decide(f, "")) bloq = 1
  }
}
BEGIN {
  n = split("curl wget http https httpie xh nc ncat telnet ssh scp sftp ftp openssl socat aria2c lynx w3m rsync invoke-webrequest invoke-restmethod iwr irm start-bitstransfer bitsadmin certutil test-netconnection tnc resolve-dnsname nslookup ping dig", L, " ")
  for (i = 1; i <= n; i++) REDE[L[i]] = 1
  n = split("ssh scp sftp nc ncat telnet ftp socat ping dig nslookup tnc test-netconnection resolve-dnsname", L, " ")
  for (i = 1; i <= n; i++) ALVO[L[i]] = 1
  # subcomandos do git que falam com um servidor (git entra como ferramenta de rede só com eles)
  n = split("clone fetch pull push remote ls-remote submodule", L, " ")
  for (i = 1; i <= n; i++) GITSUB[L[i]] = 1
}
{ texto = texto " " $0 }
END {
  t = tolower(texto)
  t = decodifica(decodifica(decodifica(t)))
  bloq = 0
  # A) URLs com esquema (a URL aninhada na query é varrida à parte)
  s = t
  while (match(s, /[a-z][a-z0-9+.-]*:\/\/[^ \t\042\047<>`|;&()]*/)) {
    m = substr(s, RSTART, RLENGTH)
    k = index(m, "://")
    pos = RSTART + k + 2
    separa(substr(m, k + 3))
    for (c = 1; c <= NH; c++) if (CH[c] != "" && decide(CH[c], RESTO)) bloq = 1
    s = substr(s, pos)
  }
  # B) host sem esquema
  r = (rede == "1") ? 1 : 0
  nw = split(t, W, /[ \t\042\047`;|&()<>,=]+/)
  if (rede == "auto") {
    for (i = 1; i <= nw; i++) if ((base(W[i])) in REDE) r = 1
    if (t ~ /webclient|httpclient|xmlhttp|winhttp/) r = 1
    # git só conta com subcomando que fala com servidor (clone, fetch, pull, push, remote, ls-remote, submodule)
    for (i = 1; i <= nw; i++) if (base(W[i]) == "git") {
      for (j = i + 1; j <= i + 6 && j <= nw; j++) if (W[j] in GITSUB) r = 1
    }
  }
  # com ferramenta de rede, todas as palavras; sem ela, só as de forma usuario@host (git clone git@host:repo)
  if (nw > 0) {
    janela = 0
    ult = 0     # posição da última ferramenta de comando (curl, wget...); no modo 1 (texto de URL) vale sempre
    for (i = 1; i <= nw; i++) {
      w = W[i]
      if (w == "") continue
      if (((base(w)) in REDE) && w != "http" && w != "https") ult = i
      if (!r && index(w, "@") == 0) continue
      # valor de opção que é arquivo ou corpo (curl -o jira-saida.txt, -d, --data...) não é host
      salta = (ant ~ /^(-o|--output|--output-document|--upload-file|-d|--data|--data-raw|--data-binary|--cacert|--cert|--key|--config|-outfile|-infile|-body|-path|-filepath)$/)
      prev = ant
      ant = w
      if (salta) continue
      if ((base(w)) in ALVO) { janela = 4; continue }
      # --connect-to a:b:c:d e --resolve host:porta:endereço: campos separados por ":"
      if (prev ~ /^(--connect-to|--resolve)$/) { campos(w); continue }
      # valor de opção que é um host (proxy), "-xHOST" colado e cabeçalho Host:valor sem espaço
      forca = (prev ~ /^(-x|--proxy|--preproxy|--socks4a?|--socks5(-hostname)?|--haproxy-[a-z-]+)$/)
      if (w ~ /^-x[^-]/) { w = substr(w, 3); forca = 1 }
      if (w ~ /^(host|x-forwarded-host|x-original-host|forwarded-host):/) { w = substr(w, index(w, ":") + 1); forca = 1 }
      # socat: TCP:host:porta, SSL:host:porta...
      if (w ~ /^(tcp|tcp4|tcp6|tcp-connect|udp|udp4|udp6|ssl|openssl|openssl-connect|socks4a?):/) w = substr(w, index(w, ":") + 1)
      if (w == "") continue
      if (w ~ /:\/\//) continue
      if (w ~ /^-/ || w ~ /^[\/.~$]/ || index(w, "\\") > 0 || w ~ /^[a-z]:[\/\\]/ || w ~ /^[0-9]+$/) continue
      separa(w)
      alvo = (janela > 0) || forca
      if (janela > 0) janela--
      for (c = 1; c <= NH; c++) {
        h = CH[c]
        if (h == "") continue
        if (alvo || index(h, ".") > 0 || TEMPORTA || TEMUSER) { if (decide(h, RESTO)) bloq = 1 }
        # host sem ponto, porta nem usuario (curl jira/rest, wget jira, iwr jira): só com ferramenta de comando
        # nas 10 palavras anteriores (git não entra: evita bloquear nome de ramo como jira-fix)
        else if ((rede == "1" || (ult > 0 && i - ult <= 10)) && estrito(h)) bloq = 1
      }
    }
  }
  exit(bloq ? 3 : 0)
}
'
# 0 = o texto cita endereço de Jira (ou o awk falhou: falha fechada); 1 = não cita. $1 = valor de `rede`.
cita_url_jira() {
  printf '%s\n' "$2" | LC_ALL=C awk -v rede="$1" "$AWK_URL" >/dev/null 2>&1
  [ $? -eq 0 ] && return 1
  return 0
}
NL='
'
CR=$(printf '\r')

# caminhos protegidos (com ou sem barra inicial); .env.example e .env.sample ficam de fora
PROTEGIDOS='(^|[[:space:]"'"'"'=/])(\.env(\.[A-Za-z0-9_-]+)?|config/(equipes|ausencias|papeis|squads)\.yaml)([[:space:]"'"'"';|&)]|$)'
# escrita, cópia, remoção ou versionamento (para .env e configs reais)
ESCRITA='(>|>>|tee[[:space:]]|sed[[:space:]]+(-[a-z]*i|--in-place)|(^|[[:space:];&|])(cp|mv|rm|ln|truncate|dd|install|chmod|python[0-9.]*|perl|node|ruby|php|pwsh|powershell|patch|rsync)[[:space:]]|git[[:space:]]+(add|commit|stash|checkout|restore|rm|mv|apply))'
# texto que liga a flag: JIRA_LIVE_ENABLED, depois ]/aspas/espaços/vírgula/:/= (e o -Value do PowerShell),
# valor 1 ou true. Usado SEM diferenciar maiúsculas no conteúdo gravado e em comando que escreve arquivo.
LIGA_FLAG='JIRA_LIVE_ENABLED[]"'"'"'[:space:],:=]+(-value[[:space:]]+)?["'"'"']?[[:space:]]*(1|true)'
# escrita genérica em comando (para o texto que liga a flag)
ESCRITA_GERAL='>|tee[[:space:]]|sed[[:space:]]+(-[a-z]*i|--in-place)|(^|[[:space:];&|(])(cp|mv|dd|install|python[0-9.]*|perl|node|ruby|php|pwsh|powershell|patch|rsync|set-content|add-content|out-file)[[:space:]]'
# 2>&1, >&2 e saída para /dev/null não gravam arquivo
REDIR_INOFENSIVA='s/[0-9]*>&[0-9-]+/ /g; s#[0-9&]*>>?[[:space:]]*/dev/null# #g'
# citações (comando já normalizado: minúsculas, '/', sem '//' nem '/./') dos arquivos que só o usuário altera
CITA_SETTINGS='settings[^[:space:]"'"'"';|&)<>/]*\.json'
CITA_CLAUDE='\.claude|claude~[0-9]'
CITA_SETTINGS_NOME='settings|settin~[0-9]'
CITA_COMPOSE='compose[^[:space:]"'"'"';|&)<>/]*(\.y[a-z]*ml|override)|(docker|compos)[a-z0-9_-]{0,6}~[0-9]'

eh_modelo() { case "$1" in .env.example|.env.sample) return 0 ;; *) return 1 ;; esac; }

# Texto gravado pela ferramenta (Write/Edit/MultiEdit/NotebookEdit): não pode ligar a flag, em nenhum arquivo.
# Falha fechada: JSON ilegível nega.
checar_conteudo() {
  conteudo=$(printf '%s' "$payload" | jq -r '[.tool_input.content?, .tool_input.new_string?, (.tool_input.edits? // [] | .[]? | .new_string?), .tool_input.new_source?] | map(strings) | join("\n")') ||
    negar "erro ao ler o conteúdo da ferramenta (falha fechada)."
  if [ -n "$conteudo" ] && printf '%s' "$conteudo" | grep -Eiq "$LIGA_FLAG"; then
    negar "a ferramenta grava texto que liga JIRA_LIVE_ENABLED (em qualquer arquivo). Só o usuário liga a flag."
  fi
}

# Normaliza um caminho. Resultado em $norm (segmentos unidos por '/', sem barra inicial); se o caminho deve
# ser bloqueado de saída (quebra de linha, ':', nome curto 8.3), o motivo fica em $motivo.
normalizar() {
  norm=""; motivo=""
  case "$1" in *"$NL"*|*"$CR"*) motivo="caminho com quebra de linha"; return ;; esac
  n=$(printf '%s' "$1" | tr '\\' '/' | tr '[:upper:]' '[:lower:]' | sed -E 's#^//[?.]/unc/#//#; s#^(//[?.]/|/[?][?]/)##; s#^[a-z]:##')
  case "$n" in *:*) motivo="caminho com ':' (fluxo alternativo do NTFS ou dispositivo)"; return ;; esac
  ifs_antigo=$IFS
  set -f
  IFS=/
  for seg in $n; do
    IFS=$ifs_antigo
    # espaços nas pontas; '.' e vazio somem; '..' volta um nível
    seg="${seg#"${seg%%[![:space:]]*}"}"
    seg="${seg%"${seg##*[![:space:]]}"}"
    case "$seg" in
      ''|.) continue ;;
      ..) case "$norm" in */*) norm="${norm%/*}" ;; *) norm="" ;; esac; continue ;;
    esac
    # o Windows ignora pontos e espaços no fim do nome (.env. e ".env " = .env)
    while :; do case "$seg" in *[.[:space:]]) seg="${seg%?}" ;; *) break ;; esac; done
    [ -n "$seg" ] || continue
    norm="${norm:+$norm/}$seg"
  done
  # nome curto 8.3: da pasta .claude (claude~1), de qualquer segmento dentro dela, ou do nome do arquivo
  sob_claude=""
  IFS=/
  for seg in $norm; do
    case "$seg" in claude~[0-9]*) motivo="nome curto 8.3 ($seg) pode ser a pasta .claude" ;; esac
    if [ -n "$sob_claude" ]; then
      case "$seg" in *~[0-9]*) motivo="nome curto 8.3 ($seg) dentro de .claude" ;; esac
    fi
    if [ "$seg" = .claude ]; then sob_claude=1; fi
  done
  IFS=$ifs_antigo
  set +f
  case "${norm##*/}" in *~[0-9]*) motivo="nome curto 8.3 (${norm##*/}) pode apontar para arquivo protegido" ;; esac
}

# Bash: se o comando cita settings do Claude, compose ou override, NEGA POR PADRÃO; só passa leitura conhecida.
so_usuario_comando() {
  t=$(printf '%s' "$1" | tr '\\' '/' | tr '[:upper:]' '[:lower:]' | sed -E 's#/+#/#g; s#/\./#/#g; s#/\./#/#g')
  cita=""
  if printf '%s' "$t" | grep -Eq "$CITA_SETTINGS|$CITA_COMPOSE"; then
    cita=1
  elif printf '%s' "$t" | grep -Eq "$CITA_CLAUDE" && printf '%s' "$t" | grep -Eq "$CITA_SETTINGS_NOME"; then
    cita=1
  fi
  [ -n "$cita" ] || return 0
  msg="comando cita settings do Claude, compose ou override do compose e não é leitura conhecida: só o usuário altera esses arquivos"
  s=$(printf '%s' "$t" | sed -E "$REDIR_INOFENSIVA")
  case "$s" in *">"*) negar "$msg (redirecionamento)." ;; esac
  # trechos separados por ; & | ( ) ` e quebra de linha; cada um precisa começar por leitura conhecida
  # (chaves não separam, para não partir scripts de awk; `{ cp ...; }` começa por '{' e é negado)
  trechos=$(printf '%s\n' "$s" | tr ';&|()`' '\n\n\n\n\n\n')
  while IFS= read -r g; do
    g="${g#"${g%%[![:space:]]*}"}"
    [ -n "$g" ] || continue
    # atribuições no início (VAR=valor cmd) não são o comando
    g=$(printf '%s' "$g" | sed -E 's/^([a-z_][a-z0-9_]*=[^[:space:]]*[[:space:]]+)+//')
    w="${g%%[[:space:]]*}"
    w=$(printf '%s' "$w" | tr -d "\"'")
    w="${w#\\}"
    w="${w##*/}"
    w="${w%.exe}"
    case "$w" in ''|'$') continue ;; esac
    case "$w" in
      cat|ls|grep|egrep|fgrep|rg|head|tail|diff|wc|stat|file|more|jq|cmp|readlink|realpath|basename|dirname|nl|shasum|sha256sum|md5|md5sum) ;;
      cd|pushd|popd|echo|printf|true|test) ;;
      type|get-content|gc|select-string|sls|findstr) ;;
      less)
        if printf '%s' "$g" | grep -Eq '(^|[[:space:]])(-[a-z]*o|--log-file)'; then negar "$msg (less -o grava arquivo)."; fi ;;
      sed)
        if printf '%s' "$g" | grep -Eq '(^|[[:space:]])(-[a-z]*i|--in-place)'; then negar "$msg (sed -i)."; fi
        # comandos w/W (grava) e e (executa) no script, depois de tirar as opções
        if printf '%s' "$g" | sed -E 's/[[:space:]]-[a-z]+/ /g' | grep -Eq "[;{}/0-9\$[:space:]'\"][we]([[:space:]'\"]|\$)"; then
          negar "$msg (sed com w ou e)."
        fi ;;
      awk|gawk|nawk)
        if printf '%s' "$g" | grep -Eq 'system|inplace|(^|[[:space:]])-i'; then negar "$msg (awk com system ou -i)."; fi ;;
      git)
        if printf '%s' "$g" | grep -q -- '--output'; then negar "$msg (git --output)."; fi
        sub=$(printf '%s' "$g" | sed -E 's/^git//; s/^([[:space:]]+(-c[[:space:]]+[^[:space:]]+|-[^[:space:]]+))*//; s/^[[:space:]]+//')
        sub="${sub%%[[:space:]]*}"
        case "$sub" in
          diff|log|show|status|blame|ls-files|grep|rev-parse|cat-file|shortlog) ;;
          *) negar "$msg (git $sub)." ;;
        esac ;;
      docker|docker-compose)
        if printf '%s' "$g" | grep -Eq '(^|[[:space:]])(cp|-o|--output)([[:space:]]|=|$)'; then negar "$msg (docker cp/-o)."; fi ;;
      *) negar "$msg ($w)." ;;
    esac
  done <<EOF
$trechos
EOF
}

case "$modo" in
jira)
  ferramenta=$(printf '%s' "$payload" | jq -r '.tool_name // ""')
  cmd=$(printf '%s' "$payload" | jq -r '.tool_input.command // ""')
  checar_conteudo
  if [ "$flag" = "true" ]; then
    # Flag ligada pelo usuário no ambiente: as regras de acesso não valem. Só barra exibir segredo no terminal
    # (echo/printf/printenv/env/set/declare no início ou depois de ; && || | ( com variável de segredo do Jira).
    if printf '%s' "$cmd" | grep -Eq '(^|[;&|(`])[[:space:]]*(echo|printf|printenv|env|set|declare)[[:space:]]([^;&|]*)JIRA_[A-Za-z0-9_]*(TOKEN|PASS|SENHA|SECRET)'; then
      negar "comando exibe token ou senha do Jira no terminal (vale mesmo com a flag em true). Use o token só como argumento de outro comando."
    fi
    # env/printenv/set sem argumento canalizado para grep de "jira" despeja o ambiente com os tokens
    if printf '%s' "$cmd" | grep -Eq '(^|[;&|(`])[[:space:]]*(env|printenv|set)[[:space:]]*\|[[:space:]]*grep[^;&|]*[Jj][Ii][Rr][Aa]'; then
      negar "comando despeja o ambiente filtrando por jira e mostraria tokens (vale mesmo com a flag em true)."
    fi
  else
    case "$ferramenta" in
      mcp__*tlassian*) negar "ferramenta do Atlassian/Jira ($ferramenta): $MSG_TRAVA." ;;
    esac
    # 1) uso das credenciais/URL do Jira em qualquer comando: barra QUALQUER JIRA_<letras/números/sublinhado>
    #    exceto a própria flag (JIRA_LIVE_ENABLED), que tem a regra 4 (ligar dentro do comando = bloqueado).
    if printf '%s' "$cmd" | grep -Eo 'JIRA_[A-Za-z0-9_]+' | grep -qvx 'JIRA_LIVE_ENABLED'; then
      negar "comando usa variáveis de conexão do Jira: $MSG_TRAVA."
    fi
    # 2) endereço de Jira no comando (qualquer cliente): host com "jira" no rótulo, jira+sidia em host+caminho
    #    ou *.atlassian.net com sidia; documentação pública (atlassian.com) liberada. Host sem esquema só conta
    #    junto de ferramenta de rede. A palavra "jira" solta NÃO bloqueia.
    if cita_url_jira auto "$cmd"; then
      negar "comando referencia endereço de Jira: $MSG_TRAVA."
    fi
    # 3) WebFetch do Claude Code (se o matcher do settings o incluir): a URL pode vir sem esquema
    url_web=$(printf '%s' "$payload" | jq -r '.tool_input.url // ""')
    if [ -n "$url_web" ] && cita_url_jira 1 "$url_web"; then
      negar "URL de Jira ($ferramenta): $MSG_TRAVA."
    fi
    # 4) comandos do app que conectam ao Jira real e ligar a flag dentro do comando
    #    Reconhece também as formas JSON/YAML/dotenv (chave entre aspas, ':' ou '=', valor 1 ou true).
    if printf '%s' "$cmd" | grep -Eiq 'jiradash[[:space:]]+(check|sync|serve)|JIRA_LIVE_ENABLED["'"'"']?[[:space:]]*[:=][[:space:]]*["'"'"']?[[:space:]]*(1|true)'; then
      negar "comando que conecta ao Jira real ou liga a flag: $MSG_TRAVA."
    fi
  fi
  ;;
url|urlesq)
  # texto de ferramenta de navegador/web (adaptador do Cline): o texto vem cru no stdin.
  # url = procura host com e sem esquema; urlesq = só URLs com esquema (texto qualquer, sem falso positivo).
  # Com a flag ligada nada é bloqueado aqui.
  if [ "$flag" != "true" ]; then
    r=0
    [ "$modo" = "url" ] && r=1
    if cita_url_jira "$r" "$payload"; then
      negar "texto com endereço de Jira: $MSG_TRAVA."
    fi
  fi
  ;;
arquivos)
  checar_conteudo
  caminho=$(printf '%s' "$payload" | jq -r '.tool_input.file_path // .tool_input.notebook_path // ""') ||
    negar "erro ao ler o caminho (falha fechada)."
  if [ -n "$caminho" ]; then
    # caminho relativo: resolvido contra o cwd do payload (cd .claude e depois settings.local.json)
    cwd=$(printf '%s' "$payload" | jq -r '.cwd // ""')
    case "$caminho" in
      /*|\\*|[A-Za-z]:*) ;;
      *) [ -z "$cwd" ] || caminho="$cwd/$caminho" ;;
    esac
    normalizar "$caminho"
    [ -z "$motivo" ] || negar "escrita em $caminho: $motivo."
    base="${norm##*/}"
    # arquivos que ligam a flag (settings do Claude, compose e override): só o usuário edita, com qualquer conteúdo
    case "/$norm" in
      */.claude/settings*.json) negar "escrita em $base: só o usuário altera esse arquivo (ele pode ligar JIRA_LIVE_ENABLED)." ;;
    esac
    case "$base" in
      *compose*override*|*compose*.y*ml) negar "escrita em $base: só o usuário altera o compose (ele repassa JIRA_LIVE_ENABLED ao painel)." ;;
    esac
    if ! eh_modelo "$base"; then
      case "$base" in
        .env|.env.*) negar "edição de arquivo de ambiente ($base): contém segredos." ;;
      esac
    fi
    case "/$norm" in
      */config/equipes.yaml|*/config/ausencias.yaml|*/config/papeis.yaml|*/config/squads.yaml)
        negar "config com dados pessoais reais ($base). Edite só os modelos .exemplo/.demo." ;;
    esac
  fi
  cmd=$(printf '%s' "$payload" | jq -r '.tool_input.command // ""')
  if [ -n "$cmd" ]; then
    # tokens exatamente iguais aos modelos não contam como protegidos
    sem_modelos=$(printf '%s' "$cmd" | sed -E 's/(^|[[:space:]"'"'"'=/])\.env\.(example|sample)([[:space:]"'"'"';|&)]|$)/\1 \3/g')
    if printf '%s' "$sem_modelos" | grep -Eq "$PROTEGIDOS"; then
      if printf '%s' "$sem_modelos" | grep -Eq "$ESCRITA"; then
        negar "comando altera ou versiona segredo/config com dados reais."
      fi
    fi
    # settings do Claude, compose e override: nega por padrão (ler continua liberado)
    so_usuario_comando "$cmd"
    # gravar texto que liga a flag em qualquer arquivo (vale com qualquer valor da flag)
    if printf '%s' "$cmd" | grep -Eiq "$LIGA_FLAG" &&
       printf '%s' "$cmd" | sed -E "$REDIR_INOFENSIVA" | grep -Eiq "$ESCRITA_GERAL"; then
      negar "comando grava texto que liga JIRA_LIVE_ENABLED num arquivo. Só o usuário liga a flag."
    fi
    # git add forçado ignora o .gitignore
    # sem aspas; -f isolado ou agrupado (-fA, -Af) e --force/--fo/--forc, sempre no início de um argumento
    sem_aspas=$(printf '%s' "$cmd" | tr -d "\"'")
    if printf '%s' "$sem_aspas" | grep -Eq 'git[[:space:]]+add[[:space:]]([^;&|]*[[:space:]])?(-[A-Za-z]*f[A-Za-z]*|--fo[a-z]*)([[:space:]=]|$)'; then
      negar "git add forçado ignora o .gitignore (segredos e configs reais)."
    fi
  fi
  ;;
esac
exit 0
