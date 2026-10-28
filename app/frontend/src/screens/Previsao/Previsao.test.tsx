import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gaveta, renderizarTela, simular } from '../../test/telas'
import type { PrevisaoDto } from './api'
import { Previsao } from './Previsao'

const DTO: PrevisaoDto = {
  versaoRegras: 'previsao v1',
  backlog: 20,
  termina: { p50: '2026-11-23', p85: '2026-11-30', p95: '2026-12-07', semanasP50: 4, semanasP85: 5, semanasP95: 6 },
  confianca: 'media',
  semanasComDado: 12,
  semanasHistorico: 16,
  limiteSemanas: 104,
  comChegada: true,
  entregasNaJanela: 40,
  vazaoSemanal: [{ semana: '2026-10-05', n: 5 }, { semana: '2026-10-12', n: 6 }, { semana: '2026-10-19', n: 0 }],
  chegadaSemanal: [{ semana: '2026-10-05', n: 3 }, { semana: '2026-10-12', n: 2 }, { semana: '2026-10-19', n: 4 }],
  quantos: [{ semanas: 2, p50: 10, p85: 7, p95: 5 }, { semanas: 4, p50: 20, p85: 16, p95: 13 }, { semanas: 8, p50: 40, p85: 34, p95: 30 }],
  porIssue: [
    { key: 'TG-101', resumo: 'Ajustar tela', tipo: 'Checklist', status: 'In Progress', responsavel: 'ana', idade: 20, restanteP50: 5, restanteP85: 7.8, n: 6 },
    { key: 'TG-103', resumo: 'Corrigir exportação', tipo: 'Checklist', status: 'On Hold', responsavel: 'carla', idade: 7, restanteP50: null, restanteP85: null, n: 1 },
  ],
  unit: 'util',
  avisos: [],
}

