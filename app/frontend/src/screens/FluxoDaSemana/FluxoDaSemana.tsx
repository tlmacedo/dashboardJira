import { useState } from 'react'
import { useLang } from '../../i18n/LangProvider'
import { Cartao } from '../../shared/Cartao'
import { CardPage } from '../../shared/CardPage'
import { baixarCsv } from '../../shared/csv'
import { Filtros, resumoDosFiltros, useFiltrosDaUrl } from '../../shared/Filtros'
import { dataCurta, inteiro } from '../../shared/format'
import { GavetaDeTasks } from '../../shared/GavetaDeTasks'
import { temBloqueio, useCard } from '../../shared/useCard'
import type { FluxoDaSemanaDto } from './api'
import { GraficoDeFluxo } from './GraficoDeFluxo'
import { SERIES_DO_FLUXO } from './series'
import '../../shared/tabela.css'

type Gaveta = { cartaoId: string; segmento: string; titulo: string }

/** "+5", "0" ou "-3": o saldo do backlog sempre com sinal explícito quando positivo. */
function comSinal(n: number, lang: 'pt' | 'en'): string {
  return `${n > 0 ? '+' : ''}${inteiro(n, lang)}`
}

/**
 * Fluxo da semana (FX-01 e FX-02): quantas tasks entram, começam e saem a cada semana (segunda a domingo de Manaus), com
 * as canceladas à parte e o sinal "backlog crescendo". Sem série por pessoa: o recorte por pessoa é um filtro, não várias
 * séries lado a lado. Cada barra e cada número da tabela abrem as tasks do recorte.
 */
