# Fase 14: Primeira conexão, validação com dados reais e calibração — Plano de implementação

> **Para executores:** USE superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Os passos usam caixas `- [ ]`. Para o ClineSR, isso equivale à seção 4 de `docs/guia-execucao-outra-ia.md`.

**Objetivo (Goal):** Depois que **o usuário** ligar `JIRA_LIVE_ENABLED`, fazer a primeira conexão real com cuidado (checklist), descobrir os ids reais dos campos e preencher o `jira_campos.yaml` local, rodar a primeira sincronização, validar cada card com dados reais contra uma referência (contagem do próprio Jira, app legado na tag `legacy-final` ou números conhecidos do gestor), decidir e validar os cards da instância Sidia (projeto CAT), calibrar heurísticas e limiares com dados reais, e registrar toda divergência com uma categoria e uma ação. Nada disso é "afirmado" sobre o Jira real antes de validado (PRODUCT.md, princípio 4).

**Arquitetura (Architecture):** O plano tem duas metades, cada uma um PR:
- **14a — primeira conexão (Tarefas 1 a 6).** Pode começar **logo depois do Plano 4A**, em paralelo com a sequência principal, assim que o usuário ligar a flag. Cria só ferramentas pequenas e isoladas no pacote `validacao/` (sem migração, para não disputar número com os planos em curso): o casador de campos e a conferência de status, que trabalham sobre respostas salvas **fora do repositório**; o registro de divergências; e o roteiro da primeira conexão.
- **14b — validação dos cards e calibração (Tarefas 7 a 14).** Começa depois dos Planos 5, 6 e 7 (cards prontos) e, para Auditoria e Integridade, depois da trava de LGPD do Plano 11 liberada pelo DPO. Acrescenta a recarga completa (sem rede), o roteiro de validação por ficha, a ferramenta de calibração por retroteste e o aceite do gestor.

Todo código novo é testado **offline**, com respostas sintéticas em `src/test/resources/fixtures/validacao/` (hosts `.invalid`, nomes fictícios). **Nenhum teste automático toca o Jira real**, mesmo com a flag `true` (a tarefa `test` do Gradle fixa a flag em `false`, Plano 4A, T11). As etapas que falam com o Jira são **passos de runbook**, executados pela IA só com a flag ligada pelo usuário.

**Stack (Tech Stack):** Kotlin 2 + Ktor 3.3.1, kotlinx.serialization (JSON), kaml (YAML estrito), SQLite embutido (testes com SQLite real em arquivo temporário), Ktor `MockEngine`.

**Spec da fase (ler primeiro):** `docs/superpowers/specs/2026-10-25-spec-fase-14-primeira-conexao-validacao-e-calibracao.md`. **Spec mestra:** `docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md` (seções 3, 4.2 "Heurísticas", 4.6 "Oráculo de números", 5.5, 9, 10 e 11). Documentos de base: `docs/jira/checklist-primeira-conexao.md`, `docs/jira/runbook-sincronizacao.md`, `docs/jira/matriz-compatibilidade.md`. Lacunas de origem: `docs/superpowers/cobertura-do-projeto.md`, C2, A6 e M6.

## Estado herdado (confirmar na execução)

**14a** confere, antes da Tarefa 1, que o Plano 4A está na `main`. **14b** confere, antes da Tarefa 7, que os Planos 5, 6 e 7 estão na `main` (e o 11, para a Tarefa 12). Se algum nome mudou, use o da `main`, atualize só os nomes deste plano e registre `Ruling:` no progresso.

| Nome ou capacidade | Origem |
|---|---|
| `CLAUDE.md`, regra 1: a flag `JIRA_LIVE_ENABLED` é a **única** trava; só o usuário muda. **Se a regra 1 da `main` ainda exigir autorização por pesquisa, vale o `CLAUDE.md`: perguntar ao usuário antes de cada consulta.** | `CLAUDE.md` (25/10) |
| `docs/jira/checklist-primeira-conexao.md` com "Confirme que a flag de acesso ao Jira está ligada e rode o passo" em cada passo; `cadaPassoDoChecklistConfirmaAFlag`, `checklistSemRegraAntiga` | Plano 4A, T11 |
| `docs/jira/runbook-sincronizacao.md` (Etapas 1 a 3), `SyncSettings` (`SYNC_MAX_ITENS`, `SYNC_MAX_REQUISICOES`, `SYNC_FUSO_SERVIDOR`, `SYNC_INSTANCIAS`), `GET /api/sync/status`, `SyncStateStore` (`cursor`, `avancarCursor`), `SyncService.incremental` | Plano 4A, T1, T2, T5, T8, T9 |
| `FieldDictionary`, `FieldDefinition` (`nome`, `tipo`, `instancia`, `finalidade`, `valores`, `customId`), `FieldType` (`SELECT`, `TEXT`, `DATE`, `USER`, `LABELS`, `NUMBER`), `CardQueryFactory`, `ctx.fichas`, `ctx.campos` | Planos 2 e 4A, T3 |
| `StatusMap` (com `ordem` e `resolucoesNegativas`) | Planos 3 e 5, T1 |
| `.gitignore` com `**/config/jira_campos.yaml` e `**/config/status_map.yaml` | Plano 4A, T9 |
| Fichas `app/config/cards/*.card.yaml` e as rotas `/api/cards/{id}` | Planos 3 a 7 |
| Heurísticas em YAML versionado: `risco`, `previsao`, `metas`, `planejamento`, `esforco`, `integridade`, `distribuicao`, `checklist_review`, `auditoria` (`app/config/<nome>.exemplo.yaml`) | Planos 4 a 7 |
| Trava de LGPD de Auditoria e Integridade (liberada só com aceite do DPO) | Plano 11 (confirmar o nome) |
| Validador de configuração (por exemplo `painel validar-config`) e, se existir, o mecanismo de subcomandos da linha de comando | Plano 12 (confirmar nomes) |
| Tag `legacy-final` (se o `legacy/` já foi removido) | Plano 8, T9 |

## Restrições globais (Global Constraints)

