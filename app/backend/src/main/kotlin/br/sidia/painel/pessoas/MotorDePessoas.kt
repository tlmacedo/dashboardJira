package br.sidia.painel.pessoas

import br.sidia.painel.AppContext
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.time.BusinessCalendar

/**
 * O motor puro de capacidade: horas da issue, capacidade líquida, demanda e agenda. Os objetos são leves e sem estado,
 * então cada card pode montar o seu a cada cálculo ([MotorDePessoas] a partir do [AppContext]).
 */
class MotorDePessoas(
    equipes: Equipes,
    ausencias: Ausencias,
    val calendario: BusinessCalendar,
    val regras: RegrasDePlanejamento,
    val statusMap: StatusMap,
) {
    constructor(ctx: AppContext) : this(ctx.equipes, ctx.ausencias, ctx.calendar, ctx.regrasPlanejamento, ctx.statusMap)

    val equipes: Equipes = equipes
    val ausencias: Ausencias = ausencias
    val horas = HorasDaIssue(regras)
    val capacidade = Capacidade(equipes, ausencias, calendario, regras)

    /** A mesma capacidade sem nenhuma ausência (o cenário "se ninguém faltasse", base das comparações). */
    val capacidadeSemAusencia = Capacidade(equipes, Ausencias(emptyList()), calendario, regras)
    val demanda = Demanda(horas, capacidade, statusMap, regras)
    val agenda = Agenda(horas, capacidade, statusMap)

    /** As pessoas da equipe, em ordem alfabética (nunca por carga). */
    fun pessoas(): List<String> = equipes.todos.map { it.id }.sorted()
}
