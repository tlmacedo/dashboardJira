import { useState } from 'react'
import { useLang } from '../../i18n/LangProvider'
import { Cartao } from '../../shared/Cartao'
import { CardPage } from '../../shared/CardPage'
import { baixarCsv } from '../../shared/csv'
import { Filtros, resumoDosFiltros, useFiltrosDaUrl } from '../../shared/Filtros'
import { dataCurta, inteiro, numero } from '../../shared/format'
import { GavetaDeTasks } from '../../shared/GavetaDeTasks'
import { temBloqueio, useCard } from '../../shared/useCard'
import type { AgingWipDto } from './api'
import { GraficoDeIdade } from './GraficoDeIdade'
import '../../shared/tabela.css'

type Gaveta = { cartaoId: string; segmento: string; titulo: string }

/**
 * Envelhecimento por etapa (AW-01 e AW-02): para cada etapa do fluxo, as tasks em andamento como pontos com a idade desde a
 * última entrada na etapa, contra a faixa normal (P50 a P85) da própria etapa. Acima do P85 leva ▲ e o texto "acima do
 * normal"; etapa sem amostra mínima fica sem faixa e com aviso. O ponto não traz pessoa. O clique no ponto (abrir a task)
 * espera a gaveta universal do Plano 10; hoje cada etapa e cada contagem de "acima do normal" abrem a lista.
 */
