package br.sidia.painel.checklist

import br.sidia.painel.domain.Percentiles
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.domain.TimeUnitMode
import br.sidia.painel.time.BusinessCalendar
import br.sidia.painel.trabalho.Fluxo
import java.time.Duration
import java.util.Locale
import kotlin.math.roundToInt

/** Reabertura do pai entre os pares que têm [label]. [lift] é a razão contra a taxa média (nulo se a média é zero). [nBaixo]: `n` abaixo da amostra mínima. */
data class LiftDeLabel(val label: String, val n: Int, val taxaReabertura: Double, val lift: Double?, val nBaixo: Boolean)

/** Uma faixa de duração ativa da review (`<1h`, `1-4h`, `4-12h`, `>=12h`) e a taxa de reabertura do pai nela. */
data class FaixaDuracao(val faixa: String, val n: Int, val taxaReabertura: Double?)

/**
 * Resumo do Checklist × Review. Percentuais são FRAÇÕES (0,25 = 25%) sobre o número de pares. Não há campo de pessoa:
 * nada aqui compara colegas. [atrasosEmCadeia] é a contagem de pares em que a review terminou depois do vencimento do
 * pai (o que era o "quem atrasa quem" do app anterior, sem nomes). [aprendizados] são códigos de frase com os
 * parâmetros (`CODIGO|chave=valor|...`), só quando a amostra permite; o texto é do frontend.
 */
data class ResumoCR(
    val pares: Int,
    val semReview: Int,
    val cicloReviewP50: Double?,
    val razaoReviewPaiP50: Double?,
    val criadaAposPaiHorasP50: Double?,
    val venceDepoisPct: Double?,
    val mesmaPessoaPct: Double?,
    val comAlertaPct: Double?,
    val duracaoXReabertura: List<FaixaDuracao>,
    val labels: List<LiftDeLabel>,
    val diferencasDeLabels: List<Pair<String, Int>>,
    val atrasosEmCadeia: Int,
    val aprendizados: List<String>,
)

/** Estatísticas do Checklist × Review: funções puras sobre os pares já montados. */
object EstatisticasChecklistReview {
    /** A label "review" só marca o tipo da issue; não diz nada sobre o resultado do checklist (rótulo técnico do app anterior). */
    private const val LABEL_TECNICA = "review"

    /** Lift mínimo para uma label virar frase de aprendizado (valor do app anterior). */
    private const val LIFT_DO_APRENDIZADO = 1.5

    private const val MAXIMO_DE_LABELS = 50
    private const val MAXIMO_DE_DIFERENCAS = 14
    private const val MAXIMO_DE_LABELS_NO_APRENDIZADO = 3

    fun resumir(
        pares: List<ParCR>, semReview: Int, regras: RegrasChecklistReview, cal: BusinessCalendar, map: StatusMap,
        unit: TimeUnitMode = TimeUnitMode.UTIL,
    ): ResumoCR {
        val n = pares.size
        fun pct(k: Int): Double? = if (n == 0) null else k.toDouble() / n
        fun com(a: AlertaDoPar) = pares.count { a in it.alertas }

        val concluidas = pares.filter { reviewConcluida(it, map) }
        val ciclos = concluidas.mapNotNull { Fluxo.cicloDias(it.review, map, cal, unit) }
        val razoes = concluidas.mapNotNull { p ->
            val review = Fluxo.cicloDias(p.review, map, cal, unit) ?: return@mapNotNull null
            val pai = Fluxo.cicloDias(p.pai, map, cal, unit)?.takeIf { it > 0.0 } ?: return@mapNotNull null
            review / pai
        }
        val criadaApos = pares.map { Duration.between(it.pai.created, it.review.created).toMillis() / MILIS_POR_HORA }

        val faixas = duracaoXReabertura(concluidas, regras)
        val labels = lifts(pares, regras)
        val emCadeia = pares.count { emCadeia(it, cal) }
        return ResumoCR(
            pares = n, semReview = semReview,
            cicloReviewP50 = Percentiles.median(ciclos), razaoReviewPaiP50 = Percentiles.median(razoes),
            criadaAposPaiHorasP50 = Percentiles.median(criadaApos),
            venceDepoisPct = pct(com(AlertaDoPar.REVIEW_VENCE_DEPOIS)), mesmaPessoaPct = pct(com(AlertaDoPar.MESMA_PESSOA)),
            comAlertaPct = pct(pares.count { it.alertas.isNotEmpty() }),
            duracaoXReabertura = faixas, labels = labels, diferencasDeLabels = diferencas(pares), atrasosEmCadeia = emCadeia,
            aprendizados = aprendizados(pares, faixas, labels, regras),
        )
    }

