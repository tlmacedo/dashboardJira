package br.sidia.painel.demo

import br.sidia.painel.TestPaths
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.domain.UnmappedStatusException
import br.sidia.painel.fichasDeTeste
import br.sidia.painel.sync.ChangeEventRecord
import br.sidia.painel.sync.ChangelogParser
import br.sidia.painel.sync.ChavesDeUsuario
import br.sidia.painel.sync.IssueLinks
import br.sidia.painel.sync.ParesTaskReview
import br.sidia.painel.sync.VinculosConfig
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.IsoFields
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Confere as fixtures de demonstração SEM banco: vocabulário, datas, privacidade e a cobertura do cenário
 * (para o Plano 4 encontrar sinais de auditoria sem depender do código dele).
 */
class FixturesDeDemoTest {
    private val manifesto = lerManifestoDemo("demo")
    private val zona = ZoneId.of("America/Manaus")
    private val ancora: LocalDate = manifesto.ancora
    private val limiteDeInstante: Instant = ancora.minusDays(1).atTime(23, 59).atZone(zona).toInstant()

    private fun issues(instancia: String): List<JsonObject> {
        val inst = manifesto.instancias.getValue(instancia)
        return lerIssuesDemo("demo", inst.arquivo)
    }

    private val todas: List<JsonObject> get() = manifesto.instancias.keys.flatMap { issues(it) }

