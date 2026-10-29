import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
// @ts-expect-error o projeto não inclui os tipos do Node; o Vitest roda em Node e lê o arquivo de tokens direto.
import { readFileSync } from 'node:fs'
import { LangProvider } from '../../i18n/LangProvider'
import { Barras } from './Barras'
import { Dispersao } from './Dispersao'
import { Linhas } from './Linhas'
import { CATEGORIAS, estiloDaSerie } from './paleta'

afterEach(() => cleanup())

const envolver = (el: React.ReactNode) => render(<LangProvider>{el}</LangProvider>)

const categorias = [
  { chave: 'a', rotulo: 'Sem 1' },
  { chave: 'b', rotulo: 'Sem 2' },
]
const series = [
  { id: 's1', rotulo: 'Entradas', valores: [3, 5] },
  { id: 's2', rotulo: 'Saídas', valores: [2, 0] },
]

/** Luminância relativa (WCAG) de uma cor #rrggbb. */
function luminancia(hex: string): number {
  const c = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255).map((v) => (v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4))
  return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]
}
const contraste = (a: string, b: string) => {
  const [x, y] = [luminancia(a), luminancia(b)].sort((p, q) => q - p)
  return (x + 0.05) / (y + 0.05)
}

const lerTokens = (): string => readFileSync('src/theme/tokens.css', 'utf8')

function bloco(css: string, seletor: string): string {
  const i = css.indexOf(seletor)
  return css.slice(i, css.indexOf('}', i))
}
const valorDe = (b: string, nome: string) => new RegExp(`${nome}:\\s*(#[0-9a-fA-F]{6})`).exec(b)![1]

describe('paleta categórica', () => {
  it('seisCoresComContraste3NaSuperficie', () => {
    const css = lerTokens()
    for (const [seletor, superficie] of [[':root {', '#ffffff'], ["[data-theme='dark'] {", '#171e2e']] as const) {
      const b = bloco(css, seletor)
      for (const nome of CATEGORIAS) expect(contraste(valorDe(b, nome), superficie), `${seletor} ${nome}`).toBeGreaterThanOrEqual(3)
    }
  })

  it('serieTemCorPadraoEForma', () => {
    const vistos = new Set(Array.from({ length: 6 }, (_, i) => JSON.stringify([estiloDaSerie(i).cor, estiloDaSerie(i).padrao, estiloDaSerie(i).forma])))
    expect(vistos.size).toBe(6)
    expect(estiloDaSerie(6)).toEqual(estiloDaSerie(0))
  })
})

describe('gráficos compartilhados', () => {
  it('graficoTemTabelaEquivalente', () => {
    envolver(<Barras categorias={categorias} series={series} rotulo="Fluxo" />)
    expect(screen.getByText('Ver dados em tabela')).toBeTruthy()
    expect(screen.getByRole('table', { hidden: true, name: 'Fluxo' })).toBeTruthy()
  })

  it('ariaLabelResumeOGrafico', () => {
    envolver(<Barras categorias={categorias} series={series} rotulo="Fluxo" />)
    const img = screen.getByRole('img', { name: /Fluxo: séries Entradas, Saídas; 2 categorias; maior valor 5/ })
    expect(img).toBeTruthy()
  })

  it('legendaSempreVisivel', () => {
    envolver(<Barras categorias={categorias} series={series} rotulo="Fluxo" />)
    const itens = screen.getByRole('list', { name: 'Legenda do gráfico' }).querySelectorAll('li')
    expect(itens).toHaveLength(2)
    expect(itens[0].getAttribute('data-padrao')).toBe('solido')
    expect(itens[1].getAttribute('data-forma')).toBe('quadrado')
  })

  it('nenhumGraficoSoPorCor', () => {
    const { container } = envolver(
      <>
        <Barras categorias={categorias} series={series} rotulo="B" />
        <Linhas categorias={categorias} series={series} rotulo="L" />
        <Dispersao rotulo="D" series={[{ id: 'p', rotulo: 'Prazo', pontos: [{ x: 1, y: 2, rotulo: 'TG-1' }] }]} />
      </>,
    )
    const marcados = container.querySelectorAll('[data-serie]')
    expect(marcados.length).toBeGreaterThan(0)
    for (const el of marcados) {
      expect(el.getAttribute('data-padrao') ?? el.getAttribute('data-forma')).toBeTruthy()
    }
    // Linhas e dispersão: marcador de forma por ponto e tabela equivalente.
    expect(container.querySelectorAll('.grafico-tabela').length).toBe(3)
    expect(container.querySelectorAll('[data-forma]').length).toBeGreaterThan(4)
  })

  it('barraComValorAbreORecorte', () => {
    let aberto = ''
    envolver(<Barras categorias={categorias} series={series} rotulo="Fluxo" aoAbrir={(s, i) => (aberto = `${s.id}:${i}`)} />)
    screen.getByRole('button', { name: 'Entradas Sem 2: 5' }).dispatchEvent(new MouseEvent('click', { bubbles: true }))
    expect(aberto).toBe('s1:1')
    expect(screen.queryByRole('button', { name: 'Saídas Sem 2: 0' })).toBeNull()
  })
})
