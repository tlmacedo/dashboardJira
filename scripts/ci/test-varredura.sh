#!/bin/sh
# Testes de varrer-dados-pessoais.sh. Cada caso cria um repositório git temporário, faz um commit base
# na `main`, acrescenta o caso numa branch e confere o código de saída e a saída da varredura.
# Uso: sh scripts/ci/test-varredura.sh
#
# ATENÇÃO: nenhum valor "proibido" aparece por extenso neste arquivo (senão a própria varredura o acharia
# no PR). Cada valor é montado em tempo de execução, por pedaços. Domínio `.test` = deve ser achado;
# `.invalid` = deve passar.
aqui=$(cd "$(dirname "$0")" && pwd)
script="$aqui/varrer-dados-pessoais.sh"
raiz_repo=$(cd "$aqui/../.." && pwd)
falhas=0
tmpraiz=$(mktemp -d)
trap 'rm -rf "$tmpraiz"' EXIT HUP INT TERM

# Valores montados por pedaços.
email_real() { printf '%s@%s.%s' "$1" empresa test; }
email_ficticio() { printf '%s@%s.%s' "$1" exemplo invalid; }
url_interna() { printf '%s://%s.%s/%s' https painel interno test; }
url_invalid() { printf '%s://%s.%s/%s' https painel invalid x; }
carga25() { printf '%s' 'Zq8Lm3Xv9Rt2Wp6Yk1Nb4Hc7'; }

novo_repo() { # cria o repositório base e devolve o caminho em $repo
  repo=$(mktemp -d "$tmpraiz/r.XXXXXX")
  (
    cd "$repo" || exit 1
    git init -q -b main . 2>/dev/null || { git init -q . && git checkout -q -b main; }
    git config user.name "Teste Fictício"
    git config user.email "teste@exemplo.invalid"
    git config commit.gpgsign false
    printf 'base\n' > LEIAME.md
    git add LEIAME.md
    git commit -q -m "base"
    git checkout -q -b pr
  )
}

# adiciona <arquivo> <conteúdo> [mensagem]  (cria/sobrescreve o arquivo e faz um commit)
adiciona() {
  (
    cd "$repo" || exit 1
    mkdir -p "$(dirname "$1")"
    printf '%s\n' "$2" > "$1"
    git add -f "$1"
    git commit -q -m "${3:-alteracao}"
  )
}

roda() { # argumentos da varredura; resultado em $saida e $cod
  saida=$(cd "$repo" && sh "$script" --base main "$@" 2>&1)
  cod=$?
}

confere() { # descrição, código esperado, trecho esperado na saída (opcional)
  ok=1
  [ "$cod" = "$2" ] || ok=0
  if [ -n "$3" ]; then
    printf '%s' "$saida" | grep -F -q -e "$3" || ok=0
  fi
  if [ "$ok" = 1 ]; then echo "ok    $1"; else echo "FALHA $1 (saiu $cod, esperado $2; trecho esperado: '$3')"; printf '%s\n' "$saida" | sed 's/^/      | /'; falhas=$((falhas+1)); fi
}

nao_contem() { # descrição, termo que NÃO pode aparecer na saída
  if printf '%s' "$saida" | grep -F -q -e "$2"; then
    echo "FALHA $1 (a saída imprimiu o termo encontrado)"; falhas=$((falhas+1))
  else
    echo "ok    $1"
  fi
}

# --- casos ---

# semAchadosSaiZero
novo_repo; adiciona src/Ola.kt 'fun ola() = println("ola mundo")'
roda; confere semAchadosSaiZero 0 "VARREDURA OK"

# emailRealFalha / emailFicticioPassa
novo_repo; adiciona doc.md "contato: $(email_real ana)"
roda; confere emailRealFalha 1 "ACHADO EMAIL doc.md:1"
novo_repo; adiciona doc.md "contato: $(email_ficticio ana)"
roda; confere emailFicticioPassa 0 "VARREDURA OK"

# rótulo do Kotlin parecido com e-mail não conta
novo_repo; adiciona src/A.kt 'val x = this@Application.module'
roda; confere rotuloKotlinNaoEhEmail 0 "VARREDURA OK"

