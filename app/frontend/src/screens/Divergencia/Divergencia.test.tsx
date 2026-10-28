import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { LangProvider } from '../../i18n/LangProvider'
import type { TasksDoCartao } from '../../shared/tasksApi'
import { Divergencia } from './Divergencia'
import type { TaskReviewDto } from './api'

const BASE = 'https://jira.exemplo.invalid'

const DTO: TaskReviewDto = {
  pares: 4,
  divergentes: 1,
  percentual: 25,
  avisos: [],
  divergencias: [
    {
      task: 'TG-13',
      review: 'TG-113',
      vencimentoTask: '2026-10-15',
      vencimentoReview: '2026-10-20',
      responsavelTask: 'ana',
      responsavelReview: 'bruno',
      divergente: true,
      urlTask: `${BASE}/browse/TG-13`,
      urlReview: `${BASE}/browse/TG-113`,
    },
  ],
}

const tasks = (n: number): TasksDoCartao => ({
  tituloChave: 'card.dv01.titulo',
  definicaoChave: 'card.dv01.oQueMostra',
  total: n,
  colunas: ['chave', 'resumo', 'responsavel', 'status', 'detalhe'],
  linkJira: null,
  linhas: Array.from({ length: n }, (_, i) => ({ key: `TG-${i + 13}`, resumo: `Task ${i}`, responsavel: 'ana', status: 'Em andamento', detalhe: 'Review x' })),
})

type Resp = { status: number; corpo?: unknown }

function simular(rotas: Record<string, Resp>) {
  const mock = vi.fn(async (entrada: RequestInfo | URL) => {
    const r = rotas[String(entrada).split('?')[0]]
    if (!r) return new Response('', { status: 404 })
    return new Response(r.corpo === undefined ? null : JSON.stringify(r.corpo), { status: r.status })
  })
  vi.stubGlobal('fetch', mock)
  return mock
}

function renderizar(inicial = '/divergencia') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <LangProvider>
        <MemoryRouter initialEntries={[inicial]}>
          <Routes>
            <Route path="/divergencia" element={<Divergencia />} />
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

const CARD = '/api/cards/task-review'

describe('tela Divergência Task × Review', () => {
  it('mostraAvisoFixo', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizar()
    expect(await screen.findByRole('note')).toHaveTextContent('Indício, não prova')
  })

  it('divergenciaMostraPercentual', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizar()
    const kpi = await screen.findByRole('button', { name: /pares divergentes/i })
    expect(kpi).toHaveTextContent('25%')
    expect(kpi).toHaveTextContent('1 de 4')
    expect(screen.getByRole('button', { name: /^Pares Task × Review/ })).toHaveTextContent('4')
  })

  it('divergenciaAbreLista', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO }, '/api/cartoes/DV-01/tasks': { status: 200, corpo: tasks(1) } })
    renderizar('/divergencia?squad=Alfa')
    await userEvent.click(await screen.findByRole('button', { name: /pares divergentes/i }))
    const gaveta = await screen.findByRole('dialog')
    await within(gaveta).findByText('TG-13')
    expect(within(gaveta).getAllByRole('row')).toHaveLength(2)
    const url = String(mock.mock.calls.find((c) => String(c[0]).startsWith('/api/cartoes/DV-01/tasks'))?.[0])
    expect(url).toContain('segmento=divergentes')
    expect(url).toContain('squad=Alfa')
  })

  it('divergenciaLinkParaATaskNoJira', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizar()
    const tabela = await screen.findByRole('table', { name: 'Pares com vencimentos diferentes' })
    const linkTask = within(tabela).getByRole('link', { name: /TG-13/ })
    expect(linkTask).toHaveAttribute('href', `${BASE}/browse/TG-13`)
    expect(linkTask).toHaveAttribute('target', '_blank')
    expect(linkTask.getAttribute('rel')).toContain('noopener')
    expect(linkTask.getAttribute('rel')).toContain('noreferrer')
    expect(linkTask).toHaveTextContent('abrir no Jira')
    const linkReview = within(tabela).getByRole('link', { name: /TG-113/ })
    expect(linkReview).toHaveAttribute('href', `${BASE}/browse/TG-113`)
    // Não há como corrigir pelo painel.
    expect(screen.queryByRole('button', { name: /corrigir|ajustar|salvar/i })).toBeNull()
    expect(within(tabela).queryAllByRole('button')).toHaveLength(0)
  })

  it('semUrlMostraSoATexto', async () => {
    simular({ [CARD]: { status: 200, corpo: { ...DTO, divergencias: [{ ...DTO.divergencias[0], urlTask: null, urlReview: null }] } } })
    renderizar()
    const tabela = await screen.findByRole('table', { name: 'Pares com vencimentos diferentes' })
    expect(within(tabela).queryAllByRole('link')).toHaveLength(0)
    expect(within(tabela).getByText('TG-13')).toBeInTheDocument()
    expect(within(tabela).getByText('TG-113')).toBeInTheDocument()
  })

  it('urlInseguraNaoViraLink', async () => {
    const insegura = { ...DTO.divergencias[0], urlTask: 'javascript:alert(1)', urlReview: 'http://jira.exemplo.invalid/browse/TG-113' }
    simular({ [CARD]: { status: 200, corpo: { ...DTO, divergencias: [insegura] } } })
    renderizar()
    const tabela = await screen.findByRole('table', { name: 'Pares com vencimentos diferentes' })
    expect(within(tabela).queryAllByRole('link')).toHaveLength(0)
  })

  it('mostraOsDoisVencimentosComSimbolo', async () => {
    simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizar()
    const tabela = await screen.findByRole('table', { name: 'Pares com vencimentos diferentes' })
    const linha = within(tabela).getByRole('row', { name: /TG-13/ })
    expect(linha).toHaveTextContent('15/10/2026')
    expect(linha).toHaveTextContent('20/10/2026')
    expect(linha).toHaveTextContent(/▲\s*divergente/)
  })

  it('semParesMostraAvisoDeVinculosIndisponiveis', async () => {
    simular({
      [CARD]: {
        status: 200,
        corpo: { pares: 0, divergentes: 0, percentual: null, divergencias: [], avisos: [{ codigo: 'VINCULOS_INDISPONIVEIS', gravidade: 'informativo', mensagem: '' }] },
      },
    })
    renderizar()
    expect(await screen.findByText(/nenhum vínculo/i)).toBeInTheDocument()
    // Sem pares não há percentual nem gaveta: o indicador mostra o traço e não é um botão.
    const indicadores = screen.getByRole('group', { name: 'Indicadores' })
    expect(within(indicadores).getByText('—')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /pares divergentes/i })).toBeNull()
  })

  it('semPermissao403', async () => {
    simular({ [CARD]: { status: 403 } })
    renderizar()
    expect(await screen.findByText(/apenas a coordenação e o gerente/i)).toBeInTheDocument()
    expect(screen.queryByRole('table')).toBeNull()
  })

  it('filtrosVaoParaAApi', async () => {
    const mock = simular({ [CARD]: { status: 200, corpo: DTO } })
    renderizar('/divergencia?squad=Alfa&pessoa=ana')
    await screen.findByRole('table', { name: 'Pares com vencimentos diferentes' })
    const url = String(mock.mock.calls[0][0])
    expect(url).toContain('squad=Alfa')
    expect(url).toContain('pessoa=ana')
  })
})
