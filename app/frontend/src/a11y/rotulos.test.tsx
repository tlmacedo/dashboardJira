import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { LangProvider } from '../i18n/LangProvider'
import { Abertas } from '../screens/Abertas/Abertas'
import type { AbertasDto } from '../screens/Abertas/api'
import { Menu } from '../shell/Menu'
import { Shell } from '../shell/Shell'
import { Ocupacao } from '../shared/Ocupacao'
import { TabelaVirtual, type Coluna } from '../shared/TabelaVirtual'
import { simular } from '../test/telas'
import { ThemeProvider } from '../theme/ThemeProvider'

const DTO: AbertasDto = {
  abertas: 18,
  emAndamento: 2,
  acimaDoP85: 1,
  historicoPorTipo: [{ tipo: 'Checklist', p50: 6, p85: 7.4, n: 3 }],
  porStatus: [{ chave: 'Open', n: 2 }],
  porPrioridade: [{ chave: 'High', n: 2 }],
  porIdade: [{ chave: '0-7', n: 1 }],
  porVencimento: [{ chave: 'vencidas', n: 1 }],
  linhas: [
    { key: 'TG-101', resumo: 'Ajustar tela', tipo: 'Checklist', status: 'In Progress', prioridade: 'High', responsavel: 'ana', idade: 20, vence: '2026-10-28', horasRestantes: null },
  ],
  unit: 'util',
  avisos: [],
}

function renderizarCasca() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ThemeProvider>
        <LangProvider>
          <MemoryRouter initialEntries={['/abertas']}>
            <Routes>
              <Route element={<Shell />}>
                <Route path="/abertas" element={<Abertas />} />
              </Route>
            </Routes>
          </MemoryRouter>
        </LangProvider>
      </ThemeProvider>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  localStorage.clear()
  simular({
    '/api/meta': { status: 200, corpo: { devMode: true, liveEnabled: false, zone: 'America/Manaus', banner: 'MODO DESENVOLVIMENTO' } },
    '/api/cards/abertas': { status: 200, corpo: DTO },
    '/api/filtros/opcoes': { status: 200, corpo: { squads: ['Alfa'], pessoas: [], tipos: [], prioridades: [], os: [], campos: [], avisos: [], rapidos: [] } },
  })
})
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('rótulos e papéis (leitor de tela)', () => {
  it('kpisTemNomeAcessivel', async () => {
    renderizarCasca()
    const kpis = await screen.findByRole('group', { name: 'Indicadores das abertas' })
    // O nome do botão do indicador traz o título E o valor ("Abertas ... 18"), nunca só o número.
    expect(within(kpis).getByRole('button', { name: /Abertas.*18/ })).toBeInTheDocument()
    // O símbolo ▲ não é o único sinal: o estado "acima do P85" também é dito em texto.
    const acima = within(kpis).getByRole('button', { name: /Acima do P85/ })
    expect(acima).toHaveTextContent('▲')
    expect(acima).toHaveTextContent('1')
  })

  it('tabelasComCabecalho', async () => {
    renderizarCasca()
    await screen.findByRole('group', { name: 'Indicadores das abertas' })
    const tabelas = screen.getAllByRole('table')
    expect(tabelas.length).toBeGreaterThan(0)
    for (const tabela of tabelas) {
      expect(tabela, 'tabela sem nome').toHaveAccessibleName()
      expect(within(tabela).getAllByRole('columnheader').length, 'tabela sem cabeçalho de coluna').toBeGreaterThan(0)
      for (const th of tabela.querySelectorAll('th')) expect(th, th.outerHTML).toHaveAttribute('scope')
    }
  })

  it('controlesInterativosTemNomeAcessivel', async () => {
    renderizarCasca()
    await screen.findByRole('group', { name: 'Indicadores das abertas' })
    const interativos = [
      ...screen.getAllByRole('button'),
      ...screen.getAllByRole('link'),
      ...screen.queryAllByRole('textbox'),
      ...screen.queryAllByRole('combobox'),
      ...screen.queryAllByRole('checkbox'),
      ...screen.queryAllByRole('spinbutton'),
    ]
    expect(interativos.length).toBeGreaterThan(10)
    for (const el of interativos) expect(el, el.outerHTML.slice(0, 120)).toHaveAccessibleName()
  })

  it('idiomaDaPaginaAcompanhaALingua', async () => {
    renderizarCasca()
    await screen.findByRole('group', { name: 'Indicadores das abertas' })
    expect(document.documentElement.lang).toBe('pt-BR')
    await userEvent.click(screen.getByRole('button', { name: 'EN' }))
    expect(document.documentElement.lang).toBe('en')
  })

  it('seletoresDoMenuSaoGruposComEstadoPressionado', async () => {
    renderizarCasca()
    await screen.findByRole('group', { name: 'Indicadores das abertas' })
    for (const nome of ['Tema', 'Idioma']) {
      const grupo = screen.getByRole('group', { name: nome })
      const botoes = within(grupo).getAllByRole('button')
      expect(botoes.length).toBeGreaterThan(1)
      // Exatamente um pressionado; o estado vai em aria-pressed, e o visual soma negrito e fundo (não só cor).
      expect(botoes.filter((b) => b.getAttribute('aria-pressed') === 'true')).toHaveLength(1)
    }
  })
})

