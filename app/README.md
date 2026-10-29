# Painel de Squads (app)

Backend Kotlin/Ktor em `backend/`, frontend React + TypeScript (Vite) em `frontend/`. O app Python anterior fica em `../legacy/` apenas como referência. Desenho completo: [spec](../docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md).

## Como subir

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
- **Ao ligar a flag do Jira, não é preciso apagar o banco da demonstração.** Na primeira subida com a flag ligada o painel apaga sozinho SÓ as linhas fictícias (as chaves `DEMO-n` e `DEMOCAT-n` da demonstração), numa transação, e enquanto não houve nenhuma tentativa de sincronização; depois da primeira tentativa o expurgo nunca mais apaga nada. Até a primeira carga concluir, a tela mostra "Aguardando a primeira carga do Jira".
- Um banco que o painel não reconhece (tem issues e nenhuma tentativa de sincronização, depois do expurgo) é recusado com o código `BANCO_DESCONHECIDO_SEM_SINCRONIZACAO`: faça backup do `data/painel.db` e verifique as issues (podem ser reais) antes de apagá-lo.
- A demonstração só carrega em banco que nunca teve tentativa de sincronização com o Jira, mesmo que ela tenha falhado (código `BANCO_COM_SINCRONIZACAO_REAL`), e um banco com issues reais também a recusa. Nesses casos, faça backup, verifique e use um banco novo.
- Se a flag `JIRA_LIVE_ENABLED` estiver ligada no arquivo `app/.env`, a subida com demonstração **falha de propósito**: volte-a para `false` antes. A flag vale só nesse arquivo; variável de mesmo nome no terminal é ignorada.

## Flags

| Variável | Padrão | Efeito |
|---|---|---|
| `JIRA_LIVE_ENABLED` | `false` | Única chave de liga e desliga da sincronização. Só o usuário liga; `false` proíbe qualquer acesso ao Jira. |
| `DASHBOARD_DEV_MODE` | `true` em `iniciar.sh`/`iniciar.ps1` e em `./gradlew run`; `false` no pacote instalado | Modo desenvolvimento: **sem login**, todos os dados, faixa vermelha. O pacote instalado (`installDist`) sobe em modo produção quando a variável falta; o modelo `.env.example` não a liga. |
| `DASHBOARD_DEMO_DATA` | `false` | Carrega issues **fictícias** na subida, sem tocar no Jira. Só com `DASHBOARD_DEV_MODE=true` e `JIRA_LIVE_ENABLED=false`; qualquer outra combinação derruba a subida. |
| `DASHBOARD_DPO_ACEITE`, `DASHBOARD_DPO_ACEITE_REF` | `false` e vazio | Trava do DPO (LGPD): sem o aceite, Auditoria e Integridade só abrem no modo dev offline (dev ligado e Jira desligado); fora dele respondem 403 `AGUARDANDO_ACEITE_DPO`. Com `true` a referência do parecer é obrigatória (1 a 80 caracteres `A-Za-z0-9._/-`). **Só o usuário liga, depois do parecer do DPO.** |
| `RETENTION_HISTORICO_MONTHS`, `RETENTION_ACCESS_LOG_MONTHS`, `RETENTION_DECISOES_MONTHS`, `RETENTION_EXECUCOES_MONTHS` | `RETENTION_MONTHS` (24) | Retenção por categoria de dado, de 1 a 120 meses: histórico (issue resolvida antiga, alterações, horas), registro de acesso, decisões de distribuição e execuções. Sem valor próprio vale `RETENTION_MONTHS`; a auditoria não encurta abaixo de 12. Os valores finais são decisão do DPO. |
| `PAINEL_STATIC_DIR` | `frontend/dist` | Pasta do build do frontend servida em `/`. |
| `PAINEL_HOST` | `127.0.0.1` | Interface em que o painel escuta (porta 8080). O padrão só atende a própria máquina; outra interface exige firewall (`docs/implantacao.md`). |
| `PAINEL_CACHE_SEG` | `60` | Segundos em que as telas reaproveitam a leitura bruta do banco; `0` desliga. Fim de sincronização e de retenção invalidam na hora. |
| `OIDC_ISSUER`, `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`, `OIDC_REDIRECT_URI`, `OIDC_CLAIM_USERNAME`, `SESSION_SECRET` | vazio | Login SSO/OIDC e assinatura da sessão. Obrigatórios em produção (menos o segredo do cliente e a claim); ver `docs/implantacao.md`. |
| `OIDC_USERNAME_ESTRATEGIA`, `OIDC_DOMINIOS_PERMITIDOS`, `OIDC_HOSTS_PERMITIDOS` | `claim_direto`, vazio, vazio | Como a claim do `id_token` vira o username do Jira (`claim_direto`, `parte_local_do_email` ou `conta_windows`; uma só por instalação). **`OIDC_DOMINIOS_PERMITIDOS` é obrigatória** com as duas últimas (sem ela a produção não sobe: `DOMINIO_DO_USERNAME_AUSENTE`). `OIDC_HOSTS_PERMITIDOS` limita os hosts que o provedor pode anunciar na descoberta. |
| `DASHBOARD_PORT`, `DASHBOARD_ORIGEM_PUBLICA`, `DASHBOARD_PROXY_CONFIAVEL` | `8080`, vazio, vazio | Porta de escuta; origem pela qual o navegador acessa o painel (`https` em produção; a proteção CSRF a compara com o cabeçalho `Origin`); faixas CIDR do proxy reverso cujos cabeçalhos `X-Forwarded-*` são aceitos (vazio = nenhum proxy confiável). A origem e o proxy são obrigatórios em produção, ver "Guardas de produção". |
| `PAINEL_USUARIO` | `painel` | Só nos scripts de atualização e reversão no Linux (`deploy/linux/`): a conta sem privilégio do serviço, com a qual a restauração é feita. Nunca uid 0. Não é variável do painel. |
| `DATABASE_PATH` | `data/painel.db` | Arquivo SQLite do painel. |

