# Fase 16: Escrita no Jira pelo painel (vencimento e labels) — Plano de implementação

> **FUTURO — não executar sem decisão do usuário.** Este plano muda a política de segurança do projeto: hoje o painel é **somente leitura** e nunca escreve no Jira (spec mestra, seção 2, "Não objetivos"; Plano 6, T16; Plano 7, T11). Nenhuma tarefa deste plano começa antes de o usuário responder os pontos de parada da Tarefa 1. Sem resposta, o plano fica arquivado como está.

> **Para executores:** USE superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Os passos usam caixas `- [ ]`. Para o ClineSR, isso equivale à seção 4 de `docs/guia-execucao-outra-ia.md`.

**Objetivo (Goal):** Permitir que quem já pode alterar vencimento e labels de atraso pelas regras da auditoria (Reporter/PL, coordenadores, gerente, Squad Leader do responsável) corrija, **pelo painel**, o vencimento (`duedate`) e as labels (adicionar, remover, trocar) de uma task do Jira, individualmente ou em lote, sempre por **prévia → confirmação explícita → execução**, com conflito detectado, idempotência, registro imutável de quem, quando, antes, depois e motivo, e reversão da última alteração. Pedido do usuário em 25/10: "futuramente vamos nos planejar para fazer esses ajustes direto pelo nosso dashboard: correções de due dates, adição, remoção ou ajuste de label; escrever e/ou atualizar na task do Jira pelo nosso dashboard, individualmente ou em lote."

**Arquitetura (Architecture):** Um pacote novo `br.sidia.painel.escrita`, **fora** do pacote `jira/`, para que as garantias de somente leitura do cliente de consulta continuem valendo sem mudança (`SemEscritaNoJiraTest` do Plano 7 e `SomenteLeituraTest` do Plano 6 seguem verdes).
- **Trava dupla:** `WriteGuard` exige `JIRA_LIVE_ENABLED=true` **e** `JIRA_WRITE_ENABLED=true`, lidas a cada requisição, e `DASHBOARD_DEV_MODE=false`. Com qualquer uma fora disso, o escritor não envia nada (0 requisições de qualquer método).
- **Escritor:** `JiraWriteClient` é o único arquivo de `src/main` com `HttpMethod.Put`. Ele é criado só por `FabricaDeEscritaJira`. Tem caminho HTTP próprio: não reusa `requisitarJson`, que exige corpo JSON e quebraria com o 204 vazio, nem `executarComRetry`, que repete 5xx e falhas de rede e poderia escrever duas vezes.
- **Domínio puro:** `IntencaoDeAlteracao` só aceita os campos permitidos. `PermissaoDeEscrita` reaproveita a `Autorizacao` do Plano 4, cruzada com a `Visibility`. `Previa` calcula antes e depois e os sinais de auditoria que a mudança vai gerar.
- **Execução:** `ExecutorDeEscrita` confirma, relê a issue antes de cada PUT, compara com o valor esperado (conflito), escreve em ritmo limitado, confere depois e grava cada passo em `escrita_jira`. Essa tabela é só de acréscimo: um gatilho barra `UPDATE` e `DELETE`, com uma única exceção, a retenção.
- **Auditoria:** as alterações que a conta de serviço fez a pedido do painel são reconhecidas e atribuídas à pessoa real (o Plano 4 não as marca como "não autorizadas").
- **API e tela:** rotas `/api/escrita/*` com CSRF (Plano 9). Ações a partir da lista de divergências e da gaveta da issue, modal de prévia com CSV e histórico com "desfazer".

Nenhum teste automático acessa o Jira real: o escritor e o leitor usam `MockEngine` com contadores por método.

**Stack (Tech Stack):**
- Kotlin 2 + Ktor 3.3.1 (cliente com `MockEngine`, servidor com o `ProtecaoCsrf` do Plano 9)
- kotlinx.serialization e kaml (YAML estrito)
- SQLite embutido + Flyway (testes com SQLite real em arquivo temporário)
- React + TypeScript (Vite, Vitest, Testing Library, TanStack Query)
- `sh` nos testes do hook

**Spec da fase (ler primeiro):** `docs/superpowers/specs/2026-10-25-spec-fase-16-escrita-no-jira-pelo-painel.md`. **Spec mestra:** `docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md` (seções 2 "Não objetivos", 3, 4.3, 7.1 a 7.3, 10). **Spec e plano da auditoria:** `2026-10-25-spec-fase-04-auditoria.md` e `2026-10-25-plano-04-auditoria.md` (regras de quem pode alterar, `Autorizacao`, `PROPRIO_RESPONSAVEL`, Task × Review, tela Divergência).

## Estado herdado (confirmar na execução)

Antes da Tarefa 2, o executor confere na `main` os nomes abaixo. Se um plano anterior deu outro nome à mesma capacidade, use o da `main`, ajuste só os nomes deste plano e registre `Ruling:` no arquivo de progresso. **Planos 10 e 11 ainda não estavam escritos em 25/10:** o que vier deles (por exemplo a trava de LGPD) é confirmado aqui, nunca presumido.

| Nome ou capacidade | Origem |
|---|---|
| `LiveGuard`, `JiraClientFactory`, `JiraInstanceConfig`, `JiraAuthKind`, `RetryPolicy`, `SomenteAFabricaCriaClientesJira`, `padraoEscapado` | `jira/` (Planos 1 e 2, na `main`) |
| `Settings` (`jiraLiveEnabled`, `dashboardDevMode`, `jiraInstances`, proibição de `copy(jiraLiveEnabled = ...)` em `src/main`) | `config/Settings.kt` (na `main`) |
| `Role`, `Principal`, `RoleDirectory` (`rolesOf`, `squadOf`, `squadsLedBy`, `isReporterAuthorized`), `Visibility.visibleUserIds`, `requireRole`, `normalizarUsername` | `security/` (na `main`) |
| `AccessLog.record`, `AccessLogException` (falha = 503 sem dados) | `db/AccessLog.kt` (na `main`) |
| `Autorizacao` (autorização na data civil de Manaus, com `PROPRIO_RESPONSAVEL`), `CodigoSinal`, `LabelsDeAtraso`, `RegrasDeAuditoria`, `ParTaskReview`, `TaskReviewLinhaDto`, `executarAuditado`, tela `screens/Divergencia/`, `shared/csv.ts` | Plano 4 (confirmar nomes) |
| `ChangeEventRecord`, `change_event`, `issue.removida_em`, `Syncer` (sincronização de uma chave), `OrcamentoDeRequisicoes` (se existir) | Planos 3 e 4A (confirmar nomes) |
| `OidcAuthenticator`, sessão `painel_sessao`, inventário `ROTAS_PUBLICAS` / `InventarioDeRotasTest.inventarioConhecido` | Plano 8 (confirmar nomes) |
| `ProtecaoCsrf` (`protecaoCsrf`, `CABECALHO_CSRF`, `metodoMutante`), `ErroDto`, `TestHttp.comCsrf`, `todoMetodoMutanteExigeCsrf`, frontend `enviarJson` e `ErroDaApi` | Plano 9, T4 (confirmar nomes) |
| Gaveta universal da task `TarefaDrawer` e rota `/api/tarefa/{key}` (já filtrada pela visão) | Plano 10, T5 e T6 (confirmar nomes) |
| `SemEscritaNoJiraTest.nenhumCaminhoEscreveNoJira` | Plano 7, T11 |
| `SomenteLeituraTest` | Plano 6, T16 |
| Trava de LGPD de Auditoria e Integridade (nome da variável) | Plano 11 (não escrito em 25/10; confirmar) |
| Produção com OIDC real e leitura validada com a flag ligada | Planos 13 e 14 |
| Próximo número livre de migração Flyway | `resources/db/migration/` (usar a próxima livre na execução; hoje só existe a V1, então a V2; nunca editar a V1 nem outra mesclada) |

## Pontos de parada: perguntar ao usuário

Todos estão na Tarefa 1. Cada um é uma pergunta separada, com a opção recomendada em primeiro lugar e marcada "(Recomendada)". Sem resposta, o plano **não** começa; a resposta e a data entram em `docs/escrita/decisoes.md` e no arquivo de progresso.

1. **Seguir com a escrita no Jira?**
   - *Aprovar o plano agora e executá-lo só depois da Fase 14 (leitura validada em produção) e do aceite do DPO (Recomendada):* a escrita parte de números já conferidos com o Jira real. Consequência: nada muda no painel até lá, e o plano fica na fila.
   - *Executar já, offline, com as duas flags desligadas:* o código fica pronto e testado com `MockEngine`. Consequência: o produto ganha código que escreve e que ninguém usa ainda, e a superfície de risco fica maior no repositório antes da hora.
   - *Não fazer escrita; manter o painel só de leitura:* as correções continuam no próprio Jira. Consequência: o plano é arquivado e as garantias de somente leitura ficam como estão.
2. **Credencial usada para escrever.** O token do SSO/OIDC do painel **não é** uma credencial do Jira: o Jira DC não o aceita.
   - *Conta de serviço dedicada só para escrita, com permissão mínima (Recomendada):* token próprio, separado do de leitura (`JIRA_SAMSUNG_WRITE_TOKEN`). Permissões mínimas, a confirmar com o administrador do Jira: Browse Projects, Edit Issues e Schedule Issues no projeto TG-Basic. Consequência: o histórico do Jira mostra a conta de serviço como autora. O painel guarda a pessoa real em `escrita_jira`, e a auditoria passa a reconhecer essas alterações (Tarefa 12). Revogar é fácil (um token só).
   - *Autorização por pessoa pelo OAuth 2.0 do próprio Jira DC (app link de entrada):* o Jira mostra a pessoa real como autora. Consequência: depende da versão do Jira DC e de um administrador do Jira criar o app link. O painel teria de guardar tokens de atualização por pessoa, cifrados, e isso exige tarefas extras (fluxo de consentimento, rotação, revogação). Registrar como `Ruling:` e replanejar as Tarefas 3 e 12.
   - *Token pessoal (PAT) colado por cada pessoa no painel:* autoria real no Jira. Consequência: o painel passa a guardar segredos pessoais de dezenas de pessoas. Não recomendado.
   - *Reusar o token de leitura atual:* sem trabalho de infraestrutura. Consequência: mistura privilégios, e o token de leitura deixa de ser "só leitura". Não recomendado.
