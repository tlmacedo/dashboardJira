import { describe, expect, it } from 'vitest'
import { neutralizarCelulaCsv, paraCsv } from './csv'

describe('CSV', () => {
  it('neutralizaFormulas', () => {
    for (const perigo of ['=1+1', '+1', '-1', '@x', '\tx', '\rx']) {
      expect(neutralizarCelulaCsv(perigo).startsWith("'")).toBe(true)
    }
    expect(neutralizarCelulaCsv('TG-1')).toBe('TG-1')
  })

  it('escapaAspasESeparador', () => {
    expect(paraCsv([['a;b', 'c"d']])).toBe('"a;b";"c""d"')
  })

  it('neutralizaAposEspacosEAspas', () => {
    for (const perigo of [' =HYPERLINK("x")', '"=cmd"', "'+1", '\t=1', ' =1', '​@x', '  \t-1']) {
      expect(neutralizarCelulaCsv(perigo).startsWith("'")).toBe(true)
    }
    expect(neutralizarCelulaCsv('TG-1 resumo normal')).toBe('TG-1 resumo normal')
    expect(neutralizarCelulaCsv('1,5 dias')).toBe('1,5 dias')
  })

  it('escapeContinuaCorretoDepoisDaNeutralizacao', () => {
    expect(paraCsv([['="a";b']])).toBe('"\'=""a"";b"')
  })
})
