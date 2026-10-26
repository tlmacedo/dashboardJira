package br.sidia.painel.auditoria

/**
 * Os 11 sinais de auditoria da spec 7.2 (a Fase 4 detecta todos; Integridade é da Fase 7). O código é o
 * identificador estável: aparece no YAML de regras, nas respostas da API e no documento de diferenças.
 */
enum class CodigoSinal {
    VENCIMENTO_ALTERADO_NAO_AUTORIZADO,
    VENCIMENTO_REMOVIDO,
    VENCIMENTO_TASK_REVIEW_DIVERGENTE,
    RESPONSAVEL_TROCADO_NAO_AUTORIZADO,
    PASSA_BOLA,
    TROCA_PERTO_DO_VENCIMENTO,
    LABEL_APOS_FECHAMENTO_NAO_AUTORIZADO,
    LABEL_PELO_RESPONSAVEL_APOS_FECHAMENTO,
    LABEL_TARDIA_COM_POSSE_ANTECIPADA,
    LABEL_APOS_VENCIMENTO,
    CANCELAMENTO_SUSPEITO,
}
