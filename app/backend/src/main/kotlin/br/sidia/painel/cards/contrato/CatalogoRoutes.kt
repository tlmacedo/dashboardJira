package br.sidia.painel.cards.contrato

import br.sidia.painel.AppContext
import br.sidia.painel.cards.ErroHttp
import br.sidia.painel.cards.executar
import br.sidia.painel.cards.papeisVigentes
import br.sidia.painel.db.AccessLog
import br.sidia.painel.security.principal
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * `GET /api/cartoes/catalogo`: os cartões que o papel de quem pediu vê (o Mapa de cartões e a ajuda ⓘ leem daqui).
 * Passa pela mesma execução auditada das demais rotas de dados: toda chamada, inclusive a negada, vai para o
 * `access_log`, e sem registro não há resposta (503).
 */
fun Route.rotasDoCatalogo(ctx: AppContext, catalogo: CatalogoDeCartoes, auditoria: AccessLog) {
    get("/api/cartoes/catalogo") {
        call.executar(ctx, auditoria) {
            val papeis = papeisVigentes(ctx, call.principal)
            if (papeis.isEmpty()) throw ErroHttp(HttpStatusCode.Forbidden)
            CatalogoDto(catalogo.visiveisPara(papeis).map { it.meta.paraDto() })
        }
    }
}
