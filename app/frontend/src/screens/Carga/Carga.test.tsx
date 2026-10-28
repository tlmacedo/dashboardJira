import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gaveta, renderizarTela, simular } from '../../test/telas'
import type { CargaDto, LinhaCarga } from './api'
import { Carga } from './Carga'

const ok = { fracao: 0.25, semCapacidade: false, estado: 'ok' }

const ANA: LinhaCarga = {
  pessoa: 'ana', abertas: 2, wip: 1, filaH: 16.8, capacidade4SemH: 67.2, ocupacao: ok, ausenciaDias: 2, horasRegistradas30d: null,
  acimaDoP85: 0, vencidas: 0, riscoAlto: 0, atrasoAcumuladoH: 0, horasAssumidas: false,
}
const BRUNO: LinhaCarga = {
  ...ANA, pessoa: 'bruno', abertas: 1, wip: 0, filaH: 4, capacidade4SemH: 0, ocupacao: { fracao: null, semCapacidade: true, estado: 'problema' },
  ausenciaDias: 0, horasAssumidas: false,
}
const CARLA: LinhaCarga = {
  ...ANA, pessoa: 'carla', abertas: 1, wip: 0, filaH: 6, ocupacao: { fracao: 1.5, semCapacidade: false, estado: 'problema' }, vencidas: 1,
  atrasoAcumuladoH: 6, horasAssumidas: true,
}

const DTO: CargaDto = {
  versaoRegras: 'planejamento v1', jain: 0.72, acimaDaCapacidade: 1, comFolga: 1, tasksAcimaDaCapacidade: 1, tasksComFolga: 2,
  linhas: [ANA, BRUNO, CARLA], avisos: [],
}
const CARD = '/api/cards/carga'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Carga por pessoa', () => {
  it('cargaSemCapacidadeEmTexto', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Carga />, '/carga')
    const tabela = await screen.findByRole('table', { name: 'Carga por pessoa' })
    const linha = within(tabela).getByRole('rowheader', { name: 'bruno' }).closest('tr') as HTMLElement
    // "sem capacidade" é texto, com a demanda ao lado; nunca uma porcentagem.
    expect(linha).toHaveTextContent('sem capacidade')
    expect(linha).toHaveTextContent('4,0 h planejadas')
    expect(linha.textContent).not.toMatch(/\d+%/)
    // Quem tem capacidade mostra número e símbolo.
    const ana = within(tabela).getByRole('rowheader', { name: 'ana' }).closest('tr') as HTMLElement
    expect(ana).toHaveTextContent(/✓\s*25%/)
    expect(within(tabela).getByRole('rowheader', { name: 'carla' }).closest('tr')).toHaveTextContent(/▲\s*150%/)
  })

  it('cargaSemColunaDePosicao', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Carga />, '/carga')
    const tabela = await screen.findByRole('table', { name: 'Carga por pessoa' })
    expect(within(tabela).getAllByRole('rowheader').map((c) => c.textContent)).toEqual(['ana', 'bruno', 'carla'])
    const cabecalhos = within(tabela).getAllByRole('columnheader').map((c) => c.textContent ?? '')
    expect(cabecalhos.some((c) => /^#$|posição|ranking|melhor|pior|maior|menor/i.test(c))).toBe(false)
    expect(screen.getByText('Ordem alfabética, sem ranking.')).toBeInTheDocument()
    // Contagens no lugar de "maior e menor ocupação".
    const kpis = screen.getByRole('group', { name: 'Indicadores da carga' })
    expect(within(kpis).getByText('Pessoas acima da capacidade')).toBeInTheDocument()
    expect(within(kpis).getByText('Pessoas com folga')).toBeInTheDocument()
  })

  it('cargaUsuarioUmaLinhaSemJain', async () => {
    simular({ [CARD]: { status: 200, corpo: { ...DTO, jain: null, acimaDaCapacidade: 0, comFolga: 1, linhas: [ANA] } } })
    renderizarTela(<Carga />, '/carga')
    const tabela = await screen.findByRole('table', { name: 'Carga por pessoa' })
    expect(within(tabela).getAllByRole('rowheader')).toHaveLength(1)
    const kpis = screen.getByRole('group', { name: 'Indicadores da carga' })
    expect(within(kpis).getByText('Indisponível com menos de 2 pessoas com capacidade')).toBeInTheDocument()
    expect(within(kpis).queryByText('0,72')).toBeNull()
  })

  it('jainComDuasCasas', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Carga />, '/carga')
    const kpis = await screen.findByRole('group', { name: 'Indicadores da carga' })
    expect(within(kpis).getByText('0,72')).toBeInTheDocument()
  })

  it('horasAssumidasMarcadasEAtrasoAcumulado', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Carga />, '/carga')
    const tabela = await screen.findByRole('table', { name: 'Carga por pessoa' })
    const carla = within(tabela).getByRole('rowheader', { name: 'carla' }).closest('tr') as HTMLElement
    expect(within(carla).getByLabelText(/Parte das horas é assumida/)).toBeInTheDocument()
    expect(carla).toHaveTextContent('6,0')
  })

  it('numeroDaTabelaAbreGavetaDaPessoa', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/CP-03/tasks': { status: 200, corpo: gaveta(2, 'card.cp03.titulo') } })
    renderizarTela(<Carga />, '/carga')
    await userEvent.click(await screen.findByRole('button', { name: 'ana Abertas: 2' }))
    const dialogo = await screen.findByRole('dialog')
    expect(await within(dialogo).findByText('2 tasks')).toBeInTheDocument()
    expect(String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/CP-03/tasks'))?.[0])).toContain('segmento=pessoa%3Aana%3Afila')
  })

  it('kpiAcimaAbreAsTasksNaFila', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/CP-02/tasks': { status: 200, corpo: gaveta(1, 'card.cp02.titulo') } })
    renderizarTela(<Carga />, '/carga')
    const kpis = await screen.findByRole('group', { name: 'Indicadores da carga' })
    await userEvent.click(within(kpis).getByRole('button', { name: /^Pessoas acima da capacidade/ }))
    await screen.findByRole('dialog')
    expect(String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/CP-02/tasks'))?.[0])).toContain('segmento=acimaDaCapacidade')
  })

  it('semPermissaoMostraMensagem', async () => {
    simular({ [CARD]: { status: 403 } })
    renderizarTela(<Carga />, '/carga')
    expect(await screen.findByText(/Seu papel não permite ver estes dados/)).toBeInTheDocument()
  })
})
