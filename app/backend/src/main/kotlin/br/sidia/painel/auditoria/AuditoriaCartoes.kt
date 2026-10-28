package br.sidia.painel.auditoria

import br.sidia.painel.cards.CODIGO_SEGMENTO_INVALIDO
import br.sidia.painel.cards.ErroHttp
import br.sidia.painel.cards.contrato.CartaoMeta
import br.sidia.painel.cards.contrato.CartaoRegistrado
import br.sidia.painel.cards.contrato.EntradaDeCartao
import br.sidia.painel.cards.contrato.FonteDoDado
import br.sidia.painel.cards.contrato.GrupoDeCartao
import br.sidia.painel.cards.contrato.LinhaDeDrill
import br.sidia.painel.cards.contrato.Natureza
import br.sidia.painel.cards.hoje
import br.sidia.painel.lgpd.ModuloSensivel
import br.sidia.painel.security.Role
import io.ktor.http.HttpStatusCode

/** Só coordenação e gerente veem os cartões de auditoria (spec 7.3); o dado dentro deles ainda passa por `Visibility`. */
private val COORDENACAO_E_GERENTE = setOf(Role.COORDENADOR, Role.GERENTE)

private fun segmentoInvalido(): Nothing = throw ErroHttp(HttpStatusCode.BadRequest, CODIGO_SEGMENTO_INVALIDO)

/**
 * Cartão da Auditoria no contrato: [numeros] e [linhas] saem do MESMO [AuditoriaCalculada] (mesmos filtros), então o
 * número do cartão é sempre o total do seu drill. [linhas] devolve nulo para um segmento que não é deste cartão (400).
 */
internal class CartaoDeAuditoria(
    override val meta: CartaoMeta,
    private val numeros: (AuditoriaCalculada) -> Map<String, Int>,
    private val linhas: (AuditoriaCalculada, String) -> List<LinhaDeDrill>?,
) : CartaoRegistrado {
    override val instancia: String = INSTANCIA_AUDITORIA
    override val moduloSensivel: ModuloSensivel = ModuloSensivel.AUDITORIA

    private fun calcular(e: EntradaDeCartao) = calcularAuditoria(e.ctx, e.principal, lerFiltrosAuditoria(e.parametros, hoje(e.ctx)))

    override suspend fun valores(e: EntradaDeCartao): Map<String, Int> = numeros(calcular(e))

    override suspend fun drill(e: EntradaDeCartao, segmento: String): List<LinhaDeDrill> =
        linhas(calcular(e), segmento) ?: segmentoInvalido()
}

/** Cartão Task × Review no contrato (pares e divergentes). */
internal class CartaoTaskReview(override val meta: CartaoMeta) : CartaoRegistrado {
    override val instancia: String = INSTANCIA_AUDITORIA
    override val moduloSensivel: ModuloSensivel = ModuloSensivel.AUDITORIA

    private fun calcular(e: EntradaDeCartao) = calcularTaskReview(e.ctx, e.principal, lerFiltrosTaskReview(e.parametros))

    override suspend fun valores(e: EntradaDeCartao): Map<String, Int> =
        calcular(e).resumo.let { mapOf(SEG_PARES to it.pares, SEG_DIVERGENTES to it.divergentes) }

    override suspend fun drill(e: EntradaDeCartao, segmento: String): List<LinhaDeDrill> {
        val c = calcular(e)
        return when (segmento) {
            SEG_PARES -> linhasDePares(c.pares, c.visiveis)
            SEG_DIVERGENTES -> linhasDePares(c.pares.filter { it.divergente }, c.visiveis)
            else -> segmentoInvalido()
        }
    }

    companion object {
        const val SEG_PARES = "pares"
        const val SEG_DIVERGENTES = "divergentes"
    }
}

private fun meta(
    id: String, tela: String, natureza: Natureza, fontes: List<FonteDoDado>,
) = CartaoMeta(
    id = id,
    grupo = GrupoDeCartao.AUDITORIA,
    natureza = natureza,
    tela = tela,
    tituloChave = "card.${id.lowercase().replace("-", "")}.titulo",
    oQueMostraChave = "card.${id.lowercase().replace("-", "")}.oQueMostra",
    comoCalculaChave = "card.${id.lowercase().replace("-", "")}.comoCalcula",
    fontes = fontes,
    papeis = COORDENACAO_E_GERENTE,
    temDrill = true,
    exigeChangelog = true,
    exigeWorklog = false,
)

