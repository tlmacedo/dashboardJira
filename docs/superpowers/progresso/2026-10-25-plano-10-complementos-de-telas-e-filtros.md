# Progresso do Plano 10: complementos de telas e filtros

Branch `feat/complementos-telas-filtros-plano-10` (parte de `origin/main` com o Plano 7b mesclado). Inclui as ideias I-26 e I-27 e o reuso da `GavetaDeTasks`. Sem acesso ao Jira em nenhum momento.

## Checklist

- [x] T1 campos do dicionário em `issue.campos_dicionario` (migração V6) e preenchimento retroativo do `raw`
- [x] T2 `GET /api/filtros/opcoes` pelo escopo visível
- [x] T3 filtro e agrupamento por campo (`campo.<slug>`, `porCampo`) e 400 com código
- [x] T4 filtros do frontend por opções do backend, campos do dicionário, chips, filtros rápidos (I-27)
- [x] T5 `GET /api/tarefa/{key}` e `TarefaDrawer`
- [x] T6 gaveta com chave clicável, página `/detalhe`, busca por chave (I-26), reuso da `GavetaDeTasks`
- [x] T7 `TabelaVirtual`
- [x] T8 `EstadoDaTela` e estados das 28 telas sobre `CardPage`
- [x] T9 mensagens e avisos por código, datas e números por idioma
- [x] T10 ajustes M3, M4, B4, E5 (VG-02, VG-03, FE-11, FE-12, NE-09, fichas do menu)
- [x] T11 gráficos e paleta categórica acessível
- [x] T12 diferenças esperadas e fechamento

## Rulings (pontos de parada e escolhas; valor recomendado)

- Ruling: migração `V6__campos_do_dicionario.sql` (a V5 já existia na main após o Plano 7b). Colunas `campos_dicionario` (JSON) e `campos_dicionario_em` (marcador de linha completa; nulo = preenchida só do `raw`, com aviso `CAMPOS_DO_DICIONARIO_INCOMPLETOS`). Só preenche se algum campo tem `customId`.
- Ruling: o link para o Jira reusa `cards.contrato.LinkJira.paraIssue` (já existente do 17a), sem criar `tarefa/LinkJira.kt`. URL com credencial embutida resulta em **sem link** (política segura já testada) em vez de "limpar" a URL como o plano sugeria.
- Ruling: ponto de parada VG-02/VG-03 (27/10/2026): opção recomendada, manter na Central, com destino no cartão de origem (FE-01 `concluidas` e FE-03 `ciclo`) e variação relativa em %, não em pontos percentuais.
- Ruling: NE-09 como segmentos `maiorTempo:<status>` e `maiorTempoPorStatus` (reusa `ContagemGrupoDto`); o "por issue" é o motivo da linha na gaveta.
- Ruling: convenção de i18n mantida: avisos em `cardpage.aviso.<COD>` e erros em `erros.<COD>`; todo 400 sem código vira `FILTRO_INVALIDO`; código sem tradução mostra "Aviso do servidor (código X)".
- Ruling: novo estado `filtro-invalido` em `EstadoDaTela` (acréscimo ao desenho).
- Ruling: filtros rápidos (I-27) em `filtros_rapidos.yaml` opcional (ausente = lista vazia; inválido = log e lista vazia); só parâmetros existentes (`squad`, `tipo`, `prioridade`, `os`, `unit`, `campo.<slug>`), nunca pessoa.
- Ruling: busca por chave (I-26) no Shell; só o número usa o único prefixo de chave da visão (`prefixosDeChave`).
- Ruling: E13 sem mudança (a Auditoria já usa os helpers compartilhados); E12 já resolvido na main (teste estático `gavetaUnica.test.ts`); E5 já coberto por `ConsistenciaEntreTelasTest`.
- Ruling: `HistoricoDrawer` mantido (colunas de autorização e aviso), agora com `useModalAcessivel` e `TabelaDeHistorico` compartilhada.
- Ruling: telas com estado próprio (Auditoria, Divergência, Integridade, Sincronização, Mapa de cartões, Tempo em status, Detalhe) ficam fora do teste de estados em tabela.
- Ruling: gráficos próprios existentes (Fluxo, Controle, Tendência, Idade, sparkline) mantidos, pois já têm padrão, forma e tabela; trocados `--s1..3` por `--cat-*`. Novos gráficos usam `Barras`, `Linhas`, `Dispersao`.
- Ruling: a paleta do tema Sidia herda a do Claro (mesma superfície branca).

## Menores adiados

- Filtros rápidos de "situação" (só atrasadas etc.): exigem filtro novo no backend.
- Prop `vazio` genérica do `CardPage` existe mas não foi ligada às telas.
- Auditoria, Integridade e Registro de acesso não migradas para `TabelaVirtual`.
- `ValidadorDeConfig` não valida `filtros_rapidos.yaml`.
- `.campo input` ainda com `--line` (decorativa) em vez de `--line-control`.
- Capturas visuais (1440 e 390 px, tema Claro) impossíveis sem navegador: pendentes.
- Reconciliação completa para preencher os campos novos nas linhas antigas: quando o usuário ligar a flag.

## Desvio de processo

- No merge de `origin/main` usei `git add -A app docs` uma vez (escopo limitado a `app` e `docs`) para concluir a resolução de conflitos.