## LGPD e aceite do DPO

Auditoria e Integridade mostram sinais sobre pessoas e ficam **fechadas por uma trava em código** até o aceite do encarregado de dados (DPO):

- Fora do modo desenvolvimento offline (`DASHBOARD_DEV_MODE=true` e `JIRA_LIVE_ENABLED=false`, sobre um banco sem dado sincronizado de verdade), as rotas de Auditoria e Integridade respondem 403 `AGUARDANDO_ACEITE_DPO` (depois da checagem de papel), a Central deixa os itens deles de fora e o menu marca as telas com a pausa e o texto "aguardando aceite do DPO".
- O aceite é `DASHBOARD_DPO_ACEITE=true` com `DASHBOARD_DPO_ACEITE_REF=<referência do parecer>` no `app/.env`. **Só o usuário define, depois do parecer do DPO**; nenhuma IA liga. Sem a referência o painel não sobe. A data do aceite fica no `HANDOFF.md`.
- Documentos em `docs/lgpd/`: inventário dos dados (verificado contra o banco por teste), relatório para o DPO (com as perguntas e as opções), atendimento ao titular e aviso à equipe.
- Cada pessoa consulta os próprios dados na tela **Meus dados** (`GET /api/titular/meus-dados`, sem parâmetros). A retenção é por categoria (`RETENTION_*_MONTHS`, tabela de flags acima).

## Sincronização periódica (`SYNC_*`)

A flag `JIRA_LIVE_ENABLED` é a única chave de liga e desliga. As variáveis `SYNC_*` só ajustam limites e horários (vazio = padrão): `SYNC_INSTANCIAS` (padrão `samsung`), `SYNC_INTERVALO_MIN` (5 a 1440; padrão 10), `SYNC_HORA_RECONCILIACAO` (padrão 03:00), `SYNC_HORA_RETENCAO` (padrão 04:00), `SYNC_MAX_ITENS` (1 a 200000; padrão 50000), `SYNC_MAX_REQUISICOES` (1 a 20000; padrão 2000), `SYNC_SOBREPOSICAO_MIN` (1 a 60; padrão 5), `SYNC_FUSO_SERVIDOR` (padrão America/Manaus) e `SYNC_TEMPO_MAX_MIN` (1 a 240; padrão 60). A retenção local usa `RETENTION_MONTHS` (6 a 600; padrão 24) e a auditoria de acessos tem chave própria, `ACCESS_LOG_RETENTION_MONTHS` (12 a 600; padrão 24). Procedimento completo, status e códigos de erro: [runbook da sincronização](../docs/jira/runbook-sincronizacao.md).

