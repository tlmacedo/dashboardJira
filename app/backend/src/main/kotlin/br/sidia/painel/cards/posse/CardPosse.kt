package br.sidia.painel.cards.posse

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.CardDeLeitura
import br.sidia.painel.cards.CartaoDeLeitura
import br.sidia.painel.cards.EntradaDeCard
import br.sidia.painel.cards.INSTANCIA_TRABALHO
import br.sidia.painel.cards.ResultadoDeCard
import br.sidia.painel.cards.contrato.CartaoRegistrado
import br.sidia.painel.cards.contrato.FonteDoDado
import br.sidia.painel.cards.contrato.GrupoDeCartao
import br.sidia.painel.cards.contrato.Natureza
import br.sidia.painel.cards.linhaDe
import br.sidia.painel.cards.metaDeTrabalho
import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.TimeUnitMode
import br.sidia.painel.sync.FiltroDeFicha
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.Situacao
import java.util.Locale

/**
 * Card Posse da task: o tempo que cada pessoa ficou com cada task (resolvidas no período, mais as abertas), em dias úteis ou
 * corridos. Entra a task em que alguém da visão teve posse em algum momento; quem está fora da visão (ou o "sem responsável")
 * aparece como uma só "outra pessoa" (nulo), nunca pelo nome. Todos os papéis veem o card; o USUARIO só enxerga a si mesmo.
 * Período padrão de 90 dias pela data de resolução, como Fechadas. Sem ranking: pessoas em ordem alfabética.
 */
object CardPosse : CardDeLeitura<PosseDto> {
    override val id = "posse"
    override val periodoPadraoDias: Int? = 90

    override fun calcular(e: EntradaDeCard): ResultadoDeCard<PosseDto> {
        val ctx = e.ctx
        val zona = ctx.settings.zone
        val inicio = requireNotNull(e.filtros.de).atStartOfDay(zona).toInstant()
        val fim = requireNotNull(e.filtros.ate).plusDays(1).atStartOfDay(zona).toInstant()
        val ficha = FiltroDeFicha.de(ctx.fichas.getValue(id))
        val abertas = e.store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.ABERTAS, osVersion = e.filtros.os))
        val resolvidas = e.store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.RESOLVIDAS, inicio, fim, e.filtros.os))
        Fluxo.exigirStatusMapeados(ctx.statusMap, abertas, resolvidas)

        val visiveis = e.escopo.visiveis
        val unit = e.filtros.unit
        val tasks = (abertas + resolvidas).filter { e.filtros.aceita(it) && e.escopo.permitePosse(it, ctx.statusMap, e.agora) }
            .sortedWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.key })
        val detalhes = tasks.associate { it.key to PosseCalculator.detalhe(it, e.agora, ctx.statusMap, ctx.calendar, unit) }

        // Quem está fora da visão (e o "sem responsável") vira UM só item nulo, com as partes somadas.
        val tasksDto = tasks.map { s ->
            val d = detalhes.getValue(s.key)
            val dosVisiveis = d.posse.filter { it.pessoa != null && it.pessoa in visiveis }.map { FracaoPosseDto(it.pessoa, it.dias, it.fracao) }
            val outras = d.posse.filter { it.pessoa == null || it.pessoa !in visiveis }
            val nulo = if (outras.isEmpty()) emptyList() else listOf(FracaoPosseDto(null, outras.sumOf { it.dias }, outras.sumOf { it.fracao }))
            PosseTaskDto(s.key, s.summary, d.transferencias, dosVisiveis + nulo)
        }

        val porPessoa = visiveis.sorted().mapNotNull { p ->
            val dela = tasks.filter { s -> detalhes.getValue(s.key).posse.any { it.pessoa == p } }
            if (dela.isEmpty()) return@mapNotNull null
            PossePessoaDto(
                pessoa = p, tasks = dela.size,
                dias = dela.sumOf { s -> detalhes.getValue(s.key).posse.first { it.pessoa == p }.dias },
                recebidas = dela.sumOf { detalhes.getValue(it.key).recebidas[p] ?: 0 },
                repassadas = dela.sumOf { detalhes.getValue(it.key).repassadas[p] ?: 0 },
            )
        }
        val dto = PosseDto(tasksDto, porPessoa, if (unit == TimeUnitMode.UTIL) "util" else "corrido", emptyList())

        val unidade = if (unit == TimeUnitMode.UTIL) "dias úteis" else "dias corridos"
        fun numero(v: Double) = String.format(Locale.ROOT, "%.1f", v)
        fun linhas(lista: List<IssueSnapshot>, detalhe: (IssueSnapshot) -> String) = lista.map { linhaDe(it, e.escopo, detalhe(it)) }
        return ResultadoDeCard(dto) { segmento ->
            when {
                segmento == "tasks" -> linhas(tasks) { s -> "${detalhes.getValue(s.key).transferencias} passagem(ns) de responsável" }
                segmento == "transferidas" ->
                    linhas(tasks.filter { detalhes.getValue(it.key).transferencias > 0 }) { s -> "${detalhes.getValue(s.key).transferencias} passagem(ns) de responsável" }
                segmento.startsWith("pessoa:") -> {
                    val p = segmento.removePrefix("pessoa:")
                    if (porPessoa.none { it.pessoa == p }) null
                    else linhas(tasks.filter { s -> detalhes.getValue(s.key).posse.any { it.pessoa == p } }) { s ->
                        val parte = detalhes.getValue(s.key).posse.first { it.pessoa == p }
                        "Posse de ${numero(parte.dias)} $unidade (${numero(parte.fracao * 100)}%)"
                    }
                }
                else -> null
            }
        }
    }

    override fun vazio(avisos: List<AvisoDto>) = PosseDto(tasks = emptyList(), pessoas = emptyList(), unit = "util", avisos = avisos)
}

/** Cartões da tela Posse da task: `PS-01` (as tasks e as que mudaram de mão) e `PS-02` (as tasks de cada pessoa da visão). */
internal fun cartoesDePosse(): List<CartaoRegistrado> {
    val fontes = listOf(FonteDoDado.ISSUE, FonteDoDado.CHANGELOG)
    return listOf(
        CartaoDeLeitura(
            metaDeTrabalho("PS-01", CardPosse.id, GrupoDeCartao.PESSOAS_E_PLANEJAMENTO, Natureza.FLUXO, fontes, exigeChangelog = true),
            CardPosse, { it == "tasks" || it == "transferidas" },
        ) { dto -> mapOf("tasks" to dto.tasks.size, "transferidas" to dto.tasks.count { it.transferencias > 0 }) },
        CartaoDeLeitura(
            metaDeTrabalho("PS-02", CardPosse.id, GrupoDeCartao.PESSOAS_E_PLANEJAMENTO, Natureza.PESSOAS, fontes, exigeChangelog = true),
            CardPosse, { it.startsWith("pessoa:") },
        ) { dto -> dto.pessoas.associate { "pessoa:${it.pessoa}" to it.tasks } },
    )
}
