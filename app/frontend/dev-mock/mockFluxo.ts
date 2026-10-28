// Dados FICTÍCIOS dos cards do Plano 17b para o desenvolvimento visual (PAINEL_MOCK=1 pnpm dev). Não entra no bundle de produção.

const SEMANAS = ['2026-08-10', '2026-08-17', '2026-08-24', '2026-08-31', '2026-09-07', '2026-09-14', '2026-09-21', '2026-09-28', '2026-10-05', '2026-10-12', '2026-10-19', '2026-10-26']
const ENTROU = [6, 5, 7, 4, 8, 6, 9, 7, 8, 5, 9, 3]
const COMECOU = [5, 5, 6, 5, 6, 6, 7, 7, 6, 6, 7, 2]
const SAIU = [5, 6, 5, 5, 6, 5, 6, 6, 5, 4, 5, 1]

export function fluxoMock() {
  const semanas = SEMANAS.map((semana, i) => ({ semana, entrou: ENTROU[i], comecou: COMECOU[i], saiu: SAIU[i], canceladas: i % 4 === 0 ? 1 : 0, emCurso: i === SEMANAS.length - 1 }))
  const soma = (campo: 'entrou' | 'comecou' | 'saiu' | 'canceladas') => semanas.reduce((s, w) => s + w[campo], 0)
  return {
    semanas, entrou: soma('entrou'), comecou: soma('comecou'), saiu: soma('saiu'), canceladas: soma('canceladas'),
    saldoDoBacklog: soma('entrou') - soma('saiu') - soma('canceladas'), semanasCrescendo: 3, backlogCrescendo: true,
    semanasNaJanela: 12, tasksNaJanela: 143, avisos: [],
  }
}

export function agingMock() {
  const pontos = (idades: number[], p85: number | null) => idades.map((idade, i) => ({ key: `TG-${3000 + i * 11}`, idade, acima: p85 != null && idade > p85 }))
  const etapa = (status: string, categoria: string, idades: number[], faixa: { p50: number; p85: number; n: number } | null) => {
    const p = pontos(idades, faixa?.p85 ?? null)
    return { status, categoria, total: p.length, acima: p.filter((x) => x.acima).length, historicoN: faixa?.n ?? 2, faixa, pontos: p }
  }
  const etapas = [
    etapa('Em andamento', 'ACTIVE', [1, 2, 2.5, 4, 6, 14], { p50: 3.2, p85: 6.1, n: 212 }),
    etapa('Em revisão', 'WAIT', [1, 3, 9], { p50: 5.4, p85: 11, n: 198 }),
    etapa('Bloqueado', 'BLOCKED', [2, 5], null),
  ]
  return {
    etapas, emAndamento: etapas.reduce((s, e) => s + e.total, 0), acimaDoNormal: etapas.reduce((s, e) => s + e.acima, 0), unit: 'util',
    avisos: [{ codigo: 'AMOSTRA_PEQUENA', gravidade: 'informativo', mensagem: 'x', params: { tipo: 'Bloqueado', n: '2', minimo: '5' } }],
  }
}

export function bloqueiosMock() {
  return {
    bloqueadas: 4, bloqueadoras: 2, bloqueadorasForaDaVisao: 1, prazoEmConflito: 1, verificarNoJira: 1, semCausa: 2, tipoDeLink: 'Blocks', avisos: [],
    linhas: [
      { bloqueada: 'TG-2101', resumo: 'Ajustar tela de login', tipo: 'Checklist', status: 'Em andamento', vence: '2026-10-28', bloqueadora: { estado: 'VISIVEL', key: 'TG-2090', vence: '2026-11-03', conflito: true, semVencimento: false } },
      { bloqueada: 'TG-2108', resumo: 'Corrigir exportação', tipo: 'Checklist', status: 'A fazer', vence: '2026-11-02', bloqueadora: { estado: 'OUTRO_SQUAD', key: null, vence: null, conflito: false, semVencimento: true } },
      { bloqueada: 'TG-2115', resumo: 'Revisar contrato da API', tipo: 'Checklist', status: 'A fazer', vence: null, bloqueadora: { estado: 'FORA_DA_BASE', key: 'TG-9999', vence: null, conflito: false, semVencimento: false } },
    ],
  }
}

