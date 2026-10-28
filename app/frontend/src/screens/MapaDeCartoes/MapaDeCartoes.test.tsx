import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { LangProvider } from '../../i18n/LangProvider'
import type { Role } from '../../nav/nav'
import { RoleContext } from '../../shell/RoleContext'
import type { CartaoCatalogo } from '../../shared/useCatalogo'
import { MapaDeCartoes } from './MapaDeCartoes'

function cartao(id: string, extra: Partial<CartaoCatalogo> = {}): CartaoCatalogo {
  const n = id.slice(4)
  return {
    id,
    grupo: 'resultados',
    natureza: 'FLUXO',
    tela: 'tempo-em-status',
    tituloChave: 'tempo.kpi.analisadas',
    oQueMostraChave: `card.ts0${n}.oQueMostra`,
    comoCalculaChave: `card.ts0${n}.comoCalcula`,
    fontes: ['ISSUE', 'CHANGELOG'],
    papeis: ['USUARIO', 'SQUAD_LEADER', 'COORDENADOR', 'GERENTE'],
    temDrill: true,
    exigeChangelog: true,
    exigeWorklog: false,
    ...extra,
  }
}

const CATALOGO: CartaoCatalogo[] = [
  cartao('TS-01'),
  cartao('TS-02', { tituloChave: 'tempo.kpi.eficiencia' }),
  cartao('TS-03', { tituloChave: 'tempo.kpi.paradas', natureza: 'ESTOQUE' }),
  cartao('SY-01', {
    grupo: 'governanca',
    natureza: 'QUALIDADE',
    tela: 'sincronizacao',
    tituloChave: 'sinc.modo',
    oQueMostraChave: 'card.sy01.oQueMostra',
    comoCalculaChave: 'card.sy01.comoCalcula',
    fontes: ['SINCRONIZACAO'],
    papeis: ['GERENTE'],
    temDrill: false,
  }),
]

function simular(cartoes: CartaoCatalogo[], status = 200) {
  const mock = vi.fn(async (entrada: RequestInfo | URL) => {
    if (String(entrada).split('?')[0] !== '/api/cartoes/catalogo') return new Response('', { status: 404 })
    return new Response(status === 200 ? JSON.stringify({ cartoes }) : null, { status })
  })
  vi.stubGlobal('fetch', mock)
  return mock
}

function renderizar(inicial = '/mapaCartoes', papel: Role = 'GERENTE') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <LangProvider>
        <RoleContext.Provider value={papel}>
          <MemoryRouter initialEntries={[inicial]}>
            <Routes>
              <Route path="/mapaCartoes" element={<MapaDeCartoes />} />
            </Routes>
          </MemoryRouter>
        </RoleContext.Provider>
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

