import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gaveta, renderizarTela, simular } from '../../test/telas'
import type { LinhaDeCarga, SquadDto } from './api'
import { Squad } from './Squad'

const ok = { fracao: 0.4, semCapacidade: false, estado: 'ok' }
const linha = (pessoa: string, abertas: number): LinhaDeCarga => ({
  pessoa, abertas, wip: 1, filaH: 10, capacidade4SemH: 100, ocupacao: ok, ausenciaDias: 0, acimaDoP85: 0, vencidas: 0, riscoAlto: 1, horasAssumidas: false,
})

const DTO: SquadDto = {
  squad: 'Alfa',
  carga: [linha('ana', 2), linha('bruno', 1)],
  vencimentos: {
    dias: 7, vencem: 2, riscoDeAtrasar: 1, avisos: [],
    linhas: [
      { key: 'TG-201', resumo: 'a', pessoa: 'ana', vence: '2026-11-04', termino: '2026-11-03', folgaDiasUteis: 1, estado: 'ok' },
      { key: 'TG-202', resumo: 'b', pessoa: 'ana', vence: '2026-11-05', termino: '2026-11-06', folgaDiasUteis: -1, estado: 'problema' },
    ],
  },
  ausencias: [{ pessoa: 'ana', de: '2026-11-04', ate: '2026-11-05', fracao: 1 }],
  riscoAlto: 1, riscoMedio: 2, avisos: [],
}
const API = '/api/squad'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Meu squad', () => {
  it('squadVencimentosDaSemana', async () => {
    simular({ [API]: { status: 200, corpo: DTO } })
    renderizarTela(<Squad />, '/squad')
    const tabela = await screen.findByRole('table', { name: 'Vencimentos da semana' })
    const linhas = within(tabela).getAllByRole('row').slice(1)
    expect(linhas).toHaveLength(2)
    // Situação em símbolo e texto.
    expect(linhas[0]).toHaveTextContent('✓ com folga')
    expect(linhas[1]).toHaveTextContent('▲ risco de atrasar')
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Meu squad: Alfa')
  })

  it('cargaEmOrdemAlfabeticaSemNota', async () => {
    simular({ [API]: { status: 200, corpo: DTO } })
    renderizarTela(<Squad />, '/squad')
    const tabela = await screen.findByRole('table', { name: 'Carga por pessoa' })
    const linhas = within(tabela).getAllByRole('row').slice(1)
    expect(linhas.map((l) => within(l).getByRole('rowheader').textContent)).toEqual(['ana', 'bruno'])
    for (const c of within(tabela).getAllByRole('columnheader')) expect(c.querySelector('button')).toBeNull()
  })

  it('numeroDaPessoaAbreAGavetaDoCartaoComOSquad', async () => {
    const mock = simular({ [API]: { status: 200, corpo: DTO }, '/api/cartoes/CP-03/tasks': { status: 200, corpo: gaveta(2, 'card.cp03.titulo') } })
    renderizarTela(<Squad />, '/squad', '/squad?squad=Alfa')
    await userEvent.click(await screen.findByRole('button', { name: 'ana: 2' }))
    expect(await screen.findByRole('dialog')).toBeInTheDocument()
    const url = mock.mock.calls.map((c) => String(c[0])).find((u) => u.startsWith('/api/cartoes/CP-03/tasks')) ?? ''
    expect(url).toContain('segmento=pessoa%3Aana%3Afila')
    expect(url).toContain('squad=Alfa')
    // O pedido do squad vai para a API como o usuário o escolheu.
    expect(mock.mock.calls.some((c) => String(c[0]) === '/api/squad?squad=Alfa')).toBe(true)
  })

  it('semPermissaoMostraMensagemDePapel', async () => {
    simular({ [API]: { status: 403 } })
    renderizarTela(<Squad />, '/squad')
    expect(await screen.findByRole('alert')).toHaveTextContent('Seu papel não permite ver estes dados')
  })
})
