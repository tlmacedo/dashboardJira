import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useLang } from '../../i18n/LangProvider'
import { Cartao, type EstadoDoCartao } from '../../shared/Cartao'
import { CardPage } from '../../shared/CardPage'
import { ContagensEmBarras } from '../../shared/ContagensEmBarras'
import { baixarCsv } from '../../shared/csv'
import { dataCurta, inteiro, numero, percentual } from '../../shared/format'
import { GavetaDeTasks } from '../../shared/GavetaDeTasks'
import { Ocupacao } from '../../shared/Ocupacao'
import { temBloqueio } from '../../shared/useCard'
import '../../shared/pessoas.css'
import '../../shared/tabela.css'
import { fetchCentral, SIMBOLO_DO_ESTADO, type ItemCentral, type QuandoDto } from './api'
import './inicio.css'

type Gaveta = { cartaoId: string; segmento: string; titulo: string }

const SEM_FILTROS = new URLSearchParams()

/** O estado de um indicador no cartão KPI; `indisponivel` não tem estado (o valor já é "—"). */
function estadoDoCartao(e: string, t: (k: string) => string): { tipo: EstadoDoCartao; texto: string } | undefined {
  return e === 'ok' || e === 'atencao' || e === 'problema' || e === 'neutro' ? { tipo: e, texto: t(`central.estado.${e}`) } : undefined
}

/** Cartão da Central de cada KPI: o id do catálogo é o do cartão que abre as tasks. */
const CARTAO_DO_KPI: Record<string, string> = { ABERTAS: 'VG-01', VENCIDAS: 'VG-05', RISCO_ALTO: 'VG-08', NO_PRAZO: 'VG-04', NO_PRAZO_AJUSTADO: 'VG-04' }

/**
 * Central de atenção (coordenação e gerente): a porta de entrada. Layout editorial: KPIs no topo, os indicadores que pedem
 * atenção em colunas (o nome leva à tela do card de destino e o número abre as tasks, o MESMO número do card) e os gráficos por
 * último. "Sem atualização" e "Paradas no status" são linhas separadas. Sem ranking de pessoas.
 */
