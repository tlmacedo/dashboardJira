import { useLang } from '../../i18n/LangProvider'
import { inteiro } from '../format'
import type { CategoriaDeBarras, SerieDeBarras } from './Barras'
import { Legenda } from './Legenda'
import { DefinicoesDePadroes, Marcador, useIdDePadroes } from './padroes'
import { estiloDaSerie } from './paleta'
import { TabelaEquivalente } from './TabelaEquivalente'
import './graficos.css'

export type LinhasProps = { categorias: CategoriaDeBarras[]; series: SerieDeBarras[]; rotulo: string }

const LARGURA = 720
const ALTURA = 240
const M = { esquerda: 36, direita: 12, topo: 12, base: 30 }

/**
 * Linhas por categoria em SVG próprio. Cada série tem cor, traço (contínuo, tracejado...) e marcador de forma em cada ponto;
 * legenda sempre visível e tabela equivalente.
 */
export function Linhas(props: LinhasProps) {
  const { t, lang } = useLang()
  const base = useIdDePadroes()
  const { categorias, series } = props
  const maximo = Math.max(1, ...series.flatMap((s) => s.valores))
  const largura = LARGURA - M.esquerda - M.direita
  const altura = ALTURA - M.topo - M.base
  const x = (i: number) => M.esquerda + (categorias.length <= 1 ? largura / 2 : (i / (categorias.length - 1)) * largura)
  const y = (v: number) => M.topo + altura - (v / maximo) * altura
  const ticks = [...new Set([0, Math.ceil(maximo / 2), maximo])]
  const resumo = t('grafico.resumo')
    .replace('{nome}', props.rotulo)
    .replace('{series}', series.map((s) => s.rotulo).join(', '))
    .replace('{n}', inteiro(categorias.length, lang))
    .replace('{maximo}', inteiro(maximo, lang))
  return (
    <div className="grafico" role="group" aria-label={props.rotulo}>
      <svg width="0" height="0" aria-hidden="true" focusable="false" style={{ position: 'absolute' }}>
        <DefinicoesDePadroes base={base} series={series.length} />
      </svg>
      <Legenda itens={series.map((s) => ({ id: s.id, rotulo: s.rotulo }))} base={base} rotulo={t('grafico.legenda')} />
      <svg viewBox={`0 0 ${LARGURA} ${ALTURA}`} className="grafico-svg" role="img" aria-label={resumo}>
        {ticks.map((v) => (
          <g key={v}>
            <line x1={M.esquerda} x2={LARGURA - M.direita} y1={y(v)} y2={y(v)} className="grafico-grade" />
            <text x={M.esquerda - 6} y={y(v) + 4} textAnchor="end" className="grafico-eixo">
              {inteiro(v, lang)}
            </text>
          </g>
        ))}
        {categorias.map((c, i) => (
          <text key={c.chave} x={x(i)} y={ALTURA - M.base + 16} textAnchor="middle" className="grafico-eixo">
            {c.rotulo}
          </text>
        ))}
        {series.map((s, k) => {
          const e = estiloDaSerie(k)
          const pontos = s.valores.map((v, i) => `${x(i)},${y(v)}`).join(' ')
          return (
            <g key={s.id} data-serie={s.id} data-padrao={e.padrao} data-forma={e.forma}>
              <polyline points={pontos} className="grafico-linha" stroke={e.cor} strokeDasharray={e.tracejado} />
              {s.valores.map((v, i) => (
                <Marcador key={i} forma={e.forma} cx={x(i)} cy={y(v)} r={4} cor={e.cor} />
              ))}
            </g>
          )
        })}
      </svg>
      <TabelaEquivalente
        rotulo={props.rotulo}
        cabecalhos={['', ...series.map((s) => s.rotulo)]}
        linhas={categorias.map((c, i) => [c.rotulo, ...series.map((s) => inteiro(s.valores[i] ?? 0, lang))])}
      />
    </div>
  )
}
