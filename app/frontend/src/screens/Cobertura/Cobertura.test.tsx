import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gaveta, renderizarTela, simular } from '../../test/telas'
import type { CoberturaDto } from './api'
import { Cobertura } from './Cobertura'

const DTO: CoberturaDto = {
  dias: 30,
  avisos: [],
  ausencias: [
    {
      pessoa: 'ana', de: '2026-11-04', ate: '2026-11-05', issues: ['TG-202'], horasAReplanejar: 5.6, competencias: ['android', 'rede'],
      // Alfabética (bia, caio, davi), com a folga fora de ordem (2, 32, 24).
      podemCobrir: [
        { pessoa: 'bia', folgaH: 2, temTodasAsCompetencias: true },
        { pessoa: 'caio', folgaH: 32, temTodasAsCompetencias: false },
        { pessoa: 'davi', folgaH: 24, temTodasAsCompetencias: true },
      ],
    },
    { pessoa: 'bruno', de: '2026-11-10', ate: '2026-11-10', issues: [], horasAReplanejar: 0, competencias: [], podemCobrir: [] },
    { pessoa: 'carla', de: '2026-11-02', ate: '2026-11-06', issues: ['TG-205'], horasAReplanejar: 0.4, competencias: [], podemCobrir: [] },
  ],
}
const CARD = '/api/cards/cobertura'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Cobertura por ausência', () => {
  it('coberturaCandidatosAlfabeticos', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Cobertura />, '/cobertura')
    const ana = (await screen.findByRole('article', { name: /ana/ })) as HTMLElement
    const lista = within(ana).getByRole('list', { name: 'Podem cobrir' })
    const itens = within(lista).getAllByRole('listitem').map((i) => i.textContent ?? '')
    // Ordem alfabética, com a folga ao lado (sem ordenar por folga) e o texto da competência.
    expect(itens.map((i) => i.split(' ')[0])).toEqual(['bia', 'caio', 'davi'])
    expect(itens[0]).toContain('folga 2,0 h')
    expect(itens[1]).toContain('folga 32,0 h')
    expect(itens[1]).toContain('● falta competência')
    expect(itens[2]).toContain('✓ tem as competências')
    expect(screen.getByText(/ordem alfabética, sem ordenar por folga/i)).toBeInTheDocument()
  })

  it('ausenciaMostraHorasCompetenciasEPeriodo', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Cobertura />, '/cobertura')
    const ana = (await screen.findByRole('article', { name: /ana/ })) as HTMLElement
    expect(ana).toHaveTextContent('04/11/2026 a 05/11/2026')
    expect(ana).toHaveTextContent('Horas a replanejar: 5,6 h')
    expect(ana).toHaveTextContent('Competências pedidas: android, rede')
    // A ausência não tem motivo na tela.
    expect(document.body.textContent?.toLowerCase()).not.toContain('motivo')
  })

  it('semTrabalhoAfetadoEhDito', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Cobertura />, '/cobertura')
    const bruno = (await screen.findByRole('article', { name: /bruno/ })) as HTMLElement
    expect(bruno).toHaveTextContent('Sem trabalho a cobrir neste período.')
    const carla = screen.getByRole('article', { name: /carla/ })
    expect(carla).toHaveTextContent('Ninguém do squad tem folga nessas semanas.')
  })

  it('tasksAfetadasAbremAGavetaComMesmoTamanho', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/CB-01/tasks': { status: 200, corpo: gaveta(1, 'card.cb01.titulo') } })
    renderizarTela(<Cobertura />, '/cobertura')
    const ana = (await screen.findByRole('article', { name: /ana/ })) as HTMLElement
    await userEvent.click(within(ana).getByRole('button', { name: /1 task\(s\) afetada\(s\)/ }))
    const dialogo = await screen.findByRole('dialog')
    expect(await within(dialogo).findByText('1 tasks')).toBeInTheDocument()
    const url = decodeURIComponent(String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/CB-01/tasks'))?.[0]))
    expect(url).toContain('segmento=ausencia:ana:2026-11-04')
  })

  it('horizonteVaiNaUrl', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Cobertura />, '/cobertura')
    await screen.findByRole('article', { name: /ana/ })
    await userEvent.click(screen.getByRole('button', { name: '60 dias' }))
    expect(screen.getByTestId('url').textContent).toContain('dias=60')
    await vi.waitFor(() => expect(mock.mock.calls.some((c) => String(c[0]).includes('dias=60'))).toBe(true))
  })

  it('semAusenciasMostraMensagem', async () => {
    simular({ [CARD]: { status: 200, corpo: { ...DTO, ausencias: [] } } })
    renderizarTela(<Cobertura />, '/cobertura')
    expect(await screen.findByText('Nenhuma ausência no horizonte.')).toBeInTheDocument()
  })
})
