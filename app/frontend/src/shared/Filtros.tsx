import { useState, type FormEvent, type ReactNode } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useLang } from '../i18n/LangProvider'
import './cardpage.css'

/** Campos comuns dos cards de trabalho e prazos; cada tela escolhe os que fazem sentido. */
export type CampoDeFiltro = 'periodo' | 'squad' | 'pessoa' | 'tipo' | 'prioridade' | 'os' | 'unit'

export const TIPOS_PADRAO = ['Checklist', 'Checklist TG', 'Checklist Review']
export const PRIORIDADES_PADRAO = ['Highest', 'High', 'Medium', 'Low', 'Lowest']

/** Parâmetros de URL que cada campo controla (`tipo` e `prioridade` se repetem). */
const PARAMETROS: Record<CampoDeFiltro, string[]> = {
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
  partes.push(`${t('filtros.unidade')}: ${t(sp.get('unit') === 'corrido' ? 'filtros.corrido' : 'filtros.util')}`)
  return partes.join(' | ')
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
  }
}

/** Troca só os parâmetros dos [campos] da tela; o resto da URL (granularidade, movimento...) é preservado. */
function paramsDoRascunho(base: URLSearchParams, campos: CampoDeFiltro[], r: Rascunho): URLSearchParams {
  const p = new URLSearchParams(base)
  for (const c of campos) for (const k of PARAMETROS[c]) p.delete(k)
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
  return p
}

export type FiltrosProps = {
  campos: CampoDeFiltro[]
  /** Filtros atuais (da URL). A tela usa `key={valor.toString()}` para recomeçar o rascunho quando a URL muda. */
  valor: URLSearchParams
  aoAplicar: (novo: URLSearchParams) => void
  /** Pessoas já vistas na tela (ordem alfabética), para o seletor de pessoa. */
  pessoas?: string[]
  tipos?: string[]
  prioridades?: string[]
  /** Controles específicos da tela (por exemplo, a granularidade de Metas), dentro do mesmo formulário. */
  extras?: ReactNode
}

/**
 * Barra de filtros comuns dos cards de trabalho e prazos: o rascunho só vai para a URL em "Aplicar", e a URL é a
 * fonte da verdade (link compartilhável, botão voltar). O valor digitado nunca vira HTML: só atributo de campo.
 */
export function Filtros(props: FiltrosProps) {
  const { t } = useLang()
  const { campos } = props
  const [r, setR] = useState<Rascunho>(() => rascunhoDe(props.valor))
  const tipos = props.tipos ?? TIPOS_PADRAO
  const prioridades = props.prioridades ?? PRIORIDADES_PADRAO

  function enviar(e: FormEvent) {
    e.preventDefault()
    props.aoAplicar(paramsDoRascunho(props.valor, campos, r))
  }

  function alternar(k: 'tipo' | 'prioridade', valor: string) {
    setR((a) => ({ ...a, [k]: a[k].includes(valor) ? a[k].filter((v) => v !== valor) : [...a[k], valor] }))
  }

  return (
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
          <input type="text" maxLength={100} value={r.squad} onChange={(e) => setR({ ...r, squad: e.target.value })} />
        </label>
      )}
      {campos.includes('pessoa') && (
        <label className="campo">
          <span>{t('filtros.pessoa')}</span>
          <select value={r.pessoa} onChange={(e) => setR({ ...r, pessoa: e.target.value })}>
            <option value="">{t('filtros.todas')}</option>
            {[...new Set([...(props.pessoas ?? []), ...(r.pessoa ? [r.pessoa] : [])])]
              .sort((a, b) => a.localeCompare(b))
              .map((p) => (
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
          <input type="text" maxLength={100} value={r.os} onChange={(e) => setR({ ...r, os: e.target.value })} />
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
  )
}