3. **Campos da primeira versão.**
   - *Só vencimento e labels (adicionar, remover, trocar) (Recomendada):* cobre o pedido de 25/10 e as divergências Task × Review. Consequência: responsável e status continuam só no Jira.
   - *Vencimento, labels e responsável:* consequência: tarefa extra de troca de responsável, com validação de username pelo `RoleDirectory`, e risco de "passa-bola" feito pelo próprio painel.
   - *Incluir também status/transição:* consequência: transições têm telas, campos obrigatórios e validadores por fluxo, que o painel não conhece. Não recomendado nesta fase.
4. **Quem pode escrever e por onde.**
   - *As mesmas regras da auditoria, com entrada pela tela que cada um já vê (Recomendada):* Reporter (PL), coordenadores, gerente e Squad Leader do squad do responsável, nunca o próprio responsável. Coordenação e gerência entram por Divergência e Auditoria; PL e Squad Leader, pela gaveta da issue nos cards que veem (Plano 5). Consequência: a regra de "quem pode" é uma só, no painel e na auditoria.
   - *Só coordenadores e gerente:* entrada só por Divergência e Auditoria. Consequência: o PL e o Squad Leader continuam corrigindo no Jira, mais simples e com menos gente escrevendo.
   - *Só o gerente:* consequência: um gargalo, mas o menor risco.
5. **Tamanho máximo do lote.**
   - *Até 20 issues por lote, com confirmação digitando o número de alterações (Recomendada):* cobre a correção de divergências de uma semana. Consequência: lotes maiores viram várias prévias.
   - *Até 50 issues por lote:* consequência: menos cliques, mas um erro de filtro atinge mais tasks.
   - *Sem lote (uma issue por vez):* consequência: o menor risco, mas a correção em massa fica cansativa e a pessoa volta ao Jira.
6. **Labels que o painel pode escrever.**
   - *Só as labels de uma lista em YAML versionado (`escrita.exemplo.yaml`: as de `labels_atraso.yaml` mais uma lista própria) (Recomendada):* impede erro de digitação e label inventada. Consequência: label nova exige mudar o YAML e reiniciar.
   - *Qualquer label com formato válido do Jira:* consequência: mais livre, mas um erro de digitação vira label nova no projeto.
7. **Notificações do Jira.**
   - *Deixar o padrão do Jira (notifica os observadores) (Recomendada):* não exige permissão extra. Consequência: o lote gera e-mails do Jira; o limite do lote reduz o volume.
   - *Pedir para não notificar (`notifyUsers=false`):* consequência: segundo a documentação pública, esse parâmetro exige que a conta seja administradora do projeto ou do Jira, o que fere o "mínimo privilégio". Assumido, não validado.
8. **LGPD e retenção do registro de escrita.** `escrita_jira` guarda username, motivo e valores.
   - *Submeter ao DPO antes de ligar `JIRA_WRITE_ENABLED`; até lá, retenção provisória igual à do `access_log` (24 meses) (Recomendada):* consequência: a escrita só liga com o aceite registrado em `docs/escrita/ripd-escrita.md`.
   - *Guardar sem prazo:* consequência: prova histórica completa, mas fere a minimização sem justificativa do DPO.
   - *12 meses:* consequência: menos dado guardado, e a reversão e a prova de alterações antigas se perdem antes.
9. **Primeira escrita real** (só no fim, depois de mesclado, com as duas flags ligadas pelo usuário).
   - *O usuário escolhe uma issue de teste no TG-Basic e executa o checklist da primeira escrita (Recomendada):* confere o 204, o formato de `update.labels` e as permissões sem tocar em task real. Consequência: uma issue de teste fica com histórico de alterações.
   - *Começar direto por uma divergência real de uma issue:* consequência: mais rápido, mas o primeiro erro de formato atinge uma task do time.

## Restrições globais (Global Constraints)

- **Plataforma (adendo de 27/10, `plans/2026-10-27-adendo-plataforma-sqlite-sem-docker.md`):** SQLite embutido, sem Docker e sem servidor de banco; o adendo vence qualquer menção antiga a PostgreSQL, Docker ou Testcontainers. SQL novo segue a tradução da seção 2 do adendo (instantes em `INTEGER` ms UTC, datas em `TEXT` ISO, listas em JSON com `json_each`, helpers de `db/SqlSupport.kt`). Comandos de terminal: Windows 11 PowerShell (principal), por exemplo `cd app\backend; .\gradlew.bat test` e `cd app\frontend; pnpm test`; em `sh`, `cd app/backend && ./gradlew test`. Flags do Jira e de escrita só valem no `app/.env`.
- **Flag de leitura:** `JIRA_LIVE_ENABLED` é a única trava de **acesso** ao Jira (regra 1 do `CLAUDE.md`). Com `false`, nenhuma requisição sai (leitura nem escrita), por nenhum caminho.
- **Flag de escrita, separada e sempre `false` por padrão:** `JIRA_WRITE_ENABLED`. A escrita exige as **duas** flags `true`; com qualquer uma `false`, nenhuma requisição de escrita acontece. Não existe "escrita sem leitura" nem um modo em que uma flag implique a outra.
  - **Só o usuário liga a flag de escrita.** Nenhuma IA a liga, nem em `app/.env`, manifesto, teste de integração ou comando (a flag só vale no `app/.env`).
  - O nome usa o prefixo `JIRA_` por decisão do usuário, uma exceção à convenção do Plano 13 ("variáveis novas começam com `DASHBOARD_` ou `OIDC_`"). Ela fica ao lado das credenciais `JIRA_SAMSUNG_*` e o hook já barra qualquer `JIRA_*` em comandos.
- **Subida fail-closed:** a aplicação **não sobe** com `JIRA_WRITE_ENABLED=true` se `JIRA_LIVE_ENABLED=false`, se `DASHBOARD_DEV_MODE=true` ou se a credencial de escrita faltar. A mensagem cita só o nome da regra.
- **Nenhum teste automático acessa o Jira real**, mesmo com as flags `true`: `MockEngine` com contador por método e hosts `.invalid` (`https://jira.exemplo.invalid`).
- **Campos permitidos (primeira versão):** `duedate` (definir uma data; **remover não**) e `labels` (adicionar, remover, trocar). Responsável, status, transição, comentário, resumo, descrição e qualquer outro campo são recusados no modelo, no corpo do PUT e no teste estático.
- **Fluxo obrigatório:** prévia (sem nenhuma escrita) → confirmação explícita com motivo → execução. Nenhuma rota escreve sem uma prévia válida, do mesmo usuário, não expirada, com o texto de confirmação certo.
- **Quem pode escrever:** as regras da auditoria (spec mestra 7.1, com `PROPRIO_RESPONSAVEL` do Plano 4), avaliadas **hoje** na data civil de Manaus, sempre cruzadas com a `Visibility`. A permissão de escrever nunca amplia o que a pessoa vê.
- **Sem escrita em modo de desenvolvimento:** o principal `dev` (GERENTE sem login) nunca escreve.
- **Registro imutável:** toda tentativa, resultado, conflito, negação e reversão vira uma linha nova em `escrita_jira`. Falhar ao gravar a tentativa impede o PUT; falhar ao gravar o resultado deixa o item `INCERTA` e interrompe o lote.
- **Escrita sem repetição automática**, exceto 429 com `Retry-After` dentro do teto. Falha de rede, timeout ou 5xx viram `INCERTA` e a issue é relida: nunca se reenvia às cegas.
- **CSRF** em toda rota mutante (Plano 9); só JSON; nenhum `GET` muda estado; toda rota grava `access_log` (sem o texto do motivo nos filtros).
- **Sem ranking de pessoas;** cor nunca é o único sinal (`✓` aplicada, `▲` conflito/negada/falha, `●` incerta/sem mudança, `↶` revertida, sempre com texto); pt-BR e inglês; WCAG AA nos três temas.
- **Dados pessoais:** só nomes fictícios em fixtures (cenário ESC-1); o motivo é texto livre limitado e com aviso "não escreva dados pessoais"; token nunca em log, erro, `toString` ou resposta.
- Configs reais fora do git; versionar só `*.exemplo.yaml`. Comentários em pt-BR; commits Conventional Commits pt-BR (`feat(escrita): ...`).

## Foco da revisão (Review Focus)

