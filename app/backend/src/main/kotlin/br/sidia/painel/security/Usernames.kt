package br.sidia.painel.security

import java.util.Locale

/**
 * Forma canônica de um username (do login ou vindo do Jira): sem espaços nas pontas e em minúsculas,
 * independente do idioma do servidor ([Locale.ROOT]). NÃO valida o formato (isso é do
 * `RoleDirectory` e do `JqlValues`). Vazio ou só espaços falha com [IllegalArgumentException],
 * sem ecoar o valor recebido.
 */
fun normalizarUsername(raw: String): String {
    val normalizado = raw.trim().lowercase(Locale.ROOT)
    require(normalizado.isNotEmpty()) { "username vazio" }
    return normalizado
}
