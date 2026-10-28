package br.sidia.painel.db

import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlin.io.path.ExperimentalPathApi::class)
class TravaDeProcessoTest {
    @Test
    fun validaEnquantoTravadaEFalsaDepoisDeFechar() {
        val pasta = Files.createTempDirectory("painel-trava-")
        try {
            val trava = TravaDeProcesso.adquirir(pasta.resolve("painel.db"))
            assertTrue(trava.valida)
            trava.close()
            assertFalse(trava.valida)
        } finally {
            pasta.deleteRecursively()
        }
    }

    @Test
    fun segundaTravaNoMesmoBancoERecusada() {
        val pasta = Files.createTempDirectory("painel-trava-")
        try {
            val primeira = TravaDeProcesso.adquirir(pasta.resolve("painel.db"))
            assertFailsWith<IllegalStateException> { TravaDeProcesso.adquirir(pasta.resolve("painel.db")) }
            primeira.close()
        } finally {
            pasta.deleteRecursively()
        }
    }
}
