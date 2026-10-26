package br.sidia.painel.auditoria

import br.sidia.painel.sync.ChangeEventRecord

/**
 * Sinais do campo `assignee` (troca por não autorizado, passa-bola, troca perto do vencimento) e do
 * cancelamento suspeito. Sem vencimento vigente, as regras de "perto" e "depois" não disparam.
 */
object DetectoresDeResponsavel {

    /** Responsável de referência de uma troca: o anterior, ou o novo quando a task estava sem responsável. */
    private fun referencia(e: ChangeEventRecord): String? = e.from?.takeIf { it.isNotBlank() } ?: e.to?.takeIf { it.isNotBlank() }

    /**
     * Trocas reais (de valor para outro valor diferente); texto só de espaços vale como vazio.
     * Empate de instante: as consultas "em at" usam `<` estrito (eventos do mesmo instante não se enxergam)
     * e a ordem entre eventos empatados é a de entrada (ordenação estável).
     */
    private fun trocas(t: AuditTimeline): List<ChangeEventRecord> =
        t.trocasDeResponsavel.filter { (it.from?.trim() ?: "") != (it.to?.trim() ?: "") }

    private fun autorizado(t: AuditTimeline, ctx: ContextoDeteccao, e: ChangeEventRecord): Boolean =
        ctx.autorizacao.avaliar(e.authorRef, t.issue.reporter, referencia(e), e.at).autorizado

    private val trocado = Detector { t, ctx ->
        trocas(t).filter { !autorizado(t, ctx, it) }.mapNotNull {
            ctx.sinal(CodigoSinal.RESPONSAVEL_TROCADO_NAO_AUTORIZADO, t, it, referencia(it))
        }
    }

    /** Um sinal por issue, na troca que completa a condição; vale para qualquer autor. */
    private val passaBola = Detector { t, ctx ->
        var pertoAteAqui = 0
        for (e in trocas(t)) {
            val venc = t.vencimentoEm(e.at) ?: continue
            if (!ctx.perto(ctx.dia(e.at), venc)) continue
            pertoAteAqui++
            val voltaAQuemJaTeve = !e.to.isNullOrBlank() && t.jaTeve(e.to, e.at)
            if (pertoAteAqui >= ctx.regras.passaBolaTrocas || voltaAQuemJaTeve) {
                return@Detector listOfNotNull(ctx.sinal(CodigoSinal.PASSA_BOLA, t, e, referencia(e)))
            }
        }
        emptyList()
    }

    private val trocaPerto = Detector { t, ctx ->
        trocas(t).mapNotNull { e ->
            val venc = t.vencimentoEm(e.at) ?: return@mapNotNull null
            if (!ctx.perto(ctx.dia(e.at), venc) || autorizado(t, ctx, e)) return@mapNotNull null
            ctx.sinal(CodigoSinal.TROCA_PERTO_DO_VENCIMENTO, t, e, referencia(e))
        }
    }

    /** Transição para status de cancelamento depois do vencimento, ou por não autorizado: um sinal por evento. */
    private val cancelamento = Detector { t, ctx ->
        val cancelamentos = ctx.regras.statusCancelamento.map { it.trim().lowercase() }.toSet()
        t.transicoes.mapNotNull { e ->
            if (e.to?.trim()?.lowercase() !in cancelamentos) return@mapNotNull null
            val resp = t.responsavelEm(e.at)
            val venc = t.vencimentoEm(e.at)
            val depois = venc != null && ctx.dia(e.at).isAfter(venc)
            val naoAutorizado = !ctx.autorizacao.avaliar(e.authorRef, t.issue.reporter, resp, e.at).autorizado
            if (!depois && !naoAutorizado) return@mapNotNull null
            ctx.sinal(CodigoSinal.CANCELAMENTO_SUSPEITO, t, e, resp)
        }
    }

    val todos: List<Detector> = listOf(trocado, passaBola, trocaPerto, cancelamento)
}
