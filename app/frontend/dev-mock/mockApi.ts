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
  return {
    tituloChave: 'tempo.kpi.analisadas',
    definicaoChave: `card.ts0${n}.oQueMostra`,
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
  return { cartoes: [...ts, ...sy] }
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