    private const val MILIS_POR_HORA = 3_600_000.0

    // ---- Predicados compartilhados com o drill: o número do cartão e as tasks da gaveta usam exatamente os mesmos ----

    /** A review foi concluída de fato (categoria concluída, resolução positiva e data): só ela entra em ciclo e em faixa. */
    internal fun reviewConcluida(p: ParCR, map: StatusMap): Boolean = Fluxo.concluidaPositiva(p.review, map)

    /** A review terminou (dia de Manaus) depois do vencimento do pai: o "atraso em cadeia" (sem nomes). */
    internal fun emCadeia(p: ParCR, cal: BusinessCalendar): Boolean {
        val vencePai = p.pai.duedate
        val fim = p.review.resolved
        return vencePai != null && fim != null && cal.localDate(fim).isAfter(vencePai)
    }

    /** Índice da faixa de duração da review: quantos cortes as horas ativas alcançaram (limite inferior inclusivo). */
    internal fun indiceDaFaixa(p: ParCR, regras: RegrasChecklistReview): Int =
        regras.faixasDuracaoHoras.count { (p.horasAtivasReview ?: 0.0) >= it }

    /** Reviews concluídas por faixa de horas ativas (limite inferior inclusivo, superior exclusivo) e a taxa de pai reaberto. */
    private fun duracaoXReabertura(concluidas: List<ParCR>, regras: RegrasChecklistReview): List<FaixaDuracao> {
        val cortes = regras.faixasDuracaoHoras
        val nomes = nomesDasFaixas(cortes)
        val porFaixa = concluidas.groupBy { p -> indiceDaFaixa(p, regras) }
        return nomes.mapIndexed { i, nome ->
            val dela = porFaixa[i].orEmpty()
            FaixaDuracao(nome, dela.size, if (dela.isEmpty()) null else dela.count { it.paiReaberto }.toDouble() / dela.size)
        }
    }

    /** `[1, 4, 12]` vira `<1h`, `1-4h`, `4-12h` e `>=12h`. */
    internal fun nomesDasFaixas(cortes: List<Double>): List<String> {
        fun h(v: Double) = if (v == Math.floor(v) && v < 1e9) v.toLong().toString() else v.toString()
        return buildList {
            add("<${h(cortes.first())}h")
            cortes.zipWithNext().forEach { (a, b) -> add("${h(a)}-${h(b)}h") }
            add(">=${h(cortes.last())}h")
        }
    }

    /** As labels do par (união das duas pontas, uma vez por par), sem o rótulo técnico `review`. */
    internal fun labelsDoPar(p: ParCR): Set<String> =
        (p.pai.labels + p.review.labels).filterNot { it.equals(LABEL_TECNICA, ignoreCase = true) }.toSet()

    /** As diferenças de labels do par: `pai:<label>` (só no pai) e `review:<label>` (só na review). */
    internal fun diferencasDoPar(p: ParCR): Set<String> {
        val noPai = p.pai.labels.filterNot { it.equals(LABEL_TECNICA, ignoreCase = true) }.toSet()
        val naReview = p.review.labels.filterNot { it.equals(LABEL_TECNICA, ignoreCase = true) }.toSet()
        return (noPai - naReview).map { "pai:$it" }.toSet() + (naReview - noPai).map { "review:$it" }.toSet()
    }

