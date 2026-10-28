import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { LangProvider } from '../../i18n/LangProvider'
import en from '../../i18n/en.json'
import pt from '../../i18n/pt.json'
import type { TasksDoCartao } from '../../shared/tasksApi'
import { Auditoria } from './Auditoria'
import { CODIGOS_DE_SINAL, type AuditoriaDto, type HistoricoDto, type Sinal } from './api'

const sinal = (extra: Partial<Sinal>): Sinal => ({
  codigo: 'VENCIMENTO_ALTERADO_NAO_AUTORIZADO',
  resultado: 'auditoria',
  gravidade: 3,
  key: 'TG-10',
  resumo: 'Ajustar tela',
  campo: 'duedate',
  de: '2026-10-15',
  para: '2026-10-20',
  at: '2026-10-10T12:00:00Z',
  dia: '2026-10-10',
  autor: 'ana',
  autorizado: false,
  motivo: 'PROPRIO_RESPONSAVEL',
  responsavel: 'ana',
  emLote: null,
  ...extra,
})

const SINAIS: Sinal[] = [
  sinal({}),
  sinal({ codigo: 'PASSA_BOLA', key: 'TG-11', campo: 'assignee', de: 'bruno', para: 'ana', autor: 'pl.alfa', autorizado: true, motivo: 'REPORTER', responsavel: 'bruno', at: '2026-10-09T12:00:00Z', dia: '2026-10-09' }),
  sinal({ codigo: 'PASSA_BOLA', key: 'TG-12', campo: 'assignee', de: 'ana', para: 'carla', autor: 'carla', motivo: 'NAO_AUTORIZADO', emLote: 6, at: '2026-10-08T12:00:00Z', dia: '2026-10-08' }),
  sinal({ codigo: 'VENCIMENTO_TASK_REVIEW_DIVERGENTE', resultado: 'alerta', gravidade: 1, key: 'TG-113', resumo: 'Review', autor: 'bruno', motivo: 'NAO_AUTORIZADO', responsavel: 'ana', at: '2026-10-07T12:00:00Z', dia: '2026-10-07' }),
]

const contagem = (codigo: string) => SINAIS.filter((s) => s.codigo === codigo).length

const DTO: AuditoriaDto = {
  aviso: 'INDICIO_NAO_PROVA',
  versaoRegras: 'regras v1, labels v1',
  total: SINAIS.length,
  porSinal: CODIGOS_DE_SINAL.map((c) => ({
    codigo: c,
    resultado: c === 'VENCIMENTO_TASK_REVIEW_DIVERGENTE' ? 'alerta' : 'auditoria',
    gravidade: c === 'VENCIMENTO_TASK_REVIEW_DIVERGENTE' ? 1 : 3,
    n: contagem(c),
  })),
  sinais: SINAIS,
  noPrazo: { concluidas: 4, noPrazo: 2, noPrazoAjustado: 3, justificadasSobAuditoria: 1, taxa: 0.5, taxaAjustada: 0.75 },
  avisos: [],
}

const HISTORICO: HistoricoDto = {
  aviso: 'INDICIO_NAO_PROVA',
  key: 'TG-10',
  resumo: 'Ajustar tela',
  reporter: 'pl.alfa',
  linhas: [
    { at: '2026-10-10T12:00:00Z', campo: 'duedate', de: '2026-10-15', para: '2026-10-20', autor: 'ana', autorizado: false, motivo: 'PROPRIO_RESPONSAVEL', valorGuardado: true },
    { at: '2026-10-11T12:00:00Z', campo: 'description', de: null, para: null, autor: 'ana', autorizado: null, motivo: null, valorGuardado: false },
  ],
  avisos: [],
}

const tasks = (n: number): TasksDoCartao => ({
  tituloChave: 'card.au02.titulo',
  definicaoChave: 'card.au02.oQueMostra',
  total: n,
  colunas: ['chave', 'resumo', 'responsavel', 'status', 'detalhe'],
  linkJira: null,
  linhas: Array.from({ length: n }, (_, i) => ({ key: 'TG-11', resumo: `Task ${i}`, responsavel: 'ana', status: 'Em andamento', detalhe: `Passa-bola ${i}` })),
})

type Resp = { status: number; corpo?: unknown }

function simular(rotas: Record<string, Resp>) {
  const mock = vi.fn(async (entrada: RequestInfo | URL) => {
    const caminho = String(entrada).split('?')[0]
    const r = rotas[caminho]
    if (!r) return new Response('', { status: 404 })
    return new Response(r.corpo === undefined ? null : JSON.stringify(r.corpo), { status: r.status })
  })
  vi.stubGlobal('fetch', mock)
  return mock
}

