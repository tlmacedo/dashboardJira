package br.sidia.painel.cards.contrato

import br.sidia.painel.auditoria.cartoesDeAuditoria
import br.sidia.painel.cards.cartoesDeSincronizacao
import br.sidia.painel.cards.cartoesDeTempoEmStatus
import br.sidia.painel.cards.cartoesDeTrabalho
import br.sidia.painel.cards.checklist.cartoesDeChecklistReview
import br.sidia.painel.distribuicao.cartoesDeDistribuicao
import br.sidia.painel.governanca.cartoesDeGovernanca
import br.sidia.painel.inicio.cartoesDeInicio
import br.sidia.painel.integridade.cartoesDeIntegridade

/**
 * O catálogo do painel: todo cartão que existe numa tela se registra aqui (teste `todaRotaDeCardEstaNoRegistro`).
 * Os planos seguintes acrescentam seus cartões a esta lista.
 */
fun catalogoPadrao(): CatalogoDeCartoes {
    // Os cartões da Central chamam outros cartões do próprio catálogo: recebem uma referência preguiçosa a ele.
    lateinit var catalogo: CatalogoDeCartoes
    catalogo = CatalogoDeCartoes(
        cartoesDeTempoEmStatus() + cartoesDeSincronizacao() + cartoesDeAuditoria() + cartoesDeTrabalho() +
            cartoesDeChecklistReview() + cartoesDeIntegridade() + cartoesDeDistribuicao() + cartoesDeGovernanca() +
            cartoesDeInicio { catalogo },
    )
    return catalogo
}
