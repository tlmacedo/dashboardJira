# Painel de Squads (app)

Backend Kotlin/Ktor em `backend/`, frontend React + TypeScript (Vite) em `frontend/`. O app Python anterior fica em `../legacy/` apenas como referência. Desenho completo: [spec](../docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md).

## Como subir (sem Docker e sem servidor de banco)

Requisitos: **JDK 21** e **Node 22 com pnpm**. O banco é um arquivo SQLite (`data/painel.db`), criado sozinho na primeira subida.

```sh
cp .env.example .env     # opcional; o arquivo .env é ignorado pelo git
sh iniciar.sh            # Linux/macOS
```

No Windows (PowerShell): `Copy-Item .env.example .env` e depois `.\iniciar.ps1`.

- Os scripts instalam as dependências do frontend, geram o build e rodam o backend (`./gradlew run`, executado a partir de `app/`).
- Painel: <http://127.0.0.1:8080> (serve a SPA; a API fica em `/api/*`, por exemplo `/api/health` e `/api/meta`).
- Para apagar os dados locais: pare o painel e apague `data/painel.db` (e `painel.db-wal` e `painel.db-shm`, se existirem).
- Só **um painel por arquivo de banco**: uma segunda subida no mesmo arquivo é recusada (trava `painel.db.lock`).

## Ver o painel hoje com dados fictícios

```sh
cd app
DASHBOARD_DEMO_DATA=true sh iniciar.sh        # PowerShell: $env:DASHBOARD_DEMO_DATA='true'; .\iniciar.ps1
```

- Os dados são **100% fictícios** (issues `DEMO-n` e `DEMOCAT-n`, pessoas dos modelos `.exemplo`) e a faixa vermelha avisa. As datas se ajustam em semanas inteiras até hoje. Nenhuma requisição ao Jira é feita.
- Apagar `data/painel.db` é obrigatório **antes de ligar a flag do Jira**: o painel recusa subir com a flag ligada e dados de demonstração no banco. Um banco que já tem issues reais também recusa a demonstração.
- Se `JIRA_LIVE_ENABLED` estiver ligada no terminal (ou no ambiente do Claude Code), a subida com demonstração **falha de propósito**: desligue-a nesse terminal antes.

## Flags

| Variável | Padrão | Efeito |
|---|---|---|
| `JIRA_LIVE_ENABLED` | `false` | Única chave de liga e desliga da sincronização. Só o usuário liga; `false` proíbe qualquer acesso ao Jira. |
| `DASHBOARD_DEV_MODE` | `true` | Modo desenvolvimento: **sem login**, todos os dados, faixa vermelha. |
| `DASHBOARD_DEMO_DATA` | `false` | Carrega issues **fictícias** na subida, sem tocar no Jira. Só com `DASHBOARD_DEV_MODE=true` e `JIRA_LIVE_ENABLED=false`; qualquer outra combinação derruba a subida. |
| `PAINEL_STATIC_DIR` | `frontend/dist` | Pasta do build do frontend servida em `/`. |
| `DATABASE_PATH` | `data/painel.db` | Arquivo SQLite do painel. |

## Sincronização periódica (`SYNC_*`)

A flag `JIRA_LIVE_ENABLED` é a única chave de liga e desliga. As variáveis `SYNC_*` só ajustam limites e horários (vazio = padrão): `SYNC_INSTANCIAS` (padrão `samsung`), `SYNC_INTERVALO_MIN` (5 a 1440; padrão 10), `SYNC_HORA_RECONCILIACAO` (padrão 03:00), `SYNC_HORA_RETENCAO` (padrão 04:00), `SYNC_MAX_ITENS` (1 a 200000; padrão 50000), `SYNC_MAX_REQUISICOES` (1 a 20000; padrão 2000), `SYNC_SOBREPOSICAO_MIN` (1 a 60; padrão 5), `SYNC_FUSO_SERVIDOR` (padrão America/Manaus) e `SYNC_TEMPO_MAX_MIN` (1 a 240; padrão 60). A retenção local usa `RETENTION_MONTHS` (6 a 600; padrão 24) e a auditoria de acessos tem chave própria, `ACCESS_LOG_RETENTION_MONTHS` (12 a 600; padrão 24). Procedimento completo, status e códigos de erro: [runbook da sincronização](../docs/jira/runbook-sincronizacao.md).

