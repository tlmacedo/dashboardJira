package br.sidia.painel.auditoria

import br.sidia.painel.TestPaths
import br.sidia.painel.vinculosDeTeste
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RegrasDeAuditoriaTest {
    @TempDir
    lateinit var pasta: Path

    private val vinculos = vinculosDeTeste()
    private val modelo: Path = TestPaths.configDir.resolve("auditoria.exemplo.yaml")

    /** Modelo versionado com uma linha trocada (texto exato `de` -> `para`). */
    private fun comAlteracao(de: String, para: String): Path {
        val original = Files.readString(modelo)
        assertTrue(de in original, "trecho '$de' não existe no modelo")
        return pasta.resolve("auditoria.yaml").also { Files.writeString(it, original.replace(de, para)) }
    }

    private fun carregar(p: Path) = RegrasDeAuditoria.fromYaml(p, vinculos)

    @Test
    fun modeloVersionadoCarrega() {
        val r = carregar(modelo)
        assertEquals(1, r.versao)
        assertEquals(2, r.pertoDoVencimentoDias)
        assertEquals(2, r.posseAntesDoVencimentoDias)
        assertEquals(3, r.passaBolaTrocas)
        assertEquals(UnidadeDias.CORRIDO, r.unidade)
        assertEquals(setOf("Cancelled", "Canceled", "Dropped"), r.statusCancelamento)
        assertEquals(CodigoSinal.entries.toSet(), r.sinais.keys)
        assertEquals(3, r.sinais.getValue(CodigoSinal.PASSA_BOLA).gravidade)
        assertEquals(1, r.sinais.getValue(CodigoSinal.VENCIMENTO_TASK_REVIEW_DIVERGENTE).gravidade)
        assertEquals(2, r.sinais.getValue(CodigoSinal.CANCELAMENTO_SUSPEITO).gravidade)
        assertTrue(r.sinais.values.all { it.ativo })
    }

    @Test
    fun arquivoEstrito() {
        assertFailsWith<IllegalArgumentException> { carregar(comAlteracao("passaBolaTrocas: 3", "passaBolaTrocas: 3\nchaveInventada: 1")) }
        assertFailsWith<IllegalArgumentException> { carregar(comAlteracao("versao: 1", "versao: 2")) }
        assertFailsWith<IllegalArgumentException> { carregar(comAlteracao("unidade: corrido", "unidade: semanal")) }
    }

    @Test
    fun limiteDaReducaoNoYaml() {
        assertEquals(0.2, carregar(modelo).estimativaReducaoMinima)
        assertEquals(2, carregar(modelo).sinais.getValue(CodigoSinal.ESTIMATIVA_REDUZIDA).gravidade)
        for (ruim in listOf("0.04", "0.91", "-1", "1")) {
            val e = assertFailsWith<IllegalArgumentException> { carregar(comAlteracao("estimativaReducaoMinima: 0.2", "estimativaReducaoMinima: $ruim")) }
            assertTrue("estimativaReducaoMinima" in e.message.orEmpty(), e.message)
        }
        // Limites inclusivos passam.
        assertEquals(0.05, carregar(comAlteracao("estimativaReducaoMinima: 0.2", "estimativaReducaoMinima: 0.05")).estimativaReducaoMinima)
        assertEquals(0.9, carregar(comAlteracao("estimativaReducaoMinima: 0.2", "estimativaReducaoMinima: 0.9")).estimativaReducaoMinima)
    }

    @Test
    fun unidadeUtilEhAceita() {
        assertEquals(UnidadeDias.UTIL, carregar(comAlteracao("unidade: corrido", "unidade: util")).unidade)
    }

    @Test
    fun limitesValidos() {
        fun falhaCitando(chave: String, de: String, para: String) {
            val e = assertFailsWith<IllegalArgumentException> { carregar(comAlteracao(de, para)) }
            assertTrue(chave in e.message.orEmpty(), e.message)
        }
        falhaCitando("pertoDoVencimentoDias", "pertoDoVencimentoDias: 2", "pertoDoVencimentoDias: 31")
        falhaCitando("pertoDoVencimentoDias", "pertoDoVencimentoDias: 2", "pertoDoVencimentoDias: -1")
        falhaCitando("passaBolaTrocas", "passaBolaTrocas: 3", "passaBolaTrocas: 1")
        falhaCitando("passaBolaTrocas", "passaBolaTrocas: 3", "passaBolaTrocas: 11")
        falhaCitando("posseAntesDoVencimentoDias", "posseAntesDoVencimentoDias: 2", "posseAntesDoVencimentoDias: 31")
        // limites inclusivos passam
        carregar(comAlteracao("pertoDoVencimentoDias: 2", "pertoDoVencimentoDias: 0"))
        carregar(comAlteracao("pertoDoVencimentoDias: 2", "pertoDoVencimentoDias: 30"))
        carregar(comAlteracao("passaBolaTrocas: 3", "passaBolaTrocas: 2"))
        carregar(comAlteracao("passaBolaTrocas: 3", "passaBolaTrocas: 10"))
        // gravidade fora de 1..3 cita o código
        val e = assertFailsWith<IllegalArgumentException> {
            carregar(comAlteracao("PASSA_BOLA: { gravidade: 3 }", "PASSA_BOLA: { gravidade: 4 }"))
        }
        assertTrue("PASSA_BOLA" in e.message.orEmpty(), e.message)
        assertFailsWith<IllegalArgumentException> {
            carregar(comAlteracao("PASSA_BOLA: { gravidade: 3 }", "PASSA_BOLA: { gravidade: 0 }"))
        }
    }

    @Test
    fun todoCodigoTemRegra() {
        val ausente = assertFailsWith<IllegalArgumentException> {
            carregar(comAlteracao("  PASSA_BOLA: { gravidade: 3 }\n", ""))
        }
        assertTrue("PASSA_BOLA" in ausente.message.orEmpty(), ausente.message)
        val desconhecido = assertFailsWith<IllegalArgumentException> {
            carregar(comAlteracao("  PASSA_BOLA: { gravidade: 3 }\n", "  PASSA_BOLA: { gravidade: 3 }\n  CODIGO_INVENTADO: { gravidade: 2 }\n"))
        }
        assertTrue("CODIGO_INVENTADO" in desconhecido.message.orEmpty(), desconhecido.message)
    }

    @Test
    fun sinalPodeSerDesativado() {
        val r = carregar(comAlteracao("PASSA_BOLA: { gravidade: 3 }", "PASSA_BOLA: { gravidade: 3, ativo: false }"))
        assertEquals(false, r.sinais.getValue(CodigoSinal.PASSA_BOLA).ativo)
    }

    @Test
    fun tiposReviewVemDosVinculos() {
        assertEquals(vinculos.tiposReview, carregar(modelo).tiposReview)
        // o auditoria.yaml não tem a chave: se tiver, falha por ser estrito
        assertFailsWith<IllegalArgumentException> {
            carregar(comAlteracao("passaBolaTrocas: 3", "passaBolaTrocas: 3\ntiposReview: [\"Outra\"]"))
        }
    }

    @Test
    fun emLoteMinimoTemPadraoCincoEFaixaValida() {
        assertEquals(5, carregar(modelo).emLoteMinimo)
        assertEquals(3, carregar(comAlteracao("emLoteMinimo: 5", "emLoteMinimo: 3")).emLoteMinimo)
        // sem a chave vale o padrão
        assertEquals(5, carregar(comAlteracao("emLoteMinimo: 5\n", "")).emLoteMinimo)
        for (ruim in listOf("1", "0", "-2", "101")) {
            val e = assertFailsWith<IllegalArgumentException> { carregar(comAlteracao("emLoteMinimo: 5", "emLoteMinimo: $ruim")) }
            assertTrue("emLoteMinimo" in e.message.orEmpty(), e.message)
        }
        carregar(comAlteracao("emLoteMinimo: 5", "emLoteMinimo: 2"))
        carregar(comAlteracao("emLoteMinimo: 5", "emLoteMinimo: 100"))
    }

    @Test
    fun statusDeCancelamentoNaoPodeSerVazio() {
        assertFailsWith<IllegalArgumentException> {
            carregar(comAlteracao("statusCancelamento: [Cancelled, Canceled, Dropped]", "statusCancelamento: []"))
        }
    }
}
