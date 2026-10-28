// Contrato do card Revisões pendentes por revisor (espelha `RevisoesDto` do backend). Cartões RV-01 e RV-02.

import type { Aviso } from '../../shared/useCard'

export type Revisor = {
  revisor: string
  pendentes: number
  vencidas: number
  /** Idade em dias úteis desde a criação maior que o prazo de revisão do planejamento. */
  foraDoSla: number
  /** Mediana das idades em dias úteis. */
  idadeP50: number | null
}

export type RevisoesDto = {
  /** Revisores da visão, em ordem alfabética (sem ranking). */
  linhas: Revisor[]
  /** Reviews sem revisor: só coordenação e gerente (nulo para os demais). */
  semRevisor: number | null
  avisos: Aviso[]
}
