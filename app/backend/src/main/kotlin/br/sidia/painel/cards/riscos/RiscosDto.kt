package br.sidia.painel.cards.riscos

import br.sidia.painel.cards.AvisoDto
import kotlinx.serialization.Serializable

/**
 * Resposta do card Riscos (RI-01 a RI-03). Cada número abre, pelo drill genérico, as MESMAS tasks que o formam
 * (segmentos: `nivel:alto`, `nivel:medio`, `nivel:baixo`, `emRisco`, `pessoa:<id>:emRisco`, `pessoa:<id>:abertas`).
 * [linhas] traz só as tasks de nível alto e médio (até 200); o nível baixo existe no drill para conferência.
 * [versaoRegras] identifica a versão dos pesos (`risco.yaml`) usados no cálculo.
 */
@Serializable
data class RiscosDto(
    val versaoRegras: String,
    val alto: Int,
    val medio: Int,
    val baixo: Int,
    /** `alto + medio`: as tasks da lista. */
    val emRisco: Int,
    val limiteAlto: Int,
    val limiteMedio: Int,
    val linhas: List<LinhaRiscoDto>,
    val sinaisPorPessoa: List<SinaisPessoaDto>,
    val avisos: List<AvisoDto>,
)

/** Um fator e os pontos que ele somou (o código é traduzido pela tela). */
@Serializable
data class FatorPontosDto(val fator: String, val pontos: Int)

/** Uma task em risco: [responsavel] é nulo quando a pessoa está fora da visão de quem pediu. */
@Serializable
data class LinhaRiscoDto(
    val key: String,
    val resumo: String,
    val tipo: String,
    val status: String,
    val responsavel: String?,
    val pontos: Int,
    val nivel: String,
    /** O fator de mais pontos (empate: o primeiro da ordem dos fatores). */
    val fatorPrincipal: String,
    val fatores: List<FatorPontosDto>,
)

/** Proporção, não ranking: issues em risco e abertas da pessoa (ordem alfabética, só quem está na visão). */
@Serializable
data class SinaisPessoaDto(val pessoa: String, val issuesEmRisco: Int, val abertas: Int)
