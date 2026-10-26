---
description: Retoma o Painel de Squads de onde a última sessão parou
---

Leia `HANDOFF.md` na raiz do projeto, depois `CLAUDE.md` e, se existir, a memória do projeto. Em seguida:

1. Confira o estado real com `git status` e `git log --oneline -5` e diga em poucas linhas onde estamos, apontando qualquer diferença em relação ao `HANDOFF.md`.
2. Se a sessão não foi aberta com `claude --advisor opus --subagents haiku`, lembre o usuário (regra global).
3. Siga a lista "Próximos passos" do `HANDOFF.md` a partir do primeiro item ainda não feito, respeitando as regras que não podem ser esquecidas (em especial: a flag `JIRA_LIVE_ENABLED` é a única trava do acesso ao Jira e só o usuário a muda; aprovações sempre em forma de pergunta com opções).
4. Não execute o plano de implementação antes de o usuário revisar os planos e escolher o método de execução.
5. Ao terminar a sessão, atualize o `HANDOFF.md` (estado, próximos passos, pendências) e faça um commit.
