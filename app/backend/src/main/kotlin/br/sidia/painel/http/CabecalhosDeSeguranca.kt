package br.sidia.painel.http

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationPlugin
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.path
import io.ktor.server.response.header

/**
 * Política de conteúdo estrita: tudo vem da própria origem (o build do Vite não gera script nem estilo inline).
 * Sem `unsafe-inline` e sem `unsafe-eval`. O React aplica `style={}` pelo CSSOM, que a CSP não bloqueia.
 */
internal const val POLITICA_DE_CONTEUDO =
    "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; " +
        "connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'"

private val cabecalhosFixos = listOf(
    "Content-Security-Policy" to POLITICA_DE_CONTEUDO,
    "X-Frame-Options" to "DENY",
    "X-Content-Type-Options" to "nosniff",
    "Referrer-Policy" to "no-referrer",
    "Permissions-Policy" to "camera=(), microphone=(), geolocation=(), payment=()",
    "Cross-Origin-Opener-Policy" to "same-origin",
)

/** Caminhos de API e de autenticação: resposta de dados, nunca guardada em cache. */
private fun ehApiOuAuth(caminho: String) =
    caminho == "/api" || caminho.startsWith("/api/") || caminho == "/auth" || caminho.startsWith("/auth/")

/**
 * Acrescenta em TODA resposta (inclusive 401, 403, 404 e arquivos estáticos) os cabeçalhos de segurança,
 * cada um uma única vez: um cabeçalho que a rota já definiu não é repetido.
 *
 * - `Cache-Control: no-store` só nas respostas sob `/api` e `/auth` que ainda não definiram o cabeçalho
 *   (o servidor estático define o seu).
 * - `Strict-Transport-Security` só fora do modo de desenvolvimento (que roda em HTTP local).
 *
 * Se o servidor de desenvolvimento do Vite precisar de outra política, ela fica no `vite.config.ts`, nunca aqui.
 */
fun cabecalhosDeSeguranca(devMode: Boolean): ApplicationPlugin<Unit> = createApplicationPlugin("CabecalhosDeSeguranca") {
    onCallRespond { call, _ ->
        val saida = call.response.headers
        for ((nome, valor) in cabecalhosFixos) {
            if (saida[nome] == null) call.response.header(nome, valor)
        }
        if (!devMode && saida[HttpHeaders.StrictTransportSecurity] == null) {
            call.response.header(HttpHeaders.StrictTransportSecurity, "max-age=31536000")
        }
        if (ehApiOuAuth(call.request.path()) && saida[HttpHeaders.CacheControl] == null) {
            call.response.header(HttpHeaders.CacheControl, "no-store")
        }
    }
}
