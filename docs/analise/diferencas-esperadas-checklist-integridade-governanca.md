# Diferenças esperadas: Checklist × Review, Integridade e Governança

Este documento lista, por bloco, onde o painel novo (Kotlin + React) responde diferente do app Python anterior (`legacy/`, na tag `legacy-final`) e do projeto irmão bscbi-web, e por quê. Cada diferença é uma decisão, não um defeito. Quem comparar números do legado com os do painel deve começar aqui.

## Seção 7a: Checklist × Review e Integridade

Os cartões do bloco: **CR-01** pares, **CR-02** checklists sem review, **CR-03** ciclo da review, **CR-04** razão review ÷ checklist, **CR-05** criação da review após o pai, **CR-06** review vence depois, **CR-07** mesma pessoa nos dois, **CR-08** pares com alerta, **CR-09** tabela de pares com a comparação lado a lado, **CR-10** aprendizados, **CR-11** duração da review × reabertura, **CR-12** atrasos em cadeia, **CR-13** labels e resultado, **CR-14** diferenças de labels entre o pai e a review. Na Integridade: **IN-01** evidências, **IN-02** pessoas com indício alto ou médio, **IN-03** tasks afetadas, **IN-04** mediana da taxa do time, **IN-05** evidências por sinal, **IN-06** impacto nas metas, **IN-07** tabela de evidências e **IN-08** catálogo de sinais.

### Checklist × Review

| Assunto | Legado ou bscbi-web | Painel | Por quê |
|---|---|---|---|
| Quem é a review | bscbi-web: `summary ~ Review`; legado: filhos do `parent` | Tipo `Checklist Review` (lista no YAML `checklist_review.yaml`), ligada ao checklist pelo vínculo **Blocks** (`issue_link`) | O resumo é texto livre e o `parent` não é a relação real; o vínculo de bloqueio é o que a equipe usa |
| Par Task × Review | Contava pares onde o lado pai era uma Task | Nunca conta: o pai tem de estar em `tiposPai` | Task × Review mede outra coisa e distorcia os percentuais |
| Alertas do par | Seis regras de `pair_row` em `reports.py` (não em `analytics.py`) | As mesmas seis, com os mesmos limites, conferidas por `CompatLegadoChecklistTest` | Continuidade; o texto de cada alerta vem do código, em pt e en |
| CR-12 | "Quem atrasa quem": pares de pessoas | Reformulado como atrasos em cadeia (review resolvida depois do vencimento do pai), **sem pares de pessoas** em série | Sem ranking nem comparação de colegas |
| Amostras pequenas | Percentual sem aviso | **n baixo** em texto quando a amostra tem menos de 5; o lift só vira frase com a amostra mínima | Evita conclusão com poucos casos |
| Aprendizados (CR-10) | Texto livre | Frases fixas por código, só com amostra suficiente; sem amostra aparece a mensagem de mínimo | Texto reproduzível e traduzível |
| Responsáveis | Sempre visíveis | Só os que estão na visão de quem consulta; fora dela aparece traço | Regra de acesso por papel |

### Integridade

A tela e a API são **somente o gerente**: qualquer outro papel recebe 403 na rota, no drill genérico dos cartões IN-nn e no catálogo. **Toda** consulta, inclusive a negada ou malformada, é gravada no `access_log` sem chave de issue nem valor derivável (rota fixa, segmentos posicionais mascarados); se o registro falhar, a resposta é 503 sem dados. As duas faixas "Indício, não prova" e "Validar a base legal com o DPO antes do uso real" aparecem em todos os estados da tela e no CSV. Em produção a tela fica atrás da trava do DPO (`TravaDpo`, fail-closed, código `AGUARDANDO_ACEITE_DPO`) até o aceite do Plano 11.

Os 12 sinais, com o nome técnico usado no código e na API:

