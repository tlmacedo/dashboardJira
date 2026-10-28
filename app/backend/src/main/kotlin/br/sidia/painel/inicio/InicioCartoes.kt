package br.sidia.painel.inicio

import br.sidia.painel.AppContext
import br.sidia.painel.cards.CODIGO_SEGMENTO_INVALIDO
import br.sidia.painel.cards.ErroHttp
import br.sidia.painel.cards.INSTANCIA_TRABALHO
import br.sidia.painel.cards.contrato.CartaoMeta
import br.sidia.painel.cards.contrato.CartaoRegistrado
import br.sidia.painel.cards.contrato.CatalogoDeCartoes
import br.sidia.painel.cards.contrato.EntradaDeCartao
import br.sidia.painel.cards.contrato.FonteDoDado
import br.sidia.painel.cards.contrato.GrupoDeCartao
import br.sidia.painel.cards.contrato.LinhaDeDrill
import br.sidia.painel.cards.contrato.Natureza
import br.sidia.painel.cards.hoje
import br.sidia.painel.cards.metaDeTrabalho
import br.sidia.painel.cards.papeisVigentes
import br.sidia.painel.cards.usernameValido
import br.sidia.painel.lgpd.ModuloSensivel
import br.sidia.painel.lgpd.TravaDpo
import br.sidia.painel.security.Principal
import br.sidia.painel.security.Role
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters

/** Cartões da Central, só para a coordenação e o gerente. */
private val ALTA = setOf(Role.COORDENADOR, Role.GERENTE)

/** Para onde um número da Central aponta: o cartão de destino e o segmento dele. */
internal class Destino(val cartao: String, val segmento: String)

/**
 * Cartão da Central (VG-nn): um painel de números que SÃO os de outros cartões. Não calcula nada: [valores] e [drill] chamam o
 * cartão de destino de cada segmento, com a mesma entrada, então o número da Central é o número do card de destino e o total
 * do drill dele (invariante testada em `ContratoBloco7bTest`). O destino que o papel de quem pergunta não vê, ou que está atrás
 * da trava do DPO fechada, simplesmente não existe: some de [valores] e o drill responde 400 como para qualquer segmento
 * desconhecido (nada revela que o módulo existe).
 *
 * [dinamico] liga um destino cujos segmentos são conhecidos só na hora (as semanas do VG-10): vale o prefixo e o corte nas
 * últimas [ultimos] ordens do segmento.
 */
internal class CartaoDeCentral(
    override val meta: CartaoMeta,
    private val destinos: Map<String, Destino>,
    private val catalogo: () -> CatalogoDeCartoes,
    override val moduloSensivel: ModuloSensivel? = null,
    private val dinamico: Pair<String, String>? = null,
    private val ultimos: Int = 12,
) : CartaoRegistrado {
    override val instancia: String = INSTANCIA_TRABALHO

    /** O cartão de destino, só se quem pergunta pode vê-lo (papel e trava do DPO); senão nulo. */
    private fun visivel(e: EntradaDeCartao, id: String): CartaoRegistrado? {
        val c = catalogo().porId(id) ?: return null
        val papeis = papeisVigentes(e.ctx, e.principal)
        if (!c.meta.temDrill || c.meta.papeis.none { it in papeis }) return null
        if (c.moduloSensivel != null && !TravaDpo(e.ctx.settings, e.ctx.dataSource).liberada) return null
        return c
    }

    override suspend fun valores(e: EntradaDeCartao): Map<String, Int> = buildMap {
        for ((segmento, d) in destinos) {
            val c = visivel(e, d.cartao) ?: continue
            c.valores(e)[d.segmento]?.let { put(segmento, it) }
        }
        dinamico?.let { (cartao, prefixo) ->
            val c = visivel(e, cartao) ?: return@let
            c.valores(e).filterKeys { it.startsWith(prefixo) }.entries.sortedBy { it.key }.takeLast(ultimos).forEach { put(it.key, it.value) }
        }
    }

    override suspend fun drill(e: EntradaDeCartao, segmento: String): List<LinhaDeDrill> {
        val d = destinos[segmento] ?: dinamico?.takeIf { segmento.startsWith(it.second) }?.let { Destino(it.first, segmento) }
            ?: throw ErroHttp(HttpStatusCode.BadRequest, CODIGO_SEGMENTO_INVALIDO)
        val c = visivel(e, d.cartao) ?: throw ErroHttp(HttpStatusCode.BadRequest, CODIGO_SEGMENTO_INVALIDO)
        return c.drill(e, d.segmento)
    }
}

