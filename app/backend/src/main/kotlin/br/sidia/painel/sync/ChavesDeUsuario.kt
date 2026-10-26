package br.sidia.painel.sync

import br.sidia.painel.security.normalizarUsername
import kotlinx.serialization.json.JsonObject

/**
 * Chaves internas de usuário do Jira DC (`JIRAUSER10500`). Em versões novas o changelog de responsável traz
 * essa chave em `from`/`to`, e o username só aparece no objeto de usuário (`name` + `key`) da própria issue.
 * A coleta tem de rodar sobre o JSON recebido, ANTES do [SanitizadorRaw], que apaga a `key`.
 */
object ChavesDeUsuario {
    private val FORMATO = Regex("^JIRAUSER\\d+$", RegexOption.IGNORE_CASE)
    private val CAMPOS_DE_PESSOA = listOf("assignee", "reporter", "creator")

    fun ehChaveInterna(v: String): Boolean = FORMATO.matches(v)

    /** Chave interna (em maiúsculas) para username normalizado, vista na issue e nos autores dos históricos. */
    fun coletar(issue: JsonObject, historicos: List<JsonObject>): Map<String, String> {
        val mapa = LinkedHashMap<String, String>()
        val campos = issue["fields"] as? JsonObject
        for (campo in CAMPOS_DE_PESSOA) registrar(mapa, campos?.get(campo) as? JsonObject)
        for (h in historicos) registrar(mapa, h["author"] as? JsonObject)
        return mapa
    }

    private fun registrar(mapa: MutableMap<String, String>, usuario: JsonObject?) {
        usuario ?: return
        val chave = usuario.texto("key")?.trim()?.takeIf { ehChaveInterna(it) } ?: return
        val nome = usuario.texto("name")?.takeIf { it.isNotBlank() } ?: return
        mapa[chave.uppercase()] = normalizarUsername(nome)
    }
}
