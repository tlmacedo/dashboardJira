# Progresso — plano: docs/superpowers/plans/2026-10-25-plano-09-ci-e-endurecimento.md

Branch: `feat/ci-e-endurecimento` (lote 9a = tarefas 1 a 3; os lotes seguintes continuam nela). Executor: Claude Code, sem o usuário presente; as decisões abertas foram tomadas pela opção Recomendada e reversível.

## Ponto de partida (main antes do plano)

- Backend `./gradlew test`: 746 testes, 0 falhas. Frontend `pnpm vitest run`: 48 testes (4 arquivos), 0 falhas; `pnpm lint` sai 0 (só avisos); `pnpm build` ok.

## Rulings

- Ruling: ponto de parada 1 (ordem de execução): Plano 9 antes de continuar o Plano 4 (opção 1, Recomendada). Custo se errado: a Tarefa 4 do Plano 4 espera este PR.
- Ruling: ponto de parada 2 (onde o CI roda): GitHub Actions com runner `ubuntu-latest` (opção 1, Recomendada), só JDK 21 e Node 22/pnpm. Reversível: trocar `runs-on`.
- Ruling: ponto de parada 3 (proteção da `main`): só documentada em `docs/operacao/ci.md`; é ação do usuário no GitHub. A IA não altera configuração do repositório.
- Ruling: actions por tag maior (`@v4`), não por SHA. Resolver o SHA exigiria `gh api` (rede além do git), proibida nesta execução. Custo se errado: tag movida por terceiro. O teste aceita tag maior ou SHA de 40 hexadecimais, então fixar depois não exige mudar o teste.
- Ruling: `VARREDURA_NOMES_ARQUIVO` (variável opcional) permite trocar o caminho de `nomes-proibidos.local.txt`; existe só para o teste `nomeProibidoPorArquivoLocal` não escrever na pasta versionada. O padrão continua sendo `scripts/ci/nomes-proibidos.local.txt`.
- Ruling: categoria `EMAIL` também aceita domínios `.invalid`/`.example` sem constar em `padroes-permitidos.txt` (igual à regra de hosts), e ignora rótulos do Kotlin (`this@Classe.metodo`, `return@x`).
- Ruling: categoria `SEGREDO`, exceções além das do plano, para não acusar os testes e o código existentes: valor com `segredo`/`secreto`, começando com `seu_`, contendo `suaempresa` ou `[` (classe de regex), acesso a membro de código (`os.environ.get`), e valor que termina em `&` (parâmetro de URL). Sem essas exceções a árvore atual teria 17 achados, todos falsos positivos (conferidos um a um: fixtures, código que lê o segredo de outro lugar e placeholders de documentação).
- Ruling: `padroes-permitidos.txt` recebeu só hosts públicos (documentação de fornecedores, registros de pacotes) e marcadores de lugar (`h`, `u`, `j`, `host`, `jira`, `x`, `0.0.0.0`, `10.0.0.1`, `127.0.0.2`, `exemplo.com`, `suaempresa.*`) já presentes no repositório. Nenhum host da empresa nem dado pessoal foi achado na árvore (ponto de parada 4 não disparou).
- Ruling: arquivos de banco (`*.db`, `-wal`, `-shm`, `.lock`) entram em `ARQUIVO_PROIBIDO`; o achado de arquivo proibido sai com linha `0` (`arquivo:0`).

- Ruling (lote 9b, T4): a proteção CSRF é um plugin de aplicação que registra um interceptador na fase `Plugins` (com `finish()`), instalado depois do controle de acesso; assim a auditoria já vê o principal e o handler nunca roda. Consequência aceita: `POST` sem cabeçalho em rota de API inexistente responde 403 `CSRF_AUSENTE` (e não 404); `SpaTest.apiInexistenteDevolve404SemHtml` passou a testar os dois casos (com `comCsrf()` dá 404; sem, 403 JSON, nunca HTML). Chamada mutante sem login (401) também vem antes do CSRF: o controle de acesso responde primeiro.
- Ruling (T4): origem esperada sem `DASHBOARD_ORIGEM_PUBLICA` = `<esquema local>://<cabeçalho Host>` (fallback para o host/porta locais se o `Host` faltar, que é o caso do motor de teste); `:80`/`:443` padrão são removidos na comparação; o painel não confia em `X-Forwarded-*`. Atrás de proxy a variável é obrigatória (documentado no `.env.example`).
- Ruling (T4): `todoMetodoMutanteExigeCsrf` percorre `RoutingRoot` como `RoutingNode` (a interface `Route` não expõe `selector` nem `children` no Ktor 3.3.1) e usa `RoutingNode.parent.toString()` como caminho acumulado.

