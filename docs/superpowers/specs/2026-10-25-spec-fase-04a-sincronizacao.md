# Spec da fase 4A: Sincronização periódica com o Jira, dados de demonstração e hook consciente da flag

Data: 25/10/2026
Plano de implementação: [`docs/superpowers/plans/2026-10-25-plano-04a-sincronizacao.md`](../plans/2026-10-25-plano-04a-sincronizacao.md) (Tarefas 1 a 12)
Spec mestra do projeto: [`2026-10-24-painel-squads-kotlin-react-design.md`](2026-10-24-painel-squads-kotlin-react-design.md)

**Precedência:** `CLAUDE.md` > `docs/guia-execucao-outra-ia.md` > esta spec > o plano. A spec mestra (seções 2 e 3) ainda fala em "autorização expressa por pesquisa"; isso foi **substituído em 25/10** pela regra da flag única (abaixo). Onde houver conflito, vale a regra nova.

Esta spec é autossuficiente: quem a lê não precisa do histórico das conversas que a geraram.

## 1. Intenção e porquê

O painel guarda issues e changelog do Jira Data Center num PostgreSQL próprio e calcula os cards a partir dali. Até o Plano 3, a gravação (`Syncer`) existia só em teste: nada a montava em produção, não havia agendamento, cursor, reconciliação nem retenção de `issue` e `access_log`. Sem isso, o painel nunca terá dados reais.

Esta fase monta **toda** a sincronização (carga inicial, incremental a cada 10 minutos, reconciliação diária, retenção agendada e tela de status), com uma única chave de liga/desliga: a flag `JIRA_LIVE_ENABLED`. A execução é 100% offline (fixtures e `MockEngine`), com prova automática de que a flag `false` impede qualquer requisição.

Três complementos fecham a fase:

- **Dados de demonstração** (Tarefa 10): hoje o painel sobe vazio enquanto a flag está `false`, e o usuário não consegue ver as telas com números. A fase carrega, só em desenvolvimento, issues e changelog **fictícios**, sem tocar no Jira.
- **Hook do Claude e checklist alinhados à flag** (Tarefa 11): a trava de ferramentas (`.claude/hooks/guard.sh`) e o checklist da primeira conexão ainda seguem a regra antiga ("pedir autorização a cada pesquisa"). Eles passam a obedecer só à flag.
- **Vínculos entre issues** (Tarefa 12, a última): a Review (`Checklist Review`) se liga à sua task pelo link "Blocks / is blocked by" do Jira (`fields.issuelinks`), não pelo campo `parent`. A fase grava esses links na tabela `issue_link` e entrega o pareamento Task × Review que o Plano 4 (card Task × Review) consome.

## 2. Objetivos, não objetivos e antiobjetivos

### Objetivos

- Sincronização incremental com marca d'água (cursor por instância, com sobreposição de minutos), carga inicial limitada ao corte de retenção, reconciliação diária com remoção lógica e retenção agendada de `issue`, `access_log` e `sync_run`.
- Agendador único por processo, ligado ao ciclo de vida do Ktor, com relógio e espera injetáveis.
- Registro de cada execução (`sync_run`) só com códigos, e tela de status para o gerente.
- Suspensão automática da instância após 401/403/429 ou 3 falhas seguidas, até o reinício do app.
- Filtro de leitura pela ficha do card (o que a sincronização traz a mais não vaza para os cards).
- Painel com números sem Jira, por dados fictícios, só em desenvolvimento.
- Hook do Claude e checklist obedecendo à flag.
- Vínculos "Blocks" gravados por instância (uma linha por link, pertencendo às duas pontas) e pares Task × Review resolvidos de forma determinística, nas duas direções.

### Não objetivos

- Escrita no Jira (atribuir, comentar, transicionar, corrigir vencimento): é o Plano 16 (`2026-10-25-plano-16-escrita-no-jira-pelo-painel.md`), futuro.
- Parear Review e task pelo campo `parent` (`parent_key` fica só para hierarquia/Epic).
- Worklog (nenhum dado de worklog é sincronizado nesta fase).
- Usernames diferentes na instância Sidia (`jira_sidia`), e testes com mais de uma instância sincronizando (a configuração aceita; os testes cobrem a `samsung`).
- Mostrar a instância `sidia` em alguma tela (os dados de demonstração dela só provam o isolamento).
- Qualquer conexão real ao Jira durante a execução do plano.