1. **Qualquer flag desligada e mesmo assim sai um PUT** (flag lida só na subida, retry depois de desligar, rota que chama o escritor sem o guard) → `quatroCombinacoesDeFlag`, `guardAntesDeCadaTentativa` (T3) e `flagsDesligadasZeroRequisicoesNaApi` (T11).
2. **Escrita duplicada** (duplo clique, reenvio da confirmação, retry de rede depois de o Jira já ter aplicado) → `segundaConfirmacaoNaoRepete` (T8) e `falhaDeRedeViraIncertaSemRepetir` (T3).
3. **Sobrescrever mudança feita no Jira depois da prévia** → `conflitoQuandoValorMudouDesdeAPrevia` (T8) e `naoDesfazSeMudou` (T10).
4. **Permissão de escrita mais larga que a da auditoria ou que a visão** (Squad Leader que é o responsável, SL de outro squad, issue fora da `Visibility`) → `proprioResponsavelNaoPode`, `slDeOutroSquadNaoPode`, `foraDaVisaoNaoVazaNemEscreve` (T6).
5. **Escrita pela conta de serviço vista pela auditoria como "não autorizada"** ou, ao contrário, uma alteração manual com o token da conta de serviço escondida → `escritaDoPainelAtribuidaAPessoaReal`, `contaDeServicoSemRegistroViraSinal` (T12).
6. **Lote acidental** (selecionar tudo, limite furado, confirmação genérica) → `loteAcimaDoLimite400`, `textoDeConfirmacaoComContagemErrada400` (T7, T8) e `confirmacaoExigeDigitarONumero` (T13).
7. **Registro de escrita alterável ou apagável** fora da retenção → `registroImutavel`, `retencaoEhOUnicoCaminhoDeApagar` (T5).

---

## Arquivos (mapa)

```
app/backend/src/main/kotlin/br/sidia/painel/
  config/Settings.kt                               # jiraWriteEnabled, credencial de escrita, regras de subida
  escrita/WriteGuard.kt, EscritaSettings.kt        # trava dupla e limites (YAML)
  escrita/FabricaDeEscritaJira.kt, JiraWriteClient.kt   # único lugar com HttpMethod.Put
  escrita/IntencaoDeAlteracao.kt                   # modelo e validação
  escrita/PermissaoDeEscrita.kt                    # Autorizacao (Plano 4) + Visibility
  escrita/EscritaStore.kt                          # previa_escrita e escrita_jira
  escrita/Previa.kt, ExecutorDeEscrita.kt, RitmoDeEscrita.kt, Reversao.kt
  escrita/EscritaRoutes.kt, EscritaDto.kt
  auditoria/EscritaPeloPainel.kt                   # atribuição da conta de serviço (Plano 4 estendido)
app/backend/src/main/resources/db/migration/V{n}__escrita_jira.sql
app/config/escrita.exemplo.yaml
app/frontend/src/screens/Escrita/{PreviaModal.tsx,Historico.tsx,api.ts,escrita.css,Escrita.test.tsx}
app/frontend/src/screens/Divergencia/Divergencia.tsx      # ações de correção
app/frontend/src/shared/TarefaDrawer.tsx                  # gaveta universal do Plano 10: ajuste de labels e vencimento
.claude/hooks/guard.sh, .claude/hooks/test-guard.sh
.clinerules/00-regras-do-projeto.md, CLAUDE.md, docs/guia-execucao-outra-ia.md
docs/escrita/decisoes.md, docs/escrita/ripd-escrita.md, docs/jira/checklist-primeira-escrita.md
docs/jira/matriz-compatibilidade.md                       # linhas novas da escrita
```

Testes em `app/backend/src/test/kotlin/br/sidia/painel/escrita/` (mesmos nomes de classe com `Test`), `auditoria/EscritaPeloPainelTest.kt` e `escrita/EscritaDocTest.kt`.

**Cenário ESC-1** (usado nas Tarefas 4 a 12; papéis em memória, como o AUD-1 do Plano 4):
- Pessoas e papéis:
  - `pl.alfa`: Reporter das issues do squad Alfa (USUARIO).
  - `ana` e `bruno`: USUARIO do Alfa.
  - `carla`: USUARIO do Beta.
  - `lider.alfa`: SQUAD_LEADER do Alfa.
  - `lider.beta`: SQUAD_LEADER do Beta.
  - `coord.um`: COORDENADOR.
  - `gerente.exemplo`: GERENTE.
  - `svc.painel`: a conta de serviço; não está em `papeis.yaml`.
- Issues, na instância `samsung`:
  - **TG-10**: Task, responsável `ana`, vencimento `2026-10-15`, labels `[]`.
  - **TG-11**: a Review de TG-10, responsável `bruno`, vencimento `2026-10-13` (divergente).
  - **TG-12**: Task, responsável `lider.alfa`, vencimento `2026-10-20`.
  - **TG-13**: Task, responsável `carla` (Beta), vencimento `2026-10-22`.
  - **TG-14**: Task sem vencimento, com a Review **TG-15** de vencimento `2026-10-18`.
- Data de "hoje" nos testes: `2026-10-27` (terça) em America/Manaus.

---

### Tarefa 1: Decisões do usuário (ponto de parada)

**Arquivos:** Criar `docs/escrita/decisoes.md` (só escolhas e datas, sem host, nome real nem segredo); Teste `escrita/EscritaDocTest.kt` (`decisoesTemCadaPergunta`, `decisoesSemHostNemNomeReal`).

**Interfaces:** Produz as decisões que as Tarefas 2 a 14 consomem: `seguir`, `credencial`, `campos`, `quemEscreve`, `limiteDoLote`, `politicaDeLabels`, `notificacoes`, `retencao`, `primeiraEscrita`.

- [ ] **Passo 1: Ponto de parada: perguntar ao usuário** as nove perguntas da seção "Pontos de parada", uma por assunto, opções na ordem dada, a primeira marcada "(Recomendada)", cada uma com o porquê e a consequência.
- [ ] **Passo 2: Se a resposta à pergunta 1 for "não fazer escrita"**, registrar no `HANDOFF.md` e no índice que a Fase 16 foi arquivada e **parar aqui**. Se for "depois da Fase 14", registrar e parar até a Fase 14 e o aceite do DPO estarem na `main`.
- [ ] **Passo 3: Se a credencial escolhida não for a conta de serviço**, parar e replanejar as Tarefas 3 e 12 (registrar `Ruling:`); este plano assume a conta de serviço.
- [ ] **Passo 4: Testes que falham:** `decisoesTemCadaPergunta` (uma seção por pergunta, com resposta e data); `decisoesSemHostNemNomeReal` (nenhuma URL fora de `.invalid`, nenhum e-mail, nenhum `customfield_`).
- [ ] **Passo 5: Escrever** `docs/escrita/decisoes.md`. **Passo 6: Rodar** `cd app/backend && ./gradlew test --tests '*EscritaDocTest*'` → PASSA. **Passo 7: Commit** `docs(escrita): decisões do usuário para a escrita no Jira`.

---

### Tarefa 2: Flag de escrita, trava dupla e regras de subida

**Arquivos:** Modificar `config/Settings.kt`, `app/.env.example` (só a chave, comentada, com `false`), `AppContext.kt`; Criar `escrita/WriteGuard.kt`, `escrita/EscritaSettings.kt`, `app/config/escrita.exemplo.yaml`; Teste `config/SettingsTest.kt` (casos novos), `escrita/WriteGuardTest.kt`, `escrita/EscritaSettingsTest.kt`, `jira/SomenteAFabricaCriaClientesJiraTest.kt` (caso novo).

**Interfaces:**
- Produz:
  ```kotlin
  // Settings ganha:
  val jiraWriteEnabled: Boolean = false                 // JIRA_WRITE_ENABLED
  val jiraWriteInstances: Map<String, JiraInstanceSettings> = emptyMap()   // JIRA_SAMSUNG_WRITE_TOKEN (+ usuário no Basic)
  // Regras de subida (IllegalStateException citando só a regra):
  //   "escrita-exige-leitura"  -> jiraWriteEnabled && !jiraLiveEnabled
  //   "escrita-sem-dev-mode"   -> jiraWriteEnabled && dashboardDevMode
  //   "escrita-sem-credencial" -> jiraWriteEnabled && instância sem token de escrita
  //   "token-de-escrita-igual-ao-de-leitura" -> mesmo valor nos dois (decisão 2 da T1)

  /** Trava de escrita: lê as duas flags a cada check(); final, como o LiveGuard. */
  class WriteGuard(private val leitura: () -> Boolean, private val escrita: () -> Boolean, private val devMode: () -> Boolean) {
      fun check()            // lança JiraWriteDisabledException("...JIRA_WRITE_ENABLED...") sem valores
      fun habilitada(): Boolean
  }
  class JiraWriteDisabledException(message: String) : RuntimeException(message)

  data class EscritaSettings(
      val versao: Int, val limiteDoLote: Int /*1..50; padrão 20*/, val validadePreviaMinutos: Int /*padrão 15*/,
      val intervaloMinimoMs: Long /*padrão 1000*/, val maxPorMinuto: Int /*padrão 30*/, val retryAfterMaxSegundos: Long /*padrão 60*/,
      val labelsPermitidas: List<String> /*exato ou prefixo com "*" final*/, val motivoMin: Int /*10*/, val motivoMax: Int /*500*/,
  ) { companion object { fun fromYaml(path: Path): EscritaSettings } }
  ```
- `toString` de `Settings` mostra `jiraWriteEnabled` e mascara o token de escrita (`***`).
- O teste estático que já proíbe `copy(jiraLiveEnabled = ...)` em `src/main` passa a proibir também `copy(jiraWriteEnabled = ...)` e `copy(dashboardDevMode = ...)` junto de escrita.

