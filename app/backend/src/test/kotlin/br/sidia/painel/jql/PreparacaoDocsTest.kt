package br.sidia.painel.jql

import br.sidia.painel.TestPaths
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
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
