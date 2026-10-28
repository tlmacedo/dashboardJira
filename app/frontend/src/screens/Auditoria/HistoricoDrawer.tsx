import { useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { createPortal } from 'react-dom'
import { ErroDaApi } from '../../api/client'
import { useLang } from '../../i18n/LangProvider'
import { baixarCsv } from '../../shared/csv'
import { fetchHistorico, type LinhaHistorico } from './api'
import '../../shared/gaveta.css'
import '../../shared/tabela.css'

/** Data e hora no fuso do painel (America/Manaus), no formato do idioma. */
function quando(iso: string, lang: 'pt' | 'en'): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return new Intl.DateTimeFormat(lang === 'pt' ? 'pt-BR' : 'en-US', {
    timeZone: 'America/Manaus',
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(d)
}

export type HistoricoDrawerProps = {
  chave: string
  /** Filtros ativos da tela, já em texto, para a linha de filtros do CSV. */
  resumoDosFiltros: string
  aoFechar: () => void
}

/**
 * Gaveta com o histórico de uma issue campo a campo (`GET /api/auditoria/historico/{key}`). Mesmo comportamento da
 * gaveta de tasks: foco no Fechar ao abrir, Tab preso, Esc fecha e o foco volta a quem abriu; o resto fica inerte.
 * Campo sem valor guardado mostra "valor não guardado (privacidade)": o painel nunca guarda texto livre do Jira.
 */
export function HistoricoDrawer(props: HistoricoDrawerProps) {
  const { t, lang } = useLang()
  const { chave, aoFechar } = props
  const raiz = useRef<HTMLDivElement>(null)
  const fechar = useRef<HTMLButtonElement>(null)

  const consulta = useQuery({ queryKey: ['auditoria-historico', chave], queryFn: () => fetchHistorico(chave), retry: false })

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

  const dto = consulta.data
  const status = consulta.error instanceof ErroDaApi ? consulta.error.status : null
  const titulo = t('auditoria.historico.titulo').replace('{key}', chave)

  function textoDaAutorizacao(l: LinhaHistorico): string {
    if (l.autorizado == null) return '—'
    return `${l.autorizado ? '✓' : '▲'} ${l.autorizado ? t('auditoria.autorizado') : t('auditoria.naoAutorizado')}${l.motivo ? ` · ${t(`auditoria.motivo.${l.motivo}`)}` : ''}`
  }

  function exportar() {
    if (!dto) return
    baixarCsv(`historico-${chave}.csv`, [
      [`# ${titulo}`],
      [`# ${t('auditoria.aviso')}`],
      [`# ${t('auditoria.csv.filtros')}: ${props.resumoDosFiltros}`],
      [],
      [
        t('auditoria.historico.col.data'),
        t('auditoria.historico.col.campo'),
        t('auditoria.historico.col.de'),
        t('auditoria.historico.col.para'),
        t('auditoria.historico.col.autor'),
        t('auditoria.historico.col.autorizacao'),
      ],
      ...dto.linhas.map((l) => [
        l.at,
        l.campo,
        l.valorGuardado ? (l.de ?? '') : t('auditoria.historico.naoGuardado'),
        l.valorGuardado ? (l.para ?? '') : t('auditoria.historico.naoGuardado'),
        l.autor ?? '',
        textoDaAutorizacao(l),
      ]),
    ])
  }

  return createPortal(
    <div className="gaveta-fundo" onClick={aoFechar}>
      <div
        ref={raiz}
        className="gaveta"
        role="dialog"
        aria-modal="true"
        aria-labelledby="historico-titulo"
        onClick={(e) => e.stopPropagation()}
        onKeyDown={aoTeclar}
      >
        <header className="gaveta-topo">
          <div>
            <h2 id="historico-titulo">{titulo}</h2>
            {dto && <p className="sub">{dto.resumo}</p>}
            {dto?.reporter && <p className="gaveta-filtros">{t('auditoria.historico.reporter').replace('{reporter}', dto.reporter)}</p>}
            <p role="note" className="aud-aviso aud-aviso--gaveta">
              <span aria-hidden="true">●</span> {t('auditoria.aviso')}
            </p>
          </div>
          <div className="gaveta-acoes">
            <button type="button" className="btn" onClick={exportar} disabled={!dto || dto.linhas.length === 0}>
              {t('auditoria.historico.csv')}
            </button>
            <button type="button" className="btn" ref={fechar} onClick={aoFechar}>
              {t('auditoria.historico.fechar')}
            </button>
          </div>
        </header>

        <div className="gaveta-corpo">
          {consulta.isPending && (
            <p className="vazio" aria-busy="true">
              {t('app.carregando')}
            </p>
          )}
          {status === 403 && <p className="estado estado--erro">{t('auditoria.historico.semPermissao')}</p>}
          {status === 404 && <p className="estado estado--erro">{t('auditoria.historico.naoEncontrada')}</p>}
          {consulta.isError && status !== 403 && status !== 404 && (
            <p className="estado estado--erro">
              {t('auditoria.historico.erro')}{' '}
              <button type="button" className="btn" onClick={() => consulta.refetch()}>
                {t('auditoria.tentarDeNovo')}
              </button>
            </p>
          )}
          {dto && dto.linhas.length === 0 && <p className="vazio">{t('auditoria.historico.vazio')}</p>}
          {dto && dto.linhas.length > 0 && (
            <div className="tabela-rolagem" tabIndex={0} role="region" aria-label={titulo}>
              <table className="tabela">
                <thead>
                  <tr>
                    <th scope="col">{t('auditoria.historico.col.data')}</th>
                    <th scope="col">{t('auditoria.historico.col.campo')}</th>
                    <th scope="col">{t('auditoria.historico.col.de')}</th>
                    <th scope="col">{t('auditoria.historico.col.para')}</th>
                    <th scope="col">{t('auditoria.historico.col.autor')}</th>
                    <th scope="col">{t('auditoria.historico.col.autorizacao')}</th>
                  </tr>
                </thead>
                <tbody>
                  {dto.linhas.map((l, i) => (
                    <tr key={`${l.at}:${l.campo}:${i}`}>
                      <td className="num">{quando(l.at, lang)}</td>
                      <th scope="row" className="mono">
                        {l.campo}
                      </th>
                      {l.valorGuardado ? (
                        <>
                          <td>{l.de ?? '—'}</td>
                          <td>{l.para ?? '—'}</td>
                        </>
                      ) : (
                        <td colSpan={2} className="aud-privacidade">
                          {t('auditoria.historico.naoGuardado')}
                        </td>
                      )}
                      <td>{l.autor ?? t('auditoria.autorDesconhecido')}</td>
                      <td>{textoDaAutorizacao(l)}</td>
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
