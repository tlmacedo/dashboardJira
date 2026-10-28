// Contrato do card Carga por pessoa (espelha `CargaDto` do backend). Cartões CP-01 a CP-03.
// As tasks por trás de cada número vêm do drill genérico (`shared/tasksApi.ts`).

import type { OcupacaoDto } from '../../shared/Ocupacao'
import type { Aviso } from '../../shared/useCard'

export type LinhaCarga = {
  pessoa: string
  abertas: number
  wip: number
  filaH: number
  capacidade4SemH: number
  ocupacao: OcupacaoDto
  ausenciaDias: number
  horasRegistradas30d: number | null
  acimaDoP85: number
  vencidas: number
  riscoAlto: number
  atrasoAcumuladoH: number
  horasAssumidas: boolean
}

export type CargaDto = {
  versaoRegras: string
  /** Índice do conjunto visível; nulo com menos de 2 pessoas com capacidade (e sempre para o usuário comum). */
  jain: number | null
  acimaDaCapacidade: number
  comFolga: number
  tasksAcimaDaCapacidade: number
  tasksComFolga: number
  linhas: LinhaCarga[]
  avisos: Aviso[]
}
