package br.sidia.painel.distribuicao

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.comCsrf
import br.sidia.painel.config.Settings
import br.sidia.painel.pessoas.Cap1
import br.sidia.painel.security.Authenticator
import br.sidia.painel.security.Principal
import br.sidia.painel.security.principalFor
import br.sidia.painel.testContextComBanco
import br.sidia.painel.trabalho.RespTrb
import br.sidia.painel.trabalho.Trb1
import br.sidia.painel.module
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate

/**
 * Cenário DI-1 (fixtures fictícias) sobre o CAP-1: `ana` (Alfa, competências android e rede), `bruno` (Alfa, android) e `carla`
 * (Beta, rede e ui), relógio de sexta 30/10/2026. Além das quatro abertas do CAP-1:
 * - pendentes: TG-301 (sem dono, 6 h, `skill:android`), TG-302 (com o gerente, 4 h) e TG-303 (sem dono, 8 h, `skill:rede`);
 * - bruno sobrecarregado: TG-311, TG-312 e TG-313 (16 h cada, "a fazer" e dele, vencimento em 20/11).
 */
object Di1 {
    private fun comRestante(i: Trb1.IssueDeTeste, segundos: Long): Trb1.IssueDeTeste {
        val campos = (i.json["fields"] as JsonObject).toMutableMap()
        campos["timeestimate"] = JsonPrimitive(segundos)
        val novo = buildJsonObject {
            i.json.forEach { (k, v) -> if (k != "fields") put(k, v) }
            put("fields", JsonObject(campos))
        }
        return Trb1.IssueDeTeste(novo, i.eventos)
    }

    private fun aFazer(chave: String, resp: String?, horas: Int, vence: String?, labels: List<String> = emptyList()) =
        comRestante(Trb1.issue(chave, "Checklist", resp, "10-25T12", "Open", vence = vence, labels = labels, prioridade = "Medium"), horas * 3600L)

    fun issues(): List<Trb1.IssueDeTeste> = Cap1.issuesParaBanco() + listOf(
        aFazer("TG-301", null, 6, "2026-11-10", listOf("skill:android")),
        aFazer("TG-302", "gerente.exemplo", 4, null),
        aFazer("TG-303", null, 8, null, listOf("skill:rede")),
        aFazer("TG-311", "bruno", 16, "2026-11-20"),
        aFazer("TG-312", "bruno", 16, "2026-11-20"),
        aFazer("TG-313", "bruno", 16, "2026-11-20"),
    )

    fun novoContexto(banco: BancoEmArquivo, issues: List<Trb1.IssueDeTeste> = issues()): AppContext {
        val ctx = testContextComBanco(
            Settings(dashboardDevMode = false), banco, Cap1.papeis, Cap1.relogio, statusMap = Cap1.statusMap, calendar = Cap1.calendario,
            equipes = Cap1.equipes, ausencias = Cap1.ausencias, regrasPlanejamento = Cap1.regras,
        )
        Trb1.carregar(requireNotNull(ctx.dataSource), issues)
        return ctx
    }

    /** Uma chamada HTTP de teste: `get` e `post` (com o cabeçalho anti-CSRF e a origem do painel). */
    class Cliente(
        val get: suspend (String?, String) -> RespTrb,
        val post: suspend (String?, String, String) -> RespTrb,
        /** POST sem o cabeçalho anti-CSRF. */
        val postSemCsrf: suspend (String?, String, String) -> RespTrb,
    )

    fun rodar(ctx: AppContext, bloco: suspend (Cliente) -> Unit) = testApplication {
        val hoje = LocalDate.now(ctx.clock.withZone(ctx.settings.zone))
        val autenticador = object : Authenticator {
            override suspend fun authenticate(call: ApplicationCall): Principal? =
                call.request.headers["X-Teste-Usuario"]?.let { principalFor(it, Cap1.papeis, hoje) }
        }
        application { module(ctx.settings, ctx, autenticador) }
        val http = client
        bloco(
            Cliente(
                get = { u, c -> http.get(c) { if (u != null) header("X-Teste-Usuario", u) }.let { RespTrb(it.status, it.bodyAsText()) } },
                post = { u, c, corpo ->
                    http.post(c) {
                        if (u != null) header("X-Teste-Usuario", u)
                        comCsrf()
                        contentType(ContentType.Application.Json)
                        setBody(corpo)
                    }.let { RespTrb(it.status, it.bodyAsText()) }
                },
                postSemCsrf = { u, c, corpo ->
                    http.post(c) {
                        if (u != null) header("X-Teste-Usuario", u)
                        contentType(ContentType.Application.Json)
                        setBody(corpo)
                    }.let { RespTrb(it.status, it.bodyAsText()) }
                },
            ),
        )
    }

    /** O corpo JSON de um POST de decisão; `null` omite o campo. */
    fun corpo(tipo: String, chave: String, decisao: String, sugerido: String? = null, escolhido: String? = null, motivo: String? = null): String =
        buildJsonObject {
            put("tipo", tipo)
            put("issueKey", chave)
            put("decisao", decisao)
            if (sugerido != null) put("sugerido", sugerido)
            if (escolhido != null) put("escolhido", escolhido)
            if (motivo != null) put("motivo", motivo)
        }.toString()
}
