package br.sidia.painel.previsao

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Parâmetros da Previsão (Monte Carlo) e da previsão por épico, lidos de `previsao.yaml` (versionado, sem dado pessoal;
 * em produção o arquivo real é obrigatório e o `.exemplo` só vale em desenvolvimento). A [versao] sai em toda resposta.
 */
data class RegrasDePrevisao(
    val versao: Int,
    val semanasHistorico: Int,
    val sorteios: Int,
    val limiteSemanas: Int,
    val minSemanasConfianca: Int,
    val comChegada: Boolean,
    val minItensEpico: Int,
) {
    init {
        require(versao == 1) { "versao $versao não suportada (esperado: 1)" }
        require(semanasHistorico in 1..260) { "semanasHistorico deve estar entre 1 e 260" }
        require(sorteios in 100..1_000_000) { "sorteios deve estar entre 100 e 1000000" }
        require(limiteSemanas in 1..520) { "limiteSemanas deve estar entre 1 e 520" }
        require(minSemanasConfianca in 1..semanasHistorico) { "minSemanasConfianca deve estar entre 1 e semanasHistorico" }
        require(minItensEpico >= 1) { "minItensEpico deve ser pelo menos 1" }
    }

    /** `baixa` com menos de [minSemanasConfianca] semanas com dado, `media` até completar o histórico, `alta` com todas. */
    fun confianca(semanasComDado: Int): String = when {
        semanasComDado < minSemanasConfianca -> "baixa"
        semanasComDado < semanasHistorico -> "media"
        else -> "alta"
    }

    companion object {
        /** Valores do modelo, para os testes que não leem o arquivo. */
        val PADRAO = RegrasDePrevisao(1, 16, 10_000, 104, 8, true, 5)

        /** Estrito: chave desconhecida, versão diferente de 1 ou valor fora da faixa falham citando o arquivo. */
        fun fromYaml(path: Path): RegrasDePrevisao {
            val bruto = try {
                Yaml.default.decodeFromString(RegrasDePrevisaoYaml.serializer(), Files.readString(path))
            } catch (e: SerializationException) {
                throw IllegalArgumentException("$path: regras de previsão inválidas: ${e.message}", e)
            }
            return try {
                RegrasDePrevisao(
                    bruto.versao, bruto.semanasHistorico, bruto.sorteios, bruto.limiteSemanas, bruto.minSemanasConfianca,
                    bruto.comChegada, bruto.minItensEpico,
                )
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("$path: regras de previsão inválidas: ${e.message}", e)
            }
        }
    }
}

@Serializable
private data class RegrasDePrevisaoYaml(
    val versao: Int,
    val semanasHistorico: Int,
    val sorteios: Int,
    val limiteSemanas: Int,
    val minSemanasConfianca: Int,
    val comChegada: Boolean,
    val minItensEpico: Int,
)
