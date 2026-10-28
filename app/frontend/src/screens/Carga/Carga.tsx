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
import type { CargaDto } from './api'
import '../../shared/tabela.css'
import '../../shared/pessoas.css'

type Gaveta = { cartaoId: string; segmento: string; titulo: string }

/**
 * Carga por pessoa (CP-01 a CP-03). Sem ranking: a tabela fica em ordem alfabética, sem coluna de posição, e no lugar de
 * "maior e menor ocupação" há duas contagens (acima da capacidade e com folga). Capacidade zero é o texto "sem capacidade".
 * O usuário comum recebe só a própria linha e nenhum índice do conjunto (o Jain vem nulo).
 */
export function Carga() {
  const { t, lang } = useLang()
  const [sp, aplicar] = useFiltrosDaUrl()
  const consulta = useCard<CargaDto>('carga', sp)
  const dto = consulta.data
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)
  const bloqueado = temBloqueio(dto?.avisos)
  const traco = '—'
  const resumo = resumoDosFiltros(sp, t, (iso) => dataCurta(iso, lang))
  const abrir = (cartaoId: string, segmento: string, titulo: string) => setGaveta({ cartaoId, segmento, titulo })
  const comAbrir = (n: number, cartaoId: string, segmento: string, titulo: string) =>
    !bloqueado && n > 0 ? () => abrir(cartaoId, segmento, titulo) : undefined

  function exportar() {
    if (!dto) return
    baixarCsv('carga.csv', [
      [`# ${t('nav.carga')} (${dto.versaoRegras})`],
      [`# ${t('gaveta.csv.filtros')}: ${resumo}`],
      [],
      [
        t('carga.col.pessoa'), t('carga.col.abertas'), t('carga.col.wip'), t('carga.col.fila'), t('carga.col.cap4'),
        t('carga.col.ocupacao'), t('carga.col.ausencia'), t('carga.col.p85'), t('carga.col.vencidas'), t('carga.col.risco'),
        t('carga.col.atraso'),
      ],
      ...dto.linhas.map((l) => [
        l.pessoa, String(l.abertas), String(l.wip), String(l.filaH), String(l.capacidade4SemH),
        l.ocupacao.semCapacidade ? t('pessoas.semCapacidade') : `${Math.round((l.ocupacao.fracao ?? 0) * 100)}%`,
        String(l.ausenciaDias), String(l.acimaDoP85), String(l.vencidas), String(l.riscoAlto), String(l.atrasoAcumuladoH),
      ]),
    ])
  }

  /** Número da tabela: botão que abre as tasks da pessoa quando há o que abrir. */
  function contagem(pessoa: string, tipo: 'fila' | 'wip' | 'vencidas' | 'acimaDoP85' | 'riscoAlto', n: number, rotulo: string) {
    if (bloqueado) return traco
    if (n === 0) return inteiro(n, lang)
    return (
      <button
        type="button" className="contagem-botao" aria-label={`${pessoa} ${rotulo}: ${n}`}
        onClick={() => abrir('CP-03', `pessoa:${pessoa}:${tipo}`, `${pessoa}: ${rotulo}`)}
      >
        {inteiro(n, lang)}
      </button>
    )
  }

  return (
    <>
      <CardPage
        titulo={t('nav.carga')}
        pergunta={t('carga.pergunta')}
        filtros={<Filtros key={sp.toString()} campos={['squad', 'pessoa']} valor={sp} aoAplicar={aplicar} />}
        consulta={consulta}
        avisos={dto?.avisos}
        aoExportar={exportar}
      >
        {dto && (
          <>
            <div role="group" aria-label={t('carga.indicadores')} className="cardpage-kpis">
              <Cartao
                id="CP-01" titulo={t('carga.kpi.jain')} valor={bloqueado || dto.jain === null ? traco : numero(dto.jain, lang, 2)}
                legenda={dto.jain === null ? t('carga.kpi.jain.indisponivel') : t('carga.kpi.jain.nota')}
              />
              <Cartao
                id="CP-02" titulo={t('carga.kpi.acima')} valor={bloqueado ? traco : inteiro(dto.acimaDaCapacidade, lang)}
                simboloDoValor={!bloqueado && dto.acimaDaCapacidade > 0 ? '▲' : undefined}
                legenda={t('carga.kpi.tasksNaFila').replace('{n}', inteiro(dto.tasksAcimaDaCapacidade, lang))}
                aoAbrir={comAbrir(dto.tasksAcimaDaCapacidade, 'CP-02', 'acimaDaCapacidade', t('carga.kpi.acima'))}
              />
              <Cartao
                id="CP-02" titulo={t('carga.kpi.folga')} valor={bloqueado ? traco : inteiro(dto.comFolga, lang)}
                legenda={t('carga.kpi.tasksNaFila').replace('{n}', inteiro(dto.tasksComFolga, lang))}
                aoAbrir={comAbrir(dto.tasksComFolga, 'CP-02', 'comFolga', t('carga.kpi.folga'))}
              />
            </div>

            <Cartao id="CP-03" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-cp-tabela" titulo={t('carga.tabela')}>
              {dto.linhas.length === 0 ? (
                <p className="vazio">{t('carga.vazia')}</p>
              ) : (
                <>
                  <p className="legenda-meta">{t('pessoas.alfabetica')}</p>
                  <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-cp-tabela">
                    <table className="tabela grade-pessoas" aria-labelledby="h-cp-tabela">
                      <thead>
                        <tr>
                          <th scope="col">{t('carga.col.pessoa')}</th>
                          <th scope="col" className="num">{t('carga.col.abertas')}</th>
                          <th scope="col" className="num">{t('carga.col.wip')}</th>
                          <th scope="col" className="num">{t('carga.col.fila')}</th>
                          <th scope="col" className="num">{t('carga.col.cap4')}</th>
                          <th scope="col">{t('carga.col.ocupacao')}</th>
                          <th scope="col" className="num">{t('carga.col.ausencia')}</th>
                          <th scope="col" className="num">{t('carga.col.horas30')}</th>
                          <th scope="col" className="num">{t('carga.col.p85')}</th>
                          <th scope="col" className="num">{t('carga.col.vencidas')}</th>
                          <th scope="col" className="num">{t('carga.col.risco')}</th>
                          <th scope="col" className="num">{t('carga.col.atraso')}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {/* Ordem alfabética vinda do backend; sem coluna de posição nem destaque de melhor ou pior. */}
                        {dto.linhas.map((l) => (
                          <tr key={l.pessoa}>
                            <th scope="row">{l.pessoa}</th>
                            <td className="num">{contagem(l.pessoa, 'fila', l.abertas, t('carga.col.abertas'))}</td>
                            <td className="num">{contagem(l.pessoa, 'wip', l.wip, t('carga.col.wip'))}</td>
                            <td className="num">
                              {bloqueado ? traco : numero(l.filaH, lang)}
                              {l.horasAssumidas && (
                                <span title={t('pessoas.horasAssumidas')} aria-label={t('pessoas.horasAssumidas')}> ≈</span>
                              )}
                            </td>
                            <td className="num">{bloqueado ? traco : numero(l.capacidade4SemH, lang)}</td>
                            <td>{bloqueado ? traco : <Ocupacao o={l.ocupacao} demandaH={l.filaH - l.atrasoAcumuladoH} />}</td>
                            <td className="num">{bloqueado ? traco : numero(l.ausenciaDias, lang)}</td>
                            <td className="num">{l.horasRegistradas30d === null ? traco : numero(l.horasRegistradas30d, lang)}</td>
                            <td className="num">{contagem(l.pessoa, 'acimaDoP85', l.acimaDoP85, t('carga.col.p85'))}</td>
                            <td className="num">{contagem(l.pessoa, 'vencidas', l.vencidas, t('carga.col.vencidas'))}</td>
                            <td className="num">{contagem(l.pessoa, 'riscoAlto', l.riscoAlto, t('carga.col.risco'))}</td>
                            <td className="num">{bloqueado ? traco : numero(l.atrasoAcumuladoH, lang)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  <p className="legenda-meta">{t('carga.nota')}</p>
                </>
              )}
            </Cartao>
            <p className="legenda-meta">{t('carga.rodape').replace('{versao}', dto.versaoRegras)}</p>
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