- [ ] **Passo 1: Testes que falham:**
  - `escritaDesligadaPorPadrao`: ambiente vazio → `false`.
  - `escritaExigeLeituraNaSubida`: escrita `true` com leitura `false` → falha `escrita-exige-leitura`.
  - `escritaComDevModeNaoSobe`: escrita `true` com dev `true` → falha `escrita-sem-dev-mode`.
  - `escritaSemCredencialNaoSobe`.
  - `tokenDeEscritaIgualAoDeLeituraNaoSobe`.
  - `mensagemSemValor`: nenhuma mensagem contém o token.
  - `writeGuardExigeAsDuasFlags`: tabela das 8 combinações de leitura × escrita × dev; só `true, true, false` passa.
  - `writeGuardLeAFlagACadaCheck`: a variável muda e o próximo `check()` lança.
  - `copyDaFlagDeEscritaProibidaEmSrcMain`.
  - `escritaSettingsEstrito`: chave desconhecida falha; limite fora de 1..50 falha; produção sem arquivo real falha; em dev usa o `.exemplo`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*SettingsTest*' --tests '*WriteGuardTest*' --tests '*EscritaSettingsTest*' --tests '*SomenteAFabricaCriaClientesJira*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA e suíte completa. **Passo 5: Commit** `feat(escrita): flag de escrita separada, trava dupla e subida fail-closed`.

---

### Tarefa 3: Escritor do Jira (único PUT do código)

**Arquivos:** Criar `escrita/FabricaDeEscritaJira.kt`, `escrita/JiraWriteClient.kt`; Teste `escrita/JiraWriteClientTest.kt`, `escrita/SomenteOEscritorUsaPutTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  /** Estado mínimo lido da issue: só o que a escrita compara. Nada de displayName, e-mail ou texto livre. */
  data class EstadoDaIssue(val instance: String, val key: String, val duedate: LocalDate?, val labels: Set<String>,
                           val reporter: String?, val assignee: String?, val issueType: String, val parentKey: String?, val updated: Instant)

  /** Corpo do PUT. Só estes dois campos existem: o tipo não deixa montar outro. */
  data class EdicaoJira(val duedate: LocalDate?, val labelsAdicionar: Set<String>, val labelsRemover: Set<String>) {
      fun paraJson(): String   // {"fields":{"duedate":"aaaa-mm-dd"}} e/ou {"update":{"labels":[{"add":".."},{"remove":".."}]}}
  }

  sealed interface ResultadoDoPut {
      data object Aplicado : ResultadoDoPut                              // 204 (ou 200)
      data class Recusado(val status: Int, val codigo: String) : ResultadoDoPut   // 400/403/404/409: o Jira não aplicou
      data class Incerto(val motivo: String) : ResultadoDoPut            // rede, timeout, 5xx: não se sabe se aplicou
      data class Adiado(val esperarSegundos: Long) : ResultadoDoPut      // 429 com Retry-After acima do teto
  }

  class JiraWriteClient internal constructor(config: JiraInstanceConfig, http: HttpClient, guard: WriteGuard, ritmo: RitmoDeEscrita) {
      suspend fun lerEstado(key: String): EstadoDaIssue    // GET /rest/api/2/issue/{key}?fields=duedate,labels,reporter,assignee,issuetype,parent,updated
      suspend fun editar(key: String, edicao: EdicaoJira): ResultadoDoPut   // PUT /rest/api/2/issue/{key}
  }
  class FabricaDeEscritaJira(settings: Settings, /* internal: http, guard, ritmo para testes */) : AutoCloseable {
      fun create(instanceId: String): JiraWriteClient     // com a escrita desligada, devolve cliente com motor que recusa tudo
  }
  ```
- Caminho HTTP **próprio**: não chama `requisitarJson` nem `executarComRetry` do `JiraClient`. `lerEstado` também passa pelo `WriteGuard`, porque é leitura feita **para escrever**: com a escrita desligada, 0 requisições.
- Chave validada por `JqlValues.issueKey` (Plano 4A); chave inválida → `IllegalArgumentException` antes de qualquer requisição.
- Nenhuma repetição, exceto `429` com `Retry-After` ≤ `retryAfterMaxSegundos` (uma vez; o guard é consultado de novo antes). 429 acima do teto → `Adiado`. Falha de rede, timeout ou 5xx → `Incerto`, **sem** reenviar.
- Erros sem corpo nem token (`padraoEscapado` reaproveitado para mascarar); `codigo` vem só do status (`SEM_PERMISSAO`, `NAO_ENCONTRADA`, `CAMPO_INVALIDO`).
- Fatos da API do Jira DC usados aqui, todos **assumidos, não validados** (a validação é o checklist da T14): PUT responde 204 sem corpo; `update.labels` com `add`/`remove`; `fields.duedate` no formato `aaaa-mm-dd`; `duedate` exige a permissão Schedule Issues; não há `If-Match`/ETag no PUT de issue (sem trava otimista: a janela entre reler e escrever é o risco residual, mitigado pela releitura imediata e pela conferência depois).

- [ ] **Passo 1: Testes que falham** (`MockEngine` com contador por método e por caminho; host `https://jira.exemplo.invalid`):
  - `quatroCombinacoesDeFlag`: para (leitura, escrita) em {(f,f),(f,t),(t,f),(t,t)}, `lerEstado` + `editar`. Só (t,t) gera requisições; nas outras três, o contador **de todos os métodos** fica em 0 e a exceção é `JiraWriteDisabledException`.
  - `guardAntesDeCadaTentativa`: 429 com `Retry-After: 1`; a flag é desligada durante a espera; a segunda tentativa não sai (1 PUT no total).
  - `retryAfterRespeitado`: o atraso injetado recebe 1 s.
  - `retryAfterAcimaDoTetoInterrompe`: 429 com `Retry-After: 3600` → `Adiado(3600)` e 1 PUT.
  - `falhaDeRedeViraIncertaSemRepetir`: `IOException` → `Incerto` com 1 PUT; 503 → `Incerto` com 1 PUT.
  - `respostaVazia204Aplicada`.
  - `corpoDoPutSoComCamposPermitidos`: o JSON enviado tem só `fields.duedate` e/ou `update.labels`.
  - `chaveInvalidaSemRequisicao`.
  - `erroSemTokenNemCorpo`: 403 com corpo que ecoa o token → mensagem sem ele.
  - `lerEstadoNaoGuardaDadoPessoal`: `displayName`/`emailAddress` da resposta não aparecem em `EstadoDaIssue`.
  - `SomenteOEscritorUsaPutTest.putSoNoEscritor`: varre `src/main`; `HttpMethod.Put`, `Patch` e `Delete` só em `escrita/JiraWriteClient.kt`; `JiraWriteClient(` só em `FabricaDeEscritaJira.kt`.
  - `SemEscritaNoJiraTest.nenhumCaminhoEscreveNoJira` (Plano 7) continua verde **sem mudança**.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*JiraWriteClientTest*' --tests '*SomenteOEscritorUsaPutTest*' --tests '*SemEscritaNoJiraTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA e suíte completa. **Passo 5: Commit** `feat(escrita): escritor do Jira com trava dupla e sem repetição cega`.

---

### Tarefa 4: Modelo de intenção de alteração

**Arquivos:** Criar `escrita/IntencaoDeAlteracao.kt`; Teste `escrita/IntencaoDeAlteracaoTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  sealed interface Alteracao {
      data class DefinirVencimento(val novo: LocalDate) : Alteracao          // remover vencimento não existe
      data class AdicionarLabel(val label: String) : Alteracao
      data class RemoverLabel(val label: String) : Alteracao
      data class TrocarLabel(val de: String, val para: String) : Alteracao   // vira remover + adicionar no mesmo PUT
  }
  enum class OrigemDaIntencao { DIVERGENCIA_TASK_REVIEW, GAVETA_DA_ISSUE, AUDITORIA, REVERSAO }
  data class IntencaoDeAlteracao(val instance: String, val issueKey: String, val alteracoes: List<Alteracao>, val origem: OrigemDaIntencao)
  data class PedidoDeAlteracao(val intencoes: List<IntencaoDeAlteracao>, val motivo: String)
  sealed interface ErroDeIntencao { val codigo: String }     // CAMPO_NAO_PERMITIDO, LABEL_NAO_PERMITIDA, LABEL_FORMATO, VENCIMENTO_FORA_DA_FAIXA,
                                                             // ALTERACOES_CONTRADITORIAS, ISSUE_REPETIDA, MOTIVO_CURTO, MOTIVO_LONGO, LOTE_VAZIO
  object ValidadorDeIntencao { fun validar(p: PedidoDeAlteracao, cfg: EscritaSettings, hoje: LocalDate): List<ErroDeIntencao> }
  ```
- Regras:
  - O JSON da API só desserializa os quatro tipos de `Alteracao`; um campo desconhecido (`assignee`, `status`, `summary`) é 400 `CAMPO_NAO_PERMITIDO`. O desserializador é estrito.
  - Label: formato `^[A-Za-z0-9_.\-]{1,255}$` (sem espaço; o Jira separa labels por espaço) e presença em `labelsPermitidas` (curinga só no fim, sem diferenciar maiúsculas, como `LabelsDeAtraso`).
  - Vencimento entre `hoje − 365 dias` e `hoje + 365 dias`, em Manaus.
  - Contraditório: adicionar e remover a mesma label, ou dois `DefinirVencimento` diferentes na mesma issue.
  - Uma issue aparece uma vez por pedido.
  - Motivo obrigatório (`motivoMin`..`motivoMax`), sem controle (vira `?`).

