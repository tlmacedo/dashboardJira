import { afterEach, describe, expect, it, vi } from 'vitest'
import { fetchSyncStatus } from '../screens/Sincronizacao/api'
import { fetchTempoEmStatus, ApiError, FILTROS_VAZIOS } from '../screens/TempoEmStatus/api'
import {
  caminhoDeVolta,
  enviarJson,
  ErroDaApi,
  EVENTO_SESSAO_EXPIRADA,
  fetchMe,
  getJson,
  urlDeLogin,
} from './client'

afterEach(() => vi.unstubAllGlobals())

// Todo o código-fonte da aplicação (menos testes e o próprio cliente): nenhum arquivo pode chamar `fetch` direto.
const fontes = import.meta.glob(['/src/**/*.{ts,tsx}', '!/src/**/*.test.{ts,tsx}', '!/src/api/client.ts'], {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>

describe('único cliente HTTP', () => {
  it('nenhumFetchForaDoCliente', () => {
    expect(Object.keys(fontes).length).toBeGreaterThan(10) // a varredura não pode ser vazia
    const culpados = Object.entries(fontes)
      .filter(([, texto]) => /(^|[^.\w])fetch\s*\(/.test(texto))
      .map(([caminho]) => caminho)
    expect(culpados).toEqual([])
  })
})

describe('sessão expirada', () => {
  function contarEventos() {
    const ouvinte = vi.fn()
    window.addEventListener(EVENTO_SESSAO_EXPIRADA, ouvinte)
    return { ouvinte, parar: () => window.removeEventListener(EVENTO_SESSAO_EXPIRADA, ouvinte) }
  }

  it('respostas401DisparamSessaoExpirada', async () => {
    const { ouvinte, parar } = contarEventos()
    simular(new Response('', { status: 401 }))
    await expect(fetchMe()).rejects.toBeInstanceOf(ErroDaApi)
    expect(ouvinte).toHaveBeenCalledTimes(1)
    await expect(fetchSyncStatus()).rejects.toBeInstanceOf(ErroDaApi)
    expect(ouvinte).toHaveBeenCalledTimes(2)
    await expect(fetchTempoEmStatus(FILTROS_VAZIOS)).rejects.toBeInstanceOf(ErroDaApi)
    expect(ouvinte).toHaveBeenCalledTimes(3)
    await expect(getJson('/api/x')).rejects.toBeInstanceOf(ErroDaApi)
    await expect(enviarJson('POST', '/api/x')).rejects.toBeInstanceOf(ErroDaApi)
    expect(ouvinte).toHaveBeenCalledTimes(5)
    parar()
  })

  it('respostas403NaoDisparam', async () => {
    const { ouvinte, parar } = contarEventos()
    simular(new Response('', { status: 403 }))
    await expect(fetchMe()).rejects.toBeInstanceOf(ErroDaApi)
    await expect(fetchSyncStatus()).rejects.toBeInstanceOf(ErroDaApi)
    await expect(enviarJson('POST', '/api/x')).rejects.toBeInstanceOf(ErroDaApi)
    expect(ouvinte).not.toHaveBeenCalled()
    parar()
  })

  it('getJson manda Accept e devolve o corpo', async () => {
    const f = simular(new Response(JSON.stringify({ a: 1 }), { status: 200 }))
    expect(await getJson('/api/x')).toEqual({ a: 1 })
    const [, init] = f.mock.calls[0] as unknown as [string, RequestInit]
    expect(new Headers(init.headers).get('Accept')).toBe('application/json')
  })
})

describe('volta ao login', () => {
  it('voltaSoAceitaCaminhoInterno', () => {
    for (const ruim of ['//evil.example', 'https://evil.example', '/\\evil.example', 'javascript:alert(1)', '', 'auditoria', '/\t/evil.example', '/a\\b']) {
      expect(caminhoDeVolta(ruim), ruim).toBeNull()
    }
    expect(caminhoDeVolta('/auditoria?de=2026-10-01')).toBe('/auditoria?de=2026-10-01')
    expect(caminhoDeVolta('/')).toBe('/')
  })

  it('urlDeLogin codifica a volta', () => {
    expect(urlDeLogin(null)).toBe('/auth/login')
    expect(urlDeLogin('/auditoria?de=2026-10-01')).toBe('/auth/login?volta=%2Fauditoria%3Fde%3D2026-10-01')
  })
})

function simular(resposta: Response) {
  const f = vi.fn(async () => resposta)
  vi.stubGlobal('fetch', f)
  return f
}

describe('enviarJson', () => {
  it('enviarJsonMandaCabecalhoCsrf', async () => {
    const f = simular(new Response(JSON.stringify({ ok: true }), { status: 200 }))
    const r = await enviarJson<{ ok: boolean }>('POST', '/api/algo', { a: 1 })
    expect(r).toEqual({ ok: true })
    const [url, init] = f.mock.calls[0] as unknown as [string, RequestInit]
    expect(url).toBe('/api/algo')
    expect(init.method).toBe('POST')
    expect(init.credentials).toBe('same-origin')
    expect(init.body).toBe(JSON.stringify({ a: 1 }))
    const cab = new Headers(init.headers)
    expect(cab.get('X-Painel-CSRF')).toBe('1')
    expect(cab.get('Content-Type')).toBe('application/json')
  })

  it('sem corpo não manda body', async () => {
    const f = simular(new Response('{}', { status: 200 }))
    await enviarJson('DELETE', '/api/algo')
    const [, init] = f.mock.calls[0] as unknown as [string, RequestInit]
    expect(init.body).toBeUndefined()
  })

  it('resposta 204 devolve undefined', async () => {
    simular(new Response(null, { status: 204 }))
    await expect(enviarJson('POST', '/api/algo')).resolves.toBeUndefined()
  })

  it('erroDaApiTrazCodigo', async () => {
    simular(new Response(JSON.stringify({ codigo: 'ORIGEM_INVALIDA', params: { x: 'y' } }), { status: 403 }))
    const erro = (await enviarJson('POST', '/api/algo').catch((e: unknown) => e)) as ErroDaApi
    expect(erro).toBeInstanceOf(ErroDaApi)
    expect(erro.status).toBe(403)
    expect(erro.codigo).toBe('ORIGEM_INVALIDA')
    expect(erro.params).toEqual({ x: 'y' })
  })

  it('erro sem corpo JSON fica sem código', async () => {
    simular(new Response('', { status: 500 }))
    const erro = (await enviarJson('PUT', '/api/algo').catch((e: unknown) => e)) as ErroDaApi
    expect(erro).toBeInstanceOf(ErroDaApi)
    expect(erro.status).toBe(500)
    expect(erro.codigo).toBeNull()
    expect(erro.params).toEqual({})
  })
})

describe('ApiError', () => {
  it('apiErrorContinuaSendoErroDaApi', () => {
    const e = new ApiError(403)
    expect(e).toBeInstanceOf(ApiError)
    expect(e).toBeInstanceOf(ErroDaApi)
    expect(e.status).toBe(403)
    expect(e.codigo).toBeNull()
    expect(e.message).toBe('HTTP 403')
  })
})
