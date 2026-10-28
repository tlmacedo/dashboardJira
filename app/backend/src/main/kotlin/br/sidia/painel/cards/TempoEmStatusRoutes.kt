package br.sidia.painel.cards

import br.sidia.painel.AppContext
import br.sidia.painel.cards.fluxo.Eixo
import br.sidia.painel.cards.fluxo.Medida
import br.sidia.painel.db.AccessLog
import br.sidia.painel.domain.IssueTimeline
import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.domain.TimeInStatusCalculator
import br.sidia.painel.domain.TimeInStatusResult
import br.sidia.painel.domain.TimeUnitMode
import br.sidia.painel.domain.UnmappedStatusException
import br.sidia.painel.http.ErroDto
import br.sidia.painel.lgpd.BloqueadoPeloDpoException
import br.sidia.painel.lgpd.TravaDpo
import br.sidia.painel.security.Principal
import br.sidia.painel.security.Role
import br.sidia.painel.security.Visibility
import br.sidia.painel.security.normalizarUsername
import br.sidia.painel.security.principal
import br.sidia.painel.sync.FiltroDeFicha
import br.sidia.painel.sync.IssueStore
import br.sidia.painel.sync.normalizarOsVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.util.AttributeKey
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

/** Id da ficha (`tempo-em-status.card.yaml`) cujos tipos e status excluídos valem também na leitura. */
private const val FICHA_TEMPO_EM_STATUS = "tempo-em-status"

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
private val FILTROS_AUDITADOS = setOf(
    "squad", "pessoa", "tipo", "os", "de", "ate", "unit", "semOutliers", "segmento", "sinal", "resultado",
    // Cards de trabalho e prazos (Plano 5).
    "prioridade", "granularidade", "movimento",
    // Cruzamentos (Plano 17b): só os ids dos eixos e da medida, nunca valor de eixo (que pode ser nome de pessoa).
    "linhas", "colunas", "medida",
)

private val FORMATO_USERNAME = Regex("[a-z0-9._-]{1,64}")

private val log = LoggerFactory.getLogger("br.sidia.painel.cards")

/**
 * Dados a mais para o `access_log`, postos pelo bloco da rota (ex.: `total`, a quantidade de linhas devolvidas).
 * Nunca chaves de issue, JQL nem endereço do Jira: a auditoria guarda rota e quantidade.
 */
internal val EXTRAS_DE_AUDITORIA = AttributeKey<Map<String, String>>("painel.auditoria.extras")

/**
 * Resposta de erro de requisição: só o status, nunca corpo (nada de eco de valores nem de detalhes). Com [codigo],
 * o corpo é o `ErroDto` com esse código estável (a tela monta o texto por ele); nunca leva o valor recebido.
 */
internal class ErroHttp(val status: HttpStatusCode, val codigo: String? = null, val params: Map<String, String> = emptyMap()) :
    RuntimeException(status.toString(), null, false, false)

