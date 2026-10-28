// Contrato do card Capacidade e ausências (espelha `CapacidadeDto` do backend). Cartões CA-01 a CA-09, só liderança.
// A ausência não tem motivo: o painel não o guarda nem o mostra.

import type { OcupacaoDto } from '../../shared/Ocupacao'
import type { Aviso } from '../../shared/useCard'

export type CelulaMapa = { semana: string; ocupacao: OcupacaoDto; ausente: boolean }
export type LinhaMapa = { pessoa: string; semanas: CelulaMapa[] }
export type AlertaCapacidade = {
  pessoa: string
  /** `CARGA_ALTA` ou `AUSENCIA_COM_TRABALHO`. */
  tipo: 'CARGA_ALTA' | 'AUSENCIA_COM_TRABALHO' | string
  semana: string
  horasAReplanejar: number | null
}
export type AusenciaDto = { pessoa: string; de: string; ate: string; fracao: number }
export type FeriadoDto = { dia: string; nome: string | null }
export type AtrasoPrevisto = { key: string; pessoa: string; vence: string; termino: string | null; causa: 'AUSENCIA' | 'CARGA' | string }

export type CapacidadeDto = {
  versaoRegras: string
  alertas: AlertaCapacidade[]
  atrasosPrevistos: number
  atrasosPorAusencia: number
  jain: number | null
  mapa: LinhaMapa[]
  ausencias: AusenciaDto[]
  feriados: FeriadoDto[]
  listaAtrasos: AtrasoPrevisto[]
  avisos: Aviso[]
}
