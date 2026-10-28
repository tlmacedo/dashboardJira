// Cliente da API do próprio painel (Ktor). O frontend nunca fala com o Jira.

export type Meta = {
  devMode: boolean
  liveEnabled: boolean
  zone: string
  banner: string | null
}

export type Me = { userId: string; roles: string[] }

/** Cabeçalho que a proteção CSRF do backend exige em todo método que altera estado. */
export const CABECALHO_CSRF = 'X-Painel-CSRF'

/**
 * Erro de uma resposta HTTP da API. `codigo` e `params` vêm do `ErroDto` do backend quando o corpo é JSON
 * (a tela monta o texto pelo código); respostas sem corpo (400, 5xx) ficam com `codigo` nulo.
 */
export class ErroDaApi extends Error {
  readonly status: number
  readonly codigo: string | null
  readonly params: Record<string, string>
  constructor(status: number, codigo: string | null = null, params: Record<string, string> = {}) {
    super(codigo ? `HTTP ${status} (${codigo})` : `HTTP ${status}`)
    this.status = status
    this.codigo = codigo
    this.params = params
  }
}

/** Lê o `ErroDto` do corpo, se houver; qualquer outra coisa vira erro sem código. */
async function erroDaResposta(resposta: Response): Promise<ErroDaApi> {
  try {
    const corpo = (await resposta.json()) as { codigo?: unknown; params?: unknown }
    if (typeof corpo?.codigo === 'string') {
      const params =
        corpo.params && typeof corpo.params === 'object' ? (corpo.params as Record<string, string>) : {}
      return new ErroDaApi(resposta.status, corpo.codigo, params)
    }
  } catch {
    // corpo vazio ou que não é JSON
  }
  return new ErroDaApi(resposta.status)
}

/**
 * Envia um método que altera estado (POST, PUT, PATCH, DELETE) com o cabeçalho anti-CSRF.
 * Resposta sem conteúdo (204) devolve `undefined`.
 */
export async function enviarJson<T>(
  metodo: 'POST' | 'PUT' | 'PATCH' | 'DELETE',
  url: string,
  corpo?: unknown,
): Promise<T> {
  const resposta = await fetch(url, {
    method: metodo,
    credentials: 'same-origin',
    headers: { Accept: 'application/json', 'Content-Type': 'application/json', [CABECALHO_CSRF]: '1' },
    body: corpo === undefined ? undefined : JSON.stringify(corpo),
  })
  if (!resposta.ok) throw await erroDaResposta(resposta)
  if (resposta.status === 204) return undefined as T
  return (await resposta.json()) as T
}

/** Quem está logado e com quais papéis (vindos do diretório no backend). */
export async function fetchMe(): Promise<Me> {
  const resposta = await fetch('/api/me', { headers: { Accept: 'application/json' } })
  if (!resposta.ok) throw new Error(`/api/me respondeu ${resposta.status}`)
  return (await resposta.json()) as Me
}

export async function fetchMeta(): Promise<Meta> {
  const resposta = await fetch('/api/meta', { headers: { Accept: 'application/json' } })
  if (!resposta.ok) throw new Error(`/api/meta respondeu ${resposta.status}`)
  return (await resposta.json()) as Meta
}
