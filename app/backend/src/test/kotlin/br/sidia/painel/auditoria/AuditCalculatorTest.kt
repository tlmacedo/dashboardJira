package br.sidia.painel.auditoria

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.sync.ParDeChaves
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Junta sinais, Task × Review e "no prazo" no período (cenário AUD-1, vencimento 15/10, fuso de Manaus). */
class AuditCalculatorTest {
    private val m = CenarioMulti()
    private val c = m.c
    private val calc = AuditCalculator(c.ctx)

    // Período de outubro em Manaus: [01/10 00:00, 01/11 00:00) = [04:00Z, 04:00Z).
    private val de = m.i("2026-10-01T04:00:00Z")
    private val ate = m.i("2026-11-01T04:00:00Z")
    private val diaDe = LocalDate.parse("2026-10-01")
    private val diaAte = LocalDate.parse("2026-10-31")

    private fun calcular(
        comEventos: List<AuditIssue> = emptyList(),
        concluidas: List<AuditIssue> = emptyList(),
        pares: ParesLidos = ParesLidos(emptyList(), emptyList(), 0, false),
        calculador: AuditCalculator = calc,
        inicio: java.time.Instant = de,
        fim: java.time.Instant = ate,
    ) = calculador.calcular(comEventos, concluidas, pares, inicio, fim, diaDe, diaAte)

    /** Troca de responsável por não autorizado (`carla`): gera RESPONSAVEL_TROCADO_NAO_AUTORIZADO. */
    private fun trocaPorCarla(key: String, at: String) =
        m.issue(key, eventos = listOf(m.ev(key, "assignee", "ana", "bruno", at, "carla")), assignee = "bruno")

    @Test
    fun periodoFiltraPelaDataDoEvento() {
        // O período é [de, ate): 30/10 23:30 em Manaus (31/10 03:30Z) entra com ate = 31/10 04:00Z; às 04:00Z em ponto não.
        val dentro = trocaPorCarla("TG-1", "2026-10-31T03:30:00Z")
        val naFronteira = trocaPorCarla("TG-2", "2026-10-31T04:00:00Z")
        val antes = trocaPorCarla("TG-3", "2026-10-01T03:59:00Z")
        val noInicio = trocaPorCarla("TG-4", "2026-10-01T04:00:00Z")
        val r = calcular(comEventos = listOf(dentro, naFronteira, antes, noInicio), fim = m.i("2026-10-31T04:00:00Z"))
        assertEquals(setOf("TG-1", "TG-4"), r.sinais.map { it.issueKey }.toSet())
    }

    @Test
    fun ordemSemRankingDePessoas() {
        // Mesma data: desempata pela ordem natural das chaves (TG-2 antes de TG-10), depois pelo código; nunca por pessoa.
        val r = calcular(
            comEventos = listOf(
                trocaPorCarla("TG-10", "2026-10-03T12:00:00Z"),
                trocaPorCarla("TG-2", "2026-10-03T12:00:00Z"),
                trocaPorCarla("TG-7", "2026-10-05T12:00:00Z"),
            ),
        )
        assertEquals(listOf("TG-7", "TG-2", "TG-10"), r.sinais.map { it.issueKey })
        // Contagem por código, na ordem do enum, com todos os códigos presentes.
        assertEquals(CodigoSinal.entries.toList(), r.porCodigo.keys.toList())
        assertEquals(3, r.porCodigo.getValue(CodigoSinal.RESPONSAVEL_TROCADO_NAO_AUTORIZADO))
    }

    @Test
    fun versaoDasRegrasNoResultado() {
        assertEquals("regras v1, labels v1", calcular().versaoRegras)
    }

    @Test
    fun reporterAlteradoViraAviso() {
        val issue = m.issue("TG-1", eventos = listOf(m.ev("TG-1", "reporter", null, null, "2026-10-03T12:00:00Z", "carla")))
        val aviso = calcular(comEventos = listOf(issue)).avisos.single { it.codigo == "REPORTER_ALTERADO" }
        assertEquals(AvisoDto.INFORMATIVO, aviso.gravidade)
        assertEquals("1", aviso.params["n"])
    }