| Sinal | Gravidade padrão | Observação |
|---|---|---|
| `RESGATE_POR_PRORROGACAO` | 3 | Prorrogada depois de vencer e entregue entre o prazo antigo e o novo; nunca gera também `VENCIMENTO_PRORROGADO` |
| `TROCA_ANTES_DE_FECHAR` | 3 | Só se o novo responsável foi quem fechou; limite de horas inclusivo. A pessoa do sinal é o autor da troca (o legado atribuía ao novo responsável que fechou) |
| `CRIADA_E_FECHADA_RAPIDO` | 3 | Menos de N minutos entre criar e fechar |
| `REABRE_E_FECHA_RAPIDO` | 3 | As duas pontas em menos de N horas |
| `FECHAMENTO_EM_LOTE` | 3 | Janela de tempo, inclusive atravessando a meia-noite |
| `VENCIMENTO_PRORROGADO` | 2 | Qualquer prorrogação que não seja resgate |
| `VENCIMENTO_TARDIO` | 2 | Só a primeira definição do vencimento, acima da fração da vida da task |
| `PULOU_O_FLUXO` | 2 | Nunca passou por status de categoria ativa |
| `ESTIMATIVA_AUMENTADA` | 2 | Veja "estimativa" abaixo |
| `LABEL_CRITICIDADE_REMOVIDA` | 2 | Removida até N dias antes do fechamento |
| `FECHADA_POR_OUTRO` | 1 | Quem fechou não era o responsável no instante e nunca recebeu a task antes (como no legado). Líderes de squad NÃO são excluídos (veja M1 abaixo) |
| `FECHADA_NO_LIMITE` | 1 | Hora local de Manaus, a partir do limite do YAML |

Diferenças deliberadas:

- **Texto de ajuda igual ao código (achado 12).** No legado a explicação de alguns sinais (por exemplo "menos de 2 h" e "entre 22h e 24h") divergia do que o código fazia. No painel o catálogo (IN-08) monta cada frase a partir dos parâmetros do `integridade.yaml` em uso, então a ajuda nunca diz um número diferente do cálculo.
- **Fechada no limite no fuso de America/Manaus.** O legado usava a hora do servidor; o painel converte o instante do fechamento para Manaus e compara com o vencimento vigente naquele instante.
- **Estimativa: ativa desde o Plano 11.** O sinal `ESTIMATIVA_AUMENTADA` usa o valor da estimativa original (segundos) que o `change_event` passou a guardar (decisão de ampliar os campos com valor, opção A, registrada em `docs/superpowers/progresso/2026-10-25-plano-11-auditoria-completa-e-lgpd.md`) e vem ligado no modelo de YAML. Enquanto os eventos antigos não são recarregados do Jira (a nova carga só roda com a flag ligada pelo usuário) não há evidência e a tela mostra o aviso `SINAL_SEM_DADO` (só quando o sinal está ligado). A "estimativa reduzida" e o "worklog retroativo" do relatório são sinais da Auditoria, não da Integridade (`docs/analise/diferencas-esperadas-auditoria.md`).
- **Pessoa do sinal = autor do evento.** A pessoa é quem fez a ação no changelog (a troca, o fechamento); sem autor, o responsável no instante. O legado atribuía ao responsável atual da task, o que culpava quem recebeu a task depois.
- **Um fechamento rápido gera um sinal só.** Como no legado (`if life_min < 15 ... elif not started`), a task fechada em menos de `criadaEFechadaMinutos` gera `CRIADA_E_FECHADA_RAPIDO` e NÃO também `PULOU_O_FLUXO` (o mais específico vence); a que foi fechada devagar sem passar por status ativo segue com `PULOU_O_FLUXO`. Com `CRIADA_E_FECHADA_RAPIDO` desligado, o pulo do fluxo volta a valer. Num lote de seis tasks rápidas a conta é 6 pontos por task (3 da criada e fechada, 3 do lote), igual ao legado.
- **Desvios do legado que seguem o plano (achados da revisão final):**
  - **M1, `FECHADA_POR_OUTRO`:** quem já foi responsável da task deixou de contar como "outro" (corrigido, como no legado). O legado também excluía o gerente do squad; o painel NÃO exclui líderes (o contexto dos detectores não tem papéis), então Squad Leader e PL que fecham task de terceiros geram o sinal, de gravidade 1. Falso positivo conhecido; se pesar, a correção é passar o diretório de papéis ao contexto da Integridade.
  - **M2, `VENCIMENTO_PRORROGADO`:** conta qualquer extensão do prazo; o legado só contava a feita a até 2 dias do vencimento. O detalhe traz `diasAntesDoVencimento` para filtrar na leitura.
  - **M3, `TROCA_ANTES_DE_FECHAR`:** a pessoa é o autor da troca; o legado atribuía ao novo responsável que fechou.
  - **M4, `PULOU_O_FLUXO`:** ignora espera e bloqueio (só categoria ativa conta como trabalho) e não olha o tempo registrado; o legado considerava espera/bloqueio como "começou" e exigia tempo registrado zero.
  - **M5, `FECHAMENTO_EM_LOTE`:** sem autor do fechamento a pessoa cai no responsável no instante; um lote na borda de `de` perde as tasks anteriores ao período (a janela só vê o que está no recorte).
  - **M6, gaveta:** o texto do detalhe da gaveta ainda sai só em português (adiado, como nas demais gavetas).
