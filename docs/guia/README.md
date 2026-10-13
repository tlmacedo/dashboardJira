# Guia de implantação: dashboard de monitoramento de tarefas no Jira Data Center

## Objetivo

Este guia leva você, passo a passo, a montar um dashboard de monitoramento de tarefas de uma equipe no Jira Data Center, para uso operacional diário e análise gerencial. O dashboard cobre:

- resumo da equipe;
- indicadores negligenciados;
- divergência de vencimento entre tarefa pai e subtarefas;
- sincronização de vencimentos;
- cards de tempo dedicado, sem contagem em duplicidade.

A abordagem é um **núcleo nativo** (só recursos do próprio Jira) mais **módulos opcionais** por app, REST ou script, que você implanta apenas se o app ou o acesso estiver disponível no seu ambiente. Os filtros trazem JQL pronto para copiar e colar, com placeholders como `[CHAVE_DO_PROJETO]` e `grupo-da-equipe` para você substituir.

## Ordem de execução

Siga os capítulos nesta ordem. Cada um cita o anterior como pré-requisito.

1. [Capítulo 00: pré-requisitos e checagem de versão](00-pre-requisitos.md): dados do ambiente, matriz nativo × app e checagem de versão.
2. [Capítulo 01: filtros salvos](01-filtros-salvos.md): filtro-base, segmentação por período e catálogo de filtros.
3. [Capítulo 02: resumo da equipe](02-resumo-da-equipe.md): métrica → gadget → filtro → configuração e limites dos gadgets nativos.
4. [Capítulo 03: negligenciados e horas](03-negligenciados-e-horas.md): indicadores negligenciados, horas por pessoa e cards de tempo dedicado.
5. [Capítulo 04: tempo por status](04-tempo-por-status.md): comparação das alternativas, árvore de decisão e configuração.
6. [Capítulo 05: divergência de datas e botão de sincronização](05-datas-e-botao-de-sincronizacao.md): detecção da divergência, regra do botão, permissões, riscos e roteiro de teste em homologação.
7. [Capítulo 06: layout, permissões e validação](06-layout-permissoes-e-validacao.md): layout em 3 colunas, compartilhamento, checklist de validação, testes de histórico de status, desempenho, manutenção e melhorias futuras.
8. [Capítulo 07: histórico de status](07-historico-de-status.md): módulo opcional (depende de REST ou de app) com filtros JQL de histórico, tabela card → fonte, receitas REST, SQL e o script `scripts/tempo_em_status.py`. Faça depois do capítulo 04 e use os testes da seção 5 do capítulo 06.

## O que é núcleo nativo e o que é módulo opcional

**Núcleo nativo** (funciona só com recursos do Jira Data Center; alguns gadgets dependem da versão e levam a marca de confirmação):

- capítulos [00](00-pre-requisitos.md), [01](01-filtros-salvos.md), [02](02-resumo-da-equipe.md) e [03](03-negligenciados-e-horas.md), exceto as partes de horas que usam Timesheet, que é de app;
- capítulo [05](05-datas-e-botao-de-sincronizacao.md), camada 1: regra agendada de sinalização, filtro de divergência e botão de sincronização com Automation (se o Automation estiver disponível na sua versão);
- capítulo [06](06-layout-permissoes-e-validacao.md).

**Módulos opcionais** (só valem a pena se o app correspondente estiver disponível):

- capítulo [04](04-tempo-por-status.md) inteiro: app de Time in Status, eazyBI ou campos personalizados com Automation;
- capítulo [05](05-datas-e-botao-de-sincronizacao.md), seção 2: módulos ScriptRunner e eazyBI para a divergência;
- Timesheet (Tempo, Simple Reports ou Decadis sumUp), usado nos cards de horas do capítulo 03 e do layout: é um app, que precisa estar instalado;
- partes eazyBI e de outros apps citadas nos capítulos 02 e 03 (por exemplo, percentuais fora do gráfico de pizza, cards numéricos reais e cards completos de estimado × realizado);
- capítulo [07](07-historico-de-status.md): os filtros JQL de histórico e o script usam só o Jira (JQL e REST), mas dependem de acesso REST com token e de confirmação na sua versão; tempo em status por app (eazyBI, Jira Charting) exige o app.

A matriz da seção 2 do [capítulo 00](00-pre-requisitos.md) detalha, funcionalidade por funcionalidade, o que é nativo e o que exige app.

## Como ler as marcas

- **[Confirmar na sua versão]**: o recurso pode variar conforme a versão do Jira, a configuração ou o que está habilitado. Confira na sua instância antes de depender dele.
- **[Exige app: X]**: o recurso só existe com o app X instalado e licenciado. O custo é o de uma licença paga no Marketplace; consultar o preço atual para o seu número de usuários.

Detalhes na seção 4 do [capítulo 00](00-pre-requisitos.md).

## O que o guia não faz

- O guia não acessa o seu Jira. Ele não conhece a sua versão, os seus apps, os seus projetos nem os seus dados.
- Todos os testes são feitos por você, com a amostra real do seu ambiente: a conferência dos números (capítulo 06, seção 4) e o roteiro de teste do botão em homologação (capítulo 05, seção 9) só terminam quando você os executa.
- Onde algo depende de versão ou de app, o guia diz isso explicitamente em vez de supor.

## Arquivos de apoio

- `scripts/check_guia.py`: verificador do guia (seção abaixo).
- `scripts/tempo_em_status.py`: calcula tempo em status, passagens, ciclo e reaberturas a partir do changelog do REST (capítulo 07, seção 5); testes em `scripts/tests/test_tempo_em_status.py`.
- `reports/`: relatórios de pesquisa que fundamentam o guia (`Histórico de status e tempos Jira.md` e `Filtros JQL para cards Jira.md`).
- `research_notes/`: notas de apoio dessas pesquisas, uma pasta por relatório.

## Verificação automática do guia

Após qualquer edição nos capítulos, rode na raiz do repositório:

```bash
python3 scripts/check_guia.py --completo
```

O verificador confere os arquivos, a sintaxe do JQL, os nomes dos filtros e as referências cruzadas entre capítulos. O resultado esperado é `9 capítulo(s) verificado(s), 0 erro(s)`. Os testes do próprio verificador rodam com:

```bash
python3 scripts/tests/test_check_guia.py
```
