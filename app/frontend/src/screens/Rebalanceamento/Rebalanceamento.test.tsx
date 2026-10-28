import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gaveta, renderizarTela, simular } from '../../test/telas'
import type { RebalanceamentoDto } from '../Distribuicao/api'
import { Rebalanceamento } from './Rebalanceamento'

const DTO: RebalanceamentoDto = {
  modoSombra: true,
  versaoRegras: 'distribuicao v1',
  gatilho: 1.1,
  histerese: 20,
  movimentos: [
    {
      issueKey: 'TG-311', resumo: 'Task grande', de: 'bruno', para: 'carla', horas: 16,
      ocupacaoOrigemAntes: 1.38, ocupacaoOrigemDepois: 0.95, ocupacaoDestinoDepois: 0.29, decisao: null,
    },
    {
      issueKey: 'TG-312', resumo: 'Outra task', de: 'bruno', para: 'ana', horas: 8,
      ocupacaoOrigemAntes: 0.95, ocupacaoOrigemDepois: 0.74, ocupacaoDestinoDepois: 0.4, decisao: 'APROVADA',
    },
  ],
  movimentosPendentes: 1,
  candidatos: ['ana', 'bruno', 'carla'],
  avisos: [],
}
const API = '/api/rebalanceamento'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Rebalanceamento', () => {
  it('rebalanceamentoMostraAntesEDepois', async () => {
    simular({ [API]: { status: 200, corpo: DTO } })
    renderizarTela(<Rebalanceamento />, '/rebalanceamento')
    const tabela = await screen.findByRole('table', { name: 'Movimentos sugeridos' })
    const linha = within(tabela).getAllByRole('row')[1]
    const celulas = within(linha).getAllByRole('cell').map((c) => c.textContent)
    // De, para, horas, origem antes, origem depois, destino depois.
    expect(celulas.slice(0, 6)).toEqual(['bruno', 'carla', '16,0', '138%', '95%', '29%'])
    expect(within(linha).getByRole('rowheader')).toHaveTextContent('TG-311')
    // Faixa do modo sombra e regra com gatilho e histerese, em texto.
    expect(screen.getByRole('note')).toHaveTextContent('Modo sombra: nada é alterado no Jira')
    expect(screen.getByText(/acima de 110% em 4 semanas.*20 pontos percentuais/)).toBeInTheDocument()
  })

  it('decisaoRegistradaEmTextoEBotoesSoNaPendente', async () => {
    simular({ [API]: { status: 200, corpo: DTO } })
    renderizarTela(<Rebalanceamento />, '/rebalanceamento')
    const tabela = await screen.findByRole('table', { name: 'Movimentos sugeridos' })
    expect(within(tabela).getByText('✓ Aprovada')).toBeInTheDocument()
    expect(within(tabela).getAllByRole('button', { name: 'Aprovar' })).toHaveLength(1)
    // A troca não oferece a origem nem o destino sugerido.
    await userEvent.setup().click(within(tabela).getByRole('button', { name: 'Trocar' }))
    expect(within(screen.getByLabelText('Trocar para')).getAllByRole('option').map((o) => o.textContent)).toEqual(['Escolha uma pessoa', 'ana'])
  })

  it('numeroAbreAGavetaDoMesmoTamanho', async () => {
    simular({ [API]: { status: 200, corpo: DTO }, '/api/cartoes/RB-02/tasks': { status: 200, corpo: gaveta(1, 'card.rb02.titulo') } })
    renderizarTela(<Rebalanceamento />, '/rebalanceamento')
    await screen.findByRole('table', { name: 'Movimentos sugeridos' })
    await userEvent.setup().click(screen.getByRole('button', { name: /Movimentos sem decisão/ }))
    expect(await screen.findByRole('dialog')).toBeInTheDocument()
  })

  it('vazioMostraOMotivo', async () => {
    simular({ [API]: { status: 200, corpo: { ...DTO, movimentos: [], movimentosPendentes: 0 } } })
    renderizarTela(<Rebalanceamento />, '/rebalanceamento')
    expect(await screen.findByText(/Ninguém passou do gatilho/)).toBeInTheDocument()
  })
})
