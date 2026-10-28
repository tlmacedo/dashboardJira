# Progresso — plano: docs/superpowers/plans/2026-10-28-plano-17-cards-de-fluxo-e-gestao.md

Branch: `feat/contrato-do-cartao` (bloco 17a = Tarefas 1 a 7). Executor: Claude Code (subagente implementador), sem o usuário presente; pontos de parada resolvidos pela opção Recomendada e registrados como `Ruling:`. O 17b (Tarefas 8 a 13) fica para depois do 14b (ponto de parada PP5).

## Ponto de partida (main em 4a8fde5)

- Backend `./gradlew test --rerun-tasks`: 861 testes, 0 falhas. Frontend `pnpm vitest run`: 74 testes, 0 falhas.

## Checklist (17a)

- [x] Tarefa 1: registro de cartões e catálogo por papel (commit 1d146f1; backend 869 totais, 0 falhas; +8 em `CatalogoDeCartoesTest`)
- [x] Tarefa 2: drill genérico e teste de contrato (commit e295e83; backend 882 totais, 0 falhas; +13 em `ContratoDeCartoesTest`)
- [x] Tarefa 3: componente `Cartao` e ajuda (ⓘ) (commit 2b959d1; frontend 86 totais, 0 falhas; +12 em `Cartao.test.tsx`; build ok)
- [x] Tarefa 4: gaveta de tasks compartilhada e "Abrir no Jira" (commit d7137b5; backend 890 totais, 0 falhas; +8 em `LinkJiraTest`; frontend 104 totais, 0 falhas; +18 em `GavetaDeTasks.test.tsx`, +4 em `csv.test.ts`, −4 que saíram de `TempoEmStatus.test.tsx` sem mudança de asserção; build ok)
- [x] Tarefa 5: migrar Tempo em status e Sincronização para o contrato (commit 3745dd8; backend 890 totais, 0 falhas; frontend 104 totais, 0 falhas; build ok; `Sincronizacao.test.tsx` sem nenhuma alteração; `TempoEmStatus.test.tsx` só nas linhas listadas em "Asserções alteradas")
- [x] Tarefa 6: tela Mapa de cartões (commit 7dd8418; frontend 114 totais, 0 falhas; +9 em `MapaDeCartoes.test.tsx`, +1 em `nav.test.ts`; build ok)
- [x] Tarefa 7: revisão visual, acessibilidade e fechamento do 17a (passos 1 e 2 feitos; capturas de tela NÃO feitas, ver "Revisão visual"; o passo 3 além do push — revisão por outro contexto, PR, CI e merge — fica com quem abrir a PR)

## Números finais do 17a (sem cache)

- Backend `./gradlew test --rerun-tasks` (JDK 21): **890 testes, 0 falhas, 0 ignorados** (861 + 29 novos: 8 `CatalogoDeCartoesTest`, 13 `ContratoDeCartoesTest`, 8 `LinkJiraTest`).
- Frontend `pnpm install --frozen-lockfile && pnpm test && pnpm build`: **114 testes (11 arquivos), 0 falhas; build ok** (74 + 40 novos: 12 `Cartao`, 18 `GavetaDeTasks`, 4 `csv` movidos, 9 `MapaDeCartoes`, 1 `nav`; −4 de CSV que saíram de `TempoEmStatus.test.tsx`). `pnpm lint`: só os 3 avisos antigos de `only-export-components`.
- `sh scripts/ci/varrer-dados-pessoais.sh`: `VARREDURA OK`.
- Nenhuma migração, nenhuma dependência npm nova, nenhuma chamada ao Jira (a flag ficou desligada; `LinkJiraTest` usa `Settings` com a flag ligada só para provar o texto da URL, sem criar cliente nem requisição).

## Revisão visual, acessibilidade e fechamento (Tarefa 7)

Critérios da seção 7 de `docs/design/cartoes-gramatica-e-wireframes.md`:

1. **Elementos do cartão e unidade:** `Cartao.test.tsx` cobre título, natureza, valor, estado, variação, procedência e ação; o valor chega formatado e com unidade pela tela.
2. **Contraste calculado dos tokens** (`node app/frontend/scripts/contraste-dos-tokens.mjs`, sobre `theme/tokens.css`, razão WCAG; mínimos 4,5:1 texto e 3:1 foco e componente; o script sai com código 1 enquanto o par `--line` falhar, por isso não está ligado à CI). Resultados:

