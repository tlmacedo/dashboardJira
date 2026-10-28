package br.sidia.painel.checklist

import br.sidia.painel.sync.ParDeChaves
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.SnapDeTeste.snap
import br.sidia.painel.trabalho.SnapDeTeste.trilha

/**
 * Cenário CR-1 do Plano 7 (fixtures fictícias): relógio `2026-10-30T12:00:00Z`, papéis do TRB-1, pai `Checklist` e
 * review `Checklist Review`. Quatro pares e um checklist sem review:
 *
 * | Par | Pai (resp., vence, resolvido) | Review (resp., vence, situação) | Alertas |
 * | P1 | TG-401 ana, 10-10, 10-09T12Z | TG-402 bruno, 10-10, resolvida 10-08T12Z, ativa 8 h | nenhum |
 * | P2 | TG-403 ana, 10-15, 10-14T12Z | TG-404 ana, 10-17, resolvida 10-16T12Z, ativa 24 h | MESMA_PESSOA, REVIEW_VENCE_DEPOIS, PAI_FECHADO_ANTES |
 * | P3 | TG-405 bruno, 10-20, reaberto 10-21T12Z | TG-406 carla, 10-20, resolvida 10-18T12:15Z, ativa 0,25 h | REVIEW_CURTA_COM_PAI_REABERTO |
 * | P4 | TG-407 carla, 10-28, aberto | TG-408 bruno, 10-25, aberta | REVIEW_VENCIDA |
 * | — | TG-409 ana, sem review | — | conta em "Checklists sem review" |
 */
object Cr1 {
    val agora = java.time.Instant.parse("2026-10-30T12:00:00Z")

    val p1Pai = snap(
        "TG-401", "Checklist", "ana", "2026-10-01T12:00:00Z", "Closed", resolution = "Done", resolvida = "2026-10-09T12:00:00Z",
        vence = "2026-10-10", eventos = trilha("TG-401", "Open", "2026-10-05T12:00:00Z" to "In Progress", "2026-10-09T12:00:00Z" to "Closed"),
        labels = listOf("android"),
    )
    val p1Review = snap(
        "TG-402", "Checklist Review", "bruno", "2026-10-03T12:00:00Z", "Closed", resolution = "Done", resolvida = "2026-10-08T12:00:00Z",
        vence = "2026-10-10", eventos = trilha("TG-402", "Open", "2026-10-08T04:00:00Z" to "In Progress", "2026-10-08T12:00:00Z" to "Closed"),
        labels = listOf("android", "review"),
    )

    val p2Pai = snap(
        "TG-403", "Checklist", "ana", "2026-10-02T12:00:00Z", "Closed", resolution = "Done", resolvida = "2026-10-14T12:00:00Z",
        vence = "2026-10-15", eventos = trilha("TG-403", "Open", "2026-10-06T12:00:00Z" to "In Progress", "2026-10-14T12:00:00Z" to "Closed"),
        labels = listOf("android"),
    )
    val p2Review = snap(
        "TG-404", "Checklist Review", "ana", "2026-10-04T12:00:00Z", "Closed", resolution = "Done", resolvida = "2026-10-16T12:00:00Z",
        vence = "2026-10-17", eventos = trilha("TG-404", "Open", "2026-10-15T12:00:00Z" to "In Progress", "2026-10-16T12:00:00Z" to "Closed"),
    )

    val p3Pai = snap(
        "TG-405", "Checklist", "bruno", "2026-10-05T12:00:00Z", "In Progress", vence = "2026-10-20",
        eventos = trilha(
            "TG-405", "Open", "2026-10-12T12:00:00Z" to "In Progress", "2026-10-19T12:00:00Z" to "Closed",
            "2026-10-21T12:00:00Z" to "In Progress",
        ),
        labels = listOf("ui"),
    )
    val p3Review = snap(
        "TG-406", "Checklist Review", "carla", "2026-10-06T12:00:00Z", "Closed", resolution = "Done", resolvida = "2026-10-18T12:15:00Z",
        vence = "2026-10-20", eventos = trilha("TG-406", "Open", "2026-10-18T12:00:00Z" to "In Progress", "2026-10-18T12:15:00Z" to "Closed"),
    )

    val p4Pai = snap(
        "TG-407", "Checklist", "carla", "2026-10-08T12:00:00Z", "In Progress", vence = "2026-10-28",
        eventos = trilha("TG-407", "Open", "2026-10-20T12:00:00Z" to "In Progress"),
        labels = listOf("ui"),
    )
    val p4Review = snap("TG-408", "Checklist Review", "bruno", "2026-10-09T12:00:00Z", "Open", vence = "2026-10-25")

    val semReview = snap("TG-409", "Checklist", "ana", "2026-10-10T12:00:00Z", "Open", vence = "2026-11-05")

    val issues: List<IssueSnapshot> =
        listOf(p1Pai, p1Review, p2Pai, p2Review, p3Pai, p3Review, p4Pai, p4Review, semReview)

    /** Os vínculos já resolvidos (task = o Checklist, review = a Review), como o 4A entrega. */
    val vinculos = listOf(
        ParDeChaves("TG-401", "TG-402"), ParDeChaves("TG-403", "TG-404"),
        ParDeChaves("TG-405", "TG-406"), ParDeChaves("TG-407", "TG-408"),
    )
}
