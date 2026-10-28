package br.sidia.painel.cards.negligenciadas

import br.sidia.painel.cards.CardDeLeitura
import br.sidia.painel.cards.CartaoDeLeitura
import br.sidia.painel.cards.EntradaDeCard
import br.sidia.painel.cards.contrato.CartaoRegistrado
import br.sidia.painel.cards.contrato.FonteDoDado
import br.sidia.painel.cards.contrato.GrupoDeCartao
import br.sidia.painel.cards.contrato.Natureza
import br.sidia.painel.cards.metaDeTrabalho
import br.sidia.painel.cards.ErroHttp
import br.sidia.painel.cards.INSTANCIA_TRABALHO
import br.sidia.painel.cards.ResultadoDeCard
import br.sidia.painel.cards.linhaDe
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.domain.TimeUnitMode
import br.sidia.painel.sync.FiltroDeFicha
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.Situacao
import io.ktor.http.HttpStatusCode
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Quantas linhas cada lista leva na resposta (a contagem e a gaveta trazem todas). */
const val LIMITE_DA_LISTA = 100

/** Semanas de histórico que dão a mediana por status (a regra do app atual). */
internal const val SEMANAS_DE_HISTORICO = 16L

/**
 * Card Negligenciadas (NG-01 a NG-06), com o último movimento real (I-06) em cada linha e o filtro `movimento=semReal`
 * ("sem movimento real"): só as issues cujo último movimento real (status, responsável, vencimento, labels ou
 * resolução) tem mais de `diasSemAtualizacao` dias. O filtro vale para as seis listas e para o drill; NÃO substitui
 * o NG-01, que continua pelo `updated` do Jira.
 */
object CardNegligenciadas : CardDeLeitura<NegligenciadasDto> {
    override val id = "negligenciadas"
    override val periodoPadraoDias: Int? = null

    private const val DIAS_SEM_ATUALIZACAO = 7

    private fun semMovimentoReal(e: EntradaDeCard): Boolean = when (e.parametros["movimento"]?.trim()) {
        null, "" -> false
        "semReal" -> true
        else -> throw ErroHttp(HttpStatusCode.BadRequest)
    }

