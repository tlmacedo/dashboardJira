# Cobertura do projeto: planos 4A a 8 contra a spec e os documentos

Data: 25/10/2026. Auditoria somente leitura, feita na branch `docs/planos-04a-04` (árvore de trabalho com alterações não commitadas). Nenhum acesso ao Jira nem à rede.

Pergunta respondida: se outra IA executar **apenas** os planos 4A, 4, 5, 6, 7 e 8 (os Planos 1, 2, 3A e 3 já estão na `main`), o projeto fica terminado?

Fontes comparadas:
- a spec mestra `docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md`;
- `docs/analise/relatorio-cards-app-atual.md`;
- `CLAUDE.md` (árvore de trabalho), `PRODUCT.md` e `docs/design/plano-visual-frontend.md`;
- `HANDOFF.md`, `.superpowers/sdd/2026-10-24-plano-03-tempo-em-status/final-review.md` e `docs/jira/*`;
- `docs/guia-execucao-outra-ia.md` e `.clinerules/`;
- o código atual: migrações V1 a V3, `nav.ts` e `MeDto`.

Estados usados nas tabelas:
- **COBERTO:** algum plano entrega o item, com tarefa e teste.
- **PARCIAL:** um plano entrega só parte, ou só documenta.
- **SEM PLANO:** nenhum plano entrega.
- **FORA DE ESCOPO:** a spec não aprovou o item.

---

## (A) Veredito

**Não.** Os planos 4A a 8 fecham a *etapa offline*: os cards do relatório e os 11 cards novos, a auditoria, a sincronização, o OIDC testado com emissor falso, o compose de produção, a acessibilidade AA e a remoção do `legacy/`. Mesmo assim, o painel não fica pronto para produção.

O que falta:
- **CI:** não existe workflow, nem no repositório nem no histórico.
- **Validação com dados reais:** depois que a flag for ligada, ninguém valida os números nem calibra as heurísticas.
- **LGPD:** nenhum plano produz o material para o DPO.
- **Spec 7.2:** dois sinais obrigatórios ficam sem plano.
- **Implantação:** não há plano para a implantação real nem para a operação.

Há ainda inconsistências críticas entre os planos. A principal: os planos 5 a 8 não estão versionados, e o `CLAUDE.md` da `main` contradiz o 4A.

---

## (B) Requisito ou card → onde está no plano

### B.1 Cards do app atual (relatório, seção 4), um ID por linha

