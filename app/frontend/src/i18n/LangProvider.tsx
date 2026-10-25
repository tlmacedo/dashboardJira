import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { gravarPreferencia, lerPreferencia } from '../storage'
import { t as traduzir, type Lang } from './index'

const CHAVE = 'painel.idioma'

type Ctx = { lang: Lang; setLang: (l: Lang) => void; t: (key: string) => string }

const LangContext = createContext<Ctx | null>(null)

function idiomaInicial(): Lang {
  const salvo = lerPreferencia(CHAVE)
  return salvo === 'en' || salvo === 'pt' ? salvo : 'pt' // padrão pt-BR
}

export function LangProvider({ children }: { children: ReactNode }) {
  const [lang, setLangEstado] = useState<Lang>(idiomaInicial)

  useEffect(() => {
    document.documentElement.lang = lang === 'pt' ? 'pt-BR' : 'en'
  }, [lang])

  const setLang = useCallback((l: Lang) => {
    setLangEstado(l)
    gravarPreferencia(CHAVE, l)
  }, [])

  const valor = useMemo<Ctx>(() => ({ lang, setLang, t: (key) => traduzir(key, lang) }), [lang, setLang])
  return <LangContext.Provider value={valor}>{children}</LangContext.Provider>
}

export function useLang(): Ctx {
  const ctx = useContext(LangContext)
  if (!ctx) throw new Error('useLang fora do LangProvider')
  return ctx
}
