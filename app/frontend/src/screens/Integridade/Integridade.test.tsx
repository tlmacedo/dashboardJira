import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gaveta, renderizarTela, simular } from '../../test/telas'
import { SINAIS, type CatalogoIntegridade, type IntegridadeDto } from './api'
import { Integridade } from './Integridade'

const porSinal = SINAIS.map((s) => ({ sinal: s, gravidade: s === 'PULOU_O_FLUXO' ? 3 : 2, ativo: s !== 'ESTIMATIVA_AUMENTADA', n: s === 'FECHADA_POR_OUTRO' ? 2 : 0 }))

const DTO: IntegridadeDto = {
  aviso: 'INDICIO_NAO_PROVA',
  avisoDpo: 'VALIDAR_BASE_LEGAL_COM_DPO',
  versaoRegras: 'integridade v1',
  de: '2026-07-30',
  ate: '2026-10-28',
  resumo: {
    evidencias: 4, issuesAfetadas: 3, medianaTaxa: 0.1, porSinal,
    // Fora da ordem alfabética de propósito: a tela deve ordenar por nome, nunca por pontos.
    pessoas: [
      { pessoa: 'carla', nivel: 'ALTO', pontos: 20, concluidas: 10, taxa: 2, multiplo: 20, graves: 3, evidencias: 1 },
      { pessoa: 'bruno', nivel: 'MEDIO', pontos: 6, concluidas: 12, taxa: 0.5, multiplo: 5, graves: 0, evidencias: 3 },
    ],
    pessoasAlto: 1, pessoasMedio: 1, evidenciasNivelAlto: 1, evidenciasNivelMedio: 3, impactoMetasPp: 6.5, taxaNoPrazo: 0.8, taxaOriginal: 0.7,
    pontosAlto: 12, multiploAlto: 3, gravesAlto: 3, pontosMedio: 6, multiploMedio: 2, concluidasMinimas: 8,
  },
  evidencias: [
    { sinal: 'FECHADA_POR_OUTRO', gravidade: 2, key: 'TG-801', pessoa: 'bruno', quando: '2026-10-14T13:00:00Z', dia: '2026-10-14', detalhe: { responsavel: 'ana' } },
    { sinal: 'PULOU_O_FLUXO', gravidade: 3, key: 'TG-802', pessoa: null, quando: '2026-10-15T13:00:00Z', dia: '2026-10-15', detalhe: {} },
  ],
  avisos: [],
}
const CATALOGO: CatalogoIntegridade = {
  aviso: 'INDICIO_NAO_PROVA', avisoDpo: 'VALIDAR_BASE_LEGAL_COM_DPO', versaoRegras: 'integridade v1',
  sinais: SINAIS.map((s) => ({ sinal: s, gravidade: 2, ativo: true, params: { horas: '2', minutos: '5', quantidade: '5', pct: '20', dias: '2', hora: '17', labels: 'x' } })),
  niveis: { pontosAlto: 12, multiploAlto: 3, gravesAlto: 3, pontosMedio: 6, multiploMedio: 2, concluidasMinimas: 8, pisoMediana: 0.05 },
}
const API = '/api/integridade'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Integridade', () => {
  it('faixasFixasPtEn', async () => {
    simular({ [API]: { status: 200, corpo: DTO } })
    renderizarTela(<Integridade />, '/integridade')
    await screen.findByRole('table', { name: 'Evidências por sinal' })
    const notas = screen.getAllByRole('note')
    expect(notas[0]).toHaveTextContent('Indício, não prova')
    expect(notas[1]).toHaveTextContent('Validar a base legal com o DPO antes do uso real')
    cleanup()
    localStorage.setItem('painel.idioma', 'en')
    simular({ [API]: { status: 200, corpo: DTO } })
    renderizarTela(<Integridade />, '/integridade')
    await screen.findByRole('table', { name: 'Evidence per signal' })
    const en = screen.getAllByRole('note')
    expect(en[0]).toHaveTextContent('A hint, not proof')
    expect(en[1]).toHaveTextContent('Validate the legal basis with the DPO before real use')
  })

  it('faixasFixasTambemNosEstadosDeErro', async () => {
    simular({ [API]: { status: 500 } })
    renderizarTela(<Integridade />, '/integridade')
    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível carregar')
    expect(screen.getAllByRole('note')).toHaveLength(2)
  })

  it('pessoasAlfabeticasSemColunaDePontosOrdenavel', async () => {
    simular({ [API]: { status: 200, corpo: DTO } })
    renderizarTela(<Integridade />, '/integridade')
    const tabela = await screen.findByRole('table', { name: 'Pessoas com indício alto ou médio' })
    const linhas = within(tabela).getAllByRole('row').slice(1)
    expect(linhas[0]).toHaveTextContent('bruno')
    expect(linhas[1]).toHaveTextContent('carla')
    // Nível em símbolo + texto.
    expect(linhas[0]).toHaveTextContent('● Indício médio')
    expect(linhas[1]).toHaveTextContent('▲ Indício alto')
    // Nenhum cabeçalho é botão (nada de ordenar por pontos) nem ranking.
    expect(within(tabela).queryAllByRole('button', { name: /pontos|ordenar/i })).toHaveLength(0)
    for (const c of within(tabela).getAllByRole('columnheader')) {
      expect(c.querySelector('button')).toBeNull()
      expect(c.textContent ?? '').not.toMatch(/^#$|posição|ranking|melhor|pior/i)
    }
    expect(screen.getByText(/Ordem alfabética, sem ranking/)).toBeInTheDocument()
  })

  it('semPermissaoMostraMensagemDePapel', async () => {
    simular({ [API]: { status: 403 } })
    renderizarTela(<Integridade />, '/integridade')
    expect(await screen.findByRole('alert')).toHaveTextContent('Seu papel não permite ver estes dados')
    expect(screen.queryByRole('button', { name: 'Tentar de novo' })).toBeNull()
  })

  it('aguardandoDpoMostraMensagemPropria', async () => {
    simular({ [API]: { status: 403, corpo: { codigo: 'AGUARDANDO_ACEITE_DPO' } } })
    renderizarTela(<Integridade />, '/integridade')
    expect(await screen.findByRole('alert')).toHaveTextContent('desligada até o encarregado de dados (DPO)')
    expect(screen.getAllByRole('note')).toHaveLength(2)
  })

  it('filtroInvalidoMostraMensagem', async () => {
    simular({ [API]: { status: 400 } })
    renderizarTela(<Integridade />, '/integridade')
    expect(await screen.findByRole('alert')).toHaveTextContent('Algum filtro é inválido')
  })

  it('contagemAbreAGavetaComOSegmentoDoSinal', async () => {
    const mock = simular({ [API]: { status: 200, corpo: DTO }, '/api/cartoes/IN-05/tasks': { status: 200, corpo: gaveta(2, 'card.in05.titulo') } })
    renderizarTela(<Integridade />, '/integridade')
    const tabela = await screen.findByRole('table', { name: 'Evidências por sinal' })
    await userEvent.click(within(tabela).getByRole('button', { name: /Fechada por quem não era o responsável: 2/ }))
    expect(await screen.findByRole('dialog')).toBeInTheDocument()
    const url = decodeURIComponent(String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/IN-05/tasks'))?.[0]))
    expect(url).toContain('segmento=sinal:FECHADA_POR_OUTRO')
  })

  it('catalogoSoCarregaQuandoAberto', async () => {
    const mock = simular({ [API]: { status: 200, corpo: DTO }, '/api/integridade/catalogo': { status: 200, corpo: CATALOGO } })
    renderizarTela(<Integridade />, '/integridade')
    await screen.findByRole('table', { name: 'Evidências por sinal' })
    expect(mock.mock.calls.some((c) => String(c[0]).startsWith('/api/integridade/catalogo'))).toBe(false)
    await userEvent.click(screen.getByText('Ver o catálogo de sinais'))
    expect(await screen.findByRole('table', { name: 'Catálogo de sinais' })).toBeInTheDocument()
    expect(mock.mock.calls.some((c) => String(c[0]).startsWith('/api/integridade/catalogo'))).toBe(true)
  })

  it('evidenciaSemAutorNaoMostraNull', async () => {
    simular({ [API]: { status: 200, corpo: DTO } })
    renderizarTela(<Integridade />, '/integridade')
    const tabela = await screen.findByRole('table', { name: 'Tabela de evidências' })
    expect(within(tabela).getByText('TG-802').closest('tr')).toHaveTextContent('autor desconhecido')
    expect(document.body.textContent).not.toMatch(/\bnull\b|undefined/)
  })
})
