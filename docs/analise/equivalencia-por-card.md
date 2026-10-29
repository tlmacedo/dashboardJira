# Equivalência por card: app legado × painel novo

Conferência do Plano 8, Tarefa 3. Para cada card do inventário do app Python (`legacy/`, em `docs/analise/relatorio-cards-app-atual.md`) este documento diz **qual(is) card(s) do catálogo do painel novo o substituem**, **se a conta é a mesma** e **onde está a prova**. É a base do "sim" do usuário para a remoção do `legacy/` (Plano 8, Tarefa 9): sem esse "sim", nada em `legacy/` é tocado.

- Data: 2026-10-29. Escrito só a partir dos documentos de diferenças esperadas, do inventário e dos testes; nenhum dado real foi lido e nenhum servidor Jira foi acessado.
- A coluna **Card(s) novo(s)** traz ids do catálogo (`GET /api/cartoes/catalogo`), entre crases. A primeira coluna traz o id **do inventário legado**, sem crases. Atenção: alguns ids coincidem de propósito (a mesma pergunta continua com o mesmo id), mas outros foram reaproveitados para um card **diferente** (o `AU-01` e o `SI-01` do catálogo são cards novos; o `AU-01` e o `SI-01` do legado estão em `DC-01` e `SY-nn`).
- `EquivalenciaDocTest` confere sozinho: todo id do inventário tem linha, todo id de card entre crases existe no catálogo, todo card do catálogo é citado, e toda prova `Classe.metodo` existe em `src/test`.

## Como ler a situação

| Situação | Significado |
|---|---|
| equivalente | A mesma conta do legado (fórmula conferida no código do legado ou por `CompatLegado*Test`), nas mesmas opções. As diferenças gerais da próxima seção valem mesmo assim. |
| diferença esperada | O card existe e responde a mesma pergunta, mas com uma diferença **decidida** e documentada (motivo no documento da última coluna). |
| não migrado | Sem card equivalente; o motivo está na última coluna. |

## Diferenças que valem para todos os cards

Valem para qualquer linha abaixo, inclusive as "equivalente" (detalhes e provas em `diferencas-esperadas-trabalho-e-prazos.md` e `diferencas-esperadas-tempo-em-status.md`):

1. **Fuso:** todo "dia" é o de America/Manaus (o legado usava UTC; em Visão geral e Fechadas, UTC, e em Metas, fuso local).
2. **Dias úteis por padrão**, com a opção "dias corridos" (`unit=corrido`), que reproduz o legado.
3. **Concluída** é categoria `done` **e** `resolution`; "done" sem `resolution` vai para a Qualidade dos dados e sai dos números. **Cancelada** não conta como entrega. O Epic não entra nas telas de trabalho.
4. **Atraso justificado:** "no prazo" e "no prazo ajustado" lado a lado (`labels_atraso.yaml`).
5. **Escopo por papel:** usuário vê o próprio; Squad Leader, o seu squad; coordenador e gerente, todos. Agregados só sobre o visível.
6. **Sem ranking de pessoas:** tabelas de pessoas em ordem alfabética, sem posição.
7. **Toda consulta** (200, 400, 403) vai para o `access_log`, sem chave de issue.
8. **Sem worklog até o Plano 6b:** horas registradas vêm do campo da issue ou de "—", nunca de zero inventado.

## Tabela de equivalência

Documentos: [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md) · [tempo em status](diferencas-esperadas-tempo-em-status.md) · [pessoas e planejamento](diferencas-esperadas-pessoas-e-planejamento.md) · [checklist, integridade e governança](diferencas-esperadas-checklist-integridade-governanca.md) · [auditoria](diferencas-esperadas-auditoria.md) · [complementos](diferencas-esperadas-complementos.md).

### Visão geral e Meu painel

