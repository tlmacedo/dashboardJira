package br.sidia.painel.pessoas

import br.sidia.painel.cards.riscos.PrevisaoDeTermino
import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.IssueSnapshot
import java.time.LocalDate

/**
 * Quando a issue de [pessoa] termina se a pessoa só consumir a capacidade líquida dela. [termino] nulo = não cabe no
 * horizonte da agenda. [atrasa]: o término cai depois do [vencimento] (ou não cabe antes dele). [horasPorDia] = o que
 * foi consumido em cada dia, em ordem cronológica.
 */
data class Agendamento(
    val key: String,
    val pessoa: String,
    val termino: LocalDate?,
    val vencimento: LocalDate?,
    val atrasa: Boolean,
    val horasPorDia: Map<LocalDate, Double>,
)

/**
 * Previsão de término pela CAPACIDADE (não pelo histórico): a fila de cada pessoa consome, dia útil a dia útil a partir
 * de hoje, a capacidade líquida do dia (ausência e feriado incluídos). A fila: as já iniciadas primeiro; depois prioridade
 * (Highest a Lowest; desconhecida ou ausente no fim), vencimento (sem vencimento no fim), criação e chave natural. Issue
 * vencida entra na fila (o atraso segue visível na previsão). Domínio puro: nada aqui grava.
 */
class Agenda(
    private val horas: HorasDaIssue,
    private val cap: Capacidade,
    private val map: StatusMap,
    private val horizonteDias: Int = 120,
) {
    /** Os agendamentos das issues abertas de [pessoa], na ordem da fila. */
    fun agendar(pessoa: String, abertas: List<IssueSnapshot>, hoje: LocalDate): List<Agendamento> =
        agendarCom(cap, pessoa, abertas, hoje)

    /** Cada issue agendada com a capacidade desta agenda e com [sem] (por exemplo, sem a ausência): `(atual, sem)`. */
    fun comparar(pessoa: String, abertas: List<IssueSnapshot>, hoje: LocalDate, sem: Capacidade): List<Pair<Agendamento, Agendamento>> {
        val atual = agendar(pessoa, abertas, hoje)
        val alternativa = agendarCom(sem, pessoa, abertas, hoje).associateBy { it.key }
        return atual.map { it to alternativa.getValue(it.key) }
    }

    /**
     * Os agendamentos de todas as pessoas que têm issue em [abertas], prontos para o Riscos e a Previsão. Issue sem
     * responsável não consome capacidade de ninguém: fica sem previsão.
     */
    fun agendarTodas(abertas: List<IssueSnapshot>, hoje: LocalDate): Map<String, Agendamento> =
        abertas.mapNotNull { it.assignee }.distinct().flatMap { agendar(it, abertas, hoje) }.associateBy { it.key }

    /** A previsão de término de cada issue (`null` quando não cabe no horizonte ou não tem responsável). */
    fun comoPrevisao(abertas: List<IssueSnapshot>, hoje: LocalDate): PrevisaoDeTermino {
        val porChave = agendarTodas(abertas, hoje)
        return PrevisaoDeTermino { chave -> porChave[chave]?.termino }
    }

    private fun agendarCom(capacidade: Capacidade, pessoa: String, abertas: List<IssueSnapshot>, hoje: LocalDate): List<Agendamento> {
        val fila = abertas.filter { it.assignee == pessoa && map.category(it.status) != StatusCategory.DONE }
            .sortedWith(
                compareBy<IssueSnapshot>({ if (Fluxo.inicioAtivo(it, map) != null) 0 else 1 }, { indiceDaPrioridade(it.priority) })
                    .thenBy { it.duedate ?: LocalDate.MAX }
                    .thenBy { it.created }
                    .thenBy(ORDEM_NATURAL_DE_CHAVES) { it.key },
            )
        val ultimoDia = hoje.plusDays(horizonteDias - 1L)
        val resultado = ArrayList<Agendamento>(fila.size)
        var dia = hoje
        var sobraDoDia = capacidade.dia(pessoa, dia)
        for (s in fila) {
            var falta = horas.de(s).valor
            val porDia = LinkedHashMap<LocalDate, Double>()
            var termino: LocalDate? = null
            while (dia <= ultimoDia) {
                if (sobraDoDia > EPSILON) {
                    val usa = minOf(sobraDoDia, falta)
                    porDia[dia] = (porDia[dia] ?: 0.0) + usa
                    sobraDoDia -= usa
                    falta -= usa
                }
                if (falta <= EPSILON) {
                    termino = dia
                    break
                }
                dia = dia.plusDays(1)
                sobraDoDia = capacidade.dia(pessoa, dia)
            }
            val vence = s.duedate
            val atrasa = vence != null && (if (termino == null) !vence.isAfter(ultimoDia) else termino.isAfter(vence))
            resultado += Agendamento(s.key, pessoa, termino, vence, atrasa, porDia)
            // Quem não coube no horizonte esgotou os dias: as próximas da fila também não cabem.
            if (termino == null) dia = ultimoDia.plusDays(1)
        }
        return resultado
    }

    private fun indiceDaPrioridade(prioridade: String?): Int {
        val i = ORDEM_DE_PRIORIDADE.indexOfFirst { it.equals(prioridade?.trim(), ignoreCase = true) }
        return if (i < 0) ORDEM_DE_PRIORIDADE.size else i
    }

    private companion object {
        const val EPSILON = 1e-9
        /** A mesma ordem de prioridade de Abertas e Metas (o Jira não manda a ordem). */
        val ORDEM_DE_PRIORIDADE = listOf("Highest", "High", "Medium", "Low", "Lowest")
    }
}