    @Test
    fun vencimentoIlegivelViraAviso() {
        val issue = m.issue("TG-1", eventos = listOf(m.ev("TG-1", "duedate", "15/10/2026", "2026-10-20", "2026-10-03T12:00:00Z", "carla")))
        val r = calcular(comEventos = listOf(issue))
        assertEquals("1", r.avisos.single { it.codigo == "VENCIMENTO_ILEGIVEL" }.params["n"])
        // sem exceção e sem sinal de "vencimento alterado" (não dá para comparar uma data ilegível)
        assertTrue(r.sinais.none { it.codigo == CodigoSinal.VENCIMENTO_ALTERADO_NAO_AUTORIZADO })
    }

    @Test
    fun statusSemMapaIgnoraIssueEAvisa() {
        val estranha = trocaPorCarla("TG-1", "2026-10-03T12:00:00Z").copy(status = "Estado Novo")
        val ok = trocaPorCarla("TG-2", "2026-10-03T12:00:00Z")
        val feita = m.issue("TG-3", status = "Outro Novo", resolution = "Done", resolved = "2026-10-10T12:00:00Z")
        val r = calcular(comEventos = listOf(estranha, ok), concluidas = listOf(feita))
        assertEquals(listOf("TG-2"), r.sinais.map { it.issueKey })
        val avisos = r.avisos.filter { it.codigo == "ISSUES_IGNORADAS_STATUS" }.associate { it.params.getValue("status") to it.params.getValue("n") }
        assertEquals(mapOf("Estado Novo" to "1", "Outro Novo" to "1"), avisos)
        assertEquals(0, r.noPrazo.concluidas)
    }

    @Test
    fun vinculoAmbiguoViraAviso() {
        val r = calcular(pares = ParesLidos(emptyList(), emptyList(), 2, false))
        assertEquals("2", r.avisos.single { it.codigo == "VINCULO_AMBIGUO" }.params["n"])
        assertTrue(r.avisos.none { it.codigo == "VINCULOS_INDISPONIVEIS" })
    }

    @Test
    fun semVinculosCardVazioComAviso() {
        val r = calcular(pares = ParesLidos(emptyList(), emptyList(), 0, true))
        assertEquals(ResumoTaskReview(0, 0, null), r.taskReview)
        val aviso = r.avisos.single { it.codigo == "VINCULOS_INDISPONIVEIS" }
        assertTrue(aviso.params.isEmpty())
        assertEquals(AvisoDto.INFORMATIVO, aviso.gravidade)
        assertTrue(r.avisos.none { it.codigo == "VINCULO_AMBIGUO" })
    }

    @Test
    fun taskReviewEntraNoResultadoEMaisSinal() {
        val task = m.issue("TG-1", duedate = "2026-10-15")
        val review = m.issue(
            "TG-101", duedate = "2026-10-18", tipo = "Checklist Review",
            eventos = listOf(m.ev("TG-101", "duedate", "2026-10-15", "2026-10-18", "2026-10-10T12:00:00Z", "carla")),
        )
        val r = calcular(pares = ParesLidos(listOf(task, review), listOf(ParDeChaves("TG-1", "TG-101")), 0, false))
        assertEquals(ResumoTaskReview(1, 1, 100.0), r.taskReview)
        assertEquals(1, r.pares.size)
        assertEquals(listOf(CodigoSinal.VENCIMENTO_TASK_REVIEW_DIVERGENTE), r.sinais.map { it.codigo })
    }

    @Test
    fun noPrazoNoResultado() {
        val feita = m.issue("TG-9", status = "Done", resolution = "Done", resolved = "2026-10-14T12:00:00Z")
        val r = calcular(concluidas = listOf(feita))
        assertEquals(1, r.noPrazo.concluidas)
        assertEquals(1, r.noPrazo.noPrazo)
    }

    // ---- Alteração em lote (I-15): modificador informativo ----

    private fun lote(n: Int, minuto: String = "2026-10-10T12:00", campo: String = "assignee") = (1..n).map { k ->
        val key = "TG-${100 + k}"
        // Cada uma em segundos diferentes do mesmo minuto.
        val at = "$minuto:${"%02d".format(k)}Z"
        val e = if (campo == "assignee") m.ev(key, "assignee", "ana", "bruno", at, "carla") else m.ev(key, "duedate", "2026-10-15", "2026-10-20", at, "carla")
        m.issue(key, eventos = listOf(e), assignee = "bruno")
    }

