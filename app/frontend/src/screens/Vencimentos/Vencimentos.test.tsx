import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gaveta, renderizarTela, simular } from '../../test/telas'
import type { VencimentosDto } from './api'
import { Vencimentos } from './Vencimentos'

const DTO: VencimentosDto = {
  dias: 7, vencem: 3, riscoDeAtrasar: 2,
  linhas: [
    { key: 'TG-201', resumo: 'Ajustar tela', pessoa: 'ana', vence: '2026-11-04', termino: '2026-11-03', folgaDiasUteis: 1, estado: 'ok' },
    { key: 'TG-202', resumo: 'Corrigir exportação', pessoa: 'ana', vence: '2026-11-05', termino: '2026-11-06', folgaDiasUteis: -1, estado: 'problema' },
    { key: 'TG-203', resumo: 'Revisar contrato', pessoa: null, vence: '2026-11-05', termino: null, folgaDiasUteis: null, estado: 'problema' },
  ],
  avisos: [],
}
const CARD = '/api/cards/vencimentos'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Vencimentos da semana', () => {
  it('vencimentosFolgaNegativaComTriangulo', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Vencimentos />, '/vencimentos')
    const tabela = await screen.findByRole('table', { name: 'Vencimentos com folga real' })
    const linha = (k: string) => within(tabela).getByText(k).closest('tr') as HTMLElement
    // Folga negativa: ▲ e o texto "risco de atrasar"; folga positiva: ✓.
    expect(linha('TG-202')).toHaveTextContent(/▲\s*risco de atrasar/)
    expect(linha('TG-202')).toHaveTextContent('-1')
    expect(linha('TG-201')).toHaveTextContent(/✓\s*com folga/)
    // Sem previsão também é risco, com o texto "sem previsão".
    expect(linha('TG-203')).toHaveTextContent(/▲\s*risco de atrasar/)
    expect(linha('TG-203')).toHaveTextContent('sem previsão')
  })

  it('alternaSeteECatorzeDias', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Vencimentos />, '/vencimentos')
    await screen.findByRole('table', { name: 'Vencimentos com folga real' })
    await userEvent.click(screen.getByRole('button', { name: '14 dias' }))
    expect(screen.getByTestId('url').textContent).toContain('dias=14')
    await vi.waitFor(() => expect(mock.mock.calls.some((c) => String(c[0]).includes('dias=14'))).toBe(true))
  })

  it('riscoDeAtrasarAbreGavetaComMesmoTamanho', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/VE-02/tasks': { status: 200, corpo: gaveta(2, 'card.ve02.titulo') } })
    renderizarTela(<Vencimentos />, '/vencimentos')
    const kpis = await screen.findByRole('group', { name: 'Indicadores dos vencimentos' })
    await userEvent.click(within(kpis).getByRole('button', { name: /^Risco de atrasar/ }))
    const dialogo = await screen.findByRole('dialog')
    expect(await within(dialogo).findByText('2 tasks')).toBeInTheDocument()
    expect(String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/VE-02/tasks'))?.[0])).toContain('segmento=riscoDeAtrasar')
  })

  it('semVencimentosMostraMensagem', async () => {
    simular({ [CARD]: { status: 200, corpo: { ...DTO, vencem: 0, riscoDeAtrasar: 0, linhas: [] } } })
    renderizarTela(<Vencimentos />, '/vencimentos')
    expect(await screen.findByText('Nada vence no período.')).toBeInTheDocument()
  })
})
