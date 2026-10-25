# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

Backend em Kotlin (Ktor) com PostgreSQL; frontend em React + TypeScript com Vite (SPA). Decisão do usuário, tomada pelas regras de negócio. O app Python atual será substituído.

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

- Fonte de dados: Jira DC da Samsung (Bearer PAT) e Jira da Sidia (Basic, projeto CAT). Nenhuma consulta real ao Jira sem autorização expressa; a flag `JIRA_LIVE_ENABLED=false` é o padrão.
- Ausências, feriados, papéis, squads e pesos de cálculo vêm de arquivos YAML versionados.
- Fuso único America/Manaus; dias úteis como padrão, com opção de dias corridos.
- Cada número é clicável e abre as tasks que o compõem (janela, nova aba, CSV).

## Capabilities and Constraints

- Cards pré-definidos, cada um com sua busca própria; relatório do inventário em `docs/analise/relatorio-cards-app-atual.md`.
- Cards novos aprovados: calendário do time, cobertura por ausência, plano de 4 a 8 semanas, vencimentos da semana com folga real, posse da task por pessoa, simulador de ausência, matriz de competências, revisões pendentes por revisor, carga por tipo/OS, tendência de ocupação, esforço por task fechada (tipo + labels TEST-* + horas do worklog).
- Auditoria: apenas Reporter (PL), coordenadores ou Squad Leader do responsável podem trocar responsável, vencimento e labels de atraso.
- Interface em pt-BR e inglês com escolha do usuário.
- Sem ranking individual de pessoas; tabelas de pessoas em ordem alfabética. Integridade restrita ao gerente e auditada.
- A tela Integridade e a auditoria tratam dados pessoais: validar base legal com o DPO antes do uso real.

## Brand Commitments

O usuário pediu que o frontend siga a identidade visual da Sidia. Temas disponíveis: claro, escuro e Sidia. Os ativos oficiais (logo, manual de marca, fonte) não foram fornecidos; a página pública sidia.com usa a família tipográfica "Mark".

## Evidence on Hand

- App Python atual com 21 telas e manual com capturas: `app/docs/manual/`.
- Documento de engenharia reversa do bscbi-web e catálogo de filtros do TG-Basic (vocabulário de campos).
- Ausentes: manual de identidade visual da Sidia, arquivos de logo e licença da fonte "Mark". Não inventar.

## Product Principles

1. Todo número leva às tasks que o compõem.
2. Métricas de fluxo avaliam o sistema de trabalho, não ranqueiam pessoas.
3. Indício não é prova: auditoria mostra evidência verificável no histórico da task.
4. Nada que dependa do Jira real é afirmado antes de validado com autorização.
5. Cada pessoa vê somente o que seu papel permite.

## Accessibility & Inclusion

WCAG AA como mínimo; a cor nunca é o único sinal (símbolos e texto junto); pt-BR e inglês; temas claro, escuro e Sidia, todos em AA.
