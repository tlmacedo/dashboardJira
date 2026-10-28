// Contrato da Qualidade dos dados (espelha `QualidadeDto` do backend). Cartões QD-01 a QD-05. Somente leitura: o vocabulário
// (labels, status, campos) é do gestor, nos YAML. As tasks por trás de cada número vêm do drill genérico (`shared/tasksApi.ts`).

import type { Aviso } from '../../shared/useCard'

/** As verificações, agrupadas pelos cartões que as mostram (a ordem é a da tela). */
export const GRUPOS_DE_VERIFICACAO = [
  { cartao: 'QD-01', titulo: 'qualidade.grupo.abertas', verificacoes: ['SEM_ESTIMATIVA', 'SEM_RESPONSAVEL', 'SEM_VENCIMENTO', 'SEM_EPICO', 'EM_ANDAMENTO_SEM_HORAS', 'SEM_SQUAD', 'PRIORIDADE_PADRAO'] },
  { cartao: 'QD-02', titulo: 'qualidade.grupo.conclusao', verificacoes: ['DONE_SEM_RESOLUTION', 'RESOLUTION_SEM_DONE'] },
  { cartao: 'QD-03', titulo: 'qualidade.grupo.vocabulario', verificacoes: ['STATUS_SEM_MAPEAMENTO', 'LABEL_SEM_CLASSIFICACAO'] },
  { cartao: 'QD-04', titulo: 'qualidade.grupo.bloqueadas', verificacoes: ['BLOQUEADA_SEM_CAUSA'] },
  { cartao: 'QD-05', titulo: 'qualidade.grupo.campos', verificacoes: ['CAMPO_INDISPONIVEL'] },
] as const

export type LinhaQualidade = {
  verificacao: string
  n: number
  universo: number
  fracao: number | null
  /** `ok`, `atencao`, `problema`, `neutro` (só informativa) ou `indisponivel` (falta o dado para a verificação). */
  estado: 'ok' | 'atencao' | 'problema' | 'neutro' | 'indisponivel' | string
  /** Nomes de vocabulário que a linha cita (status fora do mapa, campos sem valor, labels); nunca pessoas. */
  nomes: string[]
}

export type LabelSemClasse = { label: string; n: number }

export type QualidadeDto = {
  versaoRegras: string
  janelaDias: number
  linhas: LinhaQualidade[]
  labels: LabelSemClasse[]
  avisos: Aviso[]
}
