import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gaveta, renderizarTela, simular } from '../../test/telas'
import type { ChecklistReviewDto, ParCR } from './api'
import { ChecklistReview } from './ChecklistReview'

const par = (extra: Partial<ParCR>): ParCR => ({
  pai: 'TG-401', review: 'TG-402', resumoPai: 'Checklist de câmera', resumoReview: 'Review da câmera', responsavelPai: 'ana', responsavelReview: 'bruno',
  statusPai: 'Closed', statusReview: 'Closed', vencePai: '2026-10-10', venceReview: '2026-10-10', criacaoPai: '2026-10-01', criacaoReview: '2026-10-03',
  resolvidoPai: '2026-10-09', resolvidoReview: '2026-10-08', horasAtivasReview: 8, paiReaberto: false, alertas: [], ...extra,
})

const DTO: ChecklistReviewDto = {
  versaoRegras: 'checklist-review v1',
  unit: 'util',
  avisos: [],
  resumo: {
    pares: 4, semReview: 1, concluidas: 3, comAlerta: 3,
    porAlerta: { REVIEW_VENCE_DEPOIS: 1, PAI_FECHADO_ANTES: 1, MESMA_PESSOA: 1, REVIEW_CURTA_COM_PAI_REABERTO: 1, REVIEW_VENCIDA: 1, REVIEW_ATRASADA: 0 },
    cicloReviewP50: 2, razaoReviewPaiP50: 0.5, criadaAposPaiHorasP50: 24, venceDepoisPct: 0.25, mesmaPessoaPct: 0.25, comAlertaPct: 0.75,
    duracaoXReabertura: [
      { faixa: '<1h', n: 1, taxaReabertura: 1, nBaixo: true },
      { faixa: '1-4h', n: 0, taxaReabertura: null, nBaixo: true },
      { faixa: '4-12h', n: 6, taxaReabertura: 0.5, nBaixo: false },
      { faixa: '>=12h', n: 1, taxaReabertura: 0, nBaixo: true },
    ],
    labels: [
      { label: 'ui', n: 2, taxaReabertura: 0.5, lift: 2, nBaixo: true },
      { label: 'android', n: 6, taxaReabertura: 0.17, lift: 0.7, nBaixo: false },
    ],
    diferencasDeLabels: [{ codigo: 'pai:ui', pares: 2 }, { codigo: 'review:rede', pares: 1 }],
    atrasosEmCadeia: 1,
    aprendizados: [{ codigo: 'MESMA_PESSOA_SEM_REVISAO_INDEPENDENTE', params: { n: '1', pct: '25' } }],
    amostraMinima: 5, limiteVenceDepois: 0.1, limiteMesmaPessoa: 0.1, limiteParesComAlerta: 0.3,
  },
  pares: [
    par({}),
    par({ pai: 'TG-403', review: 'TG-404', responsavelReview: 'ana', alertas: ['MESMA_PESSOA', 'REVIEW_VENCE_DEPOIS'], venceReview: '2026-10-17' }),
    par({ pai: 'TG-407', review: 'TG-408', responsavelReview: null, statusReview: 'Open', alertas: ['REVIEW_VENCIDA'], resolvidoReview: null, horasAtivasReview: null }),
  ],
  semReview: [{ key: 'TG-409', resumo: 'Sem review', responsavel: 'ana', status: 'Open' }],
}
const CARD = '/api/cards/checklist-review'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Checklist × Review', () => {
  it('alertaComSimboloETexto', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<ChecklistReview />, '/checklistReview')
    const tabela = await screen.findByRole('table', { name: 'Pares Checklist × Review' })
    const linha = within(tabela).getByText('TG-403').closest('tr') as HTMLElement
    // O alerta nunca é só cor: símbolo ▲ junto do texto.
    expect(linha).toHaveTextContent('▲ Mesma pessoa no checklist e na review')
    expect(linha).toHaveTextContent('▲ Review vence depois do checklist')
    // Par sem alerta: ✓ e texto.
    expect(within(tabela).getByText('TG-401').closest('tr')).toHaveTextContent('✓ Sem alerta')
    // O indicador acima do limite do YAML também leva símbolo e texto.
    const kpis = screen.getByRole('group', { name: 'Indicadores do Checklist × Review' })
    expect(within(kpis).getByRole('button', { name: /Pares com alerta/ })).toHaveTextContent('▲ Acima de 30%')
    expect(within(kpis).getByRole('button', { name: /Mesma pessoa nos dois/ })).toHaveTextContent('● Acima de 10%')
  })

  it('nBaixoEmTexto', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<ChecklistReview />, '/checklistReview')
    const labels = await screen.findByRole('table', { name: 'Labels e resultado' })
    // Label com n baixo mostra o lift, marcado em texto; label com amostra suficiente não leva a marca.
    expect(within(labels).getByText('ui').closest('tr')).toHaveTextContent('2,0× · n baixo')
    expect(within(labels).getByText('android').closest('tr')).not.toHaveTextContent('n baixo')
    const faixas = screen.getByRole('table', { name: 'Duração da review × reabertura do checklist' })
    expect(within(faixas).getByText('<1h').closest('tr')).toHaveTextContent('n baixo')
    expect(within(faixas).getByText('4-12h').closest('tr')).not.toHaveTextContent('n baixo')
    // Faixa vazia não vira "n baixo": só traço.
    expect(within(faixas).getByText('1-4h').closest('tr')).not.toHaveTextContent('n baixo')
  })

  it('gavetaLadoALado', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<ChecklistReview />, '/checklistReview')
    const tabela = await screen.findByRole('table', { name: 'Pares Checklist × Review' })
    const linha = within(tabela).getByText('TG-403').closest('tr') as HTMLElement
    await userEvent.click(within(linha).getByRole('button', { name: /Comparar TG-403 TG-404/ }))
    const dialogo = await screen.findByRole('dialog')
    const lado = within(dialogo).getByRole('table', { name: 'Comparação do par' })
    // As duas pontas lado a lado, com o responsável só quando o painel o mandou.
    expect(within(lado).getByText('Checklist', { selector: 'th' })).toBeInTheDocument()
    expect(within(lado).getByRole('rowheader', { name: 'Responsável' }).closest('tr')).toHaveTextContent('ana')
    expect(within(lado).getByRole('rowheader', { name: 'Vence em' }).closest('tr')).toHaveTextContent('10/10/2026')
    expect(within(lado).getByRole('rowheader', { name: 'Vence em' }).closest('tr')).toHaveTextContent('17/10/2026')
    expect(within(dialogo).getByText(/Mesma pessoa no checklist e na review/)).toBeInTheDocument()
    // Esc fecha e devolve o foco ao botão que abriu.
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(within(linha).getByRole('button', { name: /Comparar TG-403 TG-404/ })).toHaveFocus()
  })

  it('responsavelForaDaVisaoVemComTraco', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<ChecklistReview />, '/checklistReview')
    const tabela = await screen.findByRole('table', { name: 'Pares Checklist × Review' })
    expect(within(tabela).getByText('TG-407').closest('tr')).toHaveTextContent('ana / —')
    expect(document.body.textContent).not.toMatch(/\bnull\b|undefined/)
  })

  it('kpiAbreAGavetaComOSegmentoDoCartao', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/CR-07/tasks': { status: 200, corpo: gaveta(1, 'card.cr07.titulo') } })
    renderizarTela(<ChecklistReview />, '/checklistReview')
    const kpis = await screen.findByRole('group', { name: 'Indicadores do Checklist × Review' })
    await userEvent.click(within(kpis).getByRole('button', { name: /Mesma pessoa nos dois/ }))
    expect(await screen.findByRole('dialog')).toBeInTheDocument()
    const url = decodeURIComponent(String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/CR-07/tasks'))?.[0]))
    expect(url).toContain('segmento=alerta:MESMA_PESSOA')
  })

  it('aprendizadoEmTextoESemAmostraVemMensagem', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<ChecklistReview />, '/checklistReview')
    expect(await screen.findByText('1 pares (25%) têm a mesma pessoa no checklist e na review: não há revisão independente.')).toBeInTheDocument()
    cleanup()
    simular({ [CARD]: { status: 200, corpo: { ...DTO, resumo: { ...DTO.resumo, aprendizados: [] } } } })
    renderizarTela(<ChecklistReview />, '/checklistReview')
    expect(await screen.findByText('Sem amostra suficiente para escrever um aprendizado (mínimo de 5).')).toBeInTheDocument()
  })

  it('semPermissaoMostraMensagem', async () => {
    simular({ [CARD]: { status: 403 } })
    renderizarTela(<ChecklistReview />, '/checklistReview')
    expect(await screen.findByRole('alert')).toHaveTextContent('Seu papel não permite ver estes dados')
  })

  it('nenhumaTabelaOrdenaOuListaPessoasPorDesempenho', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<ChecklistReview />, '/checklistReview')
    await screen.findByRole('table', { name: 'Pares Checklist × Review' })
    const cabecalhos = screen.getAllByRole('columnheader').map((c) => c.textContent ?? '')
    expect(cabecalhos.some((c) => /^#$|posição|ranking|melhor|pior/i.test(c))).toBe(false)
    expect(screen.queryByText(/quem atrasa quem/i)).toBeNull()
  })
})
