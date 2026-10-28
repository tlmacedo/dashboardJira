import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import en from '../i18n/en.json'
import { LangProvider } from '../i18n/LangProvider'
import pt from '../i18n/pt.json'
import type { Role } from '../nav/nav'
import { AvisoDeAlertas, ListaDeAlertas } from './AvisoDeAlertas'
import { RoleContext } from './RoleContext'

const ROTA = '/api/operacao/alertas'

type Alerta = { codigo: string; gravidade: string; instancia: string | null; desde: string }

const CRITICO: Alerta = { codigo: 'SYNC_SUSPENSA', gravidade: 'CRITICO', instancia: 'samsung', desde: '2026-10-30T12:00:00Z' }
const OUTRO_CRITICO: Alerta = { codigo: 'DISCO_BAIXO', gravidade: 'CRITICO', instancia: null, desde: '2026-10-30T12:30:00Z' }
const ATENCAO: Alerta = { codigo: 'PAT_EXPIRANDO', gravidade: 'ATENCAO', instancia: 'samsung', desde: '2026-10-30T13:00:00Z' }

function simular(alertas: Alerta[] | number) {
  const mock = vi.fn(async (entrada: RequestInfo | URL) => {
    if (String(entrada).split('?')[0] !== ROTA) return new Response('', { status: 404 })
    if (typeof alertas === 'number') return new Response('', { status: alertas })
    return new Response(JSON.stringify({ alertas }), { status: 200 })
  })
  vi.stubGlobal('fetch', mock)
  return mock
}

function renderizar(papel: Role, filho = <AvisoDeAlertas />) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <LangProvider>
        <RoleContext.Provider value={papel}>
          <MemoryRouter>{filho}</MemoryRouter>
        </RoleContext.Provider>
      </LangProvider>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  localStorage.clear()
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('AvisoDeAlertas', () => {
  it('faixaSoParaGerente', async () => {
    const mock = simular([CRITICO])
    for (const papel of ['USUARIO', 'SQUAD_LEADER', 'COORDENADOR'] as Role[]) {
      renderizar(papel)
      await new Promise((r) => setTimeout(r, 20))
      expect(screen.queryByRole('status')).toBeNull()
      cleanup()
    }
    expect(mock).not.toHaveBeenCalled() // quem não é gerente nem consulta
    renderizar('GERENTE')
    expect(await screen.findByRole('status')).toBeInTheDocument()
    expect(mock).toHaveBeenCalledTimes(1)
  })

  it('criticoComTrianguloETexto', async () => {
    simular([CRITICO, OUTRO_CRITICO, ATENCAO])
    renderizar('GERENTE')
    const faixa = await screen.findByRole('status')
    expect(within(faixa).getByText('▲ 2 alerta(s) crítico(s)')).toBeInTheDocument()
    expect(within(faixa).getByText('● 1 de atenção')).toBeInTheDocument()
  })

  it('criticoComTrianguloETextoEmIngles', async () => {
    localStorage.setItem('painel.idioma', 'en')
    simular([CRITICO, ATENCAO])
    renderizar('GERENTE')
    const faixa = await screen.findByRole('status')
    expect(within(faixa).getByText('▲ 1 critical alert(s)')).toBeInTheDocument()
    expect(within(faixa).getByText('● 1 warning(s)')).toBeInTheDocument()
  })

  it('semAlertasSemFaixa', async () => {
    const mock = simular([])
    renderizar('GERENTE')
    await vi.waitFor(() => expect(mock).toHaveBeenCalled())
    await new Promise((r) => setTimeout(r, 20))
    expect(screen.queryByRole('status')).toBeNull()
  })

  it('erroNaConsultaNaoMostraFaixa', async () => {
    const mock = simular(500)
    renderizar('GERENTE')
    await vi.waitFor(() => expect(mock).toHaveBeenCalled())
    await new Promise((r) => setTimeout(r, 20))
    expect(screen.queryByRole('status')).toBeNull()
  })

  it('respostaEstranhaNaoQuebra', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ devMode: true }), { status: 200 })))
    renderizar('GERENTE')
    await new Promise((r) => setTimeout(r, 20))
    expect(screen.queryByRole('status')).toBeNull()
  })

  it('linkParaSincronizacao', async () => {
    simular([ATENCAO])
    renderizar('GERENTE')
    const faixa = await screen.findByRole('status')
    expect(within(faixa).getByRole('link', { name: 'Ver detalhes' })).toHaveAttribute('href', '/sincronizacao#alertas')
  })
})

describe('ListaDeAlertas', () => {
  it('listaCadaAlertaComSimboloTextoInstanciaEDesde', async () => {
    simular([CRITICO, ATENCAO])
    renderizar('GERENTE', <ListaDeAlertas />)
    const lista = await screen.findByRole('list', { name: 'Alertas operacionais' })
    const itens = within(lista).getAllByRole('listitem')
    expect(itens).toHaveLength(2)
    expect(itens[0].textContent).toContain('▲')
    expect(itens[0].textContent).toContain('Sincronização suspensa')
    expect(itens[0].textContent).toContain('samsung')
    // 2026-10-30T12:00Z = 08:00 em Manaus
    expect(itens[0].textContent).toMatch(/30\/10\/2026.*08:00/)
    expect(itens[1].textContent).toContain('●')
    expect(itens[1].textContent).toContain('Token do Jira perto de vencer')
  })

  it('semAlertasDizQueNaoHaNenhum', async () => {
    simular([])
    renderizar('GERENTE', <ListaDeAlertas />)
    expect(await screen.findByText('Nenhum alerta ativo.')).toBeInTheDocument()
  })

  it('naoApareceParaQuemNaoEGerente', async () => {
    const mock = simular([CRITICO])
    renderizar('COORDENADOR', <ListaDeAlertas />)
    await new Promise((r) => setTimeout(r, 20))
    expect(screen.queryByRole('list', { name: 'Alertas operacionais' })).toBeNull()
    expect(mock).not.toHaveBeenCalled()
  })
})

describe('traducoes dos alertas', () => {
  // Lista fixa igual ao enum `CodigoAlerta` do backend (14 códigos). O teste do backend `AlertasTraduzidosTest` lê este
  // mesmo pt.json/en.json e acusa um código novo do enum sem tradução, então as duas pontas não se separam.
  const codigosDoBackend = [
    'SYNC_SUSPENSA',
    'SYNC_ATRASADA',
    'RECONCILIACAO_SUSPEITA',
    'RECONCILIACAO_ATRASADA',
    'RETENCAO_FALHOU',
    'ACCESS_LOG_FALHANDO',
    'PAT_EXPIRANDO',
    'PAT_EXPIRADO',
    'CALENDARIO_SEM_PROXIMO_ANO',
    'DISCO_BAIXO',
    'BANCO_SUSPEITO',
    'BACKUP_ATRASADO',
    'RESTAURACAO_NAO_TESTADA',
    'MODO_DEV_COM_JIRA_LIGADO',
  ]

  it('todoCodigoDeAlertaTraduzido', () => {
    expect(codigosDoBackend).toHaveLength(14)
    for (const codigo of codigosDoBackend) {
      for (const [nome, dic] of [['pt', pt], ['en', en]] as const) {
        const texto = (dic as Record<string, string>)[`alertas.${codigo}`]
        expect(texto, `alertas.${codigo} em ${nome}`).toBeTruthy()
        expect(texto).not.toBe(`alertas.${codigo}`)
      }
    }
  })
})
