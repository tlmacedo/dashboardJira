import { useState } from 'react'
import { useLang } from '../../i18n/LangProvider'
import { Cartao } from '../../shared/Cartao'
import { CardPage } from '../../shared/CardPage'
import { baixarCsv } from '../../shared/csv'
import { Filtros, resumoDosFiltros, useFiltrosDaUrl } from '../../shared/Filtros'
import { dataCurta, inteiro, numero } from '../../shared/format'
import { GavetaDeTasks } from '../../shared/GavetaDeTasks'
import { temBloqueio, useCard } from '../../shared/useCard'
import type { EsforcoDto } from './api'
import '../../shared/tabela.css'
import '../../shared/pessoas.css'

type Gaveta = { cartaoId: string; segmento: string; titulo: string }

/**
 * Esforço por task fechada (EF-01 e EF-02): os TRÊS componentes (peso do tipo, testes TEST-* e horas) e o índice composto
 * lado a lado, com a versão das regras (proposta inicial, a calibrar). Por pessoa, o esforço segue a POSSE da task e não o
 * autor do worklog. Sem worklog, as horas vêm do tempo gasto do Jira e a linha diz isso em texto. Pessoas em ordem alfabética.
 */
export function Esforco() {
  const { t, lang } = useLang()
  const [sp, aplicar] = useFiltrosDaUrl()
  const consulta = useCard<EsforcoDto>('esforco', sp)
  const dto = consulta.data
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)
  const bloqueado = temBloqueio(dto?.avisos)
  const traco = '—'
  const resumo = resumoDosFiltros(sp, t, (iso) => dataCurta(iso, lang))
  const porMes = sp.get('granularidade') === 'mes'
  const abrir = (cartaoId: string, segmento: string, titulo: string) => setGaveta({ cartaoId, segmento, titulo })

  function escolher(mes: boolean) {
    const novo = new URLSearchParams(sp)
    if (mes) novo.set('granularidade', 'mes')
    else novo.delete('granularidade')
    aplicar(novo)
  }

  const linha = (x: { pesoTipo: number; testes: number; horas: number; indice: number }) =>
    t('esforco.linha')
      .replace('{tipo}', numero(x.pesoTipo, lang))
      .replace('{testes}', Number.isInteger(x.testes) ? inteiro(x.testes, lang) : numero(x.testes, lang))
      .replace('{horas}', numero(x.horas, lang))
      .replace('{indice}', numero(x.indice, lang))

  function exportar() {
    if (!dto) return
    baixarCsv('esforco.csv', [
      [`# ${t('nav.esforco')} (${dto.versaoRegras})`],
      [`# ${t('gaveta.csv.filtros')}: ${resumo}`],
      [],
      [t('esforco.col.task'), t('esforco.col.tipo'), t('esforco.col.testes'), t('esforco.col.horas'), t('esforco.col.indice')],
      ...dto.tasks.map((x) => [x.key, String(x.pesoTipo), String(x.testes), String(x.horas), String(x.indice)]),
    ])
  }

  function contagem(pessoa: string, n: number) {
    if (bloqueado) return traco
    if (n === 0) return inteiro(n, lang)
    return (
      <button
        type="button" className="contagem-botao" aria-label={`${pessoa} ${t('esforco.col.tasks')}: ${n}`}
        onClick={() => abrir('EF-02', `pessoa:${pessoa}`, `${pessoa}: ${t('esforco.col.tasks')}`)}
      >
        {inteiro(n, lang)}
      </button>
    )
  }

  const rotuloDoPeriodo = (inicio: string) =>
    porMes ? inicio.slice(0, 7).split('-').reverse().join('/') : t('esforco.semanaDe').replace('{data}', dataCurta(inicio, lang))

  return (
    <>
      <CardPage
        titulo={t('nav.esforco')}
        pergunta={t('esforco.pergunta')}
        filtros={
          <Filtros
            key={sp.toString()} campos={['periodo', 'squad', 'pessoa', 'tipo', 'prioridade', 'os', 'campos', 'unit']} valor={sp} aoAplicar={aplicar}
            pessoas={dto?.pessoas.map((p) => p.pessoa)}
            extras={
              <div role="group" aria-label={t('esforco.granularidade')} className="seg seg--claro">
                {[false, true].map((mes) => (
                  <button key={String(mes)} type="button" aria-pressed={porMes === mes} onClick={() => escolher(mes)}>
                    {t(mes ? 'esforco.mes' : 'esforco.semana')}
                  </button>
                ))}
              </div>
            }
          />
        }
        consulta={consulta}
        avisos={dto?.avisos}
        aoExportar={exportar}
      >
        {dto && (
          <>
            <div role="group" aria-label={t('esforco.indicadores')} className="cardpage-kpis">
              <Cartao
                id="EF-01" titulo={t('esforco.kpi.tasks')} valor={bloqueado ? traco : inteiro(dto.tasks.length, lang)}
                legenda={dto.versaoRegras}
                aoAbrir={!bloqueado && dto.tasks.length > 0 ? () => abrir('EF-01', 'tasks', t('esforco.kpi.tasks')) : undefined}
              />
            </div>

            <Cartao id="EF-01" variante="lista" nivelDoTitulo={2} idDoTitulo="h-ef-tasks" titulo={t('esforco.tabela.tasks')}>
              {dto.tasks.length === 0 ? (
                <p className="vazio">{t('esforco.vazia')}</p>
              ) : (
                <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-ef-tasks">
                  <table className="tabela" aria-label={t('esforco.tabela.tasks')}>
                    <thead>
                      <tr>
                        <th scope="col">{t('esforco.col.task')}</th>
                        <th scope="col">{t('esforco.col.esforco')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {dto.tasks.map((x) => (
                        <tr key={x.key}>
                          <td>
                            <span className="mono">{x.key}</span>
                            <span className="resumo-task"> {x.resumo}</span>
                          </td>
                          <td>
                            {bloqueado ? traco : linha(x)}
                            {x.horasDoCampo && !bloqueado && (
                              <span className="legenda-meta"> <span aria-hidden="true">●</span> {t('esforco.horasDoCampo')}</span>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </Cartao>

            {dto.pessoas.length > 0 && (
              <Cartao id="EF-02" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-ef-pessoas" titulo={t('esforco.tabela.pessoas')}>
                <p className="legenda-meta">{t('pessoas.alfabetica')}</p>
                <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-ef-pessoas">
                  <table className="tabela grade-pessoas" aria-labelledby="h-ef-pessoas">
                    <thead>
                      <tr>
                        <th scope="col">{t('esforco.col.pessoa')}</th>
                        <th scope="col" className="num">{t('esforco.col.tasks')}</th>
                        <th scope="col" className="num">{t('esforco.col.tipo')}</th>
                        <th scope="col" className="num">{t('esforco.col.testes')}</th>
                        <th scope="col" className="num">{t('esforco.col.horas')}</th>
                        <th scope="col" className="num">{t('esforco.col.indice')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {dto.pessoas.map((p) => (
                        <tr key={p.pessoa}>
                          <th scope="row">{p.pessoa}</th>
                          <td className="num">{contagem(p.pessoa, p.tasks)}</td>
                          <td className="num">{bloqueado ? traco : numero(p.pesoTipo, lang)}</td>
                          <td className="num">{bloqueado ? traco : numero(p.testes, lang)}</td>
                          <td className="num">
                            {bloqueado ? traco : numero(p.horas, lang)}
                            {p.horasDoCampo && <span title={t('esforco.horasDoCampo')} aria-label={t('esforco.horasDoCampo')}> ≈</span>}
                          </td>
                          <td className="num">{bloqueado ? traco : numero(p.indice, lang)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </Cartao>
            )}

            {dto.periodos.length > 0 && (
              <Cartao id="EF-01" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-ef-periodos" titulo={t('esforco.tabela.periodos')}>
                <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-ef-periodos">
                  <table className="tabela" aria-labelledby="h-ef-periodos">
                    <thead>
                      <tr>
                        <th scope="col">{t('esforco.col.periodo')}</th>
                        <th scope="col" className="num">{t('esforco.col.tasks')}</th>
                        <th scope="col" className="num">{t('esforco.col.horas')}</th>
                        <th scope="col" className="num">{t('esforco.col.indice')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {dto.periodos.map((p) => (
                        <tr key={p.inicio}>
                          <th scope="row">{rotuloDoPeriodo(p.inicio)}</th>
                          <td className="num">{inteiro(p.tasks, lang)}</td>
                          <td className="num">{numero(p.horas, lang)}</td>
                          <td className="num">{numero(p.indice, lang)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </Cartao>
            )}
            <p className="legenda-meta">{t('esforco.rodape').replace('{versao}', dto.versaoRegras)}</p>
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
