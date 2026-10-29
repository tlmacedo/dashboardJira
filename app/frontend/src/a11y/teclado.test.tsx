import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState, type ReactElement } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { LangProvider } from '../i18n/LangProvider'
import { Abertas } from '../screens/Abertas/Abertas'
import type { AbertasDto } from '../screens/Abertas/api'
import { ComparacaoDoPar } from '../screens/ChecklistReview/ComparacaoDoPar'
import { HistoricoDrawer } from '../screens/Auditoria/HistoricoDrawer'
import { FatoresDoRisco } from '../screens/Riscos/FatoresDoRisco'
import { Shell } from '../shell/Shell'
import { GavetaDeTasks } from '../shared/GavetaDeTasks'
import { TarefaDrawer } from '../shared/TarefaDrawer'
import { elementosComTab, useModalAcessivel } from '../shared/useModalAcessivel'
import { simular, gaveta } from '../test/telas'
import { ThemeProvider } from '../theme/ThemeProvider'

const DTO: AbertasDto = {
  abertas: 4,
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

const META = { devMode: true, liveEnabled: false, zone: 'America/Manaus', banner: 'MODO DESENVOLVIMENTO' }

function renderizarCasca(rota = '/abertas') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ThemeProvider>
        <LangProvider>
          <MemoryRouter initialEntries={[rota]}>
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
  delete document.documentElement.dataset.theme
  simular({
    '/api/meta': { status: 200, corpo: META },
    '/api/cards/abertas': { status: 200, corpo: DTO },
    '/api/filtros/opcoes': { status: 200, corpo: { squads: ['Alfa'], pessoas: [], tipos: [], prioridades: [], os: [], campos: [], avisos: [], rapidos: [] } },
    '/api/cartoes/AB-01/tasks': { status: 200, corpo: gaveta(2) },
  })
})
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('teclado na casca', () => {
  it('pularParaOConteudo', async () => {
    const { container } = renderizarCasca()
    await screen.findByRole('group', { name: 'Indicadores das abertas' })
    const usuario = userEvent.setup()
    await usuario.tab()
    // É o primeiro foco da página e aponta para o <main> (que aceita foco programático).
    const pular = screen.getByRole('link', { name: 'Pular para o conteúdo' })
    expect(pular).toHaveFocus()
    expect(pular).toHaveAttribute('href', '#conteudo')
    const principal = container.querySelector('main#conteudo')
    expect(principal).not.toBeNull()
    expect(principal).toHaveAttribute('tabindex', '-1')
    expect(screen.getByRole('main')).toBe(principal)
  })

  it('pontosDeReferenciaTemNome', async () => {
    renderizarCasca()
    await screen.findByRole('group', { name: 'Indicadores das abertas' })
    expect(screen.getByRole('navigation', { name: 'Menu principal' })).toBeInTheDocument()
    expect(screen.getByRole('main')).toBeInTheDocument()
    // O menu é um <aside> (complementar) e o item atual é anunciado pelo aria-current.
    expect(screen.getByRole('link', { current: 'page' })).toBeInTheDocument()
  })

  it('tabPercorreMenuFiltrosKpisTabela', async () => {
    const { container } = renderizarCasca()
    await screen.findByRole('group', { name: 'Indicadores das abertas' })
    const usuario = userEvent.setup()
    const zonaDe = (el: Element | null): string => {
      if (!el) return 'nenhuma'
      if (el.closest('aside.menu')) return 'menu'
      if (el.closest('.busca-chave')) return 'busca'
      if (el.closest('form.filtros, .filtros-bloco')) return 'filtros'
      if (el.closest('.cardpage-kpis')) return 'kpis'
      if (el.closest('table, .tabela-rolagem')) return 'tabela'
      return 'outra'
    }
    const primeira = new Map<string, number>()
    for (let i = 0; i < 400; i++) {
      await usuario.tab()
      const z = zonaDe(document.activeElement)
      if (!primeira.has(z)) primeira.set(z, i)
      if (primeira.has('tabela')) break
    }
    // A ordem do foco segue a ordem visual: menu, busca, filtros, indicadores, tabela.
    const ordem = ['menu', 'busca', 'filtros', 'kpis', 'tabela'].map((z) => {
      expect(primeira.has(z), `a zona ${z} não recebeu foco`).toBe(true)
      return primeira.get(z)!
    })
    expect(ordem, JSON.stringify([...primeira])).toEqual([...ordem].sort((a, b) => a - b))
    expect(container.querySelector('[tabindex]:not([tabindex="0"]):not([tabindex="-1"])'), 'tabindex positivo quebra a ordem natural').toBeNull()
  })

  it('kpiSeAtivaComEnterEAbreGavetaQueEscFecha', async () => {
    renderizarCasca()
    const kpis = await screen.findByRole('group', { name: 'Indicadores das abertas' })
    const botao = within(kpis).getByRole('button', { name: /^Abertas/ })
    const usuario = userEvent.setup()
    botao.focus()
    await usuario.keyboard('{Enter}')
    const dialogo = await screen.findByRole('dialog')
    expect(dialogo).toBeInTheDocument()
    await usuario.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(botao).toHaveFocus()
  })
})

