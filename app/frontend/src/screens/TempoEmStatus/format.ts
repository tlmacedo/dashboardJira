import type { Lang } from '../../i18n'

const locale = (lang: Lang) => (lang === 'pt' ? 'pt-BR' : 'en-US')

/** Número no formato do idioma, com casas fixas. */
export function numero(v: number, lang: Lang, casas = 1): string {
  return v.toLocaleString(locale(lang), { minimumFractionDigits: casas, maximumFractionDigits: casas })
}

export function inteiro(v: number, lang: Lang): string {
  return v.toLocaleString(locale(lang), { maximumFractionDigits: 0 })
}

export function percentual(fracao: number, lang: Lang): string {
  return `${Math.round(fracao * 100).toLocaleString(locale(lang))}%`
}

/** yyyy-mm-dd (valor do input date) para dd/mm/aaaa (pt) ou mm/dd/yyyy (en). */
export function dataCurta(iso: string, lang: Lang): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso)
  if (!m) return iso
  return lang === 'pt' ? `${m[3]}/${m[2]}/${m[1]}` : `${m[2]}/${m[3]}/${m[1]}`
}

/** Faixa do tempo mediano por pessoa × status: ≤ 3 d ✓, 3–6 d ●, > 6 d ▲. */
export type Faixa = 'ok' | 'atencao' | 'alto'

export function faixaDe(dias: number): Faixa {
  if (dias <= 3) return 'ok'
  if (dias <= 6) return 'atencao'
  return 'alto'
}

export const SIMBOLO: Record<Faixa, string> = { ok: '✓', atencao: '●', alto: '▲' }
