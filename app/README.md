# Painel de Squads (app)

Backend Kotlin/Ktor em `backend/`, frontend React + TypeScript (Vite) em `frontend/`. O app Python anterior fica em `../legacy/` apenas como referência. Desenho completo: [spec](../docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md).

## Como subir (Docker)

```sh
cp .env.example .env          # o .env é ignorado pelo git
# edite o .env e defina POSTGRES_PASSWORD (ex.: openssl rand -hex 16)
docker compose up --build -d
```

- Painel: <http://127.0.0.1:8080> (serve a SPA; a API fica em `/api/*`, por exemplo `/api/health` e `/api/meta`).
- `POSTGRES_PASSWORD` é obrigatória e não tem valor padrão: sem ela o compose se recusa a iniciar.
- Para derrubar e apagar os dados locais: `docker compose down -v`.

## Flags

| Variável | Padrão | Efeito |
|---|---|---|
| `JIRA_LIVE_ENABLED` | `false` | Jira real desligado. Só altere com autorização expressa. |
| `DASHBOARD_DEV_MODE` | `true` | Modo desenvolvimento: **sem login**, todos os dados, faixa vermelha. |
| `PAINEL_STATIC_DIR` | `frontend/dist` (`/app/static` na imagem) | Pasta do build do frontend servida em `/`. |

## Aviso de segurança

O modo desenvolvimento **não tem login**. Nunca o exponha em rede com dados reais. Por isso o compose publica a porta só em `127.0.0.1:8080`.

**A imagem Docker nasce com `DASHBOARD_DEV_MODE=true` (sem login).** NUNCA a publique com `docker run -p` nem em rede alguma sem antes desligar esse modo (`DASHBOARD_DEV_MODE=false`).

Para produção: defina `DASHBOARD_DEV_MODE=false` (exige o login SSO/OIDC, previsto em plano posterior), e só então troque a publicação no `docker-compose.yml` (por exemplo `"8080:8080"` ou, melhor, um proxy reverso com TLS na frente). Não faça isso enquanto o login não existir.

## Estado desta fundação

Este plano prova que o serviço `painel` sobe, serve a SPA e responde `/api/health` e `/api/meta`, e que o `postgres` fica saudável. A ligação do Ktor ao banco (abrir o pool e chamar `Database.migrate`) **ainda não existe**: é objeto de plano posterior, então as migrações não são aplicadas na subida. As variáveis `DATABASE_URL`, `DATABASE_USER` e `DATABASE_PASSWORD` já são entregues ao contêiner para esse momento.

## Desenvolvimento sem Docker

```sh
cd backend && ./gradlew test        # testes do backend (veja a nota sobre Docker abaixo)
cd frontend && pnpm install && pnpm test && pnpm dev   # frontend em http://localhost:5173 (proxy de /api para :8080)
```

### O que precisa de Docker nos testes

- **Exigem Docker em execução:** os testes de banco (`RetentionTest`, em `backend/src/test/kotlin/br/sidia/painel/db/`), que sobem um PostgreSQL real via Testcontainers. Sem Docker eles **falham** (não são ignorados).
- **Rodam sem Docker:** todos os demais testes do backend (configuração, cliente Jira com mock, calendário, papéis, controle de acesso, SPA, health e meta) e todos os testes do frontend (`pnpm test`).
- Para rodar só o que dispensa Docker: `./gradlew test --tests 'br.sidia.painel.security.*' --tests 'br.sidia.painel.time.*' --tests 'br.sidia.painel.jira.*' --tests 'br.sidia.painel.config.*' --tests 'br.sidia.painel.SpaTest' --tests 'br.sidia.painel.HealthTest' --tests 'br.sidia.painel.MetaTest'`.

### Rodar o backend fora do Docker

A partir de `app/backend`, os padrões `frontend/dist` (`PAINEL_STATIC_DIR`) e `config` (`PAINEL_CONFIG_DIR`) apontam para o lugar errado (relativos a `backend/`). Indique os caminhos:

```sh
cd frontend && pnpm build && cd ../backend
PAINEL_STATIC_DIR=../frontend/dist PAINEL_CONFIG_DIR=../config ./gradlew run
```
