import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { LangProvider } from '../i18n/LangProvider'
import { simular } from '../test/telas'
import { BuscaPorChave } from './BuscaPorChave'

const OPCOES = (prefixos: string[]) => ({
  squads: [], pessoas: [], tipos: [], prioridades: [], os: [], campos: [], avisos: [], rapidos: [], prefixosDeChave: prefixos,
})

const TAREFA = (key: string) => ({
  key, resumo: 'Task fictícia', tipo: 'Checklist', status: 'Open', categoria: 'TODO', resolucao: null, responsavel: null, prioridade: null,
  criada: '2026-10-01T12:00:00Z', atualizada: null, resolvida: null, vencimento: null, vencimentoOriginal: null, labels: [], campos: {},
  tempoPorStatus: [], posse: [], fatoresDeRisco: [], historico: null, linkJira: null, avisos: [],
})

function renderizar() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <LangProvider>
        <BuscaPorChave />
      </LangProvider>
    </QueryClientProvider>,
  )
}

const caixa = () => screen.getByRole('textbox', { name: 'Buscar task pela chave' })

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

describe('Busca rápida por chave (I-26)', () => {
  it('chaveCompletaAbreAGaveta', async () => {
    const mock = simular({ '/api/tarefa/TG-107': { status: 200, corpo: TAREFA('TG-107') } })
    renderizar()
    await userEvent.type(caixa(), 'tg-107{Enter}')
    expect(await screen.findByRole('heading', { level: 2, name: 'Task TG-107' })).toBeInTheDocument()
    // Não consulta as opções quando a chave já vem completa.
    expect(mock.mock.calls.map((c) => String(c[0]))).toEqual(['/api/tarefa/TG-107'])
  })

  it('soONumeroUsaOUnicoPrefixoDaVisao', async () => {
    simular({
      '/api/filtros/opcoes': { status: 200, corpo: OPCOES(['TG']) },
      '/api/tarefa/TG-107': { status: 200, corpo: TAREFA('TG-107') },
    })
    renderizar()
    await userEvent.type(caixa(), '107')
    await userEvent.click(screen.getByRole('button', { name: 'Abrir task' }))
    expect(await screen.findByRole('heading', { level: 2, name: 'Task TG-107' })).toBeInTheDocument()
  })

  it('soONumeroComMaisDeUmPrefixoPedeAChaveCompleta', async () => {
    const mock = simular({ '/api/filtros/opcoes': { status: 200, corpo: OPCOES(['AA', 'TG']) } })
    renderizar()
    await userEvent.type(caixa(), '107{Enter}')
    expect(await screen.findByRole('alert')).toHaveTextContent('Digite a chave (TG-123) ou só o número.')
    expect(mock.mock.calls.map((c) => String(c[0]))).toEqual(['/api/filtros/opcoes'])
  })

  it('textoForaDoFormatoNaoChamaARede', async () => {
    const mock = simular({})
    renderizar()
    await userEvent.type(caixa(), 'TG-1;drop table{Enter}')
    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(mock).not.toHaveBeenCalled()
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('chaveForaDaVisaoMostraNaoEncontradaSemRevelarNada', async () => {
    simular({ '/api/tarefa/TG-999': { status: 404 } })
    renderizar()
    await userEvent.type(caixa(), 'TG-999{Enter}')
    expect(await screen.findByText('Task não encontrada na sua visão.')).toBeInTheDocument()
  })
})
