import { cleanup, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { ThemeProvider } from '../theme/ThemeProvider'
import { renderizarTela } from '../test/telas'
import { Menu } from './Menu'

beforeEach(() => localStorage.clear())
afterEach(() => cleanup())

const menu = (bloqueados?: string[]) => (
  <ThemeProvider>
    <Menu role="GERENTE" modulosBloqueados={bloqueados} />
  </ThemeProvider>
)

describe('Menu e a trava do DPO', () => {
  it('itemBloqueadoMostraPausaETexto', () => {
    renderizarTela(menu(['AUDITORIA', 'INTEGRIDADE']), '/central')
    for (const nome of ['Alterações de data', 'Integridade']) {
      const link = screen.getByRole('link', { name: new RegExp(nome) })
      // O símbolo não é o único sinal: o texto também diz que aguarda o aceite.
      expect(link).toHaveTextContent('⏸')
      expect(link).toHaveTextContent('aguardando aceite do DPO')
    }
    // A Divergência é do módulo Auditoria; outras telas não são afetadas.
    expect(screen.getByRole('link', { name: /Divergência/ })).toHaveTextContent('aguardando aceite do DPO')
    expect(screen.getByRole('link', { name: /Central/ })).not.toHaveTextContent('⏸')
  })

  it('itemBloqueadoEmIngles', () => {
    localStorage.setItem('painel.idioma', 'en')
    renderizarTela(menu(['INTEGRIDADE']), '/central')
    expect(screen.getByRole('link', { name: /Integrity/ })).toHaveTextContent('awaiting DPO approval')
  })

  it('soOModuloBloqueadoFicaMarcado', () => {
    renderizarTela(menu(['INTEGRIDADE']), '/central')
    expect(screen.getByRole('link', { name: /Integridade/ })).toHaveTextContent('⏸')
    expect(screen.getByRole('link', { name: /Alterações de data/ })).not.toHaveTextContent('⏸')
  })

  it('semBloqueioMenuNormal', () => {
    renderizarTela(menu(), '/central')
    expect(document.body.textContent ?? '').not.toContain('⏸')
    expect(document.body.textContent ?? '').not.toContain('aguardando aceite do DPO')
  })
})
