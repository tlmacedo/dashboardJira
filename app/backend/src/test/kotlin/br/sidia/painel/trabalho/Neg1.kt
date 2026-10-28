package br.sidia.painel.trabalho

/**
 * Cenário NEG-1 do Plano 5b (fixtures fictícias, mesmo relógio e calendário do TRB-1): período 01/10 a 30/10.
 *
 * - TG-131: fechada em 10-08, reaberta em 10-21 (dona então: ana), passa a bruno em 10-22 e fecha de novo em 10-23.
 * - TG-132: devolvida de Submitted para In Progress em 10-22 (carla), aberta e vencida (vence 10-25).
 * - TG-133: reaberta em 2026-09-20 (fora do período), `updated` em 10-25.
 * - TG-134: três trocas de responsável (ana, bruno, carla, ana) em 10-05, 10-06 e 10-07; fecha em 10-09.
 * - TG-135: cancelada (Won't Do) em 10-12.
 * - TG-136: concluída com atraso (vence 10-16, fecha 10-20), estimativa de 10 h e gasto de 14 h.
 * - TG-137: concluída com atraso (vence 10-19, fecha 10-21) com a label LATE_SENT.
 */
object Neg1 {
    fun issues(): List<Trb1.IssueDeTeste> = listOf(
        Trb1.issue(
            "TG-131", "Checklist", "bruno", "10-02T12", "Closed", resolution = "Done", resolvida = "10-23T12",
            eventos = Trb1.trilha(
                "TG-131", "Open", "10-03T12" to "In Progress", "10-08T12" to "Closed", "10-21T12" to "In Progress", "10-23T12" to "Closed",
            ) + Trb1.ev("TG-131", "assignee", "ana", "bruno", "10-22T12"),
        ),
        Trb1.issue(
            "TG-132", "Checklist", "carla", "10-10T12", "In Progress", vence = "2026-10-25", atualizada = "10-22T12",
            eventos = Trb1.trilha("TG-132", "Open", "10-14T12" to "Submitted", "10-22T12" to "In Progress"),
        ),
        Trb1.issue(
            "TG-133", "Checklist", "ana", "09-05T12", "In Progress", atualizada = "10-25T12",
            eventos = Trb1.trilha(
                "TG-133", "Open", "09-10T12" to "In Progress", "09-15T12" to "Closed", "09-20T12" to "In Progress",
            ),
        ),
        Trb1.issue(
            "TG-134", "Checklist", "ana", "10-01T12", "Closed", resolution = "Done", resolvida = "10-09T12",
            eventos = Trb1.trilha("TG-134", "Open", "10-02T12" to "In Progress", "10-09T12" to "Closed") +
                Trb1.ev("TG-134", "assignee", "ana", "bruno", "10-05T12") +
                Trb1.ev("TG-134", "assignee", "bruno", "carla", "10-06T12") +
                Trb1.ev("TG-134", "assignee", "carla", "ana", "10-07T12"),
        ),
        Trb1.issue(
            "TG-135", "Checklist", "bruno", "10-03T12", "Closed", resolution = "Won't Do", resolvida = "10-12T12",
            eventos = Trb1.trilha("TG-135", "Open", "10-12T12" to "Closed"),
        ),
        Trb1.issue(
            "TG-136", "Checklist", "carla", "10-06T12", "Closed", resolution = "Done", resolvida = "10-20T12", vence = "2026-10-16",
            estimativaS = 10 * 3600L, gastoS = 14 * 3600L,
            eventos = Trb1.trilha("TG-136", "Open", "10-07T12" to "In Progress", "10-20T12" to "Closed"),
        ),
        Trb1.issue(
            "TG-137", "Checklist", "ana", "10-07T12", "Closed", resolution = "Done", resolvida = "10-21T12", vence = "2026-10-19",
            labels = listOf("LATE_SENT"),
            eventos = Trb1.trilha("TG-137", "Open", "10-08T12" to "In Progress", "10-21T12" to "Closed"),
        ),
    )
}
