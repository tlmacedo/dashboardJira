package br.sidia.painel.cards

import br.sidia.painel.domain.TimeUnitMode
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Leitura e validação dos filtros comuns dos cards de trabalho e prazos. */
class FiltrosDeCardTest {
    private val hoje = LocalDate.parse("2026-10-30")

    private fun ler(vararg p: Pair<String, String>, padrao: Int? = null, maxDias: Int = 366): FiltrosDeCard =
        FiltrosDeCard.ler(Parameters.build { p.forEach { append(it.first, it.second) } }, hoje, padrao, maxDias)

    private fun assert400(vararg p: Pair<String, String>, padrao: Int? = 90) {
        val e = assertFailsWith<ErroHttp>("${p.toList()}") { ler(*p, padrao = padrao) }
        assertEquals(HttpStatusCode.BadRequest, e.status)
    }

    @Test
    fun lerFiltrosValidos() {
        val f = ler(
            "tipo" to "Checklist", "tipo" to "Checklist Review", "prioridade" to "High", "prioridade" to "Medium",
            "squad" to "Alfa", "pessoa" to "Ana", "os" to "Android  17.0", "de" to "2026-10-01", "ate" to "2026-10-30",
            "unit" to "corrido",
        )
        assertEquals(listOf("Checklist", "Checklist Review"), f.tipos)
        assertEquals(listOf("High", "Medium"), f.prioridades)
        assertEquals("Alfa", f.squad)
        assertEquals("ana", f.pessoa, "o id da pessoa é normalizado para minúsculas")
        assertEquals("Android 17.0", f.os, "espaços internos colapsados, como no valor gravado")
        assertEquals(LocalDate.parse("2026-10-01"), f.de)
        assertEquals(LocalDate.parse("2026-10-30"), f.ate)
        assertEquals(TimeUnitMode.CORRIDO, f.unit)
    }

    @Test
    fun datasInvalidasDao400() {
        assert400("de" to "2026-13-01")
        assert400("ate" to "ontem")
        assert400("de" to "2026-10-30", "ate" to "2026-10-01")
        assert400("de" to "2024-01-01", "ate" to "2026-10-30") // período maior que o máximo
        assert400("de" to "+999999999-12-31")
    }

    @Test
    fun valoresRuinsDao400() {
        assert400("unit" to "semana")
        assert400("pessoa" to "../etc")
        assert400("tipo" to "x".repeat(101))
        assert400("prioridade" to "a\u0000b")
        assert400("squad" to "x".repeat(101))
    }

    @Test
    fun unitPadraoUtil() {
        assertEquals(TimeUnitMode.UTIL, ler().unit)
        assertEquals(TimeUnitMode.UTIL, ler("unit" to "util").unit)
    }

    @Test
    fun periodoPadraoEmNoventaDiasInclusivos() {
        val f = ler(padrao = 90)
        assertEquals(hoje, f.ate)
        assertEquals(90L, ChronoUnit.DAYS.between(f.de, f.ate) + 1)
        // Só `de` informado: `ate` vira hoje; só `ate`: `de` vem 90 dias antes dele.
        assertEquals(hoje, ler("de" to "2026-10-01", padrao = 90).ate)
        assertEquals(LocalDate.parse("2026-09-03"), ler("ate" to "2026-12-01", padrao = 90).de)
    }

    @Test
    fun semPeriodoPadraoNaoInventaDatas() {
        val f = ler(padrao = null)
        assertNull(f.de)
        assertNull(f.ate)
    }

    @Test
    fun limiteDoPeriodoEhInclusivo() {
        ler("de" to "2025-10-30", "ate" to "2026-10-30", maxDias = 366) // 366 dias: passa
        assert400("de" to "2025-10-29", "ate" to "2026-10-30", padrao = null)
    }

    private val campos = listOf(
        br.sidia.painel.sync.CampoDeFiltro("SKU Type", "sku-type", null),
        br.sidia.painel.sync.CampoDeFiltro("Checklist Type", "checklist-type", null),
    )

    private fun lerCampos(vararg p: Pair<String, String>): FiltrosDeCard =
        FiltrosDeCard.ler(Parameters.build { p.forEach { append(it.first, it.second) } }, hoje, null, 366, campos)

    @Test
    fun lerFiltroDeCampoRepetivel() {
        val f = lerCampos("campo.sku-type" to "MR", "campo.sku-type" to "1st Carrier (Global)", "campo.checklist-type" to "Full")
        assertEquals(setOf("MR", "1st Carrier (Global)"), f.campos.getValue("SKU Type"))
        assertEquals(setOf("Full"), f.campos.getValue("Checklist Type"))
        assertEquals(emptyMap(), lerCampos().campos)
    }

    @Test
    fun slugDesconhecido400ComCodigo() {
        val e = assertFailsWith<ErroHttp> { lerCampos("campo.nao-existe" to "1") }
        assertEquals(HttpStatusCode.BadRequest, e.status)
        assertEquals("FILTRO_INVALIDO", e.codigo)
        assertEquals(mapOf("campo" to "nao-existe"), e.params)
        // Sem a lista de campos conhecidos (card sem dicionário), todo slug é desconhecido.
        assertFailsWith<ErroHttp> { ler("campo.sku-type" to "MR") }
    }

    @Test
    fun valorComControle400() {
        assertFailsWith<ErroHttp> { lerCampos("campo.sku-type" to "a\u0007b") }
        assertFailsWith<ErroHttp> { lerCampos("campo.sku-type" to "x".repeat(121)) }
        lerCampos("campo.sku-type" to "x".repeat(120)) // no limite: passa
    }
}
