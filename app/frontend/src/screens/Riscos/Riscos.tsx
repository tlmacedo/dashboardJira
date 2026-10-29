import { useState } from 'react'
import { useLang } from '../../i18n/LangProvider'
import { Cartao } from '../../shared/Cartao'
import { CardPage } from '../../shared/CardPage'
import { baixarCsv } from '../../shared/csv'
import { Filtros, resumoDosFiltros, useFiltrosDaUrl } from '../../shared/Filtros'
import { dataCurta, inteiro } from '../../shared/format'
import { GavetaDeTasks } from '../../shared/GavetaDeTasks'
import { TabelaOrdenavel, type Coluna } from '../../shared/TabelaOrdenavel'
import { temBloqueio, useCard } from '../../shared/useCard'
import type { LinhaRisco, RiscosDto } from './api'
import { FatoresDoRisco } from './FatoresDoRisco'
import '../../shared/tabela.css'

type Gaveta = { cartaoId: string; segmento: string; titulo: string }

/** Símbolo + texto do nível (nunca só cor). */
const SIMBOLO_DO_NIVEL: Record<string, string> = { alto: '▲', medio: '●', baixo: '■' }

/**
 * Riscos (RI-01 a RI-03): score explicável das tasks abertas. A lista mostra os níveis alto e médio com o fator principal;
 * "Pontos por fator" abre a gaveta com a soma de cada fator. Os sinais por pessoa são uma proporção, em ordem alfabética,
 * sem ranking. Os pesos vêm de risco.yaml e a versão aparece no rodapé.
 */
