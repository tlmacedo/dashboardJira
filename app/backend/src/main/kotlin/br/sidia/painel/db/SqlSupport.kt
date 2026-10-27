package br.sidia.painel.db

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/*
 * O SQLite não tem arrays nem `ANY (?)`. Listas viram um JSON de texto (um único parâmetro) e o SQL as lê com
 * `json_each(?)`, por exemplo: `WHERE id IN (SELECT value FROM json_each(?))`. Os valores nunca entram na string do SQL.
 */

/** Lista de textos como JSON (`["a","b"]`); coluna TEXT ou parâmetro de `json_each`. */
fun Collection<String>.emJson(): String = JsonArray(map { JsonPrimitive(it) }).toString()

/** Lista de números como JSON (`[1,2]`), para `IN (SELECT value FROM json_each(?))`. */
fun Collection<Long>.idsEmJson(): String = JsonArray(map { JsonPrimitive(it) }).toString()

/** Lista de listas de texto como JSON (`[["a","b"],["c","d"]]`), para casar tuplas com `json_extract(value, '$[0]')`. */
fun Collection<List<String>>.tuplasEmJson(): String =
    JsonArray(map { t -> JsonArray(t.map { JsonPrimitive(it) }) }).toString()

/** Lê uma coluna JSON de textos; nula ou vazia dá lista vazia. */
fun jsonParaTextos(texto: String?): List<String> =
    if (texto.isNullOrBlank()) emptyList() else Json.parseToJsonElement(texto).jsonArray.map { it.jsonPrimitive.content }
