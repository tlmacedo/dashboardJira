import { cleanup, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderizarTela, simular } from '../../test/telas'
import type { PrevisaoDto } from './api'
import { Previsao } from './Previsao'

const DTO: PrevisaoDto = {
  versaoRegras: 'previsao v1', backlog: 3,
  termina: { p50: '2026-11-09', p85: '2026-11-16', p95: null, semanasP50: 2, semanasP85: 3, semanasP95: null },
  confianca: 'media', semanasComDado: 10, semanasHistorico: 16, limiteSemanas: 104, comChegada: true, entregasNaJanela: 20,
  vazaoSemanal: [{ semana: '2026-10-19', n: 4 }], chegadaSemanal: [{ semana: '2026-10-19', n: 3 }],
  quantos: [{ semanas: 2, p50: 8, p85: 6, p95: 4 }],
  porIssue: [
    { key: 'TG-201', resumo: 'Ajustar tela', tipo: 'Checklist', status: 'In Progress', responsavel: 'ana', idade: 4, restanteP50: 2, restanteP85: 4, n: 6, pelaCapacidade: '2026-11-03', atrasa: false },
    { key: 'TG-202', resumo: 'Corrigir exportação', tipo: 'Checklist', status: 'In Progress', responsavel: 'ana', idade: 3, restanteP50: 2, restanteP85: 4, n: 6, pelaCapacidade: '2026-11-06', atrasa: true },
    { key: 'TG-203', resumo: 'Revisar contrato', tipo: 'Checklist', status: 'In Progress', responsavel: null, idade: 3, restanteP50: null, restanteP85: null, n: 1, pelaCapacidade: null, atrasa: null },
  ],
  unit: 'util', avisos: [],
}

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Previsão: colunas da capacidade (Fase 6)', () => {
  it('pelaCapacidadeEAtrasaComSimboloETexto', async () => {
    simular({ '/api/cards/previsao': { status: 200, corpo: DTO } })
    renderizarTela(<Previsao />, '/previsao')
    const tabela = await screen.findByRole('table', { name: 'Previsão por task' })
    const linha = (k: string) => within(tabela).getByText(k).closest('tr') as HTMLElement
    expect(linha('TG-201')).toHaveTextContent('03/11/2026')
    expect(linha('TG-201')).toHaveTextContent(/✓\s*no prazo/)
    expect(linha('TG-202')).toHaveTextContent('06/11/2026')
    expect(linha('TG-202')).toHaveTextContent(/▲\s*atrasa/)
    // Sem previsão pela capacidade: texto e traço, nunca uma data inventada.
    expect(linha('TG-203')).toHaveTextContent('sem previsão')
    expect(within(tabela).getAllByRole('columnheader').map((c) => c.textContent)).toEqual(expect.arrayContaining(['Pela capacidade', 'Atrasa?']))
  })
})
