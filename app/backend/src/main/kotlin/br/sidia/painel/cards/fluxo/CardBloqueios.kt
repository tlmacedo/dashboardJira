package br.sidia.painel.cards.fluxo

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.CardDeLeitura
import br.sidia.painel.cards.CartaoDeLeitura
import br.sidia.painel.cards.DrillRow
import br.sidia.painel.cards.EntradaDeCard
import br.sidia.painel.cards.INSTANCIA_TRABALHO
import br.sidia.painel.cards.ResultadoDeCard
import br.sidia.painel.cards.contrato.CartaoRegistrado
import br.sidia.painel.cards.contrato.FonteDoDado
import br.sidia.painel.cards.contrato.GrupoDeCartao
import br.sidia.painel.cards.contrato.Natureza
import br.sidia.painel.cards.linhaDe
import br.sidia.painel.cards.metaDeTrabalho
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.sync.FiltroDeFicha
import br.sidia.painel.sync.SqliteIssueLinkStore
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.Situacao

/**
 * Card Bloqueios entre tasks (BQ-01 a BQ-05). Lê as abertas do escopo (a ficha `bloqueios`), os vínculos ativos do tipo
 * configurado em `vinculos.yaml` e as pontas deles direto de `issue`, e entrega a análise do [BloqueiosCalculator]. Cada
 * número abre as MESMAS tasks pelo drill; a bloqueadora de pessoa fora da visão não aparece com chave nem nome.
 */
object CardBloqueios : CardDeLeitura<BloqueiosDto> {
    override val id = "bloqueios"
    override val periodoPadraoDias: Int? = null

    override fun calcular(e: EntradaDeCard): ResultadoDeCard<BloqueiosDto> {
        val ctx = e.ctx
        val ficha = FiltroDeFicha.de(ctx.fichas.getValue(id))
        val abertas = e.store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.ABERTAS, osVersion = e.filtros.os))
            .filter { e.escopo.permiteAtual(it) && e.filtros.aceita(it) }
        Fluxo.exigirStatusMapeados(ctx.statusMap, abertas)
        val ds = requireNotNull(ctx.dataSource)
        val vinculos = SqliteIssueLinkStore(ds).ativos(INSTANCIA_TRABALHO, ctx.vinculos.tipoDeLink)
        val pontas = LeitorDePontas(ds).ler(INSTANCIA_TRABALHO, vinculos.flatMap { listOf(it.fromKey, it.toKey) }.toSet())
        val visivel = { responsavel: String? -> responsavel?.let { it in e.escopo.visiveis } ?: e.escopo.veSemResponsavel }
        val analise = BloqueiosCalculator(ctx.statusMap, ctx.vinculos).analisar(abertas, vinculos, pontas, visivel)

        fun linhas(l: List<IssueSnapshot>) = l.map { s -> linhaDe(s, e.escopo, analise.detalhePorBloqueada[s.key].orEmpty()) }
        val segmentos = mapOf<String, () -> List<DrillRow>>(
            "bloqueadas" to { linhas(analise.bloqueadas) },
            "prazoEmConflito" to { linhas(analise.prazoEmConflito) },
            "verificarNoJira" to { linhas(analise.verificarNoJira) },
            "semCausa" to {
                analise.semCausa.map { s ->
                    val porque = if (ctx.statusMap.category(s.status) == StatusCategory.BLOCKED) "status bloqueado" else "flag de impedimento"
                    linhaDe(s, e.escopo, "$porque sem nenhum vínculo aberto que explique")
                }
            },
            "bloqueadoras" to {
                analise.bloqueadorasVisiveis.map { p ->
                    DrillRow(p.key, p.resumo, p.responsavel, p.status, "bloqueadora aberta" + (p.vence?.let { "; vence $it" } ?: "; sem vencimento"))
                }
            },
        )
        return ResultadoDeCard(analise.dto) { segmento -> segmentos[segmento]?.invoke() }
    }

    override fun vazio(avisos: List<AvisoDto>) = BloqueiosDto(
        bloqueadas = 0, bloqueadoras = 0, bloqueadorasForaDaVisao = 0, prazoEmConflito = 0, verificarNoJira = 0, semCausa = 0,
        linhas = emptyList(), tipoDeLink = "", avisos = avisos,
    )
}

/** Cartões da tela Bloqueios entre tasks: BQ-01 a BQ-05, um por número (o BQ-02 conta só as bloqueadoras ao alcance de quem vê). */
internal fun cartoesDeBloqueios(): List<CartaoRegistrado> {
    val fontes = listOf(FonteDoDado.ISSUE, FonteDoDado.ISSUE_LINK, FonteDoDado.CONFIGURACAO)
    fun cartao(n: Int, natureza: Natureza, segmento: String, numero: (BloqueiosDto) -> Int): CartaoRegistrado = CartaoDeLeitura(
        metaDeTrabalho("BQ-%02d".format(n), CardBloqueios.id, GrupoDeCartao.TRABALHO_EM_CURSO, natureza, fontes),
        CardBloqueios, { it == segmento },
    ) { mapOf(segmento to numero(it)) }
    return listOf(
        cartao(1, Natureza.ESTOQUE, "bloqueadas") { it.bloqueadas },
        cartao(2, Natureza.ESTOQUE, "bloqueadoras") { it.bloqueadoras },
        cartao(3, Natureza.PRAZO, "prazoEmConflito") { it.prazoEmConflito },
        cartao(4, Natureza.QUALIDADE, "semCausa") { it.semCausa },
        cartao(5, Natureza.QUALIDADE, "verificarNoJira") { it.verificarNoJira },
    )
}
