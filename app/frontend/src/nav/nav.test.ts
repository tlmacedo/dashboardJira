import { describe, expect, it } from 'vitest'
import { t } from '../i18n'
import { visibleNav } from './nav'

const ids = (roles: Parameters<typeof visibleNav>[0]) =>
  visibleNav(roles).flatMap((g) => g.items.map((i) => i.id))

describe('menu por papel', () => {
  it('usuarioSoVeMeuPainel', () => {
    const vistos = ids(['USUARIO'])
    expect(vistos).toContain('meu')
    expect(vistos).not.toContain('auditoria')
    expect(vistos).not.toContain('integridade')
  })

  it('integridadeSoParaGerente', () => {
    expect(ids(['GERENTE'])).toContain('integridade')
    expect(ids(['COORDENADOR'])).toContain('auditoria')
    expect(ids(['COORDENADOR'])).not.toContain('integridade')
    expect(ids(['SQUAD_LEADER'])).not.toContain('integridade')
    expect(ids(['USUARIO'])).not.toContain('integridade')
  })

  it('gruposVaziosOmitidos', () => {
    const grupos = visibleNav(['USUARIO']).map((g) => g.id)
    expect(grupos).not.toContain('auditoria')
    expect(grupos).not.toContain('governanca')
    expect(grupos).not.toContain('acao')
    expect(visibleNav(['USUARIO']).every((g) => g.items.length > 0)).toBe(true)
    // o gerente vê os 8 grupos do plano visual
    expect(visibleNav(['GERENTE'])).toHaveLength(8)
  })
})

describe('tradução', () => {
  it('chaveAusenteDevolveAChave', () => {
    expect(t('nao.existe', 'pt')).toBe('nao.existe')
    expect(t('nao.existe', 'en')).toBe('nao.existe')
  })

  it('traduz nos dois idiomas', () => {
    expect(t('nav.meu', 'pt')).toBe('Meu painel')
    expect(t('nav.meu', 'en')).toBe('My dashboard')
  })
})
