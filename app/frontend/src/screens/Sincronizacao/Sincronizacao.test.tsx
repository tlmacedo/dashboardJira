import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { LangProvider } from '../../i18n/LangProvider'
import { ThemeProvider } from '../../theme/ThemeProvider'
import type { ExecucaoDto, SyncStatusDto } from './api'
import { Sincronizacao } from './Sincronizacao'

const EXEC_OK: ExecucaoDto = {
  tipo: 'INCREMENTAL',
  status: 'OK',
  inicio: '2026-10-25T15:00:00Z',
  fim: '2026-10-25T15:01:00Z',
  issues: 12,
  eventos: 40,
  requisicoes: 3,
  incompletas: 0,
  usuariosNaoResolvidos: 0,
  removidas: 0,
  codigoErro: null,
}

const EXEC_FALHA: ExecucaoDto = {
  ...EXEC_OK,
  status: 'FALHA',
  inicio: '2026-10-25T14:00:00Z',
  fim: '2026-10-25T14:00:05Z',
  issues: 0,
  eventos: 0,
  requisicoes: 1,
  codigoErro: 'HTTP_401',
}

const EXEC_CANCELADA: ExecucaoDto = { ...EXEC_OK, status: 'CANCELADA', inicio: '2026-10-25T13:00:00Z', fim: null }

const LIGADO: SyncStatusDto = {
  modo: 'PERIODICO',
  liveEnabled: true,
  instancias: [
    {
      instancia: 'samsung',
      gate: 'LIBERADA',
      cursor: '2026-10-25T15:00:00Z',
      ultimaReconciliacao: '2026-10-25T07:00:00Z',
      suspensa: null,
      execucoes: [EXEC_OK, EXEC_FALHA, EXEC_CANCELADA],
    },
  ],
  retencao: { ...EXEC_OK, tipo: 'RETENCAO', removidas: 7 },
}

const DESLIGADO: SyncStatusDto = {
  modo: 'DESLIGADO_PELA_FLAG',
  liveEnabled: false,
  instancias: [
    { instancia: 'samsung', gate: 'DESLIGADO_PELA_FLAG', cursor: null, ultimaReconciliacao: null, suspensa: null, execucoes: [] },
  ],
  retencao: null,
}

const ROTA = '/api/sync/status'

function simular(status: number, corpo?: unknown) {
  const mock = vi.fn(async (entrada: RequestInfo | URL) => {
    if (String(entrada).split('?')[0] !== ROTA) return new Response('', { status: 404 })
    return new Response(corpo === undefined ? null : JSON.stringify(corpo), { status })
  })
  vi.stubGlobal('fetch', mock)
  return mock
}

function renderizar() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ThemeProvider>
        <LangProvider>
          <MemoryRouter initialEntries={['/sincronizacao']}>
            <Routes>
              <Route path="/sincronizacao" element={<Sincronizacao />} />
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
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Sincronização', () => {
  it('telaSincronizacaoMostraCodigos', async () => {
    simular(200, LIGADO)
    renderizar()
    const tabela = await screen.findByRole('table', { name: /Últimas execuções.*samsung/ })
    const linhas = within(tabela).getAllByRole('row')
    // cabeçalho + 3 execuções
    expect(linhas).toHaveLength(4)
    const falha = linhas.find((l) => /HTTP_401|401/.test(l.textContent ?? ''))
    expect(falha).toBeDefined()
    // Código traduzido (texto), símbolo ▲ e o código técnico também visível.
    expect(within(falha as HTMLElement).getByText(/▲\s*Falha/)).toBeInTheDocument()
    expect(within(falha as HTMLElement).getByText(/não autorizado/i)).toBeInTheDocument()
    expect(within(falha as HTMLElement).getByText(/HTTP_401/)).toBeInTheDocument()
    expect(within(linhas[1]).getByText(/✓\s*OK/)).toBeInTheDocument()
    expect(within(tabela).getByText(/●\s*Cancelada/)).toBeInTheDocument()
  })

  it('telaSincronizacaoMostraDesligadoPelaFlag', async () => {
    simular(200, DESLIGADO)
    renderizar()
    const faixa = await screen.findByRole('status')
    expect(within(faixa).getByText(/●\s*Desligado pela flag/)).toBeInTheDocument()
    expect(within(faixa).getByText(/só o usuário liga a flag; reinício necessário/i)).toBeInTheDocument()
    // Instância sem execução: estado vazio explicado.
    expect(screen.getByText(/Nenhuma execução registrada/)).toBeInTheDocument()
    expect(screen.getByText(/Desligado pela flag/, { selector: '.sinc-gate' })).toBeInTheDocument()
  })

  it('telaSincronizacaoMostraDesligadoPelaFlagEmIngles', async () => {
    localStorage.setItem('painel.idioma', 'en')
    simular(200, DESLIGADO)
    renderizar()
    const faixa = await screen.findByRole('status')
    expect(within(faixa).getByText(/●\s*Off by flag/)).toBeInTheDocument()
    expect(within(faixa).getByText(/only the user turns the flag on; restart required/i)).toBeInTheDocument()
  })

  it('suspensaMostraNotaDeReinicio', async () => {
    simular(200, {
      ...LIGADO,
      instancias: [{ ...LIGADO.instancias[0], gate: 'SUSPENSA_POR_ERRO', suspensa: 'HTTP_401' }],
    })
    renderizar()
    expect(await screen.findByText(/▲\s*Suspensa/)).toBeInTheDocument()
    expect(screen.getByText(/reinicie o app para retomar/i)).toBeInTheDocument()
  })

  it('telaSincronizacao403MostraMensagemDePapel', async () => {
    simular(403)
    renderizar()
    expect(await screen.findByText(/apenas o gerente/i)).toBeInTheDocument()
    expect(screen.queryByRole('table')).toBeNull()
  })

  it('erroDeServidorMostraTentarDeNovo', async () => {
    const mock = simular(500)
    renderizar()
    const botao = await screen.findByRole('button', { name: 'Tentar de novo' })
    expect(screen.getByRole('alert')).toHaveTextContent(/HTTP 500/)
    await userEvent.click(botao)
    await vi.waitFor(() => expect(mock.mock.calls.length).toBeGreaterThan(1))
  })

  it('mostraCarregando', () => {
    vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>(() => {})))
    renderizar()
    expect(screen.getByText('Carregando…')).toBeInTheDocument()
  })

  it('nuncaMostraTextoLivreDeErro', async () => {
    simular(200, {
      ...LIGADO,
      instancias: [{ ...LIGADO.instancias[0], execucoes: [{ ...EXEC_FALHA, codigoErro: 'CODIGO_NOVO_DESCONHECIDO' }] }],
    })
    renderizar()
    // Código desconhecido aparece como está (é só um código), sem quebrar a tela.
    expect(await screen.findByText(/CODIGO_NOVO_DESCONHECIDO/)).toBeInTheDocument()
  })
})
