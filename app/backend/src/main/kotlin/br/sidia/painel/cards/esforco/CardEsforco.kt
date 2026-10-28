package br.sidia.painel.cards.esforco

import br.sidia.painel.AppContext
import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.CardDeLeitura
import br.sidia.painel.cards.CartaoDeLeitura
import br.sidia.painel.cards.EntradaDeCard
import br.sidia.painel.cards.ErroHttp
import br.sidia.painel.cards.INSTANCIA_TRABALHO
import br.sidia.painel.cards.ResultadoDeCard
import br.sidia.painel.cards.contrato.CartaoRegistrado
import br.sidia.painel.cards.contrato.FonteDoDado
import br.sidia.painel.cards.contrato.GrupoDeCartao
import br.sidia.painel.cards.contrato.Natureza
import br.sidia.painel.cards.linhaDe
import br.sidia.painel.cards.metaDeTrabalho
import br.sidia.painel.cards.posse.PosseCalculator
import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.sync.FiltroDeFicha
import br.sidia.painel.sync.SqliteWorklogStore
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.Situacao
import io.ktor.http.HttpStatusCode
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Código do aviso: parte das horas veio do tempo gasto do Jira, porque a task não tem worklog (`n` = quantas tasks). */
const val AVISO_HORAS_DO_CAMPO_TIMESPENT = "HORAS_DO_CAMPO_TIMESPENT"

/** O esforço de cada task (chave -> componentes e índice) com o worklog sincronizado; a Carga por tipo/OS usa o mesmo cálculo. */
internal fun esforcosDas(ctx: AppContext, tasks: List<IssueSnapshot>): Map<String, EsforcoTask> {
    val worklogs = ctx.dataSource?.let { SqliteWorklogStore(it).porIssue(INSTANCIA_TRABALHO, tasks.map { s -> s.key }.toSet()) }.orEmpty()
    val calc = EsforcoCalculator(ctx.regrasEsforco)
    return tasks.associate { s -> s.key to calc.daTask(s, worklogs[s.key].orEmpty()) }
}

/**
 * Card Esforço por task fechada: as concluídas positivas do período (padrão 30 dias, pela resolução) com o peso do tipo, os
 * testes `TEST-*` e as horas do worklog lado a lado e o índice composto de `esforco.yaml` (proposta inicial). Por pessoa, o
 * esforço segue a POSSE da task (não o autor do worklog). Todos os papéis veem o card; o USUARIO só enxerga a si mesmo.
 * Filtros: squad, pessoa, tipo, prioridade, OS, de/ate, unit (unidade da posse) e `granularidade=semana|mes` (400 se outro).
 */
object CardEsforco : CardDeLeitura<EsforcoDto> {
    override val id = "esforco"
    override val periodoPadraoDias: Int? = 30

