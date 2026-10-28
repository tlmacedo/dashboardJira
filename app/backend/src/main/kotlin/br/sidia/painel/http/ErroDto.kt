package br.sidia.painel.http

import kotlinx.serialization.Serializable

/**
 * Corpo de uma resposta de erro: um código estável (a tela monta o texto por ele, em pt-BR ou inglês) e parâmetros
 * opcionais. Nunca carrega mensagem livre, dado pessoal, JQL nem detalhe de exceção.
 */
@Serializable
data class ErroDto(val codigo: String, val params: Map<String, String> = emptyMap())
