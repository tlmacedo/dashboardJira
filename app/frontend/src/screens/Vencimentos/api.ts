// Contrato do card Vencimentos da semana (espelha `VencimentosDto` do backend). Cartões VE-01 e VE-02.

import type { Aviso } from '../../shared/useCard'

export type LinhaVencimento = {
  key: string
  resumo: string
  pessoa: string | null
  vence: string
  /** Término previsto pela capacidade do responsável; nulo = não cabe no horizonte ou sem responsável. */
  termino: string | null
  /** Dias úteis entre o término previsto e o vencimento; negativa = atrasa. */
  folgaDiasUteis: number | null
  /** `ok`, `atencao` (folga zero) ou `problema` (folga negativa ou sem término). */
  estado: 'ok' | 'atencao' | 'problema' | string
}

export type VencimentosDto = {
  dias: number
  vencem: number
  linhas: LinhaVencimento[]
  riscoDeAtrasar: number
  avisos: Aviso[]
}
