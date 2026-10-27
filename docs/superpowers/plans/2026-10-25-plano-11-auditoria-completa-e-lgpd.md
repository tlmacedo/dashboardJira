# Fase 11: Auditoria completa e LGPD — Plano de implementação

> **Para executores:** USE superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Os passos usam caixas `- [ ]`.

**Objetivo (Goal):** Fechar a seção 7.2 da spec mestra (os sinais "estimativa reduzida" e "worklog retroativo", e o `ESTIMATIVA_AUMENTADA` da Integridade, que dependem do mesmo dado) e preparar o painel para o uso real sob a LGPD: uma **trava em código** que mantém Auditoria e Integridade desligadas em produção até o aceite do DPO (flag própria, fail-closed), o inventário dos dados tratados verificado contra o esquema do banco, a retenção final configurável por categoria, o atendimento ao titular (acesso aos próprios dados) e o relatório para o DPO com o texto de comunicação à equipe.

**Arquitetura (Architecture):** A trava é um objeto puro (`TravaDpo`) derivado **só do `Settings`** (aceite do DPO, modo dev e flag do Jira), nunca do conteúdo do banco; as rotas sensíveis a consultam dentro de `executarAuditado`, que responde 403 com `ErroDto("AGUARDANDO_ACEITE_DPO")` e grava `access_log`. Os sinais novos são detectores puros no mesmo molde do Plano 4 (`Detector` sobre `AuditTimeline`), alimentados por dois dados novos: o valor de `timeoriginalestimate`/`timeestimate` no `change_event` e a data de criação do worklog. A retenção passa a ter um prazo por categoria. O titular consulta os próprios dados por uma rota que só aceita o principal da chamada. Inventário, relatório e comunicação são documentos versionados com testes que os ligam ao esquema e ao código.

**Stack (Tech Stack):** Kotlin 2.2.20 + Ktor 3.3.1, kaml, SQLite embutido + Flyway (testes com SQLite real em arquivo temporário), Ktor `MockEngine`; React 19 + TypeScript (Vite, Vitest, Testing Library, TanStack Query).

**Spec da fase (ler primeiro):** `docs/superpowers/specs/2026-10-25-spec-fase-11-auditoria-completa-e-lgpd.md`. **Spec mestra:** seções 3 ("Dados pessoais sensíveis"), 4.6 (retenção), 7.1, 7.2, 7.3, 10 ("a auditoria detecta cada sinal da seção 7"), 11 (LGPD). **Auditoria:** `docs/superpowers/cobertura-do-projeto.md` (lacunas C3 e C4; B.2 seções 3, 7.2, 11; B.5 LGPD).

**Quando executar:** depois do Plano 10 mesclado e **antes** do Plano 8 (para o inventário de rotas e a equivalência do 8 já incluírem os sinais novos e a trava).

## Estado herdado (confirmar na execução)

Antes da Tarefa 1, conferir na `main` que os Planos 4A, 9, 4, 5, 6, 7 e 10 foram mesclados e que estes nomes existem. Se algum mudou, atualizar só os nomes deste plano e registrar `Ruling:` no progresso.

| Nome | Origem |
|---|---|
| `Settings.fromEnv`, `booleano(env, nome, padrao)`, `retentionMonths` | `config/Settings.kt` (Plano 1) |
| `executarAuditado(auditoria, ctx, bloco)` | `cards/RotaAuditada.kt` (Plano 4, T7) |
| `CodigoSinal` (11 valores), `Sinal`, `Resultado`, `Detector`, `ContextoDeteccao`, `RegrasDeAuditoria`, `RegraSinal`, `MotivoAutorizacao`, `Autorizacao`, `AuditTimeline`, `AuditIssue`, `AuditStore`, `SqliteAuditStore`, `AuditCalculator` | `auditoria/` (Plano 4) |
| `auditoria.exemplo.yaml` (`sinais:` exige uma regra por código: `todoCodigoTemRegra`) | Plano 4, T1 |
| `todoCodigoDetectadoNoCenarioCompleto`, `documentoCitaTodosOsCodigos` | Plano 4, T5 e T9 |
| `ChangelogParser` com `CAMPOS_COM_VALOR = listOf("status", "resolution", "duedate", "labels", "OS Version")` (mais `assignee` normalizado); `ChangeEventRecord.changelogId` = chave + id do histórico + posição | `sync/ChangelogParser.kt` (Plano 3) |
| `SqliteIssueStore.upsert`: `INSERT INTO change_event ... ON CONFLICT (issue_id, changelog_id) DO NOTHING` | `sync/IssueStore.kt` (Plano 3) |
| `WorklogRecord(jiraId, autor, started, seconds)`, `WorklogParser`, `WorklogStore.porIssue`, tabela `worklog` com `instance` e `jira_id` | Plano 6, T10 |
| `SinalIntegridade.ESTIMATIVA_AUMENTADA` (inativo em `integridade.exemplo.yaml`), aviso `SINAL_SEM_DADO`, `DetectoresDePrazoEFluxo` | Plano 7, T4 e T5 |
| Rotas `/api/auditoria/*`, `/api/drill/auditoria`, `/api/cards/task-review`, `/api/drill/task-review` | Plano 4, T7 |
| Rotas `/api/integridade`, `/api/integridade/catalogo`, `/api/drill/integridade` | Plano 7, T7 |
| `ItemCentral.ALTERACOES_SEM_AUTORIZACAO`, `TASK_REVIEW_DIVERGENTE`, `INDICIO_ALTO` | Plano 7, T15 |
| `TarefaDto.historico` (`/api/tarefa/{key}`) | Plano 10, T5 |
| `Retention.purge` (apaga `change_event`, `worklog`, `issue` resolvida antiga, `access_log`, `sync_run`), `sync_run` `RETENCAO` | Plano 4A, T7 |
| `decisao_distribuicao` | Plano 7, T11 |
| `AccessLog.listar` | Plano 7, T14 |
| `ErroDto`, `CodigosDeMensagem` | Planos 9 (T4) e 10 (T9) |
| `MeDto(userId, roles)` (`/api/me`), `nav.ts` | Planos 3 e 1 |
| Último número de migração na `main` (confirme com `ls`; hoje só existe a V1) | `resources/db/migration/` |

## Restrições globais (Global Constraints)

