import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../screens/TempoEmStatus/api'
import { enviarJson, ErroDaApi } from './client'

afterEach(() => vi.unstubAllGlobals())

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