- **Plataforma (adendo de 27/10, `plans/2026-10-27-adendo-plataforma-sqlite-sem-docker.md`):** SQLite embutido, sem Docker e sem servidor de banco; o adendo vence qualquer menção antiga a PostgreSQL, Docker ou Testcontainers. SQL novo segue a tradução da seção 2 do adendo (instantes em `INTEGER` ms UTC, datas em `TEXT` ISO, listas em JSON com `json_each`, helpers de `db/SqlSupport.kt`). Comandos de terminal: Windows 11 PowerShell (principal), por exemplo `cd app\backend; .\gradlew.bat test` e `cd app\frontend; pnpm test`; em `sh`, `cd app/backend && ./gradlew test`. Flags do Jira e de escrita só valem no `app/.env`.
- **A flag é a única trava, e só o usuário a muda.** A IA executora **nunca** liga `JIRA_LIVE_ENABLED`, nem dentro de um comando. Na Tarefa 1 ela **pergunta** ao usuário e **espera**. Com a flag `false`, nenhuma etapa de runbook deste plano roda; as tarefas de código seguem offline.
- **Nenhum teste automático acessa o Jira real**, mesmo com a flag `true`. Testes usam fixtures sintéticas e `MockEngine`.
- **Primeira conexão segue o checklist**, na ordem, uma instância por vez, com os limites dele (`maxResults=1` no Passo 3, uma issue no Passo 4). **Pare no primeiro 401, 403, 429, 5xx ou CAPTCHA**: não repetir, não trocar credencial; relatar ao usuário e esperar.
- **Respostas brutas do Jira nunca entram no repositório.** Ficam num diretório local fora da árvore do git (`$HOME/painel-validacao/<data>/`, modo `700`), são lidas pelas ferramentas e **apagadas** ao fim da tarefa que as usou (registrar a remoção no progresso).
- **Configs reais são locais e ignoradas pelo git:** `app/config/jira_campos.yaml`, `status_map.yaml`, `papeis.yaml`, `equipes.yaml`, `ausencias.yaml`, `squads.yaml`, `calendario.yaml` e os YAML de heurística calibrados. **Nunca** preencher `customId` ou valores reais num arquivo `.exemplo`. Antes de cada commit: `git status` não lista nenhum desses arquivos.
- **Registros versionados guardam só agregados e datas:** sem chave de issue, username, nome, e-mail, host, `customfield` real, JQL com valor real nem resposta bruta. Comparações por pessoa ou por issue ficam em `validacao-local/` (ignorado pelo git, criado na T3). O repositório pode ter visibilidade em discussão (cobertura A8): trate-o como se pudesse ser lido por terceiros.
- **Auditoria e Integridade com dados reais só depois do aceite do DPO** (trava do Plano 11). Até lá, essas telas não são validadas com dados reais (Tarefa 12 é ponto de parada, não pulo silencioso).
- **Sem migração no 14a.** O 14b também não precisa de migração (a recarga completa só zera o cursor).
- Variáveis novas começam com `DASHBOARD_` ou `SYNC_` (nunca `JIRA_`). Commits em Conventional Commits pt-BR (`feat(validacao): ...`, `docs(jira): ...`, `chore(calibracao): ...`). Comentários em pt-BR.

## Foco da revisão (Review Focus)

1. **Campo do dicionário casado com o campo errado** (dois campos com o mesmo nome em projetos diferentes, ou tipo real diferente do YAML): o casador não grava nada e lista o problema → `nomeDuplicadoParaSemGravar`, `tipoDivergenteParaSemGravar` (T2).
2. **`customId` ou resposta bruta vazando para o git** → `nuncaEscreveEmArquivoExemplo`, `saidaPadraoEhIgnoradaPeloGit` (T2), `registroSemDadosPessoais` (T3).
3. **Registro de divergência com chave de issue ou username** → `registroSemDadosPessoais` (T3).
4. **Recarga completa que faz requisição ou apaga dados** → `recargaNaoFazRequisicao`, `recargaNaoApagaIssues` (T7).
5. **Calibração que sobrescreve a configuração em uso ou ajusta sobre a mesma amostra que avalia** → `propostaNaoSobrescreve`, `avaliaEmJanelaSeparada` (T11).
6. **Card dado como "validado" sem referência independente** (o painel comparado com ele mesmo) → `roteiroExigeFonteDeReferencia` (T8).

---

## Arquivos (mapa)

```
app/backend/src/main/kotlin/br/sidia/painel/validacao/
  CasadorDeCampos.kt             # /field (JSON salvo) × dicionário → jira_campos.yaml local (14a, T2)
  ConferenciaDeStatus.kt         # /status (JSON salvo) × status_map → relatório e proposta local (14a, T2)
  RegistroDeDivergencias.kt      # leitura e validação estrita do registro (14a, T3)
  RoteiroDeValidacao.kt          # roteiro por ficha (14b, T8)
  Calibracao.kt                  # retroteste das heurísticas (14b, T11)
  ValidacaoCli.kt                # subcomandos casar-campos, conferir-status, roteiro, calibrar, recarga-completa
app/backend/src/main/kotlin/br/sidia/painel/sync/SyncStateStore.kt   # + zerarCursor (14b, T7)
app/backend/src/test/kotlin/br/sidia/painel/validacao/*Test.kt
app/backend/src/test/resources/fixtures/validacao/{field.json, status.json, field-duplicado.json, field-tipo-errado.json}
docs/validacao/{registro-de-divergencias.yaml, roteiro-primeira-conexao.md, roteiro-de-validacao.md, calibracao.md, aceite-do-gestor.md}
docs/jira/matriz-compatibilidade.md, docs/jira/checklist-primeira-conexao.md (só registros e correções), .gitignore (validacao-local/)
HANDOFF.md, docs/superpowers/plans/2026-10-24-indice-dos-planos.md
```

---

## Bloco 14a — primeira conexão (depois do Plano 4A)

### Tarefa 0 (pré-requisito do 14a): os hooks de proteção passam a ler a flag do arquivo de variáveis do app

**Por quê:** desde 27/10 o app lê a flag do Jira **somente** de `app/.env` (`config/ArquivoEnv.kt`), mas os hooks de proteção (`.claude/hooks/guard.sh` e `.clinerules/hooks/PreToolUse` / `PreToolUse.ps1`) ainda leem a variável da flag do **ambiente do processo** da IA. Resultado: o usuário liga a flag no `app/.env`, o painel passa a acessar o Jira, mas o hook continua barrando todo comando da IA que cite o Jira. Duas chaves para a mesma trava é o que a decisão de 27/10 quer evitar.

