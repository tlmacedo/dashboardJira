# Spec da Fase 3B: saída do Docker e do PostgreSQL (EXECUTADA)

**Estado:** executada e mesclada na `main` (PRs #18 e #19, 26 e 27/10/2026). Este documento é o registro da decisão e do que mudou, para quem executa os planos seguintes.

## Por quê

A empresa bloqueia Docker e PostgreSQL no PC onde o projeto roda. Decisão do usuário (26/10, respondida em pergunta com opções): **SQLite em arquivo** e **ambiente com JDK 21 e Node/pnpm**, sem contêiner. Alternativa descartada: H2 embutido (esquema e SQL de produção diferentes do teste) e PostgreSQL portátil (binário a instalar e manter).

## O que foi feito

- **Banco:** SQLite embutido (`org.xerial:sqlite-jdbc`), arquivo `app/data/painel.db` (`DATABASE_PATH`), WAL, `busy_timeout`, chaves estrangeiras, escrita IMMEDIATE, pool de 4. Migração única `V1__base.sql` (esquema final consolidado). Fim das travas `advisory` do PostgreSQL: travamento em memória por instância + trava de SO por arquivo (`<banco>.lock`), uma subida por banco.
- **Convenções de dados:** instante = `INTEGER` em milissegundos UTC; prazo = `TEXT` ISO; listas e JSON bruto = `TEXT` lido com `json_each`/`json_extract` (`db/SqlSupport.kt`).
- **Testes:** SQLite real em arquivo temporário (`BancoEmArquivo`, `testContextComBanco`); Testcontainers removido. 745 testes do backend passando.
- **Variáveis:** `app/.env` lido pelo painel (`config/ArquivoEnv.kt`). A flag `JIRA_LIVE_ENABLED` **vale somente no `app/.env`** (terminal e sistema são ignorados; ausente = desligado).
- **Subida:** `app/iniciar.ps1` / `app/iniciar.sh`; `./gradlew run` roda a partir de `app/`.
- **Removidos:** `Dockerfile`, `.dockerignore`, `docker-compose.yml`, hooks de Docker nas settings, `ensure-docker.sh` (agora no-op).

## Critérios de aceitação (cumpridos)

1. `cd app/backend; .\gradlew.bat test` passa sem Docker nem serviço externo.
2. O painel sobe com dados fictícios e responde em `/api/health` e `/api/meta`.
3. Nenhuma menção operacional a Docker/PostgreSQL em `app/README.md`, `app/.env.example` e no runbook da sincronização (há teste).
4. Flag do Jira controlada somente pelo `app/.env` (há teste).

## O que isto mudou para os planos pendentes

Ver `docs/superpowers/plans/2026-10-27-adendo-plataforma-sqlite-sem-docker.md`: tradução do SQL, regras de teste, migrações a partir da `V2`, equivalentes para imagem/volume/backup/CI. Os Planos 8, 9, 12 e 13 foram reescritos; os demais foram ajustados.

## Pendências conhecidas

- Alerta do GitGuardian em `SettingsTest.kt` (3 "Generic Password"): são valores fictícios de teste de mascaramento (`abc123`, `sg1`). Falso positivo; marcar como tal no painel do GitGuardian (ação do usuário).
- `legacy/` ainda tem o `Dockerfile`/compose do app Python antigo: é referência, não evoluir, será removido no Plano 8.