| Par (uso) | Claro | Escuro | Sidia |
|---|---|---|---|
| texto em superfície (título, valor) | 17,76 | 14,72 | 17,53 |
| texto2 em superfície (rótulo, legenda, ⓘ) | 7,00 | 8,75 | 7,82 |
| texto2 em soft (cartão em hover) | 6,15 | 7,77 | 6,71 |
| primário em superfície ("Ver tasks", links) | 11,34 | 7,30 | 11,34 |
| primário em soft (hover) | 9,95 | 6,49 | 9,73 |
| estado ✓ (ok) em superfície | 5,44 | 13,09 | 5,44 |
| estado ● (warn) em superfície | 5,43 | 9,62 | 5,43 |
| estado ▲ (bad) em superfície | 6,54 | 7,29 | 6,54 |
| foco em superfície / em fundo | 6,87 / 6,41 | 7,30 / 8,00 | 6,87 / 6,37 |
| texto do botão primário | 11,34 | 8,15 | 11,34 |
| `--line` como borda de componente (mín. 3:1) | **1,36** | **1,34** | **1,37** |

   Todos passam, exceto `--line` como borda de **controle**. Correção feita no que esta fase criou: os campos do Mapa de cartões usam `--text2` na borda (≥ 7:1). A borda do cartão em si é divisória decorativa (o cartão é identificado por título e texto, não pela moldura) e o ⓘ não tem moldura. Os campos antigos da tela Tempo em status (`.campo input`) ainda usam `--line`: registrado em "Minor adiados".
3. **Estado nunca só por cor:** símbolo + texto em todo estado (`estadoTemSimboloETexto`); natureza com glifo + texto; aviso de incompleto com ● e texto; teste do Mapa e do cartão sem chave crua.
4. **Teclado:** ⓘ antes da ação na ordem de Tab (`cartaoTemUmaAcaoPrincipalEIconeDeAjudaAlcancavel`); Enter abre e Esc fecha o balão devolvendo o foco; gaveta com foco no Fechar, Tab preso, Esc fecha e devolve o foco, resto inerte. O anel de foco do cartão é o `::after` da ação (contorna o cartão todo, ≥ 6:1).
5. **Leitor de tela:** `<article>` rotulado pelo título; o botão da ação principal leva título + valor + "Ver tasks" por `aria-labelledby`; balão com `role="group"` e rótulo; tabelas rotuladas pelo título do cartão; esqueleto com texto só para leitor.
6. **Sem salto de layout e sem rolagem horizontal a 360 px:** altura mínima fixa por variante em linha (`semSaltoDeLayoutNoEsqueleto`); grade de KPIs vira 1 coluna abaixo de 640 px; tabelas rolam dentro do cartão (`overflow-x: auto`); Mapa e gaveta empilham abaixo de 640 px; balão do ⓘ com largura `min(320px, 80vw)` ancorado à direita. Conferido por leitura do CSS e dos testes, **não** em navegador.
7. **Texto do ⓘ em pt e en:** `todoCartaoTemTextoPtEEn` (backend) lê `pt.json` e `en.json` e exige título, "o que mostra", "como calcula", grupo, natureza e fontes nos dois idiomas, com "o que mostra" e "como calcula" diferentes entre os idiomas. `prefers-reduced-motion` desliga o esqueleto pulsante e a animação da gaveta.

**Capturas de tela não foram feitas:** o ambiente desta execução não tem navegador (nem Chrome, nem Playwright), e o usuário não estava presente. Pendente para quem rodar o painel (`PAINEL_MOCK=1 pnpm dev` serve o catálogo e o drill fictícios, ou a demonstração do backend): olhar Tempo em status, Sincronização, Mapa de cartões e a gaveta nos temas Claro, Escuro e Sidia, a 360, 768 e 1280 px, e passar uma simulação de daltonismo. Nada nesta execução usou dado real.

**Passo 2:** `docs/superpowers/plans/2026-10-24-indice-dos-planos.md` marca o 17a como executado (PR pendente); a seção 6 da spec foi levada ao `HANDOFF.md` (nova seção "Regras do contrato do cartão") e a skill `.cline/skills/novo-card-painel/SKILL.md` aponta para o contrato.

**Passo 3 (entrega):** feito aqui apenas o `git push -u origin feat/contrato-do-cartao`. Não foram abertos PR nem merge (fora do pedido). Autorrevisão pelo checklist do guia (seção 5) feita sobre o diff inteiro: visão por papel no catálogo e no drill (testes de vazamento), `access_log` em toda chamada inclusive 400/403/404 e sem chaves de issue, sem dado pessoal, SQL só parametrizado (nada novo), relógio e fuso injetados, sem ranking de pessoas, símbolos e pt/en, nenhum teste vazio (mutação feita em TS-03 e em uma chave de tradução). Falta a revisão independente em contexto limpo, as 7 condições do fluxo de entrega, o CI e o merge.

## Estado herdado (conferido no código)