| ID | Situação | Card(s) novo(s) | Prova | Documento |
|---|---|---|---|---|
| VG-01 | equivalente | `VG-01` | `CentralRoutesTest.itensBatemComAsTelasDeDestino` | [checklist, integridade e governança](diferencas-esperadas-checklist-integridade-governanca.md), Início |
| VG-02 | diferença esperada | `VG-02`, `FE-01` | `CentralCalculatorTest.centralTemConcluidas30dComVariacao` | [complementos](diferencas-esperadas-complementos.md), M3: variação relativa, não em pontos percentuais |
| VG-03 | diferença esperada | `VG-03`, `FE-03` | `CentralCalculatorTest.centralTemCiclo30d`, `CentralRoutesTest.concluidas30dECicloBatemComFechadasEComAGaveta` | [complementos](diferencas-esperadas-complementos.md), M3: ciclo em dias úteis por padrão |
| VG-04 | diferença esperada | `VG-04`, `MT-01` | `CentralCalculatorTest.kpisDePrazoLadoALadoContraAMeta` | [checklist, integridade e governança](diferencas-esperadas-checklist-integridade-governanca.md), Início: janela de 90 dias e "no prazo ajustado" |
| VG-05 | equivalente | `VG-05`, `NG-03` | `CentralRoutesTest.itensBatemComAsTelasDeDestino` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md), NG-03 |
| VG-06 | diferença esperada | `VG-09`, `NG-01` | `CentralCalculatorTest.semAtualizacaoEParadasSaoDoisItensQueNaoSeSomam` | [checklist, integridade e governança](diferencas-esperadas-checklist-integridade-governanca.md): deixou de ser KPI próprio, virou item da Central |
| VG-07 | diferença esperada | `VG-09`, `NG-04` | `CentralRoutesTest.itensBatemComAsTelasDeDestino` | [checklist, integridade e governança](diferencas-esperadas-checklist-integridade-governanca.md): item da Central, com a flag de impedimento |
| VG-08 | equivalente | `VG-08`, `RI-01` | `CentralRoutesTest.itensBatemComAsTelasDeDestino`, `CompatLegadoTrabalhoTest.riscoComPesosDoLegado` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md), RI-01 |
| VG-09 | diferença esperada | `VG-09` | `CentralRoutesTest.semAtualizacaoEParadasSeparadas`, `CentralCalculatorTest.itemQueOPapelNaoVeNaoExisteNoDto` | [checklist, integridade e governança](diferencas-esperadas-checklist-integridade-governanca.md): sem dupla contagem (achado 2), cada número chama o card de destino |
| VG-10 | diferença esperada | `VG-10`, `FE-07` | `CentralCalculatorTest.entregasPorSemanaEmOrdemDeData` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md), FE-07: semana de Manaus |
| VG-11 | diferença esperada | `VG-11`, `PV-02` | `CompatLegadoTrabalhoTest.monteCarloSemChegadaComoOLegado` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md), PV-02: o novo acrescenta a taxa de chegada |
| VG-12 | diferença esperada | `VG-12` | `CentralCalculatorTest.squadsEmOrdemAlfabeticaSemNotaDePessoa`, `CentralRoutesTest.cartaoPorSquadAlfabeticoSemNotaDePessoa` | [checklist, integridade e governança](diferencas-esperadas-checklist-integridade-governanca.md), Início: ocupação média e Jain, sem nota de pessoa |
| MP-01 | equivalente | `MP-01` | `SquadEMeuPainelRoutesTest.numerosIguaisAosCards` | [checklist, integridade e governança](diferencas-esperadas-checklist-integridade-governanca.md), Meu painel |
| MP-02 | diferença esperada | `MP-02`, `CP-03` | `SquadEMeuPainelRoutesTest.meuPainelDaAna` | [pessoas e planejamento](diferencas-esperadas-pessoas-e-planejamento.md): "sem capacidade" no lugar de 990% |
| MP-03 | equivalente | `MP-03`, `RI-01` | `SquadEMeuPainelRoutesTest.numerosIguaisAosCards` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md), RI-01 |
| MP-04 | diferença esperada | `MP-04`, `CA-07` | `SquadEMeuPainelRoutesTest.ausenciasDoSquadSemMotivoNemOutroSquad` | [checklist, integridade e governança](diferencas-esperadas-checklist-integridade-governanca.md): ausências sem motivo (LGPD) |
| MP-05 | diferença esperada | `MP-05` | `SquadEMeuPainelRoutesTest.meuPainelIgnoraParametroPessoaDoUsuario`, `SquadEMeuPainelRoutesTest.meuPainelTemNoPrazoENoPrazoAjustadoLadoALado` | [checklist, integridade e governança](diferencas-esperadas-checklist-integridade-governanca.md): sempre o painel do próprio usuário |

