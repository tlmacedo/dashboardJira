# Skills do Claude copiadas para o Cline/ClineSR (teste)

Cópia de **45 skills** que o Claude Code tem instaladas, escolhidas porque a licença permite copiar (MIT ou Apache-2.0) e porque não dependem de nada específico do Claude Cowork ou do Android. Inventário completo (inclusive o que **não** foi copiado e por quê): [`../SKILLS_INSTALADAS_CLAUDE.md`](../SKILLS_INSTALADAS_CLAUDE.md).

Isto é um **teste de compatibilidade**: ninguém garantiu que o Cline reconhece ou executa estas skills do mesmo jeito que o Claude Code. As regras do projeto vencem qualquer skill (`CLAUDE.md` > `.clinerules/` > workflows e skills do projeto > skills de terceiros).

## Formato que o Cline exige
Uma pasta por skill, **um nível** abaixo do diretório de skills, com `SKILL.md` dentro e o nome da pasta **igual** ao campo `name:` do `SKILL.md`. Esta pasta já está nesse formato (`skills-para-cline/<skill>/SKILL.md`). Cada cópia tem o arquivo `.origem-copiada-do-claude` (plugin, licença e pasta de origem). As licenças estão em `LICENSES/`: mantenha-as junto.

## Como copiar para o Cline (PC da empresa, Windows 11)
A partir da raiz do clone:
```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\copiar-skills-para-cline.ps1
```
O script copia cada skill para `%USERPROFILE%\.cline\skills\<skill>` (diretório global do Cline), **sem sobrescrever** pastas de mesmo nome que não tenham vindo daqui, e imprime o que fez. Opções: `-Destino <pasta>` (ex.: `.cline\skills`, só para este projeto; **não versione** o resultado) e `-Somente tdd,diagnosing-bugs` (subconjunto, para testar poucas).

Sem o script, é só copiar as pastas à mão: `Copy-Item -Recurse skills-para-cline\tdd $env:USERPROFILE\.cline\skills\tdd`.

## Como verificar amanhã se o Cline reconheceu
1. Reabra o VS Code e abra a aba **Skills** do Cline/ClineSR (se a versão exigir ligar o recurso de skills nas configurações, ligue; isso **não foi confirmado** para a variante da empresa).
2. Conferir que aparecem, por exemplo, `tdd`, `diagnosing-bugs`, `grilling`, `handoff`, `headroom`, `cartographer`, `skill-creator`. Anote quais aparecem e quais não.
3. Teste de uso, com tarefa pequena e sem Jira: "Use a skill `tdd` para adicionar um teste a uma função pequena de `app/backend`" e veja se o Cline carrega o `SKILL.md` e segue o roteiro.
4. Registre o resultado em `docs/ferramentas-claude-para-o-cline/skills.md` (seção de resultados) com: versão do ClineSR, quantas skills foram reconhecidas, quais falharam e a mensagem.

## Limitações esperadas
- Várias skills citam ferramentas do Claude Code (`Skill`, `Agent`/subagentes, `AskUserQuestion`, `TaskCreate`, `WebSearch`) que o Cline não tem do mesmo jeito. O corpo da skill continua útil como roteiro, mas passos que dependem dessas ferramentas devem ser feitos "à mão" ou pulados. A regra do projeto para perguntas ao usuário (pergunta com opções) vale no lugar.
- `cartographer` e `headroom` trazem scripts/prompts auxiliares; **não execute scripts de skills** sem entender o que fazem (regra do projeto sobre código de terceiros).
- Skills de `product-management` e `productivity` são de uso geral, sem relação com o painel; estão aqui só porque foram pedidas e têm licença livre.
- Nada aqui acessa o Jira. A flag do Jira continua sendo a única trava, e só o usuário a liga.

## Atualizar
Estas cópias são um retrato de 27/10/2026. Para skills que existem em repositório público (`mattpocock/skills`), prefira `scripts/instalar-skills-cline.ps1`, que baixa a versão atual direto da fonte.
