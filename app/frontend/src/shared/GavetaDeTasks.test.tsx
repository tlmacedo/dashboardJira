import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { LangProvider } from '../i18n/LangProvider'
import { GavetaDeTasks } from './GavetaDeTasks'
import type { LinhaDeTask, TasksDoCartao } from './tasksApi'

const linhas = (n: number): LinhaDeTask[] =>
  Array.from({ length: n }, (_, i) => ({
    key: `TG-${i + 1}`,
    resumo: `Task ${i + 1}`,
    responsavel: 'ana.silva',
    status: 'Em revisão',
    detalhe: `${i + 1},0 dias úteis em Em revisão`,
  }))

const resposta = (n: number, extra: Partial<TasksDoCartao> = {}): TasksDoCartao => ({
  tituloChave: 'tempo.kpi.paradas',
  definicaoChave: 'card.ts03.oQueMostra',
  total: n,
  colunas: ['chave', 'resumo', 'responsavel', 'status', 'detalhe'],
  linhas: linhas(n),
  linkJira: null,
  ...extra,
})

const ROTA = '/api/cartoes/TS-03/tasks'

function simular(status: number, corpo?: unknown) {
  const mock = vi.fn(async (entrada: RequestInfo | URL) => {
    if (String(entrada).split('?')[0] !== ROTA) return new Response('', { status: 404 })
    return new Response(corpo === undefined ? null : JSON.stringify(corpo), { status })
  })
  vi.stubGlobal('fetch', mock)
  return mock
}

/** Página com um botão que abre a gaveta, para provar que o foco volta a quem abriu. */
function Pagina(props: { resumo?: string; titulo?: string }) {
  const [aberta, setAberta] = useState(false)
  return (
    <div data-testid="pagina">
      <button type="button" onClick={() => setAberta(true)}>
        Abrir gaveta
      </button>
      <a href="#fora">Link fora da gaveta</a>
      {aberta && (
        <GavetaDeTasks
          cartaoId="TS-03"
          segmento="paradas"
          filtros={new URLSearchParams({ squad: 'Alfa', unit: 'corrido' })}
          titulo={props.titulo ?? 'Paradas além do normal'}
          resumoDosFiltros={props.resumo ?? 'Squad: Alfa (dias corridos)'}
          aoFechar={() => setAberta(false)}
        />
      )}
    </div>
  )
}

function renderizar(props: { resumo?: string; titulo?: string } = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <LangProvider>
        <Pagina {...props} />
      </LangProvider>
    </QueryClientProvider>,
  )
}

/** Renderiza a página (se o teste ainda não o fez) e abre a gaveta pelo botão, como uma pessoa faria. */
async function abrir() {
  if (!screen.queryByTestId('pagina')) renderizar()
  await userEvent.click(screen.getByRole('button', { name: 'Abrir gaveta' }))
  const gaveta = await screen.findByRole('dialog')
  return gaveta
}

