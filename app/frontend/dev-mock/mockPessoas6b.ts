// Dados fictícios dos cards do bloco 6b da Fase 6 (posse, esforço, cobertura, simulador, competências, revisões e carga por tipo/OS)
// SÓ para o desenvolvimento visual (PAINEL_MOCK=1 pnpm dev). Não entra no bundle de produção. Nenhum dado real; nomes inventados.

const ok = (f: number) => ({ fracao: f, semCapacidade: false, estado: f > 1.1 ? 'problema' : f >= 0.85 ? 'atencao' : 'ok' })
const semCap = { fracao: null, semCapacidade: true, estado: 'problema' }

function posse(corrido: boolean) {
  return {
    unit: corrido ? 'corrido' : 'util',
    tasks: [
      { key: 'TG-2101', resumo: 'Ajustar tela de login', transferencias: 1, posse: [{ pessoa: 'ana', dias: 3, fracao: 0.642857 }, { pessoa: null, dias: 1.7, fracao: 0.357143 }] },
      { key: 'TG-2102', resumo: 'Corrigir exportação', transferencias: 0, posse: [{ pessoa: 'ana', dias: 8, fracao: 1 }] },
      { key: 'TG-2103', resumo: 'Revisar contrato da API', transferencias: 2, posse: [{ pessoa: 'bruno', dias: 2, fracao: 0.4 }, { pessoa: 'carla', dias: 3, fracao: 0.6 }] },
    ],
    pessoas: [
      { pessoa: 'ana', tasks: 2, dias: 11, recebidas: 0, repassadas: 1 },
      { pessoa: 'bruno', tasks: 1, dias: 2, recebidas: 1, repassadas: 1 },
      { pessoa: 'carla', tasks: 1, dias: 3, recebidas: 1, repassadas: 0 },
    ],
    avisos: [] as unknown[],
  }
}

function esforco(mes: boolean) {
  return {
    versaoRegras: 'esforço v1',
    granularidade: mes ? 'mes' : 'semana',
    tasks: [
      { key: 'TG-2101', resumo: 'Ajustar tela de login', tipo: 'Checklist', resolvida: '2026-10-12', pesoTipo: 3, testes: 3, horas: 10, indice: 14.5, horasDoCampo: false },
      { key: 'TG-2105', resumo: 'Revisar contrato da API', tipo: 'Checklist Review', resolvida: '2026-10-27', pesoTipo: 1, testes: 0, horas: 2, indice: 3, horasDoCampo: true },
    ],
    pessoas: [
      { pessoa: 'ana', tasks: 1, pesoTipo: 1.93, testes: 1.93, horas: 6.43, indice: 9.32, horasDoCampo: false },
      { pessoa: 'bruno', tasks: 1, pesoTipo: 1.07, testes: 1.07, horas: 3.57, indice: 5.18, horasDoCampo: false },
      { pessoa: 'carla', tasks: 1, pesoTipo: 1, testes: 0, horas: 2, indice: 3, horasDoCampo: true },
    ],
    periodos: mes
      ? [{ inicio: '2026-10-01', tasks: 2, horas: 12, indice: 17.5 }]
      : [{ inicio: '2026-10-12', tasks: 1, horas: 10, indice: 14.5 }, { inicio: '2026-10-26', tasks: 1, horas: 2, indice: 3 }],
    avisos: [{ codigo: 'HORAS_DO_CAMPO_TIMESPENT', gravidade: 'informativo', mensagem: 'x', params: { n: '1' } }],
  }
}

function cobertura(dias: number) {
  return {
    dias,
    avisos: [] as unknown[],
    ausencias: [
      {
        pessoa: 'ana', de: '2026-11-04', ate: '2026-11-05', issues: ['TG-2202'], horasAReplanejar: 5.6, competencias: ['android'],
        podemCobrir: [{ pessoa: 'bruno', folgaH: 9.2, temTodasAsCompetencias: true }, { pessoa: 'diego', folgaH: 14, temTodasAsCompetencias: false }],
      },
      { pessoa: 'bruno', de: '2026-11-10', ate: '2026-11-10', issues: [], horasAReplanejar: 0, competencias: [], podemCobrir: [] },
      { pessoa: 'carla', de: '2026-11-02', ate: '2026-11-06', issues: ['TG-2205'], horasAReplanejar: 0.4, competencias: [], podemCobrir: [] },
    ],
  }
}

function simulador(p: URLSearchParams) {
  const pessoa = p.get('pessoa')
  if (!pessoa) {
    return {
      pessoa: '', de: '', ate: '', fracao: 1, atrasosCausados: [] as string[], horasAReplanejar: 0, semanas: [],
      avisos: [{ codigo: 'SIMULACAO_SEM_PARAMETROS', gravidade: 'informativo', mensagem: 'x' }],
    }
  }
  return {
    pessoa, de: p.get('de') ?? '', ate: p.get('ate') ?? '', fracao: p.get('fracao') === '0.5' ? 0.5 : 1,
    atrasosCausados: ['TG-2203'], horasAReplanejar: 1.2, avisos: [] as unknown[],
    semanas: [
      { semana: '2026-10-26', antes: ok(0.2), depois: ok(0.2) },
      { semana: '2026-11-02', antes: ok(0.18), depois: semCap },
    ],
  }
}