### Abertas, Épicos, Negligenciadas, Riscos e Previsão

| ID | Situação | Card(s) novo(s) | Prova | Documento |
|---|---|---|---|---|
| AB-01 | equivalente | `AB-01` | `AbertasNegligenciadasRoutesTest.drillTemOMesmoTamanhoDoCardAbertas` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md), Abertas |
| AB-02, AB-03 | diferença esperada | `AB-02` | `AbertasNegligenciadasRoutesTest.historicoDoUsuarioSoComAsProprias`, `CompatLegadoTrabalhoTest.cicloCorridoComoOLegado` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md): P50 e P85 por tipo, num card só |
| AB-04 | diferença esperada | `AB-04` | `AbertasNegligenciadasRoutesTest.filtrosDeTipoPrioridadeEUnidadeAlteramOCard` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md): acima do P85 do próprio tipo |
| AB-05 | equivalente | `AB-05` | `AbertasNegligenciadasRoutesTest.statusNaoMapeado200` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md), Abertas |
| AB-06 | diferença esperada | `AB-06` | `AbertasNegligenciadasRoutesTest.baseSemCamposDeTrabalhoMostraAvisoNaoZero` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md): vazio com aviso enquanto não há prioridade sincronizada |
| AB-07 | equivalente | `AB-07` | `CompatLegadoTrabalhoTest.faixasDeIdadeComoOLegado` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md): igual em dias corridos |
| AB-08 | equivalente | `AB-08` | `AbertasNegligenciadasRoutesTest.usuarioVeSoAsProprias` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md), Abertas |
| AB-09 | diferença esperada | `AB-09` | `AbertasNegligenciadasRoutesTest.drillTemOMesmoTamanhoDoCardAbertas` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md): responsável só dentro da visão |
| EP-01 | diferença esperada | `EP-01` | `PrevisaoEpicosRoutesTest.comEpico`, `PrevisaoEpicosRoutesTest.epicosDrillMesmoTamanhoEVisao` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md): sem as canceladas, previsão P85 |
| NG-01, NG-05 | diferença esperada | `NG-01`, `NG-05` | `AbertasNegligenciadasRoutesTest.filtroSemMovimentoRealNaoSubstituiASemAtualizacao` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md): duas listas separadas, paradas em dias úteis |
| NG-02 | diferença esperada | `NG-02` | `AbertasNegligenciadasRoutesTest.semResponsavelSoCoordenacao` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md): fila sem dono só para coordenação e gerente |
| NG-03 | equivalente | `NG-03` | `AbertasNegligenciadasRoutesTest.drillTemOMesmoTamanhoDoCardNegligenciadas` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md), Negligenciadas |
| NG-04 | diferença esperada | `NG-04` | `AbertasNegligenciadasRoutesTest.drillTemOMesmoTamanhoDoCardNegligenciadas` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md): inclui a flag de impedimento |
| NG-06 | equivalente | `NG-06` | `AbertasNegligenciadasRoutesTest.drillTemOMesmoTamanhoDoCardNegligenciadas` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md), Negligenciadas |
| RI-01 | equivalente | `RI-01` | `CompatLegadoTrabalhoTest.riscoComPesosDoLegado`, `RiscosRoutesTest.contagens` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md), Riscos |
| RI-02 | diferença esperada | `RI-02` | `RiscosRoutesTest.drillNivel` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md): alto e médio, nível baixo só no drill |
| RI-03 | diferença esperada | `RI-03` | `RiscosRoutesTest.sinaisPorPessoaAlfabeticosSemRanking` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md): proporção, ordem alfabética |
| PV-01 | equivalente | `PV-01` | `PrevisaoEpicosRoutesTest.drillBacklogMesmoTamanho` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md), Previsão |
| PV-02, PV-03, PV-04 | diferença esperada | `PV-02`, `PV-03`, `PV-04` | `CompatLegadoTrabalhoTest.monteCarloSemChegadaComoOLegado`, `PrevisaoEpicosRoutesTest.mesmoNumeroNoMesmoDiaComOsMesmosFiltros` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md): taxa de chegada, confiança por semanas com dado, semana de Manaus |
| PV-05 | equivalente | `PV-05` | `PrevisaoEpicosRoutesTest.previsaoDoUsuarioSoComAsProprias` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md), Previsão |
| PV-06 | diferença esperada | `PV-06` | `CompatLegadoTrabalhoTest.restanteDadaIdadeComoOLegado` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md): só tasks em andamento |

