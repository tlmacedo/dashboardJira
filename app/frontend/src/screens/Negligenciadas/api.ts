// Contrato do card Negligenciadas (espelha `NegligenciadasDto` do backend). Cartões NG-01 a NG-06.

import type { Aviso } from '../../shared/useCard'

/** As seis listas, na ordem da tela; o nome é também o segmento do drill e a chave de `listas`. */
export const KINDS = ['semAtualizacao', 'semResponsavel', 'vencidas', 'bloqueadas', 'paradas', 'aFazer'] as const
export type Kind = (typeof KINDS)[number]

/** Cartão do catálogo que conta cada lista. */
export const CARTAO_DA_LISTA: Record<Kind, string> = {
  semAtualizacao: 'NG-01',
  semResponsavel: 'NG-02',
  vencidas: 'NG-03',
  bloqueadas: 'NG-04',
  paradas: 'NG-05',
  aFazer: 'NG-06',
}

export type UltimoMovimento = { campo: string; dia: string; dias: number }

export type LinhaNegligenciada = {
  key: string
  resumo: string
  tipo: string
  status: string
  responsavel: string | null
  vence: string | null
  labels: string[]
  dias: number
  ultimoMovimento: UltimoMovimento
  semMovimentoReal: boolean
}

export type NegligenciadasDto = {
  semAtualizacao: number
  semResponsavel: number
  vencidas: number
  bloqueadas: number
  paradasNoStatus: number
  aFazerAntigas: number
  listas: Record<string, LinhaNegligenciada[]>
  diasSemAtualizacao: number
  semMovimentoReal: boolean
  unit: 'util' | 'corrido' | string
  avisos: Aviso[]
}

/** Quantas tasks tem cada lista (o DTO nomeia dois contadores de forma diferente do segmento). */
export function totalDaLista(dto: NegligenciadasDto, kind: Kind): number {
  switch (kind) {
    case 'semAtualizacao':
      return dto.semAtualizacao
    case 'semResponsavel':
      return dto.semResponsavel
    case 'vencidas':
      return dto.vencidas
    case 'bloqueadas':
      return dto.bloqueadas
    case 'paradas':
      return dto.paradasNoStatus
    case 'aFazer':
      return dto.aFazerAntigas
  }
}
