package br.sidia.painel.cards

import br.sidia.painel.AppContext
import br.sidia.painel.db.AccessLog
import br.sidia.painel.domain.IssueTimeline
import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.domain.TimeInStatusCalculator
import br.sidia.painel.domain.TimeInStatusResult
import br.sidia.painel.domain.TimeUnitMode
import br.sidia.painel.domain.UnmappedStatusException
import br.sidia.painel.security.Principal
import br.sidia.painel.security.Visibility
import br.sidia.painel.security.normalizarUsername
import br.sidia.painel.security.principal
import br.sidia.painel.sync.IssueStore
import br.sidia.painel.sync.normalizarOsVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Instância do Jira de onde o card lê as issues (a mesma da ficha `tempo-em-status.card.yaml`). */
const val INSTANCIA_TEMPO_EM_STATUS = "samsung"

/** Padrão do período: os últimos 90 dias até hoje (fuso do painel). */
private const val PERIODO_PADRAO_DIAS = 90L

/** Teto do período pedido, para uma consulta não varrer o banco inteiro por engano. */
private const val PERIODO_MAXIMO_DIAS = 3660L

/** Com `semOutliers=true`, tasks com ciclo maior que isso (dias corridos) ficam fora. */
private const val OUTLIER_DIAS = 30

/** Faixa de anos aceita em `de`/`ate`. */
private const val ANO_MINIMO = 1970
private const val ANO_MAXIMO = 2200

/** Id gravado no `access_log` quando o id do principal negado é vazio (não há usuário a registrar). */
private const val ID_NEUTRO = "(sem-id)"

/** Tamanho máximo de qualquer filtro textual. */
private const val MAX_FILTRO = 100

/** Id reservado do principal do modo desenvolvimento (ver `RoleDirectory`). */
private const val ID_DEV = "dev"

/** Única lista de filtros que vai para a auditoria: o que não está aqui nunca é gravado. */
private val FILTROS_AUDITADOS = setOf("squad", "pessoa", "tipo", "os", "de", "ate", "unit", "semOutliers", "kind")

private val FORMATO_USERNAME = Regex("[a-z0-9._-]{1,64}")

private val log = LoggerFactory.getLogger("br.sidia.painel.cards")

/** Resposta de erro de requisição: só o status, nunca corpo (nada de eco de valores nem de detalhes). */
private class ErroHttp(val status: HttpStatusCode) : RuntimeException(status.toString(), null, false, false)

private class Filtros(
    val squad: String?,
    val pessoa: String?,
    val tipo: String?,
    val os: String?,
    val de: LocalDate,
    val ate: LocalDate,
    val unit: TimeUnitMode,
    val semOutliers: Boolean,
)

/** Cálculo já filtrado pela visibilidade de quem pediu; base do card e do detalhamento. */
private class Calculado(
    val dto: TempoEmStatusDto,
    val resultado: TimeInStatusResult?,
    val calculador: TimeInStatusCalculator,
    val concluidas: Map<String, IssueTimeline>,
    val visiveis: Set<String>,
    val permitidos: Set<String>,
    val agora: Instant,
    val unit: TimeUnitMode,
    val zona: ZoneId,
)

/** O que o detalhamento pede (`kind`). */
private sealed interface Detalhe {
    data object Analisadas : Detalhe
    data object Paradas : Detalhe
    data class PorStatus(val status: String) : Detalhe
    data class PorPessoaStatus(val pessoa: String, val status: String) : Detalhe
}

/**
 * Rotas `GET /api/me`, `GET /api/cards/tempo-em-status` e `GET /api/drill/tempo-em-status`.
 * Toda leitura passa por [Visibility] (nega por padrão) e toda chamada, inclusive a negada, vai para o
 * `access_log`. Se o registro falhar, a resposta com dados não sai (503).
 */
