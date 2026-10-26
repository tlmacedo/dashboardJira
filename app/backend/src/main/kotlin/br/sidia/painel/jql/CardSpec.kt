package br.sidia.painel.jql

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path

/** Tipos de parâmetro que uma ficha pode declarar. */
val TIPOS_DE_PARAMETRO: Set<String> = setOf("squad", "periodo", "os", "pessoa", "tipo", "atualizadoDesde", "chaves")

/** Parâmetro declarado na ficha. [type] é um de [TIPOS_DE_PARAMETRO]. */
data class CardParam(val name: String, val type: String, val required: Boolean = true)

/**
 * Ficha de um card (seção 5.2 da spec). [filtrosFixos] entra como `campo in (...)`;
 * [filtrosExcluidos] entra como `campo not in (...)` (ex.: status cancelados).
 */
data class CardSpec(
    val id: String,
    val titulo: String,
    val instancia: String,
    val campos: List<String>,
    val filtrosFixos: Map<String, List<String>>,
    val parametros: List<CardParam>,
    val formula: String,
    val exemplo: String,
    val aceite: List<String>,
    val expandChangelog: Boolean,
    val filtrosExcluidos: Map<String, List<String>> = emptyMap(),
    /** Se verdadeiro, a consulta termina em `ORDER BY updated ASC, key ASC` (sincronização incremental). */
    val ordenarPorAtualizacao: Boolean = false,
)

/**
 * Valor do parâmetro `atualizadoDesde`: o instante de corte e o fuso do SERVIDOR do Jira, no qual
 * o JQL é interpretado (`updated >= "aaaa-MM-dd HH:mm"` sem fuso explícito).
 */
data class AtualizadoDesde(val at: java.time.Instant, val fusoDoServidor: java.time.ZoneId)

/** Consulta pronta para o cliente do Jira: JQL gerado só pelo construtor, mais `fields` e expansão. */
data class JqlQuery(val instance: String, val jql: String, val fields: List<String>, val expandChangelog: Boolean)

/** Campo do dicionário que cada tipo de parâmetro filtra. */
internal val CAMPO_DO_PARAMETRO: Map<String, String> = mapOf(
    "squad" to "assignee",
    "pessoa" to "assignee",
    "periodo" to "resolved",
    "os" to "OS Version",
    "tipo" to "issuetype",
    "atualizadoDesde" to "updated",
    "chaves" to "key",
)

/** Campos usados por `ORDER BY updated ASC, key ASC` (ficha com `ordenarPorAtualizacao`). */
internal val CAMPOS_DA_ORDENACAO: List<String> = listOf("updated", "key")

object CardSpecs {
    /**
     * Lê a ficha em YAML (estrita: chave desconhecida falha citando arquivo e chave) e confere,
     * já na carga, que `campos`, as chaves dos filtros fixos e os campos filtrados pelos parâmetros
     * existem no dicionário DA INSTÂNCIA da ficha. Campo ausente lança [UnknownFieldException].
     */
    fun fromYaml(path: Path, dict: FieldDictionary): CardSpec {
        val ficha = try {
            Yaml.default.decodeFromString(FichaYaml.serializer(), Files.readString(path))
        } catch (e: SerializationException) {
            throw IllegalArgumentException("$path: ficha inválida: ${e.message}", e)
        }
        val spec = try {
            CardSpec(
                id = ficha.id,
                titulo = ficha.titulo,
                instancia = ficha.instancia,
                campos = ficha.campos,
                filtrosFixos = ficha.filtrosFixos,
                parametros = ficha.parametros.map { CardParam(it.nome, it.tipo, it.obrigatorio) },
                formula = ficha.formula,
                exemplo = ficha.exemplo,
                aceite = ficha.aceite,
                expandChangelog = ficha.expandChangelog,
                filtrosExcluidos = ficha.filtrosExcluidos,
                ordenarPorAtualizacao = ficha.ordenarPorAtualizacao,
            ).also { validar(it) }
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("$path: ${e.message}", e)
        }
        val doDicionario = dict.forInstance(spec.instancia)
        val porSecao = listOf(
            "campos" to spec.campos,
            "filtrosFixos" to spec.filtrosFixos.keys.toList(),
            "filtrosExcluidos" to spec.filtrosExcluidos.keys.toList(),
            "parametros" to spec.parametros.map { CAMPO_DO_PARAMETRO.getValue(it.type) },
            // A ordenação usa updated e key; sem eles no dicionário a falha vem já na carga.
            "ordenarPorAtualizacao" to (if (spec.ordenarPorAtualizacao) CAMPOS_DA_ORDENACAO else emptyList()),
        )
        for ((secao, nomes) in porSecao) {
            for (nome in nomes) {
                try {
                    doDicionario.get(nome)
                } catch (e: UnknownFieldException) {
                    throw UnknownFieldException(e.name, "$path, seção $secao, instância '${spec.instancia}'")
                }
            }
        }
        ensaiar(path, spec, doDicionario)
        return spec
    }

