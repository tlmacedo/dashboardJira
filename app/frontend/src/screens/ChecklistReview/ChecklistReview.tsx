import { useState } from 'react'
import { useLang } from '../../i18n/LangProvider'
import { Cartao, type EstadoDoCartao } from '../../shared/Cartao'
import { CardPage } from '../../shared/CardPage'
import { baixarCsv } from '../../shared/csv'
import { Filtros, resumoDosFiltros, useFiltrosDaUrl } from '../../shared/Filtros'
import { dataCurta, inteiro, numero, percentual } from '../../shared/format'
import { GavetaDeTasks } from '../../shared/GavetaDeTasks'
import { temBloqueio, useCard } from '../../shared/useCard'
import { ALERTAS, type ChecklistReviewDto, type ParCR } from './api'
import { ComparacaoDoPar } from './ComparacaoDoPar'
import '../../shared/tabela.css'
import './checklistReview.css'

type Gaveta = { cartaoId: string; segmento: string; titulo: string }

/** Tipos que contam como o pai do par (os filtros de tipo valem para o checklist, não para a review). */
const TIPOS_DO_PAI = ['Checklist', 'Checklist TG']

/**
 * Checklist × Review (CR-01 a CR-14): para cada checklist, a sua review (pelo vínculo de bloqueio) e os alertas do par.
 * Tela de trabalho, densa. Nenhuma tabela ou frase compara colegas; os responsáveis só aparecem quando estão na visão.
 * Alerta e "n baixo" sempre em símbolo + texto; o lift só vira frase com a amostra mínima.
 */
