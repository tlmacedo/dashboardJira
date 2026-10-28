import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gaveta, renderizarTela, simular } from '../../test/telas'
import { Abertas } from './Abertas'
import type { AbertasDto } from './api'

const DTO: AbertasDto = {
  abertas: 4,
  emAndamento: 2,
  acimaDoP85: 1,
  historicoPorTipo: [
    { tipo: 'Checklist', p50: 6, p85: 7.4, n: 3 },
    { tipo: 'Checklist Review', p50: 1, p85: 1, n: 1 },
  ],
  porStatus: [
    { chave: 'Open', n: 2 },
    { chave: 'In Progress', n: 1 },
    { chave: 'On Hold', n: 1 },
  ],
  porPrioridade: [
    { chave: 'High', n: 2 },
    { chave: 'Medium', n: 2 },
  ],
  porIdade: [
    { chave: '0-7', n: 1 },
    { chave: '8-14', n: 1 },
    { chave: '15-30', n: 1 },
    { chave: '>30', n: 1 },
  ],
  porVencimento: [
    { chave: 'vencidas', n: 1 },
    { chave: '7d', n: 1 },
    { chave: 'depois', n: 0 },
    { chave: 'sem', n: 2 },
  ],
  linhas: [
    { key: 'TG-101', resumo: 'Ajustar tela', tipo: 'Checklist', status: 'In Progress', prioridade: 'High', responsavel: 'ana', idade: 20, vence: '2026-10-28', horasRestantes: null },
    { key: 'TG-102', resumo: 'Revisar fluxo', tipo: 'Checklist Review', status: 'Open', prioridade: 'Medium', responsavel: 'bruno', idade: 33, vence: null, horasRestantes: 4 },
    { key: 'TG-103', resumo: 'Atualizar docs', tipo: 'Checklist', status: 'On Hold', prioridade: 'High', responsavel: 'carla', idade: 7, vence: '2026-11-02', horasRestantes: 8 },
    { key: 'TG-104', resumo: 'Sem dono', tipo: 'Checklist', status: 'Open', prioridade: 'Medium', responsavel: null, idade: 1, vence: null, horasRestantes: null },
  ],
  unit: 'util',
  avisos: [{ codigo: 'AMOSTRA_PEQUENA', gravidade: 'informativo', mensagem: 'x', params: { tipo: 'Checklist Review', n: '1', minimo: '5' } }],
}

