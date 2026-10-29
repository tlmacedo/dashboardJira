/** Só aceita o link vindo da API se for https e sem credencial: nunca um `javascript:` nem um endereço torto. */
export function linkSeguro(link: string | null): string | null {
  if (!link) return null
  try {
    const u = new URL(link)
    return u.protocol === 'https:' && !u.username && !u.password ? link : null
  } catch {
    return null
  }
}
