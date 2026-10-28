import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useLang } from '../../i18n/LangProvider'
import { CardPage } from '../../shared/CardPage'
import { Cartao } from '../../shared/Cartao'
import { baixarCsv } from '../../shared/csv'
import { inteiro, numero, percentual } from '../../shared/format'
import { GavetaDeTasks } from '../../shared/GavetaDeTasks'
import { temBloqueio } from '../../shared/useCard'
import '../../shared/tabela.css'
import { fetchRebalanceamento, type RebalanceamentoDto } from '../Distribuicao/api'
import { DecisaoDeSugestao } from '../Distribuicao/DecisaoDeSugestao'
import { FaixaModoSombra } from '../Distribuicao/Distribuicao'
import '../Distribuicao/distribuicao.css'

type Gaveta = { cartaoId: string; segmento: string; titulo: string }

/**
 * Rebalanceamento (RB-01 a RB-03), em MODO SOMBRA: tasks ainda não iniciadas que o painel sugere tirar de quem passou do
 * gatilho de ocupação, com a ocupação da origem antes e depois e a do destino depois. A lista vem em ordem de task (nunca por
 * quem está mais carregado) e a decisão é gravada só no painel.
 */
export function Rebalanceamento() {
  const { t, lang } = useLang()
  const qc = useQueryClient()
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)
  const consulta = useQuery({ queryKey: ['rebalanceamento'], queryFn: fetchRebalanceamento, placeholderData: keepPreviousData, retry: false })
  const dto: RebalanceamentoDto | undefined = consulta.data
  const bloqueado = temBloqueio(dto?.avisos)
  const traco = '—'
  const comAbrir = (n: number, cartaoId: string, segmento: string, titulo: string) =>
    !bloqueado && n > 0 ? () => setGaveta({ cartaoId, segmento, titulo }) : undefined
  const recarregar = () => void Promise.all([qc.invalidateQueries({ queryKey: ['rebalanceamento'] }), qc.invalidateQueries({ queryKey: ['decisoes'] })])
  const pct = (f: number) => percentual(f, lang)

  function exportar() {
    if (!dto) return
    baixarCsv('rebalanceamento.csv', [
      [`# ${t('nav.rebalanceamento')} (${dto.versaoRegras})`],
      [`# ${t('distribuicao.sombra')}`],
      [],
      [t('rebalanceamento.col.task'), t('rebalanceamento.col.de'), t('rebalanceamento.col.para'), t('rebalanceamento.col.horas'), t('rebalanceamento.col.origemAntes'), t('rebalanceamento.col.origemDepois'), t('rebalanceamento.col.destinoDepois'), t('rebalanceamento.col.decisao')],
      ...dto.movimentos.map((m) => [m.issueKey, m.de, m.para, String(m.horas), pct(m.ocupacaoOrigemAntes), pct(m.ocupacaoOrigemDepois), pct(m.ocupacaoDestinoDepois), m.decisao ? t(`decisao.${m.decisao}`) : '']),
    ])
  }

  return (
    <>
      <CardPage
        titulo={t('nav.rebalanceamento')}
        pergunta={t('rebalanceamento.pergunta')}
        filtros={<FaixaModoSombra />}
        consulta={consulta}
        avisos={dto?.avisos}
        aoExportar={exportar}
      >
        {dto && (
          <>
            <div role="group" aria-label={t('rebalanceamento.indicadores')} className="cardpage-kpis">
              <Cartao
                id="RB-01" titulo={t('rebalanceamento.kpi.movimentos')} valor={bloqueado ? traco : inteiro(dto.movimentos.length, lang)}
                aoAbrir={comAbrir(dto.movimentos.length, 'RB-01', 'movimentos', t('rebalanceamento.kpi.movimentos'))}
              />
              <Cartao
                id="RB-02" titulo={t('rebalanceamento.kpi.semDecisao')} valor={bloqueado ? traco : inteiro(dto.movimentosPendentes, lang)}
                aoAbrir={comAbrir(dto.movimentosPendentes, 'RB-02', 'semDecisao', t('rebalanceamento.kpi.semDecisao'))}
              />
            </div>

            <Cartao id="RB-01" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-rb-tabela" titulo={t('rebalanceamento.tabela')}>
              <p className="legenda-meta">
                {t('rebalanceamento.regra').replace('{gatilho}', String(Math.round(dto.gatilho * 100))).replace('{pp}', String(Math.round(dto.histerese)))}
              </p>
              {dto.movimentos.length === 0 ? (
                <p className="vazio">{t('rebalanceamento.vazia')}</p>
              ) : (
                <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-rb-tabela">
                  <table className="tabela mov-tabela" aria-labelledby="h-rb-tabela">
                    <thead>
                      <tr>
                        <th scope="col">{t('rebalanceamento.col.task')}</th>
                        <th scope="col">{t('rebalanceamento.col.de')}</th>
                        <th scope="col">{t('rebalanceamento.col.para')}</th>
                        <th scope="col" className="num">{t('rebalanceamento.col.horas')}</th>
                        <th scope="col" className="num">{t('rebalanceamento.col.origemAntes')}</th>
                        <th scope="col" className="num">{t('rebalanceamento.col.origemDepois')}</th>
                        <th scope="col" className="num">{t('rebalanceamento.col.destinoDepois')}</th>
                        <th scope="col">{t('rebalanceamento.col.decisao')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {dto.movimentos.map((m) => (
                        <tr key={m.issueKey}>
                          <th scope="row">
                            {m.issueKey}
                            <br />
                            <span className="legenda-meta">{m.resumo}</span>
                          </th>
                          <td>{m.de}</td>
                          <td>{m.para}</td>
                          <td className="num">{numero(m.horas, lang)}</td>
                          <td className="num">{pct(m.ocupacaoOrigemAntes)}</td>
                          <td className="num">{pct(m.ocupacaoOrigemDepois)}</td>
                          <td className="num">{pct(m.ocupacaoDestinoDepois)}</td>
                          <td className="decisao-cel">
                            <DecisaoDeSugestao
                              tipo="REBALANCEAMENTO" issueKey={m.issueKey} sugerido={m.para}
                              candidatos={dto.candidatos.filter((p) => p !== m.de)} decisao={m.decisao} aoDecidir={recarregar}
                            />
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </Cartao>
            <p className="legenda-meta">{t('distribuicao.rodape').replace('{versao}', dto.versaoRegras)}</p>
          </>
        )}
      </CardPage>
      {gaveta && (
        <GavetaDeTasks
          cartaoId={gaveta.cartaoId} segmento={gaveta.segmento} titulo={gaveta.titulo} filtros={new URLSearchParams()}
          resumoDosFiltros="" aoFechar={() => setGaveta(null)}
        />
      )}
    </>
  )
}
