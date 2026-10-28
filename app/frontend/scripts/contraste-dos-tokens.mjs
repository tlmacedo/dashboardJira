// Contraste WCAG calculado a partir dos tokens dos três temas (src/theme/tokens.css), para a revisão visual dos cartões.
// Uso: node scripts/contraste-dos-tokens.mjs [caminho/do/tokens.css]   (padrão: src/theme/tokens.css)
// Sai com código 1 se algum par ficar abaixo do mínimo (4,5:1 texto; 3:1 foco e borda de componente).
import { readFileSync } from 'node:fs'

const css = readFileSync(process.argv[2] ?? 'src/theme/tokens.css', 'utf8')

function bloco(seletor) {
  const i = css.indexOf(seletor + ' {')
  if (i < 0) throw new Error('seletor ausente: ' + seletor)
  const fim = css.indexOf('}', i)
  const tokens = {}
  for (const m of css.slice(i, fim).matchAll(/--([a-z0-9-]+):\s*(#[0-9a-fA-F]{6})\s*;/g)) tokens[m[1]] = m[2]
  return tokens
}

const claro = bloco(':root')
const temas = {
  claro,
  escuro: { ...claro, ...bloco("[data-theme='dark']") },
  sidia: { ...claro, ...bloco("[data-theme='sidia']") },
}

function luminancia(hex) {
  const c = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255)
  const l = c.map((v) => (v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4))
  return 0.2126 * l[0] + 0.7152 * l[1] + 0.0722 * l[2]
}

function razao(a, b) {
  const [claroDosDois, escuroDosDois] = [luminancia(a), luminancia(b)].sort((p, q) => q - p)
  return (claroDosDois + 0.05) / (escuroDosDois + 0.05)
}

// [token da frente, token do fundo, mínimo exigido, onde]
const pares = [
  ['text', 'surface', 4.5, 'título, valor e corpo do cartão'],
  ['text2', 'surface', 4.5, 'rótulo, legenda, procedência, ⓘ'],
  ['text2', 'soft', 4.5, 'legenda com o cartão em hover'],
  ['primary', 'surface', 4.5, '"Ver tasks", links e "Abrir no Jira"'],
  ['primary', 'soft', 4.5, '"Ver tasks" com o cartão em hover'],
  ['ok', 'surface', 4.5, 'estado ✓ (texto)'],
  ['warn', 'surface', 4.5, 'estado ● e aviso de dados incompletos (texto)'],
  ['bad', 'surface', 4.5, 'estado ▲ e mensagem de erro (texto)'],
  ['focus', 'surface', 3, 'anel de foco sobre o cartão'],
  ['focus', 'bg', 3, 'anel de foco sobre a página'],
  ['text2', 'bg', 3, 'borda dos campos do Mapa de cartões'],
  ['line', 'surface', 3, 'borda do cartão (divisória decorativa; falha conhecida, ver o progresso do Plano 17)'],
  ['on-primary', 'primary', 4.5, 'botão primário'],
]

let falhas = 0
for (const [tema, t] of Object.entries(temas)) {
  console.log(`\n== tema ${tema}`)
  for (const [a, b, minimo, onde] of pares) {
    const r = razao(t[a], t[b])
    const ok = r >= minimo
    if (!ok) falhas++
    console.log(`${ok ? 'ok   ' : 'FALHA'} ${r.toFixed(2).padStart(5)}:1 (mín ${minimo}) ${a} ${t[a]} em ${b} ${t[b]} - ${onde}`)
  }
}
console.log(`\npares abaixo do mínimo: ${falhas}`)
process.exit(falhas > 0 ? 1 : 0)
