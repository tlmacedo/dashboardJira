import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { LangProvider } from '../../i18n/LangProvider'
import { ThemeProvider } from '../../theme/ThemeProvider'
import { Shell } from '../../shell/Shell'
import { neutralizarCelulaCsv, paraCsv } from './csv'
import { TempoEmStatus } from './TempoEmStatus'
import type { DrillRow, TempoEmStatusDto } from './api'

const DTO: TempoEmStatusDto = {
  analisadas: 236,
  eficienciaFluxo: 0.31,
  paradas: 17,
  porStatus: [
    { status: 'Em andamento', category: 'ACTIVE', median: 3.2, p85: 6.1, n: 200 },
    { status: 'Em revisão', category: 'WAIT', median: 5.4, p85: 11, n: 180 },
    { status: 'Bloqueado', category: 'BLOCKED', median: 1.1, p85: 2.3, n: 40 },
  ],
  pessoaStatus: [
    { person: 'bruno', status: 'Em revisão', median: 7.2, n: 12 },
    { person: 'ana', status: 'Em andamento', median: 2.1, n: 30 },
    { person: 'ana', status: 'Em revisão', median: 4.8, n: 20 },
    { person: 'bruno', status: 'Bloqueado', median: 3.0, n: 4 },
  ],
  abertasParadas: [
    { key: 'TG-101', summary: 'Ajustar tela', assignee: 'ana', status: 'Em revisão', daysInStatus: 9.5, median: 5.4 },
    { key: 'TG-102', summary: '=HYPERLINK("x")', assignee: null, status: 'Bloqueado', daysInStatus: 4, median: 1.1 },
  ],
  unit: 'util',
  avisos: [],
}

const linhas = (n: number): DrillRow[] =>
  Array.from({ length: n }, (_, i) => ({
    key: `TG-${i + 1}`,
    resumo: `Task ${i + 1}`,
    responsavel: 'ana',
    status: 'Em revisão',
    detalhe: `${i + 1},0 dias úteis em Em revisão`,
  }))

type Resp = { status: number; corpo?: unknown }
type Rotas = Record<string, Resp | (() => Resp)>

// Simula só a API local: os testes nunca usam rede.
function simular(rotas: Rotas) {
  const mock = vi.fn(async (entrada: RequestInfo | URL) => {
    const caminho = String(entrada).split('?')[0]
    const r = rotas[caminho]
    if (!r) return new Response('', { status: 404 })
    const { status, corpo } = typeof r === 'function' ? r() : r
    return new Response(corpo === undefined ? null : JSON.stringify(corpo), { status })
  })
  vi.stubGlobal('fetch', mock)
  return mock
}

function renderizar(inicial = '/tempo') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ThemeProvider>
        <LangProvider>
          <MemoryRouter initialEntries={[inicial]}>
            <Routes>
              <Route path="/tempo" element={<TempoEmStatus />} />
            </Routes>
          </MemoryRouter>
        </LangProvider>
      </ThemeProvider>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  localStorage.clear()
  delete document.documentElement.dataset.theme
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

const CARD = '/api/cards/tempo-em-status'
const DRILL = '/api/drill/tempo-em-status'

