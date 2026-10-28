package br.sidia.painel.pessoas

import br.sidia.painel.domain.AssigneeChange
import br.sidia.painel.domain.IssueTimeline
import br.sidia.painel.domain.Transition
import br.sidia.painel.time.BusinessCalendar
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.Trb1
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay

/**
 * Cenário CAP-1 do Plano 6 (fixtures fictícias): relógio sexta 30/10/2026 08:00 de Manaus (o mesmo do TRB-1), feriados
 * 02/11 e 20/11, pessoas `ana` e `bruno` (Alfa) e `carla` (Beta) dos papéis do TRB-1.
 *
 * Capacidade por dia útil: `ana` 5,6 h (40 h, foco 0,7, alocação 1,0), `bruno` 2,8 h (alocação 0,5), `carla` 5,6 h (foco
 * padrão). Ausências: `carla` 02 a 06/11, `bruno` 10/11 (0,5) e `ana` 04 e 05/11.
 */
object Cap1 {
    val zona = Trb1.zona
    val agora: Instant = Trb1.agora
    val relogio = Trb1.relogio
    val hoje: LocalDate = LocalDate.of(2026, 10, 30)
    val calendario = BusinessCalendar(zona, setOf(MonthDay.of(11, 2), MonthDay.of(11, 20)))
    val statusMap = Trb1.statusMap
    val papeis = Trb1.papeis
    val regras = RegrasDePlanejamento.PADRAO

    val equipes = Equipes(
        listOf(
            MembroEquipe("ana", 40.0, 0.7, 1.0, setOf("android", "rede")),
            MembroEquipe("bruno", 40.0, null, 0.5, setOf("android")),
            MembroEquipe("carla", 40.0, null, 1.0, setOf("rede", "ui")),
        ),
    )

    private fun dia(s: String) = LocalDate.parse(s)

    val ausencias = Ausencias(
        listOf(
            Ausencia("carla", dia("2026-11-02"), dia("2026-11-06"), 1.0),
            Ausencia("bruno", dia("2026-11-10"), dia("2026-11-10"), 0.5),
            Ausencia("ana", dia("2026-11-04"), dia("2026-11-05"), 1.0),
        ),
    )

    val capacidade = Capacidade(equipes, ausencias, calendario, regras)
    val capacidadeSemAusencia = Capacidade(equipes, Ausencias(emptyList()), calendario, regras)

    /** As quatro issues abertas do cenário, em dados simples (a mesma verdade para os testes de domínio e de rota). */
    data class Linha(
        val key: String, val resp: String, val status: String, val vence: String?, val restanteS: Long?, val prioridade: String?,
        val labels: List<String>, val os: String?, val criada: String, val iniciada: String? = null, val tipo: String = "Checklist",
    )

    val linhas = listOf(
        Linha("TG-201", "ana", "In Progress", "2026-11-04", 40320, "Medium", listOf("skill:android"), "Android 15", "10-20T12", "10-26T12"),
        Linha("TG-202", "ana", "Open", "2026-11-05", 20160, "Medium", listOf("skill:android"), "Android 15", "10-21T12"),
        Linha("TG-203", "bruno", "Open", "2026-11-05", 14400, "High", emptyList(), null, "10-22T12"),
        Linha("TG-205", "carla", "Open", "2026-10-28", null, "Medium", emptyList(), null, "10-10T12"),
    )

    /** Snapshot direto (sem banco), para os testes de domínio. */
    fun snap(l: Linha): IssueSnapshot {
        val criada = Trb1.t(l.criada)
        val transicoes = l.iniciada?.let { listOf(Transition(Trb1.t(it), "Open", l.status, null)) }.orEmpty()
        return snapshot(
            l.key, l.tipo, l.resp, l.status, criada, l.vence?.let(LocalDate::parse), l.restanteS, null, null, l.prioridade, l.labels, l.os, transicoes,
        )
    }

    @Suppress("LongParameterList")
    fun snapshot(
        key: String, tipo: String, resp: String?, status: String, criada: Instant, vence: LocalDate?, restanteS: Long?,
        estimativaS: Long?, gastoS: Long?, prioridade: String? = "Medium", labels: List<String> = emptyList(), os: String? = null,
        transicoes: List<Transition> = emptyList(),
    ) = IssueSnapshot(
        instance = Trb1.INSTANCIA, key = key, summary = "Task fictícia $key", issueType = tipo, priority = prioridade, status = status,
        resolution = null, created = criada, updated = criada, resolved = null, duedate = vence, assignee = resp, reporter = "pl.alfa",
        labels = labels, parentKey = null, epicKey = null, storyPoints = null, estimateS = estimativaS, remainingS = restanteS,
        spentS = gastoS, osVersion = os, flagged = false, components = emptyList(),
        timeline = IssueTimeline(
            key = key, summary = "Task fictícia $key", issueType = tipo, created = criada, initialStatus = "Open", initialAssignee = resp,
            transitions = transicoes, assigneeChanges = emptyList<AssigneeChange>(), resolved = null, resolution = null, currentStatus = status,
            currentAssignee = resp, labels = labels,
        ),
        events = emptyList(),
    )

    fun abertas(): List<IssueSnapshot> = linhas.map(::snap)

    /** As mesmas issues como JSON do Jira, para gravar no banco nos testes de rota (com `timeestimate` = restante). */
    fun issuesParaBanco(): List<Trb1.IssueDeTeste> = linhas.map { l ->
        val base = Trb1.issue(
            l.key, l.tipo, l.resp, l.criada, l.status, vence = l.vence, labels = l.labels, prioridade = l.prioridade,
            osVersion = l.os, eventos = l.iniciada?.let { Trb1.trilha(l.key, "Open", it to l.status) }.orEmpty(),
        )
        l.restanteS?.let { base.comRestante(it) } ?: base
    }

    private fun Trb1.IssueDeTeste.comRestante(segundos: Long): Trb1.IssueDeTeste {
        val campos = (json["fields"] as JsonObject).toMutableMap()
        campos["timeestimate"] = JsonPrimitive(segundos)
        val novo = buildJsonObject {
            json.forEach { (k, v) -> if (k != "fields") put(k, v) }
            put("fields", JsonObject(campos))
        }
        return Trb1.IssueDeTeste(novo, eventos)
    }
}
