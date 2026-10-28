// Cliente da API do próprio painel (Ktor). O frontend nunca fala com o Jira.

export type Meta = {
  devMode: boolean
  liveEnabled: boolean
  zone: string
  banner: string | null
}

export type Me = { userId: string; roles: string[] }

/** Cabeçalho que a proteção CSRF do backend exige em todo método que altera estado. */
export const CABECALHO_CSRF = 'X-Painel-CSRF'

/**
 * Erro de uma resposta HTTP da API. `codigo` e `params` vêm do `ErroDto` do backend quando o corpo é JSON
 * (a tela monta o texto pelo código); respostas sem corpo (400, 5xx) ficam com `codigo` nulo.
 */
export class ErroDaApi extends Error {
  readonly status: number
  readonly codigo: string | null
  readonly params: Record<string, string>
  constructor(status: number, codigo: string | null = null, params: Record<string, string> = {}) {
    super(codigo ? `HTTP ${status} (${codigo})` : `HTTP ${status}`)
    this.status = status
    this.codigo = codigo
    this.params = params
  }
}

/** Lê o `ErroDto` do corpo, se houver; qualquer outra coisa vira erro sem código. */
async function erroDaResposta(resposta: Response): Promise<ErroDaApi> {
  try {
    const corpo = (await resposta.json()) as { codigo?: unknown; params?: unknown }
    if (typeof corpo?.codigo === 'string') {
      const params =
        corpo.params && typeof corpo.params === 'object' ? (corpo.params as Record<string, string>) : {}
      return new ErroDaApi(resposta.status, corpo.codigo, params)
    }
  } catch {
    // corpo vazio ou que não é JSON
  }
  return new ErroDaApi(resposta.status)
}

/** Evento global disparado em todo 401: a sessão acabou (o diálogo `SessaoExpirada` escuta). 403 NÃO dispara. */
export const EVENTO_SESSAO_EXPIRADA = 'painel:sessao-expirada'

/** 401 avisa a tela inteira; qualquer outro status é tratado só por quem chamou. */
export function avisarSeSessaoExpirou(status: number): void {
  if (status === 401) window.dispatchEvent(new Event(EVENTO_SESSAO_EXPIRADA))
}

/**
 * Caminho para voltar depois do login. Só aceita caminho interno do próprio site: começa com "/", não com "//",
 * sem barra invertida nem caractere de controle (o navegador os trata como "//"), sem esquema. Senão `null`.
 */
export function caminhoDeVolta(atual: string): string | null {
  if (!atual.startsWith('/') || atual.startsWith('//')) return null
  // eslint-disable-next-line no-control-regex
  if (/[\\\u0000-\u001f\u007f]/.test(atual)) return null
  return atual
}

/** `/auth/login`, com `?volta=` quando há caminho interno para voltar. */
export function urlDeLogin(volta: string | null): string {
  return volta ? `/auth/login?volta=${encodeURIComponent(volta)}` : '/auth/login'
}

/** GET de JSON do próprio painel. Erro vira [ErroDaApi]; 401 avisa a tela (sessão expirada). */
export async function getJson<T>(url: string): Promise<T> {
  const resposta = await fetch(url, { headers: { Accept: 'application/json' }, credentials: 'same-origin' })
  if (!resposta.ok) {
    avisarSeSessaoExpirou(resposta.status)
    throw await erroDaResposta(resposta)
  }
  return (await resposta.json()) as T
}

/**
 * Envia um método que altera estado (POST, PUT, PATCH, DELETE) com o cabeçalho anti-CSRF.
 * Resposta sem conteúdo (204) devolve `undefined`.
 */
export async function enviarJson<T>(
  metodo: 'POST' | 'PUT' | 'PATCH' | 'DELETE',
  url: string,
  corpo?: unknown,
): Promise<T> {
  const resposta = await fetch(url, {
    method: metodo,
    credentials: 'same-origin',
    headers: { Accept: 'application/json', 'Content-Type': 'application/json', [CABECALHO_CSRF]: '1' },
    body: corpo === undefined ? undefined : JSON.stringify(corpo),
  })
  if (!resposta.ok) {
    avisarSeSessaoExpirou(resposta.status)
    throw await erroDaResposta(resposta)
  }
  if (resposta.status === 204) return undefined as T
  return (await resposta.json()) as T
}