| ID | Card | Onde (arquivo + tarefa) | Estado |
|---|---|---|---|
| VG-01 | Abertas | Plano 7, T15 (KPI da Central; dado de Plano 5, T5), tela T17 | COBERTO |
| VG-02 | Concluídas (30 d) com variação | Nenhum KPI da Central (T15 traz abertas, vencidas, risco alto, no prazo e ajustado). Concluídas só em Fechadas (Plano 5, T6), sem a janela 30 d + 30 d e sem a variação | PARCIAL |
| VG-03 | Ciclo P50 (P85 no rodapé) | Fora da Central. O ciclo existe em Fechadas (Plano 5, T6), com período padrão de 90 d | PARCIAL |
| VG-04 | No prazo (30 d) | Plano 7, T15 (90 d, ajustado ao lado: diferença esperada) | COBERTO |
| VG-05 | Vencidas | Plano 7, T15 (`VENCIDAS`) | COBERTO |
| VG-06 | Negligenciadas | Plano 7, T15 (`SEM_ATUALIZACAO`); Plano 5, T5 | COBERTO |
| VG-07 | Bloqueadas | Plano 7, T15 (`BLOQUEADAS`) | COBERTO |
| VG-08 | Risco alto (médio) | Plano 7, T15 (KPI); Plano 5, T11 | COBERTO |
| VG-09 | Central de atenção (13 itens) | Plano 7, T15 (16 itens; sem atualização e paradas separados) e T17 | COBERTO |
| VG-10 | Entregas por semana | Plano 7, T15 | COBERTO |
| VG-11 | Quando o backlog termina | Plano 7, T15 (Monte Carlo do Plano 5, T12) | COBERTO |
| VG-12 | Cartão por squad | Plano 7, T15 (`CartaoSquadDto`) | COBERTO |
| MP-01 | Minhas abertas | Plano 7, T16 e T17 | COBERTO |
| MP-02 | Ocupação 4 semanas | Plano 7, T16 (`Demanda` do Plano 6) | COBERTO |
| MP-03 | Em risco | Plano 7, T16 | COBERTO |
| MP-04 | Ausências futuras | Plano 7, T16 | COBERTO |
| MP-05 | Minha fila | Plano 7, T16 (ordem da `Agenda` do Plano 6, T4) | COBERTO |
| AB-01 | Abertas | Plano 5, T5 e T8 | COBERTO |
| AB-02 | Idade P50 histórica | Plano 5, T5 (`historicoPorTipo`, por tipo) | COBERTO |
| AB-03 | Idade P85 histórica | Plano 5, T5 | COBERTO |
| AB-04 | Acima do P85 | Plano 5, T5 (P85 do próprio tipo) | COBERTO |
| AB-05 | Por status | Plano 5, T5 | COBERTO |
| AB-06 | Por prioridade | Plano 5, T5 (aviso se faltarem campos) | COBERTO |
| AB-07 | Idade em faixas | Plano 5, T5 | COBERTO |
| AB-08 | Vencimento | Plano 5, T5 | COBERTO |
| AB-09 | Tabela de abertas, CSV | Plano 5, T5 (`linhas`) e T8 | COBERTO |
| EP-01 | Tabela por épico | Plano 5, T12 e T13 (previsão só com amostra própria) | COBERTO |
| NG-01 | Sem atualização | Plano 5, T5 | COBERTO |
| NG-02 | Sem responsável | Plano 5, T5 (decisão de visibilidade em aberto, ver E) | COBERTO |
| NG-03 | Vencidas | Plano 5, T5 | COBERTO |
| NG-04 | Bloqueadas | Plano 5, T5 | COBERTO |
| NG-05 | Paradas no status | Plano 5, T5 | COBERTO |
| NG-06 | A fazer há mais de 30 d | Plano 5, T5 | COBERTO |
| RI-01 | Risco alto/médio | Plano 5, T11 e T13 | COBERTO |
| RI-02 | Tabela em risco | Plano 5, T11 e T13 | COBERTO |
| RI-03 | Sinais por pessoa | Plano 5, T11 (proporção, alfabética) | COBERTO |
| PV-01 | Backlog | Plano 5, T12 | COBERTO |
| PV-02 | Termina em P50/P85/P95 | Plano 5, T12 (com taxa de chegada) | COBERTO |
| PV-03 | Confiança | Plano 5, T12 | COBERTO |
| PV-04 | Throughput semanal | Plano 5, T12 | COBERTO |
| PV-05 | Quantos itens em 2/4/8 sem. | Plano 5, T12 (`quantos`) | COBERTO |
| PV-06 | Previsão por issue | Plano 5, T12, mais Plano 6, T4 ("pela capacidade" e "Atrasa?") | COBERTO |
| FE-01 | Concluídas | Plano 5, T6 | COBERTO |
| FE-02 | Pontos entregues | Plano 5, T6 | COBERTO |
| FE-03 | Ciclo P50/P85 | Plano 5, T6 | COBERTO |
| FE-04 | Lead time | Plano 5, T6 | COBERTO |
| FE-05 | No prazo | Plano 5, T6 (com o ajustado) | COBERTO |
| FE-06 | Canceladas | Plano 5, T6 | COBERTO |
| FE-07 | Entregas por semana | Plano 5, T6 | COBERTO |
| FE-08 | Dispersão do ciclo | Plano 5, T6 (`DispersaoDto`) | COBERTO |
| FE-09 | Por tipo | Plano 5, T6 | COBERTO |
| FE-10 | Por projeto | Plano 5, T6 | COBERTO |
| FE-11 | Entregas por pessoa | Plano 5, T6 (finalizou, participou, pontos, ciclo, estimado × gasto). Falta o **tempo ativo** | PARCIAL |
| FE-12 | Tabela de concluídas (Real÷estim., prazo, CSV) | `FechadasDto` não tem a tabela de linhas; existe só o drill genérico | PARCIAL |
| NE-01 | Reabertas | Plano 5, T10 | COBERTO |
| NE-02 | Devolvidas | Plano 5, T10 | COBERTO |
| NE-03 | Canceladas | Plano 5, T10 | COBERTO |
| NE-04 | Concluídas com atraso | Plano 5, T10 | COBERTO |
| NE-05 | Abertas vencidas | Plano 5, T10 | COBERTO |
| NE-06 | Estouro de estimativa | Plano 5, T10 | COBERTO |
| NE-07 | Muitas trocas | Plano 5, T10 | COBERTO |
| NE-08 | Taxas | Plano 5, T10 | COBERTO |
| NE-09 | Listas por categoria com "maior tempo" por status | As listas saem pelo drill; nenhuma tarefa entrega o "maior tempo por status" | PARCIAL |
| NE-10 | Por responsável | Plano 5, T10 | COBERTO |
| TS-01 | Issues analisadas | Plano 3 (na `main`) | COBERTO |
| TS-02 | Eficiência de fluxo | Plano 3 | COBERTO |
| TS-03 | Paradas além do normal | Plano 3 | COBERTO |
| TS-04 | Tempo por status | Plano 3 | COBERTO |
| TS-05 | Onde o tempo é gasto | Plano 3 | COBERTO |
| TS-06 | Mapa pessoa × status | Plano 3 | COBERTO |
| TS-07 | Lista de paradas | Plano 3 | COBERTO |
| MT-01 | No prazo | Plano 5, T7 e T8 | COBERTO |
| MT-02 | No prazo pela data original | Plano 5, T7 | COBERTO |
| MT-03 | Antecipadas | Plano 5, T7 | COBERTO |
| MT-04 | Em D-1 ou D-2 | Plano 5, T7 | COBERTO |
| MT-05 | No dia | Plano 5, T7 | COBERTO |
| MT-06 | Atrasadas | Plano 5, T7 | COBERTO |
| MT-07 | Sem vencimento | Plano 5, T7 | COBERTO |
| MT-08 | Entregas por período | Plano 5, T7 (`porPeriodo`) | COBERTO |
| MT-09 | % no prazo por período | Plano 5, T7 | COBERTO |
| MT-10 | Distribuição | Plano 5, T7 | COBERTO |
| MT-11 | Tabela por tipo | Plano 5, T7 | COBERTO |
| MT-12 | Tabela por prioridade | Plano 5, T7 | COBERTO |
| MT-13 | Tabela por squad | Plano 5, T7 | COBERTO |
| MT-14 | Tabela por pessoa | Plano 5, T7 (alfabética) | COBERTO |
| CR-01 | Pares | Plano 7, T1 a T3 | COBERTO |
| CR-02 | Checklists sem review | Plano 7, T1 | COBERTO |
| CR-03 | Ciclo da review | Plano 7, T2 | COBERTO |
| CR-04 | Review no ciclo do checklist | Plano 7, T2 | COBERTO |
| CR-05 | Review criada após o pai | Plano 7, T2 | COBERTO |
| CR-06 | Review vence depois | Plano 7, T2 | COBERTO |
| CR-07 | Mesma pessoa | Plano 7, T2 | COBERTO |
| CR-08 | Pares com alerta | Plano 7, T2 | COBERTO |
| CR-09 | Tabela de pares, lado a lado | Plano 7, T3 | COBERTO |
| CR-10 | Aprendizados | Plano 7, T2 | COBERTO |
| CR-11 | Duração × reabertura | Plano 7, T2 | COBERTO |
| CR-12 | Quem atrasa quem | Plano 7, T2 (vira "atrasos em cadeia", sem pares de pessoas) | COBERTO (reformulado) |
| CR-13 | Labels e lift | Plano 7, T2 | COBERTO |
| CR-14 | Diferenças de labels | Plano 7, T2 | COBERTO |
| IN-01 | Evidências | Plano 7, T6 e T7 | COBERTO |
| IN-02 | Pessoas alta/média | Plano 7, T6 | COBERTO |
| IN-03 | Issues afetadas | Plano 7, T6 | COBERTO |
| IN-04 | Mediana da taxa | Plano 7, T6 | COBERTO |
| IN-05 | Evidências por sinal | Plano 7, T4 e T5. 11 dos 12 sinais ativos; `ESTIMATIVA_AUMENTADA` fica sem dado | PARCIAL |
| IN-06 | Impacto nas metas | Plano 7, T6 | COBERTO |
| IN-07 | Tabela de evidências | Plano 7, T7 | COBERTO |
| IN-08 | Catálogo | Plano 7, T7 | COBERTO |
| CP-01 | Jain | Plano 6, T5 | COBERTO |
| CP-02 | Maior/menor ocupação | Plano 6, T5 (vira contagens, sem ranking) | COBERTO (reformulado) |
| CP-03 | Tabela por pessoa | Plano 6, T5 (horas registradas pelo worklog do Plano 6, T10) | COBERTO |
| CA-01 | Alertas de carga e ausência | Plano 6, T5 | COBERTO |
| CA-02 | Atrasos previstos | Plano 6, T5 | COBERTO |
| CA-03 | Atrasos por causa de ausência | Plano 6, T5 | COBERTO |
| CA-04 | Jain | Plano 6, T5 | COBERTO |
| CA-05 | Mapa de capacidade | Plano 6, T5 | COBERTO |
| CA-06 | Alertas (> 110%, ausência com trabalho) | Plano 6, T5 | COBERTO |
| CA-07 | Ausências planejadas | Plano 6, T5 | COBERTO |
| CA-08 | Feriados no horizonte | Plano 6, T5 | COBERTO |
| CA-09 | Lista de atrasos previstos | Plano 6, T5 | COBERTO |
| GA-01 | Fluxo cumulativo de 60 d | Plano 6, T7 | COBERTO |
| GA-02 | Estado das etapas (gargalo) | Plano 6, T7 | COBERTO |
| GA-03 | Acima da capacidade | Plano 6, T7 | COBERTO |
| GA-04 | Folga | Plano 6, T7 | COBERTO |
| DI-01 | Aguardando | Plano 7, T9 e T11 | COBERTO |
| DI-02 | Sugestões pendentes | Plano 7, T11 | COBERTO |
| DI-03 | Gerar sugestões (Jain antes → depois) | Plano 7, T9 | COBERTO |
| DI-04 | Ranking e fatores | Plano 7, T9 e T12 (fatores em ordem alfabética) | COBERTO (reformulado) |
| DI-05 | Decisão | Plano 7, T11 e T12 | COBERTO |
| RB-01 | Calcular | Plano 7, T10 | COBERTO |
| RB-02 | Movimentos pendentes | Plano 7, T11 | COBERTO |
| RB-03 | Decisão | Plano 7, T11 e T12 | COBERTO |
| QD (sem estimativa) | Qualidade dos dados | Plano 7, T13 | COBERTO |
| QD (sem responsável) | idem | Plano 7, T13 | COBERTO |
| QD (sem vencimento) | idem | Plano 7, T13 | COBERTO |
| QD (sem épico) | idem | Plano 7, T13 | COBERTO |
| QD (em andamento sem horas) | idem | Plano 7, T13 | COBERTO |
| QD (sem squad) | idem | Plano 7, T13 | COBERTO |
| QD (prioridade padrão) | idem | Plano 7, T13 (`prioridadePadrao`, vocabulário a confirmar) | COBERTO |
| AU-01 | Decisões de distribuição | Plano 7, T11 e T12 | COBERTO |
| AU-02 | Registro de auditoria | Plano 7, T14 | COBERTO |
| SI-01 | Estado da sincronização | Plano 4A, T8 | COBERTO |
| SI-02 | Como ligar ao Jira | Plano 4A, T9 (runbook) e T8 (nota na tela) | COBERTO |

