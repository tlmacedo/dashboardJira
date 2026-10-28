package br.sidia.painel.integridade

import br.sidia.painel.AppContext
import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.DrillRow
import br.sidia.painel.cards.ErroHttp
import br.sidia.painel.cards.INSTANCIA_TRABALHO
import br.sidia.painel.cards.data
import br.sidia.painel.cards.hoje
import br.sidia.painel.cards.metas.CardMetas
import br.sidia.painel.cards.metas.MetasDto
import br.sidia.painel.cards.montarEntrada
import br.sidia.painel.cards.calcularSeguro
import br.sidia.painel.cards.papeisVigentes
import br.sidia.painel.cards.texto
import br.sidia.painel.cards.usernameValido
import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.UnmappedStatusException
import br.sidia.painel.security.Principal
import br.sidia.painel.security.Role
import br.sidia.painel.sync.FiltroDeFicha
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.Situacao
import br.sidia.painel.trabalho.WorkStore
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.parametersOf
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Período padrão da Integridade: os últimos 90 dias de Manaus, `ate` inclusivo. */
private const val PERIODO_PADRAO_DIAS = 90L

/** Máximo de dias (inclusive) de um período pedido. */
internal const val PERIODO_MAXIMO_DIAS = 180L

/** Corte da lista de evidências da resposta (os números e a gaveta trazem todas). */
internal const val LIMITE_DE_EVIDENCIAS = 600

/** Aviso de que a lista de evidências foi cortada (n mostradas de total). */
const val AVISO_LISTA_PARCIAL_INTEGRIDADE = "EVIDENCIAS_PARCIAIS"

/** Campos do changelog que os sinais leem: uma task entra no período se tem evento de algum deles nele. */
private val CAMPOS_DOS_SINAIS = setOf("status", "assignee", "resolution", "duedate", "labels", "timeoriginalestimate")

/** SÓ o GERENTE vê a Integridade (spec 7.3 e 4.3): qualquer outro papel é 403, que também vai para o `access_log`. */
internal fun exigirGerente(ctx: AppContext, principal: Principal) {
    if (Role.GERENTE !in papeisVigentes(ctx, principal)) throw ErroHttp(HttpStatusCode.Forbidden)
}

/**
 * Filtros da Integridade. Período e squad definem o UNIVERSO (os níveis e a mediana saem dele); `sinal` e `pessoa` só recortam a
 * lista de evidências e as pessoas mostradas, para o nível de alguém nunca depender de quem o gerente resolveu filtrar.
 */
internal class FiltrosIntegridade(val de: LocalDate, val ate: LocalDate, val squad: String?, val pessoa: String?, val sinal: SinalIntegridade?)

/** Lê e valida os filtros; qualquer valor ruim é 400 (nunca 500), sem eco do valor recebido. */
internal fun lerFiltrosIntegridade(params: Parameters, hoje: LocalDate): FiltrosIntegridade {
    val ate = data(params, "ate") ?: hoje
    val de = data(params, "de") ?: ate.minusDays(PERIODO_PADRAO_DIAS - 1)
    if (de.isAfter(ate) || ChronoUnit.DAYS.between(de, ate) + 1 > PERIODO_MAXIMO_DIAS) throw ErroHttp(HttpStatusCode.BadRequest)
    val sinal = texto(params, "sinal")?.let { nome -> SinalIntegridade.entries.firstOrNull { it.name == nome } ?: throw ErroHttp(HttpStatusCode.BadRequest) }
    return FiltrosIntegridade(de, ate, texto(params, "squad"), texto(params, "pessoa")?.let(::usernameValido), sinal)
}

/**
 * O resultado da Integridade de um período, já filtrado. [evidencias] é a lista mostrada (recortada por sinal e pessoa, em ordem
 * natural de chave); [universo] são as evidências do período e do squad, de onde saem os níveis. [bloqueado]: há status fora do
 * mapa, os números ficam zerados e o aviso diz por quê.
 */
internal class IntegridadeCalculada(
    val filtros: FiltrosIntegridade,
    val evidencias: List<EvidenciaIntegridade>,
    val resumo: ResumoIntegridade,
    val pessoasListadas: List<NivelPessoa>,
    val issues: Map<String, IssueSnapshot>,
    val metas: MetasDto?,
    val avisos: List<AvisoDto>,
    val zona: ZoneId,
    val bloqueado: Boolean,
) {
    private val nivelDe: Map<String, NivelIntegridade> = resumo.pessoas.associate { it.pessoa to it.nivel }

    /** Evidências das pessoas de um nível (as da lista mostrada). */
    fun doNivel(nivel: NivelIntegridade): List<EvidenciaIntegridade> = evidencias.filter { e -> e.pessoa?.let { nivelDe[it] } == nivel }

    /** Evidências de uma pessoa que está na lista de alto e médio (senão, o segmento é inválido). */
    fun daPessoa(id: String): List<EvidenciaIntegridade>? = if (pessoasListadas.any { it.pessoa == id }) evidencias.filter { it.pessoa == id } else null

    fun doSinal(s: SinalIntegridade): List<EvidenciaIntegridade> = evidencias.filter { it.sinal == s }

    val issuesAfetadas: List<String> get() = evidencias.map { it.issueKey }.distinct().sortedWith(ORDEM_NATURAL_DE_CHAVES)
}

