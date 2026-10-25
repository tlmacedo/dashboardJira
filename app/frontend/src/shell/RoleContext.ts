import { createContext, useContext } from 'react'
import type { Role } from '../nav/nav'

export const RoleContext = createContext<Role>('USUARIO')

/** Papel efetivo da sessão (simulado em desenvolvimento). */
export function useRole(): Role {
  return useContext(RoleContext)
}
