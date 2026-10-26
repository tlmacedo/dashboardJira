package br.sidia.painel.auditoria

/**
 * Sinais do campo `duedate`. Responsável de referência = responsável no instante do evento.
 * Definir o vencimento pela primeira vez (`from` nulo) nunca gera sinal.
 */
object DetectoresDeVencimento {

    /** `from` e `to` legíveis e diferentes, por autor não autorizado. */
    private val alterado = Detector { t, ctx ->
        t.trocasDeVencimento.mapNotNull { e ->
            val de = Vencimentos.ler(e.from) ?: return@mapNotNull null
            val para = Vencimentos.ler(e.to) ?: return@mapNotNull null
            if (de == para) return@mapNotNull null
            val resp = t.responsavelEm(e.at)
            val avaliacao = ctx.autorizacao.avaliar(e.authorRef, t.issue.reporter, resp, e.at)
            if (avaliacao.autorizado) return@mapNotNull null
            ctx.sinal(CodigoSinal.VENCIMENTO_ALTERADO_NAO_AUTORIZADO, t, e, resp, de = de.toString(), para = para.toString())
        }
    }

    /** `from` preenchido e `to` vazio, por qualquer autor (inclusive coordenação). */
    private val removido = Detector { t, ctx ->
        t.trocasDeVencimento.mapNotNull { e ->
            if (e.from.isNullOrBlank() || !e.to.isNullOrBlank()) return@mapNotNull null
            val de = Vencimentos.ler(e.from)?.toString() ?: e.from
            ctx.sinal(CodigoSinal.VENCIMENTO_REMOVIDO, t, e, t.responsavelEm(e.at), de = de, para = null)
        }
    }

    val todos: List<Detector> = listOf(alterado, removido)
}
