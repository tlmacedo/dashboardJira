// Contrato do card Cruzamentos (espelha `CruzamentosDto` do backend). Cartão CZ-01.
// As tasks por trás de cada célula vêm do drill genérico (`shared/tasksApi.ts`), por segmento posicional.

import type { Aviso } from '../../shared/useCard'

/** n tasks na célula; com a medida idade P50, `valor` é o P50 da idade (nulo sem tasks). */
export type Celula = { n: number; valor: number | null; amostraPequena: boolean }

export type CruzamentosDto = {
  linhasEixo: string
  colunasEixo: string
  medida: 'contagem' | 'idadeP50' | string
  /** Valores do eixo das linhas, na ordem do eixo (nunca por contagem). `(sem)` = sem esse dado. */
  linhas: string[]
  colunas: string[]
  /** `[linha][coluna]`. */
  celulas: Celula[][]
  /** Nulo quando as linhas são pessoas: não há total por pessoa em destaque. */
  totaisDasLinhas: number[] | null
  totaisDasColunas: number[]
  total: number
  eixosDeLinha: string[]
  eixosDeColuna: string[]
  unit: 'util' | 'corrido' | string
  avisos: Aviso[]
}
