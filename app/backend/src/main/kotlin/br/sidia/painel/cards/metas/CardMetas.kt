package br.sidia.painel.cards.metas

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
import br.sidia.painel.sync.FiltroDeFicha
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.Situacao
import br.sidia.painel.trabalho.candidatasNoPrazo
import io.ktor.http.HttpStatusCode
import java.util.Locale

/**
 * Card Metas e prazos: o universo é pelo VENCIMENTO no período (padrão 90 dias), "no prazo" e "no prazo ajustado" lado a
 * lado pela regra única, e a data original do vencimento ao lado da atual. Parâmetro próprio: `granularidade`
 * (`dia`, `semana` — o padrão — ou `mes`; outro valor é 400).
 */
object CardMetas : CardDeLeitura<MetasDto> {
    override val id = "metas"
    override val periodoPadraoDias: Int? = 90

    private fun granularidade(e: EntradaDeCard): Granularidade = when (e.parametros["granularidade"]?.trim()?.lowercase(Locale.ROOT)) {
        null, "", "semana" -> Granularidade.SEMANA
        "dia" -> Granularidade.DIA
        "mes" -> Granularidade.MES
        else -> throw ErroHttp(HttpStatusCode.BadRequest)
    }

    override fun calcular(e: EntradaDeCard): ResultadoDeCard<MetasDto> {
        val ctx = e.ctx
        val zona = ctx.settings.zone
        val de = requireNotNull(e.filtros.de)
        val ate = requireNotNull(e.filtros.ate)
        val g = granularidade(e)
        val ficha = FiltroDeFicha.de(ctx.fichas.getValue(id))
        val candidatas = e.store.candidatasNoPrazo(INSTANCIA_TRABALHO, ficha, de, ate, e.filtros.os)
            .filter { e.escopo.permiteAtual(it) && e.filtros.aceita(it) }
        val resolvidas = e.store.snapshots(
            ConsultaDeTrabalho(
                INSTANCIA_TRABALHO, ficha, Situacao.RESOLVIDAS, de.atStartOfDay(zona).toInstant(),
                ate.plusDays(1).atStartOfDay(zona).toInstant(), e.filtros.os,
            ),
        ).filter { e.escopo.permiteAtual(it) && e.filtros.aceita(it) }
        val analise = MetasCalculator(ctx.calendar, ctx.statusMap, ctx.labelsDeAtraso, ctx.regrasDeMetas, ctx.roles)
            .analisar(candidatas, resolvidas, de, ate, g, e.escopo.visiveis)
        return ResultadoDeCard(analise.dto) { segmento ->
            analise.segmentos[segmento]?.map { s ->
                val resolvida = s.resolved?.let { ctx.calendar.localDate(it) }
                val detalhe = when {
                    segmento == "semVencimento" -> "Resolvida em $resolvida; sem vencimento"
                    s.key in analise.deltas -> "Resolvida em $resolvida; vencia em ${s.duedate} (${diferenca(analise.deltas.getValue(s.key))})"
                    else -> "Resolvida em $resolvida"
                }
                linhaDe(s, e.escopo, detalhe)
            }
        }
    }

    /** "no dia", "2 dia(s) antes" ou "3 dia(s) depois" do vencimento. */
    private fun diferenca(delta: Int): String = when {
        delta == 0 -> "no dia do vencimento"
        delta < 0 -> "${-delta} dia(s) antes"
        else -> "$delta dia(s) depois"
    }

    override fun vazio(avisos: List<AvisoDto>) = MetasDto(
        versaoRegras = "", universo = 0, noPrazo = 0, noPrazoAjustado = 0, noPrazoOriginal = 0, antecipadas = 0, emD1D2 = 0,
        noDia = 0, atrasadas = 0, semVencimento = 0, taxaNoPrazo = null, taxaAjustada = null, taxaOriginal = null,
        taxaAntecipadas = null, alertaDataOriginal = false, metaNoPrazo = RegrasDeMetas.PADRAO.metaNoPrazo,
        metaAntecipadas = RegrasDeMetas.PADRAO.metaAntecipadas, granularidade = "semana", porPeriodo = emptyList(),
        distribuicao = emptyList(), porTipo = emptyList(), porPrioridade = emptyList(), porSquad = emptyList(),
        porPessoa = emptyList(), avisos = avisos,
    )
}

