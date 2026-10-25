package br.sidia.painel.jql

import br.sidia.painel.TestPaths
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Protege os documentos de preparação da primeira conexão autorizada ao Jira.
 * Os documentos só descrevem chamadas; nenhuma é feita aqui.
 */
class PreparacaoDocsTest {
    /** Raiz do repositório: `app/config` fica dois níveis abaixo dela. */
    private val raiz: Path = TestPaths.configDir.toAbsolutePath().normalize().parent.parent

    private fun ler(relativo: String): String {
        val arquivo = raiz.resolve(relativo)
        assertTrue(Files.isRegularFile(arquivo), "Documento ausente: $arquivo")
        return Files.readString(arquivo)
    }

    @Test
    fun checklistETemTodasAsChamadas() {
        val texto = ler("docs/jira/checklist-primeira-conexao.md")
        val chamadas = listOf("/myself", "/field", "/search", "expand=changelog", "/status")
        for (chamada in chamadas) {
            assertTrue(texto.contains(chamada), "Checklist sem a chamada $chamada")
        }
        val ocorrencias = Regex(Regex.escape("autorização expressa")).findAll(texto).count()
        assertTrue(ocorrencias >= 5, "Esperadas ao menos 5 menções a 'autorização expressa', achadas $ocorrencias")
    }

    /** Seções de passo do checklist: do título `## Passo`/`### Passo` até o próximo título de mesmo nível ou acima. */
    private fun secoesDePasso(texto: String): List<String> {
        val linhas = texto.lines()
        val inicios = linhas.indices.filter { Regex("^#{2,3} Passo \\d").containsMatchIn(linhas[it]) }
        return inicios.map { ini ->
            val nivel = linhas[ini].takeWhile { it == '#' }.length
            val fim = (ini + 1 until linhas.size).firstOrNull { i ->
                val l = linhas[i]
                l.startsWith("#") && l.takeWhile { it == '#' }.length <= nivel
            } ?: linhas.size
            linhas.subList(ini, fim).joinToString("\n")
        }
    }

    @Test
    fun cadaPassoDoChecklistExigeAutorizacaoExpressa() {
        val secoes = secoesDePasso(ler("docs/jira/checklist-primeira-conexao.md"))
        assertEquals(5, secoes.size, "Esperadas 5 seções de passo")
        for (secao in secoes) {
            // Grafia exata (sem ignoreCase): os documentos escrevem "autorização expressa" em minúsculas.
            assertTrue(secao.contains("autorização expressa"), "Seção sem 'autorização expressa': ${secao.lines().first()}")
        }
    }

    @Test
    fun documentosDeJiraNaoTemHostRealTokenNemEmail() {
        val hostsPermitidos = setOf("exemplo.invalid", "example.com", "localhost")
        val docs = Files.list(raiz.resolve("docs/jira")).use { s -> s.filter { it.toString().endsWith(".md") }.toList() }
        assertTrue(docs.size >= 2, "Esperados os documentos de docs/jira")
        for (doc in docs) {
            val texto = Files.readString(doc)
            for (m in Regex("https?://([^/\\s:?#)`>\\]]+)", RegexOption.IGNORE_CASE).findAll(texto)) {
                assertTrue(m.groupValues[1].lowercase() in hostsPermitidos, "${doc.fileName}: host real em '${m.value}'")
            }
            assertTrue(!Regex("Bearer\\s+[A-Za-z0-9._~+/=-]{8,}").containsMatchIn(texto), "${doc.fileName}: 'Bearer' seguido de valor")
            assertTrue(
                !Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+").containsMatchIn(texto),
                "${doc.fileName}: e-mail no documento",
            )
        }
    }

    @Test
    fun matrizTemALinhaDeFieldsPorId() {
        val texto = ler("docs/jira/matriz-compatibilidade.md")
        val linha = texto.lines().firstOrNull { it.startsWith("|") && it.contains("`fields` por id") }
        assertTrue(linha != null, "Matriz sem a linha de `fields` por id")
        assertTrue(linha.contains("customfield_NNNNN"), "Linha de fields por id sem o formato customfield_NNNNN")
    }

    @Test
    fun matrizTemAsLinhasMinimas() {
        val texto = ler("docs/jira/matriz-compatibilidade.md")
        val itens = listOf(
            "POST /rest/api/2/search",
            "Bearer PAT",
            "Basic",
            "expand=changelog",
            "maxResults=1",
            "filtros salvos",
            "campos por nome",
            "5.000",
            "paginação",
            "dateCompare",
            "taxonomia de labels",
            "tipos de issue",
            "cf[12416]",
            "cf[11412]",
        )
        for (item in itens) {
            // Sem diferenciar maiúsculas: o item pode abrir a frase na tabela.
            assertTrue(texto.contains(item, ignoreCase = true), "Matriz sem o item '$item'")
        }
        for (coluna in listOf("Item", "Validado no bscbi-web", "Neste app", "Como confirmar")) {
            assertTrue(texto.contains(coluna), "Matriz sem a coluna '$coluna'")
        }
    }
}
