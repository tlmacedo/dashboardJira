package br.sidia.painel.sync

import br.sidia.painel.TestPaths
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Pareamento Task x Review pelo vínculo Blocks (nas duas direções) e leitura estrita da configuração. */
class VinculosTaskReviewTest {
    private val cfg = VinculosConfig(versao = 1, tipoDeLink = "Blocks", tiposReview = setOf("Checklist Review"))

    private fun blocks(de: String, para: String, tipo: String = "Blocks") =
        VinculoRecord(de, para, tipo, DirecaoVinculo.OUTWARD)

    private fun tipos(vararg pares: Pair<String, String>) = pares.toMap()

    private fun resolver(vinculos: List<VinculoRecord>, tipoDe: Map<String, String>, c: VinculosConfig = cfg) =
        ParesTaskReview.resolver(vinculos, tipoDe, c)

    private fun yaml(texto: String): Path = Files.createTempFile("vinculos", ".yaml").also {
        Files.writeString(it, texto)
        it.toFile().deleteOnExit()
    }

    @Test
    fun reviewQueBloqueiaATask() {
        val r = resolver(listOf(blocks("TG-2", "TG-1")), tipos("TG-1" to "Task", "TG-2" to "Checklist Review"))
        assertEquals(listOf(ParDeChaves("TG-1", "TG-2")), r.pares)
        assertEquals(0, r.ambiguas)
        assertEquals(0, r.ignorados)
    }

    @Test
    fun reviewBloqueadaPelaTask() {
        val r = resolver(listOf(blocks("TG-3", "TG-4")), tipos("TG-3" to "Task", "TG-4" to "Checklist Review"))
        assertEquals(listOf(ParDeChaves("TG-3", "TG-4")), r.pares)
    }

    @Test
    fun blocksEntreDuasTasksIgnorado() {
        val r = resolver(listOf(blocks("TG-5", "TG-6")), tipos("TG-5" to "Task", "TG-6" to "Task"))
        assertEquals(emptyList(), r.pares)
        assertEquals(0, r.ignorados)
        assertEquals(0, r.ambiguas)
    }

    @Test
    fun reviewComReviewIgnorado() {
        val r = resolver(
            listOf(blocks("TG-2", "TG-4")),
            tipos("TG-2" to "Checklist Review", "TG-4" to "checklist review"),
        )
        assertEquals(emptyList(), r.pares)
    }

    @Test
    fun outroTipoDeLinkIgnorado() {
        val relates = VinculoRecord("TG-1", "TG-2", "Relates", DirecaoVinculo.SIMETRICO)
        val r = resolver(listOf(relates), tipos("TG-1" to "Task", "TG-2" to "Checklist Review"))
        assertEquals(emptyList(), r.pares)
        // Tipo de link diferente não conta como ponta ausente.
        assertEquals(0, r.ignorados)
    }

    @Test
    fun tipoDeLinkSemDiferenciarMaiusculas() {
        val r = resolver(listOf(blocks("TG-2", "TG-1", "BLOCKS")), tipos("TG-1" to "Task", "TG-2" to "Checklist Review"))
        assertEquals(listOf(ParDeChaves("TG-1", "TG-2")), r.pares)
    }

    @Test
    fun pontaForaDaBaseIgnorada() {
        val r = resolver(listOf(blocks("TG-2", "TG-99")), tipos("TG-2" to "Checklist Review"))
        assertEquals(emptyList(), r.pares)
        assertEquals(1, r.ignorados)
    }

    @Test
    fun ambiguidadeUsaChaveNaturalMenor() {
        val r = resolver(
            listOf(blocks("TG-50", "TG-10"), blocks("TG-50", "TG-9")),
            tipos("TG-9" to "Task", "TG-10" to "Task", "TG-50" to "Checklist Review"),
        )
        assertEquals(listOf(ParDeChaves("TG-9", "TG-50")), r.pares)
        assertEquals(1, r.ambiguas)
    }

    @Test
    fun taskComDuasReviewsUsaChaveMenor() {
        val r = resolver(
            listOf(blocks("TG-20", "TG-1"), blocks("TG-3", "TG-1")),
            tipos("TG-1" to "Task", "TG-20" to "Checklist Review", "TG-3" to "Checklist Review"),
        )
        assertEquals(listOf(ParDeChaves("TG-1", "TG-3")), r.pares)
        assertEquals(1, r.ambiguas)
    }

