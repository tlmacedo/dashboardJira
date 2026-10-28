import { useState } from 'react'
import { useLang } from '../../i18n/LangProvider'
import { Cartao } from '../../shared/Cartao'
import { CardPage } from '../../shared/CardPage'
import { ContagensEmBarras } from '../../shared/ContagensEmBarras'
import { baixarCsv } from '../../shared/csv'
import { Filtros, resumoDosFiltros, useFiltrosDaUrl } from '../../shared/Filtros'
import { dataCurta, inteiro, numero, percentual } from '../../shared/format'
import { GavetaDeTasks } from '../../shared/GavetaDeTasks'
import { PorCampo } from '../../shared/PorCampo'
import { TabelaOrdenavel, type Coluna } from '../../shared/TabelaOrdenavel'
import { temBloqueio, useCard } from '../../shared/useCard'
import type { FechadasDto, LinhaFechada } from './api'
import { ControleDoCiclo } from './ControleDoCiclo'
import '../../shared/tabela.css'

type Gaveta = { cartaoId: string; segmento: string; titulo: string }

/** Símbolo + texto da situação do prazo de uma concluída (nunca só cor). */
const SIMBOLO_DO_PRAZO: Record<string, string> = { noPrazo: '✓', ajustado: '●', atrasada: '▲', semVencimento: '■' }

