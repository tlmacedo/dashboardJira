package br.sidia.painel.cards.fluxo

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.config.Settings
import br.sidia.painel.sync.IssueLinks
import br.sidia.painel.sync.SqliteIssueStore
import br.sidia.painel.testContextComBanco
import br.sidia.painel.trabalho.Trb1
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.sql.DataSource
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Acrescenta `fields.issuelinks` ao JSON da issue, como o Jira Data Center devolve: [bloqueia] são as chaves que ESTA issue
 * bloqueia (`outwardIssue`) e [bloqueadaPor] as que a bloqueiam (`inwardIssue`). Um dos lados da relação basta; a lista
 * vazia (com [emitir]) diz ao sincronizador que os vínculos da issue sumiram.
 */
fun Trb1.IssueDeTeste.comLinks(
    bloqueia: List<String> = emptyList(),
    bloqueadaPor: List<String> = emptyList(),
    tipo: String = "Blocks",
    emitir: Boolean = true,
): Trb1.IssueDeTeste {
    if (!emitir) return this
    fun item(chave: String, ponta: String) = buildJsonObject {
        putJsonObject("type") {
            put("name", tipo)
            put("inward", "is blocked by")
            put("outward", "blocks")
        }
        putJsonObject(ponta) { put("key", chave) }
    }
    val itens = bloqueia.map { item(it, "outwardIssue") } + bloqueadaPor.map { item(it, "inwardIssue") }
    val campos = json["fields"] as JsonObject
    val novosCampos = buildJsonObject {
        campos.forEach { (k, v) -> put(k, v) }
        put("issuelinks", JsonArray(itens))
    }
    val novo = buildJsonObject {
        json.forEach { (k, v) -> if (k != "fields") put(k, v) }
        put("fields", novosCampos)
    }
    return Trb1.IssueDeTeste(novo, eventos)
}

/**
 * Cenário BQ-1 (fixtures fictícias, relógio e pessoas do TRB-1) dos bloqueios entre tasks:
 *
 * - TG-801 (ana, vence 28/10) bloqueada por TG-802 (bruno, vence 03/11): bloqueadora ABERTA que vence DEPOIS → conflito.
 * - TG-803 (carla, vence 02/11) bloqueada por TG-804 (carla, vence 30/10): sem conflito.
 * - TG-805 (ana, On Hold) bloqueada só por TG-806, já concluída: não bloqueia, e o status bloqueado fica sem causa.
 * - TG-807 (Review do bruno) bloqueada por TG-808 (task da ana): par Task x Review, fora do card.
 * - TG-809 (ana) bloqueada por TG-9999, que não está na base: "verificar no Jira".
 * - TG-810 (bruno, com a flag de impedimento) sem nenhum vínculo: bloqueada sem causa registrada.
 * - TG-811 (ana, vence 05/11) bloqueada por TG-812 (carla, sem vencimento): para a ana a bloqueadora é de outro squad.
 * - TG-813 (ana) ligada a TG-802 por um vínculo de OUTRO tipo ("Relates"): ignorado.
 * - TG-814 (ana) sem vínculo; [comOVinculoDaTG814] a liga a TG-802 e [semOVinculoDaTG814] a desliga (`removida_em`).
 */
object Bq1 {
    /**
     * Como `Trb1.carregar`, mas grava também os vínculos (`issuelinks`) de cada issue, como o sincronizador faz. Quem tem o
     * campo `issuelinks` define os vínculos da issue (lista vazia os remove); quem não o tem não mexe neles.
     */
    fun carregar(ds: DataSource, issues: List<Trb1.IssueDeTeste>): SqliteIssueStore {
        val store = SqliteIssueStore(ds)
        for (i in issues) {
            val chave = (i.json["key"] as JsonPrimitive).content
            store.upsert(i.json, i.eventos, Trb1.INSTANCIA, Trb1.CUSTOM_OS_VERSION, IssueLinks.ler(chave, i.json), Trb1.agora, Trb1.ids)
        }
        return store
    }

    /** Contexto do TRB-1 com as [issues] (e os vínculos delas) já gravadas. */
    fun novoContexto(banco: BancoEmArquivo, issues: List<Trb1.IssueDeTeste> = issues(), settings: Settings = Settings(dashboardDevMode = false)): AppContext {
        val ctx = testContextComBanco(settings, banco, Trb1.papeis, Trb1.relogio, statusMap = Trb1.statusMap, calendar = Trb1.calendario)
        carregar(requireNotNull(ctx.dataSource), issues)
        return ctx
    }

    private fun abertaEmAndamento(chave: String, resp: String, vence: String?, status: String = "In Progress", tipo: String = "Checklist") =
        Trb1.issue(
            chave, tipo, resp, "10-20T12", status, vence = vence,
            eventos = if (status == "Open") emptyList() else Trb1.trilha(chave, "Open", "10-26T12" to status),
        )

    fun issues(): List<Trb1.IssueDeTeste> = listOf(
        abertaEmAndamento("TG-801", "ana", "2026-10-28").comLinks(bloqueadaPor = listOf("TG-802")),
        abertaEmAndamento("TG-802", "bruno", "2026-11-03"),
        abertaEmAndamento("TG-803", "carla", "2026-11-02", "Open").comLinks(bloqueadaPor = listOf("TG-804")),
        abertaEmAndamento("TG-804", "carla", "2026-10-30", "Open"),
        abertaEmAndamento("TG-805", "ana", null, "On Hold").comLinks(bloqueadaPor = listOf("TG-806")),
        Trb1.issue(
            "TG-806", "Checklist", "bruno", "10-01T12", "Closed", resolution = "Done", resolvida = "10-15T12",
            eventos = Trb1.trilha("TG-806", "Open", "10-15T12" to "Closed"),
        ),
        abertaEmAndamento("TG-807", "bruno", null, "Open", "Checklist Review").comLinks(bloqueadaPor = listOf("TG-808")),
        abertaEmAndamento("TG-808", "ana", null, "In Progress"),
        abertaEmAndamento("TG-809", "ana", null, "In Progress").comLinks(bloqueadaPor = listOf("TG-9999")),
        Trb1.issue("TG-810", "Checklist", "bruno", "10-20T12", "Open", flagged = true),
        abertaEmAndamento("TG-811", "ana", "2026-11-05", "Open").comLinks(bloqueadaPor = listOf("TG-812")),
        abertaEmAndamento("TG-812", "carla", null, "Open"),
        abertaEmAndamento("TG-813", "ana", null, "In Progress").comLinks(bloqueadaPor = listOf("TG-802"), tipo = "Relates"),
        abertaEmAndamento("TG-814", "ana", null, "In Progress"),
    )

    /** TG-814 bloqueada por TG-802 (o vínculo que depois some). */
    fun comOVinculoDaTG814(): Trb1.IssueDeTeste =
        abertaEmAndamento("TG-814", "ana", null, "In Progress").comLinks(bloqueadaPor = listOf("TG-802"))

    /** A segunda gravação de TG-814: o vínculo sumiu no Jira (lista vazia), então vira `removida_em`. */
    fun semOVinculoDaTG814(): Trb1.IssueDeTeste = abertaEmAndamento("TG-814", "ana", null, "In Progress").comLinks()
}