| Nome do plano | Nome real | Observação |
|---|---|---|
| `TempoEmStatusRoutes.kt`, `TempoEmStatusDto.kt`, rota de drill com `kind` | iguais, em `cards/` | Rota antiga: `GET /api/drill/tempo-em-status?kind=...` |
| `TempoEmStatus.tsx` (função local `Kpi`) | função local chama-se `KpiBotao` (dentro de `Kpis`) | |
| `DrillDrawer.tsx`, `csv.ts`, `format.ts`, `api.ts` | iguais, em `screens/TempoEmStatus/` | |
| `Sincronizacao.tsx` | igual | A tela não tem "KPIs" propriamente: faixa de modo, blocos por instância e retenção |
| `nav.ts`, `Shell`, `Menu`, `RoleContext`, `i18n/*.json`, `LangProvider` | iguais | |
| `AccessLog`, `executarAuditado`, `Visibility`, `Papel` | `AccessLog` e `Visibility` iguais; `executarAuditado` chama-se `executar` (privada em `TempoEmStatusRoutes.kt`); `Papel` chama-se `Role` | |
| `FonteDoDado`, `EntradaDeCartao`, `LinhaDeDrill` | não existiam: criados nesta fase | `LinhaDeDrill` é alias de `DrillRow` |

## Rulings

