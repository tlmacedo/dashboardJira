import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useMemo, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ErroDaApi } from '../../api/client'
import { useLang } from '../../i18n/LangProvider'
import { Cartao } from '../../shared/Cartao'
import { baixarCsv } from '../../shared/csv'
import { dataCurta, inteiro, percentual } from '../../shared/format'
import { GavetaDeTasks } from '../../shared/GavetaDeTasks'
import {
  CODIGOS_DE_SINAL,
  fetchSinais,
  filtrosDaUrl,
  paramsDe,
  type AuditoriaDto,
  type Filtros,
  type Sinal,
} from './api'
import { textoDoAviso } from './aviso'
import { HistoricoDrawer } from './HistoricoDrawer'
import '../../shared/tabela.css'
import '../TempoEmStatus/tempo.css'
import './auditoria.css'

/** Acima disso a lista mostra só as primeiras linhas (a gaveta e o CSV trazem todas). */
const MAX_LINHAS = 200

/** Recorte aberto na gaveta de tasks: o cartão que o conta e o segmento (ver `AuditoriaCartoes.kt` no backend). */
type Gaveta = { cartaoId: string; segmento: string; titulo: string }

/** "Squad: Alfa | Período: 01/10/2026 – 30/10/2026": os filtros ativos em texto, para a gaveta e para o CSV. */
function resumoDosFiltros(f: Filtros, t: (k: string) => string, lang: 'pt' | 'en'): string {
  const partes: string[] = []
  if (f.de || f.ate) {
    partes.push(`${t('auditoria.filtro.periodo')}: ${f.de ? dataCurta(f.de, lang) : '…'} – ${f.ate ? dataCurta(f.ate, lang) : '…'}`)
  }
  if (f.squad) partes.push(`${t('auditoria.filtro.squad')}: ${f.squad}`)
  if (f.pessoa) partes.push(`${t('auditoria.filtro.pessoa')}: ${f.pessoa}`)
  if (f.sinal) partes.push(`${t('auditoria.filtro.sinal')}: ${t(`auditoria.sinal.${f.sinal}`)}`)
  if (f.resultado) partes.push(`${t('auditoria.filtro.resultado')}: ${t(`auditoria.resultado.${f.resultado}`)}`)
  return partes.join(' | ') || `${t('auditoria.csv.nenhum')} (${t('auditoria.filtro.padrao')})`
}

/** Pessoas dos sinais (responsável e autor) em ordem alfabética: sem contagem, sem ordem por volume. */
function pessoasDos(sinais: Sinal[]): string[] {
  const nomes = new Set<string>()
  for (const s of sinais) {
    if (s.responsavel) nomes.add(s.responsavel)
    if (s.autor) nomes.add(s.autor)
  }
  return [...nomes]
}

const ordemAlfabetica = (a: string, b: string) => a.localeCompare(b)

