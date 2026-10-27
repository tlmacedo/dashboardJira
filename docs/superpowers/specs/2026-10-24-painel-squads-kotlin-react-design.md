# Design: Painel de Squads (Kotlin + React), preparado para o Jira DC

> **Nota (27/10):** a plataforma mudou por decisão do usuário (a empresa bloqueia Docker e PostgreSQL): o banco é SQLite embutido em arquivo e não há Docker. Ver `docs/superpowers/plans/2026-10-27-adendo-plataforma-sqlite-sem-docker.md`, que vence este documento onde houver conflito de plataforma.

Data: 24/10/2026
Status: revisado por um construtor independente; aguardando aprovação do usuário

## 1. Intenção e porquê

Substituir o app Python atual (`app/`) por um dashboard novo, em Kotlin e React, que gerencie o time TG-Basic: fluxo e prazos das tasks, carga e capacidade das pessoas, planejamento de atividades e ausências, e **auditoria de alterações indevidas** (datas, responsável e labels que escondem atraso).

O app ainda **não tem nenhuma informação do Jira real**. Esta etapa deixa tudo pronto, offline, para que a conexão real, quando for autorizada, saiba **o que e como** buscar, usando como base:

1. o vocabulário de campos do arquivo de filtros do TG-Basic (`Filtros_Status_tool.txt`, usado como exemplo, **nunca diretamente**);
2. o projeto irmão bscbi-web, que já funciona em produção (cliente `POST /search`, Bearer PAT, `expand=changelog`, totais por `maxResults=1`, filtros salvos, taxonomia de labels).

Por que agora: o app atual é um protótipo sem validação real, tem divergências nos números (fuso, contagens duplicadas) e não cobre as regras do TG-Basic nem a auditoria que a gestão precisa.

## 2. Objetivos, não objetivos e antiobjetivos

### Objetivos
- Recriar, validar e melhorar os cards do app atual, com a busca de cada card definida pela sua finalidade.
- Criar os cards novos de pessoas, planejamento, ausências e esforço (seção 6).
- Implementar a auditoria de datas, dono e labels com as regras da seção 7.
- Entregar um frontend com identidade Sidia, três temas, pt-BR e inglês, WCAG AA.
- Deixar o cliente Jira, o dicionário de campos e o checklist da 1ª conexão prontos, testados contra respostas simuladas.

### Não objetivos
- Nenhuma consulta ao Jira real nesta etapa. Escrita no Jira (atribuir issues) continua desligada.
- Cadastro ou aprovação de ausências pela interface.
- Aplicativo móvel nativo (o frontend é responsivo, mas o alvo é desktop).
- Ranking individual de pessoas.

### Antiobjetivos (falha mesmo que "funcione")
- Qualquer chamada ao Jira DC da empresa sem autorização expressa, por pesquisa.
- Mostrar a uma pessoa dados que o papel dela não permite.
- Apresentar indício de auditoria como prova de má-fé.
- Números que divergem entre telas por regra de data ou fuso diferente.
- Cor como único sinal de estado.

## 3. Restrições

- **Regra de ouro (temporária):** nenhuma consulta ao Jira da empresa sem autorização expressa do usuário, por pesquisa. Hoje nenhuma está autorizada. Trava técnica já criada em `.claude/hooks/guard.sh`; flag de ambiente `JIRA_LIVE_ENABLED=false` por padrão.
- Dados reais de pessoas (equipes, ausências, papéis) e segredos ficam fora do repositório (`.gitignore`), com modelos `.exemplo`/`.demo` versionados.
- Datas e prazos em fuso **America/Manaus** (UTC−4, sem horário de verão).
- Jira alvo: **Samsung Data Center** (Bearer PAT, projeto TG-Basic) e **Sidia** (Basic user:token, projeto CAT, só cards próprios da automação). Cada card declara de qual instância lê.
- Jira DC identifica pessoas por `name`/`key`, nunca por `accountId`. ScriptRunner (`dateCompare`) **não** é dependência: D-1/D-2/no prazo são calculados em código.
- Dados pessoais sensíveis (Integridade, auditoria): validar base legal com o DPO antes do uso real.

## 4. Decisões já tomadas (pelo usuário)

