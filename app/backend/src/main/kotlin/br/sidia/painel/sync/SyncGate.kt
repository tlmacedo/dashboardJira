package br.sidia.painel.sync

/** Modo da sincronização, derivado SÓ da flag `JIRA_LIVE_ENABLED`. */
enum class SyncMode {
    DESLIGADO,
    PERIODICO,
    ;

    companion object {
        fun de(liveEnabled: Boolean): SyncMode = if (liveEnabled) PERIODICO else DESLIGADO
    }
}

/** Tipo de execução. A carga inicial é `INCREMENTAL` sem cursor. */
enum class SyncKind { INCREMENTAL, RECONCILIACAO, RETENCAO }

enum class MotivoNegacao { DESLIGADO_PELA_FLAG, INSTANCIA_NAO_CONFIGURADA, SUSPENSA_POR_ERRO }

sealed interface GateDecision {
    data object Liberada : GateDecision
    data class Negada(val motivo: MotivoNegacao) : GateDecision
}

/** Decide se uma execução pode falar com o Jira. Puro: sem relógio, rede ou banco. */
class SyncGate(private val liveEnabled: Boolean, private val cfg: SyncSettings) {
    val modo: SyncMode = SyncMode.de(liveEnabled)

    fun podeAgendar(): Boolean = liveEnabled

    /**
     * Ordem das regras: retenção não passa pelo gate (não fala com o Jira); flag desligada; instância fora da
     * lista; instância suspensa por erro; senão liberada.
     */
    fun avaliar(kind: SyncKind, instance: String, suspensa: Boolean): GateDecision {
        require(kind != SyncKind.RETENCAO) { "A retenção não fala com o Jira e não passa pelo SyncGate" }
        if (!liveEnabled) return GateDecision.Negada(MotivoNegacao.DESLIGADO_PELA_FLAG)
        if (instance !in cfg.instancias) return GateDecision.Negada(MotivoNegacao.INSTANCIA_NAO_CONFIGURADA)
        if (suspensa) return GateDecision.Negada(MotivoNegacao.SUSPENSA_POR_ERRO)
        return GateDecision.Liberada
    }
}
