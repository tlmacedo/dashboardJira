// Dados FICTÍCIOS do bloco 7a (Checklist × Review e Integridade) SÓ para o desenvolvimento visual (PAINEL_MOCK=1 pnpm dev).
// Não entra no bundle de produção. Nenhum dado real; nomes e chaves inventados.

function checklistReviewMock() {
  const alertas = ['REVIEW_VENCE_DEPOIS', 'PAI_FECHADO_ANTES', 'MESMA_PESSOA', 'REVIEW_CURTA_COM_PAI_REABERTO', 'REVIEW_VENCIDA', 'REVIEW_ATRASADA']
  const par = (n: number, extra: Record<string, unknown> = {}) => ({
    pai: `TG-${4000 + n}`, review: `TG-${4100 + n}`, resumoPai: `Checklist de teste ${n}`, resumoReview: `Review do checklist ${n}`,
    responsavelPai: 'ana', responsavelReview: n % 2 === 0 ? 'ana' : 'bruno', statusPai: 'Concluído', statusReview: 'Concluído',
    vencePai: '2026-10-15', venceReview: n % 3 === 0 ? '2026-10-17' : '2026-10-14', criacaoPai: '2026-10-01', criacaoReview: '2026-10-03',
    resolvidoPai: '2026-10-14', resolvidoReview: '2026-10-16', horasAtivasReview: 6.5 - n, paiReaberto: n % 4 === 0,
    alertas: [] as string[], ...extra,
  })
  const pares = [
    par(1), par(2, { alertas: ['MESMA_PESSOA'] }), par(3, { alertas: ['REVIEW_VENCE_DEPOIS', 'PAI_FECHADO_ANTES'] }),
    par(4, { alertas: ['REVIEW_CURTA_COM_PAI_REABERTO'], horasAtivasReview: 0.25 }),
    par(5, { alertas: ['REVIEW_VENCIDA'], statusReview: 'Em andamento', resolvidoReview: null }),
    par(6, { alertas: ['MESMA_PESSOA', 'REVIEW_VENCE_DEPOIS'] }),
  ]
  return {
    versaoRegras: 'checklist-review v1', unit: 'util', avisos: [],
    resumo: {
      pares: pares.length, semReview: 2, concluidas: 5, comAlerta: 5, porAlerta: Object.fromEntries(alertas.map((a) => [a, pares.filter((p) => p.alertas.includes(a)).length])),
      cicloReviewP50: 1.8, razaoReviewPaiP50: 0.35, criadaAposPaiHorasP50: 24, venceDepoisPct: 0.33, mesmaPessoaPct: 0.33, comAlertaPct: 0.83,
      duracaoXReabertura: [
        { faixa: '<1h', n: 1, taxaReabertura: 1, nBaixo: true }, { faixa: '1-4h', n: 0, taxaReabertura: null, nBaixo: true },
        { faixa: '4-12h', n: 4, taxaReabertura: 0.25, nBaixo: true }, { faixa: '>=12h', n: 0, taxaReabertura: null, nBaixo: true },
      ],
      labels: [{ label: 'ui', n: 3, taxaReabertura: 0.67, lift: 2.4, nBaixo: true }, { label: 'android', n: 6, taxaReabertura: 0.17, lift: 0.6, nBaixo: false }],
      diferencasDeLabels: [{ codigo: 'pai:ui', pares: 2 }, { codigo: 'review:rede', pares: 1 }],
      atrasosEmCadeia: 2, aprendizados: [{ codigo: 'MESMA_PESSOA_SEM_REVISAO_INDEPENDENTE', params: { n: '2', pct: '33' } }],
      amostraMinima: 5, limiteVenceDepois: 0.1, limiteMesmaPessoa: 0.1, limiteParesComAlerta: 0.3,
    },
    pares,
    semReview: [
      { key: 'TG-4201', resumo: 'Checklist sem review A', responsavel: 'ana', status: 'Em andamento' },
      { key: 'TG-4202', resumo: 'Checklist sem review B', responsavel: 'bruno', status: 'A fazer' },
    ],
  }
}

const SINAIS_MOCK = [
  'RESGATE_POR_PRORROGACAO', 'TROCA_ANTES_DE_FECHAR', 'CRIADA_E_FECHADA_RAPIDO', 'REABRE_E_FECHA_RAPIDO', 'FECHAMENTO_EM_LOTE', 'VENCIMENTO_PRORROGADO',
  'VENCIMENTO_TARDIO', 'PULOU_O_FLUXO', 'ESTIMATIVA_AUMENTADA', 'LABEL_CRITICIDADE_REMOVIDA', 'FECHADA_POR_OUTRO', 'FECHADA_NO_LIMITE',
]

function integridadeMock() {
  const evidencias = [
    { sinal: 'FECHADA_POR_OUTRO', gravidade: 2, key: 'TG-4301', pessoa: 'bruno', quando: '2026-10-14T13:00:00Z', dia: '2026-10-14', detalhe: { responsavel: 'ana' } },
    { sinal: 'VENCIMENTO_PRORROGADO', gravidade: 2, key: 'TG-4302', pessoa: 'bruno', quando: '2026-10-15T13:00:00Z', dia: '2026-10-15', detalhe: { dias: '3' } },
    { sinal: 'PULOU_O_FLUXO', gravidade: 3, key: 'TG-4303', pessoa: 'carla', quando: '2026-10-16T13:00:00Z', dia: '2026-10-16', detalhe: {} },
  ]
  return {
    aviso: 'INDICIO_NAO_PROVA', avisoDpo: 'VALIDAR_BASE_LEGAL_COM_DPO', versaoRegras: 'integridade v1', de: '2026-07-30', ate: '2026-10-28',
    resumo: {
      evidencias: evidencias.length, issuesAfetadas: 3, medianaTaxa: 0.1,
      porSinal: SINAIS_MOCK.map((s) => ({ sinal: s, gravidade: 2, ativo: s !== 'ESTIMATIVA_AUMENTADA', n: evidencias.filter((e) => e.sinal === s).length })),
      pessoas: [{ pessoa: 'bruno', nivel: 'MEDIO', pontos: 4, concluidas: 12, taxa: 0.33, multiplo: 3.3, graves: 0, evidencias: 2 }],
      pessoasAlto: 0, pessoasMedio: 1, evidenciasNivelAlto: 0, evidenciasNivelMedio: 2, impactoMetasPp: 6.5, taxaNoPrazo: 0.82, taxaOriginal: 0.75,
      pontosAlto: 12, multiploAlto: 3, gravesAlto: 3, pontosMedio: 6, multiploMedio: 2, concluidasMinimas: 8,
    },
    evidencias,
    avisos: [],
  }
}

/** Resposta do mock para os cards do bloco 7a; `undefined` quando a rota não é deste bloco. */
export function mockFase7a(pathname: string): unknown | undefined {
  if (pathname === '/api/cards/checklist-review') return checklistReviewMock()
  if (pathname === '/api/integridade') return integridadeMock()
  if (pathname === '/api/integridade/catalogo') {
    return {
      aviso: 'INDICIO_NAO_PROVA', avisoDpo: 'VALIDAR_BASE_LEGAL_COM_DPO', versaoRegras: 'integridade v1',
      sinais: SINAIS_MOCK.map((s) => ({ sinal: s, gravidade: 2, ativo: true, params: {} })),
      niveis: { pontosAlto: 12, multiploAlto: 3, gravesAlto: 3, pontosMedio: 6, multiploMedio: 2, concluidasMinimas: 8, pisoMediana: 0.05 },
    }
  }
  return undefined
}
