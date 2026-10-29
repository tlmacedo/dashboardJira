// Contraste WCAG 2.x calculado direto dos tokens dos três temas (src/theme/tokens.css).
// O script `scripts/contraste-dos-tokens.mjs` faz a mesma conta em Node puro para a revisão visual; aqui ela fica
// disponível para os testes que precisam citar tema, par e razão na mensagem de falha.

export type NomeDoTema = 'light' | 'dark' | 'sidia'

export type Tokens = Record<string, string>

/** Luminância relativa (WCAG 2.x) de uma cor `#rrggbb`. */
export function luminancia(hex: string): number {
  const canais = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255)
  const [r, g, b] = canais.map((v) => (v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4))
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

/** Razão de contraste entre duas cores `#rrggbb` (de 1 a 21). */
export function contraste(corA: string, corB: string): number {
  const [claro, escuro] = [luminancia(corA), luminancia(corB)].sort((p, q) => q - p)
  return (claro + 0.05) / (escuro + 0.05)
}

const SELETORES: Record<NomeDoTema, string> = {
  light: ':root',
  dark: "[data-theme='dark']",
  sidia: "[data-theme='sidia']",
}

function bloco(css: string, seletor: string): { cores: Tokens; referencias: Tokens } {
  const i = css.indexOf(seletor + ' {')
  if (i < 0) throw new Error('seletor ausente: ' + seletor)
  const corpo = css.slice(i, css.indexOf('}', i))
  const cores: Tokens = {}
  const referencias: Tokens = {}
  for (const m of corpo.matchAll(/--([a-z0-9-]+):\s*(#[0-9a-fA-F]{6})\s*;/g)) cores[m[1]] = m[2].toLowerCase()
  for (const m of corpo.matchAll(/--([a-z0-9-]+):\s*var\(--([a-z0-9-]+)\)\s*;/g)) referencias[m[1]] = m[2]
  return { cores, referencias }
}

/** Tokens de cor de cada tema já mesclados com o Claro (`:root`), resolvendo as referências `var(--x)`. */
export function tokensDosTemas(css: string): Record<NomeDoTema, Tokens> {
  const base = bloco(css, SELETORES.light)
  const saida = {} as Record<NomeDoTema, Tokens>
  for (const tema of Object.keys(SELETORES) as NomeDoTema[]) {
    const proprio = tema === 'light' ? { cores: {}, referencias: {} } : bloco(css, SELETORES[tema])
    const cores: Tokens = { ...base.cores, ...proprio.cores }
    const referencias = { ...base.referencias, ...proprio.referencias }
    for (const [nome, alvo] of Object.entries(referencias)) if (!(nome in proprio.cores)) cores[nome] = cores[alvo]
    saida[tema] = cores
  }
  return saida
}
