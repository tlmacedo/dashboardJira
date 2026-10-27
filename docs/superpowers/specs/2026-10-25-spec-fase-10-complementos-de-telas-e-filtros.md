# Spec da Fase 10: Complementos de telas e filtros

Data: 25/10/2026
Plano: [`docs/superpowers/plans/2026-10-25-plano-10-complementos-de-telas-e-filtros.md`](../plans/2026-10-25-plano-10-complementos-de-telas-e-filtros.md) (12 tarefas, um PR)
Spec mestra: [`2026-10-24-painel-squads-kotlin-react-design.md`](2026-10-24-painel-squads-kotlin-react-design.md) (seções 5.1, 6.1, 10)
Plano visual: [`docs/design/plano-visual-frontend.md`](../../design/plano-visual-frontend.md) (seções 3, 5.5 a 5.8, 6.4, 7)
Auditoria: [`docs/superpowers/cobertura-do-projeto.md`](../cobertura-do-projeto.md) (A4, A5, M1, M2, M3, M4, M5, M7, B4; E5, E12, E13)
Depende de: Planos 4A, 9, 4, 5, 6 e 7 mesclados na `main`. Roda **antes** dos Planos 11 e 8.

> **Precedência:** `CLAUDE.md` > `docs/guia-execucao-outra-ia.md` > esta spec > o plano > spec mestra de 24/10. A spec mestra ainda traz a regra antiga de acesso ao Jira, substituída em 25/10 pela flag única.

## 1. Intenção e porquê

Os Planos 5 a 7 entregam os cards, mas deixam incompleto o que todas as telas compartilham:
- os filtros são texto livre (ninguém produz a lista de squads e pessoas);
- Checklist Type e SKU Type são sincronizados e nenhum card os usa, embora a spec 6.1 aprove "o dicionário como filtros e agrupamentos";
- o detalhe de uma task só existe na Auditoria, sem o link para o Jira do wireframe 6.4;
- um detalhamento com dezenas de milhares de linhas trava o navegador;
- cada tela trata "carregando", "vazio" e "erro" do seu jeito, e só o 403 é testado;
- os avisos e erros do backend chegam em pt-BR, e a versão em inglês mostra texto em português;
- cada tela improvisa o próprio gráfico, sem paleta acessível.

Esta fase fecha esse conjunto de uma vez, no kit comum. Assim cada tela herda o comportamento, e o Plano 8 faz a revisão visual e de acessibilidade final sobre telas já completas. Ela também absorve os ajustes que a auditoria apontou nos Planos 5 e 7 (VG-02, VG-03, FE-11, FE-12, NE-09, o universo de "no prazo" e as fichas do menu), para que outra IA não precise reabrir planos já executados.

## 2. Objetivos, não objetivos e antiobjetivos

### Objetivos

- **Campos do dicionário:** Checklist Type, SKU Type, Model Name, Model Category, TG Request Type e Carrier Code gravados por issue e usados como filtro em todo card e como agrupamento em Abertas e Fechadas.
- **Opções de filtro:** um endpoint que devolve squads, pessoas, tipos, prioridades, OS e valores de campo, sempre do escopo visível do principal.
- **Gaveta universal da task**, em todas as telas: situação, datas, tempo por status, posse, fatores de risco, campos, histórico (só coordenação e gerente) e link para o Jira montado da configuração.
- **Detalhe em nova aba:** o mesmo drill numa página própria.
- **Paginação virtual** nas tabelas grandes, navegável por teclado.
- **Estados de tela padronizados:** carregando, vazio, erro com "tentar de novo", sem permissão, sessão e dados incompletos.
- **Mensagens por código:** todo 400 e todo aviso do backend saem por código, traduzidos em pt e en; datas e números formatados por idioma.
- **Gráficos com paleta categórica acessível** (cor, padrão e forma), com tabela equivalente.
- **Ajustes da auditoria:** VG-02 e VG-03 na Central (salvo decisão contrária), FE-11 tempo ativo, FE-12 tabela de concluídas, NE-09 maior tempo por status, "no prazo" sem canceladas e Epic, e uma ficha por item do menu.