**Interim (se esta tarefa ainda não foi feita):** o usuário, além de editar o `app/.env`, define a variável de usuário do Windows com o mesmo nome e valor (pelo painel "Variáveis de ambiente" do Windows ou por `[Environment]::SetEnvironmentVariable` no PowerShell dele) e reinicia o VS Code. A IA **não** faz isso por ele.

**Arquivos:** modificar `.clinerules/hooks/PreToolUse.ps1`, `.clinerules/hooks/PreToolUse`, `.claude/hooks/guard.sh` e os três testes (`test-PreToolUse.ps1`, `test-PreToolUse.sh`, `test-guard.sh`); atualizar `docs/ferramentas-claude-para-o-cline/hooks.md` e a "Hooks e a flag" do `HANDOFF.md`.

- [ ] **Passo 1: Testes que falham** (nos três arquivos de teste, com um arquivo de variáveis temporário apontado por variável de teste, nunca o arquivo real): `flagVemDoArquivoDeVariaveis` (flag verdadeira no arquivo libera; falsa ou ausente bloqueia); `ambienteDoProcessoNaoLiga` (ambiente verdadeiro e arquivo falso ou ausente: bloqueia); `ambienteDoProcessoNaoDesliga` (arquivo verdadeiro, ambiente falso: libera); `arquivoIlegivelFalhaFechado` (arquivo inexistente, sem permissão ou valor inválido: bloqueia); `leituraDaIaAoArquivoContinuaBarrada` (o hook lê o arquivo para si, mas continua negando que a IA o leia, escreva ou versione: os casos antigos seguem passando).
- [ ] **Passo 2: Implementar.** O hook localiza o arquivo na raiz do repositório (`<raiz>/app/.env`; a raiz vem do próprio caminho do hook, não de variável da IA), lê **só a linha da flag** (mesma regra de `ArquivoEnv`: `NOME=valor`, aspas e comentário final opcionais, sem diferenciar maiúsculas no valor) e trata qualquer erro como "flag desligada". Nunca imprime o conteúdo do arquivo nem o valor de outras variáveis. Mantenha todas as demais travas (texto que liga a flag, arquivo de variáveis protegido contra a IA etc.).
- [ ] **Passo 3: Rodar** as três suítes de hook (`sh .claude/hooks/test-guard.sh`; `sh .clinerules/hooks/test-PreToolUse.sh`; `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .clinerules\hooks\test-PreToolUse.ps1`) e a suíte do backend.
- [ ] **Passo 4: Revisão adversarial** (é código de segurança: peça ao revisor a mutação real dos testes e a tentativa de contornar: caminho relativo, symlink, variável de ambiente de nome parecido, arquivo com a flag duplicada, a última ocorrência vence).
- [ ] **Passo 5: Commit** `fix(hooks): a flag do Jira é lida do arquivo de variáveis do app, igual ao painel`.

### Tarefa 1: Pedir ao usuário que ligue a flag e preparar a sessão

**Arquivos:** Criar `docs/validacao/roteiro-primeira-conexao.md`; Modificar `.gitignore` (`validacao-local/`); Teste `validacao/RoteiroDocTest.kt`.

**Interfaces:** O roteiro reúne, numa página, a ordem desta fase: checklist (Passos 1 a 5 e o passo extra), casador de campos (T2), conferência de status (T2), primeiro ciclo de sincronização (T5) e onde registrar cada resultado. Não repete o checklist: aponta para ele.

- [ ] **Passo 1: Testes que falham:** `roteiroApontaChecklistERunbook` (links para os dois documentos), `roteiroDizQueSoOUsuarioLigaAFlag`, `roteiroSemHostRealNemToken` (mesma regra de `documentosDeJiraNaoTemHostRealTokenNemEmail`), `validacaoLocalIgnoradaPeloGit` (o `.gitignore` contém `validacao-local/`).
- [ ] **Passo 2: Rodar** `cd app/backend && ./gradlew test --tests '*RoteiroDocTest*'` → FALHA. **Passo 3: Escrever.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `docs(validacao): roteiro da primeira conexão`.
- [ ] **Passo 6: Ponto de parada: perguntar ao usuário** e **esperar a resposta** antes de qualquer acesso ao Jira:
  - "Eu (usuário) ligo a flag `JIRA_LIVE_ENABLED=true` agora, na instância Samsung DC, e a IA segue o checklist (Recomendada)": o usuário liga a flag e preenche URL e token da instância **dentro de `app/.env`** (o painel só lê a flag ali; o arquivo nunca é versionado nem lido pela IA), faz o interim da Tarefa 0 se ela ainda não estiver feita (variável de usuário do Windows para os hooks + reiniciar o VS Code) e reinicia o painel; consequência: a IA executa os Passos 1 a 5 na ordem, parando no primeiro erro.
  - "Eu (usuário) ligo a flag, mas eu mesmo rodo as consultas e passo as respostas salvas à IA": consequência: a IA só roda as ferramentas offline sobre os arquivos que o usuário colocar em `$HOME/painel-validacao/`.
  - "Ainda não": consequência: o bloco 14a para aqui; registrar no `HANDOFF.md` a pendência com data e seguir com a sequência principal.
- [ ] **Passo 7: Conferir** (só depois do "sim"): `JIRA_LIVE_ENABLED` vale `true` **no `app/.env` que o usuário configurou** (a flag só vale ali; a IA lê, não escreve); o token está só no `app/.env` (fora do git); `git status` limpo de arquivos de config real. Se algo falhar, parar e relatar.

---

### Tarefa 2: Casador de campos e conferência de status (offline)

