package br.sidia.painel.cards.checklist

import br.sidia.painel.AppContext
import br.sidia.painel.auditoria.SqliteAuditStore
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
import br.sidia.painel.cards.rotaDeCard
import br.sidia.painel.checklist.AlertaDoPar
import br.sidia.painel.checklist.EstatisticasChecklistReview
import br.sidia.painel.checklist.ParCR
import br.sidia.painel.checklist.ParesChecklistReview
import br.sidia.painel.checklist.RegrasChecklistReview
import br.sidia.painel.db.AccessLog
import br.sidia.painel.domain.TimeUnitMode
import br.sidia.painel.sync.FiltroDeFicha
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.SqliteWorkStore
import br.sidia.painel.trabalho.Situacao
import io.ktor.server.routing.Route
import java.util.Locale

/**
 * Card Checklist × Review (CR-01 a CR-14): pares pelo vínculo "Blocks" (nunca pelo `parent`), alertas por par,
 * estatísticas, lift de labels e aprendizados com amostra mínima. Os filtros (tipo, prioridade, OS, squad, pessoa e
 * período) valem para o PAI; o par é visível para quem participa dele (responsável do pai OU da review). Nada aqui
 * compara colegas: o responsável só aparece se estiver na visão de quem pediu.
 */
object CardChecklistReview : CardDeLeitura<ChecklistReviewDto> {
    override val id = "checklist-review"
    override val periodoPadraoDias: Int? = 90

    /** Corte da lista de pares na resposta (a contagem e a gaveta trazem todos). */
    private const val LIMITE_DE_PARES = 500
    private const val LIMITE_SEM_REVIEW = 100

