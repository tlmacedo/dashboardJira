// Dados fictícios dos cards de pessoas e planejamento (Fase 6) SÓ para o desenvolvimento visual (PAINEL_MOCK=1 pnpm dev).
// Não entra no bundle de produção. Nenhum dado real; nomes inventados.

const PESSOAS = ['ana', 'bruno', 'carla', 'diego', 'elisa']
const SEMANAS = ['2026-10-26', '2026-11-02', '2026-11-09', '2026-11-16', '2026-11-23', '2026-11-30', '2026-12-07', '2026-12-14']
const ok = (f: number) => ({ fracao: f, semCapacidade: false, estado: f > 1.1 ? 'problema' : f >= 0.85 ? 'atencao' : 'ok' })
const semCap = { fracao: null, semCapacidade: true, estado: 'problema' }
const ocupacaoDe = (p: number, s: number) => (p === 2 && s === 1 ? semCap : ok([1.5, 0.4, 0.9, 0.25, 1.2][p] * (s % 2 === 0 ? 1 : 0.8)))

function carga() {
  return {
    versaoRegras: 'planejamento v1', jain: 0.74, acimaDaCapacidade: 2, comFolga: 1, tasksAcimaDaCapacidade: 7, tasksComFolga: 2,
    linhas: PESSOAS.map((pessoa, i) => ({
      pessoa, abertas: 2 + (i % 3), wip: 1 + (i % 2), filaH: 12.4 + i * 3.1, capacidade4SemH: 67.2 - i * 6, ocupacao: ocupacaoDe(i, 0),
      ausenciaDias: i === 2 ? 5 : i === 0 ? 2 : 0, horasRegistradas30d: null, acimaDoP85: i % 2, vencidas: i === 2 ? 1 : 0, riscoAlto: i === 0 ? 1 : 0,
      atrasoAcumuladoH: i === 2 ? 6 : 0, horasAssumidas: i === 2,
    })),
    avisos: [{ codigo: 'HORAS_ASSUMIDAS', gravidade: 'informativo', mensagem: 'x' }],
  }
}

function capacidade() {
  return {
    versaoRegras: 'planejamento v1',
    alertas: [
      { pessoa: 'ana', tipo: 'AUSENCIA_COM_TRABALHO', semana: '2026-11-02', horasAReplanejar: 5.6 },
      { pessoa: 'ana', tipo: 'CARGA_ALTA', semana: '2026-10-26', horasAReplanejar: null },
    ],
    atrasosPrevistos: 2, atrasosPorAusencia: 1, jain: 0.74,
    mapa: PESSOAS.map((pessoa, p) => ({
      pessoa,
      semanas: SEMANAS.map((semana, s) => {
        const o = ocupacaoDe(p, s)
        return { semana, ocupacao: o, ausente: o.semCapacidade }
      }),
    })),
    ausencias: [
      { pessoa: 'ana', de: '2026-11-04', ate: '2026-11-05', fracao: 1 },
      { pessoa: 'carla', de: '2026-11-02', ate: '2026-11-06', fracao: 1 },
    ],
    feriados: [{ dia: '2026-11-02', nome: 'Finados' }, { dia: '2026-11-20', nome: 'Consciência Negra' }],
    listaAtrasos: [
      { key: 'TG-2202', pessoa: 'ana', vence: '2026-11-05', termino: '2026-11-06', causa: 'AUSENCIA' },
      { key: 'TG-2210', pessoa: 'bruno', vence: '2026-11-10', termino: null, causa: 'CARGA' },
    ],
    avisos: [] as unknown[],
  }
}