### 4.1 Stack
- Backend **Kotlin (Ktor)**, banco **SQLite embutido (arquivo)**, frontend **React + TypeScript (Vite, SPA)**. Decidido por requisitos de negócio, ignorando o código existente. O app Python em `app/` é substituído no lugar; o histórico permanece no git e os testes e dados demo servem de referência de números.
- Flutter e Next.js descartados.
- Ordem de entrega: **fatia vertical primeiro** com o card **Tempo em status** (valida changelog, campos customizados, cache, tela e testes), depois os demais cards.

### 4.2 Regras de cálculo
| Tema | Decisão |
|---|---|
| Fuso | America/Manaus em todo cálculo de dia (prazo, D-1/D-2, período) |
| Tempo | **Dias úteis** como padrão (feriados do calendário configurado); opção de alternar para dias corridos, persistida na URL |
| Concluída | Categoria de status "done" **e** `resolution` preenchida; divergência vai para Qualidade dos dados; status não mapeado bloqueia o card com aviso |
| Atraso justificado | Duas métricas lado a lado: "no prazo" puro e "no prazo ajustado" (atraso com label justificada conta como no prazo). Labels em YAML, iniciadas pela taxonomia do bscbi-web (`ONHOLDBY-*`, `LATE_SENT`, `DUEDATE_NEGOTIATED`, `BI-CONSIDERCANCELLED`…) |
| Eventos (reabertas, devolvidas, trocas) | Período pela data do evento no changelog |
| Central de atenção | "Sem atualização" e "Paradas no status" são dois itens separados |
| Previsão | Monte Carlo com taxa de chegada de itens novos; previsão por épico só com amostra própria (senão "—") |
| Capacidade | Capacidade zero aparece como "sem capacidade" (não 990%); issues vencidas saem da semana atual e viram "atraso acumulado (h)" |
| Horas sem estimativa | Horas padrão **por tipo de issue**, em YAML; issue marcada como "horas assumidas" reduz a confiança |
| Heurísticas | Pesos do risco, limiares da Integridade, pesos da distribuição e fator de foco em **configuração versionada**; versão gravada nos resultados; calibração depois, com dados reais |
| Esforço por task fechada | Mostrar três componentes (peso do tipo, número de labels `TEST-*`, horas do worklog) e um **índice composto** com pesos em YAML. A fórmula do índice é proposta inicial do design (ver 9) |

### 4.3 Acesso e papéis
- Flag `DASHBOARD_DEV_MODE` (padrão `true`): sem login, todos os dados visíveis, faixa vermelha fixa "MODO DESENVOLVIMENTO". Permitida mesmo com `JIRA_LIVE_ENABLED=true`, com a faixa avisando.
- Produção (`false`): SSO/OIDC. **Usuário** vê só o próprio. **Squad Leader** vê o próprio e quem está no seu squad. **Coordenador** e **gerente** veem todos. Papéis, squads e liderança vêm de YAML. O `id` da pessoa nesse YAML é o username dela no Jira, o mesmo valor do `assignee` das issues (não é código de cargo); `jira_sidia` é um campo opcional futuro para quem tem username diferente na instância Sidia.
- Todas as rotas da API exigem sessão e aplicam o filtro por papel. **Integridade** só para gerente; toda consulta a Auditoria e Integridade é registrada.

### 4.4 Ausências
- Férias, licenças e feriados vêm de **YAML versionado**, somente leitura no app. Não há cadastro nem aprovação. O antigo card de "aprovação de ausências com impacto" vira um **simulador de impacto** ("e se esta ausência acontecer?") que lê uma ausência hipotética.

### 4.5 Visual
- Direção **híbrida**: densa (proposta A) nas telas de trabalho (tabelas, calendário, auditoria, carga) e editorial (proposta B) nas telas de leitura (Central de atenção, Meu painel, resumos). Mesmos tokens, temas e componentes.
- Temas **Claro**, **Escuro** e **Sidia** (marca forte: menu e cabeçalho em azul `#1329A0`; moldura de 4 cores do logo como faixa fina). Navegação: menu lateral em 8 grupos filtrado pelo papel, com página inicial por papel.
- Identidade levantada do site público da Sidia e do logo enviado (azul `#1329A0`, verde `#13DC14`, índigo `#3E4095`, verde `#00A85A`, laranja `#F58634`); **não há manual de marca nem arquivo limpo do logo** (hoje um recorte de baixa resolução). Contrastes calculados: laranja e verde reprovam AA como texto sobre branco e entram só escurecidos ou como preenchimento com rótulo.
- Detalhes, wireframes e protótipos: `docs/design/plano-visual-frontend.md` e `docs/design/prototipo/`.

