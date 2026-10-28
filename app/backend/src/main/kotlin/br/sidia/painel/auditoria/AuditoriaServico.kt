package br.sidia.painel.auditoria

import br.sidia.painel.AppContext
import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.DrillRow
import br.sidia.painel.cards.ErroHttp
import br.sidia.painel.cards.INSTANCIA_TEMPO_EM_STATUS
import br.sidia.painel.cards.data
import br.sidia.painel.cards.hoje
import br.sidia.painel.cards.papeisVigentes
import br.sidia.painel.cards.texto
import br.sidia.painel.cards.usernameValido
import br.sidia.painel.cards.contrato.LinkJira
import br.sidia.painel.domain.NoPrazoDetalhe
import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.security.Principal
import br.sidia.painel.security.Role
import br.sidia.painel.security.Visibility
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Instância lida pela auditoria: a mesma do Tempo em status (a ficha de sincronização do time). */
internal const val INSTANCIA_AUDITORIA = INSTANCIA_TEMPO_EM_STATUS

/** Padrão do período: os últimos 30 dias de Manaus, `ate` inclusivo. */
private const val PERIODO_PADRAO_DIAS = 30L

/** Máximo de dias (inclusive) de um período pedido. */
private const val PERIODO_MAXIMO_DIAS = 180L

/** Tamanho máximo de um valor guardado mostrado no histórico. */
internal const val LIMITE_VALOR_HISTORICO = 200

/** Só coordenação e gerente veem a auditoria (spec 7.3); outro papel recebe 403, que também vai para o `access_log`. */
internal fun exigirCoordenacao(ctx: AppContext, principal: Principal) {
    val papeis = papeisVigentes(ctx, principal)
    if (Role.COORDENADOR !in papeis && Role.GERENTE !in papeis) throw ErroHttp(HttpStatusCode.Forbidden)
}

/** Filtros da auditoria. `pessoa` casa o responsável no instante OU o autor; `squad` é o squad do responsável na data do evento. */
internal class FiltrosAuditoria(
    val de: LocalDate,
    val ate: LocalDate,
    val squad: String?,
    val pessoa: String?,
    val sinal: CodigoSinal?,
    val resultado: Resultado?,
)

/** Lê e valida os filtros; qualquer valor ruim é 400 (nunca 500), sem eco do valor recebido. */
internal fun lerFiltrosAuditoria(params: Parameters, hoje: LocalDate): FiltrosAuditoria {
    val ate = data(params, "ate") ?: hoje
    val de = data(params, "de") ?: ate.minusDays(PERIODO_PADRAO_DIAS - 1)
    if (de.isAfter(ate) || ChronoUnit.DAYS.between(de, ate) + 1 > PERIODO_MAXIMO_DIAS) throw ErroHttp(HttpStatusCode.BadRequest)
    val sinal = texto(params, "sinal")?.let { nome ->
        CodigoSinal.entries.firstOrNull { it.name == nome } ?: throw ErroHttp(HttpStatusCode.BadRequest)
    }
    val resultado = when (texto(params, "resultado")?.lowercase(Locale.ROOT)) {
        null -> null
        "auditoria" -> Resultado.AUDITORIA
        "alerta" -> Resultado.ALERTA
        else -> throw ErroHttp(HttpStatusCode.BadRequest)
    }
    return FiltrosAuditoria(de, ate, texto(params, "squad"), texto(params, "pessoa")?.let(::usernameValido), sinal, resultado)
}

/** Contexto dos detectores montado do contexto do painel (relógio e fuso do painel). */
internal fun contextoDeDeteccao(ctx: AppContext) = ContextoDeteccao(
    ctx.regrasAuditoria, ctx.labelsDeAtraso, Autorizacao(ctx.roles, ctx.settings.zone), ctx.calendar, ctx.statusMap,
)

/** O squad existe hoje? Squad inexistente é 400, como no Tempo em status. */
private fun validarSquad(ctx: AppContext, squad: String?, hoje: LocalDate) {
    if (squad != null && ctx.roles.membersOf(squad, hoje).isEmpty()) throw ErroHttp(HttpStatusCode.BadRequest)
}

