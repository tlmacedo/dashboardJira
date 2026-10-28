package br.sidia.painel.docs

import br.sidia.painel.integridade.SinalIntegridade
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/** O documento de diferenças esperadas do bloco 7a cita todos os cartões, os 12 sinais e as decisões do bloco. */
class DiferencasChecklistIntegridadeDocTest {
    private fun documento(): String {
        var dir: Path? = Path.of("").toAbsolutePath()
        while (dir != null) {
            val f = dir.resolve("docs/analise/diferencas-esperadas-checklist-integridade-governanca.md")
            if (Files.exists(f)) return Files.readString(f)
            dir = dir.parent
        }
        error("docs/analise/diferencas-esperadas-checklist-integridade-governanca.md não encontrado")
    }

    @Test
    fun documentoCitaOsIds() {
        val texto = documento()
        val esperados = (1..14).map { "CR-%02d".format(it) } + (1..8).map { "IN-%02d".format(it) } + SinalIntegridade.entries.map { it.name }
        val faltando = esperados.filterNot { it in texto }
        assertTrue(faltando.isEmpty(), "IDs e sinais sem menção no documento: $faltando")
    }

    @Test
    fun documentoTrazAsDecisoesDoBloco7a() {
        val texto = documento().lowercase()
        for (tema in listOf(
            "checklist review", "blocks", "n baixo", "sem pares de pessoas", "indício, não prova", "validar a base legal com o dpo",
            "america/manaus", "autor do evento", "estimativa", "sem dado", "somente o gerente", "access_log", "ordem alfabética",
        )) {
            assertTrue(tema in texto, "tema ausente: $tema")
        }
    }

    @Test
    fun documentoNaoTemDadoPessoalReal() {
        val texto = documento()
        for (proibido in listOf("ana.silva", "TG-107")) assertTrue(proibido !in texto, proibido)
    }
}