    override fun calcular(e: EntradaDeCard): ResultadoDeCard<ChecklistReviewDto> {
        val ctx = e.ctx
        val regras = ctx.regrasChecklistReview
        val ficha = FiltroDeFicha.de(ctx.fichas.getValue(id))
        val todas = e.store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.TODAS))
        Fluxo.exigirStatusMapeados(ctx.statusMap, todas)
        val lidos = SqliteAuditStore(requireNotNull(ctx.dataSource)).paresTaskReview(INSTANCIA_TRABALHO, ctx.vinculos)
        val motor = ParesChecklistReview(regras, ctx.regrasAuditoria.tiposReview, ctx.statusMap, ctx.calendar)

        val filtrado = e.filtros.squad != null || e.filtros.pessoa != null
        fun doPai(pai: IssueSnapshot) = e.filtros.aceita(pai) && noPeriodo(pai, e) &&
            (e.filtros.os == null || e.filtros.os.equals(pai.osVersion, ignoreCase = true))
        // Sem filtro de squad/pessoa vale a visão do par inteiro; com filtro, o escopo (já cruzado com o filtro) vale para o pai.
        fun visivel(p: ParCR) = e.escopo.permiteAtual(p.pai) || (!filtrado && e.escopo.permiteAtual(p.review))

        val pares = motor.pares(todas, lidos.vinculos, e.agora).filter { doPai(it.pai) && visivel(it) }
        val semReview = motor.semReview(todas, lidos.vinculos).filter { doPai(it) && e.escopo.permiteAtual(it) }

        val unit = e.filtros.unit
        val stats = EstatisticasChecklistReview.resumir(pares, semReview.size, regras, ctx.calendar, ctx.statusMap, unit)
        val concluidas = pares.filter { EstatisticasChecklistReview.reviewConcluida(it, ctx.statusMap) }
        val faixas = EstatisticasChecklistReview.nomesDasFaixas(regras.faixasDuracaoHoras)
        val comAlerta = pares.filter { it.alertas.isNotEmpty() }
        val porAlerta = AlertaDoPar.entries.associate { a -> a.name to pares.count { a in it.alertas } }

        val avisos = buildList {
            if (lidos.ambiguas > 0) add(AvisoDto(AvisoDto.VINCULO_AMBIGUO, AvisoDto.INFORMATIVO, "${lidos.ambiguas} vínculo(s) ambíguos", mapOf("n" to lidos.ambiguas.toString())))
            if (lidos.semVinculos) add(AvisoDto(AvisoDto.VINCULOS_INDISPONIVEIS, AvisoDto.INFORMATIVO, "nenhum vínculo do tipo configurado na base"))
            if (pares.size in 1 until regras.amostraMinima) {
                add(
                    AvisoDto(
                        AvisosDoChecklistReview.PARES_EM_AMOSTRA_PEQUENA, AvisoDto.INFORMATIVO, "poucos pares",
                        mapOf("n" to pares.size.toString(), "minimo" to regras.amostraMinima.toString()),
                    ),
                )
            }
            if (pares.size > LIMITE_DE_PARES) {
                add(
                    AvisoDto(
                        AvisosDoChecklistReview.LISTA_PARCIAL, AvisoDto.INFORMATIVO, "lista cortada",
                        mapOf("n" to LIMITE_DE_PARES.toString(), "total" to pares.size.toString()),
                    ),
                )
            }
        }

        val dto = ChecklistReviewDto(
            versaoRegras = regras.rotulo,
            resumo = ResumoCRDto(
                pares = stats.pares, semReview = stats.semReview, concluidas = concluidas.size, comAlerta = comAlerta.size,
                porAlerta = porAlerta, cicloReviewP50 = stats.cicloReviewP50, razaoReviewPaiP50 = stats.razaoReviewPaiP50,
                criadaAposPaiHorasP50 = stats.criadaAposPaiHorasP50, venceDepoisPct = stats.venceDepoisPct,
                mesmaPessoaPct = stats.mesmaPessoaPct, comAlertaPct = stats.comAlertaPct,
                duracaoXReabertura = stats.duracaoXReabertura.map { FaixaDuracaoDto(it.faixa, it.n, it.taxaReabertura, it.n < regras.amostraMinima) },
                labels = stats.labels.map { LiftDeLabelDto(it.label, it.n, it.taxaReabertura, it.lift, it.nBaixo) },
                diferencasDeLabels = stats.diferencasDeLabels.map { DiferencaDeLabelDto(it.first, it.second) },
                atrasosEmCadeia = stats.atrasosEmCadeia, aprendizados = stats.aprendizados.map(::aprendizado),
                amostraMinima = regras.amostraMinima, limiteVenceDepois = regras.limiteVenceDepois,
                limiteMesmaPessoa = regras.limiteMesmaPessoa, limiteParesComAlerta = regras.limiteParesComAlerta,
            ),
            pares = pares.take(LIMITE_DE_PARES).map { paraDto(it, e) },
            semReview = semReview.take(LIMITE_SEM_REVIEW).map { SemReviewDto(it.key, it.summary, it.assignee?.takeIf { a -> a in e.escopo.visiveis }, it.status) },
            avisos = avisos,
            unit = if (unit == TimeUnitMode.UTIL) "util" else "corrido",
        )

        val rotulosDeLabel = stats.labels.map { it.label }.toSet()
        val diferencas = stats.diferencasDeLabels.map { it.first }.toSet()

        fun linhas(lista: List<ParCR>) = lista.map { linhaDe(it.pai, e.escopo, detalheDoPar(it)) }
        return ResultadoDeCard(dto) { segmento ->
            when {
                segmento == "pares" -> linhas(pares)
                segmento == "semReview" -> semReview.map { linhaDe(it, e.escopo, "Checklist sem nenhuma review ligada pelo vínculo de bloqueio") }
                segmento == "comAlerta" -> linhas(comAlerta)
                segmento == "concluidas" -> linhas(concluidas)
                segmento == "cadeia" -> linhas(pares.filter { EstatisticasChecklistReview.emCadeia(it, ctx.calendar) })
                segmento.startsWith("alerta:") ->
                    AlertaDoPar.entries.firstOrNull { it.name == segmento.removePrefix("alerta:") }?.let { a -> linhas(pares.filter { a in it.alertas }) }
                segmento.startsWith("faixa:") -> segmento.removePrefix("faixa:").takeIf { it in faixas }?.let { nome ->
                    linhas(concluidas.filter { faixas[EstatisticasChecklistReview.indiceDaFaixa(it, regras)] == nome })
                }
                segmento.startsWith("label:") -> segmento.removePrefix("label:").takeIf { it in rotulosDeLabel }?.let { l ->
                    linhas(pares.filter { l in EstatisticasChecklistReview.labelsDoPar(it) })
                }
                segmento.startsWith("dif:") -> segmento.removePrefix("dif:").takeIf { it in diferencas }?.let { d ->
                    linhas(pares.filter { d in EstatisticasChecklistReview.diferencasDoPar(it) })
                }
                else -> null
            }
        }
    }

    /** Checklist resolvido no período (dia de Manaus); o que ainda está aberto entra sempre (regra do app anterior). */
    private fun noPeriodo(pai: IssueSnapshot, e: EntradaDeCard): Boolean {
        val resolvido = pai.resolved ?: return true
        val dia = e.ctx.calendar.localDate(resolvido)
        val de = e.filtros.de
        val ate = e.filtros.ate
        return (de == null || !dia.isBefore(de)) && (ate == null || !dia.isAfter(ate))
    }

    private fun paraDto(p: ParCR, e: EntradaDeCard): ParCRDto {
        val cal = e.ctx.calendar
        fun visivel(a: String?) = a?.takeIf { it in e.escopo.visiveis }
        return ParCRDto(
            pai = p.pai.key, review = p.review.key, resumoPai = p.pai.summary, resumoReview = p.review.summary,
            responsavelPai = visivel(p.pai.assignee), responsavelReview = visivel(p.review.assignee),
            statusPai = p.pai.status, statusReview = p.review.status,
            vencePai = p.pai.duedate?.toString(), venceReview = p.review.duedate?.toString(),
            criacaoPai = cal.localDate(p.pai.created).toString(), criacaoReview = cal.localDate(p.review.created).toString(),
            resolvidoPai = p.pai.resolved?.let { cal.localDate(it).toString() },
            resolvidoReview = p.review.resolved?.let { cal.localDate(it).toString() },
            horasAtivasReview = p.horasAtivasReview, paiReaberto = p.paiReaberto, alertas = p.alertas.map { it.name }.sorted(),
        )
    }

    private val ALERTA_PT = mapOf(
        AlertaDoPar.REVIEW_VENCE_DEPOIS to "review vence depois do pai",
        AlertaDoPar.PAI_FECHADO_ANTES to "pai fechado antes da review",
        AlertaDoPar.MESMA_PESSOA to "mesma pessoa nos dois",
        AlertaDoPar.REVIEW_CURTA_COM_PAI_REABERTO to "review curta e pai reaberto",
        AlertaDoPar.REVIEW_VENCIDA to "review vencida",
        AlertaDoPar.REVIEW_ATRASADA to "review atrasada",
    )

    /** Texto da linha da gaveta (a gaveta mostra o pai; a review e os alertas vão aqui). */
    private fun detalheDoPar(p: ParCR): String {
        val sinal = if (p.alertas.isEmpty()) "✓" else "▲"
        val alertas = p.alertas.sorted().joinToString("; ") { ALERTA_PT.getValue(it) }.ifEmpty { "sem alerta" }
        val horas = p.horasAtivasReview?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "—"
        return "$sinal Review ${p.review.key} (${p.review.status}): vence ${p.review.duedate ?: "—"} (review) × ${p.pai.duedate ?: "—"} (pai); " +
            "$horas h ativas; $alertas"
    }

    /** `CODIGO|chave=valor|...` (do cálculo) vira o código e os parâmetros do DTO. */
    private fun aprendizado(texto: String): AprendizadoDto {
        val partes = texto.split('|')
        val params = partes.drop(1).mapNotNull { p -> p.indexOf('=').takeIf { it > 0 }?.let { p.substring(0, it) to p.substring(it + 1) } }.toMap()
        return AprendizadoDto(partes.first(), params)
    }

    override fun vazio(avisos: List<AvisoDto>): ChecklistReviewDto {
        val r = RegrasChecklistReview.PADRAO
        return ChecklistReviewDto(
            versaoRegras = r.rotulo,
            resumo = ResumoCRDto(
                pares = 0, semReview = 0, concluidas = 0, comAlerta = 0, porAlerta = AlertaDoPar.entries.associate { it.name to 0 },
                cicloReviewP50 = null, razaoReviewPaiP50 = null, criadaAposPaiHorasP50 = null, venceDepoisPct = null, mesmaPessoaPct = null,
                comAlertaPct = null, duracaoXReabertura = emptyList(), labels = emptyList(), diferencasDeLabels = emptyList(),
                atrasosEmCadeia = 0, aprendizados = emptyList(), amostraMinima = r.amostraMinima, limiteVenceDepois = r.limiteVenceDepois,
                limiteMesmaPessoa = r.limiteMesmaPessoa, limiteParesComAlerta = r.limiteParesComAlerta,
            ),
            pares = emptyList(), semReview = emptyList(), avisos = avisos,
        )
    }
}

