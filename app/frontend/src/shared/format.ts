import type { Lang } from '../i18n'

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

/**
 * Data e hora curtas de um instante ISO (UTC) NO FUSO DE MANAUS, qualquer que seja o fuso do navegador: os filtros de período
 * (`de`/`ate`) são dias de Manaus, então a hora mostrada tem de ser a mesma régua.
 */
export function instanteCurto(iso: string, lang: Lang): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleString(locale(lang), { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', timeZone: 'America/Manaus' }).replace(/ /g, ' ')
}

/** yyyy-mm-dd (valor do input date) para dd/mm/aaaa (pt) ou mm/dd/yyyy (en). */
export function dataCurta(iso: string, lang: Lang): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso)
  if (!m) return iso
  return lang === 'pt' ? `${m[3]}/${m[2]}/${m[1]}` : `${m[2]}/${m[3]}/${m[1]}`
}