function calendario(inicio: string, mes: boolean) {
  const dias = Array.from({ length: mes ? 28 : 7 }, (_, i) => {
    const d = new Date(`${inicio}T00:00:00Z`)
    d.setUTCDate(d.getUTCDate() + i)
    const dia = d.toISOString().slice(0, 10)
    const util = d.getUTCDay() !== 0 && d.getUTCDay() !== 6 && dia !== '2026-11-02'
    return { dia, feriado: dia === '2026-11-02' ? 'Finados' : null, util, capacidadeSquad: util ? (i % 3 === 0 ? 0.2 : 0.8) : null, vencimentos: util && i % 3 === 0 ? 2 : 0, critico: util && i % 3 === 0 }
  })
  return {
    inicio,
    dias,
    linhas: PESSOAS.map((pessoa, p) => ({
      pessoa,
      celulas: dias.map((d, i) => ({ dia: d.dia, ausencia: p === 0 && (i === 2 || i === 3) ? 1 : p === 1 && i === 5 ? 0.5 : 0, vencimentos: p === 0 && d.vencimentos > 0 ? 1 : 0 })),
    })),
    avisos: [] as unknown[],
  }
}

function plano(semanas: number) {
  return {
    semanas: SEMANAS.slice(0, semanas),
    linhas: PESSOAS.map((pessoa, p) => ({
      pessoa,
      celulas: SEMANAS.slice(0, semanas).map((semana, s) => {
        const o = ocupacaoDe(p, s)
        const cap = o.semCapacidade ? 0 : 20
        return { semana, planejadaH: o.semCapacidade ? 3 : +((o.fracao ?? 0) * cap).toFixed(1), capacidadeH: cap, ocupacao: o, itens: 1 + ((p + s) % 3), semDataH: s % 2 ? 2 : 0 }
      }),
    })),
    avisos: [] as unknown[],
  }
}

function vencimentos(dias: number) {
  const linhas = [
    { key: 'TG-2201', resumo: 'Ajustar tela de login', pessoa: 'ana', vence: '2026-11-04', termino: '2026-11-03', folgaDiasUteis: 1, estado: 'ok' },
    { key: 'TG-2202', resumo: 'Corrigir exportação', pessoa: 'ana', vence: '2026-11-05', termino: '2026-11-06', folgaDiasUteis: -1, estado: 'problema' },
    { key: 'TG-2203', resumo: 'Revisar contrato da API', pessoa: 'bruno', vence: '2026-11-05', termino: '2026-11-05', folgaDiasUteis: 0, estado: 'atencao' },
  ]
  return { dias, vencem: linhas.length, linhas, riscoDeAtrasar: 1, avisos: [] as unknown[] }
}

function gargalos() {
  const dias = Array.from({ length: 60 }, (_, i) => {
    const d = new Date('2026-09-02T00:00:00Z')
    d.setUTCDate(d.getUTCDate() + i)
    return d.toISOString().slice(0, 10)
  })
  const rampa = (de: number, ate: number) => dias.map((_, i) => Math.round(de + ((ate - de) * i) / 59))
  return {
    dias,
    faixas: [
      { status: 'A Fazer', contagens: rampa(8, 6) },
      { status: 'Em andamento', contagens: rampa(5, 13) },
      { status: 'Em revisão', contagens: rampa(2, 4) },
    ],
    etapas: [
      { status: 'A Fazer', categoria: 'TODO', wip: 6, limite: null, crescimento15d: -1, gargalo: false, motivo: null },
      { status: 'Em andamento', categoria: 'ACTIVE', wip: 13, limite: 12, crescimento15d: 3, gargalo: true, motivo: 'WIP_ACIMA_DO_LIMITE' },
      { status: 'Em revisão', categoria: 'WAIT', wip: 4, limite: 5, crescimento15d: 1, gargalo: false, motivo: null },
    ],
    acimaDaCapacidade: 2, comFolga: 1, tasksAcimaDaCapacidade: 7, tasksComFolga: 2, avisos: [] as unknown[],
  }
}

function tendencia() {
  const semanas = Array.from({ length: 12 }, (_, i) => {
    const d = new Date('2026-08-10T00:00:00Z')
    d.setUTCDate(d.getUTCDate() + i * 7)
    return d.toISOString().slice(0, 10)
  })
  const padroes = ['RECORRENTE', 'NORMAL', 'PONTUAL', 'NORMAL', 'NORMAL']
  return {
    semanas,
    linhas: PESSOAS.map((pessoa, p) => ({
      pessoa,
      ocupacoes: semanas.map((_, s) => (p === 0 && s > 7 ? ok(1.3) : p === 2 && s === 11 ? ok(1.2) : ok(0.5 + ((p + s) % 4) * 0.1))),
      padrao: padroes[p],
    })),
    avisos: [{ codigo: 'HORAS_ATUAIS_NO_PASSADO', gravidade: 'informativo', mensagem: 'x' }],
  }
}

