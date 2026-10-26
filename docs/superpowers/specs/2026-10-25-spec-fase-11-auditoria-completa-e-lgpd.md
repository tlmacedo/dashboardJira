# Spec da Fase 11: Auditoria completa e LGPD

Data: 25/10/2026
Plano: [`docs/superpowers/plans/2026-10-25-plano-11-auditoria-completa-e-lgpd.md`](../plans/2026-10-25-plano-11-auditoria-completa-e-lgpd.md) (12 tarefas, um PR)
Spec mestra: [`2026-10-24-painel-squads-kotlin-react-design.md`](2026-10-24-painel-squads-kotlin-react-design.md) (seções 3, 4.6, 7.1, 7.2, 7.3, 10, 11)
Auditoria: [`docs/superpowers/cobertura-do-projeto.md`](../cobertura-do-projeto.md) (lacunas C3 e C4)
Depende de: Planos 4A, 9, 4, 5, 6, 7 e 10 mesclados na `main`. Roda **antes** do Plano 8.

> **Precedência:** `CLAUDE.md` > `docs/guia-execucao-outra-ia.md` > esta spec > o plano > spec mestra de 24/10. A spec mestra ainda traz a regra antiga de acesso ao Jira, substituída em 25/10 pela flag única.

## 1. Intenção e porquê

Duas coisas impedem chamar a auditoria de completa e o painel de pronto para dados reais.

**A seção 7.2 da spec mestra tem 12 sinais, e só 11 estão no Plano 4.** "Estimativa reduzida ou worklog retroativo" ficou fora por falta de dado: o `change_event` não guarda o valor das estimativas, e o worklog não guarda quando foi registrado. O critério de sucesso 10 ("a auditoria detecta cada sinal da seção 7") fica sem cumprir. O sinal `ESTIMATIVA_AUMENTADA` da Integridade depende do mesmo dado e está inativo. Guardar esse dado é ampliar o que o painel trata sobre pessoas, então quem decide é o usuário.

**A spec manda validar a base legal com o DPO "antes do uso real"**, e hoje só um aviso na tela separa a Auditoria e a Integridade do uso real. Se alguém ligar a flag do Jira e subir em produção, as duas telas mostram sinais sobre pessoas sem nenhum parecer. Esta fase põe uma trava em código, fail-closed, que só o aceite do DPO abre. Ela também produz o que o DPO precisa para decidir: o inventário dos dados tratados, ligado ao esquema real; a retenção por categoria; o atendimento ao titular; e um relatório com as perguntas em aberto, cada uma com opções.

## 2. Objetivos, não objetivos e antiobjetivos

### Objetivos

- **Trava do DPO:** Auditoria e Integridade (rotas, drills, itens da Central e histórico da gaveta) ficam bloqueadas fora do modo dev offline até `DASHBOARD_DPO_ACEITE=true` com uma referência do parecer. A decisão sai só da configuração.
- **Sinais novos**, conforme a decisão do usuário: `ESTIMATIVA_REDUZIDA` e `WORKLOG_RETROATIVO` na Auditoria, e `ESTIMATIVA_AUMENTADA` ativo na Integridade.
- **Inventário dos dados tratados:** uma linha por coluna do banco, com teste que falha quando aparece coluna nova sem inventário.
- **Retenção final configurável por categoria:** histórico, registro de acesso, decisões e execuções. Inclui a tabela de decisões, que hoje não tem retenção.
- **Direitos do titular:** consulta e download dos próprios dados, para todo papel; correção e eliminação pelo canal do DPO.
- **Documentos para o DPO e a equipe:** relatório para o DPO, atendimento ao titular e comunicação à equipe.
- **Minimização:** remover a tabela `person` se ela não tiver uso.

### Não objetivos

- Dar o parecer do DPO ou ligar o aceite. Isso cabe ao DPO e ao usuário.
- Afirmar uma base legal. Os documentos apresentam candidatas como perguntas.
- Liberar Auditoria e Integridade separadamente (pergunta 6 ao DPO).
- Mostrar ao titular o conteúdo dos sinais sobre ele (pergunta 4 ao DPO).
- Runbook de incidentes (Plano 12).
- Sincronizar de novo agora: a nova carga só acontece quando o usuário ligar a flag do Jira.