internal class Filtros(
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
internal class Calculado(
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
internal sealed interface Detalhe {
    data object Analisadas : Detalhe
    data object Paradas : Detalhe
    data class PorStatus(val status: String) : Detalhe
    data class PorPessoaStatus(val pessoa: String, val status: String) : Detalhe
}

/**
 * Rotas `GET /api/me` e `GET /api/cards/tempo-em-status`. O detalhamento (as tasks por trás de cada número) é a rota
 * genérica `GET /api/cartoes/{id}/tasks` dos cartões TS-nn (ver `cards/contrato/DrillGenericoRoutes.kt`).
 * Toda leitura passa por [Visibility] (nega por padrão) e toda chamada, inclusive a negada, vai para o
 * `access_log`. Se o registro falhar, a resposta com dados não sai (503).
 */
fun Route.rotasTempoEmStatus(ctx: AppContext, store: IssueStore, auditoria: AccessLog) {
    get("/api/me") {
        call.executar(ctx, auditoria) {
            val principal = call.principal
            val papeis = papeisVigentes(ctx, principal)
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

}

/**
 * Papéis vigentes de quem pediu: sempre do diretório na data de hoje; só o principal `dev` do modo
 * desenvolvimento é a exceção (ele não existe no diretório).
 */
internal fun papeisVigentes(ctx: AppContext, principal: Principal): Set<Role> =
    if (ctx.settings.dashboardDevMode && principal.userId == ID_DEV) {
        principal.roles
    } else {
        ctx.roles.rolesOf(principal.userId, hoje(ctx))
    }

/**
 * Tasks de um segmento com os mesmos filtros do card. Único caminho do detalhamento de Tempo em status: os cartões
 * TS-nn passam por aqui.
 */
internal fun drillDeTempoEmStatus(
    ctx: AppContext, store: IssueStore, principal: Principal, params: Parameters, segmento: String?,
    aceita: (Detalhe) -> Boolean = { true },
): List<DrillRow> {
    val detalhe = lerDetalhe(segmento)
    if (!aceita(detalhe)) throw ErroHttp(HttpStatusCode.BadRequest, CODIGO_SEGMENTO_INVALIDO)
    val filtros = lerFiltros(params, hoje(ctx))
    val calculado = calcular(ctx, store, principal, filtros, (detalhe as? Detalhe.PorPessoaStatus)?.pessoa)
    // Pessoa fora da visibilidade: 403 (mesmo critério do filtro `pessoa` do card).
    if (detalhe is Detalhe.PorPessoaStatus && detalhe.pessoa !in calculado.visiveis) {
        throw ErroHttp(HttpStatusCode.Forbidden)
    }
    return linhasDoDetalhe(calculado, detalhe)
}

/** Código estável do 400 de segmento (ou `kind`) desconhecido ou malformado. */
internal const val CODIGO_SEGMENTO_INVALIDO = "SEGMENTO_INVALIDO"

/**
 * Grava a negação de um principal autenticado porém sem papel vigente (o controle de acesso responde 403
 * antes de chegar às rotas). Nunca lança: a negação acontece de qualquer jeito.
 */
suspend fun registrarNegacaoSemPapel(auditoria: AccessLog, ctx: AppContext, call: ApplicationCall, principal: Principal) {
    try {
        withContext(Dispatchers.IO) {
            auditoria.record(
                principal.userId.ifBlank { ID_NEUTRO }, rotaParaRegistro(call.request.path()),
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

/**
 * Rota como vai para o `access_log`: o PADRÃO, sem a chave de issue do caminho (a auditoria guarda rota e quantidade,
 * nunca chaves). Hoje só o histórico da auditoria leva a chave no caminho.
 */
internal fun rotaParaRegistro(caminho: String): String {
    val normal = normalizarCaminho(caminho)
    if (normal.startsWith("/api/tarefa/", ignoreCase = true)) return "/api/tarefa/{key}"
    return if (normal.startsWith("/api/auditoria/historico/", ignoreCase = true)) "/api/auditoria/historico/{key}" else normal
}

/**
 * Caminho como o roteador o enxerga e como a auditoria deve guardá-lo: percent-decode repetido (até 3 vezes, para `%252D`),
 * sem `;parametros`, sem barras duplas, `.` nem `..`. Um caminho codificado (`/api/%69ntegridade`) vira o mesmo texto do normal
 * e nunca escapa da máscara nem do rastro de quem consultou. Caixa preservada (a comparação ignora caixa onde importa).
 */
internal fun normalizarCaminho(caminho: String): String {
    var atual = caminho.substringBefore('?')
    repeat(3) {
        val decodificado = try {
            java.net.URLDecoder.decode(atual.replace("+", "%2B"), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            return@repeat
        }
        if (decodificado == atual) return@repeat
        atual = decodificado
    }
    val segmentos = ArrayDeque<String>()
    for (bruto in atual.replace('\\', '/').split('/')) {
        val s = bruto.substringBefore(';').filter { !it.isISOControl() }
        when (s) {
            "", "." -> Unit
            ".." -> segmentos.removeLastOrNull()
            else -> segmentos.addLast(s)
        }
    }
    return "/" + segmentos.joinToString("/")
}

/** Posta pela rota que atende um módulo sensível, mesmo antes de o caminho dizer: o registro mascara o achado. */
internal val ROTA_SENSIVEL = AttributeKey<Boolean>("painel.auditoria.sensivel")

internal fun hoje(ctx: AppContext): LocalDate = LocalDate.now(ctx.clock.withZone(ctx.settings.zone))

private fun filtrosAuditados(call: ApplicationCall): Map<String, String> {
    val sensivel = rotaSensivel(call.request.path()) || call.attributes.getOrNull(ROTA_SENSIVEL) == true
    return call.request.queryParameters.entries()
        .filter { it.key in FILTROS_AUDITADOS && !(sensivel && it.key in FILTROS_QUE_NOMEIAM_O_ACHADO) }
        .associate { it.key to valorAuditado(it.key, it.value.firstOrNull().orEmpty(), sensivel) }
}

/**
 * Rotas dos módulos sensíveis ([br.sidia.painel.lgpd.ModuloSensivel]) que HOJE passam pela `TravaDpo`: a Integridade e os
 * cartões `IN-nn`. O `access_log` guarda ali QUEM consultou o módulo, nunca o achado: sem id de pessoa, sem nome de sinal e sem
 * contagem. A decisão usa o caminho NORMALIZADO e sem diferenciar caixa, mais o atributo [ROTA_SENSIVEL] posto pela própria
 * rota/cartão resolvido. A Auditoria (`ModuloSensivel.AUDITORIA`) NÃO está aqui nem passa pela `TravaDpo` ainda: é do Plano 11;
 * um módulo novo que passe pela trava acrescenta o seu prefixo aqui.
 */
private val PREFIXOS_SENSIVEIS = listOf("/api/integridade", "/api/cartoes/in-", "/api/governanca/acessos")

internal fun rotaSensivel(caminho: String): Boolean {
    val normal = normalizarCaminho(caminho).lowercase(java.util.Locale.ROOT)
    return PREFIXOS_SENSIVEIS.any { normal.startsWith(it) }
}

/** Filtros que, numa rota sensível, apontariam para a pessoa ou o sinal consultado: não são gravados. */
private val FILTROS_QUE_NOMEIAM_O_ACHADO = setOf("pessoa", "sinal")

/** Segmentos sem pessoa nem sinal que podem ir inteiros para a auditoria de uma rota sensível. */
private val SEGMENTOS_NEUTROS = setOf("todas", "afetadas")

/** Marcador gravado no lugar de um eixo ou medida fora da lista fechada (o valor cru nunca vai para a auditoria). */
private const val VALOR_INVALIDO = "(invalido)"

/** Eixos e medida do Cruzamentos só vão ao `access_log` se forem ids da lista fechada; qualquer outro texto vira o marcador. */
private fun valorAuditado(parametro: String, valor: String, sensivel: Boolean): String = when (parametro) {
    "linhas", "colunas" -> if (Eixo.deId(valor) != null) valor else VALOR_INVALIDO
    "medida" -> if (Medida.deId(valor) != null) valor else VALOR_INVALIDO
    // Rota sensível: só o TIPO do segmento (`pessoa:*`, `nivel:*`, `sinal:*`), nunca o id da pessoa nem o nome do sinal.
    "segmento" -> when {
        sensivel -> segmentoSensivelParaRegistro(valor)
        // Segmento de gaveta que cita uma chave de issue (pedido malformado ou plantado; qualquer caixa): a chave nunca vai para a auditoria.
        CHAVE_DE_ISSUE_NO_TEXTO.containsMatchIn(valor) -> VALOR_INVALIDO
        // Label é texto livre do Jira (pode nomear alguém): a auditoria guarda que a consulta foi por label, não qual.
        valor.startsWith("label:", ignoreCase = true) -> "label:*"
        else -> valor
    }
    else -> valor
}

/** Forma de uma chave de issue do Jira (`TG-701`) em qualquer lugar do texto. */
private val CHAVE_DE_ISSUE_NO_TEXTO = Regex("\\b[A-Za-z][A-Za-z0-9_]*-\\d+\\b")

private val TIPOS_DE_SEGMENTO_SENSIVEL = setOf("pessoa", "nivel", "sinal")

/** `pessoa:bruno` vira `pessoa:*`; segmento neutro passa inteiro; qualquer outra coisa vira o marcador de inválido. */
private fun segmentoSensivelParaRegistro(valor: String): String {
    if (valor in SEGMENTOS_NEUTROS) return valor
    val tipo = valor.substringBefore(':')
    return if (':' in valor && tipo in TIPOS_DE_SEGMENTO_SENSIVEL) "$tipo:*" else VALOR_INVALIDO
}

/**
 * Executa [bloco] (fora do laço de eventos, pois usa JDBC), registra a consulta com o resultado e só então
 * responde. Erros de requisição viram status sem corpo; qualquer outra falha vira 500 sem detalhes
 * (o log do servidor guarda só o tipo da exceção). Um [ErroHttp] com código responde o `ErroDto` do código.
 */
internal suspend inline fun <reified T : Any> ApplicationCall.executar(
    ctx: AppContext,
    auditoria: AccessLog,
    crossinline bloco: suspend () -> T,
) {
    val principal = principal
    var status = HttpStatusCode.OK
    var corpo: T? = null
    var codigoDoErro: String? = null
    var paramsDoErro: Map<String, String> = emptyMap()
    try {
        corpo = withContext(Dispatchers.IO) { bloco() }
    } catch (e: ErroHttp) {
        status = e.status
        // Todo 400 sai com um código (a tela monta o texto por ele): sem código próprio, é filtro inválido.
        codigoDoErro = e.codigo ?: if (e.status == HttpStatusCode.BadRequest) CODIGO_FILTRO_INVALIDO else null
        paramsDoErro = e.params
    } catch (e: BloqueadoPeloDpoException) {
        // Módulo sensível fechado pela trava do DPO: 403 com o código estável, sem dado; a consulta fica no `access_log`.
        status = HttpStatusCode.Forbidden
        codigoDoErro = TravaDpo.CODIGO
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.error("Falha ao montar a resposta de {} ({})", rotaParaRegistro(request.path()), e.javaClass.simpleName)
        status = HttpStatusCode.InternalServerError
    }
    try {
        withContext(Dispatchers.IO) {
            auditoria.record(
                principal.userId, rotaParaRegistro(request.path()),
                filtrosAuditados(this@executar) + (attributes.getOrNull(EXTRAS_DE_AUDITORIA).orEmpty()) +
                    ("resultado" to status.value.toString()),
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
    when {
        corpo != null -> respond(corpo)
        codigoDoErro != null -> respond(status, ErroDto(codigoDoErro, paramsDoErro))
        else -> respond(status)
    }
}

// ---- Leitura e validação dos parâmetros ----

internal fun texto(params: Parameters, nome: String): String? {
    val valor = params[nome]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (valor.length > MAX_FILTRO || valor.any { it.isISOControl() }) throw ErroHttp(HttpStatusCode.BadRequest)
    return valor
}

internal fun data(params: Parameters, nome: String): LocalDate? =
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

internal fun lerFiltros(params: Parameters, hoje: LocalDate): Filtros {
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

internal fun usernameValido(bruto: String): String {
    val id = try {
        normalizarUsername(bruto)
    } catch (e: IllegalArgumentException) {
        throw ErroHttp(HttpStatusCode.BadRequest)
    }
    if (!FORMATO_USERNAME.matches(id)) throw ErroHttp(HttpStatusCode.BadRequest)
    return id
}

internal fun lerDetalhe(kind: String?): Detalhe {
    val invalido = { ErroHttp(HttpStatusCode.BadRequest, CODIGO_SEGMENTO_INVALIDO) }
    val k = kind?.trim().orEmpty()
    if (k.isEmpty() || k.length > MAX_FILTRO * 2 || k.any { it.isISOControl() }) throw invalido()
    return when {
        k == "analisadas" -> Detalhe.Analisadas
        k == "paradas" -> Detalhe.Paradas
        k.startsWith("status:") -> Detalhe.PorStatus(k.removePrefix("status:").trim().ifEmpty { throw invalido() })
        k.startsWith("pessoa:") -> {
            // O id de usuário não tem ':', então o primeiro ":status:" separa a pessoa do status.
            val resto = k.removePrefix("pessoa:")
            val corte = resto.indexOf(":status:")
            if (corte < 0) throw invalido()
            val status = resto.substring(corte + ":status:".length).trim()
            if (status.isEmpty()) throw invalido()
            Detalhe.PorPessoaStatus(usernameValido(resto.substring(0, corte)), status)
        }
        else -> throw invalido()
    }
}

// ---- Cálculo filtrado pela visibilidade ----

/** Todo username que teve ou tem a issue (inclusive o responsável inicial e os das trocas). */
private fun pessoasDe(t: IssueTimeline): Set<String> = buildSet {
    t.initialAssignee?.let(::add)
    t.assigneeChanges.forEach { c -> c.to?.let(::add) }
    t.currentAssignee?.let(::add)
}

internal fun calcular(
    ctx: AppContext,
    store: IssueStore,
    principal: Principal,
    f: Filtros,
    // Pessoa pedida no detalhamento: entra entre os candidatos mesmo sem nenhuma issue (a própria pessoa recebe [], não 403).
    pessoaExtra: String? = null,
): Calculado {
    val hoje = hoje(ctx)
    val zona = ctx.settings.zone
    val agora = ctx.clock.instant()

    // squad é NOME de squad; resolve em usernames pelo diretório na data de hoje (nunca lista digitada).
    val membrosDoSquad = f.squad?.let { nome ->
        ctx.roles.membersOf(nome, hoje).takeIf { it.isNotEmpty() } ?: throw ErroHttp(HttpStatusCode.BadRequest)
    }

    // Só o que a ficha manda (tipos e status excluídos) também na leitura: a base guarda tudo que foi sincronizado.
    val filtroDaFicha = FiltroDeFicha.de(ctx.fichas.getValue(FICHA_TEMPO_EM_STATUS))
    val fechadasBrutas = store.timelines(
        INSTANCIA_TEMPO_EM_STATUS,
        f.de.atStartOfDay(zona).toInstant(),
        f.ate.plusDays(1).atStartOfDay(zona).toInstant(),
        false,
        f.os,
        filtroDaFicha,
    )
    val abertasBrutas = store.timelines(INSTANCIA_TEMPO_EM_STATUS, null, null, true, f.os, filtroDaFicha)

    // Concluída = categoria DONE E resolution (spec 4.2). Divergente (um sem o outro) não entra em nenhum
    // número e é só contada, para a Qualidade dos dados. Status sem mapeamento não é classificado aqui:
    // o cálculo abaixo avisa dele.
    fun categoria(t: IssueTimeline): StatusCategory? =
        try {
            ctx.statusMap.category(t.currentStatus)
        } catch (e: UnmappedStatusException) {
            null
        }

    fun temResolucao(t: IssueTimeline) = !t.resolution.isNullOrBlank()

    fun divergente(t: IssueTimeline) = categoria(t)?.let { (it == StatusCategory.DONE) != temResolucao(t) } ?: false

    fun concluida(t: IssueTimeline) = categoria(t) == StatusCategory.DONE && temResolucao(t)
    val fechadas = fechadasBrutas.filterNot(::divergente)
    // A consulta das abertas também traz as sem resolution: só ficam as realmente abertas.
    val abertas = abertasBrutas.filterNot { divergente(it) || concluida(it) }

    val existentes = (fechadasBrutas + abertasBrutas).flatMapTo(mutableSetOf()) { pessoasDe(it) }
    val visiveis = Visibility.visibleUserIds(principal, ctx.roles, hoje, existentes + listOfNotNull(f.pessoa, pessoaExtra))
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
    // Divergentes (de quem o usuário poderia ver), contadas uma vez só mesmo quando vêm das duas consultas.
    val divergentes = (fechadasBrutas + abertasBrutas).filter { candidata(it) && divergente(it) }.map { it.key }.toSet().size
    if (divergentes > 0) {
        avisos += AvisoDto(
            AvisoDto.CONCLUSAO_DIVERGENTE, AvisoDto.INFORMATIVO,
            "$divergentes issue(s) com status e resolução divergentes ignoradas",
            mapOf("n" to divergentes.toString()),
        )
    }
    // Concluída (DONE + resolution) sem data de resolução não cabe em nenhuma lista: avisa em vez de sumir.
    val semData = (fechadasBrutas + abertasBrutas).filter { candidata(it) && concluida(it) && it.resolved == null }
        .map { it.key }.toSet().size
    if (semData > 0) {
        avisos += AvisoDto(
            AvisoDto.CONCLUIDA_SEM_DATA, AvisoDto.INFORMATIVO,
            "$semData issue(s) concluídas sem data de resolução ignoradas",
            mapOf("n" to semData.toString()),
        )
    }
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
        avisos += AvisoDto(
            AvisoDto.STATUS_SEM_MAPEAMENTO, AvisoDto.BLOQUEANTE, "status sem mapeamento: ${e.status}",
            mapOf("status" to e.status),
        )
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

internal fun linhasDoDetalhe(c: Calculado, detalhe: Detalhe): List<DrillRow> {
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
