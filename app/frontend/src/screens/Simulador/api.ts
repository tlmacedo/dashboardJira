// Contrato do Simulador de ausência (espelha `SimuladorDto` do backend). Cartão SI-01. Só leitura: a tela só usa GET.

import type { OcupacaoDto } from '../../shared/Ocupacao'
import type { Aviso } from '../../shared/useCard'

export type ComparacaoSemana = { semana: string; antes: OcupacaoDto; depois: OcupacaoDto }

export type SimuladorDto = {
  /** Vazio quando o formulário ainda não foi preenchido (o aviso `SIMULACAO_SEM_PARAMETROS` explica). */
  pessoa: string
  de: string
  ate: string
  fracao: number
  /** Chaves das tasks que não atrasavam e passam a atrasar. */
  atrasosCausados: string[]
  horasAReplanejar: number
  semanas: ComparacaoSemana[]
  avisos: Aviso[]
}
