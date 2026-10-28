import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gaveta, renderizarTela, simular } from '../../test/telas'
import type { GargalosDto } from './api'
import { Gargalos } from './Gargalos'

const dias = Array.from({ length: 60 }, (_, i) => `2026-09-${String((i % 28) + 1).padStart(2, '0')}`)
const serie = (inicio: number, fim: number) => Array.from({ length: 60 }, (_, i) => (i <= 44 ? inicio : fim))

const DTO: GargalosDto = {
  dias,
  faixas: [
    { status: 'Open', contagens: serie(3, 3) },
    { status: 'In Progress', contagens: serie(5, 13) },
  ],
  etapas: [
    { status: 'Open', categoria: 'TODO', wip: 3, limite: null, crescimento15d: 0, gargalo: false, motivo: null },
    { status: 'In Progress', categoria: 'ACTIVE', wip: 13, limite: 12, crescimento15d: 8, gargalo: true, motivo: 'WIP_ACIMA_DO_LIMITE' },
    { status: 'Submitted', categoria: 'WAIT', wip: 9, limite: null, crescimento15d: 4, gargalo: true, motivo: 'FAIXA_CRESCEU' },
  ],
  acimaDaCapacidade: 1, comFolga: 2, tasksAcimaDaCapacidade: 3, tasksComFolga: 4, avisos: [],
}
const CARD = '/api/cards/gargalos'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Gargalos e fluxo', () => {
  it('gargaloComMotivoEmTexto', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Gargalos />, '/gargalos')
    const tabela = await screen.findByRole('table', { name: 'Estado das etapas' })
    const linha = (s: string) => within(tabela).getByRole('rowheader', { name: s }).closest('tr') as HTMLElement
    expect(linha('In Progress')).toHaveTextContent(/▲\s*Gargalo: acima do limite de WIP/)
    expect(linha('Submitted')).toHaveTextContent(/▲\s*Gargalo: a faixa cresceu nos últimos 15 dias/)
    // A etapa "a fazer" nunca é gargalo.
    expect(linha('Open')).toHaveTextContent(/✓\s*sem gargalo/)
    expect(linha('Open')).toHaveTextContent('A fazer')
  })

  it('fluxoMostraHojeEVariacaoEmTexto', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Gargalos />, '/gargalos')
    expect(await screen.findByText('hoje 13 (+8 em 15 dias)')).toBeInTheDocument()
    expect(screen.getByText('hoje 3 (0 em 15 dias)')).toBeInTheDocument()
  })

  it('etapaAbreAsTasksDoStatus', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/GA-02/tasks': { status: 200, corpo: gaveta(13, 'card.ga02.titulo') } })
    renderizarTela(<Gargalos />, '/gargalos')
    await userEvent.click(await screen.findByRole('button', { name: 'In Progress Tasks agora: 13' }))
    const dialogo = await screen.findByRole('dialog')
    expect(await within(dialogo).findByText('13 tasks')).toBeInTheDocument()
    expect(String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/GA-02/tasks'))?.[0])).toContain('segmento=status%3AIn+Progress')
  })

  it('contagensDePessoasSemRanking', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/GA-04/tasks': { status: 200, corpo: gaveta(4, 'card.ga04.titulo') } })
    renderizarTela(<Gargalos />, '/gargalos')
    const kpis = await screen.findByRole('group', { name: 'Indicadores dos gargalos' })
    expect(within(kpis).getByText('4 tasks na fila delas')).toBeInTheDocument()
    await userEvent.click(within(kpis).getByRole('button', { name: /^Pessoas com folga/ }))
    await screen.findByRole('dialog')
    expect(String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/GA-04/tasks'))?.[0])).toContain('segmento=comFolga')
  })

  it('usuarioComumVe403', async () => {
    simular({ [CARD]: { status: 403 } })
    renderizarTela(<Gargalos />, '/gargalos')
    expect(await screen.findByText(/Seu papel não permite ver estes dados/)).toBeInTheDocument()
  })
})
