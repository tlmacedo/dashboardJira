# Progresso: Plano 15, guias e treinamento (Tarefas 5 a 8)

Plano: `docs/superpowers/plans/2026-10-25-plano-15-identidade-final-ingles-e-entrega.md`
Branch: `docs/guias-e-treinamento-plano-15` (base `origin/main` 588ef87). Roteiro: linha A15 de `docs/superpowers/plans/2026-10-28-roteiro-antes-e-depois-da-flag.md`. Só documentação e testes de documentos; nenhum código de produção mudou.
Fora deste bloco: T1 (ativos, já registrados em outra branch), T9 (sessões de treinamento), T10 (dono da manutenção, checklist de entrega) e T11 (fechamento).

## Checklist

- [x] Tarefa 5: `DESIGN.md` (raiz) derivado do código; `DesignMdTest` (+6 casos; `designMdBateComTokens` compara valor a valor com `tokens.css`).
- [x] Tarefa 6: guia do painel em pt-BR (`docs/manual/guia-do-painel.md`) e em inglês (`docs/manual/panel-guide.md`); `GuiasDocTest`.
- [x] Tarefa 7: perguntas frequentes (`docs/manual/perguntas-frequentes.md` e `docs/manual/faq.md`); `GuiasDocTest`.
- [x] Tarefa 8: material de treinamento por papel (`docs/treinamento/{usuario,squad-leader,coordenador,gerente,roteiro-da-sessao}.md`, `img/manifesto.yaml`); `TreinamentoDocTest` (9 casos). Sem capturas de tela (ver Rulings).

## Integração do Plano 11 (concluída em 29/10/2026)

O Plano 11 foi mesclado na `main` (2d213ba) e a `main` foi mesclada nesta branch. Itens da antiga lista "Reconferir após o Plano 11", todos feitos:

1. **Tela "Meus dados"** (`/api/titular/meus-dados`, item `meusDados` do menu, todos os papéis): seção nos dois guias, pergunta 10 do FAQ (pt e en) e a tela `meusDados` nos quatro materiais de treinamento, mais a pergunta 6 do roteiro.
2. **Trava do DPO**: Auditoria (AU-01 a AU-03, DV-01 e VG-14) e Integridade (IN-01 a IN-08 e VG-13) passam por ela; aceite por configuração (`DASHBOARD_DPO_ACEITE` com referência), fechada por padrão; qualquer estado de sincronização de instância do Jira fecha a porta da demonstração; aviso `ITENS_AGUARDANDO_ACEITE_DPO` à coordenação. Seção "Trava do DPO" dos guias, nota das telas `central`, `auditoria` e `integridade` e materiais da coordenação e do gerente atualizados.
3. **Cartão novo e sinais novos**: VG-14 entrou nas tabelas dos guias; `ESTIMATIVA_REDUZIDA` e `WORKLOG_RETROATIVO` (Auditoria) e `ESTIMATIVA_AUMENTADA` (catálogo da Integridade) estão citados; a Integridade por pessoa (pontos, taxa, múltiplo e nível) está descrita no material do gerente.
4. **Retenção por categoria** (`RETENTION_*_MONTHS`, padrão 24 meses) e o texto da tela sobre correção e eliminação: nos guias e no FAQ.
5. A seção "A completar depois do Plano 11" saiu dos guias; o teste que a ligava ao progresso virou `guiasSemPendenciaDoPlano11`, e `estadoDoJiraEDaTravaBateComOCodigo` agora amarra o novo estado do código.
6. O expurgo automático de dados fictícios ao ligar a flag está em outro PR, ainda não mesclado: **não consta dos documentos** (nada é prometido).

## Rulings e divergências do plano

- Ruling: o guia em pt-BR (`docs/manual/guia-do-painel.md`, Plano 8, Tarefa 10) **não existia na `main`**. Foi escrito aqui, junto com a versão em inglês, com o mesmo formato que o plano de 8 descreve. O `GuiaDoPainelDocTest` do Plano 8 também não existe; os casos `guiaCitaCadaItemDoMenu` e `guiaCitaCadaFicha` entraram em `GuiasDocTest`.
- Ruling: o `DESIGN.md` cita os componentes reais (`Cartao`, `GavetaDeTasks`, `TabelaVirtual`, `EstadoDaTela`, `CardPage`, `Filtros`, `AjudaDoCartao`, `Marca`, gráficos), não `KpiCard` nem `DrillDrawer` (não existem).
- Ruling: os tokens `--accent` e `--s1` a `--s4` existem, mas nenhum componente os usa; o `DESIGN.md` os marca como reservados.
- Ruling: as capturas de tela do treinamento (T8, passo 3) **não foram geradas**: este ambiente não tem o painel aberto com navegador. O `manifesto.yaml` fica com a lista vazia e o teste `capturasSoDeDemonstracao` vale quando houver imagens. Para gerar: subir o painel com os dados de demonstração e capturar a 1440 px, tema Claro, pt-BR.
- Ruling: o "registro de divergências" do Plano 14 ainda não existe; o FAQ diz isso e manda reportar ao coordenador ou ao dono da manutenção.

## Correções da revisão do PR #47

- I-1: na demonstração o seletor "Papel (simulação)" só filtra o **menu** (o servidor em modo dev é sempre gerente, com todos os dados; o Meu painel usa o usuário "dev" e aparece zerado). Os cinco materiais, os dois guias e o FAQ foram reescritos para dizer que o escopo de dados por papel só vale em produção, com login; os exercícios do Usuário e do Squad Leader não dependem mais de "meus dados".
- "Meu squad": item só do Squad Leader; a tela aceita `?squad=` na URL (frase sobre coordenação e gerente removida).
- FAQ 5: "outra pessoa" restrito à tela Posse; universo de "concluídas" agora diz "resolução que não é cancelamento" e "fora de Epic".
- Guias: título "Telas do menu" vazio removido (os grupos ficam como H2).
- `GuiasDocTest.estadoDoJiraEDaTravaBateComOCodigo` amarra flag falsa, trava fechada em produção, `TravaDpo.CODIGO`, os cartões atrás da trava (IN-01 a IN-08 e VG-13) e a Auditoria sem trava ao código.

## Minor adiados

- Lacuna: o material de treinamento só existe em pt-BR (guia e FAQ têm as duas línguas). Traduzir fica para depois, se o time pedir.
- Uniformizar "cartão"/"card" nos textos: adiado (as traduções das telas usam os dois termos).

- Capturas de tela do treinamento (acima).
- `docs/treinamento/registro.md` (T9) e `docs/entrega/` (T10) ficam para depois do ponto de parada com o usuário.