## Operação (logs, prontidão, métricas, alertas e backup)

Passo a passo para quem opera o painel: [`docs/operacao/`](../docs/operacao/) (runbook diário, runbook de incidentes, configuração, observabilidade, backup e restauração, rotação de segredos). Todas as variáveis abaixo são opcionais, ficam no arquivo de variáveis (modelo `.env.example`) e **nenhuma liga o Jira**.

| Variável | Padrão | Efeito |
|---|---|---|
| `PAINEL_LOG_FORMATO` | `JSON` | Formato dos logs: `JSON` (uma linha por evento, com máscara de segredos) ou `TEXTO` |
| `PAINEL_LOG_DIR` | vazio | Pasta do `painel.log` (rotação de 20 MB, 14 dias, teto de 500 MB); vazio = só a saída do processo |
| `PAINEL_DISCO_MIN_MB` | `500` | Espaço livre mínimo na pasta do banco; abaixo disso `/api/health/pronto` responde 503 (motivo `DISCO_BAIXO` só no log e para o gerente) |
| `PAINEL_METRICAS_PORTA`, `PAINEL_METRICAS_HOST` | `9464`, `127.0.0.1` | Porta interna do `/metrics` (nunca na 8080); `0` desliga |
| `PAINEL_ALERTAS_ARQUIVO` | vazio | Arquivo de texto onde cada mudança de alerta vira uma linha |
| `SYNC_PAT_VALIDADE_SAMSUNG`, `SYNC_PAT_VALIDADE_SIDIA` | vazio | Validade do token do Jira (`aaaa-mm-dd`), informada por você; alerta 14 dias antes |
| `SESSION_SECRET`, `SESSION_SECRET_ANTERIOR` | vazio | Segredo do cookie de sessão e o anterior, aceito só para ler durante uma rotação |
| `BACKUP_DIR`, `BACKUP_HORA`, `BACKUP_RETENCAO_COPIAS` | vazio, `02:00`, `14` | Backup diário do banco (pasta fora do repositório, obrigatória em produção) |

Endpoints: `GET /api/health` (vivacidade), `GET /api/health/pronto` (prontidão: banco, migrações, disco e trava), `GET /api/operacao/alertas` (gerente) e `GET /metrics` só na porta interna.

Subcomandos do mesmo programa, que rodam e saem sem subir o servidor (use os scripts `scripts/painel-cli.sh` e `scripts/painel-cli.ps1`, que localizam o pacote em `PAINEL_HOME`):

```sh
sh scripts/painel-cli.sh validar-config [--dir config]   # confere a configuração antes de reiniciar (sem banco, sem Jira)
sh scripts/painel-cli.sh backup-agora                    # cópia do banco na hora (funciona com o serviço rodando)
sh scripts/painel-cli.sh verificar-restauracao [cópia]   # restaura a cópia num temporário e confere
```

No PowerShell: `.\scripts\painel-cli.ps1 validar-config`, `... backup-agora`, `... verificar-restauracao`.

## Aviso de segurança

O modo desenvolvimento **não tem login**. Nunca o exponha em rede com dados reais. O painel escuta na porta `8080` e **quem restringe o acesso é o firewall/rede da máquina**: em estação de trabalho, deixe-o acessível só em `127.0.0.1`.

