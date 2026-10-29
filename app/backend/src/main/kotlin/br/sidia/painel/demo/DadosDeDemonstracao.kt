package br.sidia.painel.demo

import br.sidia.painel.AppContext
import br.sidia.painel.sync.CampoDeFiltro
import br.sidia.painel.sync.CamposDoDicionario
import br.sidia.painel.sync.ChangelogParser
import br.sidia.painel.sync.ChavesDeUsuario
import br.sidia.painel.sync.IssueLinks
import br.sidia.painel.db.emJson
import br.sidia.painel.db.tuplasEmJson
import br.sidia.painel.sync.SqliteIssueStore
import br.sidia.painel.sync.WorklogParser
import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.sql.DataSource

private val log = LoggerFactory.getLogger("br.sidia.painel.demo")

/** Uma instância das fixtures: arquivo JSON, prefixo das chaves (`DEMO`, `DEMOCAT`) e o campo do OS Version. */
data class InstanciaDemo(
    val arquivo: String,
    val prefixo: String,
    val osVersionCustomId: String?,
    /** Campos do dicionário (nome -> id fictício `customfield_9000N`) usados nas issues fictícias; só no manifesto. */
    val camposCustomIds: Map<String, String> = emptyMap(),
)

/** Manifesto das fixtures. As datas absolutas dos JSON giram em torno da [ancora]. */
data class ManifestoDemo(val versao: Int, val ancora: LocalDate, val instancias: Map<String, InstanciaDemo>)

/** O que a carga gravou numa instância. */
data class ResumoDemo(val instancia: String, val issues: Int, val eventos: Int, val vinculos: Int = 0, val decisoes: Int = 0)

/** A carga (ou a subida) foi recusada de propósito; [codigo] é um identificador estável, sem dados. */
class DemoRecusadaException(val codigo: String) : IllegalStateException("dados de demonstração recusados: $codigo")

@Serializable
private data class ManifestoYaml(val versao: Int, val ancora: String, val instancias: Map<String, InstanciaYaml>)

@Serializable
private data class InstanciaYaml(
    val arquivo: String,
    val prefixo: String,
    val osVersionCustomId: String? = null,
    val camposCustomIds: Map<String, String> = emptyMap(),
)

private val FORMATO_PREFIXO = Regex("[A-Z]{1,20}")

private fun lerRecurso(pasta: String, nome: String): String {
    val caminho = "/$pasta/$nome"
    val fluxo = DadosDeDemonstracao::class.java.getResourceAsStream(caminho)
        ?: throw IllegalStateException("recurso de demonstração ausente: $caminho")
    return fluxo.use { it.readBytes().toString(Charsets.UTF_8) }
}

/** Lê `manifesto.yaml` da [pasta] de recursos e valida prefixos e arquivos. */
internal fun lerManifestoDemo(pasta: String = "demo"): ManifestoDemo {
    val bruto = Yaml.default.decodeFromString(ManifestoYaml.serializer(), lerRecurso(pasta, "manifesto.yaml"))
    require(bruto.instancias.isNotEmpty()) { "manifesto de demonstração sem instâncias" }
    val instancias = bruto.instancias.mapValues { (_, i) ->
        // O prefixo entra em LIKE: só letras maiúsculas, sem curingas.
        require(FORMATO_PREFIXO.matches(i.prefixo)) { "prefixo de demonstração inválido" }
        InstanciaDemo(i.arquivo, i.prefixo, i.osVersionCustomId, i.camposCustomIds)
    }
    return ManifestoDemo(bruto.versao, LocalDate.parse(bruto.ancora), instancias)
}

/** Issues (com `changelog.histories` embutido, no formato do Data Center) de um arquivo `{"issues": [...]}`. */
internal fun lerIssuesDemo(pasta: String, arquivo: String): List<JsonObject> =
    Json.parseToJsonElement(lerRecurso(pasta, arquivo)).jsonObject.getValue("issues").jsonArray.map { it.jsonObject }

private val FORMATO_INSTANTE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxx")
private val CAMPOS_DE_INSTANTE = setOf("created", "updated", "resolutiondate")
private val CHAVES_DE_DATA_NO_CHANGELOG = listOf("from", "to", "fromString", "toString")

/**
 * Carrega issues e changelog FICTÍCIOS (recursos `demo/`) no banco, sem tocar em nenhum servidor: cada issue passa
 * pelo mesmo caminho de gravação da sincronização (coleta de chaves de usuário, conversão do changelog, upsert).
 * Só roda em desenvolvimento com a flag do Jira desligada (ver [prepararDemonstracao]).
 */