- **Plataforma (adendo de 27/10, `plans/2026-10-27-adendo-plataforma-sqlite-sem-docker.md`):** SQLite embutido, sem Docker e sem servidor de banco; o adendo vence qualquer menção antiga a PostgreSQL, Docker ou Testcontainers. SQL novo segue a tradução da seção 2 do adendo (instantes em `INTEGER` ms UTC, datas em `TEXT` ISO, listas em JSON com `json_each`, helpers de `db/SqlSupport.kt`). Comandos de terminal: Windows 11 PowerShell (principal), por exemplo `cd app\backend; .\gradlew.bat test` e `cd app\frontend; pnpm test`; em `sh`, `cd app/backend && ./gradlew test`. Flags do Jira e de escrita só valem no `app/.env`.
- **A única trava de acesso ao Jira é a flag `JIRA_LIVE_ENABLED`** (regra 1 do `CLAUDE.md`, 25/10). Este plano não acessa o Jira: tudo roda com fixtures e `MockEngine`. A trava do DPO **não** é uma trava de acesso ao Jira: ela só decide se as telas de Auditoria e Integridade mostram dados; nada aqui liga ou desliga a sincronização.
- **A trava do DPO é decidida só por configuração** (`DASHBOARD_DPO_ACEITE`, `DASHBOARD_DPO_ACEITE_REF`, `DASHBOARD_DEV_MODE`, `JIRA_LIVE_ENABLED`), nunca olhando o banco. Fail-closed: qualquer valor ausente, vazio ou inválido = bloqueada fora do modo dev offline.
- **Só o usuário (depois do parecer do DPO) liga o aceite.** Nenhuma IA define `DASHBOARD_DPO_ACEITE=true` em arquivo (inclusive o `app/.env`, onde a flag só vale) ou comando; o `.env.example` traz `false`.
- **Indício, não prova:** os sinais novos seguem o aviso fixo `INDICIO_NAO_PROVA`; Integridade continua só do gerente; Auditoria, de coordenação e gerente.
- **Minimização:** o `change_event` só passa a guardar valor de campos numéricos de estimativa (segundos), nunca texto livre; o worklog continua sem comentário. Nenhum dado novo de pessoa além do que já existe (`author_ref`).
- **Titular:** a rota de dados próprios só devolve dados do principal da chamada; nenhum parâmetro escolhe outra pessoa.
- Toda rota nova grava `access_log` (inclusive 400, 403 e 404); falha ao gravar → 503.
- **Migração:** uma só, `V{n}__dados_para_auditoria_completa.sql`, com `n` = próxima migração livre (confirme com `ls app/backend/src/main/resources/db/migration`; hoje só existe a V1, então a V2; nunca editar a V1). Seu conteúdo depende da resposta do ponto de parada da Tarefa 3. Nunca editar migração mesclada.
- Documentos sem nomes reais, hosts reais, tokens nem e-mails reais. Não afirmar base legal: o DPO decide; os documentos apresentam candidatas como perguntas.
- Variáveis novas começam com `DASHBOARD_` ou `RETENTION_` (nunca `JIRA_`).
- Comentários em pt-BR; commits Conventional Commits pt-BR (`feat(lgpd): ...`, `feat(auditoria): ...`, `docs(lgpd): ...`).

## Foco da revisão (Review Focus)

1. **Produção sem o aceite** (`DASHBOARD_DEV_MODE=false`, aceite ausente) com o GERENTE chamando a API de Integridade direto: 403 `AGUARDANDO_ACEITE_DPO`, `access_log` gravado, nenhum dado no corpo → `producaoSemAceiteBloqueiaIntegridade` (T1).
2. **Modo dev com a flag do Jira ligada** (dados reais sem login): a trava continua fechada sem o aceite → `devComJiraLigadoTambemBloqueia` (T1).
3. **Rota sensível nova esquecida** (alguém cria `/api/auditoria/qualquer` depois): o teste que percorre a árvore de rotas exige a trava em todo prefixo sensível → `todaRotaSensivelPassaPelaTrava` (T1).
4. **Titular pedindo dados de outra pessoa** (`/api/titular/meus-dados?pessoa=bruno` como `ana`): o parâmetro é rejeitado com 400, nunca obedecido → `titularNaoEscolheOutraPessoa` (T9).
5. **Coluna nova no banco sem entrada no inventário** (um plano futuro acrescenta um campo pessoal): o teste do inventário falha citando `tabela.coluna` → `todaColunaEstaNoInventario` (T8).
6. **Estimativa reduzida antes de o trabalho começar** (replanejamento legítimo) não vira sinal; depois de entrar em andamento vira → `reducaoAntesDeIniciarNaoGera` e `reducaoDepoisDeIniciarGera` (T4).

---

## Arquivos (mapa)

```
app/backend/src/main/resources/db/migration/V{n}__dados_para_auditoria_completa.sql
app/backend/src/main/kotlin/br/sidia/painel/
  lgpd/{TravaDpo.kt, LgpdSettings.kt, TitularRoutes.kt, TitularDto.kt}
  config/Settings.kt, cards/RotaAuditada.kt, Application.kt, security (MeDto)
  sync/ChangelogParser.kt, sync/IssueStore.kt, sync/WorklogParser.kt, sync/WorklogStore.kt
  auditoria/{Sinais.kt, DetectoresDeEstimativa.kt, DetectoresDeWorklog.kt, AuditModel.kt, AuditStore.kt, RegrasDeAuditoria.kt}
  integridade/ (só o YAML de exemplo e testes), inicio/CentralCalculator.kt, tarefa/TarefaRoutes.kt
  db/Retention.kt
app/config/{auditoria.exemplo.yaml, integridade.exemplo.yaml}
app/frontend/src/{nav/nav.ts, shell/Menu.tsx, screens/MeusDados/, screens/Central/, shared/EstadoDaTela.tsx, i18n/}
app/.env.example, app/README.md
docs/lgpd/{inventario-de-dados.md, relatorio-para-o-dpo.md, comunicacao-a-equipe.md, atendimento-ao-titular.md}
docs/analise/diferencas-esperadas-auditoria.md, docs/jira/checklist-primeira-conexao.md
```

---

### Tarefa 1: Trava do DPO no backend (fail-closed, só por configuração)

**Arquivos:** Criar `lgpd/LgpdSettings.kt`, `lgpd/TravaDpo.kt`; Modificar `config/Settings.kt` (campo `lgpd`), `cards/RotaAuditada.kt` (`executarAuditado` mapeia `BloqueadoPeloDpoException`), as rotas de `auditoria/AuditoriaRoutes.kt` e `integridade/IntegridadeRoutes.kt`, `inicio/CentralCalculator.kt` (itens sensíveis), `tarefa/TarefaRoutes.kt` (campo `historico`), a rota `/api/me` (`MeDto`), `http/CodigosDeMensagem.kt`, `app/.env.example` (a chave do aceite sempre `false` e sem `REF`; o valor real vive só no `app/.env`, lido por `config/ArquivoEnv.kt`); Teste `lgpd/TravaDpoTest.kt`, `lgpd/TravaDpoRoutesTest.kt`, `config/SettingsTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  data class LgpdSettings(val aceiteDpo: Boolean = false, val aceiteRef: String? = null)
  enum class ModuloSensivel { AUDITORIA, INTEGRIDADE }
  class BloqueadoPeloDpoException(val modulo: ModuloSensivel) : RuntimeException("AGUARDANDO_ACEITE_DPO")
  class TravaDpo(private val settings: Settings) {
      val liberada: Boolean        // aceiteDpo || (dashboardDevMode && !jiraLiveEnabled)
      fun exigir(modulo: ModuloSensivel)   // lança BloqueadoPeloDpoException se !liberada
      fun motivo(): String?        // null se liberada; "AGUARDANDO_ACEITE_DPO" senão
  }
  // MeDto ganha: val modulosBloqueados: List<String> = emptyList()   // ["AUDITORIA", "INTEGRIDADE"] quando bloqueada
  ```
