// Contrato do card Fechadas (espelha `FechadasDto` do backend). Cartões FE-01 a FE-12.

import type { Aviso } from '../../shared/useCard'
import type { Agrupamento } from '../../shared/PorCampo'
import type { Contagem, FaixaTipo } from '../Abertas/api'

export type Faixa = { p50: number; p85: number; n: number }

export type PontoCiclo = { key: string; dia: string; ciclo: number }

export type Dispersao = { p50: number; p85: number; p95: number; pontos: PontoCiclo[] }

export type Semana = { semana: string; n: number }

export type EntregaPessoa = {
  pessoa: string
  finalizou: number
  participou: number
  pontos: number | null
  cicloP50: number | null
  horasEstimadas: number | null
  horasGastas: number | null
}

export type LinhaFechada = {
  key: string
  resumo: string
  tipo: string
  responsavel: string | null
  resolvida: string
  ciclo: number | null
  lead: number | null
  vence: string | null
  /** `noPrazo`, `ajustado`, `atrasada` ou `semVencimento`. */
  prazo: 'noPrazo' | 'ajustado' | 'atrasada' | 'semVencimento' | string
  pontos: number | null
  horasEstimadas: number | null
  horasGastas: number | null
}

export type FechadasDto = {
  concluidas: number
  pontos: number | null
  comPontos: number
  ciclo: Faixa | null
  lead: Faixa | null
  noPrazo: number
  noPrazoAjustado: number
  comVencimento: number
  taxaNoPrazo: number | null
  taxaAjustada: number | null
  canceladas: number
  porSemana: Semana[]
  dispersao: Dispersao | null
  porTipo: FaixaTipo[]
  porProjeto: Contagem[]
  porPessoa: EntregaPessoa[]
  linhas: LinhaFechada[]
  semResolution: number
  unit: 'util' | 'corrido' | string
  avisos: Aviso[]
  /** Por campo do dicionário (cartão AB-10 ou FE-13); ausente em respostas antigas. */
  porCampo?: Agrupamento[]
}
