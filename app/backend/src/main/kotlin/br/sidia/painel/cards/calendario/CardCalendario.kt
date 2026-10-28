package br.sidia.painel.cards.calendario

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
import br.sidia.painel.cards.dataOuNulo
import br.sidia.painel.cards.linhaDe
import br.sidia.painel.cards.metaDeTrabalho
import br.sidia.painel.pessoas.MotorDePessoas
import br.sidia.painel.sync.FiltroDeFicha
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.Situacao
import io.ktor.http.HttpStatusCode
import java.time.DayOfWeek
import java.util.Locale
import java.time.temporal.TemporalAdjusters

/**
 * Card Calendário do time: semana (padrão) ou mês, a partir de `inicio` (padrão: a segunda-feira da semana atual). Todos os
 * papéis veem; o USUARIO recebe só a própria linha e a capacidade só dele. Filtros que valem: `squad` e `pessoa`.
 */
object CardCalendario : CardDeLeitura<CalendarioDto> {
    override val id = "calendario"
    override val periodoPadraoDias: Int? = null

    override fun calcular(e: EntradaDeCard): ResultadoDeCard<CalendarioDto> {
        val ctx = e.ctx
        val motor = MotorDePessoas(ctx)
        val inicio = e.parametros.dataOuNulo("inicio") ?: e.hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val modo = when (e.parametros["modo"]?.trim()?.lowercase(Locale.ROOT)) {
            null, "", "semana" -> ModoCalendario.SEMANA
            "mes" -> ModoCalendario.MES
            else -> throw ErroHttp(HttpStatusCode.BadRequest)
        }
        val ficha = FiltroDeFicha.de(ctx.fichas.getValue(id))
        val abertas = e.store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.ABERTAS)).filter { e.escopo.permiteAtual(it) }
        Fluxo.exigirStatusMapeados(ctx.statusMap, abertas)

        val pessoas = motor.pessoas().filter { it in e.escopo.visiveis }
        val analise = CalendarioCalculator(motor).calcular(pessoas, abertas, inicio, modo)
        return ResultadoDeCard(analise.dto) { segmento ->
            analise.segmentos[segmento]?.map { s -> linhaDe(s, e.escopo, "Vence em ${s.duedate}") }
        }
    }

    override fun vazio(avisos: List<AvisoDto>) = CalendarioDto(inicio = "", dias = emptyList(), linhas = emptyList(), avisos = avisos)
}

/** Cartão da tela Calendário do time (`CL-01`): cada dia abre as tasks que vencem nele. */
internal fun cartoesDeCalendario(): List<CartaoRegistrado> = listOf(
    CartaoDeLeitura(
        metaDeTrabalho(
            "CL-01", CardCalendario.id, GrupoDeCartao.PESSOAS_E_PLANEJAMENTO, Natureza.PRAZO,
            listOf(FonteDoDado.ISSUE, FonteDoDado.CONFIGURACAO),
        ),
        CardCalendario, { it.startsWith("dia:") },
    ) { dto -> dto.dias.associate { "dia:${it.dia}" to it.vencimentos } },
)
