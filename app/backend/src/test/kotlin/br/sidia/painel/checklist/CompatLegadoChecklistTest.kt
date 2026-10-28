package br.sidia.painel.checklist

import br.sidia.painel.TestPaths
import br.sidia.painel.integridade.EvidenciaIntegridade
import br.sidia.painel.integridade.NivelIntegridade
import br.sidia.painel.integridade.NiveisPorPessoa
import br.sidia.painel.integridade.RegrasDeIntegridade
import br.sidia.painel.integridade.SinalIntegridade
import org.junit.jupiter.api.Assumptions
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Compatibilidade com o app Python anterior, conferida no código do legado. A lógica dos alertas do par e dos níveis da
 * Integridade está em `legacy/backend/jiradash/reports.py` (`pair_row` e `integrity_view`), NÃO em `analytics.py`. O teste é
 * pulado se `legacy/` sair do repositório (Fase 8); as regras ficam provadas por `ParesChecklistReviewTest` e `NiveisPorPessoaTest`.
 */
class CompatLegadoChecklistTest {
    private fun reports(): String {
        val f = TestPaths.repoDir.resolve("legacy/backend/jiradash/reports.py")
        Assumptions.assumeTrue(Files.isRegularFile(f), "legacy/ não está no repositório: teste de compatibilidade pulado")
        return Files.readString(f)
    }

    @Test
    fun alertasDoParComoOLegado() {
        val py = reports()
        // As seis regras de `pair_row`, na mesma ordem do legado.
        val regrasDoLegado = listOf(
            "due_gap is not None and due_gap > 0",
            "p.resolved and (not r.resolved or r.resolved > p.resolved)",
            "p.assignee_id and p.assignee_id == r.assignee_id",
            "r.resolved and r_active_h < 0.5 and m.reopen_count(p, a.cat_of)",
            "not r.resolved and r.due and r.due < a.today",
            "r.due and r.resolved and local(r.resolved).date() > r.due",
        )
        for (regra in regrasDoLegado) assertTrue(regra in py, "regra do legado mudou: $regra")
        // O painel tem exatamente os seis alertas, um por regra, na mesma ordem.
        assertEquals(
            listOf("REVIEW_VENCE_DEPOIS", "PAI_FECHADO_ANTES", "MESMA_PESSOA", "REVIEW_CURTA_COM_PAI_REABERTO", "REVIEW_VENCIDA", "REVIEW_ATRASADA"),
            AlertaDoPar.entries.map { it.name },
        )
    }

    @Test
    fun fechamentoRapidoEPuloDoFluxoSaoExclusivosComoNoLegado() {
        val py = reports()
        // No legado é `if life_min < 15 ... elif not started ...`: um fechamento rápido gera um sinal só.
        assertTrue("if life_min < 15:" in py && "elif not started and r.time_spent_s == 0:" in py, "regra do legado mudou")
    }

    private fun evidencias(pessoa: String, g3: Int = 0, g2: Int = 0, g1: Int = 0): List<EvidenciaIntegridade> {
        val regras = RegrasDeIntegridade.PADRAO
        var k = 0
        fun uma(g: Int) = EvidenciaIntegridade(
            SinalIntegridade.entries.first { regras.gravidade(it) == g }, g, "TG-$pessoa-${++k}", pessoa, Instant.parse("2026-10-14T13:00:00Z"), emptyMap(),
        )
        return List(g3) { uma(3) } + List(g2) { uma(2) } + List(g1) { uma(1) }
    }

    @Test
    fun niveisComoOLegado() {
        val py = reports()
        // Limiares do legado: alto = 12 pontos, 3x a mediana e 3 graves; médio = 6 pontos e 2x; taxa = pontos / max(concluídas, 8); piso 0,05.
        for (trecho in listOf(
            "pts[pid] >= 12 and ratio >= 3 and strong >= 3", "pts[pid] >= 6 and ratio >= 2",
            "pts[pid] / max(denom[pid], 8)", "base = max(med, 0.05)", "SEVERITY[s] >= 3",
        )) assertTrue(trecho in py, "regra do legado mudou: $trecho")
        val r = RegrasDeIntegridade.PADRAO
        assertEquals(12, r.pontosAlto)
        assertEquals(3.0, r.multiploAlto, 1e-9)
        assertEquals(3, r.gravesAlto)
        assertEquals(6, r.pontosMedio)
        assertEquals(2.0, r.multiploMedio, 1e-9)
        assertEquals(8, r.concluidasMinimas)
        assertEquals(0.05, r.pisoMediana, 1e-9)
        // Comportamento: ana com 15 pontos e 4 graves sobre a mediana do time é ALTO; bruno com 6 pontos e 1,8x fica normal.
        val concluidas = mapOf("ana" to 10, "bruno" to 8, "carla" to 12, "dora" to 10)
        val todas = evidencias("ana", g3 = 4, g2 = 1, g1 = 1) + evidencias("bruno", g2 = 3) + evidencias("carla", g1 = 1)
        val resumo = NiveisPorPessoa.resumir(todas, concluidas, r, null)
        val porPessoa = resumo.pessoas.associateBy { it.pessoa }
        assertEquals(NivelIntegridade.ALTO, porPessoa.getValue("ana").nivel)
        assertEquals(NivelIntegridade.NORMAL, porPessoa.getValue("bruno").nivel)
    }
}
