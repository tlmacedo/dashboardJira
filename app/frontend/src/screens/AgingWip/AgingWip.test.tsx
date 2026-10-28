import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gaveta, renderizarTela, simular } from '../../test/telas'
import type { AgingWipDto } from './api'
import { AgingWip } from './AgingWip'

const DTO: AgingWipDto = {
  etapas: [
    {
      status: 'In Progress', categoria: 'ACTIVE', total: 3, acima: 1, historicoN: 7, faixa: { p50: 4, p85: 9.4, n: 7 },
      pontos: [{ key: 'TG-101', idade: 29, acima: true }, { key: 'TG-111', idade: 6, acima: false }, { key: 'TG-112', idade: 2, acima: false }],
    },
    {
      status: 'Submitted', categoria: 'WAIT', total: 0, acima: 0, historicoN: 6, faixa: { p50: 1, p85: 1, n: 6 }, pontos: [],
    },
    {
      status: 'On Hold', categoria: 'BLOCKED', total: 1, acima: 0, historicoN: 0, faixa: null, pontos: [{ key: 'TG-103', idade: 4, acima: false }],
    },
  ],
  emAndamento: 4,
  acimaDoNormal: 1,
  unit: 'util',
  avisos: [{ codigo: 'AMOSTRA_PEQUENA', gravidade: 'informativo', mensagem: 'x', params: { tipo: 'On Hold', n: '0', minimo: '5' } }],
}

