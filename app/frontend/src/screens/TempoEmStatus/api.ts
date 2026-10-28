// Contrato do card "Tempo em status" (espelha TempoEmStatusDto do backend; as tasks por trás dos números vêm do drill genérico, ver shared/tasksApi.ts).
// Só fala com o backend do próprio painel; nunca com o Jira.

import { ErroDaApi, getJson } from '../../api/client'

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

/** Aviso do backend: o texto exibido vem do código (i18n); a gravidade decide se esconde os números. */
export type Aviso = {
  codigo: string
  gravidade: 'bloqueante' | 'informativo' | string
  mensagem: string
  /** Dados do aviso (`status`, `n`) para montar o texto pelo código; ausente quando o backend não manda. */
  params?: Record<string, string>
}

export type TempoEmStatusDto = {
  analisadas: number
  eficienciaFluxo: number | null
  paradas: number
  porStatus: StatusStat[]
  pessoaStatus: PersonStatus[]
  abertasParadas: StuckIssue[]
  unit: 'util' | 'corrido' | string
  avisos: Aviso[]
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
export class ApiError extends ErroDaApi {
  constructor(status: number) {
    super(status)
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

function pegar<T>(caminho: string, params: URLSearchParams): Promise<T> {
  return getJson<T>(`${caminho}?${params.toString()}`)
}

export function fetchTempoEmStatus(filtros: Filtros): Promise<TempoEmStatusDto> {
  return pegar('/api/cards/tempo-em-status', paramsDe(filtros))
}
