// Contrato do drill genérico (espelha `DrillGenericoDto` do backend). Só fala com o backend do próprio painel.

import { getJson } from '../api/client'

/** Uma linha da gaveta. `responsavel` é nulo quando a pessoa está fora da visão de quem pediu (ou não há responsável). */
export type LinhaDeTask = {
  key: string
  resumo: string
  responsavel: string | null
  status: string
  detalhe: string
}

export type TasksDoCartao = {
  /** Chaves de tradução (título e "o que mostra" do cartão). */
  tituloChave: string
  definicaoChave: string
  total: number
  colunas: string[]
  linhas: LinhaDeTask[]
  /** Link pronto para abrir a seleção no Jira (montado no backend); nulo quando não há. O frontend nunca monta URL do Jira. */
  linkJira: string | null
}

/** `GET /api/cartoes/{id}/tasks?segmento=...&<filtros>`: as tasks por trás de um número do cartão. */
export function fetchTasksDoCartao(cartaoId: string, segmento: string, filtros: URLSearchParams): Promise<TasksDoCartao> {
  const p = new URLSearchParams(filtros)
  p.set('segmento', segmento)
  return getJson<TasksDoCartao>(`/api/cartoes/${encodeURIComponent(cartaoId)}/tasks?${p.toString()}`)
}
