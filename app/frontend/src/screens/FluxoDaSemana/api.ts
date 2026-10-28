// Contrato do card Fluxo da semana (espelha `FluxoDaSemanaDto` do backend). Cartões FX-01 e FX-02.
// As tasks por trás de cada número vêm do drill genérico (`shared/tasksApi.ts`).

import type { Aviso } from '../../shared/useCard'

/** Uma semana (segunda a domingo de Manaus). `emCurso`: a semana de hoje, ainda incompleta. */
export type SemanaDeFluxo = {
  semana: string
  entrou: number
  comecou: number
  saiu: number
  canceladas: number
  emCurso: boolean
}

export type FluxoDaSemanaDto = {
  semanas: SemanaDeFluxo[]
  entrou: number
  comecou: number
  saiu: number
  canceladas: number
  /** entrou - saiu - canceladas (pode ser negativo). */
  saldoDoBacklog: number
  /** Semanas FECHADAS seguidas, até a última, em que a entrada superou a saída. */
  semanasCrescendo: number
  backlogCrescendo: boolean
  semanasNaJanela: number
  tasksNaJanela: number
  avisos: Aviso[]
}
