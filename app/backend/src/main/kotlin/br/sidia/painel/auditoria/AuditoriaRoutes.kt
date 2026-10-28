package br.sidia.painel.auditoria

import br.sidia.painel.AppContext
import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.ErroHttp
import br.sidia.painel.cards.executarAuditado
import br.sidia.painel.cards.hoje
import br.sidia.painel.db.AccessLog
import br.sidia.painel.jql.InvalidJqlValueException
import br.sidia.painel.jql.JqlValues
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.util.Locale

/** Campos do changelog cujo valor (de/para) o painel guarda e pode mostrar; os demais só têm o nome do campo. */
private val CAMPOS_COM_VALOR = setOf("status", "assignee", "resolution", "duedate", "labels", "os version")

/** Campos cuja alteração tem regra de quem pode fazer (spec 7.1). */
private val CAMPOS_DE_AUTORIZACAO = setOf("assignee", "duedate", "labels")

/**
 * Rotas da auditoria, SÓ de leitura (corrigir no Jira pelo painel é o Plano 16, futuro):
 * `GET /api/auditoria/sinais`, `GET /api/auditoria/historico/{key}` e `GET /api/cards/task-review`.
 * Só COORDENADOR e GERENTE (a checagem de papel roda dentro da execução auditada, então a negação também vai para o
 * `access_log`); toda resposta de sinais e de histórico leva o aviso fixo "indício, não prova". As gavetas de tasks dos
 * cartões AU-nn e DV-01 são a rota genérica `GET /api/cartoes/{id}/tasks`.
 */
fun Route.rotasDeAuditoria(ctx: AppContext, auditoria: AccessLog) {
    get("/api/auditoria/sinais") {
        call.executarAuditado(auditoria, ctx) { principal ->
            exigirCoordenacao(ctx, principal)
            val calculada = calcularAuditoria(ctx, principal, lerFiltrosAuditoria(call.request.queryParameters, hoje(ctx)))
            paraDto(calculada, ctx)
        }
    }

    get("/api/auditoria/historico/{key}") {
        call.executarAuditado(auditoria, ctx) { principal ->
            exigirCoordenacao(ctx, principal)
            historico(ctx, call.parameters["key"].orEmpty())
        }
    }

    get("/api/cards/task-review") {
        call.executarAuditado(auditoria, ctx) { principal ->
            exigirCoordenacao(ctx, principal)
            paraDto(calcularTaskReview(ctx, principal, lerFiltrosTaskReview(call.request.queryParameters)), ctx)
        }
    }
}

/** Histórico campo a campo; chave fora do padrão é 400 e issue inexistente (ou removida) é 404. */
internal fun historico(ctx: AppContext, chave: String): HistoricoDto {
    try {
        JqlValues.issueKey(chave)
    } catch (e: InvalidJqlValueException) {
        throw ErroHttp(HttpStatusCode.BadRequest)
    }
    val issue = SqliteAuditStore(requireNotNull(ctx.dataSource)).issue(INSTANCIA_AUDITORIA, chave) ?: throw ErroHttp(HttpStatusCode.NotFound)
    val deteccao = contextoDeDeteccao(ctx)
    val t = AuditTimeline(issue, ctx.statusMap)
    val linhas = issue.events.sortedBy { it.at }.map { e ->
        val campo = e.field.lowercase(Locale.ROOT)
        val guardado = campo in CAMPOS_COM_VALOR
        // Só se avalia o que os detectores avaliariam: troca de responsável; vencimento com `de` e `para` legíveis e
        // diferentes (primeira definição, remoção e valor ilegível não); label adicionada que justifica atraso.
        val temRegra = when (campo) {
            "assignee" -> true
            "duedate" -> {
                val de = Vencimentos.ler(e.from)
                val para = Vencimentos.ler(e.to)
                de != null && para != null && de != para
            }
            "labels" -> t.mudancasDeLabels.any { (ev, m) -> ev.changelogId == e.changelogId && m.adicionadas.any(ctx.labelsDeAtraso::justifica) }
            else -> false
        }
        val avaliacao = if (campo in CAMPOS_DE_AUTORIZACAO && temRegra) {
            // Na troca de responsável a referência é o responsável anterior (ou o novo, se não havia); nos demais, o do instante.
            val referencia = if (campo == "assignee") {
                e.from?.takeIf { it.isNotBlank() } ?: e.to?.takeIf { it.isNotBlank() }
            } else {
                t.responsavelEm(e.at)
            }
            deteccao.autorizacao.avaliar(e.authorRef, issue.reporter, referencia, e.at)
        } else {
            null
        }
        LinhaHistoricoDto(
            at = e.at.toString(), campo = e.field,
            de = e.from?.takeIf { guardado }?.take(LIMITE_VALOR_HISTORICO),
            para = e.to?.takeIf { guardado }?.take(LIMITE_VALOR_HISTORICO),
            autor = e.authorRef, autorizado = avaliacao?.autorizado, motivo = avaliacao?.motivo?.name, valorGuardado = guardado,
        )
    }
    val avisos = buildList {
        if (t.reporterAlterado) add(AvisoDto(AvisoDto.REPORTER_ALTERADO, AvisoDto.INFORMATIVO, "troca de reporter no histórico", mapOf("n" to "1")))
        if (t.vencimentosIlegiveis > 0) {
            add(AvisoDto(AvisoDto.VENCIMENTO_ILEGIVEL, AvisoDto.INFORMATIVO, "vencimento ilegível no histórico", mapOf("n" to t.vencimentosIlegiveis.toString())))
        }
    }
    return HistoricoDto(AVISO_INDICIO_NAO_PROVA, issue.key, issue.summary, issue.reporter, linhas, avisos)
}
