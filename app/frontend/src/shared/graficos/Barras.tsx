import { useLang } from '../../i18n/LangProvider'
import { inteiro } from '../format'
import { Legenda } from './Legenda'
import { DefinicoesDePadroes, Marcador, preenchimentoDaSerie, useIdDePadroes } from './padroes'
import { estiloDaSerie } from './paleta'
import { TabelaEquivalente } from './TabelaEquivalente'
import './graficos.css'

export type CategoriaDeBarras = { chave: string; rotulo: string; sub?: string }
export type SerieDeBarras = { id: string; rotulo: string; valores: number[] }

export type BarrasProps = {
  categorias: CategoriaDeBarras[]
  series: SerieDeBarras[]
  /** Nome do gráfico (resumo para leitor de tela: série, categorias e o maior valor). */
  rotulo: string
  /** Presente = cada barra com valor é um botão que abre o recorte (série, índice da categoria). */
  aoAbrir?: (serie: SerieDeBarras, indice: number) => void
  /** Texto de cada barra para leitor de tela e título; padrão "<série> <categoria>: <valor>". */
  rotuloDaBarra?: (serie: SerieDeBarras, categoria: CategoriaDeBarras, valor: number) => string
  /** Sem a tabela equivalente embutida (a tela já mostra os mesmos dados em tabela ao lado). */
  semTabela?: boolean
}

const LARGURA = 720
const ALTURA = 240
const MARGEM = { esquerda: 36, direita: 8, topo: 12, base: 34 }

/**
 * Barras agrupadas por categoria em SVG próprio (sem biblioteca). Cada série tem cor, PADRÃO de preenchimento e FORMA
 * (`data-padrao`, `data-forma`, repetidos na legenda, sempre visível); os mesmos números saem numa tabela equivalente. O
 * valor zero não desenha barra clicável.
 */
export function Barras(props: BarrasProps) {
  const { t, lang } = useLang()
  const base = useIdDePadroes()
  const { categorias, series } = props
  const maximo = Math.max(1, ...series.flatMap((s) => s.valores))
  const largura = LARGURA - MARGEM.esquerda - MARGEM.direita
  const altura = ALTURA - MARGEM.topo - MARGEM.base
  const passo = largura / Math.max(1, categorias.length)
  const barra = Math.min(18, (passo * 0.8) / Math.max(1, series.length))
  const grupo = barra * series.length
  const ticks = [...new Set([0, Math.ceil(maximo / 2), maximo])]
  const y = (v: number) => MARGEM.topo + altura - (v / maximo) * altura
  const rotuloDe = props.rotuloDaBarra ?? ((s, c, v) => `${s.rotulo} ${c.rotulo}: ${v}`)
  const resumo = t('grafico.resumo')
    .replace('{nome}', props.rotulo)
    .replace('{series}', series.map((s) => s.rotulo).join(', '))
    .replace('{n}', inteiro(categorias.length, lang))
    .replace('{maximo}', inteiro(maximo, lang))

  return (
    <div className="grafico" role="group" aria-label={props.rotulo}>
      <Legenda itens={series.map((s) => ({ id: s.id, rotulo: s.rotulo }))} base={base} rotulo={t('grafico.legenda')} />
      <svg viewBox={`0 0 ${LARGURA} ${ALTURA}`} className="grafico-svg" preserveAspectRatio="xMidYMid meet" role={props.aoAbrir ? 'presentation' : 'img'} aria-label={props.aoAbrir ? undefined : resumo}>
        <DefinicoesDePadroes base={base} series={series.length} />
        {ticks.map((v) => (
          <g key={v}>
            <line x1={MARGEM.esquerda} x2={LARGURA - MARGEM.direita} y1={y(v)} y2={y(v)} className="grafico-grade" />
            <text x={MARGEM.esquerda - 6} y={y(v) + 4} textAnchor="end" className="grafico-eixo">
              {inteiro(v, lang)}
            </text>
          </g>
        ))}
        {categorias.map((c, i) => {
          const x0 = MARGEM.esquerda + i * passo + (passo - grupo) / 2
          return (
            <g key={c.chave}>
              {series.map((s, k) => {
                const valor = s.valores[i] ?? 0
                const e = estiloDaSerie(k)
                const x = x0 + k * barra
                const rotulo = rotuloDe(s, c, valor)
                const forma = (
                  <rect
                    x={x} y={y(valor)} width={Math.max(1, barra - 2)} height={(valor / maximo) * altura}
                    fill={preenchimentoDaSerie(base, k)} stroke={e.cor} strokeWidth={1.5} className="grafico-barra"
                  />
                )
                const marca = valor > 0 ? <Marcador forma={e.forma} cx={x + Math.max(1, barra - 2) / 2} cy={y(valor) - 5} r={3} cor={e.cor} /> : null
                const corpo = (
                  <>
                    {forma}
                    {marca}
                  </>
                )
                const atributos = { 'data-serie': s.id, 'data-padrao': e.padrao, 'data-forma': e.forma }
                if (valor === 0) return <g key={s.id} {...atributos}>{corpo}</g>
                if (!props.aoAbrir) {
                  return (
                    <g key={s.id} {...atributos} role="img" aria-label={rotulo}>
                      {corpo}
                    </g>
                  )
                }
                const abrir = () => props.aoAbrir?.(s, i)
                return (
                  <g
                    key={s.id} {...atributos} role="button" tabIndex={0} aria-label={rotulo} className="grafico-botao" onClick={abrir}
                    onKeyDown={(ev) => {
                      if (ev.key === 'Enter' || ev.key === ' ') {
                        ev.preventDefault()
                        abrir()
                      }
                    }}
                  >
                    {corpo}
                  </g>
                )
              })}
              <text x={x0 + grupo / 2} y={ALTURA - MARGEM.base + 14} textAnchor="middle" className="grafico-eixo">
                {c.rotulo}
              </text>
              {c.sub && (
                <text x={x0 + grupo / 2} y={ALTURA - MARGEM.base + 26} textAnchor="middle" className="grafico-eixo">
                  {c.sub}
                </text>
              )}
            </g>
          )
        })}
      </svg>
      {!props.semTabela && (
        <TabelaEquivalente
          rotulo={props.rotulo}
          cabecalhos={['', ...series.map((s) => s.rotulo)]}
          linhas={categorias.map((c, i) => [c.rotulo, ...series.map((s) => inteiro(s.valores[i] ?? 0, lang))])}
        />
      )}
    </div>
  )
}