export function controleMock() {
  const pontos = (tipo: number) => Array.from({ length: 30 }, (_, i) => {
    const dia = new Date(Date.UTC(2026, 8, 1 + i)).toISOString().slice(0, 10)
    const ciclo = 2 + ((i * 7 + tipo) % 11) + (i === 17 ? 28 : 0)
    return { dia, ciclo, mediaMovel: 5 + (i % 5) * 0.6, outlier: ciclo > 30 }
  })
  const meses = (n: number) => Array.from({ length: 12 }, (_, i) => {
    const mes = new Date(Date.UTC(2025, 10 + i, 1)).toISOString().slice(0, 7)
    const q = i < 8 ? n : i === 8 ? 3 : 0
    return { mes, n: q, p50: q ? 5 + (i % 4) : null, p85: q ? 9 + (i % 4) : null, amostraPequena: q > 0 && q < 5 }
  })
  return {
    series: [
      { tipo: 'Checklist', n: 30, p50: 6, p85: 10.5, amostraPequena: false, janela: 7, pontos: pontos(0), meses: meses(14) },
      { tipo: 'Checklist Review', n: 3, p50: 4, p85: 5, amostraPequena: true, janela: null, pontos: pontos(3).slice(0, 3).map((p) => ({ ...p, mediaMovel: null })), meses: meses(3) },
    ],
    concluidas: 33, semOutliers: false, limiteOutlierDias: 30, outliersExcluidos: 0, de: '2025-11-01', ate: '2026-10-30', unit: 'util', avisos: [],
  }
}

export function cruzamentosMock(linhasEixo: string, colunasEixo: string, medida: string) {
  const linhas = linhasEixo === 'pessoa' ? ['ana', 'bruno', 'carla', '(sem)'] : ['Checklist', 'Checklist Review']
  const colunas = colunasEixo === 'categoria' ? ['TODO', 'ACTIVE', 'WAIT', 'BLOCKED'] : ['Em andamento', 'Em revisão', 'Bloqueado']
  const celulas = linhas.map((_, i) => colunas.map((_c, j) => {
    const n = (i * 3 + j * 5) % 9
    return { n, valor: medida === 'idadeP50' && n > 0 ? 2 + n * 1.3 : null, amostraPequena: medida === 'idadeP50' && n > 0 && n < 5 }
  }))
  return {
    linhasEixo, colunasEixo, medida, linhas, colunas, celulas,
    totaisDasLinhas: linhasEixo === 'pessoa' ? null : celulas.map((l) => l.reduce((s, c) => s + c.n, 0)),
    totaisDasColunas: colunas.map((_, j) => celulas.reduce((s, l) => s + l[j].n, 0)), total: celulas.flat().reduce((s, c) => s + c.n, 0),
    eixosDeLinha: ['squad', 'categoria', 'status', 'tipo', 'prioridade', 'os', 'idade', 'vencimento', 'atraso', 'pessoa'],
    eixosDeColuna: ['squad', 'categoria', 'status', 'tipo', 'prioridade', 'os', 'idade', 'vencimento', 'atraso'], unit: 'util', avisos: [],
  }
}

/** Cartões do 17b no catálogo fictício (grupo e natureza como no backend). */
export function catalogoFluxo() {
  const c = (id: string, tela: string, grupo: string, natureza: string, fontes: string[], temDrill = true) => {
    const base = `card.${id.replace('-', '').toLowerCase()}`
    return { id, grupo, natureza, tela, tituloChave: `${base}.titulo`, oQueMostraChave: `${base}.oQueMostra`, comoCalculaChave: `${base}.comoCalcula`, fontes, papeis: ['USUARIO', 'SQUAD_LEADER', 'COORDENADOR', 'GERENTE'], temDrill, exigeChangelog: false, exigeWorklog: false }
  }
  return [
    c('FX-01', 'fluxo-da-semana', 'trabalho', 'FLUXO', ['ISSUE', 'CHANGELOG']), c('FX-02', 'fluxo-da-semana', 'trabalho', 'FLUXO', ['ISSUE', 'CHANGELOG']),
    c('AW-01', 'aging-wip', 'trabalho', 'FLUXO', ['ISSUE', 'CHANGELOG']), c('AW-02', 'aging-wip', 'trabalho', 'FLUXO', ['ISSUE', 'CHANGELOG']),
    c('BQ-01', 'bloqueios', 'trabalho', 'ESTOQUE', ['ISSUE', 'ISSUE_LINK']), c('BQ-02', 'bloqueios', 'trabalho', 'ESTOQUE', ['ISSUE', 'ISSUE_LINK']),
    c('BQ-03', 'bloqueios', 'trabalho', 'PRAZO', ['ISSUE', 'ISSUE_LINK']), c('BQ-04', 'bloqueios', 'trabalho', 'QUALIDADE', ['ISSUE', 'ISSUE_LINK']),
    c('BQ-05', 'bloqueios', 'trabalho', 'QUALIDADE', ['ISSUE', 'ISSUE_LINK']),
    c('CT-01', 'controle-ciclo', 'resultados', 'FLUXO', ['ISSUE', 'CHANGELOG']), c('CT-02', 'controle-ciclo', 'resultados', 'FLUXO', ['ISSUE', 'CHANGELOG']),
    c('CZ-01', 'cruzamentos', 'resultados', 'ESTOQUE', ['ISSUE', 'CONFIGURACAO']),
  ]
}
