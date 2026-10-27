# Adendo de plataforma: SQLite embutido, sem Docker (vale para TODOS os planos pendentes)

**Data:** 27/10/2026. **Origem:** decisão do usuário em 26/10: a empresa bloqueia Docker e PostgreSQL. A troca já foi executada e mesclada na `main` (PRs #18 e #19; ver `docs/superpowers/specs/2026-10-27-spec-fase-3b-saida-do-docker-sqlite.md`).

**Precedência:** este adendo vence qualquer trecho de plano ou spec que fale de PostgreSQL, Docker, compose, Testcontainers, imagem, contêiner, Kubernetes ou volume. Ordem completa: `CLAUDE.md` > `.clinerules/` > `docs/guia-execucao-outra-ia.md` > **este adendo** > spec da fase > plano > spec mestra de 24/10. Os planos 4A a 16 foram escritos *antes* da troca; onde o texto diz "PostgreSQL", leia "SQLite" e aplique as regras abaixo. Os planos 8, 9, 12 e 13 (e suas specs) já foram reescritos para a nova realidade; nos demais, só o que está na tabela da seção 7 muda.

## 1. O que existe hoje (não refaça)

| Peça | Como é agora |
|---|---|
| Banco | SQLite em arquivo: `app/data/painel.db` (variável `DATABASE_PATH`), modo WAL, `busy_timeout` 15 s, chaves estrangeiras ligadas, escrita já como IMMEDIATE. Pool Hikari de 4 conexões (`DATABASE_POOL_SIZE`). Código em `db/Database.kt`. |
| Um painel por arquivo | `db/TravaDeProcesso.kt`: trava de SO em `<banco>.lock`. Uma segunda subida no mesmo arquivo é recusada. Não existe mais "outra réplica": só um processo escreve. |
| Migrações | Flyway. **Só existe a `V1__base.sql`** (esquema final consolidado; as antigas V1 a V6 do PostgreSQL deixaram de existir). **A próxima migração é a `V2`**; numere em sequência (`V2`, `V3`, ...) na ordem em que os PRs chegam à `main`, e confira `ls app/backend/src/main/resources/db/migration` antes de criar. A tabela de migrações do índice dos planos foi renumerada. **Nunca edite uma migração já mesclada.** |
| Variáveis | `app/.env` (copiado de `app/.env.example`), lido na subida pelo próprio painel (`config/ArquivoEnv.kt`). **A flag do Jira vale somente no `app/.env`**: variável de terminal ou do sistema com o mesmo nome é ignorada; linha ausente = desligado. |
| Subida | `app/iniciar.ps1` (Windows) e `app/iniciar.sh`. O `./gradlew run` roda a partir de `app/`. |
| Testes de banco | SQLite real em arquivo temporário: `BancoEmArquivo` e `testContextComBanco(settings, banco, ...)` em `src/test/kotlin/br/sidia/painel/TestContextComBanco.kt`. Cada chamada cria um banco novo e migrado. **Nunca use H2, mock de banco nem Testcontainers.** Não há Docker em lugar nenhum. |

## 2. Tradução do SQL (PostgreSQL → SQLite)

| Era (PostgreSQL) | Agora (SQLite) |
|---|---|
| `timestamptz` / `Instant` | `INTEGER` em milissegundos UTC. Grave e leia com `setTimestamp`/`getTimestamp` (o driver converte). Comparações `<`, `>=` funcionam direto com `Timestamp`. Nunca guarde texto de data para instante. |
| `date` (prazo) | `TEXT` ISO `aaaa-mm-dd` (compara e ordena como texto). |
| `text[]`, `ANY(?)`, `unnest` | Lista vira JSON em `TEXT`; consulte com `json_each(?)`. Helpers em `db/SqlSupport.kt`: `emJson()`, `idsEmJson()`, `tuplasEmJson()`, `jsonParaTextos()`. Ex.: `WHERE id IN (SELECT value FROM json_each(?))`. |
| `jsonb`, `->`, `->>`, `?` | `TEXT` com JSON; `json_extract(col, '$.a.b')`, `json_each`, `json_object`. |
| `boolean` | `INTEGER` 0/1 (`CHECK (col IN (0,1))`). |
| `SERIAL`/`BIGSERIAL` | `INTEGER PRIMARY KEY AUTOINCREMENT`. |
| `ON CONFLICT ... DO UPDATE` com `EXCLUDED` | Igual (SQLite ≥ 3.24). `RETURNING` existe: use `executeQuery` no `INSERT`/`UPDATE ... RETURNING`. |
| `GREATEST/LEAST` | `max(a,b)` / `min(a,b)` escalares. |
| `DISTINCT ON`, `FILTER (WHERE ...)`, `FOR UPDATE`, `pg_advisory_lock`, `LATERAL` | Não existem. Use subconsulta correlacionada, `CASE WHEN`, a transação `IMMEDIATE` (já é o padrão) e o travamento em memória por chave que o código já usa (`SqliteSyncStateStore.comTrava`). |
| `now()`, `::tipo`, `ILIKE` | `(CAST(strftime('%s','now') AS INTEGER) * 1000)` (ou, melhor, o `Clock` injetado), `CAST(x AS tipo)`, `LIKE` (já ignora maiúsculas só em ASCII). |
| `lower()`/`upper()` | **Só ASCII**. Para comparar nomes com acento, normalize em Kotlin antes de gravar ou consultar. |
| `ALTER TABLE ... ALTER COLUMN` | Não existe. Para mudar tipo ou restrição: crie a tabela nova, copie, apague a antiga, renomeie (tudo numa migração). `ADD COLUMN` e `DROP COLUMN` funcionam. |
| `information_schema` | `sqlite_master` e `pragma_table_info('tabela')`. |
| Alias em `DELETE`/`UPDATE` | `DELETE FROM tabela AS t WHERE ...` (o `AS` é obrigatório). |
| Erro de unicidade (`SQLState 23505`) | `SQLiteException.resultCode == SQLITE_CONSTRAINT_UNIQUE` (ou `..._PRIMARYKEY`). Veja `violacaoDeUnicidade` em `sync/SyncService.kt`. |
| Concorrência | Um gravador por vez (WAL). Transações curtas; nunca segure transação aberta durante chamada de rede ao Jira. Leituras não bloqueiam. |

Antes de escrever SQL novo, abra `V1__base.sql` e um repositório vizinho (`sync/IssueStore.kt`) e siga o estilo.

## 3. Regras de teste

- Teste de banco: `private val banco = BancoEmArquivo()` no `companion object` da classe, `@AfterAll` chamando `banco.apagar()`, e `testContextComBanco(settings, banco)` por teste (banco novo a cada chamada). Para o repositório direto: `Database.connect(Database.urlDeArquivo(banco.novoArquivo()))` seguido de `Database.migrate(ds)`.
- Teste com datas: use `Timestamp`/`Instant` por parâmetro; se precisar de literal em SQL cru, converta para milissegundos.
- Para simular falha do banco (ex.: violação de unicidade), use `CREATE TRIGGER ... BEGIN <inserção duplicada>; END` e não funções do PostgreSQL.
- Não existe teste de "outra réplica viva". O equivalente é: `fecharOrfas(inicioDoProcesso, ...)` só fecha execuções iniciadas **antes** do início do processo atual.
- A suíte do backend sobe sem nenhum serviço externo: `cd app/backend; .\gradlew.bat test` (Windows) ou `./gradlew test`. Se a suíte exigir algo além de JDK 21, é defeito do plano: pare e pergunte.

## 4. Proibido em qualquer plano

Dockerfile, `docker-compose.yml`, `.dockerignore`, imagens, digests, registro de contêiner, Kubernetes, Helm, `docker compose`, `docker run`, Testcontainers, PostgreSQL, H2, `pg_dump`. Se uma tarefa pedir qualquer um deles, **não execute a parte**: aplique a seção 5 e registre `Ruling:` no arquivo de progresso.

## 5. Equivalentes para o que os planos antigos faziam com contêiner

| Plano antigo previa | Fazer agora |
|---|---|
| Imagem de produção, `HEALTHCHECK`, usuário sem privilégio | Pacote `./gradlew installDist` (+ `pnpm build`) rodando como serviço do SO com conta sem privilégio; verificação de saúde por `GET /api/health` (Planos 12 e 13 reescritos). |
| Volume do banco | A pasta `app/data/` (e, em produção, o caminho de `DATABASE_PATH`). |
| `docker compose logs` | Saída do processo / arquivo de log do serviço. |
| Backup do PostgreSQL | Cópia consistente do SQLite (`VACUUM INTO` ou API de backup online) para pasta fora do repositório; restauração testada (Plano 12). |
| Reiniciar/atualizar contêiner | Parar o serviço, trocar o pacote, subir; reversão = voltar o pacote anterior (o esquema só avança por migração, então toda migração nova deve ser compatível com o pacote anterior ou ter backup antes). |
| CI com Docker | Fluxo do GitHub Actions só com JDK 21 e Node 22/pnpm (Plano 9 reescrito). |
| `ensure-docker.sh` | Não existe mais. |

## 6. Cuidados específicos de PC corporativo (consulte o Plano 0)

Proxy e inspeção TLS da empresa (certificado raiz no cacerts do JDK), espelho Maven/npm interno, política de execução do PowerShell, `git`/`gh` autenticados, porta 8080 só em `127.0.0.1`. Tudo isso é a **Fase 0** (`2026-10-27-plano-00-pc-da-empresa.md`), primeira coisa a executar no PC da empresa.

## 7. O que muda em cada plano pendente

| Plano | Mudança obrigatória |
|---|---|
| 4 (Auditoria, tarefas 4 a 9) | "leitura no PostgreSQL" = leitura no SQLite; testes de banco com `testContextComBanco`. Sem migração prevista. |
| 5 | Migração "V7" vira a próxima livre (`V2`). Colunas de lista em JSON (`TEXT`). |
| 6 | Migração "V8" (worklog por instância) vira a próxima livre. |
| 7 | Migração "V9" (decisão de distribuição) vira a próxima livre. |
| 8 | Reescrito: sem contêiner; pacote + serviço. |
| 9 | Reescrito: CI sem Docker; sem imagem. |
| 10 | Migração "V10" vira a próxima livre; filtros pelo dicionário usam `json_each`. |
| 11 | Migração "V11" condicional vira a próxima livre. |
| 12, 13 | Reescritos: sem contêiner; backup e implantação do pacote. |
| 14, 15 | Sem mudança de plataforma; ler "flag no `app/.env`" onde o texto fala de variável de ambiente. |
| 16 | Futuro; migração = próxima livre; sem mudança de plataforma. |

Os números `V7`…`V13` que ainda aparecerem nos textos antigos são **apenas rótulos**: confirme sempre o próximo número livre no momento da execução.
