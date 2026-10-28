package br.sidia.painel.governanca

import br.sidia.painel.AppContext
import br.sidia.painel.cards.ErroHttp
import br.sidia.painel.cards.ROTA_SENSIVEL
import br.sidia.painel.cards.data
import br.sidia.painel.cards.executarAuditado
import br.sidia.painel.cards.exigirPapel
import br.sidia.painel.cards.hoje
import br.sidia.painel.cards.texto
import br.sidia.painel.cards.usernameValido
import br.sidia.painel.db.AccessLog
import br.sidia.painel.db.LinhaAcesso
import br.sidia.painel.security.Role
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

private val SO_GERENTE = setOf(Role.GERENTE)

/** Linhas por página do registro de acesso. */
internal const val PAGINA_DO_REGISTRO = 200

private const val PERIODO_PADRAO_DIAS = 6L
private const val PERIODO_MAXIMO_DIAS = 90L

/** Prefixo de rota aceito no filtro: só o alfabeto das rotas do painel (inclusive o `{key}` do padrão gravado). */
private val FORMATO_DE_ROTA = Regex("/[A-Za-z0-9/_{}.-]{0,99}")

/** Forma de uma chave de issue (`TG-701`, `tg-701`) como um segmento isolado do caminho. */
private val SEGMENTO_DE_CHAVE = Regex("[A-Za-z][A-Za-z0-9_]*-\\d+")

/** Filtros gravados que são metadados da chamada, não filtros de quem consultou. */
private val METADADOS = setOf("resultado", "total")

/**
 * `GET /api/governanca/acessos?de=&ate=&usuario=&rota=&depoisDe=` (AU-02): o registro de acesso, SÓ do gerente. A própria consulta é
 * registrada, como a de qualquer rota de dados (200, 400 e 403), e falha ao registrar é 503 sem dados. Período de no máximo 90 dias
 * (datas pela leitura comum, 1970–2200; 400 e nunca 500), páginas de 200 do mais recente ao mais antigo e cursor `depoisDe`.
 *
 * Nunca mostra mais do que o `access_log` guarda e nunca o que identifica o achado: sem filtros crus (só os nomes), sem segmentos,
 * sem chave de issue na rota. Sem ranking de quem consultou mais: não há contagem nem ordenação por pessoa. Os filtros desta
 * própria rota (`usuario`, `rota`, `depoisDe`) não vão para o `access_log` (a lista de filtros auditados não os inclui).
 */
fun Route.rotasDeRegistroDeAcesso(ctx: AppContext, auditoria: AccessLog) {
    get("/api/governanca/acessos") {
        call.attributes.put(ROTA_SENSIVEL, true)
        call.executarAuditado(auditoria, ctx) { principal ->
            exigirPapel(ctx, principal, SO_GERENTE)
            val params = call.request.queryParameters
            val ate = data(params, "ate") ?: hoje(ctx)
            val de = data(params, "de") ?: ate.minusDays(PERIODO_PADRAO_DIAS)
            if (de.isAfter(ate) || de.plusDays(PERIODO_MAXIMO_DIAS - 1).isBefore(ate)) throw ErroHttp(HttpStatusCode.BadRequest)
            val usuario = texto(params, "usuario")?.let(::usernameValido)
            val rota = texto(params, "rota")?.also { if (!FORMATO_DE_ROTA.matches(it)) throw ErroHttp(HttpStatusCode.BadRequest) }
            val depoisDe = texto(params, "depoisDe")?.let { it.toLongOrNull()?.takeIf { n -> n > 0 } ?: throw ErroHttp(HttpStatusCode.BadRequest) }
            val zona = ctx.settings.zone
            val achadas = auditoria.listar(
                de.atStartOfDay(zona).toInstant(), ate.plusDays(1).atStartOfDay(zona).toInstant(), usuario, rota, PAGINA_DO_REGISTRO + 1, depoisDe,
            )
            val pagina = achadas.take(PAGINA_DO_REGISTRO)
            AcessosDto(
                de = de.toString(), ate = ate.toString(), acessos = pagina.map { (_, linha) -> paraDto(linha) },
                proximo = if (achadas.size > PAGINA_DO_REGISTRO) pagina.last().first else null, tamanhoDaPagina = PAGINA_DO_REGISTRO,
            )
        }
    }
}

/** A linha como a tela a vê: rota sem chave de issue, resultado e só os nomes dos filtros. */
internal fun paraDto(l: LinhaAcesso): AcessoDto {
    val filtros: JsonObject? = l.filters?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
    val resultado = (filtros?.get("resultado") as? JsonPrimitive)?.content?.toIntOrNull()
    return AcessoDto(
        quando = l.at.toString(), usuario = l.userId, rota = rotaSemChave(l.route), resultado = resultado,
        filtros = filtros?.keys.orEmpty().filter { it !in METADADOS }.sorted(),
    )
}

/**
 * Por defesa, mesmo que uma linha antiga tenha uma chave de issue na rota, ela sai como `{key}`. O id de cartão do drill
 * (`/api/cartoes/TS-03/tasks`) não é chave de issue e fica.
 */
internal fun rotaSemChave(rota: String): String {
    val partes = rota.split('/')
    return partes.mapIndexed { i, p -> if (i > 0 && SEGMENTO_DE_CHAVE.matches(p) && partes[i - 1] != "cartoes") "{key}" else p }.joinToString("/")
}
