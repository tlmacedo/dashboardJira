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

// Sinal observável de que a query de /api/meta já terminou (sucesso ou erro).
async function esperarMeta(container: HTMLElement, estado: 'success' | 'error' = 'success') {
  await vi.waitFor(() =>
    expect(container.querySelector('.shell')).toHaveAttribute('data-meta', estado),
  )
}

const MANAUS = 'America/Manaus'
const verLinks = () => ({
  integridade: screen.queryByRole('link', { name: 'Integridade' }),
  auditoria: screen.queryByRole('link', { name: 'Alterações de data, dono e label' }),
})

beforeEach(() => {
  localStorage.clear()
  delete document.documentElement.dataset.theme
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
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
    simularMeta({ devMode: true, liveEnabled: false, zone: MANAUS, banner: null })
    const { container } = renderizar()
    await esperarMeta(container)
    // o seletor de papel só existe depois que a resposta (devMode:true) foi processada
    expect(screen.getByRole('button', { name: 'Gerente' })).toBeInTheDocument()
    expect(screen.queryByRole('status')).toBeNull()
  })

  it('trocaDeTemaGravaNaRaiz', async () => {
    simularMeta({ devMode: true, liveEnabled: false, zone: 'America/Manaus', banner: 'MODO DESENVOLVIMENTO' })
    renderizar()
    await userEvent.click(await screen.findByRole('button', { name: 'Sidia' }))
    expect(document.documentElement.dataset.theme).toBe('sidia')
    expect(localStorage.getItem('painel.tema')).toBe('sidia')
  })

  it('seletorDePapelSoEmDev', async () => {
    simularMeta({ devMode: false, liveEnabled: false, zone: MANAUS, banner: null })
    const { container } = renderizar()
    await esperarMeta(container) // resposta já processada
    expect(screen.queryByRole('button', { name: 'Gerente' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Usuário' })).toBeNull()
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

  it('producaoIgnoraPapelSimuladoGravado', async () => {
    localStorage.setItem('painel.papelSimulado', 'GERENTE')
    simularMeta({ devMode: false, liveEnabled: false, zone: MANAUS, banner: null })
    const { container } = renderizar()
    await esperarMeta(container)
    const { integridade, auditoria } = verLinks()
    expect(integridade).toBeNull()
    expect(auditoria).toBeNull()
    expect(screen.getByRole('link', { name: 'Meu painel' })).toBeInTheDocument()
  })

  it.each([
    ['rejeicao', () => vi.fn(async () => Promise.reject(new Error('rede fora')))],
    ['500', () => vi.fn(async () => new Response('erro', { status: 500 }))],
  ])('metaFalhaViraUsuario (%s)', async (_nome, fabrica) => {
    localStorage.setItem('painel.papelSimulado', 'GERENTE')
    vi.stubGlobal('fetch', fabrica())
    const { container } = renderizar()
    await esperarMeta(container, 'error')
    const { integridade, auditoria } = verLinks()
    expect(integridade).toBeNull()
    expect(auditoria).toBeNull()
    expect(screen.getByRole('link', { name: 'Meu painel' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Gerente' })).toBeNull()
  })

  it('botaoSairSoForaDoModoDesenvolvimento', async () => {
    simularMeta({ devMode: true, liveEnabled: false, zone: MANAUS, banner: 'MODO DESENVOLVIMENTO' })
    const dev = renderizar()
    await esperarMeta(dev.container)
    expect(screen.queryByRole('button', { name: 'Sair' })).toBeNull()
    cleanup()

    simularMeta({ devMode: false, liveEnabled: false, zone: MANAUS, banner: null })
    const producao = renderizar()
    await esperarMeta(producao.container)
    expect(screen.getByRole('button', { name: 'Sair' })).toBeInTheDocument()
  })

  it('sairEnviaLogoutComCsrfEVoltaParaARaiz', async () => {
    const chamadas: { url: string; metodo: string; csrf: string | null }[] = []
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        const cabecalhos = new Headers(init?.headers)
        chamadas.push({ url, metodo: init?.method ?? 'GET', csrf: cabecalhos.get('X-Painel-CSRF') })
        if (url === '/auth/logout') return new Response(null, { status: 204 })
        return new Response(JSON.stringify({ devMode: false, liveEnabled: false, zone: MANAUS, banner: null }), { status: 200 })
      }),
    )
    const assign = vi.fn()
    vi.stubGlobal('location', { ...window.location, assign })
    const { container } = renderizar()
    await esperarMeta(container)
    await userEvent.click(screen.getByRole('button', { name: 'Sair' }))
    await vi.waitFor(() => expect(assign).toHaveBeenCalledWith('/'))
    const logout = chamadas.find((c) => c.url === '/auth/logout')
    expect(logout).toEqual({ url: '/auth/logout', metodo: 'POST', csrf: '1' })
  })

  it('sairQueFalhaNaoNavega', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) =>
        url === '/auth/logout'
          ? new Response(null, { status: 503 })
          : new Response(JSON.stringify({ devMode: false, liveEnabled: false, zone: MANAUS, banner: null }), { status: 200 }),
      ),
    )
    const assign = vi.fn()
    vi.stubGlobal('location', { ...window.location, assign })
    const { container } = renderizar()
    await esperarMeta(container)
    await userEvent.click(screen.getByRole('button', { name: 'Sair' }))
    // a falha vira aviso na tela e a sessão continua: nada de ir para a raiz fingindo que saiu
    expect(await screen.findByText(/Não foi possível sair/)).toBeInTheDocument()
    expect(assign).not.toHaveBeenCalled()
  })

  it('temaEIdiomaInvalidosCaemNoPadrao', async () => {
    localStorage.setItem('painel.tema', 'neon')
    localStorage.setItem('painel.idioma', 'fr')
    simularMeta({ devMode: false, liveEnabled: false, zone: MANAUS, banner: null })
    const { container } = renderizar()
    await esperarMeta(container)
    expect(document.documentElement.dataset.theme).toBe('light') // jsdom sem matchMedia
    expect(document.documentElement.lang).toBe('pt-BR')
    expect(screen.getByRole('link', { name: 'Meu painel' })).toBeInTheDocument()
  })

  it('localStorageQueLancaNaoQuebra', async () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('bloqueado')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('bloqueado')
    })
    simularMeta({ devMode: true, liveEnabled: false, zone: MANAUS, banner: 'MODO DESENVOLVIMENTO' })
    const { container } = renderizar()
    await esperarMeta(container)
    await userEvent.click(screen.getByRole('button', { name: 'Sidia' }))
    expect(document.documentElement.dataset.theme).toBe('sidia')
    await userEvent.click(screen.getByRole('button', { name: 'EN' }))
    expect(document.documentElement.lang).toBe('en')
    await userEvent.click(screen.getByRole('button', { name: 'Coordinator' }))
    expect(screen.getByRole('link', { name: 'Attention center' })).toBeInTheDocument()
  })
})
