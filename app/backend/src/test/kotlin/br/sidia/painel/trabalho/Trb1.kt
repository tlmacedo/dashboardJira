package br.sidia.painel.trabalho

import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.security.AtribuicaoPapel
import br.sidia.painel.security.Pessoa
import br.sidia.painel.security.Role
import br.sidia.painel.security.RoleDirectory
import br.sidia.painel.security.Vigencia
import br.sidia.painel.sync.ChangeEventRecord
import br.sidia.painel.sync.SqliteIssueStore
import br.sidia.painel.time.BusinessCalendar
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.Clock
import java.time.Instant
import java.time.MonthDay
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.sql.DataSource

/**
 * Cenário TRB-1 do Plano 5 (fixtures fictícias): relógio sexta 30/10/2026 08:00 de Manaus, feriados 12/10 e 02/11,
 * pessoas `ana` e `bruno` (Alfa), `carla` (Beta), `lider.alfa`, `coord.um` e `gerente.exemplo`. Datas no formato
 * `MM-ddTHH` em UTC dentro de 2026 (ex.: `"10-05T12"` = 2026-10-05T12:00:00Z).
 */
object Trb1 {
    const val INSTANCIA = "samsung"
    val zona: ZoneId = ZoneId.of("America/Manaus")
    val agora: Instant = Instant.parse("2026-10-30T12:00:00Z")
    val relogio: Clock = Clock.fixed(agora, zona)
    val calendario = BusinessCalendar(zona, setOf(MonthDay.of(10, 12), MonthDay.of(11, 2)))

    val statusMap = StatusMap(
        mapOf(
            "Open" to StatusCategory.TODO,
            "In Progress" to StatusCategory.ACTIVE,
            "Submitted" to StatusCategory.WAIT,
            "On Hold" to StatusCategory.BLOCKED,
            "Resolved" to StatusCategory.DONE,
            "Closed" to StatusCategory.DONE,
            "Cancelled" to StatusCategory.DONE,
        ),
        ordem = listOf("Open", "In Progress", "Submitted", "Resolved", "Closed"),
        resolucoesNegativas = setOf("Won't Do", "Duplicate"),
    )

    private fun usuario(id: String, squad: String) = Pessoa(id, id, listOf(AtribuicaoPapel(Role.USUARIO, Vigencia(null, null), squad)))

    val papeis = RoleDirectory(
        listOf(
            usuario("ana", "Alfa"),
            usuario("bruno", "Alfa"),
            usuario("carla", "Beta"),
            Pessoa("lider.alfa", "lider.alfa", listOf(AtribuicaoPapel(Role.SQUAD_LEADER, Vigencia(null, null), "Alfa"))),
            Pessoa("coord.um", "coord.um", listOf(AtribuicaoPapel(Role.COORDENADOR, Vigencia(null, null)))),
            Pessoa("gerente.exemplo", "gerente.exemplo", listOf(AtribuicaoPapel(Role.GERENTE, Vigencia(null, null)))),
        ),
    )

    /** customId de `Story Points` e `Epic Link` usados nas issues do cenário. */
    val ids: Map<String, String?> = mapOf(
        "Story Points" to "customfield_10002", "Epic Link" to "customfield_10003", "Flagged" to "customfield_10004",
    )

    /** customId de `OS Version` usado nas issues do cenário. */
    const val CUSTOM_OS_VERSION = "customfield_12416"

    /** Os seis campos do dicionário com `filtro: true`, com os ids fictícios usados nas issues do cenário (Plano 10). */
    val camposDeFiltro: List<br.sidia.painel.sync.CampoDeFiltro> = listOf(
        "Checklist Type", "SKU Type", "Model Name", "Model Category", "TG Request Type", "Carrier Code",
    ).mapIndexed { i, nome ->
        br.sidia.painel.sync.CampoDeFiltro(nome, br.sidia.painel.sync.CamposDoDicionario.slugDe(nome), "customfield_${90001 + i}")
    }

    /** `"10-05T12"` -> `2026-10-05T12:00:00Z`; com minutos, `"10-05T12:15"` -> `2026-10-05T12:15:00Z`. */
    fun t(mmddhh: String): Instant = Instant.parse(if (mmddhh.length > 8) "2026-$mmddhh:00Z" else "2026-$mmddhh:00:00Z")

    private val formatoJira: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxx").withZone(ZoneOffset.UTC)

    /** Uma issue do cenário: o JSON do Jira e os eventos do changelog. */
    class IssueDeTeste(val json: JsonObject, val eventos: List<ChangeEventRecord>)

    private var contadorDeEventos = 0

    fun ev(key: String, campo: String, de: String?, para: String?, quando: String, autor: String = "pl.alfa") =
        ChangeEventRecord("$key:h${++contadorDeEventos}:0", key, campo, de, para, t(quando), autor)

    /** Eventos de status: cada par `quando -> status` encadeia o `from` no `to` anterior. */
    fun trilha(key: String, inicial: String, vararg passos: Pair<String, String>): List<ChangeEventRecord> {
        var atual = inicial
        return passos.map { (quando, para) -> ev(key, "status", atual, para, quando).also { atual = para } }
    }