class DadosDeDemonstracao(
    private val ds: DataSource,
    private val store: SqliteIssueStore,
    private val clock: Clock,
    private val zone: ZoneId,
    private val pasta: String = "demo",
) {
    /**
     * Apaga e regrava as issues de demonstração de todas as instâncias numa transação só. Antes de apagar
     * qualquer linha, recusa banco com issue que não seja de demonstração (dados reais).
     */
    fun carregar(): List<ResumoDemo> {
        val manifesto = lerManifestoDemo(pasta)
        val dias = deslocamentoDias(manifesto.ancora, LocalDate.now(clock.withZone(zone)))
        // Tudo é lido e deslocado ANTES de tocar no banco: fixture quebrada não deixa instância pela metade.
        val preparadas = manifesto.instancias.map { (id, inst) ->
            Triple(id, inst, lerIssuesDemo(pasta, inst.arquivo).map { deslocar(it, dias) })
        }
        // Verificação e gravação na MESMA conexão e transação: nada é apagado se houver dado real, e a carga é atômica.
        return ds.connection.use { con ->
            val autoCommit = con.autoCommit
            con.autoCommit = false
            try {
                exigirSemDadosReais(con, manifesto)
                val resumos = preparadas.map { (id, inst, issues) -> gravar(con, id, inst, issues) }
                con.commit()
                resumos
            } catch (e: Throwable) {
                con.rollback()
                throw e
            } finally {
                con.autoCommit = autoCommit
            }
        }
    }

    /**
     * Só age enquanto NÃO existe prova de sincronização real (checada na mesma transação); com a prova, não apaga nada.
     * Apaga SÓ as linhas fictícias da demonstração, numa transação única: para cada instância do manifesto, as issues cuja chave
     * é EXATAMENTE uma das chaves da fixture daquela instância (e tem o prefixo dela), com seus eventos e horas (cascata), os vínculos
     * e as decisões de distribuição dessas chaves. Issue de outro prefixo, de mesmo prefixo em outra instância ou de chave fora da
     * fixture nunca é tocada. Não escreve `sync_run` nem mexe em `sync_state` (o cursor zerado pela V7 fica como está).
     *
     * Se depois do expurgo ainda restar issue e o banco não tiver NENHUMA prova de sincronização real, o banco é desconhecido
     * (nem demonstração pura, nem dado sincronizado): desfaz tudo e recusa com `BANCO_DESCONHECIDO_SEM_SINCRONIZACAO`.
     */
    fun expurgar(): List<ResumoDemo> {
        val manifesto = lerManifestoDemo(pasta)
        // As chaves vêm das fixtures (leitura de recurso, antes de tocar no banco).
        val chaves = manifesto.instancias.map { (id, inst) ->
            Triple(id, inst, lerIssuesDemo(pasta, inst.arquivo).map { (it["key"] as JsonPrimitive).content }
                .filter { it.startsWith("${inst.prefixo}-") })
        }
        return ds.connection.use { con ->
            val autoCommit = con.autoCommit
            con.autoCommit = false
            try {
                // Depois da primeira sincronização real o expurgo nunca mais apaga nada: o projeto do Jira real pode ter chaves
                // iguais às da fixture (DEMO-n), e essas issues, vínculos e decisões são dados de verdade. A prova é checada
                // NA MESMA transação; a primeira subida com a flag roda antes do agendador, então ainda não há sincronização.
                val resumos = if (br.sidia.painel.sync.SincronizacaoReal.houveTentativa(con)) {
                    emptyList()
                } else {
                    val apagados = chaves.map { (id, _, lista) -> expurgarInstancia(con, id, lista) }
                    // Sem prova de sincronização, o que sobra de issue não é demonstração nem dado sincronizado: banco desconhecido.
                    if (existeIssue(con)) throw DemoRecusadaException("BANCO_DESCONHECIDO_SEM_SINCRONIZACAO")
                    apagados
                }
                con.commit()
                resumos
            } catch (e: Throwable) {
                con.rollback()
                throw e
            } finally {
                con.autoCommit = autoCommit
            }
        }
    }

    private fun existeIssue(con: Connection): Boolean =
        con.createStatement().use { st -> st.executeQuery("SELECT EXISTS (SELECT 1 FROM issue)").use { it.next() && it.getBoolean(1) } }

    private fun expurgarInstancia(con: Connection, instancia: String, chaves: List<String>): ResumoDemo {
        if (chaves.isEmpty()) return ResumoDemo(instancia, 0, 0)
        val json = chaves.emJson()
        var eventos = 0
        con.prepareStatement(
            "SELECT count(*) FROM change_event WHERE issue_id IN (SELECT id FROM issue WHERE instance = ? AND key IN (SELECT value FROM json_each(?)))",
        ).use { ps ->
            ps.setString(1, instancia)
            ps.setString(2, json)
            ps.executeQuery().use { if (it.next()) eventos = it.getInt(1) }
        }
        // Vínculos e decisões não têm cascata: saem aqui, pela chave exata da fixture (e só desta instância).
        val vinculos = con.prepareStatement(
            "DELETE FROM issue_link WHERE instance = ? AND (from_key IN (SELECT value FROM json_each(?)) OR to_key IN (SELECT value FROM json_each(?)))",
        ).use { ps ->
            ps.setString(1, instancia)
            ps.setString(2, json)
            ps.setString(3, json)
            ps.executeUpdate()
        }
        val decisoes = con.prepareStatement(
            "DELETE FROM decisao_distribuicao WHERE instance = ? AND issue_key IN (SELECT value FROM json_each(?))",
        ).use { ps ->
            ps.setString(1, instancia)
            ps.setString(2, json)
            ps.executeUpdate()
        }
        // Eventos e horas saem pela cascata da issue.
        val issues = con.prepareStatement("DELETE FROM issue WHERE instance = ? AND key IN (SELECT value FROM json_each(?))").use { ps ->
            ps.setString(1, instancia)
            ps.setString(2, json)
            ps.executeUpdate()
        }
        return ResumoDemo(instancia, issues, eventos, vinculos, decisoes)
    }

    /** Se existe alguma issue de chave com prefixo de demonstração, em qualquer instância. */
    fun existemDados(): Boolean {
        val padroes = lerManifestoDemo(pasta).instancias.values.map { "${it.prefixo}-%" }
        return ds.connection.use { con ->
            con.prepareStatement("SELECT EXISTS (SELECT 1 FROM issue WHERE EXISTS (SELECT 1 FROM json_each(?) WHERE issue.key LIKE value))").use { ps ->
                ps.setString(1, padroes.emJson())
                ps.executeQuery().use { it.next() && it.getBoolean(1) }
            }
        }
    }

    /**
     * Na conexão da carga (mesma transação do apagar e regravar). É dado real toda issue que não seja de uma das
     * instâncias do manifesto COM o prefixo de demonstração dela; vale também para instâncias fora do manifesto.
     */
    private fun exigirSemDadosReais(con: Connection, manifesto: ManifestoDemo) {
        val pares = manifesto.instancias.map { (id, inst) -> listOf(id, "${inst.prefixo}-%") }
        val real = con.prepareStatement(
            "SELECT EXISTS (SELECT 1 FROM issue i WHERE NOT EXISTS (" +
                "SELECT 1 FROM json_each(?) d WHERE i.instance = json_extract(d.value, '$[0]') " +
                "AND i.key LIKE json_extract(d.value, '$[1]')))",
        ).use { ps ->
            ps.setString(1, pares.tuplasEmJson())
            ps.executeQuery().use { it.next() && it.getBoolean(1) }
        }
        if (real) throw DemoRecusadaException("BANCO_COM_DADOS_REAIS")
    }

    /** Grava uma instância na conexão e na transação do chamador (sem commit nem rollback aqui). */
    private fun gravar(con: Connection, instancia: String, inst: InstanciaDemo, issues: List<JsonObject>): ResumoDemo {
        var eventos = 0
        con.prepareStatement("DELETE FROM issue WHERE instance = ? AND key LIKE ?").use { ps ->
            ps.setString(1, instancia)
            ps.setString(2, "${inst.prefixo}-%")
            ps.executeUpdate()
        }
        // issue_link não tem FK nem cascata: os vínculos com ponta de prefixo de demonstração saem na mesma transação.
        con.prepareStatement("DELETE FROM issue_link WHERE instance = ? AND (from_key LIKE ? OR to_key LIKE ?)").use { ps ->
            ps.setString(1, instancia)
            ps.setString(2, "${inst.prefixo}-%")
            ps.setString(3, "${inst.prefixo}-%")
            ps.executeUpdate()
        }
        val agora = clock.instant()
        for (issue in issues) {
            val historicos = ((issue["changelog"] as? JsonObject)?.get("histories") as? JsonArray)
                .orEmpty().map { it.jsonObject }
            // A coleta das chaves JIRAUSER* roda sobre o JSON recebido, antes de qualquer sanitização.
            val chaves = ChavesDeUsuario.coletar(issue, historicos)
            val registros = ChangelogParser.toEvents(issue, historicos, chaves)
            // Os vínculos saem do JSON da fixture, como na sincronização (sem eles a demo ficaria sem pares).
            val chaveDaIssue = (issue["key"] as JsonPrimitive).content
            store.upsertNaTransacao(
                con, issue, registros, instancia, inst.osVersionCustomId, IssueLinks.ler(chaveDaIssue, issue), agora,
                worklogs = horasDaFixture(issue, chaves),
                camposDeFiltro = inst.camposCustomIds.map { (nome, id) -> CampoDeFiltro(nome, CamposDoDicionario.slugDe(nome), id) },
            )
            eventos += registros.size
        }
        return ResumoDemo(instancia, issues.size, eventos)
    }

    /** Soma [dias] a todo instante e a todo vencimento da issue (inclusive os itens `duedate` do changelog). */
    private fun deslocar(issue: JsonObject, dias: Long): JsonObject {
        if (dias == 0L) return issue
        val saida = LinkedHashMap<String, JsonElement>(issue)
        (issue["fields"] as? JsonObject)?.let { campos ->
            saida["fields"] = JsonObject(
                campos.mapValues { (nome, valor) ->
                    when {
                        nome in CAMPOS_DE_INSTANTE -> deslocarTexto(valor) { deslocarInstante(it, dias) }
                        nome == "worklog" -> deslocarWorklog(valor, dias)
                        nome == "duedate" -> deslocarTexto(valor) { deslocarData(it, dias) }
                        else -> valor
                    }
                },
            )
        }
        (issue["changelog"] as? JsonObject)?.let { changelog ->
            val historicos = (changelog["histories"] as? JsonArray).orEmpty().map { h ->
                val historico = h.jsonObject
                val novo = LinkedHashMap<String, JsonElement>(historico)
                novo["created"] = deslocarTexto(historico["created"]) { deslocarInstante(it, dias) }
                novo["items"] = JsonArray((historico["items"] as? JsonArray).orEmpty().map { it.jsonObject }.map { item ->
                    if ((item["field"] as? JsonPrimitive)?.content.equals("duedate", ignoreCase = true)) {
                        val ajustado = LinkedHashMap<String, JsonElement>(item)
                        for (chave in CHAVES_DE_DATA_NO_CHANGELOG) {
                            item[chave]?.let { ajustado[chave] = deslocarTexto(it) { t -> deslocarData(t, dias) } }
                        }
                        JsonObject(ajustado)
                    } else {
                        item
                    }
                })
                JsonObject(novo)
            }
            saida["changelog"] = JsonObject(changelog + ("histories" to JsonArray(historicos)))
        }
        return JsonObject(saida)
    }

    /** Os registros de horas da fixture (`fields.worklog.worklogs`), lidos pelo mesmo leitor da sincronização; nulo se a issue não tem. */
    private fun horasDaFixture(issue: JsonObject, chaves: Map<String, String>) =
        (((issue["fields"] as? JsonObject)?.get("worklog") as? JsonObject)?.get("worklogs") as? JsonArray)
            ?.let { WorklogParser.toRecords(it.map { w -> w.jsonObject }, chaves) }

    /** Desloca `started` e `created` de cada registro de horas (a fixture traz as datas ancoradas em 30/10/2026). */
    private fun deslocarWorklog(valor: JsonElement, dias: Long): JsonElement {
        val campo = valor as? JsonObject ?: return valor
        val registros = (campo["worklogs"] as? JsonArray) ?: return valor
        val novos = registros.map { r ->
            val w = r as? JsonObject ?: return@map r
            JsonObject(
                w.mapValues { (nome, v) ->
                    if (nome == "started" || nome == "created") deslocarTexto(v) { deslocarInstante(it, dias) } else v
                },
            )
        }
        return JsonObject(campo + ("worklogs" to JsonArray(novos)))
    }

    private fun deslocarTexto(valor: JsonElement?, f: (String) -> String): JsonElement =
        when {
            valor == null || valor is JsonNull -> JsonNull
            valor is JsonPrimitive && valor.isString -> JsonPrimitive(f(valor.content))
            else -> valor
        }

    /** O fuso fixo do painel (sem horário de verão) mantém a hora local ao somar dias. */
    private fun deslocarInstante(texto: String, dias: Long): String =
        ChangelogParser.parseInstant(texto).atZone(zone).plusDays(dias).format(FORMATO_INSTANTE)

    /** Aceita `aaaa-mm-dd` e `aaaa-mm-dd 00:00:00.0`; o que vem depois da data é preservado. */
    private fun deslocarData(texto: String, dias: Long): String =
        LocalDate.parse(texto.take(10)).plusDays(dias).toString() + texto.drop(10)

    companion object {
        /**
         * Deslocamento em semanas inteiras arredondado para baixo: `floor((hoje − âncora) / 7) × 7`. Pode ser
         * negativo. Preserva o dia da semana e deixa a âncora deslocada entre hoje − 6 dias e hoje.
         */
        fun deslocamentoDias(ancora: LocalDate, hoje: LocalDate): Long {
            val dias = java.time.temporal.ChronoUnit.DAYS.between(ancora, hoje)
            return Math.floorDiv(dias, 7L) * 7L
        }
    }
}

