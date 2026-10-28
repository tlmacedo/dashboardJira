import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gaveta, renderizarTela, simular } from '../../test/telas'
import type { LinhaNegligenciada, NegligenciadasDto } from './api'
import { Negligenciadas } from './Negligenciadas'

const linha = (extra: Partial<LinhaNegligenciada>): LinhaNegligenciada => ({
  key: 'TG-101',
  resumo: 'Ajustar tela',
  tipo: 'Checklist',
  status: 'In Progress',
  responsavel: 'ana',
  vence: '2026-10-28',
  labels: ['LATE_SENT'],
  dias: 10,
  ultimoMovimento: { campo: 'status', dia: '2026-10-01', dias: 29 },
  semMovimentoReal: true,
  ...extra,
})

const TG101 = linha({})
const TG102 = linha({ key: 'TG-102', resumo: 'Revisar fluxo', status: 'Open', responsavel: 'bruno', vence: null, labels: [], dias: 45, ultimoMovimento: { campo: 'criacao', dia: '2026-09-15', dias: 45 } })
const TG103 = linha({ key: 'TG-103', resumo: 'Atualizar docs', status: 'On Hold', responsavel: 'carla', dias: 4, ultimoMovimento: { campo: 'status', dia: '2026-10-26', dias: 4 }, semMovimentoReal: false })
const TG104 = linha({ key: 'TG-104', resumo: 'Sem dono', status: 'Open', responsavel: null, dias: 1, ultimoMovimento: { campo: 'criacao', dia: '2026-10-29', dias: 1 }, semMovimentoReal: false })

const DTO: NegligenciadasDto = {
  semAtualizacao: 1,
  semResponsavel: 1,
  vencidas: 1,
  bloqueadas: 1,
  paradasNoStatus: 2,
  aFazerAntigas: 1,
  listas: {
    semAtualizacao: [TG101],
    semResponsavel: [TG104],
    vencidas: [linha({ dias: 2 })],
    bloqueadas: [TG103],
    paradas: [TG101, TG103],
    aFazer: [TG102],
  },
  diasSemAtualizacao: 7,
  semMovimentoReal: false,
  unit: 'util',
  avisos: [],
}