    /** Tipos de campo aceitos por tipo de parâmetro. */
    private val TIPOS_DO_CAMPO: Map<String, Set<FieldType>> = mapOf(
        "periodo" to setOf(FieldType.DATE),
        "squad" to setOf(FieldType.USER),
        "pessoa" to setOf(FieldType.USER),
        "os" to setOf(FieldType.SELECT, FieldType.TEXT),
        "tipo" to setOf(FieldType.SELECT, FieldType.TEXT),
        "atualizadoDesde" to setOf(FieldType.DATE),
        "chaves" to setOf(FieldType.TEXT),
    )

    /**
     * Ensaio na carga (sem Jira): confere o tipo do campo de cada parâmetro e monta os filtros fixos
     * e excluídos num [JqlBuilder], para que valor fora de `knownValues`, texto inseguro ou lista vazia
     * falhem aqui e não na hora da busca.
     */
    private fun ensaiar(path: Path, spec: CardSpec, dict: FieldDictionary) {
        try {
            spec.parametros.forEach { p ->
                val campo = dict.get(CAMPO_DO_PARAMETRO.getValue(p.type))
                val aceitos = TIPOS_DO_CAMPO.getValue(p.type)
                require(campo.type in aceitos) {
                    "parâmetro '${p.name}' (${p.type}) aponta para o campo '${campo.name}' do tipo ${campo.type}; esperado ${aceitos.joinToString("/")}"
                }
            }
            val b = JqlBuilder(dict, spec.instancia)
            spec.filtrosFixos.forEach { (campo, valores) -> b.inList(campo, valores) }
            spec.filtrosExcluidos.forEach { (campo, valores) -> b.notInList(campo, valores) }
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("$path: ${e.message}", e)
        }
    }

    private fun validar(spec: CardSpec) {
        require(spec.id.isNotBlank()) { "ficha sem id" }
        require(spec.filtrosFixos.isNotEmpty()) { "ficha '${spec.id}' sem nenhum filtro fixo (filtrosFixos)" }
        require(spec.titulo.isNotBlank()) { "ficha '${spec.id}' sem titulo" }
        require(spec.instancia.isNotBlank()) { "ficha '${spec.id}' sem instancia" }
        spec.parametros.forEach {
            require(it.type in TIPOS_DE_PARAMETRO) {
                "parâmetro '${it.name}' com tipo desconhecido '${it.type}' (esperado: ${TIPOS_DE_PARAMETRO.joinToString()})"
            }
        }
        require(spec.parametros.map { it.name }.toSet().size == spec.parametros.size) {
            "ficha '${spec.id}' com nomes de parâmetro repetidos"
        }
        require(spec.parametros.map { it.type }.toSet().size == spec.parametros.size) {
            "ficha '${spec.id}' com dois parâmetros do mesmo tipo"
        }
    }
}

@Serializable
private data class FichaYaml(
    val id: String,
    val titulo: String,
    val instancia: String,
    val campos: List<String> = emptyList(),
    val filtrosFixos: Map<String, List<String>> = emptyMap(),
    val filtrosExcluidos: Map<String, List<String>> = emptyMap(),
    val parametros: List<ParametroYaml> = emptyList(),
    val formula: String = "",
    val exemplo: String = "",
    val aceite: List<String> = emptyList(),
    val expandChangelog: Boolean = false,
    val ordenarPorAtualizacao: Boolean = false,
)

@Serializable
private data class ParametroYaml(val nome: String, val tipo: String, val obrigatorio: Boolean = true)