export function Auditoria() {
  const { t, lang } = useLang()
  const [sp, setSp] = useSearchParams()
  const filtros = useMemo(() => filtrosDaUrl(sp), [sp])
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)
  const [historico, setHistorico] = useState<string | null>(null)
  const [pessoasVistas, setPessoasVistas] = useState<string[]>([])

  const consulta = useQuery({
    queryKey: ['auditoria', paramsDe(filtros).toString()],
    queryFn: () => fetchSinais(paramsDe(filtros)),
    placeholderData: keepPreviousData,
    retry: false,
  })
  const dto = consulta.data
  const status = consulta.error instanceof ErroDaApi ? consulta.error.status : null

  // O seletor de pessoa guarda as já vistas nesta tela, para não encolher ao filtrar por uma delas
  // (estado derivado da resposta, ajustado durante a renderização).
  const faltam = dto ? pessoasDos(dto.sinais).filter((p) => !pessoasVistas.includes(p)) : []
  if (faltam.length > 0) setPessoasVistas([...pessoasVistas, ...faltam].sort(ordemAlfabetica))

  function aplicar(novo: Filtros) {
    setSp(paramsDe(novo), { replace: false })
  }

  function abrir(cartaoId: string, segmento: string, titulo: string) {
    setGaveta({ cartaoId, segmento, titulo })
  }

  const resumo = resumoDosFiltros(filtros, t, lang)

  function exportar() {
    if (!dto) return
    const motivo = (s: Sinal) => t(`auditoria.motivo.${s.motivo}`)
    baixarCsv('auditoria.csv', [
      [`# ${t('nav.auditoria')}`],
      [`# ${t('auditoria.aviso')}`],
      [`# ${t('auditoria.csv.filtros')}: ${resumo}`],
      [`# ${t('auditoria.csv.versao')}: ${dto.versaoRegras}`],
      [],
      [
        t('auditoria.col.data'),
        t('auditoria.col.issue'),
        t('auditoria.col.sinal'),
        t('auditoria.filtro.resultado'),
        t('auditoria.col.gravidade'),
        t('auditoria.col.campo'),
        t('auditoria.historico.col.de'),
        t('auditoria.historico.col.para'),
        t('auditoria.col.autor'),
        t('auditoria.col.autorizacao'),
        t('auditoria.col.responsavel'),
        t('auditoria.emLote').replace(' ({n})', ''),
      ],
      ...dto.sinais.map((s) => [
        s.dia,
        s.key,
        t(`auditoria.sinal.${s.codigo}`),
        t(`auditoria.resultado.${s.resultado}`),
        String(s.gravidade),
        s.campo,
        s.de ?? '',
        s.para ?? '',
        s.autor ?? '',
        `${s.autorizado ? t('auditoria.autorizado') : t('auditoria.naoAutorizado')} (${motivo(s)})`,
        s.responsavel ?? '',
        s.emLote ? String(s.emLote) : '',
      ]),
    ])
  }

  return (
    <div className="tempo auditoria">
      <div className="top">
        <div>
          <h1>{t('nav.auditoria')}</h1>
          <p className="sub">{t('auditoria.sub')}</p>
        </div>
        <div className="top-acoes">
          {dto && (
            <button type="button" className="btn" onClick={exportar} disabled={dto.sinais.length === 0}>
              {t('auditoria.csv')}
            </button>
          )}
        </div>
      </div>

      {/* Indício, não prova: faixa fixa, em toda tela e toda gaveta da auditoria. */}
      <p role="note" className="aud-aviso">
        <span aria-hidden="true">●</span> {t('auditoria.aviso')}
      </p>

      <div className="barra">
        <FormFiltros key={paramsDe(filtros).toString()} filtros={filtros} pessoas={pessoasVistas} aoAplicar={aplicar} />
      </div>

      {consulta.isPending && (
        <p className="vazio" aria-busy="true">
          {t('app.carregando')}
        </p>
      )}

      {consulta.isError && !dto && (
        <div className="estado estado--erro" role="alert">
          {status === 403 && <p>{t('auditoria.semPermissao')}</p>}
          {status === 400 && <p>{t('auditoria.filtroInvalido')}</p>}
          {status !== 403 && status !== 400 && (
            <p>
              {t('auditoria.erro')}
              {status ? ` (HTTP ${status})` : ''}
            </p>
          )}
          {status !== 403 && (
            <button type="button" className="btn" onClick={() => consulta.refetch()}>
              {t('auditoria.tentarDeNovo')}
            </button>
          )}
        </div>
      )}

      {dto && (
        <div className={consulta.isFetching ? 'conteudo conteudo--atualizando' : 'conteudo'} aria-busy={consulta.isFetching}>
          {dto.avisos.length > 0 && (
            <div className="aviso" role="status">
              <strong>{t('auditoria.avisos')}</strong>
              <ul>
                {dto.avisos.map((a) => (
                  <li key={`${a.codigo}:${JSON.stringify(a.params ?? {})}`}>{textoDoAviso(a, t)}</li>
                ))}
              </ul>
            </div>
          )}
          <Indicadores dto={dto} aoAbrir={abrir} />
          <PorSinal dto={dto} aoAbrir={abrir} />
          <Lista dto={dto} aoAbrirHistorico={setHistorico} aoAbrirGaveta={abrir} />
        </div>
      )}

      {gaveta && (
        <GavetaDeTasks
          cartaoId={gaveta.cartaoId}
          segmento={gaveta.segmento}
          titulo={gaveta.titulo}
          filtros={paramsDe(filtros)}
          resumoDosFiltros={resumo}
          aoFechar={() => setGaveta(null)}
        />
      )}
      {historico && <HistoricoDrawer chave={historico} resumoDosFiltros={resumo} aoFechar={() => setHistorico(null)} />}
    </div>
  )
}

