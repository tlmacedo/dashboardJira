// Cliente da API do próprio painel (Ktor). O frontend nunca fala com o Jira.

export type Meta = {
  devMode: boolean
  liveEnabled: boolean
  zone: string
  banner: string | null
}

export type Me = { userId: string; roles: string[] }

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
