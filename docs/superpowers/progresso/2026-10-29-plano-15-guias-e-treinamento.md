# Progresso: Plano 15, guias e treinamento (Tarefas 5 a 8)

Plano: `docs/superpowers/plans/2026-10-25-plano-15-identidade-final-ingles-e-entrega.md`
Branch: `docs/guias-e-treinamento-plano-15` (base `origin/main` 588ef87). Roteiro: linha A15 de `docs/superpowers/plans/2026-10-28-roteiro-antes-e-depois-da-flag.md`. Só documentação e testes de documentos; nenhum código de produção mudou.
Fora deste bloco: T1 (ativos, já registrados em outra branch), T9 (sessões de treinamento), T10 (dono da manutenção, checklist de entrega) e T11 (fechamento).

## Checklist

- [x] Tarefa 5: `DESIGN.md` (raiz) derivado do código; `DesignMdTest` (+6 casos; `designMdBateComTokens` compara valor a valor com `tokens.css`).
- [x] Tarefa 6: guia do painel em pt-BR (`docs/manual/guia-do-painel.md`) e em inglês (`docs/manual/panel-guide.md`); `GuiasDocTest`.
- [x] Tarefa 7: perguntas frequentes (`docs/manual/perguntas-frequentes.md` e `docs/manual/faq.md`); `GuiasDocTest`.
- [ ] Tarefa 8: material de treinamento por papel (`docs/treinamento/`); `TreinamentoDocTest`.

## Reconferir após o Plano 11

O Plano 11 (branch `feat/auditoria-completa-lgpd-plano-11`) ainda não foi mesclado. Os documentos descrevem estes pontos como **previstos / em integração**. Depois da mescla, reconferir e atualizar:

1. **Tela "Meus dados"** (`/api/titular/meus-dados`): item novo do menu para todos os papéis. Se entrar no `nav.ts`, o teste `guiaCitaCadaItemDoMenu` obriga uma seção nova nos dois guias (`guia-do-painel.md`, `panel-guide.md`); acrescentar também ao material de todos os papéis em `docs/treinamento/` e à pergunta 5 do FAQ, se fizer sentido.
2. **Trava do DPO**: hoje só a Integridade passa por ela (`TravaDpo`, código `AGUARDANDO_ACEITE_DPO`). Se a Auditoria (AU-01 a AU-03) passar a ser barrada e o aceite virar configuração (`DASHBOARD_DPO_ACEITE`), reescrever a seção "Trava do DPO" dos dois guias, a nota da tela `auditoria` e o material do coordenador e do gerente.
3. **Cartões novos de auditoria e sinais novos de Integridade**: o teste `cadaCartaoDoCatalogoEstaNosGuias` falha até cada cartão novo entrar nas tabelas dos dois guias (a coluna "O que mostra" vem de `pt.json` e `en.json`).
4. **Retenção por categoria** e **texto da tela "Meus dados"** (pedir correção ou eliminação ao DPO): citar nos guias e no FAQ.
5. **Seção "A completar depois do Plano 11"** dos dois guias: remover quando tudo acima estiver feito.
6. **Material do gerente** (Registro de acesso): a consulta à própria tela e ao registro de acesso passa a ser parte do relatório para o DPO.

## Rulings e divergências do plano

- Ruling: o guia em pt-BR (`docs/manual/guia-do-painel.md`, Plano 8, Tarefa 10) **não existia na `main`**. Foi escrito aqui, junto com a versão em inglês, com o mesmo formato que o plano de 8 descreve. O `GuiaDoPainelDocTest` do Plano 8 também não existe; os casos `guiaCitaCadaItemDoMenu` e `guiaCitaCadaFicha` entraram em `GuiasDocTest`.
- Ruling: o `DESIGN.md` cita os componentes reais (`Cartao`, `GavetaDeTasks`, `TabelaVirtual`, `EstadoDaTela`, `CardPage`, `Filtros`, `AjudaDoCartao`, `Marca`, gráficos), não `KpiCard` nem `DrillDrawer` (não existem).
- Ruling: os tokens `--accent` e `--s1` a `--s4` existem, mas nenhum componente os usa; o `DESIGN.md` os marca como reservados.
- Ruling: as capturas de tela do treinamento (T8, passo 3) **não foram geradas**: este ambiente não tem o painel aberto com navegador. O `manifesto.yaml` fica com a lista vazia e o teste `capturasSoDeDemonstracao` vale quando houver imagens. Para gerar: subir o painel com os dados de demonstração e capturar a 1440 px, tema Claro, pt-BR.
- Ruling: o "registro de divergências" do Plano 14 ainda não existe; o FAQ diz isso e manda reportar ao coordenador ou ao dono da manutenção.

## Minor adiados

- Capturas de tela do treinamento (acima).
- `docs/treinamento/registro.md` (T9) e `docs/entrega/` (T10) ficam para depois do ponto de parada com o usuário.
