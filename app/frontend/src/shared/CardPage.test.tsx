import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ErroDaApi } from '../api/client'
import { LangProvider } from '../i18n/LangProvider'
import { CardPage } from './CardPage'
import { Filtros, useFiltrosDaUrl } from './Filtros'
import { textoDoAviso, temBloqueio, type Aviso } from './useCard'

function LocalizacaoAtual() {
  const l = useLocation()
  return <output data-testid="url">{l.search}</output>
}

function Pagina({ campos }: { campos: Parameters<typeof Filtros>[0]['campos'] }) {
  const [sp, aplicar] = useFiltrosDaUrl()
  return <Filtros key={sp.toString()} campos={campos} valor={sp} aoAplicar={aplicar} pessoas={['bruno', 'ana']} />
}

function renderizar(ui: ReactNode, inicial = '/') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <LangProvider>
        <MemoryRouter initialEntries={[inicial]}>
          {ui}
          <LocalizacaoAtual />
        </MemoryRouter>
      </LangProvider>
    </QueryClientProvider>,
  )
}

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

const consulta = (extra: Partial<Parameters<typeof CardPage>[0]['consulta']> = {}) => ({
  data: undefined,
  isPending: false,
  isError: false,
  isFetching: false,
  error: null,
  refetch: vi.fn(),
  ...extra,
})

describe('Filtros', () => {
  it('filtrosVaoParaAUrl', async () => {
    renderizar(<Pagina campos={['periodo', 'squad', 'pessoa', 'tipo', 'prioridade', 'unit']} />)
    const u = userEvent.setup()
    await u.type(screen.getByLabelText('De'), '2026-10-01')
    await u.type(screen.getByLabelText('Até'), '2026-10-30')
    await u.type(screen.getByLabelText('Squad'), 'Alfa')
    await u.selectOptions(screen.getByLabelText('Pessoa'), 'ana')
    await u.click(screen.getByRole('checkbox', { name: 'Checklist' }))
    await u.click(screen.getByRole('checkbox', { name: 'Checklist Review' }))
    await u.click(screen.getByRole('checkbox', { name: 'High' }))
    await u.click(screen.getByRole('button', { name: 'Dias corridos' }))
    await u.click(screen.getByRole('button', { name: 'Aplicar filtros' }))
    const p = new URLSearchParams(screen.getByTestId('url').textContent ?? '')
    expect(p.get('de')).toBe('2026-10-01')
    expect(p.get('ate')).toBe('2026-10-30')
    expect(p.get('squad')).toBe('Alfa')
    expect(p.get('pessoa')).toBe('ana')
    expect(p.getAll('tipo')).toEqual(['Checklist', 'Checklist Review'])
    expect(p.getAll('prioridade')).toEqual(['High'])
    expect(p.get('unit')).toBe('corrido')
  })

  it('urlInicialPreencheOsCamposEPreservaParametrosDeOutraTela', async () => {
    renderizar(<Pagina campos={['squad', 'tipo']} />, '/?squad=Beta&tipo=Checklist&granularidade=mes')
    expect(screen.getByLabelText('Squad')).toHaveValue('Beta')
    expect(screen.getByRole('checkbox', { name: 'Checklist' })).toBeChecked()
    const u = userEvent.setup()
    await u.clear(screen.getByLabelText('Squad'))
    await u.click(screen.getByRole('button', { name: 'Aplicar filtros' }))
    const p = new URLSearchParams(screen.getByTestId('url').textContent ?? '')
    expect(p.get('squad')).toBeNull()
    expect(p.get('granularidade')).toBe('mes')
    expect(p.getAll('tipo')).toEqual(['Checklist'])
  })

  it('pessoasEmOrdemAlfabetica', () => {
    renderizar(<Pagina campos={['pessoa']} />)
    const opcoes = screen.getAllByRole('option').map((o) => o.textContent)
    expect(opcoes).toEqual(['Todas', 'ana', 'bruno'])
  })
})

