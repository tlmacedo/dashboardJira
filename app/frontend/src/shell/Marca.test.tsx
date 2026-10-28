import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { LangProvider } from '../i18n/LangProvider'
import { ThemeProvider } from '../theme/ThemeProvider'

// O módulo de ativos é trocável: os testes simulam "com logo" e "sem logo".
const ativos = vi.hoisted(() => ({ valor: null as null | { logoParaFundoClaro: string; logoParaFundoEscuro: string } }))
vi.mock('./marca-ativos', () => ({
  get ATIVOS_MARCA() {
    return ativos.valor
  },
}))

import { Marca } from './Marca'

function envolver(filho: ReactNode) {
  return (
    <ThemeProvider>
      <LangProvider>{filho}</LangProvider>
    </ThemeProvider>
  )
}

beforeEach(() => {
  localStorage.clear()
  ativos.valor = { logoParaFundoClaro: '/claro.png', logoParaFundoEscuro: '/escuro.png' }
})
afterEach(cleanup)

describe('Marca', () => {
  it('marcaComLogoTemAltEnome', () => {
    render(envolver(<Marca />))
    const img = screen.getByRole('img', { name: 'Sidia' })
    expect(img).toHaveAttribute('src', '/claro.png')
    expect(screen.getByText('Painel de Squads')).toBeInTheDocument()
  })

  it('usaALogoClaraNosTemasEscuroESidia', () => {
    localStorage.setItem('painel.tema', 'sidia')
    render(envolver(<Marca />))
    expect(screen.getByRole('img', { name: 'Sidia' })).toHaveAttribute('src', '/escuro.png')
  })

  it('marcaSemLogoMostraTexto', () => {
    ativos.valor = null
    render(envolver(<Marca />))
    expect(screen.queryByRole('img')).toBeNull()
    expect(screen.getByText('Painel de Squads')).toBeInTheDocument()
  })

  it('imagemQueFalhaCaiNoTexto', () => {
    render(envolver(<Marca />))
    fireEvent.error(screen.getByRole('img', { name: 'Sidia' }))
    expect(screen.queryByRole('img')).toBeNull()
    expect(screen.getByText('Painel de Squads')).toBeInTheDocument()
  })

  it('compactaMostraSoALogo', () => {
    render(envolver(<Marca compacta />))
    expect(screen.getByRole('img', { name: 'Sidia' })).toBeInTheDocument()
    expect(screen.queryByText('Painel de Squads')).toBeNull()
  })

  it('compactaSemLogoAindaMostraOTexto', () => {
    ativos.valor = null
    render(envolver(<Marca compacta />))
    expect(screen.getByText('Painel de Squads')).toBeInTheDocument()
  })
})
