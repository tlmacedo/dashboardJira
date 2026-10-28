import { useLang } from '../../i18n/LangProvider'
import { inteiro } from '../../shared/format'
import type { MesDoCiclo } from './controleApi'
import { mesCurto } from './meses'
import './controle.css'

const LARGURA = 720
const ALTURA = 200
const M = { esquerda: 40, direita: 12, topo: 12, base: 30 }

/** Segmentos contínuos de uma série com buracos (mês sem task quebra a linha). */
function segmentos(valores: Array<{ x: number; y: number } | null>): string[] {
  const saida: string[] = []
  let atual: string[] = []
  for (const v of valores) {
    if (v) atual.push(`${v.x},${v.y}`)
    else if (atual.length > 0) {
      saida.push(atual.join(' '))
      atual = []
    }
  }
  if (atual.length > 0) saida.push(atual.join(' '))
  return saida.filter((s) => s.includes(' '))
}

/**
 * Tendência mensal do ciclo em SVG próprio: P50 (círculo, linha cheia) e P85 (quadrado, linha tracejada) por mês. Mês com
 * poucos itens leva o marcador vazado (`data-poucos`) e o aviso na tabela; mês sem task quebra a linha. A tabela vem ao lado.
 */
export function GraficoDeTendencia({ meses, unidade }: { meses: MesDoCiclo[]; unidade: string }) {
  const { t, lang } = useLang()
  const maximoBruto = Math.max(1, ...meses.map((m) => m.p85 ?? 0))
  const passo = Math.max(1, Math.ceil(maximoBruto / 4))
  const maximo = passo * Math.ceil(maximoBruto / passo)
  const largura = LARGURA - M.esquerda - M.direita
  const altura = ALTURA - M.topo - M.base
  const passoX = largura / Math.max(1, meses.length)
  const x = (i: number) => M.esquerda + passoX * (i + 0.5)
  const y = (v: number) => M.topo + altura - (v / maximo) * altura
  const ticks = Array.from({ length: Math.floor(maximo / passo) + 1 }, (_, i) => i * passo)
  const p50 = meses.map((m, i) => (m.p50 == null ? null : { x: x(i), y: y(m.p50) }))
  const p85 = meses.map((m, i) => (m.p85 == null ? null : { x: x(i), y: y(m.p85) }))

  return (
    <div className="controle-grafico" role="group" aria-label={t('controle.rotulo.tendencia')}>
      <ul className="controle-legenda" aria-label={t('fluxo.legenda')}>
        <li>
          <svg width="22" height="14" aria-hidden="true"><line x1="1" x2="21" y1="7" y2="7" className="controle-linha-p50" /><circle cx="11" cy="7" r="3.5" className="controle-p50" /></svg>
          P50
        </li>
        <li>
          <svg width="22" height="14" aria-hidden="true"><line x1="1" x2="21" y1="7" y2="7" className="controle-linha-p85" /><rect x="7.5" y="3.5" width="7" height="7" className="controle-p85" /></svg>
          P85
        </li>
        <li>
          <svg width="22" height="14" aria-hidden="true"><circle cx="11" cy="7" r="3.5" className="controle-p50 controle-vazado" /></svg>
          {t('controle.legenda.poucos')}
        </li>
      </ul>
      <svg viewBox={`0 0 ${LARGURA} ${ALTURA}`} className="controle-svg" aria-hidden="true">
        {ticks.map((v) => (
          <g key={v}>
            <line x1={M.esquerda} x2={LARGURA - M.direita} y1={y(v)} y2={y(v)} className="controle-grade" />
            <text x={M.esquerda - 6} y={y(v) + 4} textAnchor="end" className="controle-eixo">{inteiro(v, lang)}</text>
          </g>
        ))}
        <text x={M.esquerda} y={ALTURA - 4} className="controle-eixo">{t('controle.eixoMeses').replace('{unidade}', unidade)}</text>
        {segmentos(p50).map((pts) => <polyline key={`a${pts}`} points={pts} className="controle-linha-p50" />)}
        {segmentos(p85).map((pts) => <polyline key={`b${pts}`} points={pts} className="controle-linha-p85" />)}
        {meses.map((m, i) => (
          <g key={m.mes}>
            <text x={x(i)} y={ALTURA - M.base + 14} textAnchor="middle" className="controle-eixo">{mesCurto(m.mes).slice(0, 2)}</text>
            {m.p50 != null && (
              <circle
                cx={x(i)} cy={y(m.p50)} r={4} data-poucos={m.amostraPequena ? 'true' : 'false'}
                className={`controle-p50${m.amostraPequena ? ' controle-vazado' : ''}`}
              />
            )}
            {m.p85 != null && (
              <rect
                x={x(i) - 3.5} y={y(m.p85) - 3.5} width={7} height={7} data-poucos={m.amostraPequena ? 'true' : 'false'}
                className="controle-p85"
              />
            )}
          </g>
        ))}
      </svg>
    </div>
  )
}
