import { useState } from 'react'
import { useLang } from '../../i18n/LangProvider'
import { CardPage } from '../../shared/CardPage'
import { Cartao } from '../../shared/Cartao'
import { baixarCsv } from '../../shared/csv'
import { Filtros, resumoDosFiltros, useFiltrosDaUrl } from '../../shared/Filtros'
import { dataCurta, inteiro, percentual } from '../../shared/format'
import { GavetaDeTasks } from '../../shared/GavetaDeTasks'
import { temBloqueio, useCard } from '../../shared/useCard'
import type { CalendarioDto } from './api'
import '../../shared/tabela.css'
import '../../shared/pessoas.css'

type Gaveta = { segmento: string; titulo: string }

/** Soma [n] dias a uma data `aaaa-mm-dd` sem depender do fuso do navegador. */
function somarDias(iso: string, n: number): string {
  const d = new Date(`${iso}T00:00:00Z`)
  d.setUTCDate(d.getUTCDate() + n)
  return d.toISOString().slice(0, 10)
}

/**
 * Calendário do time (CL-01): semana ou mês a partir de uma data. Legenda em texto: ▓ ausência (▒ meio dia), ◆n vencimentos,
 * ▲ dia crítico (dia útil com vencimentos e capacidade do time baixa). Um dia com vencimentos abre a gaveta com as tasks que
 * vencem nele. O usuário comum vê só a própria linha. A ausência não tem motivo.
 */
