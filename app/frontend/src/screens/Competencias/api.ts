// Contrato do card Matriz de competências (espelha `CompetenciasDto` do backend). Cartões CM-01 a CM-03.

import type { Aviso } from '../../shared/useCard'

export type CompetenciasDto = {
  /** Linhas da matriz, em ordem alfabética (as da visão e as pedidas por tasks abertas). */
  competencias: string[]
  /** Colunas da matriz: pessoas da visão, em ordem alfabética. */
  pessoas: string[]
  /** `celulas[i][j]` = `pessoas[j]` tem `competencias[i]`. */
  celulas: boolean[][]
  /** Competências de exatamente uma pessoa da visão (vazio com menos de 2 pessoas na visão). */
  pessoaUnica: string[]
  /** Pedidas por task aberta e que ninguém da visão tem. */
  semNinguem: string[]
  demandaH: Record<string, number>
  avisos: Aviso[]
  tasksPorCompetencia: Record<string, number>
  tasksSemNinguem: number
}
