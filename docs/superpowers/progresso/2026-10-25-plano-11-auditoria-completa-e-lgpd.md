# Progresso — plano: docs/superpowers/plans/2026-10-25-plano-11-auditoria-completa-e-lgpd.md

Branch `feat/auditoria-completa-lgpd-plano-11`, a partir da `main` `31c95e1` (migrações até a V5). Executor: Claude Code (subagente implementador), sem o usuário presente; pontos de parada resolvidos pela opção Recomendada e registrados como `Ruling:` (roteiro `2026-10-28-roteiro-antes-e-depois-da-flag.md`, seção 0). O aceite do DPO (`DASHBOARD_DPO_ACEITE`) NÃO foi ligado por ninguém: só a mecânica existe e a trava fica FECHADA (fail-closed). Nenhum acesso ao Jira, nenhuma tag, nenhuma remoção do `legacy/`.

## Estado por tarefa

| Tarefa | Estado | Observações |
|---|---|---|
| 1 Trava do DPO no backend | Concluída | `lgpd/{LgpdSettings,TravaDpo}.kt`, Auditoria atrás da trava, VG-14, `access_log` sem alvo |
| 2 Trava do DPO na tela | Concluída | `shared/AguardandoDpo.tsx`, Menu com a pausa e o texto, Central com aviso |
| 3 Ampliar dados guardados (opção A) | Concluída | migração `V7__dados_para_auditoria_completa.sql` |
| 4 Estimativa reduzida | Concluída | `auditoria/DetectoresDeEstimativa.kt` |
| 5 Worklog retroativo | Concluída | `auditoria/DetectoresDeWorklog.kt`, histórico com horas |
| 6 Integridade com `ESTIMATIVA_AUMENTADA` e documentação | Concluída | 13 de 13 sinais; DEMO-39 e DEMO-40 |
| 7 Retenção por categoria | Concluída | `config/RetencaoSettings.kt`, `db/Retention.kt` |
| 8 Inventário dos dados | Concluída | `docs/lgpd/inventario-de-dados.md`, `InventarioDeDadosTest` |
| 9 Direitos do titular (API) | Concluída | `lgpd/TitularRoutes.kt`, `TitularDto.kt` |
| 10 Tela Meus dados | Concluída | `screens/MeusDados/` |
| 11 Documentos para o DPO e a equipe | Concluída | `docs/lgpd/{relatorio-para-o-dpo,atendimento-ao-titular,comunicacao-a-equipe}.md`, `DocumentosLgpdTest` |
| 12 Fechamento | Concluída | `HANDOFF.md`, `app/README.md`, guia de execução, roteiro |

## Rulings