- [ ] **Passo 1: Testes que falham:** `campoForaDaListaRecusado` (JSON com `assignee` → `CAMPO_NAO_PERMITIDO`), `removerVencimentoNaoExiste`, `labelComEspacoRecusada`, `labelForaDaListaRecusada`, `curingaSoNoFim`, `adicionarERemoverAMesmaLabelContraditorio`, `issueRepetidaNoLote`, `motivoObrigatorio` (vazio, 9 e 501 caracteres), `vencimentoForaDaFaixa`, `trocaViraRemoverMaisAdicionar`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*IntencaoDeAlteracaoTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(escrita): modelo de intenção de alteração com campos permitidos`.

---

### Tarefa 5: Tabelas de prévia e de registro imutável

**Arquivos:** Criar `resources/db/migration/V{n}__escrita_jira.sql`, `escrita/EscritaStore.kt`; Modificar `db/Retention.kt` (retenção de `escrita_jira`); Teste `escrita/EscritaStoreTest.kt`, `db/RetentionTest.kt` (caso novo).

**Interfaces:**
- Migração:
  - `previa_escrita`, que é estado de trabalho e pode mudar de situação:
    - `id TEXT` (UUID em texto), `instance`, `user_id`, `criada_em`, `expira_em` (instantes em `INTEGER` ms UTC);
    - `situacao` (`PENDENTE`, `CONFIRMADA`, `EXPIRADA`, `CANCELADA`);
    - `hash_conteudo`, `chave_idempotencia` (`UNIQUE` (o SQLite aceita vários nulos));
    - `itens` (`TEXT` com JSON; chave, alterações, valores esperados de `duedate` e `labels`);
    - `motivo`.
  - `escrita_jira`, só de acréscimo:
    - `id INTEGER PRIMARY KEY AUTOINCREMENT`, `previa_id`, `lote_id`, `instance`, `issue_key`;
    - `campo` (`duedate` ou `labels`), `valor_antes`, `valor_pedido`, `valor_observado`;
    - `user_id`, `papel_na_data`, `motivo`;
    - `resultado` (`TENTATIVA`, `APLICADA`, `JA_APLICADA`, `CONFLITO`, `NEGADA`, `RECUSADA`, `INCERTA`, `NAO_EXECUTADA`, `REVERTIDA`);
    - `status_http`, `em INTEGER` (ms UTC), `reversao_de` (FK para `escrita_jira.id`), `conta_de_servico`.
  - Os gatilhos `escrita_jira_imutavel_update` e `escrita_jira_imutavel_delete` (`BEFORE UPDATE`/`BEFORE DELETE`, com `SELECT RAISE(ABORT, 'escrita_jira é só de acréscimo')`) só deixam passar quando a linha `retencao` da tabela de controle `controle_interno(chave TEXT PRIMARY KEY, valor TEXT)` vale `'on'` (cláusula `WHEN`). O SQLite não tem variável de sessão: só a retenção grava `'on'`, dentro da mesma transação IMMEDIATE, e volta a `'off'` antes de confirmar.
  - Índices: `(instance, issue_key, campo, em)`.
  - `CHECK (length(motivo) BETWEEN 10 AND 500)`.
- Produz:
  ```kotlin
  class EscritaStore(ds: DataSource) {
      fun criarPrevia(p: PreviaNova): UUID
      fun previa(id: UUID): PreviaGravada?
      fun marcarConfirmada(id: UUID, chave: String): Boolean        // false se já confirmada com outra chave; idempotente com a mesma
      fun registrar(linha: LinhaDeEscrita): Long                    // INSERT; nunca UPDATE
      fun ultimaAplicada(instance: String, key: String, campo: String): LinhaGravada?
      fun historico(instance: String, de: Instant, ate: Instant, keys: Set<String>?): List<LinhaGravada>   // por data e chave
      fun aplicadasPelaContaDeServico(instance: String, key: String, campo: String, perto: Instant, janela: Duration): List<LinhaGravada>
  }
  ```
- SQL só parametrizado; sempre `(instance, issue_key)`.

- [ ] **Passo 1: Testes que falham** (SQLite em arquivo temporário):
  - `registroImutavel`: `UPDATE` e `DELETE` em `escrita_jira` falham.
  - `retencaoEhOUnicoCaminhoDeApagar`: a retenção apaga as linhas mais antigas que o prazo; o mesmo `DELETE` sem a variável de sessão falha.
  - `chaveDeIdempotenciaUnica`.
  - `marcarConfirmadaIdempotente`: a mesma chave devolve `true` duas vezes; outra chave devolve `false`.
  - `instanciaSeparada`.
  - `motivoObrigatorioNoBanco`.
  - `historicoPorDataEChaveSemRanking`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*EscritaStoreTest*' --tests '*RetentionTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA e suíte completa. **Passo 5: Commit** `feat(escrita): tabelas de prévia e registro imutável de escrita`.

---

### Tarefa 6: Permissões por papel (regras da auditoria) e visão

**Arquivos:** Criar `escrita/PermissaoDeEscrita.kt`; Teste `escrita/PermissaoDeEscritaTest.kt`.

**Interfaces:**
- Consome: `Autorizacao` do Plano 4 (confirmar o nome e a assinatura). Ela aplica a spec mestra 7.1 com `PROPRIO_RESPONSAVEL`. **Não** usar `RoleDirectory.isReporterAuthorized` direto: ele aceita o Squad Leader que é o próprio responsável, e isso contradiz a decisão 3 do Plano 4.
- Produz:
  ```kotlin
  enum class MotivoNegacao { PROPRIO_RESPONSAVEL, SEM_PAPEL_NA_DATA, SQUAD_DIFERENTE, FORA_DA_VISAO, DEV_MODE, PAPEL_NAO_PERMITIDO_PELA_DECISAO }
  sealed interface Decisao { data class Permitida(val papel: String) : Decisao; data class Negada(val motivo: MotivoNegacao) : Decisao }
  class PermissaoDeEscrita(dir: RoleDirectory, autorizacao: Autorizacao, clock: Clock, zona: ZoneId, devMode: Boolean, quemEscreve: QuemEscreve /* decisão 4 da T1 */) {
      fun decidir(p: Principal, estado: EstadoDaIssue, visiveis: Set<String>): Decisao
  }
  ```
- A data é **hoje** em America/Manaus (relógio injetado).
- `FORA_DA_VISAO` (responsável fora de `Visibility.visibleUserIds`) é avaliado **antes** e responde como "issue não encontrada" na API, sem vazar chave nem nome.
- Com `QuemEscreve.SO_COORDENACAO`, PL e Squad Leader recebem `PAPEL_NAO_PERMITIDO_PELA_DECISAO`.

- [ ] **Passo 1: Testes que falham** (ESC-1):
  - `reporterPode`: `pl.alfa` em TG-10.
  - `coordenadorPode` e `gerentePode`.
  - `squadLeaderDoResponsavelPode`: `lider.alfa` em TG-10.
  - `slDeOutroSquadNaoPode`: `lider.beta` em TG-10 → `SQUAD_DIFERENTE`.
  - `proprioResponsavelNaoPode`: `lider.alfa` em TG-12, onde é o responsável, → `PROPRIO_RESPONSAVEL`; `ana` em TG-10 → idem.
  - `usuarioComumNaoPode`: `bruno` em TG-10.
  - `vigenciaDeHojeEmManaus`: papel que termina em `2026-10-26`; "hoje" às 23:30 de Manaus de `2026-10-26` é `2026-10-27` em UTC e o papel ainda vale; às 00:30 de Manaus de `2026-10-27` já não vale.
  - `foraDaVisaoNaoVazaNemEscreve`.
  - `devModeNaoEscreve`: principal `dev` → `DEV_MODE`.
  - `decisaoSoCoordenacao`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*PermissaoDeEscritaTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(escrita): permissão de escrita pelas regras da auditoria e pela visão`.

---

### Tarefa 7: Prévia (dry-run) com limite de lote, sinais previstos e CSV

**Arquivos:** Criar `escrita/Previa.kt`; Teste `escrita/PreviaTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  enum class SituacaoDoItem { VAI_ALTERAR, SEM_MUDANCA, NEGADA, INVALIDA }
  data class ItemDePrevia(val issueKey: String, val campo: String, val antes: String?, val depois: String?, val situacao: SituacaoDoItem,
                          val motivoNegacao: String?, val sinaisPrevistos: List<String> /* CodigoSinal do Plano 4 */)
  data class ResultadoDaPrevia(val id: UUID, val expiraEm: Instant, val itens: List<ItemDePrevia>, val aAlterar: Int,
                               val textoDeConfirmacao: String /* "CONFIRMAR <aAlterar> ALTERACOES" */, val hash: String)
  class ServicoDePrevia(fabrica: FabricaDeEscritaJira, permissao: PermissaoDeEscrita, store: EscritaStore, cfg: EscritaSettings, labels: LabelsDeAtraso, clock: Clock) {
      suspend fun gerar(p: Principal, pedido: PedidoDeAlteracao, visiveis: Set<String>): ResultadoDaPrevia
  }
  object CsvDaPrevia { fun gerar(r: ResultadoDaPrevia): String }   // reusa a neutralização de fórmula de shared/csv (backend: mesma regra)
  ```
- A prévia **lê** o estado atual de cada issue pelo `JiraWriteClient.lerEstado` (sob o `WriteGuard`) e **nunca** chama `editar`.
- Mais issues do que `limiteDoLote` → 400 `LOTE_ACIMA_DO_LIMITE`, sem nenhuma leitura.
- Os valores esperados (antes) ficam gravados na prévia para comparação na execução.
- Sinais previstos (aviso, não bloqueio):
  - label que justifica posta depois do vencimento → `LABEL_APOS_VENCIMENTO`;
  - depois da task fechada → `LABEL_APOS_FECHAMENTO_*`;
  - vencimento alterado perto do vencimento → aviso de passa-bola.
