import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gaveta, renderizarTela, simular } from '../../test/telas'
import type { FechadasDto, LinhaFechada } from './api'
import { Fechadas } from './Fechadas'

const linha = (extra: Partial<LinhaFechada>): LinhaFechada => ({
  key: 'TG-111', resumo: 'Ajustar tela', tipo: 'Checklist', responsavel: 'ana', resolvida: '2026-10-09', ciclo: 4, lead: 8,
  vence: '2026-10-09', prazo: 'noPrazo', pontos: 3, horasEstimadas: null, horasGastas: null, ...extra,
})

const DTO: FechadasDto = {
  concluidas: 4,
  pontos: 8,
  comPontos: 2,
  ciclo: { p50: 5, p85: 7.1, n: 4 },
  lead: { p50: 8, p85: 12.3, n: 4 },
  noPrazo: 3,
  noPrazoAjustado: 4,
  comVencimento: 4,
  taxaNoPrazo: 0.75,
  taxaAjustada: 1,
  canceladas: 1,
  porSemana: [
    { semana: '2026-10-05', n: 1 },
    { semana: '2026-10-12', n: 2 },
    { semana: '2026-10-19', n: 1 },
    { semana: '2026-10-26', n: 0 },
  ],
  dispersao: { p50: 5, p85: 7.1, p95: 7.7, pontos: [{ key: 'TG-111', dia: '2026-10-09', ciclo: 4 }] },
  porTipo: [
    { tipo: 'Checklist', p50: 6, p85: 7.4, n: 3 },
    { tipo: 'Checklist Review', p50: 1, p85: 1, n: 1 },
  ],
  porProjeto: [{ chave: 'TG', n: 4 }],
  porPessoa: [
    { pessoa: 'ana', finalizou: 2, participou: 2, pontos: 3, cicloP50: 6, horasEstimadas: null, horasGastas: null },
    { pessoa: 'bruno', finalizou: 1, participou: 1, pontos: 5, cicloP50: 6, horasEstimadas: 10, horasGastas: 14 },
    { pessoa: 'carla', finalizou: 1, participou: 1, pontos: null, cicloP50: 1, horasEstimadas: null, horasGastas: null },
  ],
  linhas: [
    linha({}),
    linha({ key: 'TG-112', responsavel: 'bruno', resolvida: '2026-10-15', ciclo: 6, lead: 13, vence: '2026-10-15', pontos: 5, horasEstimadas: 10, horasGastas: 14 }),
    linha({ key: 'TG-113', resolvida: '2026-10-20', ciclo: 8, lead: 15, vence: '2026-10-16', prazo: 'ajustado', pontos: null }),
    linha({ key: 'TG-114', tipo: 'Checklist Review', responsavel: 'carla', resolvida: '2026-10-14', ciclo: 1, lead: 6, vence: '2026-10-16', pontos: null }),
  ],
  semResolution: 1,
  unit: 'util',
  avisos: [],
}

