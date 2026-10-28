// API simulada SÓ para desenvolvimento visual (PAINEL_MOCK=1 pnpm dev). Dados fictícios.
// Não entra no bundle de produção: é carregada apenas pelo vite.config.ts em modo serve.
import type { Plugin } from 'vite'
import { agingMock, bloqueiosMock, catalogoFluxo, controleMock, cruzamentosMock, fluxoMock } from './mockFluxo.ts'

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

// ---- Cards de trabalho e prazos (Plano 5): Abertas, Negligenciadas, Fechadas e Metas (dados fictícios) ----

const TIPOS_MOCK = ['Checklist', 'Checklist Review']
const STATUS_MOCK = ['Open', 'In Progress', 'Submitted', 'On Hold']

function abertasMock(corrido: boolean) {
  const f = corrido ? 1.4 : 1
  const linhas = Array.from({ length: 24 }, (_, i) => ({
    key: `TG-${2200 + i}`,
    resumo: `Task fictícia ${i + 1}`,
    tipo: TIPOS_MOCK[i % 2],
    status: STATUS_MOCK[i % STATUS_MOCK.length],
    prioridade: ['High', 'Medium', 'Low'][i % 3],
    responsavel: i % 8 === 7 ? null : PESSOAS[i % PESSOAS.length],
    idade: +((2 + i * 1.7) * f).toFixed(1),
    vence: i % 4 === 3 ? null : `2026-11-${String(1 + (i % 20)).padStart(2, '0')}`,
    horasRestantes: i % 3 === 0 ? 4 + i : null,
  }))
  return {
    abertas: linhas.length,
    emAndamento: 15,
    acimaDoP85: 4,
    historicoPorTipo: [
      { tipo: 'Checklist', p50: +(5.5 * f).toFixed(1), p85: +(9.1 * f).toFixed(1), n: 62 },
      { tipo: 'Checklist Review', p50: +(1.8 * f).toFixed(1), p85: +(3.4 * f).toFixed(1), n: 3 },
    ],
    porStatus: STATUS_MOCK.map((s, i) => ({ chave: s, n: 6 - i })),
    porPrioridade: [{ chave: 'High', n: 8 }, { chave: 'Medium', n: 8 }, { chave: 'Low', n: 8 }],
    porIdade: [{ chave: '0-7', n: 6 }, { chave: '8-14', n: 6 }, { chave: '15-30', n: 7 }, { chave: '>30', n: 5 }],
    porVencimento: [{ chave: 'vencidas', n: 5 }, { chave: '7d', n: 7 }, { chave: 'depois', n: 6 }, { chave: 'sem', n: 6 }],
    linhas,
    unit: corrido ? 'corrido' : 'util',
    avisos: [{ codigo: 'AMOSTRA_PEQUENA', gravidade: 'informativo', mensagem: 'amostra pequena', params: { tipo: 'Checklist Review', n: '3', minimo: '5' } }],
  }
}

function negligenciadasMock(semReal: boolean) {
  const linha = (i: number, dias: number, campo: string) => ({
    key: `TG-${2300 + i}`,
    resumo: `Task fictícia ${i + 1}`,
    tipo: 'Checklist',
    status: STATUS_MOCK[i % STATUS_MOCK.length],
    responsavel: PESSOAS[i % PESSOAS.length],
    vence: i % 2 ? '2026-10-20' : null,
    labels: i % 3 === 0 ? ['LATE_SENT'] : [],
    dias,
    ultimoMovimento: { campo, dia: '2026-10-12', dias: 18 - i },
    semMovimentoReal: 18 - i > 7,
  })
  const listas = {
    semAtualizacao: [linha(0, 12, 'status'), linha(1, 9, 'assignee')],
    semResponsavel: [{ ...linha(2, 5, 'criacao'), responsavel: null }],
    vencidas: [linha(3, 3, 'duedate'), linha(4, 6, 'status')],
    bloqueadas: [linha(5, 4, 'status')],
    paradas: [linha(0, 22, 'status'), linha(5, 8, 'status')],
    aFazer: [linha(6, 41, 'criacao')],
  }
  const filtrar = (l: { semMovimentoReal: boolean }[]) => (semReal ? l.filter((x) => x.semMovimentoReal) : l)
  const f = Object.fromEntries(Object.entries(listas).map(([k, v]) => [k, filtrar(v)]))
  return {
    semAtualizacao: f.semAtualizacao.length,
    semResponsavel: f.semResponsavel.length,
    vencidas: f.vencidas.length,
    bloqueadas: f.bloqueadas.length,
    paradasNoStatus: f.paradas.length,
    aFazerAntigas: f.aFazer.length,
    listas: f,
    diasSemAtualizacao: 7,
    semMovimentoReal: semReal,
    unit: 'util',
    avisos: [] as unknown[],
  }
}

