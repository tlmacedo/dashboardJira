import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gaveta, renderizarTela, simular } from '../../test/telas'
import type { ControleDoCicloDto } from './controleApi'
import { ControleDoCiclo } from './ControleDoCiclo'

const DTO: ControleDoCicloDto = {
  series: [
    {
      tipo: 'Checklist', n: 6, p50: 6, p85: 11.5, amostraPequena: false, janela: 5,
      pontos: [
        { dia: '2026-09-28', ciclo: 2, mediaMovel: 3, outlier: false },
        { dia: '2026-10-05', ciclo: 4, mediaMovel: 4, outlier: false },
        { dia: '2026-10-09', ciclo: 6, mediaMovel: 5, outlier: false },
        { dia: '2026-10-15', ciclo: 9, mediaMovel: 6, outlier: false },
        { dia: '2026-10-20', ciclo: 13, mediaMovel: 8, outlier: false },
        { dia: '2026-10-27', ciclo: 45, mediaMovel: 14, outlier: true },
      ],
      meses: [
        { mes: '2026-08', n: 0, p50: null, p85: null, amostraPequena: false },
        { mes: '2026-09', n: 1, p50: 2, p85: 2, amostraPequena: true },
        { mes: '2026-10', n: 5, p50: 9, p85: 22.6, amostraPequena: false },
      ],
    },
    {
      tipo: 'Checklist Review', n: 2, p50: 20.5, p85: 20.9, amostraPequena: true, janela: null,
      pontos: [{ dia: '2026-10-14', ciclo: 20, mediaMovel: null, outlier: false }, { dia: '2026-10-21', ciclo: 21, mediaMovel: null, outlier: false }],
      meses: [{ mes: '2026-10', n: 2, p50: 20.5, p85: 20.9, amostraPequena: true }],
    },
  ],
  concluidas: 8,
  semOutliers: false,
  limiteOutlierDias: 30,
  outliersExcluidos: 0,
  de: '2025-11-01',
  ate: '2026-10-30',
  unit: 'util',
  avisos: [],
}

