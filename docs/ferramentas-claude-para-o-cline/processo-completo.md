# O processo completo, como o Claude Code o executou (para o ClineSR repetir)

Este documento descreve o ciclo exato que produziu os Planos 1 a 4A: implementar uma tarefa, revisá-la com **olhos independentes**, corrigir, repetir, revisar o conjunto, publicar e voltar à `main`. O ClineSR deve fazer **o mesmo**. Os fluxos em `.clinerules/workflows/` são o atalho executável; aqui está o porquê e os textos prontos.

Precedência em caso de conflito: `CLAUDE.md` > `.clinerules/` > `docs/guia-execucao-outra-ia.md` > este documento > skills de terceiros.

## 0. Papéis

No Claude Code havia um orquestrador e subagentes. No Cline (sem subagentes confiáveis) os papéis são **contextos separados**:

| Papel | O que faz | Como no Cline |
|---|---|---|
| Orquestrador | Lê o plano, mantém o progresso, decide, pergunta ao usuário | A tarefa principal |
| Implementador | Faz UMA tarefa com TDD e commita | Uma **nova tarefa** (`new_task`) com o texto da seção 3, ou a principal, tarefa a tarefa |
| Revisor | Lê o diff da tarefa e julga; **não edita código** | Uma **nova tarefa** (contexto limpo) com o texto da seção 4 |
| Revisor final | Lê o conjunto da branch antes do PR | Nova tarefa, com o texto da seção 6 |

**Por que contexto limpo:** quem escreveu o código tende a achar que ele está certo. O revisor separado achou, nesta obra, bugs reais que os testes não pegavam: injeção de fórmula no CSV, dados pessoais em coluna de banco, execução viva fechada por engano por outra réplica, DoS na contagem de dias úteis, contornos nos hooks. Se não for possível abrir uma nova tarefa, faça a revisão como se fosse outra pessoa: releia só o diff e o brief, sem o histórico, e use a lista de `/revisar-tarefa.md`.

## 1. Preparação do plano

1. `git checkout main`, `git pull`; `git branch -r --no-merged origin/main` e `gh pr list`: havendo trabalho fora da `main`, **pare e pergunte**.
2. `git checkout -b feat/<nome-do-plano>`.
3. Crie `docs/superpowers/progresso/<arquivo-do-plano>.md` (primeira linha `# Progresso — plano: <caminho>`). Ele é versionado: é a memória entre sessões e entre máquinas.
4. Extraia o texto de cada tarefa (`### Tarefa N`) do plano para ler uma por vez. Nunca trabalhe com o plano inteiro de memória.

## 2. O ciclo de cada tarefa

```
implementar (TDD) → rodar testes → commit → revisar (contexto limpo)
      ↑                                              │
      └── corrigir Critical/Important ←──────────────┘ (até aprovar)
Minor → anotar no progresso (vão para a "onda final")
```

Regras do ciclo:
- **Rodadas de correção:** até 3 rodadas na mesma tarefa. Na 4ª, pare e pergunte ao usuário. Cada rodada é UM commit novo (não reescreva o commit anterior) e termina com revisão **limitada aos achados corrigidos** (re-revisão focada).
- **Conflito entre um achado e o texto do plano:** o plano é o argumento; a spec manda. Decida, registre `Ruling: <o que> — <por quê> — <custo se errado>` e siga.
- **Minor:** não bloqueia. Vai para a lista "Minor adiados" do arquivo de progresso, com arquivo e cenário. Importante: o que a revisão marcar como obrigatório para uma tarefa futura (ex.: "a Tarefa 6 deve tratar X") vai para a lista de **compromissos**, e a tarefa futura recebe o aviso.
- **Tarefa que mexe em segurança** (controle de acesso, hooks, auditoria, dados pessoais): a revisão é mais rigorosa e adversarial; peça mutação real dos testes.
- **Teste que passa de primeira é suspeito:** quebre o código de propósito, veja o teste falhar, desfaça.
- Mostre sempre os números (testes e falhas). Nada de "deve passar".

## 3. Texto para o implementador (cole numa nova tarefa)

> Você implementa a Tarefa N do plano `<caminho>` na branch `<branch>`. Leia antes: o cabeçalho do plano (Goal, Architecture, Restrições globais, Foco da revisão), a spec da fase, o texto da tarefa (`<arquivo-do-brief>`), o arquivo de progresso e `docs/guia-execucao-outra-ia.md`. Confira os nomes no código real; se divergirem do plano, siga o código e registre.
> Regras duras: nunca acesse o Jira nem a rede; nunca ligue a flag `JIRA_LIVE_ENABLED`. Sem Docker; SQLite real em arquivo temporário nos testes (`testContextComBanco`). Escreva os testes da tarefa primeiro e veja-os falhar pelo motivo certo, implemente, veja passar, rode a suíte completa. `git add` só dos seus arquivos. Um commit em pt-BR (Conventional Commits). Devolva: status, hash, testes (total/falhas), divergências do plano e preocupações.

## 4. Texto para o revisor (cole numa nova tarefa; só leitura)

