package br.sidia.painel.integridade

import br.sidia.painel.sync.ChangeEventRecord
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.SnapDeTeste
import br.sidia.painel.trabalho.SnapDeTeste.ev
import br.sidia.painel.trabalho.SnapDeTeste.snap
import br.sidia.painel.trabalho.Trb1
import java.time.Instant

/**
 * Apoio do cenário INT-1 (fixtures fictícias): relógio `2026-10-30T12:00:00Z`, mapa de status, calendário e fuso do TRB-1
 * (Open/In Progress/Submitted/On Hold/Resolved/Closed/Cancelled; feriados 12/10 e 02/11; America/Manaus = UTC-4).
 */
object Int1 {
    val agora: Instant = Instant.parse("2026-10-30T12:00:00Z")
    val ctx = ContextoIntegridade(Trb1.statusMap, Trb1.zona, Trb1.calendario)
    val regras: RegrasDeIntegridade get() = RegrasDeIntegridade.PADRAO

    fun i(s: String): Instant = SnapDeTeste.i(s)

    /** Cópia das regras com os sinais indicados desligados. */
    @Suppress("unused")
    fun semOsSinais(vararg desligar: SinalIntegridade) = regras.copy(ativos = regras.ativos - desligar.toSet())

    /**
     * Task fechada: criada em [criada], em andamento desde [inicio] (por [resp]) e fechada em [fim] por [autorFim]. Os
     * [extras] são eventos a mais (troca de responsável, vencimento, labels...). Status final `Closed` com resolução `Done`.
     */
    @Suppress("LongParameterList")
    fun fechada(
        key: String,
        resp: String? = "ana",
        criada: String = "2026-10-01T12:00:00Z",
        inicio: String? = "2026-10-02T12:00:00Z",
        fim: String = "2026-10-14T12:00:00Z",
        autorFim: String? = resp,
        vence: String? = null,
        extras: List<ChangeEventRecord> = emptyList(),
        labels: List<String> = emptyList(),
        tipo: String = "Checklist",
        resolution: String = "Done",
    ): IssueSnapshot {
        val passos = buildList {
            if (inicio != null) add(ev(key, "status", "Open", "In Progress", inicio, resp))
            add(ev(key, "status", if (inicio != null) "In Progress" else "Open", "Closed", fim, autorFim))
        }
        return snap(
            key, tipo, resp, criada, "Closed", eventos = passos + extras, resolution = resolution, resolvida = fim, vence = vence, labels = labels,
        )
    }
}
