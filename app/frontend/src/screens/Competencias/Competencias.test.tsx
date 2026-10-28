import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gaveta, renderizarTela, simular } from '../../test/telas'
import type { CompetenciasDto } from './api'
import { Competencias } from './Competencias'

const DTO: CompetenciasDto = {
  competencias: ['android', 'ios', 'rede', 'ui'],
  pessoas: ['ana', 'bruno', 'carla'],
  celulas: [
    [true, true, false], // android
    [false, false, false], // ios (pedida, ninguém tem)
    [true, false, true], // rede
    [false, false, true], // ui (só a carla)
  ],
  pessoaUnica: ['ui'],
  semNinguem: ['ios'],
  demandaH: { android: 16.8, ios: 6 },
  avisos: [],
  tasksPorCompetencia: { android: 2, ios: 1 },
  tasksSemNinguem: 1,
}
const CARD = '/api/cards/competencias'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Matriz de competências', () => {
  it('competenciaPessoaUnicaComTriangulo', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Competencias />, '/competencias')
    const tabela = await screen.findByRole('table', { name: 'Matriz de competências' })
    // Pessoa única e "ninguém" têm triângulo E texto; a competência com 2 pessoas, ✓ e texto.
    expect(within(tabela).getByRole('rowheader', { name: 'ui' }).closest('tr')).toHaveTextContent('▲ pessoa única')
    expect(within(tabela).getByRole('rowheader', { name: 'ios' }).closest('tr')).toHaveTextContent('▲ ninguém tem')
    expect(within(tabela).getByRole('rowheader', { name: 'android' }).closest('tr')).toHaveTextContent('✓ coberta')
  })

  it('matrizTemPessoasEmOrdemAlfabeticaESimNaoEmTexto', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Competencias />, '/competencias')
    const tabela = await screen.findByRole('table', { name: 'Matriz de competências' })
    const colunas = within(tabela).getAllByRole('columnheader').map((c) => c.textContent)
    expect(colunas).toEqual(['Competência', 'ana', 'bruno', 'carla', 'Situação'])
    const ui = within(tabela).getByRole('rowheader', { name: 'ui' }).closest('tr') as HTMLElement
    // A célula diz "tem" ou "não tem" para o leitor de tela (a marca visual nunca é o único sinal).
    expect(within(ui).getAllByText('não tem')).toHaveLength(2)
    expect(within(ui).getAllByText('tem')).toHaveLength(1)
  })

  it('demandaPorCompetenciaAbreAGavetaComMesmoTamanho', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/CM-03/tasks': { status: 200, corpo: gaveta(2, 'card.cm03.titulo') } })
    renderizarTela(<Competencias />, '/competencias')
    const demanda = await screen.findByRole('table', { name: 'Demanda por competência' })
    expect(within(demanda).getByRole('rowheader', { name: 'android' }).closest('tr')).toHaveTextContent('16,8')
    await userEvent.click(within(demanda).getByRole('button', { name: 'android Tasks abertas: 2' }))
    const dialogo = await screen.findByRole('dialog')
    expect(await within(dialogo).findByText('2 tasks')).toBeInTheDocument()
    expect(decodeURIComponent(String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/CM-03/tasks'))?.[0]))).toContain('segmento=skill:android')
  })

  it('kpiSemNinguemAbreAGaveta', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/CM-02/tasks': { status: 200, corpo: gaveta(1, 'card.cm02.titulo') } })
    renderizarTela(<Competencias />, '/competencias')
    const kpis = await screen.findByRole('group', { name: 'Indicadores das competências' })
    expect(within(kpis).getByText('Competências de pessoa única')).toBeInTheDocument()
    await userEvent.click(within(kpis).getByRole('button', { name: /^Tasks que pedem competência sem ninguém/ }))
    await screen.findByRole('dialog')
    expect(String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/CM-02/tasks'))?.[0])).toContain('segmento=semNinguem')
  })

  it('usuarioComUmaPessoaNaoTemPessoaUnica', async () => {
    simular({
      [CARD]: {
        status: 200,
        corpo: { ...DTO, competencias: ['android', 'rede'], pessoas: ['ana'], celulas: [[true], [true]], pessoaUnica: [], semNinguem: [], demandaH: {}, tasksPorCompetencia: {}, tasksSemNinguem: 0 },
      },
    })
    renderizarTela(<Competencias />, '/competencias')
    const tabela = await screen.findByRole('table', { name: 'Matriz de competências' })
    expect(tabela.textContent).not.toContain('pessoa única')
    expect(within(tabela).getAllByRole('rowheader')).toHaveLength(2)
  })

  it('semCompetenciasMostraMensagem', async () => {
    simular({ [CARD]: { status: 200, corpo: { ...DTO, competencias: [], celulas: [], pessoaUnica: [], semNinguem: [], demandaH: {}, tasksPorCompetencia: {}, tasksSemNinguem: 0 } } })
    renderizarTela(<Competencias />, '/competencias')
    expect(await screen.findByText('Nenhuma competência cadastrada nem pedida.')).toBeInTheDocument()
  })
})
