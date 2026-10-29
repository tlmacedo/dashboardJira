import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ErroDaApi } from '../api/client'
import { LangProvider } from '../i18n/LangProvider'
import { EstadoDaTela, estadoDe, type Estado } from './EstadoDaTela'
import type { Aviso } from './useCard'

function mostrar(estado: Estado, extra: Partial<Parameters<typeof EstadoDaTela>[0]> = {}) {
  return render(
    <LangProvider>
      <MemoryRouter>
        <EstadoDaTela estado={estado} papel={[]} {...extra} />
      </MemoryRouter>
    </LangProvider>,
  )
}

const consulta = (extra: Partial<Parameters<typeof estadoDe>[0]> = {}) => ({
  data: undefined as unknown, isPending: false, isError: false, error: null as unknown, refetch: vi.fn(), ...extra,
})

beforeEach(() => localStorage.clear())
afterEach(() => cleanup())

describe('estado aguardando o DPO', () => {
  it('estadoAguardandoDpoAntesDoSemPermissao', () => {
    const c = consulta({ isError: true, error: new ErroDaApi(403, 'AGUARDANDO_ACEITE_DPO', { modulo: 'AUDITORIA' }) })
    expect(estadoDe(c).tipo).toBe('aguardando-dpo')
    // 403 sem o código continua sendo "sem permissão".
    expect(estadoDe(consulta({ isError: true, error: new ErroDaApi(403) })).tipo).toBe('sem-permissao')
    expect(estadoDe(consulta({ isError: true, error: new ErroDaApi(403, 'OUTRO') })).tipo).toBe('sem-permissao')
  })

  it('mostraSoOTextoSemNumerosNemTentarDeNovo', () => {
    mostrar({ tipo: 'aguardando-dpo' })
    expect(screen.getByRole('alert')).toHaveTextContent('desligada até o encarregado de dados (DPO) aprovar o uso. Nenhum dado foi consultado.')
    expect(screen.queryByRole('button')).toBeNull()
  })
})

describe('estadoDe', () => {
  it('mapeiaCadaSituacaoDaConsulta', () => {
    expect(estadoDe(consulta({ isPending: true })).tipo).toBe('carregando')
    expect(estadoDe(consulta({ isError: true, error: new ErroDaApi(403) })).tipo).toBe('sem-permissao')
    expect(estadoDe(consulta({ isError: true, error: new ErroDaApi(401) })).tipo).toBe('sessao')
    expect(estadoDe(consulta({ isError: true, error: new ErroDaApi(400, 'FILTRO_INVALIDO') })).tipo).toBe('filtro-invalido')
    expect(estadoDe(consulta({ isError: true, error: new ErroDaApi(500) })).tipo).toBe('erro')
    expect(estadoDe(consulta({ isError: true, error: new Error('boom') })).tipo).toBe('erro')
    expect(estadoDe(consulta({ data: { linhas: [] } }), (d) => (d as { linhas: unknown[] }).linhas.length === 0).tipo).toBe('vazio')
    expect(estadoDe(consulta({ data: { linhas: [1] } }), (d) => (d as { linhas: unknown[] }).linhas.length === 0).tipo).toBe('ok')
  })

  it('erroComDadoAnteriorMantemATela', () => {
    expect(estadoDe(consulta({ data: { x: 1 }, isError: true, error: new ErroDaApi(500) })).tipo).toBe('ok')
  })

  it('avisoBloqueanteEIncompletoEInformativoNao', () => {
    const bloq: Aviso = { codigo: 'STATUS_SEM_MAPEAMENTO', gravidade: 'bloqueante', mensagem: 'x' }
    const info: Aviso = { codigo: 'AMOSTRA_PEQUENA', gravidade: 'informativo', mensagem: 'x' }
    expect(estadoDe(consulta({ data: {} }), undefined, [bloq, info]).tipo).toBe('incompleto')
    expect(estadoDe(consulta({ data: {} }), undefined, [info]).tipo).toBe('ok')
  })

  it('erroTemTentarDeNovoQueRefaz', () => {
    const refetch = vi.fn()
    const e = estadoDe(consulta({ isError: true, error: new ErroDaApi(500), refetch }))
    if (e.tipo !== 'erro') throw new Error('esperava erro')
    e.tentarDeNovo()
    expect(refetch).toHaveBeenCalled()
  })
})

describe('EstadoDaTela', () => {
  it('carregandoTemEsqueletoETextoParaLeitorDeTela', () => {
    const { container } = mostrar({ tipo: 'carregando' })
    expect(container.querySelector('[aria-busy="true"]')).not.toBeNull()
    expect(screen.getByText('Carregando…')).toBeInTheDocument()
  })

  it('vazioTemMensagemEBotaoLimparFiltros', async () => {
    const limpar = vi.fn()
    mostrar({ tipo: 'vazio' }, { aoLimparFiltros: limpar })
    expect(screen.getByText('Nenhum item para os filtros escolhidos.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Limpar filtros' }))
    expect(limpar).toHaveBeenCalled()
  })

  it('erroMostraTentarDeNovoENuncaTextoTecnico', async () => {
    const tentar = vi.fn()
    mostrar({ tipo: 'erro', tentarDeNovo: tentar })
    expect(screen.getByRole('alert')).toHaveTextContent('Não foi possível carregar os dados.')
    expect(screen.getByRole('alert').textContent).not.toMatch(/HTTP|Exception|\d{3}/)
    await userEvent.click(screen.getByRole('button', { name: 'Tentar de novo' }))
    expect(tentar).toHaveBeenCalled()
  })

  it('erroNuncaMostraTextoTecnico', async () => {
    // Resposta 500 com corpo de exceção: a tela só conhece o status (nunca o corpo), e o texto é o do dicionário.
    const resposta = new Response('java.lang.NullPointerException', { status: 500 })
    const erro = new ErroDaApi(resposta.status)
    const e = estadoDe(consulta({ isError: true, error: erro }))
    mostrar(e)
    expect(document.body.textContent).not.toContain('NullPointerException')
    expect(document.body.textContent).not.toContain('500')
  })

  it('semPermissaoDizParaQuemEATela', () => {
    mostrar({ tipo: 'sem-permissao' }, { papel: ['COORDENADOR', 'GERENTE'] })
    expect(screen.getByRole('alert')).toHaveTextContent('Esta tela é para Coordenador, Gerente.')
  })

  it('sessaoPedeParaEntrarDeNovo', () => {
    mostrar({ tipo: 'sessao' })
    expect(screen.getByRole('alert')).toHaveTextContent('Entre de novo para ver os dados')
  })

  it('linkDeQualidadeSoParaGerente', () => {
    const incompleto: Estado = { tipo: 'incompleto', avisos: [] }
    mostrar(incompleto, { papelAtual: 'GERENTE' })
    expect(screen.getByRole('link', { name: 'Ver em Qualidade dos dados' })).toHaveAttribute('href', '/qualidade')
    cleanup()
    mostrar(incompleto, { papelAtual: 'COORDENADOR' })
    expect(screen.queryByRole('link', { name: 'Ver em Qualidade dos dados' })).toBeNull()
  })

  it('emInglesTraduz', () => {
    localStorage.setItem('painel.idioma', 'en')
    mostrar({ tipo: 'vazio' }, { aoLimparFiltros: () => {} })
    expect(screen.getByText('No items for the chosen filters.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Clear filters' })).toBeInTheDocument()
  })
})
