package br.sidia.painel.cards.fechadas

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
import br.sidia.painel.trabalho.Situacao
import br.sidia.painel.trabalho.candidatasNoPrazo
import java.util.Locale

/**
 * Card Fechadas: concluídas pela data de resolução (dia de Manaus), período padrão de 90 dias, "no prazo" e "no prazo
 * ajustado" lado a lado e percentis por tipo. Lê só o que o escopo de quem consulta permite.
 */
object CardFechadas : CardDeLeitura<FechadasDto> {
    override val id = "fechadas"
    override val periodoPadraoDias: Int? = 90

    override fun calcular(e: EntradaDeCard): ResultadoDeCard<FechadasDto> {
        val ctx = e.ctx
        val zona = ctx.settings.zone
        val de = requireNotNull(e.filtros.de)
        val ate = requireNotNull(e.filtros.ate)
        val inicio = de.atStartOfDay(zona).toInstant()
        val fim = ate.plusDays(1).atStartOfDay(zona).toInstant()
        val ficha = FiltroDeFicha.de(ctx.fichas.getValue(id))
        val resolvidas = e.store.snapshots(
            ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.RESOLVIDAS, inicio, fim, e.filtros.os),
        ).filter { e.escopo.permiteAtual(it) && e.filtros.aceita(it) }
        val comEventoDeStatus = e.store.comEventoEntre(INSTANCIA_TRABALHO, ficha, setOf("status"), inicio, fim)
            .filter { e.escopo.permiteAtual(it) && e.filtros.aceita(it) && (e.filtros.os == null || it.osVersion.equals(e.filtros.os, ignoreCase = true)) }
        // O bloco "no prazo" lê as candidatas por VENCIMENTO no período (a mesma leitura de Metas e da Auditoria).
        val candidatas = e.store.candidatasNoPrazo(INSTANCIA_TRABALHO, ficha, de, ate, e.filtros.os)
            .filter { e.escopo.permiteAtual(it) && e.filtros.aceita(it) }
        val analise = FechadasCalculator(ctx.calendar, ctx.statusMap, ctx.labelsDeAtraso)
            .analisar(resolvidas, candidatas, comEventoDeStatus, de, ate, e.agora, e.filtros.unit, e.escopo.visiveis)
        val unidade = if (e.filtros.unit == TimeUnitMode.UTIL) "dias úteis" else "dias corridos"
        fun numero(v: Double?) = String.format(Locale.ROOT, "%.1f", v ?: 0.0)
        return ResultadoDeCard(analise.dto) { segmento ->
            analise.segmentos[segmento]?.map { s ->
                val resolvida = analise.diaDaResolucao[s.key]?.let { "Resolvida em $it" } ?: "Concluída sem resolução"
                val detalhe = when {
                    segmento == "lead" -> "$resolvida; lead time de ${numero(analise.leads[s.key])} $unidade"
                    segmento == "ciclo" || segmento == "dispersao" || segmento.startsWith("tipo:") ->
                        "$resolvida; ciclo de ${numero(analise.ciclos[s.key])} $unidade"
                    segmento == "noPrazo" || segmento == "noPrazoAjustado" || segmento == "comVencimento" ->
                        "$resolvida; vencia em ${s.duedate}"
                    segmento == "comPontos" -> "$resolvida; ${numero(s.storyPoints)} ponto(s)"
                    segmento == "semResolution" -> "Em status concluído sem resolução"
                    segmento == "canceladas" -> "$resolvida; resolução de cancelamento"
                    else -> resolvida
                }
                linhaDe(s, e.escopo, detalhe)
            }
        }
    }

    override fun vazio(avisos: List<AvisoDto>) = FechadasDto(
        concluidas = 0, pontos = null, comPontos = 0, ciclo = null, lead = null, noPrazo = 0, noPrazoAjustado = 0, comVencimento = 0,
        taxaNoPrazo = null, taxaAjustada = null, canceladas = 0, porSemana = emptyList(), dispersao = null, porTipo = emptyList(),
        porProjeto = emptyList(), porPessoa = emptyList(), linhas = emptyList(), semResolution = 0, unit = "util", avisos = avisos,
    )
}

/** Cartões da tela Fechadas (inventário `FE-01` a `FE-12`). */
internal fun cartoesDeFechadas(): List<CartaoRegistrado> {
    val issue = listOf(FonteDoDado.ISSUE)
    val comChangelog = listOf(FonteDoDado.ISSUE, FonteDoDado.CHANGELOG)
    val comConfig = listOf(FonteDoDado.ISSUE, FonteDoDado.CONFIGURACAO)
    fun cartao(
        n: Int, natureza: Natureza, fontes: List<FonteDoDado>, aceita: (String) -> Boolean, numeros: (FechadasDto) -> Map<String, Int>,
    ): CartaoRegistrado = CartaoDeLeitura(
        metaDeTrabalho("FE-%02d".format(n), CardFechadas.id, GrupoDeCartao.RESULTADOS, natureza, fontes, exigeChangelog = FonteDoDado.CHANGELOG in fontes),
        CardFechadas, aceita, numeros,
    )
    return listOf(
        cartao(1, Natureza.FLUXO, issue, { it == "concluidas" }) { mapOf("concluidas" to it.concluidas) },
        cartao(2, Natureza.FLUXO, issue, { it == "comPontos" }) { mapOf("comPontos" to it.comPontos) },
        cartao(3, Natureza.FLUXO, comChangelog, { it == "ciclo" }) { mapOf("ciclo" to (it.ciclo?.n ?: 0)) },
        cartao(4, Natureza.FLUXO, issue, { it == "lead" }) { mapOf("lead" to (it.lead?.n ?: 0)) },
        cartao(5, Natureza.PRAZO, comConfig, { it in setOf("noPrazo", "noPrazoAjustado", "comVencimento") }) {
            mapOf("noPrazo" to it.noPrazo, "noPrazoAjustado" to it.noPrazoAjustado, "comVencimento" to it.comVencimento)
        },
        cartao(6, Natureza.QUALIDADE, comChangelog, { it == "canceladas" || it == "semResolution" }) {
            mapOf("canceladas" to it.canceladas, "semResolution" to it.semResolution)
        },
        cartao(7, Natureza.FLUXO, issue, { it.startsWith("semana:") }) { dto -> dto.porSemana.associate { "semana:${it.semana}" to it.n } },
        cartao(8, Natureza.FLUXO, comChangelog, { it == "dispersao" }) { mapOf("dispersao" to (it.dispersao?.pontos?.size ?: 0)) },
        cartao(9, Natureza.FLUXO, comChangelog, { it.startsWith("tipo:") }) { dto -> dto.porTipo.associate { "tipo:${it.tipo}" to it.n } },
        cartao(10, Natureza.FLUXO, issue, { it.startsWith("projeto:") }) { dto -> dto.porProjeto.associate { "projeto:${it.chave}" to it.n } },
        cartao(11, Natureza.PESSOAS, comChangelog, { it.startsWith("pessoa:") }) { dto ->
            buildMap {
                for (p in dto.porPessoa) {
                    put("pessoa:${p.pessoa}:finalizou", p.finalizou)
                    put("pessoa:${p.pessoa}:participou", p.participou)
                }
            }
        },
        cartao(12, Natureza.FLUXO, comChangelog, { it == "concluidas" }) { mapOf("concluidas" to it.concluidas) },
    )
}
