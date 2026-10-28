import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { createPortal } from 'react-dom'
import { useLang } from '../../i18n/LangProvider'
import { dataCurta, numero } from '../../shared/format'
import type { ParCR } from './api'
import '../../shared/gaveta.css'
import '../../shared/tabela.css'

/**
 * Comparação lado a lado de um par (o checklist e a sua review): os mesmos campos nas duas colunas. Mesmo comportamento
 * da gaveta de tasks: o foco vai para "Fechar" ao abrir, Tab fica preso, Esc fecha e devolve o foco, e o resto da página
 * fica inerte. Responsável só aparece quando o painel o mandou (pessoa dentro da visão); senão, "—".
 */
export function ComparacaoDoPar(props: { par: ParCR; aoFechar: () => void }) {
  const { t, lang } = useLang()
  const { par, aoFechar } = props
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

  const dia = (iso: string | null) => (iso ? dataCurta(iso, lang) : '—')
  const linhas: { rotulo: string; pai: string; review: string }[] = [
    { rotulo: t('cr.lado.resumo'), pai: par.resumoPai, review: par.resumoReview },
    { rotulo: t('cr.lado.responsavel'), pai: par.responsavelPai ?? '—', review: par.responsavelReview ?? '—' },
    { rotulo: t('cr.lado.status'), pai: par.statusPai, review: par.statusReview },
    { rotulo: t('cr.lado.criacao'), pai: dia(par.criacaoPai), review: dia(par.criacaoReview) },
    { rotulo: t('cr.lado.vence'), pai: dia(par.vencePai), review: dia(par.venceReview) },
    { rotulo: t('cr.lado.resolvido'), pai: dia(par.resolvidoPai), review: dia(par.resolvidoReview) },
    {
      rotulo: t('cr.lado.horasAtivas'),
      pai: '—',
      review: par.horasAtivasReview == null ? '—' : `${numero(par.horasAtivasReview, lang)} h`,
    },
    { rotulo: t('cr.lado.reaberto'), pai: par.paiReaberto ? t('cr.sim') : t('cr.nao'), review: '—' },
  ]

  return createPortal(
    <div className="gaveta-fundo" onClick={aoFechar}>
      <div
        ref={raiz}
        className="gaveta"
        role="dialog"
        aria-modal="true"
        aria-labelledby="cr-lado-titulo"
        onClick={(e) => e.stopPropagation()}
        onKeyDown={aoTeclar}
      >
        <header className="gaveta-topo">
          <div>
            <h2 id="cr-lado-titulo">
              {t('cr.lado.titulo').replace('{pai}', par.pai).replace('{review}', par.review)}
            </h2>
            <p className="gaveta-definicao">{t('cr.lado.sub')}</p>
          </div>
          <div className="gaveta-acoes">
            <button type="button" className="btn" ref={fechar} onClick={aoFechar}>
              {t('gaveta.fechar')}
            </button>
          </div>
        </header>
        <div className="gaveta-corpo">
          <div className="tabela-rolagem" tabIndex={0} role="region" aria-label={t('cr.lado.tabela')}>
            <table className="tabela" aria-label={t('cr.lado.tabela')}>
              <thead>
                <tr>
                  <th scope="col">{t('cr.lado.campo')}</th>
                  <th scope="col">
                    {t('cr.lado.pai')} <span className="mono">{par.pai}</span>
                  </th>
                  <th scope="col">
                    {t('cr.lado.review')} <span className="mono">{par.review}</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {linhas.map((l) => (
                  <tr key={l.rotulo}>
                    <th scope="row">{l.rotulo}</th>
                    <td>{l.pai}</td>
                    <td>{l.review}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <h3>{t('cr.lado.alertas')}</h3>
          {par.alertas.length === 0 ? (
            <p>
              <span aria-hidden="true">✓</span> {t('cr.semAlerta')}
            </p>
          ) : (
            <ul>
              {par.alertas.map((a) => (
                <li key={a}>
                  <span aria-hidden="true">▲</span> {t(`cr.alerta.${a}`)}
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>,
    portal,
  )
}
