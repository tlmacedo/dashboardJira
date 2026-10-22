# Distribuição e balanceamento de carga de trabalho entre membros de uma equipe (Jira)

Data de referência: 2026-10. Legenda: "Cited Findings" = confirmado em fonte consultada (algumas são resumos de busca, não leitura integral da página; sinalizado). "Inferences" = raciocínio meu ou conhecimento geral NÃO verificado nesta sessão. Limitação: o Planalto (LGPD) e a referência REST da Atlassian não puderam ser lidos (503 / conteúdo truncado).

## 1. Algoritmos de atribuição (round robin, least loaded, ponderado, skill/afinidade, justiça, Hungarian, power of two choices)

### Takeaway
O Jira Cloud nativo oferece só três métodos simples: round robin, balanced workload (menos issues abertas) e random. Ponderação por esforço, capacidade/ausências e afinidade precisam ser implementadas por você (consulta JQL + score próprio) ou por apps. A teoria (power of two choices, Jain, max-min) dá base para uma heurística gulosa explicável.

### Cited Findings
- Atlassian Cloud descreve 3 métodos de distribuição: round robin (ordem fixa A, B, C...), balanced workload ("o membro com menos work items abertos") e random (sorteio de lista, role ou grupo, sem considerar carga). Artigo só se aplica à Cloud. — [Atlassian KB](https://support.atlassian.com/jira/kb/jira-automation-rule-to-auto-assign-issues-based-on-role-or-group/); versão para Automation Server/DC: [Smart assign Jira issues](https://confluence.atlassian.com/automation074/smart-assign-jira-issues-load-balancing-round-robin-and-more-1141481228.html)
- O artigo não define como "aberto" é contado no balanced workload (lógica exata não documentada na página lida; resumo por modelo auxiliar). — [Atlassian KB](https://support.atlassian.com/jira/kb/jira-automation-rule-to-auto-assign-issues-based-on-role-or-group/)
- Power of two choices: cada item vai para o menos cheio entre d=2 candidatos aleatórios; melhora exponencialmente a carga máxima vs. 1 escolha (balls-into-bins) e, no modelo de filas ("supermarket"), o tempo médio no sistema. — [Mitzenmacher, TPDS 2001](https://www.eecs.harvard.edu/~michaelm/postscripts/tpds2001.pdf); [Survey Mitzenmacher/Richa/Sitaraman](https://www.eecs.harvard.edu/~michaelm/postscripts/handbook2001.pdf)
- Max-min fairness: maximiza a menor alocação possível entre as viáveis. Jain's index = (Σx)² / (n·Σx²); independente de escala; vale 1 quando todos iguais; mínimo 1/n (um só recebe tudo). Jain normaliza x_i pela alocação "ótima" (ex.: max-min) — o que é útil para ponderar por capacidade. — [Jain, af9409-fair](https://research.engineering.wustl.edu/~jain/atmf/ftp/af9409-fair.txt)
- Apps de Marketplace citam estratégias: round robin simples, contagem de issues abertas e "workload"; weighted/biased round robin (Smartload), regras por projeto, escolha de tipos/status que contam na carga. — [Round Robin Assignment (legado Server)](https://marketplace.atlassian.com/apps/1220401/round-robin-assignment-for-jira-jsd); [Smartload JSM](https://marketplace.atlassian.com/apps/132939184/smartload-for-jira-service-management)

### Inferences
(Conhecimento geral de escalonamento, não verificado em fonte nesta sessão.)
- Round robin: simples, determinístico, auditável; quebra com issues de tamanhos heterogêneos, ausências e membros com ritmos distintos (iguala contagem, não carga).
- Least loaded por contagem: ignora tamanho; 5 bugs triviais pesam como 5 épicos. Melhor medir por esforço restante (story points/horas estimadas menos tempo gasto) com fallback para contagem quando não há estimativa. Herding: se várias issues chegam juntas e a carga é lida defasada, todas vão para a mesma pessoa — mitigar atualizando a carga a cada atribuição (ou power of two choices).
- Score recomendado (proposta): para cada candidato elegível, `carga_normalizada = esforço_aberto / capacidade_disponível_no_horizonte`; custo = carga_normalizada + penalidade_WIP + bônus_afinidade (negativo) + penalidade_skill_faltante. Escolher menor custo; mostrar os termos ao gerente. Capacidade = dias úteis no horizonte (menos férias/feriados/ausências, fonte: calendário) × fator de foco × throughput histórico suavizado e com teto (ver seção 2).
- Hungarian/min-cost flow: ótimo global para lote simultâneo (n issues × m pessoas com custos), útil em planejamento de sprint; para fluxo contínuo (issues chegando uma a uma) o guloso com re-leitura de carga é suficiente e mais explicável. Hungarian quebra se custos forem subjetivos/instáveis e quando há capacidade de múltiplas issues por pessoa (aí usar min-cost flow ou ILP).
- Métricas de equidade para dashboard: Jain sobre `carga/capacidade` (1 = perfeito), coeficiente de variação e razão max/mediana; Gini é alternativa mas Jain/CV bastam para times pequenos (n<15, onde Gini é ruidoso).
- Max-min fairness aplicado: prioriza elevar quem está com menor carga normalizada, que é exatamente o "least loaded normalizado".

### Gaps
- Não encontrei fonte para a fórmula exata do "balanced workload" da Atlassian (quais status/tipos contam).
- Sem fonte primária lida para Hungarian/min-cost flow e Gini (apenas conhecimento geral).

## 2. Igualdade x produtividade; "castigo do mais produtivo"; WIP

### Takeaway
Equalizar a carga normalizada pela capacidade (não a contagem) evita punir o rápido sem dar a ele carga infinita; baixa performance deve ser tratado por gestão humana, fora do algoritmo de atribuição. Não consegui fontes primárias; seção é majoritariamente inferência.

### Cited Findings
- Nenhuma fonte primária consultada nesta sessão sobre WIP por pessoa ou sobre o efeito "castigo do produtivo".
- Jain normalizado pela alocação ótima (capacidade) é o mecanismo formal para "justiça ponderada". — [Jain](https://research.engineering.wustl.edu/~jain/atmf/ftp/af9409-fair.txt)

### Inferences
- Usar throughput histórico apenas como peso de capacidade suavizado (ex.: mediana de 6 sprints, limitado a 0,8x–1,25x) para o rápido não receber carga desproporcional; reportar a diferença como informação, não como nota.
- Separar duas coisas: (a) atribuição de carga (capacidade) e (b) avaliação de desempenho (humana, contextual). O algoritmo não deve produzir ranking de pessoas.
- Limite de WIP por pessoa (ex.: 2-3 issues em progresso; ou soma de pontos): se todos estão no teto, a sugestão deve ir para "backlog/sem atribuição" com alerta, em vez de estourar o teto.
- Evitar Goodhart: se pontos viram meta individual, estimativas inflam. Preferir estimativas por t-shirt/contagem para balanceamento.

### Gaps
- Falta literatura citável (Kanban/Little's Law, estudos de equidade em equipes de engenharia). Recomendo buscar antes do relatório final.

## 3. Como o Jira trata hoje (Cloud x Data Center, Marketplace)

### Takeaway
Cloud: Automation tem round robin/balanced/random; JSM não atribui nativamente a grupo/equipe com disponibilidade. Plans não sinaliza sobrecarga individual e (segundo vendedor, não confirmado) relatórios de workload nativos foram aposentados em 31/07/2026. Data Center depende de Automation for Jira (app) ou apps como Workflow Toolbox.

### Cited Findings
- Cloud: três métodos; assignees precisam das permissões Browse Projects e Assignable User, senão a regra falha (erro "Unable to assign tickets to user"). — [Atlassian KB](https://support.atlassian.com/jira/kb/jira-automation-rule-to-auto-assign-issues-based-on-role-or-group/)
- Server/DC: Automation for Jira cobre random/workload/round robin (versões 7.x do doc). — [Smart assign (automation074)](https://confluence.atlassian.com/automation074/smart-assign-jira-issues-load-balancing-round-robin-and-more-1141481228.html)
- Decadis Jira Workflow Toolbox (DC): post functions "least busy" (menos tickets não resolvidos) e round robin por fila grupo+role. — [Decadis docs](https://docs.apps.decadis.net/jira-workflow-toolbox-data-center/assign-to-project-role)
- JSM não suporta nativamente atribuição a grupo/time; queues não resolvem atribuição inteligente (opinião de Community Champion). — [Atlassian Community](https://community.atlassian.com/forums/Jira-Service-Management/Automating-ticket-assignment-to-a-group-in-JSM/qaa-p/3095564)
- Apps: Smartload for JSM (pesos, round robin enviesado, por projeto, na criação) [link](https://marketplace.atlassian.com/apps/132939184/smartload-for-jira-service-management); Smart Assignments & Rotations (rotações, round robin, load balancing, disponibilidade; 103 instalações segundo listing) [link](https://marketplace.atlassian.com/apps/1225712/smart-assignments-rotations); SnapAssign (Forge; round robin, load-based, shift-aware, JQL, audit logs; preço a conferir) [link](https://snapbytesapps.atlassian.net/wiki/spaces/SNAPASSIGN); Round Robin Assignment for JIRA & JSD é app legado Server [link](https://marketplace.atlassian.com/apps/1220401/round-robin-assignment-for-jira-jsd).
- Plans/Advanced Roadmaps: docs antigos (Portfolio Server 2017-2020) descrevem Capacity report (capacidade livre/planejada/uso por sprint). — [Capacity report](https://confluence.atlassian.com/display/JIRAPortfolioServer030/Capacity+report)
- Thread Atlassian Community (jul/2026): Plans não sinaliza superalocação individual (resposta de representante Planyway). Outra resposta (30/07/2026, Planyway) afirma que User Workload Report e Time Tracking Report seriam aposentados em 31/07/2026 — alegação de vendedor, NÃO confirmada na Atlassian. — [Community](https://community.atlassian.com/forums/Jira-questions/How-to-identify-capacity-issues-during-sprint-planning-with-Jira/qaa-p/3262757); [Community 2](https://community.atlassian.com/forums/Jira-questions/How-to-make-user-workload-report-for-complete-team/qaq-p/3046059)
- Apps de capacidade/time-off: Sprint Capacity Planner, Allocaty, Capacity for Jira, Deiser Capacity. — [Marketplace](https://marketplace.atlassian.com/apps/1238359/capacity-for-jira-resource-planning); [Deiser](https://www.deiser.com/apps/capacity)
- Workload Pie, Tempo Planner, ScriptRunner: NÃO verifiquei listings/limitações.

### Inferences
- Nenhuma opção nativa pondera por story points, ausências ou afinidade; logo uma solução própria (dashboard + sugestão) agrega valor real, e pode coexistir escrevendo apenas o assignee.
- Se o gerente aprova antes, automação nativa de "auto-assign na criação" deve ficar desligada nesses projetos para não conflitar.

### Gaps
- Confirmar nas notas de release da Atlassian a aposentadoria dos relatórios de workload; confirmar disponibilidade DC do Automation (hoje embutido em DC 9+? não verificado); Workload Pie/Tempo/ScriptRunner não pesquisados.

## 4. Modo sugerir-e-aprovar, auditoria, explicabilidade, ética, LGPD

### Takeaway
Sugerir-e-aprovar com explicação dos fatores e log é o desenho seguro: mantém humano na decisão, reduz risco do art. 20 LGPD e dá contestabilidade. Texto exato do art. 20 não pôde ser lido no Planalto; confirmar antes de citar juridicamente.

### Cited Findings
- Art. 20 LGPD garante ao titular pedir revisão de decisões tomadas unicamente com base em tratamento automatizado que afetem seus interesses, inclusive as de perfil profissional; a exclusividade da automação é o critério; "revisão pro forma" sem avaliação real provavelmente não afasta a aplicação. — resumo de busca de [Confidata](https://confidata.com.br/blog/decisoes-automatizadas-lgpd-direitos-obrigacoes), [Âmbito Jurídico](https://ambitojuridico.com.br/?p=9069553) (fontes secundárias)
- O dispositivo (§3º) que garantia expressamente revisão por pessoa natural foi vetado, logo o direito expresso a revisão humana não está no texto vigente; ANPD teria destacado em 2026 a "contestabilidade" (alegação de artigo secundário, não confirmada). — [Âmbito Jurídico](https://ambitojuridico.com.br/?p=9069553); [Migalhas](https://www.migalhas.com.br/depeso/348841/a-explicabilidade-como-diretriz-para-as-decisoes-automatizadas)
- Texto oficial para conferir: [Lei 13.709/2018 (Planalto)](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709.htm) — não consegui carregar.

### Inferences
- Se o gerente aprova com avaliação real, a decisão não é "unicamente automatizada"; ainda assim, o art. 20 §1º (informações claras sobre critérios e procedimentos) é boa prática a seguir: exibir os fatores do score.
- Princípios do art. 6 (finalidade, necessidade, transparência, não discriminação) sugerem: usar só dados necessários (carga, capacidade, skills declaradas), não usar dados sensíveis, informar a equipe sobre a lógica, e não reaproveitar os dados de produtividade para avaliação individual sem base própria. Verificar com jurídico/DPO; base legal provável: legítimo interesse (art. 7º IX/art. 10) ou execução de contrato — não verificado.
- Trilha de auditoria: registrar (issue, candidatos, score por fator, versão do algoritmo, sugerido, escolhido pelo gerente, motivo de override, timestamp). Comentário/propriedade na issue ou tabela própria; permitir revisão e contestação pelo membro.
- Viés: afinidade cria silos (quem sabe recebe sempre); impor teto de afinidade e rodízio de aprendizado; monitorar Jain por grupo.

### Gaps
- Texto literal dos arts. 6, 7, 10 e 20 não verificado no Planalto; sem fonte oficial sobre orientação da ANPD em 2026.

## 5. Rebalanceamento

### Takeaway
Rebalancear por gatilhos (sobrecarga, ausência futura, risco de atraso) movendo o mínimo de issues, preferindo itens não iniciados e de baixo contexto. Sem fontes citáveis encontradas; tudo inferência.

### Cited Findings
- Nenhuma fonte consultada nesta sessão.

### Inferences
- Gatilhos: carga normalizada > 1,2 por N dias; ausência planejada (calendário) cobrindo prazo de issue aberta; previsão de atraso (esforço restante > capacidade até o due date/fim da sprint); Jain abaixo de limiar (ex.: <0,9).
- Heurística de mínima movimentação: ordenar candidatas a mover por (não iniciadas primeiro; menos horas já registradas; menor afinidade com o dono atual; maior afinidade/folga com receptor); mover até a carga voltar ao limiar (algoritmo guloso, equivalente a minimizar cardinalidade do conjunto movido). Nunca mover issue em "In Progress" com alto contexto sem justificativa; preferir pareamento/handoff documentado.
- Aplicar histerese (não rebalancear por diferenças <10%) para evitar thrashing e custo de troca de contexto.
- Entregar como lista de propostas aprovadas pelo gerente, não aplicação automática.

### Gaps
- Sem dados empíricos sobre custo de troca de contexto/transferência de conhecimento (buscar literatura).

## 6. Escrever a atribuição no Jira (REST, permissões)

### Takeaway
Cloud: `PUT /rest/api/3/issue/{key}/assignee` com `{"accountId": "..."}` (null desatribui, "-1" assignee padrão segundo fontes comunitárias). Data Center: o campo é `name` (ou key, conforme versão); não verifiquei na referência oficial.

### Cited Findings
- Endpoint dedicado `PUT /rest/api/{2|3}/issue/{issueIdOrKey}/assignee`. — [fórum Atlassian](https://community.developer.atlassian.com/t/set-issue-to-unassigned-via-api/82622)
- Na Cloud, payload só com `name` falha por mudanças de privacidade (GDPR); usar `accountId`, inclusive na v2. A migração tirou username/userKey das APIs Cloud (identificação por accountId). — [API privacy changes](https://developer.atlassian.com/cloud/jira/platform/api-changes-for-user-privacy-announcement/); [migration guide](https://developer.atlassian.com/cloud/jira/platform/deprecation-notice-user-privacy-api-migration-guide/); [fórum](https://community.developer.atlassian.com/t/set-issue-to-unassigned-via-api/82622/2)
- `accountId: null` desatribui e `"-1"` usa assignee padrão do projeto (fontes comunitárias/wrappers; relatos conflitantes). — [fórum](https://community.developer.atlassian.com/t/set-issue-to-unassigned-via-api/82622/3); [PowerJira wrapper](https://powershellgallery.com/packages/PowerJira/1.1.1/Content/public/api-v2/issue/Invoke-JiraAssignIssue.ps1)
- Lista de usuários atribuíveis: `GET /rest/api/2/user/assignable/search` (retorna só quem pode ser atribuído à issue). — [exemplo Atlassian Server](https://developer.atlassian.com/server/jira/platform/jira-rest-api-example-edit-issues-6291632/)
- DC/Server: o exemplo oficial de edição usa `fields.assignee` com `name` e responde 204. — [Atlassian Server example](https://developer.atlassian.com/server/jira/platform/jira-rest-api-example-edit-issues-6291632/)
- Permissões: no projeto, assignee precisa de Browse Projects e Assignable User. — [Atlassian KB](https://support.atlassian.com/jira/kb/jira-automation-rule-to-auto-assign-issues-based-on-role-or-group/)

### Inferences
- Quem chama a API precisa da permissão de projeto "Assign Issues" (nome do esquema de permissões; conhecimento geral, não confirmado em página oficial nesta sessão) e acesso Browse; erro 400 "assignee cannot be set" pode indicar campo fora da tela (relato de fórum) — preferir o endpoint dedicado ao PUT /issue.
- Fluxo seguro: `assignable/search` por issue → validar candidato → PUT assignee → registrar auditoria. Usar token de serviço/OAuth do gerente (ação "em nome do gerente") e deixar claro no log.

### Gaps
- Referência oficial v3 (developer.atlassian.com) não carregou; confirmar permissão exata, códigos de resposta (204/400/401/403/404), e semântica de DC (`name` vs `key` por versão, DC 10/11 em 2026).
