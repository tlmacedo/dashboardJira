# Inventário dos dados tratados pelo Painel de Squads

Documento do Plano 11 (LGPD). Lista **cada coluna do banco** do painel (SQLite, `app/data/painel.db`), com o que ela guarda, se é dado pessoal, para que serve, de onde vem, quem vê e por quanto tempo fica. Um teste (`InventarioDeDadosTest`) compara esta tabela com o esquema real: coluna nova sem linha aqui, ou linha de coluna que sumiu, derruba o teste. Texto de apoio ao encarregado de dados (DPO); **a base legal não é afirmada aqui**: a coluna "Base legal" é sempre "a definir pelo DPO" e as candidatas estão como perguntas em `docs/lgpd/relatorio-para-o-dpo.md`.

Legenda de "Pessoal?": `sim` identifica uma pessoa (username do Jira); `indireto` pode apontar para uma pessoa por contexto (horas, texto livre, consulta); `não` não se refere a pessoa. Origem: `Jira` (sincronizado), `painel` (gerado pelo painel) ou `configuração` (arquivo YAML). O painel é **somente leitura** do Jira nesta etapa. "Quem vê" resume as regras do painel (negar por padrão): USUARIO vê só o próprio; SQUAD_LEADER, o próprio e o squad; COORDENADOR e GERENTE, todos. Auditoria e Integridade só abrem com o aceite do DPO (`DASHBOARD_DPO_ACEITE`). Retenção: variáveis `RETENTION_HISTORICO_MONTHS`, `RETENTION_ACCESS_LOG_MONTHS`, `RETENTION_DECISOES_MONTHS` e `RETENTION_EXECUCOES_MONTHS` (padrão 24 meses; valores finais do DPO).

## Colunas do banco

