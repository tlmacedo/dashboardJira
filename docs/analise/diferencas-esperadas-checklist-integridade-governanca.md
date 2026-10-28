# Diferenças esperadas: Checklist × Review, Integridade e Governança

Este documento lista, por bloco, onde o painel novo (Kotlin + React) responde diferente do app Python anterior (`legacy/`) e do projeto irmão bscbi-web, e por quê. Cada diferença é uma decisão, não um defeito. Quem comparar números do legado com os do painel deve começar aqui.

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
- **Estimativa: sem dado.** O sinal `ESTIMATIVA_AUMENTADA` precisa do valor da estimativa no changelog, que a sincronização ainda não guarda. Sem eventos com valor legível não há evidência e a tela mostra o aviso `SINAL_SEM_DADO` (só quando o sinal está ligado). O sinal fica desligado no modelo de YAML até uma decisão futura do usuário ampliar os campos com valor. Os sinais "estimativa reduzida" e "worklog retroativo" do relatório não têm dono neste bloco.
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