> Você revisa a Tarefa N (commit `<hash>`, base `<hash-anterior>`). Somente leitura: não edite, não faça commit, não acesse o Jira. Leia o texto da tarefa, o relatório do implementador e `git diff <base>..<hash>`.
> Dê dois vereditos. **A) Conformidade:** assinaturas, valores e testes nomeados do texto da tarefa. **B) Qualidade e segurança:** visibilidade por papel (nada fora da visão), dados pessoais em banco/log/fixture, SQL só parametrizado, segredos fora de mensagens, fuso de Manaus e relógio injetado, sem ranking de pessoas, dado ruim não derruba a tela, e **testes que afirmam de verdade** (faça a mutação mental: o teste falharia se a linha principal fosse removida ou invertida?).
> Classifique cada achado como Critical, Important ou Minor, com arquivo:linha e um cenário concreto (qual papel, qual entrada, o que acontece). Sem Critical nem Important = APROVADO. Devolva só os dois vereditos e os achados, uma linha cada.

## 5. A "onda final" (antes da revisão final)

Quando todas as tarefas do plano estiverem aprovadas:
1. Releia a lista de Minor adiados e de compromissos no arquivo de progresso.
2. Faça uma **onda única de correções**, em commits pequenos por tema, por prioridade: **A** dados e segurança; **B** correção de comportamento; **C** testes fracos e estilo. Não amplie o escopo do plano.
3. Rode a suíte completa **sem cache** (`--rerun-tasks`) das duas camadas.

## 6. Revisão final do conjunto (contexto limpo, o mais capaz possível)

> Você é o revisor final da branch `<branch>` (`git diff main..HEAD`). Somente leitura. Não reabra o que o arquivo de progresso já registra como julgado, aceito ou adiado, a menos que a soma das partes piore. Foque no que só a visão do conjunto enxerga: (1) com a flag do Jira desligada, nenhum caminho faz requisição (varra o código por clientes HTTP fora da fábrica); (2) dados pessoais em cada tabela, log, mensagem de erro, fixture e documento versionado; (3) retenção/remoção destrutiva e migrações em banco já populado; (4) concorrência e ciclo de vida; (5) coerência entre código, documentos, `.env.example`, scripts de subida e runbook; (6) o que contradiz o plano ou a spec; (7) o que bloqueia abrir a PR. Classifique Critical/Important/Minor e diga explicitamente se a branch está PRONTA PARA PUSH/PR/MERGE.

Importantes (Important) da revisão final se corrigem **antes** do push, em commits novos, com **re-revisão focada** nas correções que mudam comportamento. Os Minor restantes vão para o progresso.

## 7. Publicar (fim de plano): resumo; o procedimento completo e vigente é `docs/superpowers/fluxo-de-entrega.md`

Siga `/fim-de-branch.md`. Em resumo, nesta ordem:
1. Suíte completa das duas camadas, **mostrando os números**.
2. **Varredura de dados pessoais** do que vai ser publicado (diff e mensagens de commit): e-mails, usernames reais, hosts internos, tokens, chaves; `git status` sem `.env`, `papeis.yaml`, `equipes.yaml`, `ausencias.yaml`, `squads.yaml`, `Filtros_Status_tool.txt`; `gh repo view --json visibility` precisa dizer `PRIVATE`.
3. Atualize o arquivo de progresso, o `HANDOFF.md` (estado, próximo passo, pendências) e o índice dos planos.
4. `git push -u origin <branch>`; `gh pr create --base main` com corpo em arquivo (`--body-file`): o que traz, segurança, verificação (números), o que **não** foi testado, pendências. Plano incompleto = PR em **rascunho** (`--draft`) com o estado exato.
5. **Aguarde o CI** (`gh pr checks`).
6. **Merge:** por decisão do usuário (27/10/2026), a IA mescla (squash) quando as 7 condições da seção 5 do fluxo de entrega forem verdadeiras; senão pergunta.
7. Depois do merge: `git checkout main`, `git pull`, rode a suíte na `main` para confirmar que compila e passa; apague a branch local com `git branch -d` (o `--delete-branch` do merge já apaga a remota mesclada); **nunca** apague uma branch com código fora da `main`.
8. Nunca termine uma sessão com commit só local nem branch sem PR: publique ou registre o motivo e a próxima ação no `HANDOFF.md`.

## 8. Quando parar e perguntar ao usuário

Sempre em forma de pergunta com 2 a 4 opções, a recomendada primeiro, cada uma com o porquê e a consequência (`/perguntar-ao-usuario.md`). Pare para: ação irreversível; segurança que o plano não resolve; efeito fora do repositório (merge, push em branch compartilhada, apagar branch remota com código fora da `main`, configuração do GitHub); decisão de produto; 4ª rodada de correção; qualquer ideia de acessar o Jira (a resposta padrão é não: só o usuário liga a flag, à mão). **Decisões que o usuário já tomou** estão em `CLAUDE.md`, `HANDOFF.md` e nos rulings dos arquivos de progresso: não pergunte de novo.

## 9. Armadilhas que já custaram tempo

- **Hooks barram a IA** de editar compose, settings e o arquivo de ambiente, e de gravar texto que ligue a flag do Jira (inclusive em documentação e testes): entregue o trecho pronto ao usuário; monte valores de teste por variável; mensagem de commit por arquivo (`git commit -F`), sem citar esses nomes.
- Rodar o Gradle em dois lugares ao mesmo tempo no mesmo diretório corrompe a execução: um de cada vez.
- Gradle travado ou "database is locked": feche qualquer painel/JVM usando `app/data/painel.db` e rode um Gradle por vez.
- Comece cada tarefa relendo o texto dela; não confie na memória da tarefa anterior.
- Não use `git add -A` nem `-f`: o usuário mantém `legacy/.env.example` modificado e fora de commit.
