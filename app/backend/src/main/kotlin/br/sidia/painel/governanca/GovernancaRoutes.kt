package br.sidia.painel.governanca

import br.sidia.painel.AppContext
import br.sidia.painel.cards.CODIGO_SEGMENTO_INVALIDO
import br.sidia.painel.cards.ErroHttp
import br.sidia.painel.cards.contrato.CartaoMeta
import br.sidia.painel.cards.contrato.CartaoRegistrado
import br.sidia.painel.cards.contrato.EntradaDeCartao
import br.sidia.painel.cards.contrato.FonteDoDado
import br.sidia.painel.cards.contrato.GrupoDeCartao
import br.sidia.painel.cards.contrato.LinhaDeDrill
import br.sidia.painel.cards.contrato.Natureza
import br.sidia.painel.cards.metaDeTrabalho
import br.sidia.painel.cards.rotaDeCard
import br.sidia.painel.db.AccessLog
import br.sidia.painel.security.Role
import br.sidia.painel.trabalho.SqliteWorkStore
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route

/**
 * Rotas da Governança que seguem o molde dos cards: `GET /api/cards/qualidade` (coordenação e gerente). O registro de acesso e a
 * configuração em leitura têm rotas próprias (`/api/governanca/acessos` e `/api/governanca/configuracao`, só do gerente).
 */
fun Route.rotasDeGovernanca(ctx: AppContext, auditoria: AccessLog) {
    val store = SqliteWorkStore(requireNotNull(ctx.dataSource))
    rotaDeCard(ctx, store, auditoria, CardQualidade, PAPEIS_DA_QUALIDADE)
    rotasDeRegistroDeAcesso(ctx, auditoria)
    rotasDeConfiguracao(ctx, auditoria)
}

/** Todos os cartões da Governança, para o `catalogoPadrao()` (as Decisões de distribuição moram em `distribuicao/`). */
fun cartoesDeGovernanca(): List<CartaoRegistrado> = cartoesDeQualidade() + cartoesDeRegistroEConfiguracao()

/** Cartão sem tasks por trás (uma lista de linhas ou de arquivos): não tem números por segmento e a gaveta não abre. */
private class CartaoSemGaveta(override val meta: CartaoMeta) : CartaoRegistrado {
    override suspend fun valores(e: EntradaDeCartao): Map<String, Int> = emptyMap()

    override suspend fun drill(e: EntradaDeCartao, segmento: String): List<LinhaDeDrill> =
        throw ErroHttp(HttpStatusCode.BadRequest, CODIGO_SEGMENTO_INVALIDO)
}

/**
 * RA-01 (registro de acesso) e CF-01 (configuração em leitura): só do gerente, sem gaveta. O texto de cada um diz o que NÃO mostram
 * (filtros crus, segmentos, chaves, segredos, caminhos).
 */
private fun cartoesDeRegistroEConfiguracao(): List<CartaoRegistrado> {
    fun meta(id: String, tela: String, fontes: List<FonteDoDado>) = metaDeTrabalho(
        id, tela, GrupoDeCartao.GOVERNANCA, Natureza.QUALIDADE, fontes, temDrill = false, papeis = setOf(Role.GERENTE),
    )
    return listOf(
        CartaoSemGaveta(meta("RA-01", "registroAcesso", listOf(FonteDoDado.SINCRONIZACAO))),
        CartaoSemGaveta(meta("CF-01", "configuracao", listOf(FonteDoDado.CONFIGURACAO))),
    )
}