- `Settings.fromEnv`: `DASHBOARD_DPO_ACEITE` (`true`/`false` como as demais flags; vazio = `false`); `DASHBOARD_DPO_ACEITE_REF` (obrigatória quando o aceite é `true`: 1 a 80 caracteres `[A-Za-z0-9._/-]`, referência do parecer, por exemplo `DPO-2026-11`; ausente com aceite `true` → `IllegalArgumentException("DASHBOARD_DPO_ACEITE_REF obrigatória com DASHBOARD_DPO_ACEITE=true")`; inválida → erro citando só o nome). A referência sai no log de subida (só ela) e em `/api/governanca/configuracao` como `aceiteDpo: "<ref>"` ou `null`.
- Regra da trava (valor padrão 25/10, reversível): **liberada só com o aceite**, ou no **modo dev offline** (`DASHBOARD_DEV_MODE=true` **e** `JIRA_LIVE_ENABLED=false`, quando só há fixtures e dados de demonstração). Modo dev com a flag do Jira `true` fica **bloqueado** sem aceite.
- Onde a trava vale (`ModuloSensivel`):
  - **AUDITORIA:** `/api/auditoria/*`, `/api/drill/auditoria`, `/api/cards/task-review`, `/api/drill/task-review`; `TarefaDto.historico` (vira `null` com aviso informativo `HISTORICO_AGUARDANDO_ACEITE_DPO`); itens da Central `ALTERACOES_SEM_AUTORIZACAO` e `TASK_REVIEW_DIVERGENTE` (ficam fora da lista, com aviso `ITENS_AGUARDANDO_ACEITE_DPO`).
  - **INTEGRIDADE:** `/api/integridade`, `/api/integridade/catalogo`, `/api/drill/integridade`; item `INDICIO_ALTO` da Central.
- Ordem: o papel é conferido primeiro (USUARIO continua 403 sem código especial); só quem tem o papel recebe 403 `ErroDto("AGUARDANDO_ACEITE_DPO", {"modulo": "AUDITORIA"|"INTEGRIDADE"})`. O `access_log` grava a chamada com `filters` contendo `motivo=AGUARDANDO_ACEITE_DPO`.

- [ ] **Passo 1: Testes que falham:**
  - `TravaDpoTest`: `padraoDoAmbienteVazioEhLiberadaSoPorqueEhDevOffline` (`Settings.fromEnv(emptyMap())` → liberada, porque o padrão é dev com Jira desligado); `producaoSemAceiteBloqueia` (`DASHBOARD_DEV_MODE=false`); `devComJiraLigadoTambemBloqueia`; `aceiteLibera` (`DASHBOARD_DPO_ACEITE=true`, `REF=DPO-2026-11`, produção → liberada); `valorInvalidoFalhaNaSubida` (`DASHBOARD_DPO_ACEITE=sim`); `aceiteSemReferenciaFalha`; `referenciaInvalidaFalha` (`"a b"`, 81 caracteres).
  - `TravaDpoRoutesTest` (contexto com banco; settings de produção com autenticador de teste que injeta o principal):
    - `producaoSemAceiteBloqueiaAuditoria` e `producaoSemAceiteBloqueiaIntegridade`: GERENTE → 403, corpo `{"codigo":"AGUARDANDO_ACEITE_DPO","params":{"modulo":...}}`, sem nenhum outro campo; 1 linha em `access_log` com o motivo.
    - `usuarioContinua403SemCodigoDoDpo`.
    - `comAceiteTudoComoAntes` (as mesmas chamadas → 200).
    - `historicoDaGavetaBloqueado` (`/api/tarefa/TG-101` → 200, `historico == null`, aviso `HISTORICO_AGUARDANDO_ACEITE_DPO`).
    - `centralSemItensSensiveis` (sem `ALTERACOES_SEM_AUTORIZACAO`, `TASK_REVIEW_DIVERGENTE` nem `INDICIO_ALTO`; aviso presente).
    - `meDtoListaModulosBloqueados`.
    - `todaRotaSensivelPassaPelaTrava`: percorre a árvore de rotas; toda rota com prefixo `/api/auditoria`, `/api/integridade`, `/api/drill/auditoria`, `/api/drill/integridade`, `/api/cards/task-review` ou `/api/drill/task-review`, chamada como GERENTE sem aceite em produção → 403 com o código.
  - Continuam verdes, sem mudança, porque os contextos de teste usam o padrão dev offline: `soCoordenacaoEGerente`, `devModeSemLogin` (Plano 4), `integridadeSoGerenteEmTodasAsRotas`, `devModeGerenteVe` (Plano 7), `centralDoGerenteComIndicioAlto` (Plano 7).
- [ ] **Passo 2: Rodar** `cd app/backend && ./gradlew test --tests '*TravaDpo*' --tests '*SettingsTest*'` → FALHA.
- [ ] **Passo 3: Implementar.** `.env.example`: `DASHBOARD_DPO_ACEITE=false` e `DASHBOARD_DPO_ACEITE_REF=` com a nota "só depois do parecer do DPO; quem liga é o usuário".
- [ ] **Passo 4: Rodar** → PASSA; suíte completa (os testes dos Planos 4, 7 e 10 continuam verdes).
- [ ] **Passo 5: Commit** `feat(lgpd): trava do DPO para Auditoria e Integridade, decidida só por configuração`.

---

### Tarefa 2: Trava do DPO na tela

**Arquivos:** Modificar `app/frontend/src/nav/nav.ts` (item com `modulo?: 'AUDITORIA'|'INTEGRIDADE'`), `shell/Menu.tsx`, `shared/EstadoDaTela.tsx` (estado `aguardando-dpo`), `shared/useCard.ts`, `screens/Central/`, `api/client.ts` (`Me.modulosBloqueados`), i18n; Teste `shell/Menu.test.tsx`, `shared/EstadoDaTela.test.tsx`, `screens/Central/Central.test.tsx`.

**Interfaces:**
- Menu: itens de módulo bloqueado continuam visíveis para quem tem o papel, com o símbolo `⏸` e o texto "aguardando aceite do DPO" (en: "awaiting DPO approval"); ao abrir, a tela mostra o estado `aguardando-dpo`: "Esta tela fica desligada até o encarregado de dados (DPO) aprovar o uso. Nenhum dado foi consultado." (en equivalente).
- `estadoDe` (Plano 10) reconhece `ErroDaApi.codigo == "AGUARDANDO_ACEITE_DPO"` → `aguardando-dpo` (antes do caso genérico de 403).
- Central: a faixa de avisos mostra `ITENS_AGUARDANDO_ACEITE_DPO` por código, sem os itens.

- [ ] **Passo 1: Testes que falham:** `itemBloqueadoMostraPausaETexto` (pt e en; o símbolo não é o único sinal); `telaBloqueadaNaoMostraNumeros`; `estadoAguardandoDpoAntesDoSemPermissao`; `centralMostraAvisoSemItens`; `semBloqueioMenuNormal`.
- [ ] **Passo 2: Rodar** `cd app/frontend && pnpm vitest run src/shell src/shared src/screens/Central` → FALHA.
- [ ] **Passo 3: Implementar.**
- [ ] **Passo 4: Rodar** → PASSA; `pnpm build`.
- [ ] **Passo 5: Commit** `feat(ui): telas de Auditoria e Integridade aguardando o aceite do DPO`.