### Antiobjetivos (falha mesmo que "funcione")

- Qualquer requisição ao Jira com `JIRA_LIVE_ENABLED=false`, por qualquer caminho (cliente, agendador, carga inicial, reconciliação, dados de demonstração, ferramenta de IA).
- Uma variável que não seja a flag ligar a sincronização (`SYNC_MODE`, contador, tabela de autorizações, modo manual).
- Uma IA ligar a flag por conta própria, inclusive dentro de um comando.
- Reconciliação que apaga a base local porque o Jira respondeu vazio ou pouco demais.
- Cursor que avança além da última issue gravada (perda silenciosa de dados) ou que recua.
- Texto de exceção, token, URL, JQL ou username em `sync_run`, log ou resposta HTTP.
- Dados de demonstração em produção, ou misturados com dados reais no mesmo banco.
- Hook que afrouxa alguma trava com a flag ausente, `false` ou inválida.
- Vínculo apagado porque a issue chegou sem o campo `issuelinks`, link duplicado por ter sido visto pelas duas pontas, ou desempate por ordem de texto (TG-10 antes de TG-9).

## 3. Restrições

### Regras transversais do projeto (valem nesta fase)

- **Flag do Jira (regra 1 do `CLAUDE.md`, reescrita em 25/10):** `JIRA_LIVE_ENABLED` é a **única trava**. `false` (padrão e estado atual): proibido acessar qualquer servidor Jira por qualquer caminho, de qualquer IA ou pessoa. `true`: acesso liberado (app, sincronização periódica e ferramentas), sem pedido de autorização por pesquisa; a primeira conexão segue `docs/jira/checklist-primeira-conexao.md` como procedimento cuidadoso recomendado. **Só o usuário muda a flag.** Testes usam sempre fixtures e `MockEngine`, mesmo com a flag `true` no ambiente.
- **Visibilidade por papel:** em `/api` tudo é negado por padrão; `USUARIO` vê só o próprio; `SQUAD_LEADER` o próprio e o seu squad; `COORDENADOR` e `GERENTE` veem todos; issue sem responsável ou com responsável fora de `papeis.yaml` só para coordenação e gerente. A tela de status da sincronização é só do `GERENTE`.
- **Sem ranking de pessoas;** tabelas de pessoas em ordem alfabética (nesta fase, nenhuma tabela de pessoas é criada).
- **Concluída** = categoria de status `done` **e** `resolution` preenchida; divergência vira aviso informativo `CONCLUSAO_DIVERGENTE` (Qualidade dos dados).
- **Atraso justificado** com duas métricas lado a lado ("no prazo" e "no prazo ajustado"); labels em YAML. Nesta fase só as fixtures de demonstração preparam casos para isso (o cálculo é do Plano 4).
- **Fuso America/Manaus** para todo "dia" (horários de reconciliação e retenção, corte de retenção); **dias úteis** por padrão nas métricas, com opção de dias corridos.
- **Interface:** temas Claro, Escuro e Sidia; pt-BR e inglês completos; WCAG AA; a cor nunca é o único sinal (`✓`, `▲`, `●` sempre com texto).
- **Dados pessoais:** nada de `displayName`, e-mail, avatar ou `accountId` em banco, log ou fixture; fixtures só com nomes fictícios e domínio `.invalid`; nunca editar nem commitar `.env`, `config/papeis.yaml`, `equipes.yaml`, `ausencias.yaml`, `squads.yaml` nem `Filtros_Status_tool.txt`.
- **Banco real nos testes:** PostgreSQL por Testcontainers (Docker ativo), nunca H2 ou mock.
- **Fail-closed em produção:** configuração obrigatória ausente derruba a subida; só o modo dev cai nos modelos `.exemplo`.
- Conversa e comentários de código em pt-BR; commits em Conventional Commits pt-BR.

