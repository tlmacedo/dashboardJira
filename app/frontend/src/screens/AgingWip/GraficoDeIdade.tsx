import { useLang } from '../../i18n/LangProvider'
import { inteiro, numero } from '../../shared/format'
import type { Etapa } from './api'
import './aging.css'

export type GraficoDeIdadeProps = {
  etapas: Etapa[]
  /** "dias úteis" ou "dias corridos", já traduzido (eixo e rótulo de cada ponto). */
  unidade: string
}

const LARGURA = 720
const ROTULO = 150
const DIREITA = 16
const TOPO = 8
const EIXO = 30
const RAIO = 5
const PISTA = 13

/** Situação textual de um ponto: nunca só a cor ou a forma. */
function situacaoDoPonto(etapa: Etapa, acima: boolean): 'acima' | 'normal' | 'semFaixa' {
  if (!etapa.faixa) return 'semFaixa'
  return acima ? 'acima' : 'normal'
}

/**
 * Gráfico de pontos por etapa em SVG próprio: uma linha por etapa, a faixa histórica (P50 a P85) como banda quando a
 * etapa tem amostra, e UM ponto por task na posição da idade. Dentro do normal é círculo; acima do P85 é triângulo (forma
 * diferente, além da cor) e o rótulo do ponto diz "acima do normal". Pontos muito próximos sobem para pistas vizinhas. Sem
 * nome de pessoa; a tabela equivalente fica na tela.
 */
export function GraficoDeIdade({ etapas, unidade }: GraficoDeIdadeProps) {
  const { t, lang } = useLang()
  const maximoBruto = Math.max(1, ...etapas.flatMap((e) => [...e.pontos.map((p) => p.idade), e.faixa?.p85 ?? 0]))
  const passo = Math.max(1, Math.ceil(maximoBruto / 5))
  const maximo = passo * Math.ceil(maximoBruto / passo)
  const area = LARGURA - ROTULO - DIREITA
  const x = (v: number) => ROTULO + (v / maximo) * area

  // Pista de cada ponto: o primeiro livre perto o bastante para não sobrepor o anterior (já ordenados do mais velho ao novo).
  const linhas = etapas.map((e) => {
    const ocupadas: number[][] = []
    const pontos = [...e.pontos].sort((a, b) => a.idade - b.idade).map((p) => {
      let pista = 0
      while ((ocupadas[pista] ?? []).some((px) => Math.abs(px - x(p.idade)) < RAIO * 2 + 2)) pista++
      ;(ocupadas[pista] ??= []).push(x(p.idade))
      return { ...p, pista }
    })
    const pistas = Math.max(1, ...pontos.map((p) => p.pista + 1))
    return { etapa: e, pontos, altura: 26 + pistas * PISTA }
  })
  const alturaTotal = linhas.reduce((s, l) => s + l.altura, 0)
  const ticks = Array.from({ length: Math.floor(maximo / passo) + 1 }, (_, i) => i * passo)
  const topos = linhas.map((_, i) => TOPO + linhas.slice(0, i).reduce((soma, l) => soma + l.altura, 0))

  return (
    <div className="aging-grafico" role="group" aria-label={t('aging.grafico.rotulo')}>
      <ul className="aging-legenda" aria-label={t('fluxo.legenda')}>
        <li>
          <svg width="14" height="14" aria-hidden="true"><circle data-forma="circulo" cx="7" cy="7" r="5" className="aging-ponto" /></svg>
          {t('aging.legenda.normal')}
        </li>
        <li>
          <svg width="14" height="14" aria-hidden="true"><polygon data-forma="triangulo" points="7,1 13,13 1,13" className="aging-ponto aging-ponto--acima" /></svg>
          {t('aging.legenda.acima')}
        </li>
        <li>
          <svg width="22" height="14" aria-hidden="true"><rect x="1" y="3" width="20" height="8" className="aging-faixa" /></svg>
          {t('aging.legenda.faixa')}
        </li>
      </ul>
      <svg viewBox={`0 0 ${LARGURA} ${alturaTotal + TOPO + EIXO}`} className="aging-svg" role="presentation">
        {ticks.map((v) => (
          <g key={v}>
            <line x1={x(v)} x2={x(v)} y1={TOPO} y2={TOPO + alturaTotal} className="aging-grade" />
            <text x={x(v)} y={TOPO + alturaTotal + 14} textAnchor="middle" className="aging-eixo">{inteiro(v, lang)}</text>
          </g>
        ))}
        <text x={x(maximo / 2)} y={TOPO + alturaTotal + 27} textAnchor="middle" className="aging-eixo">
          {t('aging.eixo').replace('{unidade}', unidade)}
        </text>
        {linhas.map(({ etapa, pontos, altura }, i) => {
          const y0 = topos[i]
          const meio = y0 + altura / 2
          return (
            <g key={etapa.status}>
              <line x1={0} x2={LARGURA} y1={y0 + altura} y2={y0 + altura} className="aging-divisoria" />
              <text x={ROTULO - 8} y={meio - 2} textAnchor="end" className="aging-etapa">{etapa.status}</text>
              <text x={ROTULO - 8} y={meio + 11} textAnchor="end" className="aging-eixo">
                {t(`aging.cat.${etapa.categoria}`)} · {etapa.total}
              </text>
              {etapa.faixa && (
                <g data-faixa={etapa.status}>
                  <rect
                    x={x(etapa.faixa.p50)} y={y0 + 4} width={Math.max(2, x(etapa.faixa.p85) - x(etapa.faixa.p50))} height={altura - 8}
                    className="aging-faixa"
                  />
                  <line x1={x(etapa.faixa.p50)} x2={x(etapa.faixa.p50)} y1={y0 + 4} y2={y0 + altura - 4} className="aging-marca" />
                  <line x1={x(etapa.faixa.p85)} x2={x(etapa.faixa.p85)} y1={y0 + 4} y2={y0 + altura - 4} className="aging-marca" />
                </g>
              )}
              {pontos.map((p) => {
                const cx = x(p.idade)
                const cy = y0 + altura - 10 - p.pista * PISTA
                const situacao = situacaoDoPonto(etapa, p.acima)
                const rotulo = `${p.key}: ${numero(p.idade, lang)} ${unidade} ${t('aging.em')} ${etapa.status}, ${t(`aging.situacao.${situacao}`)}`
                return (
                  <g key={p.key} role="img" aria-label={rotulo}>
                    <title>{rotulo}</title>
                    {p.acima ? (
                      <polygon
                        data-forma="triangulo" points={`${cx},${cy - RAIO - 1} ${cx + RAIO + 1},${cy + RAIO} ${cx - RAIO - 1},${cy + RAIO}`}
                        className="aging-ponto aging-ponto--acima"
                      />
                    ) : (
                      <circle data-forma="circulo" cx={cx} cy={cy} r={RAIO} className="aging-ponto" />
                    )}
                  </g>
                )
              })}
            </g>
          )
        })}
      </svg>
    </div>
  )
}
