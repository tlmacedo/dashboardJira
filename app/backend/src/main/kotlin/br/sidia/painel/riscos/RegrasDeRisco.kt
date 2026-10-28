package br.sidia.painel.riscos

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.nio.file.Files
import java.nio.file.Path

/** Os fatores que somam pontos de risco numa task aberta. [rotulo] é o texto curto (pt-BR) das gavetas. */
enum class FatorDeRisco(val rotulo: String) {
    IDADE_ACIMA_P85("idade acima do P85 do tipo"),
    IDADE_ACIMA_P50("idade acima da mediana do tipo"),
    PARADA("parada no status"),
    VENCIDA("vencida"),
    PREVISAO_APOS_VENCIMENTO("previsão depois do vencimento"),
    VENCE_EM_BREVE("vence em breve"),
    BLOQUEADA("bloqueada"),
    REABERTA("reaberta"),
    MUITAS_TROCAS("muitas trocas de responsável"),
    ESTOURO_DE_HORAS("estouro de horas"),
    SEM_ATUALIZACAO("sem atualização"),
    SEM_ESTIMATIVA("sem estimativa"),
}

/**
 * Pesos e limiares do card Riscos, lidos de `risco.yaml` (versionado, sem dado pessoal; em produção o arquivo real é
 * obrigatório e o `.exemplo` só vale em desenvolvimento). Os pesos são heurísticos; a [versao] sai em toda resposta.
 */
data class RegrasDeRisco(
    val versao: Int,
    val pesos: Map<FatorDeRisco, Int>,
    val alto: Int,
    val medio: Int,
    val venceEmDias: Int,
    val estouro: Double,
    val trocas: Int,
    val diasSemAtualizacao: Int,
) {
    init {
        require(versao == 1) { "versao $versao não suportada (esperado: 1)" }
        val faltando = FatorDeRisco.entries.filter { it !in pesos }
        require(faltando.isEmpty()) { "fator sem peso: ${faltando.joinToString()}" }
        require(pesos.values.all { it >= 0 }) { "peso negativo" }
        require(medio >= 1 && medio < alto) { "medio ($medio) deve ser maior que 0 e menor que alto ($alto)" }
        require(venceEmDias >= 0) { "venceEmDias não pode ser negativo" }
        require(estouro > 1.0) { "estouro deve ser maior que 1" }
        require(trocas >= 1) { "trocas deve ser pelo menos 1" }
        require(diasSemAtualizacao >= 1) { "diasSemAtualizacao deve ser pelo menos 1" }
    }

    /** Nível do total de pontos: `alto`, `medio` ou `baixo`. */
    fun nivel(pontos: Int): String = when {
        pontos >= alto -> "alto"
        pontos >= medio -> "medio"
        else -> "baixo"
    }

    companion object {
        /** Valores do modelo (herdados do app anterior), para os testes que não leem o arquivo. */
        val PADRAO = RegrasDeRisco(
            versao = 1,
            pesos = mapOf(
                FatorDeRisco.IDADE_ACIMA_P85 to 30, FatorDeRisco.IDADE_ACIMA_P50 to 10, FatorDeRisco.PARADA to 15,
                FatorDeRisco.VENCIDA to 25, FatorDeRisco.PREVISAO_APOS_VENCIMENTO to 20, FatorDeRisco.VENCE_EM_BREVE to 8,
                FatorDeRisco.BLOQUEADA to 15, FatorDeRisco.REABERTA to 10, FatorDeRisco.MUITAS_TROCAS to 10,
                FatorDeRisco.ESTOURO_DE_HORAS to 15, FatorDeRisco.SEM_ATUALIZACAO to 10, FatorDeRisco.SEM_ESTIMATIVA to 5,
            ),
            alto = 60, medio = 30, venceEmDias = 3, estouro = 1.25, trocas = 3, diasSemAtualizacao = 7,
        )

        /** Estrito: chave desconhecida, fator desconhecido ou sem peso, versão diferente de 1 ou limiares incoerentes falham citando o arquivo. */
        fun fromYaml(path: Path): RegrasDeRisco {
            val bruto = try {
                Yaml.default.decodeFromString(RegrasDeRiscoYaml.serializer(), Files.readString(path))
            } catch (e: SerializationException) {
                throw IllegalArgumentException("$path: regras de risco inválidas: ${e.message}", e)
            }
            return try {
                val desconhecidos = bruto.pesos.keys.filter { nome -> FatorDeRisco.entries.none { it.name == nome } }
                require(desconhecidos.isEmpty()) { "fator desconhecido: ${desconhecidos.joinToString()}" }
                RegrasDeRisco(
                    bruto.versao, bruto.pesos.mapKeys { (nome, _) -> FatorDeRisco.valueOf(nome) }, bruto.alto, bruto.medio,
                    bruto.venceEmDias, bruto.estouro, bruto.trocas, bruto.diasSemAtualizacao,
                )
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("$path: regras de risco inválidas: ${e.message}", e)
            }
        }
    }
}

@Serializable
private data class RegrasDeRiscoYaml(
    val versao: Int,
    val pesos: Map<String, Int>,
    val alto: Int,
    val medio: Int,
    val venceEmDias: Int,
    val estouro: Double,
    val trocas: Int,
    val diasSemAtualizacao: Int,
)