function FormFiltros(props: { filtros: Filtros; pessoas: string[]; aoAplicar: (f: Filtros) => void }) {
  const { t } = useLang()
  const [rascunho, setRascunho] = useState<Filtros>(props.filtros)
  // A pessoa já escolhida sempre aparece, mesmo que nenhuma resposta a tenha trazido ainda.
  const pessoas = useMemo(
    () => [...new Set([...props.pessoas, ...(props.filtros.pessoa ? [props.filtros.pessoa] : [])])].sort(ordemAlfabetica),
    [props.pessoas, props.filtros.pessoa],
  )

  function enviar(e: FormEvent) {
    e.preventDefault()
    props.aoAplicar(rascunho)
  }

  return (
    <form className="filtros" onSubmit={enviar} aria-label={t('auditoria.filtros')}>
      <label className="campo campo--data">
        <span>{t('auditoria.filtro.de')}</span>
        <input type="date" value={rascunho.de} onChange={(e) => setRascunho({ ...rascunho, de: e.target.value })} />
      </label>
      <label className="campo campo--data">
        <span>{t('auditoria.filtro.ate')}</span>
        <input type="date" value={rascunho.ate} onChange={(e) => setRascunho({ ...rascunho, ate: e.target.value })} />
      </label>
      <label className="campo">
        <span>{t('auditoria.filtro.squad')}</span>
        <input type="text" maxLength={100} value={rascunho.squad} onChange={(e) => setRascunho({ ...rascunho, squad: e.target.value })} />
      </label>
      <label className="campo">
        <span>{t('auditoria.filtro.pessoa')}</span>
        <select value={rascunho.pessoa} onChange={(e) => setRascunho({ ...rascunho, pessoa: e.target.value })}>
          <option value="">{t('auditoria.filtro.todas')}</option>
          {pessoas.map((p) => (
            <option key={p} value={p}>
              {p}
            </option>
          ))}
        </select>
      </label>
      <label className="campo">
        <span>{t('auditoria.filtro.sinal')}</span>
        <select value={rascunho.sinal} onChange={(e) => setRascunho({ ...rascunho, sinal: e.target.value })}>
          <option value="">{t('auditoria.filtro.todos')}</option>
          {CODIGOS_DE_SINAL.map((c) => (
            <option key={c} value={c}>
              {t(`auditoria.sinal.${c}`)}
            </option>
          ))}
        </select>
      </label>
      <label className="campo">
        <span>{t('auditoria.filtro.resultado')}</span>
        <select value={rascunho.resultado} onChange={(e) => setRascunho({ ...rascunho, resultado: e.target.value })}>
          <option value="">{t('auditoria.filtro.todos')}</option>
          <option value="auditoria">{t('auditoria.resultado.auditoria')}</option>
          <option value="alerta">{t('auditoria.resultado.alerta')}</option>
        </select>
      </label>
      <button type="submit" className="btn btn--primario">
        {t('auditoria.aplicar')}
      </button>
    </form>
  )
}