# tokenFalha / placeholderPassa / valorDeTestePassa
novo_repo; adiciona http.txt "Authorization: Bearer $(carga25)"
roda; confere tokenFalha 1 "ACHADO SEGREDO http.txt:1"
nao_contem tokenNaoImpresso "$(carga25)"
novo_repo; adiciona http.txt 'Authorization: Bearer <seu-token-aqui-com-mais-de-vinte>'
roda; confere placeholderPassa 0 "VARREDURA OK"
novo_repo; adiciona cfg.txt 'chave: ${SENHA_DO_AMBIENTE_X}'
roda; confere placeholderDeVariavelPassa 0 "VARREDURA OK"
novo_repo; adiciona cfg.txt "$(printf '%s: %s' senha 'valor-de-teste-123')"
roda; confere valorDeTestePassa 0 "VARREDURA OK"
novo_repo; adiciona cfg.txt "$(printf '%s: %s' senha 'Xk29Lm03Qz81')"
roda; confere atribuicaoDeSegredoFalha 1 "ACHADO SEGREDO cfg.txt:1"
novo_repo; adiciona cfg.txt "$(printf '%s=%s' TOKEN 'Xk29Lm03Qz81')"
roda; confere atribuicaoMaiusculaFalha 1 "ACHADO SEGREDO cfg.txt:1"

# código que lê o segredo de outro lugar não é segredo
novo_repo; adiciona src/C.py "$(printf '%s = %s' tk 'os.environ.get(x)')"
adiciona src/D.py "$(printf '%s = %s' token 'os.environ.get(x)')"
roda; confere acessoAMembroDeCodigoPassa 0 "VARREDURA OK"

# prefixos de tokens conhecidos e chave da AWS
novo_repo; adiciona gh.txt "$(printf '%s_%s' ghp 'Ab12Cd34Ef56Gh78Ij90Kl12Mn34')"
roda; confere prefixoGithubFalha 1 "ACHADO SEGREDO gh.txt:1"
novo_repo; adiciona aws.txt "$(printf '%s%s' AKIA 'ABCDEFGHIJKLMNOP')"
roda; confere chaveAwsFalha 1 "ACHADO SEGREDO aws.txt:1"

# chavePrivadaFalha
novo_repo; adiciona k.txt "$(printf -- '-----BEGIN %s PRIVATE KEY-----' RSA)"
roda; confere chavePrivadaFalha 1 "ACHADO SEGREDO k.txt:1"

# hostInternoFalha / hostInvalidPassa / localhost
novo_repo; adiciona doc.md "ver $(url_interna)"
roda; confere hostInternoFalha 1 "ACHADO HOST_INTERNO doc.md:1"
novo_repo; adiciona doc.md "ver $(url_invalid)"
roda; confere hostInvalidPassa 0 "VARREDURA OK"
novo_repo; adiciona doc.md 'ver http://127.0.0.1:8080/api/health e http://localhost:5173'
roda; confere hostLocalPassa 0 "VARREDURA OK"
novo_repo; adiciona doc.md 'ver https://github.com/exemplo/repo'
roda; confere hostPublicoPermitidoPassa 0 "VARREDURA OK"

# arquivoProibidoFalha / exemploPassa / bancoLocalFalha / modeloDeVariaveisPassa
novo_repo; adiciona app/config/papeis.yaml 'a: b'
roda; confere arquivoProibidoFalha 1 "ACHADO ARQUIVO_PROIBIDO app/config/papeis.yaml"
novo_repo; adiciona app/config/papeis.exemplo.yaml 'a: b'
roda; confere exemploPassa 0 "VARREDURA OK"
novo_repo; adiciona app/data/painel.db 'x'
roda; confere bancoLocalFalha 1 "ACHADO ARQUIVO_PROIBIDO app/data/painel.db"
novo_repo; adiciona app/data/painel.db-wal 'x'
roda; confere bancoWalFalha 1 "ACHADO ARQUIVO_PROIBIDO"
novo_repo; adiciona .env 'A=b'
roda; confere arquivoEnvFalha 1 "ACHADO ARQUIVO_PROIBIDO .env"
novo_repo
mkdir -p "$repo/app"
cp "$raiz_repo/app/.env.example" "$repo/app/.env.example"
(cd "$repo" && git add -f app/.env.example && git commit -q -m "modelo")
roda; confere modeloDeVariaveisPassa 0 "VARREDURA OK"

