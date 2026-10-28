import { useState } from 'react'
import { useLang } from '../../i18n/LangProvider'
import { Cartao, type EstadoDoCartao } from '../../shared/Cartao'
import { CardPage } from '../../shared/CardPage'
import { baixarCsv } from '../../shared/csv'
import { Filtros, resumoDosFiltros, useFiltrosDaUrl } from '../../shared/Filtros'
import { dataCurta, inteiro, percentual } from '../../shared/format'
import { GavetaDeTasks } from '../../shared/GavetaDeTasks'
import { temBloqueio, useCard } from '../../shared/useCard'
import type { NegativasDto } from './api'
import '../../shared/tabela.css'

type Gaveta = { cartaoId: string; segmento: string; titulo: string }

/**
 * Negativas (NE-01 a NE-08 e NE-10): o que saiu do trilho no período. Reabertas e devolvidas valem pela data do evento
 * no changelog. As taxas são sobre as concluídas do período e acima do limite levam ▲ com texto (nunca só cor).
 */
export function Negativas() {
  const { t, lang } = useLang()
  const [sp, aplicar] = useFiltrosDaUrl()
  const consulta = useCard<NegativasDto>('negativas', sp)
  const dto = consulta.data
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)
  const bloqueado = temBloqueio(dto?.avisos)
  const traco = '—'
  const resumo = resumoDosFiltros(sp, t, (iso) => dataCurta(iso, lang))
  const abrir = (cartaoId: string, segmento: string, titulo: string) => setGaveta({ cartaoId, segmento, titulo })
  const comAbrir = (n: number, cartaoId: string, segmento: string, titulo: string) =>
    !bloqueado && n > 0 ? () => abrir(cartaoId, segmento, titulo) : undefined
  const num = (n: number) => (bloqueado ? traco : inteiro(n, lang))

  function exportar() {
    if (!dto) return
    baixarCsv('negativas.csv', [
      [`# ${t('nav.negativas')}`],
      [`# ${t('gaveta.csv.filtros')}: ${resumo}`],
      [],
      [t('negativas.csv.indicador'), t('negativas.csv.valor')],
      [t('negativas.kpi.reabertas'), String(dto.reabertas)],
      [t('negativas.kpi.devolvidas'), String(dto.devolvidas)],
      [t('negativas.kpi.canceladas'), String(dto.canceladas)],
      [t('negativas.kpi.concluidasComAtraso'), String(dto.concluidasComAtraso)],
      [t('negativas.kpi.atrasadasSemJustificativa'), String(dto.atrasadasSemJustificativa)],
      [t('negativas.kpi.abertasVencidas'), String(dto.abertasVencidas)],
      [t('negativas.kpi.estouroDeEstimativa'), String(dto.estouroDeEstimativa)],
      [t('negativas.kpi.muitasTrocas'), String(dto.muitasTrocas)],
      [t('negativas.csv.concluidas'), String(dto.concluidas)],
      [],
      [t('negativas.col.pessoa'), t('negativas.col.reabertas'), t('negativas.col.devolvidas'), t('negativas.col.atrasadas')],
      ...dto.porResponsavel.map((p) => [p.pessoa, String(p.reabertas), String(p.devolvidas), String(p.atrasadas)]),
    ])
  }

  /** Taxa com símbolo e texto: ▲ acima do limite, ✓ até o limite, ■ sem concluídas (nunca só cor). */
  function taxa(
    valor: number | null, numerador: number,
  ): { estado?: { tipo: EstadoDoCartao; texto: string }; valorTexto: string; legenda: string } {
    const limite = Math.round((dto?.limiteDaTaxa ?? 0.2) * 100)
    if (bloqueado) return { valorTexto: traco, legenda: '' }
    if (valor == null) return { valorTexto: traco, legenda: t('negativas.taxa.semConcluidas'), estado: { tipo: 'neutro', texto: t('negativas.estado.semBase') } }
    const acima = valor > (dto?.limiteDaTaxa ?? 0.2)
    return {
      valorTexto: percentual(valor, lang),
      legenda: t('negativas.taxa.dica').replace('{n}', inteiro(numerador, lang)).replace('{total}', inteiro(dto?.concluidas ?? 0, lang)),
      estado: acima
        ? { tipo: 'problema', texto: t('negativas.estado.acima').replace('{limite}', String(limite)) }
        : { tipo: 'ok', texto: t('negativas.estado.ate').replace('{limite}', String(limite)) },
    }
  }

  return (
    <>
      <CardPage
        titulo={t('nav.negativas')}
        pergunta={t('negativas.pergunta')}
        filtros={<Filtros key={sp.toString()} campos={['periodo', 'squad', 'pessoa', 'tipo', 'prioridade', 'os', 'campos']} valor={sp} aoAplicar={aplicar} />}
        consulta={consulta}
        avisos={dto?.avisos}
        aoExportar={exportar}
      >
        {dto && (
          <>
            <div role="group" aria-label={t('negativas.indicadores')} className="cardpage-kpis">
              <Cartao id="NE-01" titulo={t('negativas.kpi.reabertas')} valor={num(dto.reabertas)} aoAbrir={comAbrir(dto.reabertas, 'NE-01', 'reabertas', t('negativas.kpi.reabertas'))} />
              <Cartao id="NE-02" titulo={t('negativas.kpi.devolvidas')} valor={num(dto.devolvidas)} aoAbrir={comAbrir(dto.devolvidas, 'NE-02', 'devolvidas', t('negativas.kpi.devolvidas'))} />
              <Cartao id="NE-03" titulo={t('negativas.kpi.canceladas')} valor={num(dto.canceladas)} aoAbrir={comAbrir(dto.canceladas, 'NE-03', 'canceladas', t('negativas.kpi.canceladas'))} />
              {/* Atraso e atraso sem justificativa lado a lado (a mesma regra de "no prazo" das outras telas). */}
              <Cartao
                id="NE-04" titulo={t('negativas.kpi.concluidasComAtraso')} valor={num(dto.concluidasComAtraso)}
                aoAbrir={comAbrir(dto.concluidasComAtraso, 'NE-04', 'concluidasComAtraso', t('negativas.kpi.concluidasComAtraso'))}
              />
              <Cartao
                id="NE-04" titulo={t('negativas.kpi.atrasadasSemJustificativa')} valor={num(dto.atrasadasSemJustificativa)}
                legenda={t('negativas.kpi.atrasadasSemJustificativa.dica')}
                aoAbrir={comAbrir(dto.atrasadasSemJustificativa, 'NE-04', 'atrasadasSemJustificativa', t('negativas.kpi.atrasadasSemJustificativa'))}
              />
              <Cartao
                id="NE-05" titulo={t('negativas.kpi.abertasVencidas')} valor={num(dto.abertasVencidas)}
                simboloDoValor={!bloqueado && dto.abertasVencidas > 0 ? '▲' : undefined}
                aoAbrir={comAbrir(dto.abertasVencidas, 'NE-05', 'abertasVencidas', t('negativas.kpi.abertasVencidas'))}
              />
              <Cartao
                id="NE-06" titulo={t('negativas.kpi.estouroDeEstimativa')} valor={num(dto.estouroDeEstimativa)}
                aoAbrir={comAbrir(dto.estouroDeEstimativa, 'NE-06', 'estouroDeEstimativa', t('negativas.kpi.estouroDeEstimativa'))}
              />
              <Cartao
                id="NE-07" titulo={t('negativas.kpi.muitasTrocas')} valor={num(dto.muitasTrocas)}
                aoAbrir={comAbrir(dto.muitasTrocas, 'NE-07', 'muitasTrocas', t('negativas.kpi.muitasTrocas'))}
              />
            </div>

            <div role="group" aria-label={t('negativas.taxas')} className="cardpage-kpis">
              {([
                ['taxaReabertura', 'taxa:reabertura', dto.taxaReabertura, dto.reabertas],
                ['taxaDevolucao', 'taxa:devolucao', dto.taxaDevolucao, dto.devolvidas],
                ['taxaAtraso', 'taxa:atraso', dto.taxaAtraso, dto.concluidasComAtraso],
                ['taxaAtrasoSemJustificativa', 'taxa:atrasoSemJustificativa', dto.taxaAtrasoSemJustificativa, dto.atrasadasSemJustificativa],
              ] as const).map(([chave, segmento, valor, numerador]) => {
                const x = taxa(valor, numerador)
                const titulo = t(`negativas.${chave}`)
                return (
                  <Cartao
                    key={chave} id="NE-08" titulo={titulo} valor={x.valorTexto} legenda={x.legenda} estado={x.estado}
                    aoAbrir={comAbrir(numerador, 'NE-08', segmento, titulo)}
                  />
                )
              })}
            </div>

            <Cartao id="NE-10" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-ne-pessoa" titulo={t('negativas.porPessoa')}>
              {dto.porResponsavel.length === 0 ? (
                <p className="vazio">{t('negativas.porPessoa.vazio')}</p>
              ) : (
                <>
                  <p className="legenda-meta">{t('negativas.porPessoa.nota')}</p>
                  <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-ne-pessoa">
                    <table className="tabela" aria-labelledby="h-ne-pessoa">
                      <thead>
                        <tr>
                          <th scope="col">{t('negativas.col.pessoa')}</th>
                          <th scope="col" className="num">{t('negativas.col.reabertas')}</th>
                          <th scope="col" className="num">{t('negativas.col.devolvidas')}</th>
                          <th scope="col" className="num">{t('negativas.col.atrasadas')}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {/* Ordem alfabética vinda do backend; sem coluna de posição e sem destaque de melhor ou pior. */}
                        {dto.porResponsavel.map((p) => (
                          <tr key={p.pessoa}>
                            <th scope="row">{p.pessoa}</th>
                            {([['reabertas', p.reabertas], ['devolvidas', p.devolvidas], ['atrasadas', p.atrasadas]] as const).map(([tipo, n]) => (
                              <td className="num" key={tipo}>
                                {!bloqueado && n > 0 ? (
                                  <button
                                    type="button" className="contagem-botao" aria-label={`${p.pessoa} ${t(`negativas.col.${tipo}`)}: ${n}`}
                                    onClick={() => abrir('NE-10', `pessoa:${p.pessoa}:${tipo}`, `${p.pessoa}: ${t(`negativas.col.${tipo}`)}`)}
                                  >
                                    {inteiro(n, lang)}
                                  </button>
                                ) : (
                                  num(n)
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