private fun vazia(f: FiltrosIntegridade, regras: RegrasDeIntegridade, zona: ZoneId, avisos: List<AvisoDto>) = IntegridadeCalculada(
    f, emptyList(), NiveisPorPessoa.resumir(emptyList(), emptyMap(), regras, null), emptyList(), emptyMap(), null, avisos, zona, bloqueado = true,
)

/**
 * Calcula a Integridade do período a partir do changelog, na hora da consulta (nada é guardado). Só o GERENTE chega aqui (a
 * checagem se repete por defesa em profundidade). Status fora do mapa vira aviso bloqueante e números zerados, nunca 500.
 */
internal fun calcularIntegridade(ctx: AppContext, principal: Principal, f: FiltrosIntegridade, store: WorkStore): IntegridadeCalculada {
    exigirGerente(ctx, principal)
    val hoje = hoje(ctx)
    val zona = ctx.settings.zone
    val regras = ctx.regrasIntegridade
    if (f.squad != null && ctx.roles.membersOf(f.squad, hoje).isEmpty()) throw ErroHttp(HttpStatusCode.BadRequest)
    val de = f.de.atStartOfDay(zona).toInstant()
    val ate = f.ate.plusDays(1).atStartOfDay(zona).toInstant()
    val ficha = FiltroDeFicha.de(ctx.fichas.getValue("integridade"))

    val comEvento = store.comEventoEntre(INSTANCIA_TRABALHO, ficha, CAMPOS_DOS_SINAIS, de, ate)
    val resolvidas = store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.RESOLVIDAS, resolvidasDe = de, resolvidasAte = ate))
    val issues = (comEvento + resolvidas).distinctBy { it.key }.sortedWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.key })

    val dias = { instante: java.time.Instant -> instante.atZone(zona).toLocalDate() }
    fun noSquad(pessoa: String?, dia: LocalDate) = f.squad == null || (pessoa != null && ctx.roles.squadOf(pessoa, dia) == f.squad)

    val universo: List<EvidenciaIntegridade>
    val concluidas: Map<String, Int>
    try {
        Fluxo.exigirStatusMapeados(ctx.statusMap, issues)
        val contexto = ContextoIntegridade(ctx.statusMap, zona, ctx.calendar)
        universo = (DetectoresDeTempo.todos + DetectoresDePrazoEFluxo.todos)
            .flatMap { it.detectar(issues, regras, contexto) }
            .filter { !it.at.isBefore(de) && it.at.isBefore(ate) }
            .filter { noSquad(it.pessoa ?: it.detalhe["responsavel"], dias(it.at)) }
        concluidas = issues.filter { s ->
            val fim = s.resolved
            fim != null && !fim.isBefore(de) && fim.isBefore(ate) && Fluxo.concluidaPositiva(s, ctx.statusMap)
        }.mapNotNull { s ->
            val dono = Fluxo.responsavelEm(s, requireNotNull(s.resolved))
            if (dono != null && noSquad(dono, dias(requireNotNull(s.resolved)))) dono else null
        }.groupingBy { it }.eachCount()
    } catch (e: UnmappedStatusException) {
        val aviso = AvisoDto(
            AvisoDto.STATUS_SEM_MAPEAMENTO, AvisoDto.BLOQUEANTE, "status sem mapeamento: ${e.status}", mapOf("status" to e.status),
        )
        return vazia(f, regras, zona, listOf(aviso))
    }

    // Metas do mesmo período e squad (IN-06): o impacto é a diferença entre o prazo atual e o prazo pela data original.
    val metas = metasDoPeriodo(ctx, principal, f, store)
    val resumo = NiveisPorPessoa.resumir(universo, concluidas, regras, metas)

    val exibidas = universo
        .filter { (f.sinal == null || it.sinal == f.sinal) && (f.pessoa == null || it.pessoa == f.pessoa) }
        .sortedWith(compareBy<EvidenciaIntegridade, String>(ORDEM_NATURAL_DE_CHAVES) { it.issueKey }.thenByDescending { it.at }.thenBy { it.sinal })
    val listadas = resumo.pessoas.filter { it.nivel != NivelIntegridade.NORMAL && (f.pessoa == null || it.pessoa == f.pessoa) }
    val avisos = buildList {
        addAll(DetectoresDePrazoEFluxo.avisosDeDados(issues, regras))
        if (exibidas.size > LIMITE_DE_EVIDENCIAS) {
            add(AvisoDto(AVISO_LISTA_PARCIAL_INTEGRIDADE, AvisoDto.INFORMATIVO, "lista cortada", mapOf("n" to LIMITE_DE_EVIDENCIAS.toString(), "total" to exibidas.size.toString())))
        }
    }
    return IntegridadeCalculada(f, exibidas, resumo, listadas, issues.associateBy { it.key }, metas, avisos, zona, bloqueado = false)
}

