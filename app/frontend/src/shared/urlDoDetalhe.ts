/**
 * URL da página de detalhe (`/detalhe`) com o mesmo recorte da gaveta: `cartao` (id do cartão), `segmento` e os mesmos
 * filtros da tela. O título da página vem do cartão validado pelo backend, nunca da URL. Caminho interno do próprio painel.
 */
export function urlDoDetalhe(cartaoId: string, segmento: string, filtros: URLSearchParams): string {
  const p = new URLSearchParams(filtros)
  p.set('cartao', cartaoId)
  p.set('segmento', segmento)
  return `/detalhe?${p.toString()}`
}
