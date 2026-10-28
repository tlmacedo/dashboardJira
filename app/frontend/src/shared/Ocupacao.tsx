import { useLang } from '../i18n/LangProvider'
import { numero, percentual } from './format'
import './pessoas.css'

/** Ocupação como o backend entrega (`OcupacaoDto`): `fracao` nula e `semCapacidade` verdadeiro quando a capacidade é zero. */
export type OcupacaoDto = { fracao: number | null; semCapacidade: boolean; estado: 'ok' | 'atencao' | 'problema' | string }

const SIMBOLO: Record<string, string> = { ok: '✓', atencao: '●', problema: '▲' }

/**
 * Ocupação sempre com NÚMERO e SÍMBOLO (nunca só cor): `✓ 25%`, `● 90%`, `▲ 150%`. Capacidade zero é o TEXTO "sem capacidade",
 * nunca uma porcentagem, com as horas planejadas ao lado quando há demanda ([demandaH]). O estado também vai em texto para
 * o leitor de tela.
 */
export function Ocupacao(props: { o: OcupacaoDto; demandaH?: number; compacta?: boolean }) {
  const { t, lang } = useLang()
  const { o } = props
  const simbolo = o.semCapacidade && o.estado === 'ok' ? '■' : (SIMBOLO[o.estado] ?? '■')
  const estado = t(`pessoas.estado.${o.estado}`)
  if (o.semCapacidade || o.fracao === null) {
    const horas = props.demandaH && props.demandaH > 0 ? ` (${t('pessoas.semCapacidade.horas').replace('{h}', numero(props.demandaH, lang))})` : ''
    return (
      <span className={`ocupacao ocupacao--${o.estado}`}>
        <span aria-hidden="true">{simbolo}</span> {t('pessoas.semCapacidade')}
        {!props.compacta && horas}
        <span className="so-leitor"> ({estado})</span>
      </span>
    )
  }
  return (
    <span className={`ocupacao ocupacao--${o.estado}`}>
      <span aria-hidden="true">{simbolo}</span> {percentual(o.fracao, lang)}
      <span className="so-leitor"> ({estado})</span>
    </span>
  )
}
