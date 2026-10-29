import { useEffect, useRef, useState, type KeyboardEvent } from 'react'

const CANDIDATOS =
  'button:not([disabled]), [href], input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), summary, [tabindex]'

/** Visível para quem usa Tab: sem `hidden`, `aria-hidden`, `inert` nem display/visibility ocultos em si ou nos ancestrais. */
function alcancavel(el: HTMLElement, limite: HTMLElement): boolean {
  for (let n: HTMLElement | null = el; n && n !== limite.parentElement; n = n.parentElement) {
    if (n.hidden || n.getAttribute('aria-hidden') === 'true' || n.hasAttribute('inert')) return false
    const css = getComputedStyle(n)
    if (css.display === 'none' || css.visibility === 'hidden') return false
  }
  return true
}

/**
 * Os elementos que recebem Tab dentro da gaveta, na ordem do documento: botões, links, campos de formulário (a gaveta de
 * decisão tem área de texto) e regiões roláveis (`tabindex="0"`). Fora: `tabindex="-1"`, ocultos e, em grupo de rádio,
 * todos menos um (o marcado, ou o primeiro): o navegador trata o grupo inteiro como uma parada só.
 */
export function elementosComTab(raiz: HTMLElement): HTMLElement[] {
  const vistos = new Set<string>()
  const saida: HTMLElement[] = []
  const radios = raiz.querySelectorAll<HTMLInputElement>('input[type="radio"]:not([disabled])')
  const escolhido = new Map<string, HTMLInputElement>()
  for (const r of radios) {
    if (!r.name || !alcancavel(r, raiz)) continue
    const atual = escolhido.get(r.name)
    if (!atual || (r.checked && !atual.checked)) escolhido.set(r.name, r)
  }
  for (const el of raiz.querySelectorAll<HTMLElement>(CANDIDATOS)) {
    const tabindex = el.getAttribute('tabindex')
    if (tabindex !== null && Number(tabindex) < 0) continue
    if (!alcancavel(el, raiz)) continue
    if (el instanceof HTMLInputElement && el.type === 'radio' && el.name) {
      if (escolhido.get(el.name) !== el || vistos.has(el.name)) continue
      vistos.add(el.name)
    }
    saida.push(el)
  }
  return saida
}

/**
 * Comportamento de gaveta modal acessível, comum à gaveta de tasks, à da task e à do histórico: vive num portal no
 * `body`; o resto da página fica inerte enquanto ela está aberta; o foco vai para o botão "Fechar" ao abrir e volta ao
 * elemento que a abriu ao fechar; Esc fecha (sem fechar a gaveta de baixo, se houver) e Tab fica preso dentro dela.
 *
 * Uso: `raiz` no elemento `role="dialog"`, `fechar` no botão Fechar, `aoTeclar` no `onKeyDown` do diálogo e `portal` como
 * destino do `createPortal`. Duas gavetas empilhadas funcionam: a de cima só desfaz o que ela mesma deixou inerte.
 */
export function useModalAcessivel(aoFechar: () => void) {
  const raiz = useRef<HTMLDivElement>(null)
  const fechar = useRef<HTMLButtonElement>(null)
  const [portal] = useState(() => document.createElement('div'))

  useEffect(() => {
    const anterior = document.activeElement as HTMLElement | null
    document.body.appendChild(portal)
    const inertes = [...document.body.children].filter((el) => el !== portal && !el.hasAttribute('inert'))
    inertes.forEach((el) => el.setAttribute('inert', ''))
    fechar.current?.focus()
    return () => {
      inertes.forEach((el) => el.removeAttribute('inert'))
      portal.remove()
      anterior?.focus()
    }
  }, [portal])

  function aoTeclar(e: KeyboardEvent) {
    if (e.key === 'Escape') {
      e.stopPropagation()
      aoFechar()
      return
    }
    if (e.key !== 'Tab' || !raiz.current) return
    const focaveis = elementosComTab(raiz.current)
    if (focaveis.length === 0) return
    const primeiro = focaveis[0]
    const ultimo = focaveis[focaveis.length - 1]
    if (e.shiftKey && document.activeElement === primeiro) {
      e.preventDefault()
      ultimo.focus()
    } else if (!e.shiftKey && document.activeElement === ultimo) {
      e.preventDefault()
      primeiro.focus()
    }
  }

  return { portal, raiz, fechar, aoTeclar }
}
