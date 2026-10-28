import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const raiz = resolve(import.meta.dirname, '../..')

/** Todo SVG servido pelo painel: a pasta public/ e, se existir, src/assets/marca/. */
function svgs(): { caminho: string; texto: string }[] {
  const pastas = [join(raiz, 'public'), join(raiz, 'src/assets/marca')]
  const achados: { caminho: string; texto: string }[] = []
  for (const pasta of pastas) {
    if (!existsSync(pasta)) continue
    for (const nome of readdirSync(pasta)) {
      if (nome.toLowerCase().endsWith('.svg')) {
        const caminho = join(pasta, nome)
        achados.push({ caminho, texto: readFileSync(caminho, 'utf8') })
      }
    }
  }
  return achados
}

describe('ativos de marca em SVG', () => {
  it('haPeloMenosUmSvg', () => {
    expect(svgs().length).toBeGreaterThan(0)
  })

  it('logoSemScriptNemReferenciaExterna', () => {
    const proibidos: [string, RegExp][] = [
      ['script', /<\s*script\b/i],
      ['manipulador on*', /\son[a-z]+\s*=/i],
      ['foreignObject', /<\s*foreignObject\b/i],
      ['href externo', /(?:xlink:)?href\s*=\s*["']\s*(?:https?:)?\/\//i],
      ['href javascript', /href\s*=\s*["']\s*javascript:/i],
      ['@import', /@import/i],
      ['url() externo', /url\(\s*["']?\s*(?:https?:)?\/\//i],
    ]
    const problemas = svgs().flatMap(({ caminho, texto }) =>
      proibidos.filter(([, re]) => re.test(texto)).map(([nome]) => `${caminho}: ${nome}`),
    )
    expect(problemas).toEqual([])
  })

  it('logoTemViewBoxETitle', () => {
    const problemas = svgs().flatMap(({ caminho, texto }) => {
      const erros: string[] = []
      if (!/<svg\b[^>]*\bviewBox\s*=/i.test(texto)) erros.push(`${caminho}: sem viewBox`)
      if (!/<title>[^<]+<\/title>/i.test(texto)) erros.push(`${caminho}: sem <title>`)
      return erros
    })
    expect(problemas).toEqual([])
  })

  it('faviconDeclarado', () => {
    const html = readFileSync(join(raiz, 'index.html'), 'utf8')
    const m = html.match(/<link[^>]*rel="icon"[^>]*href="([^"]+)"/i)
    expect(m, 'index.html sem <link rel="icon">').not.toBeNull()
    const href = m![1]
    expect(href.startsWith('/')).toBe(true) // servido do próprio domínio
    expect(existsSync(join(raiz, 'public', href))).toBe(true)
  })

  it('logosDeMarcaAtivosEstaoEmPublic', () => {
    // Lê o módulo de ativos como texto (este arquivo roda no projeto "node" do TypeScript).
    const fonte = readFileSync(join(raiz, 'src/shell/marca-ativos.ts'), 'utf8')
    const caminhos = [...fonte.matchAll(/^\s+logoPara\w+:\s*'([^']+)'/gm)].map((m) => m[1])
    expect(caminhos.length === 0 || caminhos.length === 2).toBe(true) // 0 = caminho "sem logo"
    for (const url of caminhos) {
      expect(url.startsWith('/'), url).toBe(true) // do próprio domínio
      expect(existsSync(join(raiz, 'public', url)), url).toBe(true)
    }
  })
})
