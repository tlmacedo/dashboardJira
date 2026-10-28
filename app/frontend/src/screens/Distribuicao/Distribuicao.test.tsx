import { cleanup, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderizarTela, simular } from '../../test/telas'
import type { DistribuicaoDto, Fatores } from './api'
import { Distribuicao } from './Distribuicao'

const f = (pessoa: string, score: number): Fatores => ({ pessoa, carga: 0.25, wip: 0.17, wipCheio: 0, competencias: 0, bonus: 0, score })

const DTO: DistribuicaoDto = {
  modoSombra: true,
  versaoRegras: 'distribuicao v1',
  aguardando: 2,
  sugestoesPendentes: 1,
  jainAntes: 0.8,
  jainDepois: 0.95,
  sugestoes: [
    {
      issueKey: 'TG-301', resumo: 'Task pendente A', horas: 6, horasAssumidas: false, vence: '2026-11-10', comGerente: false, sugerido: 'carla',
      // O melhor score é da carla, que vem DEPOIS na lista: a tela não reordena por score.
      fatores: [f('ana', 0.9), f('bruno', 1.6), f('carla', 0.4)],
      decisao: null,
    },
    {
      issueKey: 'TG-302', resumo: 'Task pendente B', horas: 4, horasAssumidas: true, vence: null, comGerente: true, sugerido: null, fatores: [], decisao: 'APROVADA',
    },
  ],
  candidatos: ['ana', 'bruno', 'carla'],
  avisos: [],
}
const API = '/api/distribuicao'
const POST = '/api/distribuicao/decisao'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Distribuição', () => {
  it('faixaModoSombra', async () => {
    simular({ [API]: { status: 200, corpo: DTO } })
    renderizarTela(<Distribuicao />, '/distribuicao')
    await screen.findByText('TG-301 · Task pendente A')
    expect(screen.getByRole('note')).toHaveTextContent('Modo sombra: nada é alterado no Jira')
    cleanup()
    // A faixa também aparece quando a consulta falha e em inglês.
    localStorage.setItem('painel.idioma', 'en')
    simular({ [API]: { status: 403 } })
    renderizarTela(<Distribuicao />, '/distribuicao')
    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(screen.getByRole('note')).toHaveTextContent('Shadow mode: nothing is changed in Jira')
  })

  it('fatoresAlfabeticosSemPosicao', async () => {
    simular({ [API]: { status: 200, corpo: DTO } })
    renderizarTela(<Distribuicao />, '/distribuicao')
    const tabela = await screen.findByRole('table')
    const linhas = within(tabela).getAllByRole('row').slice(1)
    expect(linhas.map((l) => within(l).getAllByRole('rowheader')[0].textContent)).toEqual(['ana', 'bruno', 'carla ✓ sugerida'])
    // Nenhuma coluna de posição e nenhum cabeçalho ordenável.
    for (const c of within(tabela).getAllByRole('columnheader')) {
      expect(c.querySelector('button')).toBeNull()
      expect(c.textContent ?? '').not.toMatch(/^#$|posição|ranking|melhor|pior/i)
    }
    expect(screen.getByText(/não uma classificação de pessoas/)).toBeInTheDocument()
    // Sugerida em símbolo e texto (não só destaque de cor) e o bônus/competência como número.
    expect(screen.getByText('✓ Sugerido: carla')).toBeInTheDocument()
  })

  it('trocarExigeMotivo', async () => {
    const mock = simular({ [API]: { status: 200, corpo: DTO }, [POST]: { status: 200, corpo: { tipo: 'DISTRIBUICAO', issueKey: 'TG-301', decisao: 'TROCADA', escolhido: 'ana' } } })
    renderizarTela(<Distribuicao />, '/distribuicao')
    await screen.findByText('TG-301 · Task pendente A')
    const u = userEvent.setup()
    await u.click(screen.getByRole('button', { name: 'Trocar' }))
    const confirmar = screen.getByRole('button', { name: 'Confirmar troca' })
    expect(confirmar).toBeDisabled()
    // A lista de pessoas está em ordem alfabética e não repete a sugerida.
    const opcoes = within(screen.getByLabelText('Trocar para')).getAllByRole('option').map((o) => o.textContent)
    expect(opcoes).toEqual(['Escolha uma pessoa', 'ana', 'bruno'])
    await u.selectOptions(screen.getByLabelText('Trocar para'), 'ana')
    expect(confirmar).toBeDisabled() // ainda sem motivo
    await u.type(screen.getByLabelText('Motivo (obrigatório)'), '   ')
    expect(confirmar).toBeDisabled() // só espaço não vale
    await u.type(screen.getByLabelText('Motivo (obrigatório)'), 'ana conhece o módulo')
    expect(confirmar).toBeEnabled()
    await u.click(confirmar)
    const chamadas = () => mock.mock.calls as unknown as [RequestInfo | URL, RequestInit | undefined][]
    await waitFor(() => expect(chamadas().some((c) => String(c[0]) === POST)).toBe(true))
    const chamada = chamadas().find((c) => String(c[0]) === POST)!
    expect(JSON.parse(String(chamada[1]!.body))).toEqual({
      tipo: 'DISTRIBUICAO', issueKey: 'TG-301', sugerido: 'carla', decisao: 'TROCADA', escolhido: 'ana', motivo: 'ana conhece o módulo',
    })
    expect(chamada[1]!.method).toBe('POST')
  })

  it('decisaoDuplicadaMostraMensagem', async () => {
    simular({ [API]: { status: 200, corpo: DTO }, [POST]: { status: 409, corpo: { codigo: 'JA_DECIDIDA', params: {} } } })
    renderizarTela(<Distribuicao />, '/distribuicao')
    await screen.findByText('TG-301 · Task pendente A')
    await userEvent.setup().click(screen.getByRole('button', { name: 'Aprovar' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Esta sugestão já foi decidida.')
  })

  it('sugestaoQueMudouEPermissaoNegadaTemMensagensPropias', async () => {
    simular({ [API]: { status: 200, corpo: DTO }, [POST]: { status: 409, corpo: { codigo: 'SUGESTAO_MUDOU', params: {} } } })
    renderizarTela(<Distribuicao />, '/distribuicao')
    await screen.findByText('TG-301 · Task pendente A')
    await userEvent.setup().click(screen.getByRole('button', { name: 'Rejeitar' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('A sugestão mudou')
    cleanup()
    simular({ [API]: { status: 200, corpo: DTO }, [POST]: { status: 403 } })
    renderizarTela(<Distribuicao />, '/distribuicao')
    await screen.findByText('TG-301 · Task pendente A')
    await userEvent.setup().click(screen.getByRole('button', { name: 'Rejeitar' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Você não pode decidir')
  })

  it('decisaoJaRegistradaSaiDosBotoesEVaiEmTexto', async () => {
    simular({ [API]: { status: 200, corpo: DTO } })
    renderizarTela(<Distribuicao />, '/distribuicao')
    await screen.findByText('TG-302 · Task pendente B')
    // A TG-302 já foi aprovada hoje: estado em símbolo + texto, sem botões; a TG-301 ainda tem os três.
    expect(screen.getByText('✓ Aprovada')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Aprovar' })).toHaveLength(1)
    expect(screen.getByText('■ Ninguém tem capacidade disponível para esta task.')).toBeInTheDocument()
  })

  it('indicadoresEBotaoAprovarSemSugestao', async () => {
    simular({ [API]: { status: 200, corpo: { ...DTO, sugestoes: [{ ...DTO.sugestoes[1], decisao: null }] } } })
    renderizarTela(<Distribuicao />, '/distribuicao')
    await screen.findByText('TG-302 · Task pendente B')
    // Sem pessoa sugerida não há o que aprovar: só trocar ou rejeitar.
    expect(screen.getByRole('button', { name: 'Aprovar' })).toBeDisabled()
    expect(screen.getByText(/0,80 → 0,95/)).toBeInTheDocument()
    expect(screen.getByText('Com o gerente')).toBeInTheDocument()
  })
})
