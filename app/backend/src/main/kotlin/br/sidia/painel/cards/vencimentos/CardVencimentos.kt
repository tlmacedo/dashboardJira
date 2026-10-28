package br.sidia.painel.cards.vencimentos

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
import br.sidia.painel.cards.ErroHttp
import br.sidia.painel.cards.linhaDe
import br.sidia.painel.cards.metaDeTrabalho
import br.sidia.painel.pessoas.MotorDePessoas
import br.sidia.painel.sync.FiltroDeFicha
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.Situacao
import io.ktor.http.HttpStatusCode
import java.util.Locale

/**
 * Card Vencimentos da semana: o que vence em 7 (padrão) ou 14 dias e quem tem risco de atrasar, pela previsão de término
 * da capacidade. Todos os papéis veem (o USUARIO só as próprias). Filtros que valem: `squad`, `pessoa` e `dias` (7 ou 14).
 */
object CardVencimentos : CardDeLeitura<VencimentosDto> {
    override val id = "vencimentos"
    override val periodoPadraoDias: Int? = null

    override fun calcular(e: EntradaDeCard): ResultadoDeCard<VencimentosDto> {
        val ctx = e.ctx
        val dias = when (e.parametros["dias"]?.trim()) {
            null, "", "7" -> 7
            "14" -> 14
            else -> throw ErroHttp(HttpStatusCode.BadRequest)
        }
        val ficha = FiltroDeFicha.de(ctx.fichas.getValue(id))
        val abertas = e.store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.ABERTAS)).filter { e.escopo.permiteAtual(it) }
        Fluxo.exigirStatusMapeados(ctx.statusMap, abertas)

        val analise = VencimentosCalculator(MotorDePessoas(ctx)).calcular(abertas, e.hoje, dias)
        val porChave = analise.dto.linhas.associateBy { it.key }
        return ResultadoDeCard(analise.dto) { segmento ->
            analise.segmentos[segmento]?.map { s ->
                val l = porChave[s.key]
                val folga = l?.folgaDiasUteis?.let { String.format(Locale.ROOT, "folga de %.0f dia(s) útil(eis)", it) } ?: "sem previsão de término"
                linhaDe(s, e.escopo, "Vence em ${s.duedate}; término previsto ${l?.termino ?: "—"}; $folga")
            }
        }
    }

    override fun vazio(avisos: List<AvisoDto>) = VencimentosDto(dias = 7, vencem = 0, linhas = emptyList(), riscoDeAtrasar = 0, avisos = avisos)
}

/** Cartões da tela Vencimentos da semana: `VE-01` (o que vence) e `VE-02` (risco de atrasar). */
internal fun cartoesDeVencimentos(): List<CartaoRegistrado> {
    val fontes = listOf(FonteDoDado.ISSUE, FonteDoDado.CONFIGURACAO)
    return listOf(
        CartaoDeLeitura(
            metaDeTrabalho("VE-01", CardVencimentos.id, GrupoDeCartao.PRAZOS, Natureza.PRAZO, fontes),
            CardVencimentos, { it == "vencem" },
        ) { mapOf("vencem" to it.vencem) },
        CartaoDeLeitura(
            metaDeTrabalho("VE-02", CardVencimentos.id, GrupoDeCartao.PRAZOS, Natureza.PRAZO, fontes),
            CardVencimentos, { it == "riscoDeAtrasar" },
        ) { mapOf("riscoDeAtrasar" to it.riscoDeAtrasar) },
    )
}