export function Calendario() {
  const { t, lang } = useLang()
  const [sp, aplicar] = useFiltrosDaUrl()
  const consulta = useCard<CalendarioDto>('calendario', sp)
  const dto = consulta.data
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)
  const bloqueado = temBloqueio(dto?.avisos)
  const traco = '—'
  const resumo = resumoDosFiltros(sp, t, (iso) => dataCurta(iso, lang))
  const modo = sp.get('modo') === 'mes' ? 'mes' : 'semana'
  const passo = modo === 'mes' ? 28 : 7

  function mudar(mudancas: Record<string, string | null>) {
    const novo = new URLSearchParams(sp)
    for (const [k, v] of Object.entries(mudancas)) {
      if (v === null) novo.delete(k)
      else novo.set(k, v)
    }
    aplicar(novo)
  }

  function exportar() {
    if (!dto) return
    baixarCsv('calendario.csv', [
      [`# ${t('nav.calendario')} (${dto.inicio})`],
      [`# ${t('gaveta.csv.filtros')}: ${resumo}`],
      [],
      [t('carga.col.pessoa'), ...dto.dias.map((d) => d.dia)],
      ...dto.linhas.map((l) => [l.pessoa, ...l.celulas.map((c) => `${c.ausencia > 0 ? t('calendario.ausente') : ''}${c.vencimentos > 0 ? ` ◆${c.vencimentos}` : ''}`.trim())]),
    ])
  }

  const marcaDeAusencia = (f: number) => (f >= 1 ? '▓' : f > 0 ? '▒' : '')

  return (
    <>
      <CardPage
        titulo={t('nav.calendario')}
        pergunta={t('calendario.pergunta')}
        filtros={
          <Filtros
            key={sp.toString()} campos={['squad', 'pessoa']} valor={sp} aoAplicar={aplicar}
            extras={
              <div className="seg-pessoas">
                <div role="group" aria-label={t('calendario.modo')} className="seg seg--claro">
                  {(['semana', 'mes'] as const).map((m) => (
                    <button key={m} type="button" aria-pressed={modo === m} onClick={() => mudar({ modo: m === 'semana' ? null : 'mes' })}>
                      {t(`calendario.modo.${m}`)}
                    </button>
                  ))}
                </div>
                {dto && (
                  <>
                    <button type="button" className="btn" onClick={() => mudar({ inicio: somarDias(dto.inicio, -passo) })}>
                      ◀ {t('calendario.anterior')}
                    </button>
                    <button type="button" className="btn" onClick={() => mudar({ inicio: somarDias(dto.inicio, passo) })}>
                      {t('calendario.proximo')} ▶
                    </button>
                  </>
                )}
              </div>
            }
          />
        }
        consulta={consulta}
        avisos={dto?.avisos}
        aoExportar={exportar}
      >
        {dto && (
          <Cartao id="CL-01" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-cl-grade" titulo={t('calendario.grade').replace('{inicio}', dataCurta(dto.inicio, lang))}>
            <p className="legenda-simbolos" role="note">
              <span>▓ {t('calendario.legenda.ausencia')}</span>
              <span>▒ {t('calendario.legenda.meioDia')}</span>
              <span>◆n {t('calendario.legenda.vencimentos')}</span>
              <span>▲ {t('calendario.legenda.critico')}</span>
            </p>
            <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-cl-grade">
              <table className="tabela grade-pessoas calendario-grade" aria-labelledby="h-cl-grade">
                <thead>
                  <tr>
                    <th scope="col">{t('carga.col.pessoa')}</th>
                    {dto.dias.map((d) => (
                      <th
                        scope="col" key={d.dia}
                        className={`${d.util ? '' : 'dia-inutil'} ${d.critico ? 'dia-critico' : ''}`.trim()}
                      >
                        {dataCurta(d.dia, lang).slice(0, 5)}
                        {d.feriado !== null && <span className="celula-num">{d.feriado || t('calendario.feriado')}</span>}
                        {d.critico && <span className="celula-num">▲ {t('calendario.critico')}</span>}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <th scope="row">{t('calendario.capacidade')}</th>
                    {dto.dias.map((d) => (
                      <td key={d.dia} className="num">
                        {bloqueado || d.capacidadeSquad === null ? traco : percentual(d.capacidadeSquad, lang)}
                      </td>
                    ))}
                  </tr>
                  <tr>
                    <th scope="row">{t('calendario.vencimentos')}</th>
                    {dto.dias.map((d) => (
                      <td key={d.dia} className="num">
                        {!bloqueado && d.vencimentos > 0 ? (
                          <button
                            type="button" className="contagem-botao" aria-label={t('calendario.vencemEm').replace('{dia}', dataCurta(d.dia, lang)).replace('{n}', String(d.vencimentos))}
                            onClick={() => setGaveta({ segmento: `dia:${d.dia}`, titulo: t('calendario.vencimentosDe').replace('{dia}', dataCurta(d.dia, lang)) })}
                          >
                            ◆{inteiro(d.vencimentos, lang)}
                          </button>
                        ) : (
                          traco
                        )}
                      </td>
                    ))}
                  </tr>
                  {dto.linhas.map((l) => (
                    <tr key={l.pessoa}>
                      <th scope="row">{l.pessoa}</th>
                      {l.celulas.map((c) => (
                        <td key={c.dia} className={c.ausencia > 0 ? 'celula-ausente' : undefined}>
                          {c.ausencia > 0 && (
                            <span className="marca" title={t('calendario.ausente')}>
                              <span aria-hidden="true">{marcaDeAusencia(c.ausencia)}</span>
                              <span className="so-leitor"> {t(c.ausencia >= 1 ? 'calendario.ausente' : 'calendario.ausenteMeioDia')}</span>
                            </span>
                          )}
                          {c.vencimentos > 0 && (
                            <span className="marca">
                              {' '}
                              <span aria-hidden="true">◆</span>
                              {c.vencimentos}
                              <span className="so-leitor"> {t('calendario.legenda.vencimentos')}</span>
                            </span>
                          )}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="legenda-meta">{t('calendario.nota')}</p>
          </Cartao>
        )}
      </CardPage>
      {gaveta && (
        <GavetaDeTasks
          cartaoId="CL-01" segmento={gaveta.segmento} titulo={gaveta.titulo} filtros={sp}
          resumoDosFiltros={resumo} aoFechar={() => setGaveta(null)}
        />
      )}
    </>
  )
}
