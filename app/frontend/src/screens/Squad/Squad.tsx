import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useLang } from '../../i18n/LangProvider'
import { Cartao } from '../../shared/Cartao'
import { CardPage } from '../../shared/CardPage'
import { baixarCsv } from '../../shared/csv'
import { dataCurta, inteiro, percentual } from '../../shared/format'
import { GavetaDeTasks } from '../../shared/GavetaDeTasks'
import { Ocupacao } from '../../shared/Ocupacao'
import { temBloqueio } from '../../shared/useCard'
import '../../shared/pessoas.css'
import '../../shared/tabela.css'
import { SIMBOLO_DO_ESTADO } from '../Central/api'
import '../Central/inicio.css'
import { fetchSquad } from './api'

type Gaveta = { cartaoId: string; segmento: string; titulo: string; filtros: URLSearchParams }

/**
 * Visão do squad (Squad Leader; coordenação e gerente escolhem o squad): a carga de cada pessoa em ordem alfabética (sem nota),
 * o que vence na semana com a folga real, as ausências (sem motivo) e o risco. Cada número abre as tasks do cartão do card de
 * origem, com o mesmo filtro de squad. Layout editorial.
 */
export function Squad() {
  const { t, lang } = useLang()
  const [sp] = useSearchParams()
  const pedido = sp.get('squad')
  const consulta = useQuery({ queryKey: ['squad', pedido], queryFn: () => fetchSquad(pedido), placeholderData: keepPreviousData, retry: false })
  const dto = consulta.data
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)
  const bloqueado = temBloqueio(dto?.avisos) || temBloqueio(dto?.vencimentos.avisos)
  const nomeDoSquad = dto?.squad ?? ''
  const doSquad = new URLSearchParams({ squad: nomeDoSquad })
  const da7Dias = new URLSearchParams({ squad: nomeDoSquad, dias: '7' })
  const abrir = (cartaoId: string, segmento: string, titulo: string, filtros = doSquad) => setGaveta({ cartaoId, segmento, titulo, filtros })
  const comAbrir = (n: number, cartaoId: string, segmento: string, titulo: string, filtros = doSquad) =>
    !bloqueado && n > 0 ? () => abrir(cartaoId, segmento, titulo, filtros) : undefined

  function exportar() {
    if (!dto) return
    baixarCsv('squad.csv', [
      [`# ${t('nav.squad')}: ${dto.squad}`],
      [],
      [t('carga.col.pessoa'), t('squad.col.abertas'), t('squad.col.vencidas'), t('squad.col.risco')],
      ...dto.carga.map((l) => [l.pessoa, String(l.abertas), String(l.vencidas), String(l.riscoAlto)]),
    ])
  }

  return (
    <>
      <CardPage
        titulo={dto ? `${t('nav.squad')}: ${dto.squad}` : t('nav.squad')}
        pergunta={t('squad.pergunta')}
        filtros={null}
        consulta={consulta}
        avisos={dto?.avisos}
        aoExportar={exportar}
      >
        {dto && (
          <>
            <div role="group" aria-label={t('squad.indicadores')} className="cardpage-kpis">
              <Cartao
                id="VE-01" titulo={t('squad.kpi.vencem')} valor={bloqueado ? '—' : inteiro(dto.vencimentos.vencem, lang)}
                aoAbrir={comAbrir(dto.vencimentos.vencem, 'VE-01', 'vencem', t('squad.kpi.vencem'), da7Dias)}
              />
              <Cartao
                id="VE-02" titulo={t('squad.kpi.atrasar')} valor={bloqueado ? '—' : inteiro(dto.vencimentos.riscoDeAtrasar, lang)}
                simboloDoValor={!bloqueado && dto.vencimentos.riscoDeAtrasar > 0 ? '▲' : undefined}
                aoAbrir={comAbrir(dto.vencimentos.riscoDeAtrasar, 'VE-02', 'riscoDeAtrasar', t('squad.kpi.atrasar'), da7Dias)}
              />
              <Cartao
                id="RI-01" ajudaComTitulo titulo={t('squad.kpi.riscoAlto')} valor={bloqueado ? '—' : inteiro(dto.riscoAlto, lang)}
                simboloDoValor={!bloqueado && dto.riscoAlto > 0 ? '▲' : undefined}
                aoAbrir={comAbrir(dto.riscoAlto, 'RI-01', 'nivel:alto', t('squad.kpi.riscoAlto'))}
              />
              <Cartao
                id="RI-01" ajudaComTitulo titulo={t('squad.kpi.riscoMedio')} valor={bloqueado ? '—' : inteiro(dto.riscoMedio, lang)}
                aoAbrir={comAbrir(dto.riscoMedio, 'RI-01', 'nivel:medio', t('squad.kpi.riscoMedio'))}
              />
            </div>

            <Cartao id="CP-03" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-squad-carga" titulo={t('squad.carga')}>
              {dto.carga.length === 0 ? (
                <p className="vazio">{t('carga.vazia')}</p>
              ) : (
                <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-squad-carga">
                  <table className="tabela grade-pessoas" aria-labelledby="h-squad-carga">
                    <thead>
                      <tr>
                        <th scope="col">{t('carga.col.pessoa')}</th>
                        <th scope="col" className="num">{t('squad.col.abertas')}</th>
                        <th scope="col">{t('squad.col.ocupacao')}</th>
                        <th scope="col" className="num">{t('squad.col.vencidas')}</th>
                        <th scope="col" className="num">{t('squad.col.risco')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {dto.carga.map((l) => (
                        <tr key={l.pessoa}>
                          <th scope="row">{l.pessoa}</th>
                          <td className="num">
                            {!bloqueado && l.abertas > 0 ? (
                              <button
                                type="button" className="contagem-botao" aria-label={`${l.pessoa}: ${l.abertas}`}
                                onClick={() => abrir('CP-03', `pessoa:${l.pessoa}:fila`, `${l.pessoa}: ${t('squad.col.abertas')}`)}
                              >
                                {inteiro(l.abertas, lang)}
                              </button>
                            ) : bloqueado ? '—' : inteiro(l.abertas, lang)}
                          </td>
                          <td><Ocupacao o={l.ocupacao} compacta /></td>
                          <td className="num">{bloqueado ? '—' : inteiro(l.vencidas, lang)}</td>
                          <td className="num">{bloqueado ? '—' : inteiro(l.riscoAlto, lang)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              <p className="legenda-meta">{t('squad.carga.nota')}</p>
            </Cartao>

            <Cartao id="VE-01" ajudaComTitulo variante="tabela" nivelDoTitulo={2} idDoTitulo="h-squad-venc" titulo={t('squad.vencimentos')}>
              {dto.vencimentos.linhas.length === 0 ? (
                <p className="vazio">{t('squad.vencimentos.vazia')}</p>
              ) : (
                <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-squad-venc">
                  <table className="tabela" aria-labelledby="h-squad-venc">
                    <thead>
                      <tr>
                        <th scope="col">{t('squad.venc.task')}</th>
                        <th scope="col">{t('carga.col.pessoa')}</th>
                        <th scope="col">{t('squad.venc.vence')}</th>
                        <th scope="col">{t('squad.venc.termino')}</th>
                        <th scope="col">{t('squad.venc.situacao')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {dto.vencimentos.linhas.map((l) => (
                        <tr key={l.key}>
                          <th scope="row">{l.key}</th>
                          <td>{l.pessoa ?? '—'}</td>
                          <td>{dataCurta(l.vence, lang)}</td>
                          <td>{l.termino ? dataCurta(l.termino, lang) : '—'}</td>
                          <td>{SIMBOLO_DO_ESTADO[l.estado] ?? '■'} {t(`squad.venc.estado.${l.estado}`)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </Cartao>

            <Cartao id="CA-06" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-squad-aus" titulo={t('squad.ausencias')}>
              {dto.ausencias.length === 0 ? (
                <p className="vazio">{t('squad.ausencias.vazia')}</p>
              ) : (
                <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-squad-aus">
                  <table className="tabela" aria-labelledby="h-squad-aus">
                    <thead>
                      <tr>
                        <th scope="col">{t('carga.col.pessoa')}</th>
                        <th scope="col">{t('squad.aus.de')}</th>
                        <th scope="col">{t('squad.aus.ate')}</th>
                        <th scope="col" className="num">{t('squad.aus.fracao')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {dto.ausencias.map((a) => (
                        <tr key={`${a.pessoa}:${a.de}`}>
                          <th scope="row">{a.pessoa}</th>
                          <td>{dataCurta(a.de, lang)}</td>
                          <td>{dataCurta(a.ate, lang)}</td>
                          <td className="num">{percentual(a.fracao, lang)}</td>
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
        <GavetaDeTasks cartaoId={gaveta.cartaoId} segmento={gaveta.segmento} titulo={gaveta.titulo} filtros={gaveta.filtros} aoFechar={() => setGaveta(null)} />
      )}
    </>
  )
}
