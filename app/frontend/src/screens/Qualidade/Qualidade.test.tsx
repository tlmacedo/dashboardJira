import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gaveta, renderizarTela, simular } from '../../test/telas'
import type { LinhaQualidade, QualidadeDto } from './api'
import { Qualidade } from './Qualidade'

const l = (verificacao: string, n: number, universo: number, estado: string, nomes: string[] = []): LinhaQualidade => ({
  verificacao, n, universo, fracao: universo > 0 ? n / universo : null, estado, nomes,
})

const DTO: QualidadeDto = {
  versaoRegras: 'qualidade v1',
  janelaDias: 90,
  linhas: [
    l('SEM_ESTIMATIVA', 2, 5, 'problema'),
    l('SEM_RESPONSAVEL', 1, 5, 'atencao'),
    l('SEM_VENCIMENTO', 0, 5, 'ok'),
    { verificacao: 'SEM_EPICO', n: 0, universo: 0, fracao: null, estado: 'indisponivel', nomes: ['Epic Link'] },
    l('EM_ANDAMENTO_SEM_HORAS', 1, 2, 'problema'),
    l('SEM_SQUAD', 1, 4, 'atencao'),
    l('PRIORIDADE_PADRAO', 3, 5, 'problema'),
    l('DONE_SEM_RESOLUTION', 1, 6, 'atencao'),
    l('RESOLUTION_SEM_DONE', 1, 6, 'atencao'),
    l('STATUS_SEM_MAPEAMENTO', 1, 12, 'problema', ['Weird']),
    l('CAMPO_INDISPONIVEL', 2, 4, 'atencao', ['Epic Link', 'Flagged']),
    l('LABEL_SEM_CLASSIFICACAO', 1, 2, 'neutro', ['MYSTERY']),
    l('BLOQUEADA_SEM_CAUSA', 1, 1, 'problema'),
  ],
  labels: [{ label: 'MYSTERY', n: 1 }],
  avisos: [],
}
const API = '/api/cards/qualidade'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Qualidade dos dados', () => {
  it('estadoComSimboloETexto', async () => {
    simular({ [API]: { status: 200, corpo: DTO } })
    renderizarTela(<Qualidade />, '/qualidade')
    const tabela = await screen.findByRole('table', { name: 'Abertas com dado faltando' })
    const linhas = within(tabela).getAllByRole('row').slice(1)
    expect(linhas[0]).toHaveTextContent('Sem estimativa')
    expect(linhas[0]).toHaveTextContent('2 / 5')
    expect(linhas[0]).toHaveTextContent('40%')
    expect(linhas[0]).toHaveTextContent('▲ Problema')
    expect(linhas[1]).toHaveTextContent('● Atenção')
    expect(linhas[2]).toHaveTextContent('✓ Dentro do esperado')
    // Campo sem valor em nenhuma issue: indisponível, sem fingir 100%; com o nome do campo.
    expect(linhas[3]).toHaveTextContent('Sem épico')
    expect(linhas[3]).toHaveTextContent('Indisponível')
    expect(linhas[3]).toHaveTextContent('Epic Link')
    expect(linhas[3]).not.toHaveTextContent('%')
    // Vocabulário: o nome do status fora do mapa aparece; o label sem classificação é só informativo (■).
    const vocab = await screen.findByRole('table', { name: 'Vocabulário do Jira' })
    expect(vocab).toHaveTextContent('Weird')
    expect(vocab).toHaveTextContent('■ Informativo')
    expect(screen.getByRole('table', { name: 'Campos sem valor' })).toHaveTextContent('Epic Link, Flagged')
  })

  it('numeroAbreAGavetaDoMesmoCartao', async () => {
    const mock = simular({ [API]: { status: 200, corpo: DTO }, '/api/cartoes/QD-01/tasks': { status: 200, corpo: gaveta(2, 'card.qd01.titulo') } })
    renderizarTela(<Qualidade />, '/qualidade')
    await screen.findByRole('table', { name: 'Abertas com dado faltando' })
    await userEvent.setup().click(screen.getByRole('button', { name: 'Sem estimativa: 2' }))
    expect(await screen.findByRole('dialog')).toBeInTheDocument()
    expect(mock.mock.calls.map((c) => String(c[0])).some((u) => u.includes('/api/cartoes/QD-01/tasks') && u.includes('segmento=SEM_ESTIMATIVA'))).toBe(true)
    // Zero não abre nada; campos sem valor não têm gaveta.
    expect(screen.queryByRole('button', { name: /Sem vencimento/ })).toBeNull()
    expect(screen.queryByRole('button', { name: /Campo sem valor/ })).toBeNull()
  })

  it('labelAbreAGavetaDoLabel', async () => {
    const mock = simular({ [API]: { status: 200, corpo: DTO }, '/api/cartoes/QD-03/tasks': { status: 200, corpo: gaveta(1, 'card.qd03.titulo') } })
    renderizarTela(<Qualidade />, '/qualidade')
    await screen.findByRole('table', { name: 'Vocabulário do Jira' })
    expect(screen.getByText(/O gestor|Para classificar/)).toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole('button', { name: 'MYSTERY: 1' }))
    await screen.findByRole('dialog')
    expect(mock.mock.calls.map((c) => decodeURIComponent(String(c[0]))).some((u) => u.includes('segmento=label:MYSTERY'))).toBe(true)
  })

  it('semPermissaoEmInglesTambemMostraMensagemDePapel', async () => {
    localStorage.setItem('painel.idioma', 'en')
    simular({ [API]: { status: 403 } })
    renderizarTela(<Qualidade />, '/qualidade')
    expect(await screen.findByRole('alert')).toBeInTheDocument()
    cleanup()
    simular({ [API]: { status: 200, corpo: DTO } })
    renderizarTela(<Qualidade />, '/qualidade')
    expect(await screen.findByRole('table', { name: 'Open tasks with missing data' })).toHaveTextContent('No estimate')
  })
})