/** Quem está logado e com quais papéis (vindos do diretório no backend). */
export function fetchMe(): Promise<Me> {
  return getJson<Me>('/api/me')
}

export function fetchMeta(): Promise<Meta> {
  return getJson<Meta>('/api/meta')
}

/** Valores de um campo do dicionário (`slug` é o que vai em `campo.<slug>` na URL). */
export type CampoOpcoes = { nome: string; slug: string; valores: string[] }

/** Filtro rápido nomeado (`filtros_rapidos.yaml`): botão que aplica uma combinação de filtros já existentes. */
export type FiltroRapido = { id: string; rotuloPt: string; rotuloEn: string; aplica: Record<string, string[]> }

/** Opções dos filtros (`GET /api/filtros/opcoes`), só do escopo visível de quem pergunta. */
export type OpcoesDeFiltro = {
  squads: string[]
  pessoas: string[]
  tipos: string[]
  prioridades: string[]
  os: string[]
  campos: CampoOpcoes[]
  avisos: { codigo: string; gravidade: string; mensagem: string; params?: Record<string, string> }[]
  rapidos: FiltroRapido[]
  /** Prefixos de chave (`TG`) das issues da visão; a busca por número usa o único prefixo, se houver só um. */
  prefixosDeChave?: string[]
}

export function fetchOpcoesDeFiltro(): Promise<OpcoesDeFiltro> {
  return getJson<OpcoesDeFiltro>('/api/filtros/opcoes')
}

/** Dias úteis que a task ficou num status. */
export type TempoStatus = { status: string; dias: number }

/** Fatia da posse: `pessoa` nula com `semResponsavel` falso = "outra pessoa" (fora da visão). */
export type FracaoPosse = { pessoa: string | null; semResponsavel: boolean; dias: number; fracao: number }

/** A gaveta universal da task (`GET /api/tarefa/{key}`); `historico` é nulo para quem não é coordenação nem gerente. */
export type Tarefa = {
  key: string
  resumo: string
  tipo: string
  status: string
  categoria: string
  resolucao: string | null
  responsavel: string | null
  prioridade: string | null
  criada: string
  atualizada: string | null
  resolvida: string | null
  vencimento: string | null
  vencimentoOriginal: string | null
  labels: string[]
  campos: Record<string, string>
  tempoPorStatus: TempoStatus[]
  posse: FracaoPosse[]
  fatoresDeRisco: string[]
  historico: import('../screens/Auditoria/api').LinhaHistorico[] | null
  linkJira: string | null
  avisos: { codigo: string; gravidade: string; mensagem: string; params?: Record<string, string> }[]
}

/** Uma task pela chave. Chave fora da visão ou inexistente é 404 (`ErroDaApi`). */
export function fetchTarefa(chave: string, instancia?: string): Promise<Tarefa> {
  const q = instancia ? `?instancia=${encodeURIComponent(instancia)}` : ''
  return getJson<Tarefa>(`/api/tarefa/${encodeURIComponent(chave)}${q}`)
}

/** Um alerta operacional ativo (`GET /api/operacao/alertas`, só gerente). Só códigos e instantes: nenhum dado pessoal. */
export type AlertaOperacional = {
  codigo: string
  gravidade: 'CRITICO' | 'ATENCAO' | string
  instancia: string | null
  desde: string
}

/** Alertas operacionais de agora. Resposta fora do formato vira lista vazia (a faixa só aparece com dado válido). */
export async function fetchAlertas(): Promise<AlertaOperacional[]> {
  const corpo = await getJson<{ alertas?: unknown }>('/api/operacao/alertas')
  if (!Array.isArray(corpo?.alertas)) return []
  return corpo.alertas.filter(
    (a): a is AlertaOperacional =>
      typeof a === 'object' && a !== null && typeof a.codigo === 'string' && typeof a.gravidade === 'string' && typeof a.desde === 'string',
  )
}