### Restrições técnicas da fase

- Variáveis novas começam com `SYNC_` ou `DASHBOARD_` (nunca `JIRA_`, que o hook barra); nenhuma delas liga a sincronização.
- JQL só por `JqlBuilder`/`CardQueryFactory`; nenhum JQL guardado em banco ou log.
- Migrações: `V4__sincronizacao.sql` (Tarefa 2), `V5__marcador_de_falhas.sql` (correção da Tarefa 2, já na branch de implementação) e `V6__vinculos_entre_issues.sql` (Tarefa 12). Regra: confirmar o próximo número livre na hora da execução (se outro plano mesclar migração antes, a da Tarefa 12 sobe); nunca editar migração mesclada.
- Nenhum `Instant.now()` solto em `sync/`: `java.time.Clock` injetado.
- Nenhum `Settings.copy(` em `src/main` (o teste `SomenteAFabricaCriaClientesJira` varre).
- Testes com "flag ligada" usam o construtor `internal` de `JiraClientFactory` com `MockEngine` e host `jira.exemplo.invalid`; nunca o motor CIO.

## 4. Decisões já tomadas

### Do usuário (25/10)

1. A flag é a única trava; sem contador, tabela de autorizações nem modo manual; `false` = 0 requisições por qualquer caminho; `true` = o agendador roda sozinho.
2. Universo da ficha de sincronização: projeto TG-Basic; tipos Checklist, Checklist TG, Checklist Review, Feature check, TG Feature check e Task; Epic fora; canceladas incluídas (a auditoria precisa delas).
3. Fuso do JQL: `SYNC_FUSO_SERVIDOR`, padrão America/Manaus, até ler o `timeZone` do usuário do token no Passo 1 do checklist.
4. Retenção do `access_log`: o mesmo `RETENTION_MONTHS` (24).
5. Retenção de `issue`: resolvidas **e** sem atualização há mais de 24 meses são apagadas com seus eventos; abertas nunca.
6. Suspensão após 401/403/429 (e após 3 falhas seguidas) até o usuário reiniciar o app (ou religar a flag, que também exige reinício).
7. Status do sincronizador só para o gerente.
8. Reconciliação nunca remove mais de 50% da base local; acima disso, `RECONCILIACAO_SUSPEITA` e nada é removido.
9. Dados de demonstração: com `DASHBOARD_DEV_MODE=true` e `DASHBOARD_DEMO_DATA=true` (padrão `false`), carga na subida, sem Jira, idempotente, cenário rico (concluídas, abertas paradas, trocas de responsável, vencimento e labels de atraso, 2 squads, 2 instâncias); `DASHBOARD_DEV_MODE=false` com demo `true` derruba a subida; teste com banco real; README com "como ver o painel hoje em 3 comandos".
10. Hook do Claude consciente da flag: libera o acesso ao Jira só com `JIRA_LIVE_ENABLED=true` no ambiente do hook; bloqueia com `false` ou ausente; checklist troca "peça autorização expressa" por "confirme `JIRA_LIVE_ENABLED=true` e rode o passo", mantendo ordem e cuidados.
11. **[usuário]** A Review se liga à task pelo **link de issue "Blocks / is blocked by"** (`fields.issuelinks`), **não** pelo campo `parent`; as duas direções valem (a Review pode bloquear ou ser bloqueada). Ação no painel: lista de divergências com link direto para a task no Jira (Plano 4); escrita no Jira pelo painel é futura (Plano 16).

### Valor padrão recomendado (25/10, reversível; marcado assim no plano)

