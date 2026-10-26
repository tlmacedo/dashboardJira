package br.sidia.painel.auditoria

import br.sidia.painel.sync.VinculosConfig
import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

/** Como contar dias nas regras de proximidade: corridos ou úteis (calendário). */
enum class UnidadeDias { CORRIDO, UTIL }

/** Regra de um sinal: gravidade de 1 a 3 e se ele está ligado. */
data class RegraSinal(val gravidade: Int, val ativo: Boolean = true)

/**
 * Regras da auditoria, lidas de `auditoria.yaml` (versionado, sem dado pessoal). [tiposReview] tem uma fonte só:
 * o `vinculos.yaml` (o `auditoria.yaml` não tem essa chave). Limites: `pertoDoVencimentoDias` e
 * `posseAntesDoVencimentoDias` de 0 a 30, `passaBolaTrocas` de 2 a 10, gravidade de 1 a 3; todo [CodigoSinal]
 * precisa de regra. Violações são [IllegalArgumentException] citando a chave.
 */
data class RegrasDeAuditoria(
    val versao: Int,
    val pertoDoVencimentoDias: Int,
    val posseAntesDoVencimentoDias: Int,
    val passaBolaTrocas: Int,
    val unidade: UnidadeDias,
    val statusCancelamento: Set<String>,
    val tiposReview: Set<String>,
    val sinais: Map<CodigoSinal, RegraSinal>,
) {
    init {
        require(pertoDoVencimentoDias in 0..30) { "pertoDoVencimentoDias $pertoDoVencimentoDias fora de 0..30" }
        require(posseAntesDoVencimentoDias in 0..30) { "posseAntesDoVencimentoDias $posseAntesDoVencimentoDias fora de 0..30" }
        require(passaBolaTrocas in 2..10) { "passaBolaTrocas $passaBolaTrocas fora de 2..10" }
        require(statusCancelamento.isNotEmpty() && statusCancelamento.none { it.isBlank() }) {
            "statusCancelamento deve ter ao menos um status, sem itens vazios"
        }
        for (codigo in CodigoSinal.entries) {
            val regra = sinais[codigo] ?: throw IllegalArgumentException("sinais: falta a regra do código $codigo")
            require(regra.gravidade in 1..3) { "sinais.$codigo: gravidade ${regra.gravidade} fora de 1..3" }
        }
    }

    companion object {
        /**
         * Estrito: chave desconhecida (inclusive `tiposReview`), código de sinal desconhecido ou ausente, `unidade`
         * inválida, versão diferente de 1 ou limite fora da faixa falham citando o arquivo e a chave.
         */
        fun fromYaml(path: Path, vinculos: VinculosConfig): RegrasDeAuditoria {
            fun invalido(motivo: String, causa: Throwable? = null): Nothing =
                throw IllegalArgumentException("$path: regras de auditoria inválidas: $motivo", causa)
            val bruto = try {
                Yaml.default.decodeFromString(RegrasYaml.serializer(), Files.readString(path))
            } catch (e: SerializationException) {
                invalido(e.message.orEmpty(), e)
            }
            if (bruto.versao != 1) invalido("versao ${bruto.versao} não suportada (esperado: 1)")
            val unidade = when (bruto.unidade.trim().lowercase(Locale.ROOT)) {
                "corrido" -> UnidadeDias.CORRIDO
                "util" -> UnidadeDias.UTIL
                else -> invalido("unidade '${bruto.unidade}' desconhecida (esperado: corrido ou util)")
            }
            val sinais = bruto.sinais.entries.associate { (nome, regra) ->
                val codigo = CodigoSinal.entries.firstOrNull { it.name == nome } ?: invalido("sinais: código desconhecido '$nome'")
                codigo to RegraSinal(regra.gravidade, regra.ativo)
            }
            return try {
                RegrasDeAuditoria(
                    versao = bruto.versao,
                    pertoDoVencimentoDias = bruto.pertoDoVencimentoDias,
                    posseAntesDoVencimentoDias = bruto.posseAntesDoVencimentoDias,
                    passaBolaTrocas = bruto.passaBolaTrocas,
                    unidade = unidade,
                    statusCancelamento = bruto.statusCancelamento.toSet(),
                    tiposReview = vinculos.tiposReview,
                    sinais = sinais,
                )
            } catch (e: IllegalArgumentException) {
                invalido(e.message.orEmpty(), e)
            }
        }
    }
}

@Serializable
private data class RegraSinalYaml(val gravidade: Int, val ativo: Boolean = true)

@Serializable
private data class RegrasYaml(
    val versao: Int,
    val pertoDoVencimentoDias: Int,
    val posseAntesDoVencimentoDias: Int,
    val passaBolaTrocas: Int,
    val unidade: String,
    val statusCancelamento: List<String>,
    val sinais: Map<String, RegraSinalYaml>,
)