const CARD = '/api/cards/fechadas'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Fechadas', () => {
  it('fechadasCicloEmUteisEAlternaCorridos', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Fechadas />, '/fechadas')
    const kpis = await screen.findByRole('group', { name: 'Indicadores das fechadas' })
    const ciclo = within(kpis).getByRole('button', { name: /^Ciclo \(dias úteis\)/ })
    expect(ciclo).toHaveTextContent('5,0 / 7,1')
    // "Dias corridos" vai para a URL e a consulta seguinte pede unit=corrido.
    await userEvent.click(screen.getByRole('button', { name: 'Dias corridos' }))
    await userEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }))
    expect(new URLSearchParams(screen.getByTestId('url').textContent ?? '').get('unit')).toBe('corrido')
    await vi.waitFor(() => {
      const chamadas = mock.mock.calls.map((c) => String(c[0])).filter((u) => u.startsWith(CARD))
      expect(chamadas.at(-1)).toContain('unit=corrido')
    })
  })

  it('noPrazoEAjustadoLadoALado', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Fechadas />, '/fechadas')
    const grupo = await screen.findByRole('group', { name: 'Prazo e cancelamentos' })
    const noPrazo = within(grupo).getByRole('button', { name: /^No prazo\b(?! ajustado)/ })
    const ajustado = within(grupo).getByRole('button', { name: /^No prazo ajustado/ })
    expect(noPrazo).toHaveTextContent('75%')
    expect(ajustado).toHaveTextContent('100%')
    expect(noPrazo).toHaveTextContent('3 de 4 com vencimento no período')
  })

  it('kpiAbreGavetaComMesmoTamanho', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/FE-01/tasks': { status: 200, corpo: gaveta(4, 'card.fe01.titulo') } })
    renderizarTela(<Fechadas />, '/fechadas', '/fechadas?de=2026-10-01&ate=2026-10-30')
    const kpis = await screen.findByRole('group', { name: 'Indicadores das fechadas' })
    await userEvent.click(within(kpis).getByRole('button', { name: /^Concluídas/ }))
    const dialogo = await screen.findByRole('dialog')
    expect(await within(dialogo).findByText('4 tasks')).toBeInTheDocument()
    const url = String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/FE-01/tasks'))?.[0])
    expect(url).toContain('segmento=concluidas')
    expect(url).toContain('de=2026-10-01')
  })

  it('semanaAbreOSegmentoDaSemana', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/FE-07/tasks': { status: 200, corpo: gaveta(2, 'card.fe07.titulo') } })
    renderizarTela(<Fechadas />, '/fechadas')
    await userEvent.click(await screen.findByRole('button', { name: '12/10/2026: 2' }))
    await screen.findByRole('dialog')
    expect(String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/FE-07/tasks'))?.[0])).toContain('segmento=semana%3A2026-10-12')
    // a semana sem entrega mostra 0 e não é clicável
    expect(within(screen.getByRole('table', { name: 'Entregas por semana' })).queryByRole('button', { name: '26/10/2026: 0' })).toBeNull()
  })

  it('tabelaPorPessoaAlfabeticaSemColunaDePosicao', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Fechadas />, '/fechadas')
    const tabela = await screen.findByRole('table', { name: 'Entregas por pessoa' })
    expect(within(tabela).getAllByRole('rowheader').map((c) => c.textContent)).toEqual(['ana', 'bruno', 'carla'])
    const cabecalhos = within(tabela).getAllByRole('columnheader').map((c) => c.textContent ?? '')
    expect(cabecalhos.some((c) => /^#$|posição|ranking|melhor|pior/i.test(c))).toBe(false)
    expect(screen.getByText(/Ordem alfabética, sem ranking/)).toBeInTheDocument()
    // Sem pontos ou horas: traço, nunca zero.
    expect(within(tabela).getByText('carla').closest('tr')).toHaveTextContent('—')
  })

  it('situacaoDoPrazoComSimboloETexto', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Fechadas />, '/fechadas')
    const tabela = await screen.findByRole('table', { name: 'Tasks concluídas' })
    const linhaDe = (k: string) => within(tabela).getByText(k).closest('tr') as HTMLElement
    expect(linhaDe('TG-111')).toHaveTextContent(/✓\s*No prazo/)
    expect(linhaDe('TG-113')).toHaveTextContent(/●\s*Ajustado/)
  })

  it('avisoBloqueanteMostraTraco', async () => {
    simular({ [CARD]: { status: 200, corpo: { ...DTO, avisos: [{ codigo: 'STATUS_SEM_MAPEAMENTO', gravidade: 'bloqueante', mensagem: 'x', params: { status: 'Exotico' } }] } } })
    renderizarTela(<Fechadas />, '/fechadas')
    const kpis = await screen.findByRole('group', { name: 'Indicadores das fechadas' })
    expect(within(kpis).getByText('Concluídas').closest('article')).toHaveTextContent('—')
    const prazo = screen.getByRole('group', { name: 'Prazo e cancelamentos' })
    expect(within(prazo).getByText('No prazo').closest('article')).toHaveTextContent('—')
    expect(within(prazo).queryAllByRole('button')).toHaveLength(0)
  })

  it('semPermissao403MostraMensagem', async () => {
    simular({ [CARD]: { status: 403 } })
    renderizarTela(<Fechadas />, '/fechadas')
    expect(await screen.findByRole('alert')).toHaveTextContent(/não permite ver estes dados/i)
  })

  it('controleDoCicloEhUmaSecaoDaTelaECompartilhaOsFiltros', async () => {
    const controle = {
      series: [{ tipo: 'Checklist', n: 1, p50: 4, p85: 4, amostraPequena: true, janela: null, pontos: [{ dia: '2026-10-09', ciclo: 4, mediaMovel: null, outlier: false }], meses: [{ mes: '2026-10', n: 1, p50: 4, p85: 4, amostraPequena: true }] }],
      concluidas: 1, semOutliers: false, limiteOutlierDias: 30, outliersExcluidos: 0, de: '2025-11-01', ate: '2026-10-30', unit: 'util', avisos: [],
    }
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cards/controle-ciclo': { status: 200, corpo: controle } })
    renderizarTela(<Fechadas />, '/fechadas', '/fechadas?squad=Alfa')
    expect(await screen.findByRole('group', { name: 'Gráfico de controle do ciclo' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Tendência mensal do ciclo' })).toBeInTheDocument()
    // A seção lê os mesmos filtros da URL da página.
    await vi.waitFor(() => {
      const chamada = mock.mock.calls.map((c) => String(c[0])).find((u) => u.startsWith('/api/cards/controle-ciclo'))
      expect(chamada).toContain('squad=Alfa')
    })
  })

  it('semPontosMostraTracoNaoZero', async () => {
    simular({ [CARD]: { status: 200, corpo: { ...DTO, pontos: null, comPontos: 0 } } })
    renderizarTela(<Fechadas />, '/fechadas')
    const kpis = await screen.findByRole('group', { name: 'Indicadores das fechadas' })
    const pontos = within(kpis).getByText('Pontos entregues').closest('article') as HTMLElement
    expect(pontos).toHaveTextContent('—')
    expect(pontos).toHaveTextContent('Nenhuma concluída com pontos sincronizados')
  })
})
