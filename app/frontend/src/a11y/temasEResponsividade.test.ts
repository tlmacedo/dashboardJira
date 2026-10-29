import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { contraste, tokensDosTemas } from './contraste.ts'

// Revisão visual (Plano 8, T5) por inspeção de código: tokens dos três temas e quebras de layout em 1440 e 390 px.
// A rodada com captura de tela (Chrome headless) é separada e fica registrada em docs/design/revisao-final.md.

const raiz = resolve(import.meta.dirname, '../..')
const ler = (rel: string) => readFileSync(join(raiz, rel), 'utf8')
const tokensCss = ler('src/theme/tokens.css')
const temas = tokensDosTemas(tokensCss)

function arquivosCss(pasta: string): string[] {
  const saida: string[] = []
  for (const nome of readdirSync(pasta)) {
    const caminho = join(pasta, nome)
    if (statSync(caminho).isDirectory()) saida.push(...arquivosCss(caminho))
    else if (nome.endsWith('.css')) saida.push(caminho)
  }
  return saida
}

const CSS_DOS_COMPONENTES = arquivosCss(join(raiz, 'src')).filter((f) => !f.endsWith('tokens.css') && !f.endsWith('fontes.css'))
const rel = (f: string) => f.replace(raiz + '/', '')

describe('tokens dos três temas', () => {
  it('todaVarUsadaTemTokenDefinido', () => {
    const definidos = new Set<string>()
    for (const f of arquivosCss(join(raiz, 'src'))) {
      for (const m of readFileSync(f, 'utf8').matchAll(/(?:^|[\s;{])--([a-z0-9-]+)\s*:/g)) definidos.add(m[1])
    }
    const faltando: string[] = []
    for (const f of CSS_DOS_COMPONENTES) {
      for (const m of readFileSync(f, 'utf8').matchAll(/var\(--([a-z0-9-]+)/g)) {
        if (!definidos.has(m[1])) faltando.push(`${rel(f)}: --${m[1]}`)
      }
    }
    expect(faltando, 'var(--x) sem definição em nenhum tema').toEqual([])
  })

  it('temaEscuroRedefineTodaCorDeFundoETextoEDeEstado', () => {
    const escuro = tokensCss.slice(tokensCss.indexOf("[data-theme='dark'] {"), tokensCss.indexOf("[data-theme='sidia'] {"))
    const obrigatorios = [
      'bg', 'surface', 'text', 'text2', 'line', 'line-control', 'soft', 'primary', 'on-primary', 'accent', 'ok', 'warn', 'bad',
      'menu', 'menu-text', 'menu-text2', 'menu-line', 'menu-line-control', 'menu-active', 'focus',
      'cat-1', 'cat-2', 'cat-3', 'cat-4', 'cat-5', 'cat-6',
    ]
    const faltando = obrigatorios.filter((n) => !new RegExp(`--${n}:\\s*#[0-9a-f]{6}`, 'i').test(escuro))
    expect(faltando, 'o tema Escuro herdaria o valor do Claro').toEqual([])
    expect(escuro).toMatch(/color-scheme:\s*dark/)
  })

  it('temaSidiaRedefineMenuFundoETextoEHerdaOResto', () => {
    const sidia = tokensCss.slice(tokensCss.indexOf("[data-theme='sidia'] {"))
    for (const n of ['bg', 'text', 'text2', 'line', 'line-control', 'soft', 'menu', 'menu-text', 'menu-text2', 'menu-line', 'menu-line-control', 'menu-active', 'menu-marca']) {
      expect(sidia, `--${n}`).toMatch(new RegExp(`--${n}:`))
    }
    // O Sidia é claro no conteúdo: não redefine superfície nem estado, que herdam do Claro (já aprovados no contraste).
    expect(sidia).not.toMatch(/color-scheme:\s*dark/)
  })

  it('paletaCategoricaTem3para1SobreASuperficieNosTresTemas', () => {
    // Séries de gráfico são elementos gráficos (WCAG 1.4.11): >= 3:1 sobre a superfície onde o gráfico é desenhado.
    for (const [tema, t] of Object.entries(temas)) {
      for (let i = 1; i <= 6; i++) {
        const razao = contraste(t[`cat-${i}`], t.surface)
        expect(razao, `tema ${tema}: cat-${i} em surface = ${razao.toFixed(2)}:1`).toBeGreaterThanOrEqual(3)
      }
    }
  })

  // Cores escritas direto no CSS dos componentes (fora de tokens.css): só as da lista, cada uma com contraste conferido.
  it('corLiteralForaDosTokensSoNaListaRevisada', () => {
    const literais: string[] = []
    for (const f of CSS_DOS_COMPONENTES) {
      for (const m of readFileSync(f, 'utf8').matchAll(/(?<![\w-])#[0-9a-fA-F]{6}\b/g)) literais.push(`${rel(f)}: ${m[0].toLowerCase()}`)
    }
    expect(literais.sort()).toEqual(['src/shell/shell.css: #77ff94', 'src/shell/shell.css: #b3261e'])
  })

  it('literaisRevisadosTemContraste', () => {
    // Foco do menu no tema Sidia (#77ff94) sobre o menu azul e sobre o item ativo.
    const s = temas.sidia
    expect(contraste('#77ff94', s.menu)).toBeGreaterThanOrEqual(3)
    expect(contraste('#77ff94', s['menu-active'])).toBeGreaterThanOrEqual(3)
    // Faixa vermelha do modo desenvolvimento: texto branco (AA) em qualquer tema.
    expect(contraste('#ffffff', '#b3261e')).toBeGreaterThanOrEqual(4.5)
    const shell = ler('src/shell/shell.css')
    expect(shell).toMatch(/\[data-theme='sidia'\] \.menu :focus-visible\s*\{\s*outline-color:\s*#77ff94/)
    expect(shell).toMatch(/\.devbar\s*\{[^}]*background:\s*#b3261e;[^}]*color:\s*#fff/)
  })
})

describe('layout em 1440 e 390 px', () => {
  const todos = arquivosCss(join(raiz, 'src')).map((f) => ({ f, css: readFileSync(f, 'utf8') }))

  it('pontosDeQuebraFicamEntre390e1440', () => {
    // Todo max-width fica acima de 390 (a regra de celular vale a 390 px) e abaixo de 1440 (a 1440 vale o desktop).
    const pontos = new Set<number>()
    for (const { css } of todos) for (const m of css.matchAll(/@media \(max-width:\s*(\d+)px\)/g)) pontos.add(Number(m[1]))
    expect(pontos.size).toBeGreaterThan(0)
    for (const p of pontos) {
      expect(p, `ponto de quebra ${p}px`).toBeGreaterThan(390)
      expect(p, `ponto de quebra ${p}px`).toBeLessThan(1440)
    }
    expect(pontos.has(820), 'a casca vira celular em 820 px').toBe(true)
  })

  it('minWidthMaiorQueOCelularSoDentroDeRolagemHorizontal', () => {
    // 390 px - 2 x 16 px de margem = 358 px úteis. Quem pede mais (gráficos SVG) precisa de um contêiner com overflow-x.
    const achados: string[] = []
    for (const { f, css } of todos) {
      for (const m of css.matchAll(/min-width:\s*(\d+)px/g)) {
        if (Number(m[1]) > 358 && !/overflow-x:\s*auto/.test(css)) achados.push(`${rel(f)}: min-width ${m[1]}px sem overflow-x: auto`)
      }
    }
    expect(achados).toEqual([])
  })

  it('larguraFixaMaiorQueOCelularNaoExisteForaDeMedia', () => {
    const achados: string[] = []
    for (const { f, css } of todos) {
      // Remove os blocos @media para olhar só as regras que valem sempre.
      const semMedia = css.replace(/@media[^{]*\{(?:[^{}]*\{[^{}]*\})*[^{}]*\}/g, '')
      for (const m of semMedia.matchAll(/(?<![-\w])(?:width|flex-basis):\s*(\d+)px/g)) {
        if (Number(m[1]) > 358) achados.push(`${rel(f)}: width ${m[1]}px`)
      }
    }
    expect(achados).toEqual([])
  })

  it('casca390UmaColunaEMain16px', () => {
    const shell = ler('src/shell/shell.css')
    const celular = shell.slice(shell.indexOf('@media (max-width: 820px)'), shell.indexOf('@media (prefers-reduced-motion'))
    expect(celular).toMatch(/\.app\s*\{[^}]*grid-template-columns:\s*minmax\(0,\s*1fr\)/)
    expect(celular).toMatch(/main\s*\{[^}]*padding:\s*16px/)
    // No desktop o menu é fixo ao lado de uma coluna que pode encolher (minmax(0, 1fr)): nada empurra a página para o lado.
    expect(shell).toMatch(/grid-template-columns:\s*240px minmax\(0,\s*1fr\)/)
  })

  it('viewportNaoBloqueiaZoom', () => {
    const html = ler('index.html')
    expect(html).toMatch(/<meta name="viewport" content="width=device-width, initial-scale=1\.0"\s*\/>/)
    expect(html).not.toMatch(/user-scalable\s*=\s*(no|0)|maximum-scale/i)
  })

  it('tabelasRolamDentroDoCartaoNoCelular', () => {
    expect(ler('src/shared/tabela.css')).toMatch(/\.tabela-rolagem\s*\{[^}]*overflow-x:\s*auto/)
    // O corpo de cartão com tabela e a lista da sincronização também rolam por dentro, sem alargar a página.
    expect(ler('src/shared/cartao.css')).toMatch(/\.cartao-corpo\s*\{[^}]*overflow-x:\s*auto/)
    expect(ler('src/screens/Sincronizacao/sincronizacao.css')).toMatch(/overflow-x:\s*auto/)
  })

  it('textosLongosQuebramEmVezDeCortar', () => {
    // Em inglês os rótulos são mais longos: os de barra e de chave usam overflow-wrap, e o botão do menu não fixa largura.
    expect(ler('src/screens/TempoEmStatus/tempo.css')).toMatch(/\.barra-nome\s*\{[^}]*overflow-wrap:\s*anywhere/)
    const menu = ler('src/shell/shell.css')
    expect(menu).not.toMatch(/\.seg button\s*\{[^}]*(?<![-\w])width:/)
  })
})