---

### Tarefa 3: Ampliação dos dados guardados para os sinais (com ponto de parada)

**PONTO DE PARADA OBRIGATÓRIO (antes do Passo 1): perguntar ao usuário se o painel passa a guardar o valor das estimativas no `change_event` e a data de criação do worklog** (spec, ponto de parada 1). A resposta decide esta tarefa e as Tarefas 4 a 6:

| Resposta | Esta tarefa | T4 (estimativa reduzida) | T5 (worklog retroativo) | T6 (`ESTIMATIVA_AUMENTADA` e completude) |
|---|---|---|---|---|
| A (Recomendada): estimativas e criação do worklog | tudo abaixo | executa | executa | executa (13 de 13 sinais) |
| B: só a criação do worklog | só a parte de worklog e a remoção de `person` | **pular** | executa | executa só a documentação: estimativa fica fora com `SINAL_SEM_DADO` |
| C: nada | só a remoção de `person` (se aplicável) | **pular** | **pular** | executa só a documentação: os dois sinais ficam fora, declarados |

Registrar a resposta, a data e a opção no `HANDOFF.md` e no progresso. **Sem resposta, não executar as Tarefas 3 a 6** (ampliar dados pessoais exige um "sim" explícito): seguir com as Tarefas 1, 2 e 7 a 12 e deixar a pergunta registrada no `HANDOFF.md` como pendência; as Tarefas 3 a 6 viram um PR próprio depois da resposta.

**Arquivos:** Criar `resources/db/migration/V{n}__dados_para_auditoria_completa.sql`; Modificar `sync/ChangelogParser.kt`, `sync/IssueStore.kt`, `sync/WorklogParser.kt`, `sync/WorklogStore.kt`, `docs/jira/checklist-primeira-conexao.md` (conferir o formato de `timeoriginalestimate`/`timeestimate` no changelog e o `created` do worklog), `docs/jira/matriz-compatibilidade.md`; Teste `sync/ChangelogParserTest.kt`, `sync/SyncerTest.kt`, `sync/WorklogParserTest.kt`, `sync/WorklogStoreTest.kt`, `sync/MigracaoDadosAuditoriaTest.kt`.

**Interfaces:**
- **Estimativas (opção A):** `CAMPOS_COM_VALOR` ganha `timeoriginalestimate` e `timeestimate`. Para esses dois campos o parser grava `from`/`to` **brutos** (segundos, como texto decimal), nunca `fromString`/`toString` (texto humanizado, como "1d 2h"). Valor não numérico → `null` (sem exceção).
- **Regravar eventos já existentes:** o `INSERT` de `change_event` passa de `DO NOTHING` para `ON CONFLICT (issue_id, changelog_id) DO UPDATE SET from_value = EXCLUDED.from_value, to_value = EXCLUDED.to_value WHERE change_event.field IN (SELECT value FROM json_each(?)) AND change_event.from_value IS NULL AND change_event.to_value IS NULL` (o parâmetro é a lista JSON dos dois campos de estimativa, montada com `emJson()` de `db/SqlSupport.kt`; `DO UPDATE ... WHERE` e `EXCLUDED` funcionam no SQLite ≥ 3.24). Nenhum outro campo é regravado; idempotente.
- **Worklog (opções A e B):** `WorklogRecord` ganha `val criado: Instant?` (campo `created` do worklog do DC); tabela `worklog` ganha `criado_em INTEGER` (anulável; milissegundos UTC, gravado com `setTimestamp`); `WorklogParser` lê `created`; `WorklogStore` grava e lê.
- **Migração `V{n}__dados_para_auditoria_completa.sql`** (o conteúdo segue a resposta):
  - A e B: `ALTER TABLE worklog ADD COLUMN criado_em INTEGER;`
  - A: `UPDATE sync_state SET watermark = NULL;` (a próxima sincronização, **só quando o usuário ligar a flag**, refaz a carga com changelog e preenche os valores; ver custo na spec). Confirmar na execução que o cursor do `SyncStateStore.cursor` lê `sync_state.watermark`; se for outra coluna, zerar essa e registrar `Ruling`.
  - A, B e C: se `grep -rn "person" app/backend/src/main` não achar uso da tabela `person` (V1: `jira_ref`, `name`), `DROP TABLE IF EXISTS person;` (minimização: nome de pessoa sem finalidade). Se houver uso, não remover e registrar no inventário (T8).
  - C sem remoção de `person` aplicável: **não criar migração** (registrar no progresso).
- Aviso informativo novo `HISTORICO_DE_ESTIMATIVA_INCOMPLETO` (`params["n"]`): eventos de estimativa sem valor ainda guardados (antes da nova carga). Entra em `CodigosDeMensagem` e no i18n.

- [ ] **Passo 1: Testes que falham** (conforme a opção):
  - A: `estimativaGuardaSegundosBrutos` (item `{"field":"timeoriginalestimate","from":"36000","fromString":"10h","to":"28800","toString":"8h"}` → `from="36000"`, `to="28800"`); `estimativaNaoNumericaViraNula`; `textoLivreContinuaSemValor` (`description` e `summary` → `from`/`to` nulos, como hoje); `regravaSoEstimativaSemValor` (evento antigo com valores nulos é preenchido; evento de `status` já gravado não muda; segunda gravação idêntica não muda nada); `cursorZeradoPelaMigracao` (`MigracaoDadosAuditoriaTest`: banco migrado até a V1 (ou até a migração anterior à nova), cursor preenchido → nulo depois da migração; linhas preservadas).
  - A e B: `worklogGuardaCriacao` (`"created":"2026-10-20T15:00:00.000-0400"` → instante correto); `worklogSemCriacaoFicaNulo`; `migracaoAcrescentaCriadoEm`.
  - A, B e C: `tabelaPersonRemovidaQuandoSemUso` (só se a remoção se aplica).
  - `FlagDesligadaZeroRequisicoesTest` continua verde (a migração não faz requisição).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*ChangelogParserTest*' --tests '*SyncerTest*' --tests '*Worklog*' --tests '*MigracaoDadosAuditoriaTest*'` → FALHA.
- [ ] **Passo 3: Implementar.**
- [ ] **Passo 4: Rodar** → PASSA; suíte completa.
- [ ] **Passo 5: Commit** `feat(sync): valor das estimativas no changelog e criação do worklog para a auditoria` (ajustar a mensagem à opção escolhida).

---

### Tarefa 4: Sinal "estimativa reduzida" (só com a opção A)

**Arquivos:** Criar `auditoria/DetectoresDeEstimativa.kt`; Modificar `auditoria/Sinais.kt` (`CodigoSinal.ESTIMATIVA_REDUZIDA`), `auditoria/RegrasDeAuditoria.kt` (chave `estimativaReducaoMinima`), `auditoria/AuditStore.kt` (`CAMPOS_AUDITADOS` inclui `timeoriginalestimate`), `auditoria/AuditCalculator.kt` (registra o detector), `app/config/auditoria.exemplo.yaml`, i18n; Teste `auditoria/DetectoresDeEstimativaTest.kt`, `auditoria/RegrasDeAuditoriaTest.kt`.

