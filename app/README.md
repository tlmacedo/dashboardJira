# Painel de Squads para Jira

Painel web (FastAPI + SQLAlchemy + front sem build) que lê o Jira (Cloud ou Data Center), guarda o histórico num banco próprio e mostra, por **squad, projeto e pessoa**: o que está aberto, o que está parado, o que foi fechado e como, previsões, capacidade com férias/licenças, e sugestões de **distribuição e rebalanceamento** que o gerente aprova.

Fundamentação (métricas, fórmulas, limites e o que não foi confirmado): [`reports/Dashboard Jira gestão por squad.md`](../reports/Dashboard%20Jira%20gestão%20por%20squad.md). Capturas de tela com dados fictícios: [`docs/manual/`](docs/manual/MANUAL_DO_USUARIO.md).

## Documentação

| Documento | Para quê |
|---|---|
| [`INICIO_RAPIDO.md`](INICIO_RAPIDO.md) | do `git clone` ao painel com o seu Jira: URL, login/token, equipes e pessoas |
| [`INTEGRACAO_PARA_IA.md`](INTEGRACAO_PARA_IA.md) | procedimento passo a passo para uma IA (ou pessoa técnica) integrar e rodar sozinha |
| [`docs/manual/MANUAL_DO_USUARIO.md`](docs/manual/MANUAL_DO_USUARIO.md) | manual com capturas: cada tela, cada card, filtros e períodos |
| [`docs-testes/RELATORIO_TESTES.md`](docs-testes/RELATORIO_TESTES.md) | testes simulados da API do Jira, filtros e carga, com resultados e lacunas |

## Começar em 1 minuto (demonstração, dados fictícios)

```bash
cd app && ./start.sh demo        # Windows: start.bat demo   (cria o ambiente, instala e abre em http://localhost:8000)
```

Manualmente:

```bash
cd app/backend
pip install -r requirements.txt
JIRADASH_MODE=demo python -m uvicorn jiradash.main:app      # http://localhost:8000
python -m pytest -q                                          # ~600 testes (leva alguns minutos: inclui matriz de filtros)
```

O modo demonstração gera ~900 issues, histórico de status, horas, férias e licenças relativas a hoje. Há um aviso fixo na tela; nenhum número vem do seu Jira.

## Ligar ao seu Jira (uso real)