### 4.6 Decisões depois da revisão do construtor

Decididas pelo usuário:
- **Vigência dos papéis:** YAML de papéis e squads com datas (ver 7.1).
- **Retenção do histórico:** configurável, padrão 24 meses (ver 5.4).

Decisões do design (podem ser revistas pelo usuário):
- **Produção × desenvolvimento:** o filtro por papel existe desde a fase 1 em todas as rotas; o fluxo SSO/OIDC fica atrás de uma interface, com provedor de teste. O provedor real entra quando a infraestrutura definir qual é, antes de qualquer uso com dados reais.
- **Volume e atualidade:** dimensionar para dezenas de pessoas e dezenas de milhares de issues, sincronização incremental a cada 10 minutos e reconciliação diária.
- **Subtask Review:** a ligação Task–Review e o tipo "Checklist Review" são configuração no dicionário de campos (tipo da issue e campo `parent`), a confirmar na 1ª conexão. Checklist × Review e Revisões pendentes por revisor usam o mesmo critério.
- **Calendário:** um calendário único do time (feriados nacionais, estaduais e de Manaus) em YAML. Calendário por pessoa fica fora desta etapa.
- **Oráculo de números:** o app atual é referência; cada divergência conhecida (fuso, contagem duplicada) é anotada como "diferença esperada" e os casos de aceite são escritos por card.
- **Ambiente:** um serviço em contêiner que serve a API e a SPA, atrás do proxy da empresa; OIDC genérico.

## 5. Dados e integração com o Jira

### 5.1 Dicionário de campos
Arquivo `config/jira_campos.yaml` classifica cada campo: nome no Jira, tipo (select, texto, data, usuário), valores conhecidos e finalidade. Cada card referencia nomes do dicionário. Os ids (`customfield_NNNNN`) são **descobertos por nome** na primeira conexão autorizada. Campos conhecidos do TG-Basic: `OS Version`, `SKU Type`, `Checklist Type`, `Model Name`, `Model Category`, `TG Request Type`, `Carrier Code`, mais `cf[12416]` e `cf[11412]` (ids vistos no filtro de exemplo, a confirmar).

### 5.2 Especificação de cada card
Cada card tem uma ficha em YAML: finalidade, instância, campos usados, filtros fixos, parâmetros (squad, período, OS), fórmula, exemplo numérico e critérios de aceite. Um construtor de JQL com **validação** (datas `aaaa-mm-dd`, usernames por regex, listas permitidas de campos e status) gera a busca; nenhum JQL cru vem da tela ou da API. Testes comparam o JQL gerado com o esperado.

### 5.3 Cliente Jira
- Duas instâncias configuráveis (Samsung DC e Sidia) com estratégia de autenticação por instância.
- DC: `POST /rest/api/2/search`, `fields` obrigatório, paginação até o `total` (limite 5.000 por página), retry com backoff em 429/5xx, timeout de 30 s, erro estruturado que preserva o status HTTP, `expand=changelog` (com busca do changelog completo quando truncado), totais por `maxResults=1`.
- Em testes e no modo padrão usa transporte simulado com fixtures; qualquer requisição de rede real exige `JIRA_LIVE_ENABLED=true` (e a autorização do usuário).

### 5.4 Sincronização
Sincronização incremental com marca d'água e reconciliação diária para o SQLite; histórico (changelog, tempo por status, worklog) guardado para as métricas. **Retenção:** o histórico completo (changelog, worklog) é guardado por um prazo configurável, **padrão 24 meses**; dados mais antigos são removidos ou agregados automaticamente. O prazo final é decisão do DPO e muda em configuração. Detalhes de implementação ficam com o construtor.