- Ruling: T1 — a `TravaDpo` continua consultando o banco (herança do 7a, Rulings I3/IMP-2/MIN-1) SÓ para fechar mais: `liberada = aceiteDpo || (dev && !jiraLive && semDadoSincronizado)`. O plano pedia "só `Settings`"; o aceite não consulta o banco, mas a condição de desenvolvimento mantém a defesa de quem desliga a flag depois de sincronizar de verdade (mais restritivo). Reversão: tirar `semDadoSincronizado()` da condição de desenvolvimento.
- Ruling: T1 — `LgpdSettings` recusa nascer com aceite sem referência válida (o próprio objeto, não só `fromEnv`).
- Ruling: T1 (pendência herdada 1) — a Auditoria passou a `ModuloSensivel.AUDITORIA` real: rotas `/api/auditoria/*`, `/api/cards/task-review`, cartões AU-01..03 e DV-01 (`moduloSensivel`). `ALTERACOES_SEM_AUTORIZACAO` (AU-01) e `TASK_REVIEW_DIVERGENTE` (DV-01) saíram do VG-09 para o cartão próprio VG-14 (módulo sensível). Aviso informativo `ITENS_AGUARDANDO_ACEITE_DPO` (`modulos`) na Central; o coordenador não fica sabendo da Integridade.
- Ruling: T1 (pendência 2) — `PREFIXOS_SENSIVEIS` ganhou Auditoria, task-review, AU-, DV-, VG-14, os drills legados e `/api/titular`; as rotas põem `ROTA_SENSIVEL`; `segmentoSensivel` do `CartaoDeCentral` passou a olhar o `moduloSensivel` do cartão de destino. O `access_log` deixa de gravar `pessoa`, `sinal`, `squad` e `total` nessas rotas e grava `motivo=AGUARDANDO_ACEITE_DPO` nos bloqueios. Teste `auditoriaGuardaSoQuemConsultouNuncaOAlvoNemOTotal`.
- Ruling: T1 (pendência 3) — "quem consultou quem" NÃO fica no `access_log` (opção mais restritiva, Recomendada): fica só quem consultou o módulo. Reversão: tirar o ramo `sensivel` em `filtrosAuditados`.
- Ruling: T1 (pendência 4) — o expurgo (`Retention.purge`) só APAGA: não escreve `sync_run`. Quem registra a execução (`RETENCAO`) é o agendador, e a trava do DPO ignora esse tipo (IMP-2), então a retenção nunca fecha a trava na demonstração. Testes `RetentionTest.retencaoNaoEscreveSyncRunNemMexeNaTravaDoDpo` e `TravaDpoTest.retencaoNaoContaComoSincronizacaoReal`.
- Ruling: T1 — `/api/me` devolve `modulosBloqueados` filtrado pelo papel (AUDITORIA para coordenação e gerente; INTEGRIDADE só para o gerente), para o usuário comum não saber que os módulos existem. A Configuração do gerente mostra `aceiteDpo` (só a referência).
- Ruling: T1 — `TarefaDto.historico`, `/api/drill/*`, `EstadoDaTela`, `estadoDe` e `CodigosDeMensagem` são do Plano 10 (não existem na `main`; branch paralela). Criados os códigos `HISTORICO_AGUARDANDO_ACEITE_DPO` e `ITENS_AGUARDANDO_ACEITE_DPO`; a aplicação ao histórico da gaveta universal fica para quando o Plano 10 mesclar. O teste `todaRotaSensivelPassaPelaTrava` já cobre `/api/drill/*` e `/api/tarefa`-style por prefixo (`/api/drill/auditoria`, `/api/drill/integridade`, `/api/drill/task-review`).
- Ruling: T2 — criado `shared/AguardandoDpo.tsx` (`ehAguardandoDpo` + componente) e usado em CardPage, Auditoria, Divergência e Integridade. Quando o Plano 10 mesclar, `estadoDe` deve chamar `ehAguardandoDpo` antes do caso genérico de 403. O menu só sabe do bloqueio fora do modo dev (o `/api/me` não é consultado em dev, por teste existente).
- Ruling: T3 (ponto de parada) — opção A (Recomendada): guardar o valor das estimativas no `change_event` (segundos brutos) e a data de criação do worklog; a migração zera `sync_state.watermark` (conferido: `SyncStateStore.cursor` lê `sync_state.watermark`) e remove a tabela `person` (sem uso em `src/main`; só dois testes a usavam e foram ajustados). Decisão tomada pelo executor sem o usuário, pela regra do roteiro; data 28/10/2026; reversão: a migração só tem efeito quando o usuário ligar a flag do Jira (a nova carga não roda antes). O usuário registra o "sim" no `HANDOFF.md`.
- Ruling: T3 — número da migração: V7. Nasceu como V6; o Plano 10 mesclou a sua `V6__campos_do_dicionario.sql` (PR #44), então a minha foi renumerada para V7 no merge da main.
- Ruling: T3 — a regravação de `change_event` compara `lower(field)` com a lista JSON dos dois campos de estimativa e só toca linhas com `from_value` e `to_value` nulos; `WorklogStore` regrava `criado_em` no `ON CONFLICT` junto com os demais campos (o registro é substituído por inteiro a cada sincronização).
- Ruling: T4/T5 — a `unidade` de `auditoria.yaml` (corrido/útil) não afeta o worklog retroativo: ele sempre conta dias úteis de Manaus. O histórico da gaveta mostra a estimativa em horas com uma casa e as horas como linha `worklog` (`para` = "aaaa-mm-dd (1.5 h)").
- Ruling: T6 — a demonstração (`DASHBOARD_DEMO_DATA`) passou a ler `fields.worklog.worklogs` da fixture e a deslocar `started`/`created` junto das demais datas; DEMO-39 (estimativa aumentada e depois reduzida) e DEMO-40 (horas registradas com atraso) são fictícios.
- Ruling: T7 — a retenção por categoria convive com as variáveis antigas: `RETENTION_MONTHS` (6..600) e `ACCESS_LOG_RETENTION_MONTHS` (12..600) continuam valendo; as novas `RETENTION_*_MONTHS` (1..120) vencem. `RETENTION_MONTHS` abaixo de 12 NÃO encurta `access_log` nem decisões (teste antigo "a auditoria não encurta"): o piso de 12 da auditoria foi mantido; a partir de 12 todas herdam. `Settings.retentionMonths` e `accessLogRetentionMonths` passaram a espelhar as categorias de histórico e de acesso. Reversão: tirar o piso em `lerRetencao`.
- Ruling: T8 — `squads.yaml` (citado no plano) não existe no código: o inventário diz isso; o teste exige a menção. A tabela `person` saiu pela V7.
- Ruling: T9 — `/api/titular/meus-dados` é marcada como rota sensível no registro: nenhum parâmetro vai ao `access_log`, nem a tentativa de pedir outra pessoa (a primeira versão do teste achou `pessoa=bruno` gravado). Duas camadas (prefixo e atributo da rota); a mutação de uma só sobrevive por redundância, a das duas é derrubada.
- Ruling: T11 — o relatório não cita a "imagem sem segredos" do plano (não há contêiner; vira "pacote sem segredos"); cada medida cita `Classe.metodo` de teste existente, conferido por `DocumentosLgpdTest`.

## Prova de que os testes pegam defeito (mutação real, uma por vez, restaurada pelo git)

Mutação -> testes derrubados: trava sempre liberada (13); trava sem `!jiraLive` (2); trava sem `semDadoSincronizado` (5); `exigir` removido da rota `/api/auditoria/sinais` (3); `pessoa`/`squad` fora do filtro sensível (2); `/api/titular` sem checagem de parâmetro (1); `/api/titular` sem a pesquisa de contagem correta (3); `/api/titular` sem `ROTA_SENSIVEL` sozinho (SOBREVIVE: redundante com o prefixo) e sem os dois (1 derrubado); estimativa sem a regra "só depois de iniciar" (1); autorizado deixa de virar alerta (1); worklog contando fim de semana como útil (3); worklog sem a regra "depois da resolução" (1); `access_log` com o prazo errado (2); decisões com o prazo errado (1); regravação de estimativa sem o filtro de campo (1); migração sem zerar o cursor (1); VG-14 sem `moduloSensivel` (1); `/api/me` mostrando Integridade a todos (2); coluna nova na migração sem linha no inventário (1); no frontend: `ehAguardandoDpo` sempre falso (2) e menu sem marcar o bloqueio (3). Os testes de TravaDpo, Titular, detectores e documentos foram escritos junto da implementação (TDD parcial: o vermelho veio das mutações e de falhas reais na primeira rodada, como o `pessoa=bruno` gravado no registro).

## Minor adiados / pendências

- (Resolvido após o merge do Plano 10 na main) A trava foi aplicada ao `historico` de `GET /api/tarefa/{key}` (nulo e aviso `HISTORICO_AGUARDANDO_ACEITE_DPO`) e `estadoDe` reconhece `AGUARDANDO_ACEITE_DPO` (estado `aguardando-dpo`, antes do 403 genérico); `titular` entrou em `MODULOS_DA_API`. Falta apenas incluir `/api/titular/meus-dados` no `inventarioConhecido` do teste de rotas do Plano 8.
- `ITENS_AGUARDANDO_ACEITE_DPO` não diz quantos itens ficaram de fora (de propósito: nem o número vaza).
- O menu só marca a pausa fora do modo dev (`/api/me` não é consultado em dev).
- Sem captura visual (sem navegador); a tela Meus dados e o menu foram conferidos por teste.
- Dev-mock (`dev-mock/`) não ganhou a rota `/api/titular/meus-dados`.
- MIN-2 do 7a (carga inicial que falhou antes da marca d'água e sem `sync_run`): continua aberta; a trava poderia olhar também issues fora do manifesto de demonstração.
- Ver a seção "Correções da revisão do PR #45" no fim deste arquivo.
- Pendências externas: parecer do DPO (sete perguntas), aceite ligado pelo usuário, envio do texto à equipe, nova carga do Jira (quando a flag for ligada) para preencher estimativas e datas de criação.

## Correções da revisão do PR #45 (Opus)

- Ruling: I-1 — a `TravaDpo` (condição de desenvolvimento) fecha também com QUALQUER linha de `sync_state` de instância do Jira (`instance <> '-'`; a "-" é a da retenção/demonstração): a V7 zera só a marca d'água e a linha sobrevive, então nem a V7 nem a retenção dos `sync_run` abrem a trava sobre dado real. A demonstração pura (e com retenção) continua aberta. Testes `migracaoQueZeraACursorNaoAbreATravaSobreDadoReal`, `demonstracaoComRetencaoContinuaAberta` e `MigracaoDadosAuditoriaTest.migracaoNaoAbreATravaDoDpoNemEscreveSyncRun` (vermelhos com a regra antiga, provado por mutação; a V7 continua sem escrever `sync_run`).
- Ruling: I-2 — relatório e aviso à equipe descrevem a pontuação da Integridade (pontos, taxa, múltiplo em relação ao time, nível ALTO/MÉDIO por pessoa, só gerente, lista alfabética, sem posição); pergunta 2 reescrita. `DocumentosLgpdTest.documentosNaoNegamAPontuacaoQueAIntegridadeEntrega` exige um termo para cada campo do `NivelPessoaDto` e proíbe as negações antigas.
- M-1 aviso da Central sem nomear módulo; M-2/M-3 `.env.example` com a precedência da retenção e o efeito de `RETENTION_HISTORICO_MONTHS` curto na janela da carga inicial; M-4 relatório (titular vê os próprios acessos); M-5 atendimento (a próxima sincronização traz de volta o dado eliminado); M-6 comentários e aviso `SINAL_SEM_DADO`; M-7 `CentralRoutesTest` sem `if` e `TravaDpoRoutesTest` com VG-14, VG-13 e IN-02.
- M-10 virou a pergunta 7 ao DPO (ex-membro sem papel vigente recebe 403 na tela Meus dados; exerce o direito pelo canal do DPO).
- Adiados: M-8 (nomes de teste do brief trocados por equivalentes, mesmo conteúdo); M-9 (dev-mock sem a rota do titular, `inventarioConhecido` do Plano 8, captura visual).
