# Spec da Fase 14: Primeira conexão, validação com dados reais e calibração

Data: 25/10/2026
Plano: [`docs/superpowers/plans/2026-10-25-plano-14-primeira-conexao-validacao-e-calibracao.md`](../plans/2026-10-25-plano-14-primeira-conexao-validacao-e-calibracao.md) (14 tarefas em dois blocos e dois PRs: **14a**, Tarefas 1 a 6; **14b**, Tarefas 7 a 14)
Spec mestra: [`2026-10-24-painel-squads-kotlin-react-design.md`](2026-10-24-painel-squads-kotlin-react-design.md) (seções 3, 4.2 "Heurísticas", 4.6 "Oráculo de números", 5.5, 9, 10, 11)
Depende de: **14a** — Plano 4A na `main` e a flag ligada **pelo usuário**; **14b** — Planos 5, 6 e 7 na `main` (e o Plano 11 para validar Auditoria e Integridade com dados reais)
Lacunas de origem: [`cobertura-do-projeto.md`](../cobertura-do-projeto.md), C2, A6, M6 e E14

> **Precedência:** `CLAUDE.md` > `docs/guia-execucao-outra-ia.md` > esta spec > o plano. Se a regra 1 do `CLAUDE.md` na `main` ainda exigir autorização por pesquisa, vale o `CLAUDE.md`: perguntar ao usuário antes de cada consulta.

## 1. Intenção e porquê

Tudo o que o painel calcula foi provado com fixtures. O `PRODUCT.md` (princípio 4) proíbe afirmar qualquer coisa sobre o Jira real antes de validar, e a spec mestra (seção 11) lista riscos que só a primeira conexão resolve: ids dos campos, formato do changelog, fuso do JQL, Reporter como PL. As heurísticas (risco, integridade, distribuição, esforço) foram herdadas sem base e a spec manda calibrá-las "depois, com dados reais".

Esta fase faz a primeira conexão com cuidado, preenche as configurações reais (locais, fora do git), valida cada card contra uma referência independente, decide os cards da Sidia, calibra as heurísticas e registra cada divergência com categoria e ação. Ao final, o gestor aceita card a card.

A fase começa em duas velocidades. A sincronização depende só do 4A, por isso o **bloco 14a** pode começar logo depois dele, assim que o usuário ligar a flag. A validação dos números depende dos cards, por isso o **bloco 14b** vem depois do Plano 7.

## 2. Objetivos, não objetivos e antiobjetivos

### Objetivos

- Primeira conexão pelo checklist, na ordem, com parada no primeiro erro.
- `jira_campos.yaml` e `status_map.yaml` reais gerados por ferramenta, sem edição manual de ids.
- Primeiro ciclo de sincronização e primeira reconciliação conferidos.
- Cada regra reversível do checklist marcada como confirmada ou refutada; as refutadas viram correção com teste.
- Recarga completa depois dos Planos 5 e 6, sem requisição extra.
- Validação de cada card contra `JIRA_TOTAL`, `LEGADO` (se o usuário aceitar) ou `GESTOR`, com tolerâncias fixas.
- Decisão e validação dos cards da Sidia (CAT).
- Calibração das heurísticas por retroteste, aplicada só com aprovação.
- Registro de divergências e aceite do gestor por card.

### Não objetivos

- Ligar a flag (é do usuário).
- Novos cards além dos da Sidia que o usuário escolher reusar.
- Escrita no Jira.
- Liberar Auditoria e Integridade sem o aceite do DPO.

### Antiobjetivos (falha mesmo que "funcione")

- A IA ligar a flag, ou acessar o Jira com a flag `false`.
- Teste automático tocando o Jira real.
- Resposta bruta, `customId` real, chave de issue, username, host ou JQL com valor real no repositório.
- `customId` gravado num arquivo `.exemplo`.
- Insistir depois de 401, 403, 429, 5xx ou CAPTCHA.
- Validar o painel contra ele mesmo (sem fonte independente).
- Calibração que sobrescreve a configuração em uso sem aprovação, ou que avalia na mesma amostra em que ajustou.
- Ajustar JQL ou fórmula "em silêncio" para bater um número.

## 3. Restrições

### 3.1 Regras transversais do projeto (valem sempre)

**Acesso ao Jira.** A única trava é `JIRA_LIVE_ENABLED` (regra 1 do `CLAUDE.md`). `false`: ninguém acessa o Jira. `true`: acesso liberado, seguindo o checklist na primeira conexão. **Só o usuário muda a flag; a IA pergunta e espera.**

**Visibilidade por papel.** USUARIO vê o seu; SQUAD_LEADER, o seu squad; COORDENADOR e GERENTE, todos. Validações rodam como gerente.

