import { useState, type FormEvent, type ReactNode } from 'react'
import { useSearchParams } from 'react-router-dom'
import type { Lang } from '../i18n'
import { useLang } from '../i18n/LangProvider'
import { useOpcoesDeFiltro } from './useOpcoesDeFiltro'
import type { FiltroRapido, OpcoesDeFiltro } from '../api/client'
import './cardpage.css'

/** Campos comuns dos cards de trabalho e prazos; cada tela escolhe os que fazem sentido. `campos` = campos do dicionário. */
export type CampoDeFiltro = 'periodo' | 'squad' | 'pessoa' | 'tipo' | 'prioridade' | 'os' | 'unit' | 'campos'

export const TIPOS_PADRAO = ['Checklist', 'Checklist TG', 'Checklist Review']
export const PRIORIDADES_PADRAO = ['Highest', 'High', 'Medium', 'Low', 'Lowest']

/** Prefixo dos parâmetros dos campos do dicionário (`campo.<slug>=<valor>`, repetível). */
const PREFIXO_DE_CAMPO = 'campo.'

/** Parâmetros de URL que cada campo controla (`tipo`, `prioridade` e `campo.*` se repetem). */
const PARAMETROS: Record<Exclude<CampoDeFiltro, 'campos'>, string[]> = {
  periodo: ['de', 'ate'],
  squad: ['squad'],
  pessoa: ['pessoa'],
  tipo: ['tipo'],
  prioridade: ['prioridade'],
  os: ['os'],
  unit: ['unit'],
}

/** Filtros da URL e a função que os troca (um clique em "Aplicar" cria uma entrada no histórico do navegador). */
export function useFiltrosDaUrl(): [URLSearchParams, (novo: URLSearchParams) => void] {
  const [sp, setSp] = useSearchParams()
  return [sp, (novo) => setSp(novo, { replace: false })]
}

/** Os filtros ativos em texto ("Squad: Alfa | Período: ..."), para o cabeçalho da gaveta e do CSV. */
export function resumoDosFiltros(
  sp: URLSearchParams,
  t: (k: string) => string,
  dataCurta: (iso: string) => string,
): string {
  const partes: string[] = []
  const de = sp.get('de')
  const ate = sp.get('ate')
  if (de || ate) partes.push(`${t('filtros.periodo')}: ${de ? dataCurta(de) : '…'} – ${ate ? dataCurta(ate) : '…'}`)
  for (const k of ['squad', 'pessoa', 'os'] as const) {
    const v = sp.get(k)
    if (v) partes.push(`${t(`filtros.${k}`)}: ${v}`)
  }
  for (const k of ['tipo', 'prioridade'] as const) {
    const v = sp.getAll(k)
    if (v.length > 0) partes.push(`${t(`filtros.${k}`)}: ${v.join(', ')}`)
  }
  for (const slug of slugsDeCampo(sp)) {
    partes.push(`${slug}: ${sp.getAll(PREFIXO_DE_CAMPO + slug).join(', ')}`)
  }
  partes.push(`${t('filtros.unidade')}: ${t(sp.get('unit') === 'corrido' ? 'filtros.corrido' : 'filtros.util')}`)
  return partes.join(' | ')
}

/** Slugs dos campos do dicionário presentes na URL (ordem de aparição, sem repetir). */
function slugsDeCampo(sp: URLSearchParams): string[] {
  return [...new Set([...sp.keys()].filter((k) => k.startsWith(PREFIXO_DE_CAMPO)).map((k) => k.slice(PREFIXO_DE_CAMPO.length)))]
}

type Rascunho = {
  de: string
  ate: string
  squad: string
  pessoa: string
  os: string
  unit: 'util' | 'corrido'
  tipo: string[]
  prioridade: string[]
  /** slug do campo do dicionário -> valores escolhidos */
  campos: Record<string, string[]>
}

function rascunhoDe(sp: URLSearchParams): Rascunho {
  return {
    de: sp.get('de') ?? '',
    ate: sp.get('ate') ?? '',
    squad: sp.get('squad') ?? '',
    pessoa: sp.get('pessoa') ?? '',
    os: sp.get('os') ?? '',
    unit: sp.get('unit') === 'corrido' ? 'corrido' : 'util',
    tipo: sp.getAll('tipo'),
    prioridade: sp.getAll('prioridade'),
    campos: Object.fromEntries(slugsDeCampo(sp).map((s) => [s, sp.getAll(PREFIXO_DE_CAMPO + s)])),
  }
}

