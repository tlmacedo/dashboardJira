// Contrato do card Épicos (espelha `EpicosDto` do backend). Cartão EP-01.
// As tasks por trás de cada número vêm do drill genérico (`shared/tasksApi.ts`): segmentos `epico:<id>:itens|concluidos|vencidos`.

import type { Aviso } from '../../shared/useCard'

export type LinhaEpico = {
  /** Código do épico para os segmentos do drill (a chave do épico nunca vai para o registro de acesso). */
  id: string
  epico: string
  itens: number
  concluidos: number
  /** Fração de 0 a 1; nula sem itens. */
  progresso: number | null
  /** Data de criação da task aberta mais antiga. */
  maisAntigo: string | null
  vencidos: number
  pontos: number | null
  /** Nulo = sem amostra própria ("—"). */
  previsaoSemanasP85: number | null
}

export type EpicosDto = {
  linhas: LinhaEpico[]
  /** Mínimo de entregas do próprio épico no histórico para ele ter previsão. */
  minItens: number
  avisos: Aviso[]
}
