package br.sidia.painel.auditoria

import java.util.Locale

/**
 * Sinal da estimativa original (campo `timeoriginalestimate`, valores em SEGUNDOS gravados pelo painel desde o Plano 11).
 *
 * `ESTIMATIVA_REDUZIDA`: a estimativa caiu pelo menos `estimativaReducaoMinima` (fração do valor anterior) DEPOIS de o trabalho
 * começar (primeira entrada em categoria ACTIVE). Antes disso é replanejamento e nunca gera sinal. Autor autorizado pela regra
 * 7.1 (Reporter, coordenação, gerente ou Squad Leader do responsável, vigentes no dia do evento) vira `ALERTA`; os demais, inclusive
 * o próprio responsável e o autor desconhecido, `AUDITORIA`. Valor ausente ou ilegível (evento anterior à nova carga) não gera
 * nada; quantos são fica no aviso `HISTORICO_DE_ESTIMATIVA_INCOMPLETO` ([eventosSemValor]). Aumento é da Integridade.
 * Indício, não prova.
 */
object DetectoresDeEstimativa {
    private const val EPS = 1e-9
    private const val SEGUNDOS_POR_HORA = 3600.0

    private fun segundos(valor: String?): Long? = valor?.trim()?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toLongOrNull()

    private fun horas(s: Long): String = String.format(Locale.ROOT, "%.1f", s / SEGUNDOS_POR_HORA)

    private val reduzida = Detector { t, ctx ->
        val inicio = t.inicioDoTrabalho() ?: return@Detector emptyList()
        t.trocasDeEstimativa.mapNotNull { e ->
            val antes = segundos(e.from) ?: return@mapNotNull null
            val depois = segundos(e.to) ?: return@mapNotNull null
            if (antes <= 0 || depois >= antes || e.at < inicio) return@mapNotNull null
            if ((antes - depois).toDouble() / antes < ctx.regras.estimativaReducaoMinima - EPS) return@mapNotNull null
            val resp = t.responsavelEm(e.at)
            val autorizado = ctx.autorizacao.avaliar(e.authorRef, t.issue.reporter, resp, e.at).autorizado
            ctx.sinal(
                CodigoSinal.ESTIMATIVA_REDUZIDA, t, e, resp,
                resultado = if (autorizado) Resultado.ALERTA else Resultado.AUDITORIA,
                de = horas(antes), para = horas(depois),
            )
        }
    }

    val todos: List<Detector> = listOf(reduzida)

    /** Eventos de estimativa original sem nenhum dos dois valores: gravados antes da carga que traz os segundos. */
    fun eventosSemValor(t: AuditTimeline): Int = t.trocasDeEstimativa.count { it.from == null && it.to == null }
}
