package br.sidia.painel

import br.sidia.painel.http.CABECALHO_CSRF
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders

/**
 * Cabeçalhos de uma chamada mutante legítima do painel (POST, PUT, PATCH, DELETE): o cabeçalho anti-CSRF e a `Origin`
 * da própria aplicação. Compartilhado pelos testes de rotas que alteram estado.
 */
fun HttpRequestBuilder.comCsrf(origem: String = "http://localhost") {
    header(CABECALHO_CSRF, "1")
    header(HttpHeaders.Origin, origem)
}