describe('estado nunca só por cor', () => {
  it('ocupacaoTemSimboloETextoParaOLeitorEmTodoEstado', () => {
    const casos: [Parameters<typeof Ocupacao>[0]['o'], string][] = [
      [{ fracao: 0.25, semCapacidade: false, estado: 'ok' }, '✓'],
      [{ fracao: 0.9, semCapacidade: false, estado: 'atencao' }, '●'],
      [{ fracao: 1.5, semCapacidade: false, estado: 'problema' }, '▲'],
    ]
    for (const [o, simbolo] of casos) {
      const { container, unmount } = render(
        <LangProvider>
          <Ocupacao o={o} />
        </LangProvider>,
      )
      expect(container.textContent, o.estado).toContain(simbolo)
      expect(container.querySelector('.so-leitor')?.textContent, `${o.estado}: estado em texto`).toMatch(/\w/)
      unmount()
    }
  })
})

describe('tabela virtual', () => {
  type Linha = { key: string; n: number }
  const colunas: Coluna<Linha>[] = [
    { id: 'key', cabecalho: 'Task', valor: (l) => l.key },
    { id: 'n', cabecalho: 'N', valor: (l) => l.n, numerica: true },
  ]
  const linhas = Array.from({ length: 500 }, (_, i) => ({ key: `TG-${i + 1}`, n: i % 7 }))

  it('ehGradeComNomeContagemTotalOrdenacaoELinhasNumeradas', async () => {
    render(
      <LangProvider>
        <TabelaVirtual<Linha> colunas={colunas} linhas={linhas} chave={(l) => l.key} rotulo="Tasks" primeiraEhCabecalhoDeLinha />
      </LangProvider>,
    )
    const grade = screen.getByRole('grid', { name: 'Tasks' })
    // Foco por Tab, contagem total (o leitor sabe que há 500 linhas mesmo com a janela de ~34) e linha ativa por descendente.
    expect(grade).toHaveAttribute('tabindex', '0')
    expect(grade).toHaveAttribute('aria-rowcount', '501')
    for (const cab of within(grade).getAllByRole('columnheader')) expect(cab).toHaveAttribute('aria-sort')
    const primeira = within(grade).getAllByRole('row').find((r) => r.hasAttribute('aria-rowindex') && r.querySelector('th[scope="row"]'))!
    expect(primeira).toHaveAttribute('aria-rowindex', '2')
    expect(within(primeira).getByRole('rowheader')).toHaveTextContent('TG-1')
    // A ordenação é anunciada em texto, e o ▲/▼ é só reforço visual.
    await userEvent.click(within(grade).getByRole('button', { name: /^N/ }))
    expect(within(grade).getByRole('columnheader', { name: /N/ })).toHaveAttribute('aria-sort', 'ascending')
    expect(grade.querySelector('.so-leitor')?.textContent).toMatch(/\w/)
  })
})

describe('menu no celular', () => {
  it('celular390MenuViraBotao', async () => {
    const client = new QueryClient()
    render(
      <QueryClientProvider client={client}>
        <ThemeProvider>
          <LangProvider>
            <MemoryRouter>
              <Menu role="GERENTE" />
            </MemoryRouter>
          </LangProvider>
        </ThemeProvider>
      </QueryClientProvider>,
    )
    const botao = screen.getByRole('button', { name: 'Menu' })
    const nav = screen.getByRole('navigation', { name: 'Menu principal' })
    // O botão ☰ comanda a lista: aria-controls aponta para ela e aria-expanded acompanha o estado.
    expect(botao).toHaveAttribute('aria-controls', nav.id)
    expect(botao).toHaveAttribute('aria-expanded', 'false')
    await userEvent.click(botao)
    expect(botao).toHaveAttribute('aria-expanded', 'true')
    expect(botao.closest('aside')).toHaveClass('menu--aberto')
    // Escolher um item recolhe a lista (no celular ela cobriria o conteúdo).
    await userEvent.click(within(nav).getAllByRole('link')[0])
    expect(botao).toHaveAttribute('aria-expanded', 'false')
  })
})