Cards novos e propostas do relatório (seção 7.2) e da spec (seção 6.2):

| Card | Onde | Estado |
|---|---|---|
| Calendário do time | Plano 6, T6 e T8 | COBERTO |
| Cobertura por ausência | Plano 6, T13 e T15 | COBERTO |
| Plano de 4 a 8 semanas | Plano 6, T6 | COBERTO |
| Vencimentos da semana com folga real | Plano 6, T6 | COBERTO |
| Pessoas sem capacidade para novos itens | Plano 6, T5 (`comFolga`) e T13 | COBERTO |
| Posse da task | Plano 6, T11 | COBERTO |
| Esforço por task fechada | Plano 6, T12 | COBERTO |
| Simulador de ausência (substitui "aprovação de ausências") | Plano 6, T13 | COBERTO |
| Matriz de competências | Plano 6, T14 | COBERTO |
| Revisões pendentes por revisor | Plano 6, T14 | COBERTO |
| Carga por tipo/OS | Plano 6, T14 | COBERTO |
| Tendência de ocupação | Plano 6, T7 (com as horas atuais) | COBERTO |
| Auditoria de alterações | Plano 4, T7 e T8 | COBERTO |
| Divergência Task × Review | Plano 4, T5, T7 e T8 | COBERTO |
| 1:1/apoio, Planejamento de férias, Meta × realizado mensal, Saúde do squad (prioridade C) | — | FORA DE ESCOPO (não aprovados na spec 6.2) |

### B.2 Spec mestra, seção por seção

