/**
 * URL da página de detalhe (`/detalhe`) com o mesmo recorte da gaveta: `cartao` (id do cartão), `segmento` e os mesmos
 * filtros da tela, mais o `titulo` do recorte (só texto, para o cabeçalho). Caminho interno do próprio painel.
 */
export function urlDoDetalhe(cartaoId: string, segmento: string, filtros: URLSearchParams, titulo?: string): string {
  const p = new URLSearchParams(filtros)
  p.set('cartao', cartaoId)
  p.set('segmento', segmento)
  if (titulo) p.set('titulo', titulo)
  return `/detalhe?${p.toString()}`
}
