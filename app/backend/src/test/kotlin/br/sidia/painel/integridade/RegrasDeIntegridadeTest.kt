package br.sidia.painel.integridade

import br.sidia.painel.TestPaths
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `integridade.yaml`: valores do modelo e leitura estrita (gravidade 1..3, todo sinal com gravidade, chave desconhecida, erro sem o valor lido). */
class RegrasDeIntegridadeTest {
    private fun arquivo(conteudo: String): Path = Files.createTempFile("integridade", ".yaml").also {
        Files.writeString(it, conteudo)
        it.toFile().deleteOnExit()
    }

    private val modelo: String = Files.readString(TestPaths.configDir.resolve("integridade.exemplo.yaml"))

    @Test
    fun modeloTemOsValoresDoPlano() {
        val r = RegrasDeIntegridade.fromYaml(TestPaths.configDir.resolve("integridade.exemplo.yaml"))
        assertEquals(1, r.versao)
        val ordem = SinalIntegridade.entries
        // 3 para os cinco primeiros, 2 para os cinco seguintes e 1 para os dois últimos.
        assertEquals(ordem.take(5).map { 3 } + ordem.drop(5).take(5).map { 2 } + listOf(1, 1), ordem.map { r.gravidades.getValue(it) })
        assertEquals(SinalIntegridade.entries.toSet() - SinalIntegridade.ESTIMATIVA_AUMENTADA, r.ativos)
        assertEquals(2.0, r.trocaAntesDeFecharHoras)
        assertEquals(15, r.criadaEFechadaMinutos)
        assertEquals(2.0, r.reabreEFechaHoras)
        assertEquals(6, r.loteQuantidade)
        assertEquals(10, r.loteMinutos)
        assertEquals(0.7, r.fracaoVidaTardia)
        assertEquals(0.3, r.estimativaAumento)
        assertEquals(listOf("CRITICAL", "BLOCKER"), r.labelsCriticidade)
        assertEquals(2, r.labelRemovidaDias)
        assertEquals(22, r.limiteInicioHora)
        assertEquals(12, r.pontosAlto)
        assertEquals(3.0, r.multiploAlto)
        assertEquals(3, r.gravesAlto)
        assertEquals(6, r.pontosMedio)
        assertEquals(2.0, r.multiploMedio)
        assertEquals(8, r.concluidasMinimas)
        assertEquals(0.05, r.pisoMediana)
        assertEquals(RegrasDeIntegridade.PADRAO, r, "o PADRÃO dos testes é o modelo versionado")
        assertEquals("integridade v1", r.rotulo)
    }

    @Test
    fun regrasEstritas() {
        fun troca(de: String, para: String) = modelo.replace(de, para).also { assertTrue(it != modelo, "o modelo não tem '$de'") }
        val ruins = listOf(
            troca("versao: 1", "versao: 2"),
            // gravidade fora de 1..3
            troca("RESGATE_POR_PRORROGACAO: 3", "RESGATE_POR_PRORROGACAO: 0"),
            troca("RESGATE_POR_PRORROGACAO: 3", "RESGATE_POR_PRORROGACAO: 4"),
            // sinal sem gravidade
            modelo.lines().filter { !it.trim().startsWith("FECHADA_NO_LIMITE: 1") }.joinToString("\n"),
            // chave desconhecida (raiz, em gravidades e em ativos)
            modelo + "\nchaveNova: 1\n",
            troca("  FECHADA_NO_LIMITE: 1", "  FECHADA_NO_LIMITE: 1\n  SINAL_INVENTADO: 2"),
            troca("  - PULOU_O_FLUXO", "  - SINAL_INVENTADO"),
            // limites
            troca("trocaAntesDeFecharHoras: 2", "trocaAntesDeFecharHoras: 0"),
            troca("criadaEFechadaMinutos: 15", "criadaEFechadaMinutos: 0"),
            troca("reabreEFechaHoras: 2", "reabreEFechaHoras: -1"),
            troca("loteQuantidade: 6", "loteQuantidade: 1"),
            troca("loteMinutos: 10", "loteMinutos: 0"),
            troca("fracaoVidaTardia: 0.7", "fracaoVidaTardia: 1.0"),
            troca("estimativaAumento: 0.3", "estimativaAumento: 0.0"),
            troca("labelsCriticidade: [\"CRITICAL\", \"BLOCKER\"]", "labelsCriticidade: [\"  \"]"),
            troca("labelRemovidaDias: 2", "labelRemovidaDias: -1"),
            troca("limiteInicioHora: 22", "limiteInicioHora: 24"),
            troca("pontosAlto: 12", "pontosAlto: 0"),
            troca("multiploAlto: 3.0", "multiploAlto: 0.5"),
            troca("gravesAlto: 3", "gravesAlto: 0"),
            troca("pontosMedio: 6", "pontosMedio: 13"),
            troca("multiploMedio: 2.0", "multiploMedio: 3.5"),
            troca("concluidasMinimas: 8", "concluidasMinimas: 0"),
            troca("pisoMediana: 0.05", "pisoMediana: 0.0"),
            modelo.lines().filter { !it.startsWith("pisoMediana") }.joinToString("\n"),
            "versao: 1\ngravidades: texto\n",
        )
        for (r in ruins) assertFailsWith<IllegalArgumentException>(r) { RegrasDeIntegridade.fromYaml(arquivo(r)) }
    }

    @Test
    fun erroCitaOArquivoENuncaOValorLido() {
        val caminho = arquivo(modelo.replace("pontosAlto: 12", "pontosAlto: SEGREDO123"))
        val e = assertFailsWith<IllegalArgumentException> { RegrasDeIntegridade.fromYaml(caminho) }
        assertTrue(caminho.toString() in e.message.orEmpty(), e.message)
        assertFalse("SEGREDO123" in e.message.orEmpty(), e.message)
        val desconhecida = arquivo(modelo + "\nchaveSecretaXyz: 1\n")
        val e2 = assertFailsWith<IllegalArgumentException> { RegrasDeIntegridade.fromYaml(desconhecida) }
        assertFalse("chaveSecretaXyz" in e2.message.orEmpty(), e2.message)
        assertEquals(null, e2.cause, "a causa do parser (que traz o valor) não é encadeada")
        // Sinal desconhecido também não ecoa o nome lido.
        val sinal = arquivo(modelo.replace("  - PULOU_O_FLUXO", "  - SINAL_SECRETO_XYZ"))
        val e3 = assertFailsWith<IllegalArgumentException> { RegrasDeIntegridade.fromYaml(sinal) }
        assertFalse("SINAL_SECRETO_XYZ" in e3.message.orEmpty(), e3.message)
    }

    @Test
    fun gravidadeEAtivoPorSinal() {
        val r = RegrasDeIntegridade.PADRAO
        assertTrue(r.ativo(SinalIntegridade.PULOU_O_FLUXO))
        assertFalse(r.ativo(SinalIntegridade.ESTIMATIVA_AUMENTADA))
        assertEquals(3, r.gravidade(SinalIntegridade.FECHAMENTO_EM_LOTE))
        assertEquals(1, r.gravidade(SinalIntegridade.FECHADA_NO_LIMITE))
    }
}
