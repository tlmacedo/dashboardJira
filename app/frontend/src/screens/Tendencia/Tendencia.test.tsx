import { cleanup, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderizarTela, simular } from '../../test/telas'
import type { TendenciaDto } from './api'
import { Tendencia } from './Tendencia'

const alta = { fracao: 1.3, semCapacidade: false, estado: 'problema' }
const normal = { fracao: 0.6, semCapacidade: false, estado: 'ok' }
const semCap = { fracao: null, semCapacidade: true, estado: 'problema' }

const DTO: TendenciaDto = {
  semanas: ['2026-10-05', '2026-10-12', '2026-10-19', '2026-10-26'],
  linhas: [
    { pessoa: 'ana', ocupacoes: [alta, alta, normal, alta], padrao: 'RECORRENTE' },
    { pessoa: 'bruno', ocupacoes: [normal, normal, normal, normal], padrao: 'NORMAL' },
    { pessoa: 'carla', ocupacoes: [normal, semCap, normal, alta], padrao: 'PONTUAL' },
  ],
  avisos: [{ codigo: 'HORAS_ATUAIS_NO_PASSADO', gravidade: 'informativo', mensagem: 'x' }],
}
const CARD = '/api/cards/tendencia'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Tendência de ocupação', () => {
  it('tendenciaPadraoRecorrenteEmTexto', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Tendencia />, '/tendencia')
    const tabela = await screen.findByRole('table', { name: 'Ocupação por semana' })
    const linha = (p: string) => within(tabela).getByRole('rowheader', { name: p }).closest('tr') as HTMLElement
    expect(linha('ana')).toHaveTextContent(/▲\s*recorrente/)
    expect(linha('carla')).toHaveTextContent(/●\s*pontual/)
    expect(linha('bruno')).toHaveTextContent(/✓\s*normal/)
    // "sem capacidade" é texto, nunca porcentagem.
    expect(linha('carla')).toHaveTextContent('sem capacidade')
  })

  it('avisoDeHorasAtuaisSempreVisivel', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Tendencia />, '/tendencia')
    await screen.findByRole('table', { name: 'Ocupação por semana' })
    expect(screen.getAllByText(/horas de hoje|horas de HOJE/i).length).toBeGreaterThanOrEqual(2)
    expect(screen.getByText(/As semanas passadas usam as horas de hoje/)).toBeInTheDocument()
  })

  it('pessoasAlfabeticasSemColunaDePosicao', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Tendencia />, '/tendencia')
    const tabela = await screen.findByRole('table', { name: 'Ocupação por semana' })
    expect(within(tabela).getAllByRole('rowheader').map((c) => c.textContent)).toEqual(['ana', 'bruno', 'carla'])
    const cabecalhos = within(tabela).getAllByRole('columnheader').map((c) => c.textContent ?? '')
    expect(cabecalhos.some((c) => /^#$|posição|ranking|melhor|pior/i.test(c))).toBe(false)
    expect(cabecalhos.slice(1, 5)).toEqual(['05/10', '12/10', '19/10', '26/10'])
  })

  it('usuarioComumVe403', async () => {
    simular({ [CARD]: { status: 403 } })
    renderizarTela(<Tendencia />, '/tendencia')
    expect(await screen.findByText(/Seu papel não permite ver estes dados/)).toBeInTheDocument()
  })
})