# nomeProibidoPorVariavel / nomeProibidoPorArquivoLocal / naoImprimeOTermoEncontrado
nome="Zorbaldo"
novo_repo; adiciona doc.md "reunião com $nome ontem"
saida=$(cd "$repo" && VARREDURA_NOMES="$nome" sh "$script" --base main 2>&1); cod=$?
confere nomeProibidoPorVariavel 1 "ACHADO NOME_PROIBIDO doc.md:1"
nao_contem nomeNaoImpresso "$nome"
printf '%s\n' "$nome" > "$tmpraiz/nomes.txt"
saida=$(cd "$repo" && VARREDURA_NOMES_ARQUIVO="$tmpraiz/nomes.txt" sh "$script" --base main 2>&1); cod=$?
confere nomeProibidoPorArquivoLocal 1 "ACHADO NOME_PROIBIDO doc.md:1"
nao_contem nomeDoArquivoNaoImpresso "$nome"
saida=$(cd "$repo" && VARREDURA_NOMES="Outra Pessoa" sh "$script" --base main 2>&1); cod=$?
confere nomeAusentePassa 0 "VARREDURA OK"

# naoImprimeOTermoEncontrado (e-mail, host e token)
novo_repo; adiciona doc.md "$(email_real carla) e $(url_interna)"
roda
confere varreduraComVariosAchados 1 "VARREDURA FALHOU: 2 achado(s)"
nao_contem naoImprimeOTermoEncontrado "$(email_real carla)"
nao_contem naoImprimeOHostEncontrado "painel.interno"

# mensagemDeCommitVarrida / emailDeAutorIgnorado
novo_repo; adiciona src/B.kt 'val b = 1' "ajuste de $(email_real dora)"
roda; confere mensagemDeCommitVarrida 1 "ACHADO EMAIL commit:"
nao_contem mensagemNaoImpressa "$(email_real dora)"
novo_repo
(
  cd "$repo" || exit 1
  printf 'val c = 2\n' > src.kt
  git add src.kt
  git commit -q -m "limpo" --author "Fulano <$(email_real fulano)>"
)
roda; confere emailDeAutorIgnorado 0 "VARREDURA OK"

# linhaRemovidaNaoConta
novo_repo
(
  cd "$repo" || exit 1
  git checkout -q main
  printf 'contato: %s\n' "$(email_real eva)" > contatos.md
  git add contatos.md; git commit -q -m "base com contato"
  git checkout -q -b pr2
  printf 'sem contato\n' > contatos.md
  git add contatos.md; git commit -q -m "remove contato"
)
saida=$(cd "$repo" && sh "$script" --base main 2>&1); cod=$?
confere linhaRemovidaNaoConta 0 "VARREDURA OK"

# base inexistente e argumento inválido: erro de uso (2)
novo_repo
saida=$(cd "$repo" && sh "$script" --base nao-existe 2>&1); cod=$?
confere baseInexistenteEhErro 2 ""
saida=$(cd "$repo" && sh "$script" --coisa 2>&1); cod=$?
confere argumentoInvalidoEhErro 2 ""

# modo --arvore pega arquivo versionado fora do diff
novo_repo
(
  cd "$repo" || exit 1
  git checkout -q main
  printf 'contato: %s\n' "$(email_real gil)" > antigo.md
  git add antigo.md; git commit -q -m "antigo"
)
saida=$(cd "$repo" && sh "$script" --arvore 2>&1); cod=$?
confere modoArvoreAcha 1 "ACHADO EMAIL antigo.md:1"

if [ "$falhas" -gt 0 ]; then echo "$falhas FALHA(S)"; exit 1; fi
echo "todos os casos passaram"