/**
 * De quem é o Meu painel. SEMPRE o próprio usuário, exceto o Squad Leader, que pode pedir `pessoa=` de alguém do squad que lidera
 * (fora dele, 403). Para os demais papéis o parâmetro é ignorado: ninguém abre o painel de outra pessoa mudando a URL.
 */
internal fun alvoDoMeuPainel(ctx: AppContext, principal: Principal, pedida: String?): String {
    val pedido = pedida?.trim()?.takeIf { it.isNotEmpty() } ?: return principal.userId
    if (Role.SQUAD_LEADER !in papeisVigentes(ctx, principal)) return principal.userId
    val id = usernameValido(pedido)
    if (id == principal.userId) return id
    val hoje = hoje(ctx)
    val doSquad = ctx.roles.squadsLedBy(principal.userId, hoje).flatMap { ctx.roles.membersOf(it, hoje) }.toSet()
    if (id !in doSquad) throw ErroHttp(HttpStatusCode.Forbidden)
    return id
}

/** Onde um número do Meu painel aponta: o cartão de destino, o segmento (que depende de quem é o painel) e parâmetros fixos. */
internal class DestinoMeu(val cartao: String, val extras: Map<String, String> = emptyMap(), val segmento: (alvo: String) -> String)

/**
 * Cartão do Meu painel (MP-nn): como o [CartaoDeCentral], só chama o cartão de destino, com `pessoa=<alvo>` (e os parâmetros
 * fixos do destino, como `dias=7`). O alvo vem de [alvoDoMeuPainel], então o painel de outra pessoa só abre para o Squad Leader do
 * squad dela. Sem memória por requisição: cada destino leva parâmetros próprios.
 */
internal class CartaoMeuPainel(
    override val meta: CartaoMeta,
    private val destinos: Map<String, DestinoMeu>,
    private val catalogo: () -> CatalogoDeCartoes,
) : CartaoRegistrado {
    override val instancia: String = INSTANCIA_TRABALHO

    private fun entradaDe(e: EntradaDeCartao, d: DestinoMeu, alvo: String) = EntradaDeCartao(
        e.ctx, e.store, e.principal,
        Parameters.build {
            append("pessoa", alvo)
            d.extras.forEach { (k, v) -> append(k, v) }
        },
    )

    override suspend fun valores(e: EntradaDeCartao): Map<String, Int> {
        val alvo = alvoDoMeuPainel(e.ctx, e.principal, e.parametros["pessoa"])
        return buildMap {
            for ((segmento, d) in destinos) {
                val c = catalogo().porId(d.cartao) ?: continue
                c.valores(entradaDe(e, d, alvo))[d.segmento(alvo)]?.let { put(segmento, it) }
            }
        }
    }

    override suspend fun drill(e: EntradaDeCartao, segmento: String): List<LinhaDeDrill> {
        val d = destinos[segmento] ?: throw ErroHttp(HttpStatusCode.BadRequest, CODIGO_SEGMENTO_INVALIDO)
        val alvo = alvoDoMeuPainel(e.ctx, e.principal, e.parametros["pessoa"])
        val c = catalogo().porId(d.cartao) ?: throw ErroHttp(HttpStatusCode.BadRequest, CODIGO_SEGMENTO_INVALIDO)
        return c.drill(entradaDe(e, d, alvo), d.segmento(alvo))
    }
}

