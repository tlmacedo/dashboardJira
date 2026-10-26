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
| `JIRA_LIVE_ENABLED` | `false` | Única chave de liga e desliga da sincronização. Só o usuário liga; `false` proíbe qualquer acesso ao Jira. |
| `DASHBOARD_DEV_MODE` | `true` | Modo desenvolvimento: **sem login**, todos os dados, faixa vermelha. |
| `PAINEL_STATIC_DIR` | `frontend/dist` (`/app/static` na imagem) | Pasta do build do frontend servida em `/`. |

## Sincronização periódica (`SYNC_*`)

A flag `JIRA_LIVE_ENABLED` é a única chave de liga e desliga. As variáveis `SYNC_*` só ajustam limites e horários (vazio = padrão): `SYNC_INSTANCIAS` (padrão `samsung`), `SYNC_INTERVALO_MIN` (5 a 1440; padrão 10), `SYNC_HORA_RECONCILIACAO` (padrão 03:00), `SYNC_HORA_RETENCAO` (padrão 04:00), `SYNC_MAX_ITENS` (1 a 200000; padrão 50000), `SYNC_MAX_REQUISICOES` (1 a 20000; padrão 2000), `SYNC_SOBREPOSICAO_MIN` (1 a 60; padrão 5), `SYNC_FUSO_SERVIDOR` (padrão America/Manaus) e `SYNC_TEMPO_MAX_MIN` (1 a 240; padrão 60). A retenção local usa `RETENTION_MONTHS` (padrão 24). Procedimento completo, status e códigos de erro: [runbook da sincronização](../docs/jira/runbook-sincronizacao.md).

## Aviso de segurança

O modo desenvolvimento **não tem login**. Nunca o exponha em rede com dados reais. Por isso o compose publica a porta só em `127.0.0.1:8080`.

**A imagem Docker nasce com `DASHBOARD_DEV_MODE=true` (sem login).** NUNCA a publique com `docker run -p` nem em rede alguma sem antes desligar esse modo (`DASHBOARD_DEV_MODE=false`).

Para produção: defina `DASHBOARD_DEV_MODE=false` (exige o login SSO/OIDC, previsto em plano posterior), e só então troque a publicação no `docker-compose.yml` (por exemplo `"8080:8080"` ou, melhor, um proxy reverso com TLS na frente). Não faça isso enquanto o login não existir.

## Inicialização e configuração

Na subida o painel carrega o calendário e o diretório de papéis, abre o pool do banco e aplica as migrações (Flyway). Falhas de configuração acontecem aí, não na primeira requisição, e nenhuma mensagem ou log carrega senha, token ou trechos dos YAML.

| Variável | Padrão | Efeito |
|---|---|---|
| `PAINEL_CONFIG_DIR` | `config` (`/app/config` na imagem) | Pasta dos YAML (`papeis.yaml`, `calendario.yaml`, `status_map.yaml`, `cards/`). |
| `DATABASE_POOL_SIZE` | `10` | Tamanho máximo do pool de conexões (1 a 100; vazio = padrão; inválido falha na subida). |
| `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` | ver `.env.example` | Conexão com o banco; a senha não tem padrão. |

**Motivos de falha na subida em produção** (`DASHBOARD_DEV_MODE=false`):

- `papeis.yaml` ausente em `PAINEL_CONFIG_DIR` (em dev o painel usa `papeis.exemplo.yaml` com aviso).
- `calendario.yaml` ausente (idem: em dev usa `calendario.exemplo.yaml`).
- `status_map.yaml` ausente (idem: em dev usa `status_map.exemplo.yaml`; em produção é obrigatório, sem padrão inventado). Crie-o a partir de `config/status_map.exemplo.yaml`, com os nomes de status reais; sem ele o painel não sobe e, com nomes que não casam, o card Tempo em status mostra "status sem mapeamento".
- YAML inválido: a mensagem cita só o caminho e o tipo do erro.
- Nenhum **GERENTE** com papel vigente hoje no `papeis.yaml`.
- `DATABASE_PASSWORD` vazia.
- Banco indisponível ou migração que falha (a mensagem cita o destino sem credenciais).
- Com `JIRA_LIVE_ENABLED=true`, instância do Jira mal configurada (a mensagem cita só o id e a regra).

**YAML reais em produção.** A imagem leva apenas os modelos (`*.exemplo.yaml`, `*.demo.yaml`) e `cards/`; os arquivos com pessoas ficam fora dela (`.dockerignore`). Monte-os somente leitura, no `docker-compose.yml` (há um bloco comentado pronto):

```yaml
volumes:
  - ./config/papeis.yaml:/app/config/papeis.yaml:ro
  - ./config/calendario.yaml:/app/config/calendario.yaml:ro
  - ./config/status_map.yaml:/app/config/status_map.yaml:ro
```

O `docker compose` padrão roda com `DASHBOARD_DEV_MODE=true` e por isso usa os modelos de exemplo (dados fictícios) e basta para subir.

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
