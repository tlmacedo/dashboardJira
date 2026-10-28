// API simulada SÓ para desenvolvimento visual (PAINEL_MOCK=1 pnpm dev). Dados fictícios.
// Não entra no bundle de produção: é carregada apenas pelo vite.config.ts em modo serve.
import type { Plugin } from 'vite'

const STATUS = [
  { status: 'Em andamento', category: 'ACTIVE', median: 3.2, p85: 6.1, n: 212 },
  { status: 'Em revisão', category: 'WAIT', median: 5.4, p85: 11.0, n: 198 },
  { status: 'Em teste', category: 'ACTIVE', median: 2.4, p85: 4.9, n: 160 },
  { status: 'Aguardando cliente', category: 'WAIT', median: 4.1, p85: 9.3, n: 74 },
  { status: 'Bloqueado', category: 'BLOCKED', median: 1.1, p85: 2.3, n: 41 },
]
const PESSOAS = ['ana', 'bruno', 'carla', 'diego', 'elisa']
const MEDIANAS: Record<string, number[]> = {
  ana: [2.1, 4.8, 1.9, 3.5, 0],
  bruno: [2.8, 7.2, 2.4, 0, 3.0],
  carla: [3.4, 5.1, 2.9, 4.2, 0],
  diego: [1.8, 3.2, 0, 6.8, 1.2],
  elisa: [2.6, 6.4, 2.2, 0, 0],
}

function cartao(corrido: boolean) {
  const f = corrido ? 1.4 : 1
  return {
    analisadas: 236,
    eficienciaFluxo: 0.31,
    paradas: 17,
    porStatus: STATUS.map((s) => ({ ...s, median: +(s.median * f).toFixed(1), p85: +(s.p85 * f).toFixed(1) })),
    pessoaStatus: PESSOAS.flatMap((p) =>
      STATUS.flatMap((s, i) => (MEDIANAS[p][i] > 0 ? [{ person: p, status: s.status, median: +(MEDIANAS[p][i] * f).toFixed(1), n: 10 + i * 3 }] : [])),
    ),
    abertasParadas: Array.from({ length: 17 }, (_, i) => ({
      key: `TG-${2100 + i * 7}`,
      summary: ['Ajustar tela de login', 'Corrigir exportação', 'Revisar contrato da API', 'Atualizar traduções'][i % 4] + ` (${i + 1})`,
      assignee: i % 6 === 5 ? null : PESSOAS[i % PESSOAS.length],
      status: STATUS[(i + 1) % STATUS.length].status,
      daysInStatus: +((8 + (i % 7) * 1.3) * f).toFixed(1),
      median: +(3.2 * f).toFixed(1),
    })),
    unit: corrido ? 'corrido' : 'util',
    avisos: [] as { codigo: string; gravidade: string; mensagem: string }[],
  }
}

// ---- Auditoria e Divergência Task × Review (dados fictícios) ----

const CODIGOS = [
  'VENCIMENTO_ALTERADO_NAO_AUTORIZADO',
  'VENCIMENTO_REMOVIDO',
  'VENCIMENTO_TASK_REVIEW_DIVERGENTE',
  'RESPONSAVEL_TROCADO_NAO_AUTORIZADO',
  'PASSA_BOLA',
  'TROCA_PERTO_DO_VENCIMENTO',
  'LABEL_APOS_FECHAMENTO_NAO_AUTORIZADO',
  'LABEL_PELO_RESPONSAVEL_APOS_FECHAMENTO',
  'LABEL_TARDIA_COM_POSSE_ANTECIPADA',
  'LABEL_APOS_VENCIMENTO',
  'CANCELAMENTO_SUSPEITO',
]
const MOTIVOS = ['NAO_AUTORIZADO', 'PROPRIO_RESPONSAVEL', 'REPORTER', 'COORDENACAO', 'LIDER_DO_SQUAD']