### Antiobjetivos (falha mesmo que "funcione")

- Uma trava que olha o conteúdo do banco para decidir. Quebraria os testes dos Planos 4 e 7 e seria imprevisível.
- Uma trava que libera em modo dev com a flag do Jira ligada, quando há dados reais sem login.
- Uma IA ligando `DASHBOARD_DPO_ACEITE=true` em arquivo, compose ou comando.
- Uma rota sensível nova (de qualquer plano) que escape da trava.
- Guardar texto livre no `change_event` ou o comentário do worklog.
- A rota do titular aceitar um parâmetro que escolha outra pessoa.
- Documento que afirma "a base legal é…", ou que contém nome, host, token ou e-mail real.

## 3. Restrições

### 3.1 Regras transversais do projeto (valem sempre)

**Acesso ao Jira.** A única trava de acesso ao Jira é a flag `JIRA_LIVE_ENABLED` (regra 1 do `CLAUDE.md`, 25/10). A trava do DPO é outra coisa: decide só se as telas sensíveis mostram dados e não liga nem desliga a sincronização. Testes usam fixtures e `MockEngine`.

**Indício, não prova.** Aviso fixo em toda tela e resposta de Auditoria e Integridade. Integridade só para o gerente; Auditoria para coordenação e gerente; toda consulta registrada.

**Quem pode alterar (spec 7.1).** Reporter (PL), coordenação e gerente, ou Squad Leader do responsável, com os papéis vigentes na data civil de Manaus da alteração.

**Sem ranking de pessoas.** Listas de pessoas em ordem alfabética; nenhuma contagem por pessoa nos sinais da Auditoria.

**Dados pessoais.** Só o que o cálculo precisa; nada de `displayName`, e-mail, avatar, texto livre. Fixtures e documentos com nomes fictícios.

**Fail-closed.** Configuração obrigatória ausente ou inválida derruba a subida.

**Ambiente.** Docker ativo; banco real por Testcontainers; comentários e commits em pt-BR.

### 3.2 Restrições específicas desta fase

- **Variáveis novas:** `DASHBOARD_DPO_ACEITE`, `DASHBOARD_DPO_ACEITE_REF`, `RETENTION_HISTORICO_MONTHS`, `RETENTION_ACCESS_LOG_MONTHS`, `RETENTION_DECISOES_MONTHS`, `RETENTION_EXECUCOES_MONTHS`. Nenhuma começa com `JIRA_`.
- **Uma migração** no máximo, `V{n}__dados_para_auditoria_completa.sql` (esperado V11). O conteúdo segue a resposta do ponto de parada 1.
- **Estimativas** são guardadas como segundos brutos (`from`/`to` numéricos), nunca o texto humanizado.
- **Worklog** ganha só a data de criação; continua sem comentário.

## 4. Decisões já tomadas

