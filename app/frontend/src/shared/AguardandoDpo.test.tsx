import { cleanup, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ErroDaApi } from '../api/client'
import { renderizarTela, simular } from '../test/telas'
import { Auditoria } from '../screens/Auditoria/Auditoria'
import { AguardandoDpo, ehAguardandoDpo } from './AguardandoDpo'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('AguardandoDpo', () => {
  it('reconheceSoOCodigoDoDpoNoStatus403', () => {
    expect(ehAguardandoDpo(new ErroDaApi(403, 'AGUARDANDO_ACEITE_DPO'))).toBe(true)
    expect(ehAguardandoDpo(new ErroDaApi(403))).toBe(false)
    expect(ehAguardandoDpo(new ErroDaApi(500, 'AGUARDANDO_ACEITE_DPO'))).toBe(false)
    expect(ehAguardandoDpo(new Error('x'))).toBe(false)
    expect(ehAguardandoDpo(null)).toBe(false)
  })

  it('mostraOTextoEmPtEEn', () => {
    renderizarTela(<AguardandoDpo />, '/x')
    expect(screen.getByRole('alert')).toHaveTextContent('desligada até o encarregado de dados (DPO) aprovar o uso. Nenhum dado foi consultado.')
    cleanup()
    localStorage.setItem('painel.idioma', 'en')
    renderizarTela(<AguardandoDpo />, '/x')
    expect(screen.getByRole('alert')).toHaveTextContent('until the data protection officer (DPO) approves its use')
  })

  it('telaBloqueadaNaoMostraNumerosNemBotoes', async () => {
    simular({ '/api/auditoria/sinais': { status: 403, corpo: { codigo: 'AGUARDANDO_ACEITE_DPO', params: { modulo: 'AUDITORIA' } } } })
    renderizarTela(<Auditoria />, '/auditoria')
    expect(await screen.findByRole('alert')).toHaveTextContent('desligada até o encarregado de dados (DPO)')
    expect(screen.queryByRole('table')).toBeNull()
    expect(screen.queryByRole('button', { name: 'Exportar CSV' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Tentar de novo' })).toBeNull()
    expect(screen.queryByText(/apenas a coordenação e o gerente/i)).toBeNull()
  })
})
