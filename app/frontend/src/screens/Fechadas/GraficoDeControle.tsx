import { useLang } from '../../i18n/LangProvider'
import { dataCurta, inteiro, numero } from '../../shared/format'
import type { SerieDoTipo } from './controleApi'
import './controle.css'

export type GraficoDeControleProps = {
  serie: SerieDoTipo
  /** "dias úteis" ou "dias corridos", já traduzido. */
  unidade: string
  /** Período do cartão (aaaa-mm-dd), o domínio do eixo do tempo. */
  de: string
  ate: string
  /** Presente = clicar no ponto abre as tasks do dia e do tipo. */
  aoAbrirDia?: (dia: string) => void
}

const LARGURA = 720
const ALTURA = 260
const M = { esquerda: 40, direita: 70, topo: 12, base: 34 }

const dias = (iso: string) => {
  const [a, m, d] = iso.split('-').map(Number)
  return Date.UTC(a, m - 1, d) / 86_400_000
}

/**
 * Gráfico de controle do ciclo em SVG próprio: um ponto por task concluída (dia de conclusão x dias de ciclo), a média
 * móvel como linha cheia e as faixas P50 e P85 do tipo como linhas tracejadas com rótulo. Dentro do normal é círculo;
 * outlier (ciclo acima do limite em dias corridos) é losango. O ponto não mostra pessoa nem chave; o clique abre as tasks do
 * dia. Os pontos são do mouse: o equivalente por teclado e leitor de tela é a tabela de pontos ao lado.
 */
export function GraficoDeControle({ serie, unidade, de, ate, aoAbrirDia }: GraficoDeControleProps) {
  const { t, lang } = useLang()
  const inicio = dias(de)
  const fim = Math.max(dias(ate), inicio + 1)
  const maximoBruto = Math.max(1, ...serie.pontos.map((p) => p.ciclo), serie.p85 ?? 0)
  const passo = Math.max(1, Math.ceil(maximoBruto / 4))
  const maximo = passo * Math.ceil(maximoBruto / passo)
  const largura = LARGURA - M.esquerda - M.direita
  const altura = ALTURA - M.topo - M.base
  const x = (iso: string) => M.esquerda + ((dias(iso) - inicio) / (fim - inicio)) * largura
  const y = (v: number) => M.topo + altura - (v / maximo) * altura
  const ticks = Array.from({ length: Math.floor(maximo / passo) + 1 }, (_, i) => i * passo)
  const media = serie.pontos.filter((p) => p.mediaMovel != null).map((p) => `${x(p.dia)},${y(p.mediaMovel as number)}`)

  return (
    <div className="controle-grafico" role="group" aria-label={t('controle.rotulo.grafico')}>
      <ul className="controle-legenda" aria-label={t('fluxo.legenda')}>
        <li>
          <svg width="14" height="14" aria-hidden="true"><circle cx="7" cy="7" r="5" className="controle-ponto" /></svg>
          {t('controle.legenda.ponto')}
        </li>
        <li>
          <svg width="14" height="14" aria-hidden="true"><polygon points="7,1 13,7 7,13 1,7" className="controle-ponto controle-ponto--outlier" /></svg>
          {t('controle.legenda.outlier')}
        </li>
        <li>
          <svg width="22" height="14" aria-hidden="true"><line x1="1" x2="21" y1="7" y2="7" className="controle-media" /></svg>
          {t('controle.legenda.media')}
        </li>
        <li>
          <svg width="22" height="14" aria-hidden="true"><line x1="1" x2="21" y1="7" y2="7" className="controle-faixa" /></svg>
          {t('controle.legenda.faixa')}
        </li>
      </ul>
      <svg viewBox={`0 0 ${LARGURA} ${ALTURA}`} className="controle-svg" aria-hidden="true">
        {ticks.map((v) => (
          <g key={v}>
            <line x1={M.esquerda} x2={LARGURA - M.direita} y1={y(v)} y2={y(v)} className="controle-grade" />
            <text x={M.esquerda - 6} y={y(v) + 4} textAnchor="end" className="controle-eixo">{inteiro(v, lang)}</text>
          </g>
        ))}
        <text x={M.esquerda} y={ALTURA - 8} className="controle-eixo">{dataCurta(de, lang)}</text>
        <text x={LARGURA - M.direita} y={ALTURA - 8} textAnchor="end" className="controle-eixo">{dataCurta(ate, lang)}</text>
        <text x={LARGURA / 2} y={ALTURA - 8} textAnchor="middle" className="controle-eixo">{t('controle.eixo').replace('{unidade}', unidade)}</text>
        {serie.p50 != null && (
          <g>
            <line x1={M.esquerda} x2={LARGURA - M.direita} y1={y(serie.p50)} y2={y(serie.p50)} className="controle-faixa" />
            <text x={LARGURA - M.direita + 4} y={y(serie.p50) + 4} className="controle-rotulo-faixa">{`P50 ${numero(serie.p50, lang)}`}</text>
          </g>
        )}
        {serie.p85 != null && (
          <g>
            <line x1={M.esquerda} x2={LARGURA - M.direita} y1={y(serie.p85)} y2={y(serie.p85)} className="controle-faixa controle-faixa--p85" />
            <text x={LARGURA - M.direita + 4} y={y(serie.p85) - 3} className="controle-rotulo-faixa">{`P85 ${numero(serie.p85, lang)}`}</text>
          </g>
        )}
        {media.length > 1 && <polyline points={media.join(' ')} className="controle-media" />}
        {serie.pontos.map((p, i) => {
          const cx = x(p.dia)
          const cy = y(p.ciclo)
          const rotulo = `${dataCurta(p.dia, lang)}: ${numero(p.ciclo, lang)} ${unidade}${p.outlier ? ` (${t('controle.outlier')})` : ''}`
          const abrir = aoAbrirDia ? () => aoAbrirDia(p.dia) : undefined
          return p.outlier ? (
            <polygon
              key={i} data-dia={p.dia} data-forma="losango" points={`${cx},${cy - 6} ${cx + 6},${cy} ${cx},${cy + 6} ${cx - 6},${cy}`}
              className="controle-ponto controle-ponto--outlier" onClick={abrir}
            >
              <title>{rotulo}</title>
            </polygon>
          ) : (
            <circle key={i} data-dia={p.dia} data-forma="circulo" cx={cx} cy={cy} r={4.5} className="controle-ponto" onClick={abrir}>
              <title>{rotulo}</title>
            </circle>
          )
        })}
      </svg>
    </div>
  )
}
