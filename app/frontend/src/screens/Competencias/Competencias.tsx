import { useState } from 'react'
import { useLang } from '../../i18n/LangProvider'
import { Cartao } from '../../shared/Cartao'
import { CardPage } from '../../shared/CardPage'
import { baixarCsv } from '../../shared/csv'
import { Filtros, resumoDosFiltros, useFiltrosDaUrl } from '../../shared/Filtros'
import { dataCurta, inteiro, numero } from '../../shared/format'
import { GavetaDeTasks } from '../../shared/GavetaDeTasks'
import { temBloqueio, useCard } from '../../shared/useCard'
import type { CompetenciasDto } from './api'
import '../../shared/tabela.css'
import '../../shared/pessoas.css'

type Gaveta = { cartaoId: string; segmento: string; titulo: string }

/**
 * Matriz de competências (CM-01 a CM-03): quem tem cada competência entre as pessoas da SUA visão (pessoas em ordem
 * alfabética), quais dependem de uma pessoa só (▲ pessoa única, só com 2 ou mais pessoas na visão) ou de ninguém (▲ ninguém
 * tem) e a demanda aberta por competência. Competência é dado de pessoas: o usuário comum só enxerga a própria linha. Cada
 * célula diz "tem" ou "não tem" em texto para o leitor de tela; a marca visual nunca é o único sinal.
 */
export function Competencias() {
  const { t, lang } = useLang()
  const [sp, aplicar] = useFiltrosDaUrl()
  const consulta = useCard<CompetenciasDto>('competencias', sp)
  const dto = consulta.data
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)
  const bloqueado = temBloqueio(dto?.avisos)
  const traco = '—'
  const resumo = resumoDosFiltros(sp, t, (iso) => dataCurta(iso, lang))
  const abrir = (cartaoId: string, segmento: string, titulo: string) => setGaveta({ cartaoId, segmento, titulo })

  function situacao(c: string): { texto: string; alerta: boolean } {
    if (dto?.semNinguem.includes(c)) return { texto: t('competencias.situacao.ninguem'), alerta: true }
    if (dto?.pessoaUnica.includes(c)) return { texto: t('competencias.situacao.unica'), alerta: true }
    return { texto: t('competencias.situacao.ok'), alerta: false }
  }

  function exportar() {
    if (!dto) return
    baixarCsv('competencias.csv', [
      [`# ${t('nav.competencias')}`],
      [`# ${t('gaveta.csv.filtros')}: ${resumo}`],
      [],
      [t('competencias.col.competencia'), ...dto.pessoas, t('competencias.col.situacao')],
      ...dto.competencias.map((c, i) => [c, ...dto.celulas[i].map((tem) => (tem ? t('competencias.tem') : t('competencias.nao'))), situacao(c).texto]),
    ])
  }

  const demanda = dto ? Object.keys(dto.demandaH).sort((a, b) => a.localeCompare(b)) : []

  return (
    <>
      <CardPage
        titulo={t('nav.competencias')}
        pergunta={t('competencias.pergunta')}
        filtros={
          <Filtros
            key={sp.toString()} campos={['squad', 'pessoa', 'tipo', 'prioridade', 'os', 'campos']} valor={sp} aoAplicar={aplicar}
            pessoas={dto?.pessoas}
          />
        }
        consulta={consulta}
        avisos={dto?.avisos}
        aoExportar={exportar}
      >
        {dto && (
          <>
            <div role="group" aria-label={t('competencias.indicadores')} className="cardpage-kpis">
              <Cartao
                id="CM-01" titulo={t('competencias.kpi.unica')} valor={bloqueado ? traco : inteiro(dto.pessoaUnica.length, lang)}
                simboloDoValor={!bloqueado && dto.pessoaUnica.length > 0 ? '▲' : undefined}
              />
              <Cartao
                id="CM-02" titulo={t('competencias.kpi.semNinguem')} valor={bloqueado ? traco : inteiro(dto.tasksSemNinguem, lang)}
                simboloDoValor={!bloqueado && dto.tasksSemNinguem > 0 ? '▲' : undefined}
                aoAbrir={!bloqueado && dto.tasksSemNinguem > 0 ? () => abrir('CM-02', 'semNinguem', t('competencias.kpi.semNinguem')) : undefined}
              />
            </div>

            <Cartao id="CM-01" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-cm-matriz" titulo={t('competencias.matriz')}>
              {dto.competencias.length === 0 ? (
                <p className="vazio">{t('competencias.vazia')}</p>
              ) : (
                <>
                  <p className="legenda-meta">{t('pessoas.alfabetica')}</p>
                  <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-cm-matriz">
                    <table className="tabela grade-pessoas" aria-labelledby="h-cm-matriz">
                      <thead>
                        <tr>
                          <th scope="col">{t('competencias.col.competencia')}</th>
                          {dto.pessoas.map((p) => (
                            <th key={p} scope="col">{p}</th>
                          ))}
                          <th scope="col">{t('competencias.col.situacao')}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {dto.competencias.map((c, i) => {
                          const s = situacao(c)
                          return (
                            <tr key={c}>
                              <th scope="row">{c}</th>
                              {dto.pessoas.map((p, j) => (
                                <td key={p} className="celula-aus">
                                  <span aria-hidden="true">{dto.celulas[i][j] ? '✓' : '—'}</span>
                                  <span className="so-leitor">{dto.celulas[i][j] ? t('competencias.tem') : t('competencias.nao')}</span>
                                </td>
                              ))}
                              <td className={s.alerta ? 'ocupacao ocupacao--problema' : 'ocupacao ocupacao--ok'}>{bloqueado ? traco : s.texto}</td>
                            </tr>
                          )
                        })}
                      </tbody>
                    </table>
                  </div>
                  <p className="legenda-meta">{t('competencias.nota')}</p>
                </>
              )}
            </Cartao>

            {demanda.length > 0 && (
              <Cartao id="CM-03" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-cm-demanda" titulo={t('competencias.demanda')}>
                <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-cm-demanda">
                  <table className="tabela grade-pessoas" aria-labelledby="h-cm-demanda">
                    <thead>
                      <tr>
                        <th scope="col">{t('competencias.col.competencia')}</th>
                        <th scope="col" className="num">{t('competencias.col.tasks')}</th>
                        <th scope="col" className="num">{t('competencias.col.horas')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {demanda.map((c) => {
                        const n = dto.tasksPorCompetencia[c] ?? 0
                        return (
                          <tr key={c}>
                            <th scope="row">{c}</th>
                            <td className="num">
                              {bloqueado ? traco : n === 0 ? inteiro(n, lang) : (
                                <button
                                  type="button" className="contagem-botao" aria-label={`${c} ${t('competencias.col.tasks')}: ${n}`}
                                  onClick={() => abrir('CM-03', `skill:${c}`, `${c}: ${t('competencias.col.tasks')}`)}
                                >
                                  {inteiro(n, lang)}
                                </button>
                              )}
                            </td>
                            <td className="num">{bloqueado ? traco : numero(dto.demandaH[c], lang)}</td>
                          </tr>
                        )
                      })}
                    </tbody>
                  </table>
                </div>
              </Cartao>
            )}
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
