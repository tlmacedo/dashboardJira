import { useState } from 'react'
import { useLang } from '../../i18n/LangProvider'
import { Cartao } from '../../shared/Cartao'
import { CardPage } from '../../shared/CardPage'
import { baixarCsv } from '../../shared/csv'
import { Filtros, resumoDosFiltros, useFiltrosDaUrl } from '../../shared/Filtros'
import { dataCurta, inteiro } from '../../shared/format'
import { GavetaDeTasks } from '../../shared/GavetaDeTasks'
import { temBloqueio, useCard } from '../../shared/useCard'
import type { FaixaFluxo, GargalosDto } from './api'
import '../../shared/tabela.css'
import '../../shared/pessoas.css'

type Gaveta = { cartaoId: string; segmento: string; titulo: string }

/** Linha do tempo de uma faixa (decorativa: os números estão na tabela e no texto ao lado). */
function Faixa({ f, dias }: { f: FaixaFluxo; dias: number }) {
  const maior = Math.max(1, ...f.contagens)
  const largura = 300
  const altura = 36
  const pontos = f.contagens
    .map((v, i) => `${(i / Math.max(1, dias - 1)) * largura},${altura - 2 - (v / maior) * (altura - 6)}`)
    .join(' ')
  return (
    <svg viewBox={`0 0 ${largura} ${altura}`} preserveAspectRatio="none" aria-hidden="true">
      <polyline points={pontos} />
    </svg>
  )
}

/**
 * Gargalos e fluxo (GA-01 a GA-04), só para a liderança: o fluxo cumulativo dos últimos 60 dias (uma linha por status, na
 * ordem do fluxo, com os números ao lado), o estado das etapas com o MOTIVO do gargalo em texto e as contagens de pessoas
 * acima da capacidade e com folga (sem ranking).
 */
