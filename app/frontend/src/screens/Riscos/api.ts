// Contrato do card Riscos (espelha `RiscosDto` do backend). Cartões RI-01 a RI-03.
// As tasks por trás de cada número vêm do drill genérico (`shared/tasksApi.ts`).

import type { Aviso } from '../../shared/useCard'

export type FatorPontos = { fator: string; pontos: number }

export type LinhaRisco = {
  key: string
  resumo: string
  tipo: string
  status: string
  responsavel: string | null
  pontos: number
  /** `alto` ou `medio` (o nível baixo só existe no drill). */
  nivel: 'alto' | 'medio' | string
  fatorPrincipal: string
  fatores: FatorPontos[]
}

export type SinaisPessoa = { pessoa: string; issuesEmRisco: number; abertas: number }

export type RiscosDto = {
  versaoRegras: string
  alto: number
  medio: number
  baixo: number
  emRisco: number
  limiteAlto: number
  limiteMedio: number
  linhas: LinhaRisco[]
  sinaisPorPessoa: SinaisPessoa[]
  avisos: Aviso[]
}
