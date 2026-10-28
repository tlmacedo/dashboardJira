// Contrato do card Plano de 4 a 8 semanas (espelha `PlanoDto` do backend). Cartão PL-01, só liderança.

import type { OcupacaoDto } from '../../shared/Ocupacao'
import type { Aviso } from '../../shared/useCard'

export type CelulaPlano = {
  semana: string
  planejadaH: number
  capacidadeH: number
  ocupacao: OcupacaoDto
  itens: number
  /** A parte de `planejadaH` que vem de tasks sem vencimento. */
  semDataH: number
}
export type LinhaPlano = { pessoa: string; celulas: CelulaPlano[] }
export type PlanoDto = { semanas: string[]; linhas: LinhaPlano[]; avisos: Aviso[] }