/**
 * Os cartões da tela Checklist × Review. A lista de pares da tela usa o cartão `CR-01` (variante lista), como a
 * Divergência usa o `DV-01`. Cada cartão só abre os seus números (segmento de outro cartão é 400).
 */
internal fun cartoesDeChecklistReview(): List<CartaoRegistrado> {
    val fontes = listOf(FonteDoDado.ISSUE, FonteDoDado.CHANGELOG, FonteDoDado.ISSUE_LINK, FonteDoDado.CONFIGURACAO)
    fun meta(id: String, natureza: Natureza, temDrill: Boolean = true) =
        metaDeTrabalho(id, CardChecklistReview.id, GrupoDeCartao.PRAZOS, natureza, fontes, exigeChangelog = true, temDrill = temDrill)
    fun cartao(id: String, natureza: Natureza, aceita: (String) -> Boolean, numeros: (ChecklistReviewDto) -> Map<String, Int>) =
        CartaoDeLeitura(meta(id, natureza), CardChecklistReview, aceita, numeros)
    val alertas = AlertaDoPar.entries.map { it.name }
    return listOf(
        cartao("CR-01", Natureza.ESTOQUE, { it == "pares" }) { mapOf("pares" to it.resumo.pares) },
        cartao("CR-02", Natureza.QUALIDADE, { it == "semReview" }) { mapOf("semReview" to it.resumo.semReview) },
        cartao("CR-03", Natureza.FLUXO, { it == "concluidas" }) { mapOf("concluidas" to it.resumo.concluidas) },
        cartao("CR-04", Natureza.FLUXO, { it == "concluidas" }) { mapOf("concluidas" to it.resumo.concluidas) },
        cartao("CR-05", Natureza.FLUXO, { it == "pares" }) { mapOf("pares" to it.resumo.pares) },
        cartao("CR-06", Natureza.PRAZO, { it == "alerta:REVIEW_VENCE_DEPOIS" }) { mapOf("alerta:REVIEW_VENCE_DEPOIS" to (it.resumo.porAlerta["REVIEW_VENCE_DEPOIS"] ?: 0)) },
        cartao("CR-07", Natureza.QUALIDADE, { it == "alerta:MESMA_PESSOA" }) { mapOf("alerta:MESMA_PESSOA" to (it.resumo.porAlerta["MESMA_PESSOA"] ?: 0)) },
        cartao("CR-08", Natureza.QUALIDADE, { it == "comAlerta" || (it.startsWith("alerta:") && it.removePrefix("alerta:") in alertas) }) { dto ->
            mapOf("comAlerta" to dto.resumo.comAlerta) + alertas.associate { "alerta:$it" to (dto.resumo.porAlerta[it] ?: 0) }
        },
        // CR-10 (aprendizados) é texto: não há tasks por trás.
        CartaoDeLeitura(meta("CR-10", Natureza.QUALIDADE, temDrill = false), CardChecklistReview, { false }) { emptyMap() },
        cartao("CR-11", Natureza.QUALIDADE, { it.startsWith("faixa:") }) { dto -> dto.resumo.duracaoXReabertura.associate { "faixa:${it.faixa}" to it.n } },
        cartao("CR-12", Natureza.PRAZO, { it == "cadeia" }) { mapOf("cadeia" to it.resumo.atrasosEmCadeia) },
        cartao("CR-13", Natureza.QUALIDADE, { it.startsWith("label:") }) { dto -> dto.resumo.labels.associate { "label:${it.label}" to it.n } },
        cartao("CR-14", Natureza.QUALIDADE, { it.startsWith("dif:") }) { dto -> dto.resumo.diferencasDeLabels.associate { "dif:${it.codigo}" to it.pares } },
    )
}

/** `GET /api/cards/checklist-review` (o drill é a rota genérica dos cartões `CR-nn`). */
fun Route.rotasDeChecklistReview(ctx: AppContext, auditoria: AccessLog) {
    rotaDeCard(ctx, SqliteWorkStore(requireNotNull(ctx.dataSource)), auditoria, CardChecklistReview)
}