/** Cartão sem tasks por trás (um índice do conjunto): sem números por segmento e sem gaveta. */
private class CartaoDeCentralSemGaveta(override val meta: CartaoMeta) : CartaoRegistrado {
    override suspend fun valores(e: EntradaDeCartao): Map<String, Int> = emptyMap()

    override suspend fun drill(e: EntradaDeCartao, segmento: String): List<LinhaDeDrill> =
        throw ErroHttp(HttpStatusCode.BadRequest, CODIGO_SEGMENTO_INVALIDO)
}

/**
 * Os indicadores da Central de atenção (VG-09), na ordem em que a tela os mostra. A chave é o nome do item (`ItemCentral`) e o
 * valor, o cartão e o segmento de destino. `AUSENCIAS_14D` não está aqui (não tem tasks) e `INDICIO_ALTO` mora no VG-13.
 */
internal val DESTINOS_DA_CENTRAL: Map<String, Destino> = linkedMapOf(
    "VENCIDAS" to Destino("NG-03", "vencidas"),
    "RISCO_ALTO" to Destino("RI-01", "nivel:alto"),
    "ACIMA_DA_CAPACIDADE" to Destino("CP-02", "acimaDaCapacidade"),
    "ATRASOS_PREVISTOS" to Destino("CA-02", "atrasosPrevistos"),
    "SEM_ATUALIZACAO" to Destino("NG-01", "semAtualizacao"),
    "PARADAS_NO_STATUS" to Destino("NG-05", "paradas"),
    "SEM_RESPONSAVEL" to Destino("NG-02", "semResponsavel"),
    "BLOQUEADAS" to Destino("NG-04", "bloqueadas"),
    "COM_FOLGA" to Destino("CP-02", "comFolga"),
    "META_DE_PRAZO" to Destino("MT-01", "noPrazo"),
    "PARES_CR_COM_ALERTA" to Destino("CR-08", "comAlerta"),
    "TASK_REVIEW_DIVERGENTE" to Destino("DV-01", "divergentes"),
    "ALTERACOES_SEM_AUTORIZACAO" to Destino("AU-01", "resultado:auditoria"),
    "SEM_ESTIMATIVA" to Destino("QD-01", "SEM_ESTIMATIVA"),
)

/**
 * Cartões do Início da coordenação e do gerente: VG-01 abertas, VG-04 no prazo, VG-05 vencidas, VG-08 risco, VG-09 itens de
 * atenção, VG-10 entregas por semana, VG-11 backlog, VG-12 por squad e VG-13 indício alto (só gerente, atrás da trava do DPO).
 * O VG-02/03 (concluídas e ciclo) estão em Fechadas e o VG-06/07 (negligenciadas e bloqueadas) são itens do VG-09; ver
 * `docs/analise/diferencas-esperadas-checklist-integridade-governanca.md`.
 */
