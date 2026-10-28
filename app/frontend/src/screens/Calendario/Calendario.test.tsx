import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gaveta, renderizarTela, simular } from '../../test/telas'
import type { CalendarioDto } from './api'
import { Calendario } from './Calendario'

const DTO: CalendarioDto = {
  inicio: '2026-11-02',
  dias: [
    { dia: '2026-11-02', feriado: 'Finados', util: false, capacidadeSquad: null, vencimentos: 0, critico: false },
    { dia: '2026-11-03', feriado: null, util: true, capacidadeSquad: 0.6, vencimentos: 0, critico: false },
    { dia: '2026-11-04', feriado: null, util: true, capacidadeSquad: 0.2, vencimentos: 1, critico: true },
  ],
  linhas: [
    {
      pessoa: 'ana',
      celulas: [
        { dia: '2026-11-02', ausencia: 0, vencimentos: 0 },
        { dia: '2026-11-03', ausencia: 0, vencimentos: 0 },
        { dia: '2026-11-04', ausencia: 1, vencimentos: 1 },
      ],
    },
    {
      pessoa: 'bruno',
      celulas: [
        { dia: '2026-11-02', ausencia: 0, vencimentos: 0 },
        { dia: '2026-11-03', ausencia: 0.5, vencimentos: 0 },
        { dia: '2026-11-04', ausencia: 0, vencimentos: 0 },
      ],
    },
  ],
  avisos: [],
}
const CARD = '/api/cards/calendario'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Calendário do time', () => {
  it('calendarioLegendaComSimbolos', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Calendario />, '/calendario')
    const legenda = await screen.findByRole('note')
    // A legenda explica os símbolos em texto: ▓ ausência, ▒ meio dia, ◆n vencimentos, ▲ dia crítico.
    for (const trecho of ['▓ ausência', '▒ meio dia', '◆n vencimentos', '▲ dia crítico']) expect(legenda).toHaveTextContent(trecho)
  })

  it('feriadoDiaCriticoEAusenciaComTexto', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Calendario />, '/calendario')
    const tabela = await screen.findByRole('table', { name: /Calendário a partir de 02\/11\/2026/ })
    expect(within(tabela).getByRole('columnheader', { name: /02\/11.*Finados/ })).toBeInTheDocument()
    expect(within(tabela).getByRole('columnheader', { name: /04\/11.*dia crítico/ })).toBeInTheDocument()
    const ana = within(tabela).getByRole('rowheader', { name: 'ana' }).closest('tr') as HTMLElement
    expect(ana).toHaveTextContent('ausente')
    expect(ana.textContent).toContain('▓')
    const bruno = within(tabela).getByRole('rowheader', { name: 'bruno' }).closest('tr') as HTMLElement
    expect(bruno).toHaveTextContent('ausente meio dia')
    expect(bruno.textContent).toContain('▒')
    // Capacidade do time por dia: 60% e 20%, e traço no dia não útil.
    const cap = within(tabela).getByRole('rowheader', { name: 'Capacidade do time' }).closest('tr') as HTMLElement
    expect(cap).toHaveTextContent('60%')
    expect(cap).toHaveTextContent('20%')
  })

  it('calendarioCelulaAbreGaveta', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/CL-01/tasks': { status: 200, corpo: gaveta(1, 'card.cl01.titulo') } })
    renderizarTela(<Calendario />, '/calendario', '/calendario?inicio=2026-11-02')
    await userEvent.click(await screen.findByRole('button', { name: 'Vencem em 04/11/2026: 1' }))
    const dialogo = await screen.findByRole('dialog')
    // O total da gaveta é o número do dia (linhas == vencimentos).
    expect(await within(dialogo).findByText('1 tasks')).toBeInTheDocument()
    const chamada = String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/CL-01/tasks'))?.[0])
    expect(chamada).toContain('segmento=dia%3A2026-11-04')
    expect(chamada).toContain('inicio=2026-11-02')
  })

  it('diaSemVencimentoNaoAbreGaveta', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Calendario />, '/calendario')
    await screen.findByRole('table', { name: /Calendário/ })
    expect(screen.queryByRole('button', { name: /Vencem em 03\/11/ })).toBeNull()
  })

  it('alternaSemanaEMesEnavega', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Calendario />, '/calendario')
    await userEvent.click(await screen.findByRole('button', { name: 'Mês' }))
    expect(screen.getByTestId('url').textContent).toContain('modo=mes')
    await userEvent.click(await screen.findByRole('button', { name: /Próximo/ }))
    expect(screen.getByTestId('url').textContent).toContain('inicio=2026-11-30')
  })

  it('usuarioComUmaLinha', async () => {
    simular({ [CARD]: { status: 200, corpo: { ...DTO, linhas: [DTO.linhas[0]] } } })
    renderizarTela(<Calendario />, '/calendario')
    const tabela = await screen.findByRole('table', { name: /Calendário/ })
    expect(within(tabela).queryByRole('rowheader', { name: 'bruno' })).toBeNull()
  })
})
