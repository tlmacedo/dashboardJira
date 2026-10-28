package br.sidia.painel.cards.simulador

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.CardDeLeitura
import br.sidia.painel.cards.CartaoDeLeitura
import br.sidia.painel.cards.EntradaDeCard
import br.sidia.painel.cards.ErroHttp
import br.sidia.painel.cards.INSTANCIA_TRABALHO
import br.sidia.painel.cards.ResultadoDeCard
import br.sidia.painel.cards.capacidade.SO_LIDERANCA
import br.sidia.painel.cards.contrato.CartaoRegistrado
import br.sidia.painel.cards.contrato.FonteDoDado
import br.sidia.painel.cards.contrato.GrupoDeCartao
import br.sidia.painel.cards.contrato.Natureza
import br.sidia.painel.cards.linhaDe
import br.sidia.painel.cards.metaDeTrabalho
import br.sidia.painel.pessoas.MotorDePessoas
import br.sidia.painel.sync.FiltroDeFicha
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.Situacao
import io.ktor.http.HttpStatusCode
import java.time.temporal.ChronoUnit

/** Código do aviso: o simulador ainda não recebeu a pessoa e o período (a tela mostra só o formulário). */
const val AVISO_SIMULACAO_SEM_PARAMETROS = "SIMULACAO_SEM_PARAMETROS"

/**
 * Simulador de ausência: `GET /api/cards/simulador?pessoa=&de=&ate=&fracao=0.5|1.0`. Só liderança; pessoa fora da visão é 403.
 * É SÓ LEITURA: compara a agenda com e sem a ausência hipotética, em memória, e não grava nada (nem ausência, nem consulta além
 * do `access_log`). Valida tudo com 400 sem eco do valor: período de no máximo 60 dias, `de` até `ate`, fim não anterior a hoje,
 * início até [HORIZONTE_DA_AGENDA_DIAS] dias à frente (além disso a agenda não enxerga) e pessoa com cadastro em `equipes.yaml`.
 * Sem `pessoa`, devolve a resposta vazia com o aviso [AVISO_SIMULACAO_SEM_PARAMETROS] (o formulário).
 */
object CardSimulador : CardDeLeitura<SimuladorDto> {
    override val id = "simulador"
    override val periodoPadraoDias: Int? = null

    private const val MAXIMO_DE_DIAS = 60L
    private const val HORIZONTE_DA_AGENDA_DIAS = 120L

    override fun calcular(e: EntradaDeCard): ResultadoDeCard<SimuladorDto> {
        val pessoa = e.filtros.pessoa
            ?: return ResultadoDeCard(
                vazio(listOf(AvisoDto(AVISO_SIMULACAO_SEM_PARAMETROS, AvisoDto.INFORMATIVO, "informe a pessoa e o período"))),
            ) { emptyList() }
        val de = e.filtros.de ?: throw ErroHttp(HttpStatusCode.BadRequest)
        val ate = e.filtros.ate ?: throw ErroHttp(HttpStatusCode.BadRequest)
        if (ChronoUnit.DAYS.between(de, ate) + 1 > MAXIMO_DE_DIAS) throw ErroHttp(HttpStatusCode.BadRequest)
        if (ate.isBefore(e.hoje) || de.isAfter(e.hoje.plusDays(HORIZONTE_DA_AGENDA_DIAS))) throw ErroHttp(HttpStatusCode.BadRequest)
        val fracao = fracaoDe(e.parametros["fracao"])

        val ctx = e.ctx
        val motor = MotorDePessoas(ctx)
        if (motor.equipes.de(pessoa) == null) throw ErroHttp(HttpStatusCode.BadRequest)
        val ficha = FiltroDeFicha.de(ctx.fichas.getValue(id))
        val abertas = e.store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.ABERTAS)).filter { e.escopo.permiteAtual(it) }
        Fluxo.exigirStatusMapeados(ctx.statusMap, abertas)

        val analise = SimuladorCalculator(motor).simular(pessoa, abertas, e.hoje, de, ate, fracao)
        return ResultadoDeCard(analise.dto) { segmento ->
            if (segmento != "atrasosCausados") null
            else analise.atrasos.map { s -> linhaDe(s, e.escopo, "Passaria a atrasar; vence em ${s.duedate}") }
        }
    }

    /** `0.5` ou `1.0` (ou `1`); ausente vale `1.0`; qualquer outro valor é 400. */
    private fun fracaoDe(bruto: String?): Double {
        val texto = bruto?.trim()?.takeIf { it.isNotEmpty() } ?: return 1.0
        return when (texto) {
            "0.5" -> 0.5
            "1", "1.0" -> 1.0
            else -> throw ErroHttp(HttpStatusCode.BadRequest)
        }
    }

    override fun vazio(avisos: List<AvisoDto>) = SimuladorDto(
        pessoa = "", de = "", ate = "", fracao = 1.0, atrasosCausados = emptyList(), horasAReplanejar = 0.0, semanas = emptyList(), avisos = avisos,
    )
}

/** Cartão do Simulador (`SI-01`): as tasks que passam a atrasar. Sem a pessoa e o período o número é zero. */
internal fun cartoesDeSimulador(): List<CartaoRegistrado> = listOf(
    CartaoDeLeitura(
        metaDeTrabalho(
            "SI-01", CardSimulador.id, GrupoDeCartao.PESSOAS_E_PLANEJAMENTO, Natureza.PRAZO,
            listOf(FonteDoDado.ISSUE, FonteDoDado.CONFIGURACAO), papeis = SO_LIDERANCA,
        ),
        CardSimulador, { it == "atrasosCausados" },
    ) { dto -> mapOf("atrasosCausados" to dto.atrasosCausados.size) },
)
