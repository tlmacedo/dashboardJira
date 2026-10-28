import { useState } from 'react'
import { useLang } from '../../i18n/LangProvider'
import { Cartao } from '../../shared/Cartao'
import { CardPage } from '../../shared/CardPage'
import { baixarCsv } from '../../shared/csv'
import { Filtros, resumoDosFiltros, useFiltrosDaUrl } from '../../shared/Filtros'
import { dataCurta, inteiro, numero } from '../../shared/format'
import { GavetaDeTasks } from '../../shared/GavetaDeTasks'
import { Ocupacao } from '../../shared/Ocupacao'
import { temBloqueio, useCard } from '../../shared/useCard'
import type { CapacidadeDto } from './api'
import '../../shared/tabela.css'
import '../../shared/pessoas.css'

type Gaveta = { cartaoId: string; segmento: string; titulo: string }

/**
 * Capacidade e ausências (CA-01 a CA-09), só para a liderança: mapa de 8 semanas ("aus." quando a ausência tira toda a
 * capacidade da semana), alertas, atrasos previstos pela capacidade com e sem a ausência, ausências (sem motivo) e feriados.
 * Pessoas em ordem alfabética, sem ranking.
 */
export function Capacidade() {
  const { t, lang } = useLang()
  const [sp, aplicar] = useFiltrosDaUrl()
  const consulta = useCard<CapacidadeDto>('capacidade', sp)
  const dto = consulta.data
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)
  const bloqueado = temBloqueio(dto?.avisos)
  const traco = '—'
  const resumo = resumoDosFiltros(sp, t, (iso) => dataCurta(iso, lang))
  const abrir = (cartaoId: string, segmento: string, titulo: string) => setGaveta({ cartaoId, segmento, titulo })
  const comAbrir = (n: number, cartaoId: string, segmento: string, titulo: string) =>
    !bloqueado && n > 0 ? () => abrir(cartaoId, segmento, titulo) : undefined

  function exportar() {
    if (!dto) return
    baixarCsv('capacidade.csv', [
      [`# ${t('nav.capacidade')} (${dto.versaoRegras})`],
      [`# ${t('gaveta.csv.filtros')}: ${resumo}`],
      [],
      [t('capacidade.col.key'), t('carga.col.pessoa'), t('capacidade.col.vence'), t('capacidade.col.termino'), t('capacidade.col.causa')],
      ...dto.listaAtrasos.map((a) => [a.key, a.pessoa, a.vence, a.termino ?? '', t(`capacidade.causa.${a.causa}`)]),
    ])
  }

  const semanas = dto?.mapa[0]?.semanas.map((s) => s.semana) ?? []

  return (
    <>
      <CardPage
        titulo={t('nav.capacidade')}
        pergunta={t('capacidade.pergunta')}
        filtros={<Filtros key={sp.toString()} campos={['squad', 'pessoa']} valor={sp} aoAplicar={aplicar} />}
        consulta={consulta}
        avisos={dto?.avisos}
        aoExportar={exportar}
      >
        {dto && (
          <>
            <div role="group" aria-label={t('capacidade.indicadores')} className="cardpage-kpis">
              <Cartao
                id="CA-01" titulo={t('capacidade.kpi.alertas')} valor={bloqueado ? traco : inteiro(dto.alertas.length, lang)}
                simboloDoValor={!bloqueado && dto.alertas.length > 0 ? '▲' : undefined}
              />
              <Cartao
                id="CA-02" titulo={t('capacidade.kpi.atrasos')} valor={bloqueado ? traco : inteiro(dto.atrasosPrevistos, lang)}
                simboloDoValor={!bloqueado && dto.atrasosPrevistos > 0 ? '▲' : undefined}
                aoAbrir={comAbrir(dto.atrasosPrevistos, 'CA-02', 'atrasosPrevistos', t('capacidade.kpi.atrasos'))}
              />
              <Cartao
                id="CA-03" titulo={t('capacidade.kpi.porAusencia')} valor={bloqueado ? traco : inteiro(dto.atrasosPorAusencia, lang)}
                aoAbrir={comAbrir(dto.atrasosPorAusencia, 'CA-03', 'atrasosPorAusencia', t('capacidade.kpi.porAusencia'))}
              />
              <Cartao
                id="CA-04" titulo={t('capacidade.kpi.jain')} valor={bloqueado || dto.jain === null ? traco : numero(dto.jain, lang, 2)}
                legenda={dto.jain === null ? t('carga.kpi.jain.indisponivel') : t('carga.kpi.jain.nota')}
              />
            </div>

            <Cartao id="CA-05" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-ca-mapa" titulo={t('capacidade.mapa')}>
              {dto.mapa.length === 0 ? (
                <p className="vazio">{t('carga.vazia')}</p>
              ) : (
                <>
                  <p className="legenda-meta">{t('capacidade.mapa.legenda')}</p>
                  <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-ca-mapa">
                    <table className="tabela grade-pessoas" aria-labelledby="h-ca-mapa">
                      <thead>
                        <tr>
                          <th scope="col">{t('carga.col.pessoa')}</th>
                          {semanas.map((s) => (
                            <th scope="col" key={s}>{dataCurta(s, lang)}</th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {dto.mapa.map((l) => (
                          <tr key={l.pessoa}>
                            <th scope="row">{l.pessoa}</th>
                            {l.semanas.map((c) => (
                              <td key={c.semana}>
                                {bloqueado ? traco : c.ausente ? (
                                  <span className="celula-aus"><span aria-hidden="true">▓</span> {t('capacidade.mapa.aus')}</span>
                                ) : (
                                  <Ocupacao o={c.ocupacao} compacta />
                                )}
                              </td>
                            ))}
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </>
              )}
            </Cartao>

            <Cartao id="CA-06" variante="lista" nivelDoTitulo={2} idDoTitulo="h-ca-alertas" titulo={t('capacidade.alertas')}>
              {dto.alertas.length === 0 ? (
                <p className="vazio">{t('capacidade.alertas.vazio')}</p>
              ) : (
                <ul>
                  {dto.alertas.map((a) => (
                    <li key={`${a.pessoa}:${a.tipo}:${a.semana}`}>
                      <span aria-hidden="true">▲</span> <strong>{a.pessoa}</strong>:{' '}
                      {t(`capacidade.alerta.${a.tipo}`)
                        .replace('{semana}', dataCurta(a.semana, lang))
                        .replace('{h}', numero(a.horasAReplanejar ?? 0, lang))}
                    </li>
                  ))}
                </ul>
              )}
            </Cartao>

            <div className="cardpage-duas-colunas">
              <Cartao id="CA-07" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-ca-aus" titulo={t('capacidade.ausencias')}>
                {dto.ausencias.length === 0 ? (
                  <p className="vazio">{t('capacidade.ausencias.vazia')}</p>
                ) : (
                  <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-ca-aus">
                    <table className="tabela" aria-labelledby="h-ca-aus">
                      <thead>
                        <tr>
                          <th scope="col">{t('carga.col.pessoa')}</th>
                          <th scope="col">{t('capacidade.col.de')}</th>
                          <th scope="col">{t('capacidade.col.ate')}</th>
                          <th scope="col" className="num">{t('capacidade.col.fracao')}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {dto.ausencias.map((a) => (
                          <tr key={`${a.pessoa}:${a.de}`}>
                            <th scope="row">{a.pessoa}</th>
                            <td>{dataCurta(a.de, lang)}</td>
                            <td>{dataCurta(a.ate, lang)}</td>
                            <td className="num">{numero(a.fracao, lang)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
                <p className="legenda-meta">{t('capacidade.ausencias.nota')}</p>
              </Cartao>
              <Cartao id="CA-08" variante="lista" nivelDoTitulo={2} idDoTitulo="h-ca-fer" titulo={t('capacidade.feriados')}>
                {dto.feriados.length === 0 ? (
                  <p className="vazio">{t('capacidade.feriados.vazio')}</p>
                ) : (
                  <ul>
                    {dto.feriados.map((f) => (
                      <li key={f.dia}>
                        {dataCurta(f.dia, lang)}: {f.nome || t('calendario.feriado')}
                      </li>
                    ))}
                  </ul>
                )}
              </Cartao>
            </div>

            <Cartao
              id="CA-09" variante="lista" nivelDoTitulo={2} idDoTitulo="h-ca-atrasos" titulo={t('capacidade.atrasos')}
              aoAbrir={comAbrir(dto.listaAtrasos.length, 'CA-09', 'atrasosPrevistos', t('capacidade.atrasos'))}
            >
              {dto.listaAtrasos.length === 0 ? (
                <p className="vazio">{t('capacidade.atrasos.vazio')}</p>
              ) : (
                <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-ca-atrasos">
                  <table className="tabela" aria-labelledby="h-ca-atrasos">
                    <thead>
                      <tr>
                        <th scope="col">{t('capacidade.col.key')}</th>
                        <th scope="col">{t('carga.col.pessoa')}</th>
                        <th scope="col">{t('capacidade.col.vence')}</th>
                        <th scope="col">{t('capacidade.col.termino')}</th>
                        <th scope="col">{t('capacidade.col.causa')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {dto.listaAtrasos.map((a) => (
                        <tr key={a.key}>
                          <th scope="row" className="mono">{a.key}</th>
                          <td>{a.pessoa}</td>
                          <td>{dataCurta(a.vence, lang)}</td>
                          <td>{a.termino ? dataCurta(a.termino, lang) : t('capacidade.semTermino')}</td>
                          <td>
                            <span aria-hidden="true">▲</span> {t(`capacidade.causa.${a.causa}`)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </Cartao>
            <p className="legenda-meta">{t('carga.rodape').replace('{versao}', dto.versaoRegras)}</p>
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
