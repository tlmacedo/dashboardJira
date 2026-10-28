# Progresso — plano: docs/superpowers/plans/2026-10-28-plano-17-cards-de-fluxo-e-gestao.md

Branch: `feat/contrato-do-cartao` (bloco 17a = Tarefas 1 a 7). Executor: Claude Code (subagente implementador), sem o usuário presente; pontos de parada resolvidos pela opção Recomendada e registrados como `Ruling:`. O 17b (Tarefas 8 a 13) fica para depois do 14b (ponto de parada PP5).

## Ponto de partida (main em 4a8fde5)

- Backend `./gradlew test --rerun-tasks`: 861 testes, 0 falhas. Frontend `pnpm vitest run`: 74 testes, 0 falhas.

## Checklist (17a)

- [x] Tarefa 1: registro de cartões e catálogo por papel (testes: backend 869 totais, 0 falhas; +8 em `CatalogoDeCartoesTest`)
- [x] Tarefa 2: drill genérico e teste de contrato (testes: ver contagem final no fechamento; +13 em `ContratoDeCartoesTest`)
- [x] Tarefa 3: componente `Cartao` e ajuda (ⓘ) (frontend 86 totais, 0 falhas; +12 em `Cartao.test.tsx`; build ok)
- [x] Tarefa 4: gaveta de tasks compartilhada e "Abrir no Jira" (backend 890 totais, 0 falhas; +8 em `LinkJiraTest`; frontend 104 totais, 0 falhas; +18 em `GavetaDeTasks.test.tsx`, +4 em `csv.test.ts`, −4 que saíram de `TempoEmStatus.test.tsx` sem mudança de asserção; build ok)
- [ ] Tarefa 5: migrar Tempo em status e Sincronização para o contrato
- [ ] Tarefa 6: tela Mapa de cartões
- [ ] Tarefa 7: revisão visual, acessibilidade e fechamento do 17a

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
- Ruling: o papel que filtra o catálogo vem de `papeisVigentes` (diretório de hoje; exceção só do `dev`), a mesma regra de `/api/me`, e não do `principal.roles` bruto.

## Minor adiados

(nenhum até agora)
