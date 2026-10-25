# Frontend do Painel de Squads

React + TypeScript + Vite. Consome apenas a API do próprio painel (Ktor); nunca fala com o Jira.

```bash
pnpm install
pnpm dev          # http://localhost:5173, com /api encaminhado para http://localhost:8080
pnpm vitest run   # testes
pnpm build        # verificação de tipos + build de produção
```

Estrutura: `src/theme` (tokens e tema), `src/i18n` (pt/en), `src/nav` (menu por papel),
`src/shell` (casca: menu, faixa de desenvolvimento), `src/api` (cliente da API).