**Interfaces:**
- `object DetectoresDeEstimativa { val todos: List<Detector> }` com `ESTIMATIVA_REDUZIDA`: evento de `timeoriginalestimate` com `from` e `to` numéricos, `to < from` e `(from − to) / from ≥ estimativaReducaoMinima` (padrão `0.2`), **depois** da primeira entrada da issue em categoria ACTIVE (antes disso é planejamento). Autor autorizado pela regra da spec 7.1 (Reporter/PL, coordenação, gerente ou Squad Leader do responsável, vigentes no dia de Manaus do evento) → `ALERTA`; demais autores, inclusive o próprio responsável e autor desconhecido → `AUDITORIA`. `campo = "timeoriginalestimate"`, `de`/`para` em horas com uma casa (`"10.0"`, `"8.0"`).
- `auditoria.exemplo.yaml`: `estimativaReducaoMinima: 0.2` (0.05..0.9; fora → falha citando a chave) e `ESTIMATIVA_REDUZIDA: {gravidade: 2}` em `sinais`.
- Valores ausentes (eventos antigos sem valor) → nenhum sinal e o aviso `HISTORICO_DE_ESTIMATIVA_INCOMPLETO` (T3) no resultado.

- [ ] **Passo 1: Testes que falham:** `reducaoDepoisDeIniciarGera` (`ana` responsável reduz `36000 → 21600` depois de `In Progress` → 1 `AUDITORIA`, `de="10.0"`, `para="6.0"`); `reducaoAntesDeIniciarNaoGera`; `reducaoPequenaNaoGera` (`36000 → 32400` = 10%); `reducaoPeloReporterViraAlerta`; `aumentoNaoGera` (fica com a Integridade); `semValorNaoGeraEAvisa`; `limiteDaReducaoNoYaml` (0.04 e 0.91 → falha); `todoCodigoTemRegra` (Plano 4) continua verde com o código novo.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*DetectoresDeEstimativaTest*' --tests '*RegrasDeAuditoriaTest*'` → FALHA.
- [ ] **Passo 3: Implementar.**
- [ ] **Passo 4: Rodar** → PASSA.
- [ ] **Passo 5: Commit** `feat(auditoria): sinal de estimativa reduzida depois do início do trabalho`.

---

### Tarefa 5: Sinal "worklog retroativo" (opções A e B)

**Arquivos:** Criar `auditoria/DetectoresDeWorklog.kt`; Modificar `auditoria/Sinais.kt` (`CodigoSinal.WORKLOG_RETROATIVO`), `auditoria/AuditModel.kt` (`AuditIssue` ganha `val worklogs: List<WorklogRecord> = emptyList()`), `auditoria/AuditStore.kt` (`SqliteAuditStore` lê os worklogs pela `WorklogStore`; `comEventos` inclui issues com worklog **criado** em `[de, ate)`), `auditoria/RegrasDeAuditoria.kt` (chave `worklogRetroativoDiasUteis`), `auditoria/AuditCalculator.kt`, `app/config/auditoria.exemplo.yaml`, i18n; Teste `auditoria/DetectoresDeWorklogTest.kt`, `auditoria/AuditStoreTest.kt`.

**Interfaces:**
- `object DetectoresDeWorklog { val todos: List<Detector> }` com `WORKLOG_RETROATIVO` (`AUDITORIA`, qualquer autor) quando, para um worklog com `criado` não nulo:
  1. os dias úteis de Manaus entre o dia de `started` e o dia de `criado` são `≥ worklogRetroativoDiasUteis` (padrão `2`); **ou**
  2. `criado` é depois de `resolved` da issue e `started` é antes de `resolved`.

  Um sinal por worklog. `campo = "worklog"`, `de` = dia de `started`, `para` = dia de `criado`, `at = criado`, `autor` = autor do worklog, `autorizado`/`motivo` = avaliação da `Autorizacao` (informativa: o resultado é sempre `AUDITORIA`), `changelogId = "worklog:<jiraId>"`. Worklog sem `criado` (antigo) → nenhum sinal e aviso `HISTORICO_DE_WORKLOG_INCOMPLETO` (`params["n"]`).
- `auditoria.exemplo.yaml`: `worklogRetroativoDiasUteis: 2` (1..30) e `WORKLOG_RETROATIVO: {gravidade: 2}`.
- `HistoricoDto` (gaveta da Auditoria) passa a listar também os worklogs da issue como linhas `campo = "worklog"` com `de` = dia de início, `para` = dia de criação e duração em horas; sem comentário.

- [ ] **Passo 1: Testes que falham:** `registradoDoisDiasUteisDepoisGera` (`started` qui 15/10, `criado` seg 19/10 → 2 dias úteis → sinal); `umDiaUtilNaoGera`; `fimDeSemanaNaoConta` (sex → seg = 1 dia útil → não); `feriadoNaoConta` (calendário de teste com 12/10); `registradoDepoisDaResolucaoGera`; `fronteiraDeManaus` (`criado` `2026-10-20T03:30Z` = 19/10 em Manaus); `semCriacaoNaoGeraEAvisa`; `historicoListaWorklogsSemComentario`; `AuditStoreTest.comEventosIncluiWorklogCriadoNoPeriodo`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*DetectoresDeWorklogTest*' --tests '*AuditStoreTest*'` → FALHA.
- [ ] **Passo 3: Implementar.**
- [ ] **Passo 4: Rodar** → PASSA; suíte completa.
- [ ] **Passo 5: Commit** `feat(auditoria): sinal de worklog retroativo`.

---

### Tarefa 6: Integridade com `ESTIMATIVA_AUMENTADA`, auditoria completa e documentação

**Arquivos:** Modificar `app/config/integridade.exemplo.yaml` (`ESTIMATIVA_AUMENTADA` em `ativos`, só com a opção A), `auditoria/AuditCalculatorTest.kt` (`todoCodigoDetectadoNoCenarioCompleto`), `docs/analise/diferencas-esperadas-auditoria.md`, `docs/analise/diferencas-esperadas-*` da Integridade (Plano 7, bloco 7a), telas de Auditoria e Integridade (rótulos e catálogo), i18n, `resources/demo/samsung.json` (casos de estimativa e worklog fictícios); Teste `auditoria/AuditCalculatorTest.kt`, `integridade/DetectoresDePrazoEFluxoTest.kt`, `docs/DiferencasAuditoriaDocTest.kt` (o `documentoCitaTodosOsCodigos` do Plano 4), `demo/FixturesDeDemoTest.kt`.

**Interfaces:**
- Opção A: `todoCodigoDetectadoNoCenarioCompleto` passa a exigir **13** códigos; `integridade.exemplo.yaml` ativa `ESTIMATIVA_AUMENTADA` (o detector do Plano 7 já existe e passa a achar dado); o catálogo da Integridade (`/api/integridade/catalogo`) mostra o sinal como ativo.
- Opção B: 12 códigos; `ESTIMATIVA_AUMENTADA` continua inativo com `SINAL_SEM_DADO`.
- Opção C: 11 códigos (como hoje); o documento de diferenças registra os dois sinais como "fora por decisão do usuário em <data>".
- Em qualquer opção: o documento de diferenças tem uma linha por `CodigoSinal` com regra, limite configurável e dado usado; a seção "o que fica de fora" reflete a opção; a demonstração (`DASHBOARD_DEMO_DATA`) ganha um caso fictício de cada sinal ativo.

