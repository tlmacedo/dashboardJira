# Progresso: expurgo dos dados fictícios ao ligar a flag (29/10/2026)

Branch `feat/expurgo-dados-ficticios` (a partir da `main` em 2d213ba, depois do PR #45). PR pendente.

## O que foi feito

- **Expurgo na subida** (`DadosDeDemonstracao.expurgar`, chamado por `prepararDemonstracao` com a flag do Jira ligada, em modo dev ou produção): apaga, numa transação, só as linhas que o manifesto identifica (instância + prefixo + chave exata da fixture): issues (com eventos e horas por cascata), `issue_link` e `decisao_distribuicao`. Idempotente.
- **Recusa do banco misturado:** restando issue sem nenhuma prova de sincronização real, desfaz tudo e falha com `BANCO_DESCONHECIDO_SEM_SINCRONIZACAO` (substitui o antigo `BANCO_COM_DADOS_DE_DEMONSTRACAO`). Com a flag desligada em produção continua `DEMO_RESIDUAL`; `DASHBOARD_DEMO_DATA` com a flag ligada continua `AMBIENTE_NAO_PERMITIDO`.
- **Prova de sincronização real compartilhada:** `sync/SincronizacaoReal` (execução incremental/reconciliação ou linha de `sync_state` de instância diferente de `INSTANCIA_RETENCAO`), usada pela `TravaDpo`, pelo expurgo e por `/api/meta` (m-1 do PR #45: acabou o literal `'-'`).
- **Estado da tela:** `/api/meta` ganha `aguardandoCargaDoJira` (flag ligada e sem sincronização real); a casca mostra "Aguardando a primeira carga do Jira" (pt/en). `/api/me` não muda. Sem faixa de dados fictícios com a flag ligada.
- **Minors do PR #45:** m-2 (comentários de `IntegridadeDto` e `NiveisPorPessoa` descrevem a pontuação) e m-3 (relatório do DPO: taxa = pontos por max(concluídas, mínimo)).

## Rulings

- O expurgo NÃO escreve `sync_run` e NÃO toca `sync_state`: o cursor zerado pela V7 (recarga completa forçada) fica intacto.
- A `TravaDpo` não foi aberta: com a flag ligada ela segue fechada até o aceite do DPO.
- Vínculo com uma ponta em chave da fixture é apagado; decisão de distribuição só pela chave exata da fixture na instância.

## Pendências

- Nenhuma nova. Seguem as externas do Plano 11 (parecer do DPO e aceite).
