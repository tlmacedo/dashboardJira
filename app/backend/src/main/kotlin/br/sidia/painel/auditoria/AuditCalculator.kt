package br.sidia.painel.auditoria

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.domain.NoPrazo
import br.sidia.painel.domain.NoPrazoDetalhe
import br.sidia.painel.domain.NoPrazoResultado
import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.UnmappedStatusException
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/**
 * Tudo o que a auditoria calcula num período. [noPrazoDetalhe] leva as issues por trás de cada número de [noPrazo]
 * (a gaveta de tasks do cartão abre dali). Indício, não prova: nada aqui diz de quem é a culpa.
 */
data class ResultadoAuditoria(
    val versaoRegras: String,
    val sinais: List<Sinal>,
    val porCodigo: Map<CodigoSinal, Int>,
    val taskReview: ResumoTaskReview,
    val pares: List<ParTaskReview>,
    val noPrazo: NoPrazoResultado,
    val avisos: List<AvisoDto>,
    val noPrazoDetalhe: NoPrazoDetalhe,
)

/**
 * Junta os sinais dos detectores, Task × Review e "no prazo" de um período. Só entram sinais com `at` em `[de, ate)`;
 * a ordem é por data decrescente, depois pela ordem natural das chaves e pelo código (nunca por pessoa).
 * Issue com status fora do mapa é ignorada (com aviso), nunca derruba a tela.
 */
class AuditCalculator(
    private val ctx: ContextoDeteccao,
    private val detectores: List<Detector> = DetectoresDeVencimento.todos + DetectoresDeResponsavel.todos + DetectoresDeLabels.todos,
) {
    private val ordem = Comparator<Sinal> { a, b ->
        b.at.compareTo(a.at).takeIf { it != 0 }
            ?: ORDEM_NATURAL_DE_CHAVES.compare(a.issueKey, b.issueKey).takeIf { it != 0 }
            ?: a.codigo.compareTo(b.codigo)
    }

    @Suppress("LongParameterList")
    fun calcular(
        comEventos: List<AuditIssue>, concluidas: List<AuditIssue>, paresBase: ParesLidos,
        de: Instant, ate: Instant, diaDe: LocalDate, diaAte: LocalDate,
    ): ResultadoAuditoria {
        // Status fora do mapa: a issue sai de todas as contas e vira um aviso por status (só chaves, nunca em texto).
        val ignoradas = sortedMapOf<String, MutableSet<String>>()
        fun mapeada(i: AuditIssue): Boolean =
            try {
                ctx.statusMap.category(i.status)
                true
            } catch (_: UnmappedStatusException) {
                ignoradas.getOrPut(i.status) { mutableSetOf() } += i.key
                false
            }

        val issues = comEventos.filter(::mapeada)
        val feitas = concluidas.filter(::mapeada)
        val issuesDosPares = paresBase.issues.filter(::mapeada)

        val doPeriodo = { s: Sinal -> s.at >= de && s.at < ate }
        val deIssues = issues.map { AuditTimeline(it, ctx.statusMap) }.flatMap { t -> detectores.flatMap { it.detectar(t, ctx) } }
        val pares = TaskReview.pares(issuesDosPares, paresBase.vinculos)
        val dePares = pares.flatMap { TaskReview.sinais(it, ctx) }
        val lotes = tamanhoDosLotes(issues + issuesDosPares)
        val sinais = (deIssues + dePares).filter(doPeriodo).map { marcarLote(it, lotes) }.sortedWith(ordem)

        val porCodigo = CodigoSinal.entries.associateWith { c -> sinais.count { it.codigo == c } }
        val detalhe = NoPrazo.detalhar(feitas, ctx.labels, ctx.statusMap, ctx.calendario.zone, diaDe, diaAte, sinais)

        return ResultadoAuditoria(
            versaoRegras = "regras v${ctx.regras.versao}, labels v${ctx.labels.versao}",
            sinais = sinais,
            porCodigo = porCodigo,
            taskReview = TaskReview.resumo(pares),
            pares = pares,
            noPrazo = detalhe.resultado,
            avisos = avisos(issues, paresBase, ignoradas),
            noPrazoDetalhe = detalhe,
        )
    }

    // ---- Alteração em lote (I-15) ----

    /** Chave do lote: a mesma pessoa, o mesmo campo, o mesmo minuto (UTC). */
    private data class ChaveDeLote(val autor: String, val campo: String, val minuto: Long)

    private fun minutoDe(at: Instant) = Math.floorDiv(at.epochSecond, 60L)

    /** Quantas issues DISTINTAS cada lote tocou, contando todos os eventos das issues carregadas. */
    private fun tamanhoDosLotes(issues: List<AuditIssue>): Map<ChaveDeLote, Int> =
        issues.flatMap { i ->
            i.events.filter { !it.authorRef.isNullOrBlank() }
                .map { ChaveDeLote(it.authorRef!!, it.field.lowercase(Locale.ROOT), minutoDe(it.at)) to i.key }
        }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.toSet().size }

    /** Marca o sinal com o tamanho do lote quando ele alcança o mínimo das regras; indício, não muda mais nada. */
    private fun marcarLote(s: Sinal, lotes: Map<ChaveDeLote, Int>): Sinal {
        val autor = s.autor?.takeIf { it.isNotBlank() } ?: return s
        val n = lotes[ChaveDeLote(autor, s.campo.lowercase(Locale.ROOT), minutoDe(s.at))] ?: return s
        return if (n >= ctx.regras.emLoteMinimo) s.copy(emLote = n) else s
    }

    // ---- Avisos (todos informativos) ----

    private fun avisos(issues: List<AuditIssue>, pares: ParesLidos, ignoradas: Map<String, Set<String>>): List<AvisoDto> {
        val avisos = mutableListOf<AvisoDto>()
        fun informativo(codigo: String, mensagem: String, params: Map<String, String> = emptyMap()) {
            avisos += AvisoDto(codigo, AvisoDto.INFORMATIVO, mensagem, params)
        }
        val timelines = issues.map { AuditTimeline(it, ctx.statusMap) }
        val reporterAlterado = timelines.count { it.reporterAlterado }
        if (reporterAlterado > 0) {
            informativo(AvisoDto.REPORTER_ALTERADO, "$reporterAlterado issue(s) com troca de reporter: a autorização usa o reporter atual", mapOf("n" to reporterAlterado.toString()))
        }
        val ilegiveis = timelines.sumOf { it.vencimentosIlegiveis }
        if (ilegiveis > 0) {
            informativo(AvisoDto.VENCIMENTO_ILEGIVEL, "$ilegiveis valor(es) de vencimento ilegíveis no histórico", mapOf("n" to ilegiveis.toString()))
        }
        for ((status, chaves) in ignoradas) {
            informativo(
                AvisoDto.ISSUES_IGNORADAS_STATUS, "${chaves.size} issue(s) com status sem mapeamento ignoradas",
                mapOf("status" to status, "n" to chaves.size.toString()),
            )
        }
        if (pares.ambiguas > 0) {
            informativo(AvisoDto.VINCULO_AMBIGUO, "${pares.ambiguas} vínculo(s) Task × Review ambíguos: ficou o par de menor chave", mapOf("n" to pares.ambiguas.toString()))
        }
        if (pares.semVinculos) informativo(AvisoDto.VINCULOS_INDISPONIVEIS, "nenhum vínculo do tipo configurado na base")
        return avisos
    }
}
