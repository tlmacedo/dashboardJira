// Contrato do card Checklist × Review (espelha `ChecklistReviewDto` do backend). Cartões CR-01 a CR-14.
// As tasks por trás de cada número vêm do drill genérico (`shared/tasksApi.ts`). Nada aqui compara pessoas.

import type { Aviso } from '../../shared/useCard'

/** Os seis alertas de um par, na ordem em que a tela os lista. */
export const ALERTAS = [
  'REVIEW_VENCE_DEPOIS',
  'PAI_FECHADO_ANTES',
  'MESMA_PESSOA',
  'REVIEW_CURTA_COM_PAI_REABERTO',
  'REVIEW_VENCIDA',
  'REVIEW_ATRASADA',
] as const

export type Alerta = (typeof ALERTAS)[number]

export type FaixaDuracao = { faixa: string; n: number; taxaReabertura: number | null; nBaixo: boolean }

export type LiftDeLabel = { label: string; n: number; taxaReabertura: number; lift: number | null; nBaixo: boolean }

/** `codigo` é `pai:<label>` (só no pai) ou `review:<label>` (só na review). */
export type DiferencaDeLabel = { codigo: string; pares: number }

/** Uma frase de aprendizado: o texto sai do `codigo` (`cr.aprendizado.<codigo>`) e os `params` preenchem os marcadores. */
export type Aprendizado = { codigo: string; params: Record<string, string> }

export type ResumoCR = {
  pares: number
  semReview: number
  concluidas: number
  comAlerta: number
  porAlerta: Record<string, number>
  cicloReviewP50: number | null
  razaoReviewPaiP50: number | null
  criadaAposPaiHorasP50: number | null
  /** Frações de 0 a 1 sobre o número de pares; nulas sem pares. */
  venceDepoisPct: number | null
  mesmaPessoaPct: number | null
  comAlertaPct: number | null
  duracaoXReabertura: FaixaDuracao[]
  labels: LiftDeLabel[]
  diferencasDeLabels: DiferencaDeLabel[]
  atrasosEmCadeia: number
  aprendizados: Aprendizado[]
  amostraMinima: number
  limiteVenceDepois: number
  limiteMesmaPessoa: number
  limiteParesComAlerta: number
}

export type ParCR = {
  pai: string
  review: string
  resumoPai: string
  resumoReview: string
  /** Só quando a pessoa está na visão de quem consulta. */
  responsavelPai: string | null
  responsavelReview: string | null
  statusPai: string
  statusReview: string
  vencePai: string | null
  venceReview: string | null
  criacaoPai: string
  criacaoReview: string
  resolvidoPai: string | null
  resolvidoReview: string | null
  horasAtivasReview: number | null
  paiReaberto: boolean
  alertas: string[]
}

export type SemReview = { key: string; resumo: string; responsavel: string | null; status: string }

export type ChecklistReviewDto = {
  versaoRegras: string
  resumo: ResumoCR
  pares: ParCR[]
  semReview: SemReview[]
  avisos: Aviso[]
  /** `util` ou `corrido`. */
  unit: string
}