| # | Decisão | Origem |
|---|---|---|
| U1 | A flag `JIRA_LIVE_ENABLED` é a única trava de acesso ao Jira | [usuário, 25/10] |
| U2 | Retenção padrão de 24 meses (`RETENTION_MONTHS`), inclusive `access_log` e `issue` resolvida antiga | [usuário, 25/10, Plano 4A] |
| U3 | "Estimativa e worklog fora" do Plano 4 (decisão 7), agora reaberta pelo ponto de parada 1 | [usuário, 25/10] |
| U4 | Integridade só para o gerente; Auditoria para coordenação e gerente; indício, não prova | [usuário, spec mestra 7.3] |
| D1 | **Trava do DPO:** liberada se `DASHBOARD_DPO_ACEITE=true` (com `DASHBOARD_DPO_ACEITE_REF` válida) **ou** se `DASHBOARD_DEV_MODE=true` e `JIRA_LIVE_ENABLED=false`; senão bloqueada. Decidida só pelo `Settings` | [valor padrão 25/10, reversível] |
| D2 | **Alcance da trava:** AUDITORIA = `/api/auditoria/*`, `/api/drill/auditoria`, `/api/cards/task-review`, `/api/drill/task-review`, histórico da gaveta, itens `ALTERACOES_SEM_AUTORIZACAO` e `TASK_REVIEW_DIVERGENTE` da Central; INTEGRIDADE = `/api/integridade*`, `/api/drill/integridade`, item `INDICIO_ALTO` | [valor padrão 25/10, reversível] |
| D3 | **Resposta da trava:** 403 `ErroDto("AGUARDANDO_ACEITE_DPO", {modulo})`, depois da checagem de papel, registrada em `access_log`; na tela, o item fica visível com `⏸` "aguardando aceite do DPO" | [valor padrão 25/10, reversível] |
| D4 | **`ESTIMATIVA_REDUZIDA`:** redução ≥ 20% (`estimativaReducaoMinima`) da estimativa original depois da 1ª entrada em andamento; autor autorizado (7.1) → alerta; demais → auditoria; gravidade 2 | [valor padrão 25/10, reversível] |
| D5 | **`WORKLOG_RETROATIVO`:** ≥ 2 dias úteis de Manaus (`worklogRetroativoDiasUteis`) entre o início declarado e a criação, ou criação depois da resolução com início antes dela; sempre auditoria; gravidade 2 | [valor padrão 25/10, reversível] |
| D6 | **Regravar eventos antigos:** `ON CONFLICT ... DO UPDATE` só para os campos de estimativa ainda sem valor; com a opção A, a migração zera o cursor para a próxima carga refazer o changelog | [valor padrão 25/10, reversível; depende do ponto de parada 1] |
| D7 | **Retenção por categoria**, cada uma com variável própria (1 a 120 meses) e padrão em `RETENTION_MONTHS` | [valor padrão 25/10, reversível] |
| D8 | **Titular:** contagens, papéis com vigência, categorias tratadas, retenção e os próprios acessos dos últimos 90 dias, com download em JSON; sem o conteúdo de sinais; correção e eliminação pelo canal do DPO | [valor padrão 25/10, reversível] |
| D9 | **Tabela `person`:** removida se não houver uso em `src/main` | [valor padrão 25/10, reversível] |

## 5. O que fica para quem implementa

- Como `executarAuditado` mapeia `BloqueadoPeloDpoException` (o comportamento observável é fixo).
- A redação do inventário, do relatório e da comunicação, desde que os testes de documento passem e a linguagem da comunicação seja simples.
- Os casos fictícios de demonstração para os sinais novos.
- A técnica para listar as colunas no teste do inventário (`information_schema.columns`).

## 6. Critérios de aceitação verificáveis

| Critério | Prova |
|---|---|
| Produção sem aceite bloqueia Auditoria e Integridade com 403 por código, registrado e sem dados | `producaoSemAceiteBloqueiaAuditoria`, `producaoSemAceiteBloqueiaIntegridade` (T1) |
| Modo dev com a flag do Jira ligada também bloqueia; aceite sem referência derruba a subida | `devComJiraLigadoTambemBloqueia`, `aceiteSemReferenciaFalha` (T1) |
| Toda rota sensível passa pela trava; gaveta e Central sem dados sensíveis | `todaRotaSensivelPassaPelaTrava`, `historicoDaGavetaBloqueado`, `centralSemItensSensiveis` (T1) |
| Os testes dos Planos 4, 7 e 10 continuam verdes em dev offline | suíte completa (T1, Passo 4) |
| A tela mostra o bloqueio com símbolo e texto, sem números | `itemBloqueadoMostraPausaETexto`, `telaBloqueadaNaoMostraNumeros` (T2) |
| Estimativas guardadas como segundos; texto livre continua sem valor; regravação idempotente | `estimativaGuardaSegundosBrutos`, `textoLivreContinuaSemValor`, `regravaSoEstimativaSemValor` (T3, opção A) |
| Worklog com data de criação | `worklogGuardaCriacao` (T3, opções A e B) |
| `ESTIMATIVA_REDUZIDA` só depois do início; autorizado vira alerta | `reducaoDepoisDeIniciarGera`, `reducaoAntesDeIniciarNaoGera`, `reducaoPeloReporterViraAlerta` (T4) |
| `WORKLOG_RETROATIVO` em dias úteis de Manaus, com feriado e fronteira de fuso | `registradoDoisDiasUteisDepoisGera`, `fimDeSemanaNaoConta`, `feriadoNaoConta`, `fronteiraDeManaus` (T5) |
| A auditoria detecta cada sinal ativo (13, 12 ou 11 conforme a opção) | `todoCodigoDetectadoNoCenarioCompleto` (T6) |
| Retenção por categoria, inclusive decisões | `variavelPropriaVence`, `decisoesAntigasSaoApagadas` (T7) |
| Toda coluna do banco está no inventário, e vice-versa | `todaColunaEstaNoInventario`, `nenhumaColunaInventariadaSumiu` (T8) |
| O titular vê só os próprios dados e não escolhe outra pessoa | `devolveSoOsDadosDoPrincipal`, `titularNaoEscolheOutraPessoa`, `semConteudoDeSinais` (T9) |
| Relatório com as 13 seções, perguntas com opções e base legal nunca afirmada | `relatorioTemAsTrezeSecoes`, `cadaPerguntaTemOpcoesComRecomendada`, `baseLegalNuncaAfirmada` (T11) |

