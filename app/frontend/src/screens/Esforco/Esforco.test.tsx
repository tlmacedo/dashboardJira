import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gaveta, renderizarTela, simular } from '../../test/telas'
import type { EsforcoDto } from './api'
import { Esforco } from './Esforco'

const DTO: EsforcoDto = {
  versaoRegras: 'esforço v1',
  granularidade: 'semana',
  tasks: [
    { key: 'TG-401', resumo: 'Ajustar tela', tipo: 'Checklist', resolvida: '2026-10-12', pesoTipo: 3, testes: 3, horas: 10, indice: 14.5, horasDoCampo: false },
    { key: 'TG-405', resumo: 'Revisar contrato', tipo: 'Checklist Review', resolvida: '2026-10-27', pesoTipo: 1, testes: 0, horas: 2, indice: 3, horasDoCampo: true },
  ],
  pessoas: [
    { pessoa: 'ana', tasks: 1, pesoTipo: 1.928571, testes: 1.928571, horas: 6.428571, indice: 9.321429, horasDoCampo: false },
    { pessoa: 'bruno', tasks: 1, pesoTipo: 1.071429, testes: 1.071429, horas: 3.571429, indice: 5.178571, horasDoCampo: false },
    { pessoa: 'carla', tasks: 1, pesoTipo: 1, testes: 0, horas: 2, indice: 3, horasDoCampo: true },
  ],
  periodos: [
    { inicio: '2026-10-12', tasks: 1, horas: 10, indice: 14.5 },
    { inicio: '2026-10-26', tasks: 1, horas: 2, indice: 3 },
  ],
  avisos: [{ codigo: 'HORAS_DO_CAMPO_TIMESPENT', gravidade: 'informativo', mensagem: 'x', params: { n: '1' } }],
}
const CARD = '/api/cards/esforco'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Esforço por task fechada', () => {
  it('esforcoTresComponentesEIndice', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Esforco />, '/esforco')
    const tabela = await screen.findByRole('table', { name: 'Esforço por task' })
    const linha = within(tabela).getByText('TG-401').closest('tr') as HTMLElement
    // Os três componentes e o índice lado a lado (wireframe 6.5).
    expect(linha).toHaveTextContent('Tipo 3,0 · Testes TEST-* 3 · Horas 10,0 h · Índice 14,5')
    // A versão das regras vai na tela.
    expect(screen.getAllByText(/esforço v1/).length).toBeGreaterThan(0)
    expect(screen.getByText(/proposta inicial, a calibrar/)).toBeInTheDocument()
  })

  it('esforcoAvisoHorasDoCampo', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Esforco />, '/esforco')
    const tabela = await screen.findByRole('table', { name: 'Esforço por task' })
    const linha = within(tabela).getByText('TG-405').closest('tr') as HTMLElement
    expect(linha).toHaveTextContent('● horas do tempo gasto do Jira (a task não tem worklog)')
    expect(within(tabela).getByText('TG-401').closest('tr')).not.toHaveTextContent('tempo gasto do Jira')
    // E o aviso da página, em texto.
    expect(await screen.findByText('Em 1 task(s) sem worklog, as horas vieram do tempo gasto do Jira.')).toBeInTheDocument()
  })

  it('esforcoPorPessoaEmOrdemAlfabeticaSemPosicao', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Esforco />, '/esforco')
    const tabela = await screen.findByRole('table', { name: 'Esforço por pessoa (pela posse)' })
    expect(within(tabela).getAllByRole('rowheader').map((c) => c.textContent)).toEqual(['ana', 'bruno', 'carla'])
    const cabecalhos = within(tabela).getAllByRole('columnheader').map((c) => c.textContent ?? '')
    expect(cabecalhos.some((c) => /^#$|posição|ranking|melhor|pior|maior|menor/i.test(c))).toBe(false)
    expect(within(tabela).getByRole('rowheader', { name: 'ana' }).closest('tr')).toHaveTextContent('9,3')
  })

  it('granularidadeVaiNaUrl', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Esforco />, '/esforco')
    await screen.findByRole('table', { name: 'Esforço por task' })
    await userEvent.click(screen.getByRole('button', { name: 'Mês' }))
    expect(screen.getByTestId('url').textContent).toContain('granularidade=mes')
    await vi.waitFor(() => expect(mock.mock.calls.some((c) => String(c[0]).includes('granularidade=mes'))).toBe(true))
  })

  it('periodosPorSemana', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Esforco />, '/esforco')
    const tabela = await screen.findByRole('table', { name: 'Esforço por período' })
    expect(within(tabela).getByText('Semana de 12/10/2026')).toBeInTheDocument()
    expect(within(tabela).getByText('Semana de 26/10/2026')).toBeInTheDocument()
  })

  it('kpiAbreAGavetaComMesmoTamanho', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/EF-01/tasks': { status: 200, corpo: gaveta(2, 'card.ef01.titulo') } })
    renderizarTela(<Esforco />, '/esforco')
    const kpis = await screen.findByRole('group', { name: 'Indicadores do esforço' })
    await userEvent.click(within(kpis).getByRole('button', { name: /^Tasks concluídas no período/ }))
    const dialogo = await screen.findByRole('dialog')
    expect(await within(dialogo).findByText('2 tasks')).toBeInTheDocument()
    expect(String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/EF-01/tasks'))?.[0])).toContain('segmento=tasks')
  })
})