function auditoria() {
  const sinais = Array.from({ length: 14 }, (_, i) => {
    const codigo = CODIGOS[(i * 3) % CODIGOS.length]
    const alerta = codigo === 'VENCIMENTO_TASK_REVIEW_DIVERGENTE'
    const motivo = MOTIVOS[i % MOTIVOS.length]
    const dia = `2026-10-${String(28 - i).padStart(2, '0')}`
    return {
      codigo,
      resultado: alerta ? 'alerta' : 'auditoria',
      gravidade: alerta ? 1 : 2 + (i % 2),
      key: `TG-${2100 + i * 5}`,
      resumo: `Task fictícia ${i + 1}`,
      campo: codigo.startsWith('LABEL') ? 'labels' : codigo.startsWith('VENC') ? 'duedate' : 'assignee',
      de: codigo.startsWith('VENC') ? '2026-10-15' : PESSOAS[i % PESSOAS.length],
      para: codigo.startsWith('VENC') ? '2026-10-22' : PESSOAS[(i + 1) % PESSOAS.length],
      at: `${dia}T15:00:00Z`,
      dia,
      autor: i % 6 === 5 ? 'JIRAUSER99' : PESSOAS[(i + 2) % PESSOAS.length],
      autorizado: motivo === 'REPORTER' || motivo === 'COORDENACAO' || motivo === 'LIDER_DO_SQUAD',
      motivo,
      responsavel: PESSOAS[i % PESSOAS.length],
      emLote: i % 5 === 0 ? 6 : null,
    }
  })
  return {
    aviso: 'INDICIO_NAO_PROVA',
    versaoRegras: 'regras v1, labels v1',
    total: sinais.length,
    porSinal: CODIGOS.map((c) => ({
      codigo: c,
      resultado: c === 'VENCIMENTO_TASK_REVIEW_DIVERGENTE' ? 'alerta' : 'auditoria',
      gravidade: c === 'VENCIMENTO_TASK_REVIEW_DIVERGENTE' ? 1 : 3,
      n: sinais.filter((s) => s.codigo === c).length,
    })),
    sinais,
    noPrazo: { concluidas: 48, noPrazo: 30, noPrazoAjustado: 39, justificadasSobAuditoria: 4, taxa: 30 / 48, taxaAjustada: 39 / 48 },
    avisos: [] as unknown[],
  }
}

function historico(key: string) {
  return {
    aviso: 'INDICIO_NAO_PROVA',
    key,
    resumo: `Task fictícia ${key}`,
    reporter: 'ana',
    linhas: [
      { at: '2026-10-10T15:00:00Z', campo: 'duedate', de: '2026-10-15', para: '2026-10-22', autor: 'bruno', autorizado: false, motivo: 'NAO_AUTORIZADO', valorGuardado: true },
      { at: '2026-10-11T15:00:00Z', campo: 'description', de: null, para: null, autor: 'carla', autorizado: null, motivo: null, valorGuardado: false },
    ],
    avisos: [] as unknown[],
  }
}

const URL_BASE_FICTICIA = 'https://jira.exemplo.invalid/browse'

function taskReview() {
  return {
    pares: 12,
    divergentes: 3,
    percentual: 25,
    avisos: [] as unknown[],
    divergencias: [101, 108, 115].map((n, i) => ({
      task: `TG-${n}`,
      review: `TG-${n + 1000}`,
      vencimentoTask: '2026-10-15',
      vencimentoReview: `2026-10-${18 + i}`,
      responsavelTask: PESSOAS[i],
      responsavelReview: PESSOAS[i + 1],
      divergente: true,
      urlTask: `${URL_BASE_FICTICIA}/TG-${n}`,
      urlReview: `${URL_BASE_FICTICIA}/TG-${n + 1000}`,
    })),
  }
}

/** Drill genérico (`/api/cartoes/{id}/tasks`): as tasks fictícias de um segmento. Sem link para o Jira. */
function drill(cartaoId: string, segmento: string) {
  const total = segmento === 'analisadas' ? 236 : segmento === 'paradas' ? 17 : 24
  const linhas = Array.from({ length: total }, (_, i) => ({
    key: `TG-${1800 + i}`,
    resumo: `Task fictícia ${i + 1}`,
    responsavel: PESSOAS[i % PESSOAS.length],
    status: segmento.startsWith('status:') ? segmento.slice(7) : 'Em revisão',
    detalhe: `${(2 + (i % 9) * 0.7).toFixed(1)} dias úteis em revisão`,
  }))
  const n = Number(cartaoId.slice(3))
  const prefixo = cartaoId.slice(0, 2).toLowerCase()
  return {
    tituloChave: prefixo === 'ts' ? 'tempo.kpi.analisadas' : `card.${prefixo}0${n}.titulo`,
    definicaoChave: `card.${prefixo}0${n}.oQueMostra`,
    total,
    colunas: ['chave', 'resumo', 'responsavel', 'status', 'detalhe'],
    linhas,
    linkJira: null,
  }
}