/** Troca só os parâmetros dos [campos] da tela; o resto da URL (granularidade, movimento...) é preservado. */
function paramsDoRascunho(base: URLSearchParams, campos: CampoDeFiltro[], r: Rascunho): URLSearchParams {
  const p = new URLSearchParams(base)
  for (const c of campos) if (c !== 'campos') for (const k of PARAMETROS[c]) p.delete(k)
  if (campos.includes('campos')) for (const k of [...p.keys()]) if (k.startsWith(PREFIXO_DE_CAMPO)) p.delete(k)
  if (campos.includes('periodo')) {
    if (r.de.trim()) p.set('de', r.de.trim())
    if (r.ate.trim()) p.set('ate', r.ate.trim())
  }
  for (const k of ['squad', 'pessoa', 'os'] as const) {
    if (campos.includes(k) && r[k].trim()) p.set(k, r[k].trim())
  }
  if (campos.includes('unit') && r.unit === 'corrido') p.set('unit', 'corrido')
  for (const k of ['tipo', 'prioridade'] as const) {
    if (campos.includes(k)) r[k].forEach((v) => p.append(k, v))
  }
  if (campos.includes('campos')) {
    for (const [slug, valores] of Object.entries(r.campos)) valores.forEach((v) => p.append(PREFIXO_DE_CAMPO + slug, v))
  }
  return p
}

/** Tela que tem o filtro de cada parâmetro de URL de um filtro rápido (`null` = parâmetro que nenhuma tela entende). */
function campoDoParametro(param: string): CampoDeFiltro | null {
  if (param.startsWith(PREFIXO_DE_CAMPO)) return 'campos'
  const achado = (Object.entries(PARAMETROS) as [Exclude<CampoDeFiltro, 'campos'>, string[]][]).find(([, ps]) => ps.includes(param))
  return achado ? achado[0] : null
}

/** O filtro rápido já está aplicado (todos os valores dele estão na URL)? */
function rapidoAtivo(sp: URLSearchParams, r: FiltroRapido): boolean {
  return Object.entries(r.aplica).every(([param, valores]) => valores.every((v) => sp.getAll(param).includes(v)))
}

const mesmo = (a: string, b: string) => a.trim().toLowerCase() === b.trim().toLowerCase()

export type FiltrosProps = {
  campos: CampoDeFiltro[]
  /** Filtros atuais (da URL). A tela usa `key={valor.toString()}` para recomeçar o rascunho quando a URL muda. */
  valor: URLSearchParams
  aoAplicar: (novo: URLSearchParams) => void
  /** Pessoas já vistas na tela (ordem alfabética), somadas às que o backend oferece. */
  pessoas?: string[]
  tipos?: string[]
  prioridades?: string[]
  /** Controles específicos da tela (por exemplo, a granularidade de Metas), dentro do mesmo formulário. */
  extras?: ReactNode
}

/** Ordena as prioridades pela escala do Jira (as conhecidas primeiro) e o resto em ordem alfabética. */
function ordenarPrioridades(lista: string[]): string[] {
  const idx = (p: string) => {
    const i = PRIORIDADES_PADRAO.findIndex((x) => mesmo(x, p))
    return i < 0 ? PRIORIDADES_PADRAO.length : i
  }
  return [...lista].sort((a, b) => idx(a) - idx(b) || a.localeCompare(b))
}

/**
 * Barra de filtros comuns dos cards de trabalho e prazos. As opções de squad, pessoa, tipo, prioridade, OS e dos campos do
 * dicionário vêm do backend (`/api/filtros/opcoes`, só do escopo visível de quem pergunta): nenhum desses filtros aceita
 * texto livre. O rascunho só vai para a URL em "Aplicar" (a URL é a fonte da verdade: link compartilhável, botão voltar);
 * os chips dos filtros ativos e os filtros rápidos aplicam na hora. O valor digitado nunca vira HTML.
 */
