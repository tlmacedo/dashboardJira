// Contrato do card Controle do ciclo e tendência (espelha `ControleDoCicloDto` do backend). Cartões CT-01 e CT-02.
// As tasks por trás de cada número vêm do drill genérico (`shared/tasksApi.ts`).

import type { Aviso } from '../../shared/useCard'

/** Uma task concluída. Sem pessoa e sem chave: o clique abre as tasks do dia e do tipo. */
export type PontoDeCiclo = { dia: string; ciclo: number; mediaMovel: number | null; outlier: boolean }

export type MesDoCiclo = { mes: string; n: number; p50: number | null; p85: number | null; amostraPequena: boolean }

export type SerieDoTipo = {
  tipo: string
  n: number
  p50: number | null
  p85: number | null
  amostraPequena: boolean
  /** Tamanho da média móvel (20% dos pontos, ímpar, mínimo 5); nulo com menos de 5 pontos. */
  janela: number | null
  pontos: PontoDeCiclo[]
  meses: MesDoCiclo[]
}

export type ControleDoCicloDto = {
  series: SerieDoTipo[]
  concluidas: number
  semOutliers: boolean
  limiteOutlierDias: number
  outliersExcluidos: number
  de: string
  ate: string
  unit: 'util' | 'corrido' | string
  avisos: Aviso[]
}
