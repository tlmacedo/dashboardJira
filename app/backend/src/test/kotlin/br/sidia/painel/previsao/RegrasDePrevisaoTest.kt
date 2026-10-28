package br.sidia.painel.previsao

import br.sidia.painel.TestPaths
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** `previsao.yaml`: valores do modelo, confiança pela amostra e validação estrita. */
class RegrasDePrevisaoTest {
    private fun arquivo(conteudo: String): Path = Files.createTempFile("previsao", ".yaml").also {
        Files.writeString(it, conteudo)
        it.toFile().deleteOnExit()
    }

    private val valido = "versao: 1\nsemanasHistorico: 16\nsorteios: 10000\nlimiteSemanas: 104\nminSemanasConfianca: 8\ncomChegada: true\nminItensEpico: 5\n"

    @Test
    fun modeloTemOsValoresPadrao() {
        val r = RegrasDePrevisao.fromYaml(TestPaths.configDir.resolve("previsao.exemplo.yaml"))
        assertEquals(RegrasDePrevisao.PADRAO, r)
        assertEquals(RegrasDePrevisao(1, 16, 10_000, 104, 8, true, 5), r)
    }

    @Test
    fun confiancaPelaAmostra() {
        val r = RegrasDePrevisao.PADRAO
        assertEquals("baixa", r.confianca(7))
        assertEquals("media", r.confianca(8))
        assertEquals("media", r.confianca(15))
        assertEquals("alta", r.confianca(16))
        assertEquals("baixa", r.confianca(0))
    }

    @Test
    fun comChegadaPodeSerDesligada() {
        assertEquals(false, RegrasDePrevisao.fromYaml(arquivo(valido.replace("comChegada: true", "comChegada: false"))).comChegada)
    }

    @Test
    fun regrasEstritas() {
        for (ruim in listOf(
            valido + "segredo-xyz: 1\n",
            valido.replace("versao: 1", "versao: 2"),
            valido.replace("sorteios: 10000", "sorteios: 5"),
            valido.replace("limiteSemanas: 104", "limiteSemanas: 0"),
            valido.replace("minSemanasConfianca: 8", "minSemanasConfianca: 20"),
            valido.replace("minItensEpico: 5", "minItensEpico: 0"),
            valido.replace("comChegada: true\n", ""),
        )) {
            val e = assertFailsWith<IllegalArgumentException>(ruim) { RegrasDePrevisao.fromYaml(arquivo(ruim)) }
            assertTrue("regras de previsão inválidas" in e.message.orEmpty(), e.message)
        }
    }
}