1. `cp config/equipes.exemplo.yaml config/equipes.yaml` e preencha: squads, gerente, membros (`ref` = accountId no Cloud, username no Data Center), projetos de cada squad, **nomes exatos dos status** e sua categoria (`todo|active|wait|blocked|done`), ordem do fluxo, resoluções negativas, férias/licenças e feriados extras.
2. Crie uma conta de serviço com *Browse Projects* e *View Worklogs* (e *Assign Issues* só se for ligar a escrita). Cloud: API token. Data Center: Personal Access Token.
3. Configure as variáveis (`.env.example`): `JIRADASH_MODE=jira`, `JIRA_FLAVOR`, `JIRA_BASE_URL`, `JIRA_TOKEN` (+ `JIRA_EMAIL` no Cloud).
4. Valide e sincronize:
   ```bash
   python -m jiradash check          # credenciais, campos (Story Points, Sprint, Flagged) e status sem mapeamento
   python -m jiradash sync --full    # carga inicial com histórico
   python -m jiradash serve
   ```
   Depois disso o processo mantém a sincronização incremental a cada `JIRADASH_SYNC_MIN` minutos (marca d'água − 5 min, idempotente por id de changelog) e uma reconciliação completa por dia.
5. Produção: PostgreSQL (`DATABASE_URL`), `JIRADASH_API_KEY` ou SSO no proxy reverso, HTTPS, `docker compose up --build`.

**A escrita no Jira vem desligada.** Em modo sombra a aprovação de uma sugestão fica registrada na auditoria sem alterar o Jira. Compare por algumas semanas as sugestões com o que o gerente faria e só então defina `JIRADASH_ALLOW_WRITE=1`.

## Telas

| Grupo | Tela | Responde |
|---|---|---|
| Visão | Visão geral (com Central de atenção) · Meu painel | saúde dos squads e o que pede ação; fila e capacidade de uma pessoa |
| Em curso | Abertas · Épicos · Negligenciadas · Riscos · Previsão | idade vs. percentis, vencimento; sem atualização/sem responsável/bloqueadas/paradas; risco explicável 0–100; Monte Carlo e data por issue |
| Resultados | Fechadas · Negativas · Tempo em status | entregas, ciclo/lead P50/P85/P95, por pessoa (alfabético); reabertas, devolvidas, canceladas, atrasadas, estouro de estimativa, com tempo por status; onde o tempo é gasto |
| Prazos e qualidade | Metas e prazos · Checklist × Review · Integridade | % no prazo e entregas em D, D-1, D-2 por período, tipo e pessoa (e pela data original); comparação de datas, labels e responsáveis entre a tarefa checklist e sua review filha; indícios de adulteração de resultados |
| Capacidade | Carga por pessoa · Capacidade e ausências · Gargalos | ocupação, equidade de Jain; mapa semanal com férias/feriados e atrasos previstos; fluxo cumulativo e etapas gargalo |
| Ação | Distribuição · Rebalanceamento | sugerir/aprovar/trocar com motivo/rejeitar, ranking com os fatores |
| Governança | Qualidade dos dados · Auditoria · Sincronização | dados faltantes que enfraquecem as previsões; quem decidiu o quê |

Os filtros (squad, projeto, pessoa, tipo, prioridade, período; cada tela mostra só os que alteram os números) ficam na URL. Listas exportam CSV com os filtros no cabeçalho.

## Como a distribuição decide

Motor puro em `backend/jiradash/distribution/engine.py` (determinístico, versionado, testado). Para cada issue nova: `score = carga projetada ÷ capacidade líquida` (4 semanas, já sem férias, licenças e feriados) `+ penalidade de WIP + competência faltante − bônus de afinidade`. Menor score vence; empate vai para quem recebeu há mais tempo. **Produtividade entra só como peso de capacidade, suavizado e limitado a ±15%**, para não punir quem entrega mais nem premiar quem entrega menos. O índice de Jain mostra a equidade antes e depois. Rebalanceamento move o mínimo de issues **não iniciadas** quando a ocupação projetada passa de 110%, com histerese. Os pesos são heurísticos e estão no topo do arquivo, ajuste-os com o histórico do seu time.

## Integridade dos resultados (metas)

A tela **Integridade** procura padrões que melhoram o placar sem melhorar o trabalho: vencimento prorrogado perto do prazo ou "resgatando" um atraso, fechamento instantâneo ou em lote, reabrir e fechar em minutos, assumir a issue de outra pessoa minutos antes de concluir, estimativa aumentada durante o trabalho, label de criticidade removida. Cada indício vem com evidência verificável no histórico da issue, e o acesso à tela é registrado na auditoria. São **indícios para revisão humana, não prova**: confira no Jira e converse antes de qualquer conclusão.

## Decisões de privacidade (LGPD)

Tabelas de desempenho não têm ranking; a exceção é a tela Integridade (pedida para identificar adulteração), que ordena por pontos de atenção e registra cada acesso. Tabelas de pessoas saem em ordem alfabética; os "sinais" servem a conversas de apoio. Ausências são guardadas sem motivo detalhado. Há trilha de auditoria de toda decisão. Antes de usar com dados reais, valide a base legal e a comunicação à equipe com o DPO: a análise jurídica da pesquisa usou só fontes secundárias.

## Limites conhecidos (leia antes de confiar nos números)

- **O cliente Jira não foi testado contra um servidor real.** Foi escrito conforme a documentação pública (Cloud `/search/jql` + `changelog/bulkfetch`; DC `/search` com `expand=changelog`) e coberto por testes com respostas simuladas. Rode `python -m jiradash check` e confira uma amostra de issues contra o Jira antes de usar. O formato do bulk fetch do Cloud e o campo de/para de assignee no DC são os pontos a verificar primeiro.
- PostgreSQL, Docker e o agendador não foram exercitados aqui (os testes e a demonstração usam SQLite).
- Desempenho: com ~900 issues as telas respondem em centenas de ms; com ~22 mil issues o tempo é de segundos (veja `docs-testes/resultados_carga.md`). Use vários workers (`uvicorn --workers 4`) e PostgreSQL para equipes grandes; a janela de histórico é `history_days` (padrão 270) no YAML.
- Tempos são em horas corridas, não em horário útil. Histórico de mudança de responsável no DC pode vir por nome de exibição; o casamento com a pessoa é por referência ou nome.
- Sem estimativa, o sistema usa `default_issue_hours` e a previsão perde confiança; a tela de qualidade mostra o quanto.
- Previsão Monte Carlo assume o ritmo recente e backlog fechado; com menos de 8 semanas de histórico recebe selo de baixa confiança.
- Autenticação do painel é só chave de API; para papéis (gerente, leitor) por squad use SSO no proxy reverso e propague `X-User`.
- Pesos do score de risco e do motor de distribuição são propostas, sem fonte normativa.

## Estrutura

```
app/
  backend/jiradash/   config, models, jira/ (client, sync), metrics/ (core, forecast, capacity, risk),
                      distribution/engine.py, analytics.py (payloads das telas), api/routes.py, demo.py, cli.py
  backend/tests/      métricas, motor, cliente Jira (simulado), API ponta a ponta
  frontend/           index.html, css, js (gráficos SVG próprios, sem dependências)
  config/             equipes.exemplo.yaml (copie), equipes.demo.yaml
```
