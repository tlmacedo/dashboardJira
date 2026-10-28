import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { getJson } from '../api/client'

/** Aviso de dados de um card (espelha `AvisoDto` do backend); o texto sai do código, nunca da `mensagem`. */
export type Aviso = {
  codigo: string
  gravidade: 'bloqueante' | 'informativo' | string
  mensagem: string
  params?: Record<string, string>
}

/** `GET /api/cards/{id}?<filtros>`: o cálculo de um card de trabalho e prazos, com os filtros da URL. */
export function fetchCard<T>(id: string, params: URLSearchParams): Promise<T> {
  const q = params.toString()
  return getJson<T>(q ? `/api/cards/${encodeURIComponent(id)}?${q}` : `/api/cards/${encodeURIComponent(id)}`)
}

/**
 * Consulta de um card (TanStack Query): a chave leva o id e os filtros, o resultado anterior fica na tela enquanto o
 * novo carrega e erro não é repetido sozinho (403 e 400 são respostas, não falhas passageiras).
 */
export function useCard<T>(id: string, params: URLSearchParams) {
  return useQuery({
    queryKey: ['card', id, params.toString()],
    queryFn: () => fetchCard<T>(id, params),
    placeholderData: keepPreviousData,
    retry: false,
  })
}

/** Há aviso bloqueante? Os números do card ficam indisponíveis e a tela mostra "—". */
export function temBloqueio(avisos: Aviso[] | undefined): boolean {
  return (avisos ?? []).some((a) => a.gravidade === 'bloqueante')
}

/**
 * Texto de um aviso pelo código (`cardpage.aviso.<CODIGO>`); os dados do aviso entram nos marcadores `{nome}` e marcador
 * sem dado nunca aparece cru. Código sem tradução mostra "Aviso do servidor (código X)": a mensagem em português do
 * backend nunca é exibida (a tela em inglês não pode mostrar português).
 */
export function textoDoAviso(a: Aviso, t: (k: string) => string): string {
  const chave = `cardpage.aviso.${a.codigo}`
  const base = t(chave)
  if (base === chave) return t('cardpage.aviso.DESCONHECIDO').replace('{codigo}', a.codigo)
  const preenchido = Object.entries(a.params ?? {}).reduce((texto, [nome, valor]) => texto.replaceAll(`{${nome}}`, () => valor), base)
  return preenchido.replace(/\{\w+\}/g, '…')
}
