package br.sidia.painel.domain

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path

/** Status do Jira sem categoria no mapa; deve ir para Qualidade dos dados, nunca ser adivinhado. */
class UnmappedStatusException(val status: String) : RuntimeException("Status sem mapeamento: $status")

/** Mapa nome do status -> [StatusCategory]. */
class StatusMap(private val byName: Map<String, StatusCategory>) {
    fun category(status: String): StatusCategory = byName[status] ?: throw UnmappedStatusException(status)

    companion object {
        /** Lê `status_map: {"A Fazer": todo, ...}` (estrito: categoria desconhecida falha citando o arquivo). */
        fun fromYaml(path: Path): StatusMap {
            val bruto = try {
                Yaml.default.decodeFromString(StatusMapYaml.serializer(), Files.readString(path)).statusMap
            } catch (e: SerializationException) {
                throw IllegalArgumentException("$path: mapa de status inválido: ${e.message}", e)
            }
            val convertido = bruto.mapValues { (status, cat) ->
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
            return StatusMap(convertido)
        }
    }
}

@Serializable
private data class StatusMapYaml(@kotlinx.serialization.SerialName("status_map") val statusMap: Map<String, String>)
