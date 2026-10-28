import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gaveta, renderizarTela, simular } from '../../test/telas'
import type { EpicosDto } from './api'
import { Epicos } from './Epicos'

const DTO: EpicosDto = {
  minItens: 5,
  avisos: [],
  linhas: [
    { id: 'a1b2c3d4e5f6', epico: 'TG-900', itens: 6, concluidos: 4, progresso: 0.666667, maisAntigo: '2026-09-10', vencidos: 1, pontos: 8, previsaoSemanasP85: null },
    { id: '0f1e2d3c4b5a', epico: 'TG-910', itens: 11, concluidos: 8, progresso: 0.727273, maisAntigo: null, vencidos: 0, pontos: null, previsaoSemanasP85: 3 },
  ],
}

const CARD = '/api/cards/epicos'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Épicos', () => {
  it('epicoSemAmostraMostraTraco', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Epicos />, '/epicos')
    const tabela = await screen.findByRole('table', { name: 'Épicos' })
    const linha = within(tabela).getByText('TG-900').closest('tr') as HTMLElement
    // Sem amostra própria: traço, nunca a vazão do time; a legenda diz o porquê.
    expect(linha).toHaveTextContent('—')
    expect(linha).not.toHaveTextContent('semanas')
    expect(screen.getByText(/Traço na previsão: menos de 5 entregas do próprio épico/)).toBeInTheDocument()
  })

  it('epicoComAmostraMostraSemanas', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Epicos />, '/epicos')
    const tabela = await screen.findByRole('table', { name: 'Épicos' })
    expect(within(tabela).getByText('TG-910').closest('tr')).toHaveTextContent('3 semanas')
  })

  it('progressoComTextoENumeros', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Epicos />, '/epicos')
    const tabela = await screen.findByRole('table', { name: 'Épicos' })
    const linha = within(tabela).getByText('TG-900').closest('tr') as HTMLElement
    expect(linha).toHaveTextContent('67%')
    expect(linha).toHaveTextContent('4 de 6')
    expect(linha).toHaveTextContent('10/09/2026')
    // Sem pontos sincronizados: traço, nunca zero.
    expect(within(tabela).getByText('TG-910').closest('tr')).toHaveTextContent('—')
  })

  it('vencidosComSimboloETexto', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Epicos />, '/epicos')
    const tabela = await screen.findByRole('table', { name: 'Épicos' })
    expect(within(tabela).getByText('TG-900').closest('tr')).toHaveTextContent(/▲\s*1/)
  })

  it('contagemAbreGavetaDoEpico', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/EP-01/tasks': { status: 200, corpo: gaveta(6, 'card.ep01.titulo') } })
    renderizarTela(<Epicos />, '/epicos')
    await userEvent.click(await screen.findByRole('button', { name: 'TG-900 Itens: 6' }))
    const dialogo = await screen.findByRole('dialog')
    expect(await within(dialogo).findByText('6 tasks')).toBeInTheDocument()
    expect(String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/EP-01/tasks'))?.[0])).toContain('segmento=epico%3Aa1b2c3d4e5f6%3Aitens')
  })

  it('semCampoDeEpicoMostraAviso', async () => {
    simular({ [CARD]: { status: 200, corpo: { ...DTO, linhas: [], avisos: [{ codigo: 'EPICO_INDISPONIVEL', gravidade: 'informativo', mensagem: 'x' }] } } })
    renderizarTela(<Epicos />, '/epicos')
    expect(await screen.findByText(/Nenhuma task com épico/)).toBeInTheDocument()
    expect(screen.getByText('Nenhum épico na sua visão.')).toBeInTheDocument()
  })

  it('avisoBloqueanteMostraTraco', async () => {
    simular({ [CARD]: { status: 200, corpo: { ...DTO, avisos: [{ codigo: 'STATUS_SEM_MAPEAMENTO', gravidade: 'bloqueante', mensagem: 'x', params: { status: 'Exotico' } }] } } })
    renderizarTela(<Epicos />, '/epicos')
    const tabela = await screen.findByRole('table', { name: 'Épicos' })
    expect(within(tabela).queryAllByRole('button')).toHaveLength(0)
    expect(within(tabela).getByText('TG-900').closest('tr')).toHaveTextContent('—')
  })

  it('semPermissao403MostraMensagem', async () => {
    simular({ [CARD]: { status: 403 } })
    renderizarTela(<Epicos />, '/epicos')
    expect(await screen.findByRole('alert')).toHaveTextContent(/não permite ver estes dados/i)
  })
})