describe('tela Mapa de cartões', () => {
  it('agrupaPorGrupoEFiltraPorNatureza', async () => {
    simular(CATALOGO)
    renderizar()
    // Grupos na ordem do menu: Resultados antes de Governança.
    const grupos = (await screen.findAllByRole('heading', { level: 2 })).map((h) => h.textContent)
    expect(grupos).toEqual(['Resultados', 'Governança'])
    expect(screen.getAllByRole('listitem')).toHaveLength(4)

    // Filtro de natureza reduz a lista e some o grupo vazio.
    await userEvent.selectOptions(screen.getByLabelText('Natureza'), 'ESTOQUE')
    expect(screen.getAllByRole('listitem')).toHaveLength(1)
    expect(screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent)).toEqual(['Resultados'])
    expect(screen.getByText('Paradas além do normal', { selector: 'h3' })).toBeInTheDocument()

    // Filtro de grupo.
    await userEvent.selectOptions(screen.getByLabelText('Natureza'), '')
    await userEvent.selectOptions(screen.getByLabelText('Grupo'), 'governanca')
    expect(screen.getAllByRole('listitem')).toHaveLength(1)
    expect(screen.getByText('Modo do sincronizador', { selector: 'h3' })).toBeInTheDocument()
  })

  it('filtrosFicamNaUrl', async () => {
    simular(CATALOGO)
    renderizar('/mapaCartoes?n=ESTOQUE')
    await screen.findAllByRole('listitem')
    expect(screen.getAllByRole('listitem')).toHaveLength(1)
    expect(screen.getByLabelText('Natureza')).toHaveValue('ESTOQUE')
  })

  it('buscaPorTitulo', async () => {
    simular(CATALOGO)
    renderizar()
    await screen.findAllByRole('listitem')
    await userEvent.type(screen.getByLabelText('Buscar título'), 'EFICIÊNCIA')
    expect(screen.getAllByRole('listitem')).toHaveLength(1)
    expect(screen.getByText('Eficiência de fluxo', { selector: 'h3' })).toBeInTheDocument()
    await userEvent.clear(screen.getByLabelText('Buscar título'))
    await userEvent.type(screen.getByLabelText('Buscar título'), 'não existe')
    expect(screen.queryAllByRole('listitem')).toHaveLength(0)
    expect(screen.getByText('Nenhum cartão encontrado com estes filtros.')).toBeInTheDocument()
  })

  it('mostraOQueMostraComoCalculaEFonte', async () => {
    simular(CATALOGO)
    renderizar()
    const item = (await screen.findByText('Tasks analisadas', { selector: 'h3' })).closest('li') as HTMLElement
    expect(within(item).getByText('TS-01')).toBeInTheDocument()
    expect(within(item).getByText(/Fluxo/)).toBeInTheDocument()
    expect(item).toHaveTextContent('O que mostra')
    expect(item).toHaveTextContent('Quantas tasks concluídas no período entram nos cálculos desta tela.')
    expect(item).toHaveTextContent('Como é calculado')
    expect(item).toHaveTextContent('Conta as tasks com status concluído e resolução')
    expect(item).toHaveTextContent('Fonte')
    expect(item).toHaveTextContent('campos da issue, histórico de mudanças (changelog)')
    // Tela onde vive (com link) e quem vê.
    expect(within(item).getByRole('link', { name: 'Tempo em status' })).toHaveAttribute('href', '/tempo')
    expect(item).toHaveTextContent('Visível para')
    expect(item).toHaveTextContent('Usuário, Squad Leader, Coordenador, Gerente')
  })

  it('soOQueOPapelVe', async () => {
    // O servidor já filtra por papel: a tela mostra exatamente o que veio, e só consulta o catálogo.
    const soDoUsuario = CATALOGO.filter((c) => c.papeis.includes('USUARIO'))
    const mock = simular(soDoUsuario)
    renderizar()
    await screen.findAllByRole('listitem')
    expect(screen.getAllByRole('listitem')).toHaveLength(3)
    expect(screen.queryByText('Modo do sincronizador')).toBeNull()
    expect(mock.mock.calls.map((c) => String(c[0]))).toEqual(['/api/cartoes/catalogo'])
  })

  it('usuarioSemPermissaoVeAviso', async () => {
    const mock = simular(CATALOGO)
    renderizar('/mapaCartoes', 'USUARIO')
    expect(await screen.findByText(/Sem permissão para este papel/)).toBeInTheDocument()
    expect(screen.queryAllByRole('listitem')).toHaveLength(0)
    expect(mock).not.toHaveBeenCalled()
  })

  it('estadosDeErroEPermissao', async () => {
    simular([], 403)
    renderizar()
    expect(await screen.findByText(/Seu papel não permite ver o mapa de cartões/)).toBeInTheDocument()
    cleanup()
    const mock = simular([], 500)
    renderizar()
    await userEvent.click(await screen.findByRole('button', { name: 'Tentar de novo' }))
    await vi.waitFor(() => expect(mock.mock.calls.length).toBeGreaterThan(1))
  })

  it('exportaCsvDoMapa', async () => {
    simular([cartao('TS-01', { tituloChave: 'tempo.kpi.analisadas' }), cartao('TS-02', { tituloChave: 'tempo.kpi.eficiencia', natureza: 'ESTOQUE' })])
    let blobGerado: Blob | null = null
    URL.createObjectURL = (b: Blob | MediaSource) => {
      blobGerado = b as Blob
      return 'blob:x'
    }
    URL.revokeObjectURL = () => {}
    renderizar('/mapaCartoes?n=FLUXO')
    await screen.findAllByRole('listitem')
    await userEvent.click(screen.getByRole('button', { name: 'Exportar CSV' }))
    const texto = await new Promise<string>((ok) => {
      const leitor = new FileReader()
      leitor.onload = () => ok(String(leitor.result))
      leitor.readAsText(blobGerado as unknown as Blob)
    })
    // O CSV leva os filtros ativos e só as linhas filtradas.
    expect(texto).toContain('# filtros: Natureza: Fluxo')
    expect(texto).toContain('"Id";"Título";"Grupo";"Natureza"')
    expect(texto).toContain('"TS-01";"Tasks analisadas";"Resultados";"Fluxo"')
    expect(texto).not.toContain('TS-02')
    expect(texto).toContain('Tempo em status')
  })

  it('semTextoSemTraducao', async () => {
    for (const idioma of ['pt', 'en']) {
      localStorage.setItem('painel.idioma', idioma)
      simular(CATALOGO)
      const { unmount } = renderizar()
      await screen.findAllByRole('listitem')
      const texto = document.body.textContent ?? ''
      // Nenhuma chave de tradução crua (ex.: "card.ts01.oQueMostra", "catalogo.fonte.ISSUE") aparece na tela.
      expect(texto, idioma).not.toMatch(/\b(card|catalogo|tempo|sinc|grupo|cartao|role|nav|mapa)\.[A-Za-z0-9_.]+/)
      unmount()
      cleanup()
    }
  })
})
