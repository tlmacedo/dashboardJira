package br.sidia.painel.cards.revisoes

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.CardDeLeitura
import br.sidia.painel.cards.CartaoDeLeitura
import br.sidia.painel.cards.EntradaDeCard
import br.sidia.painel.cards.INSTANCIA_TRABALHO
import br.sidia.painel.cards.ResultadoDeCard
import br.sidia.painel.cards.contrato.CartaoRegistrado
import br.sidia.painel.cards.contrato.FonteDoDado
import br.sidia.painel.cards.contrato.GrupoDeCartao
import br.sidia.painel.cards.contrato.Natureza
import br.sidia.painel.cards.linhaDe
import br.sidia.painel.cards.metaDeTrabalho
import br.sidia.painel.pessoas.MotorDePessoas
import br.sidia.painel.security.Role
import br.sidia.painel.sync.FiltroDeFicha
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.Situacao

/**
 * Card Revisões pendentes por revisor: as reviews abertas (tipos de `vinculos.yaml`) de cada revisor da visão, as vencidas e as
 * fora do prazo de revisão (dias úteis; a unidade do filtro `unit` não se aplica). Todos os papéis veem o card; o USUARIO só
 * enxerga as próprias. `semRevisor` (reviews sem responsável) só para coordenação e gerente e só sem filtro de squad ou pessoa.
 */
object CardRevisoes : CardDeLeitura<RevisoesDto> {
    override val id = "revisoes"
    override val periodoPadraoDias: Int? = null

    override fun calcular(e: EntradaDeCard): ResultadoDeCard<RevisoesDto> {
        val ctx = e.ctx
        val motor = MotorDePessoas(ctx)
        val ficha = FiltroDeFicha.de(ctx.fichas.getValue(id))
        val abertas = e.store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.ABERTAS, osVersion = e.filtros.os))
            .filter { e.escopo.permiteAtual(it) && e.filtros.aceita(it) }
        Fluxo.exigirStatusMapeados(ctx.statusMap, abertas)

        val analise = RevisoesCalculator(motor, ctx.regrasAuditoria.tiposReview)
            .calcular(e.escopo.visiveis, abertas, e.hoje, e.agora, veSemRevisor = e.escopo.veSemResponsavel)
        return ResultadoDeCard(analise.dto) { segmento ->
            analise.segmentos[segmento]?.map { s -> linhaDe(s, e.escopo, s.duedate?.let { "Vence em $it" } ?: "Sem vencimento") }
        }
    }

    override fun vazio(avisos: List<AvisoDto>) = RevisoesDto(linhas = emptyList(), semRevisor = null, avisos = avisos)
}

/** Só coordenação e gerente veem as reviews sem revisor. */
private val COORDENACAO = setOf(Role.COORDENADOR, Role.GERENTE)

/** Cartões da tela Revisões: `RV-01` (por revisor) e `RV-02` (sem revisor, só coordenação e gerente). */
internal fun cartoesDeRevisoes(): List<CartaoRegistrado> {
    val fontes = listOf(FonteDoDado.ISSUE, FonteDoDado.CONFIGURACAO)
    return listOf(
        CartaoDeLeitura(
            metaDeTrabalho("RV-01", CardRevisoes.id, GrupoDeCartao.PESSOAS_E_PLANEJAMENTO, Natureza.ESTOQUE, fontes),
            CardRevisoes, { it.startsWith("revisor:") },
        ) { dto ->
            buildMap {
                for (l in dto.linhas) {
                    put("revisor:${l.revisor}:pendentes", l.pendentes)
                    put("revisor:${l.revisor}:vencidas", l.vencidas)
                    put("revisor:${l.revisor}:foraDoSla", l.foraDoSla)
                }
            }
        },
        CartaoDeLeitura(
            metaDeTrabalho("RV-02", CardRevisoes.id, GrupoDeCartao.PESSOAS_E_PLANEJAMENTO, Natureza.QUALIDADE, fontes, papeis = COORDENACAO),
            CardRevisoes, { it == "semRevisor" },
        ) { dto -> dto.semRevisor?.let { mapOf("semRevisor" to it) }.orEmpty() },
    )
}