/** Cartões da tela Metas e prazos (inventário `MT-01` a `MT-14`). */
internal fun cartoesDeMetas(): List<CartaoRegistrado> {
    val prazo = listOf(FonteDoDado.ISSUE, FonteDoDado.CONFIGURACAO)
    val comChangelog = listOf(FonteDoDado.ISSUE, FonteDoDado.CHANGELOG, FonteDoDado.CONFIGURACAO)
    fun cartao(
        n: Int, natureza: Natureza, fontes: List<FonteDoDado>, aceita: (String) -> Boolean, numeros: (MetasDto) -> Map<String, Int>,
    ): CartaoRegistrado = CartaoDeLeitura(
        metaDeTrabalho("MT-%02d".format(n), CardMetas.id, GrupoDeCartao.PRAZOS, natureza, fontes, exigeChangelog = FonteDoDado.CHANGELOG in fontes),
        CardMetas, aceita, numeros,
    )
    fun umSo(chave: String, valor: (MetasDto) -> Int): (MetasDto) -> Map<String, Int> = { mapOf(chave to valor(it)) }
    fun tabela(prefixo: String, linhas: (MetasDto) -> List<LinhaMetaDto>): (MetasDto) -> Map<String, Int> =
        { dto -> linhas(dto).associate { "$prefixo:${it.chave}" to it.universo } }
    return listOf(
        cartao(1, Natureza.PRAZO, prazo, { it in setOf("universo", "noPrazo", "noPrazoAjustado") }) {
            mapOf("universo" to it.universo, "noPrazo" to it.noPrazo, "noPrazoAjustado" to it.noPrazoAjustado)
        },
        cartao(2, Natureza.PRAZO, comChangelog, { it == "noPrazoOriginal" }, umSo("noPrazoOriginal") { it.noPrazoOriginal }),
        cartao(3, Natureza.PRAZO, prazo, { it == "antecipadas" }, umSo("antecipadas") { it.antecipadas }),
        cartao(4, Natureza.PRAZO, prazo, { it == "emD1D2" }, umSo("emD1D2") { it.emD1D2 }),
        cartao(5, Natureza.PRAZO, prazo, { it == "noDia" }, umSo("noDia") { it.noDia }),
        cartao(6, Natureza.PRAZO, prazo, { it == "atrasadas" }, umSo("atrasadas") { it.atrasadas }),
        cartao(7, Natureza.QUALIDADE, prazo, { it == "semVencimento" }, umSo("semVencimento") { it.semVencimento }),
        cartao(8, Natureza.FLUXO, prazo, { it.startsWith("periodo:") && !it.endsWith(":noPrazo") }) { dto ->
            dto.porPeriodo.associate { "periodo:${it.chave}" to it.universo }
        },
        cartao(9, Natureza.PRAZO, prazo, { it.startsWith("periodo:") && it.endsWith(":noPrazo") }) { dto ->
            dto.porPeriodo.associate { "periodo:${it.chave}:noPrazo" to it.noPrazo }
        },
        cartao(10, Natureza.PRAZO, prazo, { it.startsWith("faixa:") }) { dto -> dto.distribuicao.associate { "faixa:${it.chave}" to it.n } },
        cartao(11, Natureza.PRAZO, prazo, { it.startsWith("tipo:") }, tabela("tipo") { it.porTipo }),
        cartao(12, Natureza.PRAZO, prazo, { it.startsWith("prioridade:") }, tabela("prioridade") { it.porPrioridade }),
        cartao(13, Natureza.PRAZO, prazo, { it.startsWith("squad:") }, tabela("squad") { it.porSquad }),
        cartao(14, Natureza.PESSOAS, comChangelog, { it.startsWith("pessoa:") }, tabela("pessoa") { it.porPessoa }),
    )
}
