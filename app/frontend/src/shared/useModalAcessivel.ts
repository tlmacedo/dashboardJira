import { useEffect, useRef, useState, type KeyboardEvent } from 'react'

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
    const focaveis = raiz.current.querySelectorAll<HTMLElement>('button:not([disabled]), [href], [tabindex="0"]')
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
