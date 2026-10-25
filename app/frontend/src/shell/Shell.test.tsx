import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { LangProvider } from '../i18n/LangProvider'
import { ThemeProvider } from '../theme/ThemeProvider'
import { Shell } from './Shell'

type MetaSimulado = { devMode: boolean; liveEnabled: boolean; zone: string; banner: string | null }

// Simula só o /api/meta: os testes nunca usam rede.
function simularMeta(meta: MetaSimulado) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => new Response(JSON.stringify(meta), { status: 200 })),
  )
}

function renderizar() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ThemeProvider>
        <LangProvider>
          <MemoryRouter initialEntries={['/']}>
            <Routes>
              <Route element={<Shell />}>
                <Route path="*" element={<p>conteúdo</p>} />
              </Route>
            </Routes>
          </MemoryRouter>
        </LangProvider>
      </ThemeProvider>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  localStorage.clear()
  delete document.documentElement.dataset.theme
})

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

describe('casca', () => {
  it('bannerAparece', async () => {
    simularMeta({ devMode: true, liveEnabled: false, zone: 'America/Manaus', banner: 'MODO DESENVOLVIMENTO' })
    renderizar()
    const faixa = await screen.findByRole('status')
    expect(faixa).toHaveTextContent('MODO DESENVOLVIMENTO')
  })

  it('bannerNaoApareceSemBanner', async () => {
    simularMeta({ devMode: false, liveEnabled: false, zone: 'America/Manaus', banner: null })
    renderizar()
    // espera o menu da casca para garantir que a resposta já foi processada
    await screen.findByRole('navigation')
    await vi.waitFor(() => expect(globalThis.fetch).toHaveBeenCalled())
    expect(screen.queryByRole('status')).toBeNull()
    expect(screen.queryByText('MODO DESENVOLVIMENTO')).toBeNull()
  })

  it('trocaDeTemaGravaNaRaiz', async () => {
    simularMeta({ devMode: true, liveEnabled: false, zone: 'America/Manaus', banner: 'MODO DESENVOLVIMENTO' })
    renderizar()
    await userEvent.click(await screen.findByRole('button', { name: 'Sidia' }))
    expect(document.documentElement.dataset.theme).toBe('sidia')
    expect(localStorage.getItem('painel.tema')).toBe('sidia')
  })

  it('seletorDePapelSoEmDev', async () => {
    simularMeta({ devMode: false, liveEnabled: false, zone: 'America/Manaus', banner: null })
    renderizar()
    await screen.findByRole('navigation')
    await vi.waitFor(() => expect(globalThis.fetch).toHaveBeenCalled())
    expect(screen.queryByRole('button', { name: 'Gerente' })).toBeNull()
  })

  it('papelSimuladoMudaOMenu', async () => {
    simularMeta({ devMode: true, liveEnabled: false, zone: 'America/Manaus', banner: 'MODO DESENVOLVIMENTO' })
    renderizar()
    // padrão GERENTE: vê Integridade
    expect(await screen.findByRole('link', { name: 'Integridade' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Usuário' }))
    expect(screen.queryByRole('link', { name: 'Integridade' })).toBeNull()
    expect(screen.getByRole('link', { name: 'Meu painel' })).toBeInTheDocument()
  })

  it('trocaDeIdiomaTraduzOMenu', async () => {
    simularMeta({ devMode: true, liveEnabled: false, zone: 'America/Manaus', banner: 'MODO DESENVOLVIMENTO' })
    renderizar()
    await userEvent.click(await screen.findByRole('button', { name: 'EN' }))
    expect(document.documentElement.lang).toBe('en')
    expect(screen.getByRole('link', { name: 'My dashboard' })).toBeInTheDocument()
  })
})