**Sem ranking de pessoas.** Comparações por pessoa ficam locais (`validacao-local/`), nunca em documento versionado.

**Concluída** = categoria `done` e `resolution`. **Atraso justificado:** "no prazo" e "no prazo ajustado" lado a lado, pela regra única do Plano 5, T7. **Fuso** America/Manaus; **dias úteis** por padrão.

**Configs reais** fora do git e da imagem. **DPO** antes de Auditoria e Integridade com dados reais.

**Ambiente.** Sem Docker; banco SQLite real em arquivo temporário (`testContextComBanco`/`BancoEmArquivo`); comentários e commits em pt-BR.

### 3.2 Restrições específicas desta fase

- Respostas do Jira em `$HOME/painel-validacao/<data>/` (fora do repositório, modo `700`), apagadas ao fim de cada tarefa.
- Registros versionados só com agregados e datas.
- Sem migração no 14a (corre em paralelo com a sequência principal).
- Uma instância por vez; Samsung DC primeiro, Sidia só na Tarefa 10.

## 4. Decisões já tomadas

| # | Decisão | Origem |
|---|---|---|
| U1 | A flag é a única trava; só o usuário a muda | [usuário] `CLAUDE.md`, 25/10 |
| U2 | Primeira conexão segue o checklist como procedimento cuidadoso (ordem, limites, parada no primeiro erro) | [usuário] Plano 4A, decisões 1 e 10 |
| U3 | Fuso do JQL: Manaus até ler o `timeZone` do token | [usuário] Plano 4A, decisão 3 |
| U4 | Heurísticas em YAML versionado; calibração depois, com dados reais | [usuário] spec mestra 4.2 |
| U5 | Sidia: projeto CAT, cards próprios da automação; cada card declara a instância | [usuário] spec mestra 3 |
| D1 | Dois blocos: 14a após o 4A, 14b após o 7 | [valor padrão 25/10, reversível] |
| D2 | Fontes de referência: `JIRA_TOTAL`, depois `LEGADO` (só com "sim"), depois `GESTOR` | [valor padrão 25/10, reversível] |
| D3 | Tolerâncias: contagem exata; percentual ± 1 pp; percentil ± 0,5 dia útil; Monte Carlo ± 1 semana | [valor padrão 25/10, reversível] |
| D4 | Calibração: janela de ajuste e de avaliação separadas; grade ± 10%; até 3 parâmetros por vez | [valor padrão 25/10, reversível] |
| D5 | Recarga completa = zerar o cursor pelo banco, sem migração | [valor padrão 25/10, reversível] |
| D6 | Categorias de divergência: `BUG_DO_PAINEL`, `DIFERENCA_ESPERADA`, `DADO_RUIM_NO_JIRA`, `A_INVESTIGAR` | [valor padrão 25/10, reversível] |

## 5. O que fica para quem implementa

- Mapeamento exato de `schema.custom` do Jira para `FieldType` além dos casos do plano (registrar `Ruling:`).
- Forma do subcomando da linha de comando (reusar o mecanismo do Plano 12 se existir).
- Parâmetros exatos de cada heurística que entram na grade (os declarados no YAML de cada uma).
- Texto dos documentos de validação, desde que passem nos testes.
- Escolhas em aberto: `Ruling:` em `docs/superpowers/progresso/2026-10-25-plano-14-primeira-conexao-validacao-e-calibracao.md`.

## 6. Critérios de aceitação verificáveis

1. **Roteiro (T1).** `RoteiroDocTest` passa; a resposta do usuário sobre a flag está registrada com data.
2. **Ferramentas (T2).** `CasadorDeCamposTest` e `ConferenciaDeStatusTest` passam, incluindo `nomeDuplicadoParaSemGravar`, `tipoDivergenteParaSemGravar`, `nuncaEscreveEmArquivoExemplo` e `saidaPadraoEhIgnoradaPeloGit`.
3. **Registro (T3).** `RegistroDeDivergenciasTest.registroSemDadosPessoais` passa.
4. **Primeira conexão (T4, T5).** A matriz traz cada item como confirmado ou refutado com data; o status da sincronização mostra execução `OK`, `incompletas = 0` e `usuariosNaoResolvidos = 0` (ou divergência registrada); `git status` sem config real.
5. **Correções (T6).** Cada regra refutada tem um teste `formatoReal<Regra>` verde e a divergência `CORRIGIDA`.
6. **Recarga (T7).** `recargaNaoFazRequisicao`, `recargaNaoApagaIssues`, `recargaRespeitaTravaEntreReplicas` e `proximoIncrementalViraCargaInicial` passam.
7. **Roteiro de validação (T8).** `roteiroCobreCadaFicha`, `roteiroCobreItensDoMenuSemFicha` e `roteiroExigeFonteDeReferencia` passam.
8. **Validação (T9, T10).** Toda métrica do roteiro está dentro da tolerância ou tem uma divergência classificada.
9. **Calibração (T11, T12).** `CalibracaoTest` passa (`recuperaPesoConhecido`, `avaliaEmJanelaSeparada`, `propostaNaoSobrescreve`); `docs/validacao/calibracao.md` registra antes × depois e a resposta do usuário por heurística.
10. **Aceite (T13).** `AceiteDocTest` passa; cada item do menu tem aceite ou `PENDENTE` com motivo.
11. **Fim (T14).** Suítes completas, `pnpm build` e `test-guard.sh` verdes; `$HOME/painel-validacao/` apagado; PR sem dado pessoal.