### 5.5 Entregáveis de preparação (além do código)
1. `config/jira_campos.yaml` classificado.
2. **Matriz de compatibilidade**: o que já funciona no bscbi-web (POST `/search`, Bearer, changelog, `maxResults=1`, filtros salvos, campos por nome) × o que ainda não foi validado aqui.
3. **Checklist da 1ª conexão autorizada**: lista mínima de chamadas a pedir (por exemplo `/myself`, `/field`, uma busca com `maxResults=1`), o que conferir em cada resposta e quando parar.

## 6. Cards

O inventário completo, com entradas e fórmulas de cada card atual (23 telas, cerca de 150 cards, IDs como VG-01, MT-02, TS-02), está em `docs/analise/relatorio-cards-app-atual.md`. Esse relatório é o ponto de partida; a recriação mantém, ajusta, remove ou cria cards conforme as decisões desta seção.

### 6.1 Ajustes aprovados nos existentes
- Atraso justificado (duas métricas) em Metas, Negativas, Fechadas e Meu painel.
- Dicionário de campos como filtros e agrupamentos (por versão de OS, tipo de checklist).
- Fuso único, dias úteis, "sem capacidade", atraso acumulado, horas por tipo.
- Tempo em status: separar primeira entrada e última saída de "In Progress" (regra do bscbi-web), opção de excluir outliers acima de 30 dias, percentis por tipo de issue.
- Previsão com taxa de chegada.

### 6.2 Cards novos aprovados
Calendário do time · Cobertura por ausência · Plano de 4 a 8 semanas por pessoa · Vencimentos da semana com folga real · Posse da task por pessoa · Simulador de ausência · Matriz de competências · Revisões pendentes por revisor · Carga por tipo/OS · Tendência de ocupação · **Esforço por task fechada** (por task, por pessoa proporcional à posse e por período, além da contagem).

### 6.3 Posse da task
O tempo que cada pessoa ficou com a task, incluindo transferências, é calculado do histórico de responsável. **Cada pessoa responde pelo tempo em que ficou com a task**; métricas por pessoa (tempo, esforço, atraso) usam esse tempo proporcional.

## 7. Auditoria de datas, dono e labels

### 7.1 Quem pode alterar
Apenas o **Reporter da task (o PL)**, os **coordenadores e o gerente** ou o **Squad Leader do responsável** podem trocar **responsável**, **data de vencimento** e **labels que evitam atraso**. As listas de coordenadores e Squad Leaders vêm de YAML **com vigência por datas** (cada pessoa, papel e squad tem "de" e "até"). A auditoria é retroativa: "autorizado" é avaliado com os papéis e o squad **vigentes na data de cada alteração**, não os de hoje. Qualquer outra pessoa que altere esses campos gera **alerta e auditoria**.

### 7.2 Sinais
Cada sinal cita campo, valor de/para, data, autor e se o autor era autorizado, e abre a gaveta com o histórico campo a campo.

| Sinal | Resultado |
|---|---|
| Vencimento alterado por quem não é autorizado (inclui o próprio responsável mudando a data da sua task) | auditoria |
| Vencimento removido | auditoria |
| Alteração de vencimento que deixa a Task diferente da subtask Review (as datas devem ser iguais) | alerta; card com a porcentagem de divergência e lista clicável |
| Responsável trocado por não autorizado | auditoria |
| Passa-bola de responsável (3 ou mais trocas, ou volta a quem já teve) perto do vencimento | auditoria |
| Troca de dono perto do vencimento (últimos 2 dias antes ou depois dele) por não autorizado | auditoria |
| Label de justificativa colocada **depois da task fechada ou resolvida** por não autorizado | auditoria |
| Label de atraso colocada pelo próprio responsável depois de fechada ou resolvida | auditoria; se o autor for autorizado, vira **alerta** |
| `LATE_SENT` ou `LATE_REVIEW` com o dev tendo a task **2 dias ou mais antes do vencimento** (limite configurável) | auditoria |
| Label de justificativa colocada depois do vencimento | auditoria |
| Cancelamento suspeito (após o vencimento ou por não autorizado) | auditoria |
| Estimativa reduzida ou worklog retroativo | auditoria |

Mais os 12 sinais de integridade já existentes no app atual (resgate por prorrogação, criada e fechada em minutos, fechamento em lote, etc.), mantidos com gravidade 1 a 3.

