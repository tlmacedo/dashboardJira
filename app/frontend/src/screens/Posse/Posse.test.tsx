import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gaveta, renderizarTela, simular } from '../../test/telas'
import type { PosseDto } from './api'
import { Posse } from './Posse'

const DTO: PosseDto = {
  unit: 'util',
  avisos: [],
  tasks: [
    {
      key: 'TG-401', resumo: 'Ajustar tela', transferencias: 1,
      posse: [{ pessoa: 'ana', dias: 3, fracao: 0.642857 }, { pessoa: null, dias: 1.666667, fracao: 0.357143 }],
    },
    { key: 'TG-402', resumo: 'Corrigir exportação', transferencias: 0, posse: [{ pessoa: 'ana', dias: 8, fracao: 1 }] },
  ],
  pessoas: [
    { pessoa: 'ana', tasks: 2, dias: 11, recebidas: 0, repassadas: 1 },
    { pessoa: 'bruno', tasks: 1, dias: 1.7, recebidas: 1, repassadas: 0 },
    { pessoa: 'carla', tasks: 1, dias: 4, recebidas: 0, repassadas: 0 },
  ],
}
const CARD = '/api/cards/posse'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Posse da task', () => {
  it('posseMostraOutraPessoaSemNome', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Posse />, '/posse')
    const tabela = await screen.findByRole('table', { name: 'Posse por task' })
    const linha = within(tabela).getByText('TG-401').closest('tr') as HTMLElement
    // A parte de quem está fora da visão é "outra pessoa", com a fração; nunca um nome.
    expect(linha).toHaveTextContent('ana: 64%')
    expect(linha).toHaveTextContent('outra pessoa: 36%')
    expect(linha).toHaveTextContent('1,7 dias úteis')
    expect(within(tabela).getByText('TG-402').closest('tr')).toHaveTextContent('ana: 100%')
    expect(document.body.textContent).not.toMatch(/bruno-fora|\bnull\b/)
  })

  it('pessoasEmOrdemAlfabeticaSemPosicao', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Posse />, '/posse')
    const tabela = await screen.findByRole('table', { name: 'Posse por pessoa' })
    expect(within(tabela).getAllByRole('rowheader').map((c) => c.textContent)).toEqual(['ana', 'bruno', 'carla'])
    const cabecalhos = within(tabela).getAllByRole('columnheader').map((c) => c.textContent ?? '')
    expect(cabecalhos.some((c) => /^#$|posição|ranking|melhor|pior|maior|menor/i.test(c))).toBe(false)
    expect(screen.getByText('Ordem alfabética, sem ranking.')).toBeInTheDocument()
    const ana = within(tabela).getByRole('rowheader', { name: 'ana' }).closest('tr') as HTMLElement
    expect(ana).toHaveTextContent('11,0')
  })

  it('contagemDaPessoaAbreAGavetaComMesmoTamanho', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/PS-02/tasks': { status: 200, corpo: gaveta(2, 'card.ps02.titulo') } })
    renderizarTela(<Posse />, '/posse')
    const tabela = await screen.findByRole('table', { name: 'Posse por pessoa' })
    await userEvent.click(within(tabela).getByRole('button', { name: 'ana Tasks: 2' }))
    const dialogo = await screen.findByRole('dialog')
    expect(await within(dialogo).findByText('2 tasks')).toBeInTheDocument()
    const url = decodeURIComponent(String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/PS-02/tasks'))?.[0]))
    expect(url).toContain('segmento=pessoa:ana')
  })

  it('kpiComTrocaDeResponsavelAbreAGaveta', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/PS-01/tasks': { status: 200, corpo: gaveta(1, 'card.ps01.titulo') } })
    renderizarTela(<Posse />, '/posse')
    const kpis = await screen.findByRole('group', { name: 'Indicadores da posse' })
    expect(within(kpis).getByText('Tasks do período')).toBeInTheDocument()
    await userEvent.click(within(kpis).getByRole('button', { name: /^Com troca de responsável/ }))
    await screen.findByRole('dialog')
    expect(String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/PS-01/tasks'))?.[0])).toContain('segmento=transferidas')
  })

  it('unidadeCorridaAparecePorExtenso', async () => {
    simular({ [CARD]: { status: 200, corpo: { ...DTO, unit: 'corrido' } } })
    renderizarTela(<Posse />, '/posse')
    const tabela = await screen.findByRole('table', { name: 'Posse por task' })
    expect(within(tabela).getByText('TG-401').closest('tr')).toHaveTextContent('1,7 dias corridos')
  })

  it('semTasksMostraMensagem', async () => {
    simular({ [CARD]: { status: 200, corpo: { ...DTO, tasks: [], pessoas: [] } } })
    renderizarTela(<Posse />, '/posse')
    expect(await screen.findByText('Nenhuma task no período.')).toBeInTheDocument()
  })
})
