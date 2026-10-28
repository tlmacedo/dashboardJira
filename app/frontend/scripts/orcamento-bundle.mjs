// Orçamento do bundle principal do frontend (Plano 8, Tarefa 6).
// Uso: node scripts/orcamento-bundle.mjs [pastaDist] [--limite-kb N]   (padrões: dist e 300)
// Lê o index.html, acha o script de módulo (o bundle principal) e mede o tamanho GZIP dele. Falha (saída 1) se passar
// do limite; saída 2 se não achar o build. Imprime só tamanhos e nomes de arquivo, nunca conteúdo.
import { readFileSync, existsSync } from 'node:fs'
import { join } from 'node:path'
import { gzipSync } from 'node:zlib'

const args = process.argv.slice(2)
let limiteKb = 300
let raiz = 'dist'
for (let i = 0; i < args.length; i++) {
  if (args[i] === '--limite-kb') {
    limiteKb = Number(args[++i])
    if (!Number.isFinite(limiteKb) || limiteKb <= 0) {
      console.error('ERRO: --limite-kb precisa de um número positivo')
      process.exit(2)
    }
  } else {
    raiz = args[i]
  }
}

const indice = join(raiz, 'index.html')
if (!existsSync(indice)) {
  console.error(`ERRO: ${indice} não encontrado (rode pnpm build antes)`)
  process.exit(2)
}

const html = readFileSync(indice, 'utf8')
// O bundle principal é o <script type="module" src="...">; o tema-inicial.js (sem type=module) é minúsculo e fica de fora.
const principal = [...html.matchAll(/<script\b([^>]*)>/gi)]
  .map((m) => m[1])
  .filter((atributos) => /\btype\s*=\s*["']?module/i.test(atributos))
  .map((atributos) => /\bsrc\s*=\s*["']?([^"'\s>]+)/i.exec(atributos)?.[1])
  .find(Boolean)
if (!principal) {
  console.error('ERRO: nenhum <script type="module" src> no index.html')
  process.exit(2)
}

function gzipKb(caminhoNoSite) {
  const arquivo = join(raiz, caminhoNoSite.replace(/^\/+/, ''))
  if (!existsSync(arquivo)) {
    console.error(`ERRO: ${caminhoNoSite} citado no index.html não existe em ${raiz}`)
    process.exit(2)
  }
  return gzipSync(readFileSync(arquivo), { level: 9 }).length / 1024
}

const kb = gzipKb(principal)
const css = [...html.matchAll(/<link\b[^>]*rel\s*=\s*["']?stylesheet[^>]*>/gi)]
  .map((m) => /\bhref\s*=\s*["']?([^"'\s>]+)/i.exec(m[0])?.[1])
  .filter(Boolean)
  .reduce((soma, href) => soma + gzipKb(href), 0)

console.log(`bundle principal ${principal}: ${kb.toFixed(1)} KB gzip (limite ${limiteKb} KB); CSS: ${css.toFixed(1)} KB gzip`)
if (kb > limiteKb) {
  console.error(`ORÇAMENTO ESTOURADO: ${kb.toFixed(1)} KB > ${limiteKb} KB (divida o bundle por tela)`)
  process.exit(1)
}
