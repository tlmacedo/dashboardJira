package br.sidia.painel.cards.fluxo

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.CardDeLeitura
import br.sidia.painel.cards.CartaoDeLeitura
import br.sidia.painel.cards.EntradaDeCard
import br.sidia.painel.cards.ErroHttp
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
import io.ktor.http.HttpStatusCode
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * Card Controle do ciclo e tendência (CT-01 e CT-02), a seção nova da tela Fechadas. Lê as resolvidas do período (padrão:
 * 12 meses, 365 dias) no escopo de quem consulta e entrega a análise do [ControleDoCicloCalculator]. O parâmetro
 * `semOutliers=true` tira os ciclos acima de 30 dias corridos (qualquer outro valor é 400). Cada ponto e cada mês abrem as
 * MESMAS tasks pelo drill.
 */
object CardControleDoCiclo : CardDeLeitura<ControleDoCicloDto> {
    override val id = "controle-ciclo"
    override val periodoPadraoDias: Int? = 365

    /** Meses da série quando o período não é informado. */
    private const val MESES_PADRAO = 12

    private fun semOutliers(e: EntradaDeCard): Boolean = when (e.parametros["semOutliers"]?.trim()?.lowercase(Locale.ROOT)) {
        null, "", "false" -> false
        "true" -> true
        else -> throw ErroHttp(HttpStatusCode.BadRequest)
    }

    override fun calcular(e: EntradaDeCard): ResultadoDeCard<ControleDoCicloDto> {
        val ctx = e.ctx
        val zona = ctx.settings.zone
        val ate = requireNotNull(e.filtros.ate)
        // Sem `de` informado, a série cobre os 12 MESES inteiros que terminam no mês de `ate` (e não 365 dias soltos).
        val de = if (e.parametros["de"].isNullOrBlank()) YearMonth.from(ate).minusMonths(MESES_PADRAO - 1L).atDay(1) else requireNotNull(e.filtros.de)
        val sem = semOutliers(e)
        val ficha = FiltroDeFicha.de(ctx.fichas.getValue(id))
        val resolvidas = e.store.snapshots(
            ConsultaDeTrabalho(
                INSTANCIA_TRABALHO, ficha, Situacao.RESOLVIDAS, de.atStartOfDay(zona).toInstant(),
                ate.plusDays(1).atStartOfDay(zona).toInstant(), e.filtros.os,
            ),
        ).filter { e.escopo.permiteAtual(it) && e.filtros.aceita(it) }
        val analise = ControleDoCicloCalculator(ctx.calendar, ctx.statusMap).analisar(resolvidas, de, ate, e.filtros.unit, sem)
        val unidade = if (e.filtros.unit == TimeUnitMode.UTIL) "dias úteis" else "dias corridos"
        return ResultadoDeCard(analise.dto) { segmento ->
            analise.segmentos[segmento]?.map { s ->
                val dia: LocalDate? = analise.dias[s.key]
                val ciclo = String.format(Locale.ROOT, "%.1f", analise.ciclos[s.key] ?: 0.0)
                linhaDe(s, e.escopo, "Concluída em $dia; ciclo de $ciclo $unidade")
            }
        }
    }

    override fun vazio(avisos: List<AvisoDto>) = ControleDoCicloDto(
        series = emptyList(), concluidas = 0, semOutliers = false, limiteOutlierDias = ControleDoCicloCalculator.LIMITE_OUTLIER_DIAS,
        outliersExcluidos = 0, de = "", ate = "", unit = "util", avisos = avisos,
    )
}

/** Cartões do Controle do ciclo: CT-01 (os pontos, por tipo e por dia) e CT-02 (a série mensal, por mês e tipo). */
internal fun cartoesDeControleDoCiclo(): List<CartaoRegistrado> {
    val fontes = listOf(FonteDoDado.ISSUE, FonteDoDado.CHANGELOG)
    return listOf(
        CartaoDeLeitura(
            metaDeTrabalho("CT-01", CardControleDoCiclo.id, GrupoDeCartao.RESULTADOS, Natureza.FLUXO, fontes, exigeChangelog = true),
            CardControleDoCiclo, { it.startsWith("tipo:") || it.startsWith("dia:") },
        ) { dto ->
            buildMap {
                for (s in dto.series) {
                    put("tipo:${s.tipo}", s.n)
                    for ((dia, pontos) in s.pontos.groupBy { it.dia }) put("dia:$dia:${s.tipo}", pontos.size)
                }
            }
        },
        CartaoDeLeitura(
            metaDeTrabalho("CT-02", CardControleDoCiclo.id, GrupoDeCartao.RESULTADOS, Natureza.FLUXO, fontes, exigeChangelog = true),
            CardControleDoCiclo, { it.startsWith("mes:") },
        ) { dto ->
            buildMap { for (s in dto.series) for (m in s.meses) if (m.n > 0) put("mes:${m.mes}:${s.tipo}", m.n) }
        },
    )
}
