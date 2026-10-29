import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Tarefa } from '../api/client'
import { LangProvider } from '../i18n/LangProvider'
import { simular } from '../test/telas'
import { TarefaDrawer } from './TarefaDrawer'

const TAREFA: Tarefa = {
  key: 'TG-101',
  resumo: 'Ajustar a tela',
  tipo: 'Checklist',
  status: 'In Progress',
  categoria: 'ACTIVE',
  resolucao: null,
  responsavel: 'ana',
  prioridade: 'High',
  criada: '2026-09-28T16:00:00Z',
  atualizada: '2026-10-20T16:00:00Z',
  resolvida: null,
  vencimento: '2026-10-28',
  vencimentoOriginal: '2026-10-20',
  labels: ['LATE_SENT'],
  campos: { 'SKU Type': 'MR', 'Model Name': 'MODELO-A' },
  tempoPorStatus: [
    { status: 'Open', dias: 2 },
    { status: 'In Progress', dias: 8.5 },
  ],
  posse: [
    { pessoa: 'ana', semResponsavel: false, dias: 6, fracao: 0.6 },
    { pessoa: null, semResponsavel: false, dias: 4, fracao: 0.4 },
  ],
  fatoresDeRisco: ['VENCIDA', 'IDADE_ACIMA_P85'],
  historico: [
    { at: '2026-10-20T16:00:00Z', campo: 'duedate', de: '2026-10-20', para: '2026-10-28', autor: 'pl.alfa', autorizado: true, motivo: null, valorGuardado: true },
    { at: '2026-10-21T16:00:00Z', campo: 'summary', de: null, para: null, autor: 'pl.alfa', autorizado: null, motivo: null, valorGuardado: false },
  ],
  linkJira: 'https://jira.exemplo.invalid/browse/TG-101',
  avisos: [],
}

/** A gaveta só abre quando o botão de origem é clicado, para o teste poder conferir a volta do foco. */
function Pagina() {
  const [aberta, setAberta] = useState(false)
  return (
    <>
      <button type="button" onClick={() => setAberta(true)}>
        origem
      </button>
      {aberta && <TarefaDrawer chave="TG-101" aoFechar={() => setAberta(false)} />}
      {!aberta && <p>fechada</p>}
    </>
  )
}

function com(extra: Partial<Tarefa>) {
  simular({ '/api/tarefa/TG-101': { status: 200, corpo: { ...TAREFA, ...extra } } })
}

/** Clica na origem para abrir a gaveta e devolve o diálogo já com o conteúdo carregado. */
async function abrir() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <LangProvider>
        <Pagina />
      </LangProvider>
    </QueryClientProvider>,
  )
  await userEvent.click(screen.getByRole('button', { name: 'origem' }))
  await screen.findByRole('heading', { level: 3, name: /Tempo por status|Time per status/ })
  return screen.getByRole('dialog')
}

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

