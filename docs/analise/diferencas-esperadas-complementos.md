# Diferenças esperadas: complementos de telas e filtros (Plano 10)

Cada item da auditoria do projeto (`docs/superpowers/cobertura-do-projeto.md`) tem aqui o que mudou, a prova (`Classe.metodo` de teste do backend, que o `DiferencasComplementosDocTest` confere; testes do frontend vêm pelo nome do arquivo) e a diferença em relação ao app atual (`legacy/`). Nada aqui toca o Jira: tudo foi provado com fixtures, MockEngine e SQLite real em arquivo temporário, com a flag de acesso ao Jira desligada.

## A4: opções de filtro pelo escopo visível

- **Mudou:** `GET /api/filtros/opcoes` devolve squads, pessoas (em ordem alfabética), tipos, prioridades, OS, os valores dos campos do dicionário, os filtros rápidos e os prefixos de chave, **só do escopo visível** de quem pergunta. Os selects das telas usam essa lista (nada de texto livre).
- **Prova:** `OpcoesDeFiltroRoutesTest.opcoesSoDoEscopoVisivel`, `OpcoesDeFiltroRoutesTest.pessoasEmOrdemAlfabetica`, `OpcoesDeFiltroRoutesTest.todaConsultaGravaAccessLog`; frontend: `Filtros.test.tsx`.
- **Diferença contra o app atual:** o app atual lia os valores do banco inteiro, sem escopo; aqui o usuário nunca vê o nome de quem está fora da sua visão.

## A5: filtros e agrupamentos pelo dicionário (Checklist Type, SKU Type e demais)

- **Mudou:** os seis campos marcados `filtro: true` em `jira_campos.yaml` viram `issue.campos_dicionario` (migração **V6**, JSON) e servem para filtrar (`campo.<slug>=valor`, repetível, sem diferenciar maiúsculas) e agrupar (`porCampo`) nas telas de trabalho. Linhas antigas sem os campos recebem o aviso `CAMPOS_DO_DICIONARIO_INCOMPLETOS` até a próxima reconciliação com a flag ligada pelo usuário (decisão registrada no HANDOFF).
- **Prova:** `FiltroPorCampoRoutesTest.drillTemOMesmoTamanhoDoCardComFiltroDeCampo`, `FiltroPorCampoRoutesTest.agrupamentoPorCampoComSemValorPorUltimo`, `FiltroPorCampoRoutesTest.parametroDeCampoNaoAmpliaAVisao`, `CamposDoDicionarioTest.extraiSelectTextoELista`; frontend: `PorCampo.test.tsx`.
- **Diferença contra o app atual:** o app atual não tem filtro por SKU Type nem por Checklist Type; só por OS.

## M1: gaveta universal da task

- **Mudou:** `GET /api/tarefa/{key}` (status, datas, histórico, tempo por status, posse, fatores de risco, campos do dicionário) e a `TarefaDrawer`/página `/detalhe` ("Abrir em nova aba"). A chave em qualquer lista abre a gaveta; a `GavetaDeTasks` é a única lista de drill. Chave fora da visão responde 404 (não confirma a existência); histórico só para coordenação e gerente.
- **Link para o Jira:** só aparece com a flag de acesso ligada pelo usuário, montado da URL da instância configurada, sem credenciais; com a flag desligada não há link (abri-lo seria acesso ao Jira).
- **Prova:** `TarefaRoutesTest.tarefaVisivelDevolveTudo`, `TarefaRoutesTest.tarefaForaDaVisao404`, `TarefaRoutesTest.historicoSoParaCoordenacaoEGerente`, `TarefaRoutesTest.semLinkComFlagDesligada`; frontend: `TarefaDrawer.test.tsx`, `Detalhe.test.tsx`.
- **Diferença contra o app atual:** o app atual não tem gaveta nem detalhe por task; só tabelas com a chave em texto.

## I-26 e I-27: busca por chave e filtros rápidos

- **I-26:** a caixa de busca no Shell abre a gaveta pela chave (ou pelo número, quando a visão tem um único prefixo). Prova: `TarefaRoutesTest.chaveInvalida400ComCodigo`; frontend: `BuscaPorChave.test.tsx`.
- **I-27:** `filtros_rapidos.yaml` (modelo `filtros_rapidos.exemplo.yaml`, opcional) nomeia combinações de parâmetros existentes (nunca pessoa). Prova: `FiltrosRapidosTest.modeloVersionadoEhValido`. Filtros de situação (só atrasadas etc.) ficam adiados.

## M2: gráficos e paleta categórica acessível

