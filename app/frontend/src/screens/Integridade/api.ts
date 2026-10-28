// Contrato da Integridade (espelha `IntegridadeDto` e `CatalogoIntegridadeDto` do backend). SÓ o gerente vê e toda consulta é
// auditada. Indício, não prova. As tasks por trás de cada número vêm do drill genérico (`shared/tasksApi.ts`).

import { getJson } from '../../api/client'
import type { Aviso } from '../../shared/useCard'

/** Os 12 sinais, na ordem do catálogo (a mesma do backend). */
export const SINAIS = [
  'RESGATE_POR_PRORROGACAO',
  'TROCA_ANTES_DE_FECHAR',
  'CRIADA_E_FECHADA_RAPIDO',
  'REABRE_E_FECHA_RAPIDO',
  'FECHAMENTO_EM_LOTE',
  'VENCIMENTO_PRORROGADO',
  'VENCIMENTO_TARDIO',
  'PULOU_O_FLUXO',
  'ESTIMATIVA_AUMENTADA',
  'LABEL_CRITICIDADE_REMOVIDA',
  'FECHADA_POR_OUTRO',
  'FECHADA_NO_LIMITE',
] as const

export type ContagemDeSinal = { sinal: string; gravidade: number; ativo: boolean; n: number }

/** Pessoa com indício ALTO ou MEDIO. Sem posição nem nota: a lista vem sempre em ordem alfabética. */
export type NivelPessoa = {
  pessoa: string
  nivel: 'ALTO' | 'MEDIO' | string
  pontos: number
  concluidas: number
  taxa: number
  multiplo: number
  graves: number
  evidencias: number
}

export type ResumoIntegridade = {
  evidencias: number
  issuesAfetadas: number
  medianaTaxa: number
  porSinal: ContagemDeSinal[]
  pessoas: NivelPessoa[]
  pessoasAlto: number
  pessoasMedio: number
  evidenciasNivelAlto: number
  evidenciasNivelMedio: number
  /** Pontos percentuais (25 = 25 pp); nulo sem as duas taxas. */
  impactoMetasPp: number | null
  taxaNoPrazo: number | null
  taxaOriginal: number | null
  pontosAlto: number
  multiploAlto: number
  gravesAlto: number
  pontosMedio: number
  multiploMedio: number
  concluidasMinimas: number
}

export type Evidencia = {
  sinal: string
  gravidade: number
  key: string
  pessoa: string | null
  quando: string
  dia: string
  detalhe: Record<string, string>
}

export type IntegridadeDto = {
  /** Fixos: `INDICIO_NAO_PROVA` e `VALIDAR_BASE_LEGAL_COM_DPO`. */
  aviso: string
  avisoDpo: string
  versaoRegras: string
  de: string
  ate: string
  resumo: ResumoIntegridade
  evidencias: Evidencia[]
  avisos: Aviso[]
}

export type SinalDoCatalogo = { sinal: string; gravidade: number; ativo: boolean; params: Record<string, string> }

export type CatalogoIntegridade = {
  aviso: string
  avisoDpo: string
  versaoRegras: string
  sinais: SinalDoCatalogo[]
  niveis: {
    pontosAlto: number
    multiploAlto: number
    gravesAlto: number
    pontosMedio: number
    multiploMedio: number
    concluidasMinimas: number
    pisoMediana: number
  }
}

export type FiltrosIntegridade = { de: string; ate: string; squad: string; pessoa: string; sinal: string }

export function filtrosDaUrl(sp: URLSearchParams): FiltrosIntegridade {
  return { de: sp.get('de') ?? '', ate: sp.get('ate') ?? '', squad: sp.get('squad') ?? '', pessoa: sp.get('pessoa') ?? '', sinal: sp.get('sinal') ?? '' }
}

export function paramsDe(f: FiltrosIntegridade): URLSearchParams {
  const p = new URLSearchParams()
  for (const k of ['de', 'ate', 'squad', 'pessoa', 'sinal'] as const) if (f[k].trim()) p.set(k, f[k].trim())
  return p
}

/** `GET /api/integridade?de=&ate=&squad=&pessoa=&sinal=`. */
export function fetchIntegridade(params: URLSearchParams): Promise<IntegridadeDto> {
  const q = params.toString()
  return getJson<IntegridadeDto>(q ? `/api/integridade?${q}` : '/api/integridade')
}

/** `GET /api/integridade/catalogo` (IN-08): o que cada sinal procura, sem dado de pessoas. */
export function fetchCatalogoIntegridade(): Promise<CatalogoIntegridade> {
  return getJson<CatalogoIntegridade>('/api/integridade/catalogo')
}
