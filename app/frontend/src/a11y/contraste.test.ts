import { execFileSync } from 'node:child_process'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { contraste, tokensDosTemas, type NomeDoTema } from './contraste.ts'

const raiz = resolve(import.meta.dirname, '../..')
const ler = (rel: string) => readFileSync(join(raiz, rel), 'utf8')
const TEMAS: NomeDoTema[] = ['light', 'dark', 'sidia']

function cssDe(pasta: string): string[] {
  const saida: string[] = []
  for (const nome of readdirSync(pasta)) {
    const caminho = join(pasta, nome)
    if (statSync(caminho).isDirectory()) saida.push(...cssDe(caminho))
    else if (nome.endsWith('.css')) saida.push(caminho)
  }
  return saida
}

/** Regras `seletor { corpo }` de um CSS (inclusive as de dentro de `@media`), com o seletor em uma linha. */
function regras(css: string): { seletor: string; corpo: string }[] {
  return [...css.matchAll(/([^{}]+)\{([^{}]*)\}/g)].map((m) => ({
    seletor: m[1].replace(/\/\*[\s\S]*?\*\//g, '').replace(/\s+/g, ' ').trim(),
    corpo: m[2],
  }))
}

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

describe('contraste calculado em TypeScript (mesma conta WCAG)', () => {
  const temas = tokensDosTemas(ler('src/theme/tokens.css'))

  it('formulaConfereComValoresConhecidos', () => {
    expect(contraste('#000000', '#ffffff')).toBeCloseTo(21, 5)
    expect(contraste('#ffffff', '#ffffff')).toBeCloseTo(1, 5)
    // Cinza #767676 sobre branco é o limite clássico de 4,5:1.
    expect(contraste('#767676', '#ffffff')).toBeGreaterThan(4.5)
    expect(contraste('#777777', '#ffffff')).toBeLessThan(4.5)
  })

  it('menuControleTem3para1NosTresTemas', () => {
    // O contorno dos botões segmentados e do botão do menu (componentes de interface, WCAG 1.4.11) não pode usar a
    // divisória decorativa --menu-line (1,3:1 a 1,6:1): tem token próprio, >= 3:1 sobre o menu e sobre o item ativo.
    for (const tema of TEMAS) {
      const t = temas[tema]
      expect(t['menu-line-control'], `tema ${tema}: falta --menu-line-control`).toBeDefined()
      for (const fundo of ['menu', 'menu-active']) {
        const razao = contraste(t['menu-line-control'], t[fundo])
        expect(razao, `tema ${tema}: menu-line-control em ${fundo} = ${razao.toFixed(2)}:1`).toBeGreaterThanOrEqual(3)
      }
    }
  })

  it('controlesDoMenuUsamABordaDeControleDoMenu', () => {
    const css = regras(ler('src/shell/shell.css'))
    for (const alvo of ['.seg', '.burger']) {
      const corpos = css.filter((r) => r.seletor === alvo).map((r) => r.corpo)
      expect(corpos.length, alvo).toBeGreaterThan(0)
      for (const corpo of corpos) expect(corpo, alvo).not.toMatch(/border[^;]*var\(--menu-line\)/)
      expect(corpos.join('\n'), alvo).toContain('var(--menu-line-control)')
    }
  })

  it('botaoDoRodapeDoMenuTemTextoDaSuperficieEmVezDoTextoDoMenu', () => {
    // .btn tem fundo --surface (branco no Sidia). Dentro do menu o texto herdaria --menu-text2 (#c9d4ff no Sidia),
    // quase branco sobre branco (~1,5:1). O botão "Sair" precisa fixar a cor do texto da superfície.
    const regra = regras(ler('src/shell/shell.css')).find((r) => r.seletor === '.foot .btn')
    expect(regra, 'regra .foot .btn').toBeDefined()
    expect(regra!.corpo).toMatch(/color:\s*var\(--text\)/)
    for (const tema of TEMAS) {
      const razao = contraste(temas[tema].text, temas[tema].surface)
      expect(razao, `tema ${tema}: text em surface = ${razao.toFixed(2)}:1`).toBeGreaterThanOrEqual(4.5)
    }
  })

  it('corDeEstadoTem4e5SobreASuperficie', () => {
    // ✓ ● ▲ e o texto de estado usam estas cores: 4,5:1 vale para o texto e cobre os 3:1 do símbolo.
    for (const tema of TEMAS) {
      for (const token of ['ok', 'warn', 'bad']) {
        const razao = contraste(temas[tema][token], temas[tema].surface)
        expect(razao, `tema ${tema}: ${token} em surface = ${razao.toFixed(2)}:1`).toBeGreaterThanOrEqual(4.5)
      }
    }
  })

  // Toda borda `var(--line)` (divisória decorativa, ~1,3:1) precisa estar em seletor REVISADO: container, divisória, selo
  // de texto ou esqueleto, nunca controle. Seletor novo com `--line` derruba o teste e obriga a decidir: controle usa
  // --line-control (ou --menu-line-control); container pode entrar aqui com a justificativa.
  const REVISADOS: Record<string, string> = {
    '.aud-lote': 'selo de texto "lote"',
    '.mapa-filtros': 'container da barra de filtros do mapa',
    '.mapa-cartao': 'container do cartão',
    '.mapa-natureza': 'selo de texto da natureza',
    '.sinc-faixa': 'container de resumo da sincronização',
    '.cardpage .campo-grupo': 'fieldset que agrupa os checkboxes: o grupo tem legenda; cada controle dentro é nativo',
    '.pular': 'link de atalho: o texto e o foco de 2 px o identificam',
    '.notice': 'aviso: o texto é o sinal',
    '.sessao-expirada__caixa': 'container do diálogo',
    '.cartao': 'container do cartão',
    '.cartao-natureza': 'selo de texto da natureza',
    '.cartao-balao': 'container do balão de ajuda',
    '.tabela-virtual': 'moldura da região rolável (a região tem foco visível e nome)',
    '.barra': 'container da barra de filtros',
    '.chip': 'selo do filtro ativo: o texto e o botão × (22 px, texto) o identificam',
    '.esqueleto': 'esqueleto de carregamento (decorativo)',
    '.estado': 'container de estado vazio/erro',
    '.barra-linha:hover': 'só realce de hover; o estado de foco é o outline',
    '.alertas-faixa': 'container do aviso de alertas',
  }

  it('bordaDecorativaSoEmContainersRevisados', () => {
    const achados: string[] = []
    for (const arquivo of cssDe(join(raiz, 'src'))) {
      if (arquivo.endsWith('tokens.css')) continue
      for (const r of regras(readFileSync(arquivo, 'utf8'))) {
        if (!/border(-[a-z]+)*\s*:[^;]*var\(--line\)/.test(r.corpo)) continue
        // Só divisória de um lado (border-top/-bottom/-left/-right) é separador, não contorno de componente.
        const soLado = /border-(top|bottom|left|right)\s*:/.test(r.corpo) && !/(^|[;\s])border\s*:/.test(r.corpo)
        if (soLado) continue
        for (const seletor of r.seletor.split(',').map((s) => s.trim())) {
          if (!(seletor in REVISADOS)) achados.push(`${arquivo.replace(raiz + '/', '')}: ${seletor}`)
        }
      }
    }
    expect(achados, 'seletores com borda decorativa (--line) fora da lista revisada').toEqual([])
  })

  it('controleNuncaUsaABordaDecorativa', () => {
    const controle = /\.(btn|seg|burger|celula)\b|button|input|select|textarea/
    const achados: string[] = []
    for (const arquivo of cssDe(join(raiz, 'src'))) {
      if (arquivo.endsWith('tokens.css')) continue
      for (const r of regras(readFileSync(arquivo, 'utf8'))) {
        if (!/border(-[a-z]+)*\s*:[^;]*var\(--(menu-)?line\)/.test(r.corpo)) continue
        const soLado = /border-(top|bottom|left|right)\s*:/.test(r.corpo) && !/(^|[;\s])border\s*:/.test(r.corpo)
        if (soLado) continue
        if (controle.test(r.seletor)) achados.push(`${arquivo.replace(raiz + '/', '')}: ${r.seletor}`)
      }
    }
    expect(achados).toEqual([])
  })
})

describe('foco, movimento e alvo de toque', () => {
  const shell = ler('src/shell/shell.css')

  it('focoVisivelDoisPixels', () => {
    const foco = regras(shell).find((r) => r.seletor === ':focus-visible')
    expect(foco, 'regra global :focus-visible').toBeDefined()
    expect(foco!.corpo).toMatch(/outline:\s*2px solid var\(--focus\)/)
    expect(foco!.corpo).toMatch(/outline-offset:\s*2px/)
    // Nenhuma regra apaga o contorno de foco sem trocar por outro sinal.
    for (const arquivo of cssDe(join(raiz, 'src'))) {
      const todas = regras(readFileSync(arquivo, 'utf8'))
      for (const r of todas) {
        if (!/:focus-visible/.test(r.seletor) || !/outline:\s*(none|0)\b/.test(r.corpo)) continue
        // Permitido só quando o anel de 2 px passa para o ::after do mesmo elemento (cartão inteiro clicável).
        const noAfter = todas.some((o) => o.seletor === `${r.seletor}::after` && /outline:\s*2px solid var\(--focus\)/.test(o.corpo))
        if (!noAfter) expect.fail(`${arquivo}: ${r.seletor} remove o contorno de foco`)
      }
    }
  })

  it('reducedMotionDesligaTransicaoEAnimacaoEmTudo', () => {
    const i = shell.indexOf('@media (prefers-reduced-motion: reduce)')
    expect(i).toBeGreaterThanOrEqual(0)
    const trecho = shell.slice(i, shell.indexOf('\n}\n', i))
    expect(trecho).toMatch(/\*\s*\{[^}]*transition:\s*none !important/)
    expect(trecho).toMatch(/animation:\s*none !important/)
  })

  it('todaAnimacaoTemReducedMotion', () => {
    // O global do shell cobre tudo; cada CSS que declara animação também a desliga localmente (defesa em profundidade).
    for (const arquivo of cssDe(join(raiz, 'src'))) {
      const css = readFileSync(arquivo, 'utf8')
      if (!/animation:\s*[a-z-]+\s/.test(css)) continue
      expect(css, arquivo).toMatch(/prefers-reduced-motion: reduce/)
    }
  })

  it('alvoDeToqueDeAoMenos24px', () => {
    // WCAG 2.2 (2.5.8, AA): controle com menos de 24 x 24 px só passa com espaçamento; o botão × do chip tem 24 px.
    const chip = regras(ler('src/screens/TempoEmStatus/tempo.css')).find((r) => r.seletor === '.chip button')
    expect(chip).toBeDefined()
    expect(chip!.corpo).toMatch(/width:\s*(2[4-9]|[3-9]\d)px/)
    expect(chip!.corpo).toMatch(/height:\s*(2[4-9]|[3-9]\d)px/)
  })
})