Para produção: use o pacote instalado (seção abaixo), que já sobe com `DASHBOARD_DEV_MODE=false` quando a variável não está definida, ou defina a variável como `false` (exige o login SSO/OIDC: `OIDC_ISSUER`, `OIDC_CLIENT_ID`, `OIDC_REDIRECT_URI` e `SESSION_SECRET`; sem eles o painel não sobe). O provedor de identidade é o da empresa (qualquer OpenID Connect com PKCE); ainda não há provedor ligado, então não exponha o painel em rede com dados reais.

## Pacote instalável

```sh
cd frontend && pnpm install --frozen-lockfile && pnpm build     # gera frontend/dist
cd ../backend && ./gradlew installDist                          # gera o pacote
```

O pacote fica em `app/backend/build/install/painel/` (script `bin/painel`; no Windows, `bin/painel.bat`). Diferente de `iniciar.*` e de `./gradlew run`, o **pacote instalado** sobe em **modo produção** quando `DASHBOARD_DEV_MODE` não está definida: sem o arquivo de variáveis e os YAML reais ele falha na subida ("obrigatório em produção") em vez de abrir sem login. Quem quiser modo dev no pacote precisa escrever a linha explicitamente (e a faixa vermelha continua). O pacote não leva `.env`, YAML reais nem banco; informe-os por `PAINEL_ENV_FILE`, `PAINEL_CONFIG_DIR` e `DATABASE_PATH`.

## Produção

