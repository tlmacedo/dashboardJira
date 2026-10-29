package br.sidia.painel.docs

import br.sidia.painel.TestPaths
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Remoção do app Python (Plano 8, Tarefa 9): depois que a pasta de referência saiu do repositório, nenhum documento vivo,
 * script, hook ou teste pode apontar para um caminho dela como se ainda existisse. O código antigo está preservado na tag
 * `legacy-final`, e toda menção ao caminho antigo precisa dizer isso (na mesma linha ou até duas linhas ao lado).
 * Os planos, specs e o progresso (`docs/superpowers/`) são histórico e o documento de equivalência cita o caminho de
 * propósito: ficam fora da varredura. Nada aqui acessa a rede nem o Jira.
 */
class ReferenciasLegacyTest {
    // Montado por partes para o próprio teste não aparecer na varredura que ele faz.
    private val pasta = "leg" + "acy"
    private val caminho = "$pasta/"
    private val etiqueta = "$pasta-final"

    private val raiz: Path = TestPaths.repoDir

    /** Pastas geradas ou de terceiros, ignoradas em qualquer nível. */
    private val pastasIgnoradasEmQualquerNivel = setOf(".git", "node_modules", ".gradle", ".kotlin")

    /** Pastas geradas, ignoradas só nestes caminhos (não por nome: `docs/**/data` continua varrido). */
    private val caminhosIgnorados = listOf("app/data/", "app/pacote/", "app/backend/build/", "app/frontend/dist/")

    /** Arquivos de texto sob [relativo] (arquivo ou pasta), sem as pastas geradas. */
    private fun arquivos(relativo: String): List<Path> {
        val base = raiz.resolve(relativo)
        if (!Files.exists(base)) return emptyList()
        if (Files.isRegularFile(base)) return listOf(base)
        return Files.walk(base).use { fluxo ->
            fluxo.filter { Files.isRegularFile(it) }
                .filter { p -> raiz.relativize(p).none { it.toString() in pastasIgnoradasEmQualquerNivel } }
                .filter { p -> caminhosIgnorados.none { relativo(p).startsWith(it) } }
                .toList()
        }
    }

    /** Linhas do arquivo, ou null se ele não for texto UTF-8 (binário) ou for grande demais. */
    private fun linhasOuNull(p: Path): List<String>? {
        if (Files.size(p) > 2_000_000) return null
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        return runCatching { decoder.decode(java.nio.ByteBuffer.wrap(Files.readAllBytes(p))).toString().lines() }.getOrNull()
    }

    private fun relativo(p: Path) = raiz.relativize(p).invariantSeparatorsPathString

    /** Escopo da varredura de referências (Plano 8, T9, passo 2). */
    private fun escopo(): List<Path> {
        // skills-para-cline/ fica de fora: são cópias de skills de terceiros, com exemplos genéricos que citam "legacy/".
        val ignorados = listOf("docs/superpowers/", "docs/analise/equivalencia-por-card.md")
        val todos = listOf(
            "app", ".claude", ".clinerules", ".cline", ".github", "scripts", "deploy", "ops", "docs",
            "CLAUDE.md", "README.md", "PRODUCT.md", "HANDOFF.md", "DESIGN.md", "SKILLS_INSTALADAS_CLAUDE.md", ".gitignore",
        ).flatMap(::arquivos)
        return todos.filter { p -> ignorados.none { relativo(p).startsWith(it) } && p.fileName.toString() != "ReferenciasLegacyTest.kt" }
    }

    @Test
    fun nadaReferenciaLegacy() {
        val soltas = escopo().flatMap { p ->
            val linhas = linhasOuNull(p) ?: return@flatMap emptyList()
            linhas.withIndex().filter { (i, l) ->
                caminho in l && (maxOf(0, i - 2)..minOf(linhas.lastIndex, i + 2)).none { etiqueta in linhas[it] }
            }.map { (i, l) -> "${relativo(p)}:${i + 1}: ${l.trim().take(120)}" }
        }
        assertTrue(soltas.isEmpty(), "caminho do app Python removido citado sem a menção à tag $etiqueta:\n" + soltas.joinToString("\n"))
    }

    @Test
    fun nenhumCodigoLeLegacy() {
        // Código (não comentário) de src/main e src/test que monta caminho para a pasta removida.
        val leitura = Regex("""(resolve|Path\.of|Paths\.get|File|Files\.\w+)\(.*["/]$pasta\b""")
        val lendo = listOf("app/backend/src/main", "app/backend/src/test").flatMap(::arquivos)
            .filter { it.fileName.toString().endsWith(".kt") && it.fileName.toString() != "ReferenciasLegacyTest.kt" }
            .flatMap { p ->
                (linhasOuNull(p) ?: emptyList()).withIndex()
                    .filter { (_, l) -> !l.trim().startsWith("//") && !l.trim().startsWith("*") && leitura.containsMatchIn(l) }
                    .map { (i, l) -> "${relativo(p)}:${i + 1}: ${l.trim().take(120)}" }
            }
        assertTrue(lendo.isEmpty(), "código que lê a pasta $caminho:\n" + lendo.joinToString("\n"))
    }

    @Test
    fun aPastaFoiRemovidaEOCodigoAntigoEstaNaTag() {
        assertFalse(Files.exists(raiz.resolve(pasta)), "a pasta $caminho deve ter saído do repositório (tag $etiqueta)")
        // Os documentos de entrada dizem onde recuperar o código antigo.
        assertTrue(etiqueta in ApoioDeDocs.texto("CLAUDE.md"), "CLAUDE.md deve apontar para a tag $etiqueta")
        assertEquals(true, ApoioDeDocs.existe("docs/analise/equivalencia-por-card.md"))
    }
}
