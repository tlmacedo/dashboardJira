import { useState } from 'react'
import { useLang } from '../../i18n/LangProvider'
import { Cartao } from '../../shared/Cartao'
import { CardPage } from '../../shared/CardPage'
import { baixarCsv } from '../../shared/csv'
import { Filtros, resumoDosFiltros, useFiltrosDaUrl } from '../../shared/Filtros'
import { dataCurta, numero } from '../../shared/format'
import { GavetaDeTasks } from '../../shared/GavetaDeTasks'
import { temBloqueio, useCard } from '../../shared/useCard'
import type { CoberturaDto } from './api'
import '../../shared/tabela.css'
import '../../shared/pessoas.css'

type Gaveta = { segmento: string; titulo: string }

const HORIZONTES = [7, 14, 30, 60, 90]

/**
 * Cobertura por ausência (CB-01), só liderança: para cada ausência próxima, as tasks que ficam sem dono, as horas a replanejar,
 * as competências pedidas e quem do mesmo squad pode cobrir. Os candidatos saem em ORDEM ALFABÉTICA com a folga ao lado (nunca
 * ordenados por folga: seria um ranking de disponibilidade). A ausência não tem motivo. O horizonte vai na URL (`dias`).
 */
export function Cobertura() {
  const { t, lang } = useLang()
  const [sp, aplicar] = useFiltrosDaUrl()
  const consulta = useCard<CoberturaDto>('cobertura', sp)
  const dto = consulta.data
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)
  const bloqueado = temBloqueio(dto?.avisos)
  const traco = '—'
  const resumo = resumoDosFiltros(sp, t, (iso) => dataCurta(iso, lang))
  const horizonte = Number(sp.get('dias')) >= 1 && Number(sp.get('dias')) <= 90 ? Number(sp.get('dias')) : (dto?.dias ?? 30)

  function escolher(n: number) {
    const novo = new URLSearchParams(sp)
    novo.set('dias', String(n))
    aplicar(novo)
  }

  function exportar() {
    if (!dto) return
    baixarCsv('cobertura.csv', [
      [`# ${t('nav.cobertura')}`],
      [`# ${t('gaveta.csv.filtros')}: ${resumo}`],
      [],
      [t('cobertura.col.pessoa'), t('cobertura.col.de'), t('cobertura.col.ate'), t('cobertura.col.tasks'), t('cobertura.col.horas'), t('cobertura.col.candidatos')],
      ...dto.ausencias.map((a) => [a.pessoa, a.de, a.ate, String(a.issues.length), String(a.horasAReplanejar), a.podemCobrir.map((c) => c.pessoa).join('; ')]),
    ])
  }

  return (
    <>
      <CardPage
        titulo={t('nav.cobertura')}
        pergunta={t('cobertura.pergunta')}
        filtros={
          <Filtros
            key={sp.toString()} campos={['squad', 'pessoa']} valor={sp} aoAplicar={aplicar}
            pessoas={dto?.ausencias.map((a) => a.pessoa)}
            extras={
              <div role="group" aria-label={t('cobertura.horizonte')} className="seg seg--claro">
                {HORIZONTES.map((n) => (
                  <button key={n} type="button" aria-pressed={horizonte === n} onClick={() => escolher(n)}>
                    {t('cobertura.nDias').replace('{n}', String(n))}
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
          <Cartao id="CB-01" variante="lista" nivelDoTitulo={2} idDoTitulo="h-cb-lista" titulo={t('cobertura.lista')}>
            {dto.ausencias.length === 0 ? (
              <p className="vazio">{t('cobertura.vazia')}</p>
            ) : (
              <div className="cobertura-lista">
                {dto.ausencias.map((a) => {
                  const idTitulo = `h-cb-${a.pessoa}-${a.de}`
                  const periodo = t('cobertura.periodo').replace('{de}', dataCurta(a.de, lang)).replace('{ate}', dataCurta(a.ate, lang))
                  return (
                    <article key={`${a.pessoa}:${a.de}`} className="cobertura-item" aria-labelledby={idTitulo}>
                      <h3 id={idTitulo}>
                        {a.pessoa} · {periodo}
                      </h3>
                      {a.issues.length === 0 ? (
                        <p>{t('cobertura.semTrabalho')}</p>
                      ) : (
                        <>
                          <p>
                            {bloqueado ? (
                              traco
                            ) : (
                              <button
                                type="button" className="contagem-botao"
                                onClick={() => setGaveta({ segmento: `ausencia:${a.pessoa}:${a.de}`, titulo: `${a.pessoa}: ${periodo}` })}
                              >
                                {t('cobertura.tasks').replace('{n}', String(a.issues.length))}
                              </button>
                            )}
                            {' · '}
                            {t('cobertura.horas').replace('{h}', numero(a.horasAReplanejar, lang))}
                          </p>
                          {a.competencias.length > 0 && (
                            <p>{t('cobertura.competencias').replace('{lista}', a.competencias.join(', '))}</p>
                          )}
                          {a.podemCobrir.length === 0 ? (
                            <p>{t('cobertura.ninguem')}</p>
                          ) : (
                            <>
                              <p className="legenda-meta">{t('cobertura.candidatos.nota')}</p>
                              <ul aria-label={t('cobertura.candidatos')}>
                                {a.podemCobrir.map((c) => (
                                  <li key={c.pessoa}>
                                    <strong>{c.pessoa}</strong> · {t('cobertura.folga').replace('{h}', numero(c.folgaH, lang))} ·{' '}
                                    {c.temTodasAsCompetencias ? t('cobertura.temTodas') : t('cobertura.faltaCompetencia')}
                                  </li>
                                ))}
                              </ul>
                            </>
                          )}
                        </>
                      )}
                    </article>
                  )
                })}
              </div>
            )}
            <p className="legenda-meta">{t('cobertura.nota')}</p>
          </Cartao>
        )}
      </CardPage>
      {gaveta && (
        <GavetaDeTasks
          cartaoId="CB-01" segmento={gaveta.segmento} titulo={gaveta.titulo} filtros={sp}
          resumoDosFiltros={resumo} aoFechar={() => setGaveta(null)}
        />
      )}
    </>
  )
}
