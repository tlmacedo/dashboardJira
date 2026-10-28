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

    /** Texto de cada fonte de produção, pelo caminho relativo à raiz de `src/main` (com `/`). */
    private fun textos(): Map<String, String> =
        fontes().associate { TestPaths.mainSrcDir.relativize(it).joinToString("/") to Files.readString(it) }

    /** Arquivos (por nome) cujo texto casa o [padrao], fora dos [permitidos]. */
    private fun violacoes(padrao: Regex, permitidos: Set<String>): List<String> =
        fontes().filter { it.name !in permitidos }.filter { padrao.containsMatchIn(Files.readString(it)) }.map { it.name }

    @Test
    fun varreduraEncontraOsFontesDeProducao() {
        val nomes = fontes().map { it.name }
        assertTrue("JiraClientFactory.kt" in nomes && "AppContext.kt" in nomes, nomes.toString())
    }

    /**
     * Só a fábrica constrói `JiraClient` e `HttpClient` (`JiraClient.kt` define o `createJiraHttpClient`); alias,
     * `typealias`, referência de construtor e `copy` de `Settings` (inclusive aninhado) também são barrados.
     */
    @Test
    fun jiraClientSoEhConstruidoNaFabrica() {
        // ClienteHttpOidc.kt é a única exceção: o cliente do login SSO/OIDC fala só com o provedor de identidade. O teste
        // abaixo prova que ele não cita o Jira nem recebe configuração de instância.
        val violacoes = varrerFontes(textos(), setOf("JiraClientFactory.kt", "JiraClient.kt", "ClienteHttpOidc.kt"))
        assertEquals(emptyList(), violacoes)
    }

    @Test
    fun clienteHttpDoOidcNaoTocaNoJira() {
        val texto = textos().entries.single { it.key.endsWith("security/ClienteHttpOidc.kt") }.value
        assertTrue(!texto.contains("jira", ignoreCase = true), "o cliente do OIDC não pode citar o Jira")
        assertTrue(!texto.contains("Settings") && !texto.contains("LiveGuard"), "sem configuração do Jira")
        // e só o login o usa
        val usos = textos().filter { it.value.contains("criarClienteHttpOidc") }.keys.map { it.substringAfterLast('/') }.toSet()
        assertEquals(setOf("ClienteHttpOidc.kt", "Application.kt"), usos)
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
