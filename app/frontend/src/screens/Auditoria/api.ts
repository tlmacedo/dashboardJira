// Contrato da API da Auditoria (espelha os DTOs de `auditoria/AuditoriaDto.kt` do backend). Só fala com o painel,
// nunca com o Jira. As tasks por trás de cada número vêm do drill genérico (`shared/tasksApi.ts`).

import { getJson } from '../../api/client'

/** Os 11 códigos de sinal, na ordem do catálogo do backend. A tela traduz por `auditoria.sinal.<CODIGO>`. */
export const CODIGOS_DE_SINAL = [
  'VENCIMENTO_ALTERADO_NAO_AUTORIZADO',
  'VENCIMENTO_REMOVIDO',
  'VENCIMENTO_TASK_REVIEW_DIVERGENTE',
  'RESPONSAVEL_TROCADO_NAO_AUTORIZADO',
  'PASSA_BOLA',
  'TROCA_PERTO_DO_VENCIMENTO',
  'LABEL_APOS_FECHAMENTO_NAO_AUTORIZADO',
  'LABEL_PELO_RESPONSAVEL_APOS_FECHAMENTO',
  'LABEL_TARDIA_COM_POSSE_ANTECIPADA',
  'LABEL_APOS_VENCIMENTO',
  'CANCELAMENTO_SUSPEITO',
] as const

export type Resultado = 'auditoria' | 'alerta'

export type Aviso = {
  codigo: string
  gravidade: 'bloqueante' | 'informativo' | string
  mensagem: string
  params?: Record<string, string>
}

export type ContagemSinal = { codigo: string; resultado: Resultado | string; gravidade: number; n: number }

export type Sinal = {
  codigo: string
  resultado: Resultado | string
  gravidade: number
  key: string
  resumo: string
  campo: string
  de: string | null
  para: string | null
  at: string
  /** Dia civil de Manaus (aaaa-mm-dd). */
  dia: string
  autor: string | null
  autorizado: boolean
  /** Nome de `MotivoAutorizacao` (a tela traduz por `auditoria.motivo.<NOME>`). */
  motivo: string
  responsavel: string | null
  /** Alteração em lote (I-15): tamanho do lote, nulo abaixo do mínimo. Contexto, não prova. */
  emLote?: number | null
}

export type NoPrazo = {
  concluidas: number
  noPrazo: number
  noPrazoAjustado: number
  justificadasSobAuditoria: number
  /** Fração de 0 a 1; nula sem concluídas. */
  taxa: number | null
  taxaAjustada: number | null
}

export type AuditoriaDto = {
  aviso: string
  versaoRegras: string
  total: number
  porSinal: ContagemSinal[]
  sinais: Sinal[]
  noPrazo: NoPrazo
  avisos: Aviso[]
}

export type LinhaHistorico = {
  at: string
  campo: string
  de: string | null
  para: string | null
  autor: string | null
  autorizado: boolean | null
  motivo: string | null
  /** Falso: o painel não guarda o valor deste campo (só o nome), então `de` e `para` vêm nulos. */
  valorGuardado: boolean
}

export type HistoricoDto = {
  aviso: string
  key: string
  resumo: string
  reporter: string | null
  linhas: LinhaHistorico[]
  avisos: Aviso[]
}

/** Filtros da tela; todos vivem na URL. Vazio = sem filtro (o backend aplica o período padrão de 30 dias). */
export type Filtros = { de: string; ate: string; squad: string; pessoa: string; sinal: string; resultado: string }

export const FILTROS_VAZIOS: Filtros = { de: '', ate: '', squad: '', pessoa: '', sinal: '', resultado: '' }

export const CAMPOS_DE_FILTRO = ['de', 'ate', 'squad', 'pessoa', 'sinal', 'resultado'] as const

export function filtrosDaUrl(sp: URLSearchParams): Filtros {
  const f = { ...FILTROS_VAZIOS }
  for (const k of CAMPOS_DE_FILTRO) f[k] = sp.get(k) ?? ''
  return f
}

export function paramsDe(f: Filtros): URLSearchParams {
  const p = new URLSearchParams()
  for (const k of CAMPOS_DE_FILTRO) if (f[k].trim()) p.set(k, f[k].trim())
  return p
}

/** `GET /api/auditoria/sinais?<filtros>`. */
export function fetchSinais(params: URLSearchParams): Promise<AuditoriaDto> {
  const q = params.toString()
  return getJson<AuditoriaDto>(q ? `/api/auditoria/sinais?${q}` : '/api/auditoria/sinais')
}

/** `GET /api/auditoria/historico/{key}`: o histórico campo a campo de uma issue. */
export function fetchHistorico(key: string): Promise<HistoricoDto> {
  return getJson<HistoricoDto>(`/api/auditoria/historico/${encodeURIComponent(key)}`)
}
