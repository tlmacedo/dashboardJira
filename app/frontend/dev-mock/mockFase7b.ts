// Dados FICTÍCIOS do bloco 7b (Distribuição, Rebalanceamento, Decisões, Governança e telas de início) SÓ para o desenvolvimento
// visual (PAINEL_MOCK=1 pnpm dev). Não entra no bundle de produção. Nenhum dado real; nomes e chaves inventados.

const fatores = (pessoa: string, score: number) => ({ pessoa, carga: 0.25, wip: 0.17, wipCheio: 0, competencias: 0, bonus: 0, score })

function distribuicaoMock() {
  return {
    modoSombra: true, versaoRegras: 'distribuicao v1', aguardando: 3, sugestoesPendentes: 2, jainAntes: 0.78, jainDepois: 0.93,
    candidatos: ['ana', 'bruno', 'carla'],
    sugestoes: [
      { issueKey: 'TG-4401', resumo: 'Checklist sem dono A', horas: 8, horasAssumidas: false, vence: '2026-11-10', comGerente: false, sugerido: 'carla',
        fatores: [fatores('ana', 0.42), fatores('bruno', 1.6), fatores('carla', 0.15)], decisao: null },
      { issueKey: 'TG-4402', resumo: 'Checklist com o gerente', horas: 4, horasAssumidas: true, vence: null, comGerente: true, sugerido: 'ana',
        fatores: [fatores('ana', 0.3), fatores('bruno', 1.2), fatores('carla', 0.5)], decisao: null },
      { issueKey: 'TG-4403', resumo: 'Checklist já decidido', horas: 6, horasAssumidas: false, vence: '2026-11-12', comGerente: false, sugerido: 'bruno',
        fatores: [fatores('ana', 0.8), fatores('bruno', 0.6), fatores('carla', 0.9)], decisao: 'APROVADA' },
    ],
    avisos: [],
  }
}

function rebalanceamentoMock() {
  return {
    modoSombra: true, versaoRegras: 'distribuicao v1', gatilho: 1.1, histerese: 20, movimentosPendentes: 1, candidatos: ['ana', 'bruno', 'carla'],
    movimentos: [
      { issueKey: 'TG-4411', resumo: 'Task grande não iniciada', de: 'bruno', para: 'carla', horas: 16, ocupacaoOrigemAntes: 1.38, ocupacaoOrigemDepois: 0.95, ocupacaoDestinoDepois: 0.29, decisao: null },
      { issueKey: 'TG-4412', resumo: 'Outra task não iniciada', de: 'bruno', para: 'ana', horas: 8, ocupacaoOrigemAntes: 0.95, ocupacaoOrigemDepois: 0.74, ocupacaoDestinoDepois: 0.4, decisao: 'APROVADA' },
    ],
    avisos: [],
  }
}

function decisoesMock() {
  return {
    de: '2026-09-28', ate: '2026-10-28', truncado: false,
    decisoes: [
      { tipo: 'DISTRIBUICAO', issueKey: 'TG-4403', sugerido: 'bruno', escolhido: 'bruno', decisao: 'APROVADA', motivo: null, decididoPor: 'coord.um', decididoEm: '2026-10-28T14:00:00Z', versaoRegras: 'distribuicao v1' },
      { tipo: 'REBALANCEAMENTO', issueKey: 'TG-4412', sugerido: 'ana', escolhido: 'carla', decisao: 'TROCADA', motivo: 'a carla conhece o módulo', decididoPor: 'gerente.exemplo', decididoEm: '2026-10-27T14:00:00Z', versaoRegras: 'distribuicao v1' },
    ],
  }
}