### Não objetivos

- Cards novos além dos ajustes listados.
- Guia do painel em inglês e identidade visual final (Plano 15).
- Trava do DPO na gaveta (Plano 11, que roda logo depois).
- Cards da instância Sidia/CAT (Plano 14, depois de decisão).
- Paginação no backend: o drill continua devolvendo a lista inteira, para manter "card = tamanho do detalhe".

### Antiobjetivos (falha mesmo que "funcione")

- Uma opção de filtro, uma gaveta ou um valor de campo que revele pessoa ou issue fora da visão do principal.
- A gaveta respondendo 403 para uma issue de outro squad, o que confirma que a chave existe (deve ser 404).
- Link para o Jira com a flag `false`, com host escrito no código ou com usuário e senha embutidos.
- Filtro aplicado só no card ou só no drill, fazendo os dois divergirem.
- Tela mostrando texto técnico do servidor (stack trace, mensagem de exceção) ou mensagem pt-BR na versão em inglês.
- Gráfico que distingue séries só pela cor.
- Dependência npm nova para tabela ou gráfico.

## 3. Restrições

### 3.1 Regras transversais do projeto (valem sempre)

**Acesso ao Jira.** A única trava é a flag `JIRA_LIVE_ENABLED` (regra 1 do `CLAUDE.md`, 25/10). Com `false`, ninguém acessa o Jira por caminho nenhum, inclusive uma pessoa clicando num link do painel. Só o usuário muda a flag. Testes usam fixtures, `MockEngine` e dados de demonstração.

**Visibilidade por papel.** USUARIO vê só o próprio; SQUAD_LEADER, o próprio e o seu squad; COORDENADOR e GERENTE veem todos. Parâmetros nunca ampliam a visão. Pessoa fora da visão aparece como "outra pessoa".

**Sem ranking de pessoas.** Tabelas e listas de pessoas sempre em ordem alfabética.

**Registro de acesso.** Toda rota de dados grava `access_log`, inclusive 400, 403 e 404; falha ao gravar → 503 sem dados.

**Interface.** Cor nunca é o único sinal; WCAG AA; três temas; pt-BR e inglês completos; foco visível; `Esc` fecha gaveta e devolve o foco; CSV com neutralização de fórmula.

**Dados pessoais.** Fixtures e demonstração só com nomes e valores fictícios; configs reais fora do git.

**Ambiente.** Sem Docker; banco SQLite real em arquivo temporário (`testContextComBanco`/`BancoEmArquivo`); comentários e commits em pt-BR.

### 3.2 Restrições específicas desta fase

- **Uma migração:** `V{n}__campos_do_dicionario.sql` (`n` = próxima migração livre; hoje só existe a V1, então a V2; nunca editar a V1).
- **O link para o Jira** sai de `JIRA_<INSTANCIA>_URL` (já lida pelo `Settings`), limpo de credenciais, query e fragmento, só `https`, e só com a flag `true`.
- **Nenhuma dependência npm nova.** Gráficos em SVG próprio; virtualização própria.
- **O preenchimento retroativo** lê só `issue.raw` no banco local; nunca fala com o Jira.
- **Toda resposta 400** passa a ser `ErroDto(codigo, params)` (do Plano 9).

## 4. Decisões já tomadas