fun Route.rotasTempoEmStatus(ctx: AppContext, store: IssueStore, auditoria: AccessLog) {
    get("/api/me") {
        call.executar(ctx, auditoria) {
            val principal = call.principal
            val hoje = hoje(ctx)
            // Papéis sempre do diretório na data de hoje; só o principal `dev` do modo desenvolvimento é a exceção.
            val papeis = if (ctx.settings.dashboardDevMode && principal.userId == ID_DEV) {
                principal.roles
            } else {
                ctx.roles.rolesOf(principal.userId, hoje)
            }
            if (papeis.isEmpty()) throw ErroHttp(HttpStatusCode.Forbidden)
            MeDto(principal.userId, papeis.sorted().map { it.name })
        }
    }

    get("/api/cards/tempo-em-status") {
        call.executar(ctx, auditoria) {
            val principal = call.principal
            val filtros = lerFiltros(call.request.queryParameters, hoje(ctx))
            calcular(ctx, store, principal, filtros).dto
        }
    }

    get("/api/drill/tempo-em-status") {
        call.executar(ctx, auditoria) {
            val principal = call.principal
            val detalhe = lerDetalhe(call.request.queryParameters["kind"])
            val filtros = lerFiltros(call.request.queryParameters, hoje(ctx))
            val calculado = calcular(ctx, store, principal, filtros)
            // Pessoa fora da visibilidade: 403 (mesmo critério do filtro `pessoa` do card).
            if (detalhe is Detalhe.PorPessoaStatus && detalhe.pessoa !in calculado.visiveis) {
                throw ErroHttp(HttpStatusCode.Forbidden)
            }
            linhasDoDetalhe(calculado, detalhe)
        }
    }
}

/**
 * Grava a negação de um principal autenticado porém sem papel vigente (o controle de acesso responde 403
 * antes de chegar às rotas). Nunca lança: a negação acontece de qualquer jeito.
 */
