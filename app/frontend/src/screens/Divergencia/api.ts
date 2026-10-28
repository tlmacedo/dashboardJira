// Contrato do card Task × Review (espelha `TaskReviewDto` e `TaskReviewLinhaDto` do backend). Só fala com o painel.
// A Divergência só mostra e linka: não há rota nem botão para corrigir (escrita no Jira pelo painel é o Plano 16, futuro).

import { getJson } from '../../api/client'
import type { Aviso } from '../Auditoria/api'

export type LinhaDeDivergencia = {
  task: string
  review: string
  vencimentoTask: string | null
  vencimentoReview: string | null
  responsavelTask: string | null
  responsavelReview: string | null
  divergente: boolean
  /** Link direto para a issue no Jira, montado no backend (só com a flag ligada); nulo sem link. */
  urlTask: string | null
  urlReview: string | null
}

export type TaskReviewDto = {
  pares: number
  divergentes: number
  /** Pontos percentuais (25 = 25%); nulo sem pares. */
  percentual: number | null
  avisos: Aviso[]
  divergencias: LinhaDeDivergencia[]
}

export type FiltrosDivergencia = { squad: string; pessoa: string }

export function filtrosDaUrl(sp: URLSearchParams): FiltrosDivergencia {
  return { squad: sp.get('squad') ?? '', pessoa: sp.get('pessoa') ?? '' }
}

export function paramsDe(f: FiltrosDivergencia): URLSearchParams {
  const p = new URLSearchParams()
  if (f.squad.trim()) p.set('squad', f.squad.trim())
  if (f.pessoa.trim()) p.set('pessoa', f.pessoa.trim())
  return p
}

/** `GET /api/cards/task-review?squad=&pessoa=`. */
export function fetchTaskReview(params: URLSearchParams): Promise<TaskReviewDto> {
  const q = params.toString()
  return getJson<TaskReviewDto>(q ? `/api/cards/task-review?${q}` : '/api/cards/task-review')
}
