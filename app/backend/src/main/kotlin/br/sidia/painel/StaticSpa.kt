package br.sidia.painel

import br.sidia.painel.security.segmentosNormalizados
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.http.content.LocalFileContent
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.nio.file.Files
import java.nio.file.Path

private const val CACHE_ASSETS = "public, max-age=31536000, immutable"
private const val SEM_CACHE = "no-cache"

/**
 * Serve o build do frontend em [raiz] (rotas GET fora de `/api`).
 *
 * Proteção contra travessia de diretório (path traversal), em três camadas:
 * 1. cada segmento do caminho é decodificado e recusado se for `.`, `..` ou contiver `/`, `\` ou NUL
 *    (cobre `..%2f`, `%2e%2e`, `..%5c`, `%00`);
 * 2. o arquivo resolvido é normalizado e precisa estar dentro da raiz;
 * 3. o caminho REAL (seguindo links simbólicos) também precisa estar dentro da raiz real.
 *
 * Fallback para `index.html` apenas para caminhos de página: sem extensão de arquivo e fora de `/api`.
 * Arquivo com extensão que não existe devolve 404. Rotas de API desconhecidas nunca chegam aqui.
 */
fun Route.spaEstatica(raiz: Path) {
    val raizAbsoluta = raiz.toAbsolutePath().normalize()
    get("/{...}") {
        val segmentos = segmentosNormalizados(call.request.local.uri)
        if (segmentos == null || segmentos.any { perigoso(it) } || segmentos.firstOrNull()?.equals("api", true) == true) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        // O `X-Content-Type-Options: nosniff` é escrito em toda resposta por cabecalhosDeSeguranca.
        val arquivo = resolverDentro(raizAbsoluta, segmentos)
        when {
            arquivo != null -> {
                val cache = if (segmentos.firstOrNull() == "assets") CACHE_ASSETS else SEM_CACHE
                call.response.header(HttpHeaders.CacheControl, cache)
                call.respond(LocalFileContent(arquivo.toFile()))
            }
            segmentos.isNotEmpty() && temExtensao(segmentos.last()) -> call.respond(HttpStatusCode.NotFound)
            else -> {
                val index = resolverDentro(raizAbsoluta, listOf("index.html"))
                if (index == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.response.header(HttpHeaders.CacheControl, SEM_CACHE)
                    call.respond(LocalFileContent(index.toFile()))
                }
            }
        }
    }
}

private fun perigoso(segmento: String) =
    segmento == "." || segmento == ".." || segmento.any { it == '/' || it == '\\' || it == '\u0000' }

private fun temExtensao(segmento: String) = segmento.lastIndexOf('.') > 0 || segmento.startsWith('.') && segmento.length > 1

/** Resolve [segmentos] sob [raiz]; `null` se não for um arquivo regular dentro da raiz (inclusive via links). */
private fun resolverDentro(raiz: Path, segmentos: List<String>): Path? {
    return try {
        val alvo = if (segmentos.isEmpty()) raiz.resolve("index.html") else segmentos.fold(raiz) { p, s -> p.resolve(s) }
        val normalizado = alvo.normalize()
        if (!normalizado.startsWith(raiz) || !Files.isRegularFile(normalizado)) return null
        if (!normalizado.toRealPath().startsWith(raiz.toRealPath())) return null
        normalizado
    } catch (e: Exception) {
        null // caminho inválido para o sistema de arquivos (InvalidPathException, IOException)
    }
}