- Correção Task × Review: a origem `DIVERGENCIA_TASK_REVIEW` define o vencimento da Review igual ao da Task. **Se a Task não tem vencimento, o par fica fora** (`INVALIDA`, código `TASK_SEM_VENCIMENTO`). Copiar "nenhum vencimento" equivaleria a remover o vencimento da Review, que gera `VENCIMENTO_REMOVIDO`.
- `hash` = SHA-256 do conteúdo canônico (itens, valores esperados, motivo, usuário).

- [ ] **Passo 1: Testes que falham** (`MockEngine`, ESC-1):
  - `previaNaoFazPut`: 0 PUT; só GETs.
  - `previaMostraAntesEDepois`: TG-11 `2026-10-13` → `2026-10-15`.
  - `itemSemMudancaMarcado`: label já presente → `SEM_MUDANCA`.
  - `loteAcimaDoLimite400`: 21 issues com limite 20 → 400 e 0 requisições.
  - `taskSemVencimentoForaDaCorrecao`: TG-14/TG-15 → `TASK_SEM_VENCIMENTO`.
  - `sinalPrevistoLabelAposVencimento`.
  - `negadaNaoEntraNaContagem`: o texto de confirmação conta só `VAI_ALTERAR`.
  - `previaExpira`: o relógio avança 16 min → situação `EXPIRADA` na leitura.
  - `issueForaDaVisaoNaoVaza`.
  - `csvNeutralizaFormulas`: a label `=HYPERLINK` vira texto.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*PreviaTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(escrita): prévia sem escrita, com limite de lote, sinais previstos e CSV`.

---

### Tarefa 8: Execução com confirmação explícita, idempotência e conflito

**Arquivos:** Criar `escrita/ExecutorDeEscrita.kt`; Teste `escrita/ExecutorDeEscritaTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  data class Confirmacao(val previaId: UUID, val hash: String, val texto: String, val chaveIdempotencia: String /* UUID gerado pela tela */)
  data class ResultadoDaExecucao(val loteId: UUID, val itens: List<ItemExecutado>, val interrompido: String? /* ADIADO, INCERTA, FLAG_DESLIGADA, AUDITORIA_INDISPONIVEL */)
  data class ItemExecutado(val issueKey: String, val campo: String, val resultado: String, val escritaId: Long?)
  class ExecutorDeEscrita(fabrica: FabricaDeEscritaJira, permissao: PermissaoDeEscrita, store: EscritaStore, ritmo: RitmoDeEscrita, clock: Clock) {
      suspend fun confirmar(p: Principal, c: Confirmacao, visiveis: Set<String>): ResultadoDaExecucao
  }
  ```
- Ordem, por issue:
  1. Conferir a prévia: do mesmo usuário (senão 403), `PENDENTE` e não expirada (senão 409 `PREVIA_EXPIRADA`), `hash` igual e `texto` exatamente igual a `textoDeConfirmacao` (senão 400 `CONFIRMACAO_INVALIDA`).
  2. `marcarConfirmada` com a chave. A mesma chave devolve o **mesmo** resultado já gravado, sem nenhuma requisição.
  3. Reler a issue; decidir de novo a permissão, com o estado e o papel de agora.
  4. Comparar com o valor esperado da prévia. Se for diferente, `CONFLITO` gravado e 0 PUT nessa issue. Se o valor atual já é o pedido, `JA_APLICADA` e 0 PUT.
  5. Gravar `TENTATIVA`. Se a gravação falhar, interromper o lote (`AUDITORIA_INDISPONIVEL`) sem PUT.
  6. `editar` e conferir com `lerEstado`:
     - `Aplicado` com o valor conferido → `APLICADA`;
     - `Recusado` → `RECUSADA`;
     - `Incerto` → reler; se o valor for o pedido, `APLICADA`; senão, `INCERTA`, e o lote para;
     - `Adiado` → o lote para; o resto fica `NAO_EXECUTADA`.
  7. Gravar o resultado com antes, pedido, observado, autor, papel e motivo.
- `JiraWriteDisabledException` no meio do lote → o resto fica `NAO_EXECUTADA` (`FLAG_DESLIGADA`).
- Depois de cada `APLICADA`, pedir a ressincronização da chave ao `Syncer` (confirmar o nome no 4A), sem esperar.

- [ ] **Passo 1: Testes que falham** (`MockEngine`, SQLite em arquivo temporário):
  - `semConfirmacaoNaoExecuta`.
  - `textoDeConfirmacaoComContagemErrada400`: "CONFIRMAR 3 ALTERACOES" para 2 → 400 e 0 PUT.
  - `hashDiferente400`.
  - `previaDeOutroUsuario403`.
  - `previaExpirada409`.
  - `segundaConfirmacaoNaoRepete`: mesma chave duas vezes → 1 PUT e o mesmo `loteId`.
  - `conflitoQuandoValorMudouDesdeAPrevia`: TG-11 mudou para `2026-10-14` depois da prévia → `CONFLITO`, 0 PUT em TG-11; TG-10 do mesmo lote segue.
  - `jaAplicadaSemPut`.
  - `permissaoReavaliadaNaExecucao`: o responsável muda para `lider.alfa` entre a prévia e a confirmação; quem confirma é `lider.alfa` → `NEGADA`.
  - `cadaItemGravaAntesEDepois`.
  - `falhaAoGravarAuditoriaNaoEscreve`: o banco cai antes da `TENTATIVA` → 0 PUT.
  - `incertaReleEDecide`: 503 e a releitura mostra o valor pedido → `APLICADA`; mostra o antigo → `INCERTA` e o lote para.
  - `flagDesligadaNoMeioParaORestante`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*ExecutorDeEscritaTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA e suíte completa. **Passo 5: Commit** `feat(escrita): execução confirmada, idempotente e com detecção de conflito`.

---

### Tarefa 9: Ritmo de escrita, limites de taxa e um lote por vez

**Arquivos:** Criar `escrita/RitmoDeEscrita.kt`; Teste `escrita/RitmoDeEscritaTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  class RitmoDeEscrita(cfg: EscritaSettings, clock: Clock, dormir: suspend (Duration) -> Unit) {
      suspend fun aguardarVez()                    // intervalo mínimo entre PUTs e teto por minuto, por instância
      fun <T> umLotePorVez(instance: String, bloco: suspend () -> T): T   // segunda chamada simultânea → LoteEmAndamentoException (409 LOTE_EM_ANDAMENTO)
  }
  ```
- Escrita sempre sequencial (sem paralelismo). Se o 4A tiver o `OrcamentoDeRequisicoes`, as leituras da prévia e da execução entram no orçamento da instância; senão, `Ruling:`.
- O `Retry-After` do 429 é respeitado até o teto e vale para a instância inteira (as leituras também esperam).

- [ ] **Passo 1: Testes que falham:** `intervaloMinimoEntrePuts` (3 PUTs → esperas de 1 s, com relógio falso), `tetoPorMinuto` (o 31º espera a janela), `doisLotesSimultaneos409`, `retryAfterValeParaAInstancia`, `semParalelismo` (contador de PUTs simultâneos nunca passa de 1).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*RitmoDeEscritaTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(escrita): ritmo de escrita, limite por minuto e um lote por vez`.

---

### Tarefa 10: Reversão da última alteração

**Arquivos:** Criar `escrita/Reversao.kt`; Teste `escrita/ReversaoTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  class Reversao(store: EscritaStore, previa: ServicoDePrevia) {
      /** Gera uma PRÉVIA de reversão (origem REVERSAO); a execução segue a Tarefa 8 (confirmação, conflito, idempotência). */
      suspend fun preverDesfazer(p: Principal, escritaId: Long, motivo: String, visiveis: Set<String>): ResultadoDaPrevia
  }
  ```
- Só a **última** linha `APLICADA` daquele `(instance, issue_key, campo)` pode ser desfeita. O valor esperado da prévia é o `valor_observado` gravado. Se o valor atual no Jira for diferente, `CONFLITO` e nada é escrito.
- Reverter `duedate` volta à data anterior. Se a anterior era vazia, a reversão é **recusada** (`REVERSAO_REMOVERIA_VENCIMENTO`), porque remover o vencimento está fora da primeira versão. Labels: desfaz exatamente os `add`/`remove` feitos.
- Pode desfazer: quem fez a alteração ou coordenação/gerência, sempre com a permissão da T6 avaliada de novo. Motivo obrigatório.
- A execução grava linhas novas com `reversao_de` e resultado `REVERTIDA`; a linha original nunca muda.