## Minor adiados

(nenhum até aqui)

## Tarefas

- Tarefa 5 (lote 9b): sessão expirada na tela. `client.test.ts` (+7: `nenhumFetchForaDoCliente`, `respostas401DisparamSessaoExpirada`, `respostas403NaoDisparam`, `voltaSoAceitaCaminhoInterno`, `getJson`, `urlDeLogin`) e `SessaoExpirada.test.tsx` (5: pt, en, `focoNoBotaoEEscNaoFecha`, `devModeNaoMostra`, ausência antes do 401). Falharam antes (funções inexistentes; `nenhumFetchForaDoCliente` achou 2 arquivos com `fetch`). As suítes das telas continuam sem mudar asserções. Divergência de código (sem mudar comportamento): as três telas que testavam `instanceof ApiError` passaram a testar `instanceof ErroDaApi`, pois `getJson` lança `ErroDaApi` (`ApiError` continua existindo, subclasse). `fetchMe`/`fetchMeta` agora lançam `ErroDaApi` em vez de `Error` genérico. Teste de `?volta=` com host externo não cobre o componente (o jsdom proíbe `pushState` para outra origem); a regra fica em `voltaSoAceitaCaminhoInterno`. Números: frontend 74 testes, 0 falhas, lint 0 (só avisos), build ok, `verificar-csp.mjs` ok. Conferência no navegador não feita.

- Tarefa 4 (lote 9b): CSRF e `ErroDto`. `ProtecaoCsrfTest` (16) e 4 casos novos de `Settings` (origem pública); `client.test.ts` (6). Falharam antes pela asserção (rascunho do plugin sem proteger: 12 de 16 falharam; frontend: `ErroDaApi` inexistente). `TestHttp.comCsrf()` criado. Números ao fim da tarefa: backend 792 testes, 0 falhas; frontend 63 testes, 0 falhas, lint 0, build ok.

- Fim do lote 9a (tarefas 1 a 3): backend `--rerun-tasks` 772 testes, 0 falhas; frontend `--frozen-lockfile` 57 testes, 0 falhas, build ok; `test-varredura.sh` 41 casos ok; `test-guard.sh` e `test-PreToolUse.sh` com 0 falhas. Revisão independente em contexto limpo e revisão final ficam para o fechamento do plano.
- Tarefa 3 (commit 583ba0d): CSP e cabeçalhos de segurança. `CabecalhosDeSegurancaTest` (8), `CiWorkflowTest.frontendVerificaCsp` (1), `verificar-csp.test.ts` (9). Falharam antes pelo motivo certo (cabeçalhos ausentes; `SpaTest` passou a esperar `no-store` na API). Mutação: tirar a condição de caminho do `no-store` sobreviveu ao teste original (o servidor estático sempre define o próprio `Cache-Control`), então foi acrescentada a asserção do 404 de arquivo sem cabeçalho; depois a mutação falhou como devia. Plugin por `onCallRespond` (cobre 401, 403, 404 e estáticos); `nosniff` saiu do `StaticSpa.kt`. Números ao fim da tarefa: backend 772 testes, 0 falhas; frontend 57 testes, 0 falhas, lint 0, build ok, `verificar-csp.mjs` ok. **Conferência manual no navegador e `curl` dos cabeçalhos não feitas** (o hook de proteção barrou o comando que subia o painel e chamava `curl`; não contornei). A cobertura vem de `testApplication`.
- Tarefa 2 (commit 59d1f1f): workflow de CI. `CiWorkflowTest` com 17 testes; falhou antes (arquivo do workflow ausente) e passou depois; mutação conferida (`java-version` trocado derrubou `versoesFixas`). O teste `frontendVerificaCsp` entra na Tarefa 3, junto do passo. O `tasks.test` do Gradle passou a declarar `.github/`, `scripts/ci/`, `docs/operacao/` e `app/.env.example` como entradas, senão o Gradle dava os testes por atualizados ao mudar só o workflow. YAML validado com `yaml.safe_load`. **Não rodou no GitHub** (sem acesso); a prova real é o primeiro PR (Tarefa 12).
- Tarefa 1 (commit edffbf1): varredura de dados pessoais e segredos (`scripts/ci/`). 40 casos em `test-varredura.sh` passando; mutação conferida (linha removida contada e atribuição de segredo ignorada: ambas derrubaram os testes certos); `--arvore` na árvore atual: `VARREDURA OK`.
