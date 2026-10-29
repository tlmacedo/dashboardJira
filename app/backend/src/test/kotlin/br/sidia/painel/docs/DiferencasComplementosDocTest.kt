package br.sidia.painel.docs

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * O documento de diferenças esperadas do Plano 10 cita cada item da auditoria e, para cada prova no formato
 * `Classe.metodo`, o método existe de fato em `src/test` (a prova não pode apodrecer).
 */
class DiferencasComplementosDocTest {
    private fun raiz(): Path {
        var dir: Path? = Path.of("").toAbsolutePath()
        while (dir != null) {
            if (Files.exists(dir.resolve("docs/analise/diferencas-esperadas-complementos.md"))) return dir
            dir = dir.parent
        }
        error("docs/analise/diferencas-esperadas-complementos.md não encontrado")
    }

    private fun documento(): String = Files.readString(raiz().resolve("docs/analise/diferencas-esperadas-complementos.md"))

    private val itens = listOf("A4", "A5", "M1", "M2", "M3", "M4", "M5", "M7", "B4", "E5", "E12", "E13", "I-26", "I-27")

    @Test
    fun documentoCitaCadaItemEProva() {
        val texto = documento()
        val faltando = itens.filterNot { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(texto) }
        assertTrue(faltando.isEmpty(), "itens sem menção no documento: $faltando")

        val provas = Regex("""`([A-Z]\w*Test)\.(\w+)`""").findAll(texto).map { it.groupValues[1] to it.groupValues[2] }.toSet()
        assertTrue(provas.size >= itens.size, "poucas provas citadas: ${provas.size}")

        val fontes = Files.walk(raiz().resolve("app/backend/src/test/kotlin")).use { fluxo ->
            fluxo.filter { it.name.endsWith(".kt") }.toList().associate { it.name.removeSuffix(".kt") to Files.readString(it) }
        }
        val inexistentes = provas.filterNot { (classe, metodo) ->
            val fonte = fontes[classe]
            fonte != null && Regex("""fun\s+`?$metodo\b""").containsMatchIn(fonte)
        }
        assertTrue(inexistentes.isEmpty(), "provas que não existem em src/test: $inexistentes")
    }

    @Test
    fun cadaItemTemUmaProva() {
        // Cada seção "## <item>" traz ao menos uma prova: `Classe.metodo` do backend ou um arquivo de teste do frontend.
        val secoes = documento().split(Regex("(?m)^## ")).drop(1)
        val semProva = secoes.filter { it.lineSequence().first().trim().let { t -> itens.any { i -> t.startsWith(i) } } }
            .filterNot { Regex("""`[A-Z]\w*Test\.\w+`|\.test\.tsx?`""").containsMatchIn(it) }
            .map { it.lineSequence().first() }
        assertTrue(semProva.isEmpty(), "seções sem prova: $semProva")
    }

    @Test
    fun documentoTrazAsDiferencasContraOAppAtual() {
        val texto = documento().lowercase()
        for (tema in listOf("sku type", "gaveta", "flag", "link para o jira", "app atual", "v6", "vg-02", "ne-09", "filtros_rapidos")) {
            assertTrue(tema in texto, "tema ausente: $tema")
        }
    }
}
