package br.sidia.painel.cards.previsao

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
import br.sidia.painel.domain.TimeUnitMode
import br.sidia.painel.sync.FiltroDeFicha
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.SerieSemanal
import br.sidia.painel.trabalho.Situacao
import br.sidia.painel.trabalho.sementeDoDia
import java.util.Locale

/**
 * Card Previsão: Monte Carlo sobre a vazão e a chegada semanais do escopo de quem consulta (semanas fechadas, segunda a
 * domingo de Manaus). Dentro da mesma visão e dos mesmos filtros, a semente é a do dia: o card, o detalhamento e a recarga
 * mostram o mesmo número.
 */
object CardPrevisao : CardDeLeitura<PrevisaoDto> {
    override val id = "previsao"
    override val periodoPadraoDias: Int? = null

    override fun calcular(e: EntradaDeCard): ResultadoDeCard<PrevisaoDto> {
        val ctx = e.ctx
        val regras = ctx.regrasDePrevisao
        val zona = ctx.settings.zone
        val ficha = FiltroDeFicha.de(ctx.fichas.getValue(id))
        val abertas = e.store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.ABERTAS, osVersion = e.filtros.os))
            .filter { e.escopo.permiteAtual(it) && e.filtros.aceita(it) }
        // O histórico começa na primeira semana da janela e vai até agora: as criadas na janela e entregues depois dela
        // precisam estar aqui para a chegada contar tudo.
        val primeiraSemana = SerieSemanal.ultimaSemanaFechada(e.hoje).minusWeeks(regras.semanasHistorico - 1L)
        val historico = e.store.snapshots(
            ConsultaDeTrabalho(
                INSTANCIA_TRABALHO, ficha, Situacao.RESOLVIDAS, resolvidasDe = primeiraSemana.atStartOfDay(zona).toInstant(), osVersion = e.filtros.os,
            ),
        ).filter { e.escopo.permiteAtual(it) && e.filtros.aceita(it) }
        Fluxo.exigirStatusMapeados(ctx.statusMap, abertas, historico)

        val analise = PrevisaoCalculator(ctx.calendar, ctx.statusMap, regras)
            .analisar(abertas, historico, e.agora, sementeDoDia(e.hoje, e.filtros), e.filtros.unit)
        val unidade = if (e.filtros.unit == TimeUnitMode.UTIL) "dias úteis" else "dias corridos"
        val porIssue = analise.dto.porIssue.associateBy { it.key }
        fun n(v: Double) = String.format(Locale.ROOT, "%.1f", v)
        return ResultadoDeCard(analise.dto) { segmento ->
            analise.segmentos[segmento]?.map { s ->
                val detalhe = when {
                    segmento == "backlog" -> "Aberta em ${s.status}"
                    segmento == "vazao" || segmento.startsWith("vazao:") ->
                        "Entregue em ${ctx.calendar.localDate(requireNotNull(s.resolved))}"
                    segmento.startsWith("chegada:") -> "Criada em ${ctx.calendar.localDate(s.created)}"
                    else -> porIssue[s.key]?.let { p ->
                        val resto = if (p.restanteP50 == null) {
                            "poucos ciclos do tipo mais longos que a idade (${p.n} de 5)"
                        } else {
                            "faltam ${n(p.restanteP50)} a ${n(requireNotNull(p.restanteP85))} $unidade (P50 a P85, ${p.n} ciclos)"
                        }
                        "Idade ${n(p.idade)} $unidade; $resto"
                    }.orEmpty()
                }
                linhaDe(s, e.escopo, detalhe)
            }
        }
    }

    override fun vazio(avisos: List<AvisoDto>) = PrevisaoDto(
        versaoRegras = "", backlog = 0, termina = QuandoDto(null, null, null, null, null, null), confianca = "baixa", semanasComDado = 0,
        semanasHistorico = 0, limiteSemanas = 0, comChegada = true, entregasNaJanela = 0, vazaoSemanal = emptyList(),
        chegadaSemanal = emptyList(), quantos = emptyList(), porIssue = emptyList(), avisos = avisos,
    )
}

/** Cartões da tela Previsão (inventário `PV-01` a `PV-06`). O PV-05 é uma projeção sem tasks por trás: não tem gaveta. */
internal fun cartoesDePrevisao(): List<CartaoRegistrado> {
    val fontes = listOf(FonteDoDado.ISSUE, FonteDoDado.CONFIGURACAO)
    val comChangelog = listOf(FonteDoDado.ISSUE, FonteDoDado.CHANGELOG, FonteDoDado.CONFIGURACAO)
    fun cartao(
        n: Int, natureza: Natureza, f: List<FonteDoDado>, temDrill: Boolean, aceita: (String) -> Boolean, numeros: (PrevisaoDto) -> Map<String, Int>,
    ): CartaoRegistrado = CartaoDeLeitura(
        metaDeTrabalho("PV-0$n", CardPrevisao.id, GrupoDeCartao.TRABALHO_EM_CURSO, natureza, f, exigeChangelog = FonteDoDado.CHANGELOG in f, temDrill = temDrill),
        CardPrevisao, aceita, numeros,
    )
    return listOf(
        cartao(1, Natureza.ESTOQUE, fontes, true, { it == "backlog" }) { mapOf("backlog" to it.backlog) },
        // O "termina em" é uma projeção do backlog: o cartão abre o backlog (o numerador), e o texto diz isso.
        cartao(2, Natureza.FLUXO, fontes, true, { it == "backlog" }) { mapOf("backlog" to it.backlog) },
        cartao(3, Natureza.QUALIDADE, fontes, true, { it == "vazao" }) { mapOf("vazao" to it.entregasNaJanela) },
        cartao(4, Natureza.FLUXO, fontes, true, { it.startsWith("vazao:") || it.startsWith("chegada:") }) { dto ->
            dto.vazaoSemanal.associate { "vazao:${it.semana}" to it.n } + dto.chegadaSemanal.associate { "chegada:${it.semana}" to it.n }
        },
        cartao(5, Natureza.FLUXO, fontes, false, { false }) { emptyMap() },
        cartao(6, Natureza.FLUXO, comChangelog, true, { it == "porIssue" }) { mapOf("porIssue" to it.porIssue.size) },
    )
}