function renderizar(inicial = '/auditoria') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <LangProvider>
        <MemoryRouter initialEntries={[inicial]}>
          <Routes>
            <Route path="/auditoria" element={<Auditoria />} />
          </Routes>
        </MemoryRouter>
      </LangProvider>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  localStorage.clear()
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

const SINAIS_URL = '/api/auditoria/sinais'
const HIST_URL = '/api/auditoria/historico/TG-10'

describe('tela Auditoria', () => {
  it('mostraAvisoFixo', async () => {
    simular({ [SINAIS_URL]: { status: 200, corpo: DTO } })
    renderizar()
    const faixa = await screen.findByRole('note')
    expect(faixa).toHaveTextContent('Indício, não prova: a tela mostra alterações para conferir, não conclusões sobre pessoas.')
  })

  it('mostraAvisoFixoEmIngles', async () => {
    localStorage.setItem('painel.idioma', 'en')
    simular({ [SINAIS_URL]: { status: 200, corpo: DTO } })
    renderizar()
    expect(await screen.findByRole('note')).toHaveTextContent('Evidence, not proof:')
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(/changes/i)
  })

  it('simbolosAlemDaCor', async () => {
    simular({ [SINAIS_URL]: { status: 200, corpo: DTO } })
    renderizar()
    const lista = await screen.findByRole('table', { name: 'Sinais do período' })
    const linha = (chave: string) => within(lista).getByRole('button', { name: chave }).closest('tr') as HTMLElement
    // não autorizado: ▲ + texto
    expect(linha('TG-10')).toHaveTextContent(/▲\s*não autorizado/)
    // autorizado: ✓ + texto (e o motivo)
    expect(linha('TG-11')).toHaveTextContent(/✓\s*autorizado/)
    expect(linha('TG-11')).toHaveTextContent('Reporter da task')
    // alerta: ● + texto
    expect(linha('TG-113')).toHaveTextContent(/●\s*Alerta/)
    // auditoria: ▲ + texto
    expect(linha('TG-10')).toHaveTextContent(/▲\s*Auditoria/)
  })

  it('kpisNoPrazoEAjustadoLadoALado', async () => {
    simular({ [SINAIS_URL]: { status: 200, corpo: DTO } })
    renderizar()
    const noPrazo = await screen.findByRole('button', { name: /^No prazo\b(?! ajustado)/ })
    const ajustado = screen.getByRole('button', { name: /No prazo ajustado/ })
    expect(noPrazo).toHaveTextContent('50%')
    expect(ajustado).toHaveTextContent('75%')
    // lado a lado: o mesmo grupo de indicadores
    expect(noPrazo.closest('[role="group"]')).toBe(ajustado.closest('[role="group"]'))
    expect(screen.getByRole('button', { name: /Justificadas sob auditoria/ })).toHaveTextContent('1')
    // sinais de auditoria (▲) e alertas (●)
    expect(screen.getByRole('button', { name: /Sinais de auditoria/ })).toHaveTextContent('3')
    expect(screen.getByRole('button', { name: /^Alertas/ })).toHaveTextContent('1')
  })

  it('semConcluidasMostraTracoNoPrazo', async () => {
    simular({
      [SINAIS_URL]: {
        status: 200,
        corpo: { ...DTO, noPrazo: { concluidas: 0, noPrazo: 0, noPrazoAjustado: 0, justificadasSobAuditoria: 0, taxa: null, taxaAjustada: null } },
      },
    })
    renderizar()
    const noPrazo = await screen.findByRole('group', { name: 'Indicadores' })
    expect(within(noPrazo).getAllByText('—').length).toBeGreaterThanOrEqual(2)
  })

  it('clicarNoSinalAbreHistorico', async () => {
    const mock = simular({ [SINAIS_URL]: { status: 200, corpo: DTO }, [HIST_URL]: { status: 200, corpo: HISTORICO } })
    renderizar()
    const lista = await screen.findByRole('table', { name: 'Sinais do período' })
    await userEvent.click(within(lista).getByRole('button', { name: 'TG-10' }))
    const gaveta = await screen.findByRole('dialog')
    await within(gaveta).findByText('2026-10-15')
    expect(mock.mock.calls.some((c) => String(c[0]) === HIST_URL)).toBe(true)
    // linhas == DTO (cabeçalho + 2)
    expect(within(gaveta).getAllByRole('row')).toHaveLength(1 + HISTORICO.linhas.length)
    // campo sem valor guardado: texto de privacidade
    expect(within(gaveta).getByText('valor não guardado (privacidade)')).toBeInTheDocument()
    // o aviso fixo também vale na gaveta
    expect(within(gaveta).getByRole('note')).toHaveTextContent('Indício, não prova')
    // Esc fecha e devolve o foco à chave
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(within(lista).getByRole('button', { name: 'TG-10' })).toHaveFocus()
  })

  it('drillMesmoTamanho', async () => {
    const mock = simular({ [SINAIS_URL]: { status: 200, corpo: DTO }, '/api/cartoes/AU-02/tasks': { status: 200, corpo: tasks(2) } })
    renderizar('/auditoria?squad=Alfa')
    const tabela = await screen.findByRole('table', { name: 'Sinais por tipo' })
    const passaBola = within(tabela).getByRole('button', { name: /Passa-bola/ })
    expect(passaBola).toHaveTextContent('2')
    await userEvent.click(passaBola)
    const gaveta = await screen.findByRole('dialog')
    await within(gaveta).findAllByText('TG-11')
    expect(within(gaveta).getAllByRole('row')).toHaveLength(1 + 2)
    const url = String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/AU-02/tasks'))?.[0])
    expect(url).toContain('segmento=sinal%3APASSA_BOLA')
    // os mesmos filtros da tela vão para a gaveta
    expect(url).toContain('squad=Alfa')
  })

  it('tabelaPorSinalEmOrdemFixaComGravidadeEmTexto', async () => {
    simular({ [SINAIS_URL]: { status: 200, corpo: DTO } })
    renderizar()
    const tabela = await screen.findByRole('table', { name: 'Sinais por tipo' })
    const nomes = within(tabela).getAllByRole('rowheader').map((c) => c.textContent)
    expect(nomes).toHaveLength(CODIGOS_DE_SINAL.length)
    expect(nomes[0]).toBe('Vencimento alterado por quem não podia')
    expect(within(tabela).getAllByText('Gravidade 3').length).toBeGreaterThan(0)
    expect(within(tabela).getByText('Gravidade 1')).toBeInTheDocument()
  })

  it('emLoteApareceComoContextoNaoComoProva', async () => {
    simular({ [SINAIS_URL]: { status: 200, corpo: DTO } })
    renderizar()
    const lista = await screen.findByRole('table', { name: 'Sinais do período' })
    const chip = within(lista).getByText('em lote (6)')
    expect(chip).toHaveAttribute('title', expect.stringMatching(/contexto, não prova/))
    expect(within(lista).getAllByText(/em lote/)).toHaveLength(1)
  })

  it('csvNeutralizaFormulas', async () => {
    const perigoso: AuditoriaDto = {
      ...DTO,
      sinais: [sinal({ autor: '=HYPERLINK("x")', responsavel: ' =1+1', key: 'TG-77' })],
      total: 1,
    }
    simular({ [SINAIS_URL]: { status: 200, corpo: perigoso } })
    let blobGerado: Blob | null = null
    URL.createObjectURL = (b: Blob | MediaSource) => {
      blobGerado = b as Blob
      return 'blob:x'
    }
    URL.revokeObjectURL = () => {}
    renderizar('/auditoria?squad=Alfa')
    await userEvent.click(await screen.findByRole('button', { name: 'Exportar CSV' }))
    const texto = await new Promise<string>((ok) => {
      const leitor = new FileReader()
      leitor.onload = () => ok(String(leitor.result))
      leitor.readAsText(blobGerado as unknown as Blob)
    })
    expect(texto).toContain(`"'=HYPERLINK(""x"")"`)
    expect(texto).toContain(`"' =1+1"`)
    expect(texto).toContain('# filtros: Squad: Alfa')
    expect(texto).toContain('Indício, não prova')
  })

  it('semPermissao403MostraMensagemDePapel', async () => {
    simular({ [SINAIS_URL]: { status: 403 } })
    renderizar()
    expect(await screen.findByText(/apenas a coordenação e o gerente/i)).toBeInTheDocument()
    expect(screen.queryByRole('table')).toBeNull()
    expect(screen.queryByRole('button', { name: 'Exportar CSV' })).toBeNull()
  })

  it('filtroInvalido400', async () => {
    simular({ [SINAIS_URL]: { status: 400 } })
    renderizar('/auditoria?de=2026-13-01')
    expect(await screen.findByText(/filtro é inválido/i)).toBeInTheDocument()
  })

  it('semRankingDePessoas', async () => {
    simular({ [SINAIS_URL]: { status: 200, corpo: DTO } })
    renderizar()
    await screen.findByRole('table', { name: 'Sinais do período' })
    // Nenhuma coluna de contagem por pessoa e nenhuma tabela de pessoas.
    for (const tabela of screen.getAllByRole('table')) {
      const cabecalhos = within(tabela).getAllByRole('columnheader').map((c) => c.textContent ?? '')
      expect(cabecalhos.some((h) => /ranking|por pessoa|pessoas/i.test(h))).toBe(false)
    }
    expect(screen.queryByText(/ranking/i)).toBeNull()
    // O filtro de pessoa lista as pessoas dos sinais em ordem alfabética.
    const seletor = screen.getByLabelText('Pessoa')
    const opcoes = within(seletor).getAllByRole('option').map((o) => o.textContent)
    expect(opcoes[0]).toBe('Todas')
    const nomes = opcoes.slice(1) as string[]
    expect(nomes).toEqual(['ana', 'bruno', 'carla', 'pl.alfa'])
    expect(nomes).toEqual([...nomes].sort((a, b) => a.localeCompare(b)))
  })

  it('filtrosVindosDaUrlVaoParaAApi', async () => {
    const mock = simular({ [SINAIS_URL]: { status: 200, corpo: DTO } })
    renderizar('/auditoria?squad=Alfa&sinal=PASSA_BOLA&resultado=auditoria&de=2026-10-01&ate=2026-10-30&pessoa=ana')
    await screen.findByRole('table', { name: 'Sinais do período' })
    const url = String(mock.mock.calls[0][0])
    for (const p of ['squad=Alfa', 'sinal=PASSA_BOLA', 'resultado=auditoria', 'de=2026-10-01', 'ate=2026-10-30', 'pessoa=ana']) {
      expect(url).toContain(p)
    }
  })

  it('avisosInformativosViramTextoPeloCodigo', async () => {
    simular({
      [SINAIS_URL]: {
        status: 200,
        corpo: {
          ...DTO,
          avisos: [
            { codigo: 'REPORTER_ALTERADO', gravidade: 'informativo', mensagem: '', params: { n: '2' } },
            { codigo: 'ISSUES_IGNORADAS_STATUS', gravidade: 'informativo', mensagem: '', params: { status: 'Em Triagem', n: '1' } },
            { codigo: 'VINCULOS_INDISPONIVEIS', gravidade: 'informativo', mensagem: '' },
          ],
        },
      },
    })
    renderizar()
    expect(await screen.findByText(/2 issue\(s\) com troca de reporter/)).toBeInTheDocument()
    expect(screen.getByText(/status sem mapeamento "Em Triagem"/)).toBeInTheDocument()
    expect(screen.getByText(/nenhum vínculo/i)).toBeInTheDocument()
    // os números continuam na tela
    expect(screen.getByRole('table', { name: 'Sinais do período' })).toBeInTheDocument()
  })

  it('semSinaisMostraMensagemVazia', async () => {
    simular({ [SINAIS_URL]: { status: 200, corpo: { ...DTO, total: 0, sinais: [], porSinal: DTO.porSinal.map((p) => ({ ...p, n: 0 })) } } })
    renderizar()
    expect(await screen.findByText('Nenhum sinal no período.')).toBeInTheDocument()
  })
})

