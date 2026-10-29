package br.sidia.painel.trabalho

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.contrato.CatalogoDeCartoes
import br.sidia.painel.cards.contrato.catalogoPadrao
import br.sidia.painel.config.Settings
import br.sidia.painel.module
import br.sidia.painel.security.Authenticator
import br.sidia.painel.security.Principal
import br.sidia.painel.security.principalFor
import br.sidia.painel.testContextComBanco
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.testing.testApplication
import java.time.LocalDate

/** Resposta de uma chamada HTTP de teste. */
class RespTrb(val status: HttpStatusCode, val corpo: String)

/**
 * Contexto de teste do cenário TRB-1: SQLite real em arquivo, relógio de sexta 30/10/2026, mapa de status e calendário
 * do cenário e papéis fictícios. As [issues] já vêm gravadas.
 */
fun novoContextoTrb1(
    banco: BancoEmArquivo,
    issues: List<Trb1.IssueDeTeste> = Trb1.issues(),
    settings: Settings = Settings(dashboardDevMode = false, lgpd = br.sidia.painel.lgpd.LgpdSettings(true, "TESTE-TRB1")),
): AppContext {
    val ctx = testContextComBanco(settings, banco, Trb1.papeis, Trb1.relogio, statusMap = Trb1.statusMap, calendar = Trb1.calendario)
    Trb1.carregar(requireNotNull(ctx.dataSource), issues)
    return ctx
}

/**
 * Sobe o módulo do painel com um autenticador por cabeçalho (`X-Teste-Usuario`) e entrega ao [bloco] uma função
 * `get(usuario, caminho)`. [rotasExtras] roda dentro de `application { }` (para registrar um card de teste).
 */
fun rodarTrb1(
    ctx: AppContext,
    catalogo: CatalogoDeCartoes = catalogoPadrao(),
    rotasExtras: Application.() -> Unit = {},
    bloco: suspend (suspend (String?, String) -> RespTrb) -> Unit,
) = testApplication {
    val hoje = LocalDate.now(ctx.clock.withZone(ctx.settings.zone))
    val autenticador = object : Authenticator {
        override suspend fun authenticate(call: ApplicationCall): Principal? =
            call.request.headers["X-Teste-Usuario"]?.let { principalFor(it, Trb1.papeis, hoje) }
    }
    application {
        module(ctx.settings, ctx, autenticador, catalogo = catalogo)
        rotasExtras()
    }
    val http = client
    bloco { usuario, caminho ->
        val r = http.get(caminho) { if (usuario != null) header("X-Teste-Usuario", usuario) }
        RespTrb(r.status, r.bodyAsText())
    }
}