function competencias() {
  return {
    competencias: ['android', 'ios', 'rede', 'ui'],
    pessoas: ['ana', 'bruno', 'carla'],
    celulas: [[true, true, false], [false, false, false], [true, false, true], [false, false, true]],
    pessoaUnica: ['ui'],
    semNinguem: ['ios'],
    demandaH: { android: 16.8, ios: 6 },
    tasksPorCompetencia: { android: 2, ios: 1 },
    tasksSemNinguem: 1,
    avisos: [] as unknown[],
  }
}

function revisoes() {
  return {
    semRevisor: 1,
    avisos: [] as unknown[],
    linhas: [
      { revisor: 'ana', pendentes: 1, vencidas: 0, foraDoSla: 0, idadeP50: 2 },
      { revisor: 'bruno', pendentes: 2, vencidas: 1, foraDoSla: 1, idadeP50: 2.5 },
      { revisor: 'carla', pendentes: 1, vencidas: 1, foraDoSla: 0, idadeP50: 1 },
    ],
  }
}

function cargaTipo() {
  return {
    tipos: ['Checklist', 'Checklist Review'],
    versoes: ['Android 16.0 (16)', 'Android 17.0 (OneUI_9.0)', ''],
    abertasN: [[4, 3, 2], [1, 0, 1]],
    abertasH: [[22.4, 16, 10], [2, 0, 2]],
    fechadasN: [[3, 2, 1], [1, 1, 0]],
    esforco: [[30.5, 18, 5.5], [3, 3, 0]],
    avisos: [] as unknown[],
  }
}

/** A resposta do card do bloco 6b pedido, ou `undefined` se a rota não é de um deles. */
export function mockPessoas6b(pathname: string, params: URLSearchParams): unknown {
  switch (pathname) {
    case '/api/cards/posse':
      return posse(params.get('unit') === 'corrido')
    case '/api/cards/esforco':
      return esforco(params.get('granularidade') === 'mes')
    case '/api/cards/cobertura':
      return cobertura(Math.min(90, Math.max(1, Number(params.get('dias')) || 30)))
    case '/api/cards/simulador':
      return simulador(params)
    case '/api/cards/competencias':
      return competencias()
    case '/api/cards/revisoes':
      return revisoes()
    case '/api/cards/carga-tipo':
      return cargaTipo()
    default:
      return undefined
  }
}

const TODOS = ['USUARIO', 'SQUAD_LEADER', 'COORDENADOR', 'GERENTE']
const LIDERANCA = ['SQUAD_LEADER', 'COORDENADOR', 'GERENTE']
const ALTA = ['COORDENADOR', 'GERENTE']

/** Os cartões do bloco 6b para o catálogo simulado (mesmos ids, telas, papéis e natureza do backend). */
export function cartoesDePessoas6bMock() {
  const def: [string, string, string, string[], boolean, boolean][] = [
    ['PS-01', 'posse', 'FLUXO', TODOS, true, false], ['PS-02', 'posse', 'PESSOAS', TODOS, true, false],
    ['EF-01', 'esforco', 'FLUXO', TODOS, true, true], ['EF-02', 'esforco', 'PESSOAS', TODOS, true, true],
    ['CB-01', 'cobertura', 'PESSOAS', LIDERANCA, true, false], ['SI-01', 'simulador', 'PRAZO', LIDERANCA, true, false],
    ['CM-01', 'competencias', 'PESSOAS', TODOS, false, false], ['CM-02', 'competencias', 'QUALIDADE', TODOS, true, false],
    ['CM-03', 'competencias', 'ESTOQUE', TODOS, true, false],
    ['RV-01', 'revisoes', 'ESTOQUE', TODOS, true, false], ['RV-02', 'revisoes', 'QUALIDADE', ALTA, true, false],
    ['CG-01', 'carga-tipo', 'ESTOQUE', TODOS, true, false], ['CG-02', 'carga-tipo', 'FLUXO', TODOS, true, true],
  ]
  return def.map(([id, tela, natureza, papeis, temDrill, exigeWorklog]) => {
    const base = `card.${id.replace('-', '').toLowerCase()}`
    return {
      id, grupo: id.startsWith('EF') ? 'resultados' : 'pessoas', natureza, tela, tituloChave: `${base}.titulo`, oQueMostraChave: `${base}.oQueMostra`,
      comoCalculaChave: `${base}.comoCalcula`, fontes: ['ISSUE', 'CONFIGURACAO'], papeis, temDrill, exigeChangelog: false, exigeWorklog,
    }
  })
}
