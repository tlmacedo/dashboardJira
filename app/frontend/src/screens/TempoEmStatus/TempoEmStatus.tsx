import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useMemo, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ErroDaApi } from '../../api/client'
import { useLang } from '../../i18n/LangProvider'
import { Cartao } from '../../shared/Cartao'
import { baixarCsv } from '../../shared/csv'
import { GavetaDeTasks } from '../../shared/GavetaDeTasks'
import {
  fetchTempoEmStatus,
  paramsDe,
  type Aviso,
  type Categoria,
  type Filtros,
  type PersonStatus,
  type TempoEmStatusDto,
  type Unidade,
} from './api'
import {
  dataCurta,
  faixaDe,
  inteiro,
  linhaFiltrosCsv,
  numero,
  percentual,
  resumoDosFiltros,
  SIMBOLO,
  type Faixa,
} from './format'
import '../../shared/tabela.css'
import './tempo.css'

const MAX_PARADAS = 50
const CAMPOS_TEXTO = ['squad', 'pessoa', 'tipo', 'os'] as const
type CampoTexto = (typeof CAMPOS_TEXTO)[number]

/** Lê os filtros da URL; valores desconhecidos caem no padrão (o backend valida de novo). */
function filtrosDaUrl(sp: URLSearchParams): Filtros {
  return {
    squad: sp.get('squad') ?? '',
    pessoa: sp.get('pessoa') ?? '',
    tipo: sp.get('tipo') ?? '',
    os: sp.get('os') ?? '',
    de: sp.get('de') ?? '',
    ate: sp.get('ate') ?? '',
    unit: sp.get('unit') === 'corrido' ? 'corrido' : 'util',
    semOutliers: sp.get('semOutliers') === 'true',
  }
}

function paraUrl(f: Filtros): URLSearchParams {
  const p = new URLSearchParams()
  for (const k of ['squad', 'pessoa', 'tipo', 'os', 'de', 'ate'] as const) {
    if (f[k].trim()) p.set(k, f[k].trim())
  }
  if (f.unit === 'corrido') p.set('unit', 'corrido')
  if (f.semOutliers) p.set('semOutliers', 'true')
  return p
}

/** Recorte aberto na gaveta: o cartão que o conta e o segmento (ver `CartaoRegistrado.drill` no backend). */
type Gaveta = { cartaoId: string; segmento: string; titulo: string }

/** Ids dos títulos dos cartões de seção: nomeiam as tabelas e regiões (leitor de tela). */
const ID_TITULO_MATRIZ = 'h-matriz'

