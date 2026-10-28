import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useLang } from '../../i18n/LangProvider'
import { Cartao } from '../../shared/Cartao'
import { CardPage } from '../../shared/CardPage'
import { baixarCsv } from '../../shared/csv'
import { dataCurta, inteiro, numero, percentual } from '../../shared/format'
import { GavetaDeTasks } from '../../shared/GavetaDeTasks'
import { Ocupacao } from '../../shared/Ocupacao'
import { temBloqueio } from '../../shared/useCard'
import '../../shared/pessoas.css'
import '../../shared/tabela.css'
import '../Central/inicio.css'
import { fetchMeuPainel, type ItemDaFila } from './api'

type Gaveta = { cartaoId: string; segmento: string; titulo: string }

/** Símbolo e texto do risco de uma task da fila (a cor nunca é o único sinal). */
const SIMBOLO_DO_RISCO: Record<string, string> = { baixo: '✓', medio: '●', alto: '▲' }

/** `▲ atrasa` quando o término previsto passa do vencimento; `✓ no prazo` quando cabe; `■ sem vencimento` quando não há prazo. */
function situacaoDoPrazo(i: ItemDaFila, t: (k: string) => string): string {
  if (i.vence === null) return `■ ${t('meu.fila.semVencimento')}`
  return i.atrasa ? `▲ ${t('meu.fila.atrasa')}` : `✓ ${t('meu.fila.noPrazo')}`
}

/**
 * Meu painel: o painel do próprio usuário (o Squad Leader pode abrir o de uma pessoa do squad com `?pessoa=`). Minhas abertas,
 * o que vence em 7 dias, o que está em risco, a ocupação das próximas 4 semanas, a fila na ordem da agenda, as ausências
 * futuras (sem motivo) e o esforço do mês com os três componentes lado a lado. É informação para planejar, nunca uma nota.
 */
