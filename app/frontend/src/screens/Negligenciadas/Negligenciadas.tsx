import { useState } from 'react'
import { useLang } from '../../i18n/LangProvider'
import { Cartao } from '../../shared/Cartao'
import { CardPage } from '../../shared/CardPage'
import { baixarCsv } from '../../shared/csv'
import { Filtros, resumoDosFiltros, useFiltrosDaUrl } from '../../shared/Filtros'
import { dataCurta, inteiro, numero } from '../../shared/format'
import { GavetaDeTasks } from '../../shared/GavetaDeTasks'
import { temBloqueio, useCard } from '../../shared/useCard'
import { CARTAO_DA_LISTA, KINDS, totalDaLista, type Kind, type NegligenciadasDto } from './api'
import '../../shared/tabela.css'

type Gaveta = { cartaoId: string; segmento: string; titulo: string }

const MOVIMENTO_REAL = 'semReal'

/**
 * Negligenciadas (NG-01 a NG-06): seis listas SEPARADAS (a mesma task pode estar em mais de uma). "Sem atualização" é pelo
 * `updated` do Jira; o filtro "sem movimento real" olha o changelog (status, responsável, vencimento, labels, resolução).
 */
export function Negligenciadas() {
  const { t, lang } = useLang()
  const [sp, aplicar] = useFiltrosDaUrl()
  const consulta = useCard<NegligenciadasDto>('negligenciadas', sp)
  const dto = consulta.data
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)
  const bloqueado = temBloqueio(dto?.avisos)
  const traco = '—'
  const resumo = resumoDosFiltros(sp, t, (iso) => dataCurta(iso, lang)) +
    (sp.get('movimento') === MOVIMENTO_REAL ? ` | ${t('negligenciadas.filtro.semMovimentoReal')}` : '')
  const unidadeDias = (k: Kind) =>
    k === 'paradas' || k === 'bloqueadas'
      ? t(dto?.unit === 'corrido' ? 'abertas.diasCorridos' : 'abertas.diasUteis')
      : t('abertas.diasCorridos')
  const abrir = (kind: Kind) => setGaveta({ cartaoId: CARTAO_DA_LISTA[kind], segmento: kind, titulo: t(`negligenciadas.lista.${kind}`) })

  function alternarMovimentoReal(ligado: boolean) {
    const p = new URLSearchParams(sp)
    if (ligado) p.set('movimento', MOVIMENTO_REAL)
    else p.delete('movimento')
    aplicar(p)
  }

  function exportar() {
    if (!dto) return
    const linhas: string[][] = []
    for (const k of KINDS) {
      for (const l of dto.listas[k] ?? []) {
        linhas.push([
          t(`negligenciadas.lista.${k}`), l.key, l.resumo, l.tipo, l.status, l.responsavel ?? '', l.vence ?? '', l.labels.join(' '),
          numero(l.dias, lang), t(`negligenciadas.campo.${l.ultimoMovimento.campo}`), l.ultimoMovimento.dia,
        ])
      }
    }
    baixarCsv('negligenciadas.csv', [
      [`# ${t('nav.negligenciadas')}`],
      [`# ${t('gaveta.csv.filtros')}: ${resumo}`],
      [],
      [t('negligenciadas.csv.lista'), t('abertas.col.task'), t('abertas.col.resumo'), t('abertas.col.tipo'), t('abertas.col.status'), t('abertas.col.responsavel'), t('abertas.col.vence'), 'Labels', t('negligenciadas.col.dias'), t('negligenciadas.col.ultimoMovimento'), t('negligenciadas.col.dataDoMovimento')],
      ...linhas,
    ])
  }

  return (
    <>
      <CardPage
        titulo={t('nav.negligenciadas')}
        pergunta={t('negligenciadas.pergunta')}
        filtros={
          <>
            <Filtros key={sp.toString()} campos={['squad', 'pessoa', 'tipo', 'prioridade', 'os', 'campos', 'unit']} valor={sp} aoAplicar={aplicar} />
            <label className="controle-movimento">
              <input
                type="checkbox"
                checked={sp.get('movimento') === MOVIMENTO_REAL}
                onChange={(e) => alternarMovimentoReal(e.target.checked)}
              />
              {t('negligenciadas.filtro.semMovimentoReal')}
            </label>
          </>
        }
        consulta={consulta}
        avisos={dto?.avisos}
        aoExportar={exportar}
      >
        {dto && (
          <>
            {dto.semMovimentoReal && (
              <p role="note" className="alerta-original">
                <span aria-hidden="true">●</span>{' '}
                {t('negligenciadas.filtro.semMovimentoReal.ativo').replace('{n}', String(dto.diasSemAtualizacao))}
              </p>
            )}
            <div role="group" aria-label={t('negligenciadas.indicadores')} className="cardpage-kpis">
              {KINDS.map((k) => {
                const total = totalDaLista(dto, k)
                return (
                  <Cartao
                    key={k}
                    id={CARTAO_DA_LISTA[k]}
                    titulo={t(`negligenciadas.lista.${k}`)}
                    valor={bloqueado ? traco : inteiro(total, lang)}
                    simboloDoValor={!bloqueado && total > 0 ? '▲' : undefined}
                    legenda={t(`negligenciadas.dica.${k}`).replace('{n}', String(dto.diasSemAtualizacao))}
                    aoAbrir={!bloqueado && total > 0 ? () => abrir(k) : undefined}
                  />
                )
              })}
            </div>

            {KINDS.map((k) => {
              const linhas = dto.listas[k] ?? []
              const total = totalDaLista(dto, k)
              const idTitulo = `h-ng-${k}`
              return (
                <Cartao
                  key={k}
                  id={CARTAO_DA_LISTA[k]}
                  variante="lista"
                  nivelDoTitulo={2}
                  idDoTitulo={idTitulo}
                  titulo={`${t(`negligenciadas.lista.${k}`)} (${bloqueado ? traco : inteiro(total, lang)})`}
                >
                  {linhas.length === 0 ? (
                    <p className="vazio">{t('negligenciadas.lista.vazia')}</p>
                  ) : (
                    <>
                      <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby={idTitulo}>
                        <table className="tabela" aria-labelledby={idTitulo}>
                          <thead>
                            <tr>
                              <th scope="col">{t('abertas.col.task')}</th>
                              <th scope="col">{t('abertas.col.status')}</th>
                              <th scope="col">{t('abertas.col.responsavel')}</th>
                              <th scope="col">{t('abertas.col.vence')}</th>
                              <th scope="col">Labels</th>
                              <th scope="col" className="num">
                                {t(`negligenciadas.dias.${k}`)} ({unidadeDias(k)})
                              </th>
                              <th scope="col">{t('negligenciadas.col.ultimoMovimento')}</th>
                            </tr>
                          </thead>
                          <tbody>
                            {linhas.map((l) => (
                              <tr key={l.key}>
                                <th scope="row">
                                  <span className="mono">{l.key}</span>
                                  <span className="resumo-task"> {l.resumo}</span>
                                </th>
                                <td>{l.status}</td>
                                <td>{l.responsavel ?? traco}</td>
                                <td>{l.vence ? dataCurta(l.vence, lang) : traco}</td>
                                <td>{l.labels.length ? l.labels.join(', ') : traco}</td>
                                <td className="num">{numero(l.dias, lang)}</td>
                                <td>
                                  {t(`negligenciadas.campo.${l.ultimoMovimento.campo}`)} · {dataCurta(l.ultimoMovimento.dia, lang)} (
                                  {numero(l.ultimoMovimento.dias, lang)} {t('abertas.diasCorridos')})
                                  {l.semMovimentoReal && (
                                    <span>
                                      {' '}
                                      <span aria-hidden="true">●</span> {t('negligenciadas.semMovimentoReal')}
                                    </span>
                                  )}
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                      {total > linhas.length && (
                        <p className="legenda-meta">
                          {t('negligenciadas.lista.limite').replace('{n}', String(linhas.length)).replace('{total}', inteiro(total, lang))}{' '}
                          <button type="button" className="contagem-botao" onClick={() => abrir(k)}>
                            {t('cartao.verTasks')}
                          </button>
                        </p>
                      )}
                    </>
                  )}
                </Cartao>
              )
            })}
          </>
        )}
      </CardPage>
      {gaveta && (
        <GavetaDeTasks
          cartaoId={gaveta.cartaoId}
          segmento={gaveta.segmento}
          titulo={gaveta.titulo}
          filtros={sp}
          resumoDosFiltros={resumo}
          aoFechar={() => setGaveta(null)}
        />
      )}
    </>
  )
}
