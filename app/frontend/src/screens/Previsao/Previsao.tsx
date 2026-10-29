import { useState } from 'react'
import { useLang } from '../../i18n/LangProvider'
import { Cartao, type EstadoDoCartao } from '../../shared/Cartao'
import { CardPage } from '../../shared/CardPage'
import { ContagensEmBarras } from '../../shared/ContagensEmBarras'
import { baixarCsv } from '../../shared/csv'
import { Filtros, resumoDosFiltros, useFiltrosDaUrl } from '../../shared/Filtros'
import { dataCurta, inteiro, numero } from '../../shared/format'
import { GavetaDeTasks } from '../../shared/GavetaDeTasks'
import { TabelaOrdenavel, type Coluna } from '../../shared/TabelaOrdenavel'
import { temBloqueio, useCard } from '../../shared/useCard'
import type { PrevisaoDto, PrevisaoIssue } from './api'
import '../../shared/tabela.css'
import '../../shared/pessoas.css'

type Gaveta = { cartaoId: string; segmento: string; titulo: string }

const ESTADO_DA_CONFIANCA: Record<string, EstadoDoCartao> = { baixa: 'atencao', media: 'neutro', alta: 'ok' }

/**
 * Previsão (PV-01 a PV-06): Monte Carlo sobre a vazão e a chegada semanais do que o papel vê. Quando o backlog não
 * converge, a resposta é "mais de N semanas" (nunca um número inventado). A confiança vem com símbolo e texto. O PV-05 é uma
 * projeção sem tasks por trás: não tem gaveta.
 */
