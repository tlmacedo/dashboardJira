package br.sidia.painel.integridade

import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.IssueSnapshot
import java.time.Instant

/** O fechamento de uma task: o instante e o AUTOR da transição para um status concluído (nulo se o changelog não o trouxe). */
internal data class Fechamento(val at: Instant, val autor: String?)

/** Apoio comum dos detectores: o fechamento, a pessoa do sinal e a montagem da evidência. */
internal object Fechamentos {
    /**
     * O último fechamento da task, ou nulo se ela não está concluída de verdade (categoria concluída, resolução positiva e data).
     * Sem transição de status para concluído no changelog, vale a data de resolução e o autor fica nulo.
     */
    fun de(s: IssueSnapshot, map: StatusMap): Fechamento? {
        if (!Fluxo.concluidaPositiva(s, map)) return null
        val t = s.timeline.transitions.filter { map.category(it.to) == StatusCategory.DONE }.maxByOrNull { it.at }
        return if (t != null) Fechamento(t.at, t.authorRef) else s.resolved?.let { Fechamento(it, null) }
    }

    /** A pessoa do sinal (decisão D3): o autor do evento que o dispara; sem autor, o responsável no instante. */
    fun pessoa(autor: String?, s: IssueSnapshot, at: Instant): String? = autor?.takeIf { it.isNotBlank() } ?: Fluxo.responsavelEm(s, at)

    /** Autor do evento, se houver (sem espaços vazios). */
    fun autor(autor: String?): String? = autor?.takeIf { it.isNotBlank() }
}

/**
 * Monta a evidência de [sinal] na task [s] no instante [at], ou nulo se o sinal está desligado. A gravidade vem das regras e o
 * responsável no instante entra sempre no detalhe (`responsavel`), para a tela mostrar de quem era a task.
 */
internal fun RegrasDeIntegridade.evidencia(
    sinal: SinalIntegridade, s: IssueSnapshot, at: Instant, pessoa: String?, detalhe: Map<String, String> = emptyMap(),
): EvidenciaIntegridade? {
    if (!ativo(sinal)) return null
    val responsavel = Fluxo.responsavelEm(s, at)
    return EvidenciaIntegridade(sinal, gravidade(sinal), s.key, pessoa, at, if (responsavel == null) detalhe else detalhe + ("responsavel" to responsavel))
}
