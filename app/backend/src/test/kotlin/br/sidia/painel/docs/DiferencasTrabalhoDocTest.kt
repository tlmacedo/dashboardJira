package br.sidia.painel.docs

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/** O documento de diferenças esperadas do bloco 5a cita todos os cartões de trabalho e prazos. */
class DiferencasTrabalhoDocTest {
    private fun documento(): String {
        var dir: Path? = Path.of("").toAbsolutePath()
        while (dir != null) {
            val f = dir.resolve("docs/analise/diferencas-esperadas-trabalho-e-prazos.md")
            if (Files.exists(f)) return Files.readString(f)
            dir = dir.parent
        }
        error("docs/analise/diferencas-esperadas-trabalho-e-prazos.md não encontrado")
    }

    @Test
    fun documentoCitaOsIds() {
        val texto = documento()
        val esperados = (1..9).map { "AB-%02d".format(it) } + (1..6).map { "NG-%02d".format(it) } +
            (1..12).map { "FE-%02d".format(it) } + (1..14).map { "MT-%02d".format(it) }
        val faltando = esperados.filterNot { it in texto }
        assertTrue(faltando.isEmpty(), "IDs sem menção no documento: $faltando")
    }

    @Test
    fun documentoTrazOsTemasDasDiferencas() {
        val texto = documento()
        for (tema in listOf("Manaus", "dias úteis", "resolution", "P85", "atraso justificado", "Sem atualização", "worklog")) {
            assertTrue(tema.lowercase() in texto.lowercase(), "tema ausente: $tema")
        }
    }
}