    override fun calcular(e: EntradaDeCard): ResultadoDeCard<NegligenciadasDto> {
        val ctx = e.ctx
        val map = ctx.statusMap
        val cal = ctx.calendar
        val ficha = FiltroDeFicha.de(ctx.fichas.getValue(id))
        val filtroReal = semMovimentoReal(e)

        val abertas = e.store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.ABERTAS, osVersion = e.filtros.os))
            .filter { e.escopo.permiteAtual(it) && e.filtros.aceita(it) && map.category(it.status) != StatusCategory.DONE }
        fun diasSemMovimento(s: IssueSnapshot) = cal.calendarDays(minOf(Fluxo.ultimoMovimentoReal(s).em, e.agora), e.agora)
        val base = if (filtroReal) abertas.filter { diasSemMovimento(it) > DIAS_SEM_ATUALIZACAO } else abertas
        val historico = e.store.snapshots(
            ConsultaDeTrabalho(
                INSTANCIA_TRABALHO, ficha, Situacao.RESOLVIDAS, resolvidasDe = e.agora.minus(Duration.ofDays(SEMANAS_DE_HISTORICO * 7)),
                osVersion = e.filtros.os,
            ),
        ).filter { e.escopo.permiteAtual(it) }

        val listas = NegligenciadasCalculator(cal, map, DIAS_SEM_ATUALIZACAO).calcular(base, historico, e.agora, e.filtros.unit)

        fun linha(s: IssueSnapshot, kind: String): LinhaNegligenciadaDto {
            val mov = Fluxo.ultimoMovimentoReal(s)
            val dias = diasDaLista(s, kind, e)
            val diasMov = cal.calendarDays(minOf(mov.em, e.agora), e.agora)
            return LinhaNegligenciadaDto(
                key = s.key, resumo = s.summary, tipo = s.issueType, status = s.status, responsavel = s.assignee,
                vence = s.duedate?.toString(), labels = s.labels, dias = dias,
                ultimoMovimento = UltimoMovimentoDto(mov.campo, cal.localDate(mov.em).toString(), diasMov),
                semMovimentoReal = diasMov > DIAS_SEM_ATUALIZACAO,
            )
        }
        val dtoListas = listas.mapValues { (kind, l) -> l.take(LIMITE_DA_LISTA).map { linha(it, kind) } }
        val dto = NegligenciadasDto(
            semAtualizacao = listas.getValue(KindsDeNegligenciada.SEM_ATUALIZACAO).size,
            semResponsavel = listas.getValue(KindsDeNegligenciada.SEM_RESPONSAVEL).size,
            vencidas = listas.getValue(KindsDeNegligenciada.VENCIDAS).size,
            bloqueadas = listas.getValue(KindsDeNegligenciada.BLOQUEADAS).size,
            paradasNoStatus = listas.getValue(KindsDeNegligenciada.PARADAS).size,
            aFazerAntigas = listas.getValue(KindsDeNegligenciada.A_FAZER).size,
            listas = dtoListas,
            diasSemAtualizacao = DIAS_SEM_ATUALIZACAO,
            semMovimentoReal = filtroReal,
            unit = if (e.filtros.unit == TimeUnitMode.UTIL) "util" else "corrido",
            avisos = emptyList(),
        )
        return ResultadoDeCard(dto) { segmento ->
            listas[segmento]?.map { s -> linhaDe(s, e.escopo, detalhe(s, segmento, e)) }
        }
    }

    override fun vazio(avisos: List<br.sidia.painel.cards.AvisoDto>) = NegligenciadasDto(
        semAtualizacao = 0, semResponsavel = 0, vencidas = 0, bloqueadas = 0, paradasNoStatus = 0, aFazerAntigas = 0,
        listas = emptyMap(), diasSemAtualizacao = DIAS_SEM_ATUALIZACAO, semMovimentoReal = false, unit = "util", avisos = avisos,
    )

    private fun diasDaLista(s: IssueSnapshot, kind: String, e: EntradaDeCard): Double {
        val cal = e.ctx.calendar
        return when (kind) {
            KindsDeNegligenciada.SEM_ATUALIZACAO -> cal.calendarDays(minOf(s.updated ?: s.created, e.agora), e.agora)
            KindsDeNegligenciada.SEM_RESPONSAVEL -> cal.calendarDays(minOf(s.created, e.agora), e.agora)
            KindsDeNegligenciada.VENCIDAS -> s.duedate?.let { ChronoUnit.DAYS.between(it, cal.localDate(e.agora)).toDouble() } ?: 0.0
            KindsDeNegligenciada.BLOQUEADAS, KindsDeNegligenciada.PARADAS -> Fluxo.diasNoStatusAtual(s, cal, e.filtros.unit, e.agora)
            else -> Fluxo.diasNoStatusAtual(s, cal, TimeUnitMode.CORRIDO, e.agora)
        }
    }

    private fun detalhe(s: IssueSnapshot, kind: String, e: EntradaDeCard): String {
        val dias = String.format(Locale.ROOT, "%.1f", diasDaLista(s, kind, e))
        val unidade = if (kind == KindsDeNegligenciada.PARADAS || kind == KindsDeNegligenciada.BLOQUEADAS) {
            if (e.filtros.unit == TimeUnitMode.UTIL) "dias úteis" else "dias corridos"
        } else {
            "dias corridos"
        }
        val mov = Fluxo.ultimoMovimentoReal(s)
        val dia = e.ctx.calendar.localDate(mov.em)
        return "$dias $unidade; último movimento real: ${mov.campo} em $dia"
    }
}

/** Cartões da tela Negligenciadas (inventário `NG-01` a `NG-06`): cada um conta uma das seis listas. */
internal fun cartoesDeNegligenciadas(): List<CartaoRegistrado> {
    val fontes = listOf(FonteDoDado.ISSUE)
    val comChangelog = listOf(FonteDoDado.ISSUE, FonteDoDado.CHANGELOG)
    fun cartao(n: Int, kind: String, natureza: Natureza, f: List<FonteDoDado>, numero: (NegligenciadasDto) -> Int): CartaoRegistrado =
        CartaoDeLeitura(
            metaDeTrabalho("NG-0$n", CardNegligenciadas.id, GrupoDeCartao.TRABALHO_EM_CURSO, natureza, f, exigeChangelog = FonteDoDado.CHANGELOG in f),
            CardNegligenciadas, { it == kind },
        ) { mapOf(kind to numero(it)) }
    return listOf(
        cartao(1, KindsDeNegligenciada.SEM_ATUALIZACAO, Natureza.FLUXO, fontes) { it.semAtualizacao },
        cartao(2, KindsDeNegligenciada.SEM_RESPONSAVEL, Natureza.QUALIDADE, fontes) { it.semResponsavel },
        cartao(3, KindsDeNegligenciada.VENCIDAS, Natureza.PRAZO, fontes) { it.vencidas },
        cartao(4, KindsDeNegligenciada.BLOQUEADAS, Natureza.FLUXO, fontes) { it.bloqueadas },
        cartao(5, KindsDeNegligenciada.PARADAS, Natureza.FLUXO, comChangelog) { it.paradasNoStatus },
        cartao(6, KindsDeNegligenciada.A_FAZER, Natureza.ESTOQUE, comChangelog) { it.aFazerAntigas },
    )
}
