package br.sidia.painel.cards.contrato

import br.sidia.painel.AppContext
import br.sidia.painel.cards.CODIGO_SEGMENTO_INVALIDO
import br.sidia.painel.cards.EXTRAS_DE_AUDITORIA
import br.sidia.painel.cards.ErroHttp
import br.sidia.painel.cards.executar
import br.sidia.painel.cards.papeisVigentes
import br.sidia.painel.db.AccessLog
import br.sidia.painel.security.principal
import br.sidia.painel.sync.IssueStore
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/** Colunas da gaveta, na ordem; a tela traduz por `gaveta.col.<id>`. */
internal val COLUNAS_DA_GAVETA = listOf("chave", "resumo", "responsavel", "status", "detalhe")

/**
 * `GET /api/cartoes/{id}/tasks?segmento=...&<filtros>`: as tasks por trás de um número de um cartão registrado.
 *
 * - Cartão inexistente ou sem drill: 404 sem corpo. Cartão que o papel não vê: 403 sem corpo.
 * - Segmento ausente, malformado ou de outro cartão: 400 com o código `SEGMENTO_INVALIDO` (o valor recebido não
 *   volta no corpo).
 * - A visão de quem pediu é a do próprio cartão ([CartaoRegistrado.drill] filtra por `Visibility`); pedir uma pessoa
 *   fora dela é 403.
 * - Toda chamada, inclusive a negada ou malformada, vai para o `access_log`; sem registro não há resposta (503).
 */
fun Route.rotasDeDrillGenerico(ctx: AppContext, catalogo: CatalogoDeCartoes, store: IssueStore, auditoria: AccessLog) {
    get("/api/cartoes/{id}/tasks") {
        call.executar(ctx, auditoria) {
            val principal = call.principal
            val papeis = papeisVigentes(ctx, principal)
            if (papeis.isEmpty()) throw ErroHttp(HttpStatusCode.Forbidden)
            val cartao = catalogo.porId(call.parameters["id"].orEmpty()) ?: throw ErroHttp(HttpStatusCode.NotFound)
            if (cartao.meta.papeis.none { it in papeis }) throw ErroHttp(HttpStatusCode.Forbidden)
            if (!cartao.meta.temDrill) throw ErroHttp(HttpStatusCode.NotFound)
            val segmento = call.request.queryParameters["segmento"] ?: throw ErroHttp(HttpStatusCode.BadRequest, CODIGO_SEGMENTO_INVALIDO)
            val linhas = cartao.drill(EntradaDeCartao(ctx, store, principal, call.request.queryParameters), segmento)
            // A auditoria guarda a quantidade, nunca as chaves das tasks.
            call.attributes.put(EXTRAS_DE_AUDITORIA, mapOf("total" to linhas.size.toString()))
            DrillGenericoDto(
                tituloChave = cartao.meta.tituloChave,
                definicaoChave = cartao.meta.oQueMostraChave,
                total = linhas.size,
                colunas = COLUNAS_DA_GAVETA,
                linhas = linhas,
                // Montado aqui, só com a flag ligada e a instância configurada; o frontend só usa o que vem pronto.
                linkJira = LinkJira.paraChaves(ctx.settings, ctx.campos, cartao.instancia, linhas.map { it.key }),
            )
        }
    }
}
