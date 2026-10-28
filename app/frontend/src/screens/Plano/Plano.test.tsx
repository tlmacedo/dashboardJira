import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gaveta, renderizarTela, simular } from '../../test/telas'
import type { PlanoDto } from './api'
import { Plano } from './Plano'

const ok = { fracao: 0.75, semCapacidade: false, estado: 'ok' }

const DTO: PlanoDto = {
  semanas: ['2026-10-26', '2026-11-02'],
  linhas: [
    {
      pessoa: 'ana',
      celulas: [
        { semana: '2026-10-26', planejadaH: 8.4, capacidadeH: 5.6, ocupacao: { fracao: 1.5, semCapacidade: false, estado: 'problema' }, itens: 2, semDataH: 0 },
        { semana: '2026-11-02', planejadaH: 8.4, capacidadeH: 11.2, ocupacao: ok, itens: 2, semDataH: 2 },
      ],
    },
    {
      pessoa: 'carla',
      celulas: [
        { semana: '2026-10-26', planejadaH: 0, capacidadeH: 5.6, ocupacao: { fracao: 0, semCapacidade: false, estado: 'ok' }, itens: 0, semDataH: 0 },
        { semana: '2026-11-02', planejadaH: 3, capacidadeH: 0, ocupacao: { fracao: null, semCapacidade: true, estado: 'problema' }, itens: 1, semDataH: 0 },
      ],
    },
  ],
  avisos: [],
}
const CARD = '/api/cards/plano'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Plano de 4 a 8 semanas', () => {
  it('planoAlternaQuatroAOitoSemanas', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Plano />, '/plano')
    await screen.findByRole('table', { name: 'Plano por pessoa e semana' })
    const grupo = screen.getByRole('group', { name: 'Número de semanas' })
    expect(within(grupo).getAllByRole('button').map((b) => b.textContent)).toEqual(['4 semanas', '5 semanas', '6 semanas', '7 semanas', '8 semanas'])
    expect(within(grupo).getByRole('button', { name: '4 semanas' })).toHaveAttribute('aria-pressed', 'true')
    await userEvent.click(within(grupo).getByRole('button', { name: '8 semanas' }))
    expect(screen.getByTestId('url').textContent).toContain('semanas=8')
    await vi.waitFor(() => expect(mock.mock.calls.some((c) => String(c[0]).includes('semanas=8'))).toBe(true))
    expect(within(screen.getByRole('group', { name: 'Número de semanas' })).getByRole('button', { name: '8 semanas' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('semCapacidadeEmTextoComHorasPlanejadas', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Plano />, '/plano')
    const tabela = await screen.findByRole('table', { name: 'Plano por pessoa e semana' })
    const carla = within(tabela).getByRole('rowheader', { name: 'carla' }).closest('tr') as HTMLElement
    expect(carla).toHaveTextContent('sem capacidade (3,0 h planejadas)')
    expect(carla.textContent).not.toMatch(/Infinity|NaN|990/)
    const ana = within(tabela).getByRole('rowheader', { name: 'ana' }).closest('tr') as HTMLElement
    expect(ana).toHaveTextContent(/▲\s*150%/)
    expect(ana).toHaveTextContent(/✓\s*75%/)
    expect(ana).toHaveTextContent('2,0 h sem vencimento')
  })

  it('celulaAbreAsTasksDaSemana', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/PL-01/tasks': { status: 200, corpo: gaveta(2, 'card.pl01.titulo') } })
    renderizarTela(<Plano />, '/plano', '/plano?semanas=6')
    await userEvent.click((await screen.findAllByRole('button', { name: 'Tasks de ana na semana de 26/10/2026: 2' }))[0])
    const dialogo = await screen.findByRole('dialog')
    expect(await within(dialogo).findByText('2 tasks')).toBeInTheDocument()
    const chamada = String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/PL-01/tasks'))?.[0])
    expect(chamada).toContain('segmento=celula%3Aana%3A2026-10-26')
    expect(chamada).toContain('semanas=6')
  })

  it('pessoasEmOrdemAlfabeticaSemPosicao', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Plano />, '/plano')
    const tabela = await screen.findByRole('table', { name: 'Plano por pessoa e semana' })
    expect(within(tabela).getAllByRole('rowheader').map((c) => c.textContent)).toEqual(['ana', 'carla'])
  })
})