- Demo com `JIRA_LIVE_ENABLED=true` também derruba a subida.
- Datas das fixtures deslocadas até hoje em **semanas inteiras arredondadas para baixo** (dia da semana preservado; pode ser para trás), com todo instante até a véspera da âncora: nenhum dado fica no futuro. Casos sensíveis a limiar contado a partir de agora ficam a pelo menos 7 dias do limiar.
- Fixtures só com status do `status_map.exemplo.yaml` e tipos da ficha de sincronização (exceções deliberadas: `Epic` e `Cancelled`).
- Recarga idempotente por substituição das chaves de prefixo `DEMO` (instância `samsung`) e `DEMOCAT` (instância `sidia`).
- O carregador recusa banco que já tenha issue real (`BANCO_COM_DADOS_REAIS`); com a flag ligada e dados de demonstração no banco, a subida falha (`BANCO_COM_DADOS_DE_DEMONSTRACAO`). Nada é apagado automaticamente; a saída é `docker compose down -v`.
- Faixa da tela com demo: "MODO DESENVOLVIMENTO: dados fictícios de demonstração"; sem demo, `/api/meta` não muda.
- Hook: valor inválido da flag = travado; ligar a flag dentro de um comando continua barrado com a trava fechada; com a flag ligada, exibir token ou senha no terminal continua barrado; a flag do hook fica no ambiente de abertura do Claude Code ou no `.claude/settings.local.json` (git-ignorado pelo repositório a partir desta fase), nunca no `.claude/settings.json` versionado. Nas duas formas ela é **herdada por todo comando que o Claude Code roda** (inclusive `docker compose up` e `./gradlew`), então ligar a flag do hook liga a do app no que ele lançar; por isso a tarefa `test` do Gradle fixa `JIRA_LIVE_ENABLED=false`.
- Vínculos (Tarefa 12): `vinculos.yaml` (modelo `app/config/vinculos.exemplo.yaml`) com `tipoDeLink: Blocks` e `tiposReview: ["Checklist Review"]` (task = os demais tipos); uma linha por link em `issue_link`, normalizada pela ponta outward (`direction` `OUTWARD`, ou `SIMETRICO` em tipo com `inward == outward`); `issuelinks` ausente no JSON não mexe nos vínculos, lista vazia remove; link que some ganha `removida_em`; só vale como par o link com exatamente uma ponta Review e a outra task; ambiguidade (Review com mais de uma task, ou task com mais de uma Review) gera aviso informativo e fica o par de menor chave por `ORDEM_NATURAL_DE_CHAVES`, sem duplicar; nome exato do tipo e direção confirmados na primeira conexão (checklist, Passo 4).

## 5. O que fica para quem implementa

- Estrutura interna de `SyncService`, `Agendador` e `DadosDeDemonstracao`, desde que as assinaturas do plano sejam exatas.
- Formato do `manifesto.yaml` além dos campos do plano; o conteúdo exato das fixtures, desde que cubra a lista de casos da Tarefa 10 (o teste `cenarioCobreAuditoriaETempoEmStatus` confere).
- Mensagens de log e textos de i18n da tela de status (pt-BR e inglês).
- Expressões regulares do hook, desde que todos os casos de `test-guard.sh` passem.
- Confirmar, na execução, se o hook enxerga a variável definida em `.claude/settings.local.json`; se não, documentar só a opção pelo ambiente e registrar `Ruling` no arquivo de progresso.
- Toda escolha que o plano deixa aberta: a mais segura e reversível, registrada como `Ruling: <o quê> — <por quê> — <custo se errado>`.

## 6. Critérios de aceitação (verificáveis)

