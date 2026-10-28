import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gaveta, renderizarTela, simular } from '../../test/telas'
import type { SimuladorDto } from './api'
import { Simulador } from './Simulador'

const VAZIO: SimuladorDto = {
  pessoa: '', de: '', ate: '', fracao: 1, atrasosCausados: [], horasAReplanejar: 0, semanas: [],
  avisos: [{ codigo: 'SIMULACAO_SEM_PARAMETROS', gravidade: 'informativo', mensagem: 'x' }],
}
const DTO: SimuladorDto = {
  pessoa: 'bruno', de: '2026-11-03', ate: '2026-11-06', fracao: 1, atrasosCausados: ['TG-203'], horasAReplanejar: 1.2, avisos: [],
  semanas: [
    { semana: '2026-10-26', antes: { fracao: 0.2, semCapacidade: false, estado: 'ok' }, depois: { fracao: 0.2, semCapacidade: false, estado: 'ok' } },
    { semana: '2026-11-02', antes: { fracao: 0.18, semCapacidade: false, estado: 'ok' }, depois: { fracao: null, semCapacidade: true, estado: 'problema' } },
  ],
}
const CARD = '/api/cards/simulador'
const URL_BRUNO = '/simulador?pessoa=bruno&de=2026-11-03&ate=2026-11-06&fracao=1.0'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Simulador de ausência', () => {
  it('simuladorMostraAntesEDepois', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Simulador />, '/simulador', URL_BRUNO)
    const tabela = await screen.findByRole('table', { name: 'Ocupação por semana: antes e depois' })
    const linha = within(tabela).getByText('02/11/2026').closest('tr') as HTMLElement
    // Antes com número e símbolo; depois "sem capacidade" em texto, nunca porcentagem.
    expect(linha).toHaveTextContent(/✓\s*18%/)
    expect(linha).toHaveTextContent('sem capacidade')
    expect(linha.textContent).not.toMatch(/-?\d+%\s*\(problema\)/)
    const kpis = screen.getByRole('group', { name: 'Resultado da simulação' })
    expect(within(kpis).getByText('Tasks que passariam a atrasar')).toBeInTheDocument()
    expect(within(kpis).getByText('1,2 h')).toBeInTheDocument()
  })

  it('semParametrosMostraSoOFormulario', async () => {
    simular({ [CARD]: { status: 200, corpo: VAZIO } })
    renderizarTela(<Simulador />, '/simulador')
    expect(await screen.findByText('Informe a pessoa e o período para simular.')).toBeInTheDocument()
    expect(screen.getByRole('form', { name: 'Parâmetros da simulação' })).toBeInTheDocument()
    expect(screen.queryByRole('table')).toBeNull()
  })

  it('simuladorSoUsaGet', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Simulador />, '/simulador')
    await screen.findByRole('form', { name: 'Parâmetros da simulação' })
    await userEvent.type(screen.getByLabelText('Pessoa (username)'), 'bruno')
    await userEvent.type(screen.getByLabelText('De'), '2026-11-03')
    await userEvent.type(screen.getByLabelText('Até'), '2026-11-06')
    await userEvent.click(screen.getByRole('button', { name: 'Simular' }))
    // O formulário só muda a URL da tela...
    const url = screen.getByTestId('url').textContent ?? ''
    expect(url).toContain('pessoa=bruno')
    expect(url).toContain('de=2026-11-03')
    expect(url).toContain('ate=2026-11-06')
    expect(url).toContain('fracao=1.0')
    await vi.waitFor(() => expect(mock.mock.calls.some((c) => String(c[0]).includes('pessoa=bruno'))).toBe(true))
    // ...e NENHUM fetch usa método diferente de GET (o simulador nunca envia POST, PUT, PATCH nem DELETE).
    const chamadas = mock.mock.calls as unknown as [RequestInfo | URL, RequestInit | undefined][]
    expect(chamadas.length).toBeGreaterThan(0)
    for (const [, init] of chamadas) expect((init?.method ?? 'GET').toUpperCase()).toBe('GET')
  })

  it('fracaoMeiaVaiNaUrl', async () => {
    simular({ [CARD]: { status: 200, corpo: VAZIO } })
    renderizarTela(<Simulador />, '/simulador')
    await screen.findByRole('form', { name: 'Parâmetros da simulação' })
    await userEvent.type(screen.getByLabelText('Pessoa (username)'), 'ana')
    await userEvent.type(screen.getByLabelText('De'), '2026-11-03')
    await userEvent.type(screen.getByLabelText('Até'), '2026-11-04')
    await userEvent.selectOptions(screen.getByLabelText('Fração do dia'), '0.5')
    await userEvent.click(screen.getByRole('button', { name: 'Simular' }))
    expect(screen.getByTestId('url').textContent).toContain('fracao=0.5')
  })

  it('atrasosCausadosAbremAGavetaComMesmoTamanho', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/SI-01/tasks': { status: 200, corpo: gaveta(1, 'card.si01.titulo') } })
    renderizarTela(<Simulador />, '/simulador', URL_BRUNO)
    const kpis = await screen.findByRole('group', { name: 'Resultado da simulação' })
    await userEvent.click(within(kpis).getByRole('button', { name: /^Tasks que passariam a atrasar/ }))
    const dialogo = await screen.findByRole('dialog')
    expect(await within(dialogo).findByText('1 tasks')).toBeInTheDocument()
    const url = String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/SI-01/tasks'))?.[0])
    expect(url).toContain('segmento=atrasosCausados')
    // A gaveta leva os mesmos parâmetros da simulação (o número do cartão é o total dela).
    expect(url).toContain('pessoa=bruno')
    expect(url).toContain('de=2026-11-03')
  })

  it('erro400MostraFiltroInvalido', async () => {
    simular({ [CARD]: { status: 400 } })
    renderizarTela(<Simulador />, '/simulador', '/simulador?pessoa=bruno&de=2026-11-03&ate=2027-01-30')
    expect(await screen.findByText(/filtro é inválido/i)).toBeInTheDocument()
  })
})
