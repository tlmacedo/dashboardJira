// Contrato do card Cobertura por ausência (espelha `CoberturaDto` do backend). Cartão CB-01. A ausência não tem motivo.

import type { Aviso } from '../../shared/useCard'

/** Pessoa do mesmo squad com folga; os candidatos vêm em ordem alfabética (a folga NÃO define a ordem). */
export type Candidato = { pessoa: string; folgaH: number; temTodasAsCompetencias: boolean }

export type CoberturaAusencia = {
  pessoa: string
  de: string
  ate: string
  /** Chaves das tasks afetadas. */
  issues: string[]
  horasAReplanejar: number
  competencias: string[]
  podemCobrir: Candidato[]
}

export type CoberturaDto = {
  ausencias: CoberturaAusencia[]
  avisos: Aviso[]
  /** Horizonte em dias (de 1 a 90). */
  dias: number
}