- [ ] **Passo 1: Testes que falham:** `desfazQuandoValorAtualEhOGravado`, `naoDesfazSeMudou` (CONFLITO e 0 PUT), `soAUltimaAlteracaoDoCampo`, `reversaoGravaNovaLinha` (a original fica intacta), `reversaoRecusaRemoverVencimento`, `reversaoTambemExigeFlags` (0 requisições com a escrita desligada), `outraPessoaSemPapelNaoDesfaz`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*ReversaoTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(escrita): desfazer a última alteração enquanto o valor atual for o gravado`.

---

### Tarefa 11: API de escrita com CSRF, registro de acesso e prova das flags

**Arquivos:** Criar `escrita/EscritaRoutes.kt`, `escrita/EscritaDto.kt`; Modificar `Application.kt`, inventário de rotas do Plano 8; Teste `escrita/EscritaRoutesTest.kt`, `InventarioDeRotasTest` (casos novos).

**Interfaces:**
- Rotas (todas exigem sessão; nenhuma é pública):
  ```
  GET  /api/escrita/estado                      -> { habilitada: Boolean, limiteDoLote, labelsPermitidas, versao }   (sem chamar o Jira)
  POST /api/escrita/previa                      -> ResultadoDaPrevia   (corpo PedidoDeAlteracao)
  GET  /api/escrita/previa/{id}                 -> ResultadoDaPrevia   (só do próprio usuário)
  GET  /api/escrita/previa/{id}/csv             -> text/csv
  POST /api/escrita/previa/{id}/confirmar       -> ResultadoDaExecucao (corpo Confirmacao)
  GET  /api/escrita/historico?de=&ate=&key=     -> linhas por data e chave (coordenação e gerência veem todas; os demais só as próprias e as visíveis)
  POST /api/escrita/{escritaId}/desfazer        -> ResultadoDaPrevia   (corpo { motivo })
  ```
- Com `WriteGuard.habilitada() == false`:
  - `/estado` responde `habilitada=false`;
  - as demais rotas respondem 404 `ErroDto("ESCRITA_DESLIGADA")`, gravam `access_log` e fazem **0 requisições ao Jira de qualquer método**, inclusive os GETs da prévia.
- Toda rota mutante passa pela `ProtecaoCsrf` do Plano 9, sem rota isenta. Exige `Content-Type: application/json`; outro tipo → 415.
- `access_log` em 200, 400, 403, 404, 409 e 415. Os filtros guardam `rota`, `previaId`, quantidade de itens e `resultado`, **nunca** o texto do motivo nem labels livres. Falha ao gravar → 503 sem executar.
- `/api/meta` não muda (`metaNaoMudou` continua verde).

- [ ] **Passo 1: Testes que falham** (`testApplication`, `MockEngine` compartilhado com contador por método para leitor e escritor):
  - `flagsDesligadasZeroRequisicoesNaApi`: para (leitura, escrita) em {(f,f),(f,t)*,(t,f)}, percorre todas as rotas de escrita com corpo válido. Resultado: 0 PUT, 0 POST e 0 GET no motor do escritor, e 0 requisições do leitor disparadas por essas rotas. *(f,t) é montado direto no teste, já que a subida real recusa essa combinação (T2).*
  - `fluxoCompletoComAsDuasFlags`: prévia → confirmação → 1 PUT por issue alterada.
  - `todaRotaMutanteExigeCsrf`: sem `X-Painel-CSRF` → 403 e 0 requisições; reusa `todoMetodoMutanteExigeCsrf`.
  - `getNuncaAltera`.
  - `conteudoNaoJson415`.
  - `accessLogSemMotivo`: o motivo "Ajuste combinado com ana" não aparece em `access_log.filters`.
  - `historicoSemRanking`: ordenado por data e chave; nenhum total por pessoa.
  - `usuarioComumNaoVeHistoricoDosOutros`.
  - `falhaNoAccessLog503SemExecutar`.
  - `inventarioConhecido`: as rotas novas estão no inventário, com papel e registro.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*EscritaRoutesTest*' --tests '*InventarioDeRotasTest*' --tests '*ProtecaoCsrfTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA e suíte completa. **Passo 5: Commit** `feat(escrita): API de prévia, confirmação, histórico e reversão com CSRF`.

---

### Tarefa 12: Auditoria reconhece a escrita feita pelo painel

**Arquivos:** Criar `auditoria/EscritaPeloPainel.kt`; Modificar `auditoria/AuditCalculator.kt`, `auditoria/Sinais.kt` (código novo), `app/config/auditoria.exemplo.yaml` (regra do código novo), documento de diferenças da auditoria; Teste `auditoria/EscritaPeloPainelTest.kt`, `auditoria/AuditCalculatorTest.kt` (casos novos).

**Interfaces:**
- Consome: `escrita_jira` (T5), o username da conta de serviço (`JIRA_SAMSUNG_WRITE_USER` ou o `name` configurado na decisão 2, sempre normalizado).
- Produz:
  ```kotlin
  /** Troca o autor de um ChangeEventRecord feito pela conta de serviço pelo usuário do painel que pediu a escrita. */
  class EscritaPeloPainel(store: EscritaStore, contaDeServico: String, janela: Duration /* padrão 5 min */) {
      fun autorEfetivo(ev: ChangeEventRecord): AutorEfetivo   // Pessoa(userId, escritaId) | ContaDeServicoSemRegistro | Original
  }
  // CodigoSinal ganha: ALTERACAO_PELA_CONTA_DE_SERVICO_SEM_REGISTRO (auditoria, gravidade 3)
  ```
- Casamento: mesma `(instance, issue_key)`, mesmo campo, valor "para" igual ao `valor_observado` e `em` dentro da janela, com uma linha `APLICADA` ou `REVERTIDA`. A autorização do Plano 4 é avaliada para a **pessoa real**, na data da alteração. Ela é a mesma regra da T6, então uma escrita legítima não vira sinal.
- Um evento da conta de serviço **sem** linha correspondente (alguém usou o token fora do painel) vira o sinal novo, que não é escondido nem atribuído a ninguém.
- A gaveta de histórico da auditoria mostra "pelo painel, a pedido de `<pessoa>`" com o motivo, só para coordenação e gerência.

- [ ] **Passo 1: Testes que falham:**
  - `escritaDoPainelAtribuidaAPessoaReal`: evento de `svc.painel` casado com a linha de `coord.um` → autorizado; nenhum `VENCIMENTO_ALTERADO_NAO_AUTORIZADO`.
  - `contaDeServicoSemRegistroViraSinal`.
  - `foraDaJanelaNaoCasa`.
  - `valorDiferenteNaoCasa`.
  - `instanciaSeparada`.
  - `todoCodigoTemRegra` (Plano 4) continua verde com o código novo.
  - `todoCodigoDetectadoNoCenarioCompleto` (Plano 4) ganha o caso novo.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*EscritaPeloPainelTest*' --tests '*AuditCalculatorTest*' --tests '*RegrasDeAuditoriaTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA e suíte completa. **Passo 5: Commit** `feat(auditoria): reconhecer alterações feitas pelo painel com a conta de serviço`.

---

### Tarefa 13: Telas: ações, modal de prévia, confirmação, CSV e histórico

**Arquivos:** Criar `app/frontend/src/screens/Escrita/{PreviaModal.tsx,Historico.tsx,api.ts,escrita.css,Escrita.test.tsx}`; Modificar `screens/Divergencia/Divergencia.tsx` (ações), `shared/TarefaDrawer.tsx` (gaveta universal do Plano 10: ajuste de vencimento e labels, conforme a decisão 4), menu (item "Alterações no Jira"), arquivos de i18n pt-BR e inglês.

**Interfaces:**
- `api.ts`: `estado()`, `previa(pedido)`, `confirmar(id, confirmacao)`, `historico(filtros)`, `desfazer(id, motivo)`. As mutações usam `enviarJson` (Plano 9); a chave de idempotência é gerada uma vez por abertura do modal (`crypto.randomUUID()`) e reaproveitada em reenvios.
- Com `estado.habilitada=false`, **nenhum** botão de escrita é renderizado (nem desabilitado), e o histórico mostra só a nota "Escrita no Jira desligada pela configuração".
- Divergência:
  - "Corrigir vencimento da Review para o da Task" por linha e "Corrigir selecionadas";
  - a seleção é limitada a `limiteDoLote`, e não existe "selecionar todos" acima do limite;
  - os pares cuja Task não tem vencimento não têm a ação, e uma nota explica por quê.
- Gaveta da issue: "Ajustar labels" (só as permitidas, em lista) e "Corrigir vencimento" (data), para quem a decisão 4 permite.
- Modal de prévia, que bloqueia a tela, com foco preso e `Esc` fechando sem executar:
  - uma tabela com chave, campo, antes, depois, situação e sinais previstos, com símbolos e texto (`✓ vai alterar`, `● sem mudança`, `▲ negada`, `▲ inválida`) e um aviso destacado quando há sinal previsto;
  - o motivo é obrigatório, com contador e o aviso "não escreva dados pessoais";
  - o campo de confirmação exige digitar exatamente `CONFIRMAR <n> ALTERACOES`, e o botão fica desabilitado até isso;
  - o botão "Baixar CSV da prévia".
- Resultado: lista por item (`✓ aplicada`, `● já aplicada`, `▲ conflito`, `▲ recusada`, `● incerta`, `● não executada`) e o aviso de interrupção, se houver.
- Histórico: por data e chave, com "Desfazer" só na última aplicada do campo, que abre o mesmo modal.

- [ ] **Passo 1: Testes que falham** (Vitest + Testing Library, API simulada):
  - `semBotoesComEscritaDesligada`.
  - `confirmacaoExigeDigitarONumero`.
  - `escFechaSemExecutar`.
  - `selecaoLimitadaAoLote`.
  - `paresSemVencimentoDaTaskSemAcao`.
  - `simbolosAlemDaCor`.
  - `avisoDeSinalPrevisto`.
  - `mesmaChaveNoReenvio`: duplo clique manda a mesma chave.
  - `csvBaixado`.
  - `i18nCompleto`: nenhuma chave faltando em inglês.
  - `semRankingDePessoas`.
- [ ] **Passo 2: Rodar** `cd app/frontend && pnpm vitest run src/screens/Escrita src/screens/Divergencia` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA; `pnpm vitest run && pnpm build`. **Passo 5: Commit** `feat(escrita): telas de prévia, confirmação, CSV e histórico de alterações`.

---

### Tarefa 14: Hook, regras das IAs, LGPD e checklist da primeira escrita

**Arquivos:**
- Modificar:
  - `.claude/hooks/guard.sh`, `.claude/hooks/test-guard.sh`;
  - `CLAUDE.md` (regra 1), `.clinerules/00-regras-do-projeto.md`, `docs/guia-execucao-outra-ia.md` (seções 2 e 7);
  - spec mestra (seção 2, "Não objetivos": "escrita desligada" passa a apontar para esta fase);
  - `docs/superpowers/cobertura-do-projeto.md` (linha de "escrita no Jira desligada");
  - `docs/jira/matriz-compatibilidade.md` (linhas novas).
- Criar: `docs/jira/checklist-primeira-escrita.md`, `docs/escrita/ripd-escrita.md`.
- Teste: `escrita/EscritaDocTest.kt` (casos novos).

**Conteúdo:**
- **`guard.sh`:**
  - A regra 1 continua barrando todo `JIRA_*` diferente de `JIRA_LIVE_ENABLED` em comandos, o que inclui `JIRA_WRITE_ENABLED` e `JIRA_SAMSUNG_WRITE_TOKEN`. Isso fica como está.
  - Acrescentar um bloqueio explícito com mensagem própria para `JIRA_WRITE_ENABLED` com valor verdadeiro (`1`, `true`, `yes`, `on`, com aspas ou espaços), em comando (`sh` e PowerShell), `export`, `env` e atribuição `$env:`.
  - O modo `arquivos` passa a barrar a escrita de `JIRA_WRITE_ENABLED=true` em qualquer arquivo (`.env*`, manifestos).
  - As mensagens antigas que ainda falam em "autorização expressa por pesquisa" passam a citar a regra da flag única (a regra nova de 25/10).
  - O bloqueio a ferramentas MCP da Atlassian continua total, leitura e escrita.
- **`test-guard.sh`:** casos para `JIRA_WRITE_ENABLED=true ./gradlew run`, `export JIRA_WRITE_ENABLED=1`, a forma PowerShell `$env:` com a chave verdadeira e a gravação de `.env` com a chave verdadeira, todos bloqueados. A chave com `false` em `app/.env.example` é permitida.
- **`CLAUDE.md` e `.clinerules`:** acrescentar à regra 1 que a escrita no Jira exige, além de `JIRA_LIVE_ENABLED=true`, a flag `JIRA_WRITE_ENABLED=true`, separada e `false` por padrão, que só o usuário liga. Nenhuma IA escreve no Jira por ferramenta (MCP, `curl`, script), nem com as duas flags ligadas: só o painel escreve, pelo fluxo prévia → confirmação. O guia ganha a linha "Escrita" na tabela da seção 2 e o ponto de parada na seção 7.
- **`checklist-primeira-escrita.md`** (só o usuário executa, com as duas flags ligadas por ele, numa issue de teste escolhida por ele; decisão 9):
  1. Conferir as permissões da conta de serviço (Browse Projects, Edit Issues, Schedule Issues).
  2. Prévia de uma label.
  3. Confirmar e conferir o 204 e a label no Jira.
  4. Desfazer.
  5. Prévia e confirmação de um vencimento.
  6. Conferir o autor no histórico do Jira e a atribuição na auditoria (T12).
  7. Conferir as notificações.
  8. Registrar cada resultado na matriz.
  - Qualquer diferença do esperado: desligar `JIRA_WRITE_ENABLED` e parar.
- **`matriz-compatibilidade.md`:** linhas `PUT /rest/api/2/issue/{key}` (204), `update.labels` com `add`/`remove`, `fields.duedate`, permissão Schedule Issues, `notifyUsers` e a ausência de trava otimista, todas marcadas "Assumido, não validado", com o passo do checklist que valida cada uma.
- **`ripd-escrita.md`** (para o DPO):
  - dados tratados: username, papel, motivo, antes e depois;
  - finalidade e base legal a confirmar;
  - retenção (decisão 8);
  - quem acessa;
  - minimização (sem displayName nem e-mail);
  - e a regra: **`JIRA_WRITE_ENABLED` só liga depois do aceite registrado com data**.

- [ ] **Passo 1: Testes que falham:**
  - `sh .claude/hooks/test-guard.sh` com os casos novos → FALHA.
  - `EscritaDocTest.checklistDaPrimeiraEscritaCompleto`: oito passos e "desligar `JIRA_WRITE_ENABLED` e parar".
  - `matrizTemAsLinhasDaEscrita`.
  - `ripdExigeAceiteAntesDaFlag`.
  - `regrasDasIasCitamAsDuasFlags`: `CLAUDE.md`, `.clinerules` e o guia citam `JIRA_WRITE_ENABLED` e "só o usuário".
  - `documentosSemHostReal`.
- [ ] **Passo 2: Rodar** `sh .claude/hooks/test-guard.sh` e `./gradlew test --tests '*EscritaDocTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA; suítes completas das duas camadas; `test-guard.sh` verde. **Passo 5: Commit** `docs(escrita): hook, regras das IAs, RIPD e checklist da primeira escrita`.
- [ ] **Passo 6: Fim do plano:** revisão do conjunto (`git diff main..HEAD`, checklist da seção 5 do guia, com foco no Review Focus acima), varredura de dados pessoais, `HANDOFF.md` e índice, push, PR. **Exceção ao fluxo de entrega: este plano só é mesclado com ordem expressa do usuário** (escreve no Jira). **Não ligar `JIRA_WRITE_ENABLED`** em nenhum ambiente: só o usuário, depois do aceite do DPO, seguindo o checklist da primeira escrita.

