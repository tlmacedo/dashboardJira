// Exportação CSV segura: só o que já está na tela, com fórmulas neutralizadas.

/** Prefixa com apóstrofo valores que planilhas interpretariam como fórmula (= + - @ tab CR). */
export function neutralizarCelulaCsv(valor: string): string {
  return /^[\s\u200b-\u200f\u2060\ufeff\p{Cc}"']*[=+\-@\t\r]/u.test(valor) ? `'${valor}` : valor
}

function celula(valor: string): string {
  return `"${neutralizarCelulaCsv(valor).replaceAll('"', '""')}"`
}

/** Monta o CSV (separador `;`, comum em planilhas pt-BR). Cabeçalhos "# ..." entram como linhas de comentário. */
export function paraCsv(linhas: string[][]): string {
  return linhas
    .map((l) => {
      const texto = l.map(celula).join(';')
      return texto
    })
    .join('\r\n')
}

/** Baixa o CSV no navegador (BOM para o Excel abrir em UTF-8). */
export function baixarCsv(nome: string, linhas: string[][]): void {
  const blob = new Blob(['﻿', paraCsv(linhas)], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = nome
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}
