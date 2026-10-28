import { useId } from 'react'
import { useLang } from '../../i18n/LangProvider'
import { dataCurta, inteiro } from '../../shared/format'
import type { SemanaDeFluxo } from './api'
import { SERIES_DO_FLUXO, type SerieDoFluxo } from './series'
import './fluxo.css'

export type GraficoDeFluxoProps = {
  semanas: SemanaDeFluxo[]
  /** Presente = a barra com valor é um botão que abre as tasks (segmento `<serie>:<semana>`). */
  aoAbrir?: (serie: SerieDoFluxo['id'], semana: string, titulo: string) => void
}

const LARGURA = 720
const ALTURA = 240
const MARGEM = { esquerda: 36, direita: 8, topo: 12, base: 34 }

/** "05/10" (pt) ou "10/05" (en): o rótulo curto do eixo. */
function semanaCurta(iso: string, lang: 'pt' | 'en'): string {
  return dataCurta(iso, lang).split('/').slice(0, 2).join('/')
}

/**
 * Barras agrupadas por semana (entrou, começou, saiu) em SVG próprio. Cada barra com valor é um botão com rótulo
 * ("Entraram 05/10/2026: 3"); a semana sem tasks não tem barra clicável. O padrão (cheio, vazado, hachurado) diferencia as
 * séries além da cor. A tabela equivalente fica ao lado, na tela.
 */
export function GraficoDeFluxo({ semanas, aoAbrir }: GraficoDeFluxoProps) {
  const { t, lang } = useLang()
  const idDoPadrao = `fx-hachura-${useId().replace(/[^a-zA-Z0-9]/g, '')}`
  const maximo = Math.max(1, ...semanas.flatMap((s) => [s.entrou, s.comecou, s.saiu]))
  const largura = LARGURA - MARGEM.esquerda - MARGEM.direita
  const altura = ALTURA - MARGEM.topo - MARGEM.base
  const passo = largura / Math.max(1, semanas.length)
  const barra = Math.min(18, (passo * 0.8) / SERIES_DO_FLUXO.length)
  const grupo = barra * SERIES_DO_FLUXO.length
  const ticks = [...new Set([0, Math.ceil(maximo / 2), maximo])]
  const y = (v: number) => MARGEM.topo + altura - (v / maximo) * altura

  return (
    <div className="fluxo-grafico" role="group" aria-label={t('fluxo.grafico.rotulo')}>
      <ul className="fluxo-legenda" aria-label={t('fluxo.legenda')}>
        {SERIES_DO_FLUXO.map((s) => (
          <li key={s.id}>
            <svg width="16" height="12" aria-hidden="true">
              <rect
                data-padrao={s.padrao} x="1" y="1" width="14" height="10" className={`fluxo-barra fluxo-barra--${s.padrao}`}
                fill={s.padrao === 'hachurado' ? `url(#${idDoPadrao})` : undefined}
              />
            </svg>
            {t(s.rotulo)}
          </li>
        ))}
      </ul>
      <svg viewBox={`0 0 ${LARGURA} ${ALTURA}`} className="fluxo-svg" preserveAspectRatio="xMidYMid meet" role="presentation">
        <defs>
          <pattern id={idDoPadrao} width="5" height="5" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
            <rect width="5" height="5" className="fluxo-fundo" />
            <line x1="0" y1="0" x2="0" y2="5" className="fluxo-hachura" />
          </pattern>
        </defs>
        {ticks.map((v) => (
          <g key={v}>
            <line x1={MARGEM.esquerda} x2={LARGURA - MARGEM.direita} y1={y(v)} y2={y(v)} className="fluxo-grade" />
            <text x={MARGEM.esquerda - 6} y={y(v) + 4} textAnchor="end" className="fluxo-eixo">
              {inteiro(v, lang)}
            </text>
          </g>
        ))}
        {semanas.map((s, i) => {
          const x0 = MARGEM.esquerda + i * passo + (passo - grupo) / 2
          return (
            <g key={s.semana}>
              {SERIES_DO_FLUXO.map((serie, k) => {
                const valor = s[serie.campo]
                const x = x0 + k * barra
                const altBarra = (valor / maximo) * altura
                const rotulo = `${t(serie.rotulo)} ${dataCurta(s.semana, lang)}: ${valor}`
                const forma = (
                  <rect
                    x={x} y={y(valor)} width={Math.max(1, barra - 2)} height={altBarra} data-padrao={serie.padrao}
                    className={`fluxo-barra fluxo-barra--${serie.padrao}`}
                    fill={serie.padrao === 'hachurado' ? `url(#${idDoPadrao})` : undefined}
                  />
                )
                if (valor === 0) return <g key={serie.id}>{forma}</g>
                if (!aoAbrir) {
                  return (
                    <g key={serie.id} role="img" aria-label={rotulo}>
                      {forma}
                    </g>
                  )
                }
                const abrir = () => aoAbrir(serie.id, s.semana, `${t(serie.rotulo)}: ${dataCurta(s.semana, lang)}`)
                return (
                  <g
                    key={serie.id} role="button" tabIndex={0} aria-label={rotulo} className="fluxo-botao" onClick={abrir}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault()
                        abrir()
                      }
                    }}
                  >
                    {forma}
                  </g>
                )
              })}
              <text x={x0 + grupo / 2} y={ALTURA - MARGEM.base + 14} textAnchor="middle" className="fluxo-eixo">
                {semanaCurta(s.semana, lang)}
              </text>
              {s.emCurso && (
                <text x={x0 + grupo / 2} y={ALTURA - MARGEM.base + 26} textAnchor="middle" className="fluxo-eixo">
                  {t('fluxo.emCurso')}
                </text>
              )}
            </g>
          )
        })}
      </svg>
    </div>
  )
}
