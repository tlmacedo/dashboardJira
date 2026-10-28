// Contrato do card Abertas (espelha `AbertasDto` do backend). Só fala com o painel, nunca com o Jira.
// As tasks por trás de cada número vêm do drill genérico (`shared/tasksApi.ts`): cartões AB-01 a AB-09.

import type { Aviso } from '../../shared/useCard'

export type Contagem = { chave: string; n: number }

export type FaixaTipo = { tipo: string; p50: number; p85: number; n: number }

export type LinhaAberta = {
  key: string
  resumo: string
  tipo: string
  status: string
  prioridade: string | null
  responsavel: string | null
  idade: number
  vence: string | null
  horasRestantes: number | null
}

export type AbertasDto = {
  abertas: number
  emAndamento: number
  acimaDoP85: number
  historicoPorTipo: FaixaTipo[]
  porStatus: Contagem[]
  porPrioridade: Contagem[]
  porIdade: Contagem[]
  porVencimento: Contagem[]
  linhas: LinhaAberta[]
  unit: 'util' | 'corrido' | string
  avisos: Aviso[]
}
