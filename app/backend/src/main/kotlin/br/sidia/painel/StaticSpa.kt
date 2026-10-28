package br.sidia.painel

import br.sidia.painel.security.segmentosNormalizados
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.defaultForFile
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.http.content.LocalFileContent
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import java.nio.file.Files
import java.nio.file.Path

private const val CACHE_ASSETS = "public, max-age=31536000, immutable"
private const val SEM_CACHE = "no-cache"

/** O que a rota estática vai responder: um arquivo (com seu `Cache-Control`) ou 404. */
private sealed interface Alvo {
    data object NaoEncontrado : Alvo
    class Arquivo(val caminho: Path, val cache: String) : Alvo
}

/**
 * Serve o build do frontend em [raiz] (rotas GET e HEAD fora de `/api`).
 *
 * Proteção contra travessia de diretório (path traversal), em quatro camadas:
 * 1. cada segmento do caminho é decodificado e recusado se for `.`, `..` ou contiver `/`, `\` ou NUL
 *    (cobre `..%2f`, `%2e%2e`, `..%5c`, `%00`);
 * 2. segmento que começa com `.` (arquivo ou pasta oculta, como `.env` e `.git`) é sempre 404;
 * 3. o arquivo resolvido é normalizado e precisa estar dentro da raiz;
 * 4. o caminho REAL (seguindo links simbólicos) também precisa estar dentro da raiz real.
 *
 * Fallback para `index.html` apenas para caminhos de página: sem extensão de arquivo e fora de `/api`.
 * Arquivo com extensão que não existe devolve 404. Rotas de API desconhecidas nunca chegam aqui.
 *
 * `HEAD` responde 200 com os mesmos cabeçalhos do `GET` e sem corpo (rota própria; sem o plugin
 * `AutoHeadResponse`, que exigiria dependência nova).
 */
fun Route.spaEstatica(raiz: Path) {
    val raizAbsoluta = raiz.toAbsolutePath().normalize()
    get("/{...}") {
        when (val alvo = alvoDe(raizAbsoluta, call)) {
            is Alvo.Arquivo -> {
                call.response.header(HttpHeaders.CacheControl, alvo.cache)
                call.respond(LocalFileContent(alvo.caminho.toFile()))
            }
            Alvo.NaoEncontrado -> call.respond(HttpStatusCode.NotFound)
        }
    }
    head("/{...}") {
        when (val alvo = alvoDe(raizAbsoluta, call)) {
            is Alvo.Arquivo -> {
                call.response.header(HttpHeaders.CacheControl, alvo.cache)
                call.respond(SemCorpo(alvo.caminho))
            }
            Alvo.NaoEncontrado -> call.respond(HttpStatusCode.NotFound)
        }
    }
}

/** Resposta de `HEAD`: só tipo e tamanho do arquivo, nunca o conteúdo. */
private class SemCorpo(arquivo: Path) : OutgoingContent.NoContent() {
    override val contentType: ContentType = ContentType.defaultForFile(arquivo.toFile())
    override val contentLength: Long? = Files.size(arquivo)
}

private fun alvoDe(raizAbsoluta: Path, call: ApplicationCall): Alvo {
    val segmentos = segmentosNormalizados(call.request.local.uri)
    if (segmentos == null || segmentos.any { perigoso(it) } || segmentos.firstOrNull()?.equals("api", true) == true) {
        return Alvo.NaoEncontrado
    }
    // Arquivo ou pasta oculta em qualquer nível: nunca servido, nem pelo fallback da página inicial.
    if (segmentos.any { it.startsWith('.') }) return Alvo.NaoEncontrado
    // O `X-Content-Type-Options: nosniff` é escrito em toda resposta por cabecalhosDeSeguranca.
    val arquivo = resolverDentro(raizAbsoluta, segmentos)
    return when {
        arquivo != null -> Alvo.Arquivo(arquivo, if (segmentos.firstOrNull() == "assets") CACHE_ASSETS else SEM_CACHE)
        segmentos.isNotEmpty() && temExtensao(segmentos.last()) -> Alvo.NaoEncontrado
        else -> resolverDentro(raizAbsoluta, listOf("index.html"))?.let { Alvo.Arquivo(it, SEM_CACHE) } ?: Alvo.NaoEncontrado
    }
}

private fun perigoso(segmento: String) =
    segmento == "." || segmento == ".." || segmento.any { it == '/' || it == '\\' || it == '\u0000' }

private fun temExtensao(segmento: String) = segmento.lastIndexOf('.') > 0 || segmento.startsWith('.') && segmento.length > 1

/**
 * Resolve [segmentos] sob [raiz]; `null` se não for um arquivo regular dentro da raiz (inclusive via links)
 * ou se algum segmento começar com `.` (oculto).
 */
internal fun resolverDentro(raiz: Path, segmentos: List<String>): Path? {
    if (segmentos.any { it.startsWith('.') }) return null
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