beforeEach(() => {
  localStorage.clear()
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('GavetaDeTasks: teclado e foco', () => {
  it('focoVaiParaFecharEOrestoDaPaginaFicaInerte', async () => {
    simular(200, resposta(2))
    const { container } = renderizar()
    const gaveta = await abrir()
    expect(container).toHaveAttribute('inert')
    expect(gaveta.closest('[inert]')).toBeNull()
    expect(within(gaveta).getByRole('button', { name: 'Fechar' })).toHaveFocus()
  })

  it('escFechaEDevolveOFoco', async () => {
    simular(200, resposta(2))
    const { container } = renderizar()
    const gatilho = screen.getByRole('button', { name: 'Abrir gaveta' })
    await abrir()
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(container).not.toHaveAttribute('inert')
    expect(gatilho).toHaveFocus()
  })

  it('tabFicaPresoNaGaveta', async () => {
    simular(200, resposta(3, { linkJira: 'https://jira.exemplo.invalid/issues/?jql=x' }))
    const gaveta = await abrir()
    await within(gaveta).findByText('TG-1')
    const dentro = () => gaveta.contains(document.activeElement)
    // Muito mais Tabs do que elementos focáveis: o foco nunca sai da gaveta.
    for (let i = 0; i < 12; i++) {
      await userEvent.tab()
      expect(dentro(), `Tab ${i + 1}`).toBe(true)
    }
    for (let i = 0; i < 12; i++) {
      await userEvent.tab({ shift: true })
      expect(dentro(), `Shift+Tab ${i + 1}`).toBe(true)
    }
    expect(screen.getByRole('link', { name: 'Link fora da gaveta', hidden: true })).not.toHaveFocus()
  })

  it('cliqueNoFundoFecha', async () => {
    simular(200, resposta(1))
    await abrir()
    const fundo = document.querySelector('.gaveta-fundo') as HTMLElement
    await userEvent.click(fundo)
    expect(screen.queryByRole('dialog')).toBeNull()
  })
})

describe('GavetaDeTasks: chave repetida', () => {
  it('mostraAsDuasLinhasDaMesmaIssueSemAvisoDeChaveDoReact', async () => {
    // Dois sinais da auditoria na mesma issue: duas linhas com a mesma chave de task.
    const duplicadas = resposta(2, {
      linhas: [
        { key: 'TG-5', resumo: 'Task 5', responsavel: 'ana.silva', status: 'Em revisão', detalhe: 'Primeiro sinal' },
        { key: 'TG-5', resumo: 'Task 5', responsavel: 'ana.silva', status: 'Em revisão', detalhe: 'Segundo sinal' },
      ],
    })
    simular(200, duplicadas)
    const erros = vi.spyOn(console, 'error').mockImplementation(() => {})
    const gaveta = await abrir()
    await within(gaveta).findByText('Primeiro sinal')
    expect(within(gaveta).getByText('Segundo sinal')).toBeInTheDocument()
    expect(within(gaveta).getAllByRole('row')).toHaveLength(3)
    expect(erros.mock.calls.some((c) => String(c[0]).includes('same key'))).toBe(false)
  })
})

describe('GavetaDeTasks: conteúdo', () => {
  it('consomeARotaGenericaComSegmentoEFiltros', async () => {
    const mock = simular(200, resposta(2))
    await abrir()
    await screen.findByText('TG-1')
    const url = String(mock.mock.calls[0][0])
    expect(url.startsWith('/api/cartoes/TS-03/tasks?')).toBe(true)
    expect(url).toContain('segmento=paradas')
    expect(url).toContain('squad=Alfa')
    expect(url).toContain('unit=corrido')
  })

  it('mostraDefinicaoDoNumeroEFiltrosAtivos', async () => {
    simular(200, resposta(3))
    const gaveta = await abrir()
    await within(gaveta).findByText('TG-1')
    expect(within(gaveta).getByRole('heading', { name: 'Paradas além do normal' })).toBeInTheDocument()
    expect(within(gaveta).getByText('3 tasks')).toBeInTheDocument()
    // A definição é o mesmo texto do ⓘ do cartão.
    expect(gaveta).toHaveTextContent('Tasks abertas paradas no status atual além do normal para esse status.')
    expect(gaveta).toHaveTextContent('Filtros ativos: Squad: Alfa (dias corridos)')
  })

  it('tituloVemDaRespostaQuandoATelaNaoInforma', async () => {
    simular(200, resposta(1))
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <LangProvider>
          <GavetaDeTasks cartaoId="TS-03" segmento="paradas" filtros={new URLSearchParams()} aoFechar={() => {}} />
        </LangProvider>
      </QueryClientProvider>,
    )
    expect(await screen.findByRole('heading', { name: 'Paradas além do normal' })).toBeInTheDocument()
  })

  it('colunaDetalheExplicaPorQueEntrou', async () => {
    simular(200, resposta(2))
    const gaveta = await abrir()
    const tabela = await within(gaveta).findByRole('table')
    expect(within(tabela).getByRole('columnheader', { name: 'Detalhe' })).toBeInTheDocument()
    expect(within(tabela).getByText('2,0 dias úteis em Em revisão')).toBeInTheDocument()
    expect(within(tabela).getAllByRole('row')).toHaveLength(3)
  })

  it('ordemDaListaVemDaApiSemRanking', async () => {
    simular(
      200,
      resposta(0, {
        total: 4,
        linhas: [
          { key: 'TG-2', resumo: 'a', responsavel: 'zeca', status: 'S', detalhe: '9,0 dias' },
          { key: 'TG-10', resumo: 'b', responsavel: 'ana.silva', status: 'S', detalhe: '1,0 dias' },
          { key: 'TG-11', resumo: 'c', responsavel: null, status: 'S', detalhe: '5,0 dias' },
          { key: 'TG-30', resumo: 'd', responsavel: 'bia', status: 'S', detalhe: '3,0 dias' },
        ],
      }),
    )
    const gaveta = await abrir()
    const tabela = await within(gaveta).findByRole('table')
    const chaves = within(tabela).getAllByRole('rowheader').map((c) => c.textContent)
    // A ordem natural de chaves vem do backend; a gaveta não reordena por responsável nem por tempo.
    expect(chaves).toEqual(['TG-2', 'TG-10', 'TG-11', 'TG-30'])
    // Responsável fora da visão (ou ausente) é mostrado como traço, nunca como nome.
    expect(within(tabela).getAllByText('—')).toHaveLength(1)
  })

  it('valorVindoDaApiEhTexto', async () => {
    simular(200, resposta(0, { total: 1, linhas: [{ key: 'TG-1', resumo: '<img src=x onerror=alert(1)>', responsavel: null, status: 'S', detalhe: 'd' }] }))
    const gaveta = await abrir()
    await within(gaveta).findByText('<img src=x onerror=alert(1)>')
    expect(gaveta.querySelector('img')).toBeNull()
  })

  it('estadosCarregandoVazioPermissaoEErro', async () => {
    simular(200, resposta(0))
    const { unmount } = renderizar()
    const gaveta = await abrir()
    expect(await within(gaveta).findByText('Nenhuma task neste recorte.')).toBeInTheDocument()
    expect(within(gaveta).getByRole('button', { name: 'Exportar CSV' })).toBeDisabled()
    unmount()

    simular(403)
    renderizar()
    await abrir()
    expect(await screen.findByText(/Seu papel não permite ver estes dados/)).toBeInTheDocument()
    cleanup()

    const mock = simular(500)
    renderizar()
    await abrir()
    await userEvent.click(await screen.findByRole('button', { name: 'Tentar de novo' }))
    await vi.waitFor(() => expect(mock.mock.calls.length).toBeGreaterThan(1))
  })
})

describe('GavetaDeTasks: abrir no Jira', () => {
  const LINK = 'https://jira.exemplo.invalid/issues/?jql=key%20in%20(%22TG-1%22)'

  it('semLinkJiraNaoMostraAbrirNoJira', async () => {
    simular(200, resposta(2, { linkJira: null }))
    const gaveta = await abrir()
    await within(gaveta).findByText('TG-1')
    expect(within(gaveta).queryByRole('link', { name: /Abrir no Jira/ })).toBeNull()
    expect(within(gaveta).queryByText(/Abrir no Jira/)).toBeNull()
  })

  it('comLinkJiraAbreEmNovaAbaComNoopener', async () => {
    simular(200, resposta(2, { linkJira: LINK }))
    const gaveta = await abrir()
    const link = await within(gaveta).findByRole('link', { name: /Abrir no Jira/ })
    // O link vem pronto da API; o front só o usa.
    expect(link).toHaveAttribute('href', LINK)
    expect(link).toHaveAttribute('target', '_blank')
    expect(link.getAttribute('rel')).toContain('noopener')
    expect(link.getAttribute('rel')).toContain('noreferrer')
  })

  it('linkQueNaoEHttpsNuncaViraBotao', async () => {
    for (const ruim of ['javascript:alert(1)', 'http://jira.exemplo.invalid/x', 'data:text/html,x', '//jira.exemplo.invalid/x']) {
      simular(200, resposta(1, { linkJira: ruim }))
      renderizar()
      const gaveta = await abrir()
      await within(gaveta).findByText('TG-1')
      expect(within(gaveta).queryByRole('link', { name: /Abrir no Jira/ }), ruim).toBeNull()
      cleanup()
    }
  })

  it('acimaDe100ChavesAvisaEOferecePeloCsv', async () => {
    simular(200, resposta(101, { linkJira: null }))
    const gaveta = await abrir()
    await within(gaveta).findByText('TG-1')
    expect(gaveta).toHaveTextContent('Com mais de 100 tasks não há link para o Jira. Use a exportação em CSV.')
    expect(within(gaveta).getByRole('button', { name: 'Exportar CSV' })).toBeEnabled()
    expect(within(gaveta).queryByRole('link', { name: /Abrir no Jira/ })).toBeNull()
  })

  it('ateOLimiteNaoHaAviso', async () => {
    simular(200, resposta(100, { linkJira: LINK }))
    const gaveta = await abrir()
    await within(gaveta).findByText('TG-1')
    expect(gaveta).not.toHaveTextContent('Com mais de 100 tasks')
  })
})

describe('GavetaDeTasks: CSV', () => {
  async function csvGerado(gaveta: HTMLElement): Promise<string> {
    let blobGerado: Blob | null = null
    URL.createObjectURL = (b: Blob | MediaSource) => {
      blobGerado = b as Blob
      return 'blob:x'
    }
    URL.revokeObjectURL = () => {}
    await userEvent.click(within(gaveta).getByRole('button', { name: 'Exportar CSV' }))
    return new Promise<string>((ok) => {
      const leitor = new FileReader()
      leitor.onload = () => ok(String(leitor.result))
      leitor.readAsText(blobGerado as unknown as Blob)
    })
  }

  it('csvLevaFiltrosEDefinicao', async () => {
    simular(200, resposta(3))
    const gaveta = await abrir()
    await within(gaveta).findByText('TG-1')
    const texto = await csvGerado(gaveta)
    expect(texto).toContain('# Paradas além do normal')
    expect(texto).toContain('# filtros: Squad: Alfa (dias corridos)')
    expect(texto).toContain('Tasks abertas paradas no status atual além do normal para esse status.')
    expect(texto).toContain('"TG-1";"Task 1";"ana.silva";"Em revisão"')
  })

  it('csvNeutralizaFormulaDasCelulas', async () => {
    simular(200, resposta(0, { total: 1, linhas: [{ key: 'TG-1', resumo: '=HYPERLINK("x")', responsavel: null, status: '+1', detalhe: '@x' }] }))
    const gaveta = await abrir()
    await within(gaveta).findByText('TG-1')
    const texto = await csvGerado(gaveta)
    expect(texto).toContain(`"'=HYPERLINK(""x"")"`)
    expect(texto).toContain(`"'+1"`)
    expect(texto).toContain(`"'@x"`)
  })
})