describe('CardPage', () => {
  it('mostraTituloPerguntaFiltrosEConteudo', () => {
    renderizar(
      <CardPage titulo="Abertas" pergunta="O que está aberto agora?" filtros={<span>filtros aqui</span>} consulta={consulta({ data: { x: 1 } })}>
        <p>conteúdo do card</p>
      </CardPage>,
    )
    expect(screen.getByRole('heading', { level: 1, name: 'Abertas' })).toBeInTheDocument()
    expect(screen.getByText('O que está aberto agora?')).toBeInTheDocument()
    expect(screen.getByText('filtros aqui')).toBeInTheDocument()
    expect(screen.getByText('conteúdo do card')).toBeInTheDocument()
  })

  it('semPermissao403MostraMensagem', () => {
    renderizar(
      <CardPage titulo="Abertas" pergunta="?" filtros={null} consulta={consulta({ isError: true, error: new ErroDaApi(403) })}>
        <p>não deveria aparecer</p>
      </CardPage>,
    )
    expect(screen.getByRole('alert')).toHaveTextContent(/não permite ver estes dados/i)
    expect(screen.queryByText('não deveria aparecer')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Tentar de novo' })).not.toBeInTheDocument()
  })

  it('filtroInvalido400EErroComTentarDeNovo', async () => {
    const refetch = vi.fn()
    const { rerender } = renderizar(
      <CardPage titulo="A" pergunta="?" filtros={null} consulta={consulta({ isError: true, error: new ErroDaApi(400) })}>
        x
      </CardPage>,
    )
    expect(screen.getByRole('alert')).toHaveTextContent(/filtro é inválido/i)
    rerender(
      <QueryClientProvider client={new QueryClient()}>
        <LangProvider>
          <MemoryRouter>
            <CardPage titulo="A" pergunta="?" filtros={null} consulta={consulta({ isError: true, error: new ErroDaApi(503), refetch })}>
              x
            </CardPage>
          </MemoryRouter>
        </LangProvider>
      </QueryClientProvider>,
    )
    await userEvent.click(screen.getByRole('button', { name: 'Tentar de novo' }))
    expect(refetch).toHaveBeenCalled()
    expect(screen.getByRole('alert')).toHaveTextContent('HTTP 503')
  })

  it('avisoBloqueanteEmDestaqueEInformativoSeparado', () => {
    const avisos: Aviso[] = [
      { codigo: 'STATUS_SEM_MAPEAMENTO', gravidade: 'bloqueante', mensagem: 'x', params: { status: 'Revisao Exotica' } },
      { codigo: 'AMOSTRA_PEQUENA', gravidade: 'informativo', mensagem: 'y', params: { tipo: 'Checklist', n: '3', minimo: '5' } },
    ]
    renderizar(
      <CardPage titulo="A" pergunta="?" filtros={null} consulta={consulta({ data: {} })} avisos={avisos}>
        x
      </CardPage>,
    )
    expect(screen.getByRole('alert')).toHaveTextContent('Revisao Exotica')
    expect(screen.getByText(/Poucas tasks de Checklist no histórico \(3 de no mínimo 5\)/)).toBeInTheDocument()
    expect(screen.getByRole('alert')).not.toHaveTextContent('Poucas tasks')
    expect(temBloqueio(avisos)).toBe(true)
    expect(temBloqueio(avisos.slice(1))).toBe(false)
  })

  it('botaoCsvSoComDados', async () => {
    const aoExportar = vi.fn()
    const { rerender } = renderizar(
      <CardPage titulo="A" pergunta="?" filtros={null} consulta={consulta({ isPending: true })} aoExportar={aoExportar}>
        x
      </CardPage>,
    )
    expect(screen.getByRole('button', { name: 'Exportar CSV' })).toBeDisabled()
    rerender(
      <QueryClientProvider client={new QueryClient()}>
        <LangProvider>
          <MemoryRouter>
            <CardPage titulo="A" pergunta="?" filtros={null} consulta={consulta({ data: {} })} aoExportar={aoExportar}>
              x
            </CardPage>
          </MemoryRouter>
        </LangProvider>
      </QueryClientProvider>,
    )
    await userEvent.click(screen.getByRole('button', { name: 'Exportar CSV' }))
    expect(aoExportar).toHaveBeenCalledTimes(1)
  })
})

describe('textoDoAviso', () => {
  const t = (k: string) => ({ 'cardpage.aviso.X': 'valor {n} de {faltando}' })[k] ?? k
  it('preencheMarcadoresENuncaMostraMarcadorCru', () => {
    expect(textoDoAviso({ codigo: 'X', gravidade: 'informativo', mensagem: 'm', params: { n: '4' } }, t)).toBe('valor 4 de …')
  })
  it('codigoDesconhecidoUsaAMensagemDoBackend', () => {
    expect(textoDoAviso({ codigo: 'NOVO', gravidade: 'informativo', mensagem: 'msg do backend' }, t)).toBe('msg do backend')
  })
})
