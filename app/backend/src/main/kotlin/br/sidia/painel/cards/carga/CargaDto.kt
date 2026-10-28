package br.sidia.painel.cards.carga

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.OcupacaoDto
import kotlinx.serialization.Serializable

/**
 * Resposta do card Carga por pessoa (CP-01 a CP-03). Sem ranking: [linhas] em ordem alfabética, e no lugar de "maior e
 * menor ocupação" há duas contagens ([acimaDaCapacidade] e [comFolga]) sobre as pessoas da visão. [jain] é o índice do
 * conjunto visível e fica nulo com menos de 2 pessoas com capacidade (um USUARIO nunca recebe agregado que revele colegas).
 * [tasksAcimaDaCapacidade] e [tasksComFolga] são as tasks na fila dessas pessoas: o que a gaveta dos cartões abre.
 * Segmentos do drill: `acimaDaCapacidade`, `comFolga` e `pessoa:<id>:<fila|wip|vencidas|acimaDoP85|riscoAlto>`.
 */
@Serializable
data class CargaDto(
    val versaoRegras: String,
    val jain: Double?,
    val acimaDaCapacidade: Int,
    val comFolga: Int,
    val tasksAcimaDaCapacidade: Int,
    val tasksComFolga: Int,
    val linhas: List<LinhaCargaDto>,
    val avisos: List<AvisoDto>,
)

/**
 * Uma pessoa. [ocupacao] é a do horizonte de 4 semanas; [atrasoAcumuladoH] são as horas das tasks já vencidas (fora da
 * ocupação); [horasAssumidas] diz que parte das horas veio da regra por tipo ou do padrão. [horasRegistradas30d] fica nulo
 * até o worklog ser sincronizado.
 */
@Serializable
data class LinhaCargaDto(
    val pessoa: String,
    val abertas: Int,
    val wip: Int,
    val filaH: Double,
    val capacidade4SemH: Double,
    val ocupacao: OcupacaoDto,
    val ausenciaDias: Double,
    val horasRegistradas30d: Double?,
    val acimaDoP85: Int,
    val vencidas: Int,
    val riscoAlto: Int,
    val atrasoAcumuladoH: Double,
    val horasAssumidas: Boolean,
)