export function TempoEmStatus() {
  const { t, lang } = useLang()
  const [sp, setSp] = useSearchParams()
  const filtros = useMemo(() => filtrosDaUrl(sp), [sp])
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)

  const consulta = useQuery({
    queryKey: ['tempo-em-status', filtros],
    queryFn: () => fetchTempoEmStatus(filtros),
    placeholderData: keepPreviousData,
    retry: false,
  })
  const dto = consulta.data
  const status = consulta.error instanceof ErroDaApi ? consulta.error.status : null
  const unidadeTxt = t(filtros.unit === 'util' ? 'tempo.unidade.util' : 'tempo.unidade.corrido').toLowerCase()

  function aplicar(novo: Filtros) {
    setSp(paraUrl(novo), { replace: false })
  }

  const chips = [
    ...CAMPOS_TEXTO.filter((k) => filtros[k]).map((k) => ({
      id: k,
      texto: `${t(`tempo.filtro.${k}`)}: ${filtros[k]}`,
      limpar: () => aplicar({ ...filtros, [k]: '' }),
    })),
    ...(filtros.de || filtros.ate
      ? [
          {
            id: 'periodo',
            texto: `${t('tempo.filtro.periodo')}: ${filtros.de ? dataCurta(filtros.de, lang) : '…'} – ${filtros.ate ? dataCurta(filtros.ate, lang) : '…'}`,
            limpar: () => aplicar({ ...filtros, de: '', ate: '' }),
          },
        ]
      : []),
    ...(filtros.semOutliers
      ? [{ id: 'semOutliers', texto: t('tempo.filtro.semOutliers'), limpar: () => aplicar({ ...filtros, semOutliers: false }) }]
      : []),
  ]

  function exportar() {
    if (!dto) return
    baixarCsv('tempo-em-status.csv', [
      [`# ${t('nav.tempo')} (${unidadeTxt})`],
      [linhaFiltrosCsv(filtros, t, lang)],
      [],
      [t('tempo.csv.indicador'), t('tempo.csv.valor')],
      [t('tempo.kpi.analisadas'), String(dto.analisadas)],
      [t('tempo.kpi.eficiencia'), dto.eficienciaFluxo == null ? '' : percentual(dto.eficienciaFluxo, lang)],
      [t('tempo.kpi.paradas'), String(dto.paradas)],
      [],
      [t('tempo.col.status'), t('tempo.col.mediana'), 'P85', t('tempo.col.n')],
      ...dto.porStatus.map((s) => [s.status, numero(s.median, lang), numero(s.p85, lang), String(s.n)]),
      [],
      [t('tempo.col.pessoa'), t('tempo.col.status'), t('tempo.col.mediana'), t('tempo.col.n')],
      ...[...dto.pessoaStatus]
        .sort((a, b) => a.person.localeCompare(b.person) || a.status.localeCompare(b.status))
        .map((p) => [p.person, p.status, numero(p.median, lang), String(p.n)]),
      [],
      [t('tempo.col.task'), t('tempo.col.resumo'), t('tempo.col.responsavel'), t('tempo.col.status'), t('tempo.col.dias'), t('tempo.col.mediana')],
      ...dto.abertasParadas.map((s) => [s.key, s.summary, s.assignee ?? '', s.status, numero(s.daysInStatus, lang), numero(s.median, lang)]),
    ])
  }

  return (
    <div className="tempo">
      <div className="top">
        <div>
          <h1>{t('nav.tempo')}</h1>
          <p className="sub">{t('tempo.sub')}</p>
        </div>
        <div className="top-acoes">
          <div role="group" aria-label={t('tempo.unidade')} className="seg seg--claro">
            {(['util', 'corrido'] as Unidade[]).map((u) => (
              <button
                key={u}
                type="button"
                aria-pressed={filtros.unit === u}
                onClick={() => aplicar({ ...filtros, unit: u })}
              >
                {t(`tempo.unidade.${u}`)}
              </button>
            ))}
          </div>
          <button type="button" className="btn" onClick={exportar} disabled={!dto || temBloqueio(dto)}>
            {t('tempo.csv')}
          </button>
        </div>
      </div>

      <div className="barra">
        <FormFiltros key={paraUrl({ ...filtros, unit: 'util' }).toString()} filtros={filtros} aoAplicar={aplicar} />
      </div>

      {chips.length > 0 && (
        <ul className="chips" aria-label={t('tempo.filtrosAtivos')}>
          {chips.map((c) => (
            <li key={c.id} className="chip">
              <span>{c.texto}</span>
              <button type="button" aria-label={`${t('tempo.removerFiltro')}: ${c.texto}`} onClick={c.limpar}>
                ×
              </button>
            </li>
          ))}
        </ul>
      )}

      {consulta.isPending && <Esqueleto rotulo={t('app.carregando')} />}

      {consulta.isError && !dto && (
        <div className="estado estado--erro" role="alert">
          {status === 403 && <p>{t('tempo.semPermissao')}</p>}
          {status === 400 && <p>{t('tempo.filtroInvalido')}</p>}
          {status !== 403 && status !== 400 && (
            <p>
              {t('tempo.erro')}
              {status ? ` (HTTP ${status})` : ''}
            </p>
          )}
          {status !== 403 && (
            <button type="button" className="btn" onClick={() => consulta.refetch()}>
              {t('tempo.tentarDeNovo')}
            </button>
          )}
        </div>
      )}

      {dto && (
        <div className={consulta.isFetching ? 'conteudo conteudo--atualizando' : 'conteudo'} aria-busy={consulta.isFetching}>
          {dto.avisos.length > 0 && (
            <div className="aviso" role="status">
              <strong>{temBloqueio(dto) ? t('tempo.avisos') : t('tempo.avisos.informativo')}</strong>
              <ul>
                {dto.avisos.map((a) => (
                  <li key={`${a.codigo}:${a.mensagem}`}>{textoDoAviso(a, t)}</li>
                ))}
              </ul>
              {temBloqueio(dto) && <p>{t('tempo.avisos.ajuda')}</p>}
            </div>
          )}
          <Kpis dto={dto} aoAbrir={setGaveta} />
          {!temBloqueio(dto) && dto.analisadas === 0 && <p className="vazio">{t('tempo.vazio')}</p>}
          {dto.analisadas > 0 && !temBloqueio(dto) && (
            <>
              <PorStatus dto={dto} unidadeTxt={unidadeTxt} aoAbrir={setGaveta} />
              <Matriz dto={dto} aoAbrir={setGaveta} />
              <Paradas dto={dto} unidadeTxt={unidadeTxt} />
            </>
          )}
        </div>
      )}

      {gaveta && (
        <GavetaDeTasks
          cartaoId={gaveta.cartaoId}
          segmento={gaveta.segmento}
          titulo={gaveta.titulo}
          filtros={paramsDe(filtros)}
          resumoDosFiltros={resumoDosFiltros(filtros, t, lang)}
          aoFechar={() => setGaveta(null)}
        />
      )}
    </div>
  )
}