/** Fechadas (FE-01 a FE-12): o que foi entregue no período, com ciclo, "no prazo" e "no prazo ajustado" lado a lado. */
export function Fechadas() {
  const { t, lang } = useLang()
  const [sp, aplicar] = useFiltrosDaUrl()
  const consulta = useCard<FechadasDto>('fechadas', sp)
  const dto = consulta.data
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)
  const bloqueado = temBloqueio(dto?.avisos)
  const traco = '—'
  const num = (n: number) => (bloqueado ? traco : inteiro(n, lang))
  const resumo = resumoDosFiltros(sp, t, (iso) => dataCurta(iso, lang))
  const unidade = t(dto?.unit === 'corrido' ? 'abertas.diasCorridos' : 'abertas.diasUteis')
  const abrir = (cartaoId: string, segmento: string, titulo: string) => setGaveta({ cartaoId, segmento, titulo })
  const comAbrir = (n: number, cartaoId: string, segmento: string, titulo: string) =>
    !bloqueado && n > 0 ? () => abrir(cartaoId, segmento, titulo) : undefined
  const faixa = (f: { p50: number; p85: number } | null) => (bloqueado || !f ? traco : `${numero(f.p50, lang)} / ${numero(f.p85, lang)}`)
  const taxa = (x: number | null) => (bloqueado || x == null ? traco : percentual(x, lang))

  function exportar() {
    if (!dto) return
    baixarCsv('fechadas.csv', [
      [`# ${t('nav.fechadas')} (${unidade})`],
      [`# ${t('gaveta.csv.filtros')}: ${resumo}`],
      [],
      [t('fechadas.col.task'), t('abertas.col.resumo'), t('abertas.col.tipo'), t('abertas.col.responsavel'), t('fechadas.col.resolvida'), t('fechadas.col.ciclo'), t('fechadas.col.lead'), t('abertas.col.vence'), t('fechadas.col.prazo'), t('fechadas.col.pontos'), t('fechadas.col.horasEstimadas'), t('fechadas.col.horasGastas'), t('fechadas.col.razao')],
      ...dto.linhas.map((l) => [
        l.key, l.resumo, l.tipo, l.responsavel ?? '', l.resolvida, l.ciclo == null ? '' : numero(l.ciclo, lang), l.lead == null ? '' : numero(l.lead, lang),
        l.vence ?? '', t(`fechadas.prazo.${l.prazo}`), l.pontos == null ? '' : numero(l.pontos, lang),
        l.horasEstimadas == null ? '' : numero(l.horasEstimadas, lang), l.horasGastas == null ? '' : numero(l.horasGastas, lang), razao(l),
      ]),
    ])
  }

  function razao(l: LinhaFechada): string {
    return l.horasEstimadas && l.horasGastas != null ? numero(l.horasGastas / l.horasEstimadas, lang) : ''
  }

  const colunas: Coluna<LinhaFechada>[] = [
    { id: 'key', cabecalho: t('fechadas.col.task'), valor: (l) => l.key },
    { id: 'resumo', cabecalho: t('abertas.col.resumo'), valor: (l) => l.resumo },
    { id: 'tipo', cabecalho: t('abertas.col.tipo'), valor: (l) => l.tipo },
    { id: 'responsavel', cabecalho: t('abertas.col.responsavel'), valor: (l) => l.responsavel },
    { id: 'resolvida', cabecalho: t('fechadas.col.resolvida'), valor: (l) => l.resolvida, render: (l) => dataCurta(l.resolvida, lang) },
    { id: 'ciclo', cabecalho: `${t('fechadas.col.ciclo')} (${unidade})`, valor: (l) => l.ciclo, render: (l) => (l.ciclo == null ? traco : numero(l.ciclo, lang)), numerica: true },
    { id: 'lead', cabecalho: `${t('fechadas.col.lead')} (${unidade})`, valor: (l) => l.lead, render: (l) => (l.lead == null ? traco : numero(l.lead, lang)), numerica: true },
    { id: 'vence', cabecalho: t('abertas.col.vence'), valor: (l) => l.vence, render: (l) => (l.vence ? dataCurta(l.vence, lang) : traco) },
    {
      id: 'prazo', cabecalho: t('fechadas.col.prazo'), valor: (l) => l.prazo,
      render: (l) => (
        <span>
          <span aria-hidden="true">{SIMBOLO_DO_PRAZO[l.prazo] ?? '■'}</span> {t(`fechadas.prazo.${l.prazo}`)}
        </span>
      ),
    },
    { id: 'pontos', cabecalho: t('fechadas.col.pontos'), valor: (l) => l.pontos, render: (l) => (l.pontos == null ? traco : numero(l.pontos, lang)), numerica: true },
    { id: 'horas', cabecalho: `${t('fechadas.col.horasGastas')} / ${t('fechadas.col.horasEstimadas')}`, valor: (l) => (l.horasEstimadas && l.horasGastas != null ? l.horasGastas / l.horasEstimadas : null), render: (l) => razao(l) || traco, numerica: true },
  ]

  return (
    <>
      <CardPage
        titulo={t('nav.fechadas')}
        pergunta={t('fechadas.pergunta')}
        filtros={<Filtros key={sp.toString()} campos={['periodo', 'squad', 'pessoa', 'tipo', 'prioridade', 'os', 'campos', 'unit']} valor={sp} aoAplicar={aplicar} />}
        consulta={consulta}
        avisos={dto?.avisos}
        aoExportar={exportar}
      >
        {dto && (
          <>
            <div role="group" aria-label={t('fechadas.indicadores')} className="cardpage-kpis">
              <Cartao id="FE-01" titulo={t('fechadas.kpi.concluidas')} valor={num(dto.concluidas)} aoAbrir={comAbrir(dto.concluidas, 'FE-01', 'concluidas', t('fechadas.kpi.concluidas'))} />
              <Cartao
                id="FE-02" titulo={t('fechadas.kpi.pontos')} valor={bloqueado || dto.pontos == null ? traco : numero(dto.pontos, lang, 0)}
                legenda={dto.pontos == null && !bloqueado ? t('fechadas.kpi.pontos.vazio') : undefined}
                aoAbrir={comAbrir(dto.comPontos, 'FE-02', 'comPontos', t('fechadas.kpi.pontos'))}
              />
              <Cartao
                id="FE-03" titulo={`${t('fechadas.kpi.ciclo')} (${unidade})`} valor={faixa(dto.ciclo)} legenda="P50 / P85"
                n={dto.ciclo?.n} aoAbrir={comAbrir(dto.ciclo?.n ?? 0, 'FE-03', 'ciclo', t('fechadas.kpi.ciclo'))}
              />
              <Cartao
                id="FE-04" titulo={`${t('fechadas.kpi.lead')} (${unidade})`} valor={faixa(dto.lead)} legenda="P50 / P85"
                n={dto.lead?.n} aoAbrir={comAbrir(dto.lead?.n ?? 0, 'FE-04', 'lead', t('fechadas.kpi.lead'))}
              />
            </div>

            {/* "No prazo" e "no prazo ajustado" lado a lado (regra única com a Auditoria e Metas). */}
            <div role="group" aria-label={t('fechadas.prazo')} className="cardpage-kpis">
              <Cartao
                id="FE-05" titulo={t('fechadas.kpi.noPrazo')} valor={taxa(dto.taxaNoPrazo)}
                legenda={t('fechadas.kpi.noPrazo.dica').replace('{n}', inteiro(dto.noPrazo, lang)).replace('{total}', inteiro(dto.comVencimento, lang))}
                aoAbrir={comAbrir(dto.noPrazo, 'FE-05', 'noPrazo', t('fechadas.kpi.noPrazo'))}
              />
              <Cartao
                id="FE-05" titulo={t('fechadas.kpi.noPrazoAjustado')} valor={taxa(dto.taxaAjustada)}
                legenda={t('fechadas.kpi.noPrazo.dica').replace('{n}', inteiro(dto.noPrazoAjustado, lang)).replace('{total}', inteiro(dto.comVencimento, lang))}
                aoAbrir={comAbrir(dto.noPrazoAjustado, 'FE-05', 'noPrazoAjustado', t('fechadas.kpi.noPrazoAjustado'))}
              />
              <Cartao
                id="FE-06" titulo={t('fechadas.kpi.canceladas')} valor={num(dto.canceladas)}
                aoAbrir={comAbrir(dto.canceladas, 'FE-06', 'canceladas', t('fechadas.kpi.canceladas'))}
              />
              <Cartao
                id="FE-06" titulo={t('fechadas.kpi.semResolution')} valor={num(dto.semResolution)} legenda={t('fechadas.kpi.semResolution.dica')}
                aoAbrir={comAbrir(dto.semResolution, 'FE-06', 'semResolution', t('fechadas.kpi.semResolution'))}
              />
            </div>

            <div className="cardpage-duas-colunas">
              <Cartao id="FE-07" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-fe-semana" titulo={t('fechadas.porSemana')}>
                <ContagensEmBarras
                  barras
                  rotulo={t('fechadas.porSemana')}
                  cabecalho={t('fechadas.col.semana')}
                  indisponivel={bloqueado}
                  itens={dto.porSemana.map((s) => ({ chave: s.semana, rotulo: dataCurta(s.semana, lang), n: s.n }))}
                  aoAbrir={(c) => abrir('FE-07', `semana:${c}`, `${t('fechadas.porSemana')}: ${dataCurta(c, lang)}`)}
                />
              </Cartao>
              <Cartao id="FE-08" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-fe-disp" titulo={t('fechadas.dispersao')}
                aoAbrir={comAbrir(dto.dispersao?.pontos.length ?? 0, 'FE-08', 'dispersao', t('fechadas.dispersao'))}>
                {!dto.dispersao ? (
                  <p className="vazio">{t('fechadas.dispersao.vazia')}</p>
                ) : (
                  <dl className="dispersao-percentis">
                    <dt>P50</dt>
                    <dd>{bloqueado ? traco : `${numero(dto.dispersao.p50, lang)} ${unidade}`}</dd>
                    <dt>P85</dt>
                    <dd>{bloqueado ? traco : `${numero(dto.dispersao.p85, lang)} ${unidade}`}</dd>
                    <dt>P95</dt>
                    <dd>{bloqueado ? traco : `${numero(dto.dispersao.p95, lang)} ${unidade}`}</dd>
                    <dt>n</dt>
                    <dd>{inteiro(dto.dispersao.pontos.length, lang)}</dd>
                  </dl>
                )}
              </Cartao>
              <Cartao id="FE-09" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-fe-tipo" titulo={t('fechadas.porTipo')}>
                {dto.porTipo.length === 0 ? (
                  <p className="vazio">{t('fechadas.porTipo.vazio')}</p>
                ) : (
                  <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-fe-tipo">
                    <table className="tabela" aria-labelledby="h-fe-tipo">
                      <thead>
                        <tr>
                          <th scope="col">{t('abertas.col.tipo')}</th>
                          <th scope="col" className="num">P50 ({unidade})</th>
                          <th scope="col" className="num">P85 ({unidade})</th>
                          <th scope="col" className="num">n</th>
                        </tr>
                      </thead>
                      <tbody>
                        {dto.porTipo.map((f) => (
                          <tr key={f.tipo}>
                            <th scope="row">{f.tipo}</th>
                            <td className="num">{bloqueado ? traco : numero(f.p50, lang)}</td>
                            <td className="num">{bloqueado ? traco : numero(f.p85, lang)}</td>
                            <td className="num">
                              {!bloqueado && f.n > 0 ? (
                                <button type="button" className="contagem-botao" aria-label={`${f.tipo}: ${f.n}`}
                                  onClick={() => abrir('FE-09', `tipo:${f.tipo}`, `${t('fechadas.porTipo')}: ${f.tipo}`)}>
                                  {inteiro(f.n, lang)}
                                </button>
                              ) : (
                                inteiro(f.n, lang)
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </Cartao>
              <Cartao id="FE-10" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-fe-projeto" titulo={t('fechadas.porProjeto')}>
                <ContagensEmBarras
                  rotulo={t('fechadas.porProjeto')}
                  cabecalho={t('fechadas.col.projeto')}
                  indisponivel={bloqueado}
                  itens={dto.porProjeto.map((c) => ({ chave: c.chave, rotulo: c.chave, n: c.n }))}
                  aoAbrir={(c) => abrir('FE-10', `projeto:${c}`, `${t('fechadas.porProjeto')}: ${c}`)}
                />
              </Cartao>
            </div>

            <ControleDoCiclo />

            <Cartao id="FE-11" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-fe-pessoa" titulo={t('fechadas.porPessoa')}>
              {dto.porPessoa.length === 0 ? (
                <p className="vazio">{t('fechadas.porPessoa.vazio')}</p>
              ) : (
                <>
                  <p className="legenda-meta">{t('fechadas.porPessoa.nota')}</p>
                  <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-fe-pessoa">
                    <table className="tabela" aria-labelledby="h-fe-pessoa">
                      <thead>
                        <tr>
                          <th scope="col">{t('fechadas.col.pessoa')}</th>
                          <th scope="col" className="num">{t('fechadas.col.finalizou')}</th>
                          <th scope="col" className="num">{t('fechadas.col.participou')}</th>
                          <th scope="col" className="num">{t('fechadas.col.pontos')}</th>
                          <th scope="col" className="num">{t('fechadas.col.cicloP50')} ({unidade})</th>
                          <th scope="col" className="num">{t('fechadas.col.horasEstimadas')}</th>
                          <th scope="col" className="num">{t('fechadas.col.horasGastas')}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {/* Ordem alfabética vinda do backend; sem coluna de posição e sem destaque de melhor ou pior. */}
                        {dto.porPessoa.map((p) => (
                          <tr key={p.pessoa}>
                            <th scope="row">{p.pessoa}</th>
                            {([['finalizou', p.finalizou], ['participou', p.participou]] as const).map(([tipo, n]) => (
                              <td className="num" key={tipo}>
                                {!bloqueado && n > 0 ? (
                                  <button type="button" className="contagem-botao" aria-label={`${p.pessoa} ${t(`fechadas.col.${tipo}`)}: ${n}`}
                                    onClick={() => abrir('FE-11', `pessoa:${p.pessoa}:${tipo}`, `${p.pessoa}: ${t(`fechadas.col.${tipo}`)}`)}>
                                    {inteiro(n, lang)}
                                  </button>
                                ) : (
                                  bloqueado ? traco : inteiro(n, lang)
                                )}
                              </td>
                            ))}
                            <td className="num">{p.pontos == null ? traco : numero(p.pontos, lang)}</td>
                            <td className="num">{p.cicloP50 == null ? traco : numero(p.cicloP50, lang)}</td>
                            <td className="num">{p.horasEstimadas == null ? traco : numero(p.horasEstimadas, lang)}</td>
                            <td className="num">{p.horasGastas == null ? traco : numero(p.horasGastas, lang)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </>
              )}
            </Cartao>

            <PorCampo cartaoId="FE-13" agrupamentos={dto.porCampo ?? []} indisponivel={bloqueado} aoAbrir={(seg, titulo) => abrir('FE-13', seg, titulo)} />

            <Cartao id="FE-12" variante="lista" nivelDoTitulo={2} idDoTitulo="h-fe-tabela" titulo={t('fechadas.tabela')}
              aoAbrir={comAbrir(dto.concluidas, 'FE-12', 'concluidas', t('fechadas.tabela'))}>
              {dto.linhas.length === 0 ? (
                <p className="vazio">{t('fechadas.tabela.vazia')}</p>
              ) : (
                <TabelaOrdenavel
                  rotulo={t('fechadas.tabela')} colunas={colunas} linhas={bloqueado ? [] : dto.linhas} chave={(l) => l.key}
                  primeiraEhCabecalhoDeLinha inicial={{ id: 'resolvida', descendente: true }}
                />
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