export function ChecklistReview() {
  const { t, lang } = useLang()
  const [sp, aplicar] = useFiltrosDaUrl()
  const consulta = useCard<ChecklistReviewDto>('checklist-review', sp)
  const dto = consulta.data
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)
  const [comparando, setComparando] = useState<ParCR | null>(null)
  const bloqueado = temBloqueio(dto?.avisos)
  const traco = '—'
  const resumo = resumoDosFiltros(sp, t, (iso) => dataCurta(iso, lang))
  const abrir = (cartaoId: string, segmento: string, titulo: string) => setGaveta({ cartaoId, segmento, titulo })
  const comAbrir = (n: number, cartaoId: string, segmento: string, titulo: string) =>
    !bloqueado && n > 0 ? () => abrir(cartaoId, segmento, titulo) : undefined

  const pessoas = dto
    ? [...new Set(dto.pares.flatMap((p) => [p.responsavelPai, p.responsavelReview]).filter((x): x is string => !!x))].sort((a, b) =>
        a.localeCompare(b),
      )
    : undefined
  const unidade = t(dto?.unit === 'corrido' ? 'cr.unidade.corrido' : 'cr.unidade.util')

  function exportar() {
    if (!dto) return
    baixarCsv('checklist-review.csv', [
      [`# ${t('nav.checklistReview')} (${dto.versaoRegras})`],
      [`# ${t('gaveta.csv.filtros')}: ${resumo}`],
      [],
      [t('cr.col.pai'), t('cr.col.review'), t('cr.col.vencePai'), t('cr.col.venceReview'), t('cr.col.alertas')],
      ...dto.pares.map((p) => [p.pai, p.review, p.vencePai ?? '', p.venceReview ?? '', p.alertas.map((a) => t(`cr.alerta.${a}`)).join('; ')]),
    ])
  }

  /** Atenção quando a fração passa do limite do YAML (símbolo + texto, nunca só cor). */
  function estadoDaFracao(pct: number | null, limite: number, gravidade: EstadoDoCartao): { tipo: EstadoDoCartao; texto: string } | undefined {
    if (pct == null || bloqueado) return undefined
    return pct > limite
      ? { tipo: gravidade, texto: t('cr.estado.acima').replace('{limite}', percentual(limite, lang)) }
      : { tipo: 'ok', texto: t('cr.estado.dentro') }
  }

  function contagem(n: number, rotulo: string, cartaoId: string, segmento: string, titulo: string) {
    if (bloqueado) return traco
    if (n === 0) return inteiro(n, lang)
    return (
      <button type="button" className="contagem-botao" aria-label={`${rotulo}: ${n}`} onClick={() => abrir(cartaoId, segmento, titulo)}>
        {inteiro(n, lang)}
      </button>
    )
  }

  const nBaixo = (n: number, minimo: number) => (n < minimo ? <span> · {t('cr.nBaixo')}</span> : null)

  return (
    <>
      <CardPage
        titulo={t('nav.checklistReview')}
        pergunta={t('cr.pergunta')}
        filtros={
          <Filtros
            key={sp.toString()} campos={['periodo', 'squad', 'pessoa', 'tipo', 'prioridade', 'os', 'campos', 'unit']} valor={sp} aoAplicar={aplicar}
            pessoas={pessoas} tipos={TIPOS_DO_PAI}
          />
        }
        consulta={consulta}
        avisos={dto?.avisos}
        aoExportar={exportar}
      >
        {dto && (
          <>
            <p className="legenda-meta">{t('cr.regras').replace('{versao}', dto.versaoRegras).replace('{n}', String(dto.resumo.amostraMinima))}</p>

            <div role="group" aria-label={t('cr.indicadores')} className="cardpage-kpis">
              <Cartao
                id="CR-01" ajudaComTitulo titulo={t('cr.kpi.pares')} valor={bloqueado ? traco : inteiro(dto.resumo.pares, lang)}
                aoAbrir={comAbrir(dto.resumo.pares, 'CR-01', 'pares', t('cr.kpi.pares'))}
              />
              <Cartao
                id="CR-02" titulo={t('cr.kpi.semReview')} valor={bloqueado ? traco : inteiro(dto.resumo.semReview, lang)}
                legenda={t('cr.kpi.semReview.dica')}
                aoAbrir={comAbrir(dto.resumo.semReview, 'CR-02', 'semReview', t('cr.kpi.semReview'))}
              />
              <Cartao
                id="CR-08" titulo={t('cr.kpi.comAlerta')} valor={bloqueado || dto.resumo.comAlertaPct == null ? traco : percentual(dto.resumo.comAlertaPct, lang)}
                legenda={t('cr.kpi.comAlerta.dica').replace('{n}', inteiro(dto.resumo.comAlerta, lang)).replace('{total}', inteiro(dto.resumo.pares, lang))}
                estado={estadoDaFracao(dto.resumo.comAlertaPct, dto.resumo.limiteParesComAlerta, 'problema')}
                aoAbrir={comAbrir(dto.resumo.comAlerta, 'CR-08', 'comAlerta', t('cr.kpi.comAlerta'))}
              />
              <Cartao
                id="CR-06" titulo={t('cr.kpi.venceDepois')} valor={bloqueado || dto.resumo.venceDepoisPct == null ? traco : percentual(dto.resumo.venceDepoisPct, lang)}
                estado={estadoDaFracao(dto.resumo.venceDepoisPct, dto.resumo.limiteVenceDepois, 'atencao')}
                aoAbrir={comAbrir(dto.resumo.porAlerta.REVIEW_VENCE_DEPOIS ?? 0, 'CR-06', 'alerta:REVIEW_VENCE_DEPOIS', t('cr.kpi.venceDepois'))}
              />
              <Cartao
                id="CR-07" titulo={t('cr.kpi.mesmaPessoa')} valor={bloqueado || dto.resumo.mesmaPessoaPct == null ? traco : percentual(dto.resumo.mesmaPessoaPct, lang)}
                legenda={t('cr.kpi.mesmaPessoa.dica')}
                estado={estadoDaFracao(dto.resumo.mesmaPessoaPct, dto.resumo.limiteMesmaPessoa, 'atencao')}
                aoAbrir={comAbrir(dto.resumo.porAlerta.MESMA_PESSOA ?? 0, 'CR-07', 'alerta:MESMA_PESSOA', t('cr.kpi.mesmaPessoa'))}
              />
              <Cartao
                id="CR-12" titulo={t('cr.kpi.cadeia')} valor={bloqueado ? traco : inteiro(dto.resumo.atrasosEmCadeia, lang)}
                legenda={t('cr.kpi.cadeia.dica')}
                aoAbrir={comAbrir(dto.resumo.atrasosEmCadeia, 'CR-12', 'cadeia', t('cr.kpi.cadeia'))}
              />
              <Cartao
                id="CR-03" titulo={t('cr.kpi.ciclo')}
                valor={bloqueado || dto.resumo.cicloReviewP50 == null ? traco : `${numero(dto.resumo.cicloReviewP50, lang)} ${unidade}`}
                aoAbrir={comAbrir(dto.resumo.concluidas, 'CR-03', 'concluidas', t('cr.kpi.ciclo'))}
              />
              <Cartao
                id="CR-04" titulo={t('cr.kpi.razao')}
                valor={bloqueado || dto.resumo.razaoReviewPaiP50 == null ? traco : percentual(dto.resumo.razaoReviewPaiP50, lang)}
                legenda={t('cr.kpi.razao.dica')}
                aoAbrir={comAbrir(dto.resumo.concluidas, 'CR-04', 'concluidas', t('cr.kpi.razao'))}
              />
              <Cartao
                id="CR-05" titulo={t('cr.kpi.criadaApos')}
                valor={bloqueado || dto.resumo.criadaAposPaiHorasP50 == null ? traco : `${numero(dto.resumo.criadaAposPaiHorasP50, lang)} h`}
                aoAbrir={comAbrir(dto.resumo.pares, 'CR-05', 'pares', t('cr.kpi.criadaApos'))}
              />
            </div>

            <div className="cardpage-duas-colunas">
              <Cartao id="CR-08" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-cr-alertas" titulo={t('cr.tabela.alertas')}>
                <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-cr-alertas">
                  <table className="tabela" aria-labelledby="h-cr-alertas">
                    <thead>
                      <tr>
                        <th scope="col">{t('cr.col.alerta')}</th>
                        <th scope="col" className="num">{t('cr.col.pares')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {ALERTAS.map((a) => {
                        const n = dto.resumo.porAlerta[a] ?? 0
                        return (
                          <tr key={a}>
                            <th scope="row">
                              <span aria-hidden="true">{n > 0 ? '▲' : '✓'}</span> {t(`cr.alerta.${a}`)}
                            </th>
                            <td className="num">{contagem(n, t(`cr.alerta.${a}`), 'CR-08', `alerta:${a}`, t(`cr.alerta.${a}`))}</td>
                          </tr>
                        )
                      })}
                    </tbody>
                  </table>
                </div>
              </Cartao>

              <Cartao id="CR-11" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-cr-faixas" titulo={t('cr.tabela.faixas')}>
                <p className="legenda-meta">{t('cr.faixas.nota')}</p>
                <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-cr-faixas">
                  <table className="tabela" aria-labelledby="h-cr-faixas">
                    <thead>
                      <tr>
                        <th scope="col">{t('cr.col.faixa')}</th>
                        <th scope="col" className="num">{t('cr.col.reviews')}</th>
                        <th scope="col" className="num">{t('cr.col.reabertura')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {dto.resumo.duracaoXReabertura.map((f) => (
                        <tr key={f.faixa}>
                          <th scope="row">{f.faixa}</th>
                          <td className="num">{contagem(f.n, `${f.faixa} ${t('cr.col.reviews')}`, 'CR-11', `faixa:${f.faixa}`, `${t('cr.tabela.faixas')}: ${f.faixa}`)}</td>
                          <td className="num">
                            {bloqueado || f.taxaReabertura == null ? traco : percentual(f.taxaReabertura, lang)}
                            {!bloqueado && f.n > 0 && nBaixo(f.n, dto.resumo.amostraMinima)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </Cartao>
            </div>

            <Cartao id="CR-13" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-cr-labels" titulo={t('cr.tabela.labels')}>
              <p className="legenda-meta">{t('cr.labels.nota').replace('{n}', String(dto.resumo.amostraMinima))}</p>
              {dto.resumo.labels.length === 0 ? (
                <p className="vazio">{t('cr.labels.vazio')}</p>
              ) : (
                <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-cr-labels">
                  <table className="tabela" aria-labelledby="h-cr-labels">
                    <thead>
                      <tr>
                        <th scope="col">{t('cr.col.label')}</th>
                        <th scope="col" className="num">{t('cr.col.pares')}</th>
                        <th scope="col" className="num">{t('cr.col.reabertura')}</th>
                        <th scope="col" className="num">{t('cr.col.lift')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {dto.resumo.labels.map((l) => (
                        <tr key={l.label}>
                          <th scope="row" className="mono">{l.label}</th>
                          <td className="num">{contagem(l.n, `${l.label} ${t('cr.col.pares')}`, 'CR-13', `label:${l.label}`, `${t('cr.tabela.labels')}: ${l.label}`)}</td>
                          <td className="num">{bloqueado ? traco : percentual(l.taxaReabertura, lang)}</td>
                          <td className="num">
                            {bloqueado || l.lift == null ? traco : `${numero(l.lift, lang)}×`}
                            {!bloqueado && l.nBaixo && <span> · {t('cr.nBaixo')}</span>}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </Cartao>

            <div className="cardpage-duas-colunas">
              <Cartao id="CR-14" variante="lista" nivelDoTitulo={2} idDoTitulo="h-cr-dif" titulo={t('cr.tabela.diferencas')}>
                {dto.resumo.diferencasDeLabels.length === 0 ? (
                  <p className="vazio">{t('cr.dif.vazio')}</p>
                ) : (
                  <ul aria-labelledby="h-cr-dif">
                    {dto.resumo.diferencasDeLabels.map((d) => {
                      const onde = d.codigo.startsWith('pai:') ? t('cr.dif.pai') : t('cr.dif.review')
                      const label = d.codigo.slice(d.codigo.indexOf(':') + 1)
                      return (
                        <li key={d.codigo}>
                          {onde} <span className="mono">{label}</span>:{' '}
                          {contagem(d.pares, `${onde} ${label}`, 'CR-14', `dif:${d.codigo}`, `${onde}: ${label}`)} {t('cr.col.pares').toLowerCase()}
                        </li>
                      )
                    })}
                  </ul>
                )}
              </Cartao>

              <Cartao id="CR-10" variante="lista" nivelDoTitulo={2} idDoTitulo="h-cr-aprendizados" titulo={t('cr.tabela.aprendizados')}>
                {dto.resumo.aprendizados.length === 0 ? (
                  <p className="vazio">{t('cr.aprendizados.vazio').replace('{n}', String(dto.resumo.amostraMinima))}</p>
                ) : (
                  <ul aria-labelledby="h-cr-aprendizados">
                    {dto.resumo.aprendizados.map((a) => (
                      <li key={`${a.codigo}:${a.params.label ?? ''}`}>
                        {Object.entries(a.params)
                          .reduce((texto, [k, v]) => texto.replaceAll(`{${k}}`, () => v), t(`cr.aprendizado.${a.codigo}`))
                          .replace(/\{\w+\}/g, '…')}
                      </li>
                    ))}
                  </ul>
                )}
                <p className="legenda-meta">{t('cr.aprendizados.nota')}</p>
              </Cartao>
            </div>

            <Cartao id="CR-01" variante="lista" nivelDoTitulo={2} idDoTitulo="h-cr-pares" titulo={t('cr.tabela.pares')}>
              {dto.pares.length === 0 ? (
                <p className="vazio">{t('cr.pares.vazio')}</p>
              ) : (
                <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-cr-pares">
                  <table className="tabela" aria-labelledby="h-cr-pares">
                    <thead>
                      <tr>
                        <th scope="col">{t('cr.col.pai')}</th>
                        <th scope="col">{t('cr.col.review')}</th>
                        <th scope="col">{t('cr.col.vencePai')}</th>
                        <th scope="col">{t('cr.col.venceReview')}</th>
                        <th scope="col">{t('cr.col.responsaveis')}</th>
                        <th scope="col">{t('cr.col.alertas')}</th>
                        <th scope="col">{t('cr.col.comparar')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {dto.pares.map((p) => (
                        <tr key={`${p.pai}:${p.review}`}>
                          <th scope="row" className="mono">{p.pai}</th>
                          <td className="mono">{p.review}</td>
                          <td className="num">{p.vencePai ? dataCurta(p.vencePai, lang) : traco}</td>
                          <td className="num">{p.venceReview ? dataCurta(p.venceReview, lang) : traco}</td>
                          <td>{[p.responsavelPai, p.responsavelReview].map((x) => x ?? traco).join(' / ')}</td>
                          <td>
                            {p.alertas.length === 0 ? (
                              <span>
                                <span aria-hidden="true">✓</span> {t('cr.semAlerta')}
                              </span>
                            ) : (
                              <ul className="lista-simples">
                                {p.alertas.map((a) => (
                                  <li key={a}>
                                    <span aria-hidden="true">▲</span> {t(`cr.alerta.${a}`)}
                                  </li>
                                ))}
                              </ul>
                            )}
                          </td>
                          <td>
                            <button type="button" className="btn" onClick={() => setComparando(p)} aria-label={`${t('cr.comparar')} ${p.pai} ${p.review}`}>
                              {t('cr.comparar')}
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              <p className="legenda-meta">{t('cr.pares.nota')}</p>
            </Cartao>

            <Cartao id="CR-02" variante="lista" nivelDoTitulo={2} idDoTitulo="h-cr-sem" titulo={t('cr.tabela.semReview')}>
              {dto.semReview.length === 0 ? (
                <p className="vazio">{t('cr.semReview.vazio')}</p>
              ) : (
                <ul aria-labelledby="h-cr-sem">
                  {dto.semReview.map((s) => (
                    <li key={s.key}>
                      <span className="mono">{s.key}</span> <span className="resumo-task">{s.resumo}</span> · {s.status}
                    </li>
                  ))}
                </ul>
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
      {comparando && <ComparacaoDoPar par={comparando} aoFechar={() => setComparando(null)} />}
    </>
  )
}
