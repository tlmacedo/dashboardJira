#!/bin/sh
# Monta o pacote de produção do painel em app/pacote/painel/ (sem contêiner, sem servidor de banco).
# Uso (a partir de app/):
#   sh scripts/montar-pacote.sh                 monta só a pasta (versão 0.0.0-dev, para ensaio local)
#   sh scripts/montar-pacote.sh 2026.11.1       monta a pasta E o zip app/pacote/painel-2026.11.1.zip com o .sha256 ao lado
#                                               (versão AAAA.MM.N, mês 01 a 12, N >= 1), e já o verifica
#
# O pacote leva: bin/ (scripts de subida), lib/ (jars), frontend/dist/ (a SPA), config/ (SÓ os modelos *.exemplo.yaml,
# *.demo.yaml e as fichas de cards/), o modelo de variáveis, implantacao/ (scripts de atualização e reversão, modelos) e docs/
# (o guia de implantação), VERSAO.txt e LEIA-ME.txt. Configuração real, arquivo de variáveis real e arquivo de banco NUNCA
# entram: ficam FORA da pasta do pacote (ver docs/implantacao.md).
#
# Requisitos: JDK 21, Node 22 e pnpm. Em máquina sem acesso direto aos registros (proxy ou espelho da empresa), defina
# PAINEL_PNPM_ARGS e PAINEL_GRADLE_ARGS (por exemplo --offline quando as dependências já estão no cache).
set -eu

AQUI="$(cd "$(dirname "$0")" && pwd)"
APP="$(cd "$AQUI/.." && pwd)"
REPO="$(cd "$APP/.." && pwd)"
DESTINO="$APP/pacote/painel"

VERSAO="${1:-}"
PARAMS_DA_VERSAO=""
if [ -n "$VERSAO" ]; then
    if ! printf '%s' "$VERSAO" | grep -Eq '^[0-9]{4}\.(0[1-9]|1[0-2])\.[1-9][0-9]*$'; then
        echo "ERRO: versão inválida (esperado AAAA.MM.N, mês 01 a 12, N >= 1; exemplo: 2026.11.1)" >&2
        exit 2
    fi
    COMMIT="$(git -C "$APP" rev-parse --short=7 HEAD 2>/dev/null || echo desconhecido)"
    CONSTRUIDA_EM="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    PARAMS_DA_VERSAO="-Pversao=$VERSAO -Pcommit=$COMMIT -PconstruidaEm=$CONSTRUIDA_EM"
fi

echo "== Frontend: instalando dependências e gerando o build =="
# shellcheck disable=SC2086
(cd "$APP/frontend" && pnpm install --frozen-lockfile ${PAINEL_PNPM_ARGS:-} && pnpm build)

echo "== Backend: gerando o pacote com installDist =="
# shellcheck disable=SC2086
(cd "$APP/backend" && ./gradlew installDist $PARAMS_DA_VERSAO ${PAINEL_GRADLE_ARGS:-})
INSTALADO="$APP/backend/build/install/painel"
if [ ! -f "$INSTALADO/bin/painel" ] || [ ! -f "$APP/frontend/dist/index.html" ]; then
    echo "ERRO: o build não gerou $INSTALADO/bin/painel ou frontend/dist/index.html" >&2
    exit 1
fi

echo "== Montando $DESTINO =="
# Só se apaga o que está dentro de app/pacote/ (a conferência vem antes de qualquer remoção).
case "$DESTINO" in
    "$APP"/pacote/*) ;;
    *) echo "ERRO: destino fora de app/pacote/: $DESTINO" >&2; exit 2 ;;
esac
rm -rf "$DESTINO"
mkdir -p "$DESTINO/bin" "$DESTINO/lib" "$DESTINO/frontend/dist" "$DESTINO/config/cards"

cp "$INSTALADO"/bin/painel "$INSTALADO"/bin/painel.bat "$DESTINO/bin/"
chmod +x "$DESTINO/bin/painel"
cp "$INSTALADO"/lib/*.jar "$DESTINO/lib/"
cp -R "$APP"/frontend/dist/. "$DESTINO/frontend/dist/"

# Configuração: SOMENTE os modelos e as fichas versionadas. Nenhum YAML real é copiado, mesmo que exista na máquina.
for f in "$APP"/config/*.exemplo.yaml "$APP"/config/*.demo.yaml; do
    [ -f "$f" ] && cp "$f" "$DESTINO/config/"
done
for f in "$APP"/config/cards/*.card.yaml; do
    [ -f "$f" ] && cp "$f" "$DESTINO/config/cards/"
done
cp "$APP/.env.example" "$DESTINO/.env.example"
cp "$AQUI/LEIA-ME-pacote.txt" "$DESTINO/LEIA-ME.txt"

# Implantação: scripts de atualização/reversão/segredos (deploy/) e o guia (docs/). Só o que existe no repositório.
for sub in windows linux modelos; do
    if [ -d "$REPO/deploy/$sub" ]; then
        mkdir -p "$DESTINO/implantacao/$sub"
        cp -R "$REPO/deploy/$sub/." "$DESTINO/implantacao/$sub/"
    fi
done
mkdir -p "$DESTINO/docs"
[ -f "$REPO/docs/implantacao.md" ] && cp "$REPO/docs/implantacao.md" "$DESTINO/docs/"
if [ -d "$REPO/docs/implantacao" ]; then
    mkdir -p "$DESTINO/docs/implantacao"
    cp -R "$REPO/docs/implantacao/." "$DESTINO/docs/implantacao/"
fi

# VERSAO.txt: a versão, o commit e a data vêm do mesmo recurso que o painel lê (gerado pelo Gradle).
PROPRIEDADES="$APP/backend/build/generated/versao/versao.properties"
propriedade() { sed -n "s/^$1=//p" "$PROPRIEDADES" | head -n 1 | tr -d '\r'; }
{
    echo "versao=$(propriedade versao)"
    echo "commit=$(propriedade commit)"
    echo "montado_em=$(propriedade construidaEm)"
} > "$DESTINO/VERSAO.txt"

echo "Pacote pronto em $DESTINO ($(cat "$DESTINO/VERSAO.txt" | tr '\n' ' '))"

if [ -z "$VERSAO" ]; then
    echo "Próximo passo: sh scripts/verificar-pacote.sh"
    exit 0
fi

echo "== Zip e SHA-256 =="
# shellcheck disable=SC2086
(cd "$APP/backend" && ./gradlew montarPacote $PARAMS_DA_VERSAO ${PAINEL_GRADLE_ARGS:-})
ZIP="$APP/pacote/painel-$VERSAO.zip"
[ -f "$ZIP" ] || { echo "ERRO: o Gradle não gerou $ZIP" >&2; exit 1; }
if command -v sha256sum >/dev/null 2>&1; then
    HASH="$(sha256sum "$ZIP" | cut -d ' ' -f 1)"
else
    HASH="$(shasum -a 256 "$ZIP" | cut -d ' ' -f 1)"
fi
# Formato aceito por `sha256sum -c`: o hash em minúsculas, dois espaços e o nome do arquivo.
echo "$HASH  painel-$VERSAO.zip" > "$ZIP.sha256"

sh "$AQUI/verificar-pacote.sh" "$ZIP"
echo "PACOTE_OK painel-$VERSAO.zip $HASH"
