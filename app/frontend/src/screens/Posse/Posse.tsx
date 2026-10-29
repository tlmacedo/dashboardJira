import { useState } from 'react'
import { useLang } from '../../i18n/LangProvider'
import { Cartao } from '../../shared/Cartao'
import { CardPage } from '../../shared/CardPage'
import { baixarCsv } from '../../shared/csv'
import { Filtros, resumoDosFiltros, useFiltrosDaUrl } from '../../shared/Filtros'
import { dataCurta, inteiro, numero, percentual } from '../../shared/format'
import { GavetaDeTasks } from '../../shared/GavetaDeTasks'
import { temBloqueio, useCard } from '../../shared/useCard'
import type { PosseDto } from './api'
import '../../shared/tabela.css'
import '../../shared/pessoas.css'

type Gaveta = { cartaoId: string; segmento: string; titulo: string }

/**
 * Posse da task (PS-01 e PS-02): quanto tempo cada pessoa ficou com cada task e quantas vezes o responsável mudou. Quem está
 * fora da visão de quem pergunta (e o "sem responsável") aparece como "outra pessoa", nunca pelo nome. Sem ranking: pessoas em
 * ordem alfabética e sem coluna de posição. O período é pela data de resolução (mais as tasks abertas).
 */
export function Posse() {
  const { t, lang } = useLang()
  const [sp, aplicar] = useFiltrosDaUrl()
  const consulta = useCard<PosseDto>('posse', sp)
  const dto = consulta.data
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)
  const bloqueado = temBloqueio(dto?.avisos)
  const traco = '—'
  const resumo = resumoDosFiltros(sp, t, (iso) => dataCurta(iso, lang))
  const unidade = t(dto?.unit === 'corrido' ? 'posse.unidade.corrido' : 'posse.unidade.util')
  const abrir = (cartaoId: string, segmento: string, titulo: string) => setGaveta({ cartaoId, segmento, titulo })
  const comAbrir = (n: number, cartaoId: string, segmento: string, titulo: string) =>
    !bloqueado && n > 0 ? () => abrir(cartaoId, segmento, titulo) : undefined

  function exportar() {
    if (!dto) return
    baixarCsv('posse.csv', [
      [`# ${t('nav.posse')} (${unidade})`],
      [`# ${t('gaveta.csv.filtros')}: ${resumo}`],
      [],
      [t('posse.col.task'), t('posse.col.passagens'), t('posse.col.posse')],
      ...dto.tasks.map((x) => [
        x.key, String(x.transferencias),
        x.posse.map((p) => `${p.pessoa ?? t('posse.outraPessoa')}: ${percentual(p.fracao, lang)}`).join('; '),
      ]),
    ])
  }

  function contagem(pessoa: string, n: number) {
    if (bloqueado) return traco
    if (n === 0) return inteiro(n, lang)
    return (
      <button
        type="button" className="contagem-botao" aria-label={`${pessoa} ${t('posse.col.tasks')}: ${n}`}
        onClick={() => abrir('PS-02', `pessoa:${pessoa}`, `${pessoa}: ${t('posse.col.tasks')}`)}
      >
        {inteiro(n, lang)}
      </button>
    )
  }

  return (
    <>
      <CardPage
        titulo={t('nav.posse')}
        pergunta={t('posse.pergunta')}
        filtros={
          <Filtros
            key={sp.toString()} campos={['periodo', 'squad', 'pessoa', 'tipo', 'prioridade', 'os', 'campos', 'unit']} valor={sp} aoAplicar={aplicar}
            pessoas={dto?.pessoas.map((p) => p.pessoa)}
          />
        }
        consulta={consulta}
        avisos={dto?.avisos}
        aoExportar={exportar}
      >
        {dto && (
          <>
            <div role="group" aria-label={t('posse.indicadores')} className="cardpage-kpis">
              <Cartao
                id="PS-01" ajudaComTitulo titulo={t('posse.kpi.tasks')} valor={bloqueado ? traco : inteiro(dto.tasks.length, lang)}
                aoAbrir={comAbrir(dto.tasks.length, 'PS-01', 'tasks', t('posse.kpi.tasks'))}
              />
              <Cartao
                id="PS-01" ajudaComTitulo titulo={t('posse.kpi.transferidas')}
                valor={bloqueado ? traco : inteiro(dto.tasks.filter((x) => x.transferencias > 0).length, lang)}
                aoAbrir={comAbrir(dto.tasks.filter((x) => x.transferencias > 0).length, 'PS-01', 'transferidas', t('posse.kpi.transferidas'))}
              />
            </div>

            {dto.pessoas.length > 0 && (
              <Cartao id="PS-02" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-ps-pessoas" titulo={t('posse.tabela.pessoas')}>
                <>
                  <p className="legenda-meta">{t('pessoas.alfabetica')}</p>
                  <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-ps-pessoas">
                    <table className="tabela grade-pessoas" aria-labelledby="h-ps-pessoas">
                      <thead>
                        <tr>
                          <th scope="col">{t('posse.col.pessoa')}</th>
                          <th scope="col" className="num">{t('posse.col.tasks')}</th>
                          <th scope="col" className="num">{t('posse.col.dias').replace('{unidade}', unidade)}</th>
                          <th scope="col" className="num">{t('posse.col.recebidas')}</th>
                          <th scope="col" className="num">{t('posse.col.repassadas')}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {dto.pessoas.map((p) => (
                          <tr key={p.pessoa}>
                            <th scope="row">{p.pessoa}</th>
                            <td className="num">{contagem(p.pessoa, p.tasks)}</td>
                            <td className="num">{bloqueado ? traco : numero(p.dias, lang)}</td>
                            <td className="num">{bloqueado ? traco : inteiro(p.recebidas, lang)}</td>
                            <td className="num">{bloqueado ? traco : inteiro(p.repassadas, lang)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </>
              </Cartao>
            )}

            <Cartao id="PS-01" variante="lista" nivelDoTitulo={2} idDoTitulo="h-ps-tasks" titulo={t('posse.tabela.tasks')}>
              {dto.tasks.length === 0 ? (
                <p className="vazio">{t('posse.vazia')}</p>
              ) : (
                <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-ps-tasks">
                  <table className="tabela" aria-label={t('posse.tabela.tasks')}>
                    <thead>
                      <tr>
                        <th scope="col">{t('posse.col.task')}</th>
                        <th scope="col" className="num">{t('posse.col.passagens')}</th>
                        <th scope="col">{t('posse.col.posse')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {dto.tasks.map((x) => (
                        <tr key={x.key}>
                          <td>
                            <span className="mono">{x.key}</span>
                            <span className="resumo-task"> {x.resumo}</span>
                          </td>
                          <td className="num">{bloqueado ? traco : inteiro(x.transferencias, lang)}</td>
                          <td>
                            {bloqueado
                              ? traco
                              : x.posse
                                  .map((p) =>
                                    t('posse.parte')
                                      .replace('{pessoa}', p.pessoa ?? t('posse.outraPessoa'))
                                      .replace('{pct}', percentual(p.fracao, lang))
                                      .replace('{dias}', numero(p.dias, lang))
                                      .replace('{unidade}', unidade),
                                  )
                                  .join(' · ')}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              <p className="legenda-meta">{t('posse.nota')}</p>
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