describe('tela Tempo em status', () => {
  it('rendersKpis', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizar()
    const analisadas = await screen.findByRole('button', { name: /Tasks analisadas/ })
    expect(analisadas).toHaveTextContent('236')
    expect(screen.getByRole('button', { name: /Paradas além do normal/ })).toHaveTextContent('17')
    expect(screen.getByText('31%')).toBeInTheDocument()
  })

  it('alternarUnidadeRecarrega', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizar()
    await screen.findByText('31%')
    await userEvent.click(screen.getByRole('button', { name: 'Dias corridos' }))
    await vi.waitFor(() => {
      const urls = mock.mock.calls.map((c) => String(c[0]))
      expect(urls.some((u) => u.includes('unit=corrido'))).toBe(true)
    })
    expect(screen.getByRole('button', { name: 'Dias corridos' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('clicarNoKpiAbreAGaveta', async () => {
    const mock = simular({
      [CARD]: { status: 200, corpo: DTO },
      [DRILL]: { status: 200, corpo: linhas(17) },
    })
    renderizar()
    await userEvent.click(await screen.findByRole('button', { name: /Paradas além do normal/ }))
    const gaveta = await screen.findByRole('dialog')
    await within(gaveta).findByText('TG-1')
    // cabeçalho + 17 linhas
    expect(within(gaveta).getAllByRole('row')).toHaveLength(18)
    expect(mock.mock.calls.some((c) => String(c[0]).includes('kind=paradas'))).toBe(true)
    // Esc fecha e devolve o foco ao KPI
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(screen.getByRole('button', { name: /Paradas além do normal/ })).toHaveFocus()
  })

  it('avisoDeStatusNaoMapeado', async () => {
    simular({
      [CARD]: {
        status: 200,
        corpo: {
          ...DTO,
          analisadas: 0,
          eficienciaFluxo: null,
          paradas: 0,
          porStatus: [],
          pessoaStatus: [],
          abertasParadas: [],
          avisos: [{ codigo: 'STATUS_SEM_MAPEAMENTO', gravidade: 'bloqueante', mensagem: 'status sem mapeamento: Em Triagem', params: { status: 'Em Triagem' } }],
        },
      },
    })
    renderizar()
    expect(await screen.findByText(/status sem mapeamento: Em Triagem/i)).toBeInTheDocument()
    const kpis = screen.getByRole('group', { name: 'Indicadores' })
    expect(within(kpis).getAllByText('—')).toHaveLength(3)
  })

  it('avisoUsaParametroEmVezDaMensagem', async () => {
    simular({
      [CARD]: {
        status: 200,
        corpo: {
          ...DTO,
          avisos: [
            { codigo: 'STATUS_SEM_MAPEAMENTO', gravidade: 'bloqueante', mensagem: '', params: { status: 'Revisando' } },
            { codigo: 'CONCLUSAO_DIVERGENTE', gravidade: 'informativo', mensagem: '', params: { n: '3' } },
          ],
        },
      },
    })
    renderizar()
    // O texto vem do código (i18n) e do parâmetro; a mensagem do backend (vazia aqui) não é usada.
    expect(await screen.findByText(/Há status sem mapeamento: Revisando/)).toBeInTheDocument()
    expect(screen.getByText(/3 issue\(s\) com status e resolução divergentes/)).toBeInTheDocument()
  })

  it('avisoInformativoMantemOsNumeros', async () => {
    simular({
      [CARD]: {
        status: 200,
        corpo: {
          ...DTO,
          avisos: [{ codigo: 'DATAS_INCONSISTENTES', gravidade: 'informativo', mensagem: '1 issue(s) com datas inconsistentes ignoradas' }],
        },
      },
    })
    renderizar()
    // Faixa traduzida pelo código (não pela mensagem do backend) e números preservados.
    expect(await screen.findByText(/datas inconsistentes foram ignoradas/i)).toBeInTheDocument()
    expect(screen.queryByText(/1 issue\(s\)/)).toBeNull()
    const kpis = screen.getByRole('group', { name: 'Indicadores' })
    expect(within(kpis).queryByText('—')).toBeNull()
    expect(screen.getByRole('table', { name: /Mediana por pessoa/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /CSV/i })).toBeEnabled()
  })

  it('semPermissaoMostraMensagem', async () => {
    simular({ [CARD]: { status: 403 } })
    renderizar()
    expect(await screen.findByText(/seu papel não permite/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Tasks analisadas/ })).toBeNull()
  })

  it('simbolosAlemDaCor', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizar()
    const matriz = await screen.findByRole('table', { name: /Mediana por pessoa/ })
    expect(within(matriz).getByText(/✓\s*2,1/)).toBeInTheDocument() // ≤ 3 d
    expect(within(matriz).getByText(/●\s*4,8/)).toBeInTheDocument() // 3–6 d
    expect(within(matriz).getByText(/▲\s*7,2/)).toBeInTheDocument() // > 6 d
  })

  it('pessoasEmOrdemAlfabetica', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizar()
    const matriz = await screen.findByRole('table', { name: /Mediana por pessoa/ })
    const nomes = within(matriz)
      .getAllByRole('rowheader')
      .map((c) => c.textContent)
    expect(nomes).toEqual(['ana', 'bruno'])
  })

  it('filtrosVindosDaUrlVaoParaAApi', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizar('/tempo?squad=Alfa&os=OS15&semOutliers=true')
    await screen.findByText('31%')
    const url = String(mock.mock.calls[0][0])
    expect(url).toContain('squad=Alfa')
    expect(url).toContain('os=OS15')
    expect(url).toContain('semOutliers=true')
  })
})

describe('CSV', () => {
  it('neutralizaFormulas', () => {
    for (const perigo of ['=1+1', '+1', '-1', '@x', '\tx', '\rx']) {
      expect(neutralizarCelulaCsv(perigo).startsWith("'")).toBe(true)
    }
    expect(neutralizarCelulaCsv('TG-1')).toBe('TG-1')
  })

  it('escapaAspasESeparador', () => {
    expect(paraCsv([['a;b', 'c"d']])).toBe('"a;b";"c""d"')
  })

  it('neutralizaAposEspacosEAspas', () => {
    for (const perigo of [' =HYPERLINK("x")', '"=cmd"', "'+1", '\t=1', ' =1', '​@x', '  \t-1']) {
      expect(neutralizarCelulaCsv(perigo).startsWith("'")).toBe(true)
    }
    expect(neutralizarCelulaCsv('TG-1 resumo normal')).toBe('TG-1 resumo normal')
    expect(neutralizarCelulaCsv('1,5 dias')).toBe('1,5 dias')
  })

  it('escapeContinuaCorretoDepoisDaNeutralizacao', () => {
    expect(paraCsv([['="a";b']])).toBe('"\'=""a"";b"')
  })
})