    /** Reabertura do pai por label (união das duas pontas, uma vez por par) e o lift contra a taxa média de todos os pares. */
    private fun lifts(pares: List<ParCR>, regras: RegrasChecklistReview): List<LiftDeLabel> {
        if (pares.isEmpty()) return emptyList()
        val media = pares.count { it.paiReaberto }.toDouble() / pares.size
        val porLabel = HashMap<String, MutableList<ParCR>>()
        pares.forEach { p -> labelsDoPar(p).forEach { porLabel.getOrPut(it) { mutableListOf() } += p } }
        return porLabel.map { (label, dela) ->
            val taxa = dela.count { it.paiReaberto }.toDouble() / dela.size
            LiftDeLabel(label, dela.size, taxa, if (media > 0.0) taxa / media else null, dela.size < regras.amostraMinima)
        }.sortedWith(compareByDescending<LiftDeLabel> { it.lift ?: -1.0 }.thenByDescending { it.n }.thenBy { it.label })
            .take(MAXIMO_DE_LABELS)
    }

    /** Labels só no pai (`pai:<label>`) ou só na review (`review:<label>`), com o número de pares; mais comuns primeiro. */
    private fun diferencas(pares: List<ParCR>): List<Pair<String, Int>> {
        val contagem = HashMap<String, Int>()
        for (p in pares) diferencasDoPar(p).forEach { contagem.merge(it, 1, Int::plus) }
        return contagem.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(MAXIMO_DE_DIFERENCAS).map { it.key to it.value }
    }

    private fun porCento(v: Double) = (v * 100).roundToInt()

    /** Remove o que quebraria o formato `CODIGO|chave=valor` (o texto sai do frontend, nunca daqui). */
    private fun parametro(v: String) = v.replace('|', ' ').replace('=', ' ')

    private fun aprendizados(pares: List<ParCR>, faixas: List<FaixaDuracao>, labels: List<LiftDeLabel>, regras: RegrasChecklistReview): List<String> {
        val min = regras.amostraMinima
        val saida = mutableListOf<String>()
        val curta = faixas.first()
        val longa = faixas.last()
        val taxaCurta = curta.taxaReabertura
        val taxaLonga = longa.taxaReabertura
        if (curta.n >= min && longa.n >= min && taxaCurta != null && taxaLonga != null && taxaCurta > taxaLonga) {
            saida += "REVIEW_CURTA_REABRE_MAIS|curta=${porCento(taxaCurta)}|longa=${porCento(taxaLonga)}"
        }
        if (pares.size >= min) {
            fun descritivo(codigo: String, alerta: AlertaDoPar) {
                val k = pares.count { alerta in it.alertas }
                if (k > 0) saida += "$codigo|n=$k|pct=${porCento(k.toDouble() / pares.size)}"
            }
            descritivo("MESMA_PESSOA_SEM_REVISAO_INDEPENDENTE", AlertaDoPar.MESMA_PESSOA)
            descritivo("REVIEW_VENCE_DEPOIS_DO_PAI", AlertaDoPar.REVIEW_VENCE_DEPOIS)
            descritivo("PAI_FECHADO_ANTES_DA_REVIEW", AlertaDoPar.PAI_FECHADO_ANTES)
        }
        labels.filter { !it.nBaixo && (it.lift ?: 0.0) >= LIFT_DO_APRENDIZADO }.take(MAXIMO_DE_LABELS_NO_APRENDIZADO).forEach {
            saida += "LABEL_REABRE_MAIS|label=${parametro(it.label)}|lift=${String.format(Locale.ROOT, "%.1f", it.lift ?: 0.0)}|n=${it.n}"
        }
        return saida
    }
}
