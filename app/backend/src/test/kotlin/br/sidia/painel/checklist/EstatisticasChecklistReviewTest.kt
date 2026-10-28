package br.sidia.painel.checklist

import br.sidia.painel.trabalho.SnapDeTeste.snap
import br.sidia.painel.trabalho.SnapDeTeste.trilha
import br.sidia.painel.trabalho.Trb1
import kotlin.reflect.full.memberProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Estatísticas, aprendizados e lift de labels do Checklist × Review (CR-03 a CR-14). */
class EstatisticasChecklistReviewTest {
    private val regras = RegrasChecklistReview.PADRAO
    private val motor = ParesChecklistReview(regras, setOf("Checklist Review"), Trb1.statusMap, Trb1.calendario)

    private fun resumir(pares: List<ParCR>, semReview: Int = 0, r: RegrasChecklistReview = regras) =
        EstatisticasChecklistReview.resumir(pares, semReview, r, Trb1.calendario, Trb1.statusMap)

    private fun cr1() = motor.pares(Cr1.issues, Cr1.vinculos, Cr1.agora)

    /** Par sintético: só importa o que a estatística lê (labels, reabertura, horas ativas e alertas). */
    private fun par(
        n: Int, labelsPai: List<String> = emptyList(), labelsReview: List<String> = emptyList(), reaberto: Boolean = false,
        horas: Double = 5.0, alertas: Set<AlertaDoPar> = emptySet(), reviewResolvida: Boolean = true,
    ): ParCR {
        val pai = snap("TG-${2000 + n}", "Checklist", "ana", labels = labelsPai)
        val review = snap(
            "TG-${3000 + n}", "Checklist Review", "bruno", status = if (reviewResolvida) "Closed" else "Open",
            resolution = if (reviewResolvida) "Done" else null, resolvida = if (reviewResolvida) "2026-10-05T12:00:00Z" else null,
            labels = labelsReview,
        )
        return ParCR(pai, review, alertas, horas, reaberto)
    }

    @Test
    fun percentuaisDoCenario() {
        val r = resumir(cr1(), semReview = 1)
        assertEquals(4, r.pares)
        assertEquals(1, r.semReview)
        assertEquals(0.25, r.venceDepoisPct)
        assertEquals(0.25, r.mesmaPessoaPct)
        assertEquals(0.75, r.comAlertaPct)
    }

    @Test
    fun semParesNaoDivideNemQuebra() {
        val r = resumir(emptyList(), semReview = 3)
        assertEquals(0, r.pares)
        assertEquals(3, r.semReview)
        assertNull(r.venceDepoisPct)
        assertNull(r.mesmaPessoaPct)
        assertNull(r.comAlertaPct)
        assertNull(r.cicloReviewP50)
        assertNull(r.razaoReviewPaiP50)
        assertNull(r.criadaAposPaiHorasP50)
        assertEquals(emptyList(), r.labels)
        assertEquals(emptyList(), r.aprendizados)
        assertEquals(listOf(0, 0, 0, 0), r.duracaoXReabertura.map { it.n })
    }

    @Test
    fun cicloReviewP50ERazaoEAtrasoDeCriacao() {
        // Reviews concluídas de 1, 2 e 3 dias úteis; pais de 2, 4 e 6 (razão 0,5 em todos); review criada 2, 4 e 6 h depois do pai.
        fun par(n: Int, reviewDe: String, reviewAte: String, paiDe: String, paiAte: String, horasDepois: Long): ParCR {
            val pai = snap(
                "TG-${4000 + n}", "Checklist", "ana", "2026-10-01T00:00:00Z", "Closed", resolution = "Done", resolvida = paiAte,
                eventos = trilha("TG-${4000 + n}", "Open", paiDe to "In Progress", paiAte to "Closed"),
            )
            val criadaReview = java.time.Instant.parse("2026-10-01T00:00:00Z").plusSeconds(horasDepois * 3600).toString()
            val review = snap(
                "TG-${5000 + n}", "Checklist Review", "bruno", criadaReview, "Closed", resolution = "Done", resolvida = reviewAte,
                eventos = trilha("TG-${5000 + n}", "Open", reviewDe to "In Progress", reviewAte to "Closed"),
            )
            return ParCR(pai, review, emptySet(), 0.0, false)
        }
        val pares = listOf(
            par(1, "2026-10-05T04:00:00Z", "2026-10-06T04:00:00Z", "2026-10-05T04:00:00Z", "2026-10-07T04:00:00Z", 2),
            par(2, "2026-10-06T04:00:00Z", "2026-10-08T04:00:00Z", "2026-10-13T04:00:00Z", "2026-10-19T04:00:00Z", 4),
            par(3, "2026-10-13T04:00:00Z", "2026-10-16T04:00:00Z", "2026-10-19T04:00:00Z", "2026-10-27T04:00:00Z", 6),
        )
        val r = resumir(pares)
        assertEquals(2.0, r.cicloReviewP50)
        assertEquals(0.5, r.razaoReviewPaiP50)
        assertEquals(4.0, r.criadaAposPaiHorasP50)
    }

    @Test
    fun liftComAmostraPequena() {
        // A label "rara" aparece em 2 de 6 pais: o lift sai, mas marcado como "n baixo".
        val pares = (1..6).map { n -> par(n, labelsPai = if (n <= 2) listOf("rara") else emptyList(), reaberto = n == 1) }
        val rara = resumir(pares).labels.single { it.label == "rara" }
        assertEquals(2, rara.n)
        assertTrue(rara.nBaixo)
        assertEquals(0.5, rara.taxaReabertura)
        assertNotNull(rara.lift, "o lift sai mesmo com n baixo")
    }

