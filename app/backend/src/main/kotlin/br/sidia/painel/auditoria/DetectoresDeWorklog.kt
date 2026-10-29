package br.sidia.painel.auditoria

import br.sidia.painel.sync.ChangeEventRecord
import java.time.LocalDate

/**
 * Sinal `WORKLOG_RETROATIVO` (spec 7.2; Plano 11): horas registradas depois do início declarado, por um destes caminhos, sempre
 * como AUDITORIA (qualquer autor; o motivo da autorização é só informativo):
 *  1. os dias úteis de Manaus entre o dia de `started` e o dia de `criado` são pelo menos `worklogRetroativoDiasUteis`
 *     (o dia do início não conta, o do registro conta; sábado, domingo e feriado não contam); ou
 *  2. `criado` é depois de `resolved` da issue e `started` é antes de `resolved`.
 * Um sinal por worklog. Worklog sem `criado` (gravado antes da nova carga) não gera nada e entra na conta de [semCriacao], que vira
 * o aviso `HISTORICO_DE_WORKLOG_INCOMPLETO`. Indício, não prova: registrar horas depois é comum (sexta-feira, esquecimento).
 */
object DetectoresDeWorklog {

    /** Conta até [teto] dias úteis (a contagem para ali: datas distantes não custam mais). */
    private fun diasUteisEntre(ctx: ContextoDeteccao, inicio: LocalDate, fim: LocalDate, teto: Int): Int {
        var n = 0
        var d = inicio.plusDays(1)
        while (!d.isAfter(fim) && n < teto) {
            if (ctx.calendario.isBusinessDay(d)) n++
            d = d.plusDays(1)
        }
        return n
    }

    private val retroativo = Detector { t, ctx ->
        val resolvida = t.issue.resolved
        t.issue.worklogs.sortedWith(compareBy({ it.started }, { it.jiraId })).mapNotNull { w ->
            val criado = w.criado ?: return@mapNotNull null
            val diaInicio = ctx.dia(w.started)
            val diaCriacao = ctx.dia(criado)
            val porAtraso = diasUteisEntre(ctx, diaInicio, diaCriacao, ctx.regras.worklogRetroativoDiasUteis) >= ctx.regras.worklogRetroativoDiasUteis
            val aposAResolucao = resolvida != null && criado.isAfter(resolvida) && w.started.isBefore(resolvida)
            if (!porAtraso && !aposAResolucao) return@mapNotNull null
            // O worklog vira um "evento" só para reaproveitar a montagem do sinal e a avaliação de autorização.
            val evento = ChangeEventRecord("worklog:${w.jiraId}", t.issue.key, "worklog", diaInicio.toString(), diaCriacao.toString(), criado, w.autor)
            ctx.sinal(CodigoSinal.WORKLOG_RETROATIVO, t, evento, t.responsavelEm(criado))
        }
    }

    val todos: List<Detector> = listOf(retroativo)

    /** Worklogs da issue sem a data de criação (anteriores à nova carga): não geram sinal. */
    fun semCriacao(t: AuditTimeline): Int = t.issue.worklogs.count { it.criado == null }
}
