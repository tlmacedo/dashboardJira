package br.sidia.painel.domain

import java.time.Instant

object TimelineBuilder {
    /**
     * Corta a vida da issue em intervalos por status e responsável. Varre os eventos ordenados por
     * (instante, tipo), com a troca de status antes da troca de responsável quando empatam.
     * O fim é `resolved` (ou [now] se aberta). Intervalos de duração zero (ex.: o status final
     * "Concluído", que começa quando a issue é resolvida) são descartados.
     * Status sem mapeamento lança [UnmappedStatusException].
     */
    fun intervals(t: IssueTimeline, now: Instant, map: StatusMap): List<StatusInterval> {
        val fim = t.resolved ?: now
        // Ordena já na entrada (não depende de quem chama): por instante, mantendo a ordem original da lista
        // nos empates (sortedBy é estável). Depois junta as duas listas; no mesmo instante o status vem antes.
        val transicoes = t.transitions.sortedBy { it.at }
        val trocas = t.assigneeChanges.sortedBy { it.at }
        // tipo 0 = status, 1 = responsável; sortedWith é estável.
        val eventos: List<Pair<Instant, Any>> =
            (transicoes.map { it.at to (it as Any) } + trocas.map { it.at to (it as Any) })
                .sortedWith(compareBy({ it.first }, { if (it.second is Transition) 0 else 1 }))

        val resultado = mutableListOf<StatusInterval>()
        var status = t.initialStatus
        var responsavel = t.initialAssignee
        var inicio = t.created

        fun fechar(ate: Instant) {
            val categoria = map.category(status) // valida todo status visitado
            val limite = if (ate > fim) fim else ate
            if (limite > inicio) resultado += StatusInterval(status, categoria, inicio, limite, responsavel)
            if (ate > inicio) inicio = ate
        }

        for ((quando, ev) in eventos) {
            if (quando >= fim) {
                // Evento no/ depois do fim: só valida o status de destino.
                if (ev is Transition) map.category(ev.to)
                continue
            }
            fechar(quando)
            when (ev) {
                is Transition -> status = ev.to
                is AssigneeChange -> responsavel = ev.to
            }
        }
        fechar(fim)
        return resultado
    }
}
