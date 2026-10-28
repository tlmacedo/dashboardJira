// Contrato do card Previsão (espelha `PrevisaoDto` do backend). Cartões PV-01 a PV-06.
// As tasks por trás de cada número vêm do drill genérico (`shared/tasksApi.ts`).

import type { Aviso } from '../../shared/useCard'

export type SemanaPrevisao = { semana: string; n: number }

/** Datas = segunda-feira da semana alvo; tudo nulo = "mais de N semanas". Com backlog 0 as semanas são 0 e a data é nula. */
export type Termina = {
  p50: string | null
  p85: string | null
  p95: string | null
  semanasP50: number | null
  semanasP85: number | null
  semanasP95: number | null
}

export type Quantos = { semanas: number; p50: number; p85: number; p95: number }

export type PrevisaoIssue = {
  key: string
  resumo: string
  tipo: string
  status: string
  responsavel: string | null
  idade: number
  restanteP50: number | null
  restanteP85: number | null
  n: number
  /** Término previsto pela capacidade líquida do responsável (aaaa-mm-dd); nulo = não cabe no horizonte ou sem responsável. */
  pelaCapacidade?: string | null
  /** O término pela capacidade cai depois do vencimento? Nulo sem previsão ou sem vencimento. */
  atrasa?: boolean | null
}

export type PrevisaoDto = {
  versaoRegras: string
  backlog: number
  termina: Termina
  confianca: 'baixa' | 'media' | 'alta' | string
  semanasComDado: number
  semanasHistorico: number
  limiteSemanas: number
  comChegada: boolean
  entregasNaJanela: number
  vazaoSemanal: SemanaPrevisao[]
  chegadaSemanal: SemanaPrevisao[]
  quantos: Quantos[]
  porIssue: PrevisaoIssue[]
  unit: 'util' | 'corrido' | string
  avisos: Aviso[]
}
