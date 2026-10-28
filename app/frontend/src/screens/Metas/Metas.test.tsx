import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gaveta, renderizarTela, simular } from '../../test/telas'
import type { MetasDto } from './api'
import { Metas } from './Metas'

const DTO: MetasDto = {
  versaoRegras: 'metas v1, labels v1',
  universo: 4,
  noPrazo: 3,
  noPrazoAjustado: 4,
  noPrazoOriginal: 2,
  antecipadas: 1,
  emD1D2: 1,
  noDia: 2,
  atrasadas: 1,
  semVencimento: 1,
  taxaNoPrazo: 0.75,
  taxaAjustada: 1,
  taxaOriginal: 0.5,
  taxaAntecipadas: 0.25,
  alertaDataOriginal: true,
  metaNoPrazo: 0.9,
  metaAntecipadas: 0.35,
  granularidade: 'semana',
  porPeriodo: [
    { chave: '2026-10-05', universo: 1, noPrazo: 1, noPrazoAjustado: 1, taxaNoPrazo: 1, antecipadas: 0, noDia: 1, atrasadas: 0 },
    { chave: '2026-10-12', universo: 3, noPrazo: 2, noPrazoAjustado: 3, taxaNoPrazo: 0.6667, antecipadas: 1, noDia: 1, atrasadas: 1 },
  ],
  distribuicao: [
    { chave: 'D3_OU_ANTES', n: 0 },
    { chave: 'D2', n: 1 },
    { chave: 'D1', n: 0 },
    { chave: 'D', n: 2 },
    { chave: 'ATRASO_1_3', n: 0 },
    { chave: 'ATRASO_MAIS_3', n: 1 },
  ],
  porTipo: [
    { chave: 'Checklist', universo: 3, noPrazo: 2, noPrazoAjustado: 3, estado: 'problema' },
    { chave: 'Checklist Review', universo: 1, noPrazo: 1, noPrazoAjustado: 1, estado: 'ok' },
  ],
  porPrioridade: [{ chave: '(sem)', universo: 4, noPrazo: 3, noPrazoAjustado: 4, estado: 'problema' }],
  porSquad: [
    { chave: 'Alfa', universo: 3, noPrazo: 2, noPrazoAjustado: 3, estado: 'atencao' },
    { chave: 'Beta', universo: 1, noPrazo: 1, noPrazoAjustado: 1, estado: 'ok' },
  ],
  porPessoa: [
    { chave: 'ana', universo: 2, noPrazo: 1, noPrazoAjustado: 2, estado: 'problema' },
    { chave: 'bruno', universo: 1, noPrazo: 1, noPrazoAjustado: 1, estado: 'ok' },
    { chave: 'carla', universo: 1, noPrazo: 1, noPrazoAjustado: 1, estado: 'ok' },
  ],
  avisos: [],
}