const CARD = '/api/cards/controle-ciclo'
const GRAFICO = 'Gráfico de controle do ciclo'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('seção Controle do ciclo e tendência', () => {
  it('mostraOPrimeiroTipoComFaixasEMediaMovel', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<ControleDoCiclo />, '/fechadas')
    const grafico = await screen.findByRole('group', { name: GRAFICO })
    expect(screen.getByRole('heading', { name: 'Controle do ciclo' })).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Tipo' })).toHaveValue('Checklist')
    expect(within(grafico).getByText(/P50 6,0/)).toBeInTheDocument()
    expect(within(grafico).getByText(/P85 11,5/)).toBeInTheDocument()
    expect(screen.getByText('Média móvel de 5 pontos')).toBeInTheDocument()
    expect(grafico.querySelectorAll('[data-dia]')).toHaveLength(6)
    expect(grafico.querySelectorAll('polyline.controle-media')).toHaveLength(1)
  })

  it('tipoNaoMisturaChecklistEReview', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<ControleDoCiclo />, '/fechadas')
    await screen.findByRole('group', { name: GRAFICO })
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Tipo' }), 'Checklist Review')
    const grafico = screen.getByRole('group', { name: GRAFICO })
    expect(grafico.querySelectorAll('[data-dia]')).toHaveLength(2)
    expect(within(grafico).getByText(/P50 20,5/)).toBeInTheDocument()
    // Menos de 5 pontos: sem média móvel e com o aviso.
    expect(grafico.querySelectorAll('polyline.controle-media')).toHaveLength(0)
    expect(screen.getByText('Média móvel a partir de 5 pontos.')).toBeInTheDocument()
    expect(screen.getAllByText(/Poucos itens/).length).toBeGreaterThan(0)
  })

  it('pontoAbreAsTasksDoDiaEDoTipo', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/CT-01/tasks': { status: 200, corpo: gaveta(1, 'card.ct01.titulo') } })
    renderizarTela(<ControleDoCiclo />, '/fechadas')
    const grafico = await screen.findByRole('group', { name: GRAFICO })
    await userEvent.click(grafico.querySelector('[data-dia="2026-10-09"]') as Element)
    await screen.findByRole('dialog')
    expect(String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/CT-01/tasks'))?.[0])).toContain('segmento=dia%3A2026-10-09%3AChecklist')
  })

  it('outlierTemFormaDeLosangoETextoNaTabela', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<ControleDoCiclo />, '/fechadas')
    const grafico = await screen.findByRole('group', { name: GRAFICO })
    expect(grafico.querySelector('[data-dia="2026-10-27"]')).toHaveAttribute('data-forma', 'losango')
    expect(grafico.querySelector('[data-dia="2026-10-20"]')).toHaveAttribute('data-forma', 'circulo')
    await userEvent.click(screen.getByRole('button', { name: 'Ver tabela de pontos' }))
    const tabela = screen.getByRole('table', { name: 'Pontos do controle do ciclo (tabela)' })
    expect(within(tabela).getByText('27/10/2026').closest('tr')).toHaveTextContent(/▲\s*outlier/)
    expect(within(tabela).getByText('20/10/2026').closest('tr')).not.toHaveTextContent('outlier')
  })

  it('tabelaDePontosTemBotaoPorDiaParaOTeclado', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/CT-01/tasks': { status: 200, corpo: gaveta(1, 'card.ct01.titulo') } })
    renderizarTela(<ControleDoCiclo />, '/fechadas')
    await userEvent.click(await screen.findByRole('button', { name: 'Ver tabela de pontos' }))
    const tabela = screen.getByRole('table', { name: 'Pontos do controle do ciclo (tabela)' })
    // Sem coluna de pessoa.
    expect(within(tabela).getAllByRole('columnheader').map((c) => c.textContent)).toEqual(['Dia da conclusão', 'Ciclo (dias úteis)', 'Média móvel', 'Sinal'])
    within(tabela).getByRole('button', { name: '15/10/2026: 9,0' }).focus()
    await userEvent.keyboard('{Enter}')
    await screen.findByRole('dialog')
    expect(String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/CT-01/tasks'))?.[0])).toContain('segmento=dia%3A2026-10-15%3AChecklist')
  })

  it('semOutliersVaiParaAUrl', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<ControleDoCiclo />, '/fechadas')
    await screen.findByRole('group', { name: GRAFICO })
    await userEvent.click(screen.getByRole('checkbox', { name: /Sem outliers/ }))
    expect(new URLSearchParams(screen.getByTestId('url').textContent ?? '').get('semOutliers')).toBe('true')
    await vi.waitFor(() => {
      const chamadas = mock.mock.calls.map((c) => String(c[0])).filter((u) => u.startsWith(CARD))
      expect(chamadas.at(-1)).toContain('semOutliers=true')
    })
  })

  it('outliersExcluidosViramAviso', async () => {
    simular({ [CARD]: { status: 200, corpo: { ...DTO, semOutliers: true, outliersExcluidos: 3 } } })
    renderizarTela(<ControleDoCiclo />, '/fechadas', '/fechadas?semOutliers=true')
    expect(await screen.findByText('3 ciclo(s) acima de 30 dias corridos foram tirados.')).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: /Sem outliers/ })).toBeChecked()
  })

  it('tendenciaMensalComAvisoDeMesComPoucosItens', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/CT-02/tasks': { status: 200, corpo: gaveta(5, 'card.ct02.titulo') } })
    renderizarTela(<ControleDoCiclo />, '/fechadas')
    const tabela = await screen.findByRole('table', { name: 'Tendência mensal do ciclo (tabela)' })
    const setembro = within(tabela).getByText('09/2026').closest('tr') as HTMLElement
    expect(setembro).toHaveTextContent(/●\s*Poucos itens/)
    // Mês sem tasks: traço e sem botão. Mês com 5: botão que abre o mês e o tipo.
    const agosto = within(tabela).getByText('08/2026').closest('tr') as HTMLElement
    expect(within(agosto).queryAllByRole('button')).toHaveLength(0)
    expect(agosto).toHaveTextContent('—')
    expect(within(tabela).getByText('10/2026').closest('tr')).not.toHaveTextContent('Poucos itens')
    await userEvent.click(within(tabela).getByRole('button', { name: '10/2026: 5' }))
    await screen.findByRole('dialog')
    expect(String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/CT-02/tasks'))?.[0])).toContain('segmento=mes%3A2026-10%3AChecklist')
    // O gráfico da tendência traz o marcador vazado do mês com poucos itens e a legenda.
    const tendencia = screen.getByRole('group', { name: 'Gráfico da tendência mensal' })
    expect(tendencia.querySelectorAll('[data-poucos="true"]').length).toBeGreaterThan(0)
  })

  it('semTasksMostraMensagem', async () => {
    simular({ [CARD]: { status: 200, corpo: { ...DTO, series: [], concluidas: 0 } } })
    renderizarTela(<ControleDoCiclo />, '/fechadas')
    expect(await screen.findByText('Nenhuma task concluída com ciclo neste período.')).toBeInTheDocument()
  })

  it('erroDaApiNaoVirouAlertaDaPagina', async () => {
    simular({ [CARD]: { status: 500 } })
    renderizarTela(<ControleDoCiclo />, '/fechadas')
    expect(await screen.findByText('Não foi possível carregar o controle do ciclo.')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('avisoBloqueanteEscondeOGrafico', async () => {
    const aviso = { codigo: 'STATUS_SEM_MAPEAMENTO', gravidade: 'bloqueante', mensagem: 'x', params: { status: 'Exotico' } }
    simular({ [CARD]: { status: 200, corpo: { ...DTO, series: [], avisos: [aviso] } } })
    renderizarTela(<ControleDoCiclo />, '/fechadas')
    expect(await screen.findByText('Indisponível enquanto houver aviso de dados incompletos.')).toBeInTheDocument()
    expect(screen.queryByRole('group', { name: GRAFICO })).toBeNull()
  })
})