- **Mudou:** tokens `--cat-1..6` por tema (contraste de pelo menos 3:1 sobre a superfície) e os componentes `Barras`, `Linhas` e `Dispersao` com cor, padrão de preenchimento e forma do marcador, legenda sempre visível, resumo em `aria-label` e tabela equivalente ("Ver dados em tabela"). Os gráficos próprios existentes passaram a usar a paleta.
- **Prova (frontend):** `graficos.test.tsx` (`seisCoresComContraste3NaSuperficie`, `serieTemCorPadraoEForma`, `graficoTemTabelaEquivalente`, `nenhumGraficoSoPorCor`).
- **Diferença contra o app atual:** o app atual usava só cor.

## M3: VG-02 e VG-03 na Central

- **Mudou:** o ponto de parada foi resolvido pela opção recomendada (manter na Central): VG-02 (concluídas em 30 dias com variação relativa contra os 30 anteriores) e VG-03 (ciclo P50/P85 em 30 dias), cada um com destino no cartão de origem (FE-01, FE-03) e total do cartão igual ao do drill.
- **Prova:** `CentralCalculatorTest.centralTemConcluidas30dComVariacao`, `CentralCalculatorTest.centralTemCiclo30d`.
- **Diferença contra o app atual:** a variação é em porcentagem relativa, não em pontos percentuais.

## M4: FE-11, FE-12 e NE-09

- **Mudou:** tempo ativo por pessoa (FE-11), tabela de concluídas com Real÷estimado (FE-12) e "maior tempo por status" (NE-09, segmentos `maiorTempo:<status>`; o "por issue" aparece como motivo da linha na gaveta).
- **Prova:** `FechadasRoutesTest.tempoAtivoPorPessoa`, `FechadasRoutesTest.tabelaDeConcluidasComRealSobreEstimado`, `NegativasCalculatorTest.maiorTempoPorStatusNasNegativas`.
- **Diferença contra o app atual:** o app atual calculava esses três só na tela, sem drill nem escopo.

## M5: tabela virtual e estados de tela

- **Mudou:** `TabelaVirtual` (renderiza só as linhas visíveis acima de um limiar, teclado, ordenação sem mudar a contagem, CSV com tudo) e `EstadoDaTela` (carregando, vazio, erro com "tentar de novo", sem permissão, sessão expirada, filtro inválido, dados incompletos com link para a Qualidade só para o gerente). As telas com estado próprio (Auditoria, Integridade, Sincronização e outras) ficam fora do teste em tabela.
- **Prova:** `FichasDoMenuTest.centralEAuditoriaTemFicha` (fichas de todas as telas); frontend: `TabelaVirtual.test.tsx`, `EstadoDaTela.test.tsx`, `estados.test.tsx`.
- **Diferença contra o app atual:** o app atual renderizava a tabela inteira e mostrava erro técnico em texto.

## M7: mensagens por código, datas e números por idioma

- **Mudou:** todo 400/403/404 sai como `ErroDto(codigo, params)` e todo aviso por código; o frontend traduz por `erros.<COD>` e `cardpage.aviso.<COD>` em pt e en, e código sem tradução vira "Aviso do servidor (código X)" (nunca a mensagem em português solta). Datas e números seguem o idioma.
- **Prova:** `CodigosDeMensagemTest.todoCodigoTemTraducaoPtEEn`, `CodigosDeMensagemTest.codigosConhecidosCobremOsEmitidos`, `CodigosDeMensagemTest.respostas400SaoErroDto`; frontend: `mensagens.test.ts`.
- **Diferença contra o app atual:** o app atual devolvia 400 com texto em português no corpo.

## B4: ficha por item do menu

- **Mudou:** toda tela do menu tem `cards/<id>.card.yaml`; as exceções (sem ficha de cartão) estão listadas com motivo no próprio teste.
- **Prova:** `FichasDoMenuTest.cadaItemDoMenuTemFicha`.

## E5: o mesmo "no prazo" em todas as telas

- **Mudou:** canceladas e Epic ficam fora do universo nas três telas, e a regra é única.
- **Prova:** `RegraUnicaNoPrazoTest.mesmoUniversoComCanceladaEpicEResolvidaAntes`, `ConsistenciaEntreTelasTest.mesmoNoPrazoComCanceladaEEpicNoReforco`.

## E12: um só drill

- **Mudou:** não há mais `DrillDrawer` local; toda tela importa a `GavetaDeTasks`.
- **Prova (frontend):** `gavetaUnica.test.ts`.

## E13: um só parser de filtros

- **Mudou:** a Auditoria já usa os helpers compartilhados de texto, data e username; os filtros próprios dela (sinal, resultado) não existem nas outras telas. Os cartões leem filtros só por `FiltrosDeCard`.
- **Prova:** `FiltrosDeCardTest.lerFiltroDeCampoRepetivel` (parser único dos cartões, com os campos novos).

## Pendências

- Captura visual (1440 e 390 px, tema Claro) de Abertas com filtro de campo, da gaveta e de uma tabela virtual: não feita (sem navegador neste ambiente).
- Reconciliação completa para preencher os quatro campos novos nas linhas antigas: quando o usuário ligar a flag.