describe('TarefaDrawer', () => {
  it('mostraSituacaoTempoPosseFatoresECampos', async () => {
    com({})
    const gaveta = await abrir()
    expect(gaveta).toHaveAttribute('aria-modal', 'true')
    expect(within(gaveta).getByRole('heading', { level: 2 })).toHaveTextContent('Task TG-101')
    // Status com símbolo e palavra, vencimento atual e original.
    const status = within(gaveta).getByText('Status', { selector: 'dt' }).nextElementSibling
    expect(status).toHaveTextContent('●')
    expect(status).toHaveTextContent('In Progress')
    expect(within(gaveta).getByText('28/10/2026')).toBeInTheDocument()
    expect(within(gaveta).getByText('20/10/2026')).toBeInTheDocument()
    // Tempo por status em tabela, posse, fatores traduzidos e campos do dicionário.
    const tempo = within(gaveta).getByRole('table', { name: 'Tempo por status' })
    expect(within(tempo).getAllByRole('rowheader').map((c) => c.textContent)).toEqual(['Open', 'In Progress'])
    expect(within(gaveta).getByRole('table', { name: 'Posse' })).toHaveTextContent('60%')
    expect(within(gaveta).getByText('Vencida')).toBeInTheDocument()
    expect(within(gaveta).getByText('MODELO-A')).toBeInTheDocument()
    expect(within(gaveta).getByText('LATE_SENT')).toBeInTheDocument()
  })

  it('outraPessoaNaPosse', async () => {
    com({})
    const gaveta = await abrir()
    const posse = within(gaveta).getByRole('table', { name: 'Posse' })
    expect(within(posse).getAllByRole('rowheader').map((c) => c.textContent)).toEqual(['ana', 'outra pessoa'])
  })

  it('semResponsavelNaPosse', async () => {
    com({ posse: [{ pessoa: null, semResponsavel: true, dias: 3, fracao: 1 }] })
    const gaveta = await abrir()
    expect(within(gaveta).getByRole('rowheader', { name: 'sem responsável' })).toBeInTheDocument()
  })

  it('historicoQuandoVem', async () => {
    com({})
    const gaveta = await abrir()
    const tabela = within(gaveta).getByRole('table', { name: /Histórico/ })
    expect(within(tabela).getByText('valor não guardado (privacidade)')).toBeInTheDocument()
  })

  it('historicoSoQuandoVem', async () => {
    com({ historico: null })
    const gaveta = await abrir()
    expect(within(gaveta).queryByRole('table', { name: /Histórico/ })).toBeNull()
    expect(within(gaveta).queryByRole('heading', { name: 'Histórico' })).toBeNull()
  })

  it('botaoJiraSoComLink', async () => {
    com({ linkJira: null })
    const gaveta = await abrir()
    expect(within(gaveta).queryByRole('link', { name: /Abrir no Jira/ })).toBeNull()
  })

  it('botaoJiraAbreEmNovaAbaSemOpener', async () => {
    com({})
    const gaveta = await abrir()
    const link = within(gaveta).getByRole('link', { name: /Abrir no Jira/ })
    expect(link).toHaveAttribute('href', 'https://jira.exemplo.invalid/browse/TG-101')
    expect(link).toHaveAttribute('target', '_blank')
    expect(link.getAttribute('rel')).toContain('noopener')
    expect(link.getAttribute('rel')).toContain('noreferrer')
  })

  it('linkInseguroNaoViraBotao', async () => {
    com({ linkJira: 'javascript:alert(1)' })
    let gaveta = await abrir()
    expect(within(gaveta).queryByRole('link', { name: /Abrir no Jira/ })).toBeNull()
    cleanup()
    com({ linkJira: 'https://u:p@jira.exemplo.invalid/browse/TG-101' })
    gaveta = await abrir()
    expect(within(gaveta).queryByRole('link', { name: /Abrir no Jira/ })).toBeNull()
  })

  it('escFechaEDevolveFoco', async () => {
    com({})
    await abrir()
    expect(screen.getByRole('button', { name: 'Fechar' })).toHaveFocus()
    await userEvent.keyboard('{Escape}')
    expect(await screen.findByText('fechada')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'origem' })).toHaveFocus()
  })

  it('naoEncontradaMostraMensagemSemDetalhe', async () => {
    simular({ '/api/tarefa/TG-101': { status: 404 } })
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <LangProvider>
          <Pagina />
        </LangProvider>
      </QueryClientProvider>,
    )
    await userEvent.click(screen.getByRole('button', { name: 'origem' }))
    expect(await screen.findByText('Task não encontrada na sua visão.')).toBeInTheDocument()
  })

  it('emInglesTraduzAsSecoes', async () => {
    localStorage.setItem('painel.idioma', 'en')
    com({})
    const gaveta = await abrir()
    expect(within(gaveta).getByRole('table', { name: 'Time per status' })).toBeInTheDocument()
    expect(within(gaveta).getByText('Overdue')).toBeInTheDocument()
    expect(within(gaveta).getByText('10/28/2026')).toBeInTheDocument()
  })
})
