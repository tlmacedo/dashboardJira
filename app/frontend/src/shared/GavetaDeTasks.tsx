import { useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { createPortal } from 'react-dom'
import { ErroDaApi } from '../api/client'
import { useLang } from '../i18n/LangProvider'
import { baixarCsv } from './csv'
import { inteiro } from './format'
import { fetchTasksDoCartao, type LinhaDeTask } from './tasksApi'
import './gaveta.css'
import './tabela.css'

/** Acima disso o backend não gera link para o Jira (a URL ficaria longa demais): a gaveta avisa e oferece o CSV. */
const LIMITE_DO_LINK = 100

/** Coluna da gaveta: o id vem do backend (`colunas`); o texto é a chave `gaveta.col.<id>`. */
const VALOR_DA_COLUNA: Record<string, (r: LinhaDeTask) => string> = {
  chave: (r) => r.key,
  resumo: (r) => r.resumo,
  responsavel: (r) => r.responsavel ?? '',
  status: (r) => r.status,
  detalhe: (r) => r.detalhe,
}

/** Só aceita o link da API se for https e sem credencial: nunca um `javascript:` nem um endereço torto. */
function linkSeguro(link: string | null): string | null {
  if (!link) return null
  try {
    const u = new URL(link)
    return u.protocol === 'https:' && !u.username && !u.password ? link : null
  } catch {
    return null
  }
}

export type GavetaDeTasksProps = {
  cartaoId: string
  segmento: string
  /** Os mesmos filtros do cartão que abriu a gaveta (vão como parâmetros de URL). */
  filtros: URLSearchParams
  /** Título do recorte ("Status: Em revisão"); sem ele vale o título do cartão. */
  titulo?: string
  /** Filtros ativos, já em texto, para o cabeçalho e para o CSV. */
  resumoDosFiltros?: string
  aoFechar: () => void
}

/**
 * Gaveta com as tasks que compõem um número (`GET /api/cartoes/{id}/tasks`). Foco vai para o botão Fechar ao abrir,
 * Tab fica preso dentro dela, Esc fecha e o foco volta ao elemento que a abriu; o resto da página fica inerte.
 * Todo valor vindo da API é renderizado como texto. O link "Abrir no Jira" vem pronto da API (a gaveta não monta URL).
 */
export function GavetaDeTasks(props: GavetaDeTasksProps) {
  const { t, lang } = useLang()
  const { cartaoId, segmento, filtros, aoFechar, resumoDosFiltros } = props
  const raiz = useRef<HTMLDivElement>(null)
  const fechar = useRef<HTMLButtonElement>(null)
  const chaveDosFiltros = filtros.toString()

  const consulta = useQuery({
    queryKey: ['tasks-do-cartao', cartaoId, segmento, chaveDosFiltros],
    queryFn: () => fetchTasksDoCartao(cartaoId, segmento, new URLSearchParams(chaveDosFiltros)),
    retry: false,
  })

  // A gaveta vive num portal no body; o resto da página fica inerte enquanto ela está aberta.
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

  const dados = consulta.data
  const linhas = dados?.linhas ?? []
  const colunas = (dados?.colunas ?? []).filter((c) => c in VALOR_DA_COLUNA)
  const titulo = props.titulo ?? (dados ? t(dados.tituloChave) : '')
  const semPermissao = consulta.error instanceof ErroDaApi && consulta.error.status === 403
  const link = linkSeguro(dados?.linkJira ?? null)
  const acimaDoLimite = dados !== undefined && dados.total > LIMITE_DO_LINK && link === null

  function exportar() {
    if (!dados) return
    baixarCsv(`tasks-${cartaoId}.csv`, [
      [`# ${titulo}`],
      [`# ${t('gaveta.csv.filtros')}: ${resumoDosFiltros || t('gaveta.semFiltros')}`],
      [`# ${t('gaveta.definicao')}: ${t(dados.definicaoChave)}`],
      colunas.map((c) => t(`gaveta.col.${c}`)),
      ...linhas.map((r) => colunas.map((c) => VALOR_DA_COLUNA[c](r))),
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
            {dados && <p className="sub">{t('gaveta.total').replace('{n}', inteiro(dados.total, lang))}</p>}
            {dados && (
              <p className="gaveta-definicao">
                <strong>{t('gaveta.definicao')}:</strong> {t(dados.definicaoChave)}
              </p>
            )}
            {resumoDosFiltros && (
              <p className="gaveta-filtros">
                {t('gaveta.filtros')}: {resumoDosFiltros}
              </p>
            )}
            {acimaDoLimite && <p className="gaveta-aviso">{t('gaveta.acimaDe100')}</p>}
          </div>
          <div className="gaveta-acoes">
            <button type="button" className="btn" onClick={exportar} disabled={linhas.length === 0}>
              {t('gaveta.csv')}
            </button>
            {link && (
              <a className="gaveta-link" href={link} target="_blank" rel="noopener noreferrer">
                {t('gaveta.abrirNoJira')} <span aria-hidden="true">↗</span>
              </a>
            )}
            <button type="button" className="btn" ref={fechar} onClick={aoFechar}>
              {t('gaveta.fechar')}
            </button>
          </div>
        </header>

        <div className="gaveta-corpo">
          {consulta.isPending && (
            <p className="vazio" aria-busy="true">
              {t('app.carregando')}
            </p>
          )}
          {semPermissao && <p className="estado estado--erro">{t('gaveta.semPermissao')}</p>}
          {consulta.isError && !semPermissao && (
            <p className="estado estado--erro">
              {t('gaveta.erro')}{' '}
              <button type="button" className="btn" onClick={() => consulta.refetch()}>
                {t('gaveta.tentarDeNovo')}
              </button>
            </p>
          )}
          {consulta.isSuccess && linhas.length === 0 && <p className="vazio">{t('gaveta.vazio')}</p>}
          {consulta.isSuccess && linhas.length > 0 && (
            <div className="tabela-rolagem" tabIndex={0} role="region" aria-label={titulo}>
              <table className="tabela">
                <thead>
                  <tr>
                    {colunas.map((c) => (
                      <th scope="col" key={c}>
                        {t(`gaveta.col.${c}`)}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {/* Uma task pode aparecer mais de uma vez (ex.: dois sinais da auditoria na mesma issue): a posição entra na chave. */}
                  {linhas.map((r, i) => (
                    <tr key={`${r.key}:${i}`}>
                      {colunas.map((c) =>
                        c === 'chave' ? (
                          <th scope="row" className="mono" key={c}>
                            {r.key}
                          </th>
                        ) : (
                          <td key={c}>{c === 'responsavel' ? (r.responsavel ?? '—') : VALOR_DA_COLUNA[c](r)}</td>
                        ),
                      )}
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
