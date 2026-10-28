import { execFileSync } from 'node:child_process'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const raiz = resolve(import.meta.dirname, '../..')

/** Roda o script de contraste (a mesma conta WCAG usada na revisão visual) e devolve a saída. */
function rodar(): { saida: string; codigo: number } {
  try {
    return { saida: execFileSync('node', ['scripts/contraste-dos-tokens.mjs'], { cwd: raiz, encoding: 'utf8' }), codigo: 0 }
  } catch (e) {
    const erro = e as { stdout?: string; status?: number }
    return { saida: erro.stdout ?? '', codigo: erro.status ?? 1 }
  }
}

describe('contraste dos tokens (WCAG AA)', () => {
  const { saida, codigo } = rodar()

  it('contrasteDosTokensNosTresTemas', () => {
    expect(saida, 'saída do script').toContain('pares abaixo do mínimo: 0')
    expect(codigo).toBe(0)
    for (const tema of ['claro', 'escuro', 'sidia']) expect(saida).toContain(`== tema ${tema}`)
    expect(saida).not.toMatch(/^FALHA/m)
  })

  it('bordaDeControleTem3para1NosTresTemas', () => {
    // Um par por tema sobre cada fundo em que o controle aparece.
    const linhas = saida.split('\n').filter((l) => l.includes('line-control'))
    expect(linhas.length).toBeGreaterThanOrEqual(3 * 3)
    for (const l of linhas) {
      const razao = Number(l.match(/([\d.]+):1/)![1])
      expect(razao, l).toBeGreaterThanOrEqual(3)
    }
  })

  it('menuETemaSidiaCobertos', () => {
    expect(saida).toMatch(/menu-text .* em menu /)
    expect(saida).toMatch(/menu-text2 .* em menu /)
  })

  it('bordaDecorativaFicaInformativa', () => {
    // --line é divisória decorativa (fora da regra 1.4.11); aparece como informativo, sem reprovar.
    expect(saida).toMatch(/info\s+[\d.]+:1 .*line #/)
  })
})
