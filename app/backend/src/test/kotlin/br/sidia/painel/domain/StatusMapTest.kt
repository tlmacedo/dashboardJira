package br.sidia.painel.domain

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Mapa de status: categoria, ordem do fluxo e resoluções negativas (opcionais no arquivo). */
class StatusMapTest {
    private fun arquivo(conteudo: String): Path = Files.createTempFile("status_map", ".yaml").also {
        Files.writeString(it, conteudo)
        it.toFile().deleteOnExit()
    }

    private val base = """
        status_map:
          "Open": todo
          "In Progress": active
          "Closed": done
    """.trimIndent()

    @Test
    fun ordemEResolucoesNegativasCarregam() {
        val m = StatusMap.fromYaml(
            arquivo(base + "\nordem: [\"Open\", \"In Progress\", \"Closed\"]\nresolucoesNegativas: [\"Won't Do\", \"Duplicate\"]\n"),
        )
        assertEquals(listOf("Open", "In Progress", "Closed"), m.ordem)
        assertEquals(0, m.indiceNaOrdem("Open"))
        assertEquals(2, m.indiceNaOrdem("Closed"))
        assertNull(m.indiceNaOrdem("Fora"))
        assertTrue(m.ehResolucaoNegativa("Won't Do"))
        assertFalse(m.ehResolucaoNegativa("Done"))
        assertEquals(StatusCategory.ACTIVE, m.category("In Progress"))
    }

    @Test
    fun ordemComStatusForaDoMapaFalha() {
        val e = assertFailsWith<IllegalArgumentException> {
            StatusMap.fromYaml(arquivo(base + "\nordem: [\"Open\", \"Inexistente\"]\n"))
        }
        assertTrue("Inexistente" in e.message.orEmpty(), e.message)
    }

    @Test
    fun resolucaoNegativaSemDiferenciarMaiusculas() {
        val m = StatusMap(mapOf("Open" to StatusCategory.TODO), resolucoesNegativas = setOf("Won't Do"))
        assertTrue(m.ehResolucaoNegativa("won't do"))
        assertTrue(m.ehResolucaoNegativa("WON'T DO"))
        assertFalse(m.ehResolucaoNegativa(""))
    }

    @Test
    fun arquivoAntigoSemOrdemContinuaValido() {
        val m = StatusMap.fromYaml(arquivo(base))
        assertEquals(emptyList(), m.ordem)
        assertNull(m.indiceNaOrdem("Open"))
        assertFalse(m.ehResolucaoNegativa("Won't Do"))
        assertEquals(StatusCategory.DONE, m.category("Closed"))
    }

    @Test
    fun chaveDesconhecidaContinuaFalhando() {
        assertFailsWith<IllegalArgumentException> { StatusMap.fromYaml(arquivo(base + "\nbobagem: 1\n")) }
    }
}