| Seção | Requisito | Onde | Estado |
|---|---|---|---|
| 2 Objetivos | Recriar os cards e criar os novos | Planos 5 a 7 (ver B.1) | COBERTO, salvo os PARCIAIS da B.1 |
| 2 Objetivos | Auditoria da seção 7 | Plano 4 | PARCIAL (ver 7.2 abaixo) |
| 2 Objetivos | Identidade Sidia, três temas, pt/en, AA | Plano 8, T4 e T5; logo e fonte sem plano | PARCIAL |
| 2 Objetivos | Cliente, dicionário e checklist prontos | Plano 4A, T3, T9 e T11; Plano 8, T8 | COBERTO |
| 2 Não objetivos | Escrita no Jira desligada | Plano 7, T11 (`SemEscritaNoJiraTest`) | COBERTO |
| 2 Antiobjetivos | Números divergentes entre telas | Plano 5, T9; Plano 7, T15 | PARCIAL (universos de "no prazo" diferentes, ver E) |
| 3 | Trava pela flag | Plano 4A, T1, T5, T6, T7 e T11 | COBERTO |
| 3 | Instância **Sidia** (projeto CAT, cards da automação); cada card declara a instância | Nenhum card lê a `sidia` (Plano 4A a mantém só nos testes e na demonstração) | SEM PLANO (falta decisão do usuário sobre quais cards) |
| 3 | Base legal com o DPO antes do uso real | Só registrado como pendência (Planos 4, 7 e 8); nada impede o uso no código | SEM PLANO |
| 4.1 | App Python substituído; histórico no git | Plano 8, T3 e T9 (tag `legacy-final`, porta de aprovação) | COBERTO |
| 4.2 | Fuso, dias úteis, concluída, atraso justificado, eventos pelo changelog, Central separada, previsão com chegada, "sem capacidade", horas por tipo, esforço | Planos 4, 5 e 6 | COBERTO |
| 4.2 | Heurísticas: **calibração depois, com dados reais** | Nenhum plano | SEM PLANO |
| 4.3 | Filtro por papel em todas as rotas; Integridade só do gerente; consultas registradas | Plano 8, T1; Plano 7, T7 | COBERTO |
| 4.3 | Campo `jira_sidia` (username diferente na Sidia) | Declarado fora (Plano 4A) | SEM PLANO (futuro) |
| 4.4 | Ausências em YAML somente leitura; simulador | Plano 6, T1, T13 e T16 | COBERTO |
| 4.5 | Temas e menu em 8 grupos | Plano 1 (`nav.ts`); Plano 8, T4 e T5 | COBERTO |
| 4.5 | Logo limpo e manual de marca | — | SEM PLANO (depende da Sidia) |
| 4.6 | Vigência dos papéis; retenção de 24 meses | Plano 1; Plano 4A, T7 | COBERTO |
| 4.6 | OIDC genérico atrás de interface | Plano 8, T2 (só com emissor falso) | PARCIAL |
| 4.6 | Provedor OIDC real, "antes de qualquer uso com dados reais" | — | SEM PLANO (depende da infraestrutura) |
| 4.6 | Volume: incremental a cada 10 min, reconciliação diária, dezenas de milhares de issues | Plano 4A; Plano 8, T6 | COBERTO |
| 4.6 | Subtask Review a confirmar na 1ª conexão | Plano 8, T8 (checklist) | COBERTO (como checklist) |
| 4.6 | Oráculo de números | Plano 8, T3 e os `CompatLegado*Test` | COBERTO |
| 4.6 | Ambiente: contêiner atrás do proxy | Plano 8, T7 (compose `prod` e documento) | PARCIAL (sem implantação real) |
| 5.1 | Dicionário; ids descobertos por nome | Plano 2; Plano 8, T8 | COBERTO |
| 5.2 | Ficha por card com exemplo numérico e aceite | Fichas criadas nos Planos 5 a 7. Nenhum teste exige ficha para cada item do menu (Central, Integridade, Auditoria) | PARCIAL |
| 5.3 | Cliente DC com retry, changelog completo e orçamento | Planos 1 e 3; Plano 4A, T3 | COBERTO |
| 5.4 | Incremental, reconciliação, retenção agendada | Plano 4A, T5 a T7 | COBERTO |
| 5.5 | Dicionário, matriz e checklist | Plano 4A, T9 e T11; Plano 8, T8 | COBERTO |
| 6.1 | Atraso justificado em Metas, Negativas, Fechadas e Meu painel | Plano 5, T6, T7 e T10; Plano 7, T16 | COBERTO |
| 6.1 | **Dicionário como filtros e agrupamentos** (OS e também Checklist Type, SKU Type, Model…) | Só o filtro `os` (Plano 5, T4) e Carga por tipo × OS (Plano 6, T14). Checklist Type e SKU Type são sincronizados e nenhum card os usa | PARCIAL |
| 6.1 | Tempo em status: 1ª entrada e última saída de In Progress, outliers acima de 30 d, percentis por tipo | Plano 3 (`lastLeft`, `OUTLIER_DIAS = 30` no código); Plano 5, T3 | COBERTO |
| 6.2 / 6.3 | Cards novos; posse proporcional | Plano 6 | COBERTO |
| 7.1 | Quem pode alterar, com vigência por data | Plano 4, T2 | COBERTO |
| 7.2 | 11 sinais da tabela | Plano 4, T3 a T5 | COBERTO |
| 7.2 | **Estimativa reduzida ou worklog retroativo** | Fora por decisão (Plano 4, decisão 7). O worklog passa a existir no Plano 6, T10, e nenhum plano volta ao sinal | SEM PLANO |
| 7.2 | 12 sinais de integridade | Plano 7, T4 e T5 (`ESTIMATIVA_AUMENTADA` inativo, sem dado) | PARCIAL |
| 7.3 | Indício, não prova; consulta auditada; papéis | Plano 4, T7 e T8; Plano 7, T7 | COBERTO |
| 8 | Fases 1 a 8 | Planos 1 a 8 | COBERTO |
| 9 | Biblioteca de gráficos no React | Nenhum plano escolhe nem cria o componente; as telas pedem gráficos (GA-01, FE-08, VG-10…) | SEM PLANO (escolha implícita do executor) |
| 9 | Fórmula do esforço; severidades; OIDC; dados demo; fonte de sistema | Plano 6, T12; Plano 4, T1; Plano 8, T2; Plano 4A, T10 | COBERTO |
| 10 | Zero requisições com a flag `false` | Plano 4A (`FlagDesligadaZeroRequisicoesTest`) | COBERTO |
| 10 | Ficha, JQL e aceite por card | Planos 5 a 7 | PARCIAL (ver 5.2) |
| 10 | Card = tamanho da lista; mesmo dia em todas as telas | Plano 5, T9; Plano 7, T15 | COBERTO (com a ressalva da E) |
| 10 | Testes de autorização em todas as rotas | Plano 8, T1 | COBERTO |
| 10 | Auditoria detecta **cada** sinal da seção 7 | Plano 4, T5 cobre 11; estimativa e worklog ficam fora | PARCIAL |
| 10 | pt-BR e inglês, três temas, AA | Plano 8, T4 e T5; paridade de chaves no Plano 5, T4 | COBERTO |
| 10 | Documentos de preparação | Plano 8, T8 | COBERTO |
| 11 | Cliente nunca testado no servidor real | Checklist e runbook (Plano 4A, T9 e T11). Nenhum plano corrige o que a 1ª conexão revelar | PARCIAL |
| 11 | Ids de campos; Reporter como PL | Plano 8, T8 (só no checklist) | PARCIAL |
| 11 | LGPD (base legal, comunicação à equipe) | — | SEM PLANO |

### B.3 CLAUDE.md, PRODUCT.md e plano visual

| Item | Onde | Estado |
|---|---|---|
| Menu em 8 grupos e início por papel | Plano 1 (`nav.ts`); Plano 7, T17 | COBERTO |
| Seção 5.1 a 5.4: cabeçalho, filtros na URL, KPIs, no máximo dois gráficos e uma tabela | Kit de tela do Plano 5, T4 | COBERTO |
| Seção 5.5: tabela ordenável e **paginação virtual** em tabelas grandes (seção 7) | — | SEM PLANO |
| Seção 5.6: detalhamento em janela, **nova aba**, CSV e coluna "Detalhe" | Drill e CSV nos Planos 3 a 5; nova aba sem plano | PARCIAL |
| Seção 5.7: **gaveta universal da task** (status, datas, histórico com autorização, tempo por status, posse, fatores de risco) | Só a gaveta de histórico da Auditoria (Plano 4, T8), restrita a coordenação e gerente | SEM PLANO |
| Seção 5.8: estados de tela (esqueleto, vazio, erro com "tentar de novo", sem permissão, dados incompletos com link para a Qualidade) | Só o 403 é testado (Planos 4 a 7) | PARCIAL |
| Wireframe 6.4: "link para o Jira" na gaveta | — | SEM PLANO |
| Paleta categórica de 6 cores, distinguível por daltônicos, com padrão e forma | Plano 8, T4 testa só os tokens de texto e estado | SEM PLANO |
| Wireframes 6.1 a 6.5 | Plano 7, T17; Plano 3; Plano 6, T8; Plano 4, T8; Plano 7, T17 | COBERTO |
| Seção 9: `DESIGN.md` do Impeccable | — | SEM PLANO |
| Seção 8, itens 1 e 2: logo, manual, fonte Mark, cores conferidas no manual | — | SEM PLANO (depende da Sidia) |
| Azul de marca: `#1329A0` (spec e logo) × `#0023A4` (tokens do plano visual) | Minor adiado; nenhum plano decide | SEM PLANO (decisão) |
| PRODUCT.md, princípio 4 ("nada do Jira real afirmado antes de validado") | Exige a validação pós-conexão | SEM PLANO |
| Docker sempre ativo; Testcontainers | Todos os planos | COBERTO |