**Arquivos:** Criar `validacao/CasadorDeCampos.kt`, `validacao/ConferenciaDeStatus.kt`, `validacao/ValidacaoCli.kt` (subcomandos `casar-campos` e `conferir-status`; se o Plano 12 já tiver um mecanismo de subcomandos, registrar estes nele), `src/test/resources/fixtures/validacao/{field.json, field-duplicado.json, field-tipo-errado.json, status.json}` (sintéticos, ids `customfield_90001`…); Teste `validacao/CasadorDeCamposTest.kt`, `validacao/ConferenciaDeStatusTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  data class CampoJira(val id: String, val nome: String, val schemaType: String?, val schemaCustom: String?, val schemaItems: String?)
  enum class ProblemaDeCampo { AUSENTE, NOME_DUPLICADO, TIPO_DIVERGENTE }
  data class ResultadoCasamento(val preenchidos: Map<String, String> /* nome → customId */,
                                val problemas: List<Pair<String, ProblemaDeCampo>>)   // ordenado por nome do campo
  object CasadorDeCampos {
      fun lerField(json: String): List<CampoJira>
      fun tipoDe(c: CampoJira): FieldType?   // option→SELECT, string→TEXT, date/datetime→DATE, user→USER,
                                             // array+labels→LABELS, number→NUMBER; outro → null
      fun casar(dicionario: FieldDictionary, instancia: String, campos: List<CampoJira>): ResultadoCasamento
      fun gravar(dicionario: FieldDictionary, r: ResultadoCasamento, destino: Path)   // recusa se r.problemas não vazio
  }
  data class ResultadoStatus(val desconhecidos: List<String>, val categoriaDivergente: List<String>, val proposta: String /* YAML */)
  object ConferenciaDeStatus { fun conferir(statusJson: String, atual: StatusMap): ResultadoStatus }
  ```
- Campos nativos (`project`, `status`, `duedate`…) casam pelo `id` nativo e não recebem `customId`. Só campos do dicionário com `instancia` igual à pedida entram.
- `gravar` escreve o dicionário inteiro com os `customId` preenchidos; o destino padrão do subcomando é `app/config/jira_campos.yaml` (ignorado pelo git). Destino terminado em `.exemplo.yaml` → `IllegalArgumentException`.
- As mensagens e o relatório citam nomes de campo do dicionário e códigos de problema, nunca o corpo da resposta.

- [ ] **Passo 1: Testes que falham:**
  - `casaPorNomeExato` (`OS Version` → `customfield_90001`); `camposNativosSemCustomId`.
  - `nomeDuplicadoParaSemGravar` (`field-duplicado.json`: dois `SKU Type` → problema `NOME_DUPLICADO`, `gravar` lança e nada é escrito).
  - `tipoDivergenteParaSemGravar` (`Checklist Type` vem `string` e o YAML diz SELECT → `TIPO_DIVERGENTE`).
  - `ausenteListado` (`Model Name` ausente → `AUSENTE`).
  - `nuncaEscreveEmArquivoExemplo`; `saidaPadraoEhIgnoradaPeloGit` (o caminho padrão casa com um padrão do `.gitignore`).
  - `gravadoRecarregaComoDicionarioValido` (o arquivo gerado carrega pelo `FieldDictionary` estrito).
  - `ConferenciaDeStatusTest`: `statusDesconhecidoListado`, `categoriaQueContradizOMapaListada` (`Closed` com categoria `indeterminate`), `propostaCarregaComoStatusMap`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*CasadorDeCamposTest*' --tests '*ConferenciaDeStatusTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA e suíte completa. **Passo 5: Commit** `feat(validacao): casador de campos e conferência de status sobre respostas salvas`.

---

### Tarefa 3: Registro de divergências

**Arquivos:** Criar `validacao/RegistroDeDivergencias.kt`, `docs/validacao/registro-de-divergencias.yaml` (começa com `versao: 1` e `divergencias: []`); Teste `validacao/RegistroDeDivergenciasTest.kt`.

**Interfaces:**
- Formato (YAML estrito):
  ```yaml
  versao: 1
  divergencias:
    - id: DV-001                     # sequencial
      data: 2026-11-03               # dia de Manaus
      instancia: samsung
      card: fechadas                 # id da ficha ou "sincronizacao", "campos", "status"
      metrica: concluidas            # nome do campo do DTO
      painel: "41"                   # valor agregado
      referencia: "43"
      fonte: JIRA_TOTAL              # JIRA_TOTAL | LEGADO | GESTOR | CHECKLIST
      categoria: A_INVESTIGAR        # BUG_DO_PAINEL | DIFERENCA_ESPERADA | DADO_RUIM_NO_JIRA | A_INVESTIGAR
      acao: "conferir resolved < dia seguinte"
      estado: ABERTA                 # ABERTA | CORRIGIDA | ACEITA
  ```
- `object RegistroDeDivergencias { fun ler(path: Path): List<Divergencia>; fun validar(lista: List<Divergencia>) }`. `id` segue `DV-\d{3}` e é o único campo com esse formato. `validar` aplica as proibições **só aos campos de texto livre** (`acao`, `metrica`, `card`), depois de remover as citações `DV-\d{3}`: chave de issue (`[A-Z][A-Z0-9_]+-\d+`), `@`, `http`, `customfield_` e o padrão de username `[a-z]+\.[a-z]+`, **exceto** quando seguido de extensão de arquivo conhecida (`.yaml`, `.yml`, `.md`, `.kt`, `.json`, `.ts`, `.tsx`). Também recusa `painel`/`referencia` não numéricos (aceita `—` para "não calculado") e `CORRIGIDA` sem `acao`.
- Comparações por pessoa ou por issue ficam em `validacao-local/` (ignorado), nunca no registro versionado.

