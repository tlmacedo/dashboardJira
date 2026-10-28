package br.sidia.painel.integridade

import br.sidia.painel.cards.CODIGO_SEGMENTO_INVALIDO
import br.sidia.painel.cards.ErroHttp
import br.sidia.painel.cards.INSTANCIA_TRABALHO
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
import br.sidia.painel.trabalho.SqliteWorkStore
import io.ktor.http.HttpStatusCode

private fun segmentoInvalido(): Nothing = throw ErroHttp(HttpStatusCode.BadRequest, CODIGO_SEGMENTO_INVALIDO)

/**
 * Cartão da Integridade no contrato: [numeros] e [linhas] saem do MESMO [IntegridadeCalculada] (mesmos filtros), então o número
 * do cartão é o total do seu drill. SÓ o GERENTE vê (o catálogo já filtra por papel e [calcularIntegridade] confere de novo) e a
 * rota genérica só serve o drill com a trava do DPO aberta ([moduloSensivel]). [linhas] devolve nulo para um segmento que não é
 * deste cartão (400).
 */
internal class CartaoDeIntegridade(
    override val meta: CartaoMeta,
    private val numeros: (IntegridadeCalculada) -> Map<String, Int>,
    private val linhas: (IntegridadeCalculada, String) -> List<LinhaDeDrill>?,
) : CartaoRegistrado {
    override val instancia: String = INSTANCIA_TRABALHO
    override val moduloSensivel: ModuloSensivel = ModuloSensivel.INTEGRIDADE

    private fun calcular(e: EntradaDeCartao) = calcularIntegridade(
        e.ctx, e.principal, lerFiltrosIntegridade(e.parametros, hoje(e.ctx)), SqliteWorkStore(requireNotNull(e.ctx.dataSource)),
    )

    override suspend fun valores(e: EntradaDeCartao): Map<String, Int> = numeros(calcular(e))

    override suspend fun drill(e: EntradaDeCartao, segmento: String): List<LinhaDeDrill> =
        linhas(calcular(e), segmento) ?: segmentoInvalido()
}

private fun meta(id: String, natureza: Natureza, temDrill: Boolean = true): CartaoMeta {
    val chave = "card.${id.lowercase().replace("-", "")}"
    return CartaoMeta(
        id = id, grupo = GrupoDeCartao.AUDITORIA, natureza = natureza, tela = TELA_INTEGRIDADE,
        tituloChave = "$chave.titulo", oQueMostraChave = "$chave.oQueMostra", comoCalculaChave = "$chave.comoCalcula",
        fontes = listOf(FonteDoDado.ISSUE, FonteDoDado.CHANGELOG, FonteDoDado.CONFIGURACAO), papeis = setOf(Role.GERENTE),
        temDrill = temDrill, exigeChangelog = true, exigeWorklog = false,
    )
}

/** Id da tela e da rota de dados (`/api/integridade`). */
internal const val TELA_INTEGRIDADE = "integridade"

/** Cartão sem tasks por trás (mediana, impacto nas metas e catálogo): o contrato não o chama no drill. */
private class CartaoSemDrill(override val meta: CartaoMeta) : CartaoRegistrado {
    override val instancia: String = INSTANCIA_TRABALHO
    override val moduloSensivel: ModuloSensivel = ModuloSensivel.INTEGRIDADE
    override suspend fun valores(e: EntradaDeCartao): Map<String, Int> = emptyMap()
    override suspend fun drill(e: EntradaDeCartao, segmento: String): List<LinhaDeDrill> = segmentoInvalido()
}

/**
 * Os cartões da tela Integridade (`IN-nn`, só GERENTE): IN-01 evidências, IN-02 pessoas alta e média (o número do cartão é o das
 * EVIDÊNCIAS dessas pessoas, que é o que a gaveta abre; a tela mostra também a contagem de pessoas), IN-03 tasks afetadas, IN-04
 * mediana da taxa, IN-05 evidências por sinal, IN-06 impacto nas metas, IN-07 tabela de evidências e IN-08 catálogo.
 */
internal fun cartoesDeIntegridade(): List<CartaoRegistrado> = listOf(
    CartaoDeIntegridade(
        meta("IN-01", Natureza.QUALIDADE),
        numeros = { c -> mapOf("todas" to c.evidencias.size) },
        linhas = { c, seg -> if (seg == "todas") linhasDeEvidencias(c.evidencias, c) else null },
    ),
    CartaoDeIntegridade(
        meta("IN-02", Natureza.PESSOAS),
        numeros = { c ->
            buildMap {
                put("nivel:ALTO", c.doNivel(NivelIntegridade.ALTO).size)
                put("nivel:MEDIO", c.doNivel(NivelIntegridade.MEDIO).size)
                // Pessoas em ordem alfabética (a lista já vem assim).
                c.pessoasListadas.forEach { put("pessoa:${it.pessoa}", c.evidencias.count { e -> e.pessoa == it.pessoa }) }
            }
        },
        linhas = { c, seg ->
            when {
                seg == "nivel:ALTO" -> linhasDeEvidencias(c.doNivel(NivelIntegridade.ALTO), c)
                seg == "nivel:MEDIO" -> linhasDeEvidencias(c.doNivel(NivelIntegridade.MEDIO), c)
                // Sem oráculo: pessoa fora de ALTO/MÉDIO (ou inexistente) responde 200 com lista vazia, igual a quem não tem nada.
                seg.startsWith("pessoa:") -> c.daPessoa(seg.removePrefix("pessoa:"))?.let { linhasDeEvidencias(it, c) } ?: emptyList()
                else -> null
            }
        },
    ),
    CartaoDeIntegridade(
        meta("IN-03", Natureza.QUALIDADE),
        numeros = { c -> mapOf("afetadas" to c.issuesAfetadas.size) },
        linhas = { c, seg -> if (seg == "afetadas") linhasDasIssues(c) else null },
    ),
    CartaoSemDrill(meta("IN-04", Natureza.PESSOAS, temDrill = false)),
    CartaoDeIntegridade(
        meta("IN-05", Natureza.QUALIDADE),
        numeros = { c -> SinalIntegridade.entries.associate { "sinal:${it.name}" to c.doSinal(it).size } },
        linhas = { c, seg ->
            SinalIntegridade.entries.firstOrNull { "sinal:${it.name}" == seg }?.let { linhasDeEvidencias(c.doSinal(it), c) }
        },
    ),
    CartaoSemDrill(meta("IN-06", Natureza.PRAZO, temDrill = false)),
    CartaoDeIntegridade(
        meta("IN-07", Natureza.QUALIDADE),
        numeros = { c -> mapOf("todas" to c.evidencias.size) },
        linhas = { c, seg -> if (seg == "todas") linhasDeEvidencias(c.evidencias, c) else null },
    ),
    CartaoSemDrill(meta("IN-08", Natureza.QUALIDADE, temDrill = false)),
)
