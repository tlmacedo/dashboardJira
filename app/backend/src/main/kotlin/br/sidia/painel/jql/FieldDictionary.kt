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
data class FieldDef(
    val name: String,
    val type: FieldType,
    val instance: String,
    val purpose: String,
    val knownValues: List<String> = emptyList(),
    val customId: String? = null,
)

/** Campo pedido que não consta no dicionário. */
class UnknownFieldException(val name: String) : RuntimeException("Campo fora do dicionário: $name")

/**
 * Dicionário de campos do Jira. Nomes são únicos por instância; o mesmo nome pode existir em
 * instâncias diferentes (a busca por nome devolve a primeira definição declarada).
 */
class FieldDictionary(fields: List<FieldDef>) {
    private val porNome: Map<String, FieldDef>

    init {
        val vistos = mutableSetOf<Pair<String, String>>()
        fields.forEach { f ->
            require(vistos.add(f.instance to f.name)) {
                "Campo duplicado no dicionário: '${f.name}' na instância '${f.instance}'"
            }
        }
        porNome = fields.groupBy { it.name }.mapValues { it.value.first() }
    }

    /** Definição do campo; lança [UnknownFieldException] se não existir. */
    fun get(name: String): FieldDef = porNome[name] ?: throw UnknownFieldException(name)

    fun has(name: String): Boolean = name in porNome

    /** Valores conhecidos do campo (vazio se for de texto livre); lança se o campo não existir. */
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
                FieldDef(c.nome, tipo, c.instancia, c.finalidade, c.valores, c.customId)
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
