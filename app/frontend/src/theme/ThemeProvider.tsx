import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { gravarPreferencia, lerPreferencia } from '../storage'

export type Theme = 'light' | 'dark' | 'sidia'

const CHAVE = 'painel.tema'
export const TEMAS: Theme[] = ['light', 'dark', 'sidia']

type Ctx = { theme: Theme; setTheme: (t: Theme) => void }

const ThemeContext = createContext<Ctx | null>(null)

function temaInicial(): Theme {
  const salvo = lerPreferencia(CHAVE)
  if (salvo === 'light' || salvo === 'dark' || salvo === 'sidia') return salvo
  // Sem escolha salva: segue prefers-color-scheme (claro/escuro). "Sidia" só por escolha.
  try {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  } catch {
    return 'light'
  }
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeEstado] = useState<Theme>(temaInicial)

  // O tema vive no atributo data-theme da raiz; os tokens CSS reagem a ele.
  useEffect(() => {
    document.documentElement.dataset.theme = theme
  }, [theme])

  const setTheme = useCallback((t: Theme) => {
    setThemeEstado(t)
    gravarPreferencia(CHAVE, t)
  }, [])

  const valor = useMemo(() => ({ theme, setTheme }), [theme, setTheme])
  return <ThemeContext.Provider value={valor}>{children}</ThemeContext.Provider>
}

export function useTheme(): Ctx {
  const ctx = useContext(ThemeContext)
  if (!ctx) throw new Error('useTheme fora do ThemeProvider')
  return ctx
}
