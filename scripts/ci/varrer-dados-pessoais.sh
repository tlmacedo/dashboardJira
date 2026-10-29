#!/bin/sh
# Varredura de dados pessoais e segredos. Roda igual no CI e na máquina (inclusive no Git Bash do Windows).
#
# Uso: sh scripts/ci/varrer-dados-pessoais.sh [--base <ref>] [--arvore]
#   modo PR (padrão, --base origin/main): olha só as linhas ADICIONADAS por `git diff <base>...HEAD` e as
#     mensagens de `git log <base>..HEAD`. E-mail de autor nunca é varrido. Localmente rode antes `git fetch origin main`.
#   --arvore: olha todos os arquivos versionados (`git ls-files`); docs/superpowers/ fica fora do conteúdo.
#
# Saída: uma linha `ACHADO <categoria> <arquivo>:<linha>` por achado (mensagem de commit: `commit:<hash curto>`;
# arquivo proibido: linha 0) e, por fim, `VARREDURA OK` ou `VARREDURA FALHOU: <n> achado(s)`.
# NUNCA imprime o texto encontrado. Código de saída: 0 sem achados, 1 com achados, 2 em erro de uso.
#
# Categorias: ARQUIVO_PROIBIDO, EMAIL, SEGREDO, HOST_INTERNO, NOME_PROIBIDO (ver docs/operacao/ci.md).
# Nomes proibidos: scripts/ci/nomes-proibidos.local.txt (git-ignorado) ou a variável VARREDURA_NOMES (um por linha);
# VARREDURA_NOMES_ARQUIVO troca o caminho do arquivo (usado pelos testes).
# Só POSIX: sh, grep -E com classes POSIX, sed, awk e git (sem Python, sem jq).

aqui=$(cd "$(dirname "$0")" && pwd)

uso() {
  echo "uso: sh scripts/ci/varrer-dados-pessoais.sh [--base <ref>] [--arvore]" >&2
  exit 2
}

base="origin/main"
arvore=0
while [ $# -gt 0 ]; do
  case "$1" in
    --base) [ $# -ge 2 ] || uso; base="$2"; shift 2 ;;
    --arvore) arvore=1; shift ;;
    *) uso ;;
  esac
done

raiz=$(git rev-parse --show-toplevel 2>/dev/null) || { echo "erro: não está dentro de um repositório git" >&2; exit 2; }
cd "$raiz" || exit 2

if [ "$arvore" = 0 ]; then
  git rev-parse --verify --quiet "$base^{commit}" >/dev/null || {
    echo "erro: referência base inexistente (rode: git fetch origin main)" >&2
    exit 2
  }
fi

tmp=$(mktemp -d) || exit 2
trap 'rm -rf "$tmp"' EXIT HUP INT TERM
: > "$tmp/loc"        # local de cada linha de texto (arquivo:linha ou commit:hash)
: > "$tmp/txt"        # texto de cada linha, na mesma ordem de loc
: > "$tmp/achados"
: > "$tmp/arquivos"
: > "$tmp/nomes"
: > "$tmp/permitidos"

# --- entradas auxiliares ---------------------------------------------------------------------------------

# Lista de permissão (domínios de e-mail e hosts públicos), em minúsculas, sem comentários.
if [ -f "$aqui/padroes-permitidos.txt" ]; then
  tr -d '\r' < "$aqui/padroes-permitidos.txt" | grep -v '^[[:space:]]*#' | grep -v '^[[:space:]]*$' |
    tr 'A-Z' 'a-z' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' > "$tmp/permitidos"
fi

# Nomes proibidos: arquivo local (git-ignorado) e variável de ambiente.
arquivo_nomes="${VARREDURA_NOMES_ARQUIVO:-$aqui/nomes-proibidos.local.txt}"
{
  [ -f "$arquivo_nomes" ] && tr -d '\r' < "$arquivo_nomes"
  [ -n "$VARREDURA_NOMES" ] && printf '%s\n' "$VARREDURA_NOMES" | tr -d '\r'
} 2>/dev/null | grep -v '^[[:space:]]*$' > "$tmp/nomes"

