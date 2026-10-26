package br.sidia.painel.sync

import java.time.LocalTime
import java.time.ZoneId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Ajustes da sincronização periódica. Só intervalos e limites: NÃO existe campo de modo nem de autorização.
 * O modo é derivado só da flag do Jira (`SyncMode.de(settings.jiraLiveEnabled)`).
 */
data class SyncSettings(
    /** Instâncias que podem ser sincronizadas (`SYNC_INSTANCIAS`); só restringe, nunca habilita. */
    val instancias: List<String> = listOf("samsung"),
    val intervalo: Duration = 10.minutes,
    val horaReconciliacao: LocalTime = LocalTime.of(3, 0),
    val horaRetencao: LocalTime = LocalTime.of(4, 0),
    val maxItens: Int = 50_000,
    val maxRequisicoes: Int = 2_000,
    val sobreposicao: Duration = 5.minutes,
    /** Fuso do servidor Jira (padrão Manaus até o `timeZone` do token ser lido). */
    val fusoDoServidor: ZoneId = ZoneId.of("America/Manaus"),
    val tempoMaximo: Duration = 60.minutes,
    /** A reconciliação nunca remove mais que esta fração da base local. */
    val maxFracaoRemocao: Double = 0.5,
)
