// Contrato do card Envelhecimento por etapa (espelha `AgingWipDto` do backend). Cartões AW-01 e AW-02.
// As tasks por trás de cada número vêm do drill genérico (`shared/tasksApi.ts`).

import type { Aviso } from '../../shared/useCard'

export type FaixaDaEtapa = { p50: number; p85: number; n: number }

/** Uma task em andamento na etapa. Sem pessoa: ela só aparece na gaveta, pela visão do papel. */
export type PontoDeIdade = { key: string; idade: number; acima: boolean }

export type Etapa = {
  status: string
  /** `ACTIVE`, `WAIT` ou `BLOCKED`. */
  categoria: string
  total: number
  acima: number
  historicoN: number
  /** Nula quando a etapa não tem a amostra mínima de concluídas. */
  faixa: FaixaDaEtapa | null
  pontos: PontoDeIdade[]
}

export type AgingWipDto = {
  etapas: Etapa[]
  emAndamento: number
  acimaDoNormal: number
  unit: 'util' | 'corrido' | string
  avisos: Aviso[]
}