export function Central() {
  const { t, lang } = useLang()
  const consulta = useQuery({ queryKey: ['central'], queryFn: fetchCentral, placeholderData: keepPreviousData, retry: false })
  const dto = consulta.data
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)
  const bloqueado = temBloqueio(dto?.avisos)
  const nomeDoItem = (i: ItemCentral) => t(`central.item.${i.item}`)
  const abrir = (i: ItemCentral, titulo: string) =>
    i.cartao && i.segmento && !bloqueado && (i.n ?? 0) > 0 ? () => setGaveta({ cartaoId: i.cartao as string, segmento: i.segmento as string, titulo }) : undefined

  const emAtencao = (dto?.itens ?? []).filter((i) => (i.estado === 'problema' || i.estado === 'atencao') && (i.n ?? 0) > 0).length

  function exportar() {
    if (!dto) return
    baixarCsv('central.csv', [
      [`# ${t('nav.central')}`],
      [],
      [t('central.col.indicador'), t('central.col.valor'), t('central.col.estado')],
      ...dto.kpis.map((i) => [t(`central.kpi.${i.item}`), i.valor, t(`central.estado.${i.estado}`)]),
      ...dto.itens.map((i) => [nomeDoItem(i), i.valor, t(`central.estado.${i.estado}`)]),
    ])
  }

  function quando(q: QuandoDto, nivel: 'p50' | 'p85' | 'p95') {
    const data = q[nivel]
    const semanas = q[nivel === 'p50' ? 'semanasP50' : nivel === 'p85' ? 'semanasP85' : 'semanasP95']
    if (!data) return t('central.backlog.alem')
    return `${dataCurta(data, lang)} (${t('central.backlog.semanas').replace('{n}', String(semanas ?? ''))})`
  }

  return (
    <>
      <CardPage
        titulo={t('nav.central')}
        pergunta={t('central.pergunta')}
        filtros={null}
        consulta={consulta}
        avisos={dto?.avisos}
        aoExportar={exportar}
      >
        {dto && (
          <>
            <div role="group" aria-label={t('central.kpis')} className="cardpage-kpis">
              {dto.kpis.map((i) => {
                const nome = t(`central.kpi.${i.item}`)
                return (
                  <Cartao
                    key={i.item} id={CARTAO_DO_KPI[i.item] ?? 'VG-01'} ajudaComTitulo titulo={nome}
                    valor={bloqueado || i.n === null ? '—' : i.universo !== null ? `${inteiro(i.n, lang)}/${inteiro(i.universo, lang)}` : inteiro(i.n, lang)}
                    legenda={i.universo ? percentual((i.n ?? 0) / i.universo, lang) : undefined}
                    estado={estadoDoCartao(i.estado, t)}
                    aoAbrir={abrir(i, nome)}
                  />
                )
              })}
            </div>

            <Cartao id="VG-09" variante="lista" nivelDoTitulo={2} idDoTitulo="h-central-itens" titulo={t('central.itens')}>
              <p className="inicio-resumo">{t('central.resumo').replace('{n}', inteiro(emAtencao, lang))}</p>
              <ul className="inicio-lista" aria-labelledby="h-central-itens">
                {dto.itens.map((i) => {
                  const nome = nomeDoItem(i)
                  const aoAbrir = abrir(i, nome)
                  return (
                    <li key={i.item} className={`inicio-item inicio-item--${i.estado}`}>
                      <span className="inicio-item__simbolo" aria-hidden="true">{SIMBOLO_DO_ESTADO[i.estado] ?? '■'}</span>
                      <Link className="inicio-item__nome" to={`/${i.rota}`}>{nome}</Link>
                      <span className="inicio-item__valor">
                        {aoAbrir ? (
                          <button type="button" className="contagem-botao" aria-label={`${nome}: ${i.valor}`} onClick={aoAbrir}>
                            {i.universo !== null ? `${inteiro(i.n ?? 0, lang)}/${inteiro(i.universo, lang)}` : inteiro(i.n ?? 0, lang)}
                          </button>
                        ) : bloqueado || i.n === null ? (
                          '—'
                        ) : i.universo !== null ? (
                          `${inteiro(i.n, lang)}/${inteiro(i.universo, lang)}`
                        ) : (
                          inteiro(i.n, lang)
                        )}
                      </span>
                      <span className="so-leitor"> ({t(`central.estado.${i.estado}`)})</span>
                    </li>
                  )
                })}
              </ul>
            </Cartao>

            <div className="cardpage-duas-colunas">
              <Cartao id="VG-10" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-central-entregas" titulo={t('central.entregas')}>
                {dto.entregasPorSemana.length === 0 ? (
                  <p className="vazio">{t('central.entregas.vazia')}</p>
                ) : (
                  <ContagensEmBarras
                    rotulo={t('central.entregas')} cabecalho={t('central.entregas.semana')} barras indisponivel={bloqueado}
                    itens={dto.entregasPorSemana.map((s) => ({ chave: `semana:${s.semana}`, rotulo: dataCurta(s.semana, lang), n: s.n }))}
                    aoAbrir={(chave) => setGaveta({ cartaoId: 'VG-10', segmento: chave, titulo: `${t('central.entregas')}: ${dataCurta(chave.replace('semana:', ''), lang)}` })}
                  />
                )}
              </Cartao>

              <Cartao id="VG-11" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-central-backlog" titulo={t('central.backlog')}>
                {dto.backlogTermina === null || dto.backlog === null ? (
                  <p className="vazio">{t('central.backlog.indisponivel')}</p>
                ) : (
                  <>
                    <dl className="inicio-ficha">
                      <dt>{t('central.backlog.tamanho')}</dt>
                      <dd>
                        {dto.backlog > 0 && !bloqueado ? (
                          <button type="button" className="contagem-botao" aria-label={`${t('central.backlog.tamanho')}: ${dto.backlog}`} onClick={() => setGaveta({ cartaoId: 'VG-11', segmento: 'backlog', titulo: t('central.backlog') })}>
                            {inteiro(dto.backlog, lang)}
                          </button>
                        ) : (
                          inteiro(dto.backlog, lang)
                        )}
                      </dd>
                      <dt>P50</dt>
                      <dd>{quando(dto.backlogTermina, 'p50')}</dd>
                      <dt>P85</dt>
                      <dd>{quando(dto.backlogTermina, 'p85')}</dd>
                      <dt>P95</dt>
                      <dd>{quando(dto.backlogTermina, 'p95')}</dd>
                    </dl>
                    <p className="legenda-meta">
                      {t('central.backlog.confianca').replace('{c}', t(`central.backlog.confianca.${dto.confiancaDoBacklog ?? 'baixa'}`))}
                    </p>
                  </>
                )}
              </Cartao>
            </div>

            <Cartao id="VG-12" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-central-squads" titulo={t('central.squads')}>
              {dto.squads.length === 0 ? (
                <p className="vazio">{t('central.squads.vazia')}</p>
              ) : (
                <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-central-squads">
                  <table className="tabela" aria-labelledby="h-central-squads">
                    <thead>
                      <tr>
                        <th scope="col">{t('central.col.squad')}</th>
                        <th scope="col" className="num">{t('central.col.pessoas')}</th>
                        <th scope="col">{t('central.col.ocupacao')}</th>
                        <th scope="col" className="num">{t('central.col.jain')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {dto.squads.map((s) => (
                        <tr key={s.squad}>
                          <th scope="row"><Link to={`/squad?squad=${encodeURIComponent(s.squad)}`}>{s.squad}</Link></th>
                          <td className="num">{inteiro(s.pessoas, lang)}</td>
                          <td><Ocupacao o={s.ocupacaoMedia} compacta /></td>
                          <td className="num">{s.jain === null ? '—' : numero(s.jain, lang, 2)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              <p className="legenda-meta">{t('central.squads.nota')}</p>
            </Cartao>
          </>
        )}
      </CardPage>
      {gaveta && (
        <GavetaDeTasks cartaoId={gaveta.cartaoId} segmento={gaveta.segmento} titulo={gaveta.titulo} filtros={SEM_FILTROS} aoFechar={() => setGaveta(null)} />
      )}
    </>
  )
}
