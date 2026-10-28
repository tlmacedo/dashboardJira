#!/bin/sh
# Monta o pacote de produção do painel em app/pacote/painel/ (sem contêiner, sem servidor de banco).
# Uso (a partir de app/):  sh scripts/montar-pacote.sh
#
# O pacote leva: bin/ (scripts de subida), lib/ (jars), frontend/dist/ (a SPA), config/ (SÓ os modelos *.exemplo.yaml,
# *.demo.yaml e as fichas de cards/), o modelo de variáveis, VERSAO.txt e LEIA-ME.txt. Configuração real, arquivo de
# variáveis real e arquivo de banco NUNCA entram: ficam FORA da pasta do pacote (ver docs/implantacao.md).
#
# Requisitos: JDK 21, Node 22 e pnpm. Em máquina sem acesso direto aos registros (proxy ou espelho da empresa), defina
# PAINEL_PNPM_ARGS e PAINEL_GRADLE_ARGS (por exemplo --offline quando as dependências já estão no cache).
set -eu

AQUI="$(cd "$(dirname "$0")" && pwd)"
APP="$(cd "$AQUI/.." && pwd)"
DESTINO="$APP/pacote/painel"

echo "== Frontend: instalando dependências e gerando o build =="
# shellcheck disable=SC2086
(cd "$APP/frontend" && pnpm install --frozen-lockfile ${PAINEL_PNPM_ARGS:-} && pnpm build)

echo "== Backend: gerando o pacote com installDist =="
# shellcheck disable=SC2086
(cd "$APP/backend" && ./gradlew installDist ${PAINEL_GRADLE_ARGS:-})
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

VERSAO="$(sed -n 's/^version = "\(.*\)"$/\1/p' "$APP/backend/build.gradle.kts" | head -n 1)"
COMMIT="$(git -C "$APP" rev-parse --short HEAD 2>/dev/null || echo desconhecido)"
{
    echo "versao=$VERSAO"
    echo "commit=$COMMIT"
    echo "montado_em=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
} > "$DESTINO/VERSAO.txt"

echo "Pacote pronto em $DESTINO ($(cat "$DESTINO/VERSAO.txt" | tr '\n' ' '))"
echo "Próximo passo: sh scripts/verificar-pacote.sh"