function FormFiltros(props: { filtros: Filtros; aoAplicar: (f: Filtros) => void }) {
  const { t } = useLang()
  const [rascunho, setRascunho] = useState<Filtros>(props.filtros)

  function enviar(e: FormEvent) {
    e.preventDefault()
    props.aoAplicar({ ...rascunho, unit: props.filtros.unit })
  }

  return (
    <form className="filtros" onSubmit={enviar} aria-label={t('tempo.filtros')}>
      {CAMPOS_TEXTO.map((k: CampoTexto) => (
        <label key={k} className="campo">
          <span>{t(`tempo.filtro.${k}`)}</span>
          <input
            type="text"
            maxLength={100}
            value={rascunho[k]}
            onChange={(e) => setRascunho({ ...rascunho, [k]: e.target.value })}
          />
        </label>
      ))}
      <label className="campo campo--data">
        <span>{t('tempo.filtro.de')}</span>
        <input type="date" value={rascunho.de} onChange={(e) => setRascunho({ ...rascunho, de: e.target.value })} />
      </label>
      <label className="campo campo--data">
        <span>{t('tempo.filtro.ate')}</span>
        <input type="date" value={rascunho.ate} onChange={(e) => setRascunho({ ...rascunho, ate: e.target.value })} />
      </label>
      <label className="campo campo--check">
        <input
          type="checkbox"
          checked={rascunho.semOutliers}
          onChange={(e) => setRascunho({ ...rascunho, semOutliers: e.target.checked })}
        />
        <span>{t('tempo.filtro.semOutliers')}</span>
      </label>
      <button type="submit" className="btn btn--primario">
        {t('tempo.aplicar')}
      </button>
    </form>
  )
}

function Kpis(props: { dto: TempoEmStatusDto; aoAbrir: (g: Gaveta) => void }) {
  const { t, lang } = useLang()
  const { dto, aoAbrir } = props
  const indisponivel = temBloqueio(dto)
  const dash = '—'
  const analisadas = indisponivel ? dash : inteiro(dto.analisadas, lang)
  const eficiencia = indisponivel || dto.eficienciaFluxo == null ? dash : percentual(dto.eficienciaFluxo, lang)
  const paradas = indisponivel ? dash : inteiro(dto.paradas, lang)

  const temAnalisadas = !indisponivel && dto.analisadas > 0
  const temParadas = !indisponivel && dto.paradas > 0

  return (
    <div role="group" aria-label={t('tempo.indicadores')} className="kpis">
      <Cartao
        id="TS-01"
        titulo={t('tempo.kpi.analisadas')}
        valor={analisadas}
        legenda={t('tempo.kpi.analisadas.dica')}
        aoAbrir={temAnalisadas ? () => aoAbrir({ cartaoId: 'TS-01', segmento: 'analisadas', titulo: t('tempo.kpi.analisadas') }) : undefined}
      />
      {/* Percentual não tem tasks próprias: abre as analisadas, que são o numerador e o denominador. */}
      <Cartao
        id="TS-02"
        titulo={t('tempo.kpi.eficiencia')}
        valor={eficiencia}
        legenda={t('tempo.kpi.eficiencia.dica')}
        aoAbrir={temAnalisadas ? () => aoAbrir({ cartaoId: 'TS-02', segmento: 'analisadas', titulo: t('tempo.kpi.analisadas') }) : undefined}
      />
      <Cartao
        id="TS-03"
        titulo={t('tempo.kpi.paradas')}
        valor={paradas}
        simboloDoValor={temParadas ? '⏸' : undefined}
        legenda={t('tempo.kpi.paradas.dica')}
        aoAbrir={temParadas ? () => aoAbrir({ cartaoId: 'TS-03', segmento: 'paradas', titulo: t('tempo.kpi.paradas') }) : undefined}
      />
    </div>
  )
}

