package br.sidia.painel.cards

import br.sidia.painel.AppContext
import br.sidia.painel.cards.abertas.CardAbertas
import br.sidia.painel.cards.abertas.cartoesDeAbertas
import br.sidia.painel.cards.contrato.CartaoMeta
import br.sidia.painel.cards.contrato.CartaoRegistrado
import br.sidia.painel.cards.contrato.FonteDoDado
import br.sidia.painel.cards.contrato.GrupoDeCartao
import br.sidia.painel.cards.contrato.Natureza
import br.sidia.painel.cards.fechadas.CardFechadas
import br.sidia.painel.cards.fechadas.cartoesDeFechadas
import br.sidia.painel.cards.negligenciadas.CardNegligenciadas
import br.sidia.painel.cards.negligenciadas.cartoesDeNegligenciadas
import br.sidia.painel.db.AccessLog
import br.sidia.painel.security.Role
import br.sidia.painel.trabalho.SqliteWorkStore
import io.ktor.server.routing.Route

private val TODOS_OS_PAPEIS = Role.entries.toSet()

/**
 * Metadados de um cartão dos cards de trabalho e prazos. As chaves de tradução seguem o id: `AB-01` usa
 * `card.ab01.titulo`, `card.ab01.oQueMostra` e `card.ab01.comoCalcula` (pt e en, conferidos pelo `CatalogoDeCartoesTest`).
 * Todos os papéis veem o cartão; o dado dentro dele segue o escopo de cada pessoa.
 */
internal fun metaDeTrabalho(
    id: String, tela: String, grupo: GrupoDeCartao, natureza: Natureza, fontes: List<FonteDoDado>, exigeChangelog: Boolean = false,
): CartaoMeta {
    val chave = "card.${id.lowercase().replace("-", "")}"
    return CartaoMeta(
        id = id, grupo = grupo, natureza = natureza, tela = tela,
        tituloChave = "$chave.titulo", oQueMostraChave = "$chave.oQueMostra", comoCalculaChave = "$chave.comoCalcula",
        fontes = fontes, papeis = TODOS_OS_PAPEIS, temDrill = true, exigeChangelog = exigeChangelog, exigeWorklog = false,
    )
}

/** Todos os cartões dos cards de trabalho e prazos, para o `catalogoPadrao()`. */
internal fun cartoesDeTrabalho(): List<CartaoRegistrado> = cartoesDeAbertas() + cartoesDeNegligenciadas() + cartoesDeFechadas()

/** `GET /api/cards/{id}` de cada card de trabalho e prazos (o drill é a rota genérica dos cartões). */
fun Route.rotasDosCardsDeTrabalho(ctx: AppContext, auditoria: AccessLog) {
    val store = SqliteWorkStore(requireNotNull(ctx.dataSource))
    rotaDeCard(ctx, store, auditoria, CardAbertas)
    rotaDeCard(ctx, store, auditoria, CardNegligenciadas)
    rotaDeCard(ctx, store, auditoria, CardFechadas)
}