function Indicadores(props: { dto: AuditoriaDto; aoAbrir: (cartaoId: string, segmento: string, titulo: string) => void }) {
  const { t, lang } = useLang()
  const { dto, aoAbrir } = props
  const nAuditoria = dto.sinais.filter((s) => s.resultado === 'auditoria').length
  const nAlertas = dto.sinais.filter((s) => s.resultado === 'alerta').length
  const np = dto.noPrazo
  const traco = '—'
  const dica = (chave: string, n: number) => t(chave).replace('{n}', inteiro(n, lang)).replace('{total}', inteiro(np.concluidas, lang))

  return (
    <div role="group" aria-label={t('auditoria.indicadores')} className="aud-kpis">
      <Cartao
        id="AU-01"
        titulo={t('auditoria.kpi.auditoria')}
        valor={inteiro(nAuditoria, lang)}
        simboloDoValor="▲"
        legenda={t('auditoria.kpi.auditoria.dica')}
        aoAbrir={nAuditoria > 0 ? () => aoAbrir('AU-01', 'resultado:auditoria', t('auditoria.kpi.auditoria')) : undefined}
      />
      <Cartao
        id="AU-01"
        titulo={t('auditoria.kpi.alertas')}
        valor={inteiro(nAlertas, lang)}
        simboloDoValor="●"
        legenda={t('auditoria.kpi.alertas.dica')}
        aoAbrir={nAlertas > 0 ? () => aoAbrir('AU-01', 'resultado:alerta', t('auditoria.kpi.alertas')) : undefined}
      />
      {/* "No prazo" e "no prazo ajustado" lado a lado (spec 4.2). */}
      <Cartao
        id="AU-03"
        titulo={t('auditoria.kpi.noPrazo')}
        valor={np.taxa == null ? traco : percentual(np.taxa, lang)}
        legenda={dica('auditoria.kpi.noPrazo.dica', np.noPrazo)}
        aoAbrir={np.noPrazo > 0 ? () => aoAbrir('AU-03', 'noPrazo', t('auditoria.kpi.noPrazo')) : undefined}
      />
      <Cartao
        id="AU-03"
        titulo={t('auditoria.kpi.noPrazoAjustado')}
        valor={np.taxaAjustada == null ? traco : percentual(np.taxaAjustada, lang)}
        legenda={dica('auditoria.kpi.noPrazoAjustado.dica', np.noPrazoAjustado)}
        aoAbrir={np.noPrazoAjustado > 0 ? () => aoAbrir('AU-03', 'noPrazoAjustado', t('auditoria.kpi.noPrazoAjustado')) : undefined}
      />
      <Cartao
        id="AU-03"
        titulo={t('auditoria.kpi.justificadas')}
        valor={inteiro(np.justificadasSobAuditoria, lang)}
        legenda={t('auditoria.kpi.justificadas.dica')}
        aoAbrir={
          np.justificadasSobAuditoria > 0
            ? () => aoAbrir('AU-03', 'justificadasSobAuditoria', t('auditoria.kpi.justificadas'))
            : undefined
        }
      />
    </div>
  )
}

