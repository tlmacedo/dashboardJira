# /perguntar-ao-usuario.md: como levar uma dúvida ao usuário

Regra global do usuário e regra 4 do `CLAUDE.md`: **toda dúvida, pendência, falta de direção ou aprovação vira pergunta com opções.** Nunca texto solto, lista de itens para resolver sozinho ou pergunta em prosa. No Claude Code isso era feito com a ferramenta de perguntas; no Cline, use a ferramenta `ask_followup_question` com a lista de opções (`options`), como na seção "No Cline" abaixo.

## Quando perguntar

- Ação irreversível ou destrutiva (apagar branch, reescrever histórico, apagar `app/data/painel.db` com dados que não sejam de demonstração).
- Segurança que o plano não resolve.
- Efeito fora do repositório: merge, push em branch compartilhada, publicação, configuração do repositório no GitHub.
- Decisão de produto que muda o que é construído.
- Plano tão quebrado que todo caminho é palpite.
- Qualquer necessidade de acessar o Jira (a resposta padrão é "não").
- Código fora da `main` encontrado no início da sessão.
- Qualquer dúvida em que você não tem direção clara.

Escolhas pequenas e reversíveis que o plano deixou abertas não exigem pergunta: decida a mais segura, registre como Ruling no progresso e siga.

## Formato

- **Uma pergunta por assunto.** Várias pendências = várias perguntas separadas.
- **2 a 4 opções.** A que você mais recomenda vem **primeiro**, com "(Recomendada)" no texto.
- **Cada opção tem:** o porquê (por que é boa ou não) e a consequência prática (o que acontece, custo, risco).
- Contexto curto antes das opções: o que você encontrou e por que precisa da decisão.
- Sem jargão desnecessário; em pt-BR.

## Modelo

> **Contexto:** a PR #11 (Plano 4A) está aberta com testes verdes; a `main` não tem o código dela.
>
> **Pergunta:** o que faço com a PR #11?
>
> 1. **Esperar a sua revisão (Recomendada)**: você confere antes de entrar na `main`. Consequência: o próximo plano espera ou parte sem esse código.
> 2. **Mesclar agora com squash**: a `main` fica completa já. Consequência: entra sem revisão humana.
> 3. **Começar o Plano 4 em outra branch**: adianta trabalho. Consequência: risco de conflito na migração `V4`/`V5`.

## No Cline (`ask_followup_question`)

As opções do Cline são botões curtos; o porquê e a consequência de cada uma vão no texto da pergunta, na mesma ordem. Formato usado pelo próprio repositório do Cline (`.clinerules/workflows/pr-review.md` em github.com/cline/cline):

```xml
<ask_followup_question>
<question>A PR #11 (Plano 4A) está aberta com testes verdes. O que faço com ela?
1. Esperar a sua revisão (Recomendada): você confere antes de entrar na main; o próximo plano espera.
2. Mesclar agora com squash: a main fica completa já, mas sem revisão humana.
3. Começar o Plano 4 em outra branch: adianta trabalho, com risco de conflito na migração.</question>
<options>["Esperar a sua revisão (Recomendada)", "Mesclar agora com squash", "Começar o Plano 4 em outra branch"]</options>
</ask_followup_question>
```

## Depois da resposta

Registre a decisão (Ruling no progresso ou no `HANDOFF.md`) e siga. Se a resposta depender de algo fora da sessão (teste em aparelho, decisão de produto), anote a pendência com data no `HANDOFF.md` e traga a pergunta de novo na próxima sessão até ser resolvida.
