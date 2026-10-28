// @vitest-environment node
import { spawnSync } from 'node:child_process'
import { randomBytes } from 'node:crypto'
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach, describe, expect, it } from 'vitest'

const script = join(import.meta.dirname, 'orcamento-bundle.mjs')
const pastas: string[] = []

const html = (js: string) =>
  `<!doctype html><html><head><script src="/tema-inicial.js"></script><script type="module" crossorigin src="/assets/${js}"></script>` +
  `<link rel="stylesheet" crossorigin href="/assets/x.css"></head><body><div id="root"></div></body></html>`

// Monta um `dist` de teste: o bundle principal tem `kbAleatorios` KB incompressíveis (o gzip fica do mesmo tamanho).
function dist(kbAleatorios: number, indice: string | null = html('index-abc.js')) {
  const pasta = mkdtempSync(join(tmpdir(), 'bundle-'))
  pastas.push(pasta)
  mkdirSync(join(pasta, 'assets'))
  if (indice !== null) writeFileSync(join(pasta, 'index.html'), indice)
  writeFileSync(join(pasta, 'tema-inicial.js'), randomBytes(900 * 1024)) // fora do orçamento: não é o principal
  writeFileSync(join(pasta, 'assets/index-abc.js'), randomBytes(kbAleatorios * 1024))
  writeFileSync(join(pasta, 'assets/x.css'), 'body{margin:0}')
  return pasta
}

const rodar = (args: string[]) => spawnSync(process.execPath, [script, ...args], { encoding: 'utf8' })

afterEach(() => {
  while (pastas.length) rmSync(pastas.pop()!, { recursive: true, force: true })
})

describe('orcamento-bundle.mjs', () => {
  it('aceita o bundle principal dentro do limite (e ignora o tema-inicial.js)', () => {
    const r = rodar([dist(100)])
    expect(r.status).toBe(0)
    expect(r.stdout).toMatch(/bundle principal \/assets\/index-abc\.js: 10\d\.\d KB gzip \(limite 300 KB\)/)
  })

  it('falha quando o bundle principal passa do limite padrão de 300 KB', () => {
    const r = rodar([dist(320)])
    expect(r.status).toBe(1)
    expect(r.stderr).toContain('ORÇAMENTO ESTOURADO')
  })

  it('o limite pode ser mudado e a fronteira vale', () => {
    const pasta = dist(50)
    expect(rodar([pasta, '--limite-kb', '60']).status).toBe(0)
    expect(rodar([pasta, '--limite-kb', '40']).status).toBe(1)
  })

  it('sem build, sem módulo principal ou arquivo citado ausente: saída 2', () => {
    expect(rodar(['pasta-que-nao-existe']).status).toBe(2)
    expect(rodar([dist(10, '<html><head></head></html>')]).status).toBe(2)
    expect(rodar([dist(10, html('nao-existe.js'))]).status).toBe(2)
    expect(rodar([dist(10), '--limite-kb', 'abc']).status).toBe(2)
  })
})
