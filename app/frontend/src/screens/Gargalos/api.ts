// Contrato do card Gargalos e fluxo (espelha `GargalosDto` do backend). Cartões GA-01 a GA-04, só liderança.

import type { Aviso } from '../../shared/useCard'

export type FaixaFluxo = { status: string; contagens: number[] }

export type Etapa = {
  status: string
  categoria: string
  wip: number
  limite: number | null
  crescimento15d: number
  gargalo: boolean
  /** `WIP_ACIMA_DO_LIMITE` ou `FAIXA_CRESCEU`; nulo sem gargalo. */
  motivo: 'WIP_ACIMA_DO_LIMITE' | 'FAIXA_CRESCEU' | string | null
}

export type GargalosDto = {
  /** Os 60 últimos dias de Manaus (o último é hoje). */
  dias: string[]
  faixas: FaixaFluxo[]
  etapas: Etapa[]
  acimaDaCapacidade: number
  comFolga: number
  tasksAcimaDaCapacidade: number
  tasksComFolga: number
  avisos: Aviso[]
}