minusculo() { printf '%s' "$1" | tr 'A-Z' 'a-z'; }

achado() { printf '%s %s\n' "$1" "$2" >> "$tmp/achados"; } # categoria, local

loc_de() { sed -n "${1}p" "$tmp/loc"; }

# Domínio/host permitido: terminações de exemplo, localhost, ou da lista (igual ou subdomínio).
dominio_permitido() {
  dp_d="$1"
  case "$dp_d" in
    *.invalid | *.example | localhost | 127.0.0.1) return 0 ;;
  esac
  while IFS= read -r dp_e; do
    [ -n "$dp_e" ] || continue
    [ "$dp_d" = "$dp_e" ] && return 0
    case "$dp_d" in *."$dp_e") return 0 ;; esac
  done < "$tmp/permitidos"
  return 1
}

# --- coleta do que será varrido -------------------------------------------------------------------------

if [ "$arvore" = 1 ]; then
  git -c core.quotepath=off ls-files > "$tmp/arquivos"
  while IFS= read -r arq; do
    [ -f "$arq" ] || continue
    case "$arq" in docs/superpowers/*) continue ;; esac
    grep -qI '' "$arq" 2>/dev/null || continue # binário ou vazio
    awk -v f="$arq" -v locf="$tmp/loc" -v txtf="$tmp/txt" '{ print f ":" FNR >> locf; print $0 >> txtf }' "$arq"
  done < "$tmp/arquivos"
else
  git -c core.quotepath=off diff --name-only --diff-filter=d "$base...HEAD" > "$tmp/arquivos"
  git -c core.quotepath=off diff -U0 --no-color --no-ext-diff "$base...HEAD" | awk -v locf="$tmp/loc" -v txtf="$tmp/txt" '
    /^diff --git / { dentro = 0; f = ""; next }
    dentro == 0 && /^\+\+\+ / {
      f = substr($0, 7)
      if ($0 == "+++ /dev/null") f = ""
      next
    }
    /^@@ / {
      dentro = 1
      match($0, /\+[0-9]+/)
      n = substr($0, RSTART + 1, RLENGTH - 1) + 0
      next
    }
    dentro == 1 && /^\+/ {
      if (f != "") { print f ":" n >> locf; print substr($0, 2) >> txtf }
      n++
      next
    }
  '
  # Mensagens de commit (o e-mail do autor não é varrido).
  for h in $(git rev-list "$base..HEAD"); do
    curto=$(git rev-parse --short "$h")
    git log -1 --format=%B "$h" | while IFS= read -r linha; do
      printf 'commit:%s\n' "$curto" >> "$tmp/loc"
      printf '%s\n' "$linha" >> "$tmp/txt"
    done
  done
fi

# --- categoria ARQUIVO_PROIBIDO --------------------------------------------------------------------------

while IFS= read -r arq; do
  [ -n "$arq" ] || continue
  nome=${arq##*/}
  case "$nome" in
    .env.example) ;;
    .env | .env.* | papeis.yaml | equipes.yaml | ausencias.yaml | squads.yaml | calendario.yaml | status_map.yaml | \
      jira_campos.yaml | vinculos.yaml | Filtros_Status_tool.txt | settings.local.json | \
      *.db | *.db-wal | *.db-shm | *.db.lock)
      achado ARQUIVO_PROIBIDO "$arq:0" ;;
  esac
done < "$tmp/arquivos"

# --- categoria EMAIL -------------------------------------------------------------------------------------

grep -a -n -o -E '[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}' "$tmp/txt" |
  while IFS= read -r l; do
    n=${l%%:*}
    m=${l#*:}
    parte=${m%@*}
    # Rótulos do Kotlin (this@Classe.metodo) não são e-mails.
    case "$parte" in this | return | super | break | continue) continue ;; esac
    dom=$(minusculo "${m#*@}")
    dominio_permitido "$dom" || achado EMAIL "$(loc_de "$n")"
  done

