package br.sidia.painel.cards.abertas

import br.sidia.painel.cards.CardDeLeitura
import br.sidia.painel.cards.EntradaDeCard
import br.sidia.painel.cards.INSTANCIA_TRABALHO
import br.sidia.painel.cards.ResultadoDeCard
import br.sidia.painel.cards.linhaDe
import br.sidia.painel.cards.avisoDeCamposIncompletos
import br.sidia.painel.cards.camposDeFiltroDe
import br.sidia.painel.cards.CartaoDeLeitura
import br.sidia.painel.cards.contrato.CartaoRegistrado
import br.sidia.painel.cards.contrato.FonteDoDado
import br.sidia.painel.cards.contrato.GrupoDeCartao
import br.sidia.painel.cards.contrato.Natureza
import br.sidia.painel.cards.metaDeTrabalho
import br.sidia.painel.domain.TimeUnitMode
import br.sidia.painel.sync.FiltroDeFicha
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.Situacao
import java.time.Duration
import java.util.Locale

/** Card Abertas: lê as abertas e o histórico de 16 semanas dentro do escopo e entrega o DTO e as tasks de cada número. */
object CardAbertas : CardDeLeitura<AbertasDto> {
    override val id = "abertas"
    override val periodoPadraoDias: Int? = null

    private const val SEMANAS_DE_HISTORICO = 16L

    override fun calcular(e: EntradaDeCard): ResultadoDeCard<AbertasDto> {
        val ctx = e.ctx
        val ficha = FiltroDeFicha.de(ctx.fichas.getValue(id))
        val abertas = e.store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.ABERTAS, osVersion = e.filtros.os))
            .filter { e.escopo.permiteAtual(it) && e.filtros.aceita(it) }
        // O histórico é a base de comparação: segue o escopo (a visão do papel), não os filtros de tipo e prioridade.
        val historico = e.store.snapshots(
            ConsultaDeTrabalho(
                INSTANCIA_TRABALHO, ficha, Situacao.RESOLVIDAS,
                resolvidasDe = e.agora.minus(Duration.ofDays(SEMANAS_DE_HISTORICO * 7)), osVersion = e.filtros.os,
            ),
        ).filter { e.escopo.permiteAtual(it) }
        val analise = AbertasCalculator(ctx.calendar, ctx.statusMap)
            .analisar(
                abertas, historico, e.agora, e.filtros.unit, e.store.temCamposDeTrabalho(INSTANCIA_TRABALHO), camposDeFiltroDe(ctx),
            )
            .let { a -> avisoDeCamposIncompletos(ctx)?.let { v -> AbertasAnalise(a.dto.copy(avisos = a.dto.avisos + v), a.segmentos, a.ciclos, a.idades) } ?: a }
        val unidade = if (e.filtros.unit == TimeUnitMode.UTIL) "dias úteis" else "dias corridos"
        return ResultadoDeCard(analise.dto) { segmento ->
            analise.segmentos[segmento]?.map { s ->
                val detalhe = if (segmento.startsWith("historico:")) {
                    "Ciclo de ${numero(analise.ciclos[s.key])} $unidade"
                } else {
                    val vence = s.duedate?.let { "vence $it" } ?: "sem vencimento"
                    "Idade ${numero(analise.idades[s.key])} $unidade; $vence"
                }
                linhaDe(s, e.escopo, detalhe)
            }
        }
    }

    private fun numero(v: Double?): String = String.format(Locale.ROOT, "%.1f", v ?: 0.0)

    override fun vazio(avisos: List<br.sidia.painel.cards.AvisoDto>) = AbertasDto(
        abertas = 0, emAndamento = 0, acimaDoP85 = 0, historicoPorTipo = emptyList(), porStatus = emptyList(),
        porPrioridade = emptyList(), porIdade = emptyList(), porVencimento = emptyList(), linhas = emptyList(),
        unit = "util", avisos = avisos,
    )
}

/**
 * Cartões da tela Abertas (inventário `AB-01` a `AB-09`). O AB-03 (P85 histórica) divide o cartão com o AB-02: os dois
 * contam as mesmas concluídas do histórico por tipo.
 */
internal fun cartoesDeAbertas(): List<CartaoRegistrado> {
    val fontes = listOf(FonteDoDado.ISSUE)
    val comHistorico = listOf(FonteDoDado.ISSUE, FonteDoDado.CHANGELOG)
    fun meta(id: String, natureza: Natureza, f: List<FonteDoDado> = fontes) =
        metaDeTrabalho(id, CardAbertas.id, GrupoDeCartao.TRABALHO_EM_CURSO, natureza, f, exigeChangelog = FonteDoDado.CHANGELOG in f)
    return listOf(
        CartaoDeLeitura(meta("AB-01", Natureza.ESTOQUE), CardAbertas, { it == "abertas" || it == "emAndamento" }) {
            mapOf("abertas" to it.abertas, "emAndamento" to it.emAndamento)
        },
        CartaoDeLeitura(meta("AB-02", Natureza.FLUXO, comHistorico), CardAbertas, { it.startsWith("historico:") }) { dto ->
            dto.historicoPorTipo.associate { "historico:${it.tipo}" to it.n }
        },
        CartaoDeLeitura(meta("AB-04", Natureza.FLUXO, comHistorico), CardAbertas, { it == "acimaDoP85" }) { mapOf("acimaDoP85" to it.acimaDoP85) },
        CartaoDeLeitura(meta("AB-05", Natureza.ESTOQUE), CardAbertas, { it.startsWith("status:") }) { dto ->
            dto.porStatus.associate { "status:${it.chave}" to it.n }
        },
        CartaoDeLeitura(meta("AB-06", Natureza.ESTOQUE), CardAbertas, { it.startsWith("prioridade:") }) { dto ->
            dto.porPrioridade.associate { "prioridade:${it.chave}" to it.n }
        },
        CartaoDeLeitura(meta("AB-07", Natureza.ESTOQUE, comHistorico), CardAbertas, { it.startsWith("idade:") }) { dto ->
            dto.porIdade.associate { "idade:${it.chave}" to it.n }
        },
        CartaoDeLeitura(meta("AB-08", Natureza.PRAZO), CardAbertas, { it.startsWith("vence:") }) { dto ->
            dto.porVencimento.associate { "vence:${it.chave}" to it.n }
        },
        CartaoDeLeitura(meta("AB-09", Natureza.ESTOQUE, comHistorico), CardAbertas, { it == "abertas" }) { mapOf("abertas" to it.abertas) },
        CartaoDeLeitura(meta("AB-10", Natureza.ESTOQUE), CardAbertas, { it.startsWith("campo:") }) { dto ->
            dto.porCampo.flatMap { a -> a.grupos.map { "campo:${a.slug}:${it.valor ?: br.sidia.painel.cards.AgrupamentoPorCampos.SEM_VALOR}" to it.n } }.toMap()
        },
    )
}