- [ ] **Passo 1: Testes que falham:** `registroVazioValido`, `registroSemDadosPessoais` (cada padrão proibido acima → erro citando só o `id` da linha), `idProprioECitacaoNaoSaoChaveDeIssue` (`id: DV-001` e `acao: "ver DV-002"` passam), `nomeDeArquivoNaoEhUsername` (`acao: "ajustar status_map.yaml"` passa; `acao: "falar com ana.silva"` falha), `categoriaEFonteFechadas`, `corrigidaExigeAcao`, `idsSequenciais`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*RegistroDeDivergenciasTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(validacao): registro de divergências só com agregados`.

---

### Tarefa 4: Checklist da primeira conexão, passo a passo (runbook)

**Arquivos:** Modificar `docs/jira/matriz-compatibilidade.md` (status de cada item), `docs/validacao/registro-de-divergencias.yaml`, `HANDOFF.md`; local (gerados pelas ferramentas da T2, ignorados pelo git): `app/config/jira_campos.yaml`, `app/config/status_map.yaml`. **A IA nunca edita arquivos de ambiente** (regra 3 do `CLAUDE.md`): variáveis como `SYNC_FUSO_SERVIDOR` o usuário define; a IA só informa o valor a usar.

**Pré-condição:** Tarefa 1, Passo 6 respondido com uma opção que liga a flag; Tarefas 2 e 3 na branch.

- [ ] **Passo 1 (checklist, Passo 1, `/myself`):** confirme `JIRA_LIVE_ENABLED=true` e rode. **Como conferir:** 200; `timeZone` anotado. Se `timeZone` ≠ America/Manaus, **Ponto de parada: perguntar ao usuário**: "Você (usuário) define `SYNC_FUSO_SERVIDOR=<timeZone lido>` no seu ambiente e reinicia (Recomendada): o JQL com hora fica no fuso que o Jira interpreta"; "Manter Manaus e aceitar a diferença (registrar como divergência `DIFERENCA_ESPERADA`)". Não salvar nome nem e-mail.
- [ ] **Passo 2 (checklist, Passo 2, `/field`):** salvar a resposta em `$HOME/painel-validacao/<data>/field.json`; rodar `casar-campos --instancia samsung --entrada <arquivo>`. **Como conferir:** relatório sem problemas → `app/config/jira_campos.yaml` gerado; com problemas → **parar** e levar ao usuário em pergunta com opções por problema ("ajustar o tipo no dicionário (Recomendada se o tipo real for o certo)", "escolher o campo pelo projeto", "remover o campo do dicionário"). Apagar `field.json` ao fim.
- [ ] **Passo 3 (checklist, Passo 3, uma busca por ficha com `maxResults=1`):** para cada ficha da `main`, mostrar o JQL gerado e anotar só o `total`. **Como conferir:** JQL aceito sem 400; `total` registrado no registro de divergências apenas quando o usuário informar o total esperado da tela do Jira e ele diferir (fonte `JIRA_TOTAL`).
- [ ] **Passo 4 (checklist, Passo 4, uma issue com changelog):** o usuário indica a chave. **Como conferir** e registrar na matriz (sem copiar dados): formato do `from/to` do assignee, `duedate` e `labels`, changelog truncado ou não. Cada "regra reversível" do checklist recebe **confirmada** ou **refutada**.
- [ ] **Passo 5 (checklist, Passo 5, `/status`):** salvar em `$HOME/painel-validacao/<data>/status.json`; `conferir-status`. **Como conferir:** sem desconhecidos e sem categoria divergente → copiar a proposta para `app/config/status_map.yaml` (local); senão parar e perguntar status a status. Apagar o arquivo.
- [ ] **Passo 6 (passo extra, ids de `papeis.yaml`):** **Ponto de parada: perguntar ao usuário**: "Conferir cada id pela API, um por vez (Recomendada)"; "O usuário confere a lista na tela do Jira e informa só quantos divergiram". Nenhum nome vai para o repositório.
- [ ] **Passo 7: Registrar** na matriz cada item como **confirmado em aaaa-mm-dd** ou **refutado em aaaa-mm-dd (ver DV-NNN)**; rodar `./gradlew test --tests '*PreparacaoDocsTest*' --tests '*RegistroDeDivergenciasTest*'` → PASSA. **Passo 8: Commit** `docs(jira): resultado da primeira conexão na matriz` (só matriz, registro e HANDOFF).

---

### Tarefa 5: Primeiro ciclo de sincronização (runbook)

**Arquivos:** Modificar `docs/validacao/registro-de-divergencias.yaml`, `HANDOFF.md`.

- [ ] **Passo 1: Etapa 1 do runbook da sincronização** (pequena): o usuário define `SYNC_MAX_ITENS=50` e `SYNC_MAX_REQUISICOES=5` no ambiente dele e sobe o painel (a IA informa os valores e o comando; não edita arquivos de ambiente). **Como conferir:** `GET /api/sync/status` como gerente mostra `PERIODICO`, uma execução `OK`, `incompletas = 0`, `usuariosNaoResolvidos = 0`; o card Tempo em status abre sem aviso bloqueante.
- [ ] **Passo 2: Quando parar** (flag `false`, reiniciar, relatar): `HTTP_401/403/429/5XX`, `RECONCILIACAO_SUSPEITA`, `usuariosNaoResolvidos > 0` sem explicação, `incompletas > 0`.
- [ ] **Passo 3: Etapa 2** (limites padrão) e **Etapa 3** (primeira reconciliação às 03:00 de Manaus). **Como conferir:** carga completa `OK`; reconciliação `OK` com `removidas` ≤ 50% da base.
- [ ] **Passo 4: Registrar** cada número inesperado como divergência (fonte `CHECKLIST`); **Passo 5: Commit** `docs(validacao): primeiro ciclo de sincronização registrado`.

---

### Tarefa 6: Regras reversíveis refutadas viram correção com teste

**Arquivos:** Depende do que a T4 refutar (cliente, `JqlBuilder`, `ChangelogParser`, `Syncer`); fixtures **sintéticas** que reproduzem o formato observado (nunca a resposta real); Teste no arquivo de teste da classe corrigida.

**Interfaces:** Para cada regra refutada na matriz (aspas em campo por nome, `resolved <` dia seguinte, `pageSize`, changelog truncado, formato do worklog, `from/to` do assignee, Reporter = PL):

- [ ] **Passo 1: Teste que falha** com fixture sintética no formato real observado (nome do teste: `formatoReal<Regra>`; ex.: `formatoRealAssigneeFromTo`).
- [ ] **Passo 2: Rodar** → FALHA. **Passo 3: Corrigir** o mínimo. **Passo 4: Rodar** → PASSA e suíte completa; marcar a divergência `CORRIGIDA` com a ação. **Passo 5: Commit** por regra: `fix(sync): <regra> conforme o Jira real`.
- [ ] Se **Reporter = PL** for refutado, **Ponto de parada: perguntar ao usuário**: "Manter o Reporter como PL e registrar a exceção (Recomendada se as exceções forem raras)"; "Criar um campo de PL por squad em `equipes.yaml` (vira tarefa do Plano 4 reaberta)"; "Desligar o sinal de auditoria que depende do PL até decidir".
- [ ] Nenhuma regra refutada: registrar `Ruling: nenhuma correção necessária` e seguir.

**Fim do 14a:** suíte completa, varredura de dados pessoais (`git diff main..HEAD` sem chave, username, host, `customfield` real), PR `feat(validacao): primeira conexão (14a)`. Mesclar pelo fluxo de entrega (`docs/superpowers/fluxo-de-entrega.md`).

---

## Bloco 14b — validação dos cards e calibração (depois dos Planos 5, 6 e 7)

### Tarefa 7: Recarga completa sem rede (campos novos dos Planos 5 e 6)

**Por quê:** as issues gravadas antes dos Planos 5 e 6 não têm prioridade, estimativas nem worklog (cobertura E14). O cursor do 4A é monotônico; é preciso zerá-lo para que o próximo incremental vire carga inicial.

**Arquivos:** Modificar `sync/SyncStateStore.kt` (`zerarCursor`), `validacao/ValidacaoCli.kt` (subcomando `recarga-completa <instancia>`); Teste `sync/SyncStateStoreTest.kt`, `validacao/RecargaCompletaTest.kt`, `sync/FlagDesligadaZeroRequisicoesTest.kt` (caso novo).

**Interfaces:**
- `fun zerarCursor(instance: String)` em `SyncStateStore` (grava `NULL`; único jeito de recuar o cursor). Roda **sob a mesma trava entre réplicas do 4A** usada pelo `SyncService`: com uma execução em andamento, recusa com `Ocupada` e não altera nada (sem isso, o `avancarCursor` em curso, com `GREATEST(NULL, x) = x`, restauraria o cursor). Não grava `sync_run` artificial: a recarga aparece no status pela execução seguinte, uma carga inicial.
- `recarga-completa` fala **só com o banco**; instância fora de `INSTANCIAS_CONHECIDAS` → erro sem efeito. No runbook, rodar com o agendador entre execuções (ou com o app parado).

- [ ] **Passo 1: Testes que falham:** `zerarCursorDeixaNulo`, `proximoIncrementalViraCargaInicial` (MockEngine: o JQL não tem `updated >=`), `recargaNaoApagaIssues`, `recargaNaoFazRequisicao` (contador do MockEngine em 0), `recargaRespeitaTravaEntreReplicas` (trava tomada por uma execução simulada → `Ocupada` e cursor intacto), `instanciaDesconhecidaFalha`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*SyncStateStoreTest*' --tests '*RecargaCompletaTest*' --tests '*FlagDesligadaZeroRequisicoesTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA e suíte completa. **Passo 5: Commit** `feat(sync): recarga completa sob comando, sem requisição`.
- [ ] **Passo 6 (runbook, com a flag ligada pelo usuário):** rodar `recarga-completa samsung`; **como conferir:** a próxima execução no status é carga inicial `OK`; o aviso `CAMPOS_DE_TRABALHO_INDISPONIVEIS` some de Abertas.

---

### Tarefa 8: Roteiro de validação por ficha

**Arquivos:** Criar `validacao/RoteiroDeValidacao.kt`, `docs/validacao/roteiro-de-validacao.md` (gerado e versionado); Teste `validacao/RoteiroDeValidacaoTest.kt`.

**Interfaces:**
- `object RoteiroDeValidacao { fun gerar(fichas: Map<String, CardSpec>, menu: List<String>): String }` → uma seção por ficha e por item do menu sem ficha (Central, Integridade, Auditoria), com: métricas a conferir (campos numéricos do DTO), **fonte de referência** e **tolerância**.
- Fontes, nesta ordem de preferência:
  1. **`JIRA_TOTAL`** para contagens que a ficha expressa como JQL: o `total` de uma busca `maxResults=1` (o JQL é mostrado antes) ou a contagem na tela do Jira informada pelo usuário.
  2. **`LEGADO`** para métricas calculadas (P50/P85, taxas, Monte Carlo): o app Python em `legacy/` (se ainda existir) ou na tag `legacy-final`, rodado localmente e só leitura, **depois do "sim" do usuário** (ver T9).
  3. **`GESTOR`**: números que o gestor já acompanha (planilha, relatório), informados por ele.
- Tolerâncias (valor padrão 25/10, reversível): contagens **exatas**; percentuais **± 1 pp**; percentis **± 0,5 dia útil**; Monte Carlo **± 1 semana** em P50/P85.
- Item sem fonte possível → linha "sem referência independente: só aceite do gestor".

- [ ] **Passo 1: Testes que falham:** `roteiroCobreCadaFicha`, `roteiroCobreItensDoMenuSemFicha`, `roteiroExigeFonteDeReferencia` (nenhuma métrica sem fonte nem a linha "só aceite do gestor"), `roteiroSemValorReal` (JQL com parâmetros de exemplo, sem username nem host), `toleranciasPadrao`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*RoteiroDeValidacaoTest*'` → FALHA. **Passo 3: Implementar** e gerar o documento. **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(validacao): roteiro de validação por ficha`.

---

### Tarefa 9: Validar os cards da instância Samsung (runbook)

**Arquivos:** Modificar `docs/validacao/registro-de-divergencias.yaml`, `HANDOFF.md`; local: `validacao-local/<data>/` (comparações por pessoa ou issue).

- [ ] **Passo 1: Ponto de parada: perguntar ao usuário** sobre a fonte `LEGADO` para as métricas calculadas (as contagens usam sempre `JIRA_TOTAL`): "Rodar o legado localmente (`legacy/`, ou a tag `legacy-final` num worktree separado), só leitura, só em `127.0.0.1`, com o webhook desligado e a mesma flag (Recomendada): é a referência independente das fórmulas do app atual"; "Não rodar o legado e usar números do gestor nas métricas calculadas: evita um segundo cliente no Jira, mas a referência fica menos completa"; "Usar só números do gestor em tudo que não for contagem".
- [ ] **Passo 2:** para cada seção do roteiro, como gerente: obter o número do painel (rota do card com período explícito), obter a referência, comparar com a tolerância. Dentro da tolerância → nada a registrar; fora → divergência (`A_INVESTIGAR`).
- [ ] **Passo 3: Classificar** cada divergência: `BUG_DO_PAINEL` (vira correção TDD com fixture sintética, como na T6), `DIFERENCA_ESPERADA` (acrescentar ao `docs/analise/diferencas-esperadas-*.md` do card), `DADO_RUIM_NO_JIRA` (vai à Qualidade dos dados; nada a corrigir no painel).
- [ ] **Passo 4: Conferir** a ordem e a consistência entre telas com dados reais: o mesmo "no prazo" em Auditoria, Fechadas e Metas (regra única do Plano 5, T7) e o tamanho do drill igual ao número do card em 5 cards sorteados.
- [ ] **Passo 5: Commit** `docs(validacao): validação dos cards Samsung registrada` (+ commits `fix(...)` de cada `BUG_DO_PAINEL`).

---

### Tarefa 10: Cards da instância Sidia (projeto CAT)

**Arquivos:** Depende da decisão: `app/config/cards/<id>-sidia.card.yaml`, `config/Settings.kt` (nada se `SYNC_INSTANCIAS` já aceitar `sidia`), `docs/validacao/registro-de-divergencias.yaml`; Teste `jql/CardQueryFactoryTest.kt` (snapshot de cada ficha nova), `cards/<Card>RoutesTest.kt` (caso `instancia=sidia`).

- [ ] **Passo 1: Ponto de parada: perguntar ao usuário** quais cards a Sidia terá:
  - "Reusar cards existentes (Abertas, Fechadas, Tempo em status) com fichas `instancia: sidia`, projeto CAT, só para coordenador e gerente até existir `jira_sidia` (Recomendada)": sem calculador novo; consequência: a visão por pessoa na Sidia fica para quando os usernames forem mapeados.
  - "Definir cards próprios da automação": consequência: vira um plano novo (próximo número livre no índice), escrito depois da conversa com o usuário; esta tarefa só registra o pedido.
  - "Não usar a Sidia agora": consequência: tarefa encerrada com `Ruling:` e pendência no `HANDOFF.md`.
- [ ] **Passo 2 (se a primeira opção):** fichas novas com teste de snapshot do JQL (`project in ("CAT")`, tipos informados pelo usuário); rotas aceitam `instancia=sidia` só para COORDENADOR e GERENTE (`sidiaSoParaCoordenacaoEGerente`); `SYNC_INSTANCIAS=samsung,sidia`.
- [ ] **Passo 3 (runbook):** repetir os Passos 1, 2 e 5 do checklist na instância Sidia (Basic, usuário e token) e a validação da T9 para os cards escolhidos.
- [ ] **Passo 4: Commit** `feat(cards): cards da instância Sidia` (ou `docs(validacao): decisão sobre a instância Sidia`).

---

### Tarefa 11: Ferramenta de calibração (retroteste offline)

**Arquivos:** Criar `validacao/Calibracao.kt`, subcomando `calibrar <heuristica>` em `ValidacaoCli.kt`; Modificar `.gitignore` (`**/config/*.proposta.yaml` e o nome real de cada YAML de heurística: `**/config/{risco,previsao,metas,planejamento,esforco,integridade,distribuicao,checklist_review,auditoria,labels_atraso}.yaml`); Teste `validacao/CalibracaoTest.kt` (dados sintéticos com a resposta certa conhecida).

**Interfaces:**
- Produz:
  ```kotlin
  enum class Heuristica { RISCO, PREVISAO, INTEGRIDADE, DISTRIBUICAO, ESFORCO, PLANEJAMENTO, CHECKLIST_REVIEW, NEGLIGENCIADAS }
  data class Metrica(val nome: String, val atual: Double, val proposta: Double)        // ex.: precisão do "risco alto"
  data class PropostaDeCalibracao(val heuristica: Heuristica, val versaoAtual: Int, val versaoProposta: Int,
      val parametros: Map<String, Pair<Double, Double>> /* nome → (atual, proposto) */, val metricas: List<Metrica>, val amostra: Int)
  object Calibracao {
      fun propor(h: Heuristica, snapshots: List<IssueSnapshot>, ajuste: ClosedRange<LocalDate>, avaliacao: ClosedRange<LocalDate>, atual: Path): PropostaDeCalibracao
      fun gravarProposta(p: PropostaDeCalibracao, destino: Path)   // escreve <nome>.proposta.yaml; nunca sobrescreve o atual
  }
  ```
- Método (valor padrão 25/10, reversível): **ajusta numa janela e avalia na janela seguinte** (padrão: ajuste = 120 dias até 30 dias atrás; avaliação = últimos 30 dias). Grade simples sobre os parâmetros declarados no YAML (passo de 10% em torno do atual, no máximo 3 parâmetros por vez). Métricas: RISCO → precisão e cobertura do "alto" contra "concluída com atraso ou ainda vencida"; PREVISAO → erro do P85 do Monte Carlo contra o throughput real; INTEGRIDADE/CHECKLIST_REVIEW/NEGLIGENCIADAS → quantos alertas por semana (meta: não inundar; limiar definido pelo gestor); DISTRIBUICAO/PLANEJAMENTO/ESFORCO → correlação com horas registradas (worklog).
- Amostra menor que `Fluxo.AMOSTRA_MINIMA` × 10 → proposta vazia com aviso `AMOSTRA_INSUFICIENTE`.
- A proposta guarda só parâmetros e métricas agregadas.

- [ ] **Passo 1: Testes que falham:** `propostasEReaisIgnoradosPeloGit` (cada padrão acima está no `.gitignore`), `recuperaPesoConhecido` (dados sintéticos gerados com peso 2,0 → proposta 2,0 ± 10%), `avaliaEmJanelaSeparada` (nenhuma issue da avaliação entra no ajuste), `propostaIncrementaVersao`, `propostaNaoSobrescreve` (o YAML atual fica byte a byte igual), `amostraInsuficienteNaoPropoe`, `propostaSemDadosPessoais`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*CalibracaoTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(validacao): calibração das heurísticas por retroteste`.

