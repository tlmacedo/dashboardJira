package br.sidia.painel.pessoas

import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.IssueSnapshot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Ocupação = demanda ÷ capacidade. Capacidade zero NÃO é um número: é [SemCapacidade] (a tela escreve "sem capacidade" e
 * mostra a demanda ao lado), nunca 990% nem divisão por zero.
 */
sealed interface Ocupacao {
    data class Valor(val fracao: Double) : Ocupacao
    data object SemCapacidade : Ocupacao
}

/** Uma semana (chave = segunda-feira) de demanda, capacidade e ocupação de uma pessoa. */
data class SemanaDeCarga(val semana: LocalDate, val demandaH: Double, val capacidadeH: Double, val ocupacao: Ocupacao)

/**
 * A carga de uma pessoa no horizonte: as semanas, o [atrasoAcumuladoH] (horas das issues já vencidas, fora das semanas),
 * se alguma hora foi [horasAssumidas] (confiança menor) e a ocupação do horizonte inteiro.
 */
data class CargaDaPessoa(
    val pessoa: String,
    val semanas: List<SemanaDeCarga>,
    val atrasoAcumuladoH: Double,
    val horasAssumidas: Boolean,
    val ocupacaoHorizonte: Ocupacao,
)

/**
 * Demanda semanal por pessoa. As horas da issue (ver [HorasDaIssue]) se espalham por igual da semana atual até a do
 * vencimento, em pelo menos `semanasMinimasDemanda` semanas; sem vencimento, `semanasSemVencimentoIniciada` ou
 * `semanasSemVencimentoNaoIniciada`. Issue vencida (`duedate < hoje`) não entra nas semanas: soma em atraso acumulado.
 * Status fora do mapa propaga `UnmappedStatusException` (o card o transforma em aviso bloqueante).
 */
class Demanda(
    private val horas: HorasDaIssue,
    private val cap: Capacidade,
    private val map: StatusMap,
    private val regras: RegrasDePlanejamento,
) {
    fun carga(pessoa: String, abertas: List<IssueSnapshot>, hoje: LocalDate, semanas: Int): CargaDaPessoa {
        val segundaDeHoje = segundaDe(hoje)
        val demanda = DoubleArray(semanas)
        var atraso = 0.0
        var assumidas = false
        for (s in abertas) {
            if (s.assignee != pessoa || map.category(s.status) == StatusCategory.DONE) continue
            val h = horas.de(s)
            if (h.assumidas) assumidas = true
            val vence = s.duedate
            if (vence != null && vence.isBefore(hoje)) {
                atraso += h.valor
                continue
            }
            val duracao = when {
                vence != null -> maxOf(ChronoUnit.WEEKS.between(segundaDeHoje, segundaDe(vence)).toInt() + 1, regras.semanasMinimasDemanda)
                Fluxo.inicioAtivo(s, map) != null -> regras.semanasSemVencimentoIniciada
                else -> regras.semanasSemVencimentoNaoIniciada
            }
            val porSemana = h.valor / duracao
            // O que passa do horizonte fica fora das semanas mostradas (o cartão fala do horizonte).
            for (i in 0 until minOf(duracao, semanas)) demanda[i] += porSemana
        }
        val capacidades = cap.semanas(pessoa, hoje, semanas)
        val linhas = capacidades.mapIndexed { i, (segunda, capacidadeH) ->
            SemanaDeCarga(segunda, demanda[i], capacidadeH, ocupacaoDe(demanda[i], capacidadeH))
        }
        val horizonte = ocupacaoDe(demanda.sum(), capacidades.sumOf { it.second })
        return CargaDaPessoa(pessoa, linhas, atraso, assumidas, horizonte)
    }

    private fun ocupacaoDe(demandaH: Double, capacidadeH: Double): Ocupacao =
        if (capacidadeH <= 0.0) Ocupacao.SemCapacidade else Ocupacao.Valor(demandaH / capacidadeH)

    private fun segundaDe(dia: LocalDate): LocalDate = dia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}

/**
 * Índice de Jain da distribuição de ocupação, `(Σx)² ÷ (n × Σx²)`: 1,0 = todos iguais. Ignora [Ocupacao.SemCapacidade];
 * cada valor é limitado a [teto] (um valor extremo não domina o índice); `null` com menos de 2 valores. É um indicador do
 * CONJUNTO, nunca uma nota de pessoa.
 */
object Jain {
    fun indice(ocupacoes: List<Ocupacao>, teto: Double): Double? {
        val x = ocupacoes.filterIsInstance<Ocupacao.Valor>().map { minOf(it.fracao, teto) }
        if (x.size < 2) return null
        val soma = x.sum()
        val quadrados = x.sumOf { it * it }
        return if (quadrados == 0.0) 1.0 else soma * soma / (x.size * quadrados)
    }
}
