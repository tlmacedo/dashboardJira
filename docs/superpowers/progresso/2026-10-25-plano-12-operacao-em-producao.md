# Progresso — plano: docs/superpowers/plans/2026-10-25-plano-12-operacao-em-producao.md

Branch: `feat/operacao-em-producao` (Tarefas 1 a 12 num só bloco). Executor: Claude Code, sem o usuário presente; pontos de parada resolvidos pela opção Recomendada e reversível. Roda em paralelo com outros planos (5b, 6a, 17b): mudanças em arquivos compartilhados foram mantidas pequenas e localizadas.

## Estado herdado (conferido contra o código real)

- Plano 9 está na `main` (CI, `CiWorkflowTest`, cabeçalhos, CSRF). Plano 8 NÃO foi feito: não existem `Sessao.kt`, `OidcSettings` (só o stub `OidcAuthenticator`), `GeradorSintetico`, `ROTAS_PUBLICAS`/`InventarioDeRotasTest` nem `docs/implantacao.md`.
- Migrações na `main` ao começar: `V1__base.sql` e `V2__campos_de_trabalho.sql` (o Plano 5a mesclou a V2).
- `travaDeProcesso` é `AutoCloseable?` privado em `AppContext`; `Agendador` tem só `tarefas`; `rotasPublicas` é `internal val` em `security/AccessControl.kt`.

## Rulings

- Ruling: Plano 8 ausente — o plano 12 declara dependência dele, que não foi feito; faz-se a menor versão local necessária em cada ponto (ver as Rulings de cada tarefa) sem executar o Plano 8 — custo se errado: quem mesclar o Plano 8 depois reconcilia `Sessao.kt` e as rotas públicas.
- Ruling (T1): `/api/health*` e a casca estática não geram linha de log (filtro do `CallLogging`) em vez de saírem em DEBUG — o plugin só aceita um nível — custo se errado: nenhum; basta trocar o filtro.
- Ruling (T1): o modelo da rota vem do evento `RoutingRoot.RoutingCallStarted` (guardado em atributo da chamada), porque a chamada vista pelo `CallLogging` não é a do roteador — custo se errado: log com `rota=desconhecida`, coberto por teste.
- Ruling (T1): o `ARQUIVO` do `logback.xml` usa `<lazy>true</lazy>` e `${java.io.tmpdir}` como pasta quando `PAINEL_LOG_DIR` falta, para o appender existir sem criar arquivo (o `root` só o referencia com `PAINEL_LOG_ARQUIVO_REF=ARQUIVO`) — custo se errado: nenhum.
- Ruling (T1): `valoresSecretos()` devolve também `usuario:token` das instâncias Basic, para a máscara cobrir o base64 do cabeçalho — custo se errado: nenhum.
- Ruling (T2): o arquivo do banco da prontidão vem da URL do pool (`Database.arquivoDe(ds)`), não de um parâmetro novo no `AppContext`/`testContextComBanco` — uma única fonte da verdade (o pool em uso) e nenhuma mudança no construtor compartilhado — custo se errado: nenhum.
- Ruling (T2): a sonda abre o SQLite com `open_mode=1` (somente leitura, parâmetro do `sqlite-jdbc`) em vez de `mode=ro`, e as migrações pendentes também são contadas por um `SQLiteDataSource` próprio (não pelo pool), para a prontidão responder com o pool esgotado — custo se errado: voltar para `mode=ro`.
- Ruling (T2): `module(...)` ganhou o último parâmetro `prontidao: Prontidao?` (padrão `prontidaoPadrao(context)`), só para os testes injetarem relógio/limites — custo se errado: nenhum.
- Ruling (T2): não existe `InventarioDeRotasTest` (é do Plano 8); a nova rota pública entra em `rotasPublicas` e a prova é `respostaSemDetalhesEProntoEhPublico`. Quem mesclar o Plano 8 acrescenta `/api/health/pronto` ao inventário.

## Tarefas

