import { describe, expect, it } from 'vitest'
import { instanteCurto } from './format'

describe('instanteCurto', () => {
  it('mostraAHoraDeManausQualquerQuefosoDoNavegador', () => {
    // 03:30 UTC de 30/10 é 23:30 de 29/10 em Manaus (UTC-4): o dia e a hora são os do filtro de período.
    const pt = instanteCurto('2026-10-30T03:30:00Z', 'pt')
    expect(pt).toContain('29/10/2026')
    expect(pt).toContain('23:30')
    const en = instanteCurto('2026-10-30T03:30:00Z', 'en')
    expect(en).toContain('10/29/2026')
  })

  it('textoIlegivelVoltaComoVeio', () => {
    expect(instanteCurto('lixo', 'pt')).toBe('lixo')
  })
})