## 7. Riscos, pendências e pontos de parada

**Pontos de parada (perguntar ao usuário, com opções; a recomendada primeiro):**
- **Ligar a flag** (T1): o usuário liga e a IA segue o checklist (Recomendada); o usuário liga e roda as consultas; ainda não.
- **Fuso diferente de Manaus** (T4): usar o `timeZone` do token (Recomendada); manter Manaus.
- **Problemas de campo ou de status** (T4): uma pergunta por problema.
- **Ids de `papeis.yaml`** (T4): pela API, um por vez (Recomendada); o usuário confere na tela.
- **Reporter = PL refutado** (T6): manter com exceção (Recomendada se raras); PL por squad; desligar o sinal.
- **Fonte `LEGADO`** (T9, só métricas calculadas; contagens sempre por `JIRA_TOTAL`): rodar o legado localmente, só leitura, em `127.0.0.1`, webhook desligado (Recomendada); não rodar e usar números do gestor; só gestor.
- **Cards da Sidia** (T10): reusar cards com fichas CAT para coordenação e gerência (Recomendada); cards próprios (plano novo, próximo número livre); não usar agora.
- **Aplicar cada calibração** (T12): aplicar (Recomendada se melhora e há amostra); ajustar; manter.
- **Aceite do DPO** (T12): aguardar (Recomendada); já aceito, com data.
- **Aceite do gestor** (T13): aceitar; com ressalva; não aceitar.

**Riscos:**
- Changelog truncado ou formato de `assignee` diferente do previsto. Mitigação: T4 detecta, T6 corrige com teste.
- Usernames diferentes entre Samsung e Sidia. Mitigação: Sidia só para coordenação e gerência até `jira_sidia`.
- Carga no Jira na carga inicial. Mitigação: limites pequenos na Etapa 1, orçamento de requisições do 4A, parada no primeiro 429.
- Repositório possivelmente visível a terceiros (cobertura A8). Mitigação: só agregados no repositório.

## 8. Glossário do que esta fase consome

| Nome | Arquivo | Origem |
|---|---|---|
| `checklist-primeira-conexao.md`, `cadaPassoDoChecklistConfirmaAFlag` | `docs/jira/`, `jql/PreparacaoDocsTest.kt` | [Plano 4A] T11 |
| `runbook-sincronizacao.md`, `SyncSettings`, `SyncStateStore`, `SyncService`, `/api/sync/status` | `docs/jira/`, `sync/` | [Plano 4A] T1, T2, T5, T8, T9 |
| `FieldDictionary`, `FieldType`, `CardQueryFactory`, `ctx.fichas` | `jql/`, `AppContext.kt` | [main] Plano 2; [Plano 4A] T3 |
| `StatusMap` (`ordem`, `resolucoesNegativas`) | `domain/StatusMap.kt` | [Plano 5] T1 |
| `IssueSnapshot`, `Fluxo.AMOSTRA_MINIMA`, `NoPrazo.noUniverso` | `trabalho/`, `domain/` | [Plano 5] T2, T3, T7 |
| YAML de heurística (`risco`, `previsao`, `metas`, `planejamento`, `esforco`, `integridade`, `distribuicao`, `checklist_review`, `auditoria`) | `app/config/*.exemplo.yaml` | [Planos 4 a 7] |
| Trava de LGPD | backend | [Plano 11] (confirmar o nome) |
| Validador de configuração e subcomandos | backend | [Plano 12] (confirmar nomes) |
| Tag `legacy-final` | git | [Plano 8] T9 |

## 9. Referência ao plano

Execute [`2026-10-25-plano-14-primeira-conexao-validacao-e-calibracao.md`](../plans/2026-10-25-plano-14-primeira-conexao-validacao-e-calibracao.md) com TDD nas tarefas de código, conforme `docs/guia-execucao-outra-ia.md` (seções 4 a 8). As tarefas de runbook só rodam com a flag ligada pelo usuário e param no primeiro erro. O bloco 14a é um PR; o 14b, outro.
