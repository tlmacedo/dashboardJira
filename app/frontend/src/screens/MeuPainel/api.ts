// Contrato do Meu painel (espelha `MeuPainelDto` do backend). Sempre o do próprio usuário; só o Squad Leader pode pedir `pessoa=`
// de alguém do squad dele. A gaveta dos números vem dos cartões MP-01 (abertas e vencem em 7 dias), MP-03 (em risco) e MP-05 (fila).

import { getJson } from '../../api/client'
import type { OcupacaoDto } from '../../shared/Ocupacao'
import type { Aviso } from '../../shared/useCard'

export type ItemDaFila = {
  key: string
  resumo: string
  vence: string | null
  termino: string | null
  atrasa: boolean
  /** `baixo`, `medio` ou `alto`. */
  nivelRisco: 'baixo' | 'medio' | 'alto' | string
}

export type EsforcoDoMes = { pesoTipo: number; testes: number; horas: number; indice: number; versaoRegras: string }

export type AusenciaFutura = { pessoa: string; de: string; ate: string; fracao: number }

export type MeuPainelDto = {
  pessoa: string
  minhasAbertas: number | null
  vencemEm7d: number | null
  emRisco: number | null
  /** "No prazo" e "no prazo ajustado" (90 dias, as do card Metas) lado a lado; `universoDePrazo` é o total com vencimento. */
  noPrazo: number | null
  noPrazoAjustado: number | null
  universoDePrazo: number | null
  ocupacao4Sem: OcupacaoDto | null
  ausenciasFuturas: AusenciaFutura[]
  fila: ItemDaFila[]
  esforcoDoMes: EsforcoDoMes | null
  avisos: Aviso[]
}

export const fetchMeuPainel = (pessoa: string | null) =>
  getJson<MeuPainelDto>(pessoa ? `/api/meu-painel?pessoa=${encodeURIComponent(pessoa)}` : '/api/meu-painel')
