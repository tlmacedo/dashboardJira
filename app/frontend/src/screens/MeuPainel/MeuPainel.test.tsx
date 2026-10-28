import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gaveta, renderizarTela, simular } from '../../test/telas'
import type { MeuPainelDto } from './api'
import { MeuPainel } from './MeuPainel'

const DTO: MeuPainelDto = {
  pessoa: 'ana',
  minhasAbertas: 2,
  vencemEm7d: 2,
  emRisco: 1,
  ocupacao4Sem: { fracao: 0.25, semCapacidade: false, estado: 'ok' },
  ausenciasFuturas: [{ pessoa: 'ana', de: '2026-11-04', ate: '2026-11-05', fracao: 1 }],
  fila: [
    { key: 'TG-201', resumo: 'Primeira', vence: '2026-11-04', termino: '2026-11-03', atrasa: false, nivelRisco: 'baixo' },
    { key: 'TG-202', resumo: 'Segunda', vence: '2026-11-05', termino: '2026-11-06', atrasa: true, nivelRisco: 'alto' },
    { key: 'TG-203', resumo: 'Terceira', vence: null, termino: null, atrasa: false, nivelRisco: 'medio' },
  ],
  esforcoDoMes: { pesoTipo: 3, testes: 2.5, horas: 12, indice: 17.5, versaoRegras: 'esforço v1' },
  avisos: [],
}
const API = '/api/meu-painel'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Meu painel', () => {
  it('meuPainelFilaComSimbolos', async () => {
    simular({ [API]: { status: 200, corpo: DTO } })
    renderizarTela(<MeuPainel />, '/meu')
    const tabela = await screen.findByRole('table', { name: 'Minha fila' })
    const linhas = within(tabela).getAllByRole('row').slice(1)
    // Na ordem da agenda, com símbolo e texto do prazo e do risco.
    expect(linhas.map((l) => within(l).getByRole('rowheader').textContent?.slice(0, 6))).toEqual(['TG-201', 'TG-202', 'TG-203'])
    expect(linhas[0]).toHaveTextContent('✓ no prazo')
    expect(linhas[1]).toHaveTextContent('▲ atrasa')
    expect(linhas[1]).toHaveTextContent('▲ alto')
    expect(linhas[2]).toHaveTextContent('■ sem vencimento')
    expect(linhas[2]).toHaveTextContent('não cabe no horizonte')
  })

  it('meuPainelEsforcoTresComponentes', async () => {
    simular({ [API]: { status: 200, corpo: DTO } })
    renderizarTela(<MeuPainel />, '/meu')
    const esforco = (await screen.findByRole('heading', { name: 'Esforço do mês' })).closest('article') as HTMLElement
    for (const rotulo of ['Peso do tipo', 'Testes', 'Horas', 'Índice']) expect(within(esforco).getByText(rotulo)).toBeInTheDocument()
    expect(within(esforco).getByText('2,5')).toBeInTheDocument()
    expect(within(esforco).getByText(/nunca uma nota/)).toBeInTheDocument()
  })

  it('numerosAbremAGavetaDosCartoesMP', async () => {
    const mock = simular({ [API]: { status: 200, corpo: DTO }, '/api/cartoes/MP-01/tasks': { status: 200, corpo: gaveta(2, 'card.mp01.titulo') } })
    renderizarTela(<MeuPainel />, '/meu')
    await userEvent.click(await screen.findByRole('button', { name: /Vencem em 7 dias/ }))
    expect(await screen.findByRole('dialog')).toBeInTheDocument()
    const url = mock.mock.calls.map((c) => String(c[0])).find((u) => u.startsWith('/api/cartoes/MP-01/tasks')) ?? ''
    expect(url).toContain('segmento=vencemEm7d')
    expect(url).not.toContain('pessoa=')
  })

  it('squadLeaderAbrePessoaDoSquadPelaUrl', async () => {
    const mock = simular({ [API]: { status: 200, corpo: { ...DTO, pessoa: 'bruno' } } })
    renderizarTela(<MeuPainel />, '/meu', '/meu?pessoa=bruno')
    expect(await screen.findByText(/Painel de bruno/)).toBeInTheDocument()
    expect(mock.mock.calls.some((c) => String(c[0]) === '/api/meu-painel?pessoa=bruno')).toBe(true)
  })

  it('ausenciasSemMotivoEEsforcoVazio', async () => {
    simular({ [API]: { status: 200, corpo: { ...DTO, esforcoDoMes: null, ocupacao4Sem: null, fila: [] } } })
    renderizarTela(<MeuPainel />, '/meu')
    expect(await screen.findByText('Nenhuma task concluída por você neste mês.')).toBeInTheDocument()
    expect(screen.getByText('Nenhuma task aberta com você.')).toBeInTheDocument()
    expect(screen.getByText(/04\/11\/2026 – 05\/11\/2026/)).toBeInTheDocument()
    expect(document.body.textContent?.toLowerCase()).not.toContain('motivo')
  })

  it('statusForaDoMapaMostraTraco', async () => {
    simular({
      [API]: {
        status: 200,
        corpo: {
          ...DTO, minhasAbertas: null, vencemEm7d: null, emRisco: null, fila: [],
          avisos: [{ codigo: 'STATUS_SEM_MAPEAMENTO', gravidade: 'bloqueante', mensagem: 'x', params: { status: 'X' } }],
        },
      },
    })
    renderizarTela(<MeuPainel />, '/meu')
    const grupo = await screen.findByRole('group', { name: 'Meus indicadores' })
    expect(within(grupo).getAllByText('—').length).toBeGreaterThanOrEqual(3)
    expect(within(grupo).queryAllByRole('button')).toHaveLength(0)
  })
})
