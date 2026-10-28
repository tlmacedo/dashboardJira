import { useState } from 'react'
import { useLang } from '../../i18n/LangProvider'
import { Cartao } from '../../shared/Cartao'
import { CardPage } from '../../shared/CardPage'
import { baixarCsv } from '../../shared/csv'
import { Filtros, resumoDosFiltros, useFiltrosDaUrl } from '../../shared/Filtros'
import { dataCurta, inteiro, numero } from '../../shared/format'
import { GavetaDeTasks } from '../../shared/GavetaDeTasks'
import { temBloqueio, useCard } from '../../shared/useCard'
import type { Revisor, RevisoesDto } from './api'
import '../../shared/tabela.css'
import '../../shared/pessoas.css'

type Gaveta = { cartaoId: string; segmento: string; titulo: string }

/**
 * Revisões pendentes por revisor (RV-01 e RV-02): as reviews abertas de cada revisor da visão, as vencidas e as que passaram do
 * prazo de revisão (dias úteis desde a criação). Sem ranking: revisores em ordem alfabética e sem coluna de posição. A situação
 * vai sempre em símbolo E texto (▲ fora do prazo, ● vencida, ✓ em dia). "Sem revisor" só chega para coordenação e gerente.
 */
export function Revisoes() {
  const { t, lang } = useLang()
  const [sp, aplicar] = useFiltrosDaUrl()
  const consulta = useCard<RevisoesDto>('revisoes', sp)
  const dto = consulta.data
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)
  const bloqueado = temBloqueio(dto?.avisos)
  const traco = '—'
  const resumo = resumoDosFiltros(sp, t, (iso) => dataCurta(iso, lang))
  const abrir = (cartaoId: string, segmento: string, titulo: string) => setGaveta({ cartaoId, segmento, titulo })

  function situacao(r: Revisor): { simbolo: string; texto: string; classe: string } {
    if (r.foraDoSla > 0) return { simbolo: '▲', texto: t('revisoes.situacao.fora'), classe: 'ocupacao ocupacao--problema' }
    if (r.vencidas > 0) return { simbolo: '●', texto: t('revisoes.situacao.vencida'), classe: 'ocupacao ocupacao--atencao' }
    return { simbolo: '✓', texto: t('revisoes.situacao.ok'), classe: 'ocupacao ocupacao--ok' }
  }

  function contagem(revisor: string, tipo: 'pendentes' | 'vencidas' | 'foraDoSla', n: number, rotulo: string) {
    if (bloqueado) return traco
    if (n === 0) return inteiro(n, lang)
    return (
      <button
        type="button" className="contagem-botao" aria-label={`${revisor} ${rotulo}: ${n}`}
        onClick={() => abrir('RV-01', `revisor:${revisor}:${tipo}`, `${revisor}: ${rotulo}`)}
      >
        {inteiro(n, lang)}
      </button>
    )
  }

  function exportar() {
    if (!dto) return
    baixarCsv('revisoes.csv', [
      [`# ${t('nav.revisoes')}`],
      [`# ${t('gaveta.csv.filtros')}: ${resumo}`],
      [],
      [t('revisoes.col.revisor'), t('revisoes.col.pendentes'), t('revisoes.col.vencidas'), t('revisoes.col.foraDoSla'), t('revisoes.col.idade')],
      ...dto.linhas.map((l) => [l.revisor, String(l.pendentes), String(l.vencidas), String(l.foraDoSla), l.idadeP50 === null ? '' : String(l.idadeP50)]),
    ])
  }

  return (
    <>
      <CardPage
        titulo={t('nav.revisoes')}
        pergunta={t('revisoes.pergunta')}
        filtros={
          <Filtros
            key={sp.toString()} campos={['squad', 'pessoa', 'prioridade', 'os']} valor={sp} aoAplicar={aplicar}
            pessoas={dto?.linhas.map((l) => l.revisor)}
          />
        }
        consulta={consulta}
        avisos={dto?.avisos}
        aoExportar={exportar}
      >
        {dto && (
          <>
            {dto.semRevisor !== null && (
              <div role="group" aria-label={t('revisoes.indicadores')} className="cardpage-kpis">
                <Cartao
                  id="RV-02" titulo={t('revisoes.kpi.semRevisor')} valor={bloqueado ? traco : inteiro(dto.semRevisor, lang)}
                  simboloDoValor={!bloqueado && dto.semRevisor > 0 ? '●' : undefined}
                  aoAbrir={!bloqueado && dto.semRevisor > 0 ? () => abrir('RV-02', 'semRevisor', t('revisoes.kpi.semRevisor')) : undefined}
                />
              </div>
            )}

            <Cartao id="RV-01" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-rv-tabela" titulo={t('revisoes.tabela')}>
              {dto.linhas.length === 0 ? (
                <p className="vazio">{t('revisoes.vazia')}</p>
              ) : (
                <>
                  <p className="legenda-meta">{t('pessoas.alfabetica')}</p>
                  <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-rv-tabela">
                    <table className="tabela grade-pessoas" aria-labelledby="h-rv-tabela">
                      <thead>
                        <tr>
                          <th scope="col">{t('revisoes.col.revisor')}</th>
                          <th scope="col" className="num">{t('revisoes.col.pendentes')}</th>
                          <th scope="col" className="num">{t('revisoes.col.vencidas')}</th>
                          <th scope="col" className="num">{t('revisoes.col.foraDoSla')}</th>
                          <th scope="col" className="num">{t('revisoes.col.idade')}</th>
                          <th scope="col">{t('revisoes.col.situacao')}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {dto.linhas.map((l) => {
                          const s = situacao(l)
                          return (
                            <tr key={l.revisor}>
                              <th scope="row">{l.revisor}</th>
                              <td className="num">{contagem(l.revisor, 'pendentes', l.pendentes, t('revisoes.col.pendentes'))}</td>
                              <td className="num">{contagem(l.revisor, 'vencidas', l.vencidas, t('revisoes.col.vencidas'))}</td>
                              <td className="num">{contagem(l.revisor, 'foraDoSla', l.foraDoSla, t('revisoes.col.foraDoSla'))}</td>
                              <td className="num">{bloqueado || l.idadeP50 === null ? traco : numero(l.idadeP50, lang)}</td>
                              <td className={s.classe}>
                                {bloqueado ? traco : (
                                  <>
                                    <span aria-hidden="true">{s.simbolo}</span> {s.texto}
                                  </>
                                )}
                              </td>
                            </tr>
                          )
                        })}
                      </tbody>
                    </table>
                  </div>
                  <p className="legenda-meta">{t('revisoes.nota')}</p>
                </>
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
