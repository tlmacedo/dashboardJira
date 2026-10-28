package br.sidia.painel.checklist

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.fluxo.Bq1
import br.sidia.painel.cards.fluxo.comLinks
import br.sidia.painel.config.Settings
import br.sidia.painel.trabalho.Trb1

/**
 * O cenário CR-1 (ver [Cr1]) gravado no SQLite de teste, com os vínculos Blocks em as duas direções: P1 e P3 têm a
 * Review bloqueando o checklist (`inwardIssue` no checklist), P2 e P4 têm o checklist bloqueando a Review.
 */
object Cr1Banco {
    fun issues(): List<Trb1.IssueDeTeste> = listOf(
        // P1: nenhum alerta.
        Trb1.issue(
            "TG-401", "Checklist", "ana", "10-01T12", "Closed", resolution = "Done", resolvida = "10-09T12", vence = "2026-10-10",
            labels = listOf("android"), eventos = Trb1.trilha("TG-401", "Open", "10-05T12" to "In Progress", "10-09T12" to "Closed"),
        ).comLinks(bloqueadaPor = listOf("TG-402")),
        Trb1.issue(
            "TG-402", "Checklist Review", "bruno", "10-03T12", "Closed", resolution = "Done", resolvida = "10-08T12", vence = "2026-10-10",
            labels = listOf("android", "review"), eventos = Trb1.trilha("TG-402", "Open", "10-08T04" to "In Progress", "10-08T12" to "Closed"),
        ),
        // P2: mesma pessoa, review vence depois, pai fechado antes.
        Trb1.issue(
            "TG-403", "Checklist", "ana", "10-02T12", "Closed", resolution = "Done", resolvida = "10-14T12", vence = "2026-10-15",
            labels = listOf("android"), eventos = Trb1.trilha("TG-403", "Open", "10-06T12" to "In Progress", "10-14T12" to "Closed"),
        ).comLinks(bloqueia = listOf("TG-404")),
        Trb1.issue(
            "TG-404", "Checklist Review", "ana", "10-04T12", "Closed", resolution = "Done", resolvida = "10-16T12", vence = "2026-10-17",
            eventos = Trb1.trilha("TG-404", "Open", "10-15T12" to "In Progress", "10-16T12" to "Closed"),
        ),
        // P3: review de 15 minutos e pai reaberto.
        Trb1.issue(
            "TG-405", "Checklist", "bruno", "10-05T12", "In Progress", vence = "2026-10-20", labels = listOf("ui"),
            eventos = Trb1.trilha("TG-405", "Open", "10-12T12" to "In Progress", "10-19T12" to "Closed", "10-21T12" to "In Progress"),
        ).comLinks(bloqueadaPor = listOf("TG-406")),
        Trb1.issue(
            "TG-406", "Checklist Review", "carla", "10-06T12", "Closed", resolution = "Done", resolvida = "10-18T12:15", vence = "2026-10-20",
            eventos = Trb1.trilha("TG-406", "Open", "10-18T12" to "In Progress", "10-18T12:15" to "Closed"),
        ),
        // P4: review aberta e vencida.
        Trb1.issue(
            "TG-407", "Checklist", "carla", "10-08T12", "In Progress", vence = "2026-10-28", labels = listOf("ui"),
            eventos = Trb1.trilha("TG-407", "Open", "10-20T12" to "In Progress"),
        ).comLinks(bloqueia = listOf("TG-408")),
        Trb1.issue("TG-408", "Checklist Review", "bruno", "10-09T12", "Open", vence = "2026-10-25"),
        // Sem review.
        Trb1.issue("TG-409", "Checklist", "ana", "10-10T12", "Open", vence = "2026-11-05"),
    )

    /** Contexto do TRB-1 com o CR-1 gravado. [producao] liga o modo de produção (padrão do contexto dos cards). */
    fun contexto(
        banco: BancoEmArquivo, issues: List<Trb1.IssueDeTeste> = issues(), settings: Settings = Settings(dashboardDevMode = false),
    ): AppContext = Bq1.novoContexto(banco, issues, settings)
}
