// Confere se o build do frontend cabe na CSP do painel (script e estilo só da própria origem).
// Uso: node scripts/verificar-csp.mjs [pastaDist]   (padrão: dist)
// Falha (saída 1) se algum .html tiver script inline, manipulador on*=, atributo style, <style>, javascript:
// ou <link>/<script> com endereço absoluto fora da origem. Imprime só o arquivo e a regra, nunca o conteúdo.
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'

const raiz = process.argv[2] ?? 'dist'

function listarHtml(pasta) {
  const achados = []
  for (const nome of readdirSync(pasta)) {
    const caminho = join(pasta, nome)
    if (statSync(caminho).isDirectory()) achados.push(...listarHtml(caminho))
    else if (nome.toLowerCase().endsWith('.html')) achados.push(caminho)
  }
  return achados
}

// Cada regra é uma expressão aplicada ao HTML inteiro (sem diferenciar maiúsculas).
const regras = [
  // <script> sem src= (o conteúdo, mesmo vazio, é inline)
  { nome: 'script-inline', testa: (h) => [...h.matchAll(/<script\b([^>]*)>/gi)].some((m) => !/\bsrc\s*=/i.test(m[1])) },
  // atributos onclick=, onload=, onerror=...
  { nome: 'manipulador-inline', testa: (h) => /<[a-z][^>]*\son[a-z]+\s*=/i.test(h) },
  { nome: 'style-atributo', testa: (h) => /<[a-z][^>]*\sstyle\s*=/i.test(h) },
  { nome: 'style-elemento', testa: (h) => /<style\b/i.test(h) },
  { nome: 'javascript-url', testa: (h) => /javascript\s*:/i.test(h) },
  {
    nome: 'origem-externa',
    // src= ou href= com endereço absoluto (http, https ou //) em <script> e <link>
    testa: (h) =>
      [...h.matchAll(/<(?:script|link)\b[^>]*>/gi)].some((m) =>
        /\b(?:src|href)\s*=\s*["']?\s*(?:https?:)?\/\//i.test(m[0]),
      ),
  },
]

let arquivos
try {
  arquivos = listarHtml(raiz)
} catch {
  console.error(`ERRO: pasta ${raiz} não encontrada (rode pnpm build antes)`)
  process.exit(1)
}
if (arquivos.length === 0) {
  console.error(`ERRO: nenhum .html em ${raiz}`)
  process.exit(1)
}

let violacoes = 0
for (const arquivo of arquivos) {
  const html = readFileSync(arquivo, 'utf8')
  for (const regra of regras) {
    if (regra.testa(html)) {
      console.error(`VIOLACAO ${regra.nome} ${arquivo}`)
      violacoes++
    }
  }
}

if (violacoes > 0) {
  console.error(`CSP: ${violacoes} violação(ões)`)
  process.exit(1)
}
console.log(`CSP ok: ${arquivos.length} arquivo(s) html conferido(s)`)