    @Test
    fun semDuplicar() {
        val repetido = listOf(blocks("TG-2", "TG-1"), blocks("TG-2", "TG-1"))
        val r = resolver(repetido, tipos("TG-1" to "Task", "TG-2" to "Checklist Review"))
        assertEquals(listOf(ParDeChaves("TG-1", "TG-2")), r.pares)
        assertEquals(0, r.ambiguas)
    }

    @Test
    fun tiposTaskRestringeAsTasks() {
        val restrita = cfg.copy(tiposTask = setOf("Task"))
        val r = resolver(
            listOf(blocks("TG-2", "TG-1"), blocks("TG-2", "TG-7")),
            tipos("TG-1" to "Task", "TG-7" to "Epic", "TG-2" to "Checklist Review"),
            restrita,
        )
        assertEquals(listOf(ParDeChaves("TG-1", "TG-2")), r.pares)
        assertTrue(restrita.ehTask("task"))
        assertFalse(restrita.ehTask("Epic"))
        assertFalse(cfg.ehTask("Checklist Review"))
        assertTrue(cfg.ehTask("Qualquer Outro"))
    }

    @Test
    fun resultadoEstavelEmOrdemNatural() {
        val r = resolver(
            listOf(blocks("TG-2", "TG-10"), blocks("TG-4", "TG-9")),
            tipos("TG-10" to "Task", "TG-9" to "Task", "TG-2" to "Checklist Review", "TG-4" to "Checklist Review"),
        )
        assertEquals(listOf(ParDeChaves("TG-9", "TG-4"), ParDeChaves("TG-10", "TG-2")), r.pares)
    }

    @Test
    fun vinculosEstrito() {
        val ok = VinculosConfig.fromYaml(
            yaml("versao: 1\ntipoDeLink: Blocks\ntiposReview: [\"Checklist Review\"]\ntiposTask: []\n"),
        )
        assertEquals(setOf("Checklist Review"), ok.tiposReview)
        assertEquals("Blocks", ok.tipoDeLink)

        val desconhecida = assertFailsWith<IllegalArgumentException> {
            VinculosConfig.fromYaml(yaml("versao: 1\ntiposReview: [\"Checklist Review\"]\nchaveInventada: 1\n"))
        }
        assertTrue("chaveInventada" in desconhecida.message.orEmpty(), desconhecida.message)

        val semReview = assertFailsWith<IllegalArgumentException> {
            VinculosConfig.fromYaml(yaml("versao: 1\ntiposReview: []\n"))
        }
        assertTrue("tiposReview" in semReview.message.orEmpty(), semReview.message)

        val nasDuas = assertFailsWith<IllegalArgumentException> {
            VinculosConfig.fromYaml(
                yaml("versao: 1\ntiposReview: [\"Checklist Review\"]\ntiposTask: [\"checklist review\", \"Task\"]\n"),
            )
        }
        assertTrue("Checklist Review".lowercase() in nasDuas.message.orEmpty().lowercase(), nasDuas.message)

        val versaoErrada = assertFailsWith<IllegalArgumentException> {
            VinculosConfig.fromYaml(yaml("versao: 2\ntiposReview: [\"Checklist Review\"]\n"))
        }
        assertTrue("versao" in versaoErrada.message.orEmpty(), versaoErrada.message)

        val tipoVazio = assertFailsWith<IllegalArgumentException> {
            VinculosConfig.fromYaml(yaml("versao: 1\ntipoDeLink: \"  \"\ntiposReview: [\"Checklist Review\"]\n"))
        }
        assertTrue("tipoDeLink" in tipoVazio.message.orEmpty(), tipoVazio.message)
    }

    @Test
    fun exemploVersionadoEhValido() {
        val c = VinculosConfig.fromYaml(TestPaths.configDir.resolve("vinculos.exemplo.yaml"))
        assertEquals("Blocks", c.tipoDeLink)
        assertTrue(c.ehReview("Checklist Review"))
        assertEquals(emptySet(), c.tiposTask)
    }
}
