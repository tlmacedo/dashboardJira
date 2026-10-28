import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gaveta, renderizarTela, simular } from '../../test/telas'
import type { RevisoesDto } from './api'
import { Revisoes } from './Revisoes'

const DTO: RevisoesDto = {
  semRevisor: 2,
  avisos: [],
  linhas: [
    { revisor: 'ana', pendentes: 1, vencidas: 0, foraDoSla: 0, idadeP50: 2 },
    { revisor: 'bruno', pendentes: 2, vencidas: 1, foraDoSla: 1, idadeP50: 2.5 },
    { revisor: 'carla', pendentes: 1, vencidas: 1, foraDoSla: 0, idadeP50: 1 },
  ],
}
const CARD = '/api/cards/revisoes'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Revisões pendentes por revisor', () => {
  it('revisoesForaDoSlaComTexto', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Revisoes />, '/revisoes')
    const tabela = await screen.findByRole('table', { name: 'Revisões pendentes por revisor' })
    const linha = (p: string) => within(tabela).getByRole('rowheader', { name: p }).closest('tr') as HTMLElement
    // Fora do prazo: triângulo E texto; vencida sem estar fora do prazo: círculo e texto; em dia: ✓.
    expect(linha('bruno')).toHaveTextContent('▲ fora do prazo')
    expect(linha('carla')).toHaveTextContent('● vencida')
    expect(linha('ana')).toHaveTextContent('✓ em dia')
    expect(linha('bruno')).toHaveTextContent('2,5')
  })

  it('revisoresEmOrdemAlfabeticaSemPosicao', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Revisoes />, '/revisoes')
    const tabela = await screen.findByRole('table', { name: 'Revisões pendentes por revisor' })
    expect(within(tabela).getAllByRole('rowheader').map((c) => c.textContent)).toEqual(['ana', 'bruno', 'carla'])
    const cabecalhos = within(tabela).getAllByRole('columnheader').map((c) => c.textContent ?? '')
    expect(cabecalhos.some((c) => /^#$|posição|ranking|melhor|pior|maior|menor/i.test(c))).toBe(false)
  })

  it('contagemAbreAGavetaComMesmoTamanho', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/RV-01/tasks': { status: 200, corpo: gaveta(1, 'card.rv01.titulo') } })
    renderizarTela(<Revisoes />, '/revisoes')
    const tabela = await screen.findByRole('table', { name: 'Revisões pendentes por revisor' })
    await userEvent.click(within(tabela).getByRole('button', { name: 'bruno Fora do prazo: 1' }))
    const dialogo = await screen.findByRole('dialog')
    expect(await within(dialogo).findByText('1 tasks')).toBeInTheDocument()
    expect(decodeURIComponent(String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/RV-01/tasks'))?.[0]))).toContain('segmento=revisor:bruno:foraDoSla')
  })

  it('semRevisorSoAparecePraQuemRecebe', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/RV-02/tasks': { status: 200, corpo: gaveta(2, 'card.rv02.titulo') } })
    renderizarTela(<Revisoes />, '/revisoes')
    const kpis = await screen.findByRole('group', { name: 'Indicadores das revisões' })
    expect(within(kpis).getByRole('button', { name: /^Reviews sem revisor/ })).toBeInTheDocument()
    cleanup()
    simular({ [CARD]: { status: 200, corpo: { ...DTO, semRevisor: null } } })
    renderizarTela(<Revisoes />, '/revisoes')
    await screen.findByRole('table', { name: 'Revisões pendentes por revisor' })
    expect(screen.queryByText('Reviews sem revisor')).toBeNull()
  })

  it('semReviewsMostraMensagem', async () => {
    simular({ [CARD]: { status: 200, corpo: { ...DTO, linhas: [], semRevisor: null } } })
    renderizarTela(<Revisoes />, '/revisoes')
    expect(await screen.findByText('Nenhuma review pendente.')).toBeInTheDocument()
  })
})
