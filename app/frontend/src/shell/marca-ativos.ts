// PONTO ÚNICO de troca dos ativos de marca (ver docs/design/ativos-de-marca.md).
// Os caminhos apontam para arquivos servidos do próprio domínio (pasta public/).
// Quando o usuário fornecer o logo limpo em SVG, saneie-o, coloque-o em src/assets/marca/
// e troque só estes dois caminhos. Para o caminho "sem logo", exporte null: o menu
// passa a mostrar apenas o nome do painel em texto.

export type AtivosDeMarca = {
  /** Logo para fundos claros (temas Claro). */
  logoParaFundoClaro: string
  /** Logo para fundos escuros ou azuis (temas Escuro e Sidia). */
  logoParaFundoEscuro: string
}

export const ATIVOS_MARCA: AtivosDeMarca | null = {
  logoParaFundoClaro: '/logo-sidia-azul.png',
  logoParaFundoEscuro: '/logo-sidia-branco.png',
}
