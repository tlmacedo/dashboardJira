// Apoio aos testes das telas de cards: servidor simulado por rota e renderização com o roteador, o idioma e o cache.
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { vi } from 'vitest'
import { LangProvider } from '../i18n/LangProvider'
import type { TasksDoCartao } from '../shared/tasksApi'

export type Resp = { status: number; corpo?: unknown }

/** Troca o `fetch` por um servidor simulado: caminho (sem query) -> resposta; o que não existe responde 404. */
export function simular(rotas: Record<string, Resp>) {
  const mock = vi.fn(async (entrada: RequestInfo | URL) => {
    const caminho = String(entrada).split('?')[0]
    const r = rotas[caminho]
    if (!r) return new Response('', { status: 404 })
    return new Response(r.corpo === undefined ? null : JSON.stringify(r.corpo), { status: r.status })
  })
  vi.stubGlobal('fetch', mock)
  return mock
}

function Localizacao() {
  const l = useLocation()
  return <output data-testid="url">{l.search}</output>
}

/** Renderiza a tela na rota `rota` (ex.: `/abertas`) abrindo em `inicial` (com a query). */
export function renderizarTela(tela: ReactElement, rota: string, inicial = rota) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <LangProvider>
        <MemoryRouter initialEntries={[inicial]}>
          <Routes>
            <Route path={rota} element={tela} />
          </Routes>
          <Localizacao />
        </MemoryRouter>
      </LangProvider>
    </QueryClientProvider>,
  )
}

/** Resposta do drill genérico com `n` linhas fictícias. */
export function gaveta(n: number, tituloChave = 'card.ab01.titulo'): TasksDoCartao {
  return {
    tituloChave,
    definicaoChave: tituloChave.replace('.titulo', '.oQueMostra'),
    total: n,
    colunas: ['chave', 'resumo', 'responsavel', 'status', 'detalhe'],
    linhas: Array.from({ length: n }, (_, i) => ({ key: `TG-${100 + i}`, resumo: `Task ${i}`, responsavel: 'ana', status: 'Open', detalhe: `Detalhe ${i}` })),
    linkJira: null,
  }
}