### B.4 Pendências conhecidas

| Pendência | Origem | Onde | Estado |
|---|---|---|---|
| M3 (montagem do Syncer), M4 (retenção de `issue` e `access_log`), M5 (drill), M7 (vocabulário) | final-review | Plano 4A, T5 a T8 | COBERTO |
| I1 (aviso bloqueante), I2 (`change_event` com texto livre) | final-review | Dados como corrigidos na PR #9 (Plano 4A, Autoavaliação) | COBERTO |
| B1: visibilidade do repositório contraditória | final-review | HANDOFF e guia dizem "PRIVADO"; a `MEMORY.md` diz "repo público" | SEM PLANO (decisão do usuário) |
| Rulings reversíveis da 1ª conexão | HANDOFF | Plano 8, T8 (checklist); nenhum plano aplica as correções | PARCIAL |
| Avisos do backend só em pt-BR | HANDOFF | Plano 4A, T8 (códigos e `params` só no Tempo em status); demais cards com `AvisoDto` por código | PARCIAL |
| Filtros do frontend em texto livre | HANDOFF | `Filtros.tsx` (Plano 5, T4). Nenhum endpoint de opções: `MeDto` tem só `userId` e `roles` | SEM PLANO |
| `status_map.yaml` real no `.gitignore` | HANDOFF | Plano 4A, T9 | COBERTO |
| `.dockerignore` por lista de permissão | Minor adiado | — | SEM PLANO |
| Parâmetro `settingsIgnorado`; limites da varredura da fábrica; inverter `exigeAutenticacao` | Minor adiado | Plano 8, T1 cobre o efeito, não o código | SEM PLANO |
| Teste do `delay` do retry com cancelamento; máscara do token escapado; `resolverDentro` com symlink | Minor adiado | — | SEM PLANO |
| Acessibilidade da casca (contraste de `--line`, piscada do tema) | Minor adiado | Plano 8, T4 (contraste); piscada sem plano | PARCIAL |
| CSP e X-Frame-Options | Minor adiado | — | SEM PLANO |
| Imagens Docker por digest | Minor adiado | — | SEM PLANO |
| Imagem com `DASHBOARD_DEV_MODE=true` por padrão | Minor adiado | Plano 8, T7 só no perfil `prod` do compose; o padrão da imagem fica | PARCIAL |
| `SearchRequest(jql: String)` sem tipo | HANDOFF | — | SEM PLANO |
| `RetentionTest` intermitente (Ryuk) | HANDOFF | — | SEM PLANO |
| Webhook sem segredo em `legacy/` | HANDOFF | Plano 8, T9 (remoção) | COBERTO |
| `legacy/.env.example` modificado | HANDOFF | Plano 8, T9 (pré-condição) | COBERTO |
| Automações (subagentes, skills, MCP Playwright e context7) | HANDOFF | — | SEM PLANO (baixa) |
| Logo, DPO, OIDC real e infraestrutura | HANDOFF | — | SEM PLANO (dependem de terceiros) |

### B.5 O que um sistema em produção precisa

| Necessidade | Onde | Estado |
|---|---|---|
| SSO/OIDC: login, sessão e logout | Plano 8, T2 (PKCE, cookie seguro, logout; emissor falso) | PARCIAL |
| SSO/OIDC: provedor real | — | SEM PLANO (depende da infra) |
| Sessão expirada na SPA (401 → login) | Plano 8, T2 só põe o botão "Sair" | SEM PLANO |
| CSRF no único POST (`/api/distribuicao/decisao`) | Mitigado só por `SameSite=Lax` | SEM PLANO |
| Implantação: imagem e compose | Plano 8, T7 (perfil `prod` endurecido, `verificar-imagem.sh`) | COBERTO (local) |
| Implantação: destino real (VM ou k8s), registro de imagem, versão e release, segredos (cofre), HTTPS no proxy | `docs/implantacao.md` (Plano 8, T7) só lista o que pedir à infra | SEM PLANO |
| CI (GitHub Actions) | Não há workflow no repositório nem no histórico (`git log --all -- .github` vazio) | SEM PLANO |
| Backup e restauração do PostgreSQL | Plano 8, T7 só documenta (`pg_dump`/`pg_restore`); sem backup agendado nem teste automatizado de restauração | PARCIAL |
| Migrações (Flyway, reversão de versão) | Flyway nos planos; reversão só documentada (Plano 8, T7) | PARCIAL |
| Observabilidade: logs estruturados sem segredos, métricas, readiness com banco, alertas (sync suspensa, PAT expirando) | `/api/health` existe; status da sync só na tela do gerente | SEM PLANO |
| Configuração administrativa: onde ficam os YAML reais, quem edita, validação antes de subir, recarga | Plano 7, T14 mostra hash e versão (só leitura); falha na subida se inválido | SEM PLANO |
| LGPD/DPO: inventário dos dados tratados, base legal, retenção final, direitos do titular, comunicação à equipe | Retenção técnica (Plano 4A, T7); o resto não | SEM PLANO |
| Identidade visual final | — | SEM PLANO (depende da Sidia) |
| Internacionalização completa | Paridade de chaves (Plano 5, T4); o guia sai só em pt-BR (Plano 8, T10); mensagens 400 do backend sem código | PARCIAL |
| Acessibilidade | Plano 8, T4 (contraste, teclado, rótulos; sem leitor de tela real) | COBERTO |
| Desempenho e volume | Plano 8, T6 (30 mil issues, orçamentos) | COBERTO |
| Documentação de usuário | Plano 8, T10 (guia em pt-BR) | COBERTO |
| Documentação de operação | `implantacao.md` e runbook da sync; sem runbook diário nem de incidentes | PARCIAL |
| Runbook da 1ª conexão | Plano 4A, T9 e T11; Plano 8, T8 | COBERTO |
| Runbook da operação diária (sync suspensa, `RECONCILIACAO_SUSPEITA`, rotação de PAT e `SESSION_SECRET`, feriados de cada ano, entrada e saída de pessoas) | — | SEM PLANO |
| Treinamento e handoff ao time | — | SEM PLANO |
| Validação dos números com dados reais e calibração | — | SEM PLANO |

---

## (C) Lacunas priorizadas

Esforço: P (até 3 tarefas), M (4 a 8), G (mais de 8 ou com várias rodadas).