# --- categoria SEGREDO -----------------------------------------------------------------------------------

# Valor sem graça de segredo: placeholder, variável, máscara ou marcado como de teste.
valor_ignorado() {
  vi_v=$(minusculo "$1")
  case "$vi_v" in
    '' | '<'* | '$'* | '***'* | *teste* | *exemplo* | *ficticio* | *invalid*) return 0 ;;
    # Marcadores de fixture e de modelo usados no próprio projeto, e expressões de código (classe de regex).
    *segredo* | *secreto* | seu_* | *suaempresa* | *'['*) return 0 ;;
  esac
  # Acesso a membro de código (ex.: os.environ.get), não um valor literal.
  if printf '%s' "$vi_v" | grep -q -E '^[a-z_][a-z0-9_]*(\.[a-z_][a-z0-9_]*)+$'; then
    return 0
  fi
  return 1
}

# Prefixos conhecidos, chave da AWS e chave privada PEM (sem exceções).
grep -a -n -E 'gh[po]_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{16}|-----BEGIN [A-Z ]*PRIVATE KEY-----' "$tmp/txt" |
  cut -d: -f1 | sort -un | while IFS= read -r n; do achado SEGREDO "$(loc_de "$n")"; done

# Cabeçalho Bearer com 20 ou mais caracteres de token.
grep -a -n -i -o -E 'bearer[[:space:]]+[A-Za-z0-9._~+/=-]{20,}' "$tmp/txt" |
  while IFS= read -r l; do
    n=${l%%:*}
    m=${l#*:}
    v=$(printf '%s' "$m" | sed 's/^[Bb][Ee][Aa][Rr][Ee][Rr][[:space:]]*//')
    valor_ignorado "$v" || achado SEGREDO "$(loc_de "$n")"
  done

# Atribuição a password/senha/secret/token com valor de 8 ou mais caracteres.
grep -a -n -i -o -E "(password|senha|secret|token)[\"']?[[:space:]]*[:=][[:space:]]*[\"']?[^[:space:]\"',;()&]{8,}" "$tmp/txt" |
  while IFS= read -r l; do
    n=${l%%:*}
    m=${l#*:}
    v=${m#*[:=]}
    v=$(printf '%s' "$v" | sed "s/^[[:space:]]*[\"']*//")
    valor_ignorado "$v" || achado SEGREDO "$(loc_de "$n")"
  done

# --- categoria HOST_INTERNO ------------------------------------------------------------------------------

grep -a -n -o -i -E 'https?://[A-Za-z0-9][A-Za-z0-9._~%:@-]*' "$tmp/txt" |
  while IFS= read -r l; do
    n=${l%%:*}
    m=${l#*:}
    h=${m#*://}
    h=${h##*@}
    h=${h%%:*}
    h=$(minusculo "${h%.}")
    [ -n "$h" ] || continue
    dominio_permitido "$h" || achado HOST_INTERNO "$(loc_de "$n")"
  done

# --- categoria NOME_PROIBIDO -----------------------------------------------------------------------------

# O nome nunca é impresso: só o número da linha sai do grep.
while IFS= read -r nome; do
  [ -n "$nome" ] || continue
  grep -a -n -i -w -F -e "$nome" "$tmp/txt" 2>/dev/null | cut -d: -f1 | sort -un |
    while IFS= read -r n; do achado NOME_PROIBIDO "$(loc_de "$n")"; done
done < "$tmp/nomes"

# --- resultado -------------------------------------------------------------------------------------------

awk '!visto[$0]++' "$tmp/achados" > "$tmp/unicos"
total=$(wc -l < "$tmp/unicos" | tr -d ' ')
while IFS= read -r a; do
  printf 'ACHADO %s\n' "$a"
done < "$tmp/unicos"

if [ "$total" -eq 0 ]; then
  echo "VARREDURA OK"
  exit 0
fi
echo "VARREDURA FALHOU: $total achado(s)"
exit 1
