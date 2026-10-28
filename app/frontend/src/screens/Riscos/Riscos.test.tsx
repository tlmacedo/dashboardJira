import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gaveta, renderizarTela, simular } from '../../test/telas'
import type { LinhaRisco, RiscosDto } from './api'
import { Riscos } from './Riscos'

const L101: LinhaRisco = {
  key: 'TG-101', resumo: 'Ajustar tela', tipo: 'Checklist', status: 'In Progress', responsavel: 'ana', pontos: 85, nivel: 'alto',
  fatorPrincipal: 'IDADE_ACIMA_P85',
  fatores: [
    { fator: 'IDADE_ACIMA_P85', pontos: 30 }, { fator: 'VENCIDA', pontos: 25 }, { fator: 'PARADA', pontos: 15 },
    { fator: 'SEM_ATUALIZACAO', pontos: 10 }, { fator: 'SEM_ESTIMATIVA', pontos: 5 },
  ],
}
const L103: LinhaRisco = {
  key: 'TG-103', resumo: 'Corrigir exportação', tipo: 'Checklist', status: 'On Hold', responsavel: 'carla', pontos: 63, nivel: 'alto',
  fatorPrincipal: 'PARADA',
  fatores: [
    { fator: 'PARADA', pontos: 15 }, { fator: 'BLOQUEADA', pontos: 15 }, { fator: 'ESTOURO_DE_HORAS', pontos: 15 },
    { fator: 'IDADE_ACIMA_P50', pontos: 10 }, { fator: 'VENCE_EM_BREVE', pontos: 8 },
  ],
}
const L102: LinhaRisco = {
  key: 'TG-102', resumo: 'Revisar contrato', tipo: 'Checklist Review', status: 'Open', responsavel: 'bruno', pontos: 30, nivel: 'medio',
  fatorPrincipal: 'IDADE_ACIMA_P85', fatores: [{ fator: 'IDADE_ACIMA_P85', pontos: 30 }],
}

const DTO: RiscosDto = {
  versaoRegras: 'risco v1',
  alto: 2,
  medio: 1,
  baixo: 1,
  emRisco: 3,
  limiteAlto: 60,
  limiteMedio: 30,
  linhas: [L101, L103, L102],
  sinaisPorPessoa: [
    { pessoa: 'ana', issuesEmRisco: 1, abertas: 1 },
    { pessoa: 'bruno', issuesEmRisco: 1, abertas: 1 },
    { pessoa: 'carla', issuesEmRisco: 1, abertas: 1 },
  ],
  avisos: [],
}

const CARD = '/api/cards/riscos'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Riscos', () => {
  it('riscosGavetaMostraPontosPorFator', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Riscos />, '/riscos')
    await userEvent.click(await screen.findByRole('button', { name: 'Pontos por fator de TG-101' }))
    const dialogo = await screen.findByRole('dialog', { name: /Pontos por fator de TG-101/ })
    const tabela = within(dialogo).getByRole('table')
    // 5 fatores, do maior para o menor, e o total de 85 pontos.
    const fatores = within(tabela).getAllByRole('row').slice(1, -1)
    expect(fatores).toHaveLength(5)
    expect(fatores[0]).toHaveTextContent('Idade acima do P85 do tipo')
    expect(fatores[0]).toHaveTextContent('30')
    expect(within(tabela).getAllByRole('row').at(-1)).toHaveTextContent(/Total\s*85/)
  })

  it('escFechaAGavetaDeFatoresEDevolveOFoco', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Riscos />, '/riscos')
    const botao = await screen.findByRole('button', { name: 'Pontos por fator de TG-101' })
    botao.focus()
    await userEvent.click(botao)
    await screen.findByRole('dialog')
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(botao).toHaveFocus()
  })

  it('niveisComSimboloETexto', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Riscos />, '/riscos')
    const tabela = await screen.findByRole('table', { name: 'Tasks em risco' })
    const linha = (k: string) => within(tabela).getByText(k).closest('tr') as HTMLElement
    expect(linha('TG-101')).toHaveTextContent(/▲\s*Alto/)
    expect(linha('TG-102')).toHaveTextContent(/●\s*Médio/)
    // O fator principal aparece em texto, não só um código.
    expect(linha('TG-103')).toHaveTextContent('Parada no status')
  })

  it('kpiAbreGavetaComMesmoTamanho', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/RI-01/tasks': { status: 200, corpo: gaveta(2, 'card.ri01.titulo') } })
    renderizarTela(<Riscos />, '/riscos')
    const kpis = await screen.findByRole('group', { name: 'Indicadores dos riscos' })
    await userEvent.click(within(kpis).getByRole('button', { name: /^Risco alto/ }))
    const dialogo = await screen.findByRole('dialog')
    expect(await within(dialogo).findByText('2 tasks')).toBeInTheDocument()
    expect(String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/RI-01/tasks'))?.[0])).toContain('segmento=nivel%3Aalto')
  })

  it('nivelBaixoFicaNoDetalhamento', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/RI-02/tasks': { status: 200, corpo: gaveta(1, 'card.ri02.titulo') } })
    renderizarTela(<Riscos />, '/riscos')
    await userEvent.click(await screen.findByRole('button', { name: /Ver as de risco baixo \(1\)/ }))
    await screen.findByRole('dialog')
    expect(String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/RI-02/tasks'))?.[0])).toContain('segmento=nivel%3Abaixo')
  })

  it('sinaisPorPessoaAlfabeticosSemPosicao', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Riscos />, '/riscos')
    const tabela = await screen.findByRole('table', { name: 'Sinais de risco por pessoa' })
    expect(within(tabela).getAllByRole('rowheader').map((c) => c.textContent)).toEqual(['ana', 'bruno', 'carla'])
    const cabecalhos = within(tabela).getAllByRole('columnheader').map((c) => c.textContent ?? '')
    expect(cabecalhos.some((c) => /^#$|posição|ranking|melhor|pior/i.test(c))).toBe(false)
    expect(screen.getByText(/Ordem alfabética, sem ranking/)).toBeInTheDocument()
  })

  it('mostraAVersaoDasRegras', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Riscos />, '/riscos')
    expect(await screen.findByText(/Regras: risco v1/)).toBeInTheDocument()
  })

  it('avisoBloqueanteMostraTraco', async () => {
    simular({ [CARD]: { status: 200, corpo: { ...DTO, avisos: [{ codigo: 'STATUS_SEM_MAPEAMENTO', gravidade: 'bloqueante', mensagem: 'x', params: { status: 'Exotico' } }] } } })
    renderizarTela(<Riscos />, '/riscos')
    const kpis = await screen.findByRole('group', { name: 'Indicadores dos riscos' })
    expect(within(kpis).getByText('Risco alto').closest('article')).toHaveTextContent('—')
    expect(within(kpis).queryAllByRole('button')).toHaveLength(0)
  })

  it('semPermissao403MostraMensagem', async () => {
    simular({ [CARD]: { status: 403 } })
    renderizarTela(<Riscos />, '/riscos')
    expect(await screen.findByRole('alert')).toHaveTextContent(/não permite ver estes dados/i)
  })
})