### Fechadas, Negativas e Tempo em status

| ID | Situação | Card(s) novo(s) | Prova | Documento |
|---|---|---|---|---|
| FE-01, FE-02 | diferença esperada | `FE-01`, `FE-02` | `FechadasRoutesTest.periodoPadraoNoventaDias`, `FechadasRoutesTest.drillTemOMesmoTamanhoDoCard` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md), Fechadas: precisa de `resolution` positiva, pontos sem dado viram traço |
| FE-03 | diferença esperada | `FE-03` | `CompatLegadoTrabalhoTest.cicloCorridoComoOLegado` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md): ciclo em dias úteis por padrão, igual ao legado em dias corridos |
| FE-04 | equivalente | `FE-04` | `FechadasRoutesTest.periodoPadraoNoventaDias` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md), Fechadas |
| FE-05 | diferença esperada | `FE-05` | `FechadasRoutesTest.statusNaoMapeadoEmCandidataPorVencimentoViraAvisoBloqueante` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md): "no prazo" e ajustado lado a lado, regra única |
| FE-06 | diferença esperada | `FE-06` | `FechadasRoutesTest.tg801` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md): inclui as fechadas sem `resolution` |
| FE-07 | diferença esperada | `FE-07` | `FechadasRoutesTest.resolucaoNaFronteiraDeManausCaiNoMesmoDia` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md): semana de Manaus |
| FE-08, FE-09, FE-10 | equivalente | `FE-08`, `FE-09`, `FE-10` | `FechadasRoutesTest.drillTemOMesmoTamanhoDoCard` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md), Fechadas |
| FE-11 | diferença esperada | `FE-11` | `FechadasRoutesTest.tempoAtivoPorPessoa`, `FechadasRoutesTest.tempoAtivoSoContaOTrechoDaPessoa` | [complementos](diferencas-esperadas-complementos.md), M4: responsável no instante do evento, ordem alfabética |
| FE-12 | diferença esperada | `FE-12` | `FechadasRoutesTest.tabelaDeConcluidasComRealSobreEstimado` | [complementos](diferencas-esperadas-complementos.md), M4 |
| NE-01, NE-02 | diferença esperada | `NE-01`, `NE-02` | `NegativasRoutesTest.periodoDeSetembroContaAReaberturaAntiga` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md), Negativas: pela data do evento, não pela última atualização (achado 3) |
| NE-03 | equivalente | `NE-03` | `NegativasRoutesTest.drillTemOMesmoTamanhoDoCard` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md), Negativas |
| NE-04 | diferença esperada | `NE-04` | `NegativasRoutesTest.drillTemOMesmoTamanhoDoCard` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md): com e sem justificativa |
| NE-05 | equivalente | `NE-05` | `NegativasRoutesTest.usuarioVeSoAsProprias` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md), Negativas |
| NE-06, NE-07, NE-08 | diferença esperada | `NE-06`, `NE-07`, `NE-08` | `NegativasRoutesTest.squadLeaderVeSoOSeuSquad` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md): sem worklog até o 6b, trocas dentro do período, taxas com marca ▲ |
| NE-09 | diferença esperada | `NE-09` | `NegativasCalculatorTest.maiorTempoPorStatusNasNegativas` | [complementos](diferencas-esperadas-complementos.md), M4 (a nota "adiado" de [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md) foi resolvida pelo Plano 10) |
| NE-10 | diferença esperada | `NE-10` | `NegativasRoutesTest.filtrosInvalidosEPapelSemPermissao` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md): por responsável no instante do evento, em ordem alfabética |
| TS-01 | diferença esperada | `TS-01` | `TempoEmStatusRoutesTest.concluidaSemResolutionViraAvisoDivergente` | [tempo em status](diferencas-esperadas-tempo-em-status.md): concluída exige `resolution` |
| TS-02 | equivalente | `TS-02` | `CompatLegadoTest.modoCorridoReproduzAFormulaDoAppAtual` | [tempo em status](diferencas-esperadas-tempo-em-status.md): 3/7 em dias corridos |
| TS-03, TS-07 | equivalente | `TS-03`, `TS-07` | `CompatLegadoTest.modoCorridoReproduzAFormulaDoAppAtual`, `TempoEmStatusRoutesTest.drillTemOMesmoTamanhoDoCard` | [tempo em status](diferencas-esperadas-tempo-em-status.md): max(2 d, 1,5 × mediana) |
| TS-04, TS-05 | equivalente | `TS-04`, `TS-05` | `CompatLegadoTest.modoCorridoReproduzAFormulaDoAppAtual`, `TempoEmStatusRoutesTest.cardIgnoraTipoEStatusForaDaFicha` | [tempo em status](diferencas-esperadas-tempo-em-status.md): mediana e P85, reentradas somadas |
| TS-06 | diferença esperada | `TS-06` | `TempoEmStatusRoutesTest.taskQueMudouDeSquadNaoExpoeOColegaDoOutroSquad` | [tempo em status](diferencas-esperadas-tempo-em-status.md): tempo repartido por responsável vigente (posse) |

