import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gaveta, renderizarTela, simular } from '../../test/telas'
import type { BloqueiosDto } from './api'
import { Bloqueios } from './Bloqueios'

const DTO: BloqueiosDto = {
  bloqueadas: 4,
  bloqueadoras: 2,
  bloqueadorasForaDaVisao: 1,
  prazoEmConflito: 1,
  verificarNoJira: 1,
  semCausa: 2,
  tipoDeLink: 'Blocks',
  linhas: [
    {
      bloqueada: 'TG-801', resumo: 'Ajustar tela', tipo: 'Checklist', status: 'In Progress', vence: '2026-10-28',
      bloqueadora: { estado: 'VISIVEL', key: 'TG-802', vence: '2026-11-03', conflito: true, semVencimento: false },
    },
    {
      bloqueada: 'TG-803', resumo: 'Corrigir exportação', tipo: 'Checklist', status: 'Open', vence: '2026-11-02',
      bloqueadora: { estado: 'OUTRO_SQUAD', key: null, vence: null, conflito: false, semVencimento: true },
    },
    {
      bloqueada: 'TG-811', resumo: 'Revisar contrato', tipo: 'Checklist', status: 'Open', vence: '2026-11-05',
      bloqueadora: { estado: 'VISIVEL', key: 'TG-812', vence: '2026-11-04', conflito: false, semVencimento: false },
    },
    {
      bloqueada: 'TG-809', resumo: 'Atualizar traduções', tipo: 'Checklist', status: 'In Progress', vence: null,
      bloqueadora: { estado: 'FORA_DA_BASE', key: 'TG-9999', vence: null, conflito: false, semVencimento: false },
    },
  ],
  avisos: [],
}

