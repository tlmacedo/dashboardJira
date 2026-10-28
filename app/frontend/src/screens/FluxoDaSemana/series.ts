/** As séries do gráfico: forma de preenchimento DISTINTA (cheio, vazado, hachurado), então a cor nunca é o único sinal. */
export const SERIES_DO_FLUXO = [
  { id: 'entrada', rotulo: 'fluxo.kpi.entrou', campo: 'entrou', padrao: 'cheio' },
  { id: 'inicio', rotulo: 'fluxo.kpi.comecou', campo: 'comecou', padrao: 'vazado' },
  { id: 'saida', rotulo: 'fluxo.kpi.saiu', campo: 'saiu', padrao: 'hachurado' },
] as const

export type SerieDoFluxo = (typeof SERIES_DO_FLUXO)[number]
