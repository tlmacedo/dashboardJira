package br.sidia.painel.domain

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

private val RAIZ = Locale.ROOT

/** Conjunto de padrões de label: cada um é exato ou prefixo com um único `*` no FIM, sem diferenciar maiúsculas. */
private class PadroesDeLabel(campo: String, padroes: List<String>) {
    private val exatos = HashSet<String>()
    private val prefixos = ArrayList<String>()

    init {
        for (bruto in padroes) {
            val padrao = bruto.trim()
            require(padrao.isNotEmpty()) { "$campo: item vazio" }
            val estrelas = padrao.count { it == '*' }
            require(estrelas == 0 || (estrelas == 1 && padrao.endsWith('*') && padrao.length > 1)) {
                "$campo: padrão '$padrao' inválido ('*' só é aceito no fim, depois de um prefixo)"
            }
            if (estrelas == 0) exatos += padrao.lowercase(RAIZ) else prefixos += padrao.dropLast(1).lowercase(RAIZ)
        }
    }

    fun casa(label: String): Boolean {
        val l = label.trim().lowercase(RAIZ)
        return l in exatos || prefixos.any { l.startsWith(it) }
    }
}

/**
 * Labels que justificam um atraso (spec 4.2) e, dentre eles, os que marcam entrega tardia. Sem diferenciar maiúsculas.
 * Cada padrão de [justificativas] é exato ou prefixo com um único `*` no FIM (`ONHOLDBY-*`); `*` sozinho, no início
 * ou no meio (`*X`, `A*B`) é inválido. Cada [tardias] é um label exato que também precisa ser justificativa.
 * [ignoradas] (opcional) são os labels que o gestor já conhece e que NÃO justificam atraso (por exemplo `skill:*`): a Qualidade
 * dos dados só aponta como "sem classificação" o label que não é justificativa nem está ignorado (mesma sintaxe dos padrões).
 * Falhas de validação são [IllegalArgumentException]. Config versionada, sem dado pessoal.
 */
class LabelsDeAtraso(val versao: Int, justificativas: List<String>, tardias: Set<String>, ignoradas: List<String> = emptyList()) {
    private val deJustificativa = PadroesDeLabel("justificativas", justificativas)
    private val deIgnorada = PadroesDeLabel("ignoradas", ignoradas)
    private val tardiasNormalizadas: Set<String>

    init {
        tardiasNormalizadas = tardias.map { bruto ->
            val label = bruto.trim()
            require(label.isNotEmpty() && '*' !in label) { "tardias: item '$label' inválido (label exato, sem '*')" }
            require(deJustificativa.casa(label)) { "tardias: '$label' precisa estar coberto por justificativas" }
            label.lowercase(RAIZ)
        }.toSet()
    }

    /** O label justifica atraso: igual a uma justificativa ou começa com o prefixo de um padrão `PREFIXO*`. */
    fun justifica(label: String): Boolean = deJustificativa.casa(label)

    /** O label marca entrega tardia (`LATE_SENT`, `LATE_REVIEW` no modelo). */
    fun ehTardia(label: String): Boolean = label.trim().lowercase(RAIZ) in tardiasNormalizadas

    /** O gestor marcou o label como conhecido (não justifica atraso, mas não precisa de classificação). */
    fun ignora(label: String): Boolean = deIgnorada.casa(label)

    /** O label já tem lugar no vocabulário: justifica atraso ou está na lista de ignoradas. */
    fun classificada(label: String): Boolean = justifica(label) || ignora(label)

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
                LabelsDeAtraso(bruto.versao, bruto.justificativas, bruto.tardias.toSet(), bruto.ignoradas)
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
    val ignoradas: List<String> = emptyList(),
)