    private fun fields(i: JsonObject): JsonObject = i["fields"]!!.jsonObject
    private fun texto(o: JsonObject?, nome: String): String? = (o?.get(nome) as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun sub(i: JsonObject, campo: String, nome: String): String? = texto(fields(i)[campo] as? JsonObject, nome)
    private fun chave(i: JsonObject) = i["key"]!!.let { (it as JsonPrimitive).content }
    private fun tipo(i: JsonObject) = sub(i, "issuetype", "name")
    private fun status(i: JsonObject) = sub(i, "status", "name")
    private fun resolucao(i: JsonObject) = sub(i, "resolution", "name")
    private fun responsavel(i: JsonObject) = sub(i, "assignee", "name")
    private fun reporter(i: JsonObject) = sub(i, "reporter", "name")
    private fun vencimento(i: JsonObject): LocalDate? = texto(fields(i), "duedate")?.let { LocalDate.parse(it.take(10)) }
    private fun instante(i: JsonObject, campo: String): Instant? = texto(fields(i), campo)?.let(ChangelogParser::parseInstant)
    private fun historicos(i: JsonObject): List<JsonObject> =
        ((i["changelog"] as? JsonObject)?.get("histories") as? JsonArray).orEmpty().map { it.jsonObject }

    private fun eventos(i: JsonObject): List<ChangeEventRecord> =
        ChangelogParser.toEvents(i, historicos(i), ChavesDeUsuario.coletar(i, historicos(i)))

    private fun data(e: ChangeEventRecord): LocalDate = e.at.atZone(zona).toLocalDate()

    private val statusMap = StatusMap.fromYaml(TestPaths.configDir.resolve("status_map.exemplo.yaml"))

    @Test
    fun vocabularioDasFixturesEstaNoMapaENaFicha() {
        val tiposDaFicha = fichasDeTeste().getValue("sincronizacao").filtrosFixos.entries
            .first { it.key.equals("issuetype", ignoreCase = true) }.value.toSet()
        fun noMapa(s: String) = try { statusMap.category(s); true } catch (e: UnmappedStatusException) { false }
        val foraDoVocabulario = mutableListOf<String>()
        for (i in todas) {
            val epico = tipo(i) == "Epic"
            // Exceções deliberadas: o Epic (tipo) e o status Cancelled, que a ficha do Tempo em status exclui.
            if (!epico && tipo(i) !in tiposDaFicha) foraDoVocabulario += "${chave(i)}: tipo ${tipo(i)}"
            val usados = buildSet {
                status(i)?.let(::add)
                historicos(i).flatMap { (it["items"] as JsonArray).map { x -> x.jsonObject } }
                    .filter { texto(it, "field") == "status" }
                    .forEach { x -> texto(x, "fromString")?.let(::add); texto(x, "toString")?.let(::add) }
            }
            for (s in usados) {
                if (s == "Cancelled" || epico) continue
                if (!noMapa(s)) foraDoVocabulario += "${chave(i)}: status $s"
            }
        }
        assertEquals(emptyList(), foraDoVocabulario)
        assertTrue(todas.any { tipo(it) == "Epic" } && todas.any { status(it) == "Cancelled" })
    }

    @Test
    fun instantesAteAVesperaDaAncora() {
        val fora = mutableListOf<String>()
        for (i in todas) {
            for (campo in listOf("created", "updated", "resolutiondate")) {
                val t = instante(i, campo) ?: continue
                if (t.isAfter(limiteDeInstante)) fora += "${chave(i)}.$campo"
            }
            for (h in historicos(i)) {
                if (ChangelogParser.parseInstant(texto(h, "created")!!).isAfter(limiteDeInstante)) fora += "${chave(i)}.history"
            }
        }
        assertEquals(emptyList(), fora)
        // O vencimento é a única data que pode passar da âncora (e o cenário usa isso).
        assertTrue(todas.any { vencimento(it)?.isAfter(ancora) == true })
    }

    @Test
    fun fixturesDeDemoTemOsSeisCampos() {
        val ids = manifesto.instancias.getValue("samsung").camposCustomIds
        val nomes = listOf("Checklist Type", "SKU Type", "Model Name", "Model Category", "TG Request Type", "Carrier Code")
        assertEquals(nomes.sorted(), ids.keys.sorted())
        assertTrue(ids.values.all { it.matches(Regex("customfield_9000\\d")) }, "ids fictícios de 90001 a 90006")
        val issues = issues("samsung")
        for (nome in nomes) {
            val chaveJson = ids.getValue(nome)
            val valores = issues.mapNotNull { i ->
                when (val v = fields(i)[chaveJson]) {
                    is JsonObject -> texto(v, "value")
                    is JsonPrimitive -> v.takeIf { it.isString }?.content
                    else -> null
                }
            }.toSet()
            assertTrue(valores.isNotEmpty(), "nenhuma issue da demonstração traz $nome")
            if (nome in setOf("Checklist Type", "SKU Type", "Model Category", "TG Request Type")) {
                assertTrue(valores.size >= 2, "$nome precisa de ao menos dois valores para exercitar filtro e agrupamento: $valores")
            }
        }
        // Valores fictícios: sem e-mail nem host.
        val bruto = DadosDeDemonstracao::class.java.getResourceAsStream("/demo/samsung.json")!!.readBytes().toString(Charsets.UTF_8)
        assertTrue('@' !in bruto)
    }

    @Test
    fun fixturesSemDadosPessoais() {
        val ids = Regex("""(?m)^\s*-\s*id:\s*(\S+)""").findAll(
            Files.readString(TestPaths.configDir.resolve("papeis.exemplo.yaml")),
        ).map { it.groupValues[1] }.toSet()
        assertTrue("dev.alfa1" in ids)
        for (arquivo in manifesto.instancias.values.map { it.arquivo }) {
            val bruto = DadosDeDemonstracao::class.java.getResourceAsStream("/demo/$arquivo")!!.readBytes().toString(Charsets.UTF_8)
            assertTrue('@' !in bruto, "$arquivo tem '@'")
            for (proibido in listOf("emailAddress", "avatarUrls", "displayName", "accountId")) {
                assertTrue(proibido !in bruto, "$arquivo cita $proibido")
            }
            for (m in Regex("""https?://([^/"\s]+)""").findAll(bruto)) {
                assertTrue(m.groupValues[1].endsWith(".invalid"), "host fora de .invalid em $arquivo: ${m.groupValues[1]}")
            }
        }
        fun valida(p: String) =
            p in ids || p.startsWith("pl.") || p == "fora.do.diretorio" || Regex("(?i)JIRAUSER\\d+").matches(p)
        val pessoas = mutableSetOf<String>()
        for (i in todas) {
            listOf("assignee", "reporter", "creator").forEach { c -> sub(i, c, "name")?.let(pessoas::add) }
            for (h in historicos(i)) {
                texto(h["author"] as? JsonObject, "name")?.let(pessoas::add)
                for (x in (h["items"] as JsonArray).map { it.jsonObject }.filter { texto(it, "field") == "assignee" }) {
                    listOf("from", "to").forEach { k -> texto(x, k)?.let(pessoas::add) }
                }
            }
        }
        assertEquals(emptyList(), pessoas.filterNot(::valida))
    }

    // ---- Cobertura do cenário ----

    private val samsung get() = issues("samsung")
    private val concluidas = setOf("Done", "Closed")
    private val tiposDoCard = setOf("Checklist", "Checklist TG", "Checklist Review")
    private val coordenacao = setOf("coord.um", "gerente.exemplo")
    private val squadDe = mapOf(
        "dev.alfa1" to "Alfa", "dev.alfa2" to "Alfa", "lider.alfa" to "Alfa",
        "dev.beta1" to "Beta", "lider.beta" to "Beta", "dev.mudou" to "Beta",
    )
    private val liderDe = mapOf("Alfa" to "lider.alfa", "Beta" to "lider.beta")

    private fun abertaDoCard(i: JsonObject) =
        tipo(i) != "Epic" && status(i) !in concluidas && status(i) != "Cancelled"

    private fun autorizado(i: JsonObject, e: ChangeEventRecord): Boolean {
        val autor = e.authorRef
        val squad = squadDe[(if (e.field.equals("assignee", ignoreCase = true)) e.from else null) ?: responsavel(i)]
        return autor == reporter(i) || autor in coordenacao || (squad != null && autor == liderDe[squad])
    }

    private fun mudancas(i: JsonObject, campo: String) = eventos(i).filter { it.field.equals(campo, ignoreCase = true) }

    @Test
    fun cenarioCobreAuditoriaETempoEmStatus() {
        // --- Tempo em status: concluídas positivas ---
        val positivas = samsung.filter { tipo(it) in tiposDoCard && status(it) in concluidas && resolucao(it) != null }
        assertTrue(positivas.size >= 8, "poucas concluídas positivas: ${positivas.size}")
        fun fim(i: JsonObject) = instante(i, "resolutiondate")!!.atZone(zona).toLocalDate()
        assertTrue(positivas.any { vencimento(it) != null && !fim(it).isAfter(vencimento(it)) }, "falta concluída no prazo")
        assertTrue(positivas.any { vencimento(it) != null && fim(it).isAfter(vencimento(it)) }, "falta concluída com atraso")
        val justificadas = positivas.filter {
            val l = (fields(it)["labels"] as JsonArray).map { x -> (x as JsonPrimitive).content }
            "LATE_SENT" in l && "ONHOLDBY-PL" in l
        }
        assertTrue(justificadas.isNotEmpty(), "falta concluída com atraso justificado")
        assertTrue(positivas.map { fim(it).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) }.toSet().size >= 3, "poucas semanas")
        val os = positivas.mapNotNull { texto(fields(it)["customfield_12416"] as? JsonObject, "value") }.toSet()
        assertTrue(os.size >= 2, "falta mais de um OS Version")

        // --- Exceções ---
        assertTrue(samsung.any { resolucao(it) == "Won't Do" }, "falta a cancelada (Won't Do)")
        assertTrue(samsung.any { status(it) == "Closed" && resolucao(it) == null }, "falta a Closed sem resolution")
        assertTrue(samsung.any { tipo(it) == "Epic" }, "falta o Epic")
        assertTrue(samsung.any { status(it) == "Cancelled" }, "falta a Cancelled")

        // --- Abertas paradas ---
        val abertas = samsung.filter(::abertaDoCard)
        fun ultimaTransicao(i: JsonObject) = mudancas(i, "status").maxOfOrNull { it.at } ?: instante(i, "created")!!
        assertTrue(
            abertas.any { status(it) == "In Progress" && data1(ultimaTransicao(it)).isBefore(ancora.minusDays(20)) },
            "falta aberta em In Progress há mais de 10 dias úteis",
        )
        assertTrue(abertas.any { status(it) == "Blocked" }, "falta aberta em Blocked")
        assertTrue(
            abertas.any { data1(instante(it, "updated")!!).isBefore(ancora.minusDays(13)) },
            "falta aberta sem atualização há mais de 7 dias",
        )
        assertTrue(abertas.any { responsavel(it) == null }, "falta aberta sem responsável")
        assertTrue(abertas.any { responsavel(it) == "fora.do.diretorio" }, "falta aberta com responsável fora do diretório")
        assertTrue(
            abertas.any { status(it) == "To Do" && data1(instante(it, "created")!!).isBefore(ancora.minusDays(36)) },
            "falta aberta em To Do há mais de 30 dias",
        )

        // --- Trocas de responsável ---
        val trocas = samsung.flatMap { i -> mudancas(i, "assignee").map { i to it } }
        assertTrue(trocas.any { (i, e) -> !autorizado(i, e) && e.authorRef?.startsWith("dev.") == true }, "falta troca por usuário comum")
        assertTrue(
            trocas.any { (i, e) -> autorizado(i, e) && e.authorRef == liderDe[squadDe[e.from]] && e.authorRef != reporter(i) },
            "falta troca pelo Squad Leader do squad",
        )
        assertTrue(
            samsung.any { i ->
                val v = vencimento(i)
                val ts = mudancas(i, "assignee")
                v != null && ts.size >= 3 && ts.all { !data(it).isBefore(v.minusDays(2)) && !data(it).isAfter(v) }
            },
            "falta passa-bola (3 trocas até 2 dias do vencimento)",
        )
        assertTrue(
            samsung.any { i ->
                val ts = mudancas(i, "assignee")
                ts.withIndex().any { (n, e) -> ts.take(n).any { p -> p.from == e.to || p.to == e.to } }
            },
            "falta troca de volta a quem já teve",
        )

        // --- Trocas de vencimento ---
        val vencs = samsung.flatMap { i -> mudancas(i, "duedate").map { i to it } }
        assertTrue(vencs.any { (i, e) -> e.authorRef == responsavel(i) && e.from != null && e.to != null }, "falta vencimento trocado pelo responsável")
        assertTrue(vencs.any { (i, e) -> e.authorRef == reporter(i) && e.from != null && e.to != null }, "falta vencimento trocado pelo Reporter")
        assertTrue(vencs.any { (_, e) -> e.from != null && e.to == null }, "falta remoção de vencimento")
        assertTrue(vencs.any { (_, e) -> e.from == null && e.to != null }, "falta primeira definição de vencimento")
        val itensDeVencimento = samsung.flatMap { i ->
            historicos(i).flatMap { (it["items"] as JsonArray).map { x -> x.jsonObject } }.filter { texto(it, "field") == "duedate" }
        }
        assertTrue(
            itensDeVencimento.any {
                Regex("""\d{4}-\d{2}-\d{2}""").matches(texto(it, "from").orEmpty()) &&
                    Regex("""\d{4}-\d{2}-\d{2} 00:00:00\.0""").matches(texto(it, "fromString").orEmpty())
            },
            "falta item de duedate com from aaaa-mm-dd e fromString aaaa-mm-dd 00:00:00.0",
        )

        // --- Labels de atraso ---
        fun labelDeAtraso(e: ChangeEventRecord) =
            e.field.equals("labels", ignoreCase = true) && listOf("LATE_SENT", "LATE_REVIEW").any { l ->
                e.to.orEmpty().split(' ').contains(l) && !e.from.orEmpty().split(' ').contains(l)
            }

        val labels = samsung.flatMap { i -> eventos(i).filter(::labelDeAtraso).map { i to it } }
        assertTrue(labels.any { (i, e) -> instante(i, "resolutiondate")?.let { e.at.isAfter(it) } == true && !autorizado(i, e) && e.authorRef != responsavel(i) }, "falta label depois do fechamento por não autorizado")
        assertTrue(labels.any { (i, e) -> instante(i, "resolutiondate")?.let { e.at.isAfter(it) } == true && e.authorRef == responsavel(i) }, "falta label depois do fechamento pelo próprio responsável")
        assertTrue(labels.any { (i, e) -> vencimento(i)?.let { data(e).isAfter(it) } == true && instante(i, "resolutiondate")?.let { r -> !e.at.isAfter(r) } != false }, "falta label depois do vencimento")
        assertTrue(labels.any { (i, e) -> e.to.orEmpty().split(' ').contains("LATE_SENT") && vencimento(i)?.let { data(e).isBefore(it.minusDays(3)) } == true && e.authorRef == responsavel(i) }, "falta LATE_SENT com posse antecipada")

        // --- Pares Task x Checklist Review ligados por Blocks (nunca por parent) ---
        val porChave = samsung.associateBy(::chave)
        // Os vínculos saem do mesmo leitor da sincronização (o link aparece nas duas pontas e vira uma só linha).
        val vinculos = samsung.flatMap { IssueLinks.ler(chave(it), it).orEmpty() }.distinct()
        val bloqueios = vinculos.filter { it.tipo == "Blocks" }.map { it.fromKey to it.toKey }.toSet() // (bloqueia, é bloqueada)
        // Dois pares Task x Review e, do Plano 17b, dois bloqueios entre tasks abertas (DEMO-15 -> DEMO-13 e DEMO-26 -> DEMO-18).
        assertEquals(4, bloqueios.size, "a demo tem quatro vínculos Blocks: $bloqueios")
        assertEquals(
            bloqueios.size * 2,
            samsung.sumOf { i -> (fields(i)["issuelinks"] as? JsonArray).orEmpty().size },
            "cada link deve aparecer nas duas pontas",
        )
        assertTrue(samsung.none { fields(it).containsKey("parent") }, "as fixtures não usam parent")
        val tipoDe = samsung.associate { chave(it) to tipo(it).orEmpty() }
        val resolucao = ParesTaskReview.resolver(
            vinculos, tipoDe, VinculosConfig.fromYaml(TestPaths.configDir.resolve("vinculos.exemplo.yaml")),
        )
        assertEquals(2, resolucao.pares.size, "nenhum par pelo parent: só os dois Blocks")
        assertEquals(0, resolucao.ambiguas)
        val paresReviewBloqueiaTask = bloqueios.filter { (a, b) -> tipo(porChave.getValue(a)) == "Checklist Review" && tipo(porChave.getValue(b)) == "Task" }
        val paresTaskBloqueiaReview = bloqueios.filter { (a, b) -> tipo(porChave.getValue(a)) == "Task" && tipo(porChave.getValue(b)) == "Checklist Review" }
        assertTrue(paresReviewBloqueiaTask.isNotEmpty(), "falta par em que a Review bloqueia a task")
        assertTrue(paresTaskBloqueiaReview.isNotEmpty(), "falta par em que a task bloqueia a Review")
        val pares = (paresReviewBloqueiaTask + paresTaskBloqueiaReview).map { (a, b) -> vencimento(porChave.getValue(a)) to vencimento(porChave.getValue(b)) }
        assertTrue(pares.any { (x, y) -> x != null && x == y }, "falta par com vencimentos iguais")
        assertTrue(pares.any { (x, y) -> x != y }, "falta par com vencimentos divergentes")

        // --- Chave JIRAUSER* resolvida e não resolvida ---
        val resolvida = samsung.any { i ->
            ChavesDeUsuario.coletar(i, historicos(i)).isNotEmpty() &&
                mudancas(i, "assignee").any { e -> e.to != null && !ChavesDeUsuario.ehChaveInterna(e.to!!) } &&
                historicos(i).flatMap { (it["items"] as JsonArray).map { x -> x.jsonObject } }.any {
                    texto(it, "field") == "assignee" &&
                        (ChavesDeUsuario.ehChaveInterna(texto(it, "from").orEmpty()) || ChavesDeUsuario.ehChaveInterna(texto(it, "to").orEmpty()))
                }
        }
        assertTrue(resolvida, "falta JIRAUSER resolvida pelo objeto da issue")
        assertTrue(
            samsung.any { i -> mudancas(i, "assignee").any { e -> listOfNotNull(e.from, e.to).any(ChavesDeUsuario::ehChaveInterna) } },
            "falta JIRAUSER sem resolução",
        )

        // --- Estimativas e worklog (Plano 11): um caso fictício de cada sinal novo ---
        val estimativas = samsung.flatMap { i -> mudancas(i, "timeoriginalestimate").map { i to it } }
        fun numero(v: String?) = v?.toLongOrNull()
        assertTrue(
            estimativas.any { (i, e) ->
                val antes = numero(e.from)
                val depois = numero(e.to)
                val inicio = mudancas(i, "status").firstOrNull { it.to == "In Progress" }?.at
                antes != null && depois != null && (antes - depois) * 5 >= antes && inicio != null && e.at.isAfter(inicio)
            },
            "falta estimativa reduzida em pelo menos 20% depois de a task entrar em andamento",
        )
        assertTrue(
            estimativas.any { (_, e) -> numero(e.from) != null && numero(e.to) != null && numero(e.to)!! * 10 >= numero(e.from)!! * 13 },
            "falta estimativa aumentada em pelo menos 30% (Integridade)",
        )
        val feriados = br.sidia.painel.time.BusinessCalendar.fromYaml(TestPaths.configDir.resolve("calendario.exemplo.yaml"), zona)
        val worklogs = samsung.flatMap { i ->
            val lista = ((fields(i)["worklog"] as? JsonObject)?.get("worklogs") as? JsonArray).orEmpty().map { it.jsonObject }
            br.sidia.painel.sync.WorklogParser.toRecords(lista, emptyMap()).map { i to it }
        }
        assertTrue(worklogs.isNotEmpty(), "falta worklog na demonstração")
        assertTrue(worklogs.all { (_, w) -> w.criado != null && w.autor?.startsWith("dev.") == true }, "worklog com criação e autor fictício")
        fun diasUteis(w: br.sidia.painel.sync.WorklogRecord): Int {
            val ini = w.started.atZone(zona).toLocalDate()
            val fim = w.criado!!.atZone(zona).toLocalDate()
            return generateSequence(ini.plusDays(1)) { it.plusDays(1) }.takeWhile { !it.isAfter(fim) }.count { feriados.isBusinessDay(it) }
        }
        assertTrue(worklogs.any { (_, w) -> diasUteis(w) >= 2 }, "falta worklog registrado 2 dias úteis ou mais depois do início")
        assertTrue(worklogs.any { (_, w) -> diasUteis(w) < 2 }, "falta worklog registrado em dia (não retroativo)")

        // --- Duas instâncias e dois squads ---
        assertEquals(setOf("samsung", "sidia"), manifesto.instancias.keys)
        assertEquals(setOf("DEMO", "DEMOCAT"), manifesto.instancias.values.map { it.prefixo }.toSet())
        assertTrue(issues("sidia").size == 5)
        val squads = samsung.mapNotNull { responsavel(it) }.mapNotNull { squadDe[it] }.toSet()
        assertEquals(setOf("Alfa", "Beta"), squads)
    }

    private fun data1(i: Instant): LocalDate = i.atZone(zona).toLocalDate()
}
