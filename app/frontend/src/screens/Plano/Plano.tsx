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
import type { PlanoDto } from './api'
import '../../shared/tabela.css'
import '../../shared/pessoas.css'

type Gaveta = { segmento: string; titulo: string }

const OPCOES = [4, 5, 6, 7, 8]

/**
 * Plano de 4 a 8 semanas (PL-01), só liderança: horas planejadas contra a capacidade de cada pessoa em cada semana. O número
 * de semanas vai na URL (`semanas=8`). Cada célula mostra a ocupação com número e símbolo (capacidade zero é o texto "sem
 * capacidade" com as horas planejadas ao lado) e abre as tasks que ocupam a semana. Pessoas em ordem alfabética.
 */
export function Plano() {
  const { t, lang } = useLang()
  const [sp, aplicar] = useFiltrosDaUrl()
  const consulta = useCard<PlanoDto>('plano', sp)
  const dto = consulta.data
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)
  const bloqueado = temBloqueio(dto?.avisos)
  const traco = '—'
  const resumo = resumoDosFiltros(sp, t, (iso) => dataCurta(iso, lang))
  const semanas = Number(sp.get('semanas')) >= 4 && Number(sp.get('semanas')) <= 8 ? Number(sp.get('semanas')) : 4

  function escolher(n: number) {
    const novo = new URLSearchParams(sp)
    if (n === 4) novo.delete('semanas')
    else novo.set('semanas', String(n))
    aplicar(novo)
  }

  function exportar() {
    if (!dto) return
    baixarCsv('plano.csv', [
      [`# ${t('nav.plano')}`],
      [`# ${t('gaveta.csv.filtros')}: ${resumo}`],
      [],
      [t('carga.col.pessoa'), ...dto.semanas.flatMap((s) => [`${s} ${t('plano.planejada')}`, `${s} ${t('plano.capacidade')}`])],
      ...dto.linhas.map((l) => [l.pessoa, ...l.celulas.flatMap((c) => [String(c.planejadaH), String(c.capacidadeH)])]),
    ])
  }

  return (
    <>
      <CardPage
        titulo={t('nav.plano')}
        pergunta={t('plano.pergunta')}
        filtros={
          <Filtros
            key={sp.toString()} campos={['squad', 'pessoa']} valor={sp} aoAplicar={aplicar}
            extras={
              <div role="group" aria-label={t('plano.semanas')} className="seg seg--claro">
                {OPCOES.map((n) => (
                  <button key={n} type="button" aria-pressed={semanas === n} onClick={() => escolher(n)}>
                    {t('plano.nSemanas').replace('{n}', String(n))}
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
          <Cartao id="PL-01" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-pl-plano" titulo={t('plano.titulo')}>
            {dto.linhas.length === 0 ? (
              <p className="vazio">{t('carga.vazia')}</p>
            ) : (
              <>
                <p className="legenda-meta">{t('plano.legenda')}</p>
                <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-pl-plano">
                  <table className="tabela grade-pessoas" aria-labelledby="h-pl-plano">
                    <thead>
                      <tr>
                        <th scope="col">{t('carga.col.pessoa')}</th>
                        {dto.semanas.map((s) => (
                          <th scope="col" key={s}>{dataCurta(s, lang)}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {dto.linhas.map((l) => (
                        <tr key={l.pessoa}>
                          <th scope="row">{l.pessoa}</th>
                          {l.celulas.map((c) => (
                            <td key={c.semana}>
                              {bloqueado ? traco : (
                                <>
                                  <Ocupacao o={c.ocupacao} demandaH={c.planejadaH} />
                                  <span className="celula-num">
                                    {numero(c.planejadaH, lang)} / {numero(c.capacidadeH, lang)} h
                                    {c.semDataH > 0 && ` · ${t('plano.semData').replace('{h}', numero(c.semDataH, lang))}`}
                                  </span>
                                  {c.itens > 0 && (
                                    <button
                                      type="button" className="contagem-botao"
                                      aria-label={t('plano.abreCelula').replace('{pessoa}', l.pessoa).replace('{semana}', dataCurta(c.semana, lang)).replace('{n}', String(c.itens))}
                                      onClick={() => setGaveta({ segmento: `celula:${l.pessoa}:${c.semana}`, titulo: `${l.pessoa}: ${dataCurta(c.semana, lang)}` })}
                                    >
                                      {t('plano.itens').replace('{n}', inteiro(c.itens, lang))}
                                    </button>
                                  )}
                                </>
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
        )}
      </CardPage>
      {gaveta && (
        <GavetaDeTasks
          cartaoId="PL-01" segmento={gaveta.segmento} titulo={gaveta.titulo} filtros={sp}
          resumoDosFiltros={resumo} aoFechar={() => setGaveta(null)}
        />
      )}
    </>
  )
}
