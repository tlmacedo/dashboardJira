import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gaveta, renderizarTela, simular } from '../../test/telas'
import { Detalhe } from './Detalhe'

const ROTA = '/api/cartoes/AB-01/tasks'
const URL_INICIAL = '/detalhe?cartao=AB-01&segmento=abertas&squad=Alfa&unit=corrido&titulo=Abertas'

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

async function csvDa(botao: HTMLElement): Promise<string> {
  let blobGerado: Blob | null = null
  URL.createObjectURL = (b: Blob | MediaSource) => {
    blobGerado = b as Blob
    return 'blob:x'
  }
  URL.revokeObjectURL = () => {}
  await userEvent.click(botao)
  return new Promise<string>((ok) => {
    const leitor = new FileReader()
    leitor.onload = () => ok(String(leitor.result))
    leitor.readAsText(blobGerado as unknown as Blob)
  })
}

describe('Detalhe em nova aba', () => {
  it('detalheEmNovaAbaMesmaListaDoDrill', async () => {
    const mock = simular({ [ROTA]: { status: 200, corpo: gaveta(5) } })
    renderizarTela(<Detalhe />, '/detalhe', URL_INICIAL)
    expect(await screen.findByRole('heading', { level: 1, name: 'Abertas' })).toBeInTheDocument()
    const tabela = await screen.findByRole('table')
    // Mesma chamada do drill (cartão, segmento e filtros da tela de origem) e mesma quantidade de linhas.
    const chamada = new URL(String(mock.mock.calls.find((c) => String(c[0]).startsWith(ROTA))?.[0]), 'http://x')
    expect(chamada.searchParams.get('segmento')).toBe('abertas')
    expect(chamada.searchParams.get('squad')).toBe('Alfa')
    expect(chamada.searchParams.get('unit')).toBe('corrido')
    expect(chamada.searchParams.get('titulo')).toBeNull()
    expect(within(tabela).getAllByRole('row')).toHaveLength(1 + 5)
    // O CSV leva as mesmas linhas.
    const texto = await csvDa(screen.getByRole('button', { name: 'Exportar CSV' }))
    expect(texto.match(/"TG-\d+";/g)).toHaveLength(5)
  })

  it('semCartaoOuSegmentoMostraAvisoENaoChamaARede', () => {
    const mock = simular({})
    renderizarTela(<Detalhe />, '/detalhe', '/detalhe?segmento=abertas')
    expect(screen.getByText('Faltam o cartão e o recorte na URL.')).toBeInTheDocument()
    expect(mock).not.toHaveBeenCalled()
  })

  it('idDeCartaoForaDoFormatoNaoChamaARede', () => {
    const mock = simular({})
    renderizarTela(<Detalhe />, '/detalhe', '/detalhe?cartao=../x&segmento=abertas')
    expect(screen.getByText('Faltam o cartão e o recorte na URL.')).toBeInTheDocument()
    expect(mock).not.toHaveBeenCalled()
  })

  it('tituloDaUrlEntraComoTextoNuncaComoHtml', async () => {
    simular({ [ROTA]: { status: 200, corpo: gaveta(1) } })
    renderizarTela(<Detalhe />, '/detalhe', '/detalhe?cartao=AB-01&segmento=abertas&titulo=%3Cimg%20src%3Dx%20onerror%3Dalert(1)%3E')
    const h1 = await screen.findByRole('heading', { level: 1 })
    expect(h1.textContent).toBe('<img src=x onerror=alert(1)>')
    expect(h1.querySelector('img')).toBeNull()
  })

  it('chaveAbreAGavetaDaTask', async () => {
    simular({ [ROTA]: { status: 200, corpo: gaveta(2) }, '/api/tarefa/TG-100': { status: 404 } })
    renderizarTela(<Detalhe />, '/detalhe', URL_INICIAL)
    await userEvent.click(await screen.findByRole('button', { name: 'TG-100' }))
    expect(await screen.findByText('Task não encontrada na sua visão.')).toBeInTheDocument()
  })

  it('semPermissao403MostraMensagem', async () => {
    simular({ [ROTA]: { status: 403 } })
    renderizarTela(<Detalhe />, '/detalhe', URL_INICIAL)
    expect(await screen.findByRole('alert')).toHaveTextContent(/não permite ver estes dados/i)
  })
})
