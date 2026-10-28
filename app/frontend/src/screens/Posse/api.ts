// Contrato do card Posse da task (espelha `PosseDto` do backend). Cartões PS-01 e PS-02.
// As tasks por trás de cada número vêm do drill genérico (`shared/tasksApi.ts`).

import type { Aviso } from '../../shared/useCard'

/** A parte de uma pessoa na task; `pessoa` nulo = "outra pessoa ou sem responsável" (nunca o nome de quem está fora da visão). */
export type FracaoPosse = { pessoa: string | null; dias: number; fracao: number }

export type PosseTask = { key: string; resumo: string; transferencias: number; posse: FracaoPosse[] }

export type PossePessoa = { pessoa: string; tasks: number; dias: number; recebidas: number; repassadas: number }

export type PosseDto = {
  tasks: PosseTask[]
  /** Pessoas da visão, em ordem alfabética (sem ranking). */
  pessoas: PossePessoa[]
  /** `util` ou `corrido`. */
  unit: string
  avisos: Aviso[]
}