const CATEGORIA: Record<Categoria, { simbolo: string; chave: string }> = {
  TODO: { simbolo: '○', chave: 'tempo.cat.TODO' },
  ACTIVE: { simbolo: '◆', chave: 'tempo.cat.ACTIVE' },
  WAIT: { simbolo: '●', chave: 'tempo.cat.WAIT' },
  BLOCKED: { simbolo: '▲', chave: 'tempo.cat.BLOCKED' },
  DONE: { simbolo: '✓', chave: 'tempo.cat.DONE' },
}

function PorStatus(props: { dto: TempoEmStatusDto; unidadeTxt: string; aoAbrir: (g: Gaveta) => void }) {
  const { t, lang } = useLang()
  const { dto } = props
  if (dto.porStatus.length === 0) return null
  const maximo = Math.max(...dto.porStatus.map((s) => s.p85), 0.001)
  return (
    <Cartao id="TS-04" variante="serie" nivelDoTitulo={2} titulo={t('tempo.porStatus')}>
      <p className="h-nota">({props.unidadeTxt})</p>
      <ul className="barras">
        {dto.porStatus.map((s) => {
          const cat = CATEGORIA[s.category] ?? CATEGORIA.TODO
          return (
            <li key={s.status}>
              <button
                type="button"
                className={`barra-linha cat-${s.category}`}
                onClick={() =>
                  props.aoAbrir({ cartaoId: 'TS-04', segmento: `status:${s.status}`, titulo: `${t('tempo.col.status')}: ${s.status}` })
                }
              >
                <span className="barra-nome">{s.status}</span>
                <span className="barra-cat">
                  <span aria-hidden="true">{cat.simbolo}</span> {t(cat.chave)}
                </span>
                <span className="barra-trilho" aria-hidden="true">
                  <span className="barra-mediana" style={{ width: `${(s.median / maximo) * 100}%` }} />
                  <span className="barra-p85" style={{ left: `min(${(s.p85 / maximo) * 100}%, calc(100% - 2px))` }} />
                </span>
                <span className="barra-valores">
                  <span>{numero(s.median, lang)}</span>
                  <span className="barra-sep"> / </span>
                  <span>P85 {numero(s.p85, lang)}</span>
                </span>
                <span className="barra-n">n={inteiro(s.n, lang)}</span>
              </button>
            </li>
          )
        })}
      </ul>
      <p className="legenda">{t('tempo.porStatus.legenda')}</p>
    </Cartao>
  )
}

