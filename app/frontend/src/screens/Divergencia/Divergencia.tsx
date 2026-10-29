import { AguardandoDpo, ehAguardandoDpo } from '../../shared/AguardandoDpo'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useMemo, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ErroDaApi } from '../../api/client'
import { useLang } from '../../i18n/LangProvider'
import { Cartao } from '../../shared/Cartao'
import { dataCurta, inteiro, percentual } from '../../shared/format'
import { GavetaDeTasks } from '../../shared/GavetaDeTasks'
import { textoDoAviso } from '../Auditoria/aviso'
import { fetchTaskReview, filtrosDaUrl, paramsDe, type FiltrosDivergencia, type LinhaDeDivergencia } from './api'
import '../../shared/tabela.css'
import '../TempoEmStatus/tempo.css'
import '../Auditoria/auditoria.css'

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

type Gaveta = { cartaoId: string; segmento: string; titulo: string }

/**
 * Divergência Task × Review: pares de task e Review (ligados pelo link Blocks) com vencimentos diferentes. Só mostra e
 * leva ao Jira; não há botão nem rota para corrigir (escrita no Jira pelo painel é o Plano 16, futuro).
 */
export function Divergencia() {
  const { t, lang } = useLang()
  const [sp, setSp] = useSearchParams()
  const filtros = useMemo(() => filtrosDaUrl(sp), [sp])
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)

  const consulta = useQuery({
    queryKey: ['task-review', paramsDe(filtros).toString()],
    queryFn: () => fetchTaskReview(paramsDe(filtros)),
    placeholderData: keepPreviousData,
    retry: false,
  })
  const dto = consulta.data
  const status = consulta.error instanceof ErroDaApi ? consulta.error.status : null

  function aplicar(novo: FiltrosDivergencia) {
    setSp(paramsDe(novo), { replace: false })
  }

  const resumo =
    [filtros.squad && `${t('divergencia.filtro.squad')}: ${filtros.squad}`, filtros.pessoa && `${t('divergencia.filtro.pessoa')}: ${filtros.pessoa}`]
      .filter(Boolean)
      .join(' | ') || t('auditoria.csv.nenhum')

  return (
    <div className="tempo auditoria">
      <div className="top">
        <div>
          <h1>{t('nav.divergencia')}</h1>
          <p className="sub">{t('divergencia.sub')}</p>
        </div>
      </div>

      <p role="note" className="aud-aviso">
        <span aria-hidden="true">●</span> {t('auditoria.aviso')}
      </p>

      <div className="barra">
        <FormFiltros key={paramsDe(filtros).toString()} filtros={filtros} aoAplicar={aplicar} />
      </div>

      {consulta.isPending && (
        <p className="vazio" aria-busy="true">
          {t('app.carregando')}
        </p>
      )}

      {consulta.isError && !dto && ehAguardandoDpo(consulta.error) && <AguardandoDpo />}

      {consulta.isError && !dto && !ehAguardandoDpo(consulta.error) && (
        <div className="estado estado--erro" role="alert">
          {status === 403 && <p>{t('divergencia.semPermissao')}</p>}
          {status === 400 && <p>{t('divergencia.filtroInvalido')}</p>}
          {status !== 403 && status !== 400 && (
            <p>
              {t('divergencia.erro')}
              {status ? ` (HTTP ${status})` : ''}
            </p>
          )}
          {status !== 403 && (
            <button type="button" className="btn" onClick={() => consulta.refetch()}>
              {t('divergencia.tentarDeNovo')}
            </button>
          )}
        </div>
      )}

      {dto && (
        <div className={consulta.isFetching ? 'conteudo conteudo--atualizando' : 'conteudo'} aria-busy={consulta.isFetching}>
          {dto.avisos.length > 0 && (
            <div className="aviso" role="status">
              <strong>{t('divergencia.avisos')}</strong>
              <ul>
                {dto.avisos.map((a) => (
                  <li key={`${a.codigo}:${JSON.stringify(a.params ?? {})}`}>{textoDoAviso(a, t)}</li>
                ))}
              </ul>
            </div>
          )}

          <div role="group" aria-label={t('divergencia.indicadores')} className="aud-kpis">
            <Cartao
              id="DV-01"
              titulo={t('divergencia.kpi.percentual')}
              valor={dto.percentual == null ? '—' : percentual(dto.percentual / 100, lang)}
              simboloDoValor={dto.divergentes > 0 ? '▲' : undefined}
              legenda={t('divergencia.kpi.percentual.dica')
                .replace('{n}', inteiro(dto.divergentes, lang))
                .replace('{total}', inteiro(dto.pares, lang))}
              aoAbrir={dto.divergentes > 0 ? () => setGaveta({ cartaoId: 'DV-01', segmento: 'divergentes', titulo: t('divergencia.kpi.percentual') }) : undefined}
            />
            <Cartao
              id="DV-01"
              titulo={t('divergencia.kpi.pares')}
              valor={inteiro(dto.pares, lang)}
              legenda={t('divergencia.kpi.pares.dica')}
              aoAbrir={dto.pares > 0 ? () => setGaveta({ cartaoId: 'DV-01', segmento: 'pares', titulo: t('divergencia.kpi.pares') }) : undefined}
            />
          </div>

          <Lista linhas={dto.divergencias} />
        </div>
      )}

      {gaveta && (
        <GavetaDeTasks
          cartaoId={gaveta.cartaoId}
          segmento={gaveta.segmento}
          titulo={gaveta.titulo}
          filtros={paramsDe(filtros)}
          resumoDosFiltros={resumo}
          aoFechar={() => setGaveta(null)}
        />
      )}
    </div>
  )
}