## Aviso de segurança

O modo desenvolvimento **não tem login**. Nunca o exponha em rede com dados reais. O painel escuta na porta `8080` e, sem Docker para limitar a publicação, **quem restringe o acesso é o firewall/rede da máquina**: em estação de trabalho, deixe-o acessível só em `127.0.0.1`.

Para produção: defina `DASHBOARD_DEV_MODE=false` (exige o login SSO/OIDC, previsto em plano posterior). Não exponha em rede enquanto o login não existir.

## Inicialização e configuração

Na subida o painel carrega o calendário e o diretório de papéis, abre o pool do banco e aplica as migrações (Flyway). Falhas de configuração acontecem aí, não na primeira requisição, e nenhuma mensagem ou log carrega senha, token ou trechos dos YAML.

| Variável | Padrão | Efeito |
|---|---|---|
| `PAINEL_CONFIG_DIR` | `config` | Pasta dos YAML (`papeis.yaml`, `calendario.yaml`, `status_map.yaml`, `vinculos.yaml`, `cards/`). |
| `DATABASE_POOL_SIZE` | `4` | Tamanho máximo do pool de conexões (1 a 100; vazio = padrão; inválido falha na subida). |
| `DATABASE_PATH` | `data/painel.db` | Arquivo SQLite; a pasta é criada se não existir. |

**Motivos de falha na subida em produção** (`DASHBOARD_DEV_MODE=false`):

- `papeis.yaml` ausente em `PAINEL_CONFIG_DIR` (em dev o painel usa `papeis.exemplo.yaml` com aviso).
- `calendario.yaml` ausente (idem: em dev usa `calendario.exemplo.yaml`).
- `status_map.yaml` ausente (idem: em dev usa `status_map.exemplo.yaml`; em produção é obrigatório, sem padrão inventado). Crie-o a partir de `config/status_map.exemplo.yaml`, com os nomes de status reais; sem ele o painel não sobe e, com nomes que não casam, o card Tempo em status mostra "status sem mapeamento".
- `vinculos.yaml` ausente (em dev usa `vinculos.exemplo.yaml` com aviso; em produção é obrigatório). Crie-o a partir de `config/vinculos.exemplo.yaml` (tipo do link que liga a Review à task e tipos de issue; sem dado pessoal).
- YAML inválido: a mensagem cita só o caminho e o tipo do erro.
- Nenhum **GERENTE** com papel vigente hoje no `papeis.yaml`.
- Arquivo de banco inacessível, já em uso por outro painel ou migração que falha.
- Com a flag `JIRA_LIVE_ENABLED` ligada, instância do Jira mal configurada (a mensagem cita só o id e a regra).

**YAML reais em produção.** Coloque `papeis.yaml`, `calendario.yaml`, `status_map.yaml` e `vinculos.yaml` em `app/config/` (ou aponte `PAINEL_CONFIG_DIR`). Eles ficam fora do git (`.gitignore`); só os modelos `*.exemplo.yaml`/`*.demo.yaml` são versionados. O painel só lê esses arquivos.

## Desenvolvimento

```sh
cd backend && ./gradlew test        # testes do backend (SQLite em arquivo temporário; nada a instalar além do JDK 21)
cd frontend && pnpm install && pnpm test && pnpm dev   # frontend em http://localhost:5173 (proxy de /api para :8080)
```

Em desenvolvimento, rode o backend (`cd backend && ./gradlew run`, que já usa `app/` como pasta de trabalho) e o `pnpm dev` em outro terminal.
