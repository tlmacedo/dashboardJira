package br.sidia.painel.pessoas

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.TestPaths
import br.sidia.painel.config.Settings
import br.sidia.painel.module
import br.sidia.painel.security.Authenticator
import br.sidia.painel.security.Principal
import br.sidia.painel.testContextComBanco
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingNode
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Garantia de somente leitura das ausências e das equipes (decisão de produto: o YAML é versionado e o app só lê).
 * Dois guardas: (1) nenhuma rota de escrita sob as telas de leitura e (2) nenhum código de produção que cite os arquivos
 * de pessoas grave arquivo. Os detectores têm teste próprio com um caso injetado, para não virarem vácuo.
 */
class SomenteLeituraTest {
    private val prefixosDeLeitura = listOf("/api/cards", "/api/cartoes", "/api/drill", "/api/pessoas")
    private val metodosDeEscrita = Regex("""\(method:(POST|PUT|PATCH|DELETE)\)""")

    private val autenticador = object : Authenticator {
        override suspend fun authenticate(call: ApplicationCall): Principal? = null
    }

    private fun caminhosDasRotas(raiz: RoutingNode): List<String> {
        val caminhos = mutableListOf<String>()
        fun andar(r: RoutingNode) {
            caminhos += r.toString()
            r.children.forEach(::andar)
        }
        andar(raiz)
        return caminhos
    }

    /** Rotas de escrita (POST/PUT/PATCH/DELETE) sob os prefixos de leitura. */
    private fun rotasDeEscrita(raiz: RoutingNode): List<String> =
        caminhosDasRotas(raiz).filter { c -> prefixosDeLeitura.any { c.startsWith("$it/") || c.startsWith(it + "(") } && metodosDeEscrita.containsMatchIn(c) }

    private val apisDeEscrita = Regex(
        """writeText|writeBytes|appendText|Files\.write|Files\.writeString|Files\.newBufferedWriter|FileWriter|FileOutputStream|""" +
            """Files\.move|Files\.copy|Files\.delete|deleteIfExists|\.delete\(\)|PrintWriter|RandomAccessFile""",
    )
    private val citaArquivosDePessoas = Regex("""equipes|ausencias|papeis""", RegexOption.IGNORE_CASE)

    /** Fontes (nome → texto) que citam os YAML de pessoas E usam alguma API de escrita de arquivo. */
    private fun fontesQueGravam(fontes: Map<String, String>): List<String> =
        fontes.filter { (_, texto) -> citaArquivosDePessoas.containsMatchIn(texto) && apisDeEscrita.containsMatchIn(texto) }.keys.sorted()

    @Test
    fun nenhumaRotaEscreveAusenciasOuEquipes() {
        val banco = BancoEmArquivo()
        val ctx = testContextComBanco(Settings(dashboardDevMode = false), banco)
        try {
            testApplication {
                application { module(Settings(dashboardDevMode = false), ctx, autenticador) }
                startApplication()
                val raiz = application.routing { }
                val todas = caminhosDasRotas(raiz)
                // Sem enxergar as rotas de leitura o teste não prova nada.
                assertTrue(todas.any { it.startsWith("/api/cards/pessoas") || it.startsWith("/api/cards/carga") }, todas.toString())
                assertEquals(emptyList(), rotasDeEscrita(raiz))
            }
        } finally {
            ctx.close()
            banco.apagar()
        }
    }

    @Test
    fun rotaDeEscritaInjetadaEhAcusada() {
        val banco = BancoEmArquivo()
        val ctx = testContextComBanco(Settings(dashboardDevMode = false), banco)
        try {
            testApplication {
                application {
                    module(Settings(dashboardDevMode = false), ctx, autenticador)
                    routing { post("/api/pessoas/ausencias") { call.respond(HttpStatusCode.OK) } }
                }
                startApplication()
                val raiz = application.routing { }
                assertEquals(1, rotasDeEscrita(raiz).size, rotasDeEscrita(raiz).toString())
            }
        } finally {
            ctx.close()
            banco.apagar()
        }
    }

    @Test
    fun nenhumCodigoGravaOsYamlDePessoas() {
        val fontes = Files.walk(TestPaths.mainSrcDir).use { s ->
            s.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }.toList()
        }.associate { TestPaths.mainSrcDir.relativize(it).joinToString("/") to Files.readString(it) }
        assertTrue(fontes.size > 50, "poucas fontes varridas: ${fontes.size}")
        assertEquals(emptyList(), fontesQueGravam(fontes))
    }

    @Test
    fun codigoQueGravaOYamlEhAcusado() {
        val injetado = mapOf(
            "Gravador.kt" to """fun salvar() { java.io.File("config/ausencias.yaml").writeText("x") }""",
            "Leitor.kt" to """fun ler() = java.io.File("config/ausencias.yaml").readText()""",
            "Outro.kt" to """fun salvar() { java.io.File("saida.txt").writeText("x") }""",
        )
        assertEquals(listOf("Gravador.kt"), fontesQueGravam(injetado))
    }
}