### Metas e prazos

| ID | Situação | Card(s) novo(s) | Prova | Documento |
|---|---|---|---|---|
| MT-01 | diferença esperada | `MT-01` | `MetasRoutesTest.versaoDasRegrasNaResposta` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md), Metas: meta em `metas.yaml`, regra única |
| MT-02, MT-03, MT-04, MT-05, MT-06, MT-07 | equivalente | `MT-02`, `MT-03`, `MT-04`, `MT-05`, `MT-06`, `MT-07` | `CompatLegadoTrabalhoTest.metasDeltaComoOLegado` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md), Metas |
| MT-08, MT-09 | diferença esperada | `MT-08`, `MT-09` | `MetasRoutesTest.periodoPadraoEPeriodoPeloVencimento` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md): séries pelo vencimento, meta e ajustado ao lado |
| MT-10 | equivalente | `MT-10` | `CompatLegadoTrabalhoTest.metasDeltaComoOLegado` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md), Metas |
| MT-11, MT-12, MT-13 | diferença esperada | `MT-11`, `MT-12`, `MT-13` | `MetasRoutesTest.drillTemOMesmoTamanhoDoCard` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md): novo na forma (pílulas com símbolo e texto) |
| MT-14 | diferença esperada | `MT-14` | `MetasRoutesTest.usuarioVeSoAsProprias` | [trabalho e prazos](diferencas-esperadas-trabalho-e-prazos.md): ordem alfabética, sem posição |

### Checklist × Review e Integridade