export function Filtros(props: FiltrosProps) {
  const { t, lang } = useLang()
  const { campos } = props
  const [r, setR] = useState<Rascunho>(() => rascunhoDe(props.valor))
  const opcoes = useOpcoesDeFiltro().data

  const tipos = [...new Set([...(props.tipos ?? (opcoes?.tipos.length ? opcoes.tipos : TIPOS_PADRAO)), ...r.tipo])]
  const prioridades = ordenarPrioridades([
    ...new Set([...(props.prioridades ?? (opcoes?.prioridades.length ? opcoes.prioridades : PRIORIDADES_PADRAO)), ...r.prioridade]),
  ])
  const pessoas = [...new Set([...(opcoes?.pessoas ?? []), ...(props.pessoas ?? []), ...(r.pessoa ? [r.pessoa] : [])])].sort((a, b) => a.localeCompare(b))
  const squads = [...new Set([...(opcoes?.squads ?? []), ...(r.squad ? [r.squad] : [])])]
  const oss = [...new Set([...(opcoes?.os ?? []), ...(r.os ? [r.os] : [])])]

  function enviar(e: FormEvent) {
    e.preventDefault()
    props.aoAplicar(paramsDoRascunho(props.valor, campos, r))
  }

  function alternar(k: 'tipo' | 'prioridade', valor: string) {
    setR((a) => ({ ...a, [k]: a[k].includes(valor) ? a[k].filter((v) => v !== valor) : [...a[k], valor] }))
  }

  function alternarCampo(slug: string, valor: string) {
    setR((a) => {
      const atuais = a.campos[slug] ?? []
      return { ...a, campos: { ...a.campos, [slug]: atuais.includes(valor) ? atuais.filter((v) => v !== valor) : [...atuais, valor] } }
    })
  }

  const camposDoDicionario = (campos.includes('campos') ? (opcoes?.campos ?? []) : []).filter((c) => c.valores.length > 0 || (r.campos[c.slug]?.length ?? 0) > 0)

  return (
    <div className="filtros-bloco">
      <form className="filtros" onSubmit={enviar} aria-label={t('filtros.titulo')}>
        {campos.includes('periodo') && (
          <>
            <label className="campo campo--data">
              <span>{t('filtros.de')}</span>
              <input type="date" value={r.de} onChange={(e) => setR({ ...r, de: e.target.value })} />
            </label>
            <label className="campo campo--data">
              <span>{t('filtros.ate')}</span>
              <input type="date" value={r.ate} onChange={(e) => setR({ ...r, ate: e.target.value })} />
            </label>
          </>
        )}
        {campos.includes('squad') && (
          <label className="campo">
            <span>{t('filtros.squad')}</span>
            <select value={r.squad} onChange={(e) => setR({ ...r, squad: e.target.value })}>
              <option value="">{t('filtros.todas')}</option>
              {squads.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </label>
        )}
        {campos.includes('pessoa') && (
          <label className="campo">
            <span>{t('filtros.pessoa')}</span>
            <select value={r.pessoa} onChange={(e) => setR({ ...r, pessoa: e.target.value })}>
              <option value="">{t('filtros.todas')}</option>
              {pessoas.map((p) => (
                <option key={p} value={p}>
                  {p}
                </option>
              ))}
            </select>
          </label>
        )}
        {campos.includes('os') && (
          <label className="campo">
            <span>{t('filtros.os')}</span>
            <select value={r.os} onChange={(e) => setR({ ...r, os: e.target.value })}>
              <option value="">{t('filtros.todas')}</option>
              {oss.map((o) => (
                <option key={o} value={o}>
                  {o}
                </option>
              ))}
            </select>
          </label>
        )}
        {campos.includes('tipo') && (
          <fieldset className="campo-grupo">
            <legend>{t('filtros.tipo')}</legend>
            {tipos.map((v) => (
              <label key={v} className="campo--check">
                <input type="checkbox" checked={r.tipo.includes(v)} onChange={() => alternar('tipo', v)} /> {v}
              </label>
            ))}
          </fieldset>
        )}
        {campos.includes('prioridade') && (
          <fieldset className="campo-grupo">
            <legend>{t('filtros.prioridade')}</legend>
            {prioridades.map((v) => (
              <label key={v} className="campo--check">
                <input type="checkbox" checked={r.prioridade.includes(v)} onChange={() => alternar('prioridade', v)} /> {v}
              </label>
            ))}
          </fieldset>
        )}
        {camposDoDicionario.map((c) => {
          const escolhidos = r.campos[c.slug] ?? []
          const valores = [...new Set([...c.valores, ...escolhidos])]
          return (
            <fieldset key={c.slug} className="campo-grupo campo-grupo--rolagem">
              <legend>{c.nome}</legend>
              {valores.map((v) => (
                <label key={v} className="campo--check">
                  <input type="checkbox" checked={escolhidos.some((x) => mesmo(x, v))} onChange={() => alternarCampo(c.slug, v)} /> {v}
                </label>
              ))}
            </fieldset>
          )
        })}
        {campos.includes('unit') && (
          <div role="group" aria-label={t('filtros.unidade')} className="seg seg--claro">
            {(['util', 'corrido'] as const).map((u) => (
              <button key={u} type="button" aria-pressed={r.unit === u} onClick={() => setR({ ...r, unit: u })}>
                {t(`filtros.${u}`)}
              </button>
            ))}
          </div>
        )}
        {props.extras}
        <button type="submit" className="btn btn--primario">
          {t('filtros.aplicar')}
        </button>
      </form>
      <FiltrosRapidos campos={campos} valor={props.valor} rapidos={opcoes?.rapidos ?? []} aoAplicar={props.aoAplicar} lang={lang} />
      <ChipsDosFiltros campos={campos} valor={props.valor} opcoes={opcoes} aoAplicar={props.aoAplicar} />
    </div>
  )
}

/** Botões dos filtros rápidos nomeados (I-27): só os que esta tela consegue aplicar por inteiro. Clicar liga; clicar de novo desliga. */
function FiltrosRapidos(props: {
  campos: CampoDeFiltro[]
  valor: URLSearchParams
  rapidos: FiltroRapido[]
  aoAplicar: (novo: URLSearchParams) => void
  lang: Lang
}) {
  const { t } = useLang()
  const aplicaveis = props.rapidos.filter((r) =>
    Object.keys(r.aplica).every((param) => {
      const c = campoDoParametro(param)
      return c !== null && props.campos.includes(c)
    }),
  )
  if (aplicaveis.length === 0) return null

  function alternar(r: FiltroRapido) {
    const p = new URLSearchParams(props.valor)
    const ativo = rapidoAtivo(props.valor, r)
    for (const [param, valores] of Object.entries(r.aplica)) {
      if (ativo) {
        const restantes = p.getAll(param).filter((v) => !valores.includes(v))
        p.delete(param)
        restantes.forEach((v) => p.append(param, v))
      } else {
        p.delete(param)
        valores.forEach((v) => p.append(param, v))
      }
    }
    props.aoAplicar(p)
  }

  return (
    <div role="group" aria-label={t('filtros.rapidos')} className="filtros-rapidos">
      {aplicaveis.map((r) => (
        <button key={r.id} type="button" className="btn" aria-pressed={rapidoAtivo(props.valor, r)} onClick={() => alternar(r)}>
          {props.lang === 'pt' ? r.rotuloPt : r.rotuloEn}
        </button>
      ))}
    </div>
  )
}

type Chip = { chave: string; valor: string; rotulo: string; fora: boolean }

/** Chips dos filtros ativos da URL, cada um com botão acessível de remover (Tab até o chip, Enter remove). */
function ChipsDosFiltros(props: {
  campos: CampoDeFiltro[]
  valor: URLSearchParams
  opcoes: OpcoesDeFiltro | undefined
  aoAplicar: (novo: URLSearchParams) => void
}) {
  const { t } = useLang()
  const { valor, opcoes, campos } = props
  const dentro = (lista: string[] | undefined, v: string) => !opcoes || !lista || lista.some((x) => mesmo(x, v))
  const chips: Chip[] = []
  if (campos.includes('squad') && valor.get('squad')) chips.push({ chave: 'squad', valor: valor.get('squad')!, rotulo: t('filtros.squad'), fora: !dentro(opcoes?.squads, valor.get('squad')!) })
  if (campos.includes('pessoa') && valor.get('pessoa')) chips.push({ chave: 'pessoa', valor: valor.get('pessoa')!, rotulo: t('filtros.pessoa'), fora: !dentro(opcoes?.pessoas, valor.get('pessoa')!) })
  if (campos.includes('os') && valor.get('os')) chips.push({ chave: 'os', valor: valor.get('os')!, rotulo: t('filtros.os'), fora: !dentro(opcoes?.os, valor.get('os')!) })
  if (campos.includes('tipo')) for (const v of valor.getAll('tipo')) chips.push({ chave: 'tipo', valor: v, rotulo: t('filtros.tipo'), fora: !dentro(opcoes?.tipos, v) })
  if (campos.includes('prioridade')) for (const v of valor.getAll('prioridade')) chips.push({ chave: 'prioridade', valor: v, rotulo: t('filtros.prioridade'), fora: !dentro(opcoes?.prioridades, v) })
  if (campos.includes('campos')) {
    for (const slug of slugsDeCampo(valor)) {
      const campo = opcoes?.campos.find((c) => c.slug === slug)
      for (const v of valor.getAll(PREFIXO_DE_CAMPO + slug)) {
        chips.push({ chave: PREFIXO_DE_CAMPO + slug, valor: v, rotulo: campo?.nome ?? slug, fora: !dentro(campo?.valores, v) })
      }
    }
  }
  if (chips.length === 0) return null

  function remover(c: Chip) {
    const p = new URLSearchParams(valor)
    const restantes = p.getAll(c.chave).filter((v) => v !== c.valor)
    p.delete(c.chave)
    restantes.forEach((v) => p.append(c.chave, v))
    props.aoAplicar(p)
  }

  return (
    <ul className="chips" aria-label={t('filtros.ativos')}>
      {chips.map((c) => (
        <li key={`${c.chave}:${c.valor}`}>
          <button type="button" className="chip-botao" aria-label={`${t('filtros.remover')} ${c.rotulo}: ${c.valor}${c.fora ? `, ${t('filtros.foraDasOpcoes')}` : ''}`} onClick={() => remover(c)}>
            <span>
              {c.rotulo}: {c.valor}
            </span>
            {c.fora && <span className="chip-aviso"> ▲ {t('filtros.foraDasOpcoes')}</span>}
            <span aria-hidden="true"> ×</span>
          </button>
        </li>
      ))}
    </ul>
  )
}
