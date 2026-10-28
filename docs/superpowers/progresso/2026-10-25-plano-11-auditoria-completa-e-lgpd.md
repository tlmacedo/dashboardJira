# Progresso — plano: docs/superpowers/plans/2026-10-25-plano-11-auditoria-completa-e-lgpd.md

Branch `feat/auditoria-completa-lgpd-plano-11`, a partir da `main` `31c95e1` (migrações até a V5). Executor: Claude Code (subagente implementador), sem o usuário presente; pontos de parada resolvidos pela opção Recomendada e registrados como `Ruling:`. O aceite do DPO (`DASHBOARD_DPO_ACEITE`) NÃO foi ligado por ninguém: só a mecânica existe.

## Estado por tarefa

| Tarefa | Estado | Observações |
|---|---|---|
| 1 Trava do DPO no backend | Concluída | `lgpd/{LgpdSettings,TravaDpo}.kt`, Auditoria atrás da trava, VG-14, access_log sem alvo |
| 2 Trava do DPO na tela | Concluída | `shared/AguardandoDpo.tsx`, Menu com ⏸ e texto, Central com aviso |
| 3 Ampliar dados guardados (opção A) | Concluída | migração `V6__dados_para_auditoria_completa.sql` |
| 4 Estimativa reduzida | Concluída | `auditoria/DetectoresDeEstimativa.kt` |
| 5 Worklog retroativo | Concluída | `auditoria/DetectoresDeWorklog.kt`, histórico com horas |
| 6 Integridade com `ESTIMATIVA_AUMENTADA` e documentação | Concluída | 13 de 13 sinais; casos de demonstração DEMO-39 e DEMO-40 |

## Rulings

- Ruling: T4/T5 — a `unidade` de `auditoria.yaml` (corrido/útil) não afeta o worklog retroativo: ele sempre conta dias úteis de Manaus (o plano). O histórico da gaveta mostra a estimativa em horas com uma casa e as horas como linha `worklog` (`para` = "aaaa-mm-dd (1.5 h)").
- Ruling: T6 — a demonstração (`DASHBOARD_DEMO_DATA`) passou a ler `fields.worklog.worklogs` da fixture e a deslocar `started`/`created` junto das demais datas; DEMO-39 (estimativa aumentada e depois reduzida) e DEMO-40 (horas registradas com atraso) são fictícios.

- Ruling: T3 (ponto de parada) — opção A (Recomendada): guardar o valor das estimativas no `change_event` (segundos brutos) e a data de criação do worklog; a migração zera `sync_state.watermark` (conferido: `SyncStateStore.cursor` lê `sync_state.watermark`) e remove a tabela `person` (sem uso em `src/main`; só dois testes a usavam e foram ajustados). Decisão tomada pelo executor sem o usuário, pela regra do roteiro (seção 0); data 28/10/2026; reversão: não ligar nada, a migração só tem efeito quando o usuário ligar a flag do Jira. O usuário deve registrar o "sim" no `HANDOFF.md` ao ligar a flag.
- Ruling: T3 — número da migração: V6 (a V5 é a última na `main` `31c95e1`). O Plano 10 pode usar a V6; no push final conferir `origin/main` e renumerar para a próxima livre.
- Ruling: T3 — a regravação de `change_event` compara `lower(field)` com a lista JSON dos dois campos de estimativa e só toca linhas com `from_value` e `to_value` nulos; `WorklogStore` regrava `criado_em` no `ON CONFLICT` junto com os demais campos (o registro é substituído por inteiro a cada sincronização).

- Ruling: T2 — `shared/EstadoDaTela.tsx` e `estadoDe` são do Plano 10 (não existem na `main`): criado `shared/AguardandoDpo.tsx` (`ehAguardandoDpo` + componente) e usado em CardPage, Auditoria, Divergência e Integridade. Quando o Plano 10 mesclar, `estadoDe` deve chamar `ehAguardandoDpo` antes do caso genérico de 403. O menu só sabe do bloqueio fora do modo dev (o `/api/me` não é consultado em dev, por teste existente).

- Ruling: T1 — a `TravaDpo` continua consultando o banco (herança do 7a, Ruling I3/IMP-2/MIN-1) SÓ para fechar mais: `liberada = aceiteDpo || (dev && !jiraLive && semDadoSincronizado)`. O plano pedia "só `Settings`"; o aceite de fato não consulta o banco, mas a condição de desenvolvimento mantém a defesa de quem desliga a flag depois de sincronizar de verdade (mais restritivo). Reversão: tirar `semDadoSincronizado()` da condição de desenvolvimento.
- Ruling: T1 — `LgpdSettings` recusa nascer com aceite sem referência válida (o próprio objeto, não só `fromEnv`).
- Ruling: T1 (pendência herdada 1) — a Auditoria passou a `ModuloSensivel.AUDITORIA` real: rotas `/api/auditoria/*`, `/api/cards/task-review`, cartões AU-01..03 e DV-01 (`moduloSensivel`). `ALTERACOES_SEM_AUTORIZACAO` (AU-01) e `TASK_REVIEW_DIVERGENTE` (DV-01) saíram do VG-09 para o cartão próprio VG-14 (módulo sensível). Aviso informativo `ITENS_AGUARDANDO_ACEITE_DPO` (`modulos`) na Central; o coordenador não fica sabendo da Integridade.
- Ruling: T1 (pendência 2) — `PREFIXOS_SENSIVEIS` ganhou Auditoria, task-review, AU-, DV-, VG-14 e os drills legados; as rotas põem `ROTA_SENSIVEL`. O `access_log` deixa de gravar `pessoa`, `sinal`, `squad` e `total` nessas rotas. Teste `auditoriaGuardaSoQuemConsultouNuncaOAlvoNemOTotal`.
- Ruling: T1 (pendência 3) — "quem consultou quem" NÃO fica no `access_log` (opção mais restritiva, Recomendada). Fica só quem consultou o módulo. Reversão: tirar o ramo `sensivel` em `filtrosAuditados`.
- Ruling: T1 — `/api/me` devolve `modulosBloqueados` filtrado pelo papel (AUDITORIA para coordenação e gerente; INTEGRIDADE só para o gerente) para o usuário comum não saber que os módulos existem.
- Ruling: T1 — `TarefaDto.historico` e `/api/drill/*` não existem na `main` (Plano 10 em paralelo): o plano pede `HISTORICO_AGUARDANDO_ACEITE_DPO`; o código do aviso foi criado, a aplicação na gaveta fica para quando o Plano 10 mesclar (pendência). O teste `todaRotaSensivelPassaPelaTrava` já cobre `/api/drill/*` por prefixo, então uma rota dessas criada sem a trava falha.
- Ruling: T1 — `EstadoDaTela` e `CodigosDeMensagem` também são do Plano 10; usado `ErroDto` existente.

## Minor adiados / pendências

- Aplicar a trava ao `historico` da gaveta universal (`/api/tarefa/{key}`) quando o Plano 10 mesclar.
