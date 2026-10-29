# Auditoria de vencimento, responsável e labels de atraso: diferenças esperadas em relação ao app atual

Compara o que o app Python (`legacy/`, na tag `legacy-final`) fazia com a auditoria nova do painel (Plano 4, spec `docs/superpowers/specs/2026-10-25-spec-fase-04-auditoria.md`). Diferença listada aqui é intencional; qualquer outra divergência é defeito. Os exemplos usam a issue fictícia `TG-10` e as pessoas fictícias `ana`, `bruno` e `carla` (cenário AUD-1 dos testes). Nada aqui foi conferido no Jira real: a primeira conexão segue `docs/jira/checklist-primeira-conexao.md`.

## O que o app atual tem (e o que não tem)

O app atual **não tem** os sinais de auditoria de alteração de vencimento, responsável e labels de atraso. O mais perto é a **Integridade** (relatório `docs/analise/relatorio-cards-app-atual.md`, seção 4.13): 12 sinais por pessoa, entre eles "vencimento prorrogado" e "troca de responsável ≤ 2 h antes de fechar", todos pensados como pontuação de integridade, sem avaliar **quem** podia fazer a alteração. O registro de consulta ("AU-02" do relatório) é auditoria de **acesso**, não de alteração. A Integridade continua na Fase 7 e só para o gerente; a auditoria nova é outra coisa: mostra alterações para conferir (indício, não prova), para coordenação e gerente.

## Os sinais (11 da Fase 4 e os do Plano 11)

Limites em `app/config/auditoria.exemplo.yaml` (copie para `auditoria.yaml`); gravidade de 1 a 3 por código; qualquer sinal pode ser desligado (`ativo: false`).

| Código | Regra | Limite configurável | Equivalente no app atual |
|---|---|---|---|
| `VENCIMENTO_ALTERADO_NAO_AUTORIZADO` | `from` e `to` legíveis e diferentes, autor que não podia (inclusive o próprio responsável, mesmo sendo Squad Leader); definir pela 1ª vez não conta | gravidade (3) | novo (parcial: "vencimento prorrogado", da Integridade, não olha o autor) |
| `VENCIMENTO_REMOVIDO` | `from` preenchido e `to` vazio, por qualquer autor, inclusive coordenação | gravidade (3) | novo |
| `VENCIMENTO_TASK_REVIEW_DIVERGENTE` | no evento de vencimento que deixa task e Review (ligadas pelo link Blocks) com datas diferentes; alerta, não auditoria | gravidade (1) | novo (o CR-06 do relatório compara o vencimento pelo campo `parent`; aqui o par vem do link) |
| `RESPONSAVEL_TROCADO_NAO_AUTORIZADO` | troca de responsável por quem não podia | gravidade (3) | novo |
| `PASSA_BOLA` | N trocas perto do vencimento, ou a troca devolve a task a quem já a teve; um sinal por issue, qualquer autor | `passaBolaTrocas` (3), `pertoDoVencimentoDias` (2), `unidade` (corrido) | novo |
| `TROCA_PERTO_DO_VENCIMENTO` | troca de responsável a até N dias do vencimento vigente, por quem não podia | `pertoDoVencimentoDias` (2), `unidade`, gravidade (2) | novo (parcial: Integridade, "troca de responsável ≤ 2 h antes de fechar", sem olhar o autor) |
| `LABEL_APOS_FECHAMENTO_NAO_AUTORIZADO` | label que justifica atraso adicionada depois de a task fechar, por quem não podia | gravidade (2) | novo |
| `LABEL_PELO_RESPONSAVEL_APOS_FECHAMENTO` | idem, pelo próprio responsável: auditoria; alerta se ele era o Reporter ou a coordenação | gravidade (2) | novo |
| `LABEL_TARDIA_COM_POSSE_ANTECIPADA` | `LATE_SENT`/`LATE_REVIEW` em task cujo responsável a tinha desde N dias antes do vencimento ou mais | `posseAntesDoVencimentoDias` (2), `unidade`, gravidade (2) | novo |
| `LABEL_APOS_VENCIMENTO` | label que justifica atraso adicionada depois do dia do vencimento, por qualquer autor | gravidade (3) | novo |
| `CANCELAMENTO_SUSPEITO` | transição para status de cancelamento depois do vencimento, ou por quem não podia | `statusCancelamento` (Cancelled, Canceled, Dropped), gravidade (2) | novo |
| `ESTIMATIVA_REDUZIDA` | `timeoriginalestimate` caiu pelo menos `estimativaReducaoMinima` do valor anterior DEPOIS da primeira entrada em andamento (antes é replanejamento); autor autorizado vira alerta, os demais auditoria; usa os segundos guardados no `change_event` (de/para em horas) | `estimativaReducaoMinima` (0.2), gravidade (2) | novo (o app atual tem só "estimativa aumentada", na Integridade) |
| `WORKLOG_RETROATIVO` | horas registradas com `criado` pelo menos N dias úteis de Manaus depois do dia de `started` (o dia do início não conta, o do registro conta; fim de semana e feriado não contam), OU registradas depois da resolução com início antes dela; sempre auditoria, um sinal por registro; usa a data de criação guardada no worklog | `worklogRetroativoDiasUteis` (2), gravidade (2) | novo |

