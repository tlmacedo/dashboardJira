// Contrato da Visão do squad (espelha `SquadDto` do backend). Os números são os dos cards Carga, Vencimentos, Capacidade e
// Riscos com o filtro `squad`; a gaveta de cada um é a do cartão desse card (CP-03, VE-01/VE-02, RI-01) com o mesmo `squad`.

import { getJson } from '../../api/client'
import type { OcupacaoDto } from '../../shared/Ocupacao'
import type { Aviso } from '../../shared/useCard'

export type LinhaDeCarga = {
  pessoa: string
  abertas: number
  wip: number
  filaH: number
  capacidade4SemH: number
  ocupacao: OcupacaoDto
  ausenciaDias: number
  acimaDoP85: number
  vencidas: number
  riscoAlto: number
  horasAssumidas: boolean
}

export type LinhaDeVencimento = {
  key: string
  resumo: string
  pessoa: string | null
  vence: string
  termino: string | null
  folgaDiasUteis: number | null
  estado: 'ok' | 'atencao' | 'problema' | string
}

export type VencimentosDaSemana = { dias: number; vencem: number; linhas: LinhaDeVencimento[]; riscoDeAtrasar: number; avisos: Aviso[] }

export type Ausencia = { pessoa: string; de: string; ate: string; fracao: number }

export type SquadDto = {
  squad: string
  carga: LinhaDeCarga[]
  vencimentos: VencimentosDaSemana
  ausencias: Ausencia[]
  riscoAlto: number
  riscoMedio: number
  avisos: Aviso[]
}

export const fetchSquad = (squad: string | null) =>
  getJson<SquadDto>(squad ? `/api/squad?squad=${encodeURIComponent(squad)}` : '/api/squad')
