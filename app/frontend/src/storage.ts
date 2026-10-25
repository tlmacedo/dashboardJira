// Acesso ao localStorage sempre dentro de try/catch: o navegador pode bloquear o
// armazenamento (janela privada, dados limpos) e a página precisa funcionar sem ele.

export function lerPreferencia(chave: string): string | null {
  try {
    return localStorage.getItem(chave)
  } catch {
    return null
  }
}

export function gravarPreferencia(chave: string, valor: string): void {
  try {
    localStorage.setItem(chave, valor)
  } catch {
    // sem armazenamento: a preferência vale só nesta sessão
  }
}
