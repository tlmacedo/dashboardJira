import { useLang } from '../../i18n/LangProvider'
import { inteiro } from '../format'
import { Legenda } from './Legenda'
import { DefinicoesDePadroes, Marcador, useIdDePadroes } from './padroes'
import { estiloDaSerie } from './paleta'
import { TabelaEquivalente } from './TabelaEquivalente'
import './graficos.css'

export type PontoDeDispersao = { x: number; y: number; rotulo: string }
export type SerieDeDispersao = { id: string; rotulo: string; pontos: PontoDeDispersao[] }
export type DispersaoProps = { series: SerieDeDispersao[]; rotulo: string; rotuloX?: string; rotuloY?: string }

const LARGURA = 720
const ALTURA = 240
const M = { esquerda: 40, direita: 12, topo: 12, base: 30 }

/** Dispersão em SVG próprio: cada série com marcador de forma próprio, legenda visível e tabela equivalente. */
export function Dispersao(props: DispersaoProps) {
  const { t, lang } = useLang()
  const base = useIdDePadroes()
  const todos = props.series.flatMap((s) => s.pontos)
  const maxX = Math.max(1, ...todos.map((p) => p.x))
  const maxY = Math.max(1, ...todos.map((p) => p.y))
  const x = (v: number) => M.esquerda + (v / maxX) * (LARGURA - M.esquerda - M.direita)
  const y = (v: number) => M.topo + (ALTURA - M.topo - M.base) - (v / maxY) * (ALTURA - M.topo - M.base)
  const resumo = t('grafico.resumo')
    .replace('{nome}', props.rotulo)
    .replace('{series}', props.series.map((s) => s.rotulo).join(', '))
    .replace('{n}', inteiro(todos.length, lang))
    .replace('{maximo}', inteiro(maxY, lang))
  return (
    <div className="grafico" role="group" aria-label={props.rotulo}>
      <svg width="0" height="0" aria-hidden="true" focusable="false" style={{ position: 'absolute' }}>
        <DefinicoesDePadroes base={base} series={props.series.length} />
      </svg>
      <Legenda itens={props.series.map((s) => ({ id: s.id, rotulo: s.rotulo }))} base={base} rotulo={t('grafico.legenda')} />
      <svg viewBox={`0 0 ${LARGURA} ${ALTURA}`} className="grafico-svg" role="img" aria-label={resumo}>
        {[0, maxY].map((v) => (
          <g key={v}>
            <line x1={M.esquerda} x2={LARGURA - M.direita} y1={y(v)} y2={y(v)} className="grafico-grade" />
            <text x={M.esquerda - 6} y={y(v) + 4} textAnchor="end" className="grafico-eixo">
              {inteiro(v, lang)}
            </text>
          </g>
        ))}
        {[0, maxX].map((v) => (
          <text key={v} x={x(v)} y={ALTURA - M.base + 16} textAnchor="middle" className="grafico-eixo">
            {inteiro(v, lang)}
          </text>
        ))}
        {props.series.map((s, k) => {
          const e = estiloDaSerie(k)
          return (
            <g key={s.id} data-serie={s.id} data-padrao={e.padrao} data-forma={e.forma}>
              {s.pontos.map((p, i) => (
                <Marcador key={i} forma={e.forma} cx={x(p.x)} cy={y(p.y)} r={4} cor={e.cor} />
              ))}
            </g>
          )
        })}
      </svg>
      <TabelaEquivalente
        rotulo={props.rotulo}
        cabecalhos={['', props.rotuloX ?? 'x', props.rotuloY ?? 'y']}
        linhas={props.series.flatMap((s) => s.pontos.map((p) => [`${s.rotulo} ${p.rotulo}`, inteiro(p.x, lang), inteiro(p.y, lang)]))}
      />
    </div>
  )
}