- Ruling: `papeis: Set<Papel>` do plano vira `Set<Role>` (nome real do enum) — o código já usa `Role` em toda parte — custo se errado: nenhum (renomear).
- Ruling: `FonteDoDado` tem 6 valores (ISSUE, CHANGELOG, ISSUE_LINK, WORKLOG, SINCRONIZACAO, CONFIGURACAO) e `LinhaDeDrill` é `typealias` de `DrillRow` — o plano cita os dois tipos sem defini-los; reaproveitar `DrillRow` evita duas formas de linha — custo se errado: trocar o alias por classe própria.
- Ruling: `EntradaDeCartao(ctx, store, principal, parametros)` leva os parâmetros de URL (ktor `Parameters`) — valores e drill usam o mesmo `lerFiltros`, o que garante "número = drill" com os mesmos filtros — custo se errado: nenhum.
- Ruling: o catálogo registra TS-01 a TS-07 e também SY-01 a SY-03 (Sincronização, só GERENTE, `temDrill=false`) já na Tarefa 1 — a spec (seção 8) exige "todos os cartões atuais" e a Tarefa 5 precisa de metadados para migrar a Sincronização — custo se errado: remover 3 cartões e 18 chaves de texto.
- Ruling: TS-05 ("Onde o tempo é gasto") registrado à parte de TS-04, com os mesmos segmentos (a tela mostra os dois na mesma seção) — o inventário lista os dois — custo se errado: remover um registro.
- Ruling: TS-02 (eficiência, um percentual) abre as tasks analisadas (numerador e denominador), conforme a regra 5 do contrato — antes não era clicável.
- Ruling: `executarAuditado` do plano é `executar` (privada) em `TempoEmStatusRoutes.kt`; passou a `internal`, aceita bloco `suspend` e responde `ErroDto` quando o `ErroHttp` leva código — necessário para o 400 com código `SEGMENTO_INVALIDO` e para a rota do catálogo reutilizar a mesma auditoria — custo se errado: nenhum.
- Ruling: a resposta do drill genérico traz `tituloChave` (chave de tradução) no lugar de `titulo` — o backend não carrega texto; a tela traduz — custo se errado: renomear o campo no DTO e na gaveta.
- Ruling: a "fixture padrão" do teste de contrato são os dados de demonstração fictícios (`DadosDeDemonstracao`, usuários `dev.alfa1`, `lider.alfa`...) com diretório de papéis em memória; assim o teste não duplica um semeador de issues e o mesmo conjunto serve aos planos seguintes.
- Ruling: `drillOrdenaPessoasAlfabeticamente` verifica (a) os segmentos por pessoa de TS-06 em ordem alfabética e (b) as linhas de toda gaveta em ordem natural de chave, nunca por tempo ou pessoa; o drill não ordena por responsável porque a lista é de tasks, não de pessoas.
- Ruling: `todaRotaDeCardEstaNoRegistro` percorre `RoutingNode.children` do servidor de teste e exige que cada `/api/cards/<tela>` seja a `tela` de algum cartão registrado; um teste irmão prova que uma rota extra é acusada.
- Ruling: `Cartao` recebe o `titulo` já traduzido pela tela (a mesma chave `tituloChave` do catálogo) e usa o catálogo só para natureza e ajuda — assim a tela continua útil se `/api/cartoes/catalogo` falhar ou demorar, e o ⓘ some em vez de quebrar — custo se errado: derivar o título do catálogo e mostrar esqueleto até ele chegar.
- Ruling: no tipo `kpi` o corpo (valor, legenda, estado e "Ver tasks") fica DENTRO do botão da ação principal; título, chip e ⓘ ficam fora (irmãos) e acima da camada `::after` — o nome acessível do botão é título + conteúdo por `aria-labelledby`. Nas outras variantes o corpo é livre (z-index acima do `::after`) e a ação é um botão de rodapé. Preserva as asserções de Tempo em status (botão com o valor no texto e o símbolo ⏸).
- Ruling: os formatadores genéricos (`numero`, `inteiro`, `percentual`, `dataCurta`) passaram para `shared/format.ts`; `screens/TempoEmStatus/format.ts` os reexporta e mantém o que é da tela — o `Cartao` e a gaveta precisam deles e não devem importar de uma tela.
- Ruling: o ⓘ tem o rótulo fixo "Sobre este cartão" (sem o título) — um rótulo com o título casaria com `getByRole('button', {name: /Paradas além do normal/})` nos testes existentes e duplicaria o nome do cartão para leitor de tela, que já anuncia o `<article>` rotulado pelo título.
- Ruling: o montador do link é `LinkJira.paraChaves(settings, campos, instancia, chaves)` (objeto em `cards/contrato/`); usa `JqlBuilder.keysIn` (chave suspeita é rejeitada, nunca escapada) e devolve `https://host[:porta][/contexto]/issues/?jql=...`; base com credencial, sem https, com consulta ou fragmento dá `null`. Como o Plano 10 ainda não existe, não há `LinkJira` anterior a reutilizar; o Plano 10 deve estender este objeto.
- Ruling: o cartão informa a instância do Jira pelo membro opcional `CartaoRegistrado.instancia` (padrão nulo; TS-0n devolve `samsung`) — o drill genérico precisa dela para o link; membro com valor padrão não quebra os cartões futuros.
- Ruling: o `access_log` guarda a quantidade pelo extra `total` (atributo `EXTRAS_DE_AUDITORIA` da chamada, posto pelo bloco da rota); chaves de issue, JQL e endereço do Jira nunca entram (testado).
- Ruling: a gaveta só aceita `linkJira` https e sem credencial (defesa contra `javascript:`/`http:` vindos de uma resposta adulterada), além de o backend já montar só https.
- Ruling: o teste `pessoasEmOrdemAlfabetica` do plano ficou como `ordemDaListaVemDaApiSemRanking` na gaveta (a lista é de tasks, não de pessoas: a ordem natural de chaves vem do backend e a gaveta não reordena por responsável nem por tempo); a ordem alfabética de pessoas é verificada no contrato do backend (`drillOrdenaPessoasAlfabeticamente`, segmentos de TS-06) e na matriz da tela, que já tem teste. A coluna Responsável mostra `—` para null (fora da visão OU sem responsável: o `DrillRow` não distingue os dois, então "outra pessoa" seria falso para task sem dono) — custo se errado: distinguir no DTO e trocar o texto.
- Ruling: `csv.ts` foi para `shared/csv.ts` e seus 4 testes para `shared/csv.test.ts`; as regras de tabela e de gaveta ganharam `shared/tabela.css` e `shared/gaveta.css` (as cópias em `tempo.css` saem na Tarefa 5, junto do `DrillDrawer`).
- Ruling: na Tarefa 5 os três KPIs e as seções (TS-04, TS-06, TS-07) de Tempo em status usam `Cartao`; a Sincronização usa `Cartao` nos três blocos (SY-01 faixa do modo, SY-02 por instância, SY-03 retenção), pois a tela não tem KPIs numéricos. TS-05 não tem elemento próprio na tela (a seção de barras é TS-04). `Cartao` ganhou a prop `idDoTitulo` para as tabelas continuarem nomeadas pelo título (`aria-labelledby`).
- Ruling: o `DrillDrawer`, a rota `/api/drill/tempo-em-status`, a função `fetchDrill`, o tipo `DrillRow` do frontend, as chaves `tempo.gaveta.*`, `tempo.fechar`, `tempo.verTasks` e as regras de KPI, bloco, tabela e gaveta de `tempo.css` foram removidos; o mock de desenvolvimento (`dev-mock/mockApi.ts`) serve o catálogo e o drill genérico.
- Ruling: o Mapa de cartões é o item `mapaCartoes` (rota `/mapaCartoes`) em Governança, para COORDENADOR e GERENTE; a tela também recusa (aviso, sem chamar a API) quem não pode vê-la, como a `Pagina` provisória faz — é conveniência de interface, a autorização real continua no backend, que já filtra o catálogo por papel. Filtros de grupo, natureza e busca vivem na URL (`g`, `n`, `q`); a busca ignora acento e caixa.
- Ruling: "tela onde vive" vem do campo `tela` do catálogo ligado ao item do menu por id; o único caso em que os dois ids diferem hoje (`tempo-em-status` do backend e `tempo` do menu) está numa tabela pequena da tela (`TELA_PARA_MENU`). Tela sem item no menu aparece pelo id, sem link.
- Ruling: o papel que filtra o catálogo vem de `papeisVigentes` (diretório de hoje; exceção só do `dev`), a mesma regra de `/api/me`, e não do `principal.roles` bruto.

## Asserções alteradas (Tarefa 5)

Nenhuma asserção de comportamento foi alterada. Mudaram apenas as entradas dos testes, por causa da rota única do drill:

