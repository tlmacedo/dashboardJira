// Contrato do card Bloqueios entre tasks (espelha `BloqueiosDto` do backend). Cartões BQ-01 a BQ-05.
// As tasks por trás de cada número vêm do drill genérico (`shared/tasksApi.ts`).

import type { Aviso } from '../../shared/useCard'

/** Onde está a bloqueadora: ao alcance (`VISIVEL`), de outra pessoa (`OUTRO_SQUAD`, sem chave) ou fora da base. */
export type EstadoDaBloqueadora = 'VISIVEL' | 'OUTRO_SQUAD' | 'FORA_DA_BASE'

export type Bloqueadora = {
  estado: EstadoDaBloqueadora | string
  /** Nula quando `estado` é `OUTRO_SQUAD`: a bloqueadora de pessoa fora da visão não mostra chave nem nome. */
  key: string | null
  vence: string | null
  conflito: boolean
  semVencimento: boolean
}

export type LinhaDeBloqueio = {
  bloqueada: string
  resumo: string
  tipo: string
  status: string
  vence: string | null
  bloqueadora: Bloqueadora
}

export type BloqueiosDto = {
  bloqueadas: number
  bloqueadoras: number
  bloqueadorasForaDaVisao: number
  prazoEmConflito: number
  verificarNoJira: number
  semCausa: number
  linhas: LinhaDeBloqueio[]
  tipoDeLink: string
  avisos: Aviso[]
}
