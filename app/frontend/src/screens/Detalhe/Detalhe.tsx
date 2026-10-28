import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ErroDaApi } from '../../api/client'
import { useLang } from '../../i18n/LangProvider'
import { inteiro } from '../../shared/format'
import { linkSeguro } from '../../shared/linkSeguro'
import { exportarTasksCsv, ListaDeTasks } from '../../shared/ListaDeTasks'
import { fetchTasksDoCartao } from '../../shared/tasksApi'
import { TarefaDrawer } from '../../shared/TarefaDrawer'
import '../../shared/cardpage.css'
import '../../shared/tabela.css'
import '../TempoEmStatus/tempo.css'

/** Id de cartão do contrato (`AB-01`): qualquer outro formato nem chega à rede. */
const FORMATO_DO_CARTAO = /^[A-Z]{2}-\d{2}$/
const MAX_SEGMENTO = 200
/** Parâmetros da URL que descrevem o recorte; o resto é o filtro da tela de origem e vai para o backend. */
const PARAMETROS_DO_RECORTE = ['cartao', 'segmento', 'titulo']

/**
 * Detalhe em tela cheia (`/detalhe?cartao=...&segmento=...&<filtros>`): a MESMA lista da gaveta de tasks, pela mesma
 * chamada (`/api/cartoes/{id}/tasks`) e com o mesmo CSV. É para onde "Abrir em nova aba" leva. O título da URL é só texto.
 */
export function Detalhe() {
  const { t, lang } = useLang()
  const [sp] = useSearchParams()
  const cartao = sp.get('cartao') ?? ''
  const segmento = sp.get('segmento') ?? ''
  const tituloDaUrl = sp.get('titulo')?.slice(0, 200) ?? ''
  const valido = FORMATO_DO_CARTAO.test(cartao) && segmento.length > 0 && segmento.length <= MAX_SEGMENTO
  const filtros = new URLSearchParams(sp)
  for (const p of PARAMETROS_DO_RECORTE) filtros.delete(p)
  const chaveDosFiltros = filtros.toString()
  const [tarefa, setTarefa] = useState<string | null>(null)

  const consulta = useQuery({
    queryKey: ['tasks-do-cartao', cartao, segmento, chaveDosFiltros],
    queryFn: () => fetchTasksDoCartao(cartao, segmento, new URLSearchParams(chaveDosFiltros)),
    enabled: valido,
    retry: false,
  })
  const dados = consulta.data
  const titulo = tituloDaUrl || (dados ? t(dados.tituloChave) : '')
  const link = linkSeguro(dados?.linkJira ?? null)
  const semPermissao = consulta.error instanceof ErroDaApi && consulta.error.status === 403

  return (
    <div className="tempo cardpage">
      <div className="top">
        <div>
          <h1>{titulo || t('gaveta.col.chave')}</h1>
          <p className="sub">{dados ? t('gaveta.total').replace('{n}', inteiro(dados.total, lang)) : t('detalhe.pergunta')}</p>
          {dados && (
            <p className="gaveta-definicao">
              <strong>{t('gaveta.definicao')}:</strong> {t(dados.definicaoChave)}
            </p>
          )}
        </div>
        <div className="top-acoes">
          <button
            type="button"
            className="btn"
            disabled={!dados || dados.linhas.length === 0}
            onClick={() => dados && exportarTasksCsv({ arquivo: `tasks-${cartao}.csv`, titulo, dados, resumoDosFiltros: chaveDosFiltros, t })}
          >
            {t('gaveta.csv')}
          </button>
          {link && (
            <a className="gaveta-link" href={link} target="_blank" rel="noopener noreferrer">
              {t('gaveta.abrirNoJira')} <span aria-hidden="true">↗</span>
            </a>
          )}
        </div>
      </div>

      {!valido && <p className="estado estado--erro">{t('detalhe.semParametros')}</p>}
      {valido && consulta.isPending && (
        <p className="vazio" aria-busy="true">
          {t('app.carregando')}
        </p>
      )}
      {semPermissao && (
        <p className="estado estado--erro" role="alert">
          {t('cardpage.semPermissao')}
        </p>
      )}
      {consulta.isError && !semPermissao && (
        <p className="estado estado--erro" role="alert">
          {t('gaveta.erro')}{' '}
          <button type="button" className="btn" onClick={() => consulta.refetch()}>
            {t('gaveta.tentarDeNovo')}
          </button>
        </p>
      )}
      {dados && dados.linhas.length === 0 && <p className="vazio">{t('gaveta.vazio')}</p>}
      {dados && dados.linhas.length > 0 && <ListaDeTasks dados={dados} titulo={titulo} aoAbrirTask={setTarefa} />}
      {tarefa && <TarefaDrawer chave={tarefa} aoFechar={() => setTarefa(null)} />}
    </div>
  )
}
