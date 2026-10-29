import { useState } from 'react'
import { useLang } from '../../i18n/LangProvider'
import { Cartao } from '../../shared/Cartao'
import { CardPage } from '../../shared/CardPage'
import { ContagensEmBarras } from '../../shared/ContagensEmBarras'
import { baixarCsv } from '../../shared/csv'
import { Filtros, resumoDosFiltros, useFiltrosDaUrl } from '../../shared/Filtros'
import { dataCurta, inteiro, percentual } from '../../shared/format'
import { GavetaDeTasks } from '../../shared/GavetaDeTasks'
import { temBloqueio, useCard } from '../../shared/useCard'
import { GRANULARIDADES, type Granularidade, type LinhaMeta, type MetasDto } from './api'
import '../../shared/tabela.css'

type Gaveta = { cartaoId: string; segmento: string; titulo: string }

const SIMBOLO_DO_ESTADO: Record<string, string> = { ok: '✓', atencao: '●', problema: '▲' }

/** Pílula do estado frente à meta: símbolo + texto, nunca só cor. */
function Pilula({ estado }: { estado: string }) {
  const { t } = useLang()
  return (
    <span className={`pilula pilula--${estado}`}>
      <span aria-hidden="true">{SIMBOLO_DO_ESTADO[estado] ?? '■'}</span> {t(`metas.estado.${estado}`)}
    </span>
  )
}