| # | Decisão | Origem |
|---|---|---|
| U1 | A flag `JIRA_LIVE_ENABLED` é a única trava; só o usuário a liga | [usuário, 25/10] |
| U2 | Universo da ficha de sincronização (tipos Checklist, Checklist TG, Checklist Review, Feature check, TG Feature check e Task; Epic fora) | [usuário, 25/10, Plano 4A] |
| U3 | Integridade só para gerente; Auditoria (e histórico campo a campo) para coordenação e gerente | [usuário, spec mestra 7.3] |
| D1 | **Coluna única `issue.campos_dicionario` (JSON em `TEXT`)** (nome → valor) para os seis campos com `filtro: true` no `jira_campos.yaml`; `OS Version` mantém a coluna própria | [valor padrão 25/10, reversível] |
| D2 | **Ficha de sincronização ganha** Model Name, Model Category, TG Request Type e Carrier Code; linhas antigas se completam na próxima reconciliação com a flag ligada; enquanto isso, aviso `CAMPOS_DO_DICIONARIO_INCOMPLETOS` | [valor padrão 25/10, reversível] |
| D3 | **Filtro por campo** na URL como `campo.<slug>=<valor>` (repetível), sem diferenciar maiúsculas nem espaços nas pontas; aplicado antes do cálculo | [valor padrão 25/10, reversível] |
| D4 | **Opções de filtro** só das issues visíveis dos últimos 180 dias; no máximo 200 valores por lista, com aviso `OPCOES_TRUNCADAS` | [valor padrão 25/10, reversível] |
| D5 | **Gaveta:** issue fora da visão → 404; histórico só para COORDENADOR e GERENTE; `linkJira` só com a flag `true` e instância `https` configurada | [valor padrão 25/10, reversível] |
| D6 | **Virtualização** acima de 200 linhas, linha de 36 px, janela com folga de 10 linhas; CSV sempre com a lista inteira | [valor padrão 25/10, reversível] |
| D7 | **Paleta Okabe-Ito** (`#0072B2`, `#E69F00`, `#009E73`, `#CC79A7`, `#56B4E9`, `#D55E00`) com 6 padrões e 6 formas; tabela equivalente em todo gráfico; se os Planos 5 a 7 já adotaram uma biblioteca, ela fica e só recebe paleta, padrão, forma e tabela | [valor padrão 25/10, reversível] |
| D8 | **Erros e avisos por código**: chaves `avisos.<CODIGO>` e `erros.<CODIGO>` em pt e en; código sem tradução mostra `avisos.DESCONHECIDO` com o código, nunca a mensagem pt-BR | [valor padrão 25/10, reversível] |
| D9 | **FE-11, FE-12, NE-09 e o universo de "no prazo"** entram como a auditoria descreve (ver plano, T10) | [valor padrão 25/10, reversível] |
| D10 | **Fichas para Central, Integridade e Auditoria**: Central e Integridade declaram `fontes` (cards de origem), sem JQL próprio | [valor padrão 25/10, reversível] |

## 5. O que fica para quem implementa

- A técnica de leitura dos campos SELECT no JSON do DC (`value` ou `name`), desde que os testes de `CamposDoDicionarioTest` passem.
- O layout exato da gaveta e da página de detalhe, dentro da gramática do plano visual (seção 5).
- O algoritmo de janela da `TabelaVirtual`, desde que os testes de contagem de linhas e de teclado passem.
- As escalas e os eixos dos gráficos SVG.
- Os textos em inglês das mensagens, equivalentes aos de pt-BR.

## 6. Critérios de aceitação verificáveis

