package br.sidia.painel.integridade

import br.sidia.painel.AppContext
import br.sidia.painel.cards.executarAuditado
import br.sidia.painel.cards.hoje
import br.sidia.painel.db.AccessLog
import br.sidia.painel.lgpd.ModuloSensivel
import br.sidia.painel.lgpd.TravaDpo
import br.sidia.painel.trabalho.SqliteWorkStore
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Rotas da Integridade, SÓ de leitura e SÓ do GERENTE: `GET /api/integridade` e `GET /api/integridade/catalogo`. As tasks por
 * trás de cada número vêm da rota genérica dos cartões `IN-nn` (`/api/cartoes/{id}/tasks`), que tem as mesmas travas.
 *
 * A ordem das travas é sempre: (1) papel — qualquer um que não seja GERENTE recebe 403 sem código, para não saber do módulo;
 * (2) a trava do DPO (403 `AGUARDANDO_ACEITE_DPO` em produção sem aceite); (3) os parâmetros (400). TODA chamada, inclusive a
 * negada ou malformada, é gravada no `access_log` ANTES da resposta (a rota do registro é a fixa, sem chave de issue), e se a
 * gravação falhar a resposta é 503 sem dados. O catálogo não traz dado de pessoa, mas segue as mesmas travas.
 */
fun Route.rotasDeIntegridade(ctx: AppContext, auditoria: AccessLog) {
    val store = SqliteWorkStore(requireNotNull(ctx.dataSource))

    get("/api/integridade") {
        call.executarAuditado(auditoria, ctx) { principal ->
            exigirGerente(ctx, principal)
            TravaDpo(ctx.settings).exigir(ModuloSensivel.INTEGRIDADE)
            val filtros = lerFiltrosIntegridade(call.request.queryParameters, hoje(ctx))
            paraDto(calcularIntegridade(ctx, principal, filtros, store), ctx)
        }
    }

    get("/api/integridade/catalogo") {
        call.executarAuditado(auditoria, ctx) { principal ->
            exigirGerente(ctx, principal)
            TravaDpo(ctx.settings).exigir(ModuloSensivel.INTEGRIDADE)
            catalogoDto(ctx)
        }
    }
}
