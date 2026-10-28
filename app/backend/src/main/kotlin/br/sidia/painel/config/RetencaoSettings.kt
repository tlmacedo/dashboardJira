package br.sidia.painel.config

/**
 * Prazo de retenção, em meses, de cada categoria de dado (Plano 11). Os valores finais são decisão do DPO; o padrão é 24 meses
 * para todas (decisão do usuário de 25/10).
 *  - [historicoMeses]: issue resolvida antiga, `change_event`, `worklog` e o mapa de chaves de usuário (`RETENTION_HISTORICO_MONTHS`);
 *  - [accessLogMeses]: `access_log` (`RETENTION_ACCESS_LOG_MONTHS`);
 *  - [decisoesMeses]: `decisao_distribuicao`, pela `decidido_em` (`RETENTION_DECISOES_MONTHS`);
 *  - [execucoesMeses]: `sync_run` e `backup_run` (`RETENTION_EXECUCOES_MONTHS`).
 */
data class RetencaoSettings(val historicoMeses: Int, val accessLogMeses: Int, val decisoesMeses: Int, val execucoesMeses: Int) {
    init {
        require(historicoMeses >= 1 && accessLogMeses >= 1 && decisoesMeses >= 1 && execucoesMeses >= 1) { "os prazos de retenção devem ser >= 1 mês" }
    }
}