- `TempoEmStatus.test.tsx`: a constante `DRILL` passou de `/api/drill/tempo-em-status` para `/api/cartoes/TS-03/tasks`; a resposta simulada do drill passou de uma lista de linhas para o objeto do drill genérico (`linhas(n)` devolve `TasksDoCartao`); o teste `clicarNoKpiAbreAGaveta` procura `segmento=paradas` no lugar de `kind=paradas`; a importação de `DrillRow` virou a de `TasksDoCartao`; os 4 testes de CSV foram movidos sem mudança para `shared/csv.test.ts`. Mesmos seletores, mesmos textos, mesmo foco (inclusive o ⏸ no KPI e o foco de volta ao KPI após Esc).
- `TempoEmStatusRoutesTest.kt` (backend): todas as chamadas a `/api/drill/tempo-em-status?kind=X` passaram a `/api/cartoes/<TS-nn>/tasks?segmento=X` por um auxiliar (`rotaDrill`); o corpo agora é `DrillGenericoDto` (as asserções leem `linhas`); a lista de rotas do `access_log` no teste `consultaEhRegistrada` troca a rota antiga pela nova. Os 400 de filtro continuam sem corpo.
- `DadosDeDemonstracaoTest.kt`: a leitura das analisadas usa a rota nova e o campo `linhas`.

## Minor adiados

- Campos de filtro de Tempo em status (`tempo.css`, `.campo input`) e demais controles antigos usam `--line` na borda (1,3:1, abaixo de 3:1 para componente). Cenário: usuário com baixa visão não distingue o campo vazio. Sugestão: token de borda de controle (ex.: `--control-line`) nos três temas, em PR próprio de design (o plano pede "sem cor nova" no 17a).
- A procedência "atualizado há X" do `Cartao` existe e é testada, mas nenhuma tela a alimenta: o instante da última sincronização só está em `/api/sync/status` (gerente). Cenário: o cartão mostra `n`/atualização só quando o Plano 5a expuser um instante por cartão para todos os papéis.
- `DrillRow.responsavel` nulo não distingue "fora da visão" de "sem responsável"; a gaveta mostra `—` nos dois. Cenário: wireframe pede "outra pessoa" para fora da visão. Sugestão: campo `foraDaVisao` no DTO quando o 5a mexer no drill.
- TS-05 ("Onde o tempo é gasto") está no registro e no Mapa de cartões, mas a tela de Tempo em status mostra TS-04 e TS-05 numa seção só (barras de mediana e P85). Cenário: o Mapa lista um cartão que não tem bloco próprio na tela. Sugestão: separar na revisão de design do 14b.
- Capturas de tela nos três temas e três larguras (ver "Revisão visual").

---

# Bloco 17b (Tarefas 8 a 13), branch `feat/cards-de-fluxo-e-gestao`

Executor: Claude Code (subagente implementador), sem o usuário presente. Por ordem do usuário em 28/10 o 17b roda ANTES do 14b (o ponto de parada PP5 está resolvido: a calibração com dados reais fica para depois). Pontos de parada resolvidos pela opção Recomendada e registrados como `Ruling:`. Sem migração, sem dependência npm nova, sem acesso ao Jira.

Ponto de partida (main `b8d203b`, 5b mesclado): backend `./gradlew test --rerun-tasks` 1224 testes, 0 falhas; frontend 233 testes.

## Checklist (17b)

- [x] Tarefa 8: Fluxo da semana (I-01) — backend 1240 testes (+9 `FluxoDaSemanaCalculatorTest`, +7 `FluxoDaSemanaRoutesTest`), frontend 247 (+14 `FluxoDaSemana.test.tsx`); cartões `FX-01` e `FX-02`
- [x] Tarefa 9: Envelhecimento por etapa (I-05) — backend 1257 testes (+10 `AgingWipCalculatorTest`, +7 `AgingWipRoutesTest`), frontend 258 (+11 `AgingWip.test.tsx`); cartões `AW-01` e `AW-02`
- [x] Tarefa 10: Bloqueios entre tasks (I-08) — backend 1275 testes (+10 `BloqueiosCalculatorTest`, +8 `BloqueiosRoutesTest`), frontend 268 (+10 `Bloqueios.test.tsx`); cartões `BQ-01` a `BQ-05`; demonstração ampliada (ver Ruling)
- [x] Tarefa 11: Controle do ciclo e tendência (I-02 e I-03) — backend 1292 testes (+10 `ControleDoCicloCalculatorTest`, +7 `ControleDoCicloRoutesTest`), frontend 280 (+11 `ControleDoCiclo.test.tsx`, +1 em `Fechadas.test.tsx`); cartões `CT-01` e `CT-02`
- [x] Tarefa 12: Aba Cruzamentos (I-10) — cartão `CZ-01`; números da suíte na linha do fechamento (Tarefa 13)
- [ ] Tarefa 13: Fechamento do 17b

