import { cleanup, screen } from '@testing-library/react'
import type { ComponentType } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderizarTela } from '../test/telas'

/**
 * Teste dirigido por tabela: toda tela de dados montada sobre o `CardPage` mostra o estado certo para 403, 401 e erro de
 * servidor, sem nenhum texto técnico (status HTTP, mensagem de exceção). Telas com tratamento próprio (Auditoria,
 * Divergência, Integridade, Sincronização, Mapa de cartões, Tempo em status, Detalhe) têm os seus testes de estado.
 */
const TELAS: { rota: string; card: string; carregar: () => Promise<ComponentType> }[] = [
  { rota: 'abertas', card: 'abertas', carregar: async () => (await import('./Abertas/Abertas')).Abertas },
  { rota: 'agingWip', card: 'aging-wip', carregar: async () => (await import('./AgingWip/AgingWip')).AgingWip },
  { rota: 'bloqueios', card: 'bloqueios', carregar: async () => (await import('./Bloqueios/Bloqueios')).Bloqueios },
  { rota: 'calendario', card: 'calendario', carregar: async () => (await import('./Calendario/Calendario')).Calendario },
  { rota: 'capacidade', card: 'capacidade', carregar: async () => (await import('./Capacidade/Capacidade')).Capacidade },
  { rota: 'carga', card: 'carga', carregar: async () => (await import('./Carga/Carga')).Carga },
  { rota: 'cargaTipo', card: 'carga-tipo', carregar: async () => (await import('./CargaTipo/CargaTipo')).CargaTipo },
  { rota: 'checklistReview', card: 'checklist-review', carregar: async () => (await import('./ChecklistReview/ChecklistReview')).ChecklistReview },
  { rota: 'cobertura', card: 'cobertura', carregar: async () => (await import('./Cobertura/Cobertura')).Cobertura },
  { rota: 'competencias', card: 'competencias', carregar: async () => (await import('./Competencias/Competencias')).Competencias },
  { rota: 'cruzamentos', card: 'cruzamentos', carregar: async () => (await import('./Cruzamentos/Cruzamentos')).Cruzamentos },
  { rota: 'epicos', card: 'epicos', carregar: async () => (await import('./Epicos/Epicos')).Epicos },
  { rota: 'esforco', card: 'esforco', carregar: async () => (await import('./Esforco/Esforco')).Esforco },
  { rota: 'fechadas', card: 'fechadas', carregar: async () => (await import('./Fechadas/Fechadas')).Fechadas },
  { rota: 'fluxoDaSemana', card: 'fluxo-da-semana', carregar: async () => (await import('./FluxoDaSemana/FluxoDaSemana')).FluxoDaSemana },
  { rota: 'gargalos', card: 'gargalos', carregar: async () => (await import('./Gargalos/Gargalos')).Gargalos },
  { rota: 'metas', card: 'metas', carregar: async () => (await import('./Metas/Metas')).Metas },
  { rota: 'negativas', card: 'negativas', carregar: async () => (await import('./Negativas/Negativas')).Negativas },
  { rota: 'negligenciadas', card: 'negligenciadas', carregar: async () => (await import('./Negligenciadas/Negligenciadas')).Negligenciadas },
  { rota: 'plano', card: 'plano', carregar: async () => (await import('./Plano/Plano')).Plano },
  { rota: 'posse', card: 'posse', carregar: async () => (await import('./Posse/Posse')).Posse },
  { rota: 'previsao', card: 'previsao', carregar: async () => (await import('./Previsao/Previsao')).Previsao },
  { rota: 'qualidade', card: 'qualidade', carregar: async () => (await import('./Qualidade/Qualidade')).Qualidade },
  { rota: 'revisoes', card: 'revisoes', carregar: async () => (await import('./Revisoes/Revisoes')).Revisoes },
  { rota: 'riscos', card: 'riscos', carregar: async () => (await import('./Riscos/Riscos')).Riscos },
  { rota: 'simulador', card: 'simulador', carregar: async () => (await import('./Simulador/Simulador')).Simulador },
  { rota: 'tendencia', card: 'tendencia', carregar: async () => (await import('./Tendencia/Tendencia')).Tendencia },
  { rota: 'vencimentos', card: 'vencimentos', carregar: async () => (await import('./Vencimentos/Vencimentos')).Vencimentos },
]

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

describe.each(TELAS)('estados da tela $rota', ({ rota, card, carregar }) => {
  async function abrir(status: number, corpo?: string) {
    const Tela = await carregar()
    vi.stubGlobal(
      'fetch',
      vi.fn(async (entrada: RequestInfo | URL) => {
        const caminho = String(entrada).split('?')[0]
        if (caminho === `/api/cards/${card}`) return new Response(corpo ?? null, { status })
        return new Response('', { status: 404 })
      }),
    )
    renderizarTela(<Tela />, `/${rota}`)
  }

  it('403MostraSemPermissao', async () => {
    await abrir(403)
    expect(await screen.findByRole('alert')).toHaveTextContent(/não permite ver estes dados/i)
  })

  it('401MostraSessao', async () => {
    await abrir(401)
    expect(await screen.findByText('Entre de novo para ver os dados.')).toBeInTheDocument()
  })

  it('500MostraErroSemTextoTecnico', async () => {
    await abrir(500, 'java.lang.NullPointerException: boom')
    const alerta = await screen.findByRole('alert')
    expect(alerta).toHaveTextContent('Não foi possível carregar os dados.')
    expect(screen.getByRole('button', { name: 'Tentar de novo' })).toBeInTheDocument()
    expect(document.body.textContent).not.toMatch(/NullPointerException|HTTP \d{3}/)
  })
})