## 7. Riscos, pendências e pontos de parada

**PONTO DE PARADA 1 — ampliar os dados guardados (T3, antes do Passo 1).** Pergunta ao usuário:
1. **Guardar o valor das estimativas (`timeoriginalestimate`, `timeestimate`) no `change_event` e a data de criação do worklog (Recomendada).** Por quê: são números e datas, sem texto livre, e fecham os 12 sinais da seção 7.2 mais o `ESTIMATIVA_AUMENTADA`. Consequência: a migração zera o cursor da sincronização. Quando o usuário ligar a flag, a próxima sincronização refaz a carga com o changelog, para preencher os valores dos últimos 24 meses. Na escala da spec (dezenas de milhares de issues), isso leva vários ciclos do agendador, limitados a `SYNC_MAX_REQUISICOES` (2.000 por execução), e cada ciclo retoma de onde o anterior parou. Até lá, sai o aviso `HISTORICO_DE_ESTIMATIVA_INCOMPLETO`.
2. **Guardar só a data de criação do worklog.** Por quê: o worklog já é sincronizado (Plano 6), então o custo é mínimo. Consequência: `WORKLOG_RETROATIVO` entra; `ESTIMATIVA_REDUZIDA` e `ESTIMATIVA_AUMENTADA` ficam fora, com `SINAL_SEM_DADO`; nenhuma nova carga completa.
3. **Não ampliar nada.** Por quê: mínimo de dados tratados. Consequência: os dois sinais da 7.2 continuam fora, declarados no documento de diferenças; o critério de sucesso 10 da spec fica em 11 de 12.

Sem resposta, **não seguir com a T3 a T6**: fazer a T1, a T2 e a T7 a T12 e deixar a pergunta registrada no `HANDOFF.md`. Ampliar dados pessoais exige um "sim" explícito.

**Pendências do DPO e do usuário** (não bloqueiam este PR; ficam no `HANDOFF.md` e no relatório):
- **Parecer do DPO** sobre as perguntas 1 a 6 do relatório.
- **Ligar o aceite** (`DASHBOARD_DPO_ACEITE=true` e `DASHBOARD_DPO_ACEITE_REF`), feito **pelo usuário** no ambiente de produção, depois do parecer.
- **Comunicação à equipe:** enviada pelo usuário ou pela gestão, com o texto de `comunicacao-a-equipe.md`, antes do uso real.
- **Prazo de comunicação de incidente à ANPD:** conforme a regulamentação vigente, a confirmar com o DPO. O documento não fixa número.

