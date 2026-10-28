// Contrato do card Negativas (espelha `NegativasDto` do backend). Cartões NE-01 a NE-08 e NE-10.
// As tasks por trás de cada número vêm do drill genérico (`shared/tasksApi.ts`).

import type { Aviso } from '../../shared/useCard'

export type NegativaPessoa = { pessoa: string; reabertas: number; devolvidas: number; atrasadas: number }

export type NegativasDto = {
  reabertas: number
  devolvidas: number
  canceladas: number
  concluidasComAtraso: number
  atrasadasSemJustificativa: number
  abertasVencidas: number
  estouroDeEstimativa: number
  muitasTrocas: number
  /** Base das taxas: concluídas positivas do período. */
  concluidas: number
  taxaReabertura: number | null
  taxaDevolucao: number | null
  taxaAtraso: number | null
  taxaAtrasoSemJustificativa: number | null
  /** Acima desta fração (0 a 1) a taxa leva ▲. */
  limiteDaTaxa: number
  porResponsavel: NegativaPessoa[]
  unit: 'util' | 'corrido' | string
  avisos: Aviso[]
}