function qualidadeMock() {
  const l = (verificacao: string, n: number, universo: number, estado: string, nomes: string[] = []) => ({
    verificacao, n, universo, fracao: universo > 0 ? n / universo : null, estado, nomes,
  })
  return {
    versaoRegras: 'qualidade v1', janelaDias: 90, avisos: [],
    linhas: [
      l('SEM_ESTIMATIVA', 12, 90, 'ok'), l('SEM_RESPONSAVEL', 9, 90, 'ok'), l('SEM_VENCIMENTO', 28, 90, 'atencao'), l('SEM_EPICO', 0, 0, 'indisponivel', ['Epic Link']),
      l('EM_ANDAMENTO_SEM_HORAS', 14, 30, 'problema'), l('SEM_SQUAD', 3, 80, 'ok'), l('PRIORIDADE_PADRAO', 40, 90, 'problema'),
      l('DONE_SEM_RESOLUTION', 2, 60, 'ok'), l('RESOLUTION_SEM_DONE', 1, 58, 'ok'), l('STATUS_SEM_MAPEAMENTO', 0, 150, 'ok'),
      l('CAMPO_INDISPONIVEL', 1, 4, 'atencao', ['Epic Link']), l('LABEL_SEM_CLASSIFICACAO', 6, 40, 'neutro', ['REVISAR', 'SPRINT-9']), l('BLOQUEADA_SEM_CAUSA', 2, 5, 'problema'),
    ],
    labels: [{ label: 'REVISAR', n: 4 }, { label: 'SPRINT-9', n: 2 }],
  }
}

function acessosMock() {
  return {
    de: '2026-10-24', ate: '2026-10-30', proximo: null, tamanhoDaPagina: 200,
    acessos: [
      { quando: '2026-10-30T14:05:00Z', usuario: 'gerente.exemplo', rota: '/api/governanca/acessos', resultado: 200, filtros: ['de', 'ate'] },
      { quando: '2026-10-30T13:40:00Z', usuario: 'lider.alfa', rota: '/api/governanca/configuracao', resultado: 403, filtros: [] },
      { quando: '2026-10-30T13:10:00Z', usuario: 'coord.um', rota: '/api/cartoes/QD-01/tasks', resultado: 200, filtros: ['segmento'] },
    ],
  }
}

function configuracaoMock() {
  const a = (nome: string, versao: number | null, origem: string, n: string) => ({ nome, versao, origem, sha256: n.repeat(64).slice(0, 64) })
  return {
    devMode: true, liveEnabled: false, modoSync: 'DEMONSTRACAO', pessoas: 12, squads: 3,
    arquivos: [a('calendario', 1, 'exemplo', 'a'), a('cards/qualidade', null, 'real', 'b'), a('metas', 2, 'exemplo', 'c')],
  }
}

function centralMock() {
  const i = (item: string, n: number | null, estado: string, rota: string, cartao: string | null = 'VG-09', universo: number | null = null) => ({
    item, valor: n === null ? '—' : universo !== null ? `${n}/${universo}` : String(n), n, universo, estado, rota, cartao, segmento: cartao ? item : null,
  })
  const ok = { fracao: 0.8, semCapacidade: false, estado: 'ok' }
  return {
    kpis: [
      { ...i('ABERTAS', 90, 'neutro', 'abertas', 'VG-01'), segmento: 'abertas' },
      { ...i('VENCIDAS', 7, 'problema', 'negligenciadas', 'VG-05'), segmento: 'vencidas' },
      { ...i('RISCO_ALTO', 4, 'problema', 'riscos', 'VG-08'), segmento: 'alto' },
      { ...i('NO_PRAZO', 52, 'atencao', 'metas', 'VG-04', 70), segmento: 'noPrazo' },
      { ...i('NO_PRAZO_AJUSTADO', 60, 'ok', 'metas', 'VG-04', 70), segmento: 'noPrazoAjustado' },
    ],
    itens: [
      i('VENCIDAS', 7, 'problema', 'negligenciadas'), i('RISCO_ALTO', 4, 'problema', 'riscos'), i('ACIMA_DA_CAPACIDADE', 5, 'problema', 'carga'),
      i('ATRASOS_PREVISTOS', 3, 'problema', 'capacidade'), i('SEM_ATUALIZACAO', 9, 'atencao', 'negligenciadas'), i('PARADAS_NO_STATUS', 6, 'atencao', 'negligenciadas'),
      i('SEM_RESPONSAVEL', 2, 'atencao', 'negligenciadas'), i('BLOQUEADAS', 1, 'problema', 'negligenciadas'), i('AUSENCIAS_14D', 3, 'neutro', 'capacidade', null),
      i('COM_FOLGA', 2, 'neutro', 'carga'), i('META_DE_PRAZO', 52, 'atencao', 'metas', 'VG-09', 70), i('PARES_CR_COM_ALERTA', 4, 'atencao', 'checklistReview'),
      i('TASK_REVIEW_DIVERGENTE', 3, 'atencao', 'divergencia'), i('ALTERACOES_SEM_AUTORIZACAO', 2, 'problema', 'auditoria'),
      i('INDICIO_ALTO', 2, 'problema', 'integridade', 'VG-13'), i('SEM_ESTIMATIVA', 14, 'atencao', 'qualidade', 'VG-09', 90),
    ],
    entregasPorSemana: ['2026-08-10', '2026-08-17', '2026-08-24', '2026-08-31', '2026-09-07', '2026-09-14'].map((semana, k) => ({ semana, n: 4 + ((k * 3) % 7) })),
    backlogTermina: { p50: '2026-12-07', p85: '2026-12-21', p95: '2027-01-11', semanasP50: 6, semanasP85: 8, semanasP95: 11 },
    backlog: 30, confiancaDoBacklog: 'media',
    squads: [
      { squad: 'Alfa', ocupacaoMedia: ok, jain: 0.93, pessoas: 4 },
      { squad: 'Beta', ocupacaoMedia: { fracao: 1.18, semCapacidade: false, estado: 'problema' }, jain: 0.71, pessoas: 3 },
    ],
    avisos: [],
  }
}