function fechadasMock(corrido: boolean) {
  const f = corrido ? 1.4 : 1
  const linhas = Array.from({ length: 18 }, (_, i) => ({
    key: `TG-${2000 + i}`,
    resumo: `Task fictícia ${i + 1}`,
    tipo: TIPOS_MOCK[i % 2],
    responsavel: PESSOAS[i % PESSOAS.length],
    resolvida: `2026-10-${String(1 + i).padStart(2, '0')}`,
    ciclo: +((2 + (i % 7)) * f).toFixed(1),
    lead: +((4 + (i % 9)) * f).toFixed(1),
    vence: `2026-10-${String(2 + i).padStart(2, '0')}`,
    prazo: i % 6 === 5 ? 'atrasada' : i % 6 === 4 ? 'ajustado' : 'noPrazo',
    pontos: i % 3 === 0 ? 3 : null,
    horasEstimadas: i % 4 === 0 ? 8 : null,
    horasGastas: i % 4 === 0 ? 10 : null,
  }))
  return {
    concluidas: 18,
    pontos: 18,
    comPontos: 6,
    ciclo: { p50: +(4.5 * f).toFixed(1), p85: +(7.1 * f).toFixed(1), n: 18 },
    lead: { p50: +(8 * f).toFixed(1), p85: +(11.5 * f).toFixed(1), n: 18 },
    noPrazo: 13,
    noPrazoAjustado: 15,
    comVencimento: 18,
    taxaNoPrazo: 13 / 18,
    taxaAjustada: 15 / 18,
    canceladas: 2,
    porSemana: [{ semana: '2026-10-05', n: 5 }, { semana: '2026-10-12', n: 7 }, { semana: '2026-10-19', n: 4 }, { semana: '2026-10-26', n: 2 }],
    dispersao: { p50: 4.5, p85: 7.1, p95: 8.2, pontos: linhas.map((l) => ({ key: l.key, dia: l.resolvida, ciclo: l.ciclo })) },
    porTipo: [{ tipo: 'Checklist', p50: 5, p85: 7.5, n: 9 }, { tipo: 'Checklist Review', p50: 3, p85: 5.2, n: 9 }],
    porProjeto: [{ chave: 'TG', n: 18 }],
    porPessoa: PESSOAS.map((p, i) => ({
      pessoa: p, finalizou: 4 - (i % 2), participou: 4 + (i % 2), pontos: i % 2 ? 6 : null, cicloP50: +(3 + i * 0.4).toFixed(1),
      horasEstimadas: i % 2 ? 16 : null, horasGastas: i % 2 ? 19 : null,
    })),
    linhas,
    semResolution: 1,
    unit: corrido ? 'corrido' : 'util',
    avisos: [{ codigo: 'DONE_SEM_RESOLUTION', gravidade: 'informativo', mensagem: 'x', params: { n: '1' } }],
  }
}

function metasMock(granularidade: string) {
  const periodos = granularidade === 'mes'
    ? [{ chave: '2026-09', universo: 14, noPrazo: 12 }, { chave: '2026-10', universo: 18, noPrazo: 13 }]
    : [{ chave: '2026-10-05', universo: 5, noPrazo: 5 }, { chave: '2026-10-12', universo: 7, noPrazo: 5 }, { chave: '2026-10-19', universo: 6, noPrazo: 3 }]
  const linha = (chave: string, universo: number, noPrazo: number) => ({
    chave, universo, noPrazo, noPrazoAjustado: Math.min(universo, noPrazo + 1),
    estado: noPrazo / universo >= 0.9 ? 'ok' : noPrazo / universo >= 0.8 ? 'atencao' : 'problema',
  })
  return {
    versaoRegras: 'metas v1, labels v1',
    universo: 18, noPrazo: 13, noPrazoAjustado: 15, noPrazoOriginal: 11, antecipadas: 5, emD1D2: 3, noDia: 8, atrasadas: 5, semVencimento: 2,
    taxaNoPrazo: 13 / 18, taxaAjustada: 15 / 18, taxaOriginal: 11 / 18, taxaAntecipadas: 5 / 18, alertaDataOriginal: true,
    metaNoPrazo: 0.9, metaAntecipadas: 0.35, granularidade,
    porPeriodo: periodos.map((p) => ({ ...p, noPrazoAjustado: p.noPrazo + 1, taxaNoPrazo: p.noPrazo / p.universo, antecipadas: 1, noDia: 2, atrasadas: p.universo - p.noPrazo })),
    distribuicao: [{ chave: 'D3_OU_ANTES', n: 2 }, { chave: 'D2', n: 1 }, { chave: 'D1', n: 2 }, { chave: 'D', n: 8 }, { chave: 'ATRASO_1_3', n: 3 }, { chave: 'ATRASO_MAIS_3', n: 2 }],
    porTipo: [linha('Checklist', 12, 8), linha('Checklist Review', 6, 5)],
    porPrioridade: [linha('High', 6, 5), linha('Medium', 8, 6), linha('Low', 4, 2)],
    porSquad: [linha('Alfa', 10, 8), linha('Beta', 8, 5)],
    porPessoa: PESSOAS.map((p, i) => linha(p, 4, 4 - (i % 3))),
    avisos: [] as unknown[],
  }
}