function PorSinal(props: { dto: AuditoriaDto; aoAbrir: (cartaoId: string, segmento: string, titulo: string) => void }) {
  const { t, lang } = useLang()
  return (
    <Cartao id="AU-02" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-aud-por-sinal" titulo={t('auditoria.porSinal')}>
      <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-aud-por-sinal">
        <table className="tabela" aria-labelledby="h-aud-por-sinal">
          <thead>
            <tr>
              <th scope="col">{t('auditoria.col.sinal')}</th>
              <th scope="col">{t('auditoria.col.gravidade')}</th>
              <th scope="col" className="num">
                {t('auditoria.col.n')}
              </th>
            </tr>
          </thead>
          <tbody>
            {/* Ordem fixa do catálogo (vem do backend): nunca por quantidade e nunca por pessoa. */}
            {props.dto.porSinal.map((p) => {
              const nome = t(`auditoria.sinal.${p.codigo}`)
              return (
                <tr key={p.codigo}>
                  <th scope="row">{nome}</th>
                  <td>{t('auditoria.gravidade').replace('{n}', String(p.gravidade))}</td>
                  <td className="num">
                    {p.n > 0 ? (
                      <button
                        type="button"
                        className="aud-contagem"
                        aria-label={`${nome}: ${p.n}`}
                        onClick={() => props.aoAbrir('AU-02', `sinal:${p.codigo}`, nome)}
                      >
                        {inteiro(p.n, lang)}
                      </button>
                    ) : (
                      inteiro(0, lang)
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </Cartao>
  )
}

function Lista(props: {
  dto: AuditoriaDto
  aoAbrirHistorico: (chave: string) => void
  aoAbrirGaveta: (cartaoId: string, segmento: string, titulo: string) => void
}) {
  const { t, lang } = useLang()
  const { dto } = props
  const mostradas = dto.sinais.slice(0, MAX_LINHAS)
  return (
    <Cartao
      id="AU-01"
      variante="lista"
      nivelDoTitulo={2}
      idDoTitulo="h-aud-lista"
      titulo={t('auditoria.lista')}
      aoAbrir={dto.sinais.length > 0 ? () => props.aoAbrirGaveta('AU-01', 'todos', t('auditoria.lista')) : undefined}
    >
      {dto.sinais.length === 0 ? (
        <p className="vazio">{t('auditoria.lista.vazio')}</p>
      ) : (
        <>
          <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-aud-lista">
            <table className="tabela" aria-labelledby="h-aud-lista">
              <thead>
                <tr>
                  <th scope="col">{t('auditoria.col.data')}</th>
                  <th scope="col">{t('auditoria.col.issue')}</th>
                  <th scope="col">{t('auditoria.col.sinal')}</th>
                  <th scope="col">{t('auditoria.col.mudanca')}</th>
                  <th scope="col">{t('auditoria.col.autor')}</th>
                  <th scope="col">{t('auditoria.col.autorizacao')}</th>
                </tr>
              </thead>
              <tbody>
                {mostradas.map((s, i) => (
                  <tr key={`${s.key}:${s.codigo}:${s.at}:${i}`}>
                    <td className="num">{dataCurta(s.dia, lang)}</td>
                    <th scope="row">
                      <button
                        type="button"
                        className="aud-chave mono"
                        title={t('auditoria.abrirHistorico').replace('{key}', s.key)}
                        onClick={() => props.aoAbrirHistorico(s.key)}
                      >
                        {s.key}
                      </button>
                      <span className="aud-resumo">{s.resumo}</span>
                    </th>
                    <td>
                      <span aria-hidden="true">{s.resultado === 'alerta' ? '●' : '▲'}</span>{' '}
                      {t(`auditoria.resultado.${s.resultado}`)}
                      <span className="aud-detalhe">
                        {t(`auditoria.sinal.${s.codigo}`)} · {t('auditoria.gravidade').replace('{n}', String(s.gravidade))}
                      </span>
                    </td>
                    <td>
                      <span className="mono">{s.campo}</span>: {s.de ?? '—'} → {s.para ?? '—'}
                    </td>
                    <td>
                      {s.autor ?? t('auditoria.autorDesconhecido')}
                      {s.emLote ? (
                        <span className="aud-lote" title={t('auditoria.emLote.dica').replace('{n}', String(s.emLote))}>
                          {t('auditoria.emLote').replace('{n}', String(s.emLote))}
                        </span>
                      ) : null}
                    </td>
                    <td>
                      <span aria-hidden="true">{s.autorizado ? '✓' : '▲'}</span>{' '}
                      {s.autorizado ? t('auditoria.autorizado') : t('auditoria.naoAutorizado')}
                      <span className="aud-detalhe">{t(`auditoria.motivo.${s.motivo}`)}</span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {dto.sinais.length > MAX_LINHAS && (
            <p className="legenda">
              {t('auditoria.lista.limite').replace('{n}', String(MAX_LINHAS)).replace('{total}', inteiro(dto.sinais.length, lang))}
            </p>
          )}
        </>
      )}
    </Cartao>
  )
}
