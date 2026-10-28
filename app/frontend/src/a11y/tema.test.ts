import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const raiz = resolve(import.meta.dirname, '../..')
const ler = (rel: string) => readFileSync(join(raiz, rel), 'utf8')

const tokens = ler('src/theme/tokens.css')

/** Corpo do bloco CSS que começa em `seletor {` (primeira ocorrência). */
function bloco(css: string, seletor: string): string {
  const i = css.indexOf(seletor + ' {')
  expect(i, `seletor ausente: ${seletor}`).toBeGreaterThanOrEqual(0)
  return css.slice(i, css.indexOf('}', i))
}

function arquivos(pasta: string, ext: string[]): string[] {
  const saida: string[] = []
  for (const nome of readdirSync(pasta)) {
    const caminho = join(pasta, nome)
    if (statSync(caminho).isDirectory()) saida.push(...arquivos(caminho, ext))
    else if (ext.some((e) => nome.endsWith(e))) saida.push(caminho)
  }
  return saida
}

describe('tokens finais', () => {
  it('azulDeMarcaUnico', () => {
    // O azul antigo do site não pode sobrar; o valor é montado em partes para o teste não se achar.
    const antigo = ['0023', 'a4'].join('')
    const achados = arquivos(join(raiz, 'src'), ['.ts', '.tsx', '.css', '.json'])
      .filter((f) => readFileSync(f, 'utf8').toLowerCase().includes(antigo))
    expect(achados).toEqual([])
    expect(ler('index.html').toLowerCase()).not.toContain(antigo)
    expect(bloco(tokens, ':root')).toMatch(/--primary:\s*#1329a0;/i)
    expect(bloco(tokens, "[data-theme='sidia']")).toMatch(/--primary:\s*#1329a0;/i)
    expect(bloco(tokens, "[data-theme='sidia']")).toMatch(/--menu:\s*#1329a0;/i)
  })

  it('molduraSoNoTemaSidia', () => {
    const raizCss = bloco(tokens, ':root')
    expect(raizCss).toMatch(/--frame:\s*none;/)
    expect(raizCss).toMatch(/--frame-altura:\s*0px;/)
    // O tema escuro não redefine a moldura: herda "nenhuma".
    expect(bloco(tokens, "[data-theme='dark']")).not.toMatch(/--frame/)
    const sidia = bloco(tokens, "[data-theme='sidia']")
    expect(sidia).toMatch(/--frame-altura:\s*4px;/)
    for (const cor of ['#3e4095', '#00a85a', '#f58634', '#1329a0']) {
      expect(sidia.toLowerCase(), cor).toContain(cor)
    }
  })

  it('molduraDecorativa', () => {
    const shell = ler('src/shell/Shell.tsx')
    expect(shell).toMatch(/<div className="frame" aria-hidden="true"\s*\/>/)
  })

  it('bordaDeControleTemTokenProprio', () => {
    for (const tema of [':root', "[data-theme='dark']", "[data-theme='sidia']"]) {
      expect(bloco(tokens, tema), tema).toMatch(/--line-control:\s*#[0-9a-f]{6};/i)
    }
  })

  it('controlesUsamABordaDeControle', () => {
    const controles: [string, string][] = [
      ['src/shell/shell.css', '.btn'],
      ['src/screens/TempoEmStatus/tempo.css', ".campo input[type='text'],\n.campo input[type='date']"],
      ['src/screens/TempoEmStatus/tempo.css', '.seg--claro'],
      ['src/screens/TempoEmStatus/tempo.css', '.celula'],
      ['src/shared/gaveta.css', '.gaveta-link'],
    ]
    for (const [arquivo, seletor] of controles) {
      const corpo = bloco(ler(arquivo), seletor)
      expect(corpo, `${arquivo} ${seletor}`).toContain('var(--line-control)')
      expect(corpo, `${arquivo} ${seletor}`).not.toMatch(/border[^;]*var\(--line\)/)
    }
  })

  it('laranjaEVerdeDaMarcaNuncaComoTexto', () => {
    // Cores da marca com contraste insuficiente sobre claro não podem ser cor de texto.
    const proibidas = /(?:^|[\s;{])color:\s*#(?:f58634|00a85a|13dc14|6ce270|3e4095)\b/im
    const infratores = arquivos(join(raiz, 'src'), ['.css']).filter((f) => proibidas.test(readFileSync(f, 'utf8')))
    expect(infratores).toEqual([])
    for (const token of ['text', 'text2', 'ok', 'warn', 'bad', 'primary']) {
      const valores = [...tokens.matchAll(new RegExp(`--${token}:\\s*(#[0-9a-fA-F]{6})`, 'g'))].map((m) => m[1].toLowerCase())
      for (const v of valores) expect(['#f58634', '#00a85a', '#13dc14', '#6ce270'], `--${token}`).not.toContain(v)
    }
  })
})

describe('tema sem piscada', () => {
  const html = ler('index.html')

  it('temaInicialAntesDoCss', () => {
    const tags = [...html.matchAll(/<(script|link)\b[^>]*>/gi)]
    const iScript = tags.findIndex((m) => /src="\/tema-inicial\.js"/.test(m[0]))
    expect(iScript, 'index.html sem <script src="/tema-inicial.js">').toBeGreaterThanOrEqual(0)
    const tag = tags[iScript][0]
    expect(tag).not.toMatch(/\b(defer|async)\b/)
    expect(tag).not.toMatch(/type="module"/)
    // Vem antes de qualquer folha de estilo e do bundle da aplicação.
    tags.slice(0, iScript).forEach((m) => expect(m[0]).not.toMatch(/stylesheet|type="module"/))
    // CSP: nenhum script inline.
    for (const m of tags.filter((t) => t[1].toLowerCase() === 'script')) expect(m[0]).toMatch(/\bsrc=/)
    expect(html.indexOf('/tema-inicial.js')).toBeLessThan(html.indexOf('/src/main.tsx'))
  })

  /** Executa public/tema-inicial.js com um ambiente simulado e devolve o tema aplicado. */
  function aplicar(ambiente: { salvo?: string | null; escuro?: boolean; armazenamentoQuebrado?: boolean; mediaQuebrada?: boolean }) {
    const dataset: Record<string, string> = {}
    const localStorage = {
      getItem: () => {
        if (ambiente.armazenamentoQuebrado) throw new Error('bloqueado')
        return ambiente.salvo ?? null
      },
    }
    const window = {
      matchMedia: () => {
        if (ambiente.mediaQuebrada) throw new Error('sem matchMedia')
        return { matches: ambiente.escuro === true }
      },
    }
    const document = { documentElement: { dataset } }
    new Function('window', 'document', 'localStorage', ler('public/tema-inicial.js'))(window, document, localStorage)
    return dataset.theme
  }

  it('temaInicialRespeitaEscolhaSalva', () => {
    expect(aplicar({ salvo: 'sidia', escuro: false })).toBe('sidia')
    expect(aplicar({ salvo: 'dark', escuro: false })).toBe('dark')
    expect(aplicar({ salvo: 'light', escuro: true })).toBe('light')
  })

  it('semEscolhaSeguePrefersColorScheme', () => {
    expect(aplicar({ salvo: null, escuro: true })).toBe('dark')
    expect(aplicar({ salvo: null, escuro: false })).toBe('light')
    expect(aplicar({ salvo: 'valor-estranho', escuro: true })).toBe('dark')
  })

  it('sidiaSoPorEscolha', () => {
    for (const escuro of [true, false]) expect(aplicar({ salvo: null, escuro })).not.toBe('sidia')
  })

  it('armazenamentoOuMediaQuebradosNaoDerrubam', () => {
    expect(aplicar({ armazenamentoQuebrado: true, escuro: true })).toBe('dark')
    expect(aplicar({ armazenamentoQuebrado: true, mediaQuebrada: true })).toBe('light')
  })

  it('usaAMesmaChaveDoThemeProvider', () => {
    const chave = ler('src/theme/ThemeProvider.tsx').match(/const CHAVE = '([^']+)'/)
    expect(chave).not.toBeNull()
    expect(ler('public/tema-inicial.js')).toContain(`'${chave![1]}'`)
  })

  it('reducedMotionRespeitado', () => {
    expect(ler('src/shell/shell.css')).toMatch(/@media \(prefers-reduced-motion: reduce\)/)
  })
})