/**
 * Chamado na subida, logo depois de `AppContext.load`. Com `DASHBOARD_DEMO_DATA` carrega os dados fictícios
 * (só em desenvolvimento e com o Jira real desligado; qualquer outra combinação derruba a subida). Com a flag do Jira
 * ligada nunca carrega demonstração e APAGA os dados fictícios que restarem ([DadosDeDemonstracao.expurgar]: só as linhas do
 * manifesto, numa transação); banco que não é demonstração nem dado sincronizado de verdade derruba a subida.
 */
fun prepararDemonstracao(ctx: AppContext) {
    val s = ctx.settings
    val ds = ctx.dataSource ?: return
    val demo = DadosDeDemonstracao(ds, SqliteIssueStore(ds), ctx.clock, s.zone)
    try {
        if (!s.dashboardDemoData) {
            if (s.jiraLiveEnabled) {
                // Flag ligada: antes da primeira sincronização real o fictício sai sozinho (só as linhas do manifesto, numa
                // transação); depois dela o expurgo não apaga nada.
                val resumo = demo.expurgar()
                if (resumo.sumOf { it.issues + it.vinculos + it.decisoes } > 0) {
                    br.sidia.painel.db.CacheDeLeituras.invalidar()
                    log.info(
                        "Dados de demonstração (fictícios) apagados na subida: {}",
                        resumo.joinToString("; ") {
                            "${it.instancia}: ${it.issues} issues, ${it.eventos} eventos, ${it.vinculos} vínculos, ${it.decisoes} decisões"
                        },
                    )
                }
                return
            }
            // Produção (sem modo dev) com a flag desligada nunca convive com dado fictício remanescente.
            if (!s.dashboardDevMode && demo.existemDados()) throw DemoRecusadaException("DEMO_RESIDUAL")
            return
        }
        // Defesa em profundidade: o Settings já recusa estas combinações, mas um Settings montado à mão não passa.
        if (!s.dashboardDevMode || s.jiraLiveEnabled) throw DemoRecusadaException("AMBIENTE_NAO_PERMITIDO")
        val resumo = demo.carregar()
        br.sidia.painel.db.CacheDeLeituras.invalidar()
        log.info(
            "Dados de demonstração carregados (fictícios): {}",
            resumo.joinToString("; ") { "${it.instancia}: ${it.issues} issues, ${it.eventos} eventos" },
        )
    } catch (e: DemoRecusadaException) {
        val orientacao = if (e.codigo == "BANCO_DESCONHECIDO_SEM_SINCRONIZACAO") {
            "faça backup de data/painel.db e VERIFIQUE as issues do banco (podem ser reais) antes de apagá-lo; confira também DASHBOARD_DEMO_DATA, DASHBOARD_DEV_MODE e a flag do Jira."
        } else {
            "apague o arquivo do banco (data/painel.db) e confira DASHBOARD_DEMO_DATA, DASHBOARD_DEV_MODE e a flag do Jira."
        }
        log.error("Subida recusada ({}): {}", e.codigo, orientacao)
        throw e
    }
}
