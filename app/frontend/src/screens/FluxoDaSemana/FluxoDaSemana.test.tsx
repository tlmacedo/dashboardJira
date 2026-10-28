import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gaveta, renderizarTela, simular } from '../../test/telas'
import type { FluxoDaSemanaDto } from './api'
import { FluxoDaSemana } from './FluxoDaSemana'

const DTO: FluxoDaSemanaDto = {
  semanas: [
    { semana: '2026-10-05', entrou: 3, comecou: 4, saiu: 1, canceladas: 0, emCurso: false },
    { semana: '2026-10-12', entrou: 0, comecou: 1, saiu: 2, canceladas: 0, emCurso: false },
    { semana: '2026-10-19', entrou: 1, comecou: 1, saiu: 1, canceladas: 1, emCurso: false },
    { semana: '2026-10-26', entrou: 1, comecou: 0, saiu: 0, canceladas: 0, emCurso: true },
  ],
  entrou: 5,
  comecou: 6,
  saiu: 4,
  canceladas: 1,
  saldoDoBacklog: 5,
  semanasCrescendo: 0,
  backlogCrescendo: false,
  semanasNaJanela: 12,
  tasksNaJanela: 10,
  avisos: [],
}

const CARD = '/api/cards/fluxo-da-semana'
const GRAFICO = 'Gráfico do fluxo por semana'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Fluxo da semana', () => {
  it('totaisDaJanelaNosCartoes', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<FluxoDaSemana />, '/fluxoDaSemana')
    const kpis = await screen.findByRole('group', { name: 'Indicadores do fluxo' })
    const cartao = (t: string) => within(kpis).getByText(t).closest('article') as HTMLElement
    expect(cartao('Entraram')).toHaveTextContent('5')
    expect(cartao('Começaram')).toHaveTextContent('6')
    expect(cartao('Saíram')).toHaveTextContent('4')
    expect(cartao('Canceladas')).toHaveTextContent('1')
    expect(cartao('Saldo do backlog')).toHaveTextContent('+5')
  })

  it('sinalDeBacklogCrescendoTemSimboloETexto', async () => {
    simular({ [CARD]: { status: 200, corpo: { ...DTO, semanasCrescendo: 3, backlogCrescendo: true } } })
    renderizarTela(<FluxoDaSemana />, '/fluxoDaSemana')
    const kpis = await screen.findByRole('group', { name: 'Indicadores do fluxo' })
    const saldo = within(kpis).getByText('Saldo do backlog').closest('article') as HTMLElement
    // Símbolo + texto, nunca só cor.
    expect(saldo).toHaveTextContent(/▲\s*Backlog crescendo há 3 semanas/)
  })

  it('semCrescimentoSeguidoLevaCheck', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<FluxoDaSemana />, '/fluxoDaSemana')
    const kpis = await screen.findByRole('group', { name: 'Indicadores do fluxo' })
    expect(within(kpis).getByText('Saldo do backlog').closest('article')).toHaveTextContent(/✓\s*Sem crescimento seguido/)
  })

  it('saldoNegativoEZeroSemMais', async () => {
    simular({ [CARD]: { status: 200, corpo: { ...DTO, saldoDoBacklog: -3 } } })
    renderizarTela(<FluxoDaSemana />, '/fluxoDaSemana')
    const kpis = await screen.findByRole('group', { name: 'Indicadores do fluxo' })
    expect(within(kpis).getByText('Saldo do backlog').closest('article')).toHaveTextContent(/[-−]3/)
  })

  it('barraAbreAsTasksDaSemanaEDaSerie', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/FX-01/tasks': { status: 200, corpo: gaveta(3, 'card.fx01.titulo') } })
    renderizarTela(<FluxoDaSemana />, '/fluxoDaSemana')
    const grafico = await screen.findByRole('group', { name: GRAFICO })
    await userEvent.click(within(grafico).getByRole('button', { name: 'Entraram 05/10/2026: 3' }))
    const dialogo = await screen.findByRole('dialog')
    expect(await within(dialogo).findByText('3 tasks')).toBeInTheDocument()
    expect(String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/FX-01/tasks'))?.[0])).toContain('segmento=entrada%3A2026-10-05')
  })

  it('barraTambemAbrePeloTeclado', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/FX-01/tasks': { status: 200, corpo: gaveta(2, 'card.fx01.titulo') } })
    renderizarTela(<FluxoDaSemana />, '/fluxoDaSemana')
    const grafico = await screen.findByRole('group', { name: GRAFICO })
    within(grafico).getByRole('button', { name: 'Saíram 12/10/2026: 2' }).focus()
    await userEvent.keyboard('{Enter}')
    await screen.findByRole('dialog')
    expect(String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/FX-01/tasks'))?.[0])).toContain('segmento=saida%3A2026-10-12')
  })

  it('semanaSemTasksNaoTemBarraClicavel', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<FluxoDaSemana />, '/fluxoDaSemana')
    const grafico = await screen.findByRole('group', { name: GRAFICO })
    expect(within(grafico).queryByRole('button', { name: /Entraram 12\/10\/2026/ })).toBeNull()
    expect(within(grafico).queryByRole('button', { name: /Saíram 26\/10\/2026/ })).toBeNull()
  })

  it('legendaDiferenciaAsSeriesPorPadraoENaoSoPorCor', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<FluxoDaSemana />, '/fluxoDaSemana')
    const grafico = await screen.findByRole('group', { name: GRAFICO })
    const legenda = within(grafico).getByRole('list', { name: 'Legenda' })
    const itens = within(legenda).getAllByRole('listitem')
    expect(itens.map((i) => i.textContent?.trim())).toEqual(['Entraram', 'Começaram', 'Saíram'])
    // Cheio, vazado e hachurado: três formas de preenchimento distintas.
    const padroes = itens.map((i) => i.querySelector('[data-padrao]')?.getAttribute('data-padrao'))
    expect(padroes).toEqual(['cheio', 'vazado', 'hachurado'])
  })

  it('tabelaEquivalenteComOsMesmosNumerosEAbreOCancelada', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/FX-01/tasks': { status: 200, corpo: gaveta(1, 'card.fx01.titulo') } })
    renderizarTela(<FluxoDaSemana />, '/fluxoDaSemana')
    await screen.findByRole('group', { name: GRAFICO })
    const ver = screen.getByRole('button', { name: 'Ver tabela' })
    expect(ver).toHaveAttribute('aria-expanded', 'false')
    await userEvent.click(ver)
    expect(screen.getByRole('button', { name: 'Ocultar tabela' })).toHaveAttribute('aria-expanded', 'true')
    const tabela = screen.getByRole('table', { name: 'Fluxo por semana (tabela)' })
    const linha = within(tabela).getByText('05/10/2026').closest('tr') as HTMLElement
    expect(linha).toHaveTextContent(/3\s*4\s*1\s*0/)
    // A semana em curso vem marcada em texto.
    expect(within(tabela).getByText('26/10/2026').closest('tr')).toHaveTextContent('em curso')
    await userEvent.click(within(tabela).getByRole('button', { name: 'Canceladas 19/10/2026: 1' }))
    await screen.findByRole('dialog')
    expect(String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/FX-01/tasks'))?.[0])).toContain('segmento=cancelada%3A2026-10-19')
  })

  it('totalDoCartaoAbreAsMesmasTasks', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/FX-02/tasks': { status: 200, corpo: gaveta(5, 'card.fx02.titulo') } })
    renderizarTela(<FluxoDaSemana />, '/fluxoDaSemana')
    const kpis = await screen.findByRole('group', { name: 'Indicadores do fluxo' })
    await userEvent.click(within(kpis).getByRole('button', { name: /^Entraram/ }))
    await screen.findByRole('dialog')
    expect(String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/FX-02/tasks'))?.[0])).toContain('segmento=entradas')
  })

  it('amostraPequenaVemComoAviso', async () => {
    const aviso = { codigo: 'AMOSTRA_PEQUENA_NA_JANELA', gravidade: 'informativo', mensagem: 'x', params: { n: '4', minimo: '5' } }
    simular({ [CARD]: { status: 200, corpo: { ...DTO, avisos: [aviso] } } })
    renderizarTela(<FluxoDaSemana />, '/fluxoDaSemana')
    expect(await screen.findByText(/Poucas tasks na janela \(4 de no mínimo 5\)/)).toBeInTheDocument()
  })

  it('avisoBloqueanteMostraTracoENadaClicavel', async () => {
    const aviso = { codigo: 'STATUS_SEM_MAPEAMENTO', gravidade: 'bloqueante', mensagem: 'x', params: { status: 'Exotico' } }
    simular({ [CARD]: { status: 200, corpo: { ...DTO, avisos: [aviso] } } })
    renderizarTela(<FluxoDaSemana />, '/fluxoDaSemana')
    const kpis = await screen.findByRole('group', { name: 'Indicadores do fluxo' })
    expect(within(kpis).getByText('Entraram').closest('article')).toHaveTextContent('—')
    expect(within(kpis).queryAllByRole('button')).toHaveLength(0)
    expect(within(screen.getByRole('group', { name: GRAFICO })).queryAllByRole('button')).toHaveLength(0)
  })

  it('semPermissao403MostraMensagem', async () => {
    simular({ [CARD]: { status: 403 } })
    renderizarTela(<FluxoDaSemana />, '/fluxoDaSemana')
    expect(await screen.findByRole('alert')).toHaveTextContent(/não permite ver estes dados/i)
  })

  it('filtrosVaoParaAUrlESemUnidade', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<FluxoDaSemana />, '/fluxoDaSemana')
    await screen.findByRole('group', { name: 'Indicadores do fluxo' })
    // O fluxo conta tasks, não tempo: não há seletor de dias úteis/corridos.
    expect(screen.queryByRole('button', { name: 'Dias corridos' })).toBeNull()
    await userEvent.type(screen.getByRole('textbox', { name: 'Squad' }), 'Alfa')
    await userEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }))
    expect(new URLSearchParams(screen.getByTestId('url').textContent ?? '').get('squad')).toBe('Alfa')
    await vi.waitFor(() => {
      const chamadas = mock.mock.calls.map((c) => String(c[0])).filter((u) => u.startsWith(CARD))
      expect(chamadas.at(-1)).toContain('squad=Alfa')
    })
  })
})