    @Test
    fun emLoteQuandoAtingeOMinimoDeIssues() {
        val r = calcular(comEventos = lote(5))
        assertEquals(5, r.sinais.size)
        assertTrue(r.sinais.all { it.emLote == 5 }, r.sinais.map { it.emLote }.toString())
    }

    @Test
    fun foraDoMinimoNaoMarca() {
        assertTrue(calcular(comEventos = lote(4)).sinais.all { it.emLote == null })
    }

    @Test
    fun minutosDiferentesNaoSeSomam() {
        val juntas = lote(3, "2026-10-10T12:00") + lote(2, "2026-10-10T12:01").map { it.copy(key = it.key + "9", events = it.events.map { e -> e.copy(issueKey = e.issueKey + "9") }) }
        assertTrue(calcular(comEventos = juntas).sinais.all { it.emLote == null })
    }

    @Test
    fun camposDiferentesNaoSeSomam() {
        val misto = lote(3) + lote(2, campo = "duedate").map { it.copy(key = it.key + "9", events = it.events.map { e -> e.copy(issueKey = e.issueKey + "9") }) }
        assertTrue(calcular(comEventos = misto).sinais.all { it.emLote == null })
    }

    @Test
    fun pessoasDiferentesNaoSeSomam() {
        val base = lote(5)
        val trocado = base.mapIndexed { idx, issue ->
            if (idx < 3) issue else issue.copy(events = issue.events.map { it.copy(authorRef = "davi") })
        }
        assertTrue(calcular(comEventos = trocado).sinais.all { it.emLote == null })
    }

    @Test
    fun limiteVemDoYaml() {
        val tres = AuditCalculator(c.ctx.copy(regras = c.regras.copy(emLoteMinimo = 3)))
        assertTrue(calcular(comEventos = lote(3), calculador = tres).sinais.all { it.emLote == 3 })
        assertTrue(calcular(comEventos = lote(2), calculador = tres).sinais.all { it.emLote == null })
    }

    @Test
    fun emLoteContaIssuesDistintasNaoEventos() {
        // Cinco eventos do mesmo autor, campo e minuto, mas só em 3 issues: não é lote de 5 issues.
        val tres = lote(3)
        val repetido = tres[0].copy(events = tres[0].events + (1..3).map { k -> m.ev("TG-101", "assignee", "bruno", "ana", "2026-10-10T12:00:3$k" + "Z", "carla") })
        assertTrue(calcular(comEventos = listOf(repetido, tres[1], tres[2])).sinais.all { it.emLote == null })
    }

    @Test
    fun emLoteNaoCriaSinalNemMudaOsExistentes() {
        // Mesma entrada com mínimo 5 (marca) e 100 (não marca): só o emLote muda.
        val entrada = lote(5)
        val marcado = AuditCalculator(c.ctx.copy(regras = c.regras.copy(emLoteMinimo = 5))).let { calcular(comEventos = entrada, calculador = it) }.sinais
        val limpo = AuditCalculator(c.ctx.copy(regras = c.regras.copy(emLoteMinimo = 100))).let { calcular(comEventos = entrada, calculador = it) }.sinais
        assertEquals(5, marcado.size)
        assertTrue(marcado.all { it.emLote == 5 } && limpo.all { it.emLote == null })
        assertEquals(limpo, marcado.map { it.copy(emLote = null) })
        assertEquals(limpo.map { it.gravidade to it.resultado }, marcado.map { it.gravidade to it.resultado })
    }

    // ---- Todos os códigos (critério de sucesso da spec 10) ----