const CARD = '/api/cards/metas'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Metas', () => {
  it('metasNoPrazoEAjustadoLadoALado', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Metas />, '/metas')
    const grupo = await screen.findByRole('group', { name: 'No prazo e no prazo ajustado' })
    const noPrazo = within(grupo).getByRole('button', { name: /^No prazo\b(?! ajustado)(?! pela)/ })
    const ajustado = within(grupo).getByRole('button', { name: /^No prazo ajustado/ })
    expect(noPrazo).toHaveTextContent('75%')
    expect(noPrazo).toHaveTextContent('Meta 90%')
    expect(ajustado).toHaveTextContent('100%')
    expect(within(grupo).getByRole('button', { name: /^No prazo pela data original/ })).toHaveTextContent('50%')
    expect(within(grupo).getByRole('button', { name: /^Antecipadas/ })).toHaveTextContent('25%')
  })

  it('alertaDaDataOriginalSoQuandoOBackendMandar', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Metas />, '/metas')
    expect(await screen.findByRole('note')).toHaveTextContent('prazos foram remarcados')
    cleanup()
    simular({ [CARD]: { status: 200, corpo: { ...DTO, alertaDataOriginal: false } } })
    renderizarTela(<Metas />, '/metas')
    await screen.findByRole('group', { name: 'No prazo e no prazo ajustado' })
    expect(screen.queryByRole('note')).toBeNull()
  })

  it('metasPilulaComSimboloETexto', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Metas />, '/metas')
    const tipo = await screen.findByRole('table', { name: 'Por tipo' })
    const linhaDe = (t: HTMLElement, nome: string) => within(t).getByRole('rowheader', { name: nome }).closest('tr') as HTMLElement
    expect(linhaDe(tipo, 'Checklist')).toHaveTextContent(/▲\s*abaixo da meta/)
    expect(linhaDe(tipo, 'Checklist Review')).toHaveTextContent(/✓\s*na meta/)
    expect(linhaDe(screen.getByRole('table', { name: 'Por squad' }), 'Alfa')).toHaveTextContent(/●\s*perto da meta/)
  })

  it('tabelaPorPessoaAlfabeticaSemColunaDePosicao', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Metas />, '/metas')
    const tabela = await screen.findByRole('table', { name: 'Por pessoa' })
    expect(within(tabela).getAllByRole('rowheader').map((c) => c.textContent)).toEqual(['ana', 'bruno', 'carla'])
    const cabecalhos = within(tabela).getAllByRole('columnheader').map((c) => c.textContent ?? '')
    expect(cabecalhos.some((c) => /^#$|posição|ranking|melhor|pior/i.test(c))).toBe(false)
  })

  it('kpiAbreGavetaComMesmoTamanho', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/MT-01/tasks': { status: 200, corpo: gaveta(3, 'card.mt01.titulo') } })
    renderizarTela(<Metas />, '/metas', '/metas?de=2026-10-01&ate=2026-10-30&granularidade=mes')
    const grupo = await screen.findByRole('group', { name: 'No prazo e no prazo ajustado' })
    await userEvent.click(within(grupo).getByRole('button', { name: /^No prazo\b(?! ajustado)(?! pela)/ }))
    const dialogo = await screen.findByRole('dialog')
    expect(await within(dialogo).findByText('3 tasks')).toBeInTheDocument()
    const url = String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/MT-01/tasks'))?.[0])
    expect(url).toContain('segmento=noPrazo')
    expect(url).toContain('granularidade=mes')
  })

  it('serieAbreOUniversoEONoPrazoDoPeriodo', async () => {
    const mock = simular({
      [CARD]: { status: 200, corpo: DTO },
      '/api/cartoes/MT-08/tasks': { status: 200, corpo: gaveta(3, 'card.mt08.titulo') },
      '/api/cartoes/MT-09/tasks': { status: 200, corpo: gaveta(2, 'card.mt09.titulo') },
    })
    renderizarTela(<Metas />, '/metas')
    const serie = await screen.findByRole('table', { name: 'Série por período de vencimento' })
    await userEvent.click(within(serie).getByRole('button', { name: '2026-10-12: 3' }))
    await screen.findByRole('dialog')
    expect(String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/MT-08/tasks'))?.[0])).toContain('segmento=periodo%3A2026-10-12')
    await userEvent.keyboard('{Escape}')
    await userEvent.click(within(serie).getByRole('button', { name: /2026-10-12 No prazo: 2/ }))
    await screen.findByRole('dialog')
    expect(String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/MT-09/tasks'))?.[0])).toContain('segmento=periodo%3A2026-10-12%3AnoPrazo')
  })

  it('granularidadeVaiParaAUrl', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Metas />, '/metas')
    await screen.findByRole('group', { name: 'No prazo e no prazo ajustado' })
    expect(screen.getByRole('button', { name: 'Semana' })).toHaveAttribute('aria-pressed', 'true')
    await userEvent.click(screen.getByRole('button', { name: 'Mês' }))
    expect(new URLSearchParams(screen.getByTestId('url').textContent ?? '').get('granularidade')).toBe('mes')
    await vi.waitFor(() => {
      const chamadas = mock.mock.calls.map((c) => String(c[0])).filter((u) => u.startsWith(CARD))
      expect(chamadas.at(-1)).toContain('granularidade=mes')
    })
  })

  it('avisoBloqueanteMostraTraco', async () => {
    simular({ [CARD]: { status: 200, corpo: { ...DTO, avisos: [{ codigo: 'STATUS_SEM_MAPEAMENTO', gravidade: 'bloqueante', mensagem: 'x', params: { status: 'Exotico' } }] } } })
    renderizarTela(<Metas />, '/metas')
    const grupo = await screen.findByRole('group', { name: 'No prazo e no prazo ajustado' })
    expect(within(grupo).getAllByText('—').length).toBeGreaterThanOrEqual(4)
    expect(within(grupo).queryAllByRole('button')).toHaveLength(0)
    expect(screen.queryByRole('note')).toBeNull()
  })

  it('semPermissao403MostraMensagem', async () => {
    simular({ [CARD]: { status: 403 } })
    renderizarTela(<Metas />, '/metas')
    expect(await screen.findByRole('alert')).toHaveTextContent(/não permite ver estes dados/i)
  })

  it('universoVazioMostraTracoNasTaxas', async () => {
    simular({
      [CARD]: {
        status: 200,
        corpo: { ...DTO, universo: 0, noPrazo: 0, noPrazoAjustado: 0, taxaNoPrazo: null, taxaAjustada: null, taxaOriginal: null, taxaAntecipadas: null, alertaDataOriginal: false, porPeriodo: [], porTipo: [], porPrioridade: [], porSquad: [], porPessoa: [] },
      },
    })
    renderizarTela(<Metas />, '/metas')
    const grupo = await screen.findByRole('group', { name: 'No prazo e no prazo ajustado' })
    expect(within(grupo).getByText('No prazo').closest('article')).toHaveTextContent('—')
    expect(screen.getByText('Nenhum vencimento no período.')).toBeInTheDocument()
  })
})