describe('gaveta', () => {
  it('csvDaGavetaLevaFiltrosEUnidade', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO }, [DRILL]: { status: 200, corpo: linhas(3) } })
    let blobGerado: Blob | null = null
    URL.createObjectURL = (b: Blob | MediaSource) => {
      blobGerado = b as Blob
      return 'blob:x'
    }
    URL.revokeObjectURL = () => {}
    renderizar('/tempo?squad=Alfa&unit=corrido')
    await userEvent.click(await screen.findByRole('button', { name: /Paradas além do normal/ }))
    const gaveta = await screen.findByRole('dialog')
    await within(gaveta).findByText('TG-1')
    await userEvent.click(within(gaveta).getByRole('button', { name: 'Exportar CSV' }))
    const texto = await new Promise<string>((ok) => {
      const leitor = new FileReader()
      leitor.onload = () => ok(String(leitor.result))
      leitor.readAsText(blobGerado as unknown as Blob)
    })
    expect(texto).toContain('# filtros: Squad: Alfa')
    expect(texto).toContain('dias corridos')
  })

  it('fundoFicaInertEFocoVolta', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO }, [DRILL]: { status: 200, corpo: linhas(2) } })
    const { container } = renderizar()
    const kpi = await screen.findByRole('button', { name: /Paradas além do normal/ })
    await userEvent.click(kpi)
    const gaveta = await screen.findByRole('dialog')
    expect(container).toHaveAttribute('inert')
    expect(gaveta.closest('[inert]')).toBeNull()
    expect(within(gaveta).getByRole('button', { name: 'Fechar' })).toHaveFocus()
    await userEvent.keyboard('{Escape}')
    expect(container).not.toHaveAttribute('inert')
    expect(kpi).toHaveFocus()
  })

  it('kpiParadasNaoUsaOSimboloDaFaixaAtencao', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizar()
    const kpi = await screen.findByRole('button', { name: /Paradas além do normal/ })
    expect(kpi.textContent).toContain('⏸')
    expect(kpi.textContent).not.toContain('●')
  })
})

describe('menu usa o papel de /api/me', () => {
  const META = { devMode: false, liveEnabled: false, zone: 'America/Manaus', banner: null }

  function renderizarShell() {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    return render(
      <QueryClientProvider client={client}>
        <ThemeProvider>
          <LangProvider>
            <MemoryRouter initialEntries={['/']}>
              <Routes>
                <Route element={<Shell />}>
                  <Route path="*" element={<p>conteúdo</p>} />
                </Route>
              </Routes>
            </MemoryRouter>
          </LangProvider>
        </ThemeProvider>
      </QueryClientProvider>,
    )
  }

  it('menuUsaPapelDeApiMeEmProducao', async () => {
    localStorage.setItem('painel.papelSimulado', 'GERENTE')
    simular({
      '/api/meta': { status: 200, corpo: META },
      '/api/me': { status: 200, corpo: { userId: 'carla', roles: ['COORDENADOR'] } },
    })
    renderizarShell()
    expect(await screen.findByRole('link', { name: 'Alterações de data, dono e label' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Integridade' })).toBeNull()
    // sem seletor de simulação em produção
    expect(screen.queryByRole('button', { name: 'Gerente' })).toBeNull()
  })

  it.each([403, 500])('apiMeFalhandoViraUsuario (%i)', async (status) => {
    localStorage.setItem('painel.papelSimulado', 'GERENTE')
    const mock = simular({
      '/api/meta': { status: 200, corpo: META },
      '/api/me': { status, corpo: undefined },
    })
    renderizarShell()
    await vi.waitFor(() => expect(mock.mock.calls.some((c) => String(c[0]) === '/api/me')).toBe(true))
    // dá tempo de a falha ser processada
    await new Promise((r) => setTimeout(r, 30))
    expect(screen.getByRole('link', { name: 'Meu painel' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Integridade' })).toBeNull()
    expect(screen.queryByRole('link', { name: 'Alterações de data, dono e label' })).toBeNull()
  })

  it('devModeNaoConsultaApiMeEMantemSimulacao', async () => {
    const mock = simular({
      '/api/meta': { status: 200, corpo: { ...META, devMode: true, banner: 'MODO DESENVOLVIMENTO' } },
      '/api/me': { status: 200, corpo: { userId: 'dev', roles: ['USUARIO'] } },
    })
    renderizarShell()
    expect(await screen.findByRole('link', { name: 'Integridade' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Usuário' }))
    expect(screen.queryByRole('link', { name: 'Integridade' })).toBeNull()
    expect(mock.mock.calls.some((c) => String(c[0]) === '/api/me')).toBe(false)
  })
})
