// Contrato da Central de atenção (espelha `CentralDto` do backend). COORDENADOR e GERENTE. Cada indicador é o número de um card de
// destino: `rota` leva à tela e `cartao`/`segmento` abrem as tasks pelo drill genérico (`shared/tasksApi.ts`). O item de
// integridade só existe para o gerente; para os outros ele não vem no DTO.

import { getJson } from '../../api/client'
import type { OcupacaoDto } from '../../shared/Ocupacao'
import type { Aviso } from '../../shared/useCard'

export type ItemCentral = {
  /** Nome estável do indicador (`VENCIDAS`, `SEM_ATUALIZACAO`...); o texto sai de `central.item.<NOME>`. */
  item: string
  /** O número em texto sem formatação ("7", "15/20" ou "—"). */
  valor: string
  n: number | null
  universo: number | null
  /** `ok`, `atencao`, `problema`, `neutro` ou `indisponivel`. */
  estado: 'ok' | 'atencao' | 'problema' | 'neutro' | 'indisponivel' | string
  /** Tela de destino (rota sem a barra: `negligenciadas`). */
  rota: string
  /** Cartão e segmento da gaveta de tasks; nulos quando o indicador não tem tasks. */
  cartao: string | null
  segmento: string | null
}

export type SemanaEntregas = { semana: string; n: number }

export type QuandoDto = {
  p50: string | null
  p85: string | null
  p95: string | null
  semanasP50: number | null
  semanasP85: number | null
  semanasP95: number | null
}

export type CartaoSquad = { squad: string; ocupacaoMedia: OcupacaoDto; jain: number | null; pessoas: number }

export type CentralDto = {
  kpis: ItemCentral[]
  itens: ItemCentral[]
  entregasPorSemana: SemanaEntregas[]
  backlogTermina: QuandoDto | null
  backlog: number | null
  confiancaDoBacklog: string | null
  squads: CartaoSquad[]
  avisos: Aviso[]
}

export const fetchCentral = () => getJson<CentralDto>('/api/central')

/** Símbolo de cada estado (a cor nunca é o único sinal). */
export const SIMBOLO_DO_ESTADO: Record<string, string> = { ok: '✓', atencao: '●', problema: '▲', neutro: '■', indisponivel: '—' }