Quem **podia** alterar (spec 7.1): o Reporter da task (o PL), coordenadores e gerente, ou o Squad Leader do squad do responsável, **com os papéis vigentes na data civil de Manaus da alteração**. O próprio responsável não conta como autorizado por ser Squad Leader do próprio squad (decisão do usuário). Papel encerrado na véspera não autoriza; autor ausente, de automação ou chave `JIRAUSER*` não resolvida nunca vira "autorizado" e aparece na lista como autor desconhecido ou não autorizado, sem derrubar a tela.

### Alteração em lote (contexto, não prova)

Quando a **mesma pessoa** mexe no **mesmo campo**, no **mesmo minuto**, em `emLoteMinimo` issues distintas ou mais (padrão 5, em `auditoria.yaml`), os sinais dessas alterações ganham o modificador "em lote (N)". É só contexto: não cria nem apaga sinal, não muda a gravidade, não há contagem nem ranking por pessoa e só coordenação e gerente veem. O app atual tem algo parecido apenas na Integridade ("fechamento em lote ≥ 6 em 10 min"), por pessoa e com pontuação.

## Outras diferenças

| Assunto | App atual | Novo | Efeito |
|---|---|---|---|
| Fuso | Datas em UTC | America/Manaus para todo "dia" | Alteração às 23:30 de Manaus é o dia seguinte em UTC; a vigência de papel, a janela "perto do vencimento" e "depois do vencimento" usam o dia de Manaus |
| Concluída | Categoria "done" do status | Categoria `done` **e** `resolution`; divergência vai para Qualidade dos dados | A task em "Concluído" sem `resolution` fica fora de "no prazo" |
| No prazo ajustado | Ausente: todo atraso é atraso (relatório, seção 5) | "No prazo" e "no prazo ajustado" lado a lado; o ajustado conta também a task atrasada com label que justifica (`labels_atraso.yaml`) | Label sob auditoria continua contando no ajustado, com o contador "justificadas sob auditoria" ao lado |
| Quem vê | Todos veem tudo | Só coordenador e gerente (e o usuário local do modo desenvolvimento, que é gerente); usuário e Squad Leader recebem 403 | Toda consulta (200, 400, 403, 404) vai para o `access_log` |
| Aviso | Nenhum | Toda tela e toda resposta levam "indício, não prova" (`INDICIO_NAO_PROVA`) | A tela nunca afirma má-fé |
| Sem ranking | Pontuação por pessoa na Integridade | Lista por data e chave; nenhuma contagem por pessoa; filtro de pessoa em ordem alfabética | Não há "pior pessoa" |
| Task × Review | Par pelo campo `parent` | Par pelo link de issue Blocks / is blocked by, nas duas direções | Ambiguidade vira aviso `VINCULO_AMBIGUO`; base sem vínculos vira `VINCULOS_INDISPONIVEIS` (card vazio, sem erro) |
| Vencimento | Valor lido como veio | Aceita `aaaa-mm-dd` e `aaaa-mm-dd 00:00:00.0`; outro formato vira o aviso `VENCIMENTO_ILEGIVEL`, não some nem dá erro | Conferir o formato real na 1ª conexão |
| Reporter | Não usado | A autorização usa o reporter **atual**; havendo evento de `reporter`, aviso `REPORTER_ALTERADO` | O changelog guarda só o nome do campo `reporter`, sem valor |
| Cálculo | Em memória, a cada tela | Na hora da consulta, sobre o `change_event` já sincronizado | Sem tabela de achados, sem migração e sem triagem |

## O que fica de fora

- Nada da spec 7.2 fica de fora: **estimativa reduzida** e **worklog retroativo** entraram no Plano 11 (o `change_event` passou a guardar os segundos das estimativas e o worklog, a data de criação). Os registros anteriores à nova carga não geram sinal e saem como aviso (`HISTORICO_DE_ESTIMATIVA_INCOMPLETO`, `HISTORICO_DE_WORKLOG_INCOMPLETO`).
- Os 12 sinais de **Integridade** (Fase 7, só gerente).
- Histórico de reporter, e conteúdo de descrição e título (o changelog só guarda o nome desses campos; o histórico mostra "valor não guardado (privacidade)").
- Corrigir a divergência pelo painel: a Divergência mostra e leva ao Jira por link (só com a flag ligada); escrita no Jira é o Plano 16, futuro.

## Pendências

- Formato real de `duedate`, `labels` (separador em `toString`) e do `from`/`to` do assignee só se confirma na primeira conexão (checklist, Passo 4).
- **LGPD:** confirmar a base legal com o DPO antes de usar com dados reais; até lá, só fixtures e modo de demonstração.

## Como validar

- `cd app/backend && ./gradlew test --tests '*Detectores*' --tests '*AuditCalculatorTest*' --tests '*AuditoriaRoutesTest*'`
- `cd app/frontend && pnpm vitest run src/screens/Auditoria src/screens/Divergencia`
