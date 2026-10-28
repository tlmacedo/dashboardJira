// Contrato do card Tendência de ocupação (espelha `TendenciaDto` do backend). Cartão TD-01, só liderança.
// Reconstruída do changelog com as horas de HOJE (a resposta traz o aviso `HORAS_ATUAIS_NO_PASSADO`).

import type { OcupacaoDto } from '../../shared/Ocupacao'
import type { Aviso } from '../../shared/useCard'

export type LinhaTendencia = {
  pessoa: string
  ocupacoes: OcupacaoDto[]
  /** `RECORRENTE` (3 ou mais das últimas 4 semanas acima do limite alto), `PONTUAL` (1 ou 2) ou `NORMAL`. */
  padrao: 'RECORRENTE' | 'PONTUAL' | 'NORMAL' | string
}

export type TendenciaDto = { semanas: string[]; linhas: LinhaTendencia[]; avisos: Aviso[] }
