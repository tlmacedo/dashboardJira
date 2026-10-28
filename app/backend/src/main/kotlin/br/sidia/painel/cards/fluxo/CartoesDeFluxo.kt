package br.sidia.painel.cards.fluxo

import br.sidia.painel.AppContext
import br.sidia.painel.cards.contrato.CartaoRegistrado
import br.sidia.painel.cards.rotaDeCard
import br.sidia.painel.db.AccessLog
import br.sidia.painel.trabalho.SqliteWorkStore
import io.ktor.server.routing.Route

/** Todos os cartões dos cards de fluxo e gestão do Plano 17b, para `cartoesDeTrabalho()` (e daí o `catalogoPadrao()`). */
internal fun cartoesDeFluxo(): List<CartaoRegistrado> =
    cartoesDeFluxoDaSemana() + cartoesDeAgingWip() + cartoesDeBloqueios() + cartoesDeControleDoCiclo()

/** `GET /api/cards/{id}` de cada card do Plano 17b (o drill é a rota genérica dos cartões). */
fun Route.rotasDosCardsDeFluxo(ctx: AppContext, auditoria: AccessLog) {
    val store = SqliteWorkStore(requireNotNull(ctx.dataSource))
    rotaDeCard(ctx, store, auditoria, CardFluxoDaSemana)
    rotaDeCard(ctx, store, auditoria, CardAgingWip)
    rotaDeCard(ctx, store, auditoria, CardBloqueios)
    rotaDeCard(ctx, store, auditoria, CardControleDoCiclo)
}
