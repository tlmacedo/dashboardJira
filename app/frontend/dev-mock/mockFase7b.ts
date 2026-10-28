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

/** Resposta do mock para as telas do bloco 7b; `undefined` quando a rota não é deste bloco. */
export function mockFase7b(pathname: string): unknown | undefined {
  if (pathname === '/api/cards/qualidade') return qualidadeMock()
  if (pathname === '/api/distribuicao') return distribuicaoMock()
  if (pathname === '/api/rebalanceamento') return rebalanceamentoMock()
  if (pathname === '/api/governanca/decisoes') return decisoesMock()
  return undefined
}
