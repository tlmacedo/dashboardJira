import { act, cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { avisarSeSessaoExpirou } from '../api/client'
import { LangProvider } from '../i18n/LangProvider'
import { SessaoExpirada } from './SessaoExpirada'

function renderizar(devMode = false) {
  return render(
    <LangProvider>
      <SessaoExpirada devMode={devMode} />
    </LangProvider>,
  )
}

function expirar() {
  act(() => avisarSeSessaoExpirou(401))
}

beforeEach(() => {
  localStorage.clear()
  window.history.pushState({}, '', '/auditoria?de=2026-10-01')
})
afterEach(cleanup)

describe('SessaoExpirada', () => {
  it('não aparece antes do 401', () => {
    renderizar()
    expect(screen.queryByRole('alertdialog')).toBeNull()
  })

  it('telaMostraAvisoComLinkDeLogin (pt)', () => {
    renderizar()
    expirar()
    const dialogo = screen.getByRole('alertdialog')
    expect(dialogo).toHaveTextContent(
      'Sua sessão expirou. Entre de novo para continuar; os filtros da tela ficam na URL.',
    )
    const link = screen.getByRole('link', { name: 'Entrar de novo' })
    expect(link).toHaveAttribute('href', '/auth/login?volta=%2Fauditoria%3Fde%3D2026-10-01')
  })

  it('telaMostraAvisoComLinkDeLogin (en)', () => {
    localStorage.setItem('painel.idioma', 'en')
    renderizar()
    expirar()
    expect(screen.getByRole('alertdialog')).toHaveTextContent(
      'Your session has expired. Sign in again to continue; the screen filters stay in the URL.',
    )
    expect(screen.getByRole('link', { name: 'Sign in again' })).toHaveAttribute(
      'href',
      '/auth/login?volta=%2Fauditoria%3Fde%3D2026-10-01',
    )
  })

  it('focoNoBotaoEEscNaoFecha', async () => {
    renderizar()
    expirar()
    const link = screen.getByRole('link', { name: 'Entrar de novo' })
    expect(link).toHaveFocus()
    await userEvent.keyboard('{Escape}')
    expect(screen.getByRole('alertdialog')).toBeInTheDocument()
    expect(link).toHaveFocus()
  })

  it('devModeNaoMostra', () => {
    renderizar(true)
    expirar()
    expect(screen.queryByRole('alertdialog')).toBeNull()
  })
})
