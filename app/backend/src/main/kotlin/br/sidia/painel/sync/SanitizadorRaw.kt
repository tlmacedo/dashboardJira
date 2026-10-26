package br.sidia.painel.sync

import br.sidia.painel.security.normalizarUsername
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Remove dados pessoais do JSON da issue antes de guardá-lo em `issue.raw`. Em qualquer profundidade:
 * `displayName`, `emailAddress`, `avatarUrls`, `self`, `accountId` e `timeZone` somem de todos os objetos.
 * Num objeto de usuário o `name` fica normalizado e a `key` (identificador interno do usuário) é removida.
 *
 * Só é usuário o objeto que (a) está num campo de pessoa conhecido (`assignee`, `reporter`, `creator`,
 * `author`, `updateAuthor`), ou (b) traz `emailAddress` ou `accountId`, ou (c) traz `displayName` junto
 * com `name`. Um objeto com `avatarUrls` por si só (ex.: `project`) NÃO é usuário: `project.key` e
 * `project.name` ficam como estão.
 */
internal object SanitizadorRaw {
    private val CAMPOS_PESSOAIS = setOf("displayName", "emailAddress", "avatarUrls", "self", "accountId", "timeZone")
    private val CAMPOS_DE_PESSOA = setOf("assignee", "reporter", "creator", "author", "updateAuthor")

    fun sanitizar(e: JsonElement): JsonElement = sanitizar(e, null)

    private fun sanitizar(e: JsonElement, campo: String?): JsonElement = when (e) {
        is JsonObject -> sanitizarObjeto(e, campo)
        // Os elementos de uma lista herdam o nome do campo que a contém (ex.: lista de `author`).
        is JsonArray -> JsonArray(e.map { sanitizar(it, campo) })
        else -> e
    }

    private fun ehUsuario(o: JsonObject, campo: String?): Boolean =
        campo in CAMPOS_DE_PESSOA ||
            "emailAddress" in o || "accountId" in o ||
            ("displayName" in o && "name" in o)

    private fun sanitizarObjeto(o: JsonObject, campo: String?): JsonObject {
        val usuario = ehUsuario(o, campo)
        val saida = LinkedHashMap<String, JsonElement>()
        for ((k, v) in o) {
            if (k in CAMPOS_PESSOAIS) continue
            if (usuario && k == "key") continue
            saida[k] = if (usuario && k == "name" && v is JsonPrimitive && v.isString && v.content.isNotBlank()) {
                JsonPrimitive(normalizarUsername(v.content))
            } else {
                sanitizar(v, k)
            }
        }
        return JsonObject(saida)
    }
}