/** Metas e prazos (MT-01 a MT-14): "no prazo" e "no prazo ajustado" lado a lado, data original e atraso justificado. */
export function Metas() {
  const { t, lang } = useLang()
  const [sp, aplicar] = useFiltrosDaUrl()
  const consulta = useCard<MetasDto>('metas', sp)
  const dto = consulta.data
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)
  const bloqueado = temBloqueio(dto?.avisos)
  const traco = '—'
  const num = (n: number) => (bloqueado ? traco : inteiro(n, lang))
  const taxa = (x: number | null) => (bloqueado || x == null ? traco : percentual(x, lang))
  const resumo = `${resumoDosFiltros(sp, t, (iso) => dataCurta(iso, lang))} | ${t('metas.granularidade')}: ${t(`metas.granularidade.${sp.get('granularidade') ?? 'semana'}`)}`
  const abrir = (cartaoId: string, segmento: string, titulo: string) => setGaveta({ cartaoId, segmento, titulo })
  const comAbrir = (n: number, cartaoId: string, segmento: string, titulo: string) =>
    !bloqueado && n > 0 ? () => abrir(cartaoId, segmento, titulo) : undefined
  const granularidadeAtual = (dto?.granularidade ?? sp.get('granularidade') ?? 'semana') as Granularidade

  function trocarGranularidade(g: Granularidade) {
    const p = new URLSearchParams(sp)
    p.set('granularidade', g)
    aplicar(p)
  }

  const rotuloDaChave = (prefixo: string, chave: string) =>
    prefixo === 'prioridade' && chave === '(sem)' ? t('abertas.semPrioridade') : prefixo === 'squad' && chave === '(sem squad)' ? t('metas.semSquad') : chave

  function exportar() {
    if (!dto) return
    const tabela = (titulo: string, linhas: LinhaMeta[]): string[][] => [
      [],
      [`# ${titulo}`],
      [t('metas.col.grupo'), t('metas.col.universo'), t('metas.col.noPrazo'), t('metas.col.ajustado'), t('metas.col.estado')],
      ...linhas.map((l) => [l.chave, String(l.universo), String(l.noPrazo), String(l.noPrazoAjustado), t(`metas.estado.${l.estado}`)]),
    ]
    baixarCsv('metas.csv', [
      [`# ${t('nav.metas')}`],
      [`# ${t('gaveta.csv.filtros')}: ${resumo}`],
      [`# ${t('metas.csv.versao')}: ${dto.versaoRegras}`],
      [],
      [t('metas.csv.indicador'), t('metas.csv.valor')],
      [t('metas.kpi.universo'), String(dto.universo)],
      [t('metas.kpi.noPrazo'), taxa(dto.taxaNoPrazo)],
      [t('metas.kpi.noPrazoAjustado'), taxa(dto.taxaAjustada)],
      [t('metas.kpi.original'), taxa(dto.taxaOriginal)],
      [t('metas.kpi.antecipadas'), taxa(dto.taxaAntecipadas)],
      [t('metas.kpi.semVencimento'), String(dto.semVencimento)],
      [],
      [t('metas.col.periodo'), t('metas.col.universo'), t('metas.col.noPrazo'), t('metas.col.ajustado'), t('metas.col.taxa'), t('metas.col.antecipadas'), t('metas.col.noDia'), t('metas.col.atrasadas')],
      ...dto.porPeriodo.map((p) => [p.chave, String(p.universo), String(p.noPrazo), String(p.noPrazoAjustado), p.taxaNoPrazo == null ? '' : percentual(p.taxaNoPrazo, lang), String(p.antecipadas), String(p.noDia), String(p.atrasadas)]),
      ...tabela(t('metas.porTipo'), dto.porTipo),
      ...tabela(t('metas.porPrioridade'), dto.porPrioridade),
      ...tabela(t('metas.porSquad'), dto.porSquad),
      ...tabela(t('metas.porPessoa'), dto.porPessoa),
    ])
  }

  /** Tabela por tipo, prioridade, squad ou pessoa: ordem recebida (alfabética ou de catálogo), nunca por taxa. */
  function tabelaDeMetas(cartaoId: string, prefixo: string, idTitulo: string, titulo: string, linhas: LinhaMeta[]) {
    return (
      <Cartao id={cartaoId} variante="tabela" nivelDoTitulo={2} idDoTitulo={idTitulo} titulo={titulo}>
        {linhas.length === 0 ? (
          <p className="vazio">{t('metas.tabela.vazia')}</p>
        ) : (
          <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby={idTitulo}>
            <table className="tabela" aria-labelledby={idTitulo}>
              <thead>
                <tr>
                  <th scope="col">{t('metas.col.grupo')}</th>
                  <th scope="col" className="num">{t('metas.col.universo')}</th>
                  <th scope="col" className="num">{t('metas.col.noPrazo')}</th>
                  <th scope="col" className="num">{t('metas.col.ajustado')}</th>
                  <th scope="col">{t('metas.col.estado')}</th>
                </tr>
              </thead>
              <tbody>
                {linhas.map((l) => (
                  <tr key={l.chave}>
                    <th scope="row">{rotuloDaChave(prefixo, l.chave)}</th>
                    <td className="num">
                      {!bloqueado && l.universo > 0 ? (
                        <button type="button" className="contagem-botao" aria-label={`${rotuloDaChave(prefixo, l.chave)}: ${l.universo}`}
                          onClick={() => abrir(cartaoId, `${prefixo}:${l.chave}`, `${titulo}: ${rotuloDaChave(prefixo, l.chave)}`)}>
                          {inteiro(l.universo, lang)}
                        </button>
                      ) : (
                        num(l.universo)
                      )}
                    </td>
                    <td className="num">{num(l.noPrazo)}</td>
                    <td className="num">{num(l.noPrazoAjustado)}</td>
                    <td>{bloqueado ? traco : <Pilula estado={l.estado} />}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Cartao>
    )
  }

  return (
    <>
      <CardPage
        titulo={t('nav.metas')}
        pergunta={t('metas.pergunta')}
        filtros={
          <>
            <Filtros key={sp.toString()} campos={['periodo', 'squad', 'pessoa', 'tipo', 'prioridade', 'os', 'campos', 'unit']} valor={sp} aoAplicar={aplicar} />
            <div role="group" aria-label={t('metas.granularidade')} className="seg seg--claro controle-granularidade">
              {GRANULARIDADES.map((g) => (
                <button key={g} type="button" aria-pressed={granularidadeAtual === g} onClick={() => trocarGranularidade(g)}>
                  {t(`metas.granularidade.${g}`)}
                </button>
              ))}
            </div>
          </>
        }
        consulta={consulta}
        avisos={dto?.avisos}
        aoExportar={exportar}
      >
        {dto && (
          <>
            {/* "No prazo" e "no prazo ajustado" lado a lado: a mesma regra da Auditoria e de Fechadas. */}
            <div role="group" aria-label={t('metas.prazo')} className="cardpage-kpis">
              <Cartao
                id="MT-01" titulo={t('metas.kpi.noPrazo')} valor={taxa(dto.taxaNoPrazo)}
                legenda={`${t('metas.kpi.noPrazo.dica').replace('{n}', inteiro(dto.noPrazo, lang)).replace('{total}', inteiro(dto.universo, lang))} · ${t('metas.meta').replace('{meta}', percentual(dto.metaNoPrazo, lang))}`}
                aoAbrir={comAbrir(dto.noPrazo, 'MT-01', 'noPrazo', t('metas.kpi.noPrazo'))}
              />
              <Cartao
                id="MT-01" titulo={t('metas.kpi.noPrazoAjustado')} valor={taxa(dto.taxaAjustada)}
                legenda={t('metas.kpi.noPrazo.dica').replace('{n}', inteiro(dto.noPrazoAjustado, lang)).replace('{total}', inteiro(dto.universo, lang))}
                aoAbrir={comAbrir(dto.noPrazoAjustado, 'MT-01', 'noPrazoAjustado', t('metas.kpi.noPrazoAjustado'))}
              />
              <Cartao
                id="MT-02" titulo={t('metas.kpi.original')} valor={taxa(dto.taxaOriginal)}
                legenda={t('metas.kpi.noPrazo.dica').replace('{n}', inteiro(dto.noPrazoOriginal, lang)).replace('{total}', inteiro(dto.universo, lang))}
                aoAbrir={comAbrir(dto.noPrazoOriginal, 'MT-02', 'noPrazoOriginal', t('metas.kpi.original'))}
              />
              <Cartao
                id="MT-03" titulo={t('metas.kpi.antecipadas')} valor={taxa(dto.taxaAntecipadas)}
                legenda={`${inteiro(dto.antecipadas, lang)} · ${t('metas.meta').replace('{meta}', percentual(dto.metaAntecipadas, lang))}`}
                aoAbrir={comAbrir(dto.antecipadas, 'MT-03', 'antecipadas', t('metas.kpi.antecipadas'))}
              />
            </div>

            {dto.alertaDataOriginal && !bloqueado && (
              <p role="note" className="alerta-original">
                <span aria-hidden="true">●</span> {t('metas.alertaDataOriginal')}
              </p>
            )}

            <div className="cardpage-kpis">
              <Cartao id="MT-04" titulo={t('metas.kpi.emD1D2')} valor={num(dto.emD1D2)} aoAbrir={comAbrir(dto.emD1D2, 'MT-04', 'emD1D2', t('metas.kpi.emD1D2'))} />
              <Cartao id="MT-05" titulo={t('metas.kpi.noDia')} valor={num(dto.noDia)} aoAbrir={comAbrir(dto.noDia, 'MT-05', 'noDia', t('metas.kpi.noDia'))} />
              <Cartao id="MT-06" titulo={t('metas.kpi.atrasadas')} valor={num(dto.atrasadas)} simboloDoValor={dto.atrasadas > 0 && !bloqueado ? '▲' : undefined}
                aoAbrir={comAbrir(dto.atrasadas, 'MT-06', 'atrasadas', t('metas.kpi.atrasadas'))} />
              <Cartao id="MT-07" titulo={t('metas.kpi.semVencimento')} valor={num(dto.semVencimento)} legenda={t('metas.kpi.semVencimento.dica')}
                aoAbrir={comAbrir(dto.semVencimento, 'MT-07', 'semVencimento', t('metas.kpi.semVencimento'))} />
            </div>

            <div className="cardpage-duas-colunas">
              <Cartao id="MT-10" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-mt-dist" titulo={t('metas.distribuicao')}>
                <ContagensEmBarras
                  barras rotulo={t('metas.distribuicao')} cabecalho={t('metas.col.faixa')} indisponivel={bloqueado}
                  itens={dto.distribuicao.map((c) => ({ chave: c.chave, rotulo: t(`metas.faixa.${c.chave}`), n: c.n }))}
                  aoAbrir={(c) => abrir('MT-10', `faixa:${c}`, `${t('metas.distribuicao')}: ${t(`metas.faixa.${c}`)}`)}
                />
              </Cartao>
              <Cartao id="MT-08" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-mt-serie" titulo={t('metas.serie')}>
                {dto.porPeriodo.length === 0 ? (
                  <p className="vazio">{t('metas.serie.vazia')}</p>
                ) : (
                  <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-mt-serie">
                    <table className="tabela" aria-labelledby="h-mt-serie">
                      <thead>
                        <tr>
                          <th scope="col">{t('metas.col.periodo')}</th>
                          <th scope="col" className="num">{t('metas.col.universo')}</th>
                          <th scope="col" className="num">{t('metas.col.noPrazo')}</th>
                          <th scope="col" className="num">{t('metas.col.taxa')}</th>
                          <th scope="col" className="num">{t('metas.col.antecipadas')}</th>
                          <th scope="col" className="num">{t('metas.col.noDia')}</th>
                          <th scope="col" className="num">{t('metas.col.atrasadas')}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {dto.porPeriodo.map((p) => (
                          <tr key={p.chave}>
                            <th scope="row">{p.chave.length === 10 ? dataCurta(p.chave, lang) : p.chave}</th>
                            <td className="num">
                              {!bloqueado ? (
                                <button type="button" className="contagem-botao" aria-label={`${p.chave}: ${p.universo}`}
                                  onClick={() => abrir('MT-08', `periodo:${p.chave}`, `${t('metas.serie')}: ${p.chave}`)}>
                                  {inteiro(p.universo, lang)}
                                </button>
                              ) : traco}
                            </td>
                            <td className="num">
                              {!bloqueado && p.noPrazo > 0 ? (
                                <button type="button" className="contagem-botao" aria-label={`${p.chave} ${t('metas.col.noPrazo')}: ${p.noPrazo}`}
                                  onClick={() => abrir('MT-09', `periodo:${p.chave}:noPrazo`, `${t('metas.serie')}: ${p.chave}, ${t('metas.col.noPrazo')}`)}>
                                  {inteiro(p.noPrazo, lang)}
                                </button>
                              ) : num(p.noPrazo)}
                            </td>
                            <td className="num">{taxa(p.taxaNoPrazo)}</td>
                            <td className="num">{num(p.antecipadas)}</td>
                            <td className="num">{num(p.noDia)}</td>
                            <td className="num">{num(p.atrasadas)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </Cartao>
            </div>

            {tabelaDeMetas('MT-11', 'tipo', 'h-mt-tipo', t('metas.porTipo'), dto.porTipo)}
            {tabelaDeMetas('MT-12', 'prioridade', 'h-mt-prioridade', t('metas.porPrioridade'), dto.porPrioridade)}
            {tabelaDeMetas('MT-13', 'squad', 'h-mt-squad', t('metas.porSquad'), dto.porSquad)}
            {tabelaDeMetas('MT-14', 'pessoa', 'h-mt-pessoa', t('metas.porPessoa'), dto.porPessoa)}
            <p className="legenda-meta">{t('metas.rodape').replace('{versao}', dto.versaoRegras)}</p>
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