- **Só conclusão positiva gera sinal de fechamento.** Cancelamento (Won't Do, Duplicate) não é fechamento.
- **Nível por pessoa.** Os limiares do legado foram mantidos (alto: 12 pontos, 3× a mediana e 3 sinais graves; médio: 6 pontos e 2×; taxa = pontos ÷ max(concluídas, 8); piso da mediana 0,05), agora configuráveis no YAML. A mediana usa só quem tem concluídas. A lista de pessoas traz só alto e médio, em **ordem alfabética**, sem posição, nota nem coluna ordenável, e o nível sempre vai em símbolo mais texto (▲ alto, ● médio).
- **Recorte.** Os níveis e a mediana valem para o período e o squad inteiros; os filtros de sinal e de pessoa só recortam a lista de evidências.
- **Limite.** A tabela mostra até 600 evidências, com aviso quando há mais; os números e a gaveta trazem todas.
- **Gaveta.** As tasks por trás de cada número vêm do drill genérico `GET /api/cartoes/IN-nn/tasks`, com as mesmas travas da rota.

## Seção 7b: Início, Distribuição, Rebalanceamento, Qualidade, Registro de acesso e Configuração

Os cartões do bloco, com os ids do inventário (`docs/analise/relatorio-cards-app-atual.md`) quando existem lá:

- **Início:** **VG-01** abertas, **VG-02** concluídas (30 dias), **VG-03** ciclo, **VG-04** no prazo, **VG-05** vencidas, **VG-06** negligenciadas, **VG-07** bloqueadas, **VG-08** risco alto e médio, **VG-09** Central de atenção, **VG-10** entregas por semana, **VG-11** quando o backlog termina, **VG-12** cartão por squad. Mais o **VG-13** (indício alto, novo) e, no Meu painel, **MP-01** minhas abertas (e as que vencem em 7 dias), **MP-02** ocupação em 4 semanas, **MP-03** em risco, **MP-04** ausências futuras e **MP-05** minha fila.
- **Distribuição e Rebalanceamento:** **DI-01** aguardando, **DI-02** sugestões pendentes, **DI-03** índice de Jain antes e depois, **DI-04** fatores de cada candidato, **DI-05** decisão; **RB-01** movimentos sugeridos, **RB-02** movimentos pendentes, **RB-03** decisão.
- **Governança:** **QD** (Qualidade dos dados, **QD-01** a **QD-05**), **AU-01** decisões de distribuição, **AU-02** registro de auditoria (acesso), **SI-01** estado da sincronização, **SI-02** como ligar ao Jira, e a **Configuração** em leitura (nova).

### Início: Central de atenção, Meu squad e Meu painel

| Assunto | Legado | Painel | Por quê |
|---|---|---|---|
| Números da Central | Calculados no próprio painel, sem garantia de bater com a tela de destino | Cada número é o de um cartão `VG-nn`, que **chama o cartão de destino** (NG-03, RI-01, CP-02, CA-02, MT-01, CR-08, DV-01, AU-01, QD-01...). Número da Central = total da gaveta = número do card de destino, provado em `ContratoBloco7bTest` e em `CentralRoutesTest` | Uma conta só, em um só lugar |
| Sem atualização e paradas | Somadas num item só ("negligenciadas") | **Dois itens separados**: `SEM_ATUALIZACAO` (campo `updated` do Jira) e `PARADAS_NO_STATUS` (mediana por status). Uma task pode estar nos dois e nunca se soma | Achado 2 do relatório; decisão U3 |
| Janela dos KPIs | Concluídas, ciclo e no prazo em 30 dias | Concluídas e ciclo ficam no card Fechadas (VG-02/03); "no prazo" e "no prazo ajustado" aparecem **lado a lado** nos 90 dias das Metas (VG-04). Negligenciadas e bloqueadas (VG-06/07) são itens do VG-09 | Mesma regra única de prazo em todo o painel |
| Item de integridade | Pessoas com indício alto na lista de qualquer coordenador | `INDICIO_ALTO` mora no **VG-13**, **só do gerente**, com a trava do DPO aberta: para o coordenador (ou com a trava fechada) o item **não existe no DTO** nem na gaveta. A consulta é registrada no `access_log` como rota sensível | Integridade só para o gerente, sempre auditada |
| Status fora do mapa | Zeros | Os números dependentes ficam **indisponíveis** (`—`) com o aviso bloqueante, nunca zero | Zero enganoso |
| Sem estimativa | Fração fixa | Acima de 20% é problema, acima de 10% é atenção; o universo vem da Qualidade dos dados | Spec 4.2 |
| Cartão por squad (VG-12) | Ocupação média por squad | Ocupação média e Jain do squad, ordem alfabética, **sem nota de pessoa**; faixas de `planejamento.yaml` (verde até 85%, laranja até 110%) | Sem ranking de pessoas |
| Meu painel | Podia abrir o de outra pessoa pela URL | **Sempre o do próprio usuário**; o parâmetro `pessoa` só vale para o Squad Leader, dentro do squad dele (403 fora dele), e é ignorado para os outros papéis, inclusive coordenação e gerente | Visibilidade por papel |
| Meu squad | Tela do squad do líder | O Squad Leader vê o squad que lidera; coordenação e gerente abrem qualquer squad pelo link do VG-12; outro squad é 403 | Idem |
| Esforço do mês | Índice único | Os **três componentes lado a lado** (peso do tipo, testes e horas) e o índice composto, da proposta de `esforco.yaml` | Sem nota única |
| Ausências futuras | Com o motivo | **Sem motivo**: o painel nunca o guarda nem o mostra | LGPD |

### Distribuição e Rebalanceamento (modo sombra)

O painel **só sugere**. A decisão (**DI-05** e **RB-03**: aprovar, trocar com motivo obrigatório, rejeitar) é gravada **somente no painel**, na tabela `decisao_distribuicao` (retenção longa, como a auditoria), e **nada é escrito no Jira** (`SemEscritaNoJiraTest`). A unicidade vem de `chave_sugestao` (repetida = 409 `JA_DECIDIDA`; sugestão que mudou = 409 `SUGESTAO_MUDOU`). O servidor recalcula a sugestão do ator e nunca confia no que a tela mandou. Conferido contra `legacy/backend/jiradash/distribution/engine.py` (na tag `legacy-final`) em `CompatLegadoDistribuicaoTest`.

| Assunto | Legado | Painel | Por quê |
|---|---|---|---|
| DI-04 fatores | Ranking ordenado por score | Os fatores de **todos** os candidatos em **ordem alfabética**, sem coluna de posição | Decisão D1: sem ranking de pessoas |
| Pesos do score | 1,0 carga, 0,5 WIP, 1,0 WIP cheio, 0,6 competência faltante, 0,15 afinidade | **Os mesmos**, agora em `distribuicao.yaml` (versionado, estrito), com a versão em cada resposta (`distribuicao v1`) | Heurística sem calibração, configurável |
| Carga do score | `(carga + horas do item) ÷ capacidade` | `carga ÷ capacidade`: **não soma as horas do item** ao candidato; o item vai à carga só depois de escolhido | O score compara quem está mais livre antes do item; a soma das horas deslocava a escolha para quem tem mais capacidade |
| Produtividade | Throughput relativo **divide** a carga (quem entrega mais tem mais capacidade) | Horas gastas ÷ estimadas das concluídas de 16 semanas **multiplicam** a carga (quem gasta mais que o estimado enche mais rápido); mesma faixa de 0,85 a 1,15; sem histórico vale 1,0 | Evita punir quem entrega mais e usa dado que existe |
| Histerese do rebalanceamento | Mede a origem **antes** do movimento | Mede a origem **depois**: o destino precisa ficar 20 pp abaixo da origem já aliviada | Sem vai e volta; mais conservador |
| Gatilho e limite | Acima de 110%, até 10 movimentos, só não iniciadas | **Iguais**; 110% exatos não é origem; o destino também não passa do gatilho e precisa ter as competências | Continuidade |
| WIP na atribuição | Não sobe (a task entra em "a fazer") | **Igual**: o WIP só muda quando a task inicia | Continuidade |
| Ordem dos movimentos | Origem mais carregada primeiro | O atendimento segue a origem mais carregada, mas a **lista exibida sai em ordem natural de chave**, nunca pela ocupação de quem cede | Seria um ranking de pessoas |
| Quem decide | Gerente | Coordenação e gerente; o Squad Leader decide só sobre o próprio squad (403 fora dele, com tentativa registrada). As pendentes **sem dono** e as que estão com o gerente **não** entram na visão do líder (só coordenação e gerente as veem e distribuem); o líder decide o rebalanceamento do próprio squad | Visibilidade por papel |
| Auditoria da decisão | Texto livre | O `access_log` guarda só `tipo` e `decisao`, **nunca o motivo**, a task, o sugerido nem o escolhido; o motivo é saneado e limitado a 500 caracteres | LGPD |

### Qualidade dos dados (QD)

Cinco cartões (**QD-01** abertas com dado faltando, **QD-02** conclusão divergente, **QD-03** vocabulário do Jira, **QD-04** bloqueadas sem causa, **QD-05** campos sem valor), para coordenação e gerente (o item "sem estimativa" da Central leva até ela).

- **Conclusão divergente (achado 4).** `DONE_SEM_RESOLUTION` (status concluído sem `resolution`) e `RESOLUTION_SEM_DONE` (`resolution` com status que não é concluído) aparecem como verificações próprias, nos últimos 90 dias, e cada uma abre as tasks.
- **Estados.** Verde até 10%, laranja até 30%, vermelho acima; `LABEL_SEM_CLASSIFICACAO` é só **informativa** (neutra) e a lista de labels vem em ordem alfabética, nunca por contagem. Campos "a confirmar" (Story Points, Epic Link, Flagged, OS Version) **sem valor em nenhuma issue** ficam **indisponíveis** em vez de 100% de problema.
- **Status fora do mapa** não derruba a tela: vira a verificação `STATUS_SEM_MAPEAMENTO` com o nome do status e o aviso informativo.
- **Vocabulário do gestor.** Labels que não justificam atraso nem estão em `ignoradas` (novo, opcional, em `labels_atraso.yaml`) aparecem para o gestor classificar nos YAML; o painel só lê.
- **Pendente.** `PAI_CONCLUIDO_COM_FILHAS_ABERTAS` não foi implementada: a ficha só tem os três tipos de checklist e nenhuma sub-tarefa. Fica para o Plano 14b, se a primeira conexão confirmar o uso de sub-tarefas.
- **Auditoria.** O segmento `label:<nome>` é gravado como `label:*` (label é texto livre e pode nomear alguém).

### Registro de acesso, decisões e Configuração

- **Registro de acesso (AU-02 do inventário, cartão `RA-01`):** **somente o gerente**. Mostra quando, usuário, rota (a chave de issue vira `{key}`), resultado e **só os nomes dos filtros**; nunca segmento, valor de filtro nem chave de task. Período de até 90 dias, páginas de 200 com cursor, datas pela leitura comum (400, nunca 500). A **própria consulta é registrada** (inclusive a negada) e falha ao registrar é 503 sem dados. Sem contagem nem ordem por pessoa. Não passa pela `TravaDpo` (o escopo dos módulos sensíveis é do Plano 11).
- **Decisões de distribuição (AU-01 do inventário, cartão `DC-01`):** o id `AU-01` já é da Auditoria (Plano 4), por isso o registro de decisões é `DC-01`; coordenação e gerente; lista do mais recente ao mais antigo, até 500, sem contagem por pessoa.
- **Configuração (nova, cartão `CF-01`):** **somente o gerente**, leitura. Mostra o ambiente, o modo da sincronização (ao vivo, demonstração ou desligada) e, de cada arquivo de regras e ficha, **nome lógico, versão, hash SHA-256 e origem** (real ou modelo). **Nunca** mostra segredo, token, URL, caminho, nome de pessoa nem o conteúdo de `papeis.yaml`, `equipes.yaml` e `ausencias.yaml` (desses, só as contagens de pessoas e squads). O painel não edita configuração.
- **Sincronização (SI-01 e SI-02):** o estado da sincronização e "como ligar ao Jira" já foram entregues pelo Plano 4A; não mudam neste bloco.

### Pendências deste bloco

- **Captura visual** das telas do bloco (1440 e 390 px, três temas): não feita, o ambiente não tem navegador.
- **Auditoria como módulo sensível (Plano 11):** quando o escopo da `TravaDpo` incluir a Auditoria, o item `ALTERACOES_SEM_AUTORIZACAO` precisa sair do VG-09 para um cartão próprio, como o VG-13.
- **Heurísticas sem calibração:** pesos de distribuição, gatilho e histerese são os do legado; calibrar na primeira conexão real.