---

### Tarefa 12: Calibrar com dados reais e aplicar (runbook)

**Arquivos:** Criar `docs/validacao/calibracao.md` (antes × depois, só agregados); local: `<heuristica>.proposta.yaml`, gerado no diretório de configuração em uso. Em produção, a configuração real fica no volume montado (Plano 13) e só muda pelo procedimento de edição de configuração do Plano 12 (validador antes de subir; confirmar o nome).

- [ ] **Passo 1:** rodar `calibrar` para cada heurística, na ordem RISCO, PREVISAO, NEGLIGENCIADAS, PLANEJAMENTO, ESFORCO, DISTRIBUICAO, CHECKLIST_REVIEW, INTEGRIDADE.
- [ ] **Passo 2: Ponto de parada: perguntar ao usuário** (uma pergunta por heurística, com a tabela antes × depois): "Aplicar a proposta (Recomendada quando a métrica de avaliação melhora e a amostra é suficiente)"; "Aplicar com ajuste manual (indicar o valor)"; "Manter os valores atuais". Aplicar = levar a proposta ao YAML real da instalação **pelo procedimento do Plano 12** (validador, depois reinício ou recarga documentada), com `versao` nova; o painel mostra a versão em toda resposta. A IA prepara a proposta; quem aplica em produção é quem o procedimento do Plano 12 indicar.
- [ ] **Passo 3: INTEGRIDADE e AUDITORIA** só com a trava do Plano 11 liberada. **Ponto de parada: perguntar ao usuário** se o DPO já deu o aceite: "Aguardar o aceite e registrar a pendência (Recomendada)"; "O DPO já aceitou: anexar a data no `HANDOFF.md` e liberar a trava". Nunca pular em silêncio.
- [ ] **Passo 4: Commit** `docs(validacao): calibração com dados reais registrada`.