export function MeuPainel() {
  const { t, lang } = useLang()
  const [sp] = useSearchParams()
  const pessoa = sp.get('pessoa')
  const consulta = useQuery({ queryKey: ['meu-painel', pessoa], queryFn: () => fetchMeuPainel(pessoa), placeholderData: keepPreviousData, retry: false })
  const dto = consulta.data
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)
  const bloqueado = temBloqueio(dto?.avisos)
  const filtros = new URLSearchParams(pessoa ? { pessoa } : {})
  const abrir = (n: number | null, cartaoId: string, segmento: string, titulo: string) =>
    !bloqueado && (n ?? 0) > 0 ? () => setGaveta({ cartaoId, segmento, titulo }) : undefined
  const traco = '—'
  const contagem = (n: number | null) => (bloqueado || n === null ? traco : inteiro(n, lang))

  function exportar() {
    if (!dto) return
    baixarCsv('meu-painel.csv', [
      [`# ${t('nav.meu')}: ${dto.pessoa}`],
      [],
      [t('meu.fila.task'), t('meu.fila.vence'), t('meu.fila.termino'), t('meu.fila.prazo'), t('meu.fila.risco')],
      ...dto.fila.map((i) => [i.key, i.vence ?? '', i.termino ?? '', situacaoDoPrazo(i, t), t(`meu.risco.${i.nivelRisco}`)]),
    ])
  }

  return (
    <>
      <CardPage titulo={t('nav.meu')} pergunta={t('meu.pergunta')} filtros={null} consulta={consulta} avisos={dto?.avisos} aoExportar={exportar}>
        {dto && (
          <>
            {pessoa && <p className="inicio-resumo">{t('meu.deQuem').replace('{pessoa}', dto.pessoa)}</p>}
            <div role="group" aria-label={t('meu.indicadores')} className="cardpage-kpis">
              <Cartao
                id="MP-01" ajudaComTitulo titulo={t('meu.kpi.abertas')} valor={contagem(dto.minhasAbertas)}
                aoAbrir={abrir(dto.minhasAbertas, 'MP-01', 'abertas', t('meu.kpi.abertas'))}
              />
              <Cartao
                id="MP-01" ajudaComTitulo titulo={t('meu.kpi.vencem')} valor={contagem(dto.vencemEm7d)}
                aoAbrir={abrir(dto.vencemEm7d, 'MP-01', 'vencemEm7d', t('meu.kpi.vencem'))}
              />
              <Cartao
                id="MP-03" titulo={t('meu.kpi.risco')} valor={contagem(dto.emRisco)}
                simboloDoValor={!bloqueado && (dto.emRisco ?? 0) > 0 ? '▲' : undefined}
                aoAbrir={abrir(dto.emRisco, 'MP-03', 'emRisco', t('meu.kpi.risco'))}
              />
              <Cartao id="MP-02" variante="tabela" nivelDoTitulo={2} titulo={t('meu.kpi.ocupacao')}>
                {dto.ocupacao4Sem === null ? <p className="vazio">{t('meu.ocupacao.semCadastro')}</p> : <Ocupacao o={dto.ocupacao4Sem} />}
              </Cartao>
            </div>

            <Cartao id="MP-05" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-meu-fila" titulo={t('meu.fila')}>
              <p className="legenda-meta">{t('meu.fila.nota')}</p>
              {dto.fila.length === 0 ? (
                <p className="vazio">{t('meu.fila.vazia')}</p>
              ) : (
                <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-meu-fila">
                  <table className="tabela" aria-labelledby="h-meu-fila">
                    <thead>
                      <tr>
                        <th scope="col">{t('meu.fila.task')}</th>
                        <th scope="col">{t('meu.fila.vence')}</th>
                        <th scope="col">{t('meu.fila.termino')}</th>
                        <th scope="col">{t('meu.fila.prazo')}</th>
                        <th scope="col">{t('meu.fila.risco')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {dto.fila.map((i) => (
                        <tr key={i.key}>
                          <th scope="row">{i.key}<span className="legenda-meta"> {i.resumo}</span></th>
                          <td>{i.vence ? dataCurta(i.vence, lang) : '—'}</td>
                          <td>{i.termino ? dataCurta(i.termino, lang) : t('meu.fila.semPrevisao')}</td>
                          <td>{situacaoDoPrazo(i, t)}</td>
                          <td>{SIMBOLO_DO_RISCO[i.nivelRisco] ?? '■'} {t(`meu.risco.${i.nivelRisco}`)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </Cartao>

            <div className="cardpage-duas-colunas">
              <Cartao id="MP-04" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-meu-aus" titulo={t('meu.ausencias')}>
                {dto.ausenciasFuturas.length === 0 ? (
                  <p className="vazio">{t('meu.ausencias.vazia')}</p>
                ) : (
                  <ul className="inicio-lista">
                    {dto.ausenciasFuturas.map((a) => (
                      <li key={a.de} className="inicio-item">
                        <span className="inicio-item__nome">{dataCurta(a.de, lang)} – {dataCurta(a.ate, lang)}</span>
                        <span className="inicio-item__valor">{percentual(a.fracao, lang)}</span>
                      </li>
                    ))}
                  </ul>
                )}
              </Cartao>

              <Cartao id="EF-02" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-meu-esforco" titulo={t('meu.esforco')}>
                {dto.esforcoDoMes === null ? (
                  <p className="vazio">{t('meu.esforco.vazio')}</p>
                ) : (
                  <>
                    <dl className="inicio-ficha">
                      <dt>{t('meu.esforco.pesoTipo')}</dt>
                      <dd>{numero(dto.esforcoDoMes.pesoTipo, lang)}</dd>
                      <dt>{t('meu.esforco.testes')}</dt>
                      <dd>{numero(dto.esforcoDoMes.testes, lang)}</dd>
                      <dt>{t('meu.esforco.horas')}</dt>
                      <dd>{numero(dto.esforcoDoMes.horas, lang)}</dd>
                      <dt>{t('meu.esforco.indice')}</dt>
                      <dd>{numero(dto.esforcoDoMes.indice, lang)}</dd>
                    </dl>
                    <p className="legenda-meta">{t('meu.esforco.nota').replace('{versao}', dto.esforcoDoMes.versaoRegras)}</p>
                  </>
                )}
              </Cartao>
            </div>
          </>
        )}
      </CardPage>
      {gaveta && (
        <GavetaDeTasks cartaoId={gaveta.cartaoId} segmento={gaveta.segmento} titulo={gaveta.titulo} filtros={filtros} aoFechar={() => setGaveta(null)} />
      )}
    </>
  )
}
