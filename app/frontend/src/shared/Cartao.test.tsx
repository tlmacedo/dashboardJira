import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactElement } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { LangProvider } from '../i18n/LangProvider'
import { Cartao } from './Cartao'
import type { CartaoCatalogo } from './useCatalogo'

// O código-fonte do componente, lido como texto, para provar que nunca injeta HTML.
const fontes = import.meta.glob('./{Cartao,AjudaDoCartao}.tsx', { query: '?raw', import: 'default', eager: true }) as Record<string, string>

const META: CartaoCatalogo = {
  id: 'TS-03',
  grupo: 'resultados',
  natureza: 'FLUXO',
  tela: 'tempo-em-status',
  tituloChave: 'tempo.kpi.paradas',
  oQueMostraChave: 'card.ts03.oQueMostra',
  comoCalculaChave: 'card.ts03.comoCalcula',
  fontes: ['ISSUE', 'CHANGELOG'],
  papeis: ['USUARIO', 'SQUAD_LEADER', 'COORDENADOR', 'GERENTE'],
  temDrill: true,
  exigeChangelog: true,
  exigeWorklog: false,
}

function simularCatalogo(cartoes: CartaoCatalogo[] = [META]) {
  const mock = vi.fn(async (entrada: RequestInfo | URL) => {
    if (String(entrada).split('?')[0] !== '/api/cartoes/catalogo') return new Response('', { status: 404 })
    return new Response(JSON.stringify({ cartoes }), { status: 200 })
  })
  vi.stubGlobal('fetch', mock)
  return mock
}

function renderizar(ui: ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <LangProvider>
        <MemoryRouter>{ui}</MemoryRouter>
      </LangProvider>
    </QueryClientProvider>,
  )
}

const AGORA = new Date('2026-10-25T16:00:00Z')