---

### Tarefa 13: Aceite do gestor card a card

**Arquivos:** Criar `docs/validacao/aceite-do-gestor.md`; Teste `validacao/AceiteDocTest.kt`.

**Interfaces:** Uma linha por item do menu: card, data da validação, divergências abertas (ids `DV-NNN`), versão das regras, **aceite** (`ACEITO`, `ACEITO_COM_RESSALVA`, `PENDENTE`) e data. Sem nomes de pessoas (o papel "gestor" basta).

- [ ] **Passo 1: Testes que falham:** `aceiteCobreCadaItemDoMenu` (lê os ids do `nav.ts`), `aceiteSoComValoresFechados`, `aceiteCitaDivergenciasExistentes` (cada `DV-NNN` citado existe no registro), `aceiteSemNomes`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*AceiteDocTest*'` → FALHA. **Passo 3: Preencher** com o que já se sabe (`PENDENTE`). **Passo 4: Rodar** → PASSA.
- [ ] **Passo 5: Ponto de parada: perguntar ao usuário** (pode ser em blocos de cards, na ordem do menu): "Aceitar os cards sem divergência aberta (Recomendada)"; "Aceitar com ressalva (indicar qual)"; "Não aceitar (indicar o motivo; vira divergência)". Registrar resposta e data.
- [ ] **Passo 6: Commit** `docs(validacao): aceite do gestor por card`.

