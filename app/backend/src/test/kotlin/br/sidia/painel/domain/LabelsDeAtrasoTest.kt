package br.sidia.painel.domain

import br.sidia.painel.TestPaths
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LabelsDeAtrasoTest {
    @TempDir
    lateinit var pasta: Path

    private fun yaml(texto: String): Path = pasta.resolve("labels.yaml").also { Files.writeString(it, texto) }

    private fun modelo() = LabelsDeAtraso.fromYaml(TestPaths.configDir.resolve("labels_atraso.exemplo.yaml"))

    @Test
    fun modeloVersionadoCarrega() {
        val l = modelo()
        assertEquals(1, l.versao)
        assertTrue(l.justifica("DUEDATE_NEGOTIATED"))
        assertTrue(l.ehTardia("LATE_SENT"))
        assertTrue(l.ehTardia("LATE_REVIEW"))
        assertFalse(l.ehTardia("DUEDATE_NEGOTIATED"))
        assertFalse(l.justifica("OUTRA"))
    }

    @Test
    fun curingaSoNoFim() {
        val l = modelo()
        assertTrue(l.justifica("ONHOLDBY-PL"))
        assertTrue(l.justifica("onholdby-x"))
        assertFalse(l.justifica("XONHOLDBY-PL"))
        // padrões com curinga fora do fim falham na carga
        assertFailsWith<IllegalArgumentException> {
            LabelsDeAtraso.fromYaml(yaml("versao: 1\njustificativas: [\"*X\"]\ntardias: []\n"))
        }
        assertFailsWith<IllegalArgumentException> {
            LabelsDeAtraso.fromYaml(yaml("versao: 1\njustificativas: [\"A*B\"]\ntardias: []\n"))
        }
    }

    @Test
    fun semDiferenciarMaiusculas() {
        val l = modelo()
        assertTrue(l.justifica("late_sent"))
        assertTrue(l.ehTardia("late_sent"))
        assertTrue(l.ehTardia("Late_Review"))
    }

    @Test
    fun tardiaPrecisaSerJustificativa() {
        assertFailsWith<IllegalArgumentException> {
            LabelsDeAtraso(1, listOf("A"), setOf("LATE_SENT"))
        }
        assertFailsWith<IllegalArgumentException> {
            LabelsDeAtraso.fromYaml(yaml("versao: 1\njustificativas: [\"A\"]\ntardias: [\"LATE_SENT\"]\n"))
        }
    }

    @Test
    fun arquivoEstrito() {
        assertFailsWith<IllegalArgumentException> {
            LabelsDeAtraso.fromYaml(yaml("versao: 1\njustificativas: [\"A\"]\ntardias: []\nextra: 1\n"))
        }
        assertFailsWith<IllegalArgumentException> {
            LabelsDeAtraso.fromYaml(yaml("versao: 2\njustificativas: [\"A\"]\ntardias: []\n"))
        }
        assertFailsWith<IllegalArgumentException> {
            LabelsDeAtraso.fromYaml(yaml("versao: 1\njustificativas: [\"\"]\ntardias: []\n"))
        }
    }
}
