import { useState } from 'react'
import { useLang } from '../../i18n/LangProvider'
import { Cartao } from '../../shared/Cartao'
import { CardPage } from '../../shared/CardPage'
import { ContagensEmBarras } from '../../shared/ContagensEmBarras'
import { baixarCsv } from '../../shared/csv'
import { Filtros, resumoDosFiltros, useFiltrosDaUrl } from '../../shared/Filtros'
import { dataCurta, inteiro, numero } from '../../shared/format'
import { GavetaDeTasks } from '../../shared/GavetaDeTasks'
import { TabelaOrdenavel, type Coluna } from '../../shared/TabelaOrdenavel'
import { temBloqueio, useCard } from '../../shared/useCard'
import type { AbertasDto, LinhaAberta } from './api'
import '../../shared/tabela.css'

/** Recorte aberto na gaveta de tasks: o cartão que o conta e o segmento (ver `CardAbertas.kt` no backend). */
type Gaveta = { cartaoId: string; segmento: string; titulo: string }

const SEM_PRIORIDADE = '(sem)'

/** Abertas (AB-01 a AB-09): o que está em curso agora, por status, prioridade, idade e vencimento. */
export function Abertas() {
  const { t, lang } = useLang()
  const [sp, aplicar] = useFiltrosDaUrl()
  const consulta = useCard<AbertasDto>('abertas', sp)
  const dto = consulta.data
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)
  const bloqueado = temBloqueio(dto?.avisos)
  const traco = '—'
  const num = (n: number) => (bloqueado ? traco : inteiro(n, lang))
  const resumo = resumoDosFiltros(sp, t, (iso) => dataCurta(iso, lang))
  const unidade = dto?.unit === 'corrido' ? t('abertas.diasCorridos') : t('abertas.diasUteis')
  const abrir = (cartaoId: string, segmento: string, titulo: string) => setGaveta({ cartaoId, segmento, titulo })
  const comAbrir = (n: number, cartaoId: string, segmento: string, titulo: string) =>
    !bloqueado && n > 0 ? () => abrir(cartaoId, segmento, titulo) : undefined

  function exportar() {
    if (!dto) return
    baixarCsv('abertas.csv', [
      [`# ${t('nav.abertas')} (${unidade})`],
      [`# ${t('gaveta.csv.filtros')}: ${resumo}`],
      [],
      [t('abertas.col.task'), t('abertas.col.resumo'), t('abertas.col.tipo'), t('abertas.col.status'), t('abertas.col.prioridade'), t('abertas.col.responsavel'), t('abertas.col.idade'), t('abertas.col.vence'), t('abertas.col.horas')],
      ...dto.linhas.map((l) => [
        l.key, l.resumo, l.tipo, l.status, l.prioridade ?? '', l.responsavel ?? '', numero(l.idade, lang), l.vence ?? '', l.horasRestantes == null ? '' : numero(l.horasRestantes, lang),
      ]),
    ])
  }

  const colunas: Coluna<LinhaAberta>[] = [
    { id: 'key', cabecalho: t('abertas.col.task'), valor: (l) => l.key },
    { id: 'resumo', cabecalho: t('abertas.col.resumo'), valor: (l) => l.resumo },
    { id: 'tipo', cabecalho: t('abertas.col.tipo'), valor: (l) => l.tipo },
    { id: 'status', cabecalho: t('abertas.col.status'), valor: (l) => l.status },
    { id: 'prioridade', cabecalho: t('abertas.col.prioridade'), valor: (l) => l.prioridade },
    { id: 'responsavel', cabecalho: t('abertas.col.responsavel'), valor: (l) => l.responsavel },
    { id: 'idade', cabecalho: `${t('abertas.col.idade')} (${unidade})`, valor: (l) => l.idade, render: (l) => numero(l.idade, lang), numerica: true },
    { id: 'vence', cabecalho: t('abertas.col.vence'), valor: (l) => l.vence, render: (l) => (l.vence ? dataCurta(l.vence, lang) : traco) },
    {
      id: 'horas', cabecalho: t('abertas.col.horas'), valor: (l) => l.horasRestantes,
      render: (l) => (l.horasRestantes == null ? traco : numero(l.horasRestantes, lang)), numerica: true,
    },
  ]

  return (
    <>
      <CardPage
        titulo={t('nav.abertas')}
        pergunta={t('abertas.pergunta')}
        filtros={
          <Filtros key={sp.toString()} campos={['squad', 'pessoa', 'tipo', 'prioridade', 'os', 'unit']} valor={sp} aoAplicar={aplicar} />
        }
        consulta={consulta}
        avisos={dto?.avisos}
        aoExportar={exportar}
      >
        {dto && (
          <>
            <div role="group" aria-label={t('abertas.indicadores')} className="cardpage-kpis">
              <Cartao
                id="AB-01"
                titulo={t('abertas.kpi.abertas')}
                valor={num(dto.abertas)}
                aoAbrir={comAbrir(dto.abertas, 'AB-01', 'abertas', t('abertas.kpi.abertas'))}
              />
              <Cartao
                id="AB-01"
                titulo={t('abertas.kpi.emAndamento')}
                valor={num(dto.emAndamento)}
                aoAbrir={comAbrir(dto.emAndamento, 'AB-01', 'emAndamento', t('abertas.kpi.emAndamento'))}
              />
              <Cartao
                id="AB-04"
                titulo={t('abertas.kpi.acimaDoP85')}
                valor={num(dto.acimaDoP85)}
                simboloDoValor={dto.acimaDoP85 > 0 && !bloqueado ? '▲' : undefined}
                legenda={t('abertas.kpi.acimaDoP85.dica')}
                aoAbrir={comAbrir(dto.acimaDoP85, 'AB-04', 'acimaDoP85', t('abertas.kpi.acimaDoP85'))}
              />
            </div>

            <Cartao id="AB-02" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-ab-historico" titulo={t('abertas.historico')}>
              {dto.historicoPorTipo.length === 0 ? (
                <p className="vazio">{t('abertas.historico.vazio')}</p>
              ) : (
                <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-ab-historico">
                  <table className="tabela" aria-labelledby="h-ab-historico">
                    <thead>
                      <tr>
                        <th scope="col">{t('abertas.col.tipo')}</th>
                        <th scope="col" className="num">P50 ({unidade})</th>
                        <th scope="col" className="num">P85 ({unidade})</th>
                        <th scope="col" className="num">n</th>
                      </tr>
                    </thead>
                    <tbody>
                      {dto.historicoPorTipo.map((f) => (
                        <tr key={f.tipo}>
                          <th scope="row">{f.tipo}</th>
                          <td className="num">{bloqueado ? traco : numero(f.p50, lang)}</td>
                          <td className="num">{bloqueado ? traco : numero(f.p85, lang)}</td>
                          <td className="num">
                            {!bloqueado && f.n > 0 ? (
                              <button
                                type="button"
                                className="contagem-botao"
                                aria-label={`${f.tipo}: ${f.n}`}
                                onClick={() => abrir('AB-02', `historico:${f.tipo}`, `${t('abertas.historico')}: ${f.tipo}`)}
                              >
                                {inteiro(f.n, lang)}
                              </button>
                            ) : (
                              inteiro(f.n, lang)
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </Cartao>

            <div className="cardpage-duas-colunas">
              <Cartao id="AB-05" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-ab-status" titulo={t('abertas.porStatus')}>
                <ContagensEmBarras
                  barras
                  rotulo={t('abertas.porStatus')}
                  cabecalho={t('abertas.col.status')}
                  indisponivel={bloqueado}
                  itens={dto.porStatus.map((c) => ({ chave: c.chave, rotulo: c.chave, n: c.n }))}
                  aoAbrir={(c) => abrir('AB-05', `status:${c}`, `${t('abertas.porStatus')}: ${c}`)}
                />
              </Cartao>
              <Cartao id="AB-07" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-ab-idade" titulo={t('abertas.porIdade')}>
                <ContagensEmBarras
                  barras
                  rotulo={t('abertas.porIdade')}
                  cabecalho={`${t('abertas.col.idade')} (${unidade})`}
                  indisponivel={bloqueado}
                  itens={dto.porIdade.map((c) => ({ chave: c.chave, rotulo: t(`abertas.idade.${c.chave}`), n: c.n }))}
                  aoAbrir={(c) => abrir('AB-07', `idade:${c}`, `${t('abertas.porIdade')}: ${t(`abertas.idade.${c}`)}`)}
                />
              </Cartao>
              <Cartao id="AB-06" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-ab-prioridade" titulo={t('abertas.porPrioridade')}>
                {dto.porPrioridade.length === 0 ? (
                  <p className="vazio">{t('abertas.prioridade.indisponivel')}</p>
                ) : (
                  <ContagensEmBarras
                    rotulo={t('abertas.porPrioridade')}
                    cabecalho={t('abertas.col.prioridade')}
                    indisponivel={bloqueado}
                    itens={dto.porPrioridade.map((c) => ({ chave: c.chave, rotulo: c.chave === SEM_PRIORIDADE ? t('abertas.semPrioridade') : c.chave, n: c.n }))}
                    aoAbrir={(c) => abrir('AB-06', `prioridade:${c}`, `${t('abertas.porPrioridade')}: ${c === SEM_PRIORIDADE ? t('abertas.semPrioridade') : c}`)}
                  />
                )}
              </Cartao>
              <Cartao id="AB-08" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-ab-vence" titulo={t('abertas.porVencimento')}>
                <ContagensEmBarras
                  rotulo={t('abertas.porVencimento')}
                  cabecalho={t('abertas.col.vence')}
                  indisponivel={bloqueado}
                  itens={dto.porVencimento.map((c) => ({ chave: c.chave, rotulo: t(`abertas.vence.${c.chave}`), n: c.n }))}
                  aoAbrir={(c) => abrir('AB-08', `vence:${c}`, `${t('abertas.porVencimento')}: ${t(`abertas.vence.${c}`)}`)}
                />
              </Cartao>
            </div>

            <Cartao
              id="AB-09"
              variante="lista"
              nivelDoTitulo={2}
              idDoTitulo="h-ab-tabela"
              titulo={t('abertas.tabela')}
              aoAbrir={comAbrir(dto.abertas, 'AB-09', 'abertas', t('abertas.tabela'))}
            >
              {dto.linhas.length === 0 ? (
                <p className="vazio">{t('abertas.tabela.vazia')}</p>
              ) : (
                <TabelaOrdenavel
                  rotulo={t('abertas.tabela')}
                  colunas={colunas}
                  linhas={bloqueado ? [] : dto.linhas}
                  chave={(l) => l.key}
                  primeiraEhCabecalhoDeLinha
                  inicial={{ id: 'idade', descendente: true }}
                />
              )}
            </Cartao>
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