O pacote de produção (sem contêiner, com SQLite em arquivo) é montado e conferido por dois scripts, a partir de `app/`:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\montar-pacote.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\verificar-pacote.ps1
```

(`sh scripts/montar-pacote.sh` e `sh scripts/verificar-pacote.sh` no Linux/macOS.) O resultado fica em `app/pacote/painel/` (ignorado pelo git) e **nunca** leva configuração real, arquivo de variáveis real nem banco. Passando a versão (`AAAA.MM.N`: `sh scripts/montar-pacote.sh 2026.11.1`, ou `-Versao 2026.11.1` no PowerShell) sai também o zip reprodutível `painel-AAAA.MM.N.zip` com o SHA-256 (arquivo `.sha256`) ao lado; a versão aparece no rodapé da tela e em `GET /api/meta`. Tudo sobre pastas e variáveis de produção, login SSO/OIDC, endereço de escuta (`PAINEL_HOST`, padrão `127.0.0.1`), ordem segura para ligar o Jira, serviço, backup, **atualização e reversão** (`deploy/windows/atualizar.ps1` e `reverter.ps1`; `deploy/linux/atualizar.sh` e `reverter.sh`; o `ensaio-troca.sh` prova a troca e a reversão no Linux) está em [`docs/implantacao.md`](../docs/implantacao.md). Os scripts `.ps1` do pacote e da implantação só foram conferidos por testes estáticos: a primeira execução no Windows 11 é pendência registrada no roteiro (D14).

### Guardas de produção

As guardas de produção valem com `DASHBOARD_DEV_MODE=false`: `main()` recusa a subida, antes de abrir o banco e com todos os códigos de uma vez, se: a demonstração está ligada (`DEMO_EM_PRODUCAO`); `OIDC_ISSUER` ou `OIDC_REDIRECT_URI` não são `https` ou o retorno não está sob `DASHBOARD_ORIGEM_PUBLICA`; `SESSION_SECRET` é curto; falta a lista de domínios da estratégia de username (`DOMINIO_DO_USERNAME_AUSENTE`); `DASHBOARD_PROXY_CONFIAVEL` está vazia (`PROXY_NAO_CONFIGURADO`); `PAINEL_HOST` é vazio, `0.0.0.0` ou `::`; ou `DATABASE_PATH`, `PAINEL_CONFIG_DIR` ou `PAINEL_STATIC_DIR` não são caminhos absolutos. A mensagem traz só os códigos, nunca valor. Lista completa e como corrigir cada um: [`docs/implantacao.md`](../docs/implantacao.md), seção "Guardas de produção". `painel validar-config` (seção Operação) confere a configuração sem subir o servidor.

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
- `labels_atraso.yaml`, `auditoria.yaml`, `metas.yaml`, `risco.yaml` ou `previsao.yaml` ausentes (em dev usam o `.exemplo` com aviso; em produção são obrigatórios). Crie-os a partir de `config/*.exemplo.yaml` (labels que justificam atraso, limites da auditoria, metas do card Metas e prazos, pesos do card Riscos, parâmetros da Previsão e dos Épicos; sem dado pessoal).
- YAML inválido: a mensagem cita só o caminho e o tipo do erro.
- `equipes.yaml`, `ausencias.yaml`, `planejamento.yaml` ou `esforco.yaml` ausentes (em dev usam o `.exemplo` com aviso; em produção são obrigatórios). Copie de `config/equipes.exemplo.yaml`, `config/ausencias.exemplo.yaml`, `config/planejamento.exemplo.yaml` e `config/esforco.exemplo.yaml` (os pesos do esforço são proposta inicial e não levam dado pessoal). As competências de cada pessoa ficam no `equipes.yaml`. **`equipes.yaml` e `ausencias.yaml` têm dados pessoais: ficam fora do git**, só leitura (nenhuma tela grava), e os `id` precisam existir no `papeis.yaml`. A ausência não tem motivo (a chave `motivo` é recusada).
- `checklist_review.yaml` ou `integridade.yaml` ausentes (em dev usam o `.exemplo` com aviso; em produção são obrigatórios). Copie de `config/checklist_review.exemplo.yaml` (tipos de checklist e de review, limites dos alertas) e `config/integridade.exemplo.yaml` (gravidade e limites de cada sinal, níveis por pessoa); sem dado pessoal. A Integridade é só do gerente, sempre auditada, e em produção fica desligada até o aceite do DPO.
- `distribuicao.yaml` ausente (em dev usa o `.exemplo` com aviso; em produção é obrigatório). Copie de `config/distribuicao.exemplo.yaml` (pesos do score, faixa de produtividade, gatilho e histerese do rebalanceamento); sem dado pessoal. A Distribuição e o Rebalanceamento só SUGEREM (modo sombra): nada é escrito no Jira.
- Nenhum **GERENTE** com papel vigente hoje no `papeis.yaml`.
- `OIDC_ISSUER`, `OIDC_CLIENT_ID`, `OIDC_REDIRECT_URI` ou `SESSION_SECRET` ausentes ou em branco (o login SSO/OIDC é obrigatório em produção); a mensagem cita só o nome da variável.
- Arquivo de banco inacessível, já em uso por outro painel ou migração que falha.
- Com a flag `JIRA_LIVE_ENABLED` ligada, instância do Jira mal configurada (a mensagem cita só o id e a regra).

**YAML reais em produção.** Coloque `papeis.yaml`, `calendario.yaml`, `status_map.yaml` e `vinculos.yaml` em `app/config/` (ou aponte `PAINEL_CONFIG_DIR`). Eles ficam fora do git (`.gitignore`); só os modelos `*.exemplo.yaml`/`*.demo.yaml` são versionados. O painel só lê esses arquivos.

## Desenvolvimento

```sh
cd backend && ./gradlew test        # testes do backend (SQLite em arquivo temporário; nada a instalar além do JDK 21)
cd frontend && pnpm install && pnpm test && pnpm dev   # frontend em http://localhost:5173 (proxy de /api para :8080)
```

Em desenvolvimento, rode o backend (`cd backend && ./gradlew run`, que já usa `app/` como pasta de trabalho) e o `pnpm dev` em outro terminal.

## CI

Todo pull request e todo push na `main` roda o workflow `.github/workflows/ci.yml` com quatro checks: `backend` (suíte do Gradle, SQLite em arquivo temporário), `frontend` (lint, testes, build e verificação da CSP), `guarda` (testes dos hooks) e `varredura` (dados pessoais e segredos no que o PR acrescenta). Só usa JDK 21 e Node 22/pnpm: sem Docker, sem serviços e sem acesso ao Jira. Para rodar o mesmo na máquina e para a proteção da `main` (ação do usuário no GitHub), veja [docs/operacao/ci.md](../docs/operacao/ci.md).
