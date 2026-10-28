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

## Minor adiados

(nenhum até aqui)

## Tarefas

- Tarefa 2: workflow de CI. `CiWorkflowTest` com 17 testes; falhou antes (arquivo do workflow ausente) e passou depois; mutação conferida (`java-version` trocado derrubou `versoesFixas`). O teste `frontendVerificaCsp` entra na Tarefa 3, junto do passo. O `tasks.test` do Gradle passou a declarar `.github/`, `scripts/ci/`, `docs/operacao/` e `app/.env.example` como entradas, senão o Gradle dava os testes por atualizados ao mudar só o workflow. YAML validado com `yaml.safe_load`. **Não rodou no GitHub** (sem acesso); a prova real é o primeiro PR (Tarefa 12).
- Tarefa 1 (commit edffbf1): varredura de dados pessoais e segredos (`scripts/ci/`). 40 casos em `test-varredura.sh` passando; mutação conferida (linha removida contada e atribuição de segredo ignorada: ambas derrubaram os testes certos); `--arvore` na árvore atual: `VARREDURA OK`.
