import { useQuery } from '@tanstack/react-query'
import { createPortal } from 'react-dom'
import { ErroDaApi } from '../../api/client'
import { useLang } from '../../i18n/LangProvider'
import { baixarCsv } from '../../shared/csv'
import { useModalAcessivel } from '../../shared/useModalAcessivel'
import { TabelaDeHistorico, textoDaAutorizacao } from '../../shared/TabelaDeHistorico'
import { fetchHistorico } from './api'
import '../../shared/gaveta.css'
import '../../shared/tabela.css'

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
  const { t } = useLang()
  const { chave, aoFechar } = props
  const consulta = useQuery({ queryKey: ['auditoria-historico', chave], queryFn: () => fetchHistorico(chave), retry: false })

  const { portal, raiz, fechar, aoTeclar } = useModalAcessivel(aoFechar)

  const dto = consulta.data
  const status = consulta.error instanceof ErroDaApi ? consulta.error.status : null
  const titulo = t('auditoria.historico.titulo').replace('{key}', chave)

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
        textoDaAutorizacao(l, t),
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
          {dto && dto.linhas.length > 0 && <TabelaDeHistorico linhas={dto.linhas} rotulo={titulo} />}
        </div>
      </div>
    </div>,
    portal,
  )
}
