# Roteiro: tudo que vem antes e depois da mudança da flag do Jira

Data: 28/10/2026. Decisão do usuário (28/10): **o Claude Code implementa tudo que não precisa do Jira real, antes da flag; o ClineSR executa o que só faz sentido depois que o usuário mudar a flag para ligada no `app/.env`.** Este documento é o mapa único dos dois lados. Em caso de conflito vale `CLAUDE.md` > `docs/guia-execucao-outra-ia.md` > este roteiro > specs e planos de cada fase (o roteiro só ordena e divide; o *como* continua nos planos).

> **Regra que não muda:** a flag é a única trava de acesso ao Jira e **só o usuário a muda**. Nada do lado "antes" acessa o Jira (fixtures, `MockEngine` e dados de demonstração). Nenhuma IA liga a flag.

## 0. Como o lado "antes" trata os pontos de parada

Os planos têm pontos de parada que perguntam ao usuário. No lado "antes" o usuário pediu para **não ser consultado**; por isso, para cada ponto de parada que **não** tem efeito fora do repositório, o executor adota **a opção marcada "(Recomendada)"**, registra `Ruling: <ponto>, opção adotada, motivo, como reverter` no arquivo de progresso do plano e a lista aparece no relatório final. Pontos com **efeito fora do repositório** (tag e release, instalar serviço, firewall, pedir proxy ou certificado, remover `legacy/`, aceite do DPO, ativar a flag, qualquer acesso ao Jira) **não são tomados**: viram a seção 3 (depois da flag). O executor do "antes" **nunca** liga a flag, nunca remove `legacy/` e nunca cria tag.

Mesclagem de cada PR: pelas 7 condições da seção 5 de `docs/superpowers/fluxo-de-entrega.md` (revisão final sem Critical/Important, suíte verde sem cache, varredura limpa, CI verde, sem conflito, etc.). Faltando qualquer condição, o PR fica aberto e entra no relatório.

## 1. Antes da flag — feito pelo Claude Code (nesta ordem)

Cada linha é um PR a partir da `main` atualizada; a seguinte só começa depois da anterior mesclada.

| # | Entrega | Plano / tarefas | Pontos de parada tratados | Estado |
|---|---|---|---|---|
| A1 | Contrato do cartão (registro, `Cartao`, gaveta compartilhada, Mapa de cartões) | Plano 17a (T1 a T7) | — | a fazer |
| A2 | Auditoria (continuação) | Plano 4, T4 a T9 | — | a fazer |
| A3 | Cards de trabalho e prazos | Plano 5a (9 tarefas); regra I-06 (coluna "último movimento") | — | a fazer |
| A4 | Negativas, Riscos, Previsão, Épicos | Plano 5b (5 tarefas) | — | a fazer |
| A5 | Capacidade e planejamento | Plano 6a (9 tarefas) | local dos YAML reais: o documento recomenda a opção padrão; o código não depende | a fazer |
| A6 | Worklog, posse, esforço, cobertura | Plano 6b (7 tarefas) | — | a fazer |
| A7 | Checklist × Review e Integridade | Plano 7a (8 tarefas) | — | a fazer |
| A8 | Distribuição, Governança, início do gerente/squad/usuário | Plano 7b (10 tarefas); verificação I-12 (labels fora do vocabulário); I-14 só se houver sub-tarefa na ficha (senão, fica para o 14b) | — | a fazer |
| A9 | Complementos de telas e filtros | Plano 10 (12 tarefas) + I-26 e I-27 | — | a fazer |
| A10 | Auditoria completa e LGPD | Plano 11 (12 tarefas) | T3 (ampliar dados guardados): **opção recomendada**; Trava do DPO fica **fechada** (fail-closed) | a fazer |
| A11 | Cards novos de fluxo e gestão | Plano 17b (T8 a T13) | PP5: executado **antes** do 14b por ordem do usuário (risco: calibração só com dados reais; ver seção 4) | a fazer |
| A12 | Preparação final (sem remoção do `legacy/`) | Plano 8, T1 a T8 e T10 | Endereço de escuta: opção recomendada; **T3 passo 5 e T9 (remoção) ficam para a seção 3** | a fazer |
| A13 | Operação em produção | Plano 12 (12 tarefas) | canal de alertas, local do backup e do YAML: opção recomendada para o código; entrega externa fica na seção 3 | a fazer |
| A14 | Implantação real (somente a parte de código e empacotamento) | Plano 13, T2 a T6 e T9 a T10 (código, zip e SHA-256, ensaio local) | T1, T7 (tag), T8 (serviço no SO da empresa), T11 a T13: seção 3 | a fazer |
| A15 | Identidade, inglês e entrega (parte que não depende de ativos do usuário) | Plano 15, T2 a T8 com alternativas de sistema para marca e fonte | T1 (ativos de marca), T9 (sessões), T10 (dono): seção 3 | a fazer |
| A16 | Fechamento do lado "antes" | Relatório, `HANDOFF.md`, índice e este roteiro atualizados | — | a fazer |

**Fora do alcance do "antes" por natureza:** Fase 0 (PC da empresa), Plano 14a/14b (precisam do Jira real), Plano 16 (escrita no Jira: só por decisão expressa do usuário).

## 2. A mudança da flag — ação do usuário (nada disto é do Claude Code nem do ClineSR)

