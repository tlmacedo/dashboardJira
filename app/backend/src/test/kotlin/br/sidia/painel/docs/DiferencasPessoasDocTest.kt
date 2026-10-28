package br.sidia.painel.docs

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/** O documento de diferenças esperadas do bloco 6a cita todos os cartões de pessoas e planejamento e os temas decididos. */
class DiferencasPessoasDocTest {
    private fun documento(): String {
        var dir: Path? = Path.of("").toAbsolutePath()
        while (dir != null) {
            val f = dir.resolve("docs/analise/diferencas-esperadas-pessoas-e-planejamento.md")
            if (Files.exists(f)) return Files.readString(f)
            dir = dir.parent
        }
        error("docs/analise/diferencas-esperadas-pessoas-e-planejamento.md não encontrado")
    }

    @Test
    fun documentoCitaOsIds() {
        val texto = documento()
        val esperados = (1..3).map { "CP-%02d".format(it) } + (1..9).map { "CA-%02d".format(it) } +
            (1..4).map { "GA-%02d".format(it) } + listOf("CL-01", "PL-01", "VE-01", "VE-02", "TD-01") +
            listOf("Calendário do time", "Plano de 4 a 8 semanas", "Vencimentos da semana", "Tendência de ocupação")
        val faltando = esperados.filterNot { it in texto }
        assertTrue(faltando.isEmpty(), "IDs e cards sem menção no documento: $faltando")
    }

    @Test
    fun documentoTrazAsDecisoesDoBloco6a() {
        val texto = documento().lowercase()
        for (tema in listOf(
            "sem capacidade", "990%", "atraso acumulado", "horas padrão por tipo", "de hoje até domingo", "sem ranking",
            "contagens", "somente leitura", "sem motivo", "horas de hoje", "pela capacidade", "ordem alfabética",
        )) {
            assertTrue(tema.lowercase() in texto, "tema ausente: $tema")
        }
    }

    @Test
    fun documentoNaoTemDadoPessoalReal() {
        // O repositório é público: nenhum username nem chave de issue de exemplo real.
        val texto = documento()
        for (proibido in listOf("ana.silva", "TG-107")) assertTrue(proibido !in texto, proibido)
    }
}
