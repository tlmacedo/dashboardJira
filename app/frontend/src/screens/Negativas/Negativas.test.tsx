import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gaveta, renderizarTela, simular } from '../../test/telas'
import type { NegativasDto } from './api'
import { Negativas } from './Negativas'

const DTO: NegativasDto = {
  reabertas: 1,
  devolvidas: 1,
  canceladas: 1,
  concluidasComAtraso: 2,
  atrasadasSemJustificativa: 1,
  abertasVencidas: 1,
  estouroDeEstimativa: 1,
  muitasTrocas: 1,
  concluidas: 4,
  taxaReabertura: 0.25,
  taxaDevolucao: 0.25,
  taxaAtraso: 0.5,
  taxaAtrasoSemJustificativa: 0.25,
  limiteDaTaxa: 0.2,
  porResponsavel: [
    { pessoa: 'ana', reabertas: 1, devolvidas: 0, atrasadas: 1 },
    { pessoa: 'carla', reabertas: 0, devolvidas: 1, atrasadas: 1 },
  ],
  unit: 'util',
  avisos: [],
}

const CARD = '/api/cards/negativas'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Negativas', () => {
  it('negativasTaxaAcimaDe20ComTriangulo', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Negativas />, '/negativas')
    const taxas = await screen.findByRole('group', { name: 'Taxas sobre as concluídas' })
    const reabertura = within(taxas).getByText('Taxa de reabertura').closest('article') as HTMLElement
    // Símbolo + texto + número: nunca só cor.
    expect(reabertura).toHaveTextContent('25%')
    expect(reabertura).toHaveTextContent(/▲\s*Acima de 20%/)
    expect(reabertura).toHaveTextContent('1 de 4 concluídas')
  })

  it('taxaAte20FicaOkComTexto', async () => {
    simular({ [CARD]: { status: 200, corpo: { ...DTO, taxaReabertura: 0.2, reabertas: 1, concluidas: 5 } } })
    renderizarTela(<Negativas />, '/negativas')
    const taxas = await screen.findByRole('group', { name: 'Taxas sobre as concluídas' })
    const reabertura = within(taxas).getByText('Taxa de reabertura').closest('article') as HTMLElement
    expect(reabertura).toHaveTextContent(/✓\s*Até 20%/)
  })

  it('negativasAtrasadasEAtrasadasSemJustificativa', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Negativas />, '/negativas')
    const kpis = await screen.findByRole('group', { name: 'Indicadores das negativas' })
    const atraso = within(kpis).getByRole('button', { name: /^Concluídas com atraso/ })
    const semJustificativa = within(kpis).getByRole('button', { name: /^Atraso sem justificativa/ })
    expect(atraso).toHaveTextContent('2')
    expect(semJustificativa).toHaveTextContent('1')
  })

  it('semConcluidasMostraTracoNasTaxas', async () => {
    simular({ [CARD]: { status: 200, corpo: { ...DTO, concluidas: 0, taxaReabertura: null, taxaDevolucao: null, taxaAtraso: null, taxaAtrasoSemJustificativa: null } } })
    renderizarTela(<Negativas />, '/negativas')
    const taxas = await screen.findByRole('group', { name: 'Taxas sobre as concluídas' })
    const reabertura = within(taxas).getByText('Taxa de reabertura').closest('article') as HTMLElement
    expect(reabertura).toHaveTextContent('—')
    expect(reabertura).toHaveTextContent('Sem concluídas no período')
  })

  it('kpiAbreGavetaComMesmoTamanho', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/NE-01/tasks': { status: 200, corpo: gaveta(1, 'card.ne01.titulo') } })
    renderizarTela(<Negativas />, '/negativas', '/negativas?de=2026-10-01&ate=2026-10-30')
    const kpis = await screen.findByRole('group', { name: 'Indicadores das negativas' })
    await userEvent.click(within(kpis).getByRole('button', { name: /^Reabertas/ }))
    const dialogo = await screen.findByRole('dialog')
    expect(await within(dialogo).findByText('1 tasks')).toBeInTheDocument()
    const url = String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/NE-01/tasks'))?.[0])
    expect(url).toContain('segmento=reabertas')
    expect(url).toContain('de=2026-10-01')
  })

  it('taxaAbreOsNumeradores', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/NE-08/tasks': { status: 200, corpo: gaveta(1, 'card.ne08.titulo') } })
    renderizarTela(<Negativas />, '/negativas')
    const taxas = await screen.findByRole('group', { name: 'Taxas sobre as concluídas' })
    await userEvent.click(within(taxas).getByRole('button', { name: /^Taxa de atraso\b(?! sem)/ }))
    await screen.findByRole('dialog')
    expect(String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/NE-08/tasks'))?.[0])).toContain('segmento=taxa%3Aatraso')
  })

  it('tabelaPorResponsavelAlfabeticaSemColunaDePosicao', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Negativas />, '/negativas')
    const tabela = await screen.findByRole('table', { name: 'Negativas por responsável' })
    expect(within(tabela).getAllByRole('rowheader').map((c) => c.textContent)).toEqual(['ana', 'carla'])
    const cabecalhos = within(tabela).getAllByRole('columnheader').map((c) => c.textContent ?? '')
    expect(cabecalhos.some((c) => /^#$|posição|ranking|melhor|pior/i.test(c))).toBe(false)
    expect(screen.getByText(/Ordem alfabética, sem ranking/)).toBeInTheDocument()
  })

  it('naoOfereSeletorDeUnidadeQueNaoTemEfeito', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Negativas />, '/negativas')
    await screen.findByRole('group', { name: 'Indicadores das negativas' })
    // As contagens são de eventos e de datas de Manaus: dias úteis × corridos não muda nenhum número.
    expect(screen.queryByRole('button', { name: 'Dias corridos' })).toBeNull()
    expect(screen.getByRole('button', { name: 'Aplicar filtros' })).toBeInTheDocument()
  })

  it('avisoBloqueanteMostraTraco', async () => {
    simular({ [CARD]: { status: 200, corpo: { ...DTO, avisos: [{ codigo: 'STATUS_SEM_MAPEAMENTO', gravidade: 'bloqueante', mensagem: 'x', params: { status: 'Exotico' } }] } } })
    renderizarTela(<Negativas />, '/negativas')
    const kpis = await screen.findByRole('group', { name: 'Indicadores das negativas' })
    expect(within(kpis).getByText('Reabertas').closest('article')).toHaveTextContent('—')
    expect(within(kpis).queryAllByRole('button')).toHaveLength(0)
  })

  it('semPermissao403MostraMensagem', async () => {
    simular({ [CARD]: { status: 403 } })
    renderizarTela(<Negativas />, '/negativas')
    expect(await screen.findByRole('alert')).toHaveTextContent(/não permite ver estes dados/i)
  })
})
