// @vitest-environment node
import { spawnSync } from 'node:child_process'
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach, describe, expect, it } from 'vitest'

const script = join(import.meta.dirname, 'verificar-csp.mjs')
const pastas: string[] = []

// Cria um `dist` de teste com o index.html informado e roda a verificação sobre ele.
function verificar(html: string) {
  const pasta = mkdtempSync(join(tmpdir(), 'csp-'))
  pastas.push(pasta)
  mkdirSync(join(pasta, 'assets'))
  writeFileSync(join(pasta, 'index.html'), html)
  return spawnSync(process.execPath, [script, pasta], { encoding: 'utf8' })
}

const comCorpo = (cabeca: string, corpo = '<div id="root"></div>') =>
  `<!doctype html><html><head><meta charset="UTF-8" />${cabeca}</head><body>${corpo}</body></html>`

afterEach(() => {
  while (pastas.length) rmSync(pastas.pop()!, { recursive: true, force: true })
})

describe('verificar-csp.mjs', () => {
  it('aceita o index.html no formato do Vite', () => {
    const r = verificar(
      comCorpo(
        '<script type="module" crossorigin src="/assets/x.js"></script><link rel="stylesheet" href="/assets/x.css">',
      ),
    )
    expect(r.status).toBe(0)
  })

  it('recusa script inline', () => {
    const r = verificar(comCorpo('<script>alert(1)</script>'))
    expect(r.status).toBe(1)
    expect(r.stdout + r.stderr).toContain('script-inline')
  })

  it('recusa manipulador de evento inline', () => {
    const r = verificar(comCorpo('', '<button onclick="x()">a</button>'))
    expect(r.status).toBe(1)
    expect(r.stdout + r.stderr).toContain('manipulador-inline')
  })

  it('recusa atributo style no HTML', () => {
    const r = verificar(comCorpo('', '<p style="color:red">a</p>'))
    expect(r.status).toBe(1)
    expect(r.stdout + r.stderr).toContain('style-atributo')
  })

  it('recusa elemento style no HTML', () => {
    const r = verificar(comCorpo('<style>p{color:red}</style>'))
    expect(r.status).toBe(1)
  })

  it('recusa javascript: em endereço', () => {
    const r = verificar(comCorpo('', '<a href="javascript:void(0)">a</a>'))
    expect(r.status).toBe(1)
  })

  it('recusa script de outra origem', () => {
    const r = verificar(comCorpo('<script src="https://cdn.exemplo.invalid/x.js"></script>'))
    expect(r.status).toBe(1)
    expect(r.stdout + r.stderr).toContain('origem-externa')
  })

  it('não imprime o conteúdo encontrado', () => {
    const r = verificar(comCorpo('<script>segredo-no-script()</script>'))
    expect(r.stdout + r.stderr).not.toContain('segredo-no-script')
  })

  it('falha fechado sem nenhum html na pasta', () => {
    const pasta = mkdtempSync(join(tmpdir(), 'csp-'))
    pastas.push(pasta)
    expect(spawnSync(process.execPath, [script, pasta], { encoding: 'utf8' }).status).toBe(1)
  })
})