/**
 * Resultado da auditoria já filtrado e pela visão de quem pediu. [sinais] segue a ordem do calculador (data
 * decrescente, chave natural, código); [issues] serve ao resumo e ao status de cada linha.
 */
internal class AuditoriaCalculada(
    val filtros: FiltrosAuditoria,
    val resultado: ResultadoAuditoria,
    val sinais: List<Sinal>,
    val porCodigo: Map<CodigoSinal, Int>,
    val noPrazo: NoPrazoDetalhe,
    val issues: Map<String, AuditIssue>,
    val visiveis: Set<String>,
    val zona: ZoneId,
)

/**
 * Calcula a auditoria do período. Os achados saem do changelog na hora da consulta (nada é guardado). `squad` e `pessoa`
 * filtram os sinais; no "no prazo" vale só o `squad` (a taxa por pessoa não é exibida: não há ranking de pessoas).
 */
internal fun calcularAuditoria(ctx: AppContext, principal: Principal, f: FiltrosAuditoria): AuditoriaCalculada {
    val hoje = hoje(ctx)
    val zona = ctx.settings.zone
    validarSquad(ctx, f.squad, hoje)
    val store = SqliteAuditStore(requireNotNull(ctx.dataSource))
    val de = f.de.atStartOfDay(zona).toInstant()
    val ate = f.ate.plusDays(1).atStartOfDay(zona).toInstant()

    val comEventos = store.comEventos(INSTANCIA_AUDITORIA, de, ate)
    val concluidas = store.concluidasComVencimento(INSTANCIA_AUDITORIA, f.de, f.ate).filter { i ->
        f.squad == null || i.assignee?.let { ctx.roles.squadOf(it, i.resolved?.atZone(zona)?.toLocalDate() ?: hoje) } == f.squad
    }
    val pares = store.paresTaskReview(INSTANCIA_AUDITORIA, ctx.vinculos)
    val resultado = AuditCalculator(contextoDeDeteccao(ctx)).calcular(comEventos, concluidas, pares, de, ate, f.de, f.ate)

    fun casa(s: Sinal): Boolean {
        if (f.sinal != null && s.codigo != f.sinal) return false
        if (f.resultado != null && s.resultado != f.resultado) return false
        if (f.pessoa != null && s.responsavel != f.pessoa && s.autor != f.pessoa) return false
        if (f.squad != null) {
            val dia = s.at.atZone(zona).toLocalDate()
            if (s.responsavel?.let { ctx.roles.squadOf(it, dia) } != f.squad) return false
        }
        return true
    }
    val sinais = resultado.sinais.filter(::casa)

    val issues = (comEventos + pares.issues + resultado.noPrazoDetalhe.concluidas).associateBy { it.key }
    val existentes = sinais.flatMap { listOfNotNull(it.responsavel, it.autor) }.toSet() +
        issues.values.mapNotNull { it.assignee } + listOfNotNull(f.pessoa)
    val visiveis = Visibility.visibleUserIds(principal, ctx.roles, hoje, existentes)
    if (f.pessoa != null && f.pessoa !in visiveis) throw ErroHttp(HttpStatusCode.Forbidden)

    return AuditoriaCalculada(
        f, resultado, sinais, CodigoSinal.entries.associateWith { c -> sinais.count { it.codigo == c } },
        resultado.noPrazoDetalhe, issues, visiveis, zona,
    )
}

// ---- Conversões para as respostas ----

/** Resultado habitual de cada código (o de cada sinal vem no próprio sinal). */
internal fun resultadoHabitual(c: CodigoSinal): String =
    if (c == CodigoSinal.VENCIMENTO_TASK_REVIEW_DIVERGENTE) "alerta" else "auditoria"

internal fun nomeDoResultado(r: Resultado): String = r.name.lowercase(Locale.ROOT)

private fun Sinal.diaCivil(zona: ZoneId): String = at.atZone(zona).toLocalDate().toString()

