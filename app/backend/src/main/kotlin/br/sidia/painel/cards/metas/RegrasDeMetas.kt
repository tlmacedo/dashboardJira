package br.sidia.painel.cards.metas

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path

/**
 * Metas do card Metas e prazos, lidas de `metas.yaml` (versionado, sem dado pessoal; em produção o arquivo real é
 * obrigatório e o `.exemplo` só vale em desenvolvimento). [metaNoPrazo] e [metaAntecipadas] são frações de 0 a 1;
 * [alertaDataOriginalPp] e [faixaAtencaoPp] são pontos percentuais.
 */
data class RegrasDeMetas(
    val versao: Int,
    val metaNoPrazo: Double,
    val metaAntecipadas: Double,
    val alertaDataOriginalPp: Double,
    val faixaAtencaoPp: Double,
) {
    init {
        require(versao == 1) { "versao $versao não suportada (esperado: 1)" }
        require(metaNoPrazo > 0.0 && metaNoPrazo <= 1.0) { "metaNoPrazo deve estar entre 0 (exclusivo) e 1" }
        require(metaAntecipadas > 0.0 && metaAntecipadas <= 1.0) { "metaAntecipadas deve estar entre 0 (exclusivo) e 1" }
        require(alertaDataOriginalPp in 0.0..100.0) { "alertaDataOriginalPp deve estar entre 0 e 100" }
        require(faixaAtencaoPp in 0.0..100.0) { "faixaAtencaoPp deve estar entre 0 e 100" }
    }

    companion object {
        /** Valores do modelo, para os testes que não leem o arquivo. */
        val PADRAO = RegrasDeMetas(1, 0.90, 0.35, 5.0, 10.0)

        /** Estrito: chave desconhecida, versão diferente de 1 ou número fora da faixa falham citando o arquivo. */
        fun fromYaml(path: Path): RegrasDeMetas {
            val bruto = try {
                Yaml.default.decodeFromString(RegrasDeMetasYaml.serializer(), Files.readString(path))
            } catch (e: SerializationException) {
                throw IllegalArgumentException("$path: metas inválidas: ${e.message}", e)
            }
            return try {
                RegrasDeMetas(bruto.versao, bruto.metaNoPrazo, bruto.metaAntecipadas, bruto.alertaDataOriginalPp, bruto.faixaAtencaoPp)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("$path: metas inválidas: ${e.message}", e)
            }
        }
    }
}

@Serializable
private data class RegrasDeMetasYaml(
    val versao: Int,
    val metaNoPrazo: Double,
    val metaAntecipadas: Double,
    val alertaDataOriginalPp: Double,
    val faixaAtencaoPp: Double,
)