### Crítica (impedem chamar o projeto de terminado ou de usável em produção)

| # | O que falta | Por que importa | Onde deveria entrar | Esforço | Depende do usuário/infra |
|---|---|---|---|---|---|
| C1 | **CI no GitHub Actions**: `gradlew test` com Testcontainers, `pnpm vitest` e `build`, `test-guard.sh` e varredura de dados pessoais e segredos no diff | Outra IA vai executar e mesclar seis planos sem nenhuma barreira automática; a regra "master sempre verde" depende de rodar a suíte manualmente | Plano 9 (antes do 4A) | P/M | Não (só confirmar o runner com Docker) |
| C2 | **Validação com dados reais depois da 1ª conexão**. Configs reais (`jira_campos`, `status_map`, `papeis`, `equipes`, `ausencias`, `calendario`). Contagem de cada ficha contra o Jira. Aplicação dos rulings reversíveis (aspas, `resolved <` dia seguinte, `pageSize`, changelog, worklog, `from/to` do assignee, Reporter = PL). Matriz preenchida. Aceite do gestor card a card | O CLAUDE.md prevê a flag ligada a partir de 26/10. Nada garante que os números com dados reais estejam certos (PRODUCT.md, princípio 4; spec 11) | Plano 14 | G | Sim (flag, dados, aceite) |
| C3 | **LGPD/DPO**: inventário dos dados tratados (issue, changelog, worklog, `access_log`, decisões), finalidade, base legal, retenção final, direitos do titular (acesso ao próprio registro) e comunicação à equipe. Além disso, uma **trava em código** que mantenha Auditoria e Integridade desligadas em produção até o aceite do DPO | A spec (3, 7.3 e 11) exige a validação "antes do uso real". Hoje só um aviso na tela separa o painel do uso real | Plano 11 | M | Sim (DPO) |
| C4 | **Sinais da spec 7.2 "estimativa reduzida" e "worklog retroativo"**. O worklog passa a existir no Plano 6, T10, e nenhum plano volta ao sinal. A estimativa exige gravar o valor de `timeoriginalestimate` e `timeestimate` no `change_event` | O critério de sucesso 10 é que a auditoria "detecta cada sinal da seção 7"; hoje fica em 11 de 13. O sinal `ESTIMATIVA_AUMENTADA` da Integridade depende do mesmo dado | Plano 11 | M | Sim (ampliar a lista de campos com valor no changelog) |
| C5 | **Implantação real**: provedor OIDC concreto, proxy e TLS, destino (VM ou k8s), registro e versionamento da imagem, cofre de segredos, primeira subida com `DASHBOARD_DEV_MODE=false`, testes rápidos de fumaça e reversão | Sem isso o painel só roda em `127.0.0.1` com dados de demonstração | Plano 13 | G | Sim (infra da empresa) |

### Alta

| # | O que falta | Por que importa | Onde | Esforço | Depende |
|---|---|---|---|---|---|
| A1 | **Configuração administrativa**: onde ficam os YAML reais, quem edita, um validador (`painel validar-config`, rodado no CI), recarga ou reinício documentado, procedimento por PR num repositório privado de configuração | Ausências mudam toda semana; um YAML inválido derruba a subida em produção | Plano 12 | M | Sim (onde ficam os arquivos) |
| A2 | **Observabilidade**: logs JSON sem segredos (com teste), métricas, readiness que confere o banco, alerta de sync suspensa, `RECONCILIACAO_SUSPEITA` e PAT expirando | Uma suspensão por 401 só aparece se o gerente abrir a tela; o painel fica com dados velhos sem aviso | Plano 12 | M | Parcial (canal de alerta) |
| A3 | **Endurecimento web**: CSP, X-Frame-Options, Referrer-Policy; CSRF no POST de decisão; SPA tratando 401 e sessão expirada; imagem com `DASHBOARD_DEV_MODE=false` por padrão; imagens por digest; `.dockerignore` por lista de permissão | São Minor adiados que viram risco real em produção (clickjacking, dev mode acidental com todos os dados) | Plano 9 | M | Não |
| A4 | **Endpoint de opções de filtro** (squads e pessoas visíveis, tipos, OS) | O Plano 4, T8 testa "opções do filtro de pessoa em ordem alfabética" e nenhum plano produz a fonte; `MeDto` não traz isso. Pendência "filtros em texto livre" do HANDOFF | Ajuste no Plano 5, T4 (ou já no Plano 4, T7) | P | Não |
| A5 | **Filtros e agrupamentos pelo dicionário além de OS** (Checklist Type, SKU Type, Model Name/Category, Carrier, TG Request Type) | Spec 6.1 aprovada; os campos são sincronizados (Plano 4A, T3) e nenhum card os usa | Plano 10 | M | Não |
| A6 | **Cards da instância Sidia (CAT)** | A spec 3 prevê "cards próprios da automação" na Sidia. Nenhum plano define ou lê esses cards | Plano 14 (após decisão) | M | Sim (quais cards) |
| A7 | **Runbook de operação diária e de incidentes**: sync suspensa, reconciliação suspeita, rotação de PAT e `SESSION_SECRET`, restauração de backup, feriados de cada ano (o calendário vai até 2030), entrada e saída de pessoas, suspeita de vazamento | Sem isso a operação depende de quem escreveu o código | Plano 12 | P/M | Não |
| A8 | **Decisão sobre a visibilidade do repositório** (final-review B1) | A `MEMORY.md` diz "público"; HANDOFF e guia dizem "PRIVADO, com usernames reais no histórico". Se for público, é um incidente de dados pessoais | Decisão imediata (não é plano) | P | Sim |

### Média

