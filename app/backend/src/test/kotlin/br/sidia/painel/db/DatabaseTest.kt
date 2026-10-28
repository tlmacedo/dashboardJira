package br.sidia.painel.db

import br.sidia.painel.BancoEmArquivo
import org.junit.jupiter.api.AfterAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatabaseTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()
    }

    @Test
    fun migracoesPendentesNoBancoVazioSaoTodasEDepoisZero() {
        val arquivo = banco.novoArquivo()
        val ds = Database.connect(Database.urlDeArquivo(arquivo))
        (ds as AutoCloseable).use {
            assertTrue(Database.migracoesPendentes(ds) >= 1, "banco recém-criado tem migrações a aplicar")
            Database.migrate(ds)
            assertEquals(0, Database.migracoesPendentes(ds))
        }
    }

    @Test
    fun arquivoDeLeOCaminhoDoPool() {
        val arquivo = banco.novoArquivo()
        val ds = Database.connect(Database.urlDeArquivo(arquivo))
        (ds as AutoCloseable).use {
            assertEquals(arquivo.toAbsolutePath().normalize(), Database.arquivoDe(ds)?.toAbsolutePath()?.normalize())
        }
    }
}
