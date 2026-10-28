package br.sidia.painel.cards.plano

import br.sidia.painel.cards.avisosDePessoas
import br.sidia.painel.cards.paraDto
import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.pessoas.MotorDePessoas
import br.sidia.painel.trabalho.IssueSnapshot
import java.time.LocalDate

/** O DTO e as tasks de cada célula (`celula:<pessoa>:<segunda>`). */
class PlanoAnalise(val dto: PlanoDto, val segmentos: Map<String, List<IssueSnapshot>>)

/**
 * Plano de 4 a 8 semanas: a demanda de cada semana (a mesma `Demanda` da Carga, espalhada até o vencimento) contra a
 * capacidade líquida, por pessoa. Capacidade zero é "sem capacidade", mostrando as horas planejadas ao lado. Domínio puro.
 */
class PlanoCalculator(private val motor: MotorDePessoas) {
    fun calcular(pessoas: List<String>, abertas: List<IssueSnapshot>, hoje: LocalDate, semanas: Int): PlanoAnalise {
        val naoConcluidas = abertas.filter { motor.statusMap.category(it.status) != StatusCategory.DONE }
        val segmentos = LinkedHashMap<String, List<IssueSnapshot>>()
        var algumaHoraAssumida = false
        val linhas = pessoas.map { p ->
            val carga = motor.demanda.carga(p, naoConcluidas, hoje, semanas)
            algumaHoraAssumida = algumaHoraAssumida || carga.horasAssumidas
            val minhas = naoConcluidas.filter { it.assignee == p }
            val duracoes = minhas.associateWith { motor.demanda.semanasQueOcupa(it, hoje) }
            LinhaPlanoDto(
                p,
                carga.semanas.mapIndexed { i, s ->
                    segmentos["celula:$p:${s.semana}"] = minhas.filter { (duracoes[it] ?: 0) > i }
                        .sortedWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.key })
                    CelulaPlanoDto(s.semana.toString(), s.demandaH, s.capacidadeH, s.ocupacao.paraDto(motor.regras, s.demandaH), s.itens, s.semDataH)
                },
            )
        }
        val semanasIso = linhas.firstOrNull()?.celulas?.map { it.semana }
            ?: motor.capacidade.semanas("", hoje, semanas).map { it.first.toString() }
        return PlanoAnalise(PlanoDto(semanasIso, linhas, avisosDePessoas(motor, naoConcluidas, algumaHoraAssumida)), segmentos)
    }
}