describe('textos pt e en', () => {
  it('todaChaveDaAuditoriaEDaDivergenciaExisteNosDoisIdiomas', () => {
    const prefixos = ['auditoria.', 'divergencia.', 'card.au0', 'card.dv0']
    const chavesPt = Object.keys(pt).filter((k) => prefixos.some((p) => k.startsWith(p)))
    expect(chavesPt.length).toBeGreaterThan(40)
    for (const k of chavesPt) {
      expect((en as Record<string, string>)[k], `sem texto em inglês: ${k}`).toBeTruthy()
    }
    for (const k of Object.keys(en).filter((k) => prefixos.some((p) => k.startsWith(p)))) {
      expect((pt as Record<string, string>)[k], `sem texto em português: ${k}`).toBeTruthy()
    }
  })

  it('todoCodigoDeSinalETodoMotivoTemTextoNosDoisIdiomas', () => {
    const motivos = ['REPORTER', 'COORDENACAO', 'LIDER_DO_SQUAD', 'PROPRIO_RESPONSAVEL', 'NAO_AUTORIZADO', 'AUTOR_DESCONHECIDO']
    for (const dic of [pt, en] as Record<string, string>[]) {
      for (const c of CODIGOS_DE_SINAL) expect(dic[`auditoria.sinal.${c}`], c).toBeTruthy()
      for (const m of motivos) expect(dic[`auditoria.motivo.${m}`], m).toBeTruthy()
    }
  })
})