beforeEach(() => {
  localStorage.clear()
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('Cartao', () => {
  it('mostraTituloNaturezaValorEstadoEProcedencia', async () => {
    simularCatalogo()
    renderizar(
      <Cartao
        id="TS-03"
        titulo="Paradas além do normal"
        valor="17"
        legenda="Abertas acima do esperado"
        estado={{ tipo: 'problema', texto: '3 acima do P85' }}
        variacao="+2 vs semana passada"
        n={236}
        atualizadoEm="2026-10-25T15:52:00Z"
        agora={AGORA}
        aoAbrir={() => {}}
      />,
    )
    const cartao = await screen.findByRole('article', { name: /Paradas além do normal/ })
    expect(within(cartao).getByRole('heading', { name: 'Paradas além do normal' })).toBeInTheDocument()
    // Natureza vem do catálogo (texto, com forma, nunca só cor).
    expect(await within(cartao).findByText(/Fluxo/)).toBeInTheDocument()
    expect(cartao).toHaveTextContent('17')
    expect(cartao).toHaveTextContent('Abertas acima do esperado')
    expect(cartao).toHaveTextContent('▲ 3 acima do P85')
    expect(cartao).toHaveTextContent('+2 vs semana passada')
    // Procedência: n e "atualizado há 8 min" (16:00 menos 15:52).
    expect(cartao).toHaveTextContent('n = 236')
    expect(cartao).toHaveTextContent('atualizado há 8 min')
  })

  it('estadoTemSimboloETexto', () => {
    simularCatalogo([])
    const esperado = { ok: '✓', atencao: '●', problema: '▲', neutro: '■' } as const
    for (const [tipo, simbolo] of Object.entries(esperado)) {
      const { unmount } = renderizar(
        <Cartao id="TS-03" titulo="T" valor="1" estado={{ tipo: tipo as keyof typeof esperado, texto: `texto ${tipo}` }} />,
      )
      const estado = screen.getByText(`texto ${tipo}`).closest('.cartao-estado') as HTMLElement
      expect(estado.textContent).toBe(`${simbolo} texto ${tipo}`)
      // O símbolo é lido junto do texto, e a classe de cor é só reforço.
      expect(estado.className).toContain(`cartao-estado--${tipo}`)
      unmount()
    }
  })

  it('cartaoTemUmaAcaoPrincipalEIconeDeAjudaAlcancavel', async () => {
    simularCatalogo()
    const { container } = renderizar(<Cartao id="TS-03" titulo="Paradas além do normal" valor="17" aoAbrir={() => {}} />)
    const ajuda = await screen.findByRole('button', { name: 'Sobre este cartão' })
    // Nenhum botão dentro de botão.
    expect(container.querySelectorAll('button button')).toHaveLength(0)
    // Uma só ação principal, mais o ⓘ.
    const botoes = screen.getAllByRole('button')
    expect(botoes).toHaveLength(2)
    // Tab: primeiro o ⓘ, depois a ação principal.
    await userEvent.tab()
    expect(ajuda).toHaveFocus()
    await userEvent.tab()
    const acao = screen.getByRole('button', { name: /Paradas além do normal/ })
    expect(acao).toHaveFocus()
    expect(acao).toHaveTextContent('Ver tasks')
    // O ⓘ fica fora do botão principal (irmão, não filho).
    expect(acao.contains(ajuda)).toBe(false)
  })

  it('acaoPrincipalAbreAsTasks', async () => {
    simularCatalogo()
    const abrir = vi.fn()
    renderizar(<Cartao id="TS-03" titulo="Paradas além do normal" valor="17" aoAbrir={abrir} />)
    await userEvent.click(await screen.findByRole('button', { name: /Paradas além do normal/ }))
    expect(abrir).toHaveBeenCalledTimes(1)
  })

  it('ajudaAbreComEnterEFechaComEsc', async () => {
    simularCatalogo()
    renderizar(<Cartao id="TS-03" titulo="Paradas além do normal" valor="17" aoAbrir={() => {}} />)
    const ajuda = await screen.findByRole('button', { name: 'Sobre este cartão' })
    expect(ajuda).toHaveAttribute('aria-expanded', 'false')
    ajuda.focus()
    await userEvent.keyboard('{Enter}')
    expect(ajuda).toHaveAttribute('aria-expanded', 'true')
    expect(document.getElementById(ajuda.getAttribute('aria-controls') as string)).toBeInTheDocument()
    await userEvent.keyboard('{Escape}')
    expect(ajuda).toHaveAttribute('aria-expanded', 'false')
    expect(ajuda).toHaveFocus()
    expect(ajuda.getAttribute('aria-controls') && document.getElementById(ajuda.getAttribute('aria-controls') as string)).toBeFalsy()
  })

  it('ajudaMostraOQueMostraEComoCalcula', async () => {
    simularCatalogo()
    renderizar(<Cartao id="TS-03" titulo="Paradas além do normal" valor="17" aoAbrir={() => {}} />)
    await userEvent.click(await screen.findByRole('button', { name: 'Sobre este cartão' }))
    const balao = screen.getByRole('group', { name: 'Sobre este cartão' })
    expect(balao).toHaveTextContent('O que mostra')
    expect(balao).toHaveTextContent('Tasks abertas paradas no status atual além do normal para esse status.')
    expect(balao).toHaveTextContent('Como é calculado')
    expect(balao).toHaveTextContent('Aberta há mais de 1,5 × a mediana do status')
    expect(balao).toHaveTextContent('Fonte')
    expect(balao).toHaveTextContent('campos da issue')
    expect(balao).toHaveTextContent('histórico de mudanças (changelog)')
    expect(balao).toHaveTextContent('Visível para')
    expect(balao).toHaveTextContent('Squad Leader')
  })

  it('ajudaSegueOIdioma', async () => {
    localStorage.setItem('painel.idioma', 'en')
    simularCatalogo()
    renderizar(<Cartao id="TS-03" titulo="Stuck beyond normal" valor="17" aoAbrir={() => {}} />)
    await userEvent.click(await screen.findByRole('button', { name: 'About this card' }))
    const balao = screen.getByRole('group', { name: 'About this card' })
    expect(balao).toHaveTextContent('What it shows')
    expect(balao).toHaveTextContent('Open tasks stuck in their current status beyond what is normal for that status.')
    expect(balao).toHaveTextContent('How it is calculated')
  })

  it('semDrillNaoMostraVerTasks', async () => {
    simularCatalogo([{ ...META, temDrill: false }])
    renderizar(<Cartao id="TS-03" titulo="Paradas além do normal" valor="17" />)
    await screen.findByRole('button', { name: 'Sobre este cartão' })
    expect(screen.queryByText(/Ver tasks/)).toBeNull()
    // Só o ⓘ é botão; o cartão não promete uma gaveta que não existe.
    expect(screen.getAllByRole('button')).toHaveLength(1)
    expect(screen.getByRole('article')).toHaveTextContent('17')
  })

  it('variantesComCorpoPropriotemRodapeComAcao', async () => {
    simularCatalogo()
    const abrir = vi.fn()
    renderizar(
      <Cartao id="TS-03" variante="tabela" titulo="Tabela de paradas" aoAbrir={abrir}>
        <table aria-label="paradas">
          <tbody>
            <tr>
              <td>TG-107</td>
            </tr>
          </tbody>
        </table>
      </Cartao>,
    )
    const cartao = await screen.findByRole('article', { name: /Tabela de paradas/ })
    expect(within(cartao).getByRole('table', { name: 'paradas' })).toBeInTheDocument()
    await userEvent.click(within(cartao).getByRole('button', { name: /Ver tasks/ }))
    expect(abrir).toHaveBeenCalled()
  })
})

describe('estados do cartão', () => {
  it('estadosCarregandoVazioErroSemPermissaoIncompleto', async () => {
    simularCatalogo([])
    const { unmount } = renderizar(<Cartao id="TS-03" titulo="T" situacao="carregando" />)
    expect(screen.getByRole('article')).toHaveAttribute('aria-busy', 'true')
    expect(screen.getByText('Carregando o cartão…')).toBeInTheDocument()
    unmount()

    const vazio = renderizar(<Cartao id="TS-03" titulo="T" situacao="vazio" />)
    expect(screen.getByText('Nenhuma task neste recorte.')).toBeInTheDocument()
    expect(screen.getByText('Amplie o período ou remova algum filtro.')).toBeInTheDocument()
    vazio.unmount()

    const tentar = vi.fn()
    const erro = renderizar(<Cartao id="TS-03" titulo="T" situacao="erro" aoTentarDeNovo={tentar} />)
    expect(screen.getByRole('alert')).toHaveTextContent('Não foi possível carregar este cartão.')
    await userEvent.click(screen.getByRole('button', { name: 'Tentar de novo' }))
    expect(tentar).toHaveBeenCalledTimes(1)
    erro.unmount()

    const semPermissao = renderizar(<Cartao id="TS-03" titulo="T" situacao="semPermissao" valor="99" />)
    expect(screen.getByText('Seu papel não permite ver este cartão.')).toBeInTheDocument()
    // Sem permissão, o número nunca aparece.
    expect(screen.queryByText('99')).toBeNull()
    semPermissao.unmount()

    renderizar(<Cartao id="TS-03" titulo="T" valor="5" incompleto />)
    const cartao = screen.getByRole('article')
    // Incompleto mostra o número E o aviso com o caminho para a Qualidade dos dados.
    expect(cartao).toHaveTextContent('5')
    expect(cartao).toHaveTextContent('Dados incompletos')
    expect(within(cartao).getByRole('link', { name: 'Ver Qualidade dos dados' })).toHaveAttribute('href', '/qualidade')
  })

  it('valorVindoDaApiEhTexto', () => {
    simularCatalogo([])
    const perigoso = '<img src=x onerror="window.__xss=1">'
    const { container } = renderizar(<Cartao id="TS-03" titulo={perigoso} valor={perigoso} legenda={perigoso} estado={{ tipo: 'neutro', texto: perigoso }} />)
    expect(container.querySelector('img')).toBeNull()
    expect(container.textContent).toContain(perigoso)
    for (const [arquivo, codigo] of Object.entries(fontes)) {
      expect(codigo, arquivo).not.toContain('dangerouslySetInnerHTML')
      expect(codigo, arquivo).not.toContain('innerHTML')
    }
    expect(Object.keys(fontes).length).toBe(2)
  })

  it('semSaltoDeLayoutNoEsqueleto', () => {
    simularCatalogo([])
    const alturas: string[] = []
    for (const situacao of ['carregando', 'normal', 'vazio', 'erro'] as const) {
      const { unmount } = renderizar(<Cartao id="TS-03" titulo="T" valor="1" situacao={situacao} />)
      const altura = (screen.getByRole('article') as HTMLElement).style.minHeight
      expect(altura, situacao).toMatch(/^\d+px$/)
      alturas.push(altura)
      unmount()
    }
    // O esqueleto ocupa exatamente o que o cartão carregado ocupa.
    expect(new Set(alturas).size).toBe(1)
    // Cada variante tem a sua altura mínima fixa.
    const kpi = renderizar(<Cartao id="TS-03" titulo="T" valor="1" />)
    const alturaKpi = (screen.getByRole('article') as HTMLElement).style.minHeight
    kpi.unmount()
    renderizar(<Cartao id="TS-03" titulo="T" variante="serie" situacao="carregando" />)
    expect((screen.getByRole('article') as HTMLElement).style.minHeight).not.toBe(alturaKpi)
  })
})
