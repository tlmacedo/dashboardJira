package br.sidia.painel.tarefa

import br.sidia.painel.AppContext
import br.sidia.painel.auditoria.historico
import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.EntradaDeCard
import br.sidia.painel.cards.ErroHttp
import br.sidia.painel.cards.INSTANCIA_TRABALHO
import br.sidia.painel.cards.exigirPapel
import br.sidia.painel.cards.executarAuditado
import br.sidia.painel.cards.contrato.LinkJira
import br.sidia.painel.cards.montarEntrada
import br.sidia.painel.cards.papeisVigentes
import br.sidia.painel.cards.posse.PosseCalculator
import br.sidia.painel.cards.riscos.CardRiscos
import br.sidia.painel.db.AccessLog
import br.sidia.painel.domain.TimeUnitMode
import br.sidia.painel.domain.TimelineBuilder
import br.sidia.painel.domain.UnmappedStatusException
import br.sidia.painel.jql.InvalidJqlValueException
import br.sidia.painel.lgpd.TravaDpo
import br.sidia.painel.jql.JqlValues
import br.sidia.painel.security.Principal
import br.sidia.painel.security.Role
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.SqliteWorkStore
import br.sidia.painel.trabalho.vencimentoOriginal
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.util.Locale

/** Código estável do 400 de chave de issue fora do formato (`TG-123`). */
internal const val CODIGO_CHAVE_INVALIDA = "CHAVE_INVALIDA"

/**
 * `GET /api/tarefa/{key}?instancia=samsung`: a gaveta universal da task (todos os papéis). Chave fora do formato é 400
 * `CHAVE_INVALIDA`; chave inexistente OU fora da visão de quem pergunta é 404 sem corpo (nunca 403: não confirma que a
 * chave existe). A consulta vai para o `access_log` com a rota-modelo, nunca com a chave. Não faz nenhuma requisição ao Jira.
 */
fun Route.rotasDeTarefa(ctx: AppContext, auditoria: AccessLog) {
    val store = SqliteWorkStore(requireNotNull(ctx.dataSource))
    get("/api/tarefa/{key}") {
        call.executarAuditado(auditoria, ctx) { principal ->
            exigirPapel(ctx, principal, Role.entries.toSet())
            val chave = try {
                JqlValues.issueKey(call.parameters["key"].orEmpty())
            } catch (e: InvalidJqlValueException) {
                throw ErroHttp(HttpStatusCode.BadRequest, CODIGO_CHAVE_INVALIDA)
            }
            val instancia = call.request.queryParameters["instancia"]?.takeIf { it.isNotBlank() } ?: INSTANCIA_TRABALHO
            tarefaDe(ctx, store, principal, instancia, chave)
        }
    }
}

private fun tarefaDe(ctx: AppContext, store: SqliteWorkStore, principal: Principal, instancia: String, chave: String): TarefaDto {
    val s = store.porChave(instancia, chave) ?: throw ErroHttp(HttpStatusCode.NotFound)
    // Os cards leem só a instância de trabalho: o escopo (quem cada papel vê) é o mesmo deles.
    val e = montarEntrada(ctx, store, principal, Parameters.Empty, null)
    val map = ctx.statusMap
    val avisos = mutableListOf<AvisoDto>()

    // Ruling (I-2): visível só se o responsável ATUAL está na visão. A posse histórica não amplia a gaveta: ela mostraria
    // situação, vencimento, tempo por status e fatores de risco do trabalho atual de quem está fora da visão.
    if (!e.escopo.permiteAtual(s)) throw ErroHttp(HttpStatusCode.NotFound)

    val categoria = try {
        map.category(s.status).name
    } catch (x: UnmappedStatusException) {
        avisos += AvisoDto(
            AvisoDto.STATUS_SEM_MAPEAMENTO, AvisoDto.INFORMATIVO, "status sem mapeamento", mapOf("status" to x.status),
        )
        "DESCONHECIDA"
    }

    val papeis = papeisVigentes(ctx, principal)
    val temPapelDeHistorico = Role.COORDENADOR in papeis || Role.GERENTE in papeis
    // O histórico de alterações é dado da AUDITORIA: com a trava do DPO fechada ele fica fora (nulo) e a gaveta avisa, sem dado.
    val veHistorico = temPapelDeHistorico && TravaDpo(ctx.settings, ctx.dataSource).liberada
    if (temPapelDeHistorico && !veHistorico) {
        avisos += AvisoDto(AvisoDto.HISTORICO_AGUARDANDO_ACEITE_DPO, AvisoDto.INFORMATIVO, "histórico aguardando o aceite do DPO")
    }

    return TarefaDto(
        key = s.key, resumo = s.summary, tipo = s.issueType, status = s.status, categoria = categoria,
        resolucao = s.resolution, responsavel = s.assignee?.takeIf { it in e.escopo.visiveis }, prioridade = s.priority,
        criada = s.created.toString(), atualizada = s.updated?.toString(), resolvida = s.resolved?.toString(),
        vencimento = s.duedate?.toString(), vencimentoOriginal = s.vencimentoOriginal()?.toString(),
        labels = s.labels, campos = s.camposDicionario,
        tempoPorStatus = tempoPorStatus(ctx, s, e, avisos),
        posse = posseDe(ctx, s, e, avisos),
        fatoresDeRisco = fatoresDeRisco(s, e),
        historico = if (veHistorico) historico(ctx, s.key, instancia).linhas else null,
        linkJira = LinkJira.paraIssue(ctx.settings, instancia, s.key),
        avisos = avisos,
    )
}