| Critério | Prova (teste do plano) |
|---|---|
| Ambiente vazio = flag `false` = modo `DESLIGADO`; nenhuma `SYNC_*` liga nada | `padraoEhDesligado`, `nenhumaVariavelSyncLigaASincronizacao` (T1) |
| Flag `false` = 0 requisições na fábrica, na carga inicial, no incremental, na reconciliação e no agendador por um dia simulado | `fabricaComFlagFalseNaoFazRequisicao` (T1), `cargaInicialEIncrementalComFlagFalse` (T5), `reconciliacaoComFlagFalse` (T6), `agendadorComFlagFalseUmDiaSimulado` (T7) |
| Cursor nunca recua, não passa da última issue gravada e não perde o mesmo minuto | `cursorAvancaENuncaRecua` (T2), `falhaNoMeioNaoAvancaCursorAlemDoCommit`, `cursorComSobreposicaoNaoPerdeMesmoMinuto` (T5) |
| 401/403/429 suspendem sem nova tentativa até o reinício | `erro401SuspendeSemNovaTentativa` (T5), `reinicioLimpaSuspensao` (T7) |
| `sync_run` sem texto de erro | `execucaoGuardaSoCodigos` (T2), `registroNaoGuardaTextoDeErro` (T5) |
| Reconciliação não remove mais de 50% nem com Jira vazio | `reconciliacaoAbortaQuandoJiraDevolvePoucoDemais`, `remocaoDeExatamenteMetadePassa` (T6) |
| Retenção apaga só o que deve, com fronteira de Manaus | `RetentionTest.retencaoApagaIssueResolvidaAntigaEAccessLog` (T7) |
| Status só para o gerente, sem segredos, mostrando "desligado pela flag" | `statusSoParaGerente`, `statusNaoVazaSegredos`, `statusMostraDesligadoPelaFlag` (T8) |
| Ficha mais larga não vaza para o card; concluída divergente vira aviso | `cardIgnoraTipoEStatusForaDaFicha`, `concluidaSemResolutionViraAvisoDivergente` (T8) |
| Runbook e README com a regra nova | `runbookAvisaQueAFlagEhAUnicaTrava`, `readmeSemRegraAntiga` (T9) |
| Demo recusado em produção, com a flag ligada e em banco com dados reais; nenhuma requisição | `demoEmProducaoDerrubaSubida`, `demoComFlagLigadaDerrubaSubida`, `demoRecusaBancoComDadosReais`, `flagLigadaComDadosDeDemoDerrubaSubida`, `demoNaoFazRequisicao` (T10) |
| Demo idempotente, nas duas instâncias, com números no Tempo em status e nada no futuro | `recarregarEhIdempotente`, `carregaAsDuasInstancias`, `tempoEmStatusMostraNumerosComDemo`, `datasDeslocadasEmSemanasInteiras`, `nenhumInstanteNoFuturo` (T10) |
| Fixtures sem dados pessoais, com vocabulário conhecido e o cenário completo | `fixturesSemDadosPessoais`, `vocabularioDasFixturesEstaNoMapaENaFicha`, `instantesAteAVesperaDaAncora`, `cenarioCobreAuditoriaETempoEmStatus` (T10) |
| Suíte de testes nunca herda a flag ligada do terminal | `suiteNaoHerdaAFlagDoAmbiente` (T11) |
| Painel visível em 3 comandos | seção do `app/README.md` (T10, Passo 5) e conferência manual |
| Hook travado com flag ausente, `false` ou inválida; liberado só com `true`; segredo nunca exibido | casos `trava:` e `liberado:` de `.claude/hooks/test-guard.sh` (T11) |
| Checklist sem a regra antiga, cada passo confirmando a flag | `cadaPassoDoChecklistConfirmaAFlag`, `checklistSemRegraAntiga` (T11) |
| `issuelinks` lido nas duas direções, só com chaves e tipo, e pedido pela ficha de sincronização | `outwardIssueViraOrigem`, `inwardIssueViraDestino`, `naoGuardaDadosDaOutraPonta`, `itemMalformadoIgnorado`, `fichaSincronizacaoPedeIssuelinks` (T12) |
| Vínculo pertence às duas pontas, idempotente, removido quando some, nunca apagado por campo ausente, isolado por instância | `linkVistoPelasDuasPontasEhUmaLinha`, `vinculosIdempotentes`, `linkRemovidoGanhaRemovidaEm`, `linkQueVoltaLimpaRemocao`, `campoAusenteNaoApagaVinculos`, `vinculosPorInstanciaIsolados` (T12) |
| Par Task × Review pelo link `Blocks` nas duas direções, com desempate pela chave natural | `reviewQueBloqueiaATask`, `reviewBloqueadaPelaTask`, `blocksEntreDuasTasksIgnorado`, `reviewComReviewIgnorado`, `ambiguidadeUsaChaveNaturalMenor`, `taskComDuasReviewsUsaChaveMenor`, `semDuplicar` (T12) |
| Vínculos acompanham renomeação, retenção e recarga da demo; a demo grava os dois pares `Blocks` | `renomearAtualizaAsPontasDoVinculo`, `retencaoApagaVinculosDaIssuePurgada`, `carregaVinculosDaDemo`, `recarregarEhIdempotente`, `cenarioCobreAuditoriaETempoEmStatus` (T12) |
| Suítes completas verdes | `./gradlew test`, `pnpm vitest run`, `pnpm build`, `sh .claude/hooks/test-guard.sh` |

