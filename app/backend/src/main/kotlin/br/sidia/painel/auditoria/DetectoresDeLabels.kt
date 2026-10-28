package br.sidia.painel.auditoria

/**
 * Sinais do campo `labels`: só as labels ADICIONADAS que justificam atraso geram sinal (removidas não geram).
 * Um evento pode gerar mais de um código, mas nunca dois sinais do mesmo código para o mesmo evento e a mesma label.
 * Em `Sinal.para` vai a label adicionada (um sinal por label); sem vencimento vigente só valem as regras de fechamento.
 */
object DetectoresDeLabels {

    private val labelsDeAtraso = Detector { t, ctx ->
        val sinais = mutableListOf<Sinal>()
        for ((e, mudanca) in t.mudancasDeLabels) {
            val responsavel = t.responsavelEm(e.at)
            val avaliacao = ctx.autorizacao.avaliar(e.authorRef, t.issue.reporter, responsavel, e.at)
            val venc = t.vencimentoEm(e.at)
            // Ordem estável da saída: as labels do evento em ordem alfabética.
            for (label in mudanca.adicionadas.filter { ctx.labels.justifica(it) }.sorted()) {
                fun emitir(codigo: CodigoSinal, resultado: Resultado = Resultado.AUDITORIA) {
                    ctx.sinal(codigo, t, e, responsavel, resultado, de = null, para = label)?.let(sinais::add)
                }

                // 1. Depois de fechada: o próprio responsável (auditoria; alerta se ele era autorizado) ou outro não autorizado.
                if (t.fechadaEm(e.at)) {
                    val ehOResponsavel = !e.authorRef.isNullOrBlank() && e.authorRef == responsavel
                    when {
                        ehOResponsavel -> emitir(
                            CodigoSinal.LABEL_PELO_RESPONSAVEL_APOS_FECHAMENTO,
                            if (avaliacao.autorizado) Resultado.ALERTA else Resultado.AUDITORIA,
                        )
                        !avaliacao.autorizado -> emitir(CodigoSinal.LABEL_APOS_FECHAMENTO_NAO_AUTORIZADO)
                    }
                }
                if (venc == null) continue
                // 2. Depois do dia do vencimento (dia civil de Manaus), por qualquer autor.
                if (ctx.dia(e.at).isAfter(venc)) emitir(CodigoSinal.LABEL_APOS_VENCIMENTO)
                // 3. Label de entrega tardia com o responsável de posse desde o limite antes do vencimento, ou antes.
                if (ctx.labels.ehTardia(label)) {
                    val posse = t.posseDesde(e.at)
                    val limite = ctx.diaAntes(venc, ctx.regras.posseAntesDoVencimentoDias).atStartOfDay(ctx.calendario.zone).toInstant()
                    if (posse != null && !posse.isAfter(limite)) emitir(CodigoSinal.LABEL_TARDIA_COM_POSSE_ANTECIPADA)
                }
            }
        }
        sinais
    }

    val todos: List<Detector> = listOf(labelsDeAtraso)
}
