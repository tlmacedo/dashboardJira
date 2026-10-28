import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { createPortal } from 'react-dom'
import { useLang } from '../../i18n/LangProvider'
import type { LinhaRisco } from './api'
import '../../shared/gaveta.css'
import '../../shared/tabela.css'

/**
 * Gaveta com os pontos de CADA fator de uma task em risco. Foco no botão Fechar ao abrir, Tab preso dentro, Esc fecha e o
 * foco volta ao botão que a abriu; o resto da página fica inerte (o mesmo comportamento da gaveta de tasks).
 */
export function FatoresDoRisco(props: { linha: LinhaRisco; aoFechar: () => void }) {
  const { t, lang } = useLang()
  const { linha, aoFechar } = props
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

  const titulo = t('riscos.fatores.titulo').replace('{key}', linha.key)
  const soma = linha.fatores.reduce((s, f) => s + f.pontos, 0)
  const nf = (n: number) => n.toLocaleString(lang === 'pt' ? 'pt-BR' : 'en-US')

  return createPortal(
    <div className="gaveta-fundo" onClick={aoFechar}>
      <div ref={raiz} className="gaveta" role="dialog" aria-modal="true" aria-labelledby="fatores-titulo" onClick={(e) => e.stopPropagation()} onKeyDown={aoTeclar}>
        <header className="gaveta-topo">
          <div>
            <h2 id="fatores-titulo">{titulo}</h2>
            <p className="sub">{linha.resumo}</p>
          </div>
          <div className="gaveta-acoes">
            <button type="button" className="btn" ref={fechar} onClick={aoFechar}>
              {t('gaveta.fechar')}
            </button>
          </div>
        </header>
        <div className="gaveta-corpo">
          <div className="tabela-rolagem" tabIndex={0} role="region" aria-label={titulo}>
            <table className="tabela">
              <thead>
                <tr>
                  <th scope="col">{t('riscos.col.fator')}</th>
                  <th scope="col" className="num">{t('riscos.col.pontos')}</th>
                </tr>
              </thead>
              <tbody>
                {linha.fatores.map((f) => (
                  <tr key={f.fator}>
                    <th scope="row">{t(`riscos.fator.${f.fator}`)}</th>
                    <td className="num">{nf(f.pontos)}</td>
                  </tr>
                ))}
                <tr>
                  <th scope="row">{t('riscos.fatores.total')}</th>
                  <td className="num">{nf(linha.pontos)}</td>
                </tr>
              </tbody>
            </table>
          </div>
          {soma !== linha.pontos && <p className="legenda-meta">{t('riscos.fatores.limitado').replace('{soma}', nf(soma))}</p>}
        </div>
      </div>
    </div>,
    portal,
  )
}