## 7. Riscos e pendências

- **Nunca testado em servidor real:** formato do `from/to` do assignee (`JIRAUSER*`), precisão de minuto do JQL, `timeZone` do usuário do token, se o Jira honra o `pageSize`, o nome exato do tipo de link (`Blocks`), os textos `inward`/`outward` e em que direção a Review aparece em relação à task (item no Passo 4 do checklist).
- **Mudança de link sem mudança de `updated`:** o incremental e a reconciliação só rebuscam a issue cujo `updated` mudou. Não se sabe se criar ou remover um link no DC altera o `updated` das duas pontas; se não alterar, vínculos ficam desatualizados em silêncio. Conferir no Passo 4 do checklist.
- **Numeração de migração:** a `V6` da Tarefa 12 pode colidir com migração de outro plano mesclada antes; confirmar o número livre na execução. Os números esperados dos demais planos (V7 em diante) já contam a V4 a V6 do 4A (tabela "Migrações" do índice dos planos); confirmar o livre na execução.
- **Teto de itens e de requisições** contra dezenas de milhares de issues: os limites `SYNC_MAX_ITENS` e `SYNC_MAX_REQUISICOES` são palpites; o runbook começa com limites pequenos.
- **Dados de demonstração** deslocados em semanas: as datas ficam fixas na semana, mas "agora" anda todo dia, e números em dias úteis podem mudar perto de feriados (aceitável; é demonstração). Com a flag ligada no terminal, a subida com demonstração falha de propósito (o compose repassa a flag).
- **Herança da flag:** ligar a flag para o hook do Claude a liga também em tudo que o Claude Code lança; só a tarefa `test` do Gradle a fixa em `false`.
- **Prefixo `DEMO`:** um projeto real chamado `DEMO` no Jira seria confundido com demonstração; a recusa com a flag ligada falha para o lado seguro (não sobe), nunca apaga.
- **Hook:** é trava de acidente, não de má-fé (valor calculado em tempo de execução passa; está documentado em `test-guard.sh`). O Cline não tem hook: lá a regra é só de conduta (`.clinerules/`).
- **Nome único do teste do checklist:** esta fase substitui `cadaPassoDoChecklistExigeAutorizacaoExpressa` por `cadaPassoDoChecklistConfirmaAFlag` (T11). Os Planos 4 (Tarefa 9) e 8 (Tarefa 8) já usam esse nome e só conferem que ele passa; nenhum outro nome deve ser criado.
- Pendências herdadas que não são desta fase: avisos do backend só em pt-BR, filtros do frontend em texto livre, provedor SSO/OIDC real, validação com o DPO.

## 8. Glossário (nomes das fases anteriores que esta fase consome)

