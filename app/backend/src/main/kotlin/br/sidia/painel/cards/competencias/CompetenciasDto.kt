package br.sidia.painel.cards.competencias

import br.sidia.painel.cards.AvisoDto
import kotlinx.serialization.Serializable

/**
 * Resposta do card Matriz de competências (CM-01 a CM-03). Só pessoas da visão (de `equipes.yaml`, em ordem alfabética): o
 * USUARIO recebe só a própria. A matriz é `celulas[i][j]` = a pessoa `pessoas[j]` tem a competência `competencias[i]`
 * (competências em ordem alfabética: as da visão e as pedidas por tasks abertas). [pessoaUnica]: competências que exatamente uma
 * pessoa da visão tem (vazio com menos de 2 pessoas na visão: sozinha toda competência seria "única"). [semNinguem]: pedidas
 * por task aberta (`skill:x`) e que ninguém da visão tem. [demandaH]: horas abertas por competência pedida.
 * Segmentos do drill: `skill:<x>` (as tasks abertas que pedem a competência) e `semNinguem`; [tasksPorCompetencia] e
 * [tasksSemNinguem] são os números das tasks que a gaveta abre (iguais aos tamanhos dos segmentos).
 */
@Serializable
data class CompetenciasDto(
    val competencias: List<String>,
    val pessoas: List<String>,
    val celulas: List<List<Boolean>>,
    val pessoaUnica: List<String>,
    val semNinguem: List<String>,
    val demandaH: Map<String, Double>,
    val avisos: List<AvisoDto>,
    val tasksPorCompetencia: Map<String, Int> = emptyMap(),
    val tasksSemNinguem: Int = 0,
)