| ID | Situação | Card(s) novo(s) | Prova | Documento |
|---|---|---|---|---|
| CR-01, CR-02, CR-03, CR-04, CR-05 | diferença esperada | `CR-01`, `CR-02`, `CR-03`, `CR-04`, `CR-05` | `ParesChecklistReviewTest.parPeloLinkBlocksNasDuasDirecoes`, `EstatisticasChecklistReviewTest.cicloReviewP50ERazaoEAtrasoDeCriacao` | [checklist, integridade e governança](diferencas-esperadas-checklist-integridade-governanca.md): o par vem do vínculo Blocks, não do `parent` |
| CR-06, CR-07, CR-08 | diferença esperada | `CR-06`, `CR-07`, `CR-08` | `CompatLegadoChecklistTest.alertasDoParComoOLegado`, `EstatisticasChecklistReviewTest.percentuaisDoCenario` | [checklist, integridade e governança](diferencas-esperadas-checklist-integridade-governanca.md): as seis regras de alerta são as do legado; o universo de pares muda |
| CR-09 | diferença esperada | `CR-01` | `ChecklistReviewRoutesTest.usuarioVeParDoQualParticipa` | [checklist, integridade e governança](diferencas-esperadas-checklist-integridade-governanca.md): a tabela de pares é a lista do card CR-01, só com os pares da visão |
| CR-10 | diferença esperada | `CR-10` | `EstatisticasChecklistReviewTest.aprendizadoSoComAmostra` | [checklist, integridade e governança](diferencas-esperadas-checklist-integridade-governanca.md): frases fixas, só com amostra |
| CR-11 | diferença esperada | `CR-11` | `EstatisticasChecklistReviewTest.faixasSeguemOsCortesDoYaml` | [checklist, integridade e governança](diferencas-esperadas-checklist-integridade-governanca.md): mesmos cortes, agora em YAML |
| CR-12 | diferença esperada | `CR-12` | `EstatisticasChecklistReviewTest.atrasosEmCadeiaSemPessoas` | [checklist, integridade e governança](diferencas-esperadas-checklist-integridade-governanca.md): atrasos em cadeia, sem pares de pessoas |
| CR-13, CR-14 | diferença esperada | `CR-13`, `CR-14` | `EstatisticasChecklistReviewTest.liftComAmostraPequena`, `EstatisticasChecklistReviewTest.diferencasDeLabels` | [checklist, integridade e governança](diferencas-esperadas-checklist-integridade-governanca.md): "n baixo" abaixo de 5 |
| IN-01, IN-03, IN-05, IN-07 | diferença esperada | `IN-01`, `IN-03`, `IN-05`, `IN-07` | `IntegridadeRoutesTest.numerosDoCenario`, `NiveisPorPessoaTest.totaisPorSinalEIssuesAfetadas` | [checklist, integridade e governança](diferencas-esperadas-checklist-integridade-governanca.md): pessoa do sinal é o autor do evento, desvios M1 a M6, só gerente |
| IN-02, IN-04 | diferença esperada | `IN-02`, `IN-04` | `CompatLegadoChecklistTest.niveisComoOLegado`, `NiveisPorPessoaTest.pessoaSemConcluidasTemTaxaMasNaoEntraNaMediana` | [checklist, integridade e governança](diferencas-esperadas-checklist-integridade-governanca.md): mesmos limiares, agora no YAML; mediana só de quem tem concluídas |
| IN-06 | diferença esperada | `IN-06` | `NiveisPorPessoaTest.impactoNasMetas` | [checklist, integridade e governança](diferencas-esperadas-checklist-integridade-governanca.md): usa a regra única de "no prazo" |
| IN-08 | diferença esperada | `IN-08` | `IntegridadeRoutesTest.catalogoSemPessoas` | [checklist, integridade e governança](diferencas-esperadas-checklist-integridade-governanca.md): a ajuda é montada dos parâmetros do YAML (achado 12) |

### Pessoas, capacidade e fluxo

