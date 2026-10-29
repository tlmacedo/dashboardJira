# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

Backend em Kotlin (Ktor) com SQLite embutido; frontend em React + TypeScript com Vite (SPA). Decisão do usuário, tomada pelas regras de negócio. O app Python atual será substituído.

## Users

Gerentes, coordenadores, Squad Leaders e usuários (devs) do time TG-Basic. Uso em desktop, no trabalho diário, para acompanhar prazos, carga, ausências e auditoria.
- Gerente e coordenador: veem todos os dados.
- Squad Leader: vê os próprios dados e os de quem está no seu squad.
- Usuário: vê só os próprios dados.
- Em modo desenvolvimento (`DASHBOARD_DEV_MODE=true`) não há login e todos os dados aparecem.

## Product Purpose

Dashboard de gestão do time sobre tasks do Jira Data Center: o que está aberto, atrasado ou em risco, onde o tempo é gasto, quanto cada pessoa carrega, quem estará ausente e como replanejar. Também audita alterações indevidas de datas, responsáveis e labels que escondem atrasos. Sucesso: o gestor planeja e decide com números confiáveis e rastreáveis até as tasks.

## Positioning

Une gestão de fluxo (tempo em status, previsão, capacidade) a auditoria de integridade das tasks (quem alterou prazo, dono ou label, e se tinha autorização), com regras do TG-Basic (Checklist × Review, labels de atraso justificado, labels TEST-*).

## Operating Context

- Fonte de dados: Jira DC da Samsung (Bearer PAT) e Jira da Sidia (Basic, projeto CAT). A flag `JIRA_LIVE_ENABLED` é a única trava do acesso ao Jira e só o usuário a muda; `false` é o padrão.
- Ausências, feriados, papéis, squads e pesos de cálculo vêm de arquivos YAML versionados.
- Fuso único America/Manaus; dias úteis como padrão, com opção de dias corridos.
- Cada número é clicável e abre as tasks que o compõem (janela, nova aba, CSV).

## Capabilities and Constraints

- Cada indicador é um **cartão** registrado no catálogo (`catalogoPadrao()`), com título, "o que mostra" e "como calcula" em pt e en, e uma gaveta que abre as tasks que o compõem (o teste de contrato garante número do cartão = total da gaveta). A tela **Mapa de cartões** (coordenação e gerente) lista todos. Inventário do app anterior: `docs/analise/relatorio-cards-app-atual.md`; equivalência por card com o app atual: `docs/analise/equivalencia-por-card.md`.
- Telas implementadas (somente leitura, sobre dados sincronizados do Jira): tempo em status, abertas, negligenciadas, fechadas, metas e prazos, negativas, riscos, previsão, épicos, fluxo da semana, envelhecimento por etapa, bloqueios entre tasks, cruzamentos; carga, capacidade e ausências, calendário do time, plano de 4 a 8 semanas, vencimentos da semana, gargalos, tendência de ocupação, posse da task, esforço por task fechada, cobertura por ausência, simulador de ausência, matriz de competências, revisões pendentes e carga por tipo/OS; checklist × review; **Central de atenção**, Meu squad e Meu painel; **Meus dados** (cada pessoa vê o que o painel guarda sobre ela).
- Distribuição e rebalanceamento de tasks funcionam em **modo sombra**: sugerem, a decisão é gravada só no painel e nada é escrito no Jira. Escrever no Jira pelo painel é o Plano 16 e só acontece por decisão expressa do usuário.
- Auditoria: apenas Reporter (PL), coordenadores e gerente, ou Squad Leader do responsável podem trocar responsável, vencimento e labels de atraso. Auditoria (inclusive estimativa reduzida e worklog retroativo) e Integridade ficam atrás da **trava do DPO**: respondem 403 até o aceite do encarregado de dados, que só o usuário registra.
- Operação: logs, prontidão, métricas, alertas, backup diário com restauração testada e pacote de produção com versão e SHA-256 (`docs/operacao/`, `docs/implantacao.md`).
- Acesso: o `id` de cada pessoa em `papeis.yaml` é o username dela no Jira, igual ao `assignee` das issues (não é código de cargo); o papel define o que ela vê.
- Interface em pt-BR e inglês com escolha do usuário.
- Sem ranking individual de pessoas; tabelas de pessoas em ordem alfabética. Integridade restrita ao gerente e auditada.
- A tela Integridade e a auditoria tratam dados pessoais: validar base legal com o DPO antes do uso real.

## Brand Commitments

O usuário pediu que o frontend siga a identidade visual da Sidia. Temas disponíveis: claro, escuro e Sidia. Os ativos oficiais (logo, manual de marca, fonte) não foram fornecidos; a página pública sidia.com usa a família tipográfica "Mark".

## Evidence on Hand

- App Python anterior com 21 telas e manual com capturas: preservados no branch (ou tag) `legacy-final` (a pasta do app saiu do repositório em 29/10/2026, com o "sim" do usuário ao documento de equivalência).
- Documento de engenharia reversa do bscbi-web e catálogo de filtros do TG-Basic (vocabulário de campos).
- Referência local da documentação pública do Jira Data Center (JQL, filtros, REST, gestão): `docs/jira/referencia/`. Nenhum exemplo dela foi testado em servidor real.
- Guia do painel, perguntas frequentes e treinamento por papel: `docs/manual/guia-do-painel.md`, `docs/manual/perguntas-frequentes.md`, `docs/treinamento/`.
- Relatório do que foi feito antes de qualquer acesso ao Jira, com decisões, limites e ações pendentes: `docs/superpowers/relatorio-final-antes-da-flag.md`. Conferência card a card contra o app anterior (o documento diz quais linhas têm comparação numérica com o legado e quais são só consistentes): `docs/analise/equivalencia-por-card.md`.
- Ausentes: manual de identidade visual da Sidia, arquivos de logo e licença da fonte "Mark" (o painel usa alternativas de sistema; ver `docs/design/ativos-de-marca.md`). Não inventar. Ausentes também: qualquer dado real do Jira (a flag nunca foi ligada), o parecer do DPO e capturas de tela das telas em navegador.

## Product Principles

1. Todo número leva às tasks que o compõem.
2. Métricas de fluxo avaliam o sistema de trabalho, não ranqueiam pessoas.
3. Indício não é prova: auditoria mostra evidência verificável no histórico da task.
4. Nada que dependa do Jira real é afirmado antes de ser validado; o acesso ao Jira só existe com a flag ligada pelo usuário (regra 1 do `CLAUDE.md`).
5. Cada pessoa vê somente o que seu papel permite.

## Accessibility & Inclusion

WCAG AA como mínimo; a cor nunca é o único sinal (símbolos e texto junto); pt-BR e inglês; temas claro, escuro e Sidia, todos em AA.