| Critério | Prova |
|---|---|
| Os seis campos são gravados por issue e preenchidos a partir do `raw`, sem requisição ao Jira | `extraiSelectTextoELista`, `upsertGravaCamposDoDicionario`, `preencheRetroativamenteDoRaw`, `preenchimentoNaoFazRequisicao` (T1) |
| A ficha de sincronização pede os seis campos; a demonstração os traz | `fichaDeSincronizacaoPedeOsSeisCampos`, `fixturesDeDemoTemOsSeisCampos` (T1) |
| Opções de filtro só do escopo visível, alfabéticas e sem duplicar caixa | `opcoesSoDoEscopoVisivel`, `pessoasEmOrdemAlfabetica`, `valoresSemDuplicarCaixa` (T2) |
| Filtro por campo igual no card e no drill; agrupamento com drill do grupo | `drillTemOMesmoTamanhoDoCardComFiltroDeCampo`, `drillDoGrupoIgualAoGrupo` (T3) |
| Filtros na tela por seletor, sem texto livre para pessoa | `pessoaViraSeletorComAsOpcoesDoBackend` (T4) |
| Gaveta: 404 fora da visão; histórico só para coordenação e gerente; link só com a flag e sem credenciais | `tarefaForaDaVisao404`, `historicoSoParaCoordenacaoEGerente`, `semLinkComFlagDesligada`, `linkSemCredenciais`, `rotaNaoFazRequisicao` (T5) |
| A gaveta abre a partir de qualquer drill; o link abre em nova aba sem `opener`; o detalhe em nova aba mostra a mesma lista | `chaveDoDrillAbreGaveta`, `botaoJiraAbreEmNovaAbaSemOpener`, `detalheEmNovaAbaMesmaListaDoDrill` (T6) |
| Tabela com 30.000 linhas renderiza só a janela e é navegável por teclado; CSV completo | `renderizaSoAsLinhasVisiveis`, `tecladoRolaATabela`, `csvExportaTudo` (T7) |
| Cada item do menu mostra o estado certo para 403, erro, vazio e aviso bloqueante; erro sem texto técnico | `estados.test.tsx`, `erroNuncaMostraTextoTecnico` (T8) |
| Todo código de aviso e de erro tem tradução pt e en; 400 sempre como `ErroDto` | `todoCodigoTemTraducaoPtEEn`, `codigosConhecidosCobremOsEmitidos`, `respostas400SaoErroDto` (T9) |
| FE-11, FE-12, NE-09, E5, VG-02 e VG-03 (se mantidos), uma ficha por item do menu | `tempoAtivoPorPessoa`, `tabelaDeConcluidasComRealSobreEstimado`, `maiorTempoPorStatusNasNegativas`, `canceladaComVencimentoForaDoNoPrazoEmTresTelas`, `centralTemConcluidas30dComVariacao`, `cadaItemDoMenuTemFicha` (T10) |
| Gráficos com cor, padrão, forma e tabela equivalente | `serieTemCorPadraoEForma`, `graficoTemTabelaEquivalente`, `nenhumGraficoSoPorCor` (T11) |
| Documento de diferenças cita cada item e prova | `documentoCitaCadaItemEProva` (T12) |

## 7. Riscos, pendências e pontos de parada

**PONTO DE PARADA 1 — VG-02 e VG-03 na Central (T10, antes do Passo 3).** O relatório do app atual tem "Concluídas (30 d) com variação" e "Ciclo P50 (P85 no rodapé)" na visão geral; a Central do Plano 7 não os traz. Pergunta ao usuário:
1. **Manter na Central como KPIs secundários, com drill para Fechadas (Recomendada).** Por quê: preserva a equivalência com o app atual, e o cálculo já existe em Fechadas. Consequência: dois KPIs a mais na Central e dois testes de "card = drill".
2. **Remover e deixar só em Fechadas.** Por quê: a Central fica focada em atenção (o que exige ação), não em resultado. Consequência: o documento de equivalência do Plano 8 marca VG-02 e VG-03 como `removido`, com esse motivo.

Sem resposta, seguir com a opção 1 e registrar no `HANDOFF.md`.

**Pendências:**
- **Linhas antigas sem os quatro campos novos** só se completam numa reconciliação com a flag ligada (pelo usuário). Até lá, o aviso `CAMPOS_DO_DICIONARIO_INCOMPLETOS` sai nos cards. Registrar no `HANDOFF.md` como item da 1ª conexão (Plano 14).
- **Vocabulário real dos campos** (valores de SKU Type, Checklist Type etc.) só se confirma na 1ª conexão; as opções vêm dos dados, então nada precisa ser fixado em código.

