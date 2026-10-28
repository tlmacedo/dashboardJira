import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gaveta, renderizarTela, simular } from '../../test/telas'
import type { CruzamentosDto } from './api'
import { Cruzamentos } from './Cruzamentos'

const celula = (n: number, valor: number | null = null, amostraPequena = false) => ({ n, valor, amostraPequena })

const DTO: CruzamentosDto = {
  linhasEixo: 'tipo',
  colunasEixo: 'categoria',
  medida: 'contagem',
  linhas: ['Checklist', 'Checklist Review'],
  colunas: ['TODO', 'ACTIVE', 'BLOCKED'],
  celulas: [[celula(8), celula(14), celula(3)], [celula(2), celula(5), celula(0)]],
  totaisDasLinhas: [25, 7],
  totaisDasColunas: [10, 19, 3],
  total: 32,
  eixosDeLinha: ['squad', 'categoria', 'status', 'tipo', 'prioridade', 'os', 'idade', 'vencimento', 'atraso', 'pessoa'],
  eixosDeColuna: ['squad', 'categoria', 'status', 'tipo', 'prioridade', 'os', 'idade', 'vencimento', 'atraso'],
  unit: 'util',
  avisos: [],
}

const CARD = '/api/cards/cruzamentos'
const TABELA = 'Tabela cruzada das tasks abertas'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('aba Cruzamentos', () => {
  it('mostraAsLinhasAsColunasEOsNumeros', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Cruzamentos />, '/cruzamentos')
    const tabela = await screen.findByRole('table', { name: TABELA })
    expect(within(tabela).getAllByRole('columnheader').map((c) => c.textContent)).toEqual(['Tipo × Categoria', 'A fazer', 'Ativa', 'Bloqueio', 'Total'])
    const linha = within(tabela).getByRole('rowheader', { name: 'Checklist' }).closest('tr') as HTMLElement
    expect(linha).toHaveTextContent(/8\s*14\s*3\s*25/)
    expect(within(tabela).getByRole('rowheader', { name: 'Total' }).closest('tr')).toHaveTextContent(/10\s*19\s*3\s*32/)
  })

  it('pessoaSoApareceComoLinha', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Cruzamentos />, '/cruzamentos')
    await screen.findByRole('table', { name: TABELA })
    const opcoes = (nome: string) => within(screen.getByRole('combobox', { name: nome })).getAllByRole('option').map((o) => o.textContent)
    expect(opcoes('Linhas')).toContain('Pessoa')
    expect(opcoes('Colunas')).not.toContain('Pessoa')
    // Só os eixos da lista fechada: nenhum campo do dicionário.
    expect(opcoes('Linhas')).toEqual(['Squad', 'Categoria de status', 'Status', 'Tipo', 'Prioridade', 'OS Version', 'Faixa de idade', 'Semana de vencimento', 'Label de atraso', 'Pessoa'])
  })

  it('eixosEMedidaVaoParaAUrl', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Cruzamentos />, '/cruzamentos')
    await screen.findByRole('table', { name: TABELA })
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Linhas' }), 'status')
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Colunas' }), 'prioridade')
    await userEvent.click(screen.getByRole('radio', { name: 'Idade P50' }))
    const p = new URLSearchParams(screen.getByTestId('url').textContent ?? '')
    expect([p.get('linhas'), p.get('colunas'), p.get('medida')]).toEqual(['status', 'prioridade', 'idadeP50'])
    await vi.waitFor(() => {
      const ultima = mock.mock.calls.map((c) => String(c[0])).filter((u) => u.startsWith(CARD)).at(-1)
      expect(ultima).toContain('linhas=status')
      expect(ultima).toContain('colunas=prioridade')
      expect(ultima).toContain('medida=idadeP50')
    })
  })

  it('estadoDosEixosVemDaUrl', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: { ...DTO, linhasEixo: 'status', colunasEixo: 'tipo', medida: 'idadeP50' } } })
    renderizarTela(<Cruzamentos />, '/cruzamentos', '/cruzamentos?linhas=status&colunas=tipo&medida=idadeP50')
    await screen.findByRole('table', { name: TABELA })
    expect(screen.getByRole('combobox', { name: 'Linhas' })).toHaveValue('status')
    expect(screen.getByRole('combobox', { name: 'Colunas' })).toHaveValue('tipo')
    expect(screen.getByRole('radio', { name: 'Idade P50' })).toBeChecked()
    expect(String(mock.mock.calls.find((c) => String(c[0]).startsWith(CARD))?.[0])).toContain('linhas=status')
  })

  it('celulaAbreAsTasksDoSegmentoPosicionalComOsMesmosEixos', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/CZ-01/tasks': { status: 200, corpo: gaveta(14, 'card.cz01.titulo') } })
    renderizarTela(<Cruzamentos />, '/cruzamentos', '/cruzamentos?linhas=tipo&colunas=categoria')
    const tabela = await screen.findByRole('table', { name: TABELA })
    await userEvent.click(within(tabela).getByRole('button', { name: /^Checklist × Ativa: 14/ }))
    await screen.findByRole('dialog')
    const url = String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/CZ-01/tasks'))?.[0])
    expect(url).toContain('segmento=c%3A1%3A2')
    expect(url).toContain('linhas=tipo')
    expect(url).toContain('colunas=categoria')
    // Célula vazia não é botão.
    expect(within(tabela).queryByRole('button', { name: /Checklist Review × Bloqueio/ })).toBeNull()
  })

  it('totaisAbremAsTasks', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/CZ-01/tasks': { status: 200, corpo: gaveta(1, 'card.cz01.titulo') } })
    renderizarTela(<Cruzamentos />, '/cruzamentos')
    const tabela = await screen.findByRole('table', { name: TABELA })
    const segmentoDe = () => decodeURIComponent(String(mock.mock.calls.filter((c) => String(c[0]).startsWith('/api/cartoes/CZ-01/tasks')).at(-1)?.[0]).match(/segmento=([^&]+)/)![1])
    await userEvent.click(within(tabela).getByRole('button', { name: 'Total Checklist: 25' }))
    await screen.findByRole('dialog')
    expect(segmentoDe()).toBe('l:1')
    await userEvent.keyboard('{Escape}')
    await userEvent.click(within(tabela).getByRole('button', { name: 'Total Ativa: 19' }))
    await screen.findByRole('dialog')
    expect(segmentoDe()).toBe('k:2')
    await userEvent.keyboard('{Escape}')
    await userEvent.click(within(tabela).getByRole('button', { name: 'Total geral: 32' }))
    await screen.findByRole('dialog')
    expect(segmentoDe()).toBe('total')
  })

  it('semTotalPorPessoaQuandoALinhaEPessoa', async () => {
    const porPessoa: CruzamentosDto = {
      ...DTO, linhasEixo: 'pessoa', linhas: ['ana', 'bruno', '(sem)'], celulas: [[celula(3), celula(1), celula(0)], [celula(1), celula(1), celula(0)], [celula(0), celula(1), celula(1)]],
      totaisDasLinhas: null, totaisDasColunas: [4, 3, 1], total: 8,
    }
    simular({ [CARD]: { status: 200, corpo: porPessoa } })
    renderizarTela(<Cruzamentos />, '/cruzamentos', '/cruzamentos?linhas=pessoa')
    const tabela = await screen.findByRole('table', { name: TABELA })
    expect(within(tabela).getAllByRole('columnheader').map((c) => c.textContent)).toEqual(['Pessoa × Categoria', 'A fazer', 'Ativa', 'Bloqueio'])
    // Ordem alfabética vinda da API, com o "sem responsável" por último; sem total por pessoa.
    expect(within(tabela).getAllByRole('rowheader').map((r) => r.textContent)).toEqual(['ana', 'bruno', '(sem responsável)', 'Total'])
    expect(within(tabela).queryByRole('button', { name: /Total ana/ })).toBeNull()
  })

  it('totalGeralContinuaAlcancavelQuandoALinhaEPessoa', async () => {
    const porPessoa: CruzamentosDto = {
      ...DTO, linhasEixo: 'pessoa', linhas: ['ana', 'bruno'], celulas: [[celula(3), celula(1), celula(0)], [celula(1), celula(1), celula(0)]],
      totaisDasLinhas: null, totaisDasColunas: [4, 2, 0], total: 6,
    }
    const mock = simular({ [CARD]: { status: 200, corpo: porPessoa }, '/api/cartoes/CZ-01/tasks': { status: 200, corpo: gaveta(6, 'card.cz01.titulo') } })
    renderizarTela(<Cruzamentos />, '/cruzamentos', '/cruzamentos?linhas=pessoa')
    await screen.findByRole('table', { name: TABELA })
    await userEvent.click(screen.getByRole('button', { name: 'Total geral: 6' }))
    await screen.findByRole('dialog')
    const url = String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/CZ-01/tasks'))?.[0])
    expect(decodeURIComponent(url.match(/segmento=([^&]+)/)![1])).toBe('total')
  })

  it('celulaMaisAltaTemPadraoENumeroNaoSoCor', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Cruzamentos />, '/cruzamentos')
    const tabela = await screen.findByRole('table', { name: TABELA })
    // 14 é o maior; 75% de 14 = 10,5: só ela está na faixa mais alta (8 e 5 não).
    const alta = within(tabela).getByRole('button', { name: /Checklist × Ativa/ }).closest('td') as HTMLElement
    expect(alta).toHaveAttribute('data-faixa', 'alta')
    expect(alta).toHaveTextContent('14')
    expect(within(tabela).getByRole('button', { name: /Checklist × A fazer/ }).closest('td')).not.toHaveAttribute('data-faixa', 'alta')
    expect(within(tabela).getByRole('button', { name: /Checklist × Ativa: 14, faixa mais alta/ })).toBeInTheDocument()
    expect(screen.getByText(/Hachurado = faixa mais alta/)).toBeInTheDocument()
  })

  it('semFaixaMaisAltaQuandoALinhaOuAColunaEPessoa', async () => {
    const porPessoa: CruzamentosDto = {
      ...DTO, linhasEixo: 'pessoa', linhas: ['ana', 'bruno'], celulas: [[celula(9), celula(1), celula(0)], [celula(1), celula(1), celula(0)]],
      totaisDasLinhas: null, totaisDasColunas: [10, 2, 0], total: 12,
    }
    simular({ [CARD]: { status: 200, corpo: porPessoa } })
    renderizarTela(<Cruzamentos />, '/cruzamentos', '/cruzamentos?linhas=pessoa')
    let tabela = await screen.findByRole('table', { name: TABELA })
    expect(tabela.querySelectorAll('[data-faixa]')).toHaveLength(0)
    expect(screen.queryByText(/faixa mais alta/)).toBeNull()
    cleanup()
    const colunaPessoa: CruzamentosDto = {
      ...DTO, linhasEixo: 'tipo', colunasEixo: 'pessoa', colunas: ['ana', 'bruno', 'carla'],
      celulas: [[celula(9), celula(1), celula(0)], [celula(1), celula(1), celula(0)]], totaisDasLinhas: [10, 2], totaisDasColunas: [10, 2, 0], total: 12,
    }
    simular({ [CARD]: { status: 200, corpo: colunaPessoa } })
    renderizarTela(<Cruzamentos />, '/cruzamentos', '/cruzamentos?colunas=pessoa')
    tabela = await screen.findByRole('table', { name: TABELA })
    expect(tabela.querySelectorAll('[data-faixa]')).toHaveLength(0)
    expect(screen.queryByText(/faixa mais alta/)).toBeNull()
  })

  it('idadeP50MostraValorNEAvisoDeAmostraPequena', async () => {
    const idade: CruzamentosDto = {
      ...DTO, medida: 'idadeP50',
      celulas: [[celula(6, 3.5), celula(1, 9, true), celula(0)], [celula(2, 20.5, true), celula(0), celula(0)]],
    }
    simular({ [CARD]: { status: 200, corpo: idade } })
    renderizarTela(<Cruzamentos />, '/cruzamentos', '/cruzamentos?medida=idadeP50')
    const tabela = await screen.findByRole('table', { name: TABELA })
    const suficiente = within(tabela).getByRole('button', { name: /Checklist × A fazer/ })
    expect(suficiente).toHaveTextContent('3,5')
    expect(suficiente.closest('td')).toHaveTextContent('n = 6')
    expect(suficiente.closest('td')).not.toHaveTextContent('●')
    const pequena = within(tabela).getByRole('button', { name: /Checklist × Ativa/ })
    expect(pequena.closest('td')).toHaveTextContent(/9,0.*n = 1.*●/)
    expect(pequena).toHaveAttribute('aria-label', expect.stringContaining('amostra pequena'))
    expect(screen.getByText(/Dias úteis/)).toBeInTheDocument()
  })

  it('valoresDosEixosSaemTraduzidos', async () => {
    const dto: CruzamentosDto = {
      ...DTO, linhasEixo: 'idade', colunasEixo: 'atraso', linhas: ['0-7', '>30', '(sem)'], colunas: ['com', 'sem'],
      celulas: [[celula(1), celula(2)], [celula(0), celula(3)], [celula(0), celula(1)]], totaisDasLinhas: [3, 3, 1], totaisDasColunas: [1, 6], total: 7,
    }
    simular({ [CARD]: { status: 200, corpo: dto } })
    renderizarTela(<Cruzamentos />, '/cruzamentos', '/cruzamentos?linhas=idade&colunas=atraso')
    const tabela = await screen.findByRole('table', { name: TABELA })
    expect(within(tabela).getAllByRole('rowheader').map((r) => r.textContent)).toEqual(['0 a 7 dias', 'Mais de 30 dias', '(sem dado)', 'Total'])
    expect(within(tabela).getAllByRole('columnheader').map((c) => c.textContent)).toEqual(['Faixa de idade × Label de atraso', 'Com label de atraso', 'Sem label de atraso', 'Total'])
  })

  it('semOrdenacaoPorColunaNemPosicao', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Cruzamentos />, '/cruzamentos')
    const tabela = await screen.findByRole('table', { name: TABELA })
    for (const c of within(tabela).getAllByRole('columnheader')) expect(within(c).queryByRole('button')).toBeNull()
    expect(screen.getByText(/nunca por contagem/i)).toBeInTheDocument()
  })

  it('avisoBloqueanteMostraTracoENadaClicavel', async () => {
    const aviso = { codigo: 'STATUS_SEM_MAPEAMENTO', gravidade: 'bloqueante', mensagem: 'x', params: { status: 'Exotico' } }
    simular({ [CARD]: { status: 200, corpo: { ...DTO, avisos: [aviso] } } })
    renderizarTela(<Cruzamentos />, '/cruzamentos')
    const tabela = await screen.findByRole('table', { name: TABELA })
    expect(within(tabela).queryAllByRole('button')).toHaveLength(0)
    expect(within(tabela).getAllByText('—').length).toBeGreaterThan(0)
  })

  it('filtroInvalido400EPermissao403', async () => {
    simular({ [CARD]: { status: 400 } })
    renderizarTela(<Cruzamentos />, '/cruzamentos', '/cruzamentos?linhas=xyz')
    expect(await screen.findByRole('alert')).toHaveTextContent(/filtros|inválid/i)
    cleanup()
    simular({ [CARD]: { status: 403 } })
    renderizarTela(<Cruzamentos />, '/cruzamentos')
    expect(await screen.findByRole('alert')).toHaveTextContent(/não permite ver estes dados/i)
  })
})
