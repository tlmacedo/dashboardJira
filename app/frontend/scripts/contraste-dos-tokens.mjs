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
  const corpo = css.slice(i, fim)
  for (const m of corpo.matchAll(/--([a-z0-9-]+):\s*(#[0-9a-fA-F]{6})\s*;/g)) tokens[m[1]] = m[2]
  // Referências do tipo --menu-marca: var(--primary) são resolvidas depois da mescla do tema.
  const referencias = {}
  for (const m of corpo.matchAll(/--([a-z0-9-]+):\s*var\(--([a-z0-9-]+)\)\s*;/g)) referencias[m[1]] = m[2]
  Object.defineProperty(tokens, '__ref', { value: referencias, enumerable: false })
  return tokens
}

function mesclar(base, tema) {
  const r = { ...base, ...tema }
  const refs = { ...(base.__ref ?? {}), ...(tema.__ref ?? {}) }
  for (const [nome, alvo] of Object.entries(refs)) if (!(nome in tema)) r[nome] = r[alvo]
  return r
}

const claro = bloco(':root')
const temas = {
  claro: mesclar(claro, {}),
  escuro: mesclar(claro, bloco("[data-theme='dark']")),
  sidia: mesclar(claro, bloco("[data-theme='sidia']")),
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
  // Borda de controle (campo, botão, segmentado): WCAG 1.4.11, mínimo 3:1 sobre os fundos onde aparece.
  ['line-control', 'surface', 3, 'borda de controle sobre o cartão'],
  ['line-control', 'bg', 3, 'borda de controle sobre a página (campo dentro da barra de filtros)'],
  ['line-control', 'soft', 3, 'borda de controle sobre fundo suave (hover, chip)'],
  // Divisória decorativa: fora da regra 1.4.11 (não identifica componente); só informativa, não reprova.
  ['line', 'surface', null, 'divisória do cartão e da tabela (decorativa; não usar em controle)'],
  ['on-primary', 'primary', 4.5, 'botão primário'],
  // Menu lateral (no tema Sidia é azul).
  ['menu-text', 'menu', 4.5, 'item do menu'],
  ['menu-text2', 'menu', 4.5, 'grupo e rótulo do menu'],
  ['menu-text', 'menu-active', 4.5, 'item atual e segmentado ativo do menu'],
  ['menu-line', 'menu', null, 'divisória e contorno dos segmentados do menu (os botões têm texto e estado pressionado; ver Minor adiado)'],
  ['menu-marca', 'menu', 3, 'barra do item atual do menu (indicador, não texto)'],
]

let falhas = 0
for (const [tema, t] of Object.entries(temas)) {
  console.log(`\n== tema ${tema}`)
  for (const [a, b, minimo, onde] of pares) {
    const r = razao(t[a], t[b])
    const informativo = minimo === null
    const ok = informativo || r >= minimo
    if (!ok) falhas++
    const marca = informativo ? 'info ' : ok ? 'ok   ' : 'FALHA'
    console.log(`${marca} ${r.toFixed(2).padStart(5)}:1 (mín ${informativo ? '-' : minimo}) ${a} ${t[a]} em ${b} ${t[b]} - ${onde}`)
  }
}
console.log(`\npares abaixo do mínimo: ${falhas}`)
process.exit(falhas > 0 ? 1 : 0)
