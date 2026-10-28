// Contrato da Distribuição, do Rebalanceamento e do registro de decisões (espelha `DistribuicaoDto`, `RebalanceamentoDto` e
// `DecisoesDto` do backend). MODO SOMBRA: o painel só sugere; a decisão é gravada no próprio painel e nada vai ao Jira.
// As tasks por trás de cada número vêm do drill genérico (`shared/tasksApi.ts`).

import { enviarJson, getJson } from '../../api/client'
import type { Aviso } from '../../shared/useCard'

/** Os termos do score de um candidato. Sem posição: a lista vem em ordem alfabética de pessoa. */
export type Fatores = {
  pessoa: string
  carga: number
  wip: number
  wipCheio: number
  competencias: number
  bonus: number
  score: number
}

export type Sugestao = {
  issueKey: string
  resumo: string
  horas: number
  horasAssumidas: boolean
  vence: string | null
  comGerente: boolean
  sugerido: string | null
  fatores: Fatores[]
  /** `APROVADA`, `TROCADA` ou `REJEITADA` quando já foi decidida hoje; nulo = sem decisão. */
  decisao: string | null
}

export type DistribuicaoDto = {
  modoSombra: boolean
  versaoRegras: string
  aguardando: number
  sugestoesPendentes: number
  jainAntes: number | null
  jainDepois: number | null
  sugestoes: Sugestao[]
  candidatos: string[]
  avisos: Aviso[]
}

export type Movimento = {
  issueKey: string
  resumo: string
  de: string
  para: string
  horas: number
  ocupacaoOrigemAntes: number
  ocupacaoOrigemDepois: number
  ocupacaoDestinoDepois: number
  decisao: string | null
}

export type RebalanceamentoDto = {
  modoSombra: boolean
  versaoRegras: string
  gatilho: number
  histerese: number
  movimentos: Movimento[]
  movimentosPendentes: number
  candidatos: string[]
  avisos: Aviso[]
}

export type DecisaoListada = {
  tipo: string
  issueKey: string
  sugerido: string | null
  escolhido: string | null
  decisao: string
  motivo: string | null
  decididoPor: string
  decididoEm: string
  versaoRegras: string
}

export type DecisoesDto = { de: string; ate: string; decisoes: DecisaoListada[]; truncado: boolean }

export type TipoDeSugestao = 'DISTRIBUICAO' | 'REBALANCEAMENTO'

export type CorpoDeDecisao = {
  tipo: TipoDeSugestao
  issueKey: string
  sugerido: string | null
  escolhido?: string
  decisao: 'APROVADA' | 'TROCADA' | 'REJEITADA'
  motivo?: string
}

export const fetchDistribuicao = () => getJson<DistribuicaoDto>('/api/distribuicao')
export const fetchRebalanceamento = () => getJson<RebalanceamentoDto>('/api/rebalanceamento')
export const fetchDecisoes = (sp: URLSearchParams) => {
  const q = sp.toString()
  return getJson<DecisoesDto>(q ? `/api/governanca/decisoes?${q}` : '/api/governanca/decisoes')
}

/** `POST /api/distribuicao/decisao`: grava a decisão no painel (409 `JA_DECIDIDA` se alguém decidiu antes). */
export const postarDecisao = (corpo: CorpoDeDecisao) => enviarJson<unknown>('POST', '/api/distribuicao/decisao', corpo)
