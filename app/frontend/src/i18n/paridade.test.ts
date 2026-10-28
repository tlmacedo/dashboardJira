import { describe, expect, it } from 'vitest'
import en from './en.json'
import pt from './pt.json'

describe('i18n', () => {
  it('paridadeDeChavesPtEn', () => {
    const chavesPt = Object.keys(pt).sort()
    const chavesEn = Object.keys(en).sort()
    expect(chavesPt.filter((k) => !(k in en))).toEqual([])
    expect(chavesEn.filter((k) => !(k in pt))).toEqual([])
  })

  it('nenhumaTraducaoVazia', () => {
    const vazias = [...Object.entries(pt), ...Object.entries(en)].filter(([, v]) => v.trim() === '').map(([k]) => k)
    // Só rótulos que existem de propósito sem texto poderiam entrar aqui; hoje nenhum.
    expect(vazias).toEqual([])
  })

  it('marcadoresIguaisNosDoisIdiomas', () => {
    const marcadores = (s: string) => (s.match(/\{\w+\}/g) ?? []).sort().join(',')
    const diferentes = Object.keys(pt).filter((k) => k in en && marcadores((pt as Record<string, string>)[k]) !== marcadores((en as Record<string, string>)[k]))
    expect(diferentes).toEqual([])
  })
})