const CARD = '/api/cards/previsao'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Previsão', () => {
  it('terminaEmPercentisComSemanasEData', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Previsao />, '/previsao')
    const grupo = await screen.findByRole('group', { name: 'Quando o backlog termina' })
    const p50 = within(grupo).getByText('P50').closest('article') as HTMLElement
    expect(p50).toHaveTextContent('4 semanas')
    expect(p50).toHaveTextContent('23/11/2026')
    expect(within(grupo).getByText('P85').closest('article')).toHaveTextContent('5 semanas')
    expect(within(grupo).getByText('P95').closest('article')).toHaveTextContent('6 semanas')
  })

  it('previsaoMaisDeLimite', async () => {
    simular({ [CARD]: { status: 200, corpo: { ...DTO, termina: { p50: null, p85: null, p95: null, semanasP50: null, semanasP85: null, semanasP95: null } } } })
    renderizarTela(<Previsao />, '/previsao')
    const grupo = await screen.findByRole('group', { name: 'Quando o backlog termina' })
    for (const p of ['P50', 'P85', 'P95']) {
      expect(within(grupo).getByText(p).closest('article')).toHaveTextContent('Mais de 104 semanas')
    }
  })

  it('backlogVazioDizSemBacklog', async () => {
    simular({ [CARD]: { status: 200, corpo: { ...DTO, backlog: 0, termina: { p50: null, p85: null, p95: null, semanasP50: 0, semanasP85: 0, semanasP95: 0 } } } })
    renderizarTela(<Previsao />, '/previsao')
    const grupo = await screen.findByRole('group', { name: 'Quando o backlog termina' })
    expect(within(grupo).getByText('P50').closest('article')).toHaveTextContent('Sem backlog')
  })

  it('previsaoConfiancaComTexto', async () => {
    simular({ [CARD]: { status: 200, corpo: { ...DTO, confianca: 'baixa', semanasComDado: 6 } } })
    renderizarTela(<Previsao />, '/previsao')
    const kpis = await screen.findByRole('group', { name: 'Indicadores da previsão' })
    const confianca = within(kpis).getByText('Confiança da previsão').closest('article') as HTMLElement
    // Símbolo + texto, nunca só cor: "● Baixa" e a amostra por extenso.
    expect(confianca).toHaveTextContent('Baixa')
    expect(confianca).toHaveTextContent(/●\s*6 de 16 semanas com dado/)
  })

  it('confiancaAltaLevaCheck', async () => {
    simular({ [CARD]: { status: 200, corpo: { ...DTO, confianca: 'alta', semanasComDado: 16 } } })
    renderizarTela(<Previsao />, '/previsao')
    const kpis = await screen.findByRole('group', { name: 'Indicadores da previsão' })
    expect(within(kpis).getByText('Confiança da previsão').closest('article')).toHaveTextContent(/✓\s*16 de 16 semanas com dado/)
  })

  it('seriesDeVazaoEChegadaAbremASemana', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/PV-04/tasks': { status: 200, corpo: gaveta(6, 'card.pv04.titulo') } })
    renderizarTela(<Previsao />, '/previsao')
    await userEvent.click(await screen.findByRole('button', { name: '12/10/2026: 6' }))
    await screen.findByRole('dialog')
    expect(String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/PV-04/tasks'))?.[0])).toContain('segmento=vazao%3A2026-10-12')
    // a semana sem entrega mostra 0 e não é clicável
    expect(within(screen.getByRole('table', { name: 'Vazão por semana' })).queryByRole('button', { name: '19/10/2026: 0' })).toBeNull()
    // a chegada tem a própria tabela
    expect(screen.getByRole('table', { name: 'Chegada por semana' })).toBeInTheDocument()
  })

  it('quantosEm2_4_8SemanasSemGaveta', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Previsao />, '/previsao')
    const tabela = await screen.findByRole('table', { name: 'Quantos itens em 2, 4 e 8 semanas' })
    const linhas = within(tabela).getAllByRole('row').slice(1)
    expect(linhas).toHaveLength(3)
    expect(linhas[1]).toHaveTextContent('4')
    expect(linhas[1]).toHaveTextContent('20')
    expect(within(tabela).queryAllByRole('button')).toHaveLength(0)
  })

  it('previsaoPorTaskComTracoSemAmostra', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Previsao />, '/previsao')
    const tabela = await screen.findByRole('table', { name: 'Previsão por task' })
    const linha = (k: string) => within(tabela).getByText(k).closest('tr') as HTMLElement
    expect(linha('TG-101')).toHaveTextContent('5,0')
    expect(linha('TG-101')).toHaveTextContent('7,8')
    // Menos de 5 ciclos mais longos: traço, nunca zero.
    expect(linha('TG-103')).toHaveTextContent('—')
  })

  it('drillBacklogMesmoTamanho', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/PV-01/tasks': { status: 200, corpo: gaveta(20, 'card.pv01.titulo') } })
    renderizarTela(<Previsao />, '/previsao')
    const kpis = await screen.findByRole('group', { name: 'Indicadores da previsão' })
    await userEvent.click(within(kpis).getByRole('button', { name: /^Backlog/ }))
    const dialogo = await screen.findByRole('dialog')
    expect(await within(dialogo).findByText('20 tasks')).toBeInTheDocument()
    expect(String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/PV-01/tasks'))?.[0])).toContain('segmento=backlog')
  })

  it('semChegadaDizQueAChegadaNaoEntrou', async () => {
    simular({ [CARD]: { status: 200, corpo: { ...DTO, comChegada: false } } })
    renderizarTela(<Previsao />, '/previsao')
    expect(await screen.findByText(/A taxa de chegada não entrou no cálculo/)).toBeInTheDocument()
  })

  it('avisoBloqueanteMostraTraco', async () => {
    simular({ [CARD]: { status: 200, corpo: { ...DTO, avisos: [{ codigo: 'STATUS_SEM_MAPEAMENTO', gravidade: 'bloqueante', mensagem: 'x', params: { status: 'Exotico' } }] } } })
    renderizarTela(<Previsao />, '/previsao')
    const kpis = await screen.findByRole('group', { name: 'Indicadores da previsão' })
    expect(within(kpis).getByText('Backlog').closest('article')).toHaveTextContent('—')
    expect(within(kpis).queryAllByRole('button')).toHaveLength(0)
  })

  it('semPermissao403MostraMensagem', async () => {
    simular({ [CARD]: { status: 403 } })
    renderizarTela(<Previsao />, '/previsao')
    expect(await screen.findByRole('alert')).toHaveTextContent(/não permite ver estes dados/i)
  })
})
