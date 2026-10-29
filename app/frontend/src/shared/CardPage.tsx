import type { ReactNode } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { useLang } from '../i18n/LangProvider'
import { itemPorRota } from '../nav/nav'
import { useRole } from '../shell/RoleContext'
import { EstadoDaTela, estadoDe } from './EstadoDaTela'
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
  /** A resposta não tem nada a mostrar? Se sim, aparece o estado "vazio" (com "Limpar filtros") no lugar do conteúdo. */
  vazio?: (dados: unknown) => boolean
  /** Conteúdo da tela; só aparece quando há dados. */
  children: ReactNode
}

/**
 * Moldura das telas de trabalho e prazos: título e pergunta, barra de filtros, os estados de tela padronizados
 * (`EstadoDaTela`: carregando, vazio, erro com "tentar de novo", sem permissão, sessão), avisos dos dados e o conteúdo.
 * Aviso bloqueante vem em destaque; o conteúdo mostra "—" nos números (ver `temBloqueio`) e o gerente recebe o atalho para
 * a Qualidade dos dados. Nenhuma tela mostra texto técnico (status HTTP, mensagem de exceção).
 */
export function CardPage(props: CardPageProps) {
  const { t } = useLang()
  const { consulta } = props
  const [, definirParametros] = useSearchParams()
  const papelAtual = useRole()
  const rolesDaTela = itemPorRota(useLocation().pathname)?.roles
  const papeisDaTela = Array.isArray(rolesDaTela) ? rolesDaTela : []
  const temDados = consulta.data !== undefined && consulta.data !== null
  const avisos = props.avisos ?? []
  const bloqueantes = avisos.filter((a) => a.gravidade === 'bloqueante')
  const informativos = avisos.filter((a) => a.gravidade !== 'bloqueante')
  const estado = estadoDe(consulta, props.vazio)
  const limparFiltros = () => definirParametros(new URLSearchParams())

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

      {estado.tipo !== 'ok' && (
        <EstadoDaTela estado={estado} papel={papeisDaTela} aoLimparFiltros={limparFiltros} papelAtual={papelAtual} />
      )}

      {temDados && estado.tipo !== 'vazio' && (
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
              {papelAtual === 'GERENTE' && <Link to="/qualidade">{t('estado.verQualidade')}</Link>}
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
