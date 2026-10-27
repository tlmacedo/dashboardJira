# /escrever-plano.md: escrever um plano no padrão do projeto

Porte de `superpowers:brainstorming` + `superpowers:writing-plans`, como o Claude Code os usou aqui. Os planos ficam em `docs/superpowers/plans/AAAA-MM-DD-plano-NN-<nome>.md` e as specs de fase em `docs/superpowers/specs/AAAA-MM-DD-spec-fase-NN-<nome>.md`.

**Modelos a copiar (leia antes):**
- `docs/superpowers/plans/2026-10-25-plano-04a-sincronizacao.md`: plano completo mais recente (restrições globais, foco da revisão, mapa de arquivos, tarefas com interfaces e testes nomeados, autoavaliação, decisões).
- `docs/superpowers/plans/2026-10-24-plano-03-tempo-em-status.md`: fatia completa (API + domínio + banco + tela).
- `docs/superpowers/plans/2026-10-24-indice-dos-planos.md`: ordem e dependências; todo plano novo entra aqui.

## 1. Entender antes de escrever (brainstorming)

1. Leia a spec mestra (`docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md`), a spec da fase, o `HANDOFF.md` (pendências e Minor adiados) e o código que o plano vai tocar.
2. Liste as decisões de produto que a spec não fecha. Cada uma vira pergunta ao usuário pelo `/perguntar-ao-usuario.md` (uma por assunto, recomendada primeiro, porquê e consequência). Não escreva tarefas sobre decisões em aberto.
3. Divida em entregas pequenas e mescláveis: um plano deve caber numa PR revisável em horas ou poucos dias. Funcionalidade incompleta fica atrás de flag ou fora do caminho do usuário.

## 2. Estrutura obrigatória do arquivo

```
# <Título> (Plano NN) — Plano de implementação

> **Para executores:** use o workflow `/executar-plano.md` (Cline) ou superpowers:subagent-driven-development (Claude Code). Os passos usam caixas `- [ ]`.

**Objetivo (Goal):** o que fica pronto e como se prova, em um parágrafo.
**Arquitetura (Architecture):** peças novas e como se ligam.
**Stack (Tech Stack):** versões fixas usadas.
**Spec da fase (ler primeiro):** caminho; spec mestra e seções; origem das pendências.

## Restrições globais (Global Constraints)
(Jira e flag, MockEngine, SQLite sem Docker (ver o adendo de plataforma), JQL só pelo JqlBuilder, dados pessoais, migração no próximo número livre, commits em pt-BR...)

## Foco da revisão (Review Focus)
(os riscos do plano, numerados, cada um com o NOME do teste que o prova)

## Arquivos (mapa)
(árvore dos arquivos criados e modificados, com um comentário curto por linha)

### Tarefa N: <nome>
**Arquivos:** Criar ...; Modificar ...; Teste ...
**Interfaces:** Consome / Produz (assinaturas Kotlin/TypeScript exatas, em bloco de código)
- [ ] **Passo 1: Testes que falham:** lista de testes nomeados, cada um com entrada e resultado esperado
- [ ] **Passo 2: Rodar** <comando exato> → FALHA
- [ ] **Passo 3: Implementar.** (regras e cuidados, sem colar o código inteiro)
- [ ] **Passo 4: Rodar** <comando> → PASSAM. **Passo 5: Commit** `tipo(escopo): mensagem em pt-BR`

## Autoavaliação
(cobertura da spec, consistência de nomes entre tarefas, o que fica fora do plano)

## Decisões
(numeradas, com data e autor: "Decisão do usuário (DD/MM)" ou "Valor padrão recomendado (reversível)")
```

## 3. Regras de escrita

- Cada tarefa tem testes **nomeados** e o comando exato para rodá-los; cada risco do Foco da revisão aponta para um teste.
- Assinaturas exatas nas Interfaces: o executor não deve inventar nomes.
- Passos curtos (minutos, não horas); uma tarefa = um commit.
- Pt-BR; fixtures e exemplos só com nomes fictícios (`ana.silva`, `.invalid`).
- Nada que exija o Jira real: tudo executável offline (fixtures, MockEngine). O que só se confirma na primeira conexão vira item do `docs/jira/checklist-primeira-conexao.md`.
- Variáveis novas começam com `SYNC_` ou `DASHBOARD_` (nunca `JIRA_`, que o hook barra).

## 4. Fechamento

1. Releia o plano contra a spec (autoavaliação) e corrija lacunas, nomes inconsistentes e passos vagos.
2. Atualize o índice dos planos.
3. Mostre ao usuário um resumo e pergunte, com opções, se aprova o plano e como executar (por exemplo: "Executar agora em `feat/<nome>` (Recomendada)", "Revisar o plano antes", "Ajustar escopo").
4. Commit do plano em pt-BR (`docs(planos): ...`); a execução só começa depois da aprovação.
