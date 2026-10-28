package br.sidia.painel.pessoas

import br.sidia.painel.TestPaths
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** `planejamento.yaml`: valores do modelo e validação estrita dos limites e das relações entre limiares. */
class RegrasDePlanejamentoTest {
    private fun arquivo(conteudo: String): Path = Files.createTempFile("planejamento", ".yaml").also {
        Files.writeString(it, conteudo)
        it.toFile().deleteOnExit()
    }

    private val modelo: String = Files.readString(TestPaths.configDir.resolve("planejamento.exemplo.yaml"))

    @Test
    fun modeloTemOsValoresDoPlano() {
        val r = RegrasDePlanejamento.fromYaml(TestPaths.configDir.resolve("planejamento.exemplo.yaml"))
        assertEquals(1, r.versao)
        assertEquals(0.7, r.focoPadrao)
        assertEquals(0.5, r.horasMinimas)
        assertEquals(mapOf("Checklist" to 6.0, "Checklist TG" to 6.0, "Checklist Review" to 2.0), r.horasPorTipo)
        assertEquals(6.0, r.horasPadrao)
        assertEquals(listOf(2, 2, 4), listOf(r.semanasMinimasDemanda, r.semanasSemVencimentoIniciada, r.semanasSemVencimentoNaoIniciada))
        assertEquals(listOf(0.85, 1.10, 0.50), listOf(r.ocupacaoAtencao, r.ocupacaoAlta, r.folga))
        assertEquals(3.0, r.tetoJain)
        assertEquals(mapOf("In Progress" to 12, "Submitted" to 5), r.wipPorStatus)
        assertEquals(3, r.wipPorPessoa)
        assertEquals(2, r.slaRevisaoDias)
        assertEquals(0.6, r.diaCriticoCapacidade)
        assertEquals(30, r.horizonteCoberturaDias)
        assertEquals(RegrasDePlanejamento.PADRAO, r)
    }

    @Test
    fun prioridadePadraoVemDoYamlEArquivoAntigoContinuaValendo() {
        assertEquals("Medium", RegrasDePlanejamento.fromYaml(TestPaths.configDir.resolve("planejamento.exemplo.yaml")).prioridadePadrao)
        assertTrue("prioridadePadrao" in modelo, "o modelo precisa documentar a chave")
        // Arquivo real de antes desta chave: segue carregando, com o padrão "Medium" (vocabulário a confirmar na 1ª conexão).
        val antigo = modelo.lines().filterNot { it.startsWith("prioridadePadrao") }.joinToString("\n")
        assertEquals("Medium", RegrasDePlanejamento.fromYaml(arquivo(antigo)).prioridadePadrao)
        assertEquals("High", RegrasDePlanejamento.fromYaml(arquivo(modelo.replace("prioridadePadrao: \"Medium\"", "prioridadePadrao: \"High\""))).prioridadePadrao)
        for (ruim in listOf("\"\"", "\"   \"", "\"" + "x".repeat(51) + "\"")) {
            assertFailsWith<IllegalArgumentException>(ruim) {
                RegrasDePlanejamento.fromYaml(arquivo(modelo.replace("prioridadePadrao: \"Medium\"", "prioridadePadrao: $ruim")))
            }
        }
    }

    @Test
    fun regrasEstritasELimites() {
        fun troca(de: String, para: String) = modelo.replace(de, para).also { assertTrue(it != modelo, "o modelo não tem '$de'") }
        val ruins = listOf(
            troca("versao: 1", "versao: 2"),
            troca("focoPadrao: 0.7", "focoPadrao: 0.0"),
            troca("focoPadrao: 0.7", "focoPadrao: 1.2"),
            troca("horasMinimas: 0.5", "horasMinimas: 0.0"),
            troca("horasPadrao: 6.0", "horasPadrao: -1.0"),
            troca("Checklist: 6.0", "Checklist: 0.0"),
            troca("semanasMinimasDemanda: 2", "semanasMinimasDemanda: 0"),
            troca("semanasSemVencimentoIniciada: 2", "semanasSemVencimentoIniciada: 0"),
            troca("ocupacaoAlta: 1.10", "ocupacaoAlta: 0.80"),
            troca("folga: 0.50", "folga: 0.90"),
            troca("folga: 0.50", "folga: 0.0"),
            troca("tetoJain: 3.0", "tetoJain: 0.0"),
            troca("wipPorPessoa: 3", "wipPorPessoa: 0"),
            troca("\"In Progress\": 12", "\"In Progress\": 0"),
            troca("slaRevisaoDias: 2", "slaRevisaoDias: 0"),
            troca("diaCriticoCapacidade: 0.6", "diaCriticoCapacidade: 1.5"),
            troca("horizonteCoberturaDias: 30", "horizonteCoberturaDias: 0"),
            modelo + "\nchaveNova: 1\n",
            modelo.lines().filter { !it.startsWith("tetoJain") }.joinToString("\n"),
        )
        for (r in ruins) assertFailsWith<IllegalArgumentException>(r) { RegrasDePlanejamento.fromYaml(arquivo(r)) }
    }

    @Test
    fun erroCitaOArquivo() {
        val caminho = arquivo(modelo + "\nchaveNova: 1\n")
        val e = assertFailsWith<IllegalArgumentException> { RegrasDePlanejamento.fromYaml(caminho) }
        assertTrue(caminho.toString() in e.message.orEmpty(), e.message)
    }
}