| Tabela.coluna | Dado | Pessoal? | Finalidade | Origem | Quem vê | Retenção | Base legal |
|---|---|---|---|---|---|---|---|
| `issue.id` | número interno da linha | não | chave interna | painel | ninguém (interno) | enquanto a issue existir; RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `issue.key` | chave da issue (por exemplo TG-101) | não | identificar a task nos cards e nas gavetas | Jira | todos os papéis, conforme a visão | enquanto a issue existir; RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `issue.instance` | instância do Jira de origem | não | separar Samsung e Sidia | configuração | todos os papéis | enquanto a issue existir; RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `issue.project` | chave do projeto | não | filtrar o projeto do time | Jira | todos os papéis | enquanto a issue existir; RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `issue.issue_type` | tipo (Task, Checklist Review...) | não | cards por tipo | Jira | todos os papéis | enquanto a issue existir; RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `issue.status` | status atual | não | fluxo e tempo em status | Jira | todos os papéis | enquanto a issue existir; RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `issue.resolution` | resolução | não | definir "concluída" | Jira | todos os papéis | enquanto a issue existir; RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `issue.assignee_ref` | username do responsável | sim | carga, prazos e metas por pessoa | Jira | USUARIO (o próprio), SQUAD_LEADER (o squad), COORDENADOR, GERENTE | enquanto a issue existir; RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `issue.reporter_ref` | username do Reporter (o PL) | sim | regra de quem pode alterar prazo e responsável | Jira | COORDENADOR, GERENTE (Auditoria, com o aceite do DPO) | enquanto a issue existir; RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `issue.created` | quando a issue foi criada | não | idade e fluxo | Jira | todos os papéis | enquanto a issue existir; RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `issue.updated` | última alteração | não | tasks sem atualização e retenção | Jira | todos os papéis | enquanto a issue existir; RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `issue.resolved` | quando foi resolvida | não | entregas, prazo e retenção | Jira | todos os papéis | enquanto a issue existir; RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `issue.duedate` | vencimento | não | prazos e "no prazo" | Jira | todos os papéis | enquanto a issue existir; RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `issue.labels` | labels da issue (texto livre do Jira) | indireto | labels de atraso justificado e de criticidade | Jira | todos os papéis, conforme a visão | enquanto a issue existir; RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `issue.parent_key` | chave da issue-pai | não | hierarquia | Jira | todos os papéis | enquanto a issue existir; RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `issue.estimate_s` | estimativa original em segundos | não | estimativa, carga e riscos | Jira | todos os papéis | enquanto a issue existir; RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `issue.spent_s` | tempo gasto em segundos | não | esforço | Jira | todos os papéis | enquanto a issue existir; RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `issue.raw` | JSON da issue sem o changelog, os vínculos e o worklog; passa pelo SanitizadorRaw, que remove o nome de exibição, o e-mail, o avatar, o endereço pessoal, o identificador de conta e o fuso, normaliza o username e apaga a chave interna do usuário; o resumo (título) permanece e pode, em tese, citar alguém | indireto | reconstruir campos novos sem nova carga (resumo da gaveta) | Jira | só o próprio painel; o resumo aparece na gaveta de tasks, conforme a visão | enquanto a issue existir; RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `issue.os_version` | versão do SO da task | não | filtro por OS Version | Jira | todos os papéis | enquanto a issue existir; RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `issue.jira_id` | id numérico do Jira | não | identificar a issue na sincronização | Jira | ninguém (interno) | enquanto a issue existir; RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `issue.removida_em` | quando a issue sumiu do Jira | não | remoção lógica | painel | ninguém (interno) | enquanto a issue existir; RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `issue.priority` | prioridade | não | cards de risco e trabalho | Jira | todos os papéis | enquanto a issue existir; RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `issue.remaining_s` | estimativa restante em segundos | não | previsão e carga | Jira | todos os papéis | enquanto a issue existir; RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `issue.story_points` | pontos de história | não | previsão | Jira | todos os papéis | enquanto a issue existir; RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `issue.epic_key` | chave do épico | não | card de épicos | Jira | todos os papéis | enquanto a issue existir; RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `issue.flagged` | marcada com impedimento | não | card de bloqueios | Jira | todos os papéis | enquanto a issue existir; RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `issue.components` | componentes (lista) | não | filtros e cards | Jira | todos os papéis | enquanto a issue existir; RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `change_event.id` | número interno da linha | não | chave interna | painel | ninguém (interno) | RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `change_event.changelog_id` | id estável do item do histórico | não | gravar sem duplicar | painel | ninguém (interno) | RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `change_event.issue_id` | issue do evento | não | ligar o evento à issue | painel | ninguém (interno) | RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `change_event.field` | nome do campo alterado | não | tempo em status, auditoria e integridade | Jira | conforme o módulo (Auditoria e Integridade com o aceite do DPO) | RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `change_event.from_value` | valor anterior, só de campos permitidos (status, resolução, vencimento, labels, versão do SO, responsável como username, estimativas em segundos); nunca texto livre | indireto | tempo em status e sinais de auditoria | Jira | COORDENADOR, GERENTE (Auditoria, com o aceite do DPO); cálculos agregados para os demais | RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `change_event.to_value` | valor novo, com as mesmas regras de `from_value` | indireto | tempo em status e sinais de auditoria | Jira | COORDENADOR, GERENTE (Auditoria, com o aceite do DPO); cálculos agregados para os demais | RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `change_event.at` | quando alterou | indireto | linha do tempo, tempo em status e sinais | Jira | conforme o módulo | RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `change_event.author_ref` | username de quem alterou | sim | regra de quem podia alterar (Auditoria) e Integridade | Jira | COORDENADOR, GERENTE (Auditoria); só o GERENTE (Integridade); ambas com o aceite do DPO | RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `worklog.id` | número interno da linha | não | chave interna | painel | ninguém (interno) | RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `worklog.issue_id` | issue do registro | não | ligar o registro à issue | painel | ninguém (interno) | RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `worklog.author_ref` | username de quem registrou as horas | sim | horas por pessoa e sinal de worklog retroativo | Jira | USUARIO (o próprio), SQUAD_LEADER (o squad), COORDENADOR, GERENTE | RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `worklog.started` | início declarado das horas | indireto | horas por dia | Jira | conforme a visão da pessoa | RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `worklog.seconds` | duração em segundos | indireto | horas registradas | Jira | conforme a visão da pessoa | RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `worklog.instance` | instância do Jira | não | separar instâncias | configuração | ninguém (interno) | RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `worklog.jira_id` | id do registro no Jira | não | gravar sem duplicar | Jira | ninguém (interno) | RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `worklog.criado_em` | quando o registro foi criado no Jira | indireto | sinal de worklog retroativo | Jira | COORDENADOR, GERENTE (Auditoria, com o aceite do DPO) | RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `sync_state.instance` | instância | não | estado da sincronização | configuração | GERENTE (Sincronização) | sem prazo: um registro por instância | a definir pelo DPO |
| `sync_state.watermark` | até quando já sincronizou | não | sincronização incremental | painel | GERENTE (Sincronização) | sem prazo: um registro por instância | a definir pelo DPO |
| `sync_state.ultima_reconciliacao` | última reconciliação | não | sincronização | painel | GERENTE (Sincronização) | sem prazo: um registro por instância | a definir pelo DPO |
| `sync_state.suspensa_codigo` | código da suspensão | não | parar a sincronização após erro | painel | GERENTE (Sincronização) | sem prazo: um registro por instância | a definir pelo DPO |
| `sync_state.suspensa_em` | quando suspendeu | não | sincronização | painel | GERENTE (Sincronização) | sem prazo: um registro por instância | a definir pelo DPO |
| `sync_state.falhas_desde_run_id` | marcador de falhas | não | sincronização | painel | ninguém (interno) | sem prazo: um registro por instância | a definir pelo DPO |
| `access_log.id` | número interno da linha | não | chave interna | painel | ninguém (interno) | RETENTION_ACCESS_LOG_MONTHS | a definir pelo DPO |
| `access_log.user_id` | username de quem consultou | sim | auditoria de acesso (quem consultou o quê) | painel | GERENTE (Registro de acesso) | RETENTION_ACCESS_LOG_MONTHS | a definir pelo DPO |
| `access_log.route` | rota consultada, sem chave de issue | não | auditoria de acesso | painel | GERENTE (Registro de acesso) | RETENTION_ACCESS_LOG_MONTHS | a definir pelo DPO |
| `access_log.filters` | só os nomes e valores saneados de uma lista fechada de filtros, sem segredo, sem chave de issue e, nas rotas de Auditoria e Integridade, sem pessoa, squad, sinal nem total (guarda quem consultou o módulo, nunca o alvo); cada valor é truncado | indireto | auditoria de acesso | painel | GERENTE (Registro de acesso) | RETENTION_ACCESS_LOG_MONTHS | a definir pelo DPO |
| `access_log.at` | quando consultou | indireto | auditoria de acesso | painel | GERENTE (Registro de acesso) | RETENTION_ACCESS_LOG_MONTHS | a definir pelo DPO |
| `sync_run.id` | número interno da linha | não | chave interna | painel | ninguém (interno) | RETENTION_EXECUCOES_MONTHS | a definir pelo DPO |
| `sync_run.instance` | instância | não | histórico de execuções | configuração | GERENTE (Sincronização) | RETENTION_EXECUCOES_MONTHS | a definir pelo DPO |
| `sync_run.kind` | tipo (incremental, reconciliação, retenção) | não | histórico de execuções | painel | GERENTE (Sincronização) | RETENTION_EXECUCOES_MONTHS | a definir pelo DPO |
| `sync_run.started_at` | início da execução | não | histórico de execuções | painel | GERENTE (Sincronização) | RETENTION_EXECUCOES_MONTHS | a definir pelo DPO |
| `sync_run.finished_at` | fim da execução | não | histórico de execuções | painel | GERENTE (Sincronização) | RETENTION_EXECUCOES_MONTHS | a definir pelo DPO |
| `sync_run.status` | resultado | não | histórico de execuções | painel | GERENTE (Sincronização) | RETENTION_EXECUCOES_MONTHS | a definir pelo DPO |
| `sync_run.issues` | quantas issues | não | contagem da execução | painel | GERENTE (Sincronização) | RETENTION_EXECUCOES_MONTHS | a definir pelo DPO |
| `sync_run.eventos` | quantos eventos | não | contagem da execução | painel | GERENTE (Sincronização) | RETENTION_EXECUCOES_MONTHS | a definir pelo DPO |
| `sync_run.requisicoes` | quantas requisições | não | contagem da execução | painel | GERENTE (Sincronização) | RETENTION_EXECUCOES_MONTHS | a definir pelo DPO |
| `sync_run.incompletas` | quantas incompletas | não | contagem da execução | painel | GERENTE (Sincronização) | RETENTION_EXECUCOES_MONTHS | a definir pelo DPO |
| `sync_run.usuarios_nao_resolvidos` | quantas chaves de usuário sem tradução | não | contagem da execução | painel | GERENTE (Sincronização) | RETENTION_EXECUCOES_MONTHS | a definir pelo DPO |
| `sync_run.removidas` | quantas removidas | não | contagem da execução | painel | GERENTE (Sincronização) | RETENTION_EXECUCOES_MONTHS | a definir pelo DPO |
| `sync_run.codigo_erro` | código do erro (nunca texto livre) | não | diagnóstico | painel | GERENTE (Sincronização) | RETENTION_EXECUCOES_MONTHS | a definir pelo DPO |
| `sync_run.http_status` | status HTTP do erro | não | diagnóstico | painel | GERENTE (Sincronização) | RETENTION_EXECUCOES_MONTHS | a definir pelo DPO |
| `jira_user_key.instance` | instância | não | tradução de chave interna em username | configuração | ninguém (interno) | RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `jira_user_key.user_key` | chave interna do usuário no Jira | indireto | tradução de chave interna em username | Jira | ninguém (interno) | RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `jira_user_key.username` | username correspondente | sim | tradução de chave interna em username | Jira | ninguém (interno) | RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `jira_user_key.visto_em` | última vez em que foi visto | não | retenção do mapa | painel | ninguém (interno) | RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `issue_link.instance` | instância | não | vínculos Task × Review e bloqueios | configuração | ninguém (interno) | RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `issue_link.from_key` | chave de uma ponta do vínculo | não | vínculos | Jira | todos os papéis, conforme a visão | RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `issue_link.to_key` | chave da outra ponta | não | vínculos | Jira | todos os papéis, conforme a visão | RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `issue_link.link_type` | tipo do vínculo | não | vínculos | Jira | todos os papéis | RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `issue_link.direction` | direção | não | vínculos | Jira | todos os papéis | RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `issue_link.visto_em` | última vez visto | não | vínculos | painel | ninguém (interno) | RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `issue_link.removida_em` | quando o vínculo sumiu | não | remoção lógica | painel | ninguém (interno) | RETENTION_HISTORICO_MONTHS | a definir pelo DPO |
| `backup_run.id` | número interno da linha | não | chave interna | painel | ninguém (interno) | RETENTION_EXECUCOES_MONTHS | a definir pelo DPO |
| `backup_run.tipo` | cópia ou teste de restauração | não | operação | painel | GERENTE (alertas) | RETENTION_EXECUCOES_MONTHS | a definir pelo DPO |
| `backup_run.started_at` | início | não | operação | painel | GERENTE (alertas) | RETENTION_EXECUCOES_MONTHS | a definir pelo DPO |
| `backup_run.finished_at` | fim | não | operação | painel | GERENTE (alertas) | RETENTION_EXECUCOES_MONTHS | a definir pelo DPO |
| `backup_run.status` | resultado | não | operação | painel | GERENTE (alertas) | RETENTION_EXECUCOES_MONTHS | a definir pelo DPO |
| `backup_run.arquivo` | só o nome do arquivo da cópia, nunca o caminho | não | operação | painel | GERENTE (alertas) | RETENTION_EXECUCOES_MONTHS | a definir pelo DPO |
| `backup_run.tamanho_bytes` | tamanho da cópia | não | operação | painel | GERENTE (alertas) | RETENTION_EXECUCOES_MONTHS | a definir pelo DPO |
| `backup_run.sha256` | assinatura da cópia | não | conferir a integridade da cópia | painel | GERENTE (alertas) | RETENTION_EXECUCOES_MONTHS | a definir pelo DPO |
| `backup_run.codigo_erro` | código do erro | não | operação | painel | GERENTE (alertas) | RETENTION_EXECUCOES_MONTHS | a definir pelo DPO |
| `decisao_distribuicao.id` | número interno da linha | não | chave interna | painel | ninguém (interno) | RETENTION_DECISOES_MONTHS | a definir pelo DPO |
| `decisao_distribuicao.instance` | instância | não | separar instâncias | configuração | COORDENADOR, GERENTE | RETENTION_DECISOES_MONTHS | a definir pelo DPO |
| `decisao_distribuicao.tipo` | distribuição ou rebalanceamento | não | registro de decisões | painel | COORDENADOR, GERENTE | RETENTION_DECISOES_MONTHS | a definir pelo DPO |
| `decisao_distribuicao.issue_key` | chave da issue decidida | indireto | registro de decisões | Jira | COORDENADOR, GERENTE | RETENTION_DECISOES_MONTHS | a definir pelo DPO |
| `decisao_distribuicao.sugerido` | quem o painel sugeriu (modo sombra) | sim | registro de decisões | painel | COORDENADOR, GERENTE | RETENTION_DECISOES_MONTHS | a definir pelo DPO |
| `decisao_distribuicao.escolhido` | quem a pessoa escolheu | sim | registro de decisões | painel | COORDENADOR, GERENTE | RETENTION_DECISOES_MONTHS | a definir pelo DPO |
| `decisao_distribuicao.decisao` | aprovada, trocada ou rejeitada | não | registro de decisões | painel | COORDENADOR, GERENTE | RETENTION_DECISOES_MONTHS | a definir pelo DPO |
| `decisao_distribuicao.motivo` | motivo digitado (texto livre de até 500 caracteres, saneado, nunca vai para o access_log) | indireto | registro de decisões | painel | COORDENADOR, GERENTE | RETENTION_DECISOES_MONTHS | a definir pelo DPO |
| `decisao_distribuicao.decidido_por` | username de quem decidiu | sim | registro de decisões | painel | COORDENADOR, GERENTE | RETENTION_DECISOES_MONTHS | a definir pelo DPO |
| `decisao_distribuicao.decidido_em` | quando decidiu | indireto | registro de decisões e retenção | painel | COORDENADOR, GERENTE | RETENTION_DECISOES_MONTHS | a definir pelo DPO |
| `decisao_distribuicao.versao_regras` | versão das regras | não | registro de decisões | painel | COORDENADOR, GERENTE | RETENTION_DECISOES_MONTHS | a definir pelo DPO |
| `decisao_distribuicao.chave_sugestao` | assinatura da sugestão (evita decisão dupla) | não | impedir decisão repetida | painel | ninguém (interno) | RETENTION_DECISOES_MONTHS | a definir pelo DPO |

