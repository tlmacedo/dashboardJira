package br.sidia.painel.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Leitura de `fields.issuelinks` do JSON recebido do Jira DC; só lógica, sem banco. */
class IssueLinksTest {
    private fun issue(links: String?): JsonObject {
        val campo = if (links == null) "" else """, "issuelinks": $links"""
        return Json.parseToJsonElement("""{"key":"X","fields":{"summary":"s"$campo}}""").jsonObject
    }

    private fun item(tipo: String, ponta: String, chave: String, inward: String = "is blocked by", outward: String = "blocks") =
        """{"id":"1","type":{"name":"$tipo","inward":"$inward","outward":"$outward"},"$ponta":{"id":"9","key":"$chave"}}"""

    @Test
    fun outwardIssueViraOrigem() {
        val r = IssueLinks.ler("TG-2", issue("[${item("Blocks", "outwardIssue", "TG-1")}]"))
        assertEquals(listOf(VinculoRecord("TG-2", "TG-1", "Blocks", DirecaoVinculo.OUTWARD)), r)
    }

    @Test
    fun inwardIssueViraDestino() {
        val r = IssueLinks.ler("TG-1", issue("[${item("Blocks", "inwardIssue", "TG-2")}]"))
        assertEquals(listOf(VinculoRecord("TG-2", "TG-1", "Blocks", DirecaoVinculo.OUTWARD)), r)
    }

    @Test
    fun tipoSimetricoOrdenaPelaChaveNatural() {
        val simetrico = item("Relates", "outwardIssue", "TG-9", inward = "relates to", outward = "relates to")
        val r = IssueLinks.ler("TG-10", issue("[$simetrico]"))
        assertEquals(listOf(VinculoRecord("TG-9", "TG-10", "Relates", DirecaoVinculo.SIMETRICO)), r)
        // Visto pela outra ponta, o mesmo registro.
        val inverso = item("Relates", "inwardIssue", "TG-10", inward = "relates to", outward = "relates to")
        assertEquals(r, IssueLinks.ler("TG-9", issue("[$inverso]")))
    }

    @Test
    fun campoAusenteDevolveNulo() {
        assertNull(IssueLinks.ler("TG-1", issue(null)))
        assertNull(IssueLinks.ler("TG-1", issue("null")))
        assertNull(IssueLinks.ler("TG-1", Json.parseToJsonElement("""{"key":"TG-1"}""").jsonObject))
    }

    @Test
    fun listaVaziaDevolveVazia() {
        assertEquals(emptyList(), IssueLinks.ler("TG-1", issue("[]")))
    }

    @Test
    fun itemMalformadoIgnorado() {
        val semNome = """{"type":{"inward":"a","outward":"b"},"outwardIssue":{"key":"TG-2"}}"""
        val semPonta = """{"type":{"name":"Blocks"}}"""
        val duasPontas = """{"type":{"name":"Blocks"},"outwardIssue":{"key":"TG-2"},"inwardIssue":{"key":"TG-3"}}"""
        val chaveInjetada = """{"type":{"name":"Blocks"},"outwardIssue":{"key":"TG-1) or (1=1"}}"""
        val autoVinculo = item("Blocks", "outwardIssue", "TG-1")
        val naoObjeto = "42"
        val r = IssueLinks.ler("TG-1", issue("[$semNome,$semPonta,$duasPontas,$chaveInjetada,$autoVinculo,$naoObjeto]"))
        assertEquals(emptyList(), r)
    }

    @Test
    fun itemValidoSobreviveAoMalformadoEDuplicadoNaoRepete() {
        val bom = item("Blocks", "outwardIssue", "TG-2")
        val r = IssueLinks.ler("TG-1", issue("""[{"type":{"name":"Blocks"}},$bom,$bom]"""))
        assertEquals(listOf(VinculoRecord("TG-1", "TG-2", "Blocks", DirecaoVinculo.OUTWARD)), r)
    }

    @Test
    fun naoGuardaDadosDaOutraPonta() {
        val comDados = """{"id":"1","type":{"name":"Blocks","inward":"is blocked by","outward":"blocks"},
            "outwardIssue":{"id":"9","key":"TG-1","self":"https://jira.exemplo.invalid/x",
            "fields":{"summary":"segredo","status":{"name":"Done"},"priority":{"name":"High"}}}}"""
        val r = assertNotNull(IssueLinks.ler("TG-2", issue("[$comDados]")))
        assertEquals(1, r.size)
        val texto = r.single().toString()
        assertFalse("segredo" in texto || "Done" in texto || "High" in texto || "jira.exemplo" in texto, texto)
        assertEquals(setOf("fromKey", "toKey", "tipo", "direcao"), VinculoRecord::class.java.declaredFields.map { it.name }.toSet())
    }
}
