// Contrato do Registro de acesso (espelha `AcessosDto` do backend). Só o gerente. Nunca traz chave de task, segmento nem valor de
// filtro: a rota vem com `{key}` no lugar da chave e os filtros são só nomes. Sem contagem nem ordem por pessoa.

import { getJson } from '../../api/client'

export type AcessoDto = {
  /** Instante ISO (UTC) da consulta. */
  quando: string
  usuario: string
  rota: string
  /** Status HTTP da resposta (200, 400, 403, 404); nulo em linha antiga sem resultado. */
  resultado: number | null
  /** Só os NOMES dos filtros usados. */
  filtros: string[]
}

export type AcessosDto = { de: string; ate: string; acessos: AcessoDto[]; proximo: number | null; tamanhoDaPagina: number }

/** `GET /api/governanca/acessos` com os filtros da URL (`de`, `ate`, `usuario`, `rota`) e o cursor da página (`depoisDe`). */
export function fetchAcessos(sp: URLSearchParams, depoisDe: number | null) {
  const q = new URLSearchParams()
  for (const k of ['de', 'ate', 'usuario', 'rota']) {
    const v = sp.get(k)
    if (v) q.set(k, v)
  }
  if (depoisDe !== null) q.set('depoisDe', String(depoisDe))
  const s = q.toString()
  return getJson<AcessosDto>(s ? `/api/governanca/acessos?${s}` : '/api/governanca/acessos')
}
