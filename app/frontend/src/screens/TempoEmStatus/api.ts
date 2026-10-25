// Contrato do card "Tempo em status" (espelha TempoEmStatusDto/DrillRow do backend).
// Só fala com o backend do próprio painel; nunca com o Jira.

export type Categoria = 'TODO' | 'ACTIVE' | 'WAIT' | 'BLOCKED' | 'DONE'

export type StatusStat = { status: string; category: Categoria; median: number; p85: number; n: number }
export type PersonStatus = { person: string; status: string; median: number; n: number }
export type StuckIssue = {
  key: string
  summary: string
  assignee: string | null
  status: string
  daysInStatus: number
  median: number
}

export type TempoEmStatusDto = {
  analisadas: number
  eficienciaFluxo: number | null
  paradas: number
  porStatus: StatusStat[]
  pessoaStatus: PersonStatus[]
  abertasParadas: StuckIssue[]
  unit: 'util' | 'corrido' | string
  avisos: string[]
}

export type DrillRow = {
  key: string
  resumo: string
  responsavel: string | null
  status: string
  detalhe: string
}

export type Unidade = 'util' | 'corrido'

/** Filtros da tela; todos vivem na URL. Vazio significa "sem filtro". */
export type Filtros = {
  squad: string
  pessoa: string
  tipo: string
  os: string
  de: string
  ate: string
  unit: Unidade
  semOutliers: boolean
}

export const FILTROS_VAZIOS: Filtros = {
  squad: '',
  pessoa: '',
  tipo: '',
  os: '',
  de: '',
  ate: '',
  unit: 'util',
  semOutliers: false,
}

/** Erro de HTTP sem corpo: o backend responde só o status (400/403/5xx). */
export class ApiError extends Error {
  readonly status: number
  constructor(status: number) {
    super(`HTTP ${status}`)
    this.status = status
  }
}

export function paramsDe(f: Filtros): URLSearchParams {
  const p = new URLSearchParams()
  for (const k of ['squad', 'pessoa', 'tipo', 'os', 'de', 'ate'] as const) {
    if (f[k].trim()) p.set(k, f[k].trim())
  }
  p.set('unit', f.unit)
  if (f.semOutliers) p.set('semOutliers', 'true')
  return p
}

async function pegar<T>(caminho: string, params: URLSearchParams): Promise<T> {
  const resposta = await fetch(`${caminho}?${params.toString()}`, { headers: { Accept: 'application/json' } })
  if (!resposta.ok) throw new ApiError(resposta.status)
  return (await resposta.json()) as T
}

export function fetchTempoEmStatus(filtros: Filtros): Promise<TempoEmStatusDto> {
  return pegar('/api/cards/tempo-em-status', paramsDe(filtros))
}

export function fetchDrill(filtros: Filtros, kind: string): Promise<DrillRow[]> {
  const p = paramsDe(filtros)
  p.set('kind', kind)
  return pegar('/api/drill/tempo-em-status', p)
}
