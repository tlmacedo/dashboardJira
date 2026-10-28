// Contrato do card Metas e prazos (espelha `MetasDto` do backend). Cartões MT-01 a MT-14.

import type { Aviso } from '../../shared/useCard'
import type { Contagem } from '../Abertas/api'

export type Granularidade = 'dia' | 'semana' | 'mes'

export type PeriodoMeta = {
  chave: string
  universo: number
  noPrazo: number
  noPrazoAjustado: number
  taxaNoPrazo: number | null
  antecipadas: number
  noDia: number
  atrasadas: number
}

/** `ok` (✓ na meta ou acima), `atencao` (● até a faixa abaixo da meta) ou `problema` (▲). */
export type EstadoDaLinha = 'ok' | 'atencao' | 'problema'

export type LinhaMeta = { chave: string; universo: number; noPrazo: number; noPrazoAjustado: number; estado: EstadoDaLinha | string }

export type MetasDto = {
  versaoRegras: string
  universo: number
  noPrazo: number
  noPrazoAjustado: number
  noPrazoOriginal: number
  antecipadas: number
  emD1D2: number
  noDia: number
  atrasadas: number
  semVencimento: number
  taxaNoPrazo: number | null
  taxaAjustada: number | null
  taxaOriginal: number | null
  taxaAntecipadas: number | null
  alertaDataOriginal: boolean
  metaNoPrazo: number
  metaAntecipadas: number
  granularidade: Granularidade | string
  porPeriodo: PeriodoMeta[]
  distribuicao: Contagem[]
  porTipo: LinhaMeta[]
  porPrioridade: LinhaMeta[]
  porSquad: LinhaMeta[]
  porPessoa: LinhaMeta[]
  avisos: Aviso[]
}

export const GRANULARIDADES: Granularidade[] = ['dia', 'semana', 'mes']