- [ ] **Passo 1: Testes que falham:** `todoCodigoDetectadoNoCenarioCompleto` (13, 12 ou 11 conforme a opção); `estimativaAumentadaAtivaNoExemplo` (opção A); `documentoCitaTodosOsCodigos` (com os novos); `cenarioCobreAuditoriaETempoEmStatus` (demo) com os casos novos.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*AuditCalculatorTest*' --tests '*DetectoresDePrazoEFluxoTest*' --tests '*DiferencasAuditoriaDocTest*' --tests '*FixturesDeDemoTest*'` → FALHA.
- [ ] **Passo 3: Implementar** e atualizar documentos e telas.
- [ ] **Passo 4: Rodar** → PASSA; suítes completas.
- [ ] **Passo 5: Commit** `feat(auditoria): auditoria completa da seção 7.2 e estimativa na Integridade`.

---

### Tarefa 7: Retenção final por categoria

**Arquivos:** Modificar `config/Settings.kt` (campo `retencao`), `db/Retention.kt`, `app/.env.example`, `app/README.md`, `docs/implantacao.md` (se já existir; senão, só o README); Teste `db/RetentionTest.kt`, `config/SettingsTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  data class RetencaoSettings(val historicoMeses: Int, val accessLogMeses: Int, val decisoesMeses: Int, val execucoesMeses: Int)
  // Settings.retencao: cada valor vem da variável própria ou, sem ela, de RETENTION_MONTHS (padrão 24)
  ```
  Variáveis (1..120; inválida → erro citando só o nome): `RETENTION_HISTORICO_MONTHS` (issue resolvida antiga, `change_event`, `worklog`), `RETENTION_ACCESS_LOG_MONTHS` (`access_log`), `RETENTION_DECISOES_MONTHS` (`decisao_distribuicao`, pela `decidido_em`), `RETENTION_EXECUCOES_MONTHS` (`sync_run`).
- `Retention.purge` usa o prazo de cada categoria, na data civil de Manaus, e grava em `sync_run` (`RETENCAO`) o total removido; o log de subida mostra os quatro prazos (só números).
- Os valores finais são decisão do DPO (pergunta no relatório, T11); o padrão continua 24 meses para todas as categorias (decisão do usuário de 25/10, Plano 4A).

- [ ] **Passo 1: Testes que falham:** `semVariaveisTudoEm24Meses`; `retentionMonthsVaiParaTodas` (`RETENTION_MONTHS=12` → 12 nas quatro); `variavelPropriaVence` (`RETENTION_ACCESS_LOG_MONTHS=6` → só o `access_log` em 6); `limitesForaDoIntervaloFalham` (0 e 121); `RetentionTest.decisoesAntigasSaoApagadas` (decisão de `2024-09-01` com prazo de 24 meses e hoje `2026-10-30` → apagada; de `2024-11-01` → fica); `RetentionTest.cadaCategoriaComSeuPrazo`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*RetentionTest*' --tests '*SettingsTest*'` → FALHA.
- [ ] **Passo 3: Implementar.**
- [ ] **Passo 4: Rodar** → PASSA.
- [ ] **Passo 5: Commit** `feat(lgpd): retenção configurável por categoria de dado`.

---

### Tarefa 8: Inventário dos dados tratados, ligado ao esquema

**Arquivos:** Criar `docs/lgpd/inventario-de-dados.md`, `app/backend/src/test/kotlin/br/sidia/painel/lgpd/InventarioDeDadosTest.kt`.

**Interfaces:**
- O inventário tem uma tabela com uma linha por `tabela.coluna` do esquema `public` (menos `flyway_schema_history`), com as colunas: `Tabela.coluna` | `Dado` | `Pessoal?` (`sim`, `indireto`, `não`) | `Finalidade` | `Origem` (Jira, painel, configuração) | `Quem vê` (papéis) | `Retenção` (variável de T7 ou "enquanto a issue existir") | `Base legal` (sempre "a definir pelo DPO"). Depois da tabela: os arquivos de configuração com dados de pessoas (`papeis.yaml`, `equipes.yaml`, `ausencias.yaml`, `squads.yaml`: o que guardam, onde ficam, quem edita), os logs do servidor (o que nunca entra: token, texto livre, e-mail) e as categorias que o painel **não** trata (comentários, descrição, e-mail, avatar, nome de exibição).
- Teste (SQLite real em arquivo temporário, `testContextComBanco`/`BancoEmArquivo`, banco migrado até a última versão):
  - `todaColunaEstaNoInventario` (lê `sqlite_master` e `pragma_table_info('tabela')`, que o SQLite oferece no lugar de `information_schema`; cada `tabela.coluna` aparece na tabela do documento; a mensagem de falha cita a coluna);
  - `nenhumaColunaInventariadaSumiu` (linha do documento sem coluna no banco → falha);
  - `colunaPessoalTemRetencaoEQuemVe`;
  - `baseLegalNaoAfirmada` (toda célula de base legal é "a definir pelo DPO");
  - `semDadosReais` (nenhum `@` fora de `.invalid`, nenhum `https://` fora de `.invalid`).

- [ ] **Passo 1: Escrever o teste** e rodar `./gradlew test --tests '*InventarioDeDadosTest*'` → FALHA (documento inexistente).
- [ ] **Passo 2: Escrever o inventário** a partir do banco migrado (listar as colunas pelo próprio teste ou por `\d+` no SQLite local (`pragma_table_info`/`sqlite_master`)).
- [ ] **Passo 3: Rodar** → PASSA.
- [ ] **Passo 4: Conferir** que `raw` (`issue.raw`) está descrito com o que o `SanitizadorRaw` remove e que `filters` (`access_log`) está descrito com o saneamento do `AccessLog`.
- [ ] **Passo 5: Commit** `docs(lgpd): inventário dos dados tratados, verificado contra o esquema`.

---

### Tarefa 9: Direitos do titular — consulta aos próprios dados (API)

**Arquivos:** Criar `lgpd/TitularRoutes.kt`, `lgpd/TitularDto.kt`; Modificar `Application.kt`, `http/CodigosDeMensagem.kt`; Teste `lgpd/TitularRoutesTest.kt`.

