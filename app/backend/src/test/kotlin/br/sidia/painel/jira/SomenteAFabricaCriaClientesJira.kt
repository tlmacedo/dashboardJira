package br.sidia.painel.jira

import br.sidia.painel.TestPaths
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Garante por varredura do código de produção (`src/main`) que só a fábrica constrói clientes Jira.
 * O `internal` não basta: ele só protege de outros módulos.
 */
class SomenteAFabricaCriaClientesJira {
    private fun fontes(): List<Path> =
        Files.walk(TestPaths.mainSrcDir).use { s ->
            s.filter { Files.isRegularFile(it) && it.extension == "kt" }.toList()
        }

    /** Arquivos (por nome) cujo texto casa o [padrao], fora dos [permitidos]. */
    private fun violacoes(padrao: Regex, permitidos: Set<String>): List<String> =
        fontes().filter { it.name !in permitidos }.filter { padrao.containsMatchIn(Files.readString(it)) }.map { it.name }

    @Test
    fun varreduraEncontraOsFontesDeProducao() {
        val nomes = fontes().map { it.name }
        assertTrue("JiraClientFactory.kt" in nomes && "AppContext.kt" in nomes, nomes.toString())
    }

    @Test
    fun jiraClientSoEhConstruidoNaFabrica() {
        // "JiraClient(" não casa "createJiraClient(" nem "FakeJiraClient(" graças ao limite de palavra.
        assertEquals(emptyList(), violacoes(Regex("\\bJiraClient\\("), setOf("JiraClientFactory.kt")))
    }

    @Test
    fun fabricaSoEhConstruidaNoAppContext() {
        assertEquals(
            emptyList(),
            violacoes(Regex("\\bJiraClientFactory\\("), setOf("AppContext.kt", "JiraClientFactory.kt")),
        )
    }

    @Test
    fun copyDeJiraLiveEnabledNaoApareceEmProducao() {
        // Proíbe `copy(` com `jiraLiveEnabled` no mesmo arquivo de produção (permitido só em testes).
        val copy = Regex("\\.copy\\(")
        val ofensores = fontes().filter {
            val texto = Files.readString(it)
            copy.containsMatchIn(texto) && "jiraLiveEnabled" in texto &&
                Regex("\\.copy\\([^)]*jiraLiveEnabled").containsMatchIn(texto)
        }.map { it.name }
        assertEquals(emptyList(), ofensores)
    }
}
