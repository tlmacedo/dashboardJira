// Contrato do card Esforço por task fechada (espelha `EsforcoDto` do backend). Cartões EF-01 e EF-02.

import type { Aviso } from '../../shared/useCard'

/** Uma task concluída: os três componentes lado a lado e o índice composto. */
export type EsforcoTask = {
  key: string
  resumo: string
  tipo: string
  /** Data de resolução (dia de Manaus, aaaa-mm-dd). */
  resolvida: string
  pesoTipo: number
  testes: number
  horas: number
  indice: number
  /** As horas vieram do tempo gasto do Jira (a task não tem worklog). */
  horasDoCampo: boolean
}

/** O esforço de uma pessoa pela POSSE das tasks; `testes` é fracionário. */
export type EsforcoPessoa = {
  pessoa: string
  tasks: number
  pesoTipo: number
  testes: number
  horas: number
  indice: number
  horasDoCampo: boolean
}

/** Um período (segunda-feira da semana ou dia 1 do mês). */
export type EsforcoPeriodo = { inicio: string; tasks: number; horas: number; indice: number }

export type EsforcoDto = {
  /** `esforço v<versão>`: a proposta de pesos de `esforco.yaml`. */
  versaoRegras: string
  tasks: EsforcoTask[]
  /** Pessoas da visão, em ordem alfabética (sem ranking). */
  pessoas: EsforcoPessoa[]
  periodos: EsforcoPeriodo[]
  avisos: Aviso[]
  granularidade: 'semana' | 'mes' | string
}
