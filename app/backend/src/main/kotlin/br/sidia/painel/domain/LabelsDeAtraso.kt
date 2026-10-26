package br.sidia.painel.domain

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

private val RAIZ = Locale.ROOT

/**
 * Labels que justificam um atraso (spec 4.2) e, dentre eles, os que marcam entrega tardia. Sem diferenciar maiúsculas.
 * Cada padrão de [justificativas] é exato ou prefixo com um único `*` no FIM (`ONHOLDBY-*`); `*` sozinho, no início
 * ou no meio (`*X`, `A*B`) é inválido. Cada [tardias] é um label exato que também precisa ser justificativa.
 * Falhas de validação são [IllegalArgumentException]. Config versionada, sem dado pessoal.
 */
class LabelsDeAtraso(val versao: Int, justificativas: List<String>, tardias: Set<String>) {
    private val exatos: Set<String>
    private val prefixos: List<String>
    private val tardiasNormalizadas: Set<String>

    init {
        val exatosTmp = HashSet<String>()
        val prefixosTmp = ArrayList<String>()
        for (bruto in justificativas) {
            val padrao = bruto.trim()
            require(padrao.isNotEmpty()) { "justificativas: item vazio" }
            val estrelas = padrao.count { it == '*' }
            require(estrelas == 0 || (estrelas == 1 && padrao.endsWith('*') && padrao.length > 1)) {
                "justificativas: padrão '$padrao' inválido ('*' só é aceito no fim, depois de um prefixo)"
            }
            if (estrelas == 0) exatosTmp += padrao.lowercase(RAIZ) else prefixosTmp += padrao.dropLast(1).lowercase(RAIZ)
        }
        exatos = exatosTmp
        prefixos = prefixosTmp
        tardiasNormalizadas = tardias.map { bruto ->
            val label = bruto.trim()
            require(label.isNotEmpty() && '*' !in label) { "tardias: item '$label' inválido (label exato, sem '*')" }
            require(casa(label)) { "tardias: '$label' precisa estar coberto por justificativas" }
            label.lowercase(RAIZ)
        }.toSet()
    }

    private fun casa(label: String): Boolean {
        val l = label.trim().lowercase(RAIZ)
        return l in exatos || prefixos.any { l.startsWith(it) }
    }

    /** O label justifica atraso: igual a uma justificativa ou começa com o prefixo de um padrão `PREFIXO*`. */
    fun justifica(label: String): Boolean = casa(label)

    /** O label marca entrega tardia (`LATE_SENT`, `LATE_REVIEW` no modelo). */
    fun ehTardia(label: String): Boolean = label.trim().lowercase(RAIZ) in tardiasNormalizadas

    companion object {
        /** Estrito: chave desconhecida, versão diferente de 1 ou padrão inválido falham citando o arquivo. */
        fun fromYaml(path: Path): LabelsDeAtraso {
            val bruto = try {
                Yaml.default.decodeFromString(LabelsDeAtrasoYaml.serializer(), Files.readString(path))
            } catch (e: SerializationException) {
                throw IllegalArgumentException("$path: labels de atraso inválidos: ${e.message}", e)
            }
            require(bruto.versao == 1) { "$path: labels de atraso inválidos: versao ${bruto.versao} não suportada (esperado: 1)" }
            return try {
                LabelsDeAtraso(bruto.versao, bruto.justificativas, bruto.tardias.toSet())
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("$path: labels de atraso inválidos: ${e.message}", e)
            }
        }
    }
}

@Serializable
private data class LabelsDeAtrasoYaml(
    val versao: Int,
    val justificativas: List<String>,
    val tardias: List<String> = emptyList(),
)