// Todo diálogo modal do painel usa o mesmo comportamento: foco em "Fechar" ao abrir, Tab preso, Esc fecha e devolve o foco.
describe('gavetas e janelas', () => {
  function Abridor({ children }: { children: (fechar: () => void) => ReactElement }) {
    const [aberto, setAberto] = useState(false)
    return (
      <>
        <button type="button" onClick={() => setAberto(true)}>
          abrir
        </button>
        {aberto && children(() => setAberto(false))}
      </>
    )
  }

  function renderizarGaveta(filho: (fechar: () => void) => ReactElement) {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    return render(
      <QueryClientProvider client={client}>
        <LangProvider>
          <MemoryRouter>
            <Abridor>{filho}</Abridor>
          </MemoryRouter>
        </LangProvider>
      </QueryClientProvider>,
    )
  }

  const risco: Parameters<typeof FatoresDoRisco>[0]['linha'] = {
    key: 'TG-1', resumo: 'Resumo', tipo: 'Checklist', status: 'Open', responsavel: null, pontos: 3, nivel: 'alto',
    fatorPrincipal: 'vencimento', fatores: [{ fator: 'vencimento', pontos: 3 }],
  }
  const par: Parameters<typeof ComparacaoDoPar>[0]['par'] = {
    pai: 'TG-1', review: 'TG-2', resumoPai: 'a', resumoReview: 'b', responsavelPai: null, responsavelReview: null,
    statusPai: 'Open', statusReview: 'Open', criacaoPai: '2026-10-01', criacaoReview: '2026-10-02', vencePai: null, venceReview: null,
    resolvidoPai: null, resolvidoReview: null, horasAtivasReview: null, paiReaberto: false, alertas: [],
  }

  const casos: [string, (fechar: () => void) => ReactElement][] = [
    ['GavetaDeTasks', (f) => <GavetaDeTasks cartaoId="AB-01" segmento="abertas" titulo="Abertas" filtros={new URLSearchParams()} aoFechar={f} />],
    ['TarefaDrawer', (f) => <TarefaDrawer chave="TG-1" aoFechar={f} />],
    ['FatoresDoRisco', (f) => <FatoresDoRisco linha={risco} aoFechar={f} />],
    ['ComparacaoDoPar', (f) => <ComparacaoDoPar par={par} aoFechar={f} />],
    ['HistoricoDrawer', (f) => <HistoricoDrawer chave="TG-1" resumoDosFiltros="" aoFechar={f} />],
  ]

  for (const [nome, montar] of casos) {
    it(`escFechaGavetaEDevolveFoco: ${nome}`, async () => {
      simular({ '/api/cartoes/AB-01/tasks': { status: 200, corpo: gaveta(2) }, '/api/tarefa/TG-1': { status: 404 } })
      renderizarGaveta(montar)
      const usuario = userEvent.setup()
      const abrir = screen.getByRole('button', { name: 'abrir' })
      await usuario.click(abrir)
      const dialogo = await screen.findByRole('dialog')
      expect(within(dialogo).getByRole('button', { name: 'Fechar' })).toHaveFocus()
      expect(dialogo).toHaveAttribute('aria-modal', 'true')
      expect(dialogo).toHaveAccessibleName()
      // O resto da página fica inerte enquanto a gaveta está aberta.
      expect(abrir.closest('[inert]')).not.toBeNull()
      await usuario.keyboard('{Escape}')
      expect(screen.queryByRole('dialog')).toBeNull()
      expect(abrir).toHaveFocus()
      expect(abrir.closest('[inert]')).toBeNull()
    })
  }

  it('tabFicaPresoInclusiveEmCamposDeFormulario', async () => {
    // Gaveta com campo de texto, lista e área de texto (ex.: decisão com motivo): o ciclo de Tab precisa incluí-los.
    function Gaveta({ aoFechar }: { aoFechar: () => void }) {
      const { raiz, fechar, aoTeclar } = useModalAcessivel(aoFechar)
      return (
        <div ref={raiz} role="dialog" aria-modal="true" aria-label="Formulário" onKeyDown={aoTeclar}>
          <button ref={fechar} type="button" onClick={aoFechar}>
            Fechar
          </button>
          <input aria-label="Campo" />
          <select aria-label="Lista">
            <option>a</option>
          </select>
          <textarea aria-label="Motivo" />
        </div>
      )
    }
    render(<Gaveta aoFechar={() => {}} />)
    const usuario = userEvent.setup()
    expect(screen.getByRole('button', { name: 'Fechar' })).toHaveFocus()
    // Tab percorre na ordem do documento e fecha o ciclo: Fechar, Campo, Lista, Motivo, de volta a Fechar.
    await usuario.tab()
    expect(screen.getByRole('textbox', { name: 'Campo' })).toHaveFocus()
    await usuario.tab()
    expect(screen.getByRole('combobox', { name: 'Lista' })).toHaveFocus()
    await usuario.tab()
    expect(screen.getByRole('textbox', { name: 'Motivo' })).toHaveFocus()
    await usuario.tab()
    expect(screen.getByRole('button', { name: 'Fechar' })).toHaveFocus()
    // Shift+Tab no primeiro vai ao ÚLTIMO da gaveta (a área de texto).
    await usuario.tab({ shift: true })
    expect(screen.getByRole('textbox', { name: 'Motivo' })).toHaveFocus()
  })

  it('tabIgnoraTabindexNegativoOcultosEContaGrupoDeRadioUmaVez', () => {
    const { container } = render(
      <div>
        <button type="button">A</button>
        <button type="button" tabIndex={-1}>fora-tabindex</button>
        <button type="button" hidden>fora-hidden</button>
        <div aria-hidden="true"><button type="button">fora-aria-hidden</button></div>
        <div style={{ display: 'none' }}><button type="button">fora-display</button></div>
        <input type="radio" name="g" aria-label="r1" />
        <input type="radio" name="g" aria-label="r2" defaultChecked />
        <input type="radio" name="g" aria-label="r3" />
        <input type="checkbox" aria-label="c" />
        <div tabIndex={0} aria-label="regiao" />
        <button type="button" disabled>fora-disabled</button>
        <button type="button">Z</button>
      </div>,
    )
    const nomes = elementosComTab(container.firstElementChild as HTMLElement).map((e) => e.getAttribute('aria-label') ?? e.textContent)
    // O grupo de rádio é uma parada só (o marcado); o último alcançável é o Z.
    expect(nomes).toEqual(['A', 'r2', 'c', 'regiao', 'Z'])
  })

  it('tabNoUltimoVoltaAoPrimeiroMesmoComElementosForaDoCicloNoFim', async () => {
    function Gaveta({ aoFechar }: { aoFechar: () => void }) {
      const { raiz, fechar, aoTeclar } = useModalAcessivel(aoFechar)
      return (
        <div ref={raiz} role="dialog" aria-modal="true" aria-label="G" onKeyDown={aoTeclar}>
          <button ref={fechar} type="button">Fechar</button>
          <button type="button">Ultimo</button>
          <button type="button" tabIndex={-1}>Escondido-do-Tab</button>
        </div>
      )
    }
    render(<Gaveta aoFechar={() => {}} />)
    const usuario = userEvent.setup()
    await usuario.tab()
    expect(screen.getByRole('button', { name: 'Ultimo' })).toHaveFocus()
    await usuario.tab()
    expect(screen.getByRole('button', { name: 'Fechar' })).toHaveFocus()
  })
})