export function Gargalos() {
  const { t, lang } = useLang()
  const [sp, aplicar] = useFiltrosDaUrl()
  const consulta = useCard<GargalosDto>('gargalos', sp)
  const dto = consulta.data
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)
  const [tabela, setTabela] = useState(false)
  const bloqueado = temBloqueio(dto?.avisos)
  const traco = '—'
  const resumo = resumoDosFiltros(sp, t, (iso) => dataCurta(iso, lang))
  const abrir = (cartaoId: string, segmento: string, titulo: string) => setGaveta({ cartaoId, segmento, titulo })
  const comAbrir = (n: number, cartaoId: string, segmento: string, titulo: string) =>
    !bloqueado && n > 0 ? () => abrir(cartaoId, segmento, titulo) : undefined

  function exportar() {
    if (!dto) return
    baixarCsv('gargalos.csv', [
      [`# ${t('nav.gargalos')}`],
      [`# ${t('gaveta.csv.filtros')}: ${resumo}`],
      [],
      [t('gargalos.col.etapa'), t('gargalos.col.categoria'), t('gargalos.col.wip'), t('gargalos.col.limite'), t('gargalos.col.crescimento'), t('gargalos.col.situacao')],
      ...dto.etapas.map((e) => [e.status, e.categoria, String(e.wip), e.limite === null ? '' : String(e.limite), String(e.crescimento15d), e.motivo ? t(`gargalos.motivo.${e.motivo}`) : '']),
      [],
      // O fluxo cumulativo inteiro: uma linha por dia, uma coluna por status.
      [t('gargalos.tabela.dia'), ...dto.faixas.map((f) => f.status)],
      ...dto.dias.map((d, i) => [d, ...dto.faixas.map((f) => String(f.contagens[i] ?? ''))]),
    ])
  }

  const sinal = (n: number) => (n > 0 ? `+${n}` : String(n))

  return (
    <>
      <CardPage
        titulo={t('nav.gargalos')}
        pergunta={t('gargalos.pergunta')}
        filtros={<Filtros key={sp.toString()} campos={['squad', 'pessoa']} valor={sp} aoAplicar={aplicar} />}
        consulta={consulta}
        avisos={dto?.avisos}
        aoExportar={exportar}
      >
        {dto && (
          <>
            <div role="group" aria-label={t('gargalos.indicadores')} className="cardpage-kpis">
              <Cartao
                id="GA-03" titulo={t('gargalos.kpi.acima')} valor={bloqueado ? traco : inteiro(dto.acimaDaCapacidade, lang)}
                simboloDoValor={!bloqueado && dto.acimaDaCapacidade > 0 ? '▲' : undefined}
                legenda={t('carga.kpi.tasksNaFila').replace('{n}', inteiro(dto.tasksAcimaDaCapacidade, lang))}
                aoAbrir={comAbrir(dto.tasksAcimaDaCapacidade, 'GA-03', 'acimaDaCapacidade', t('gargalos.kpi.acima'))}
              />
              <Cartao
                id="GA-04" titulo={t('gargalos.kpi.folga')} valor={bloqueado ? traco : inteiro(dto.comFolga, lang)}
                legenda={t('carga.kpi.tasksNaFila').replace('{n}', inteiro(dto.tasksComFolga, lang))}
                aoAbrir={comAbrir(dto.tasksComFolga, 'GA-04', 'comFolga', t('gargalos.kpi.folga'))}
              />
            </div>

            <Cartao id="GA-01" variante="serie" nivelDoTitulo={2} idDoTitulo="h-ga-fluxo" titulo={t('gargalos.fluxo')}>
              <p className="legenda-meta">{t('gargalos.fluxo.nota').replace('{de}', dataCurta(dto.dias[0] ?? '', lang)).replace('{ate}', dataCurta(dto.dias.at(-1) ?? '', lang))}</p>
              {dto.faixas.length === 0 ? (
                <p className="vazio">{t('gargalos.vazia')}</p>
              ) : (
                <div role="list" aria-labelledby="h-ga-fluxo">
                  {dto.faixas.map((f) => {
                    const hoje = f.contagens.at(-1) ?? 0
                    const antes = f.contagens.at(-16) ?? 0
                    return (
                      <div className="faixa-fluxo" role="listitem" key={f.status}>
                        <strong>{f.status}</strong>
                        <Faixa f={f} dias={dto.dias.length} />
                        <span>
                          {bloqueado ? traco : t('gargalos.fluxo.hoje').replace('{n}', inteiro(hoje, lang)).replace('{d}', sinal(hoje - antes))}
                        </span>
                      </div>
                    )
                  })}
                </div>
              )}
              {dto.faixas.length > 0 && !bloqueado && (
                <>
                  <button type="button" className="btn" aria-expanded={tabela} onClick={() => setTabela(!tabela)}>
                    {t(tabela ? 'gargalos.tabela.ocultar' : 'gargalos.tabela.ver')}
                  </button>
                  {tabela && (
                    <div className="tabela-rolagem" tabIndex={0} role="region" aria-label={t('gargalos.tabela.titulo')}>
                      <table className="tabela" aria-label={t('gargalos.tabela.titulo')}>
                        <thead>
                          <tr>
                            <th scope="col">{t('gargalos.tabela.dia')}</th>
                            {dto.faixas.map((f) => (
                              <th scope="col" className="num" key={f.status}>{f.status}</th>
                            ))}
                          </tr>
                        </thead>
                        <tbody>
                          {dto.dias.map((d, i) => (
                            <tr key={d + i}>
                              <th scope="row">{dataCurta(d, lang)}</th>
                              {dto.faixas.map((f) => (
                                <td className="num" key={f.status}>{inteiro(f.contagens[i] ?? 0, lang)}</td>
                              ))}
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </>
              )}
            </Cartao>

            <Cartao id="GA-02" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-ga-etapas" titulo={t('gargalos.etapas')}>
              {dto.etapas.length === 0 ? (
                <p className="vazio">{t('gargalos.vazia')}</p>
              ) : (
                <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-ga-etapas">
                  <table className="tabela" aria-labelledby="h-ga-etapas">
                    <thead>
                      <tr>
                        <th scope="col">{t('gargalos.col.etapa')}</th>
                        <th scope="col">{t('gargalos.col.categoria')}</th>
                        <th scope="col" className="num">{t('gargalos.col.wip')}</th>
                        <th scope="col" className="num">{t('gargalos.col.limite')}</th>
                        <th scope="col" className="num">{t('gargalos.col.crescimento')}</th>
                        <th scope="col">{t('gargalos.col.situacao')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {dto.etapas.map((e) => (
                        <tr key={e.status}>
                          <th scope="row">{e.status}</th>
                          <td>{t(`gargalos.categoria.${e.categoria}`)}</td>
                          <td className="num">
                            {bloqueado ? traco : e.wip > 0 ? (
                              <button
                                type="button" className="contagem-botao" aria-label={`${e.status} ${t('gargalos.col.wip')}: ${e.wip}`}
                                onClick={() => abrir('GA-02', `status:${e.status}`, `${t('gargalos.col.etapa')}: ${e.status}`)}
                              >
                                {inteiro(e.wip, lang)}
                              </button>
                            ) : (
                              inteiro(0, lang)
                            )}
                          </td>
                          <td className="num">{e.limite === null ? traco : inteiro(e.limite, lang)}</td>
                          <td className="num">{bloqueado ? traco : sinal(e.crescimento15d)}</td>
                          <td>
                            {e.gargalo && e.motivo ? (
                              <span className="ocupacao ocupacao--problema">
                                <span aria-hidden="true">▲</span> {t('gargalos.gargalo')}: {t(`gargalos.motivo.${e.motivo}`)}
                              </span>
                            ) : (
                              <span className="ocupacao ocupacao--ok">
                                <span aria-hidden="true">✓</span> {t('gargalos.semGargalo')}
                              </span>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              <p className="legenda-meta">{t('gargalos.etapas.nota')}</p>
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