const CARD = '/api/cards/negligenciadas'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Negligenciadas', () => {
  it('negligenciadasDuasListasSeparadas', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Negligenciadas />, '/negligenciadas')
    const semAtualizacao = await screen.findByRole('table', { name: 'Sem atualização (1)' })
    const paradas = screen.getByRole('table', { name: 'Paradas no status (2)' })
    // A TG-101 está nas DUAS listas: são recortes diferentes (updated do Jira x tempo no status).
    expect(within(semAtualizacao).getByText('TG-101')).toBeInTheDocument()
    expect(within(paradas).getByText('TG-101')).toBeInTheDocument()
    expect(within(paradas).getByText('TG-103')).toBeInTheDocument()
    // Seis listas, uma por cartão.
    expect(screen.getAllByRole('table')).toHaveLength(6)
  })

  it('mostraOUltimoMovimentoRealEOSimboloDeSemMovimento', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Negligenciadas />, '/negligenciadas')
    const aFazer = await screen.findByRole('table', { name: 'A fazer há mais de 30 dias (1)' })
    const linhaTg102 = within(aFazer).getByText('TG-102').closest('tr') as HTMLElement
    expect(linhaTg102).toHaveTextContent('Criação · 15/09/2026')
    expect(linhaTg102).toHaveTextContent('● sem movimento real')
    const bloqueadas = screen.getByRole('table', { name: 'Bloqueadas (1)' })
    const linhaTg103 = within(bloqueadas).getByText('TG-103').closest('tr') as HTMLElement
    expect(linhaTg103).toHaveTextContent('Status · 26/10/2026')
    expect(linhaTg103).not.toHaveTextContent('sem movimento real')
    // Sem responsável: mostra o traço, não um nome.
    expect(within(screen.getByRole('table', { name: 'Sem responsável (1)' })).getByText('TG-104').closest('tr')).toHaveTextContent('—')
  })

  it('filtroSemMovimentoRealVaiParaAUrl', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Negligenciadas />, '/negligenciadas')
    await screen.findByRole('table', { name: 'Sem atualização (1)' })
    await userEvent.click(screen.getByRole('checkbox', { name: 'Sem movimento real' }))
    expect(new URLSearchParams(screen.getByTestId('url').textContent ?? '').get('movimento')).toBe('semReal')
    await vi.waitFor(() => {
      const chamadas = mock.mock.calls.map((c) => String(c[0])).filter((u) => u.startsWith(CARD))
      expect(chamadas.at(-1)).toContain('movimento=semReal')
    })
    await userEvent.click(screen.getByRole('checkbox', { name: 'Sem movimento real' }))
    expect(new URLSearchParams(screen.getByTestId('url').textContent ?? '').get('movimento')).toBeNull()
  })

  it('kpiAbreGavetaComMesmoTamanho', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/NG-05/tasks': { status: 200, corpo: gaveta(2, 'card.ng05.titulo') } })
    renderizarTela(<Negligenciadas />, '/negligenciadas')
    const kpis = await screen.findByRole('group', { name: 'Indicadores das negligenciadas' })
    const botao = within(kpis).getByRole('button', { name: /^Paradas no status/ })
    expect(botao).toHaveTextContent('2')
    expect(botao).toHaveTextContent('▲')
    await userEvent.click(botao)
    const dialogo = await screen.findByRole('dialog')
    expect(await within(dialogo).findByText('2 tasks')).toBeInTheDocument()
    expect(String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/NG-05/tasks'))?.[0])).toContain('segmento=paradas')
  })

  it('avisoBloqueanteMostraTraco', async () => {
    simular({
      [CARD]: {
        status: 200,
        corpo: { ...DTO, avisos: [{ codigo: 'STATUS_SEM_MAPEAMENTO', gravidade: 'bloqueante', mensagem: 'x', params: { status: 'Exotico' } }] },
      },
    })
    renderizarTela(<Negligenciadas />, '/negligenciadas')
    const kpis = await screen.findByRole('group', { name: 'Indicadores das negligenciadas' })
    expect(within(kpis).getAllByText('—')).toHaveLength(6)
    expect(within(kpis).queryAllByRole('button')).toHaveLength(0)
  })

  it('semPermissao403MostraMensagem', async () => {
    simular({ [CARD]: { status: 403 } })
    renderizarTela(<Negligenciadas />, '/negligenciadas')
    expect(await screen.findByRole('alert')).toHaveTextContent(/não permite ver estes dados/i)
  })

  it('listaTruncadaMostraOTotalEOCaminhoParaAGaveta', async () => {
    simular({
      [CARD]: { status: 200, corpo: { ...DTO, semAtualizacao: 150, listas: { ...DTO.listas, semAtualizacao: [TG101] } } },
      '/api/cartoes/NG-01/tasks': { status: 200, corpo: gaveta(150, 'card.ng01.titulo') },
    })
    renderizarTela(<Negligenciadas />, '/negligenciadas')
    const tabela = await screen.findByRole('table', { name: 'Sem atualização (150)' })
    const cartao = tabela.closest('article') as HTMLElement
    expect(within(cartao).getByText(/Mostrando 1 de 150\./)).toBeInTheDocument()
    await userEvent.click(within(cartao).getAllByRole('button', { name: 'Ver tasks' })[0])
    expect(await screen.findByRole('dialog')).toBeInTheDocument()
  })

  it('filtroAtivoMostraAvisoComOLimite', async () => {
    simular({ [CARD]: { status: 200, corpo: { ...DTO, semMovimentoReal: true } } })
    renderizarTela(<Negligenciadas />, '/negligenciadas', '/negligenciadas?movimento=semReal')
    expect(await screen.findByRole('note')).toHaveTextContent('há mais de 7 dias')
    expect(screen.getByRole('checkbox', { name: 'Sem movimento real' })).toBeChecked()
  })
})