suspend fun registrarNegacaoSemPapel(auditoria: AccessLog, ctx: AppContext, call: ApplicationCall, principal: Principal) {
    try {
        withContext(Dispatchers.IO) {
            auditoria.record(
                principal.userId.ifBlank { ID_NEUTRO }, call.request.path(),
                filtrosAuditados(call) + ("resultado" to HttpStatusCode.Forbidden.value.toString()),
                ctx.clock.instant(),
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.error("Falha ao registrar acesso negado no access_log ({})", e.javaClass.simpleName)
    }
}

private fun hoje(ctx: AppContext): LocalDate = LocalDate.now(ctx.clock.withZone(ctx.settings.zone))

private fun filtrosAuditados(call: ApplicationCall): Map<String, String> =
    call.request.queryParameters.entries()
        .filter { it.key in FILTROS_AUDITADOS }
        .associate { it.key to it.value.firstOrNull().orEmpty() }

/**
 * Executa [bloco] (fora do laço de eventos, pois usa JDBC), registra a consulta com o resultado e só então
 * responde. Erros de requisição viram status sem corpo; qualquer outra falha vira 500 sem detalhes
 * (o log do servidor guarda só o tipo da exceção).
 */
private suspend inline fun <reified T : Any> ApplicationCall.executar(
    ctx: AppContext,
    auditoria: AccessLog,
    crossinline bloco: () -> T,
) {
    val principal = principal
    var status = HttpStatusCode.OK
    var corpo: T? = null
    try {
        corpo = withContext(Dispatchers.IO) { bloco() }
    } catch (e: ErroHttp) {
        status = e.status
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.error("Falha ao montar a resposta de {} ({})", request.path(), e.javaClass.simpleName)
        status = HttpStatusCode.InternalServerError
    }
    try {
        withContext(Dispatchers.IO) {
            auditoria.record(
                principal.userId, request.path(),
                filtrosAuditados(this@executar) + ("resultado" to status.value.toString()),
                ctx.clock.instant(),
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Sem registro de auditoria não há resposta com dados.
        log.error("Falha ao gravar no access_log ({})", e.javaClass.simpleName)
        respond(HttpStatusCode.ServiceUnavailable)
        return
    }
    if (corpo != null) respond(corpo) else respond(status)
}

// ---- Leitura e validação dos parâmetros ----

private fun texto(params: Parameters, nome: String): String? {
    val valor = params[nome]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (valor.length > MAX_FILTRO || valor.any { it.isISOControl() }) throw ErroHttp(HttpStatusCode.BadRequest)
    return valor
}

private fun data(params: Parameters, nome: String): LocalDate? =
    texto(params, nome)?.let {
        val d = try {
            LocalDate.parse(it)
        } catch (e: java.time.format.DateTimeParseException) {
            throw ErroHttp(HttpStatusCode.BadRequest)
        }
        // Datas extremas (ex.: +999999999-12-31) estourariam as contas de período: 400, nunca 500.
        if (d.year !in ANO_MINIMO..ANO_MAXIMO) throw ErroHttp(HttpStatusCode.BadRequest)
        d
    }

private fun lerFiltros(params: Parameters, hoje: LocalDate): Filtros {
    val ate = data(params, "ate") ?: hoje
    val de = data(params, "de") ?: ate.minusDays(PERIODO_PADRAO_DIAS)
    if (de.isAfter(ate) || de.plusDays(PERIODO_MAXIMO_DIAS).isBefore(ate)) throw ErroHttp(HttpStatusCode.BadRequest)
    val unit = when (texto(params, "unit")?.lowercase(Locale.ROOT)) {
        null, "util" -> TimeUnitMode.UTIL
        "corrido" -> TimeUnitMode.CORRIDO
        else -> throw ErroHttp(HttpStatusCode.BadRequest)
    }
    val semOutliers = when (texto(params, "semOutliers")?.lowercase(Locale.ROOT)) {
        null, "false" -> false
        "true" -> true
        else -> throw ErroHttp(HttpStatusCode.BadRequest)
    }
    return Filtros(
        squad = texto(params, "squad"),
        pessoa = texto(params, "pessoa")?.let(::usernameValido),
        tipo = texto(params, "tipo"),
        // Mesma normalização do valor gravado (espaços internos colapsados); só espaços vira "sem filtro".
        os = texto(params, "os")?.let(::normalizarOsVersion)?.ifEmpty { null },
        de = de,
        ate = ate,
        unit = unit,
        semOutliers = semOutliers,
    )
}

private fun usernameValido(bruto: String): String {
    val id = try {
        normalizarUsername(bruto)
    } catch (e: IllegalArgumentException) {
        throw ErroHttp(HttpStatusCode.BadRequest)
    }
    if (!FORMATO_USERNAME.matches(id)) throw ErroHttp(HttpStatusCode.BadRequest)
    return id
}

private fun lerDetalhe(kind: String?): Detalhe {
    val k = kind?.trim().orEmpty()
    if (k.isEmpty() || k.length > MAX_FILTRO * 2 || k.any { it.isISOControl() }) throw ErroHttp(HttpStatusCode.BadRequest)
    return when {
        k == "analisadas" -> Detalhe.Analisadas
        k == "paradas" -> Detalhe.Paradas
        k.startsWith("status:") -> Detalhe.PorStatus(k.removePrefix("status:").trim().ifEmpty { throw ErroHttp(HttpStatusCode.BadRequest) })
        k.startsWith("pessoa:") -> {
            // O id de usuário não tem ':', então o primeiro ":status:" separa a pessoa do status.
            val resto = k.removePrefix("pessoa:")
            val corte = resto.indexOf(":status:")
            if (corte < 0) throw ErroHttp(HttpStatusCode.BadRequest)
            val status = resto.substring(corte + ":status:".length).trim()
            if (status.isEmpty()) throw ErroHttp(HttpStatusCode.BadRequest)
            Detalhe.PorPessoaStatus(usernameValido(resto.substring(0, corte)), status)
        }
        else -> throw ErroHttp(HttpStatusCode.BadRequest)
    }
}

// ---- Cálculo filtrado pela visibilidade ----

/** Todo username que teve ou tem a issue (inclusive o responsável inicial e os das trocas). */
private fun pessoasDe(t: IssueTimeline): Set<String> = buildSet {
    t.initialAssignee?.let(::add)
    t.assigneeChanges.forEach { c -> c.to?.let(::add) }
    t.currentAssignee?.let(::add)
}

private fun calcular(ctx: AppContext, store: IssueStore, principal: Principal, f: Filtros): Calculado {
    val hoje = hoje(ctx)
    val zona = ctx.settings.zone
    val agora = ctx.clock.instant()

    // squad é NOME de squad; resolve em usernames pelo diretório na data de hoje (nunca lista digitada).
    val membrosDoSquad = f.squad?.let { nome ->
        ctx.roles.membersOf(nome, hoje).takeIf { it.isNotEmpty() } ?: throw ErroHttp(HttpStatusCode.BadRequest)
    }

    val fechadas = store.timelines(
        INSTANCIA_TEMPO_EM_STATUS,
        f.de.atStartOfDay(zona).toInstant(),
        f.ate.plusDays(1).atStartOfDay(zona).toInstant(),
        false,
        f.os,
    )
    val abertas = store.timelines(INSTANCIA_TEMPO_EM_STATUS, null, null, true, f.os)

    val existentes = (fechadas + abertas).flatMapTo(mutableSetOf()) { pessoasDe(it) }
    val visiveis = Visibility.visibleUserIds(principal, ctx.roles, hoje, existentes + listOfNotNull(f.pessoa))
    if (f.pessoa != null && f.pessoa !in visiveis) throw ErroHttp(HttpStatusCode.Forbidden)

    var permitidos = visiveis
    if (membrosDoSquad != null) permitidos = permitidos intersect membrosDoSquad
    if (f.pessoa != null) permitidos = permitidos intersect setOf(f.pessoa)

    val avisos = mutableListOf<AvisoDto>()
    // Sem nenhuma issue com OS Version (customId ainda não descoberto), o filtro não tem o que casar: avisa.
    if (f.os != null && !store.temOsVersion(INSTANCIA_TEMPO_EM_STATUS)) {
        avisos += AvisoDto(AvisoDto.OS_INDISPONIVEL, AvisoDto.INFORMATIVO, "OS Version não disponível nos dados sincronizados")
    }

    val calculador = TimeInStatusCalculator(ctx.calendar, ctx.statusMap)
    fun candidata(t: IssueTimeline) =
        pessoasDe(t).any { it in permitidos } && (f.tipo == null || t.issueType.equals(f.tipo, ignoreCase = true))

    // Issue com datas inconsistentes (ex.: resolução antes da criação) não derruba o card: é ignorada e só
    // o total (das que o usuário poderia ver) é avisado, nunca as chaves.
    val inconsistentes = (fechadas + abertas).count { candidata(it) && calculador.datasInconsistentes(it, agora) }
    if (inconsistentes > 0) avisos += AvisoDto(AvisoDto.DATAS_INCONSISTENTES, AvisoDto.INFORMATIVO, "$inconsistentes issue(s) com datas inconsistentes ignoradas")
    val fechadasOk = fechadas.filterNot { calculador.datasInconsistentes(it, agora) }
    val abertasOk = abertas.filterNot { calculador.datasInconsistentes(it, agora) }

    var resultado: TimeInStatusResult? = null
    var concluidas = emptyMap<String, IssueTimeline>()
    try {
        // Só entram issues em que alguém visível teve posse (algum intervalo de responsável permitido).
        fun comPosse(t: IssueTimeline) = calculador.intervalos(t, agora).any { it.assignee != null && it.assignee in permitidos }
        val fechadasDoUsuario = fechadasOk.filter(::candidata).filter(::comPosse)
        val abertasDoUsuario = abertasOk.filter(::candidata).filter(::comPosse)
        resultado = calculador.compute(
            fechadasDoUsuario, abertasDoUsuario, agora, f.unit, if (f.semOutliers) OUTLIER_DIAS else null,
        )
        val analisadas = resultado.analyzed.toSet()
        concluidas = fechadasDoUsuario.filter { it.key in analisadas }.associateBy { it.key }
    } catch (e: UnmappedStatusException) {
        avisos += AvisoDto(AvisoDto.STATUS_SEM_MAPEAMENTO, AvisoDto.BLOQUEANTE, "status sem mapeamento: ${e.status}")
    } catch (e: IllegalArgumentException) {
        // Dado inconsistente (ex.: resolução antes da criação): o cálculo fica indisponível, sem detalhes.
        avisos += AvisoDto(AvisoDto.CALCULO_INDISPONIVEL, AvisoDto.BLOQUEANTE, "dados inconsistentes em algumas issues: cálculo indisponível")
    }

    val dto = TempoEmStatusDto(
        analisadas = resultado?.analyzed?.size ?: 0,
        eficienciaFluxo = resultado?.flowEfficiency,
        paradas = resultado?.stuck?.size ?: 0,
        porStatus = resultado?.perStatus.orEmpty(),
        // Ordem alfabética (sem ranking) e só pessoas permitidas: o nome de quem está fora nunca sai.
        pessoaStatus = resultado?.perPersonStatus.orEmpty().filter { it.person in permitidos },
        abertasParadas = resultado?.stuck.orEmpty().map { s ->
            if (s.assignee != null && s.assignee !in visiveis) s.copy(assignee = null) else s
        },
        unit = if (f.unit == TimeUnitMode.UTIL) "util" else "corrido",
        avisos = avisos,
    )
    return Calculado(dto, resultado, calculador, concluidas, visiveis, permitidos, agora, f.unit, zona)
}

// ---- Detalhamento ----

private fun numero(v: Double): String = String.format(Locale.ROOT, "%.1f", v)

private fun rotuloDaUnidade(u: TimeUnitMode) = if (u == TimeUnitMode.UTIL) "dias úteis" else "dias corridos"

private fun linhasDoDetalhe(c: Calculado, detalhe: Detalhe): List<DrillRow> {
    val resultado = c.resultado ?: return emptyList()
    val unidade = rotuloDaUnidade(c.unit)
    fun visivel(pessoa: String?) = pessoa?.takeIf { it in c.visiveis }

    fun intervalosDe(t: IssueTimeline) = c.calculador.intervalos(t, c.agora).filter { it.category != StatusCategory.DONE }

    val linhas: List<DrillRow> = when (detalhe) {
        Detalhe.Analisadas -> c.concluidas.values.map { t ->
            val resolvida = requireNotNull(t.resolved)
            val ciclo = c.calculador.duracao(intervaloInteiro(t, resolvida), c.unit)
            DrillRow(
                t.key, t.summary, visivel(t.currentAssignee), t.currentStatus,
                "Concluída em ${resolvida.atZone(c.zona).toLocalDate()}; ciclo de ${numero(ciclo)} $unidade",
            )
        }

        Detalhe.Paradas -> resultado.stuck.map { s ->
            DrillRow(
                s.key, s.summary, visivel(s.assignee), s.status,
                "${numero(s.daysInStatus)} $unidade em ${s.status} (mediana ${numero(s.median)})",
            )
        }

        is Detalhe.PorStatus -> c.concluidas.values.mapNotNull { t ->
            val ivs = intervalosDe(t).filter { it.status == detalhe.status }
            if (ivs.isEmpty()) return@mapNotNull null
            val total = ivs.sumOf { c.calculador.duracao(it, c.unit) }
            DrillRow(
                t.key, t.summary, visivel(t.currentAssignee), detalhe.status,
                "${numero(total)} $unidade em ${detalhe.status}",
            )
        }

        is Detalhe.PorPessoaStatus -> {
            // Pessoa visível mas fora do filtro atual (ex.: outro squad) não tem linha no card: lista vazia.
            if (detalhe.pessoa !in c.permitidos) {
                emptyList()
            } else {
                c.concluidas.values.mapNotNull { t ->
                    val ivs = intervalosDe(t).filter { it.status == detalhe.status && it.assignee == detalhe.pessoa }
                    if (ivs.isEmpty()) return@mapNotNull null
                    val total = ivs.sumOf { c.calculador.duracao(it, c.unit) }
                    DrillRow(
                        t.key, t.summary, detalhe.pessoa, detalhe.status,
                        "${numero(total)} $unidade de ${detalhe.pessoa} em ${detalhe.status}",
                    )
                }
            }
        }
    }
    return linhas.sortedWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.key })
}

/** Intervalo criação → resolução, só para medir o ciclo com a mesma régua do calculador. */
private fun intervaloInteiro(t: IssueTimeline, resolvida: Instant) =
    br.sidia.painel.domain.StatusInterval(t.currentStatus, StatusCategory.DONE, t.created, resolvida, t.currentAssignee)
