package br.sidia.painel.cards.metas

import br.sidia.painel.TestPaths
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** `metas.yaml`: valores do modelo e validação estrita. */
class RegrasDeMetasTest {
    private fun arquivo(conteudo: String): Path = Files.createTempFile("metas", ".yaml").also {
        Files.writeString(it, conteudo)
        it.toFile().deleteOnExit()
    }

    private val valido = "versao: 1\nmetaNoPrazo: 0.9\nmetaAntecipadas: 0.35\nalertaDataOriginalPp: 5\nfaixaAtencaoPp: 10\n"

    @Test
    fun modeloTemOsValoresPadrao() {
        val r = RegrasDeMetas.fromYaml(TestPaths.configDir.resolve("metas.exemplo.yaml"))
        assertEquals(RegrasDeMetas.PADRAO, r)
        assertEquals(RegrasDeMetas(1, 0.90, 0.35, 5.0, 10.0), r)
    }

    @Test
    fun arquivoValidoCarrega() {
        val r = RegrasDeMetas.fromYaml(arquivo(valido.replace("0.9\n", "0.95\n")))
        assertEquals(0.95, r.metaNoPrazo)
    }

    @Test
    fun regrasEstritas() {
        for (ruim in listOf(
            valido + "segredo-xyz: 1\n",
            valido.replace("versao: 1", "versao: 2"),
            valido.replace("metaNoPrazo: 0.9", "metaNoPrazo: 1.5"),
            valido.replace("metaNoPrazo: 0.9", "metaNoPrazo: 0"),
            valido.replace("faixaAtencaoPp: 10", "faixaAtencaoPp: -1"),
            valido.replace("alertaDataOriginalPp: 5\n", ""),
        )) {
            val e = assertFailsWith<IllegalArgumentException>(ruim) { RegrasDeMetas.fromYaml(arquivo(ruim)) }
            assertTrue("metas inválidas" in e.message.orEmpty(), e.message)
        }
    }
}
