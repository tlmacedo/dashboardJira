import { useState } from 'react'
import { useLang } from '../../i18n/LangProvider'
import { Cartao } from '../../shared/Cartao'
import { CardPage } from '../../shared/CardPage'
import { baixarCsv } from '../../shared/csv'
import { Filtros, resumoDosFiltros, useFiltrosDaUrl } from '../../shared/Filtros'
import { dataCurta, inteiro, numero, percentual } from '../../shared/format'
import { GavetaDeTasks } from '../../shared/GavetaDeTasks'
import { temBloqueio, useCard } from '../../shared/useCard'
import type { EpicosDto } from './api'
import '../../shared/tabela.css'

type Gaveta = { cartaoId: string; segmento: string; titulo: string }

/**
 * Épicos (EP-01): uma linha por épico com itens, progresso, vencidos e previsão. A previsão em semanas só existe com a vazão
 * do PRÓPRIO épico (amostra mínima no histórico); sem ela é traço com a legenda do porquê, nunca a vazão do time.
 */
export function Epicos() {
  const { t, lang } = useLang()
  const [sp, aplicar] = useFiltrosDaUrl()
  const consulta = useCard<EpicosDto>('epicos', sp)
  const dto = consulta.data
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)
  const bloqueado = temBloqueio(dto?.avisos)
  const traco = '—'
  const resumo = resumoDosFiltros(sp, t, (iso) => dataCurta(iso, lang))
  const abrir = (segmento: string, titulo: string) => setGaveta({ cartaoId: 'EP-01', segmento, titulo })

  function exportar() {
    if (!dto) return
    baixarCsv('epicos.csv', [
      [`# ${t('nav.epicos')}`],
      [`# ${t('gaveta.csv.filtros')}: ${resumo}`],
      [],
      [t('epicos.col.epico'), t('epicos.col.itens'), t('epicos.col.concluidos'), t('epicos.col.progresso'), t('epicos.col.maisAntigo'), t('epicos.col.vencidos'), t('epicos.col.pontos'), t('epicos.col.previsao')],
      ...dto.linhas.map((l) => [
        l.epico, String(l.itens), String(l.concluidos), l.progresso == null ? '' : percentual(l.progresso, lang), l.maisAntigo ?? '',
        String(l.vencidos), l.pontos == null ? '' : numero(l.pontos, lang), l.previsaoSemanasP85 == null ? '' : String(l.previsaoSemanasP85),
      ]),
    ])
  }

  /** Número que abre a gaveta do épico; zero e dado bloqueado não são clicáveis. */
  function contagem(l: EpicosDto['linhas'][number], tipo: 'itens' | 'concluidos' | 'vencidos', n: number) {
    if (bloqueado) return traco
    if (n === 0) return inteiro(n, lang)
    const rotulo = t(`epicos.col.${tipo}`)
    return (
      <button
        type="button" className="contagem-botao" aria-label={`${l.epico} ${rotulo}: ${n}`}
        onClick={() => abrir(`epico:${l.id}:${tipo}`, `${l.epico}: ${rotulo}`)}
      >
        {tipo === 'vencidos' && <span aria-hidden="true">▲ </span>}
        {inteiro(n, lang)}
      </button>
    )
  }

  return (
    <>
      <CardPage
        titulo={t('nav.epicos')}
        pergunta={t('epicos.pergunta')}
        filtros={<Filtros key={sp.toString()} campos={['squad', 'pessoa', 'os']} valor={sp} aoAplicar={aplicar} />}
        consulta={consulta}
        avisos={dto?.avisos}
        aoExportar={exportar}
      >
        {dto && (
          <Cartao id="EP-01" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-ep-tabela" titulo={t('epicos.tabela')}>
            {dto.linhas.length === 0 ? (
              <p className="vazio">{t('epicos.vazio')}</p>
            ) : (
              <>
                <p className="legenda-meta">{t('epicos.legenda').replace('{n}', String(dto.minItens))}</p>
                <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-ep-tabela">
                  <table className="tabela" aria-label={t('epicos.tabela')}>
                    <thead>
                      <tr>
                        <th scope="col">{t('epicos.col.epico')}</th>
                        <th scope="col" className="num">{t('epicos.col.itens')}</th>
                        <th scope="col" className="num">{t('epicos.col.concluidos')}</th>
                        <th scope="col" className="num">{t('epicos.col.progresso')}</th>
                        <th scope="col">{t('epicos.col.maisAntigo')}</th>
                        <th scope="col" className="num">{t('epicos.col.vencidos')}</th>
                        <th scope="col" className="num">{t('epicos.col.pontos')}</th>
                        <th scope="col" className="num">{t('epicos.col.previsao')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {dto.linhas.map((l) => (
                        <tr key={l.id}>
                          <th scope="row" className="mono">{l.epico}</th>
                          <td className="num">{contagem(l, 'itens', l.itens)}</td>
                          <td className="num">{contagem(l, 'concluidos', l.concluidos)}</td>
                          <td className="num">
                            {bloqueado || l.progresso == null ? traco : `${percentual(l.progresso, lang)} (${t('epicos.deDe').replace('{n}', String(l.concluidos)).replace('{total}', String(l.itens))})`}
                          </td>
                          <td>{bloqueado || !l.maisAntigo ? traco : dataCurta(l.maisAntigo, lang)}</td>
                          <td className="num">{contagem(l, 'vencidos', l.vencidos)}</td>
                          <td className="num">{bloqueado || l.pontos == null ? traco : numero(l.pontos, lang)}</td>
                          <td className="num">
                            {bloqueado || l.previsaoSemanasP85 == null
                              ? traco
                              : t(l.previsaoSemanasP85 === 1 ? 'previsao.semana' : 'previsao.semanas').replace('{n}', inteiro(l.previsaoSemanasP85, lang))}
                          </td>
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
          cartaoId={gaveta.cartaoId} segmento={gaveta.segmento} titulo={gaveta.titulo} filtros={sp}
          resumoDosFiltros={resumo} aoFechar={() => setGaveta(null)}
        />
      )}
    </>
  )
}