A tabela `person` (jira_ref e nome) foi **removida** pela migração V6 do Plano 11: não tinha uso e guardava nome de pessoa sem finalidade. O painel também não guarda os valores de `fromString` e `toString` do changelog, só o valor bruto dos campos permitidos.

## Arquivos de configuração com dados de pessoas

Ficam em `app/config/`, **fora do git** (o repositório só tem os modelos `.exemplo`, com nomes fictícios). O painel só **lê**; quem edita é o gestor, direto no arquivo.

| Arquivo | O que guarda | Onde fica | Quem edita |
|---|---|---|---|
| `papeis.yaml` | username do Jira, papel (usuário, Squad Leader, coordenador, gerente), squad e vigência | pasta de configuração do servidor | o gestor (fora do painel) |
| `equipes.yaml` | por pessoa: horas semanais, foco, alocação e competências | pasta de configuração do servidor | o gestor (fora do painel) |
| `ausencias.yaml` | por pessoa: períodos de ausência (sem o motivo) | pasta de configuração do servidor | o gestor (fora do painel) |
| `squads.yaml` | não existe nesta versão: os squads vêm das atribuições de `papeis.yaml` e de `equipes.yaml` | não se aplica | não se aplica |

## Logs do servidor

O log do servidor guarda só o modelo da rota (por exemplo `/api/tarefa/{key}`), o método, o status e a duração. **Nunca** entram: token ou senha (a máscara de log cobre os segredos configurados), texto livre de issue ou de motivo, e-mail, endereço do Jira, chave de issue de uma consulta nem o conteúdo de uma resposta.

## O que o painel não trata

Comentários das issues e do worklog, descrição e título como dado de pessoa (o resumo da issue fica em `issue.raw` e aparece só na gaveta), e-mail, avatar, nome de exibição, identificador de conta, fuso do usuário e senha. O token de acesso ao Jira fica só no ambiente do servidor (`app/.env`), nunca no banco nem no repositório.