export function Riscos() {
  const { t, lang } = useLang()
  const [sp, aplicar] = useFiltrosDaUrl()
  const consulta = useCard<RiscosDto>('riscos', sp)
  const dto = consulta.data
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)
  const [fatores, setFatores] = useState<LinhaRisco | null>(null)
  const bloqueado = temBloqueio(dto?.avisos)
  const traco = '—'
  const resumo = resumoDosFiltros(sp, t, (iso) => dataCurta(iso, lang))
  const abrir = (cartaoId: string, segmento: string, titulo: string) => setGaveta({ cartaoId, segmento, titulo })
  const comAbrir = (n: number, cartaoId: string, segmento: string, titulo: string) =>
    !bloqueado && n > 0 ? () => abrir(cartaoId, segmento, titulo) : undefined
  const num = (n: number) => (bloqueado ? traco : inteiro(n, lang))

  function exportar() {
    if (!dto) return
    baixarCsv('riscos.csv', [
      [`# ${t('nav.riscos')} (${dto.versaoRegras})`],
      [`# ${t('gaveta.csv.filtros')}: ${resumo}`],
      [],
      [t('abertas.col.task'), t('abertas.col.resumo'), t('abertas.col.tipo'), t('abertas.col.status'), t('abertas.col.responsavel'), t('riscos.col.nivel'), t('riscos.col.pontos'), t('riscos.col.fatorPrincipal')],
      ...dto.linhas.map((l) => [
        l.key, l.resumo, l.tipo, l.status, l.responsavel ?? '', t(`riscos.nivel.${l.nivel}`), String(l.pontos), t(`riscos.fator.${l.fatorPrincipal}`),
      ]),
    ])
  }

  const colunas: Coluna<LinhaRisco>[] = [
    { id: 'key', cabecalho: t('abertas.col.task'), valor: (l) => l.key, render: (l) => (
      <>
        <span className="mono">{l.key}</span>
        <span className="resumo-task"> {l.resumo}</span>
      </>
    ) },
    { id: 'tipo', cabecalho: t('abertas.col.tipo'), valor: (l) => l.tipo },
    { id: 'status', cabecalho: t('abertas.col.status'), valor: (l) => l.status },
    { id: 'responsavel', cabecalho: t('abertas.col.responsavel'), valor: (l) => l.responsavel },
    {
      id: 'nivel', cabecalho: t('riscos.col.nivel'), valor: (l) => l.pontos,
      render: (l) => (
        <span>
          <span aria-hidden="true">{SIMBOLO_DO_NIVEL[l.nivel] ?? '■'}</span> {t(`riscos.nivel.${l.nivel}`)}
        </span>
      ),
    },
    { id: 'pontos', cabecalho: t('riscos.col.pontos'), valor: (l) => l.pontos, numerica: true },
    { id: 'principal', cabecalho: t('riscos.col.fatorPrincipal'), valor: (l) => t(`riscos.fator.${l.fatorPrincipal}`) },
    {
      id: 'fatores', cabecalho: t('riscos.col.fatores'), valor: () => null,
      render: (l) => (
        <button type="button" className="contagem-botao" aria-label={t('riscos.fatores.titulo').replace('{key}', l.key)} onClick={() => setFatores(l)}>
          {t('riscos.fatores.ver')}
        </button>
      ),
    },
  ]

  return (
    <>
      <CardPage
        titulo={t('nav.riscos')}
        pergunta={t('riscos.pergunta')}
        filtros={<Filtros key={sp.toString()} campos={['squad', 'pessoa', 'tipo', 'prioridade', 'os', 'campos']} valor={sp} aoAplicar={aplicar} />}
        consulta={consulta}
        avisos={dto?.avisos}
        aoExportar={exportar}
      >
        {dto && (
          <>
            <div role="group" aria-label={t('riscos.indicadores')} className="cardpage-kpis">
              <Cartao
                id="RI-01" titulo={t('riscos.kpi.alto')} valor={num(dto.alto)} simboloDoValor={!bloqueado && dto.alto > 0 ? '▲' : undefined}
                legenda={t('riscos.kpi.alto.dica').replace('{n}', String(dto.limiteAlto))}
                aoAbrir={comAbrir(dto.alto, 'RI-01', 'nivel:alto', t('riscos.kpi.alto'))}
              />
              <Cartao
                id="RI-01" titulo={t('riscos.kpi.medio')} valor={num(dto.medio)} simboloDoValor={!bloqueado && dto.medio > 0 ? '●' : undefined}
                legenda={t('riscos.kpi.medio.dica').replace('{n}', String(dto.limiteMedio))}
                aoAbrir={comAbrir(dto.medio, 'RI-01', 'nivel:medio', t('riscos.kpi.medio'))}
              />
            </div>

            <Cartao
              id="RI-02" variante="lista" nivelDoTitulo={2} idDoTitulo="h-ri-lista" titulo={t('riscos.lista')}
              aoAbrir={comAbrir(dto.emRisco, 'RI-02', 'emRisco', t('riscos.lista'))}
            >
              {dto.linhas.length === 0 ? (
                <p className="vazio">{t('riscos.lista.vazia')}</p>
              ) : (
                <TabelaOrdenavel
                  rotulo={t('riscos.lista')} colunas={colunas} linhas={bloqueado ? [] : dto.linhas} chave={(l) => l.key}
                  inicial={{ id: 'pontos', descendente: true }}
                />
              )}
              {!bloqueado && dto.baixo > 0 && (
                <p className="legenda-meta">
                  <button type="button" className="contagem-botao" onClick={() => abrir('RI-02', 'nivel:baixo', t('riscos.baixo'))}>
                    {t('riscos.baixo.ver').replace('{n}', inteiro(dto.baixo, lang))}
                  </button>
                </p>
              )}
            </Cartao>

            <Cartao id="RI-03" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-ri-pessoa" titulo={t('riscos.porPessoa')}>
              {dto.sinaisPorPessoa.length === 0 ? (
                <p className="vazio">{t('riscos.porPessoa.vazio')}</p>
              ) : (
                <>
                  <p className="legenda-meta">{t('riscos.porPessoa.nota')}</p>
                  <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-ri-pessoa">
                    <table className="tabela" aria-labelledby="h-ri-pessoa">
                      <thead>
                        <tr>
                          <th scope="col">{t('riscos.col.pessoa')}</th>
                          <th scope="col" className="num">{t('riscos.col.emRisco')}</th>
                          <th scope="col" className="num">{t('riscos.col.abertas')}</th>
                          <th scope="col" className="num">{t('riscos.col.proporcao')}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {/* Ordem alfabética vinda do backend; proporção, sem coluna de posição e sem destaque de melhor ou pior. */}
                        {dto.sinaisPorPessoa.map((p) => (
                          <tr key={p.pessoa}>
                            <th scope="row">{p.pessoa}</th>
                            {([['emRisco', p.issuesEmRisco], ['abertas', p.abertas]] as const).map(([tipo, n]) => (
                              <td className="num" key={tipo}>
                                {!bloqueado && n > 0 ? (
                                  <button
                                    type="button" className="contagem-botao" aria-label={`${p.pessoa} ${t(`riscos.col.${tipo}`)}: ${n}`}
                                    onClick={() => abrir('RI-03', `pessoa:${p.pessoa}:${tipo}`, `${p.pessoa}: ${t(`riscos.col.${tipo}`)}`)}
                                  >
                                    {inteiro(n, lang)}
                                  </button>
                                ) : (
                                  num(n)
                                )}
                              </td>
                            ))}
                            <td className="num">{bloqueado ? traco : `${inteiro(p.issuesEmRisco, lang)} / ${inteiro(p.abertas, lang)}`}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </>
              )}
            </Cartao>
            <p className="legenda-meta">{t('riscos.rodape').replace('{versao}', dto.versaoRegras)}</p>
          </>
        )}
      </CardPage>
      {gaveta && (
        <GavetaDeTasks
          cartaoId={gaveta.cartaoId} segmento={gaveta.segmento} titulo={gaveta.titulo} filtros={sp}
          resumoDosFiltros={resumo} aoFechar={() => setGaveta(null)}
        />
      )}
      {fatores && <FatoresDoRisco linha={fatores} aoFechar={() => setFatores(null)} />}
    </>
  )
}