    @Test
    fun lift() {
        // 10 pais; a label "ui" está em 5 deles e os 2 reabertos são desses 5: taxa 0,4 contra média 0,2.
        val pares = (1..10).map { n -> par(n, labelsPai = if (n <= 5) listOf("ui") else emptyList(), reaberto = n <= 2) }
        val ui = resumir(pares).labels.single { it.label == "ui" }
        assertEquals(5, ui.n)
        assertEquals(0.4, ui.taxaReabertura, 1e-9)
        assertEquals(2.0, ui.lift)
        assertEquals(false, ui.nBaixo)
    }

    @Test
    fun liftSemReaberturaNaBaseEIndefinido() {
        val pares = (1..6).map { n -> par(n, labelsPai = listOf("ui")) }
        assertNull(resumir(pares).labels.single().lift, "média zero: não há razão a calcular")
    }

    @Test
    fun labelsUniaoDoPaiEDaReviewSemARevisaoTecnica() {
        // A label aparece uma vez por par, mesmo nas duas pontas; "review" é rótulo técnico e não entra.
        val pares = (1..5).map { n -> par(n, labelsPai = listOf("ui", "Review"), labelsReview = listOf("ui", "review")) }
        val labels = resumir(pares).labels
        assertEquals(listOf("ui"), labels.map { it.label })
        assertEquals(5, labels.single().n)
    }

    @Test
    fun diferencasDeLabels() {
        val r = resumir(cr1())
        // P1: "android" nas duas pontas (não é diferença); P2 só no pai: android; P3 e P4 só no pai: ui.
        assertEquals(listOf("pai:ui" to 2, "pai:android" to 1), r.diferencasDeLabels)
    }

    @Test
    fun duracaoXReabertura() {
        val faixas = resumir(cr1()).duracaoXReabertura
        assertEquals(listOf("<1h", "1-4h", "4-12h", ">=12h"), faixas.map { it.faixa })
        // P3: review de 0,25 h com pai reaberto entra em "<1h"; P4 (review aberta) fica fora de todas.
        assertEquals(listOf(1, 0, 1, 1), faixas.map { it.n })
        assertEquals(1.0, faixas[0].taxaReabertura)
        assertNull(faixas[1].taxaReabertura)
        assertEquals(0.0, faixas[2].taxaReabertura)
    }

    @Test
    fun faixasSeguemOsCortesDoYaml() {
        val r = regras.copy(faixasDuracaoHoras = listOf(0.5, 2.5))
        assertEquals(listOf("<0.5h", "0.5-2.5h", ">=2.5h"), resumir(cr1(), r = r).duracaoXReabertura.map { it.faixa })
    }

    @Test
    fun aprendizadoSoComAmostra() {
        // Quatro pares (CR-1) ficam abaixo da amostra mínima (5): nenhuma frase, mesmo com alertas.
        assertEquals(emptyList(), resumir(cr1()).aprendizados)
        // Seis reviews curtas (pai reaberto) e seis longas (nunca reaberto): a frase sai, com os dois percentuais.
        val curtas = (1..6).map { par(it, reaberto = true, horas = 0.2) }
        val longas = (11..16).map { par(it, reaberto = false, horas = 20.0) }
        val ap = resumir(curtas + longas).aprendizados
        assertTrue(ap.any { it.startsWith("REVIEW_CURTA_REABRE_MAIS") }, ap.toString())
        val frase = ap.first { it.startsWith("REVIEW_CURTA_REABRE_MAIS") }
        assertTrue("curta=100" in frase && "longa=0" in frase, frase)
        // Com 4 curtas (< 5) a frase some.
        assertTrue(resumir(curtas.take(4) + longas).aprendizados.none { it.startsWith("REVIEW_CURTA_REABRE_MAIS") })
    }

    @Test
    fun aprendizadoDeLabelPrecisaDeAmostraELift() {
        val pares = (1..10).map { n -> par(n, labelsPai = if (n <= 5) listOf("ui") else emptyList(), reaberto = n <= 2) }
        val ap = resumir(pares).aprendizados
        assertTrue(ap.any { it.startsWith("LABEL_REABRE_MAIS") && "label=ui" in it && "lift=2.0" in it }, ap.toString())
        // Label em 4 pais (n baixo) não vira frase.
        val poucos = (1..10).map { n -> par(n, labelsPai = if (n <= 4) listOf("ui") else emptyList(), reaberto = n <= 2) }
        assertTrue(resumir(poucos).aprendizados.none { it.startsWith("LABEL_REABRE_MAIS") })
    }

    @Test
    fun aprendizadosDescritivosPedemAmostraDePares() {
        val mesma = (1..5).map { par(it, alertas = setOf(AlertaDoPar.MESMA_PESSOA)) }
        assertTrue(resumir(mesma).aprendizados.any { it.startsWith("MESMA_PESSOA_SEM_REVISAO_INDEPENDENTE") })
        assertTrue(resumir(mesma.take(4)).aprendizados.isEmpty())
    }

    @Test
    fun atrasosEmCadeiaSemPessoas() {
        val r = resumir(cr1())
        // P2: a review terminou em 16/10, depois do vencimento do pai (15/10). P4 tem review aberta: fica fora.
        assertEquals(1, r.atrasosEmCadeia)
        // "mesmaPessoaPct" é uma fração de pares, não identifica ninguém; nenhum outro campo fala de pessoa.
        val campos = ResumoCR::class.memberProperties.map { it.name.lowercase() }.filterNot { it == "mesmapessoapct" }
        assertTrue(campos.none { "pessoa" in it || "responsavel" in it || "autor" in it }, campos.toString())
    }
}