**Impacto em outros planos:**
- **Plano 11:** a trava do DPO precisa cobrir o campo `historico` da gaveta (`/api/tarefa/{key}`), além das rotas de Auditoria e Integridade.
- **Plano 8:** o inventário de rotas (T1) passa a incluir `/api/filtros/opcoes` e `/api/tarefa/{key}`; a revisão visual (T5) e a acessibilidade (T4) passam pela gaveta, pela tabela virtual e pelos gráficos; o documento de equivalência (T3) usa a decisão do ponto de parada 1; o `guiaCitaCadaFicha` (T10) passa a ter as fichas da Central, Integridade e Auditoria.
- **Plano 12:** as métricas de HTTP usam o modelo de rota (`/api/tarefa/{key}`), nunca a chave.

**Riscos:**
- *Campo SELECT com formato inesperado no Jira real* (objeto sem `value`). Mitigação: o extrator ignora e o aviso de dados incompletos aparece; corrigir no Plano 14 com a amostra real.
- *Gráfico improvisado nos Planos 5 a 7 com biblioteca incompatível com a CSP do Plano 9.* Mitigação: `verificar-csp.mjs` no CI; se a biblioteca injetar estilo inline em HTML, trocar pelo componente próprio desta fase.
- *Opções de filtro lentas na escala de 30.000 issues.* Mitigação: `json_extract`/`json_each` sobre a coluna JSON (sem índice GIN no SQLite; índice de expressão só se o orçamento estourar) e janela de 180 dias; o orçamento do Plano 8, T6 mede.

**Migrações:** a desta fase usa a próxima livre na execução (hoje só existe a V1, então a próxima é a V2; os números V4 a V12 antigos eram só rótulos). Cada plano numera em sequência na ordem em que os PRs chegam à `main`; o Plano 16 (futuro) também usa a próxima livre. A auditoria previa V8 para o Plano 8; como ele usa "próximo número livre", não há conflito. Conflito real só se blocos rodarem em branches paralelas: nesse caso, renumerar a migração do PR que mesclar depois, antes do merge.

## 8. Glossário do que esta fase consome

| Nome | O que é | Origem |
|---|---|---|
| `IssueSnapshot`, `WorkStore` | modelo de leitura de issues | Plano 5, T2 |
| `FiltrosDeCard`, `CardDeLeitura`, `rotaDeCard`, `Escopo` | filtros comuns e rota genérica de card | Plano 5, T4 |
| `FieldDictionary`, `jira_campos.yaml` | dicionário de campos do Jira | Plano 2 |
| `sincronizacao.card.yaml` | ficha da sincronização | Plano 4A, T3 |
| `DadosDeDemonstracao` | dados fictícios sem Jira | Plano 4A, T10 |
| `PosseCalculator`, `RiscoCalculator`, `FatorDeRisco` | posse e fatores de risco | Planos 6, T11 e 5, T11 |
| `HistoricoDto`, `LinhaHistoricoDto` | histórico campo a campo | Plano 4, T7 |
| `CentralDto`, `ItemCentral` | Central de atenção | Plano 7, T15 |
| `ErroDto`, `enviarJson`, `ErroDaApi` | erro por código e cliente HTTP | Plano 9, T4 |
| `EVENTO_SESSAO_EXPIRADA` | aviso de sessão expirada | Plano 9, T5 |
| `campos_dicionario`, `CampoDeFiltro`, `TarefaDto`, `TabelaVirtual`, `EstadoDaTela`, `CodigosDeMensagem` (novos) | entregas desta fase | esta fase |

## 9. Referência ao plano

[`docs/superpowers/plans/2026-10-25-plano-10-complementos-de-telas-e-filtros.md`](../plans/2026-10-25-plano-10-complementos-de-telas-e-filtros.md): 12 tarefas, em um PR a partir da `main` com o Plano 7 mesclado.
