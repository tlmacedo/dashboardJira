package br.sidia.painel.demo

import br.sidia.painel.AppContext
import br.sidia.painel.sync.ChangelogParser
import br.sidia.painel.sync.ChavesDeUsuario
import br.sidia.painel.sync.PostgresIssueStore
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
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.sql.DataSource

private val log = LoggerFactory.getLogger("br.sidia.painel.demo")

/** Uma instância das fixtures: arquivo JSON, prefixo das chaves (`DEMO`, `DEMOCAT`) e o campo do OS Version. */
data class InstanciaDemo(val arquivo: String, val prefixo: String, val osVersionCustomId: String?)

/** Manifesto das fixtures. As datas absolutas dos JSON giram em torno da [ancora]. */
data class ManifestoDemo(val versao: Int, val ancora: LocalDate, val instancias: Map<String, InstanciaDemo>)

/** O que a carga gravou numa instância. */
data class ResumoDemo(val instancia: String, val issues: Int, val eventos: Int)

/** A carga (ou a subida) foi recusada de propósito; [codigo] é um identificador estável, sem dados. */
class DemoRecusadaException(val codigo: String) : IllegalStateException("dados de demonstração recusados: $codigo")

@Serializable
private data class ManifestoYaml(val versao: Int, val ancora: String, val instancias: Map<String, InstanciaYaml>)

@Serializable
private data class InstanciaYaml(val arquivo: String, val prefixo: String, val osVersionCustomId: String? = null)

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
        InstanciaDemo(i.arquivo, i.prefixo, i.osVersionCustomId)
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
    private val store: PostgresIssueStore,
    private val clock: Clock,
    private val zone: ZoneId,
    private val pasta: String = "demo",
) {
    /**
     * Apaga e regrava as issues de demonstração de cada instância (uma transação por instância). Antes de apagar
     * qualquer linha, recusa banco com issue de chave sem o prefixo de demonstração (dados reais).
     */
    fun carregar(): List<ResumoDemo> {
        val manifesto = lerManifestoDemo(pasta)
        val dias = deslocamentoDias(manifesto.ancora, LocalDate.now(clock.withZone(zone)))
        // Tudo é lido e deslocado ANTES de tocar no banco: fixture quebrada não deixa instância pela metade.
        val preparadas = manifesto.instancias.map { (id, inst) ->
            Triple(id, inst, lerIssuesDemo(pasta, inst.arquivo).map { deslocar(it, dias) })
        }
        exigirSemDadosReais(manifesto)
        return preparadas.map { (id, inst, issues) -> gravar(id, inst, issues) }
    }

    /** Se existe alguma issue de chave com prefixo de demonstração, em qualquer instância. */
    fun existemDados(): Boolean {
        val padroes = lerManifestoDemo(pasta).instancias.values.map { "${it.prefixo}-%" }.toTypedArray()
        return ds.connection.use { con ->
            con.prepareStatement("SELECT EXISTS (SELECT 1 FROM issue WHERE key LIKE ANY (?))").use { ps ->
                ps.setArray(1, con.createArrayOf("text", padroes))
                ps.executeQuery().use { it.next() && it.getBoolean(1) }
            }
        }
    }

    private fun exigirSemDadosReais(manifesto: ManifestoDemo) {
        ds.connection.use { con ->
            for ((id, inst) in manifesto.instancias) {
                val real = con.prepareStatement(
                    "SELECT EXISTS (SELECT 1 FROM issue WHERE instance = ? AND key NOT LIKE ?)",
                ).use { ps ->
                    ps.setString(1, id)
                    ps.setString(2, "${inst.prefixo}-%")
                    ps.executeQuery().use { it.next() && it.getBoolean(1) }
                }
                if (real) throw DemoRecusadaException("BANCO_COM_DADOS_REAIS")
            }
        }
    }

    private fun gravar(instancia: String, inst: InstanciaDemo, issues: List<JsonObject>): ResumoDemo {
        var eventos = 0
        ds.connection.use { con ->
            val autoCommit = con.autoCommit
            con.autoCommit = false
            try {
                con.prepareStatement("DELETE FROM issue WHERE instance = ? AND key LIKE ?").use { ps ->
                    ps.setString(1, instancia)
                    ps.setString(2, "${inst.prefixo}-%")
                    ps.executeUpdate()
                }
                for (issue in issues) {
                    val historicos = ((issue["changelog"] as? JsonObject)?.get("histories") as? JsonArray)
                        .orEmpty().map { it.jsonObject }
                    // A coleta das chaves JIRAUSER* roda sobre o JSON recebido, antes de qualquer sanitização.
                    val chaves = ChavesDeUsuario.coletar(issue, historicos)
                    val registros = ChangelogParser.toEvents(issue, historicos, chaves)
                    store.upsertNaTransacao(con, issue, registros, instancia, inst.osVersionCustomId)
                    eventos += registros.size
                }
                con.commit()
            } catch (e: Throwable) {
                con.rollback()
                throw e
            } finally {
                con.autoCommit = autoCommit
            }
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
 * ligada e dados fictícios no banco, derruba a subida: nada é apagado automaticamente, use um volume novo.
 */
fun prepararDemonstracao(ctx: AppContext) {
    val s = ctx.settings
    val ds = ctx.dataSource ?: return
    val demo = DadosDeDemonstracao(ds, PostgresIssueStore(ds), ctx.clock, s.zone)
    try {
        if (!s.dashboardDemoData) {
            if (s.jiraLiveEnabled && demo.existemDados()) throw DemoRecusadaException("BANCO_COM_DADOS_DE_DEMONSTRACAO")
            return
        }
        // Defesa em profundidade: o Settings já recusa estas combinações, mas um Settings montado à mão não passa.
        if (!s.dashboardDevMode || s.jiraLiveEnabled) throw DemoRecusadaException("AMBIENTE_NAO_PERMITIDO")
        val resumo = demo.carregar()
        log.info(
            "Dados de demonstração carregados (fictícios): {}",
            resumo.joinToString("; ") { "${it.instancia}: ${it.issues} issues, ${it.eventos} eventos" },
        )
    } catch (e: DemoRecusadaException) {
        log.error("Subida recusada ({}): use um volume novo (docker compose down -v) e confira DASHBOARD_DEMO_DATA, DASHBOARD_DEV_MODE e JIRA_LIVE_ENABLED.", e.codigo)
        throw e
    }
}
