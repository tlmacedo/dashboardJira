import { Link } from 'react-router-dom'
import { ErroDaApi } from '../api/client'
import { useLang } from '../i18n/LangProvider'
import type { Aviso } from './useCard'
import '../screens/TempoEmStatus/tempo.css'
import './cardpage.css'

/**
 * Os estados em que uma tela de dados pode estar. `filtro-invalido` (400) é um acréscimo ao desenho original: o backend
 * recusa o filtro e a tela diz o que revisar, em vez de um "erro" genérico que pede tentar de novo (não adiantaria).
 */
export type Estado =
  | { tipo: 'carregando' }
  | { tipo: 'vazio' }
  | { tipo: 'erro'; tentarDeNovo: () => void }
  | { tipo: 'filtro-invalido' }
  | { tipo: 'sem-permissao' }
  | { tipo: 'sessao' }
  | { tipo: 'incompleto'; avisos: Aviso[] }
  | { tipo: 'ok' }

/** O pedaço da consulta (TanStack Query) que decide o estado. */
export type ConsultaParaEstado = { data: unknown; isPending: boolean; isError: boolean; error: unknown; refetch: () => unknown }

/**
 * Estado de uma consulta. Erro com dado anterior na tela NÃO derruba a tela (o dado antigo continua). Sem dado: pendente é
 * carregando; 403 é sem permissão; 401 é sessão (o diálogo de sessão expirada já aparece); 400 é filtro inválido; qualquer
 * outro erro é erro. Com dado: vazio (quando [vazio] diz que sim), incompleto (aviso bloqueante) ou ok.
 */
export function estadoDe(q: ConsultaParaEstado, vazio?: (dados: unknown) => boolean, avisos: Aviso[] = []): Estado {
  const temDados = q.data !== undefined && q.data !== null
  if (!temDados) {
    if (q.isError) {
      const status = q.error instanceof ErroDaApi ? q.error.status : null
      if (status === 403) return { tipo: 'sem-permissao' }
      if (status === 401) return { tipo: 'sessao' }
      if (status === 400) return { tipo: 'filtro-invalido' }
      return { tipo: 'erro', tentarDeNovo: () => void q.refetch() }
    }
    return { tipo: 'carregando' }
  }
  const bloqueantes = avisos.filter((a) => a.gravidade === 'bloqueante')
  if (bloqueantes.length > 0) return { tipo: 'incompleto', avisos: bloqueantes }
  if (vazio?.(q.data)) return { tipo: 'vazio' }
  return { tipo: 'ok' }
}

export type EstadoDaTelaProps = {
  estado: Estado
  /** Papéis que podem ver a tela (para a mensagem de "sem permissão"); vazio = mensagem genérica. */
  papel: string[]
  /** Presente = botão "Limpar filtros" no estado vazio. */
  aoLimparFiltros?: () => void
  /** O papel de quem vê a tela: só o gerente recebe o atalho para a Qualidade dos dados. */
  papelAtual?: string
}

/**
 * Mostra o estado de tela que NÃO é "ok": esqueleto para leitor de tela, vazio com "Limpar filtros", erro com "Tentar de
 * novo" (nunca texto técnico: sem status HTTP nem mensagem de exceção), sem permissão, sessão e dados incompletos. Retorna
 * nada para `ok`. O texto é sempre do dicionário de tradução.
 */
export function EstadoDaTela(props: EstadoDaTelaProps) {
  const { t } = useLang()
  const { estado } = props
  switch (estado.tipo) {
    case 'ok':
      return null
    case 'carregando':
      return (
        <div className="estado-carregando" aria-busy="true">
          <p className="so-leitor">{t('app.carregando')}</p>
          <div className="esqueleto" aria-hidden="true" />
        </div>
      )
    case 'vazio':
      return (
        <div className="estado" role="status">
          <p>{t('estado.vazio')}</p>
          {props.aoLimparFiltros && (
            <button type="button" className="btn" onClick={props.aoLimparFiltros}>
              {t('estado.limparFiltros')}
            </button>
          )}
        </div>
      )
    case 'erro':
      return (
        <div className="estado estado--erro" role="alert">
          <p>{t('estado.erro')}</p>
          <button type="button" className="btn" onClick={estado.tentarDeNovo}>
            {t('cardpage.tentarDeNovo')}
          </button>
        </div>
      )
    case 'filtro-invalido':
      return (
        <div className="estado estado--erro" role="alert">
          <p>{t('cardpage.filtroInvalido')}</p>
        </div>
      )
    case 'sem-permissao':
      return (
        <div className="estado estado--erro" role="alert">
          <p>{t('cardpage.semPermissao')}</p>
          {props.papel.length > 0 && <p>{t('estado.telaParaPapeis').replace('{papeis}', props.papel.map((p) => t(`role.${p}`)).join(', '))}</p>}
        </div>
      )
    case 'sessao':
      return (
        <div className="estado estado--erro" role="alert">
          <p>{t('estado.sessao')}</p>
        </div>
      )
    case 'incompleto':
      return (
        <div className="aviso aviso--bloqueante" role="alert">
          <strong>{t('cardpage.avisos.bloqueante')}</strong>
          <p>{t('cardpage.avisos.bloqueante.ajuda')}</p>
          {props.papelAtual === 'GERENTE' && <Link to="/qualidade">{t('estado.verQualidade')}</Link>}
        </div>
      )
  }
}
