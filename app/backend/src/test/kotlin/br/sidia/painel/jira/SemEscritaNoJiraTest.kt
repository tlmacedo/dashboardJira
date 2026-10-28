package br.sidia.painel.jira

import br.sidia.painel.TestPaths
import java.lang.reflect.Modifier
import java.nio.file.Files
import kotlin.io.path.extension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Nenhum caminho do painel escreve no Jira (Distribuição e Rebalanceamento são só sugestão): o cliente só tem métodos de LEITURA e
 * o pacote `jira` não usa `PUT`, `PATCH` nem `DELETE`; o único `POST` é a busca (`/search`, que lê). Vale mesmo com a flag ligada.
 */
class SemEscritaNoJiraTest {
    /** Os métodos de leitura do cliente (o nome sem o sufixo de compilação, como `search$default`). */
    private val permitidos = setOf("search", "searchAll", "count", "fullChangelog", "worklogs", "changelogPorExpand", "close")

    private fun fontesDoPacoteJira(): Map<String, String> {
        val pasta = TestPaths.mainSrcDir.resolve("kotlin/br/sidia/painel/jira")
        return Files.walk(pasta).use { s ->
            s.filter { Files.isRegularFile(it) && it.extension == "kt" }.toList()
        }.associate { it.fileName.toString() to Files.readString(it) }
    }

    @Test
    fun nenhumCaminhoEscreveNoJira() {
        // 1) Os métodos públicos do cliente são só os de leitura.
        val publicos = JiraClient::class.java.methods
            .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic && it.declaringClass == JiraClient::class.java }
            .map { it.name.substringBefore('$') }
            .filter { !it.startsWith("get") && !it.startsWith("set") }
            .toSet()
        assertTrue(publicos.isNotEmpty() && "search" in publicos, "a reflexão precisa enxergar o cliente: $publicos")
        assertEquals(emptySet(), publicos - permitidos, "método novo no JiraClient: só leitura é permitida (acrescente à lista só depois de conferir)")

        // 2) O pacote `jira` não usa método HTTP de escrita e o POST é só o da busca.
        val fontes = fontesDoPacoteJira()
        assertTrue("JiraClient.kt" in fontes, fontes.keys.toString())
        for ((arquivo, texto) in fontes) {
            val semComentarios = texto.lines().filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") || it.trimStart().startsWith("/*") }
            val corpo = semComentarios.joinToString("\n")
            for (proibido in listOf("HttpMethod.Put", "HttpMethod.Patch", "HttpMethod.Delete", "HttpMethod.Options", "HttpMethod.Head")) {
                assertTrue(proibido !in corpo, "$arquivo usa $proibido")
            }
            for (linha in semComentarios.filter { "HttpMethod.Post" in it }) {
                assertTrue("/rest/api/2/search" in linha || "/search" in linha, "$arquivo: POST que não é a busca: ${linha.trim()}")
            }
            // Nenhuma chamada direta de escrita do cliente HTTP (`client.put(...)`, `client.delete(...)`, `submitForm`).
            assertTrue(!Regex("\\b(client|http|cliente)\\w*\\.(put|patch|delete|submitForm)\\s*\\(").containsMatchIn(corpo), "$arquivo escreve pelo cliente HTTP")
        }
        assertTrue(fontes.getValue("JiraClient.kt").lines().count { "HttpMethod.Post" in it } >= 1, "o POST da busca existe e é o único")
    }
}
