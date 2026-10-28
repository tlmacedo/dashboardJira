package br.sidia.painel.cards.fluxo

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
import br.sidia.painel.domain.TimeUnitMode
import br.sidia.painel.sync.FiltroDeFicha
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.Situacao
import io.ktor.http.HttpStatusCode
import java.util.Locale

/**
 * Card Cruzamentos (CZ-01): tabela cruzada das abertas do escopo sobre dois eixos da lista fechada ([Eixo]), com a medida
 * contagem ou idade P50. Parâmetros: `linhas` (padrão `tipo`), `colunas` (padrão `categoria`, nunca `pessoa`) e `medida`.
 * Qualquer valor fora da lista fechada, eixos iguais ou eixo de campo do dicionário (Plano 10) é 400. Cada célula e cada
 * total abrem as MESMAS tasks pelo drill (segmentos posicionais).
 */
object CardCruzamentos : CardDeLeitura<CruzamentosDto> {
    override val id = "cruzamentos"
    override val periodoPadraoDias: Int? = null

    private class Escolha(val linhas: Eixo, val colunas: Eixo, val medida: Medida)

    private fun ler(e: EntradaDeCard): Escolha {
        fun valor(nome: String) = e.parametros[nome]?.trim()?.takeIf { it.isNotEmpty() }
        val linhas = valor("linhas")?.let { Eixo.deId(it) ?: throw ErroHttp(HttpStatusCode.BadRequest) } ?: Eixo.TIPO
        val colunas = valor("colunas")?.let { Eixo.deId(it) ?: throw ErroHttp(HttpStatusCode.BadRequest) } ?: Eixo.CATEGORIA
        val medida = valor("medida")?.let { Medida.deId(it) ?: throw ErroHttp(HttpStatusCode.BadRequest) } ?: Medida.CONTAGEM
        if (colunas.soLinha || linhas == colunas) throw ErroHttp(HttpStatusCode.BadRequest)
        return Escolha(linhas, colunas, medida)
    }

    override fun calcular(e: EntradaDeCard): ResultadoDeCard<CruzamentosDto> {
        val ctx = e.ctx
        val escolha = ler(e)
        val ficha = FiltroDeFicha.de(ctx.fichas.getValue(id))
        val abertas = e.store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.ABERTAS, osVersion = e.filtros.os))
            .filter { e.escopo.permiteAtual(it) && e.filtros.aceita(it) }
        Fluxo.exigirStatusMapeados(ctx.statusMap, abertas)
        val analise = CruzamentosCalculator(ctx.calendar, ctx.statusMap, ctx.labelsDeAtraso) { ctx.roles.squadOf(it, e.hoje) }
            .analisar(abertas, e.agora, e.filtros.unit, escolha.linhas, escolha.colunas, escolha.medida)
        val unidade = if (e.filtros.unit == TimeUnitMode.UTIL) "dias úteis" else "dias corridos"
        val dto = analise.dto
        return ResultadoDeCard(dto) { segmento ->
            val partes = segmento.split(':')
            val linha = partes.getOrNull(1)?.toIntOrNull()?.let { dto.linhas.getOrNull(it - 1) }
            val coluna = partes.getOrNull(if (partes[0] == "c") 2 else 1)?.toIntOrNull()?.let { dto.colunas.getOrNull(it - 1) }
            val contexto = when (partes[0]) {
                "c" -> "${escolha.linhas.id}: $linha; ${escolha.colunas.id}: $coluna"
                "l" -> "${escolha.linhas.id}: ${partes.getOrNull(1)?.toIntOrNull()?.let { dto.linhas.getOrNull(it - 1) }}"
                "k" -> "${escolha.colunas.id}: $coluna"
                else -> "todas as abertas"
            }
            analise.segmentos[segmento]?.map { s ->
                linhaDe(s, e.escopo, "$contexto; idade ${String.format(Locale.ROOT, "%.1f", analise.idades.getValue(s.key))} $unidade")
            }
        }
    }

    override fun vazio(avisos: List<AvisoDto>) = CruzamentosDto(
        linhasEixo = Eixo.TIPO.id, colunasEixo = Eixo.CATEGORIA.id, medida = Medida.CONTAGEM.id, linhas = emptyList(), colunas = emptyList(),
        celulas = emptyList(), totaisDasLinhas = emptyList(), totaisDasColunas = emptyList(), total = 0,
        eixosDeLinha = Eixo.entries.map { it.id }, eixosDeColuna = Eixo.entries.filterNot { it.soLinha }.map { it.id },
        unit = "util", avisos = avisos,
    )
}

/** Cartão da aba Cruzamentos (CZ-01): as células, os totais de linha (exceto pessoa), os de coluna e o total geral. */
internal fun cartoesDeCruzamentos(): List<CartaoRegistrado> = listOf(
    CartaoDeLeitura(
        metaDeTrabalho(
            "CZ-01", CardCruzamentos.id, GrupoDeCartao.RESULTADOS, Natureza.ESTOQUE,
            listOf(FonteDoDado.ISSUE, FonteDoDado.CONFIGURACAO),
        ),
        CardCruzamentos, { it == "total" || it.startsWith("c:") || it.startsWith("l:") || it.startsWith("k:") },
    ) { dto ->
        buildMap {
            put("total", dto.total)
            dto.celulas.forEachIndexed { i, linha -> linha.forEachIndexed { j, c -> if (c.n > 0) put("c:${i + 1}:${j + 1}", c.n) } }
            dto.totaisDasLinhas?.forEachIndexed { i, n -> put("l:${i + 1}", n) }
            dto.totaisDasColunas.forEachIndexed { j, n -> put("k:${j + 1}", n) }
        }
    },
)
