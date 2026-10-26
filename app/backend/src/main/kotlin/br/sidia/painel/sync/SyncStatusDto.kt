package br.sidia.painel.sync

import kotlinx.serialization.Serializable

/**
 * Estado do sincronizador para o gerente (`GET /api/sync/status`). Só códigos, contagens e instantes:
 * nunca URL, token, usuário de serviço, JQL, mensagem de exceção do Jira nem username de pessoa.
 * [modo] é `DESLIGADO_PELA_FLAG` ou `PERIODICO` (derivado só da flag `JIRA_LIVE_ENABLED`).
 */
@Serializable
data class SyncStatusDto(
    val modo: String,
    val liveEnabled: Boolean,
    val instancias: List<InstanciaStatusDto>,
    val retencao: ExecucaoDto?,
) {
    companion object {
        const val DESLIGADO_PELA_FLAG = "DESLIGADO_PELA_FLAG"
        const val PERIODICO = "PERIODICO"
    }
}

/**
 * Uma instância do Jira. [gate] é `LIBERADA` ou o nome de um `MotivoNegacao`; [cursor] e
 * [ultimaReconciliacao] são instantes ISO-8601 (UTC); [suspensa] é o código da suspensão ou nulo.
 */
@Serializable
data class InstanciaStatusDto(
    val instancia: String,
    val gate: String,
    val cursor: String?,
    val ultimaReconciliacao: String?,
    val suspensa: String?,
    val execucoes: List<ExecucaoDto>,
) {
    companion object {
        const val LIBERADA = "LIBERADA"
    }
}

/** Execução registrada em `sync_run`; [codigoErro] é um código estável (`A-Z0-9_`), nunca texto livre. */
@Serializable
data class ExecucaoDto(
    val tipo: String,
    val status: String,
    val inicio: String,
    val fim: String?,
    val issues: Int,
    val eventos: Int,
    val requisicoes: Int,
    val incompletas: Int,
    val usuariosNaoResolvidos: Int,
    val removidas: Int,
    val codigoErro: String?,
)