| ID | Situação | Card(s) novo(s) | Prova | Documento |
|---|---|---|---|---|
| CP-01, CA-04 | diferença esperada | `CP-01`, `CA-04` | `CompatLegadoCapacidadeTest.jainComoOLegado` | [pessoas e planejamento](diferencas-esperadas-pessoas-e-planejamento.md): a conta de Jain é a do legado, com teto por valor e sem valor com menos de 2 pessoas |
| CP-02 | diferença esperada | `CP-02` | `CargaCapacidadeRoutesTest.contagensNoLugarDeExtremos` | [pessoas e planejamento](diferencas-esperadas-pessoas-e-planejamento.md): contagens no lugar de "maior e menor ocupação" (sem ranking) |
| CP-03 | diferença esperada | `CP-03` | `CargaCapacidadeRoutesTest.linhasAlfabeticasSemPosicao`, `CompatLegadoCapacidadeTest.capacidadeDoDiaComoOLegado` | [pessoas e planejamento](diferencas-esperadas-pessoas-e-planejamento.md): alfabética, "sem capacidade" no lugar de 990% |
| CA-01 | diferença esperada | `CA-01` | `CargaCapacidadeRoutesTest.capacidadeSoLideranca` | [pessoas e planejamento](diferencas-esperadas-pessoas-e-planejamento.md): só liderança |
| CA-02, CA-03, CA-09 | diferença esperada | `CA-02`, `CA-03`, `CA-09` | `CargaCapacidadeRoutesTest.alertaAusenciaComTrabalhoEAtrasoPorAusencia`, `CompatLegadoCapacidadeTest.horasMinimasComoOLegado` | [pessoas e planejamento](diferencas-esperadas-pessoas-e-planejamento.md): previsão dia útil a dia útil, não por semana inteira |
| CA-05 | diferença esperada | `CA-05` | `CargaCapacidadeRoutesTest.mapaMostraAusente` | [pessoas e planejamento](diferencas-esperadas-pessoas-e-planejamento.md): "aus." em texto, nunca porcentagem |
| CA-06 | diferença esperada | `CA-06` | `CargaCapacidadeRoutesTest.alertaAusenciaComTrabalhoEAtrasoPorAusencia` | [pessoas e planejamento](diferencas-esperadas-pessoas-e-planejamento.md): com as horas a replanejar |
| CA-07 | diferença esperada | `CA-07` | `CargaCapacidadeRoutesTest.ausenciasSemMotivoNaResposta` | [pessoas e planejamento](diferencas-esperadas-pessoas-e-planejamento.md): YAML somente leitura, sem motivo |
| CA-08 | diferença esperada | `CA-08` | `CalendarioPlanoVencimentosRoutesTest.calendarioSemana` | [pessoas e planejamento](diferencas-esperadas-pessoas-e-planejamento.md): feriados com o nome do `calendario.yaml` |
| GA-01, GA-02 | diferença esperada | `GA-01`, `GA-02` | `GargalosTendenciaRoutesTest.gargalosDoCap1` | [pessoas e planejamento](diferencas-esperadas-pessoas-e-planejamento.md): 60 dias do changelog, gargalo só em etapa ativa ou de espera |
| GA-03, GA-04 | diferença esperada | `GA-03`, `GA-04` | `GargalosTendenciaRoutesTest.gargalosESoLideranca` | [pessoas e planejamento](diferencas-esperadas-pessoas-e-planejamento.md): contagens, sem ranking |

### Ação, governança e sincronização

| ID | Situação | Card(s) novo(s) | Prova | Documento |
|---|---|---|---|---|
| DI-01, DI-02 | equivalente | `DI-01`, `DI-02` | `DistribuidorTest.pendentesSemResponsavelOuComGerente`, `DistribuicaoRoutesTest.numeroDosCartoesIgualAoDrillParaCadaPapel` | [checklist, integridade e governança](diferencas-esperadas-checklist-integridade-governanca.md), Distribuição |
| DI-03 | diferença esperada | `DI-03` | `DistribuidorTest.jainAntesEDepois`, `CompatLegadoDistribuicaoTest.scoreComoOLegado` | [checklist, integridade e governança](diferencas-esperadas-checklist-integridade-governanca.md): o score não soma as horas do item |
| DI-04 | diferença esperada | `DI-04` | `DistribuidorTest.fatoresEmOrdemAlfabetica` | [checklist, integridade e governança](diferencas-esperadas-checklist-integridade-governanca.md): fatores de todos, em ordem alfabética (sem ranking) |
| DI-05, RB-03 | diferença esperada | `DC-01` | `DistribuicaoRoutesTest.registraUmaVezEDecisaoDuplicada409`, `DistribuicaoRoutesTest.trocadaExigeMotivoEEscolhidoVisivel` | [checklist, integridade e governança](diferencas-esperadas-checklist-integridade-governanca.md): a decisão é gravada só no painel (modo sombra), motivo obrigatório na troca |
| RB-01, RB-02 | diferença esperada | `RB-01`, `RB-02` | `CompatLegadoDistribuicaoTest.rebalanceamentoComoOLegado`, `RebalanceadorTest.histerese` | [checklist, integridade e governança](diferencas-esperadas-checklist-integridade-governanca.md): histerese medida depois do movimento, lista em ordem de chave |
| QD-01, QD-02, QD-03, QD-04, QD-05, QD-06, QD-07 | diferença esperada | `QD-01`, `QD-02`, `QD-03`, `QD-04`, `QD-05` | `QualidadeRoutesTest.valoresDoCenario`, `QualidadeRoutesTest.soCoordenacaoEGerente` | [checklist, integridade e governança](diferencas-esperadas-checklist-integridade-governanca.md), Qualidade: as sete verificações (sem estimativa, sem responsável, sem vencimento, sem épico, em andamento sem horas, sem squad, prioridade padrão; numeração só deste documento, o relatório não numera) estão no `QD-01`; conclusão divergente e vocabulário ganharam `QD-02` a `QD-05` |
| AU-01 | diferença esperada | `DC-01` | `DistribuicaoRoutesTest.decisoesListadasPorData` | [checklist, integridade e governança](diferencas-esperadas-checklist-integridade-governanca.md): o id `AU-01` do catálogo é a Auditoria nova; as decisões são o `DC-01` |
| AU-02 | diferença esperada | `RA-01` | `RegistroDeAcessoRoutesTest.soGerente` | [checklist, integridade e governança](diferencas-esperadas-checklist-integridade-governanca.md): só o gerente, rota sem chave de issue |
| SI-01 | diferença esperada | `SY-01`, `SY-02`, `SY-03` | `SyncStatusRoutesTest.statusMostraDesligadoPelaFlag`, `SyncStatusRoutesTest.statusTrazRetencaoCursorEReconciliacao` | [checklist, integridade e governança](diferencas-esperadas-checklist-integridade-governanca.md): só o gerente, o modo depende da flag |
| SI-02 | não migrado | — | — | Sem card nem tela de "como ligar ao Jira": o passo a passo virou o documento `docs/jira/checklist-primeira-conexao.md`, e só o usuário liga a flag `JIRA_LIVE_ENABLED` (o modo atual aparece no `SY-01`) |