**Interfaces:**
- `GET /api/titular/meus-dados` (todos os papéis; `executarAuditado`; **qualquer** parâmetro de query → 400 `ErroDto("PARAMETRO_NAO_ACEITO")`):
  ```kotlin
  @Serializable data class MeusDadosDto(val pessoa: String, val geradoEm: String, val papeis: List<PapelVigenteDto>,
      val issuesComoResponsavel: Int, val issuesComoReporter: Int, val alteracoesComoAutor: Int, val worklogs: Int,
      val horasRegistradas: Double, val ausenciasPlanejadas: Int, val decisoesEnvolvendo: Int,
      val acessos: List<AcessoProprioDto>, val categorias: List<CategoriaTratadaDto>, val retencao: Map<String, Int>, val aviso: String)
  @Serializable data class PapelVigenteDto(val papel: String, val squad: String?, val de: String, val ate: String?)
  @Serializable data class AcessoProprioDto(val rota: String, val at: String)                  // últimos 90 dias, só do próprio, sem filtros
  @Serializable data class CategoriaTratadaDto(val categoria: String, val finalidade: String, val quemVe: List<String>, val retencaoVariavel: String)
  ```
  - `pessoa` = `principal.userId`; contagens só de linhas onde a pessoa é `assignee_ref`, `reporter_ref`, `author_ref`, autor de worklog, ou aparece em `decisao_distribuicao` (`sugerido`, `escolhido`, `decidido_por`), dentro da retenção.
  - `categorias`: Tempo em status, cards de trabalho, carga e ausências, Auditoria, Integridade e decisões, cada uma com finalidade, papéis que veem e variável de retenção. **Sem o conteúdo** de sinais de Auditoria ou Integridade sobre a pessoa (valor padrão 25/10, reversível; a decisão final é do DPO, pergunta 4 do relatório).
  - `aviso = "SOLICITACOES_PELO_CANAL_DO_DPO"`: correção e eliminação são pedidas pelo canal do DPO (os dados vêm do Jira e se corrigem lá); documentado em `docs/lgpd/atendimento-ao-titular.md` (T11).
  - A própria consulta é registrada em `access_log` (rota `/api/titular/meus-dados`).

- [ ] **Passo 1: Testes que falham** (cenário TRB-1 + worklogs do Plano 6 + uma decisão do Plano 7):
  - `devolveSoOsDadosDoPrincipal` (`ana` → contagens dela; nenhum id de outra pessoa no JSON).
  - `titularNaoEscolheOutraPessoa` (`?pessoa=bruno` → 400 `PARAMETRO_NAO_ACEITO`; `access_log` gravado).
  - `acessosSoDoProprioUltimos90Dias` (acesso de `bruno` não aparece; acesso de `ana` de 91 dias atrás não aparece).
  - `semConteudoDeSinais` (o JSON não contém nenhum `CodigoSinal` nem `SinalIntegridade`).
  - `todoPapelPodeConsultar` (USUARIO, SQUAD_LEADER, COORDENADOR, GERENTE → 200).
  - `naoDependeDaTravaDoDpo` (produção sem aceite → 200).
  - `semDadosDevolveZeros`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*TitularRoutesTest*'` → FALHA.
- [ ] **Passo 3: Implementar** (consultas parametrizadas; contagens por `COUNT`, sem carregar issues de outros).
- [ ] **Passo 4: Rodar** → PASSA.
- [ ] **Passo 5: Commit** `feat(lgpd): consulta do titular aos próprios dados`.

---

### Tarefa 10: Tela "Meus dados"

**Arquivos:** Criar `app/frontend/src/screens/MeusDados/{MeusDados.tsx, api.ts, MeusDados.test.tsx}`; Modificar `nav.ts` (item `meusDados`, todos os papéis, no grupo de configurações/conta já existente), `App.tsx`, i18n.

**Interfaces:**
- Tela editorial (leitura): o que o painel guarda sobre a pessoa (contagens e papéis), as categorias tratadas com finalidade e retenção, os acessos recentes e o botão "Baixar meus dados (JSON)" (o mesmo corpo da API, gerado no cliente com `Blob`, nome `meus-dados-<data>.json`). Texto fixo: "Para pedir correção ou eliminação, fale com o encarregado de dados (DPO). Os dados vêm do Jira e são corrigidos lá." (en equivalente).

- [ ] **Passo 1: Testes que falham:** `mostraContagensEPapeis`; `baixaJsonComOMesmoConteudo`; `textoDoCanalDoDpo` (pt e en); `itemNoMenuParaTodosOsPapeis`; `estadosDaTela` (carregando, erro, vazio pelo `EstadoDaTela`).
- [ ] **Passo 2: Rodar** `pnpm vitest run src/screens/MeusDados src/nav` → FALHA.
- [ ] **Passo 3: Implementar.**
- [ ] **Passo 4: Rodar** → PASSA; `pnpm build && node scripts/verificar-csp.mjs`.
- [ ] **Passo 5: Commit** `feat(ui): tela Meus dados com download do próprio registro`.

---

### Tarefa 11: Relatório para o DPO, atendimento ao titular e comunicação à equipe

**Arquivos:** Criar `docs/lgpd/relatorio-para-o-dpo.md`, `docs/lgpd/atendimento-ao-titular.md`, `docs/lgpd/comunicacao-a-equipe.md`, `app/backend/src/test/kotlin/br/sidia/painel/lgpd/DocumentosLgpdTest.kt`.

**Interfaces:**
- `relatorio-para-o-dpo.md`, seções nesta ordem: 1. O que é o painel (nesta etapa, somente leitura do Jira, sem escrita, sem ranking; o texto diz que, **se o Plano 16 "escrita no Jira pelo painel" for executado, este relatório e o aceite do DPO precisam ser revistos antes**); 2. Titulares (membros do time TG-Basic, líderes, coordenação, gerente); 3. Dados tratados (resumo e link para o inventário); 4. Finalidades por módulo (Tempo em status, trabalho e prazos, carga e ausências, Auditoria, Integridade, distribuição, registro de acesso); 5. **Bases legais candidatas, como perguntas** (não afirmadas); 6. Medidas técnicas e organizacionais (papéis e visibilidade, negar por padrão, registro de toda consulta, sanitização do `raw`, `change_event` só com valores permitidos, sem texto livre, retenção, CSP/CSRF, imagem sem segredos, testes que provam cada uma, com o nome do teste); 7. Riscos aos titulares e mitigação ("indício, não prova"; sinal não é conclusão; acesso restrito; sem decisão automatizada sobre pessoas: as sugestões de distribuição ficam em modo sombra e a decisão é humana); 8. Retenção proposta (24 meses, por categoria, variáveis da T7); 9. Direitos do titular (tela Meus dados e canal do DPO); 10. Incidentes (link para o runbook de incidentes do Plano 12; prazo de comunicação "conforme a regulamentação vigente da ANPD, a confirmar com o DPO"); 11. **Perguntas ao DPO**, cada uma com opções (a recomendada primeiro) e consequência no painel; 12. Como registrar o aceite (`DASHBOARD_DPO_ACEITE=true` e `DASHBOARD_DPO_ACEITE_REF=<referência do parecer>`, definidos **pelo usuário** no ambiente de produção, e a data no `HANDOFF.md`); 13. Se o DPO pedir um Relatório de Impacto (RIPD), este documento e o inventário são o insumo.
- Perguntas ao DPO (mínimo; opções e consequências no texto):
  1. Base legal da Auditoria (opções candidatas: legítimo interesse com teste de balanceamento; execução de contrato de trabalho; outra indicada pelo DPO).
  2. Base legal da Integridade (mesmas candidatas; a Integridade é mais sensível por avaliar padrões por pessoa).
  3. Prazo de retenção por categoria (24 meses; 12 meses; outro).
  4. O titular vê o conteúdo dos sinais sobre ele? (não, só as categorias, que é o padrão atual; sim, na tela Meus dados; sim, só mediante pedido ao DPO).
  5. Forma de comunicação à equipe (texto da `comunicacao-a-equipe.md` por e-mail interno; em reunião com o texto como apoio; outra).
  6. Os módulos podem ser liberados separadamente (Auditoria antes da Integridade)? (Hoje a trava libera os dois juntos; liberar separado vira uma mudança pequena: duas variáveis.)