function negativasMock() {
  return {
    reabertas: 3, devolvidas: 2, canceladas: 2, concluidasComAtraso: 5, atrasadasSemJustificativa: 2, abertasVencidas: 4,
    estouroDeEstimativa: 3, muitasTrocas: 1, concluidas: 18,
    taxaReabertura: 3 / 18, taxaDevolucao: 2 / 18, taxaAtraso: 5 / 18, taxaAtrasoSemJustificativa: 2 / 18, limiteDaTaxa: 0.2,
    porResponsavel: PESSOAS.map((p, i) => ({ pessoa: p, reabertas: i % 2, devolvidas: (i + 1) % 2, atrasadas: i % 3 })),
    unit: 'util',
    avisos: [] as unknown[],
  }
}

function riscosMock() {
  const fatores = (f: [string, number][]) => f.map(([fator, pontos]) => ({ fator, pontos }))
  const linhas = [
    { key: 'TG-2101', resumo: 'Ajustar tela de login', tipo: 'Checklist', status: 'Em andamento', responsavel: 'ana', pontos: 85, nivel: 'alto', fatorPrincipal: 'IDADE_ACIMA_P85',
      fatores: fatores([['IDADE_ACIMA_P85', 30], ['VENCIDA', 25], ['PARADA', 15], ['SEM_ATUALIZACAO', 10], ['SEM_ESTIMATIVA', 5]]) },
    { key: 'TG-2108', resumo: 'Corrigir exportação', tipo: 'Checklist', status: 'Bloqueado', responsavel: 'carla', pontos: 63, nivel: 'alto', fatorPrincipal: 'PARADA',
      fatores: fatores([['PARADA', 15], ['BLOQUEADA', 15], ['ESTOURO_DE_HORAS', 15], ['IDADE_ACIMA_P50', 10], ['VENCE_EM_BREVE', 8]]) },
    { key: 'TG-2115', resumo: 'Revisar contrato da API', tipo: 'Checklist Review', status: 'Em revisão', responsavel: 'bruno', pontos: 30, nivel: 'medio', fatorPrincipal: 'IDADE_ACIMA_P85',
      fatores: fatores([['IDADE_ACIMA_P85', 30]]) },
  ]
  return {
    versaoRegras: 'risco v1', alto: 2, medio: 1, baixo: 5, emRisco: 3, limiteAlto: 60, limiteMedio: 30, linhas,
    sinaisPorPessoa: PESSOAS.map((p, i) => ({ pessoa: p, issuesEmRisco: i % 2, abertas: 3 + (i % 3) })),
    avisos: [] as unknown[],
  }
}

function previsaoMock(corrido: boolean) {
  const semanas = ['2026-09-28', '2026-10-05', '2026-10-12', '2026-10-19']
  const f = corrido ? 1.4 : 1
  return {
    versaoRegras: 'previsao v1', backlog: 24,
    termina: { p50: '2026-11-23', p85: '2026-12-07', p95: null, semanasP50: 4, semanasP85: 6, semanasP95: null },
    confianca: 'media', semanasComDado: 12, semanasHistorico: 16, limiteSemanas: 104, comChegada: true, entregasNaJanela: 48,
    vazaoSemanal: semanas.map((semana, i) => ({ semana, n: 5 + (i % 3) })),
    chegadaSemanal: semanas.map((semana, i) => ({ semana, n: 3 + (i % 2) })),
    quantos: [2, 4, 8].map((s) => ({ semanas: s, p50: s * 5, p85: s * 4, p95: s * 3 })),
    porIssue: [
      { key: 'TG-2101', resumo: 'Ajustar tela de login', tipo: 'Checklist', status: 'Em andamento', responsavel: 'ana', idade: +(12 * f).toFixed(1), restanteP50: +(4.5 * f).toFixed(1), restanteP85: +(7.8 * f).toFixed(1), n: 9 },
      { key: 'TG-2108', resumo: 'Corrigir exportação', tipo: 'Checklist', status: 'Bloqueado', responsavel: 'carla', idade: +(30 * f).toFixed(1), restanteP50: null, restanteP85: null, n: 2 },
    ],
    unit: corrido ? 'corrido' : 'util',
    avisos: [{ codigo: 'AMOSTRA_PEQUENA', gravidade: 'informativo', mensagem: 'x', params: { tipo: 'Checklist Review', n: '3', minimo: '5' } }],
  }
}

