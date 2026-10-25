package br.sidia.painel.jql

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path

/** Tipos de campo do Jira que o construtor de JQL sabe tratar. */
enum class FieldType { SELECT, TEXT, DATE, USER, LABELS, NUMBER }

/**
 * Definição de um campo do Jira: só vocabulário e classificação, sem consulta ao Jira.
 * [customId] (ex.: `customfield_12345`) fica nulo até ser descoberto por nome numa conexão autorizada.
 */
class FieldDef(
    val name: String,
    val type: FieldType,
    val instance: String,
    val purpose: String,
    knownValues: List<String> = emptyList(),
    val customId: String? = null,
) {
    /** Valores conhecidos (vocabulário). Lista vazia significa texto livre, sem vocabulário fechado. */
    val knownValues: List<String> = java.util.List.copyOf(knownValues)

    init {
        require(name.isNotBlank()) { "Campo sem nome" }
        require(instance.isNotBlank()) { "Campo '$name' sem instância" }
        require(purpose.isNotBlank()) { "Campo '$name' sem finalidade" }
        require(customId == null || FORMATO_ID.matches(customId)) {
            "Campo '$name': customId '$customId' fora do formato customfield_NNNNN ou cf[NNNNN]"
        }
        require(this.knownValues.none { it.isBlank() }) { "Campo '$name' com valor conhecido em branco" }
        require(this.knownValues.toSet().size == this.knownValues.size) { "Campo '$name' com valores conhecidos repetidos" }
    }

    override fun equals(other: Any?): Boolean =
        other is FieldDef && name == other.name && type == other.type && instance == other.instance &&
            purpose == other.purpose && knownValues == other.knownValues && customId == other.customId

    override fun hashCode(): Int = listOf(name, type, instance, purpose, knownValues, customId).hashCode()

    override fun toString(): String = "FieldDef(name=$name, type=$type, instance=$instance, customId=$customId)"

    private companion object {
        val FORMATO_ID = Regex("""customfield_\d+|cf\[\d+]""")
    }
}

/** Campo pedido que não consta no dicionário. */
class UnknownFieldException(val name: String) : RuntimeException("Campo fora do dicionário: $name")

/**
 * Dicionário de campos do Jira. Nomes são únicos por instância, sem diferenciar maiúsculas
 * (como no JQL); o mesmo nome pode existir em instâncias diferentes. Nesse caso a API de um
 * argumento recusa a consulta ambígua: use [forInstance] antes.
 */
class FieldDictionary(fields: List<FieldDef>) {
    private val campos: List<FieldDef> = fields.toList()
    private val porNome: Map<String, List<FieldDef>>

    init {
        val vistos = mutableSetOf<Pair<String, String>>()
        campos.forEach { f ->
            require(vistos.add(f.instance to f.name.lowercase())) {
                "Campo duplicado no dicionário: '${f.name}' na instância '${f.instance}'"
            }
        }
        porNome = campos.groupBy { it.name.lowercase() }
    }

    /** Dicionário só com os campos da instância (vazio se ela não tiver campos). */
    fun forInstance(instance: String): FieldDictionary = FieldDictionary(campos.filter { it.instance == instance })

    private fun achar(name: String): FieldDef? {
        val achados = porNome[name.lowercase()] ?: return null
        check(achados.size == 1) {
            "Campo '$name' existe em mais de uma instância (${achados.joinToString { it.instance }}); use forInstance"
        }
        return achados.single()
    }

    /** Definição do campo; lança [UnknownFieldException] se não existir e [IllegalStateException] se for ambíguo. */
    fun get(name: String): FieldDef = achar(name) ?: throw UnknownFieldException(name)

    fun has(name: String): Boolean = achar(name) != null

    /** Valores conhecidos do campo; lista vazia significa texto livre. Lança se o campo não existir. */
    fun allowedValues(name: String): List<String> = get(name).knownValues

    companion object {
        /**
         * Lê `campos: [{nome, tipo, instancia, finalidade, valores?, customId?}]`. O YAML é estrito:
         * chave desconhecida, tipo inválido ou nome duplicado lançam exceção citando o arquivo.
         */
        fun fromYaml(path: Path): FieldDictionary {
            val arquivo = try {
                Yaml.default.decodeFromString(ArquivoCampos.serializer(), Files.readString(path))
            } catch (e: SerializationException) {
                throw IllegalArgumentException("$path: YAML de campos inválido: ${e.message}", e)
            }
            val campos = arquivo.campos.map { c ->
                val tipo = FieldType.entries.firstOrNull { it.name == c.tipo.uppercase() }
                    ?: throw IllegalArgumentException(
                        "$path: tipo desconhecido '${c.tipo}' no campo '${c.nome}' (esperado: ${FieldType.entries.joinToString()})",
                    )
                try {
                    FieldDef(c.nome, tipo, c.instancia, c.finalidade, c.valores, c.customId)
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException("$path: ${e.message}", e)
                }
            }
            try {
                return FieldDictionary(campos)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("$path: ${e.message}", e)
            }
        }
    }
}

@Serializable
private data class ArquivoCampos(val campos: List<CampoYaml> = emptyList())

@Serializable
private data class CampoYaml(
    val nome: String,
    val tipo: String,
    val instancia: String,
    val finalidade: String,
    val valores: List<String> = emptyList(),
    val customId: String? = null,
)
