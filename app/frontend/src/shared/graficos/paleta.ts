/**
 * Paleta categórica acessível dos gráficos (Okabe-Ito, ajustada por tema para ter ao menos 3:1 sobre a superfície). Cada série
 * tem COR, PADRÃO de preenchimento e FORMA de marcador, então nenhum gráfico depende só da cor. A série `i` usa o item
 * `i % 6` das três listas; a sétima repete a primeira nas três dimensões.
 */
export const CATEGORIAS = ['--cat-1', '--cat-2', '--cat-3', '--cat-4', '--cat-5', '--cat-6'] as const
export const PADROES = ['solido', 'listras', 'pontos', 'xadrez', 'diagonal', 'ondas'] as const
export const FORMAS = ['circulo', 'quadrado', 'triangulo', 'losango', 'cruz', 'estrela'] as const

export type Padrao = (typeof PADROES)[number]
export type Forma = (typeof FORMAS)[number]

export type EstiloDaSerie = {
  /** `var(--cat-n)`: a cor muda com o tema. */
  cor: string
  padrao: Padrao
  forma: Forma
  /** Traço da linha (a 1ª série é contínua; as outras variam), mais um sinal além da cor. */
  tracejado: string | undefined
}

const TRACEJADOS = [undefined, '6 3', '2 3', '8 3 2 3', '1 4', '10 4']

/** O estilo da série de índice [i] (a partir de 0). */
export function estiloDaSerie(i: number): EstiloDaSerie {
  const k = ((i % CATEGORIAS.length) + CATEGORIAS.length) % CATEGORIAS.length
  return { cor: `var(${CATEGORIAS[k]})`, padrao: PADROES[k], forma: FORMAS[k], tracejado: TRACEJADOS[k] }
}