internal fun cartoesDeInicio(catalogo: () -> CatalogoDeCartoes): List<CartaoRegistrado> {
    val issue = listOf(FonteDoDado.ISSUE)
    val comConfig = listOf(FonteDoDado.ISSUE, FonteDoDado.CONFIGURACAO)
    val comChangelog = listOf(FonteDoDado.ISSUE, FonteDoDado.CHANGELOG, FonteDoDado.CONFIGURACAO)
    fun meta(id: String, natureza: Natureza, fontes: List<FonteDoDado>, temDrill: Boolean = true, papeis: Set<Role> = ALTA) =
        metaDeTrabalho(id, "central", GrupoDeCartao.INICIO, natureza, fontes, exigeChangelog = FonteDoDado.CHANGELOG in fontes, temDrill = temDrill, papeis = papeis)
    fun cartao(id: String, natureza: Natureza, fontes: List<FonteDoDado>, destinos: Map<String, Destino>) =
        CartaoDeCentral(meta(id, natureza, fontes), destinos, catalogo)
    return listOf(
        cartao("VG-01", Natureza.ESTOQUE, issue, mapOf("abertas" to Destino("AB-09", "abertas"))),
        cartao(
            "VG-04", Natureza.PRAZO, comConfig,
            mapOf("noPrazo" to Destino("MT-01", "noPrazo"), "noPrazoAjustado" to Destino("MT-01", "noPrazoAjustado"), "universo" to Destino("MT-01", "universo")),
        ),
        cartao("VG-05", Natureza.PRAZO, issue, mapOf("vencidas" to Destino("NG-03", "vencidas"))),
        cartao("VG-08", Natureza.ESTOQUE, comChangelog, mapOf("alto" to Destino("RI-01", "nivel:alto"), "medio" to Destino("RI-01", "nivel:medio"))),
        cartao("VG-09", Natureza.QUALIDADE, comChangelog, DESTINOS_DA_CENTRAL),
        CartaoDeCentral(meta("VG-10", Natureza.FLUXO, issue), emptyMap(), catalogo, dinamico = "FE-07" to "semana:"),
        cartao("VG-11", Natureza.FLUXO, comConfig, mapOf("backlog" to Destino("PV-02", "backlog"))),
        CartaoDeCentralSemGaveta(meta("VG-12", Natureza.PESSOAS, comConfig, temDrill = false)),
        CartaoDeCentral(
            meta("VG-13", Natureza.PESSOAS, comChangelog, papeis = setOf(Role.GERENTE)),
            mapOf("INDICIO_ALTO" to Destino("IN-02", "nivel:ALTO")), catalogo, moduloSensivel = ModuloSensivel.INTEGRIDADE,
        ),
    ) + cartoesDoMeuPainel(catalogo)
}

/**
 * Os cartões do Meu painel, de todos os papéis (cada um vê o próprio): MP-01 minhas abertas e as que vencem em 7 dias, MP-02
 * ocupação de 4 semanas (sem gaveta), MP-03 em risco (score a partir do médio), MP-04 ausências futuras (sem gaveta, sem motivo)
 * e MP-05 a fila na ordem da agenda.
 */
private fun cartoesDoMeuPainel(catalogo: () -> CatalogoDeCartoes): List<CartaoRegistrado> {
    val todos = Role.entries.toSet()
    val comConfig = listOf(FonteDoDado.ISSUE, FonteDoDado.CONFIGURACAO)
    val comChangelog = listOf(FonteDoDado.ISSUE, FonteDoDado.CHANGELOG, FonteDoDado.CONFIGURACAO)
    fun meta(id: String, natureza: Natureza, fontes: List<FonteDoDado>, temDrill: Boolean = true) =
        metaDeTrabalho(id, "meu", GrupoDeCartao.INICIO, natureza, fontes, exigeChangelog = FonteDoDado.CHANGELOG in fontes, temDrill = temDrill, papeis = todos)
    val fila = DestinoMeu("CP-03") { "pessoa:$it:fila" }
    return listOf(
        CartaoMeuPainel(
            meta("MP-01", Natureza.ESTOQUE, comConfig),
            mapOf("abertas" to fila, "vencemEm7d" to DestinoMeu("VE-01", mapOf("dias" to "7")) { "vencem" }),
            catalogo,
        ),
        CartaoDeCentralSemGaveta(meta("MP-02", Natureza.PESSOAS, comConfig, temDrill = false)),
        CartaoMeuPainel(meta("MP-03", Natureza.QUALIDADE, comChangelog), mapOf("emRisco" to DestinoMeu("RI-03") { "pessoa:$it:emRisco" }), catalogo),
        CartaoDeCentralSemGaveta(meta("MP-04", Natureza.PESSOAS, comConfig, temDrill = false)),
        CartaoMeuPainel(meta("MP-05", Natureza.FLUXO, comConfig), mapOf("fila" to fila), catalogo),
    )
}