| # | O que falta | Por que importa | Onde | Esforço | Depende |
|---|---|---|---|---|---|
| M1 | **Gaveta universal da task** (plano visual 5.7): status, datas, histórico, tempo por status, posse, fatores de risco, link para o Jira | É o padrão de detalhe de todas as telas; hoje só a Auditoria tem gaveta | Plano 10 | M | Não |
| M2 | **Biblioteca de gráficos e paleta categórica acessível** (padrão e forma além da cor) | A spec 9 deixa a escolha ao construtor e ninguém a faz; cada tela poderia improvisar | Ajuste no Plano 5, T4 (kit) | P | Não |
| M3 | VG-02 (concluídas em 30 d com variação) e VG-03 (ciclo P50/P85 em 30 d) na Central | IDs do relatório sem destino; o Plano 8, T3 vai classificá-los como "removidos" sem decisão | Ajuste no Plano 7, T15 | P | Sim (manter ou remover) |
| M4 | FE-11 (tempo ativo), FE-12 (tabela de concluídas Real÷estim.), NE-09 ("maior tempo por status") | Equivalência incompleta com o app atual | Ajuste no Plano 5, T6 e T10 | P | Não |
| M5 | Paginação virtual de tabelas grandes; estados de tela testados (esqueleto, vazio, erro com "tentar de novo", dados incompletos com link para a Qualidade) | Plano visual 5.8 e 7; com dezenas de milhares de issues, um drill grande trava o navegador | Plano 10 | M | Não |
| M6 | **Calibração das heurísticas** (risco, integridade, distribuição, foco, esforço) com dados reais | Spec 4.2 e 9; os pesos herdados não têm base | Plano 14 | M | Sim (dados e gestor) |
| M7 | **i18n completa**: guia do painel em inglês, mensagens 400 do backend por código, datas e números por idioma testados | Spec 10 (pt-BR e inglês); o Plano 8, T10 tira o inglês do guia | Plano 15 (guia); Plano 10 (código) | P/M | Não |
| M8 | **Minor técnicos adiados**: `settingsIgnorado`, varredura da fábrica (alias e `typealias`), inverter `exigeAutenticacao`, `delay` do retry com cancelamento, máscara do token escapado, `resolverDentro` com symlink, `SearchRequest` tipado com `JqlQuery`, `RetentionTest` intermitente | Dívida de segurança e de teste já registrada nos ledgers | Plano 9 | M | Não |
| M9 | **Identidade visual final**: logo em SVG, manual, fonte Mark, decisão `#1329A0` × `#0023A4`, piscada do tema | Spec 4.5 e 11; plano visual 8 | Plano 15 | P | Sim (Sidia) |
| M10 | **Treinamento e handoff ao time**: sessão por papel, perguntas frequentes, dono da manutenção | Sem isso o time não sabe ler "no prazo × ajustado", "indício, não prova", "sem capacidade" | Plano 15 | P | Sim |

### Baixa

| # | O que falta | Onde | Esforço | Depende |
|---|---|---|---|---|
| B1 | `DESIGN.md` do Impeccable (plano visual 9) | Plano 15 | P | Não |
| B2 | Automações recomendadas no HANDOFF (subagentes, skills, MCP) | Fora dos planos; só se o usuário pedir | P | Sim |
| B3 | Cards de prioridade C do relatório (1:1, férias, meta × realizado mensal, saúde do squad) | Fora de escopo até aprovação | M | Sim |
| B4 | Teste que exija ficha `cards/<id>.card.yaml` para cada item do menu | Ajuste no Plano 8, T10 | P | Não |

**Totais:** 5 críticas, 8 altas, 10 médias, 4 baixas (27 lacunas).

---

## (D) Proposta de novos planos

| Plano | Nome | Escopo (lacunas) | Depende de | Ordem | Tarefas (estimativa) |
|---|---|---|---|---|---|
| 9 | **CI e endurecimento** | C1, A3, M8. Workflow com backend (Testcontainers), frontend, `test-guard.sh` e varredura de dados pessoais; proteção da `main` documentada; CSP e cabeçalhos; CSRF; 401 na SPA; imagem com dev mode desligado; digest; `.dockerignore` por lista de permissão; Minor técnicos | Nada (base atual) | **Antes do 4A** | 10 a 12 |
| 10 | **Complementos de telas e filtros** | A5, M1, M5, M7 (código). Filtros pelo dicionário; gaveta universal da task com link para o Jira; paginação virtual; estados de tela; mensagens do backend por código. **Ajustes nos planos existentes, em vez de plano novo:** A4 e M2 no Plano 5, T4; M3 no Plano 7, T15; M4 no Plano 5, T6 e T10; B4 no Plano 8, T10 | 5, 6 e 7 | Depois do 7, antes do 8 (o 8 faz a revisão visual e AA final) | 9 a 11 |
| 11 | **Auditoria completa e LGPD** | C3 e C4. Sinal de worklog retroativo; estimativa reduzida (e `ESTIMATIVA_AUMENTADA`) com campos de valor no `change_event`; trava em código para Auditoria e Integridade até o aceite do DPO; inventário de dados tratados; retenção final; acesso do titular ao próprio registro; texto de comunicação à equipe | 4, 6 (worklog) e 7 | Depois do 7. **As tarefas de LGPD só se detalham depois de ouvir o DPO** | 8 a 10 |
| 12 | **Operação em produção** | A1, A2, A7 e o backup automatizado. Logs JSON, métricas, readiness, alertas; validador de configuração no CI; procedimento de edição dos YAML; backup agendado com teste de restauração (Testcontainers); runbook diário e de incidentes; rotação de segredos; calendário anual | 8 e 9 | Depois do 8 | 10 a 12 |
| 13 | **Implantação real e entrada em produção** | C5. Provedor OIDC concreto, proxy e TLS, VM ou k8s, registro e release da imagem, cofre de segredos, homologação com dev mode desligado, testes de fumaça e reversão | 8, 9 e 12 | Depois do 12. **Só se detalha depois que a infraestrutura definir o provedor e o destino** | 8 a 12 |
| 14 | **1ª conexão, validação com dados reais e calibração** | C2, A6 e M6. Rodar o checklist; configs reais; contagem por ficha contra o Jira; aplicar os rulings reversíveis; atualizar a matriz; cards da Sidia/CAT (após decisão); calibrar heurísticas; aceite do gestor card a card | 4A e a flag ligada pelo usuário. Os números dos cards dependem dos Planos 5 a 7 | A parte de sincronização pode começar logo depois do 4A; a validação dos cards, depois do 7 | 10 a 14 (pode virar 14a e 14b) |
| 15 | **Identidade final, inglês e entrega ao time** | M7 (guia), M9, M10 e B1. Logo, fonte, tokens finais, `DESIGN.md`, guia em inglês, treinamento por papel, perguntas frequentes, dono da manutenção | 8 e os ativos da Sidia | Último | 8 a 10 |

Ordem sugerida:
- 9;
- 4A (e, com a flag ligada, o início do 14 só para a sync);
- 4;
- 5 (com os ajustes);
- 6;
- 7 (com os ajustes);
- 10;
- 11;
- 8;
- 12;
- 13;
- 14 (validação completa);
- 15.

O 11 vem antes do 8 para que a conferência de equivalência e o inventário de rotas do 8 já incluam os sinais novos e a trava da LGPD.

---

## (E) Inconsistências entre os planos 4A a 8

### Críticas (fazem outra IA travar ou divergir)

