package br.sidia.painel.riscos

import br.sidia.painel.TestPaths
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** `risco.yaml`: valores do modelo, níveis e validação estrita. */
class RegrasDeRiscoTest {
    private fun arquivo(conteudo: String): Path = Files.createTempFile("risco", ".yaml").also {
        Files.writeString(it, conteudo)
        it.toFile().deleteOnExit()
    }

    private val pesos = """
        pesos:
          IDADE_ACIMA_P85: 30
          IDADE_ACIMA_P50: 10
          PARADA: 15
          VENCIDA: 25
          PREVISAO_APOS_VENCIMENTO: 20
          VENCE_EM_BREVE: 8
          BLOQUEADA: 15
          REABERTA: 10
          MUITAS_TROCAS: 10
          ESTOURO_DE_HORAS: 15
          SEM_ATUALIZACAO: 10
          SEM_ESTIMATIVA: 5
    """.trimIndent()

    private val resto = "alto: 60\nmedio: 30\nvenceEmDias: 3\nestouro: 1.25\ntrocas: 3\ndiasSemAtualizacao: 7\n"
    private val valido = "versao: 1\n$pesos\n$resto"

    @Test
    fun modeloTemOsPesosDoLegado() {
        val r = RegrasDeRisco.fromYaml(TestPaths.configDir.resolve("risco.exemplo.yaml"))
        assertEquals(RegrasDeRisco.PADRAO, r)
        assertEquals(30, r.pesos.getValue(FatorDeRisco.IDADE_ACIMA_P85))
        assertEquals(5, r.pesos.getValue(FatorDeRisco.SEM_ESTIMATIVA))
        assertEquals(FatorDeRisco.entries.toSet(), r.pesos.keys)
    }

    @Test
    fun niveisPelosLimiares() {
        val r = RegrasDeRisco.PADRAO
        assertEquals(listOf("baixo", "medio", "medio", "alto", "alto"), listOf(0, 30, 59, 60, 100).map(r::nivel))
    }

    @Test
    fun arquivoValidoCarrega() {
        val r = RegrasDeRisco.fromYaml(arquivo(valido.replace("VENCIDA: 25", "VENCIDA: 40")))
        assertEquals(40, r.pesos.getValue(FatorDeRisco.VENCIDA))
    }

    @Test
    fun regrasEstritas() {
        for (ruim in listOf(
            valido + "segredo-xyz: 1\n",
            valido.replace("versao: 1", "versao: 2"),
            valido.replace("  SEM_ESTIMATIVA: 5\n", ""), // fator sem peso
            valido.replace("  SEM_ESTIMATIVA: 5\n", "  SEM_ESTIMATIVA: 5\n  FATOR_NOVO: 3\n"), // fator desconhecido
            valido.replace("medio: 30", "medio: 60"), // medio >= alto
            valido.replace("medio: 30", "medio: 0"),
            valido.replace("estouro: 1.25", "estouro: 1.0"),
            valido.replace("trocas: 3", "trocas: 0"),
            valido.replace("PARADA: 15", "PARADA: -1"),
            valido.replace("alto: 60\n", ""),
        )) {
            val e = assertFailsWith<IllegalArgumentException>(ruim) { RegrasDeRisco.fromYaml(arquivo(ruim)) }
            assertTrue("regras de risco inválidas" in e.message.orEmpty(), e.message)
        }
    }
}
