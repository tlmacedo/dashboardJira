/** Símbolo e texto da decisão: o estado nunca é só cor (`✓` aprovada, `●` trocada, `■` rejeitada). */
export function rotuloDaDecisao(decisao: string, t: (k: string) => string): string {
  const simbolo = decisao === 'APROVADA' ? '✓' : decisao === 'TROCADA' ? '●' : '■'
  return `${simbolo} ${t(`decisao.${decisao}`)}`
}