- `atendimento-ao-titular.md`: como a pessoa consulta (tela e JSON), como pede correção (no Jira, pelo PL ou líder), como pede eliminação (pelo DPO; o operador apaga pelo procedimento do runbook do Plano 12, respeitando a retenção legal), prazos internos sugeridos (a confirmar com o DPO) e o registro de cada pedido (fora do painel, no canal do DPO).
- `comunicacao-a-equipe.md`: texto curto em linguagem simples: o que o painel mostra, o que não faz (nesta etapa, não escreve no Jira; não ranqueia pessoas; não tira conclusões), quem vê o quê por papel, por quanto tempo os dados ficam, que toda consulta é registrada, e como ver os próprios dados.
- Teste `DocumentosLgpdTest`:
  - `relatorioTemAsTrezeSecoes`;
  - `relatorioCitaCadaModuloSensivelETrava` (`AUDITORIA`, `INTEGRIDADE`, `DASHBOARD_DPO_ACEITE`, `DASHBOARD_DPO_ACEITE_REF`);
  - `relatorioCitaCadaVariavelDeRetencao`;
  - `cadaPerguntaTemOpcoesComRecomendada` (cada pergunta tem ao menos duas opções e uma marcada "(Recomendada)");
  - `medidasCitamTestesQueExistem` (cada `Classe.metodo` citado existe em `src/test`);
  - `baseLegalNuncaAfirmada` (o texto não contém "a base legal é");
  - `comunicacaoCitaCadaPapelEIndicioNaoProva`;
  - `semDadosReais` (como na T8).

- [ ] **Passo 1: Escrever o teste** e rodar `./gradlew test --tests '*DocumentosLgpdTest*'` → FALHA.
- [ ] **Passo 2: Escrever** os três documentos.
- [ ] **Passo 3: Rodar** → PASSA.
- [ ] **Passo 4: Revisão de linguagem:** frases curtas, sem jargão técnico na comunicação à equipe; termos técnicos do relatório explicados na primeira menção.
- [ ] **Passo 5: Commit** `docs(lgpd): relatório para o DPO, atendimento ao titular e comunicação à equipe`.

---

### Tarefa 12: Fechamento

**Arquivos:** Modificar `HANDOFF.md`, `app/README.md` (seção "LGPD e aceite do DPO"), `docs/guia-execucao-outra-ia.md` (seção 7: ligar o aceite do DPO é decisão do usuário, como a flag do Jira).

**Interfaces:** nenhuma de código.

- [ ] **Passo 1: Suítes completas:** `cd app/backend && ./gradlew test`; `cd app/frontend && pnpm lint && pnpm vitest run && pnpm build && node scripts/verificar-csp.mjs`; `sh .claude/hooks/test-guard.sh`; varredura do Plano 9 → todos verdes.
- [ ] **Passo 2: Subida de conferência** com `DASHBOARD_DEV_MODE=false` e autenticador de teste não é possível sem o OIDC do Plano 8; conferir então só por teste (`TravaDpoRoutesTest`) e, em modo dev offline com `DASHBOARD_DEMO_DATA=true`, que as telas de Auditoria e Integridade mostram os sinais novos de demonstração.
- [ ] **Passo 3: Atualizar** o `HANDOFF.md`: opção escolhida na T3 e data; pendência "parecer do DPO" (com o link do relatório) e "aceite ligado pelo usuário" como itens abertos; pendência "nova carga com changelog para preencher estimativas" (opção A) para quando a flag for ligada.
- [ ] **Passo 4: Push e PR** pelo fluxo do guia; CI do Plano 9 verde.
- [ ] **Passo 5: Commit** `docs: fechamento do Plano 11 (auditoria completa e LGPD)`.

---

## Autoavaliação

**Cobertura (pedido e auditoria):**

| Item | Tarefa |
|---|---|
| C4: "estimativa reduzida" (spec 7.2) | T3 (dado), T4 |
| C4: "worklog retroativo" (spec 7.2) | T3 (dado), T5 |
| C4 / spec 10: a auditoria detecta cada sinal da seção 7; `ESTIMATIVA_AUMENTADA` com dado | T6 |
| Ponto de parada: ampliar campos guardados no `change_event` | T3 |
| C3: trava em código até o aceite do DPO, flag própria, fail-closed | T1, T2 |
| C3: inventário dos dados tratados | T8 |
| C3: retenção final | T7 (e pergunta 3 ao DPO) |
| C3: direitos do titular (acesso ao próprio registro) | T9, T10, T11 |
| C3: relatório para o DPO e comunicação à equipe | T11 |
| Minimização (tabela `person` sem uso) | T3 |

**Consistência de tipos:** `TravaDpo`/`ModuloSensivel`/`BloqueadoPeloDpoException` (T1) são usados por `executarAuditado`, pelas rotas e pela Central; `AGUARDANDO_ACEITE_DPO` entra em `CodigosDeMensagem` (Plano 10) e no estado `aguardando-dpo` da tela (T2); `WorklogRecord.criado` (T3) é lido por `DetectoresDeWorklog` (T5) via `AuditIssue.worklogs`; `RetencaoSettings` (T7) é citado pelo inventário (T8), pela `MeusDadosDto.retencao` (T9) e pelo relatório (T11).

**Fora deste plano:** o parecer do DPO e o aceite em si (usuário e DPO); liberar Auditoria e Integridade separadamente (depende da pergunta 6); exibir ao titular o conteúdo dos sinais (pergunta 4); runbook de incidentes (Plano 12).

## Decisões (25/10, valor padrão recomendado, reversível, salvo indicação)

1. **Trava liberada só com o aceite ou no modo dev offline**, decidida só pelo `Settings`. Modo dev com a flag do Jira ligada fica bloqueado.
2. **Resposta da trava: 403 com `ErroDto("AGUARDANDO_ACEITE_DPO")`**, depois da checagem de papel, sempre registrada.
3. **Estimativa reduzida:** redução de pelo menos 20% da estimativa original depois do início do trabalho; autorizado vira alerta, os demais viram auditoria.
4. **Worklog retroativo:** 2 dias úteis entre o início declarado e o registro, ou registro depois da resolução; sempre auditoria.
5. **Ampliar o `change_event` e o worklog: decisão do usuário** (ponto de parada da T3); a opção recomendada inclui zerar o cursor para refazer a carga quando a flag for ligada.
6. **Retenção por categoria**, todas com 24 meses por padrão (decisão do usuário de 25/10); valores finais pelo DPO.
7. **Titular vê contagens, papéis, categorias e os próprios acessos**, sem o conteúdo de sinais, até o DPO decidir.
8. **Remover a tabela `person`** se não houver uso em `src/main`.
