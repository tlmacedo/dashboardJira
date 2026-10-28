export type Role = 'USUARIO' | 'SQUAD_LEADER' | 'COORDENADOR' | 'GERENTE'

export const ROLES: Role[] = ['USUARIO', 'SQUAD_LEADER', 'COORDENADOR', 'GERENTE']

export type GroupId =
  | 'inicio'
  | 'trabalho'
  | 'resultados'
  | 'prazos'
  | 'pessoas'
  | 'acao'
  | 'auditoria'
  | 'governanca'

export type NavItem = {
  id: string
  group: GroupId
  labelKey: string
  roles: Role[] | 'all'
  route: string
}

export type NavGroup = { id: GroupId; labelKey: string; items: NavItem[] }

/** Ordem dos grupos do menu (plano visual, seção 4). */
export const GROUPS: GroupId[] = [
  'inicio',
  'trabalho',
  'resultados',
  'prazos',
  'pessoas',
  'acao',
  'auditoria',
  'governanca',
]

const LIDERANCA: Role[] = ['SQUAD_LEADER', 'COORDENADOR', 'GERENTE']
const ALTA: Role[] = ['COORDENADOR', 'GERENTE']

function item(id: string, group: GroupId, roles: Role[] | 'all', labelKey = `nav.${id}`): NavItem {
  return { id, group, labelKey, roles, route: `/${id}` }
}

export const NAV_ITEMS: NavItem[] = [
  item('central', 'inicio', ALTA),
  item('squad', 'inicio', ['SQUAD_LEADER']),
  item('meu', 'inicio', 'all'),

  item('abertas', 'trabalho', 'all'),
  item('epicos', 'trabalho', 'all'),
  item('negligenciadas', 'trabalho', 'all'),
  item('riscos', 'trabalho', 'all'),
  item('previsao', 'trabalho', 'all'),
  item('fluxoDaSemana', 'trabalho', 'all'),

  item('fechadas', 'resultados', 'all'),
  item('negativas', 'resultados', 'all'),
  item('tempo', 'resultados', 'all'),
  item('esforco', 'resultados', 'all'),

  item('metas', 'prazos', 'all'),
  item('vencimentos', 'prazos', 'all'),
  item('checklistReview', 'prazos', 'all'),

  // Usuário vê só o próprio: carga e calendário (o filtro por pessoa é do backend).
  item('carga', 'pessoas', 'all'),
  item('calendario', 'pessoas', 'all'),
  item('capacidade', 'pessoas', LIDERANCA),
  item('plano', 'pessoas', LIDERANCA),
  item('cobertura', 'pessoas', LIDERANCA),
  item('simulador', 'pessoas', LIDERANCA),
  item('competencias', 'pessoas', LIDERANCA),
  item('revisoes', 'pessoas', LIDERANCA),
  item('posse', 'pessoas', LIDERANCA),
  item('cargaTipo', 'pessoas', LIDERANCA),
  item('tendencia', 'pessoas', LIDERANCA),
  item('gargalos', 'pessoas', LIDERANCA),

  item('distribuicao', 'acao', LIDERANCA),
  item('rebalanceamento', 'acao', LIDERANCA),

  item('auditoria', 'auditoria', ALTA),
  item('divergencia', 'auditoria', ALTA),
  item('integridade', 'auditoria', ['GERENTE']),

  // Gerado do catálogo de cartões do backend; só coordenação e gerência.
  item('mapaCartoes', 'governanca', ALTA),
  item('qualidade', 'governanca', ['GERENTE']),
  item('sincronizacao', 'governanca', ['GERENTE']),
  item('configuracao', 'governanca', ['GERENTE']),
]

function pode(it: NavItem, roles: Role[]): boolean {
  return it.roles === 'all' || roles.some((r) => (it.roles as Role[]).includes(r))
}

/** Itens visíveis para os papéis informados, agrupados; grupos vazios são omitidos. */
export function visibleNav(role: Role[]): NavGroup[] {
  return GROUPS.map((g) => ({
    id: g,
    labelKey: `grupo.${g}`,
    items: NAV_ITEMS.filter((it) => it.group === g && pode(it, role)),
  })).filter((g) => g.items.length > 0)
}

/** Início por papel (plano visual, seção 4). */
export function inicioDoPapel(role: Role): string {
  if (role === 'GERENTE' || role === 'COORDENADOR') return '/central'
  if (role === 'SQUAD_LEADER') return '/squad'
  return '/meu'
}

export function itemPorRota(route: string): NavItem | undefined {
  return NAV_ITEMS.find((i) => i.route === route)
}

export function podeVer(it: NavItem, role: Role): boolean {
  return pode(it, [role])
}
