// Cliente da API do próprio painel (Ktor). O frontend nunca fala com o Jira.

export type Meta = {
  devMode: boolean
  liveEnabled: boolean
  zone: string
  banner: string | null
}

export async function fetchMeta(): Promise<Meta> {
  const resposta = await fetch('/api/meta', { headers: { Accept: 'application/json' } })
  if (!resposta.ok) throw new Error(`/api/meta respondeu ${resposta.status}`)
  return (await resposta.json()) as Meta
}
