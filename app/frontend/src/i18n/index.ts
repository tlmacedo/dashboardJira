import en from './en.json'
import pt from './pt.json'

export type Lang = 'pt' | 'en'

const dicionarios: Record<Lang, Record<string, string>> = { pt, en }

/** Traduz uma chave; chave ausente devolve a própria chave (nunca quebra a tela). */
export function t(key: string, lang: Lang): string {
  return dicionarios[lang][key] ?? key
}