**Impacto em outros planos:**
- **Plano 8, T1 (inventário de rotas):** com `DASHBOARD_DEV_MODE=false` no teste, as rotas sensíveis respondem 403 `AGUARDANDO_ACEITE_DPO` para o GERENTE. O teste `todaRotaApiTemPapelERegistro` deve rodar com aceite ligado (`LgpdSettings(aceiteDpo = true, aceiteRef = "TESTE")`) ou aceitar esse código para as rotas da lista D2. A rota `/api/titular/meus-dados` entra no `inventarioConhecido`.
- **Plano 8, T3 (equivalência):** os sinais novos entram como `novo`. Se o usuário escolher a opção 2 ou 3, entram como pendência.
- **Plano 8, T7 (implantação):** `docs/implantacao.md` cita a trava, as variáveis de aceite e as de retenção.
- **Plano 16 (futuro, escrita no Jira pelo painel):** o relatório para o DPO e a comunicação à equipe descrevem o painel como somente leitura **nesta etapa**. Se o Plano 16 for executado, os dois documentos, o inventário (tabela `escrita_jira`) e o aceite do DPO precisam ser revistos **antes** de ligar a escrita; o relatório diz isso na seção 1.
- **Plano 12:** o runbook de incidentes cita o relatório e o canal do DPO; o validador de configuração confere `DASHBOARD_DPO_ACEITE_REF`; o procedimento de eliminação a pedido do titular fica no runbook diário.

**Riscos:**
- *Formato real de `timeoriginalestimate` no changelog do DC diferente do esperado* (por exemplo, só `fromString`). Mitigação: o checklist da 1ª conexão manda conferir; valor não numérico vira nulo, sem exceção; o aviso de histórico incompleto aparece.
- *`created` do worklog ausente em alguma versão do DC.* Mitigação: worklog sem `criado` não gera sinal e gera aviso.
- *Excesso de alertas de worklog retroativo* (time que registra horas na sexta). Mitigação: limite em YAML; a calibração com dados reais é do Plano 14.
- *Zerar o cursor e a flag nunca ser ligada.* Sem efeito: com a flag `false`, nada é sincronizado.

**Migrações:** a desta fase é V11 (esperado), e só existe nas opções 1 e 2 ou com a remoção de `person`. Sequência esperada até o Plano 12: V4, V5 e V6 (4A), V7 (5a), V8 (6b), V9 (7b), V10 (10), **V11 (11)**, V12 (8, só se o orçamento estourar), próxima livre (12). Se a T3 não criar migração, o Plano 8 usa V11: ele já manda usar o próximo número livre.

## 8. Glossário do que esta fase consome

| Nome | O que é | Origem |
|---|---|---|
| `CodigoSinal`, `Sinal`, `Detector`, `AuditTimeline`, `AuditIssue`, `AuditStore` | domínio da Auditoria | Plano 4 |
| `RegrasDeAuditoria`, `auditoria.exemplo.yaml` | limites e gravidades por sinal | Plano 4, T1 |
| `Autorizacao`, `MotivoAutorizacao` | quem pode alterar na data | Plano 4, T2 |
| `executarAuditado` | rota auditada (registra antes de responder) | Plano 4, T7 |
| `ChangelogParser`, `CAMPOS_COM_VALOR`, `PostgresIssueStore.upsert` | gravação do changelog | Plano 3 |
| `WorklogRecord`, `WorklogParser`, `WorklogStore` | worklog sincronizado | Plano 6, T10 |
| `SinalIntegridade.ESTIMATIVA_AUMENTADA`, `SINAL_SEM_DADO` | sinal de Integridade sem dado | Plano 7, T5 |
| `ItemCentral` | itens da Central | Plano 7, T15 |
| `TarefaDto.historico` | histórico na gaveta universal | Plano 10, T5 |
| `Retention.purge`, `sync_run` | retenção agendada | Plano 4A, T7 |
| `decisao_distribuicao` | decisões de distribuição | Plano 7, T11 |
| `ErroDto`, `CodigosDeMensagem`, `EstadoDaTela` | erros por código e estados de tela | Planos 9 e 10 |
| `TravaDpo`, `LgpdSettings`, `RetencaoSettings`, `MeusDadosDto` (novos) | entregas desta fase | esta fase |

## 9. Referência ao plano

[`docs/superpowers/plans/2026-10-25-plano-11-auditoria-completa-e-lgpd.md`](../plans/2026-10-25-plano-11-auditoria-completa-e-lgpd.md): 12 tarefas, em um PR a partir da `main` com o Plano 10 mesclado. As Tarefas 3 a 6 dependem do ponto de parada 1.