const CARD = '/api/cards/abertas'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Abertas', () => {
  it('abertasMostraKpisEFaixas', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Abertas />, '/abertas')
    const kpis = await screen.findByRole('group', { name: 'Indicadores das abertas' })
    expect(within(kpis).getByRole('button', { name: /^Abertas/ })).toHaveTextContent('4')
    expect(within(kpis).getByRole('button', { name: /^Em andamento/ })).toHaveTextContent('2')
    const acima = within(kpis).getByRole('button', { name: /^Acima do P85/ })
    expect(acima).toHaveTextContent('1')
    expect(acima).toHaveTextContent('▲')
    // faixas de idade na ordem 0–7, 8–14, 15–30, > 30, e vencimento
    const idade = screen.getByRole('table', { name: 'Por idade' })
    expect(within(idade).getAllByRole('rowheader').map((c) => c.textContent)).toEqual(['0 a 7 dias', '8 a 14 dias', '15 a 30 dias', 'Mais de 30 dias'])
    const venc = screen.getByRole('table', { name: 'Por vencimento' })
    expect(within(venc).getByRole('button', { name: 'Vencidas: 1' })).toBeInTheDocument()
    // status na ordem recebida (a do fluxo) e prioridade
    const status = screen.getByRole('table', { name: 'Por status' })
    expect(within(status).getAllByRole('rowheader').map((c) => c.textContent)).toEqual(['Open', 'In Progress', 'On Hold'])
    expect(within(screen.getByRole('table', { name: 'Por prioridade' })).getByRole('button', { name: 'High: 2' })).toBeInTheDocument()
  })

  it('historicoPorTipoComAvisoDeAmostraPequena', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Abertas />, '/abertas')
    const tabela = await screen.findByRole('table', { name: /Idade histórica por tipo/ })
    expect(within(tabela).getByRole('rowheader', { name: 'Checklist' }).closest('tr')).toHaveTextContent('6,0')
    expect(within(tabela).getByRole('rowheader', { name: 'Checklist' }).closest('tr')).toHaveTextContent('7,4')
    // O número aparece e o aviso diz que é instável.
    expect(screen.getByText(/Poucas tasks de Checklist Review no histórico \(1 de no mínimo 5\)/)).toBeInTheDocument()
  })

  it('kpiAbreGavetaComMesmoTamanho', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/AB-01/tasks': { status: 200, corpo: gaveta(4) } })
    renderizarTela(<Abertas />, '/abertas', '/abertas?squad=Alfa&unit=corrido')
    await userEvent.click(await screen.findByRole('button', { name: /^Abertas/ }))
    const dialogo = await screen.findByRole('dialog')
    expect(await within(dialogo).findByText('4 tasks')).toBeInTheDocument()
    expect(within(dialogo).getAllByRole('row')).toHaveLength(1 + 4)
    const url = String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/AB-01/tasks'))?.[0])
    expect(url).toContain('segmento=abertas')
    // os mesmos filtros da tela vão para a gaveta
    expect(url).toContain('squad=Alfa')
    expect(url).toContain('unit=corrido')
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('contagemDeStatusAbreOSegmentoDoStatus', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/AB-05/tasks': { status: 200, corpo: gaveta(1, 'card.ab05.titulo') } })
    renderizarTela(<Abertas />, '/abertas')
    await userEvent.click(await screen.findByRole('button', { name: 'On Hold: 1' }))
    await screen.findByRole('dialog')
    const url = String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/AB-05/tasks'))?.[0])
    expect(url).toContain('segmento=status%3AOn+Hold')
  })

  it('avisoBloqueanteMostraTraco', async () => {
    const bloqueado: AbertasDto = {
      ...DTO,
      avisos: [{ codigo: 'STATUS_SEM_MAPEAMENTO', gravidade: 'bloqueante', mensagem: 'x', params: { status: 'Revisao Exotica' } }],
    }
    simular({ [CARD]: { status: 200, corpo: bloqueado } })
    renderizarTela(<Abertas />, '/abertas')
    const kpis = await screen.findByRole('group', { name: 'Indicadores das abertas' })
    expect(within(kpis).getByText('Abertas').closest('article')).toHaveTextContent('—')
    expect(screen.getByRole('alert')).toHaveTextContent('Revisao Exotica')
    // Números indisponíveis não viram botão que abre lista.
    expect(within(kpis).queryByRole('button', { name: /^Abertas/ })).toBeNull()
  })

  it('semPermissao403MostraMensagem', async () => {
    simular({ [CARD]: { status: 403 } })
    renderizarTela(<Abertas />, '/abertas')
    expect(await screen.findByRole('alert')).toHaveTextContent(/não permite ver estes dados/i)
  })

  it('unitCorridoVaiParaAUrlEFiltrosTambem', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Abertas />, '/abertas')
    await screen.findByRole('group', { name: 'Indicadores das abertas' })
    await userEvent.click(screen.getByRole('button', { name: 'Dias corridos' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'High' }))
    await userEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }))
    const p = new URLSearchParams(screen.getByTestId('url').textContent ?? '')
    expect(p.get('unit')).toBe('corrido')
    expect(p.getAll('prioridade')).toEqual(['High'])
    await vi.waitFor(() => {
      const chamadas = mock.mock.calls.map((c) => String(c[0])).filter((u) => u.startsWith(CARD))
      expect(chamadas.at(-1)).toContain('unit=corrido')
    })
  })

  it('tabelaOrdenavelSemColunaDePosicao', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Abertas />, '/abertas')
    const tabela = await screen.findByRole('table', { name: 'Tasks abertas' })
    // Ordem inicial: mais velha primeiro (idade decrescente).
    const chaves = () => within(tabela).getAllByRole('rowheader').map((c) => c.textContent)
    expect(chaves()).toEqual(['TG-102', 'TG-101', 'TG-103', 'TG-104'])
    // Cabeçalhos: nenhuma coluna de posição.
    const cabecalhos = within(tabela).getAllByRole('columnheader').map((c) => c.textContent?.replace(/[▲▼]/g, '').trim())
    expect(cabecalhos.some((c) => /^#$|posição|rank/i.test(c ?? ''))).toBe(false)
    await userEvent.click(within(tabela).getByRole('button', { name: /^Task/ }))
    expect(chaves()).toEqual(['TG-101', 'TG-102', 'TG-103', 'TG-104'])
    await userEvent.click(within(tabela).getByRole('button', { name: /^Task/ }))
    expect(chaves()).toEqual(['TG-104', 'TG-103', 'TG-102', 'TG-101'])
    expect(within(tabela).getAllByRole('columnheader').find((c) => c.getAttribute('aria-sort') === 'descending')).toBeTruthy()
  })

  it('baseSemPrioridadeMostraAvisoNaoZero', async () => {
    simular({
      [CARD]: {
        status: 200,
        corpo: { ...DTO, porPrioridade: [], avisos: [{ codigo: 'CAMPOS_DE_TRABALHO_INDISPONIVEIS', gravidade: 'informativo', mensagem: 'x' }] },
      },
    })
    renderizarTela(<Abertas />, '/abertas')
    expect((await screen.findAllByText(/Prioridade e estimativas ainda não foram sincronizadas/)).length).toBeGreaterThan(0)
    expect(screen.getByText('Prioridade ainda não sincronizada: sem números aqui até a próxima sincronização.')).toBeInTheDocument()
  })

  it('porCampoDoDicionarioAbreAGavetaDoGrupo', async () => {
    const dto = { ...DTO, porCampo: [{ campo: 'SKU Type', slug: 'sku-type', grupos: [{ valor: 'MR', n: 2 }, { valor: null, n: 2 }] }] }
    const mock = simular({ [CARD]: { status: 200, corpo: dto }, '/api/cartoes/AB-10/tasks': { status: 200, corpo: gaveta(2, 'card.ab10.titulo') } })
    renderizarTela(<Abertas />, '/abertas', '/abertas?campo.sku-type=MR')
    const tabela = await screen.findByRole('table', { name: 'Por campo: SKU Type' })
    await userEvent.click(within(tabela).getByRole('button', { name: 'MR: 2' }))
    expect(await screen.findByRole('dialog')).toBeInTheDocument()
    const chamada = mock.mock.calls.map((c) => String(c[0])).find((u) => u.startsWith('/api/cartoes/AB-10/tasks')) ?? ''
    expect(decodeURIComponent(chamada)).toContain('segmento=campo:sku-type:MR')
    // Os mesmos filtros do cartão (inclusive o campo do dicionário) vão para a gaveta.
    expect(decodeURIComponent(chamada)).toContain('campo.sku-type=MR')
  })

  it('emInglesTraduzTitulosEFaixas', async () => {
    localStorage.setItem('painel.idioma', 'en')
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Abertas />, '/abertas')
    const kpis = await screen.findByRole('group', { name: 'Open task indicators' })
    expect(within(kpis).getByRole('button', { name: /^Open/ })).toHaveTextContent('4')
    expect(within(screen.getByRole('table', { name: 'By age' })).getByText('More than 30 days')).toBeInTheDocument()
  })
})
