package br.sidia.painel.cards.esforco

import br.sidia.painel.cards.AvisoDto
import kotlinx.serialization.Serializable

/**
 * Resposta do card Esforço por task fechada (EF-01 e EF-02). [versaoRegras] = `esforço v<versão>` (a proposta de pesos de
 * `esforco.yaml`, a calibrar). Sem ranking: [pessoas] em ordem alfabética. [periodos] agrupa as concluídas por semana
 * (início na segunda-feira) ou por mês, conforme [granularidade] (`semana` ou `mes`). Segmentos do drill: `tasks` e
 * `pessoa:<id>`.
 */
@Serializable
data class EsforcoDto(
    val versaoRegras: String,
    val tasks: List<EsforcoTaskDto>,
    val pessoas: List<EsforcoPessoaDto>,
    val periodos: List<EsforcoPeriodoDto>,
    val avisos: List<AvisoDto>,
    val granularidade: String = "semana",
)

/**
 * Uma task concluída com os três componentes lado a lado e o [indice]. [horasDoCampo]: as horas vieram do tempo gasto do
 * Jira (a task não tem worklog). [resolvida] é a data de Manaus.
 */
@Serializable
data class EsforcoTaskDto(
    val key: String,
    val resumo: String,
    val tipo: String,
    val resolvida: String,
    val pesoTipo: Double,
    val testes: Int,
    val horas: Double,
    val indice: Double,
    val horasDoCampo: Boolean,
)

/** O esforço de uma pessoa da visão pela POSSE das tasks (componentes × fração de posse); `testes` fica fracionário. */
@Serializable
data class EsforcoPessoaDto(
    val pessoa: String,
    val tasks: Int,
    val pesoTipo: Double,
    val testes: Double,
    val horas: Double,
    val indice: Double,
    val horasDoCampo: Boolean,
)

/** Um período ([inicio] = segunda-feira da semana ou dia 1 do mês): quantas concluídas, as horas e o índice somado. */
@Serializable
data class EsforcoPeriodoDto(val inicio: String, val tasks: Int, val horas: Double, val indice: Double)
