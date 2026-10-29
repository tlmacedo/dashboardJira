import { cleanup, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderizarTela, simular } from '../../test/telas'
import type { DecisoesDto } from '../Distribuicao/api'
import { Decisoes } from './Decisoes'

const DTO: DecisoesDto = {
  de: '2026-10-01',
  ate: '2026-10-30',
  truncado: false,
  decisoes: [
    {
      tipo: 'DISTRIBUICAO', issueKey: 'TG-303', sugerido: 'carla', escolhido: 'carla', decisao: 'APROVADA', motivo: null,
      decididoPor: 'coord.um', decididoEm: '2026-10-30T14:00:00Z', versaoRegras: 'distribuicao v1',
    },
    {
      tipo: 'REBALANCEAMENTO', issueKey: 'TG-311', sugerido: 'carla', escolhido: 'ana', decisao: 'TROCADA', motivo: 'a ana conhece o módulo',
      decididoPor: 'gerente.exemplo', decididoEm: '2026-10-29T14:00:00Z', versaoRegras: 'distribuicao v1',
    },
  ],
}
const API = '/api/governanca/decisoes'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Decisões', () => {
  it('decisoesListaPorData', async () => {
    const mock = simular({ [API]: { status: 200, corpo: DTO } })
    renderizarTela(<Decisoes />, '/decisoes', '/decisoes?de=2026-10-01&ate=2026-10-30')
    const tabela = await screen.findByRole('table', { name: 'Decisões registradas' })
    const linhas = within(tabela).getAllByRole('row').slice(1)
    // Do mais recente para o mais antigo, como a API entrega; decisão em símbolo + texto.
    expect(linhas.map((l) => within(l).getByRole('rowheader').textContent)).toEqual(['TG-303', 'TG-311'])
    expect(linhas[0]).toHaveTextContent('✓ Aprovada')
    expect(linhas[1]).toHaveTextContent('● Trocada')
    expect(linhas[1]).toHaveTextContent('a ana conhece o módulo')
    expect(linhas[1]).toHaveTextContent('Rebalanceamento')
    expect(mock.mock.calls.map((c) => String(c[0])).find((u) => !u.startsWith('/api/filtros')) ?? '').toContain('de=2026-10-01')
    expect(mock.mock.calls.map((c) => String(c[0])).find((u) => !u.startsWith('/api/filtros')) ?? '').toContain('ate=2026-10-30')
    // Sem contagem nem coluna por pessoa que agregue: só quem decidiu em cada linha.
    expect(screen.getByText(/Sem contagem nem ordem por pessoa/)).toBeInTheDocument()
    for (const c of within(tabela).getAllByRole('columnheader')) expect(c.querySelector('button')).toBeNull()
  })

  it('listaCortadaAvisaEVazioDizOPeriodo', async () => {
    simular({ [API]: { status: 200, corpo: { ...DTO, truncado: true } } })
    renderizarTela(<Decisoes />, '/decisoes')
    expect(await screen.findByText(/foi cortada/)).toBeInTheDocument()
    cleanup()
    simular({ [API]: { status: 200, corpo: { ...DTO, decisoes: [] } } })
    renderizarTela(<Decisoes />, '/decisoes')
    expect(await screen.findByText('Nenhuma decisão registrada no período.')).toBeInTheDocument()
  })

  it('semPermissaoMostraMensagemDePapel', async () => {
    simular({ [API]: { status: 403 } })
    renderizarTela(<Decisoes />, '/decisoes')
    expect(await screen.findByRole('alert')).toHaveTextContent('Seu papel não permite ver estes dados')
  })
})
