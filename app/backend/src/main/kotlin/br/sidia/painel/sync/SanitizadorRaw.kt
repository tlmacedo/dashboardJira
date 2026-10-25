package br.sidia.painel.sync

import br.sidia.painel.security.normalizarUsername
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Remove dados pessoais do JSON da issue antes de guardá-lo em `issue.raw`. Em qualquer profundidade:
 * `displayName`, `emailAddress`, `avatarUrls` e `self` somem de todos os objetos; num objeto de usuário
 * (o que tinha algum desses campos de identidade) o `name` fica normalizado e a `key` (identificador
 * interno do usuário) é removida.
 */
internal object SanitizadorRaw {
    private val CAMPOS_PESSOAIS = setOf("displayName", "emailAddress", "avatarUrls", "self")
    private val MARCAS_DE_USUARIO = setOf("displayName", "emailAddress", "avatarUrls")

    fun sanitizar(e: JsonElement): JsonElement = when (e) {
        is JsonObject -> sanitizarObjeto(e)
        is JsonArray -> JsonArray(e.map(::sanitizar))
        else -> e
    }

    private fun sanitizarObjeto(o: JsonObject): JsonObject {
        val ehUsuario = o.keys.any { it in MARCAS_DE_USUARIO }
        val saida = LinkedHashMap<String, JsonElement>()
        for ((k, v) in o) {
            if (k in CAMPOS_PESSOAIS) continue
            if (ehUsuario && k == "key") continue
            saida[k] = if (ehUsuario && k == "name" && v is JsonPrimitive && v.isString && v.content.isNotBlank()) {
                JsonPrimitive(normalizarUsername(v.content))
            } else {
                sanitizar(v)
            }
        }
        return JsonObject(saida)
    }
}
