package br.sidia.painel.domain

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

/** Status do Jira sem categoria no mapa; deve ir para Qualidade dos dados, nunca ser adivinhado. */
class UnmappedStatusException(val status: String) : RuntimeException("Status sem mapeamento: $status")

/**
 * Mapa nome do status -> [StatusCategory], mais dois dados opcionais do workflow:
 * [ordem], a sequência natural do fluxo (a "devolvida" é a volta a um status de índice menor), e
 * [resolucoesNegativas], as resoluções que significam cancelamento (Won't Do, Duplicate...).
 * Todo status da [ordem] precisa estar no mapa.
 */
class StatusMap(
    private val byName: Map<String, StatusCategory>,
    val ordem: List<String> = emptyList(),
    resolucoesNegativas: Set<String> = emptySet(),
) {
    private val negativas: Set<String> = resolucoesNegativas.map { it.trim().lowercase(Locale.ROOT) }.toSet()

    init {
        ordem.forEach { status ->
            require(status in byName) { "status '$status' da ordem do fluxo não consta no mapa de status" }
        }
        require(ordem.toSet().size == ordem.size) { "ordem do fluxo com status repetido" }
    }

    fun category(status: String): StatusCategory = byName[status] ?: throw UnmappedStatusException(status)

    /** Posição do status na [ordem] do fluxo; nulo quando ele não faz parte dela. */
    fun indiceNaOrdem(status: String): Int? = ordem.indexOf(status).takeIf { it >= 0 }

    /** A resolução é de cancelamento? Sem diferenciar maiúsculas; vazio nunca é. */
    fun ehResolucaoNegativa(resolution: String): Boolean = resolution.trim().lowercase(Locale.ROOT) in negativas

    companion object {
        /**
         * Lê `status_map: {"A Fazer": todo, ...}` (estrito: categoria desconhecida falha citando o arquivo) e,
         * opcionais, `ordem: [...]` e `resolucoesNegativas: [...]`. Arquivo antigo sem eles continua válido.
         */
        fun fromYaml(path: Path): StatusMap {
            val arquivo = try {
                Yaml.default.decodeFromString(StatusMapYaml.serializer(), Files.readString(path))
            } catch (e: SerializationException) {
                throw IllegalArgumentException("$path: mapa de status inválido: ${e.message}", e)
            }
            val convertido = arquivo.statusMap.mapValues { (status, cat) ->
                try {
                    StatusCategory.valueOf(cat.trim().uppercase())
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException(
                        "$path: categoria '$cat' desconhecida para o status '$status' " +
                            "(esperado: ${StatusCategory.entries.joinToString { it.name.lowercase() }})",
                        e,
                    )
                }
            }
            return try {
                StatusMap(convertido, arquivo.ordem, arquivo.resolucoesNegativas.toSet())
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("$path: ${e.message}", e)
            }
        }
    }
}

@Serializable
private data class StatusMapYaml(
    @kotlinx.serialization.SerialName("status_map") val statusMap: Map<String, String>,
    val ordem: List<String> = emptyList(),
    val resolucoesNegativas: List<String> = emptyList(),
)
