import type { ReactNode } from 'react'
import { ErroDaApi } from '../api/client'
import { useLang } from '../i18n/LangProvider'
import { textoDoAviso, type Aviso } from './useCard'
import '../screens/TempoEmStatus/tempo.css'
import './cardpage.css'

/** O que a página precisa saber da consulta (compatível com o resultado do `useCard`). */
export type ConsultaDaPagina = {
  data: unknown
  isPending: boolean
  isError: boolean
  isFetching: boolean
  error: unknown
  refetch: () => unknown
}

export type CardPageProps = {
  titulo: string
  /** A pergunta que a tela responde (uma frase). */
  pergunta: string
  /** A barra de filtros (`<Filtros />`). */
  filtros: ReactNode
  consulta: ConsultaDaPagina
  avisos?: Aviso[]
  /** Presente = botão "Exportar CSV" (só os dados que já estão na tela, com fórmulas neutralizadas em `shared/csv`). */
  aoExportar?: () => void
  /** Conteúdo da tela; só aparece quando há dados. */
  children: ReactNode
}

/**
 * Moldura das telas de trabalho e prazos: título e pergunta, barra de filtros, estados de carregamento e de erro
 * (403 sem permissão, 400 filtro inválido, outro erro com "tentar de novo"), avisos dos dados e o conteúdo.
 * Aviso bloqueante vem em destaque; o conteúdo mostra "—" nos números (ver `temBloqueio`).
 */
export function CardPage(props: CardPageProps) {
  const { t } = useLang()
  const { consulta } = props
  const status = consulta.error instanceof ErroDaApi ? consulta.error.status : null
  const temDados = consulta.data !== undefined && consulta.data !== null
  const avisos = props.avisos ?? []
  const bloqueantes = avisos.filter((a) => a.gravidade === 'bloqueante')
  const informativos = avisos.filter((a) => a.gravidade !== 'bloqueante')

  return (
    <div className="tempo cardpage">
      <div className="top">
        <div>
          <h1>{props.titulo}</h1>
          <p className="sub">{props.pergunta}</p>
        </div>
        <div className="top-acoes">
          {props.aoExportar && (
            <button type="button" className="btn" onClick={props.aoExportar} disabled={!temDados}>
              {t('cardpage.csv')}
            </button>
          )}
        </div>
      </div>

      <div className="barra">{props.filtros}</div>

      {consulta.isPending && (
        <p className="vazio" aria-busy="true">
          {t('app.carregando')}
        </p>
      )}

      {consulta.isError && !temDados && (
        <div className="estado estado--erro" role="alert">
          {status === 403 && <p>{t('cardpage.semPermissao')}</p>}
          {status === 400 && <p>{t('cardpage.filtroInvalido')}</p>}
          {status !== 403 && status !== 400 && (
            <p>
              {t('cardpage.erro')}
              {status ? ` (HTTP ${status})` : ''}
            </p>
          )}
          {status !== 403 && (
            <button type="button" className="btn" onClick={() => void consulta.refetch()}>
              {t('cardpage.tentarDeNovo')}
            </button>
          )}
        </div>
      )}

      {temDados && (
        <div className={consulta.isFetching ? 'conteudo conteudo--atualizando' : 'conteudo'} aria-busy={consulta.isFetching}>
          {bloqueantes.length > 0 && (
            <div className="aviso aviso--bloqueante" role="alert">
              <strong>{t('cardpage.avisos.bloqueante')}</strong>
              <p>{t('cardpage.avisos.bloqueante.ajuda')}</p>
              <ul>
                {bloqueantes.map((a) => (
                  <li key={`${a.codigo}:${JSON.stringify(a.params ?? {})}`}>{textoDoAviso(a, t)}</li>
                ))}
              </ul>
            </div>
          )}
          {informativos.length > 0 && (
            <div className="aviso" role="status">
              <strong>{t('cardpage.avisos')}</strong>
              <ul>
                {informativos.map((a) => (
                  <li key={`${a.codigo}:${JSON.stringify(a.params ?? {})}`}>{textoDoAviso(a, t)}</li>
                ))}
              </ul>
            </div>
          )}
          {props.children}
        </div>
      )}
    </div>
  )
}