    @Suppress("LongParameterList")
    fun issue(
        key: String,
        tipo: String,
        resp: String?,
        criada: String,
        status: String,
        eventos: List<ChangeEventRecord> = emptyList(),
        resolution: String? = null,
        resolvida: String? = null,
        vence: String? = null,
        labels: List<String> = emptyList(),
        prioridade: String? = null,
        estimativaS: Long? = null,
        gastoS: Long? = null,
        pontos: Double? = null,
        atualizada: String? = null,
        epico: String? = null,
        osVersion: String? = null,
        flagged: Boolean = false,
        /** Valores dos campos do dicionário (nome -> valor), gravados nos ids fictícios de [camposDeFiltro]. */
        campos: Map<String, String> = emptyMap(),
    ): IssueDeTeste {
        val json = buildJsonObject {
            put("key", key)
            putJsonObject("fields") {
                put("summary", "Task fictícia $key")
                putJsonObject("project") { put("key", "TG") }
                putJsonObject("issuetype") { put("name", tipo) }
                putJsonObject("status") { put("name", status) }
                resolution?.let { r -> putJsonObject("resolution") { put("name", r) } }
                resp?.let { r -> putJsonObject("assignee") { put("name", r) } }
                putJsonObject("reporter") { put("name", "pl.alfa") }
                put("created", formatoJira.format(t(criada)))
                put("updated", formatoJira.format(t(atualizada ?: resolvida ?: criada)))
                resolvida?.let { put("resolutiondate", formatoJira.format(t(it))) }
                vence?.let { put("duedate", it) }
                putJsonArray("labels") { labels.forEach { add(it) } }
                prioridade?.let { p -> putJsonObject("priority") { put("name", p) } }
                estimativaS?.let { put("timeoriginalestimate", it) }
                gastoS?.let { put("timespent", it) }
                pontos?.let { put("customfield_10002", it) }
                epico?.let { put("customfield_10003", it) }
                osVersion?.let { o -> putJsonObject(CUSTOM_OS_VERSION) { put("value", o) } }
                if (flagged) putJsonArray("customfield_10004") { add(buildJsonObject { put("value", "Impediment") }) }
                for (c in camposDeFiltro) campos[c.nome]?.let { v -> putJsonObject(c.customId!!) { put("value", v) } }
            }
        }
        return IssueDeTeste(json, eventos)
    }

    /** As dez issues do TRB-1 (tabela do plano). */
    fun issues(comPrioridade: Boolean = true): List<IssueDeTeste> {
        fun p(v: String) = v.takeIf { comPrioridade }
        return listOf(
            issue(
                "TG-111", "Checklist", "ana", "10-01T12", "Closed", resolution = "Done", resolvida = "10-09T12", vence = "2026-10-09",
                pontos = 3.0,
                eventos = trilha("TG-111", "Open", "10-05T12" to "In Progress", "10-09T12" to "Closed") +
                    ev("TG-111", "duedate", "2026-10-08", "2026-10-09", "10-07T12"),
            ),
            issue(
                "TG-112", "Checklist", "bruno", "10-02T12", "Closed", resolution = "Done", resolvida = "10-15T12", vence = "2026-10-15",
                pontos = 5.0, eventos = trilha("TG-112", "Open", "10-06T12" to "In Progress", "10-15T12" to "Closed"),
            ),
            issue(
                "TG-113", "Checklist", "ana", "10-05T12", "Closed", resolution = "Done", resolvida = "10-20T12", vence = "2026-10-16",
                labels = listOf("LATE_SENT"), eventos = trilha("TG-113", "Open", "10-07T12" to "In Progress", "10-20T12" to "Closed"),
            ),
            issue(
                "TG-114", "Checklist Review", "carla", "10-08T12", "Closed", resolution = "Done", resolvida = "10-14T12", vence = "2026-10-16",
                eventos = trilha("TG-114", "Open", "10-13T12" to "In Progress", "10-14T12" to "Closed"),
            ),
            issue(
                "TG-115", "Checklist", "bruno", "10-10T12", "Closed", resolution = "Won't Do", resolvida = "10-22T12",
                eventos = trilha("TG-115", "Open", "10-22T12" to "Closed"),
            ),
            issue(
                "TG-116", "Checklist", "ana", "10-03T12", "Closed", resolvida = "10-12T12",
                eventos = trilha("TG-116", "Open", "10-05T12" to "In Progress", "10-12T12" to "Closed"),
            ),
            issue(
                "TG-101", "Checklist", "ana", "09-28T12", "In Progress", vence = "2026-10-28", prioridade = p("High"),
                atualizada = "10-20T12", eventos = trilha("TG-101", "Open", "10-01T12" to "In Progress"),
            ),
            issue(
                "TG-102", "Checklist Review", "bruno", "09-15T12", "Open", prioridade = p("Medium"), estimativaS = 4 * 3600L,
                atualizada = "10-29T12",
            ),
            issue(
                "TG-103", "Checklist", "carla", "10-20T12", "On Hold", vence = "2026-11-02", prioridade = p("High"),
                estimativaS = 8 * 3600L, gastoS = 12 * 3600L, atualizada = "10-26T12",
                eventos = trilha("TG-103", "Open", "10-21T12" to "In Progress", "10-26T12" to "On Hold"),
            ),
            issue("TG-104", "Checklist", null, "10-29T12", "Open", prioridade = p("Medium")),
        )
    }

    /** Grava as issues na instância; devolve o store para os testes continuarem a partir dele. */
    fun carregar(
        ds: DataSource,
        issues: List<IssueDeTeste> = issues(),
        instancia: String = INSTANCIA,
    ): SqliteIssueStore {
        val store = SqliteIssueStore(ds)
        for (i in issues) store.upsert(i.json, i.eventos, instancia, CUSTOM_OS_VERSION, null, agora, ids, camposDeFiltro = camposDeFiltro)
        return store
    }
}
