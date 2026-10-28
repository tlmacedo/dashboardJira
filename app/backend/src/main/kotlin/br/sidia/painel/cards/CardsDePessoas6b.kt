package br.sidia.painel.cards

import br.sidia.painel.AppContext
import br.sidia.painel.cards.capacidade.SO_LIDERANCA
import br.sidia.painel.cards.cobertura.CardCobertura
import br.sidia.painel.cards.cobertura.cartoesDeCobertura
import br.sidia.painel.cards.contrato.CartaoRegistrado
import br.sidia.painel.cards.esforco.CardEsforco
import br.sidia.painel.cards.esforco.cartoesDeEsforco
import br.sidia.painel.cards.posse.CardPosse
import br.sidia.painel.cards.posse.cartoesDePosse
import br.sidia.painel.cards.simulador.CardSimulador
import br.sidia.painel.cards.simulador.cartoesDeSimulador
import br.sidia.painel.db.AccessLog
import br.sidia.painel.trabalho.WorkStore
import io.ktor.server.routing.Route

/**
 * Os cards do bloco 6b da Fase 6 (posse, esforço, cobertura, simulador, competências, revisões e carga por tipo/OS), em um
 * arquivo próprio para o `CardsDePessoas.kt` (dos cards do bloco 6a) crescer só uma linha de cada lado.
 */
internal fun cartoesDePessoas6b(): List<CartaoRegistrado> =
    cartoesDePosse() + cartoesDeEsforco() + cartoesDeCobertura() + cartoesDeSimulador()

/** `GET /api/cards/{id}` dos cards do bloco 6b (o drill é a rota genérica dos cartões). */
internal fun Route.rotasDosCardsDePessoas6b(ctx: AppContext, store: WorkStore, auditoria: AccessLog) {
    rotaDeCard(ctx, store, auditoria, CardPosse)
    rotaDeCard(ctx, store, auditoria, CardEsforco)
    rotaDeCard(ctx, store, auditoria, CardCobertura, SO_LIDERANCA)
    rotaDeCard(ctx, store, auditoria, CardSimulador, SO_LIDERANCA)
}