export function Previsao() {
  const { t, lang } = useLang()
  const [sp, aplicar] = useFiltrosDaUrl()
  const consulta = useCard<PrevisaoDto>('previsao', sp)
  const dto = consulta.data
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)
  const bloqueado = temBloqueio(dto?.avisos)
  const traco = '—'
  const resumo = resumoDosFiltros(sp, t, (iso) => dataCurta(iso, lang))
  const unidade = t(dto?.unit === 'corrido' ? 'abertas.diasCorridos' : 'abertas.diasUteis')
  const abrir = (cartaoId: string, segmento: string, titulo: string) => setGaveta({ cartaoId, segmento, titulo })
  const comAbrir = (n: number, cartaoId: string, segmento: string, titulo: string) =>
    !bloqueado && n > 0 ? () => abrir(cartaoId, segmento, titulo) : undefined

  /** "4 semanas" com a data da semana alvo, "Mais de N semanas" ou "Sem backlog". */
  function termina(semanas: number | null, data: string | null): { valor: string; legenda?: string } {
    if (bloqueado) return { valor: traco }
    if (!dto) return { valor: traco }
    if (dto.backlog === 0) return { valor: t('previsao.semBacklog') }
    if (semanas == null) return { valor: t('previsao.maisDeLimite').replace('{n}', String(dto.limiteSemanas)) }
    return {
      valor: t(semanas === 1 ? 'previsao.semana' : 'previsao.semanas').replace('{n}', inteiro(semanas, lang)),
      legenda: data ? t('previsao.semanaAlvo').replace('{data}', dataCurta(data, lang)) : undefined,
    }
  }

  function exportar() {
    if (!dto) return
    baixarCsv('previsao.csv', [
      [`# ${t('nav.previsao')} (${dto.versaoRegras})`],
      [`# ${t('gaveta.csv.filtros')}: ${resumo}`],
      [],
      [t('previsao.csv.indicador'), t('previsao.csv.valor')],
      [t('previsao.kpi.backlog'), String(dto.backlog)],
      ['P50', String(dto.termina.semanasP50 ?? '')],
      ['P85', String(dto.termina.semanasP85 ?? '')],
      ['P95', String(dto.termina.semanasP95 ?? '')],
      [t('previsao.kpi.confianca'), t(`previsao.confianca.${dto.confianca}`)],
      [],
      [t('previsao.col.semanas'), 'P50', 'P85', 'P95'],
      ...dto.quantos.map((q) => [String(q.semanas), String(q.p50), String(q.p85), String(q.p95)]),
      [],
      [t('previsao.col.semana'), t('previsao.vazao'), t('previsao.chegada')],
      ...dto.vazaoSemanal.map((s, i) => [s.semana, String(s.n), String(dto.chegadaSemanal[i]?.n ?? '')]),
    ])
  }

  const colunas: Coluna<PrevisaoIssue>[] = [
    { id: 'key', cabecalho: t('abertas.col.task'), valor: (l) => l.key, render: (l) => (
      <>
        <span className="mono">{l.key}</span>
        <span className="resumo-task"> {l.resumo}</span>
      </>
    ) },
    { id: 'tipo', cabecalho: t('abertas.col.tipo'), valor: (l) => l.tipo },
    { id: 'status', cabecalho: t('abertas.col.status'), valor: (l) => l.status },
    { id: 'responsavel', cabecalho: t('abertas.col.responsavel'), valor: (l) => l.responsavel },
    { id: 'idade', cabecalho: `${t('abertas.col.idade')} (${unidade})`, valor: (l) => l.idade, render: (l) => numero(l.idade, lang), numerica: true },
    { id: 'p50', cabecalho: `${t('previsao.col.restante')} P50 (${unidade})`, valor: (l) => l.restanteP50, render: (l) => (l.restanteP50 == null ? traco : numero(l.restanteP50, lang)), numerica: true },
    { id: 'p85', cabecalho: `${t('previsao.col.restante')} P85 (${unidade})`, valor: (l) => l.restanteP85, render: (l) => (l.restanteP85 == null ? traco : numero(l.restanteP85, lang)), numerica: true },
    { id: 'n', cabecalho: t('previsao.col.ciclosMaisLongos'), valor: (l) => l.n, numerica: true },
    {
      id: 'capacidade', cabecalho: t('previsao.col.pelaCapacidade'), valor: (l) => l.pelaCapacidade ?? null,
      render: (l) => (l.pelaCapacidade ? dataCurta(l.pelaCapacidade, lang) : t('previsao.semPrevisaoCapacidade')),
    },
    {
      id: 'atrasa', cabecalho: t('previsao.col.atrasa'), valor: (l) => (l.atrasa == null ? null : l.atrasa ? 1 : 0),
      // Símbolo e texto, nunca só cor: ▲ atrasa, ✓ no prazo; traço quando não há previsão ou vencimento.
      render: (l) =>
        l.atrasa == null ? traco : l.atrasa ? (
          <span className="ocupacao ocupacao--problema"><span aria-hidden="true">▲</span> {t('previsao.atrasa.sim')}</span>
        ) : (
          <span className="ocupacao ocupacao--ok"><span aria-hidden="true">✓</span> {t('previsao.atrasa.nao')}</span>
        ),
    },
  ]

  const t50 = dto ? termina(dto.termina.semanasP50, dto.termina.p50) : { valor: traco }
  const t85 = dto ? termina(dto.termina.semanasP85, dto.termina.p85) : { valor: traco }
  const t95 = dto ? termina(dto.termina.semanasP95, dto.termina.p95) : { valor: traco }

  return (
    <>
      <CardPage
        titulo={t('nav.previsao')}
        pergunta={t('previsao.pergunta')}
        filtros={<Filtros key={sp.toString()} campos={['squad', 'pessoa', 'tipo', 'prioridade', 'os', 'campos', 'unit']} valor={sp} aoAplicar={aplicar} />}
        consulta={consulta}
        avisos={dto?.avisos}
        aoExportar={exportar}
      >
        {dto && (
          <>
            <div role="group" aria-label={t('previsao.indicadores')} className="cardpage-kpis">
              <Cartao
                id="PV-01" titulo={t('previsao.kpi.backlog')} valor={bloqueado ? traco : inteiro(dto.backlog, lang)}
                aoAbrir={comAbrir(dto.backlog, 'PV-01', 'backlog', t('previsao.kpi.backlog'))}
              />
              <Cartao
                id="PV-03" titulo={t('previsao.kpi.confianca')} valor={bloqueado ? traco : t(`previsao.confianca.${dto.confianca}`)}
                estado={bloqueado ? undefined : {
                  tipo: ESTADO_DA_CONFIANCA[dto.confianca] ?? 'neutro',
                  texto: t('previsao.semanasComDado').replace('{n}', String(dto.semanasComDado)).replace('{total}', String(dto.semanasHistorico)),
                }}
                aoAbrir={comAbrir(dto.entregasNaJanela, 'PV-03', 'vazao', t('previsao.kpi.confianca'))}
              />
            </div>

            <div role="group" aria-label={t('previsao.termina')} className="cardpage-kpis">
              <Cartao id="PV-02" titulo="P50" valor={t50.valor} legenda={t50.legenda} aoAbrir={comAbrir(dto.backlog, 'PV-02', 'backlog', `${t('previsao.termina')} P50`)} />
              <Cartao id="PV-02" titulo="P85" valor={t85.valor} legenda={t85.legenda} aoAbrir={comAbrir(dto.backlog, 'PV-02', 'backlog', `${t('previsao.termina')} P85`)} />
              <Cartao id="PV-02" titulo="P95" valor={t95.valor} legenda={t95.legenda} aoAbrir={comAbrir(dto.backlog, 'PV-02', 'backlog', `${t('previsao.termina')} P95`)} />
            </div>
            {!dto.comChegada && <p className="legenda-meta" role="note">{t('previsao.semChegada')}</p>}

            <div className="cardpage-duas-colunas">
              <Cartao id="PV-04" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-pv-vazao" titulo={t('previsao.vazao.titulo')}>
                <ContagensEmBarras
                  barras
                  rotulo={t('previsao.vazao.titulo')}
                  cabecalho={t('previsao.col.semana')}
                  indisponivel={bloqueado}
                  itens={dto.vazaoSemanal.map((s) => ({ chave: s.semana, rotulo: dataCurta(s.semana, lang), n: s.n }))}
                  aoAbrir={(c) => abrir('PV-04', `vazao:${c}`, `${t('previsao.vazao.titulo')}: ${dataCurta(c, lang)}`)}
                />
              </Cartao>
              <Cartao id="PV-04" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-pv-chegada" titulo={t('previsao.chegada.titulo')}>
                <ContagensEmBarras
                  barras
                  rotulo={t('previsao.chegada.titulo')}
                  cabecalho={t('previsao.col.semana')}
                  indisponivel={bloqueado}
                  itens={dto.chegadaSemanal.map((s) => ({ chave: s.semana, rotulo: dataCurta(s.semana, lang), n: s.n }))}
                  aoAbrir={(c) => abrir('PV-04', `chegada:${c}`, `${t('previsao.chegada.titulo')}: ${dataCurta(c, lang)}`)}
                />
              </Cartao>
            </div>

            <Cartao id="PV-05" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-pv-quantos" titulo={t('previsao.quantos')}>
              <p className="legenda-meta">{t('previsao.quantos.nota')}</p>
              <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-pv-quantos">
                <table className="tabela" aria-labelledby="h-pv-quantos">
                  <thead>
                    <tr>
                      <th scope="col">{t('previsao.col.semanas')}</th>
                      <th scope="col" className="num">P50</th>
                      <th scope="col" className="num">P85</th>
                      <th scope="col" className="num">P95</th>
                    </tr>
                  </thead>
                  <tbody>
                    {dto.quantos.map((q) => (
                      <tr key={q.semanas}>
                        <th scope="row">{q.semanas}</th>
                        <td className="num">{bloqueado ? traco : inteiro(q.p50, lang)}</td>
                        <td className="num">{bloqueado ? traco : inteiro(q.p85, lang)}</td>
                        <td className="num">{bloqueado ? traco : inteiro(q.p95, lang)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </Cartao>

            <Cartao
              id="PV-06" variante="lista" nivelDoTitulo={2} idDoTitulo="h-pv-issue" titulo={t('previsao.porTask')}
              aoAbrir={comAbrir(dto.porIssue.length, 'PV-06', 'porIssue', t('previsao.porTask'))}
            >
              <p className="legenda-meta">{t('previsao.porTask.nota')}</p>
              {dto.porIssue.length === 0 ? (
                <p className="vazio">{t('previsao.porTask.vazia')}</p>
              ) : (
                <TabelaOrdenavel
                  rotulo={t('previsao.porTask')} colunas={colunas} linhas={bloqueado ? [] : dto.porIssue} chave={(l) => l.key}
                />
              )}
            </Cartao>
            <p className="legenda-meta">{t('previsao.rodape').replace('{versao}', dto.versaoRegras)}</p>
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
