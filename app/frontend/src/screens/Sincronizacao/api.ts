// Contrato do status do sincronizador (espelha SyncStatusDto do backend). Só fala com o backend do painel;
// nunca com o Jira. O corpo só tem códigos, contagens e instantes: nunca URL, token, JQL nem texto de erro.

import { ApiError } from '../TempoEmStatus/api'

export type ExecucaoDto = {
  tipo: 'INCREMENTAL' | 'RECONCILIACAO' | 'RETENCAO' | string
  status: 'OK' | 'FALHA' | 'CANCELADA' | 'EM_ANDAMENTO' | string
  inicio: string
  fim: string | null
  issues: number
  eventos: number
  requisicoes: number
  incompletas: number
  usuariosNaoResolvidos: number
  removidas: number
  codigoErro: string | null
}

export type InstanciaStatusDto = {
  instancia: string
  /** `LIBERADA` ou o motivo da negação (`DESLIGADO_PELA_FLAG`, `INSTANCIA_NAO_CONFIGURADA`, `SUSPENSA_POR_ERRO`). */
  gate: string
  cursor: string | null
  ultimaReconciliacao: string | null
  suspensa: string | null
  execucoes: ExecucaoDto[]
}

export type SyncStatusDto = {
  modo: 'DESLIGADO_PELA_FLAG' | 'PERIODICO' | string
  liveEnabled: boolean
  instancias: InstanciaStatusDto[]
  retencao: ExecucaoDto | null
}

export async function fetchSyncStatus(): Promise<SyncStatusDto> {
  const resposta = await fetch('/api/sync/status', { headers: { Accept: 'application/json' } })
  if (!resposta.ok) throw new ApiError(resposta.status)
  return (await resposta.json()) as SyncStatusDto
}