/** A resposta do card de pessoas pedido, ou `undefined` se a rota não é de um deles. */
export function mockPessoas(pathname: string, params: URLSearchParams): unknown {
  switch (pathname) {
    case '/api/cards/carga':
      return carga()
    case '/api/cards/capacidade':
      return capacidade()
    case '/api/cards/calendario':
      return calendario(params.get('inicio') ?? '2026-10-26', params.get('modo') === 'mes')
    case '/api/cards/plano':
      return plano(Math.min(8, Math.max(4, Number(params.get('semanas')) || 4)))
    case '/api/cards/vencimentos':
      return vencimentos(params.get('dias') === '14' ? 14 : 7)
    case '/api/cards/gargalos':
      return gargalos()
    case '/api/cards/tendencia':
      return tendencia()
    default:
      return undefined
  }
}

const TODOS = ['USUARIO', 'SQUAD_LEADER', 'COORDENADOR', 'GERENTE']
const LIDERANCA = ['SQUAD_LEADER', 'COORDENADOR', 'GERENTE']

/** Os cartões da Fase 6 para o catálogo simulado (mesmos ids, telas e papéis do backend). */
export function cartoesDePessoasMock() {
  const def: [string, string, string, string[], boolean][] = [
    ['CP-01', 'carga', 'PESSOAS', TODOS, false], ['CP-02', 'carga', 'PESSOAS', TODOS, true], ['CP-03', 'carga', 'PESSOAS', TODOS, true],
    ['CA-01', 'capacidade', 'PESSOAS', LIDERANCA, false], ['CA-02', 'capacidade', 'PRAZO', LIDERANCA, true], ['CA-03', 'capacidade', 'PRAZO', LIDERANCA, true],
    ['CA-04', 'capacidade', 'PESSOAS', LIDERANCA, false], ['CA-05', 'capacidade', 'PESSOAS', LIDERANCA, false], ['CA-06', 'capacidade', 'PESSOAS', LIDERANCA, false],
    ['CA-07', 'capacidade', 'PESSOAS', LIDERANCA, false], ['CA-08', 'capacidade', 'PESSOAS', LIDERANCA, false], ['CA-09', 'capacidade', 'PRAZO', LIDERANCA, true],
    ['CL-01', 'calendario', 'PRAZO', TODOS, true], ['PL-01', 'plano', 'PESSOAS', LIDERANCA, true],
    ['GA-01', 'gargalos', 'FLUXO', LIDERANCA, false], ['GA-02', 'gargalos', 'FLUXO', LIDERANCA, true], ['GA-03', 'gargalos', 'PESSOAS', LIDERANCA, true],
    ['GA-04', 'gargalos', 'PESSOAS', LIDERANCA, true], ['TD-01', 'tendencia', 'PESSOAS', LIDERANCA, false],
  ]
  const venc: [string, string, string, string[], boolean][] = [
    ['VE-01', 'vencimentos', 'PRAZO', TODOS, true], ['VE-02', 'vencimentos', 'PRAZO', TODOS, true],
  ]
  return [...def, ...venc].map(([id, tela, natureza, papeis, temDrill]) => {
    const base = `card.${id.replace('-', '').toLowerCase()}`
    return {
      id, grupo: id.startsWith('VE') ? 'prazos' : 'pessoas', natureza, tela, tituloChave: `${base}.titulo`, oQueMostraChave: `${base}.oQueMostra`,
      comoCalculaChave: `${base}.comoCalcula`, fontes: ['ISSUE', 'CONFIGURACAO'], papeis, temDrill, exigeChangelog: false, exigeWorklog: false,
    }
  })
}
