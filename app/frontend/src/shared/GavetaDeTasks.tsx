import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { createPortal } from 'react-dom'
import { ErroDaApi } from '../api/client'
import { useLang } from '../i18n/LangProvider'
import { inteiro } from './format'
import { linkSeguro } from './linkSeguro'
import { exportarTasksCsv, ListaDeTasks } from './ListaDeTasks'
import { fetchTasksDoCartao } from './tasksApi'
import { TarefaDrawer } from './TarefaDrawer'
import { urlDoDetalhe } from './urlDoDetalhe'
import { useModalAcessivel } from './useModalAcessivel'
import './cardpage.css' // estilos dos botões .contagem-botao e .ordenar-botao, também usados dentro da gaveta (portal)
import './gaveta.css'
import './tabela.css'

/** Acima disso o backend não gera link para o Jira (a URL ficaria longa demais): a gaveta avisa e oferece o CSV. */
const LIMITE_DO_LINK = 100

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
 * A chave de cada linha abre a gaveta da task (`TarefaDrawer`) por cima desta; "Abrir em nova aba" leva o mesmo recorte
 * para a página `/detalhe`.
 */
export function GavetaDeTasks(props: GavetaDeTasksProps) {
  const { t, lang } = useLang()
  const { cartaoId, segmento, filtros, aoFechar, resumoDosFiltros } = props
  const { portal, raiz, fechar, aoTeclar } = useModalAcessivel(aoFechar)
  const [tarefa, setTarefa] = useState<string | null>(null)
  const chaveDosFiltros = filtros.toString()

  const consulta = useQuery({
    queryKey: ['tasks-do-cartao', cartaoId, segmento, chaveDosFiltros],
    queryFn: () => fetchTasksDoCartao(cartaoId, segmento, new URLSearchParams(chaveDosFiltros)),
    retry: false,
  })

  const dados = consulta.data
  const linhas = dados?.linhas ?? []
  const titulo = props.titulo ?? (dados ? t(dados.tituloChave) : '')
  const semPermissao = consulta.error instanceof ErroDaApi && consulta.error.status === 403
  const link = linkSeguro(dados?.linkJira ?? null)
  const acimaDoLimite = dados !== undefined && dados.total > LIMITE_DO_LINK && link === null

  function exportar() {
    if (!dados) return
    exportarTasksCsv({ arquivo: `tasks-${cartaoId}.csv`, titulo, dados, resumoDosFiltros, t })
  }

  function abrirEmNovaAba() {
    // `noopener`: a nova aba não recebe `window.opener`. A URL é um caminho do próprio painel.
    window.open(urlDoDetalhe(cartaoId, segmento, new URLSearchParams(chaveDosFiltros)), '_blank', 'noopener')
  }

  return (
    <>
      {createPortal(
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
                <button type="button" className="btn" onClick={abrirEmNovaAba} disabled={!dados}>
                  {t('gaveta.novaAba')}
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
              {dados && linhas.length > 0 && <ListaDeTasks dados={dados} titulo={titulo} aoAbrirTask={setTarefa} />}
            </div>
          </div>
        </div>,
        portal,
      )}
      {tarefa && <TarefaDrawer chave={tarefa} aoFechar={() => setTarefa(null)} />}
    </>
  )
}
