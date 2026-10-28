package br.sidia.painel.cards

import br.sidia.painel.domain.TimeUnitMode
import br.sidia.painel.sync.normalizarOsVersion
import br.sidia.painel.trabalho.IssueSnapshot
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Filtros comuns dos cards de trabalho e prazos (`squad`, `pessoa`, `tipo` e `prioridade` repetíveis, `os`, `de`, `ate`
 * e `unit`). [de] e [ate] são datas de Manaus, ambas INCLUSIVAS; ficam nulas quando o card não tem período.
 */
data class FiltrosDeCard(
    val squad: String?,
    val pessoa: String?,
    val tipos: List<String>,
    val prioridades: List<String>,
    val os: String?,
    val de: LocalDate?,
    val ate: LocalDate?,
    val unit: TimeUnitMode,
) {
    /** A issue passa nos filtros de tipo e de prioridade (sem diferenciar maiúsculas)? Lista vazia = sem filtro. */
    fun aceita(s: IssueSnapshot): Boolean =
        (tipos.isEmpty() || tipos.any { it.equals(s.issueType, ignoreCase = true) }) &&
            (prioridades.isEmpty() || prioridades.any { it.equals(s.priority, ignoreCase = true) })

    companion object {
        private const val MAX_VALOR = 100
        private const val MAX_ITENS = 50

        /**
         * Lê e valida os filtros; qualquer valor ruim é 400 sem corpo (nunca 500, nunca eco do valor).
         * Com [periodoPadraoDias], `ate` vale hoje e `de` vem [periodoPadraoDias] dias inclusivos antes dele quando não
         * informados; com `null` o card não tem período e as datas só existem se pedidas. O período pedido não passa
         * de [maxDias] dias (inclusivo) nem tem `de` depois de `ate`.
         */
        fun ler(params: Parameters, hoje: LocalDate, periodoPadraoDias: Int?, maxDias: Int = 366): FiltrosDeCard {
            val deInformado = data(params, "de")
            val ateInformado = data(params, "ate")
            val de: LocalDate?
            val ate: LocalDate?
            if (periodoPadraoDias != null) {
                ate = ateInformado ?: hoje
                de = deInformado ?: ate.minusDays(periodoPadraoDias - 1L)
            } else {
                de = deInformado
                ate = ateInformado
            }
            if (de != null && ate != null && (de.isAfter(ate) || ChronoUnit.DAYS.between(de, ate) + 1 > maxDias)) {
                throw ErroHttp(HttpStatusCode.BadRequest)
            }
            val unit = when (texto(params, "unit")?.lowercase(Locale.ROOT)) {
                null, "util" -> TimeUnitMode.UTIL
                "corrido" -> TimeUnitMode.CORRIDO
                else -> throw ErroHttp(HttpStatusCode.BadRequest)
            }
            return FiltrosDeCard(
                squad = texto(params, "squad"),
                pessoa = texto(params, "pessoa")?.let(::usernameValido),
                tipos = lista(params, "tipo"),
                prioridades = lista(params, "prioridade"),
                os = texto(params, "os")?.let(::normalizarOsVersion)?.ifEmpty { null },
                de = de,
                ate = ate,
                unit = unit,
            )
        }

        /** Parâmetro repetível: valores sem espaço nas pontas, sem repetição, no máximo [MAX_ITENS]. */
        private fun lista(params: Parameters, nome: String): List<String> {
            val valores = params.getAll(nome).orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
            if (valores.size > MAX_ITENS) throw ErroHttp(HttpStatusCode.BadRequest)
            valores.forEach { if (it.length > MAX_VALOR || it.any(Char::isISOControl)) throw ErroHttp(HttpStatusCode.BadRequest) }
            return valores.distinct()
        }
    }
}
