import { useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { createPortal } from 'react-dom'
import { useLang } from '../../i18n/LangProvider'
import { ErroDaApi } from '../../api/client'
import { fetchDrill, type Filtros } from './api'
import { baixarCsv } from './csv'
import { inteiro, linhaFiltrosCsv } from './format'

/**
 * Gaveta com as tasks que compõem um número. Foco vai para o botão Fechar ao abrir,
 * Tab fica preso dentro dela, Esc fecha e o foco volta ao elemento que a abriu.
 * Todo valor vindo da API é renderizado como texto.
 */
export function DrillDrawer(props: {
  kind: string
  titulo: string
  filtros: Filtros
  aoFechar: () => void
}) {
  const { t, lang } = useLang()
  const { kind, titulo, filtros, aoFechar } = props
  const raiz = useRef<HTMLDivElement>(null)
  const fechar = useRef<HTMLButtonElement>(null)

  const consulta = useQuery({
    queryKey: ['tempo-em-status', 'drill', kind, filtros],
    queryFn: () => fetchDrill(filtros, kind),
    retry: false,
  })

  // A gaveta vive num portal no body; o resto da página fica inerte enquanto ela está aberta.
  const [portal] = useState(() => document.createElement('div'))
  useEffect(() => {
    const anterior = document.activeElement as HTMLElement | null
    document.body.appendChild(portal)
    const inertes = [...document.body.children].filter(
      (el) => el !== portal && !el.hasAttribute('inert'),
    )
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

  const linhas = consulta.data ?? []
  const semPermissao = consulta.error instanceof ErroDaApi && consulta.error.status === 403

  function exportar() {
    baixarCsv('tempo-em-status-detalhe.csv', [
      [`# ${titulo}`],
      [linhaFiltrosCsv(filtros, t, lang)],
      [t('tempo.col.task'), t('tempo.col.resumo'), t('tempo.col.responsavel'), t('tempo.col.status'), t('tempo.col.detalhe')],
      ...linhas.map((r) => [r.key, r.resumo, r.responsavel ?? '', r.status, r.detalhe]),
    ])
  }

  return createPortal(
    <div className="gaveta-fundo" onClick={aoFechar}>
      <div
        ref={raiz}
        className="gaveta"
        role="dialog"
        aria-modal="true"
        aria-labelledby="gaveta-titulo"
        onClick={(e) => e.stopPropagation()}
        onKeyDown={aoTeclar}
      >
        <header className="gaveta-topo">
          <div>
            <h2 id="gaveta-titulo">{titulo}</h2>
            {consulta.isSuccess && (
              <p className="sub">{t('tempo.gaveta.total').replace('{n}', inteiro(linhas.length, lang))}</p>
            )}
          </div>
          <div className="gaveta-acoes">
            <button type="button" className="btn" onClick={exportar} disabled={linhas.length === 0}>
              {t('tempo.csv')}
            </button>
            <button type="button" className="btn" ref={fechar} onClick={aoFechar}>
              {t('tempo.fechar')}
            </button>
          </div>
        </header>

        <div className="gaveta-corpo">
          {consulta.isPending && <p className="vazio" aria-busy="true">{t('app.carregando')}</p>}
          {semPermissao && <p className="estado estado--erro">{t('tempo.semPermissao')}</p>}
          {consulta.isError && !semPermissao && (
            <p className="estado estado--erro">
              {t('tempo.erro')}{' '}
              <button type="button" className="btn" onClick={() => consulta.refetch()}>
                {t('tempo.tentarDeNovo')}
              </button>
            </p>
          )}
          {consulta.isSuccess && linhas.length === 0 && <p className="vazio">{t('tempo.gaveta.vazio')}</p>}
          {consulta.isSuccess && linhas.length > 0 && (
            <div className="tabela-rolagem" tabIndex={0} role="region" aria-label={titulo}>
              <table className="tabela">
                <thead>
                  <tr>
                    <th scope="col">{t('tempo.col.task')}</th>
                    <th scope="col">{t('tempo.col.resumo')}</th>
                    <th scope="col">{t('tempo.col.responsavel')}</th>
                    <th scope="col">{t('tempo.col.status')}</th>
                    <th scope="col">{t('tempo.col.detalhe')}</th>
                  </tr>
                </thead>
                <tbody>
                  {linhas.map((r) => (
                    <tr key={r.key}>
                      <th scope="row" className="mono">{r.key}</th>
                      <td>{r.resumo}</td>
                      <td>{r.responsavel ?? '—'}</td>
                      <td>{r.status}</td>
                      <td>{r.detalhe}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>,
    portal,
  )
}