/** Dias úteis por status, na ordem da primeira entrada em cada um. Status fora do mapa: lista vazia (e o aviso já foi dado). */
private fun tempoPorStatus(ctx: AppContext, s: IssueSnapshot, e: EntradaDeCard, avisos: MutableList<AvisoDto>): List<TempoStatusDto> =
    try {
        val dias = LinkedHashMap<String, Double>()
        for (iv in TimelineBuilder.intervals(s.timeline, e.agora, ctx.statusMap)) {
            dias.merge(iv.status, ctx.calendar.businessDays(iv.start, iv.end), Double::plus)
        }
        dias.map { (status, d) -> TempoStatusDto(status, d) }
    } catch (x: UnmappedStatusException) {
        semMapeamento(avisos, x)
        emptyList()
    }

/**
 * A posse da task por pessoa. Quem está fora da visão de [e] vira uma só fatia "outra pessoa" (sem nome); o tempo sem
 * responsável fica à parte. Pessoas em ordem alfabética, "outra pessoa" e "sem responsável" por último.
 */
private fun posseDe(ctx: AppContext, s: IssueSnapshot, e: EntradaDeCard, avisos: MutableList<AvisoDto>): List<FracaoPosseDto> =
    try {
        val fatias = PosseCalculator.posse(s, e.agora, ctx.statusMap, ctx.calendar, TimeUnitMode.UTIL)
        val visiveis = fatias.filter { it.pessoa != null && it.pessoa in e.escopo.visiveis }
            .sortedBy { it.pessoa?.lowercase(Locale.ROOT) }
            .map { FracaoPosseDto(it.pessoa, false, it.dias, it.fracao) }
        val outras = fatias.filter { it.pessoa != null && it.pessoa !in e.escopo.visiveis }
        val semDono = fatias.filter { it.pessoa == null }
        visiveis +
            listOfNotNull(outras.takeIf { it.isNotEmpty() }?.let { FracaoPosseDto(null, false, it.sumOf { f -> f.dias }, it.sumOf { f -> f.fracao }) }) +
            listOfNotNull(semDono.takeIf { it.isNotEmpty() }?.let { FracaoPosseDto(null, true, it.sumOf { f -> f.dias }, it.sumOf { f -> f.fracao }) })
    } catch (x: UnmappedStatusException) {
        semMapeamento(avisos, x)
        emptyList()
    }

/** Nomes dos fatores de risco que o card Riscos atribui hoje; só para tasks abertas (concluídas: lista vazia). */
private fun fatoresDeRisco(s: IssueSnapshot, e: EntradaDeCard): List<String> =
    try {
        CardRiscos.avaliarUma(e, s)?.fatores?.map { (fator, _) -> fator.name }.orEmpty()
    } catch (x: UnmappedStatusException) {
        emptyList()
    }

private fun semMapeamento(avisos: MutableList<AvisoDto>, x: UnmappedStatusException) {
    if (avisos.none { it.codigo == AvisoDto.STATUS_SEM_MAPEAMENTO }) {
        avisos += AvisoDto(AvisoDto.STATUS_SEM_MAPEAMENTO, AvisoDto.INFORMATIVO, "status sem mapeamento", mapOf("status" to x.status))
    }
}
