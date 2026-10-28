import { useQuery } from '@tanstack/react-query'
import { useMemo } from 'react'
import { getJson } from '../api/client'

/** Um cartão do catálogo (espelha `CartaoCatalogoDto`): só ids e CHAVES de tradução, nunca texto livre. */
export type CartaoCatalogo = {
  id: string
  grupo: string
  natureza: 'ESTOQUE' | 'FLUXO' | 'PRAZO' | 'PESSOAS' | 'QUALIDADE'
  tela: string
  tituloChave: string
  oQueMostraChave: string
  comoCalculaChave: string
  fontes: string[]
  papeis: string[]
  temDrill: boolean
  exigeChangelog: boolean
  exigeWorklog: boolean
}

export type Catalogo = { cartoes: CartaoCatalogo[] }

export function fetchCatalogo(): Promise<Catalogo> {
  return getJson<Catalogo>('/api/cartoes/catalogo')
}

/** O catálogo muda só com uma nova versão do painel: fica em cache por bastante tempo. */
export const TEMPO_DE_CACHE_DO_CATALOGO = 30 * 60 * 1000

/**
 * Catálogo de cartões do papel de quem está logado (TanStack Query, cache longo). Entrega o `meta` de um cartão por
 * id; enquanto carrega, ou se a consulta falhar, `meta(id)` é `undefined` e quem usa continua funcionando sem a ajuda.
 */
export function useCatalogo(ativo = true) {
  const consulta = useQuery({
    queryKey: ['catalogo'],
    queryFn: fetchCatalogo,
    staleTime: TEMPO_DE_CACHE_DO_CATALOGO,
    retry: false,
    enabled: ativo,
  })
  const porId = useMemo(() => new Map((consulta.data?.cartoes ?? []).map((c) => [c.id, c])), [consulta.data])
  return { consulta, cartoes: consulta.data?.cartoes, meta: (id: string): CartaoCatalogo | undefined => porId.get(id) }
}
