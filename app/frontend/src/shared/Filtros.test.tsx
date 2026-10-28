import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { LangProvider } from '../i18n/LangProvider'
import { simular } from '../test/telas'
import { Filtros, useFiltrosDaUrl, type CampoDeFiltro } from './Filtros'

const OPCOES = {
  squads: ['Alfa', 'Beta'],
  pessoas: ['ana', 'bruno'],
  tipos: ['Checklist', 'Task'],
  prioridades: ['High', 'Low'],
  os: ['Android 16.0 (16)', 'Android 17.0 (OneUI_9.0)'],
  campos: [
    { nome: 'SKU Type', slug: 'sku-type', valores: ['1st Carrier (Global)', 'MR'] },
    { nome: 'Checklist Type', slug: 'checklist-type', valores: ['Full'] },
  ],
  avisos: [],
  rapidos: [
    { id: 'so-task', rotuloPt: 'Só Task', rotuloEn: 'Task only', aplica: { tipo: ['Task'] } },
    { id: 'mr', rotuloPt: 'SKU MR', rotuloEn: 'SKU MR', aplica: { 'campo.sku-type': ['MR'] } },
  ],
}

function Url() {
  return <output data-testid="url">{useLocation().search}</output>
}

function Pagina({ campos }: { campos: CampoDeFiltro[] }) {
  const [sp, aplicar] = useFiltrosDaUrl()
  return <Filtros key={sp.toString()} campos={campos} valor={sp} aoAplicar={aplicar} />
}

function renderizar(campos: CampoDeFiltro[], inicial = '/') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <LangProvider>
        <MemoryRouter initialEntries={[inicial]}>
          <Pagina campos={campos} />
          <Url />
        </MemoryRouter>
      </LangProvider>
    </QueryClientProvider>,
  )
}

const url = () => new URLSearchParams(screen.getByTestId('url').textContent ?? '')

beforeEach(() => {
  localStorage.clear()
  simular({ '/api/filtros/opcoes': { status: 200, corpo: OPCOES } })
})
afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

describe('Filtros com as opções do backend', () => {
  it('pessoaViraSeletorComAsOpcoesDoBackend', async () => {
    renderizar(['squad', 'pessoa', 'os'])
    const pessoa = await screen.findByRole('combobox', { name: 'Pessoa' })
    await vi.waitFor(() => expect(within(pessoa).getAllByRole('option').map((o) => o.textContent)).toEqual(['Todas', 'ana', 'bruno']))
    // Nem pessoa, nem squad, nem OS aceitam texto livre.
    expect(screen.queryAllByRole('textbox')).toHaveLength(0)
    const squad = screen.getByRole('combobox', { name: 'Squad' })
    await vi.waitFor(() => expect(within(squad).getAllByRole('option').map((o) => o.textContent)).toEqual(['Todas', 'Alfa', 'Beta']))
    expect(within(screen.getByRole('combobox', { name: 'OS Version' })).getAllByRole('option')).toHaveLength(3)
  })

  it('campoDoDicionarioVaiParaAUrl', async () => {
    renderizar(['campos'])
    const u = userEvent.setup()
    const grupo = await screen.findByRole('group', { name: 'SKU Type' })
    await u.click(within(grupo).getByRole('checkbox', { name: 'MR' }))
    await u.click(within(grupo).getByRole('checkbox', { name: '1st Carrier (Global)' }))
    await u.click(screen.getByRole('button', { name: 'Aplicar filtros' }))
    expect(url().getAll('campo.sku-type')).toEqual(['MR', '1st Carrier (Global)'])
  })

  it('chipRemoveFiltro', async () => {
    renderizar(['squad', 'tipo', 'campos'], '/?squad=Alfa&tipo=Task&campo.sku-type=MR&granularidade=mes')
    const u = userEvent.setup()
    const chips = await screen.findByRole('list', { name: 'Filtros ativos' })
    expect(within(chips).getAllByRole('button')).toHaveLength(3)
    // Teclado: Tab até o chip e Enter remove só aquele valor, preservando o resto da URL.
    const chipSquad = within(chips).getByRole('button', { name: /Remover filtro Squad: Alfa/ })
    chipSquad.focus()
    await u.keyboard('{Enter}')
    expect(url().get('squad')).toBeNull()
    expect(url().get('tipo')).toBe('Task')
    expect(url().get('campo.sku-type')).toBe('MR')
    expect(url().get('granularidade')).toBe('mes')
  })

  it('valorForaDasOpcoesContinuaNoChip', async () => {
    renderizar(['squad', 'campos'], '/?squad=Antigo&campo.sku-type=mr')
    const chips = await screen.findByRole('list', { name: 'Filtros ativos' })
    await vi.waitFor(() => expect(within(chips).getByRole('button', { name: /Squad: Antigo/ })).toHaveTextContent('valor fora das opções atuais'))
    // "mr" difere de "MR" só na caixa: está dentro das opções, sem aviso.
    expect(within(chips).getByRole('button', { name: /SKU Type: mr/ })).not.toHaveTextContent('fora das opções')
  })

  it('filtroRapidoAplicaEDesfaz', async () => {
    renderizar(['tipo', 'campos'])
    const u = userEvent.setup()
    const grupo = await screen.findByRole('group', { name: 'Filtros rápidos' })
    await u.click(within(grupo).getByRole('button', { name: 'Só Task' }))
    expect(url().getAll('tipo')).toEqual(['Task'])
    await u.click(within(await screen.findByRole('group', { name: 'Filtros rápidos' })).getByRole('button', { name: 'Só Task' }))
    expect(url().getAll('tipo')).toEqual([])
  })

  it('filtroRapidoSoApareceSeATelaTemTodosOsFiltros', async () => {
    renderizar(['tipo'])
    const grupo = await screen.findByRole('group', { name: 'Filtros rápidos' })
    expect(within(grupo).queryByRole('button', { name: 'SKU MR' })).toBeNull()
    expect(within(grupo).getByRole('button', { name: 'Só Task' })).toBeInTheDocument()
  })

  it('semOpcoesAindaMostraOsFiltrosDaUrl', async () => {
    simular({ '/api/filtros/opcoes': { status: 500 } })
    renderizar(['squad', 'tipo'], '/?squad=Alfa')
    const chips = await screen.findByRole('list', { name: 'Filtros ativos' })
    expect(within(chips).getByRole('button', { name: /Squad: Alfa/ })).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Squad' })).toHaveValue('Alfa')
  })
})
