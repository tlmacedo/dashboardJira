package br.sidia.painel.pessoas

import br.sidia.painel.AppContext

/**
 * O motor puro de capacidade montado a partir do [AppContext]: horas da issue, capacidade líquida, demanda e agenda. Os
 * objetos são leves e sem estado, então cada card pode pedir o seu a cada cálculo.
 */
class MotorDePessoas(ctx: AppContext) {
    val horas = HorasDaIssue(ctx.regrasPlanejamento)
    val capacidade = Capacidade(ctx.equipes, ctx.ausencias, ctx.calendar, ctx.regrasPlanejamento)

    /** A mesma capacidade sem nenhuma ausência (o cenário "se ninguém faltasse", base das comparações). */
    val capacidadeSemAusencia = Capacidade(ctx.equipes, Ausencias(emptyList()), ctx.calendar, ctx.regrasPlanejamento)
    val demanda = Demanda(horas, capacidade, ctx.statusMap, ctx.regrasPlanejamento)
    val agenda = Agenda(horas, capacidade, ctx.statusMap)
}
