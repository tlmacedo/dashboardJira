import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const raiz = resolve(import.meta.dirname, '../..')
const ler = (rel: string) => readFileSync(join(raiz, rel), 'utf8')

const fontesCss = ler('src/theme/fontes.css')
const tokens = ler('src/theme/tokens.css')

const pastaFontes = join(raiz, 'public/fonts')
const arquivosDeFonte = existsSync(pastaFontes) ? readdirSync(pastaFontes).filter((n) => n.endsWith('.woff2')) : []
const blocosFontFace = [...fontesCss.matchAll(/@font-face\s*\{[^}]*\}/g)].map((m) => m[0])

/** Valor de --fonte-texto no :root (primeiro bloco de tokens). */
function pilha(): string {
  const m = tokens.match(/--fonte-texto:\s*([^;]+);/)
  expect(m, '--fonte-texto ausente em tokens.css').not.toBeNull()
  return m![1].trim()
}

describe('fonte da marca', () => {
  it('fonteServidaDoProprioDominio', () => {
    for (const [nome, css] of [['fontes.css', fontesCss], ['tokens.css', tokens]]) {
      const externas = [...css.matchAll(/url\(\s*["']?\s*(?:https?:)?\/\/[^)]*\)/gi)].map((m) => m[0])
      expect(externas, `${nome} com url() externa`).toEqual([])
    }
    // Nada de folha de fontes de terceiros no HTML.
    const html = ler('index.html')
    expect(html).not.toMatch(/fonts\.(googleapis|gstatic)\.com/i)
    for (const bloco of blocosFontFace) {
      for (const m of bloco.matchAll(/url\(\s*["']?([^"')]+)["']?\s*\)/g)) {
        expect(m[1].startsWith('/fonts/'), m[1]).toBe(true)
        expect(existsSync(join(raiz, 'public', m[1])), `arquivo ausente: ${m[1]}`).toBe(true)
      }
    }
  })

  it('fontDisplaySwap', () => {
    for (const bloco of blocosFontFace) expect(bloco).toMatch(/font-display:\s*swap/)
  })

  it('pilhaDeFallbackMantida', () => {
    const p = pilha()
    expect(p).toContain('system-ui')
    expect(p.endsWith('sans-serif')).toBe(true)
    for (const familia of ['Segoe UI', 'Roboto', 'Helvetica Neue']) expect(p).toContain(familia)
  })

  it('semFonteSemFontFace', () => {
    if (arquivosDeFonte.length === 0) {
      // Caminho alternativo (sem a Mark): nenhuma @font-face e a pilha começa no sistema.
      expect(blocosFontFace).toEqual([])
      expect(pilha().startsWith('system-ui')).toBe(true)
    } else {
      expect(blocosFontFace.length).toBeGreaterThan(0)
      expect(pilha().startsWith('"Mark"')).toBe(true)
    }
  })

  it('textoUsaSoOTokenDeFonte', () => {
    expect(tokens).not.toMatch(/--font:/) // um único token de família
    const shell = ler('src/shell/shell.css')
    expect(shell).toMatch(/font-family:\s*var\(--fonte-texto\)/)
  })

  it('fontesCssEImportadoNoMain', () => {
    expect(ler('src/main.tsx')).toContain("./theme/fontes.css")
  })
})