internal fun paraDto(c: AuditoriaCalculada, ctx: AppContext): AuditoriaDto {
    val regras = ctx.regrasAuditoria
    return AuditoriaDto(
        aviso = AVISO_INDICIO_NAO_PROVA,
        versaoRegras = c.resultado.versaoRegras,
        total = c.sinais.size,
        porSinal = CodigoSinal.entries.map { cod ->
            ContagemSinalDto(cod.name, resultadoHabitual(cod), regras.sinais.getValue(cod).gravidade, c.porCodigo.getValue(cod))
        },
        sinais = c.sinais.map { s ->
            SinalDto(
                codigo = s.codigo.name, resultado = nomeDoResultado(s.resultado), gravidade = s.gravidade, key = s.issueKey,
                resumo = c.issues[s.issueKey]?.summary.orEmpty(), campo = s.campo, de = s.de, para = s.para,
                at = s.at.toString(), dia = s.diaCivil(c.zona), autor = s.autor, autorizado = s.autorizado,
                motivo = s.motivo.name, responsavel = s.responsavel?.takeIf { it in c.visiveis }, emLote = s.emLote,
            )
        },
        noPrazo = c.noPrazo.resultado.let {
            NoPrazoDto(it.concluidas, it.noPrazo, it.noPrazoAjustado, it.justificadasSobAuditoria, it.taxa, it.taxaAjustada)
        },
        avisos = c.resultado.avisos,
    )
}

private val DESCRICAO_PT = mapOf(
    CodigoSinal.VENCIMENTO_ALTERADO_NAO_AUTORIZADO to "Vencimento alterado por quem não podia",
    CodigoSinal.VENCIMENTO_REMOVIDO to "Vencimento removido",
    CodigoSinal.VENCIMENTO_TASK_REVIEW_DIVERGENTE to "Vencimentos da task e da Review passaram a divergir",
    CodigoSinal.RESPONSAVEL_TROCADO_NAO_AUTORIZADO to "Responsável trocado por quem não podia",
    CodigoSinal.PASSA_BOLA to "Passa-bola perto do vencimento",
    CodigoSinal.TROCA_PERTO_DO_VENCIMENTO to "Troca de responsável perto do vencimento",
    CodigoSinal.LABEL_APOS_FECHAMENTO_NAO_AUTORIZADO to "Label de atraso depois de fechada, por quem não podia",
    CodigoSinal.LABEL_PELO_RESPONSAVEL_APOS_FECHAMENTO to "Label de atraso depois de fechada, pelo próprio responsável",
    CodigoSinal.LABEL_TARDIA_COM_POSSE_ANTECIPADA to "Label de entrega tardia com posse antecipada",
    CodigoSinal.LABEL_APOS_VENCIMENTO to "Label de atraso depois do vencimento",
    CodigoSinal.CANCELAMENTO_SUSPEITO to "Cancelamento suspeito",
)

/** Linha da gaveta de tasks para um sinal: um sinal, uma linha (a mesma issue pode aparecer mais de uma vez). */
internal fun linhaDoSinal(s: Sinal, c: AuditoriaCalculada): DrillRow {
    val issue = c.issues[s.issueKey]
    val mudanca = "${s.campo}: ${s.de ?: "—"} → ${s.para ?: "—"}"
    val quem = s.autor?.takeIf { it.isNotBlank() } ?: "autor desconhecido"
    val situacao = if (s.autorizado) "autorizado" else "não autorizado"
    val lote = s.emLote?.let { " · em lote ($it)" }.orEmpty()
    val detalhe = "${if (s.resultado == Resultado.ALERTA) "●" else "▲"} ${DESCRICAO_PT.getValue(s.codigo)} ($mudanca) " +
        "em ${s.diaCivil(c.zona)} por $quem ($situacao)$lote"
    return DrillRow(s.issueKey, issue?.summary.orEmpty(), s.responsavel?.takeIf { it in c.visiveis }, issue?.status.orEmpty(), detalhe)
}

/** Sinais em ordem natural de chave, depois data decrescente (a ordem do contrato de cartões para os drills). */
internal fun linhasDeSinais(sinais: List<Sinal>, c: AuditoriaCalculada): List<DrillRow> =
    sinais.sortedWith(compareBy<Sinal, String>(ORDEM_NATURAL_DE_CHAVES) { it.issueKey }.thenByDescending { it.at }.thenBy { it.codigo })
        .map { linhaDoSinal(it, c) }

