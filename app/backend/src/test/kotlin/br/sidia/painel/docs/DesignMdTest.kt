package br.sidia.painel.docs

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/** Documentos de identidade visual (Plano 15). Os casos do `DESIGN.md` entram na Tarefa 5. */
class DesignMdTest {
    private fun doc(relativo: String): String {
        var dir: Path? = Path.of("").toAbsolutePath()
        while (dir != null) {
            val f = dir.resolve(relativo)
            if (Files.exists(f)) return Files.readString(f)
            dir = dir.parent
        }
        error("$relativo não encontrado")
    }

    @Test
    fun ativosDeMarcaRegistrados() {
        val texto = doc("docs/design/ativos-de-marca.md").lowercase()
        // As três decisões do ponto de parada da Tarefa 1: logo, fonte e licença, manual de marca.
        for (tema in listOf("logo", "fonte", "licença", "manual de marca", "ruling:", "como reverter", "#1329a0")) {
            assertTrue(tema in texto, "ativos-de-marca.md não cita: $tema")
        }
    }
}
