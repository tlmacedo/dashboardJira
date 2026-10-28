import { cleanup, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderizarTela, simular } from '../../test/telas'
import type { ConfiguracaoDto } from './api'
import { Configuracao } from './Configuracao'

const HASH = 'a'.repeat(64)
const DTO: ConfiguracaoDto = {
  devMode: true,
  liveEnabled: false,
  modoSync: 'DEMONSTRACAO',
  pessoas: 12,
  squads: 3,
  arquivos: [
    { nome: 'metas', versao: 2, sha256: HASH, origem: 'exemplo' },
    { nome: 'cards/qualidade', versao: null, sha256: 'b'.repeat(64), origem: 'real' },
  ],
}
const API = '/api/governanca/configuracao'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Configuração', () => {
  it('mostraAmbienteEArquivosEmSimboloETexto', async () => {
    simular({ [API]: { status: 200, corpo: DTO } })
    renderizarTela(<Configuracao />, '/configuracao')
    const tabela = await screen.findByRole('table', { name: 'Ambiente e arquivos de regras' })
    const linhas = within(tabela).getAllByRole('row').slice(1)
    expect(linhas[0]).toHaveTextContent('metas')
    expect(linhas[0]).toHaveTextContent('■ Modelo (exemplo)')
    expect(linhas[0]).toHaveTextContent(HASH.slice(0, 12))
    expect(linhas[1]).toHaveTextContent('✓ Arquivo real')
    expect(screen.getByText('Dados de demonstração')).toBeInTheDocument()
    expect(screen.getByText('● Sim')).toBeInTheDocument()
    expect(screen.getByText('✓ Não')).toBeInTheDocument()
    // Só as contagens de pessoas e squads; o hash completo fica no title, nunca um caminho.
    expect(screen.getByText('12')).toBeInTheDocument()
    expect(document.body.textContent).not.toMatch(/https?:\/\//)
  })

  it('semPermissaoMostraMensagemDePapel', async () => {
    simular({ [API]: { status: 403 } })
    renderizarTela(<Configuracao />, '/configuracao')
    expect(await screen.findByRole('alert')).toHaveTextContent('Seu papel não permite ver estes dados')
  })
})