/** Linha para uma task concluída do "no prazo". */
internal fun linhaDeConcluida(i: AuditIssue, zona: ZoneId, visiveis: Set<String>): DrillRow {
    val dia = i.resolved?.atZone(zona)?.toLocalDate()
    return DrillRow(
        i.key, i.summary, i.assignee?.takeIf { it in visiveis }, i.status,
        "Resolvida em ${dia ?: "—"}; vencimento ${i.duedate ?: "—"}",
    )
}

// ---- Task × Review ----

/** Filtros do card Task × Review: o estado atual, sem período. */
internal class FiltrosTaskReview(val squad: String?, val pessoa: String?)

internal fun lerFiltrosTaskReview(params: Parameters): FiltrosTaskReview =
    FiltrosTaskReview(texto(params, "squad"), texto(params, "pessoa")?.let(::usernameValido))

internal class TaskReviewCalculado(
    val pares: List<ParTaskReview>,
    val resumo: ResumoTaskReview,
    val avisos: List<AvisoDto>,
    val visiveis: Set<String>,
)

internal fun calcularTaskReview(ctx: AppContext, principal: Principal, f: FiltrosTaskReview): TaskReviewCalculado {
    val hoje = hoje(ctx)
    validarSquad(ctx, f.squad, hoje)
    val lidos = SqliteAuditStore(requireNotNull(ctx.dataSource)).paresTaskReview(INSTANCIA_AUDITORIA, ctx.vinculos)
    val todos = TaskReview.pares(lidos.issues, lidos.vinculos)
    val existentes = todos.flatMap { listOfNotNull(it.task.assignee, it.review.assignee) }.toSet() + listOfNotNull(f.pessoa)
    val visiveis = Visibility.visibleUserIds(principal, ctx.roles, hoje, existentes)
    if (f.pessoa != null && f.pessoa !in visiveis) throw ErroHttp(HttpStatusCode.Forbidden)
    val pares = todos.filter { p ->
        (f.pessoa == null || p.task.assignee == f.pessoa || p.review.assignee == f.pessoa) &&
            (f.squad == null || p.task.assignee?.let { ctx.roles.squadOf(it, hoje) } == f.squad)
    }
    val avisos = buildList {
        if (lidos.ambiguas > 0) {
            add(AvisoDto(AvisoDto.VINCULO_AMBIGUO, AvisoDto.INFORMATIVO, "${lidos.ambiguas} vínculo(s) ambíguos", mapOf("n" to lidos.ambiguas.toString())))
        }
        if (lidos.semVinculos) add(AvisoDto(AvisoDto.VINCULOS_INDISPONIVEIS, AvisoDto.INFORMATIVO, "nenhum vínculo do tipo configurado na base"))
    }
    return TaskReviewCalculado(pares, TaskReview.resumo(pares), avisos, visiveis)
}

internal fun paraDto(c: TaskReviewCalculado, ctx: AppContext): TaskReviewDto = TaskReviewDto(
    pares = c.resumo.pares,
    divergentes = c.resumo.divergentes,
    percentual = c.resumo.percentual,
    avisos = c.avisos,
    divergencias = c.pares.filter { it.divergente }.map { p ->
        TaskReviewLinhaDto(
            task = p.task.key, review = p.review.key,
            vencimentoTask = p.task.duedate?.toString(), vencimentoReview = p.review.duedate?.toString(),
            responsavelTask = p.task.assignee?.takeIf { it in c.visiveis }, responsavelReview = p.review.assignee?.takeIf { it in c.visiveis },
            divergente = true,
            urlTask = LinkJira.paraIssue(ctx.settings, INSTANCIA_AUDITORIA, p.task.key),
            urlReview = LinkJira.paraIssue(ctx.settings, INSTANCIA_AUDITORIA, p.review.key),
        )
    },
)

/** Linhas da gaveta de Task × Review: uma por par, na chave da task. */
internal fun linhasDePares(pares: List<ParTaskReview>, visiveis: Set<String>): List<DrillRow> =
    pares.sortedWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.task.key }).map { p ->
        DrillRow(
            p.task.key, p.task.summary, p.task.assignee?.takeIf { it in visiveis }, p.task.status,
            "${if (p.divergente) "▲" else "✓"} Review ${p.review.key}: vencimento ${p.task.duedate ?: "—"} (task) × ${p.review.duedate ?: "—"} (Review)",
        )
    }