export function FluxoDaSemana() {
  const { t, lang } = useLang()
  const [sp, aplicar] = useFiltrosDaUrl()
  const consulta = useCard<FluxoDaSemanaDto>('fluxo-da-semana', sp)
  const dto = consulta.data
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)
  const [verTabela, setVerTabela] = useState(false)
  const bloqueado = temBloqueio(dto?.avisos)
  const traco = '—'
  const resumo = resumoDosFiltros(sp, t, (iso) => dataCurta(iso, lang))
  const abrir = (cartaoId: string, segmento: string, titulo: string) => setGaveta({ cartaoId, segmento, titulo })
  const comAbrir = (n: number, segmento: string, titulo: string) =>
    !bloqueado && n > 0 ? () => abrir('FX-02', segmento, titulo) : undefined
  const num = (n: number) => (bloqueado ? traco : inteiro(n, lang))

  const linhas = (dto?.semanas ?? []).map((s, i, todas) => ({
    ...s,
    saldo: s.entrou - s.saiu - s.canceladas,
    acumulado: todas.slice(0, i + 1).reduce((soma, w) => soma + w.entrou - w.saiu - w.canceladas, 0),
  }))

  function exportar() {
    if (!dto) return
    baixarCsv('fluxo-da-semana.csv', [
      [`# ${t('nav.fluxoDaSemana')}`],
      [`# ${t('gaveta.csv.filtros')}: ${resumo}`],
      [],
      [t('fluxo.col.semana'), t('fluxo.kpi.entrou'), t('fluxo.kpi.comecou'), t('fluxo.kpi.saiu'), t('fluxo.kpi.canceladas'), t('fluxo.col.saldo'), t('fluxo.col.acumulado')],
      ...linhas.map((l) => [l.semana, String(l.entrou), String(l.comecou), String(l.saiu), String(l.canceladas), String(l.saldo), String(l.acumulado)]),
    ])
  }

  const abrirSerie = (serie: string, semana: string, titulo: string) => abrir('FX-01', `${serie}:${semana}`, titulo)

  return (
    <>
      <CardPage
        titulo={t('nav.fluxoDaSemana')}
        pergunta={t('fluxo.pergunta')}
        filtros={<Filtros key={sp.toString()} campos={['squad', 'pessoa', 'tipo', 'prioridade', 'os', 'campos']} valor={sp} aoAplicar={aplicar} />}
        consulta={consulta}
        avisos={dto?.avisos}
        aoExportar={exportar}
      >
        {dto && (
          <>
            <div role="group" aria-label={t('fluxo.indicadores')} className="cardpage-kpis">
              <Cartao
                id="FX-02" titulo={t('fluxo.kpi.entrou')} valor={num(dto.entrou)} legenda={t('fluxo.kpi.janela').replace('{n}', String(dto.semanasNaJanela))}
                aoAbrir={comAbrir(dto.entrou, 'entradas', t('fluxo.kpi.entrou'))}
              />
              <Cartao
                id="FX-02" titulo={t('fluxo.kpi.comecou')} valor={num(dto.comecou)} legenda={t('fluxo.kpi.janela').replace('{n}', String(dto.semanasNaJanela))}
                aoAbrir={comAbrir(dto.comecou, 'iniciadas', t('fluxo.kpi.comecou'))}
              />
              <Cartao
                id="FX-02" titulo={t('fluxo.kpi.saiu')} valor={num(dto.saiu)} legenda={t('fluxo.kpi.janela').replace('{n}', String(dto.semanasNaJanela))}
                aoAbrir={comAbrir(dto.saiu, 'saidas', t('fluxo.kpi.saiu'))}
              />
              <Cartao
                id="FX-02" titulo={t('fluxo.kpi.canceladas')} valor={num(dto.canceladas)} legenda={t('fluxo.kpi.canceladas.legenda')}
                aoAbrir={comAbrir(dto.canceladas, 'canceladas', t('fluxo.kpi.canceladas'))}
              />
              <Cartao
                id="FX-02" titulo={t('fluxo.kpi.saldo')} valor={bloqueado ? traco : comSinal(dto.saldoDoBacklog, lang)} legenda={t('fluxo.saldo.legenda')}
                estado={bloqueado ? undefined : dto.backlogCrescendo
                  ? { tipo: 'problema', texto: t('fluxo.crescendo').replace('{n}', String(dto.semanasCrescendo)) }
                  : { tipo: 'ok', texto: t('fluxo.semCrescimento') }}
              />
            </div>

            <Cartao id="FX-01" variante="serie" nivelDoTitulo={2} idDoTitulo="h-fx-grafico" titulo={t('fluxo.grafico')} n={dto.tasksNaJanela}>
              <p className="legenda-meta">{t('fluxo.nota')}</p>
              <GraficoDeFluxo semanas={dto.semanas} aoAbrir={bloqueado ? undefined : abrirSerie} />
              <button
                type="button" className="btn fluxo-ver-tabela" aria-expanded={verTabela} aria-controls="fluxo-tabela"
                onClick={() => setVerTabela((v) => !v)}
              >
                {t(verTabela ? 'fluxo.ocultarTabela' : 'fluxo.verTabela')}
              </button>
              {verTabela && (
                <div id="fluxo-tabela" className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-fx-grafico">
                  <table className="tabela" aria-label={t('fluxo.tabela')}>
                    <thead>
                      <tr>
                        <th scope="col">{t('fluxo.col.semana')}</th>
                        {SERIES_DO_FLUXO.map((s) => (
                          <th key={s.id} scope="col" className="num">{t(s.rotulo)}</th>
                        ))}
                        <th scope="col" className="num">{t('fluxo.kpi.canceladas')}</th>
                        <th scope="col" className="num">{t('fluxo.col.saldo')}</th>
                        <th scope="col" className="num">{t('fluxo.col.acumulado')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {linhas.map((l) => (
                        <tr key={l.semana}>
                          <th scope="row">
                            <span>{dataCurta(l.semana, lang)}</span>
                            {l.emCurso && <span className="h-nota"> {t('fluxo.emCurso')}</span>}
                          </th>
                          {[
                            { serie: 'entrada', rotulo: t('fluxo.kpi.entrou'), n: l.entrou },
                            { serie: 'inicio', rotulo: t('fluxo.kpi.comecou'), n: l.comecou },
                            { serie: 'saida', rotulo: t('fluxo.kpi.saiu'), n: l.saiu },
                            { serie: 'cancelada', rotulo: t('fluxo.kpi.canceladas'), n: l.canceladas },
                          ].map((c) => (
                            <td key={c.serie} className="num">
                              {bloqueado ? (
                                traco
                              ) : c.n > 0 ? (
                                <button
                                  type="button" className="contagem-botao" aria-label={`${c.rotulo} ${dataCurta(l.semana, lang)}: ${c.n}`}
                                  onClick={() => abrirSerie(c.serie, l.semana, `${c.rotulo}: ${dataCurta(l.semana, lang)}`)}
                                >
                                  {inteiro(c.n, lang)}
                                </button>
                              ) : (
                                inteiro(c.n, lang)
                              )}
                            </td>
                          ))}
                          <td className="num">{bloqueado ? traco : comSinal(l.saldo, lang)}</td>
                          <td className="num">{bloqueado ? traco : comSinal(l.acumulado, lang)}</td>
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
