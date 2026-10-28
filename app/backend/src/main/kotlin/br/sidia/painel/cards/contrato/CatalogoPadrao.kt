package br.sidia.painel.cards.contrato

import br.sidia.painel.auditoria.cartoesDeAuditoria
import br.sidia.painel.cards.cartoesDeSincronizacao
import br.sidia.painel.cards.cartoesDeTempoEmStatus
import br.sidia.painel.cards.cartoesDeTrabalho
import br.sidia.painel.cards.checklist.cartoesDeChecklistReview
import br.sidia.painel.integridade.cartoesDeIntegridade

/**
 * O catálogo do painel: todo cartão que existe numa tela se registra aqui (teste `todaRotaDeCardEstaNoRegistro`).
 * Os planos seguintes acrescentam seus cartões a esta lista.
 */
fun catalogoPadrao(): CatalogoDeCartoes =
    CatalogoDeCartoes(
        cartoesDeTempoEmStatus() + cartoesDeSincronizacao() + cartoesDeAuditoria() + cartoesDeTrabalho() +
            cartoesDeChecklistReview() + cartoesDeIntegridade(),
    )