const CARD = '/api/cards/aging-wip'
const RESUMO = 'Resumo por etapa'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Envelhecimento por etapa', () => {
  it('etapasNaOrdemDoFluxoComEspeEBloqueioProprios', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<AgingWip />, '/agingWip')
    const tabela = await screen.findByRole('table', { name: RESUMO })
    const linhas = within(tabela).getAllByRole('row').slice(1)
    expect(linhas.map((l) => within(l).getAllByRole('rowheader')[0].textContent)).toEqual([
      expect.stringContaining('In Progress'), expect.stringContaining('Submitted'), expect.stringContaining('On Hold'),
    ])
    expect(linhas[1]).toHaveTextContent('espera')
    expect(linhas[2]).toHaveTextContent('bloqueio')
  })

  it('cartoesDeEmAndamentoEAcimaDoNormal', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<AgingWip />, '/agingWip')
    const kpis = await screen.findByRole('group', { name: 'Indicadores do envelhecimento' })
    expect(within(kpis).getByText('Em andamento').closest('article')).toHaveTextContent('4')
    const acima = within(kpis).getByText('Acima do normal').closest('article') as HTMLElement
    expect(acima).toHaveTextContent('1')
    // Símbolo + texto, nunca só cor.
    expect(acima).toHaveTextContent(/▲\s*1 acima do normal/)
  })

  it('nenhumaAcimaDoNormalLevaCheck', async () => {
    simular({ [CARD]: { status: 200, corpo: { ...DTO, acimaDoNormal: 0, etapas: DTO.etapas.map((e) => ({ ...e, acima: 0 })) } } })
    renderizarTela(<AgingWip />, '/agingWip')
    const kpis = await screen.findByRole('group', { name: 'Indicadores do envelhecimento' })
    expect(within(kpis).getByText('Acima do normal').closest('article')).toHaveTextContent(/✓\s*Nenhuma acima do normal/)
  })

  it('pontoAcimaDoNormalTemFormaDeTrianguloETexto', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<AgingWip />, '/agingWip')
    const grafico = await screen.findByRole('group', { name: 'Gráfico de idade por etapa' })
    const acima = within(grafico).getByRole('img', { name: /TG-101/ })
    expect(acima).toHaveAttribute('aria-label', expect.stringContaining('acima do normal'))
    expect(acima.querySelector('[data-forma]')).toHaveAttribute('data-forma', 'triangulo')
    const normal = within(grafico).getByRole('img', { name: /TG-111/ })
    expect(normal.querySelector('[data-forma]')).toHaveAttribute('data-forma', 'circulo')
    expect(normal).toHaveAttribute('aria-label', expect.stringContaining('dentro do normal'))
  })

  it('etapaSemFaixaNaoDesenhaFaixaEAvisa', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<AgingWip />, '/agingWip')
    const tabela = await screen.findByRole('table', { name: RESUMO })
    const bloqueada = within(tabela).getByText(/On Hold/).closest('tr') as HTMLElement
    expect(bloqueada).toHaveTextContent('sem faixa')
    // A etapa com faixa mostra P50 e P85; a sem faixa não desenha banda no gráfico.
    expect(within(tabela).getByText(/In Progress/).closest('tr')).toHaveTextContent(/4,0.*9,4/)
    const grafico = screen.getByRole('group', { name: 'Gráfico de idade por etapa' })
    expect(grafico.querySelectorAll('[data-faixa="In Progress"]')).toHaveLength(1)
    expect(grafico.querySelectorAll('[data-faixa="On Hold"]')).toHaveLength(0)
    expect(screen.getByText(/Poucas tasks de On Hold no histórico \(0 de no mínimo 5\)/)).toBeInTheDocument()
  })

  it('etapaEAcimaAbremAsTasks', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/AW-01/tasks': { status: 200, corpo: gaveta(3, 'card.aw01.titulo') } })
    renderizarTela(<AgingWip />, '/agingWip')
    const tabela = await screen.findByRole('table', { name: RESUMO })
    await userEvent.click(within(tabela).getByRole('button', { name: 'In Progress: 3' }))
    await screen.findByRole('dialog')
    const urls = () => mock.mock.calls.map((c) => String(c[0])).filter((u) => u.startsWith('/api/cartoes/AW-01/tasks'))
    expect(urls()[0]).toContain('segmento=etapa%3AIn+Progress')
    await userEvent.keyboard('{Escape}')
    await userEvent.click(within(tabela).getByRole('button', { name: 'In Progress acima do normal: 1' }))
    await screen.findByRole('dialog')
    expect(urls().at(-1)).toContain('segmento=acima%3AIn+Progress')
    // Etapa sem task agora não tem botão.
    expect(within(tabela).queryByRole('button', { name: /Submitted/ })).toBeNull()
  })

  it('cartaoAcimaDoNormalAbreAsMesmasTasks', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/AW-02/tasks': { status: 200, corpo: gaveta(1, 'card.aw02.titulo') } })
    renderizarTela(<AgingWip />, '/agingWip')
    const kpis = await screen.findByRole('group', { name: 'Indicadores do envelhecimento' })
    await userEvent.click(within(kpis).getByRole('button', { name: /^Acima do normal/ }))
    await screen.findByRole('dialog')
    expect(String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/AW-02/tasks'))?.[0])).toContain('segmento=acimaDoNormal')
  })

  it('tabelaDosPontosNaoTemColunaDePessoa', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<AgingWip />, '/agingWip')
    await userEvent.click(await screen.findByRole('button', { name: 'Ver tasks em tabela' }))
    const pontos = screen.getByRole('table', { name: 'Tasks em andamento por etapa (tabela)' })
    const cabecalhos = within(pontos).getAllByRole('columnheader').map((c) => c.textContent)
    expect(cabecalhos).toEqual(['Etapa', 'Task', 'Idade (dias úteis)', 'Situação'])
    const linha = within(pontos).getByText('TG-101').closest('tr') as HTMLElement
    expect(linha).toHaveTextContent('29,0')
    expect(linha).toHaveTextContent('▲')
    expect(linha).toHaveTextContent('acima do normal')
  })

  it('unitCorridoVaiParaAUrl', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<AgingWip />, '/agingWip')
    await screen.findByRole('group', { name: 'Indicadores do envelhecimento' })
    await userEvent.click(screen.getByRole('button', { name: 'Dias corridos' }))
    await userEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }))
    expect(new URLSearchParams(screen.getByTestId('url').textContent ?? '').get('unit')).toBe('corrido')
    await vi.waitFor(() => {
      const chamadas = mock.mock.calls.map((c) => String(c[0])).filter((u) => u.startsWith(CARD))
      expect(chamadas.at(-1)).toContain('unit=corrido')
    })
  })

  it('avisoBloqueanteMostraTracoENadaClicavel', async () => {
    const aviso = { codigo: 'STATUS_SEM_MAPEAMENTO', gravidade: 'bloqueante', mensagem: 'x', params: { status: 'Exotico' } }
    simular({ [CARD]: { status: 200, corpo: { ...DTO, avisos: [aviso] } } })
    renderizarTela(<AgingWip />, '/agingWip')
    const kpis = await screen.findByRole('group', { name: 'Indicadores do envelhecimento' })
    expect(within(kpis).getByText('Em andamento').closest('article')).toHaveTextContent('—')
    expect(within(kpis).queryAllByRole('button')).toHaveLength(0)
    expect(within(screen.getByRole('table', { name: RESUMO })).queryAllByRole('button')).toHaveLength(0)
  })

  it('semPermissao403MostraMensagem', async () => {
    simular({ [CARD]: { status: 403 } })
    renderizarTela(<AgingWip />, '/agingWip')
    expect(await screen.findByRole('alert')).toHaveTextContent(/não permite ver estes dados/i)
  })
})