---

### Tarefa 14: Fechamento da validação

**Arquivos:** Modificar `docs/jira/matriz-compatibilidade.md` (estado final), `PRODUCT.md` (princípio 4: o que já foi validado), `HANDOFF.md`, `docs/superpowers/plans/2026-10-24-indice-dos-planos.md` (estado da fase 14).

- [ ] **Passo 1: Conferir** `git status` (nenhum arquivo de config real, nada de `validacao-local/`); apagar `$HOME/painel-validacao/` e registrar a remoção.
- [ ] **Passo 2: Rodar** as suítes completas (backend e frontend), `pnpm build`, `sh .claude/hooks/test-guard.sh` → PASSA.
- [ ] **Passo 3: Varredura** de dados pessoais e hosts em `git diff main..HEAD`.
- [ ] **Passo 4: Commit** `docs(validacao): fechamento da validação com dados reais`; PR do bloco 14b. Mesclar pelo fluxo de entrega (`docs/superpowers/fluxo-de-entrega.md`).

---

## Autoavaliação

**Cobertura:**

| Lacuna ou requisito | Tarefas |
|---|---|
| C2: configs reais, contagem por ficha, rulings reversíveis, matriz, aceite | T2, T4, T5, T6, T8, T9, T13 |
| A6: cards da instância Sidia | T10 |
| M6: calibração das heurísticas | T11, T12 |
| E14: ressincronização depois dos Planos 5 e 6 | T7 |
| Spec 4.6: oráculo de números | T8, T9 |
| Spec 11: ids de campo, Reporter como PL | T2, T4, T6 |
| PRODUCT.md, princípio 4 | T13, T14 |

**Consistência de tipos:** `CasadorDeCampos` produz um arquivo que o `FieldDictionary` estrito carrega (T2); `RegistroDeDivergencias` é a única fonte dos `DV-NNN` citados no aceite (T3, T13); `zerarCursor` (T7) só acrescenta um método ao `SyncStateStore` do 4A; `Calibracao` lê `IssueSnapshot` (Plano 5) e escreve só `*.proposta.yaml`.

**Pendências deliberadas:** quem liga a flag e quando (usuário); cards próprios da automação Sidia, se escolhidos (plano futuro); aceite do DPO para Auditoria e Integridade (Plano 11).

## Decisões (25/10, valor padrão recomendado, reversível)

1. **Dividir em 14a (logo após o 4A) e 14b (após 5, 6 e 7)**, sem migração no 14a.
2. **Respostas brutas fora do repositório e apagadas ao fim**; registros versionados só com agregados.
3. **Fontes de referência:** contagens por `JIRA_TOTAL`; métricas calculadas por `LEGADO` rodado localmente (só com "sim") ou, sem ele, `GESTOR` (T8, T9).
7. **A IA nunca edita arquivos de ambiente:** variáveis (`SYNC_*`, fuso) o usuário define; a IA informa o valor (T4, T5).
4. **Tolerâncias:** contagem exata; percentual ± 1 pp; percentil ± 0,5 dia útil; Monte Carlo ± 1 semana (T8).
5. **Calibração com janela de ajuste e janela de avaliação separadas**, grade de ± 10%, no máximo 3 parâmetros por vez (T11).
6. **Recarga completa = zerar o cursor**, sem migração, pelo banco (T7).

Decisões que **dependem do usuário** (pontos de parada): ligar a flag (T1); fuso do JQL, problemas de campo e de status, ids de `papeis.yaml` (T4); Reporter = PL refutado (T6); fonte `LEGADO` (T9); cards da Sidia (T10); aplicar cada calibração e aceite do DPO (T12); aceite do gestor (T13).
