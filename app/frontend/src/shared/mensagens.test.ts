import { describe, expect, it } from 'vitest'
import { ErroDaApi } from '../api/client'
import en from '../i18n/en.json'
import pt from '../i18n/pt.json'
import { dataCurta, inteiro, numero } from './format'
import { textoDoErro } from './textoDoErro'
import { textoDoAviso } from './useCard'

const tp = (k: string) => (pt as Record<string, string>)[k] ?? k
const te = (k: string) => (en as Record<string, string>)[k] ?? k

describe('mensagens do backend por código', () => {
  it('avisoPorCodigoComParametro', () => {
    const aviso = { codigo: 'STATUS_SEM_MAPEAMENTO', gravidade: 'bloqueante', mensagem: 'texto em português do servidor', params: { status: 'Exótico' } }
    expect(textoDoAviso(aviso, tp)).toBe('Há status sem mapeamento: Exótico')
    expect(textoDoAviso(aviso, te)).not.toContain('Há status')
    expect(textoDoAviso(aviso, te)).toContain('Exótico')
  })

  it('codigoDesconhecidoNaoMostraMensagemPt', () => {
    const aviso = { codigo: 'CODIGO_NOVO', gravidade: 'informativo', mensagem: 'mensagem em português' }
    expect(textoDoAviso(aviso, tp)).toBe('Aviso do servidor (código CODIGO_NOVO).')
    expect(textoDoAviso(aviso, te)).toBe('Server notice (code CODIGO_NOVO).')
    expect(textoDoAviso(aviso, te)).not.toContain('português')
  })

  it('erroPorCodigoEmPtEEn', () => {
    const erro = new ErroDaApi(400, 'FILTRO_INVALIDO', { campo: 'sku-type' })
    expect(textoDoErro(erro, tp)).toBe('Algum filtro é inválido. Revise os filtros e aplique de novo.')
    expect(textoDoErro(erro, te)).toBe('A filter is invalid. Review the filters and apply again.')
    expect(textoDoErro(new ErroDaApi(409, 'NOVO_CODIGO'), te)).toBe('Server error (code NOVO_CODIGO).')
    // Sem código (ou nem erro da API): mensagem genérica, nunca o status nem o texto técnico.
    expect(textoDoErro(new ErroDaApi(500), tp)).toBe('Não foi possível carregar os dados.')
    expect(textoDoErro(new Error('java.lang.NullPointerException'), tp)).toBe('Não foi possível carregar os dados.')
  })

  it('datasENumerosPorIdioma', () => {
    expect(dataCurta('2026-10-30', 'pt')).toBe('30/10/2026')
    expect(dataCurta('2026-10-30', 'en')).toBe('10/30/2026')
    expect(numero(1234.5, 'pt')).toBe('1.234,5')
    expect(numero(1234.5, 'en')).toBe('1,234.5')
    expect(inteiro(1234, 'pt')).toBe('1.234')
    expect(inteiro(1234, 'en')).toBe('1,234')
  })
})