| Nome | Onde | O que é |
|---|---|---|
| `LiveGuard` | `jira/LiveGuard.kt` | Barra toda requisição com a flag `false` |
| `JiraClientFactory` (construtor `internal` com `http`) | `jira/JiraClientFactory.kt` | Única forma de criar cliente Jira; testes injetam `MockEngine` |
| `JiraClient.searchAll`, `JiraException`, `SearchRequest` | `jira/JiraClient.kt`, `jira/JiraModels.kt` | Busca paginada com retry; erro estruturado com status HTTP |
| `SomenteAFabricaCriaClientesJira` | `src/test/.../jira/` | Varre `src/main` atrás de cliente criado fora da fábrica e de `Settings.copy(` |
| `Settings`, `Settings.fromEnv`, `jiraLiveEnabled`, `dashboardDevMode`, `retentionMonths` | `config/Settings.kt` | Configuração por ambiente ("vazio = padrão", `toString` sem segredos) |
| `AppContext`, `AppContext.load` | `AppContext.kt` | Carrega YAML, abre o banco e migra; fail-closed em produção |
| `Application.module`, `main` | `Application.kt` | Módulo Ktor; `/api/health` e `/api/meta` públicos |
| `JqlBuilder`, `JqlValues`, `CardSpec`, `CardQueryFactory`, `FieldDictionary` | `jql/` | Única forma de gerar JQL; valores suspeitos são rejeitados |
| `Syncer`, `SyncSummary` | `sync/Syncer.kt` | Busca e grava issues com changelog (idempotente) |
| `ChangelogParser`, `ChangeEventRecord` | `sync/ChangelogParser.kt` | Changelog → eventos; só guarda valor dos campos permitidos |
| `IssueStore`, `PostgresIssueStore.upsert`, `timelines`, `temOsVersion` | `sync/IssueStore.kt` | Gravação e leitura de `issue`/`change_event` por `(instance, key)` |
| `SanitizadorRaw` | `sync/SanitizadorRaw.kt` | Tira dados pessoais do JSON antes de `issue.raw` (apaga `key` de usuário) |
| `Retention.limite`, `Retention.purge` | `db/Retention.kt` | Corte de retenção em Manaus |
| `AccessLog` | `db/AccessLog.kt` | Toda rota de dados grava `access_log`; falha = 503 |
| `requireRole`, `Visibility`, `RoleDirectory`, `Principal`, `Role` | `security/` | Controle de acesso e visão por papel |
| `TempoEmStatusRoutes`, `AvisoDto`, `DrillRow` | `cards/` | Card de exemplo; esta fase acrescenta `params` ao `AvisoDto` |
| `testContext`, `testContextComBanco` | `src/test/.../TestContext*.kt` | Contextos de teste (o segundo com PostgreSQL) |
| `PreparacaoDocsTest` | `src/test/.../jql/` | Protege os documentos de `docs/jira/` (checklist, matriz, runbook) |
| `guard.sh`, `test-guard.sh`, `ensure-docker.sh` | `.claude/hooks/` | Trava do Claude Code, seus testes e o Docker sempre ativo |
| `papeis.exemplo.yaml` | `app/config/` | Pessoas fictícias (`dev.alfa1`, `dev.alfa2`, `dev.beta1`, `lider.alfa`, `lider.beta`, `coord.um`, `gerente.exemplo`...) usadas pelas fixtures de demonstração |

Nomes que esta fase **produz** e as próximas consomem: `SyncSettings`, `SyncGate`, `SyncMode`, `MotivoNegacao`, `SyncStateStore`, `RunStatus`, `RunResumo`, `RegistroDeExecucao`, `ChavesDeUsuario`, `OrcamentoDeRequisicoes`, `TetoDeItensExcedidoException`, `AtualizadoDesde`, `buildLeve`, `SyncService`, `IssueIndex`, `Agendador`, `montarAgendador`, `FiltroDeFicha`, `ctx.fichas`, `ctx.campos`, coluna `issue.removida_em` e `issue.jira_id`, ficha `sincronizacao.card.yaml`, `AvisoDto.params`, `CONCLUSAO_DIVERGENTE`, `DadosDeDemonstracao`, `DASHBOARD_DEMO_DATA`, `cadaPassoDoChecklistConfirmaAFlag`, tabela `issue_link`, `IssueLinks`, `VinculoRecord`, `IssueLinkStore`, `VinculosConfig` (`ctx.vinculos`), `ParesTaskReview`, `ParDeChaves`, `ResolucaoDePares`.

## 9. Referência

Plano: `docs/superpowers/plans/2026-10-25-plano-04a-sincronizacao.md`. Índice: `docs/superpowers/plans/2026-10-24-indice-dos-planos.md`. Guia para outra IA: `docs/guia-execucao-outra-ia.md`. Documentos do Jira: `docs/jira/` (checklist, matriz, runbook).