### 7.3 Cuidados
Indício, não prova: aviso fixo na tela. A consulta é auditada. Integridade só para gerente. Auditoria para coordenador e gerente (Squad Leader vê só o próprio squad nas telas de cada card, sem acesso à tela de auditoria). Validação com o DPO antes do uso real.

## 8. Fases sugeridas (o plano detalha)

1. **Fundação:** estrutura Ktor + SQLite + Vite, configuração por flags (`JIRA_LIVE_ENABLED`, `DASHBOARD_DEV_MODE`), papéis e filtro por papel, autenticação SSO/OIDC (stub em desenvolvimento), cliente Jira com transporte simulado, calendário Manaus e dias úteis, dados demo.
2. **Dicionário de campos e construtor de JQL** com validação e testes de snapshot; ficha de cards em YAML.
3. **Fatia vertical: Tempo em status**, de ponta a ponta (fixtures, changelog, API, tela, testes de aceite).
4. **Auditoria:** sinais, autorização por papel, gaveta de histórico, card Task × Review, registro de consultas.
5. **Cards de trabalho e prazos** (Abertas, Metas com atraso justificado, Fechadas, Negativas, Riscos, Previsão).
6. **Pessoas, planejamento e ausências** (Carga, Capacidade, Calendário, Cobertura, Plano 4–8 semanas, Vencimentos da semana, Posse, Esforço, Simulador, Competências, Revisões pendentes, Carga por tipo/OS, Tendência).
7. **Checagem Checklist × Review, Integridade, Distribuição e Rebalanceamento, Governança.**
8. **Documentação de preparação** (matriz de compatibilidade, checklist da 1ª conexão) e revisão de acessibilidade e visual.

## 9. O que fica com o construtor

- Estrutura de pacotes e módulos Kotlin; biblioteca de acesso a dados e de migrações; biblioteca de gráficos no React.
- Formato exato dos YAML (esquemas), versionamento das heurísticas e política de cache.
- Fórmula do **índice composto de esforço**: ponto de partida do design = peso do tipo + testes × peso do teste + horas × fator, com todos os pesos em YAML e calibração posterior com as horas registradas.
- Escala de severidade de cada sinal de auditoria; "perto do vencimento" começa em 2 dias, configurável.
- Provedor OIDC concreto (a definir com a infraestrutura da empresa); em desenvolvimento usa o modo sem login.
- Estratégia de dados demo (reaproveitar o gerador do app atual).
- Fonte tipográfica: sistema, até a Sidia fornecer a fonte e o logo limpo.

## 10. Critérios de sucesso

- Nenhuma requisição ao Jira real é feita enquanto `JIRA_LIVE_ENABLED=false`; os testes falham se tentarem rede.
- Cada card tem ficha, JQL gerado (quando aplicável) e teste de aceite com exemplo numérico.
- Os números de uma tela batem com os da tela de destino ao clicar (a lista de tasks tem o mesmo tamanho do card).
- O mesmo item cai no mesmo dia em todas as telas (fuso Manaus).
- Cada papel vê apenas o permitido (testes de autorização por papel em todas as rotas).
- Auditoria detecta cada sinal da seção 7 nos casos de fixture, incluindo os casos de limite (2 dias, fuso, autorizado × não autorizado).
- Interface em pt-BR e inglês, três temas, AA verificado nos pares de cor.
- Documentos de preparação (dicionário, matriz de compatibilidade, checklist da 1ª conexão) existem e referenciam o que foi validado no bscbi-web.

## 11. Riscos e pendências conhecidas

- **Cliente Jira nunca testado em servidor real:** os pontos a verificar primeiro são o campo `from/to` do assignee no changelog e o changelog truncado no DC.
- **Logo e manual de marca:** pendentes; o logo atual é um recorte de baixa resolução.
- **Ids de campos customizados** (`cf[12416]`, `cf[11412]`, etc.) e o significado de cada um precisam de confirmação no Jira real.
- **LGPD:** auditoria e Integridade tratam dados pessoais; a base legal e a comunicação com a equipe ainda não foram validadas.
- **Reporter como PL:** a regra de autorização assume que o Reporter da task é o PL; confirmar na primeira conexão e ajustar a lista de coordenadores.
