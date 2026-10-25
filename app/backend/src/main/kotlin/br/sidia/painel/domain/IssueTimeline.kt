package br.sidia.painel.domain

import java.time.Instant

/** Troca de status registrada no changelog. [authorRef] é a referência do autor no Jira (pode faltar). */
data class Transition(val at: Instant, val from: String?, val to: String, val authorRef: String?)

/** Troca de responsável; [to] nulo significa "sem responsável". */
data class AssigneeChange(val at: Instant, val to: String?)

/** Histórico de uma issue pronto para o cálculo de tempo por status e responsável. */
data class IssueTimeline(
    val key: String,
    val summary: String,
    val issueType: String,
    val created: Instant,
    val initialStatus: String,
    val initialAssignee: String?,
    val transitions: List<Transition>,
    val assigneeChanges: List<AssigneeChange>,
    val resolved: Instant?,
    val resolution: String?,
    val currentStatus: String,
    val currentAssignee: String?,
    val labels: List<String> = emptyList(),
)

/** Trecho contínuo em que a issue ficou num status com um responsável. */
data class StatusInterval(
    val status: String,
    val category: StatusCategory,
    val start: Instant,
    val end: Instant,
    val assignee: String?,
)

/** Primeira vez em que a issue entrou em [status] (transição), ou null se nunca entrou. */
fun IssueTimeline.firstEntered(status: String): Instant? =
    transitions.filter { it.to == status }.minOfOrNull { it.at }

/** Última vez em que a issue saiu de [status] (transição com `from`), ou null se nunca saiu. */
fun IssueTimeline.lastLeft(status: String): Instant? =
    transitions.filter { it.from == status }.maxOfOrNull { it.at }