## Resumo

Resumo: 34 equivalentes, 107 diferenças esperadas, 1 não migrado (contados por id do inventário; as linhas agrupadas contam um por id).

Nenhum card do inventário ficou sem destino, exceto o SI-02. As diferenças esperadas são todas decisões registradas nos documentos da última coluna (fuso, dias úteis, regra única de "no prazo", escopo por papel, ausência de ranking de pessoas).

## Cards novos (sem equivalente no legado)

Cards do catálogo que não substituem nenhum id do inventário (spec 6.2 e os planos 4, 5, 6, 7, 10 e 17). Situação de todos: novo.

| Grupo | Cards |
|---|---|
| Auditoria de alterações (o legado só tinha a Integridade) | `AU-01`, `AU-02`, `AU-03`, `DV-01` |
| Trabalho e prazos | `AB-10`, `FE-13` |
| Fluxo e gestão | `FX-01`, `FX-02`, `AW-01`, `AW-02`, `BQ-01`, `BQ-02`, `BQ-03`, `BQ-04`, `BQ-05`, `CT-01`, `CT-02`, `CZ-01` |
| Pessoas e planejamento | `CL-01`, `PL-01`, `VE-01`, `VE-02`, `TD-01`, `PS-01`, `PS-02`, `EF-01`, `EF-02`, `CB-01`, `SI-01`, `CM-01`, `CM-02`, `CM-03`, `RV-01`, `RV-02`, `CG-01`, `CG-02` |
| Início, Meu painel e Configuração | `VG-13`, `MP-06`, `CF-01` |

## Pergunta pendente ao usuário (remoção do `legacy/`)

Este documento é o ponto de parada obrigatório do Plano 8 (Tarefa 3, passo 5; roteiro D9). Sem o "sim" expresso do usuário, nada em `legacy/` é removido ou alterado, a Tarefa 9 não é executada e a pendência segue no `HANDOFF.md`. Quem responde é só o usuário, na conversa.

Resumo para decidir: 34 equivalentes, 107 diferenças esperadas, 1 não migrado (SI-02, "como ligar ao Jira", substituído pelo checklist da primeira conexão). As fórmulas de risco, capacidade, metas, previsão, checklist e distribuição foram conferidas contra o código do legado pelos `CompatLegado*Test`.

Opções:

1. Aprovar e remover o `legacy/` na Tarefa 9 (Recomendada): o código antigo fica na tag `legacy-final` e o repositório deixa de ter duas implementações.
2. Aprovar com ajustes (indicar quais): o executor aplica os ajustes a este documento e pergunta de novo antes da Tarefa 9.
3. Ainda não; manter o `legacy/`: a Tarefa 9 fica de fora do PR e a pendência vai ao `HANDOFF.md`.