Pré-requisitos, nesta ordem:
1. **Fase 0 concluída no PC da empresa** (`docs/superpowers/plans/2026-10-27-plano-00-pc-da-empresa.md`): JDK 21, Node 22 e pnpm, clone, suíte verde no PC, PowerShell sem bash nem WSL.
2. **Proteção da `main`** no GitHub exigindo `backend`, `frontend`, `guarda` e `varredura` (`docs/operacao/ci.md`, seção 5) e o segredo `VARREDURA_NOMES`.
3. **Credenciais do Jira** no `app/.env` do PC (token pessoal da conta de leitura; nunca no repositório).
4. **Escrever a linha da flag como ligada em `app/.env`** — a única mudança que libera o Jira. Reiniciar o painel depois.
5. Avisar o ClineSR que a flag está ligada; a primeira tarefa dele é a conferência do Plano 14a, Tarefa 1.

## 3. Depois da flag — feito pelo ClineSR (nesta ordem)

Cada linha é um PR (ou execução registrada) a partir da `main` atualizada, pelo `fluxo-de-entrega.md`.

| # | Entrega | Plano / tarefas | Observações |
|---|---|---|---|
| D1 | **Primeira conexão** | Plano 14a, T1 a T6 | Checklist da 1ª conexão em ordem; `serverInfo`, `myself`, campos, uma issue; casador de campos e conferência de status sobre respostas **salvas fora do repositório**; divergências só com agregados; primeiro ciclo de sincronização; cada regra refutada vira correção com teste. Parar nas condições de parada do plano (ex.: `usuariosNaoResolvidos` maior que 0). |
| D2 | **Reparos esperados da 1ª conexão** | Seção 4 deste roteiro | Lista dos pontos que provavelmente exigem ajuste (changelog, vínculo de bloqueio, status e resolução, fuso). |
| D3 | **Recarga completa** | Plano 14b, T7 | Campos novos dos Planos 5 e 6 entram no `raw`. |
| D4 | **Validação dos cards** | Plano 14b, T8, T9 | Roteiro por ficha contra totais do Jira (fonte `JIRA_TOTAL`); inclui os cards do 17b. |
| D5 | **Cards da Sidia (CAT)** | Plano 14b, T10 | Ponto de parada com o usuário (quais cards a Sidia terá). |
| D6 | **Calibração** | Plano 14b, T11, T12 | Retroteste offline e aplicação **com aprovação** (uma pergunta por heurística). |
| D7 | **Aceite do gestor** | Plano 14b, T13, T14 | Card a card; fechamento da validação. |
| D8 | **LGPD: aceite do DPO** | Plano 11 (trava) | A trava só abre com o aceite do DPO; ação do usuário. |
| D9 | **Equivalência e remoção do `legacy/`** | Plano 8, T3 (passo 5) e T9 | Só depois do "sim" do usuário ao documento de equivalência. |
| D10 | **Operação: entrega externa** | Plano 12 (pontos de parada) | Canal dos alertas, cópia de backup fora do servidor, cifragem, local dos YAML reais. |
| D11 | **Implantação real** | Plano 13, T1, T7, T8, T11, T12, T13 | Decisões com a infraestrutura, tag, serviço, homologação e entrada em produção. A flag em produção é decisão separada do usuário. |
| D12 | **Identidade e entrega** | Plano 15, T1, T9, T10, T11 | Ativos de marca, sessões de treinamento, dono da manutenção, fechamento. |
| D13 | **Futuro** | Plano 16 (escrita no Jira) | Só com ordem expressa do usuário. |

## 4. Reparos esperados depois da flag (lista de apostas)

Lugares onde a suposição feita offline mais provavelmente falha com o servidor real. Cada um já tem teste ou ponto de extensão; o ClineSR confirma e corrige.

| # | Risco | Onde está tratado | Como confirmar |
|---|---|---|---|
| R1 | O `expand` do changelog na busca não vem, ou vem truncado | `JiraClient` com fallback (PR #28); código `CHANGELOG_NAO_VEIO` pendente | Primeiro ciclo: contar issues sem `changelog` na resposta |
| R2 | Tipo e direção do vínculo de bloqueio diferem de "Blocks" | `docs/jira/matriz-compatibilidade.md`; cards Bloqueios e Task × Review | Passo do checklist que lista os tipos de vínculo da instância |
| R3 | Status, categorias e resolução reais diferentes do vocabulário versionado | `status_map`, `STATUS_SEM_MAPEAMENTO` | Plano 14a, T2 |
| R4 | Fuso do servidor desloca contagens perto da meia-noite | `SYNC_FUSO_SERVIDOR`, `myself.timeZone` | Plano 14a, T4, Passo 1 |
| R5 | Chaves internas de usuário não resolvidas | `ChavesDeUsuario` | `usuariosNaoResolvidos` no status; ideia I-13 |
| R6 | Limite de taxa ligado e proxy da empresa removendo cabeçalhos | `RetryPolicy`, orçamento de requisições | Logs do primeiro ciclo; ideia I-25 |
| R7 | Campos personalizados com ids diferentes | casador de campos | Plano 14a, T2 |
| R8 | Sub-tarefas usadas e contadas na carga | ficha de campos | Pergunta 16 da referência; ideia I-14 |
| R9 | Instância Sidia exige ficha própria | Plano 14b, T10 | Decisão do usuário |
| R10 | Quantidade de abertas muito maior que o previsto (carga inicial lenta) | teto de requisições e itens | Status da sincronização; ideia I-24 |

## 5. Como acompanhar

- Estado de cada linha do lado "antes": coluna "Estado" da seção 1, atualizada a cada PR mesclado, e o índice dos planos.
- Arquivo de progresso de cada plano em `docs/superpowers/progresso/`, com os `Ruling:` adotados no lugar das perguntas.
- Relatório ao fim do lado "antes": o que foi feito, problemas, decisões tomadas sozinho, o que não foi possível.