const TODOS = ['USUARIO', 'SQUAD_LEADER', 'COORDENADOR', 'GERENTE']

/** Catálogo fictício (espelha `/api/cartoes/catalogo` do GERENTE): TS-01 a TS-07 e SY-01 a SY-03. */
function catalogo() {
  const ts = [
    ['TS-01', 'tempo.kpi.analisadas'],
    ['TS-02', 'tempo.kpi.eficiencia'],
    ['TS-03', 'tempo.kpi.paradas'],
    ['TS-04', 'tempo.porStatus'],
    ['TS-05', 'card.ts05.titulo'],
    ['TS-06', 'tempo.matriz'],
    ['TS-07', 'tempo.paradas'],
  ].map(([id, tituloChave]) => ({
    id,
    grupo: 'resultados',
    natureza: 'FLUXO',
    tela: 'tempo-em-status',
    tituloChave,
    oQueMostraChave: `card.ts0${id.slice(4)}.oQueMostra`,
    comoCalculaChave: `card.ts0${id.slice(4)}.comoCalcula`,
    fontes: ['ISSUE', 'CHANGELOG'],
    papeis: TODOS,
    temDrill: true,
    exigeChangelog: true,
    exigeWorklog: false,
  }))
  const sy = [
    ['SY-01', 'sinc.modo'],
    ['SY-02', 'sinc.execucoes'],
    ['SY-03', 'sinc.retencao'],
  ].map(([id, tituloChave]) => ({
    id,
    grupo: 'governanca',
    natureza: 'QUALIDADE',
    tela: 'sincronizacao',
    tituloChave,
    oQueMostraChave: `card.sy0${id.slice(4)}.oQueMostra`,
    comoCalculaChave: `card.sy0${id.slice(4)}.comoCalcula`,
    fontes: ['SINCRONIZACAO'],
    papeis: ['GERENTE'],
    temDrill: false,
    exigeChangelog: false,
    exigeWorklog: false,
  }))
  const au = [
    ['AU-01', 'auditoria', 'QUALIDADE'],
    ['AU-02', 'auditoria', 'QUALIDADE'],
    ['AU-03', 'auditoria', 'PRAZO'],
    ['DV-01', 'task-review', 'QUALIDADE'],
  ].map(([id, tela, natureza]) => ({
    id,
    grupo: 'auditoria',
    natureza,
    tela,
    tituloChave: `card.${id.replace('-', '').toLowerCase()}.titulo`,
    oQueMostraChave: `card.${id.replace('-', '').toLowerCase()}.oQueMostra`,
    comoCalculaChave: `card.${id.replace('-', '').toLowerCase()}.comoCalcula`,
    fontes: ['ISSUE', 'CHANGELOG', 'CONFIGURACAO'],
    papeis: ['COORDENADOR', 'GERENTE'],
    temDrill: true,
    exigeChangelog: true,
    exigeWorklog: false,
  }))
  return { cartoes: [...ts, ...sy, ...au] }
}

export function mockApi(): Plugin {
  return {
    name: 'painel-mock-api',
    apply: 'serve',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const url = new URL(req.url ?? '/', 'http://localhost')
        let corpo: unknown
        if (url.pathname === '/api/meta') {
          corpo = { devMode: true, liveEnabled: false, zone: 'America/Manaus', banner: 'MODO DESENVOLVIMENTO (dados fictícios)' }
        } else if (url.pathname === '/api/me') corpo = { userId: 'dev', roles: ['GERENTE'] }
        else if (url.pathname === '/api/cards/tempo-em-status') corpo = cartao(url.searchParams.get('unit') === 'corrido')
        else if (url.pathname === '/api/auditoria/sinais') corpo = auditoria()
        else if (url.pathname.startsWith('/api/auditoria/historico/')) corpo = historico(decodeURIComponent(url.pathname.split('/')[4] ?? ''))
        else if (url.pathname === '/api/cards/task-review') corpo = taskReview()
        else if (url.pathname === '/api/cartoes/catalogo') corpo = catalogo()
        else if (/^\/api\/cartoes\/[A-Z]{2}-\d{2}\/tasks$/.test(url.pathname)) {
          corpo = drill(url.pathname.split('/')[3], url.searchParams.get('segmento') ?? '')
        } else return next()
        res.setHeader('Content-Type', 'application/json')
        res.end(JSON.stringify(corpo))
      })
    },
  }
}
