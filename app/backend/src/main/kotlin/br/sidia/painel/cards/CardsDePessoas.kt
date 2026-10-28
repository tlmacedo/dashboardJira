package br.sidia.painel.cards

import br.sidia.painel.AppContext
import br.sidia.painel.cards.calendario.CardCalendario
import br.sidia.painel.cards.calendario.cartoesDeCalendario
import br.sidia.painel.cards.capacidade.CardCapacidade
import br.sidia.painel.cards.capacidade.SO_LIDERANCA
import br.sidia.painel.cards.capacidade.cartoesDeCapacidade
import br.sidia.painel.cards.carga.CardCarga
import br.sidia.painel.cards.carga.cartoesDeCarga
import br.sidia.painel.cards.contrato.CartaoRegistrado
import br.sidia.painel.cards.gargalos.CardGargalos
import br.sidia.painel.cards.gargalos.cartoesDeGargalos
import br.sidia.painel.cards.plano.CardPlano
import br.sidia.painel.cards.plano.cartoesDePlano
import br.sidia.painel.cards.tendencia.CardTendencia
import br.sidia.painel.cards.tendencia.cartoesDeTendencia
import br.sidia.painel.cards.vencimentos.CardVencimentos
import br.sidia.painel.cards.vencimentos.cartoesDeVencimentos
import br.sidia.painel.db.AccessLog
import br.sidia.painel.trabalho.WorkStore
import io.ktor.server.routing.Route

/**
 * Os cards da Fase 6 (pessoas, planejamento e ausências), juntos para o `catalogoPadrao()` e as rotas: cada plano acrescenta
 * os seus cards aqui, em vez de espalhar linhas pelos arquivos compartilhados.
 */
internal fun cartoesDePessoas(): List<CartaoRegistrado> =
    cartoesDeCarga() + cartoesDeCapacidade() + cartoesDeCalendario() + cartoesDePlano() + cartoesDeVencimentos() +
        cartoesDeGargalos() + cartoesDeTendencia()

/** `GET /api/cards/{id}` dos cards de pessoas e planejamento (o drill é a rota genérica dos cartões). */
internal fun Route.rotasDosCardsDePessoas(ctx: AppContext, store: WorkStore, auditoria: AccessLog) {
    rotaDeCard(ctx, store, auditoria, CardCarga)
    rotaDeCard(ctx, store, auditoria, CardCapacidade, SO_LIDERANCA)
    rotaDeCard(ctx, store, auditoria, CardCalendario)
    rotaDeCard(ctx, store, auditoria, CardPlano, SO_LIDERANCA)
    rotaDeCard(ctx, store, auditoria, CardVencimentos)
    rotaDeCard(ctx, store, auditoria, CardGargalos, SO_LIDERANCA)
    rotaDeCard(ctx, store, auditoria, CardTendencia, SO_LIDERANCA)
}
