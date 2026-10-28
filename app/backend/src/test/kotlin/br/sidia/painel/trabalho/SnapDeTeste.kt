package br.sidia.painel.trabalho

import br.sidia.painel.sync.ChangeEventRecord
import br.sidia.painel.sync.ChangelogParser
import java.time.Instant
import java.time.LocalDate

/**
 * Construtor de [IssueSnapshot] em memória (sem banco) para os testes de domínio. A linha do tempo sai dos eventos
 * pelo mesmo `ChangelogParser.montarTimeline` da leitura real, então status e responsável anteriores valem como no
 * produto. Datas e chaves fictícias.
 */
object SnapDeTeste {
    private var contador = 0

    fun i(s: String): Instant = Instant.parse(s)

    fun ev(key: String, campo: String, de: String?, para: String?, quando: String, autor: String? = "pl.alfa") =
        ChangeEventRecord("$key:e${++contador}:0", key, campo, de, para, i(quando), autor)

    /** Eventos de status encadeados: cada `instante -> status` usa o status anterior como `from`. */
    fun trilha(key: String, inicial: String, vararg passos: Pair<String, String>, autor: String? = "pl.alfa"): List<ChangeEventRecord> {
        var atual = inicial
        return passos.map { (quando, para) -> ev(key, "status", atual, para, quando, autor).also { atual = para } }
    }

    @Suppress("LongParameterList")
    fun snap(
        key: String,
        tipo: String = "Checklist",
        resp: String? = "ana",
        criada: String = "2026-10-01T12:00:00Z",
        status: String = "Open",
        eventos: List<ChangeEventRecord> = emptyList(),
        resolution: String? = null,
        resolvida: String? = null,
        vence: String? = null,
        labels: List<String> = emptyList(),
        prioridade: String? = "Medium",
        reporter: String? = "pl.alfa",
        estimateS: Long? = null,
    ): IssueSnapshot {
        val created = i(criada)
        val resolved = resolvida?.let(::i)
        return IssueSnapshot(
            instance = Trb1.INSTANCIA, key = key, summary = "Task fictícia $key", issueType = tipo, priority = prioridade,
            status = status, resolution = resolution, created = created, updated = resolved ?: created, resolved = resolved,
            duedate = vence?.let(LocalDate::parse), assignee = resp, reporter = reporter, labels = labels, parentKey = null,
            epicKey = null, storyPoints = null, estimateS = estimateS, remainingS = null, spentS = null, osVersion = null,
            flagged = false, components = emptyList(),
            timeline = ChangelogParser.montarTimeline(
                key = key, summary = "Task fictícia $key", issueType = tipo, created = created, status = status, assignee = resp,
                resolved = resolved, resolution = resolution, labels = labels, events = eventos,
            ),
            events = eventos,
        )
    }
}
