import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gaveta, renderizarTela, simular } from '../../test/telas'
import type { CapacidadeDto } from './api'
import { Capacidade } from './Capacidade'

const ok = { fracao: 0.4, semCapacidade: false, estado: 'ok' }
const semCap = { fracao: null, semCapacidade: true, estado: 'problema' }

const DTO: CapacidadeDto = {
  versaoRegras: 'planejamento v1',
  alertas: [
    { pessoa: 'ana', tipo: 'AUSENCIA_COM_TRABALHO', semana: '2026-11-02', horasAReplanejar: 5.6 },
    { pessoa: 'bruno', tipo: 'CARGA_ALTA', semana: '2026-10-26', horasAReplanejar: null },
  ],
  atrasosPrevistos: 1, atrasosPorAusencia: 1, jain: 0.8,
  mapa: [
    { pessoa: 'ana', semanas: [{ semana: '2026-10-26', ocupacao: ok, ausente: false }, { semana: '2026-11-02', ocupacao: ok, ausente: false }] },
    { pessoa: 'carla', semanas: [{ semana: '2026-10-26', ocupacao: ok, ausente: false }, { semana: '2026-11-02', ocupacao: semCap, ausente: true }] },
  ],
  ausencias: [{ pessoa: 'ana', de: '2026-11-04', ate: '2026-11-05', fracao: 1 }],
  feriados: [{ dia: '2026-11-02', nome: 'Finados' }],
  listaAtrasos: [{ key: 'TG-202', pessoa: 'ana', vence: '2026-11-05', termino: '2026-11-06', causa: 'AUSENCIA' }],
  avisos: [],
}
const CARD = '/api/cards/capacidade'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Capacidade e ausências', () => {
  it('mapaAusenteMostraAus', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Capacidade />, '/capacidade')
    const mapa = await screen.findByRole('table', { name: 'Mapa de capacidade (8 semanas)' })
    const carla = within(mapa).getByRole('rowheader', { name: 'carla' }).closest('tr') as HTMLElement
    // A semana inteira de ausência é o texto "aus." (com ▓), nunca uma porcentagem.
    expect(carla).toHaveTextContent('aus.')
    expect(carla.textContent).not.toMatch(/990|%.*%.*%/)
    expect(within(mapa).getAllByRole('rowheader').map((c) => c.textContent)).toEqual(['ana', 'carla'])
  })

  it('alertasEmTextoComHorasAReplanejar', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Capacidade />, '/capacidade')
    expect(await screen.findByText(/ausência com trabalho agendado na semana de 02\/11\/2026: cerca de 5,6 h a replanejar/)).toBeInTheDocument()
    expect(screen.getByText(/carga acima do limite na semana de 26\/10\/2026/)).toBeInTheDocument()
  })

  it('ausenciasSemMotivoEFeriadoComNome', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Capacidade />, '/capacidade')
    const aus = await screen.findByRole('table', { name: 'Ausências planejadas' })
    const cabecalhos = within(aus).getAllByRole('columnheader').map((c) => c.textContent ?? '')
    expect(cabecalhos.some((c) => /motivo/i.test(c))).toBe(false)
    expect(screen.getByText(/02\/11\/2026: Finados/)).toBeInTheDocument()
    expect(screen.getByText(/não guarda nem mostra o motivo/)).toBeInTheDocument()
  })

  it('atrasoComCausaEmTexto', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Capacidade />, '/capacidade')
    const tabela = await screen.findByRole('table', { name: 'Atrasos previstos pela capacidade' })
    const linha = within(tabela).getByRole('rowheader', { name: 'TG-202' }).closest('tr') as HTMLElement
    expect(linha).toHaveTextContent(/▲\s*Ausência/)
    expect(linha).toHaveTextContent('06/11/2026')
  })

  it('kpiDeAtrasosAbreGavetaComMesmoTamanho', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/CA-02/tasks': { status: 200, corpo: gaveta(1, 'card.ca02.titulo') } })
    renderizarTela(<Capacidade />, '/capacidade')
    const kpis = await screen.findByRole('group', { name: 'Indicadores da capacidade' })
    await userEvent.click(within(kpis).getByRole('button', { name: /^Atrasos previstos/ }))
    const dialogo = await screen.findByRole('dialog')
    expect(await within(dialogo).findByText('1 tasks')).toBeInTheDocument()
    expect(String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/CA-02/tasks'))?.[0])).toContain('segmento=atrasosPrevistos')
  })

  it('usuarioComumVe403', async () => {
    simular({ [CARD]: { status: 403 } })
    renderizarTela(<Capacidade />, '/capacidade')
    expect(await screen.findByText(/Seu papel não permite ver estes dados/)).toBeInTheDocument()
  })
})
