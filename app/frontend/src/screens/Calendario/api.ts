// Contrato do card Calendário do time (espelha `CalendarioDto` do backend). Cartão CL-01.
// Sem motivo de ausência. As tasks que vencem em cada dia vêm do drill genérico (segmento `dia:<aaaa-mm-dd>`).

import type { Aviso } from '../../shared/useCard'

export type DiaCalendario = {
  dia: string
  /** Nome do feriado (vazio se o arquivo não deu nome); nulo se não é feriado. */
  feriado: string | null
  util: boolean
  /** Capacidade líquida ÷ bruta das pessoas da visão; nulo em dia não útil. */
  capacidadeSquad: number | null
  vencimentos: number
  critico: boolean
}

export type CelulaCalendario = { dia: string; ausencia: number; vencimentos: number }
export type LinhaCalendario = { pessoa: string; celulas: CelulaCalendario[] }

export type CalendarioDto = {
  inicio: string
  dias: DiaCalendario[]
  linhas: LinhaCalendario[]
  avisos: Aviso[]
}