private val FONTES_DE_SINAL = listOf(FonteDoDado.ISSUE, FonteDoDado.CHANGELOG, FonteDoDado.CONFIGURACAO)

/** Os cartões da tela Auditoria (`AU-nn`) e o da Divergência Task × Review (`DV-01`). */
internal fun cartoesDeAuditoria(): List<CartaoRegistrado> = listOf(
    // AU-01: quantos sinais para conferir, e quantos são auditoria e quantos alerta.
    CartaoDeAuditoria(
        meta("AU-01", TELA_AUDITORIA, Natureza.QUALIDADE, FONTES_DE_SINAL),
        numeros = { c ->
            mapOf(
                "todos" to c.sinais.size,
                "resultado:auditoria" to c.sinais.count { it.resultado == Resultado.AUDITORIA },
                "resultado:alerta" to c.sinais.count { it.resultado == Resultado.ALERTA },
            )
        },
        linhas = { c, segmento ->
            when (segmento) {
                "todos" -> linhasDeSinais(c.sinais, c)
                "resultado:auditoria" -> linhasDeSinais(c.sinais.filter { it.resultado == Resultado.AUDITORIA }, c)
                "resultado:alerta" -> linhasDeSinais(c.sinais.filter { it.resultado == Resultado.ALERTA }, c)
                else -> null
            }
        },
    ),
    // AU-02: a contagem por tipo de sinal (na ordem do catálogo de códigos); nada por pessoa.
    CartaoDeAuditoria(
        meta("AU-02", TELA_AUDITORIA, Natureza.QUALIDADE, FONTES_DE_SINAL),
        numeros = { c -> c.porCodigo.entries.associate { (codigo, n) -> "sinal:${codigo.name}" to n } },
        linhas = { c, segmento ->
            val codigo = CodigoSinal.entries.firstOrNull { "sinal:${it.name}" == segmento }
            codigo?.let { linhasDeSinais(c.sinais.filter { s -> s.codigo == it }, c) }
        },
    ),
    // AU-03: "no prazo" e "no prazo ajustado" lado a lado; cada número abre as tasks que o compõem.
    CartaoDeAuditoria(
        meta("AU-03", TELA_AUDITORIA, Natureza.PRAZO, listOf(FonteDoDado.ISSUE, FonteDoDado.CONFIGURACAO)),
        numeros = { c ->
            c.noPrazo.resultado.let {
                mapOf(
                    "concluidas" to it.concluidas, "noPrazo" to it.noPrazo, "noPrazoAjustado" to it.noPrazoAjustado,
                    "justificadasSobAuditoria" to it.justificadasSobAuditoria,
                )
            }
        },
        linhas = { c, segmento ->
            val lista = when (segmento) {
                "concluidas" -> c.noPrazo.concluidas
                "noPrazo" -> c.noPrazo.noPrazo
                "noPrazoAjustado" -> c.noPrazo.noPrazoAjustado
                "justificadasSobAuditoria" -> c.noPrazo.justificadasSobAuditoria
                else -> null
            }
            lista?.sortedWith(compareBy(br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES) { it.key })?.map { linhaDeConcluida(it, c.zona, c.visiveis) }
        },
    ),
    // DV-01: Task × Review (vencimentos diferentes entre a task e a sua Review).
    CartaoTaskReview(meta("DV-01", TELA_TASK_REVIEW, Natureza.QUALIDADE, listOf(FonteDoDado.ISSUE, FonteDoDado.ISSUE_LINK))),
)

/** Telas dos cartões: a Auditoria vive em `/auditoria`; a Divergência, em `/divergencia` (rota de dados `/api/cards/task-review`). */
internal const val TELA_AUDITORIA = "auditoria"
internal const val TELA_TASK_REVIEW = "task-review"
