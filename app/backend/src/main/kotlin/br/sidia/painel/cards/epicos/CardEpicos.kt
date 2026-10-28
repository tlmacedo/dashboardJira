package br.sidia.painel.cards.epicos

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
import br.sidia.painel.sync.FiltroDeFicha
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.Situacao
import br.sidia.painel.trabalho.sementeDoDia

/**
 * Card Épicos: uma linha por épico com itens, progresso, vencidos e previsão só com a vazão do PRÓPRIO épico. Respeita a
 * visão de quem consulta (squad e pessoa inclusive), mas ignora os filtros de tipo e de prioridade, como o app anterior.
 */
object CardEpicos : CardDeLeitura<EpicosDto> {
    override val id = "epicos"
    override val periodoPadraoDias: Int? = null

    override fun calcular(e: EntradaDeCard): ResultadoDeCard<EpicosDto> {
        val ctx = e.ctx
        val ficha = FiltroDeFicha.de(ctx.fichas.getValue(id))
        val todas = e.store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.TODAS, osVersion = e.filtros.os))
            .filter { e.escopo.permiteAtual(it) }
        Fluxo.exigirStatusMapeados(ctx.statusMap, todas)
        val analise = EpicosCalculator(ctx.calendar, ctx.statusMap, ctx.regrasDePrevisao, ctx.regrasAuditoria.tiposReview)
            .analisar(todas, e.agora, semente(e.hoje, e.filtros))
        return ResultadoDeCard(analise.dto) { segmento ->
            analise.segmentos[segmento]?.map { s ->
                val detalhe = when {
                    segmento.endsWith(":vencidos") -> "Venceu em ${s.duedate}"
                    segmento.endsWith(":concluidos") -> "Concluída em ${ctx.calendar.localDate(requireNotNull(s.resolved))}"
                    else -> "Épico ${s.epicKey}"
                }
                linhaDe(s, e.escopo, detalhe)
            }
        }
    }

    /** Semente só com os filtros que o card aplica (squad, pessoa e OS): tipo, prioridade, período e unidade são ignorados. */
    fun semente(dia: java.time.LocalDate, f: br.sidia.painel.cards.FiltrosDeCard): Long =
        sementeDoDia(dia, f.copy(tipos = emptyList(), prioridades = emptyList(), de = null, ate = null, unit = br.sidia.painel.domain.TimeUnitMode.UTIL))

    override fun vazio(avisos: List<AvisoDto>) = EpicosDto(emptyList(), 0, avisos)
}

/** Cartão da tela Épicos (inventário `EP-01`): cada contagem do épico abre as tasks que a formam. */
internal fun cartoesDeEpicos(): List<CartaoRegistrado> = listOf(
    CartaoDeLeitura(
        metaDeTrabalho(
            "EP-01", CardEpicos.id, GrupoDeCartao.TRABALHO_EM_CURSO, Natureza.ESTOQUE,
            listOf(FonteDoDado.ISSUE, FonteDoDado.CONFIGURACAO),
        ),
        CardEpicos, { it.startsWith("epico:") },
    ) { dto ->
        buildMap {
            for (l in dto.linhas) {
                put("epico:${l.id}:itens", l.itens)
                put("epico:${l.id}:concluidos", l.concluidos)
                put("epico:${l.id}:vencidos", l.vencidos)
            }
        }
    },
)