export function AgingWip() {
  const { t, lang } = useLang()
  const [sp, aplicar] = useFiltrosDaUrl()
  const consulta = useCard<AgingWipDto>('aging-wip', sp)
  const dto = consulta.data
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)
  const [verPontos, setVerPontos] = useState(false)
  const bloqueado = temBloqueio(dto?.avisos)
  const traco = '—'
  const resumo = resumoDosFiltros(sp, t, (iso) => dataCurta(iso, lang))
  const unidade = t(dto?.unit === 'corrido' ? 'abertas.diasCorridos' : 'abertas.diasUteis')
  const abrir = (cartaoId: string, segmento: string, titulo: string) => setGaveta({ cartaoId, segmento, titulo })
  const comAbrir = (n: number, segmento: string, titulo: string) =>
    !bloqueado && n > 0 ? () => abrir('AW-02', segmento, titulo) : undefined
  const num = (n: number) => (bloqueado ? traco : inteiro(n, lang))
  const situacaoDe = (acima: boolean, temFaixa: boolean): 'acima' | 'normal' | 'semFaixa' => (!temFaixa ? 'semFaixa' : acima ? 'acima' : 'normal')
  const simboloDe = { acima: '▲', normal: '✓', semFaixa: '■' } as const

  const linhasDePontos = (dto?.etapas ?? []).flatMap((e) =>
    e.pontos.map((p) => ({ etapa: e.status, key: p.key, idade: p.idade, situacao: situacaoDe(p.acima, e.faixa != null) })),
  )

  function exportar() {
    if (!dto) return
    baixarCsv('envelhecimento-por-etapa.csv', [
      [`# ${t('nav.agingWip')} (${unidade})`],
      [`# ${t('gaveta.csv.filtros')}: ${resumo}`],
      [],
      [t('aging.col.etapa'), t('aging.col.task'), `${t('aging.col.idade')} (${unidade})`, t('aging.col.situacao')],
      ...linhasDePontos.map((l) => [l.etapa, l.key, numero(l.idade, lang), t(`aging.situacao.${l.situacao}`)]),
    ])
  }

  return (
    <>
      <CardPage
        titulo={t('nav.agingWip')}
        pergunta={t('aging.pergunta')}
        filtros={<Filtros key={sp.toString()} campos={['squad', 'pessoa', 'tipo', 'prioridade', 'os', 'unit']} valor={sp} aoAplicar={aplicar} />}
        consulta={consulta}
        avisos={dto?.avisos}
        aoExportar={exportar}
      >
        {dto && (
          <>
            <div role="group" aria-label={t('aging.indicadores')} className="cardpage-kpis">
              <Cartao
                id="AW-02" titulo={t('aging.kpi.emAndamento')} valor={num(dto.emAndamento)}
                aoAbrir={comAbrir(dto.emAndamento, 'emAndamento', t('aging.kpi.emAndamento'))}
              />
              <Cartao
                id="AW-02" titulo={t('aging.kpi.acima')} valor={num(dto.acimaDoNormal)}
                estado={bloqueado ? undefined : dto.acimaDoNormal > 0
                  ? { tipo: 'problema', texto: t('aging.acimaTexto').replace('{n}', String(dto.acimaDoNormal)) }
                  : { tipo: 'ok', texto: t('aging.semAcima') }}
                aoAbrir={comAbrir(dto.acimaDoNormal, 'acimaDoNormal', t('aging.kpi.acima'))}
              />
            </div>

            <Cartao id="AW-01" variante="serie" nivelDoTitulo={2} idDoTitulo="h-aw-grafico" titulo={t('aging.grafico')} n={dto.emAndamento}>
              <p className="legenda-meta">{t('aging.nota')}</p>
              {dto.etapas.length === 0 ? (
                <p className="vazio">{t('aging.vazio')}</p>
              ) : (
                <GraficoDeIdade etapas={bloqueado ? [] : dto.etapas} unidade={unidade} />
              )}

              <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-aw-grafico">
                <table className="tabela" aria-label={t('aging.resumo')}>
                  <thead>
                    <tr>
                      <th scope="col">{t('aging.col.etapa')}</th>
                      <th scope="col" className="num">{t('aging.col.emAndamento')}</th>
                      <th scope="col" className="num">{t('aging.col.acima')}</th>
                      <th scope="col" className="num">P50 ({unidade})</th>
                      <th scope="col" className="num">P85 ({unidade})</th>
                      <th scope="col">{t('aging.col.historico')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {dto.etapas.map((e) => (
                      <tr key={e.status}>
                        <th scope="row">
                          <span>{e.status}</span>
                          <span className="h-nota"> {t(`aging.cat.${e.categoria}`)}</span>
                        </th>
                        <td className="num">
                          {bloqueado ? traco : e.total > 0 ? (
                            <button
                              type="button" className="contagem-botao" aria-label={`${e.status}: ${e.total}`}
                              onClick={() => abrir('AW-01', `etapa:${e.status}`, e.status)}
                            >
                              {inteiro(e.total, lang)}
                            </button>
                          ) : inteiro(0, lang)}
                        </td>
                        <td className="num">
                          {bloqueado ? traco : e.acima > 0 ? (
                            <button
                              type="button" className="contagem-botao aging-acima"
                              aria-label={`${e.status} ${t('aging.kpi.acima').toLowerCase()}: ${e.acima}`}
                              onClick={() => abrir('AW-01', `acima:${e.status}`, `${e.status}: ${t('aging.kpi.acima')}`)}
                            >
                              ▲ {inteiro(e.acima, lang)}
                            </button>
                          ) : inteiro(0, lang)}
                        </td>
                        <td className="num">{bloqueado || !e.faixa ? traco : numero(e.faixa.p50, lang)}</td>
                        <td className="num">{bloqueado || !e.faixa ? traco : numero(e.faixa.p85, lang)}</td>
                        <td>
                          {e.faixa ? t('aging.historico.n').replace('{n}', String(e.historicoN)) : t('aging.semFaixa').replace('{n}', String(e.historicoN))}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <button
                type="button" className="btn aging-ver-pontos" aria-expanded={verPontos} aria-controls="aging-pontos"
                onClick={() => setVerPontos((v) => !v)}
              >
                {t(verPontos ? 'aging.ocultarPontos' : 'aging.verPontos')}
              </button>
              {verPontos && (
                <div id="aging-pontos" className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-aw-grafico">
                  <table className="tabela" aria-label={t('aging.pontos.tabela')}>
                    <thead>
                      <tr>
                        <th scope="col">{t('aging.col.etapa')}</th>
                        <th scope="col">{t('aging.col.task')}</th>
                        <th scope="col" className="num">{t('aging.col.idade')} ({unidade})</th>
                        <th scope="col">{t('aging.col.situacao')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {linhasDePontos.map((l) => (
                        <tr key={l.key}>
                          <td>{l.etapa}</td>
                          <td className="mono">{l.key}</td>
                          <td className="num">{bloqueado ? traco : numero(l.idade, lang)}</td>
                          <td>
                            <span aria-hidden="true">{simboloDe[l.situacao]}</span> {t(`aging.situacao.${l.situacao}`)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </Cartao>
          </>
        )}
      </CardPage>
      {gaveta && (
        <GavetaDeTasks
          cartaoId={gaveta.cartaoId} segmento={gaveta.segmento} titulo={gaveta.titulo} filtros={sp}
          resumoDosFiltros={resumo} aoFechar={() => setGaveta(null)}
        />
      )}
    </>
  )
}
