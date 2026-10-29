// Contrato de "Meus dados" (espelha `MeusDadosDto` do backend, `GET /api/titular/meus-dados`). Só os dados do PRÓPRIO titular:
// contagens, papéis, categorias tratadas, retenção e os acessos dele. Nenhum parâmetro escolhe outra pessoa.

import { getJson } from '../../api/client'

export type PapelVigente = { papel: string; squad: string | null; de: string | null; ate: string | null }
export type AcessoProprio = { rota: string; at: string }
export type CategoriaTratada = { categoria: string; finalidade: string; quemVe: string[]; retencaoVariavel: string }

export type MeusDadosDto = {
  pessoa: string
  geradoEm: string
  papeis: PapelVigente[]
  issuesComoResponsavel: number
  issuesComoReporter: number
  alteracoesComoAutor: number
  worklogs: number
  horasRegistradas: number
  ausenciasPlanejadas: number
  decisoesEnvolvendo: number
  acessos: AcessoProprio[]
  categorias: CategoriaTratada[]
  /** Meses de cada categoria: `historico`, `accessLog`, `decisoes`, `execucoes`. */
  retencao: Record<string, number>
  aviso: string
}

/** Sem parâmetro de query: o backend recusa qualquer um (400) e a pessoa é sempre a da sessão. */
export const fetchMeusDados = () => getJson<MeusDadosDto>('/api/titular/meus-dados')