const CARD = '/api/cards/bloqueios'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Bloqueios entre tasks', () => {
  it('cartoesComOsCincoNumeros', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Bloqueios />, '/bloqueios')
    const kpis = await screen.findByRole('group', { name: 'Indicadores dos bloqueios' })
    const cartao = (t: string) => within(kpis).getByText(t).closest('article') as HTMLElement
    expect(cartao('Bloqueadas')).toHaveTextContent('4')
    expect(cartao('Bloqueadoras')).toHaveTextContent('2')
    expect(cartao('Prazo em conflito')).toHaveTextContent('1')
    expect(cartao('Sem causa registrada')).toHaveTextContent('2')
    expect(cartao('Verificar no Jira')).toHaveTextContent('1')
  })

  it('bloqueadoraForaDaVisaoDizOutroSquadSemChaveNemNome', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Bloqueios />, '/bloqueios')
    const tabela = await screen.findByRole('table', { name: 'Bloqueios entre tasks (lista)' })
    const linha = within(tabela).getByText('TG-803').closest('tr') as HTMLElement
    expect(linha).toHaveTextContent('task de outro squad')
    // Nenhuma chave de bloqueadora nessa linha além da própria bloqueada.
    expect(linha.textContent?.match(/TG-\d+/g)).toEqual(['TG-803'])
    // O cartão das bloqueadoras avisa quantas ficaram de fora da visão.
    const kpis = screen.getByRole('group', { name: 'Indicadores dos bloqueios' })
    expect(within(kpis).getByText('Bloqueadoras').closest('article')).toHaveTextContent('1 de outro squad')
  })

  it('conflitoDePrazoTemSimboloETexto', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Bloqueios />, '/bloqueios')
    const tabela = await screen.findByRole('table', { name: 'Bloqueios entre tasks (lista)' })
    const conflito = within(tabela).getByText('TG-801').closest('tr') as HTMLElement
    expect(conflito).toHaveTextContent(/▲\s*vence depois da bloqueada/)
    expect(conflito).toHaveTextContent('03/11/2026')
    // Sem vencimento na bloqueadora: aviso informativo (●), não conflito.
    expect(within(tabela).getByText('TG-803').closest('tr')).toHaveTextContent(/●\s*sem vencimento/)
    // Bloqueadora que vence antes: nenhum sinal.
    expect(within(tabela).getByText('TG-811').closest('tr')).not.toHaveTextContent('▲')
    // Cartão do conflito: símbolo + texto.
    const kpis = screen.getByRole('group', { name: 'Indicadores dos bloqueios' })
    expect(within(kpis).getByText('Prazo em conflito').closest('article')).toHaveTextContent(/▲\s*1 com a bloqueadora vencendo depois/)
  })

  it('pontaForaDaBaseViraVerificarNoJira', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Bloqueios />, '/bloqueios')
    const tabela = await screen.findByRole('table', { name: 'Bloqueios entre tasks (lista)' })
    const linha = within(tabela).getByText('TG-809').closest('tr') as HTMLElement
    expect(linha).toHaveTextContent('TG-9999 (fora da base: verificar no Jira)')
  })

  it('semRankingNemOrdenacaoPorBloqueadora', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizarTela(<Bloqueios />, '/bloqueios')
    const tabela = await screen.findByRole('table', { name: 'Bloqueios entre tasks (lista)' })
    // A ordem é a da API (vencimento da bloqueada); os cabeçalhos não viram botão de ordenação e não há coluna de posição.
    expect(within(tabela).queryAllByRole('button')).toHaveLength(0)
    expect(within(tabela).getAllByRole('columnheader').map((c) => c.textContent)).toEqual([
      'Bloqueada', 'Bloqueadora', 'Vence (bloqueada)', 'Vence (bloqueadora)', 'Sinal',
    ])
    expect(within(tabela).getAllByRole('row').slice(1).map((r) => r.querySelector('.mono')?.textContent)).toEqual(['TG-801', 'TG-803', 'TG-811', 'TG-809'])
    expect(screen.getByText(/Sem ranking de bloqueadoras/)).toBeInTheDocument()
  })

  it('cadaCartaoAbreSuasTasks', async () => {
    const rotas: Record<string, { status: number; corpo: unknown }> = {}
    rotas[CARD] = { status: 200, corpo: DTO }
    for (const id of ['BQ-01', 'BQ-02', 'BQ-03', 'BQ-04', 'BQ-05']) rotas[`/api/cartoes/${id}/tasks`] = { status: 200, corpo: gaveta(1, `card.${id.replace('-', '').toLowerCase()}.titulo`) }
    const mock = simular(rotas)
    renderizarTela(<Bloqueios />, '/bloqueios')
    const kpis = await screen.findByRole('group', { name: 'Indicadores dos bloqueios' })
    const esperado: Array<[RegExp, string, string]> = [
      [/^Bloqueadas/, 'BQ-01', 'bloqueadas'], [/^Bloqueadoras/, 'BQ-02', 'bloqueadoras'], [/^Prazo em conflito/, 'BQ-03', 'prazoEmConflito'],
      [/^Sem causa registrada/, 'BQ-04', 'semCausa'], [/^Verificar no Jira/, 'BQ-05', 'verificarNoJira'],
    ]
    for (const [nome, cartao, segmento] of esperado) {
      await userEvent.click(within(kpis).getByRole('button', { name: nome }))
      await screen.findByRole('dialog')
      const url = mock.mock.calls.map((c) => String(c[0])).filter((u) => u.startsWith(`/api/cartoes/${cartao}/tasks`)).at(-1)
      expect(url).toContain(`segmento=${segmento}`)
      await userEvent.keyboard('{Escape}')
    }
  })

  it('semBloqueioMostraMensagemEZeros', async () => {
    simular({ [CARD]: { status: 200, corpo: { ...DTO, bloqueadas: 0, bloqueadoras: 0, bloqueadorasForaDaVisao: 0, prazoEmConflito: 0, verificarNoJira: 0, semCausa: 0, linhas: [] } } })
    renderizarTela(<Bloqueios />, '/bloqueios')
    expect(await screen.findByText('Nenhuma task bloqueada por outra task aberta neste recorte.')).toBeInTheDocument()
    const kpis = screen.getByRole('group', { name: 'Indicadores dos bloqueios' })
    expect(within(kpis).queryAllByRole('button')).toHaveLength(0)
    expect(within(kpis).getByText('Prazo em conflito').closest('article')).toHaveTextContent(/✓\s*Nenhum conflito de prazo/)
  })

  it('avisoBloqueanteMostraTracoENadaClicavel', async () => {
    const aviso = { codigo: 'STATUS_SEM_MAPEAMENTO', gravidade: 'bloqueante', mensagem: 'x', params: { status: 'Exotico' } }
    simular({ [CARD]: { status: 200, corpo: { ...DTO, avisos: [aviso] } } })
    renderizarTela(<Bloqueios />, '/bloqueios')
    const kpis = await screen.findByRole('group', { name: 'Indicadores dos bloqueios' })
    expect(within(kpis).getByText('Bloqueadas').closest('article')).toHaveTextContent('—')
    expect(within(kpis).queryAllByRole('button')).toHaveLength(0)
  })

  it('avisoDePontaForaDaBaseTemTexto', async () => {
    const aviso = { codigo: 'BLOQUEIO_FORA_DA_BASE', gravidade: 'informativo', mensagem: 'x', params: { n: '1' } }
    simular({ [CARD]: { status: 200, corpo: { ...DTO, avisos: [aviso] } } })
    renderizarTela(<Bloqueios />, '/bloqueios')
    expect(await screen.findByText(/1 task\(s\) têm bloqueadora fora da base/)).toBeInTheDocument()
  })

  it('semPermissao403MostraMensagem', async () => {
    simular({ [CARD]: { status: 403 } })
    renderizarTela(<Bloqueios />, '/bloqueios')
    expect(await screen.findByRole('alert')).toHaveTextContent(/não permite ver estes dados/i)
  })
})