function squadMock() {
  const ok = { fracao: 0.6, semCapacidade: false, estado: 'ok' }
  const l = (pessoa: string, abertas: number) => ({
    pessoa, abertas, wip: 2, filaH: 30, capacidade4SemH: 120, ocupacao: ok, ausenciaDias: 0, acimaDoP85: 1, vencidas: 1, riscoAlto: 1, horasAssumidas: false,
  })
  return {
    squad: 'Alfa', carga: [l('dev.alfa1', 6), l('dev.alfa2', 4)], riscoAlto: 2, riscoMedio: 3, avisos: [],
    vencimentos: {
      dias: 7, vencem: 2, riscoDeAtrasar: 1, avisos: [],
      linhas: [
        { key: 'TG-201', resumo: 'Tarefa fictícia', pessoa: 'dev.alfa1', vence: '2026-11-04', termino: '2026-11-03', folgaDiasUteis: 1, estado: 'ok' },
        { key: 'TG-202', resumo: 'Tarefa fictícia', pessoa: 'dev.alfa2', vence: '2026-11-05', termino: '2026-11-06', folgaDiasUteis: -1, estado: 'problema' },
      ],
    },
    ausencias: [{ pessoa: 'dev.alfa1', de: '2026-11-04', ate: '2026-11-05', fracao: 1 }],
  }
}

function meuPainelMock() {
  return {
    pessoa: 'dev', minhasAbertas: 2, vencemEm7d: 2, emRisco: 1, noPrazo: 15, noPrazoAjustado: 18, universoDePrazo: 20,
    ocupacao4Sem: { fracao: 0.25, semCapacidade: false, estado: 'ok' },
    ausenciasFuturas: [{ pessoa: 'dev', de: '2026-11-04', ate: '2026-11-05', fracao: 1 }],
    fila: [
      { key: 'TG-201', resumo: 'Tarefa fictícia', vence: '2026-11-04', termino: '2026-11-03', atrasa: false, nivelRisco: 'baixo' },
      { key: 'TG-202', resumo: 'Tarefa fictícia', vence: '2026-11-05', termino: '2026-11-06', atrasa: true, nivelRisco: 'alto' },
    ],
    esforcoDoMes: { pesoTipo: 3, testes: 2.5, horas: 12, indice: 17.5, versaoRegras: 'esforço v1' },
    avisos: [],
  }
}

/** Resposta do mock para as telas do bloco 7b; `undefined` quando a rota não é deste bloco. */
export function mockFase7b(pathname: string): unknown | undefined {
  if (pathname === '/api/cards/qualidade') return qualidadeMock()
  if (pathname === '/api/distribuicao') return distribuicaoMock()
  if (pathname === '/api/rebalanceamento') return rebalanceamentoMock()
  if (pathname === '/api/governanca/decisoes') return decisoesMock()
  if (pathname === '/api/central') return centralMock()
  if (pathname === '/api/squad') return squadMock()
  if (pathname === '/api/meu-painel') return meuPainelMock()
  if (pathname === '/api/governanca/acessos') return acessosMock()
  if (pathname === '/api/governanca/configuracao') return configuracaoMock()
  return undefined
}