function FormFiltros(props: { filtros: FiltrosDivergencia; aoAplicar: (f: FiltrosDivergencia) => void }) {
  const { t } = useLang()
  const [rascunho, setRascunho] = useState<FiltrosDivergencia>(props.filtros)

  function enviar(e: FormEvent) {
    e.preventDefault()
    props.aoAplicar(rascunho)
  }

  return (
    <form className="filtros" onSubmit={enviar} aria-label={t('divergencia.filtros')}>
      <label className="campo">
        <span>{t('divergencia.filtro.squad')}</span>
        <input type="text" maxLength={100} value={rascunho.squad} onChange={(e) => setRascunho({ ...rascunho, squad: e.target.value })} />
      </label>
      <label className="campo">
        <span>{t('divergencia.filtro.pessoa')}</span>
        <input type="text" maxLength={100} value={rascunho.pessoa} onChange={(e) => setRascunho({ ...rascunho, pessoa: e.target.value })} />
      </label>
      <button type="submit" className="btn btn--primario">
        {t('divergencia.aplicar')}
      </button>
    </form>
  )
}

/** A chave como link para o Jira (nova aba, sem `opener`) quando há URL segura; senão, só o texto. */
function ChaveComLink(props: { chave: string; url: string | null }) {
  const { t } = useLang()
  const url = linkSeguro(props.url)
  if (!url) return <span className="mono">{props.chave}</span>
  return (
    <a href={url} target="_blank" rel="noopener noreferrer" className="aud-jira">
      <span className="mono">{props.chave}</span>
      <span className="aud-detalhe">
        {t('divergencia.abrirNoJira')} <span aria-hidden="true">↗</span>
      </span>
    </a>
  )
}

function Lista({ linhas }: { linhas: LinhaDeDivergencia[] }) {
  const { t, lang } = useLang()
  // Ordem por chave da task (a do backend): nunca por pessoa.
  const vencimento = (v: string | null) => (v ? dataCurta(v, lang) : t('divergencia.semVencimento'))
  return (
    <Cartao id="DV-01" variante="lista" nivelDoTitulo={2} idDoTitulo="h-div-lista" titulo={t('divergencia.lista')}>
      {linhas.length === 0 ? (
        <p className="vazio">{t('divergencia.lista.vazio')}</p>
      ) : (
        <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-div-lista">
          <table className="tabela" aria-labelledby="h-div-lista">
            <thead>
              <tr>
                <th scope="col">{t('divergencia.col.task')}</th>
                <th scope="col">{t('divergencia.col.review')}</th>
                <th scope="col">{t('divergencia.col.vencTask')}</th>
                <th scope="col">{t('divergencia.col.vencReview')}</th>
                <th scope="col">{t('divergencia.col.responsavelTask')}</th>
                <th scope="col">{t('divergencia.col.responsavelReview')}</th>
                <th scope="col">{t('divergencia.col.estado')}</th>
              </tr>
            </thead>
            <tbody>
              {linhas.map((l) => (
                <tr key={`${l.task}:${l.review}`}>
                  <th scope="row">
                    <ChaveComLink chave={l.task} url={l.urlTask} />
                  </th>
                  <td>
                    <ChaveComLink chave={l.review} url={l.urlReview} />
                  </td>
                  <td className="num">{vencimento(l.vencimentoTask)}</td>
                  <td className="num">{vencimento(l.vencimentoReview)}</td>
                  <td>{l.responsavelTask ?? '—'}</td>
                  <td>{l.responsavelReview ?? '—'}</td>
                  <td>
                    <span aria-hidden="true">▲</span> {t('divergencia.estado')}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Cartao>
  )
}