    @Test
    fun todoCodigoDetectadoNoCenarioCompleto() {
        val fechamento = { key: String, autor: String ->
            listOf(
                m.ev(key, "status", "Open", "Done", "2026-10-14T12:00:00Z", autor),
                m.ev(key, "resolution", null, "Done", "2026-10-14T12:00:00Z", autor),
            )
        }
        val issues = listOf(
            // 1. vencimento alterado pelo próprio responsável
            m.issue("TG-101", eventos = listOf(m.ev("TG-101", "duedate", "2026-10-15", "2026-10-20", "2026-10-10T12:00:00Z", "ana"))),
            // 2. vencimento removido
            m.issue("TG-102", duedate = null, eventos = listOf(m.ev("TG-102", "duedate", "2026-10-15", null, "2026-10-10T12:00:00Z", "coord.um"))),
            // 4. responsável trocado por quem não podia
            trocaPorCarla("TG-104", "2026-10-03T12:00:00Z"),
            // 5. passa-bola: três trocas na janela do vencimento
            m.issue(
                "TG-105", assignee = "davi",
                eventos = listOf(
                    m.ev("TG-105", "assignee", "ana", "bruno", "2026-10-13T15:00:00Z", "pl.alfa"),
                    m.ev("TG-105", "assignee", "bruno", "carla", "2026-10-14T15:00:00Z", "pl.alfa"),
                    m.ev("TG-105", "assignee", "carla", "davi", "2026-10-16T15:00:00Z", "pl.alfa"),
                ),
            ),
            // 6. troca perto do vencimento por não autorizado
            m.issue("TG-106", assignee = "bruno", eventos = listOf(m.ev("TG-106", "assignee", "ana", "bruno", "2026-10-15T15:00:00Z", "carla"))),
            // 7. label depois de fechada, por não autorizado
            m.issue(
                "TG-107", status = "Done", resolution = "Done",
                eventos = fechamento("TG-107", "ana") + m.ev("TG-107", "labels", null, "DUEDATE_NEGOTIATED", "2026-10-14T15:00:00Z", "carla"),
            ),
            // 8. label depois de fechada, pelo próprio responsável
            m.issue(
                "TG-108", status = "Done", resolution = "Done",
                eventos = fechamento("TG-108", "ana") + m.ev("TG-108", "labels", null, "DUEDATE_NEGOTIATED", "2026-10-14T15:00:00Z", "ana"),
            ),
            // 9. label tardia com posse antecipada (ana é a responsável desde a criação; aberta, no dia do vencimento)
            m.issue("TG-109", eventos = listOf(m.ev("TG-109", "labels", null, "LATE_SENT", "2026-10-15T20:00:00Z", "pl.alfa"))),
            // 10. label depois do vencimento
            m.issue("TG-110", eventos = listOf(m.ev("TG-110", "labels", null, "ONHOLDBY-PL", "2026-10-17T15:00:00Z", "pl.alfa"))),
            // 11. cancelamento depois do vencimento
            m.issue("TG-111", status = "Cancelled", eventos = listOf(m.ev("TG-111", "status", "Open", "Cancelled", "2026-10-16T15:00:00Z", "coord.um"))),
            // 13. horas registradas dois dias úteis depois do início declarado
            m.issue("TG-113").copy(
                worklogs = listOf(br.sidia.painel.sync.WorklogRecord("9", "ana", java.time.Instant.parse("2026-10-15T13:00:00Z"), 3600, java.time.Instant.parse("2026-10-19T15:00:00Z"))),
            ),
            // 12. estimativa reduzida depois de iniciar, pelo próprio responsável
            m.issue(
                "TG-112", status = "In Progress",
                eventos = listOf(
                    m.ev("TG-112", "status", "Open", "In Progress", "2026-10-05T12:00:00Z", "ana"),
                    m.ev("TG-112", "timeoriginalestimate", "36000", "21600", "2026-10-08T12:00:00Z", "ana"),
                ),
            ),
        )
        // 3. Task × Review divergente
        val task = m.issue("TG-103", duedate = "2026-10-15")
        val review = m.issue(
            "TG-203", duedate = "2026-10-18", tipo = "Checklist Review",
            eventos = listOf(m.ev("TG-203", "duedate", "2026-10-15", "2026-10-18", "2026-10-10T12:00:00Z", "carla")),
        )
        val r = calcular(comEventos = issues, pares = ParesLidos(listOf(task, review), listOf(ParDeChaves("TG-103", "TG-203")), 0, false))
        val achados = r.sinais.map { it.codigo }.toSet()
        assertEquals(CodigoSinal.entries.toSet(), achados, "faltou: ${CodigoSinal.entries.toSet() - achados}")
        // Task × Review é alerta (informativo); nenhum sinal deste cenário é de lote.
        assertEquals(Resultado.ALERTA, r.sinais.single { it.codigo == CodigoSinal.VENCIMENTO_TASK_REVIEW_DIVERGENTE }.resultado)
        assertNull(r.sinais.firstOrNull { it.emLote != null })
    }
}
