package br.sidia.painel.cards

import br.sidia.painel.pessoas.MotorDePessoas
import br.sidia.painel.pessoas.Ocupacao
import br.sidia.painel.pessoas.RegrasDePlanejamento
import br.sidia.painel.trabalho.IssueSnapshot
import kotlinx.serialization.Serializable

/**
 * Ocupação para a tela: [fracao] nula e [semCapacidade] verdadeiro quando a capacidade é zero (a tela escreve "sem
 * capacidade", nunca uma porcentagem). [estado] = `ok`, `atencao` ou `problema` (símbolo e texto na tela, nunca só cor).
 */
@Serializable
data class OcupacaoDto(val fracao: Double?, val semCapacidade: Boolean, val estado: String)

/**
 * Converte a ocupação. Acima de `ocupacaoAlta` é problema, de `ocupacaoAtencao` até lá é atenção; sem capacidade é
 * problema quando há demanda ([demandaH] maior que zero) e ok quando não há trabalho a fazer.
 */
fun Ocupacao.paraDto(regras: RegrasDePlanejamento, demandaH: Double = 0.0): OcupacaoDto = when (this) {
    is Ocupacao.Valor -> OcupacaoDto(
        fracao = fracao, semCapacidade = false,
        estado = when {
            fracao > regras.ocupacaoAlta -> "problema"
            fracao >= regras.ocupacaoAtencao -> "atencao"
            else -> "ok"
        },
    )
    Ocupacao.SemCapacidade -> OcupacaoDto(fracao = null, semCapacidade = true, estado = if (demandaH > 0.0) "problema" else "ok")
}

/** Códigos de aviso dos cards de pessoas e planejamento (o texto sai de `cardpage.aviso.<CODIGO>` no frontend). */
object AvisosDePessoas {
    /** Há tasks abertas de pessoas que não constam em `equipes.yaml`: elas não entram na capacidade. */
    const val PESSOAS_SEM_CADASTRO_NA_EQUIPE = "PESSOAS_SEM_CADASTRO_NA_EQUIPE"

    /** Parte das horas veio da regra por tipo ou do padrão (sem restante nem estimativa no Jira): a confiança é menor. */
    const val HORAS_ASSUMIDAS = "HORAS_ASSUMIDAS"

    /** A Tendência usa as horas de hoje no passado (não há retrato semanal gravado). */
    const val HORAS_ATUAIS_NO_PASSADO = "HORAS_ATUAIS_NO_PASSADO"
}

/**
 * Avisos comuns dos cards de capacidade: pessoas com task e sem cadastro na equipe (só a contagem, sem nomes) e horas
 * assumidas ([algumaHoraAssumida]). Ambos informativos: o número continua na tela.
 */
internal fun avisosDePessoas(motor: MotorDePessoas, abertas: List<IssueSnapshot>, algumaHoraAssumida: Boolean): List<AvisoDto> {
    val avisos = mutableListOf<AvisoDto>()
    val semCadastro = abertas.mapNotNull { it.assignee }.filter { motor.equipes.de(it) == null }.distinct()
    if (semCadastro.isNotEmpty()) {
        avisos += AvisoDto(
            AvisosDePessoas.PESSOAS_SEM_CADASTRO_NA_EQUIPE, AvisoDto.INFORMATIVO, "pessoas com tasks e sem cadastro na equipe",
            mapOf("n" to semCadastro.size.toString()),
        )
    }
    if (algumaHoraAssumida) {
        avisos += AvisoDto(AvisosDePessoas.HORAS_ASSUMIDAS, AvisoDto.INFORMATIVO, "parte das horas é assumida pela regra por tipo")
    }
    return avisos
}