/** Metas do mesmo período e squad, pela regra única de "no prazo"; nulo se não puderem ser calculadas. */
private fun metasDoPeriodo(ctx: AppContext, principal: Principal, f: FiltrosIntegridade, store: WorkStore): MetasDto? {
    val params = if (f.squad == null) {
        parametersOf("de" to listOf(f.de.toString()), "ate" to listOf(f.ate.toString()))
    } else {
        parametersOf("de" to listOf(f.de.toString()), "ate" to listOf(f.ate.toString()), "squad" to listOf(f.squad))
    }
    val dto = CardMetas.calcularSeguro(montarEntrada(ctx, store, principal, params, CardMetas.periodoPadraoDias)).dto
    return dto.takeUnless { d -> d.avisos.any { it.gravidade == AvisoDto.BLOQUEANTE } }
}

// ---- Conversões para as respostas ----

private fun contagemPorSinal(c: IntegridadeCalculada, regras: RegrasDeIntegridade): List<ContagemDeSinalDto> =
    SinalIntegridade.entries.map { s -> ContagemDeSinalDto(s.name, regras.gravidade(s), regras.ativo(s), c.evidencias.count { it.sinal == s }) }

internal fun paraDto(c: IntegridadeCalculada, ctx: AppContext): IntegridadeDto {
    val regras = ctx.regrasIntegridade
    val porPessoa = c.evidencias.groupingBy { it.pessoa }.eachCount()
    val r = c.resumo
    return IntegridadeDto(
        aviso = AVISO_INDICIO_NAO_PROVA_INTEGRIDADE,
        avisoDpo = AVISO_VALIDAR_BASE_LEGAL_COM_DPO,
        versaoRegras = regras.rotulo,
        de = c.filtros.de.toString(),
        ate = c.filtros.ate.toString(),
        resumo = ResumoIntegridadeDto(
            evidencias = c.evidencias.size,
            issuesAfetadas = c.issuesAfetadas.size,
            medianaTaxa = r.medianaTaxa,
            porSinal = contagemPorSinal(c, regras),
            pessoas = c.pessoasListadas.map { NivelPessoaDto(it.pessoa, it.nivel.name, it.pontos, it.concluidas, it.taxa, it.multiplo, it.graves, porPessoa[it.pessoa] ?: 0) },
            pessoasAlto = r.pessoas.count { it.nivel == NivelIntegridade.ALTO },
            pessoasMedio = r.pessoas.count { it.nivel == NivelIntegridade.MEDIO },
            evidenciasNivelAlto = c.doNivel(NivelIntegridade.ALTO).size,
            evidenciasNivelMedio = c.doNivel(NivelIntegridade.MEDIO).size,
            impactoMetasPp = r.impactoMetasPp,
            taxaNoPrazo = c.metas?.taxaNoPrazo,
            taxaOriginal = c.metas?.taxaOriginal,
            pontosAlto = regras.pontosAlto, multiploAlto = regras.multiploAlto, gravesAlto = regras.gravesAlto,
            pontosMedio = regras.pontosMedio, multiploMedio = regras.multiploMedio, concluidasMinimas = regras.concluidasMinimas,
        ),
        evidencias = c.evidencias.take(LIMITE_DE_EVIDENCIAS).map {
            EvidenciaDto(it.sinal.name, it.gravidade, it.issueKey, it.pessoa, it.at.toString(), it.at.atZone(c.zona).toLocalDate().toString(), it.detalhe)
        },
        avisos = c.avisos,
    )
}

