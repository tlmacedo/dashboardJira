import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gaveta, renderizarTela, simular } from '../../test/telas'
import type { CargaTipoDto } from './api'
import { CargaTipo } from './CargaTipo'

const DTO: CargaTipoDto = {
  tipos: ['Checklist', 'Checklist Review'],
  versoes: ['Android 15', ''],
  abertasN: [[2, 3], [0, 1]],
  abertasH: [[16.8, 16], [0, 2]],
  fechadasN: [[1, 0], [0, 1]],
  esforco: [[5.5, 0], [0, 1]],
  avisos: [],
}
const CARD = '/api/cards/carga-tipo'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Carga por tipo e OS', () => {
  it('cargaTipoSemOsEmTexto', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<CargaTipo />, '/cargaTipo')
    const abertas = await screen.findByRole('table', { name: 'Abertas por tipo e OS (tasks e horas de fila)' })
    // A versão vazia vira o texto "sem OS" (nunca uma coluna sem nome), por último.
    expect(within(abertas).getAllByRole('columnheader').map((c) => c.textContent)).toEqual(['Tipo', 'Android 15', 'sem OS'])
    const fechadas = screen.getByRole('table', { name: 'Fechadas por tipo e OS (tasks e esforço)' })
    expect(within(fechadas).getAllByRole('columnheader').map((c) => c.textContent)).toEqual(['Tipo', 'Android 15', 'sem OS'])
  })

  it('celulasMostramContagemEHorasOuEsforco', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<CargaTipo />, '/cargaTipo')
    const abertas = await screen.findByRole('table', { name: 'Abertas por tipo e OS (tasks e horas de fila)' })
    const linha = within(abertas).getByRole('rowheader', { name: 'Checklist' }).closest('tr') as HTMLElement
    expect(linha).toHaveTextContent('2 · 16,8 h')
    expect(linha).toHaveTextContent('3 · 16,0 h')
    const fechadas = screen.getByRole('table', { name: 'Fechadas por tipo e OS (tasks e esforço)' })
    expect(within(fechadas).getByRole('rowheader', { name: 'Checklist' }).closest('tr')).toHaveTextContent('1 · esforço 5,5')
    // Célula vazia é só "0", sem botão.
    expect(within(abertas).getByRole('rowheader', { name: 'Checklist Review' }).closest('tr')).toHaveTextContent('0')
  })

  it('celulaAbreAGavetaComMesmoTamanho', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/CG-01/tasks': { status: 200, corpo: gaveta(3, 'card.cg01.titulo') } })
    renderizarTela(<CargaTipo />, '/cargaTipo')
    const abertas = await screen.findByRole('table', { name: 'Abertas por tipo e OS (tasks e horas de fila)' })
    await userEvent.click(within(abertas).getByRole('button', { name: 'Checklist, sem OS: 3' }))
    const dialogo = await screen.findByRole('dialog')
    expect(await within(dialogo).findByText('3 tasks')).toBeInTheDocument()
    const url = decodeURIComponent(String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/CG-01/tasks'))?.[0]))
    // A versão vazia vai como segmento terminado em `os:` (o backend lê como "sem OS").
    expect(url).toContain('segmento=abertas:tipo:Checklist:os:')
    expect(url).not.toContain('os:sem')
  })

  it('celulaDeFechadasAbreOCartaoCg02', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/CG-02/tasks': { status: 200, corpo: gaveta(1, 'card.cg02.titulo') } })
    renderizarTela(<CargaTipo />, '/cargaTipo')
    const fechadas = await screen.findByRole('table', { name: 'Fechadas por tipo e OS (tasks e esforço)' })
    await userEvent.click(within(fechadas).getByRole('button', { name: 'Checklist, Android 15: 1' }))
    await screen.findByRole('dialog')
    expect(decodeURIComponent(String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/CG-02/tasks'))?.[0]).replaceAll('+', ' '))).toContain(
      'segmento=fechadas:tipo:Checklist:os:Android 15',
    )
  })

  it('semTasksMostraMensagem', async () => {
    simular({ [CARD]: { status: 200, corpo: { ...DTO, tipos: [], versoes: [], abertasN: [], abertasH: [], fechadasN: [], esforco: [] } } })
    renderizarTela(<CargaTipo />, '/cargaTipo')
    expect(await screen.findByText('Nenhuma task.')).toBeInTheDocument()
  })
})