---

## Autoavaliação

**Cobertura do pedido e da spec:**

| Requisito | Tarefas |
|---|---|
| Pedido 25/10: corrigir due dates; adicionar, remover e ajustar labels; individual ou em lote | T4, T7, T8, T13 |
| Flag dupla; qualquer uma `false` = nenhuma escrita (0 requisições) | T2, T3 (`quatroCombinacoesDeFlag`), T11 (`flagsDesligadasZeroRequisicoesNaApi`) |
| Prévia → confirmação explícita → execução | T7, T8, T13 |
| Lote com limite e revisão; proteção contra lote acidental | T7 (`loteAcimaDoLimite400`), T8, T13 (`selecaoLimitadaAoLote`, `confirmacaoExigeDigitarONumero`) |
| Idempotência | T5, T8 (`segundaConfirmacaoNaoRepete`), T13 (`mesmaChaveNoReenvio`) |
| Conflito (valor esperado) | T8, T10 |
| Registro imutável com quem, quando, antes, depois e motivo | T5, T8 |
| Permissões das regras da auditoria (spec mestra 7.1, `PROPRIO_RESPONSAVEL`) | T6, T12 |
| Credencial de escrita (ponto de parada) | T1, T2, T3 |
| Limites de taxa e `Retry-After` | T3, T9 |
| Reversão | T10 |
| CSRF e POST seguro | T11 |
| Tela a partir das divergências; CSV de prévia | T7, T13 |
| Hook e regras do Claude e do Cline | T14 |
| LGPD/DPO | T1, T14 |
| Garantias de somente leitura anteriores (Planos 6 e 7) | T3 (o escritor fora de `jira/`; `SemEscritaNoJiraTest` inalterado) |

**Consistência de tipos:**
- `EstadoDaIssue` (T3) alimenta `PermissaoDeEscrita` (T6), `ServicoDePrevia` (T7) e `ExecutorDeEscrita` (T8).
- `ResultadoDaPrevia` serve à T8, à T10 (reversão como prévia) e à T11.
- `escrita_jira` (T5) é lida pela T10 e pela T12.
- `RitmoDeEscrita` (T9) é injetado no `JiraWriteClient` (T3); na T3, os testes usam um ritmo sem espera.

**Pendências deliberadas:**
- Todas as decisões da T1.
- A validação dos fatos da API na primeira escrita (T14), que depende de o usuário ligar as flags.
- Responsável e status ficam fora, salvo decisão 3.

## Decisões (25/10, valor padrão recomendado, reversível, salvo onde diz [usuário])

1. **[usuário]** A escrita exige `JIRA_LIVE_ENABLED=true` **e** `JIRA_WRITE_ENABLED=true`, separadas; `JIRA_WRITE_ENABLED` é `false` por padrão e só o usuário a liga. O nome com prefixo `JIRA_` é exceção à convenção do Plano 13.
2. **[usuário]** O painel continua somente leitura até este plano ser aprovado e executado. Plano marcado "FUTURO".
3. Escritor em pacote próprio (`escrita/`), fora de `jira/`, para manter intactas as garantias dos Planos 6 e 7. Alternativa descartada: pôr o PUT no `JiraClient` e afrouxar `SemEscritaNoJiraTest`.
4. Sem repetição automática da escrita, salvo 429 dentro do teto; falha incerta é relida, nunca reenviada.
5. Remover vencimento fica fora (equivale ao sinal `VENCIMENTO_REMOVIDO`); a correção Task × Review ignora a Task sem vencimento; a reversão que removeria o vencimento é recusada.
6. Escrita proibida com `DASHBOARD_DEV_MODE=true` (a subida falha).
7. Prévia válida por 15 minutos; confirmação digitando `CONFIRMAR <n> ALTERACOES`; chave de idempotência por abertura do modal.
8. Registro só de acréscimo com gatilho; a retenção é o único caminho de apagar.
9. Ritmo: 1 PUT por segundo, até 30 por minuto, um lote por vez por instância, sem paralelismo.
10. A auditoria atribui à pessoa real a alteração da conta de serviço casada com `escrita_jira` (janela de 5 min); sem casamento, sinal novo `ALTERACAO_PELA_CONTA_DE_SERVICO_SEM_REGISTRO`.

Decisões que **dependem do usuário** (pontos de parada da T1): seguir ou não e quando; credencial; campos; quem escreve e por onde; limite do lote; labels permitidas; notificações; LGPD e retenção; primeira escrita real. Ligar `JIRA_WRITE_ENABLED` em qualquer ambiente é sempre do usuário.
