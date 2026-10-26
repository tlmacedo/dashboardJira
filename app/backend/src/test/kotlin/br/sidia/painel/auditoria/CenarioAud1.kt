package br.sidia.painel.auditoria

import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.labelsDeAtrasoDeTeste
import br.sidia.painel.regrasDeAuditoriaDeTeste
import br.sidia.painel.security.AtribuicaoPapel
import br.sidia.painel.security.Pessoa
import br.sidia.painel.security.Role
import br.sidia.painel.security.RoleDirectory
import br.sidia.painel.security.Vigencia
import br.sidia.painel.sync.ChangeEventRecord
import br.sidia.painel.time.BusinessCalendar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Cenário AUD-1 do Plano 4 (papéis em memória) e construtores de teste dos detectores. A issue TG-10 nasce
 * em 2026-10-01T12:00Z, responsável inicial `ana`, vencimento 2026-10-15 (quinta), status "Open".
 */
class CenarioAud1(
    val regras: RegrasDeAuditoria = regrasDeAuditoriaDeTeste(),
    val calendario: BusinessCalendar = BusinessCalendar(ZoneId.of("America/Manaus")),
) {
    private fun d(s: String) = LocalDate.parse(s)
    fun i(s: String): Instant = Instant.parse(s)

    private fun usuario(id: String, squad: String) =
        Pessoa(id, id, listOf(AtribuicaoPapel(Role.USUARIO, Vigencia(null, null), squad)))

    val roles = RoleDirectory(
        listOf(
            usuario("pl.alfa", "Alfa"), usuario("ana", "Alfa"), usuario("bruno", "Alfa"), usuario("carla", "Beta"),
            usuario("davi", "Alfa"),
            Pessoa("lider.alfa", "x", listOf(AtribuicaoPapel(Role.SQUAD_LEADER, Vigencia(null, d("2026-10-09")), "Alfa"))),
            Pessoa("lider.alfa2", "x", listOf(AtribuicaoPapel(Role.SQUAD_LEADER, Vigencia(d("2026-10-10"), null), "Alfa"))),
            Pessoa("coord.um", "x", listOf(AtribuicaoPapel(Role.COORDENADOR, Vigencia(null, null)))),
            Pessoa("gerente.exemplo", "x", listOf(AtribuicaoPapel(Role.GERENTE, Vigencia(null, null)))),
        ),
    )

    val statusMap = StatusMap(
        mapOf("Open" to StatusCategory.TODO, "In Progress" to StatusCategory.ACTIVE, "Done" to StatusCategory.DONE, "Cancelled" to StatusCategory.DONE),
    )

    val ctx = ContextoDeteccao(
        regras, labelsDeAtrasoDeTeste(), Autorizacao(roles, ZoneId.of("America/Manaus")), calendario, statusMap,
    )

    private var seq = 0

    fun ev(campo: String, de: String?, para: String?, at: String, autor: String? = "pl.alfa") =
        ChangeEventRecord("TG-10:${++seq}:0", "TG-10", campo, de, para, i(at), autor)

    fun timeline(
        eventos: List<ChangeEventRecord>,
        assignee: String? = "ana",
        duedate: LocalDate? = d("2026-10-15"),
    ) = AuditTimeline(
        AuditIssue(
            "dc", "TG-10", "Tarefa", "Task", null, "pl.alfa", assignee, duedate,
            i("2026-10-01T12:00:00Z"), null, null, "Open", emptyList(), eventos,
        ),
        statusMap,
    )

    /** Roda os detectores de [objeto] sobre [eventos]. */
    fun detectar(
        detectores: List<Detector>,
        eventos: List<ChangeEventRecord>,
        assignee: String? = "ana",
        duedate: LocalDate? = d("2026-10-15"),
        contexto: ContextoDeteccao = ctx,
    ): List<Sinal> {
        val t = timeline(eventos, assignee, duedate)
        return detectores.flatMap { it.detectar(t, contexto) }
    }

    /** Variante do contexto com outras regras (limites, unidade, sinais desligados...). */
    fun comRegras(r: RegrasDeAuditoria) = ctx.copy(regras = r)
}
