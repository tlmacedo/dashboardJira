package br.sidia.painel.cards.competencias

import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.pessoas.MotorDePessoas
import br.sidia.painel.trabalho.IssueSnapshot
import java.util.Locale
import java.util.TreeMap

/** O DTO e as tasks por trás dos números: por competência pedida (`skill:<x>`) e as de `semNinguem`. */
class CompetenciasAnalise(val dto: CompetenciasDto, val porCompetencia: Map<String, List<IssueSnapshot>>, val semNinguem: List<IssueSnapshot>)

/**
 * Matriz de competências: quem tem o quê (de `equipes.yaml`) entre as pessoas da visão, quais competências dependem de uma
 * pessoa só, quais as tasks abertas pedem (label `skill:<x>`, sem diferenciar maiúsculas) sem ninguém da visão ter, e as horas
 * abertas por competência. Domínio puro; as [abertas] já vêm filtradas pela visão de quem pergunta.
 */
class CompetenciasCalculator(private val motor: MotorDePessoas) {
    fun calcular(visiveis: Set<String>, abertas: List<IssueSnapshot>): CompetenciasAnalise {
        val naoConcluidas = abertas.filter { motor.statusMap.category(it.status) != StatusCategory.DONE }
            .sortedWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.key })
        // Só quem consta na equipe cadastrada e está na visão (alfabética): quem não tem competências cadastradas não aparece.
        val pessoas = motor.pessoas().filter { it in visiveis }
        val dePessoa = pessoas.associateWith { motor.equipes.de(it)!!.competencias }

        val porCompetencia = TreeMap<String, MutableList<IssueSnapshot>>()
        for (s in naoConcluidas) for (c in pedidas(s)) porCompetencia.getOrPut(c) { mutableListOf() } += s

        val competencias = (dePessoa.values.flatten() + porCompetencia.keys).toSortedSet().toList()
        val celulas = competencias.map { c -> pessoas.map { p -> c in dePessoa.getValue(p) } }
        // Com menos de 2 pessoas na visão toda competência seria "única": o indicador só vale com 2 ou mais.
        val pessoaUnica = if (pessoas.size < 2) emptyList() else competencias.filter { c -> pessoas.count { c in dePessoa.getValue(it) } == 1 }
        val semNinguem = porCompetencia.keys.filter { c -> pessoas.none { c in dePessoa.getValue(it) } }
        val tasksSemNinguem = naoConcluidas.filter { s -> pedidas(s).any { it in semNinguem } }
        val demandaH = LinkedHashMap<String, Double>()
        for ((c, tasks) in porCompetencia) demandaH[c] = tasks.sumOf { motor.horas.de(it).valor }

        val dto = CompetenciasDto(
            competencias, pessoas, celulas, pessoaUnica, semNinguem, demandaH, emptyList(),
            tasksPorCompetencia = porCompetencia.mapValues { it.value.size }, tasksSemNinguem = tasksSemNinguem.size,
        )
        return CompetenciasAnalise(dto, porCompetencia, tasksSemNinguem)
    }

    /** As competências que a task pede: labels `skill:<x>` (minúsculas, sem repetição); `skill:` sem nada depois não vale. */
    private fun pedidas(s: IssueSnapshot): Set<String> =
        s.labels.map { it.trim().lowercase(Locale.ROOT) }.filter { it.startsWith(PREFIXO) && it.length > PREFIXO.length }
            .map { it.removePrefix(PREFIXO) }.toSet()

    private companion object {
        const val PREFIXO = "skill:"
    }
}
