package br.sidia.painel.cards

import br.sidia.painel.AppContext
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.domain.TimelineBuilder
import br.sidia.painel.security.Principal
import br.sidia.painel.security.Role
import br.sidia.painel.security.Visibility
import br.sidia.painel.trabalho.IssueSnapshot
import io.ktor.http.HttpStatusCode
import java.time.Instant
import java.time.LocalDate

/**
 * O que quem pergunta pode ver: as pessoas ([visiveis]) e se a fila sem responsável entra ([veSemResponsavel], só
 * coordenação e gerente e só sem filtro de squad ou pessoa). TODO número de um card sai só das issues que passam por
 * aqui (inclusive P50/P85 históricos e throughput): agregado nunca é calculado com tasks que o papel não vê.
 */
data class Escopo(val visiveis: Set<String>, val veSemResponsavel: Boolean) {
    /** Pelo responsável ATUAL: a issue sem responsável só entra com [veSemResponsavel]. */
    fun permiteAtual(s: IssueSnapshot): Boolean = s.assignee?.let { it in visiveis } ?: veSemResponsavel

    /** Por algum trecho de POSSE da issue (a regra do Tempo em status): alguém visível a teve em algum momento. */
    fun permitePosse(s: IssueSnapshot, map: StatusMap, agora: Instant): Boolean =
        TimelineBuilder.intervals(s.timeline, agora, map).any { it.assignee?.let { a -> a in visiveis } ?: veSemResponsavel }
}

/**
 * Escopo de [p] com os filtros [f]. [todos] são as pessoas que existem na base (responsáveis e donos anteriores).
 * Squad inexistente é 400 e pessoa fora do que o papel vê é 403 (o filtro nunca amplia a visão); o squad vem do
 * diretório de papéis na data de hoje, nunca de lista digitada.
 */
internal fun escopoDe(p: Principal, ctx: AppContext, hoje: LocalDate, f: FiltrosDeCard, todos: Set<String>): Escopo {
    val membrosDoSquad = f.squad?.let { nome ->
        ctx.roles.membersOf(nome, hoje).takeIf { it.isNotEmpty() } ?: throw ErroHttp(HttpStatusCode.BadRequest)
    }
    val visiveis = Visibility.visibleUserIds(p, ctx.roles, hoje, todos + listOfNotNull(f.pessoa))
    if (f.pessoa != null && f.pessoa !in visiveis) throw ErroHttp(HttpStatusCode.Forbidden)
    var permitidos = visiveis
    if (membrosDoSquad != null) permitidos = permitidos intersect membrosDoSquad
    if (f.pessoa != null) permitidos = permitidos intersect setOf(f.pessoa)
    val coordenacao = Role.COORDENADOR in p.roles || Role.GERENTE in p.roles
    return Escopo(permitidos, veSemResponsavel = coordenacao && f.squad == null && f.pessoa == null)
}