function epicosMock() {
  return {
    minItens: 5,
    linhas: [
      { id: 'a1b2c3d4e5f6', epico: 'TG-900', itens: 6, concluidos: 4, progresso: 0.666667, maisAntigo: '2026-09-10', vencidos: 1, pontos: 8, previsaoSemanasP85: null },
      { id: '0f1e2d3c4b5a', epico: 'TG-910', itens: 11, concluidos: 8, progresso: 0.727273, maisAntigo: '2026-10-01', vencidos: 0, pontos: null, previsaoSemanasP85: 3 },
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
  const numero = cartaoId.slice(3)
  const prefixo = cartaoId.slice(0, 2).toLowerCase()
  return {
    tituloChave: prefixo === 'ts' ? 'tempo.kpi.analisadas' : `card.${prefixo}${numero}.titulo`,
    definicaoChave: `card.${prefixo}${numero}.oQueMostra`,
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
  const trabalho = (tela: string, prefixo: string, ids: number[], natureza: string) =>
    ids.map((n) => {
      const numero = String(n).padStart(2, '0')
      const base = `card.${prefixo}${numero}`
      return {
        id: `${prefixo.toUpperCase()}-${numero}`,
        grupo: 'resultados',
        natureza,
        tela,
        tituloChave: `${base}.titulo`,
        oQueMostraChave: `${base}.oQueMostra`,
        comoCalculaChave: `${base}.comoCalcula`,
        fontes: ['ISSUE', 'CHANGELOG'],
        papeis: TODOS,
        temDrill: true,
        exigeChangelog: false,
        exigeWorklog: false,
      }
    })
  const intervalo = (n: number) => Array.from({ length: n }, (_, i) => i + 1)
  const tr = [
    ...trabalho('abertas', 'ab', [1, 2, 4, 5, 6, 7, 8, 9], 'FLUXO'),
    ...trabalho('negligenciadas', 'ng', intervalo(6), 'QUALIDADE'),
    ...trabalho('fechadas', 'fe', intervalo(12), 'FLUXO'),
    ...trabalho('metas', 'mt', intervalo(14), 'PRAZO'),
    ...trabalho('negativas', 'ne', [1, 2, 3, 4, 5, 6, 7, 8, 10], 'QUALIDADE'),
    ...trabalho('riscos', 'ri', intervalo(3), 'ESTOQUE').map((c) => ({ ...c, grupo: 'trabalho' })),
    ...trabalho('previsao', 'pv', intervalo(6), 'FLUXO').map((c) => ({ ...c, grupo: 'trabalho', temDrill: c.id !== 'PV-05' })),
    ...trabalho('epicos', 'ep', [1], 'ESTOQUE').map((c) => ({ ...c, grupo: 'trabalho' })),
  ]
  return { cartoes: [...ts, ...sy, ...au, ...tr, ...catalogoFluxo()] }
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
        else if (url.pathname === '/api/cards/abertas') corpo = abertasMock(url.searchParams.get('unit') === 'corrido')
        else if (url.pathname === '/api/cards/negligenciadas') corpo = negligenciadasMock(url.searchParams.get('movimento') === 'semReal')
        else if (url.pathname === '/api/cards/fechadas') corpo = fechadasMock(url.searchParams.get('unit') === 'corrido')
        else if (url.pathname === '/api/cards/metas') corpo = metasMock(url.searchParams.get('granularidade') ?? 'semana')
        else if (url.pathname === '/api/cards/negativas') corpo = negativasMock()
        else if (url.pathname === '/api/cards/riscos') corpo = riscosMock()
        else if (url.pathname === '/api/cards/previsao') corpo = previsaoMock(url.searchParams.get('unit') === 'corrido')
        else if (url.pathname === '/api/cards/epicos') corpo = epicosMock()
        else if (url.pathname === '/api/cards/fluxo-da-semana') corpo = fluxoMock()
        else if (url.pathname === '/api/cards/aging-wip') corpo = agingMock()
        else if (url.pathname === '/api/cards/bloqueios') corpo = bloqueiosMock()
        else if (url.pathname === '/api/cards/controle-ciclo') corpo = controleMock()
        else if (url.pathname === '/api/cards/cruzamentos') {
          corpo = cruzamentosMock(url.searchParams.get('linhas') ?? 'tipo', url.searchParams.get('colunas') ?? 'categoria', url.searchParams.get('medida') ?? 'contagem')
        } else if (url.pathname === '/api/auditoria/sinais') corpo = auditoria()
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