## Estado herdado do 17b (conferido no código)

| Nome do plano | Nome real | Observação |
|---|---|---|
| `SerieSemanal` (Previsão) | `trabalho/SerieSemanal.kt`, igual | O Fluxo da semana chama `SerieSemanal.calcular` (sem recalcular entradas, saídas e canceladas) |
| `CardDeLeitura`/`rotaDeCard`, `Cartao`/`GavetaDeTasks` | iguais (5a e 17a) | |
| `Fluxo.exigirStatusMapeados` | igual | |
| `TimeInStatusCalculator` | `domain/`, igual | |
| `issue_link`, `LinkJira` | `sync/IssueLinkStore.kt` (`from_key` bloqueia `to_key`), `cards/contrato/LinkJira.kt` | |
| Cartões novos | prefixos `FX`, `AW`, `BQ`, `CT`, `CZ` | Escolhidos para não colidir com os do inventário (`VG`, `CR`, `IN`, `CA`, `CP`, `DI`, `MP`, `GA`, `SI`, `RB` ...) |

## Rulings do 17b

- Ruling: Fluxo da semana (T8) — a janela são 12 semanas que terminam na semana de HOJE (marcada `emCurso`, incompleta); o sinal "backlog crescendo" só olha as semanas FECHADAS (a entrada superou a saída, estritamente, em 3 ou mais seguidas até a última fechada). O wireframe 5.1 mostra `s-0` e o plano diz "últimas 12 semanas"; a Previsão usa só as fechadas, então a coerência é testada nas semanas fechadas em comum (`mesmaSerieQueAPrevisao`). Custo se errado: passar `ultimaSemanaFechada(hoje)` em `FluxoDaSemanaCalculator`.
- Ruling: Fluxo da semana (T8) — "começaram" = `Fluxo.inicioAtivo` agrupado por `Fluxo.semanaDe`; é a única série que não vem de `SerieSemanal` (a função única só cobre entrada, saída e canceladas). O saldo do backlog é entrou − saiu − canceladas (o cancelamento sai do backlog sem ser entrega).
- Ruling: Fluxo da semana (T8) — a ficha `fluxo-da-semana` é igual à da Previsão (3 tipos Checklist, sem excluir status), para a entrada e a saída coincidirem; novo código de aviso `AMOSTRA_PEQUENA_NA_JANELA` (o `AMOSTRA_PEQUENA` existente fala em percentis de um tipo, o que não serve a uma contagem). O plano pedia `AMOSTRA_PEQUENA`; o texto novo é `cardpage.aviso.AMOSTRA_PEQUENA_NA_JANELA`. Custo se errado: usar o código antigo e trocar o texto.
- Ruling: Fluxo da semana (T8) — a linha tracejada do saldo acumulado do wireframe 5.1 não foi desenhada; o saldo acumulado está na tabela equivalente (colunas Saldo e Acumulado) e o saldo da janela no cartão. Custo se errado: acrescentar uma polilinha em `GraficoDeFluxo.tsx`.
- Ruling: Fluxo da semana (T8) — as rotas de todos os cards do 17b são registradas por `rotasDosCardsDeFluxo` (chamada de dentro de `rotasDosCardsDeTrabalho`) e os cartões por `cartoesDeFluxo()` (chamada de `cartoesDeTrabalho()`), para não mexer em `Application.kt` nem em `CatalogoPadrao.kt` (arquivos disputados por outros planos).
- Ruling: Aging WIP (T9) — a idade de uma task é o tempo desde a ÚLTIMA entrada no status atual (`Fluxo.diasNoStatusAtual`, a convenção de NG-05) e a faixa da etapa é o P50 e o P85 do tempo TOTAL que cada concluída passou naquele status (`TimeInStatusCalculator.perStatus`, a mesma conta de TS-04). Uma task que entrou duas vezes na etapa é comparada, portanto, com o total das concluídas, não com a permanência por entrada. Custo se errado: medir a permanência por entrada no calculador.
- Ruling: Aging WIP (T9) — etapas = os status de categoria ativa, espera ou bloqueado da `ordem` do `status_map` (mesmo sem task agora: a coluna existe no fluxo) mais os status em andamento fora da ordem, em ordem alfabética; A fazer e concluídos nunca são etapa. Sem `ordem` configurada vale o aviso `ORDEM_DO_FLUXO_AUSENTE` e só aparecem as etapas com task.
- Ruling: Aging WIP (T9) — o histórico da faixa segue o ESCOPO de quem consulta mas não os filtros de tipo e prioridade (como Negligenciadas e Abertas); etapa com menos de 5 concluídas fica sem faixa, ninguém conta como "acima do normal" nela e o aviso `AMOSTRA_PEQUENA` (existente, com `tipo` = o status) é emitido só para etapa que tem task agora.
- Ruling: Aging WIP (T9) — o segmento do drill leva o NOME do status (`etapa:<status>`, `acima:<status>`), vocabulário do fluxo e nunca chave de issue ou algo derivável dela; o `access_log` guarda esse segmento. O ponto do gráfico leva a chave da task mas nenhuma pessoa.
- Ruling: Aging WIP (T9) — o clique no PONTO (abrir a task) espera a gaveta universal do Plano 10; hoje o ponto é uma imagem rotulada (chave, idade, etapa, situação em texto) e cada etapa e cada contagem de "acima do normal" na tabela resumo abrem a lista. A tabela de tasks ("Ver tasks em tabela") é o equivalente textual do gráfico, sem coluna de pessoa.
- Ruling: Bloqueios (T10) — tipo e direção do vínculo vêm da configuração (`vinculos.yaml`, `tipoDeLink`) e da convenção do Jira já gravada em `issue_link` (`from_key` "bloqueia" `to_key`; vínculo simétrico é ignorado). Nada foi confirmado em servidor real (a flag segue desligada): a matriz de compatibilidade e a 1ª conexão (Plano 14a) confirmam o `type.name` e a direção. Custo se errado: ajustar `tipoDeLink` no YAML ou inverter `fromKey`/`toKey` em `BloqueiosCalculator`.
- Ruling: Bloqueios (T10) — a outra ponta do vínculo é lida direto de `issue` (`LeitorDePontas`, sem a ficha) porque só assim se distingue "concluída" de "fora da base" mesmo quando a bloqueadora é de um tipo que nenhum card lê; aberta = sem data de resolução, como no `WorkStore`. A bloqueada vem do `WorkStore` (ficha `bloqueios` = tipos de Abertas), com escopo e filtros.
- Ruling: Bloqueios (T10) — exclusão do par Task x Review: o vínculo em que exatamente UMA ponta é Review (`VinculosConfig.ehReview`) fica fora do card (é o par da Divergência), mas conta como "causa" para a verificação `BLOQUEADA_SEM_CAUSA`. Ponta fora da base NÃO conta como bloqueio (pode estar concluída): a task vai para o número "Verificar no Jira" (BQ-05) e a linha mostra a chave do vínculo; conta como causa (não é "sem causa").
- Ruling: Bloqueios (T10) — bloqueadora de pessoa fora da visão: entra nos números de bloqueadas e de prazo em conflito (a dependência existe) mas aparece como "task de outro squad" SEM chave, nome, resumo ou status; mostra o vencimento dela (dado não identificador, necessário ao conflito). O BQ-02 (bloqueadoras) conta e abre só as ao alcance; as escondidas entram em `bloqueadorasForaDaVisao`, sem drill (nada a mostrar sem expô-las). Texto do detalhe e do `access_log` nunca citam chave escondida (testado).
- Ruling: Bloqueios (T10) — `BLOQUEADA_SEM_CAUSA` fica como cartão BQ-04 deste card (status de categoria bloqueado ou flag de impedimento, sem bloqueadora aberta nem ponta fora da base) e como ponto de extensão: `BloqueiosCalculator.CODIGO_SEM_CAUSA` e `BloqueiosAnalise.semCausa` são o que a Qualidade dos dados (Plano 7b, que ainda não existe) deve chamar. O 7b só precisa registrar a verificação informativa e ler esta lista.
- Ruling: Bloqueios (T10) — demonstração ampliada (indispensável): a demo só tinha vínculos Task x Review com a bloqueadora concluída, então BQ-01, BQ-02 e BQ-03 ficariam zerados na tela em modo desenvolvimento e no teste de contrato. Dois vínculos Blocks entre tasks abertas foram acrescentados a `samsung.json` (DEMO-15 bloqueia DEMO-13, que vence antes dela, e DEMO-26 bloqueia DEMO-18; cada um nas duas pontas) e as duas asserções de contagem que fixavam "dois pares" (`DadosDeDemonstracaoTest.carregaVinculosDaDemo` e `FixturesDeDemoTest`) passaram a quatro; os 2 pares Task x Review continuam exatos. O BQ-05 (ponta fora da base) continua zerado na demo (um vínculo para chave inexistente só apareceria numa ponta e quebraria a regra "cada link nas duas pontas"); o par positivo dele está no cenário BQ-1 (`BloqueiosRoutesTest`). Custo se errado: remover os 4 trechos `issuelinks` e voltar as duas contagens para 2.
- Ruling: Controle do ciclo (T11) — é um card próprio (`/api/cards/controle-ciclo`, cartões `CT-01` e `CT-02`, tela de Resultados) renderizado como SEÇÃO da tela Fechadas; ele reutiliza `Fluxo.cicloDias` (a mesma conta do FE-08) mas não o DTO de Fechadas, porque a janela é outra (12 meses, não 90 dias). A seção lê os mesmos filtros da URL da página; `semOutliers` também vai para a URL.
- Ruling: Controle do ciclo (T11) — o "excluível por YAML" do plano virou o parâmetro `semOutliers=true` (400 para outro valor) com o limite de 30 dias corridos como constante (`LIMITE_OUTLIER_DIAS`), exatamente como o Tempo em status já faz. Um arquivo YAML novo exigiria mexer no `AppContext` e no `testContextComBanco`, arquivos disputados por outros planos; o limite aparece na resposta (`limiteOutlierDias`) para a tela não repeti-lo. Custo se errado: criar `ciclo.yaml` e ler o limite no `AppContext` (o plano 14b pode calibrar o valor).
- Ruling: Controle do ciclo (T11) — sem `de` informado a série cobre os 12 MESES inteiros que terminam no mês de `ate` (do dia 1 do mês 11 meses antes), não 365 dias soltos, para não sobrar um 13º mês parcial; com `de` informado vale o período dado. A média móvel é centrada e cortada nas pontas (média dos pontos disponíveis dentro da janela de 20% ímpar, mínimo 5); o Jira não documenta o tratamento das pontas. Custo se errado: trocar `subList` por janela só interna em `ControleDoCicloCalculator`.
- Ruling: Controle do ciclo (T11) — outlier vale em dias CORRIDOS qualquer que seja a unidade do card (como o Tempo em status), e o ponto outlier é marcado (losango e texto "outlier") quando a opção está desligada. O segmento do ponto é `dia:<aaaa-mm-dd>:<tipo>` (data de conclusão, nunca chave de issue); os pontos do gráfico são clicáveis só pelo mouse (muitos para a ordem do Tab) e a tabela de pontos é o equivalente por teclado e leitor de tela, com um botão por ponto. O clique no ponto abre as tasks do dia (a gaveta da task é do Plano 10).
- Ruling: Cruzamentos (T12) — lista fechada de eixos: squad, categoria de status, status, tipo, prioridade, OS Version, faixa de idade, semana de vencimento, com ou sem label de atraso e pessoa (só como linha). Os eixos de campos do dicionário (Checklist Type e SKU Type) NÃO foram oferecidos: o Plano 10 (campos do dicionário) ainda não existe. O ponto de extensão é `CruzamentosCalculator.DICIONARIO_PENDENTE` (pedi-los é 400 e eles não aparecem em `eixosDeLinha` nem em `eixosDeColuna`); o Plano 10 acrescenta o eixo ao enum `Eixo`, a ordem e o texto. OS Version entrou porque é coluna da issue (`os_version`), não campo livre do dicionário. Custo se errado: tirar `os` do enum.
- Ruling: Cruzamentos (T12) — universo = tasks ABERTAS (fora da categoria concluído), com a ficha, o escopo e os filtros de Abertas; semana de vencimento = segunda-feira da semana do `duedate`; "com label de atraso" = alguma label que `LabelsDeAtraso` reconhece como justificativa ou tardia. `linhas` igual a `colunas`, `colunas=pessoa`, eixo ou medida desconhecidos são 400 sem eco do valor; valor vazio volta ao padrão (`tipo` x `categoria`, contagem).
- Ruling: Cruzamentos (T12) — "pessoa fora da visão = outra pessoa" não ocorre: o universo já é filtrado pela visão (`permiteAtual`), então só há linhas de pessoas ao alcance (e `(sem)` = sem responsável, só para coordenação e gerente). O usuário vê a própria linha; o líder, as do squad dele. A ordem de cada eixo é a dele (categoria pelo fluxo, status pela `ordem`, prioridade de Highest a Lowest, idade natural, vencimento cronológico, o resto alfabético, `(sem)` por último), nunca por contagem; sem total por linha quando as linhas são pessoas (nem segmento `l:`).
- Ruling: Cruzamentos (T12) — os segmentos do drill são POSICIONAIS (`c:<linha>:<coluna>`, `l:`, `k:`, `total`; 1 = primeiro valor da lista da própria resposta), então nenhum valor de eixo (que pode ser username) vai para o `access_log`; o índice vale para a mesma consulta (mesmos filtros e eixos, que a gaveta repassa). `linhas`, `colunas` e `medida` entram na lista `FILTROS_AUDITADOS` de `TempoEmStatusRoutes.kt` (só os ids dos eixos, para a auditoria saber o que foi consultado; testado que username e chave nunca aparecem).
- Ruling: Cruzamentos (T12) — idade P50 por célula usa `Fluxo.idadeDias` (primeira entrada em andamento, ou a criação), na unidade do filtro; célula com 1 a 4 tasks leva `amostraPequena` (célula vazia não). A "faixa mais alta" da tela é 75% ou mais do maior valor da tabela (número, padrão hachurado e o texto "faixa mais alta" no rótulo do botão); a regra é da tela e pode ser recalibrada com dados reais.
- Ruling: tradução — as chaves novas de pt e en ficam num único bloco contíguo logo depois de `nav.configuracao` (menos chance de conflito de merge com os Planos 6a e 12).
