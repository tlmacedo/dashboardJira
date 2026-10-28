package br.sidia.painel.cards

import br.sidia.painel.AppContext
import br.sidia.painel.db.AccessLog
import br.sidia.painel.security.Principal
import br.sidia.painel.security.principal
import io.ktor.server.application.ApplicationCall

/**
 * Executa uma rota de dados registrando a consulta ANTES de responder (inclusive 400, 403 e 404): o bloco recebe o
 * principal da chamada; um [ErroHttp] vira o status sem corpo (ou o `ErroDto` do código) e falha ao gravar no
 * `access_log` vira 503 sem dados. É a mesma execução auditada de `executar` (Tempo em status, catálogo, drill genérico),
 * com o principal já entregue ao bloco; as rotas de auditoria e os cards seguintes usam esta.
 */
internal suspend inline fun <reified T : Any> ApplicationCall.executarAuditado(
    auditoria: AccessLog,
    ctx: AppContext,
    crossinline bloco: suspend (Principal) -> T,
) {
    val quem = principal
    executar(ctx, auditoria) { bloco(quem) }
}