    override fun calcular(e: EntradaDeCard): ResultadoDeCard<EsforcoDto> {
        val ctx = e.ctx
        val zona = ctx.settings.zone
        val porMes = when (e.parametros["granularidade"]?.trim()?.lowercase(Locale.ROOT)) {
            null, "", "semana" -> false
            "mes" -> true
            else -> throw ErroHttp(HttpStatusCode.BadRequest)
        }
        val inicio = requireNotNull(e.filtros.de).atStartOfDay(zona).toInstant()
        val fim = requireNotNull(e.filtros.ate).plusDays(1).atStartOfDay(zona).toInstant()
        val ficha = FiltroDeFicha.de(ctx.fichas.getValue(id))
        val resolvidas = e.store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.RESOLVIDAS, inicio, fim, e.filtros.os))
        Fluxo.exigirStatusMapeados(ctx.statusMap, resolvidas)

        val tasks = resolvidas.filter {
            Fluxo.concluidaPositiva(it, ctx.statusMap) && e.filtros.aceita(it) && e.escopo.permitePosse(it, ctx.statusMap, e.agora)
        }.sortedWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.key })

        val calc = EsforcoCalculator(ctx.regrasEsforco)
        val esforcos = esforcosDas(ctx, tasks)
        val posses = tasks.associate { s -> s.key to PosseCalculator.posse(s, e.agora, ctx.statusMap, ctx.calendar, e.filtros.unit) }
        val visiveis = e.escopo.visiveis
        val porPessoa = calc.porPessoa(tasks.map { esforcos.getValue(it.key) to posses.getValue(it.key) }).filterKeys { it in visiveis }
        // Segmento `pessoa:<id>`: as tasks em que a pessoa teve posse (fração maior que zero), as mesmas que somam na linha dela.
        val tasksDaPessoa = porPessoa.keys.associateWith { p ->
            tasks.filter { s -> posses.getValue(s.key).any { it.pessoa == p && it.fracao > 0.0 } }
        }

        val dia = { s: IssueSnapshot -> s.resolved!!.atZone(zona).toLocalDate() }
        val periodos = tasks.groupBy { s ->
            val d = dia(s)
            if (porMes) d.withDayOfMonth(1) else d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }.toSortedMap().map { (inicioDoPeriodo, lista) ->
            EsforcoPeriodoDto(
                inicioDoPeriodo.toString(), lista.size, lista.sumOf { esforcos.getValue(it.key).horas }, lista.sumOf { esforcos.getValue(it.key).indice },
            )
        }

        val doCampo = tasks.count { esforcos.getValue(it.key).horasDoCampo }
        val avisos = buildList {
            if (doCampo > 0) {
                add(AvisoDto(AVISO_HORAS_DO_CAMPO_TIMESPENT, AvisoDto.INFORMATIVO, "horas vieram do tempo gasto do Jira", mapOf("n" to doCampo.toString())))
            }
        }
        val dto = EsforcoDto(
            versaoRegras = ctx.regrasEsforco.rotulo,
            tasks = tasks.map { s ->
                val x = esforcos.getValue(s.key)
                EsforcoTaskDto(s.key, s.summary, s.issueType, dia(s).toString(), x.pesoTipo, x.testes, x.horas, x.indice, x.horasDoCampo)
            },
            pessoas = porPessoa.map { (p, x) ->
                EsforcoPessoaDto(p, tasksDaPessoa.getValue(p).size, x.pesoTipo, x.testesPonderados, x.horas, x.indice, x.horasDoCampo)
            },
            periodos = periodos, avisos = avisos, granularidade = if (porMes) "mes" else "semana",
        )

        fun numero(v: Double) = String.format(Locale.ROOT, "%.1f", v)
        return ResultadoDeCard(dto) { segmento ->
            when {
                segmento == "tasks" -> tasks.map { s ->
                    val x = esforcos.getValue(s.key)
                    val origem = if (x.horasDoCampo) " (horas do tempo gasto do Jira)" else ""
                    linhaDe(s, e.escopo, "Índice ${numero(x.indice)} = tipo ${numero(x.pesoTipo)} + ${x.testes} teste(s) + ${numero(x.horas)} h$origem")
                }
                segmento.startsWith("pessoa:") -> tasksDaPessoa[segmento.removePrefix("pessoa:")]?.map { s ->
                    val p = segmento.removePrefix("pessoa:")
                    val fracao = posses.getValue(s.key).first { it.pessoa == p }.fracao
                    val x = esforcos.getValue(s.key)
                    linhaDe(s, e.escopo, "Parte de ${numero(x.indice * fracao)} do índice ${numero(x.indice)} (${numero(fracao * 100)}%)")
                }
                else -> null
            }
        }
    }

    override fun vazio(avisos: List<AvisoDto>) =
        EsforcoDto(versaoRegras = "", tasks = emptyList(), pessoas = emptyList(), periodos = emptyList(), avisos = avisos)
}

/** Cartões da tela Esforço: `EF-01` (as tasks concluídas com o índice) e `EF-02` (as tasks de cada pessoa pela posse). */
internal fun cartoesDeEsforco(): List<CartaoRegistrado> {
    val fontes = listOf(FonteDoDado.ISSUE, FonteDoDado.CHANGELOG, FonteDoDado.WORKLOG, FonteDoDado.CONFIGURACAO)
    fun meta(id: String, natureza: Natureza) =
        metaDeTrabalho(id, CardEsforco.id, GrupoDeCartao.RESULTADOS, natureza, fontes, exigeChangelog = true).copy(exigeWorklog = true)
    return listOf(
        CartaoDeLeitura(meta("EF-01", Natureza.FLUXO), CardEsforco, { it == "tasks" }) { dto -> mapOf("tasks" to dto.tasks.size) },
        CartaoDeLeitura(meta("EF-02", Natureza.PESSOAS), CardEsforco, { it.startsWith("pessoa:") }) { dto ->
            dto.pessoas.associate { "pessoa:${it.pessoa}" to it.tasks }
        },
    )
}
