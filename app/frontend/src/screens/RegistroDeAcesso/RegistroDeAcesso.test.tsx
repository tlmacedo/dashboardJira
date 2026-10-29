import { cleanup, fireEvent, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderizarTela, simular } from '../../test/telas'
import type { AcessosDto } from './api'
import { RegistroDeAcesso } from './RegistroDeAcesso'

const DTO: AcessosDto = {
  de: '2026-10-24',
  ate: '2026-10-30',
  proximo: 77,
  tamanhoDaPagina: 200,
  acessos: [
    { quando: '2026-10-30T14:05:00Z', usuario: 'gerente.exemplo', rota: '/api/governanca/acessos', resultado: 200, filtros: ['de', 'ate'] },
    { quando: '2026-10-30T13:40:00Z', usuario: 'lider.alfa', rota: '/api/governanca/configuracao', resultado: 403, filtros: [] },
  ],
}
const API = '/api/governanca/acessos'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Registro de acesso', () => {
  it('listaConsultasSemContagemPorPessoa', async () => {
    const mock = simular({ [API]: { status: 200, corpo: DTO } })
    renderizarTela(<RegistroDeAcesso />, '/registroAcesso', '/registroAcesso?de=2026-10-24&ate=2026-10-30')
    const tabela = await screen.findByRole('table', { name: 'Consultas registradas' })
    const linhas = within(tabela).getAllByRole('row').slice(1)
    expect(linhas).toHaveLength(2)
    expect(linhas[0]).toHaveTextContent('gerente.exemplo')
    expect(linhas[0]).toHaveTextContent('✓ 200')
    expect(linhas[1]).toHaveTextContent('▲ 403')
    expect(mock.mock.calls.map((c) => String(c[0])).find((u) => !u.startsWith('/api/filtros')) ?? '').toContain('de=2026-10-24')
    expect(screen.getByText(/Sem contagem nem ordem por pessoa/)).toBeInTheDocument()
    // Nenhum cabeçalho ordena a tabela (sem "quem mais consultou").
    for (const c of within(tabela).getAllByRole('columnheader')) expect(c.querySelector('button')).toBeNull()
  })

  it('paginaPeloCursor', async () => {
    const mock = simular({ [API]: { status: 200, corpo: DTO } })
    renderizarTela(<RegistroDeAcesso />, '/registroAcesso')
    await screen.findByRole('table', { name: 'Consultas registradas' })
    expect(screen.getByRole('button', { name: 'Mais recentes' })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: 'Mais antigas' }))
    await vi.waitFor(() => expect(mock.mock.calls.some((c) => String(c[0]).includes('depoisDe=77'))).toBe(true))
  })

  it('semPermissaoMostraMensagemDePapel', async () => {
    simular({ [API]: { status: 403 } })
    renderizarTela(<RegistroDeAcesso />, '/registroAcesso')
    expect(await screen.findByRole('alert')).toHaveTextContent('Seu papel não permite ver estes dados')
  })
})