/** Catálogo (IN-08): o que cada sinal procura, a gravidade e o limite da regra, sem nenhum dado de pessoa. */
internal fun catalogoDto(ctx: AppContext): CatalogoIntegridadeDto {
    val r = ctx.regrasIntegridade
    fun h(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else v.toString()
    fun params(s: SinalIntegridade): Map<String, String> = when (s) {
        SinalIntegridade.TROCA_ANTES_DE_FECHAR -> mapOf("horas" to h(r.trocaAntesDeFecharHoras))
        SinalIntegridade.CRIADA_E_FECHADA_RAPIDO -> mapOf("minutos" to r.criadaEFechadaMinutos.toString())
        SinalIntegridade.REABRE_E_FECHA_RAPIDO -> mapOf("horas" to h(r.reabreEFechaHoras))
        SinalIntegridade.FECHAMENTO_EM_LOTE -> mapOf("quantidade" to r.loteQuantidade.toString(), "minutos" to r.loteMinutos.toString())
        SinalIntegridade.VENCIMENTO_TARDIO -> mapOf("pct" to Math.round(r.fracaoVidaTardia * 100).toString())
        SinalIntegridade.ESTIMATIVA_AUMENTADA -> mapOf("pct" to Math.round(r.estimativaAumento * 100).toString())
        SinalIntegridade.LABEL_CRITICIDADE_REMOVIDA -> mapOf("labels" to r.labelsCriticidade.joinToString(", "), "dias" to r.labelRemovidaDias.toString())
        SinalIntegridade.FECHADA_NO_LIMITE -> mapOf("hora" to r.limiteInicioHora.toString())
        else -> emptyMap()
    }
    return CatalogoIntegridadeDto(
        aviso = AVISO_INDICIO_NAO_PROVA_INTEGRIDADE,
        avisoDpo = AVISO_VALIDAR_BASE_LEGAL_COM_DPO,
        versaoRegras = r.rotulo,
        sinais = SinalIntegridade.entries.map { SinalDoCatalogoDto(it.name, r.gravidade(it), r.ativo(it), params(it)) },
        niveis = NiveisDoCatalogoDto(r.pontosAlto, r.multiploAlto, r.gravesAlto, r.pontosMedio, r.multiploMedio, r.concluidasMinimas, r.pisoMediana),
    )
}

private val DESCRICAO_PT = mapOf(
    SinalIntegridade.RESGATE_POR_PRORROGACAO to "Atraso resgatado por prorrogação",
    SinalIntegridade.TROCA_ANTES_DE_FECHAR to "Troca de responsável logo antes de fechar",
    SinalIntegridade.CRIADA_E_FECHADA_RAPIDO to "Criada e fechada em minutos",
    SinalIntegridade.REABRE_E_FECHA_RAPIDO to "Reaberta e fechada de novo rapidamente",
    SinalIntegridade.FECHAMENTO_EM_LOTE to "Fechamento em lote",
    SinalIntegridade.VENCIMENTO_PRORROGADO to "Vencimento prorrogado",
    SinalIntegridade.VENCIMENTO_TARDIO to "Vencimento definido tardiamente",
    SinalIntegridade.PULOU_O_FLUXO to "Pulou o fluxo",
    SinalIntegridade.ESTIMATIVA_AUMENTADA to "Estimativa aumentada",
    SinalIntegridade.LABEL_CRITICIDADE_REMOVIDA to "Label de criticidade removida perto do fim",
    SinalIntegridade.FECHADA_POR_OUTRO to "Fechada por quem não era o responsável",
    SinalIntegridade.FECHADA_NO_LIMITE to "Fechada no limite do prazo",
)

/** Linha da gaveta para uma evidência: uma evidência, uma linha (a mesma task pode aparecer mais de uma vez). */
internal fun linhaDaEvidencia(e: EvidenciaIntegridade, c: IntegridadeCalculada): DrillRow {
    val issue = c.issues[e.issueKey]
    val simbolo = if (e.gravidade >= 3) "▲" else "●"
    val quem = e.pessoa ?: "autor desconhecido"
    val detalhe = "$simbolo ${DESCRICAO_PT.getValue(e.sinal)} (gravidade ${e.gravidade}) em ${e.at.atZone(c.zona).toLocalDate()} por $quem; indício, não prova"
    return DrillRow(e.issueKey, issue?.summary.orEmpty(), e.detalhe["responsavel"], issue?.status.orEmpty(), detalhe)
}

/** Evidências como linhas, em ordem natural de chave (a ordem do contrato dos cartões). */
internal fun linhasDeEvidencias(evidencias: List<EvidenciaIntegridade>, c: IntegridadeCalculada): List<DrillRow> =
    evidencias.sortedWith(compareBy<EvidenciaIntegridade, String>(ORDEM_NATURAL_DE_CHAVES) { it.issueKey }.thenByDescending { it.at }.thenBy { it.sinal })
        .map { linhaDaEvidencia(it, c) }

/** Uma linha por task afetada (a task, sem repetir), em ordem natural de chave. */
internal fun linhasDasIssues(c: IntegridadeCalculada): List<DrillRow> =
    c.issuesAfetadas.map { key ->
        val issue = c.issues[key]
        val doisOuMais = c.evidencias.count { it.issueKey == key }
        DrillRow(key, issue?.summary.orEmpty(), issue?.assignee, issue?.status.orEmpty(), "● $doisOuMais evidência(s) de indício; indício, não prova")
    }