function Matriz(props: { dto: TempoEmStatusDto; aoAbrir: (g: Gaveta) => void }) {
  const { t, lang } = useLang()
  const { dto } = props
  const { pessoas, statuses, celulas } = useMemo(() => {
    const ordemStatus = dto.porStatus.map((s) => s.status)
    const extras = dto.pessoaStatus.map((p) => p.status).filter((s) => !ordemStatus.includes(s))
    const celulas = new Map<string, PersonStatus>()
    for (const p of dto.pessoaStatus) celulas.set(`${p.person}\u0000${p.status}`, p)
    // Ordem alfabética: sem ranking de pessoas.
    const pessoas = [...new Set(dto.pessoaStatus.map((p) => p.person))].sort((a, b) => a.localeCompare(b))
    return { pessoas, statuses: [...new Set([...ordemStatus, ...extras])], celulas }
  }, [dto])
  if (pessoas.length === 0) return null

  const faixaTxt: Record<Faixa, string> = { ok: t('tempo.faixa.ok'), atencao: t('tempo.faixa.atencao'), alto: t('tempo.faixa.alto') }

  return (
    <Cartao id="TS-06" variante="tabela" nivelDoTitulo={2} idDoTitulo={ID_TITULO_MATRIZ} titulo={t('tempo.matriz')}>
      <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby={ID_TITULO_MATRIZ}>
        <table className="tabela tabela--matriz" aria-labelledby={ID_TITULO_MATRIZ}>
          <thead>
            <tr>
              <th scope="col">{t('tempo.col.pessoa')}</th>
              {statuses.map((s) => (
                <th scope="col" key={s} className="num">
                  {s}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {pessoas.map((p) => (
              <tr key={p}>
                <th scope="row">{p}</th>
                {statuses.map((s) => {
                  const c = celulas.get(`${p}\u0000${s}`)
                  if (!c) return <td key={s} className="num celula-vazia">—</td>
                  const faixa = faixaDe(c.median)
                  return (
                    <td key={s} className="num">
                      <button
                        type="button"
                        className={`celula celula--${faixa}`}
                        aria-label={`${p}, ${s}: ${numero(c.median, lang)} ${t('tempo.dias')}, ${faixaTxt[faixa]}, n=${c.n}`}
                        onClick={() =>
                          props.aoAbrir({ cartaoId: 'TS-06', segmento: `pessoa:${p}:status:${s}`, titulo: `${p} · ${s}` })
                        }
                      >
                        {`${SIMBOLO[faixa]} ${numero(c.median, lang)}`}
                      </button>
                    </td>
                  )
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="legenda">
        <span>✓ {t('tempo.faixa.ok')}</span> · <span>● {t('tempo.faixa.atencao')}</span> · <span>▲ {t('tempo.faixa.alto')}</span>
        {' — '}
        {t('tempo.matriz.legenda')}
      </p>
    </Cartao>
  )
}

function Paradas(props: { dto: TempoEmStatusDto; unidadeTxt: string }) {
  const { t, lang } = useLang()
  const { abertasParadas } = props.dto
  const mostradas = abertasParadas.slice(0, MAX_PARADAS)
  return (
    <Cartao id="TS-07" variante="lista" nivelDoTitulo={2} idDoTitulo="h-paradas" titulo={t('tempo.paradas')}>
      {abertasParadas.length === 0 ? (
        <p className="vazio">{t('tempo.paradas.vazio')}</p>
      ) : (
        <>
          <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-paradas">
            <table className="tabela">
              <thead>
                <tr>
                  <th scope="col">{t('tempo.col.task')}</th>
                  <th scope="col">{t('tempo.col.resumo')}</th>
                  <th scope="col">{t('tempo.col.responsavel')}</th>
                  <th scope="col">{t('tempo.col.status')}</th>
                  <th scope="col" className="num">{t('tempo.col.dias')}</th>
                  <th scope="col" className="num">{t('tempo.col.mediana')}</th>
                </tr>
              </thead>
              <tbody>
                {mostradas.map((s) => (
                  <tr key={s.key}>
                    <th scope="row" className="mono">{s.key}</th>
                    <td>{s.summary}</td>
                    <td>{s.assignee ?? '—'}</td>
                    <td>{s.status}</td>
                    <td className="num">
                      <span aria-hidden="true">▲ </span>
                      {numero(s.daysInStatus, lang)}
                    </td>
                    <td className="num">{numero(s.median, lang)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="legenda">
            {abertasParadas.length > MAX_PARADAS
              ? t('tempo.paradas.limite').replace('{n}', String(MAX_PARADAS)).replace('{total}', inteiro(abertasParadas.length, lang))
              : t('tempo.paradas.legenda').replace('{unidade}', props.unidadeTxt)}
          </p>
        </>
      )}
    </Cartao>
  )
}

function Esqueleto({ rotulo }: { rotulo: string }) {
  return (
    <div className="esqueleto" aria-busy="true">
      <p className="so-leitor" role="status">{rotulo}</p>
      <div className="esq-kpis">
        <div className="esq" />
        <div className="esq" />
        <div className="esq" />
      </div>
      <div className="esq esq--bloco" />
      <div className="esq esq--bloco" />
    </div>
  )
}

/**
 * Texto do aviso traduzido pelo código. Os dados do aviso (nome do status, contagem) vêm em `params` e
 * entram nos marcadores `{nome}` do texto; a mensagem do backend só serve de último recurso para um código
 * desconhecido.
 */
function textoDoAviso(a: Aviso, t: (k: string) => string): string {
  const chave = `tempo.aviso.${a.codigo}`
  const base = t(chave)
  if (base === chave) return a.mensagem
  const preenchido = Object.entries(a.params ?? {}).reduce((texto, [nome, valor]) => texto.replaceAll(`{${nome}}`, () => valor), base)
  // Marcador sem parâmetro correspondente nunca aparece cru na tela.
  return preenchido.replace(/\{\w+\}/g, '…')
}

/** Só aviso bloqueante esconde os números; os informativos aparecem como faixa. */
function temBloqueio(dto: TempoEmStatusDto): boolean {
  return dto.avisos.some((a) => a.gravidade === 'bloqueante')
}
