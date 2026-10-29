import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

// Varreduras estáticas do código das telas (acessibilidade). Cobrem o que os testes de renderização
// (`rotulos.test.tsx`, `teclado.test.tsx`) não renderizam: toda tela, não só a amostra.

const raiz = resolve(import.meta.dirname, '..')

function fontes(pasta: string): { caminho: string; texto: string }[] {
  const saida: { caminho: string; texto: string }[] = []
  for (const nome of readdirSync(pasta)) {
    const caminho = join(pasta, nome)
    if (statSync(caminho).isDirectory()) saida.push(...fontes(caminho))
    else if (nome.endsWith('.tsx') && !nome.includes('.test.')) saida.push({ caminho, texto: readFileSync(caminho, 'utf8') })
  }
  return saida
}
const rel = (c: string) => c.replace(raiz + '/', '')

describe('varredura do código das telas', () => {
  it('todoThDoCodigoTemScope', () => {
    // Toda <th> declarada diz se é de coluna ou de linha (leitor de tela).
    const sem: string[] = []
    for (const f of fontes(raiz)) {
      for (const m of f.texto.matchAll(/<th(?=[\s>])[^>]*?>/gs)) {
        if (!/scope=/.test(m[0])) sem.push(`${rel(f.caminho)}: ${m[0].replace(/\s+/g, ' ').slice(0, 60)}`)
      }
    }
    expect(sem).toEqual([])
  })

  it('imagensTemAltOuSaoDecorativas', () => {
    const sem: string[] = []
    for (const f of fontes(raiz)) {
      for (const m of f.texto.matchAll(/<img(?=[\s/>])[\s\S]*?\/>/g)) {
        if (!/\balt=/.test(m[0])) sem.push(rel(f.caminho))
      }
    }
    expect(sem).toEqual([])
  })

  it('todaClasseDeEstadoColoridoVemComSimboloNoMesmoArquivo', () => {
    // As classes que só mudam a cor (ok, atenção, problema, crítico) precisam andar com ✓ ● ▲ no mesmo componente,
    // ou usar o componente Ocupacao, que já os põe. Um arquivo novo que só troque a cor derruba este teste.
    const estado = /(ocupacao--|pilula--|cartao-estado--|celula--|sinc-(ok|aviso|ruim)|alertas-(critico|atencao)|aging-acima|inicio-item--)/
    const sem: string[] = []
    for (const f of fontes(raiz)) {
      if (!estado.test(f.texto)) continue
      if (/[✓●▲■]/.test(f.texto) || /<Ocupacao\b/.test(f.texto)) continue
      sem.push(rel(f.caminho))
    }
    expect(sem, 'arquivo com estado por cor sem nenhum símbolo ✓ ● ▲').toEqual([])
  })

  it('todoDialogoModalUsaOHookAcessivel', () => {
    // O alertdialog da sessão expirada tem foco próprio (um só elemento focável); os `role="dialog"` usam o hook
    // (foco em "Fechar", Tab preso, Esc fecha e devolve o foco, resto da página inerte).
    const achados = fontes(raiz)
      .filter((f) => /role="dialog"/.test(f.texto) && !/useModalAcessivel/.test(f.texto))
      .map((f) => rel(f.caminho))
    expect(achados, 'diálogo sem useModalAcessivel').toEqual([])
  })

  it('cssDoCelularEscondeAListaEMostraOBotao', () => {
    const css = readFileSync(join(raiz, 'shell/shell.css'), 'utf8')
    const i = css.indexOf('@media (max-width: 820px)')
    expect(i).toBeGreaterThan(0)
    const celular = css.slice(i, css.indexOf('@media (prefers-reduced-motion', i))
    expect(celular).toMatch(/\.burger\s*\{[^}]*display:\s*inline-block/)
    expect(celular).toMatch(/\.menu-nav,\s*\.menu \.foot\s*\{[^}]*display:\s*none/)
    expect(celular).toMatch(/\.menu--aberto \.menu-nav\s*\{[^}]*display:\s*block/)
  })
})