1. **Os artefatos de planejamento não estão no git.**
   - **Fora do controle de versão:** `docs/superpowers/plans/2026-10-25-plano-05…`, `-06…`, `-07…`, `-08…`, a spec da fase 06, `docs/guia-execucao-outra-ia.md` e `.clinerules/`.
   - **Modificados e sem commit:** `CLAUDE.md`, `HANDOFF.md`, o índice e os planos 4 e 4A.
   - **Consequência:** quem fizer checkout da `main` não vê os planos 5 a 8. Lá, a regra 1 do `CLAUDE.md` ainda exige "autorização expressa por pesquisa" mesmo com a flag `true`, o que contradiz o 4A (T11 e decisão 1) e o 8 ("a flag é a única trava"). O `PRODUCT.md` e a `matriz-compatibilidade.md` também ainda falam em "autorização expressa" (o Plano 8, T8 e T10 só corrige isso no fim).
2. **Specs de fase citadas e inexistentes.**
   - O 4A manda "ler primeiro" `docs/superpowers/specs/2026-10-25-spec-fase-04a-sincronizacao.md`.
   - O 7 cita `…spec-fase-07-checklist-integridade-distribuicao-governanca.md`.
   - O 8 cita `…spec-fase-08-preparacao-final-e-remocao-do-legacy.md`.
   - Só existe a da fase 06.
3. **Dependência oculta do Plano 4 no 4A.** O Plano 4 e o índice dizem que o 4 depende do 4A "só pelos dados reais". Mesmo assim, o Plano 4:
   - usa `JqlValues.issueKey` (T7: validar a chave do histórico), que nasce no 4A, T3;
   - usa `AvisoDto` com `params` (T5: `REPORTER_ALTERADO n`, `VENCIMENTO_ILEGIVEL n`), que nasce no 4A, T8.

   Executado antes do 4A, o 4 não compila. A ordem 4A → 4 resolve, mas o texto precisa dizer isso.
4. **Um mesmo teste com três nomes.**
   - Plano 4, T9: manda manter `cadaPassoDoChecklistExigeAutorizacaoExpressa` passando.
   - Plano 4A, T11: troca esse teste por `cadaPassoDoChecklistConfirmaAFlag`.
   - Plano 8, T8 e Review Focus 6: cria `cadaPassoDoChecklistExigeAFlag` para "substituir" o primeiro nome.

   Na ordem 4A → 4, o Passo 1 da T9 do Plano 4 pede um teste que já não existe.
5. **"No prazo" com universos diferentes entre telas** (antiobjetivo da spec 2).
   - O `NoPrazo.calcular` do Plano 4 conta DONE + `resolution`, inclusive resoluções negativas (`Won't Do`) e Epic. Ele nem conhece as resoluções negativas, que só entram no `StatusMap` no Plano 5, T1.
   - Fechadas (Plano 5, T6) "delega a `NoPrazo`".
   - Metas (Plano 5, T7) usa "concluídas positivas, exclui Epic, vencimento no período".
   - O teste `mesmoNoPrazoEmTresTelas` passa no TRB-1 só porque a cancelada TG-115 não tem vencimento.
   - **Correção:** o Plano 5, T7 deve levar `NoPrazo` a excluir as negativas e o Epic, e acrescentar um caso cancelado com vencimento.
6. **O Plano 5 tem duas decisões abertas** ("Decisões que precisam do usuário": issues sem responsável para o Squad Leader; P50/P85 do USUARIO). Os Planos 6 a 8 têm as decisões delegadas e resolvidas. Uma IA que execute "só os planos" para no Plano 5.

### Menores

7. O índice diz que o 4A tem 9 tarefas; o plano tem 11 (T10, dados de demonstração; T11, hook e checklist).
8. Dois códigos para o mesmo aviso: `CONCLUSAO_DIVERGENTE` (4A, T8) e `DONE_SEM_RESOLUTION` (Planos 5 e 7; o 7 acrescenta `RESOLUTION_SEM_DONE`).
9. O mesmo nome simples `Agenda` em dois lugares: `object Agenda` em `sync/` (4A, T7) e `class Agenda` em `pessoas/` (6, T4). O Plano 5 diz que "`PrevisaoDeTermino` é implementada pela `Agenda`", o que é ambíguo.
10. Arquivo de progresso: os planos falam em `progress.md` (ledger local do Claude, ignorado pelo git); o guia manda criar `docs/superpowers/progresso/<plano>.md` (versionado).
11. Papéis no menu × API:
    - Posse: o menu é só da liderança, mas o teste `pessoaInvisivelViraNula` (6, T11) chama a API como USUARIO.
    - Visão do squad: o menu é só do Squad Leader, mas a API (7, T16) aceita coordenador e gerente.
12. O Plano 5, T4 move o `DrillDrawer` para `shared/`, e a tela de Auditoria (4, T8) importa do caminho antigo. O plano não manda atualizar.
13. Leitura de filtros duplicada: a Auditoria (4, T7) tem o próprio parser de `de`/`ate`/`squad`/`pessoa`, e o `FiltrosDeCard` (5, T4) só refatora o Tempo em status.
14. O 4A grava `fields` novos na ficha de sincronização nos Planos 5, T1 e 6, T10. As issues já gravadas antes disso ficam sem prioridade e sem worklog até a próxima atualização. Só o aviso `CAMPOS_DE_TRABALHO_INDISPONIVEIS` cobre isso; nenhum plano agenda uma ressincronização completa depois do 5 e do 6.

### Migrações

| Plano | Migração | Número na execução sequencial |
|---|---|---|
| 4A, T2 | `V4__sincronizacao.sql` (fixo no plano, com a regra "próximo livre") | V4 |
| 4 | nenhuma | — |
| 5a, T1 | `V{n}__campos_de_trabalho.sql` | V5 |
| 6b, T10 | `V{n}__worklog_por_instancia.sql` | V6 |
| 7b, T11 | `V{n}__decisao_distribuicao.sql` | V7 |
| 8, T6 | `V{n}__indices_de_leitura.sql` (só se o orçamento estourar) | V8 condicional |

Executados em sequência, os planos não têm conflito de numeração. O risco aparece só se blocos (5a/5b, 6a/6b, 7a/7b) ou planos rodarem em branches paralelas. Não há dependência circular. Há apenas:
- condicionais nos dois sentidos entre o 4A e o 4 (`removida_em` no `AuditStore`);
- planos posteriores que modificam código de planos anteriores (o 6 altera `CardRiscos`/`PrevisaoCalculator` do 5; o 7 altera `RegrasDePlanejamento` do 6), o que exige seguir a ordem.

### Interfaces consumidas sem produtor

- Opções de filtro (squads, pessoas, tipos, OS) para as telas: o Plano 4, T8 e o Plano 5, T4 consomem, e ninguém produz (`MeDto` só tem `userId` e `roles`).
- Biblioteca e componente de gráfico: os Planos 5 a 7 pedem gráficos, e nenhuma tarefa cria o componente.
- Ficha para Central, Integridade e Auditoria: o Plano 8, T10 (`guiaCitaCadaFicha`) supõe que exista uma ficha por item do menu, e nenhum plano as cria.
