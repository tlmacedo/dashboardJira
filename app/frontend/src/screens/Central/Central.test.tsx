import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gaveta, renderizarTela, simular } from '../../test/telas'
import type { CentralDto, ItemCentral } from './api'
import { Central } from './Central'

const item = (nome: string, n: number | null, estado: string, rota: string, cartao: string | null = 'VG-09', universo: number | null = null): ItemCentral => ({
  item: nome, valor: n === null ? '—' : universo !== null ? `${n}/${universo}` : String(n), n, universo, estado, rota, cartao, segmento: cartao ? nome : null,
})

const DTO: CentralDto = {
  kpis: [
    { ...item('ABERTAS', 9, 'neutro', 'abertas', 'VG-01'), segmento: 'abertas' },
    { ...item('VENCIDAS', 3, 'problema', 'negligenciadas', 'VG-05'), segmento: 'vencidas' },
    { ...item('NO_PRAZO', 15, 'atencao', 'metas', 'VG-04', 20), segmento: 'noPrazo' },
    { ...item('NO_PRAZO_AJUSTADO', 18, 'ok', 'metas', 'VG-04', 20), segmento: 'noPrazoAjustado' },
  ],
  itens: [
    item('VENCIDAS', 3, 'problema', 'negligenciadas'),
    item('SEM_ATUALIZACAO', 4, 'atencao', 'negligenciadas'),
    item('PARADAS_NO_STATUS', 2, 'atencao', 'negligenciadas'),
    item('BLOQUEADAS', 0, 'ok', 'negligenciadas'),
    item('AUSENCIAS_14D', 3, 'neutro', 'capacidade', null),
    item('SEM_ESTIMATIVA', 5, 'problema', 'qualidade', 'VG-09', 20),
  ],
  entregasPorSemana: [{ semana: '2026-10-12', n: 2 }, { semana: '2026-10-19', n: 4 }],
  backlogTermina: { p50: '2026-12-07', p85: '2026-12-21', p95: null, semanasP50: 6, semanasP85: 8, semanasP95: null },
  backlog: 30,
  confiancaDoBacklog: 'media',
  squads: [
    { squad: 'Alfa', ocupacaoMedia: { fracao: 0.8, semCapacidade: false, estado: 'ok' }, jain: 0.95, pessoas: 2 },
    { squad: 'Beta', ocupacaoMedia: { fracao: 1.2, semCapacidade: false, estado: 'problema' }, jain: null, pessoas: 1 },
  ],
  avisos: [],
}
const API = '/api/central'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Central de atenção', () => {
  it('centralItensSaoLinks', async () => {
    const mock = simular({ [API]: { status: 200, corpo: DTO }, '/api/cartoes/VG-09/tasks': { status: 200, corpo: gaveta(3, 'card.vg09.titulo') } })
    renderizarTela(<Central />, '/central')
    const lista = await screen.findByRole('list', { name: 'O que pede atenção' })
    // O nome leva à tela do card de destino.
    expect(within(lista).getByRole('link', { name: 'Tasks vencidas' })).toHaveAttribute('href', '/negligenciadas')
    expect(within(lista).getByRole('link', { name: 'Sem estimativa' })).toHaveAttribute('href', '/qualidade')
    // O número abre as tasks do cartão da Central, com o segmento do item.
    await userEvent.click(within(lista).getByRole('button', { name: 'Tasks vencidas: 3' }))
    expect(await screen.findByRole('dialog')).toBeInTheDocument()
    const chamada = mock.mock.calls.map((c) => String(c[0])).find((u) => u.startsWith('/api/cartoes/VG-09/tasks'))
    expect(chamada).toContain('segmento=VENCIDAS')
  })

  it('centralSemAtualizacaoEParadasEmLinhasSeparadas', async () => {
    simular({ [API]: { status: 200, corpo: DTO } })
    renderizarTela(<Central />, '/central')
    const lista = await screen.findByRole('list', { name: 'O que pede atenção' })
    const itens = within(lista).getAllByRole('listitem')
    const sem = itens.find((li) => li.textContent?.includes('Sem atualização'))
    const paradas = itens.find((li) => li.textContent?.includes('Paradas no status'))
    expect(sem).toBeDefined()
    expect(paradas).toBeDefined()
    expect(sem).not.toBe(paradas)
    expect(sem).toHaveTextContent('4')
    expect(paradas).toHaveTextContent('2')
    // Estado em símbolo e texto: nunca só cor.
    expect(sem).toHaveTextContent('●')
    expect(itens.find((li) => li.textContent?.includes('Tasks vencidas'))).toHaveTextContent('▲')
  })

  it('centralCoordenadorSemItemIntegridade', async () => {
    simular({ [API]: { status: 200, corpo: DTO } })
    renderizarTela(<Central />, '/central')
    await screen.findByRole('list', { name: 'O que pede atenção' })
    expect(screen.queryByText(/indício alto/i)).toBeNull()
    cleanup()
    // O gerente recebe o item no DTO e ele aparece, como qualquer outro (link e número).
    const comIndicio: CentralDto = { ...DTO, itens: [...DTO.itens, { ...item('INDICIO_ALTO', 2, 'problema', 'integridade', 'VG-13') }] }
    simular({ [API]: { status: 200, corpo: comIndicio } })
    renderizarTela(<Central />, '/central')
    expect(await screen.findByRole('link', { name: 'Evidências de indício alto' })).toHaveAttribute('href', '/integridade')
  })

  it('kpisDePrazoLadoALadoEMetaSemTasks', async () => {
    simular({ [API]: { status: 200, corpo: DTO } })
    renderizarTela(<Central />, '/central')
    const grupo = await screen.findByRole('group', { name: 'Indicadores principais' })
    expect(within(grupo).getByText('15/20')).toBeInTheDocument()
    expect(within(grupo).getByText('18/20')).toBeInTheDocument()
    expect(within(grupo).getByText('75%')).toBeInTheDocument()
    // Ausências não têm tasks: o número é só texto, sem botão.
    const lista = screen.getByRole('list', { name: 'O que pede atenção' })
    expect(within(lista).queryByRole('button', { name: /ausentes/ })).toBeNull()
  })

  it('squadsEmOrdemComLinkParaAVisaoDoSquad', async () => {
    simular({ [API]: { status: 200, corpo: DTO } })
    renderizarTela(<Central />, '/central')
    const tabela = await screen.findByRole('table', { name: 'Ocupação por squad' })
    const linhas = within(tabela).getAllByRole('row').slice(1)
    expect(linhas.map((l) => within(l).getByRole('rowheader').textContent)).toEqual(['Alfa', 'Beta'])
    expect(within(linhas[0]).getByRole('link', { name: 'Alfa' })).toHaveAttribute('href', '/squad?squad=Alfa')
    expect(linhas[1]).toHaveTextContent('▲')
  })

  it('statusForaDoMapaMostraTracoEAviso', async () => {
    const bloqueado: CentralDto = {
      ...DTO,
      kpis: DTO.kpis.map((k) => ({ ...k, n: null, valor: '—', estado: 'indisponivel' })),
      itens: DTO.itens.map((i) => (i.cartao ? { ...i, n: null, valor: '—', estado: 'indisponivel' } : i)),
      backlogTermina: null, backlog: null, confiancaDoBacklog: null, squads: [],
      avisos: [{ codigo: 'STATUS_SEM_MAPEAMENTO', gravidade: 'bloqueante', mensagem: 'status sem mapeamento: X', params: { status: 'X' } }],
    }
    simular({ [API]: { status: 200, corpo: bloqueado } })
    renderizarTela(<Central />, '/central')
    const lista = await screen.findByRole('list', { name: 'O que pede atenção' })
    expect(within(lista).queryAllByRole('button')).toHaveLength(0)
    expect(within(lista).getAllByText('—').length).toBeGreaterThan(0)
  })

  it('semPermissaoMostraMensagemDePapel', async () => {
    simular({ [API]: { status: 403 } })
    renderizarTela(<Central />, '/central')
    expect(await screen.findByRole('alert')).toHaveTextContent('Seu papel não permite ver estes dados')
  })
})
