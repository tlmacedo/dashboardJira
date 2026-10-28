import { useQuery } from '@tanstack/react-query'
import { fetchOpcoesDeFiltro } from '../api/client'

/**
 * Opções dos filtros vindas do backend. Falha (rede, 403) não quebra a tela: sem opções, os filtros mostram só o que já
 * está na URL. O resultado vale por um minuto e é o mesmo para todas as telas.
 */
export function useOpcoesDeFiltro() {
  return useQuery({
    queryKey: ['filtros-opcoes'],
    queryFn: fetchOpcoesDeFiltro,
    retry: false,
    staleTime: 60_000,
  })
}
