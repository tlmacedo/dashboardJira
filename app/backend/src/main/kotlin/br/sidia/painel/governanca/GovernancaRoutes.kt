package br.sidia.painel.governanca

import br.sidia.painel.AppContext
import br.sidia.painel.cards.contrato.CartaoRegistrado
import br.sidia.painel.cards.rotaDeCard
import br.sidia.painel.db.AccessLog
import br.sidia.painel.trabalho.SqliteWorkStore
import io.ktor.server.routing.Route

/**
 * Rotas da Governança que seguem o molde dos cards: `GET /api/cards/qualidade` (coordenação e gerente). O registro de acesso e a
 * configuração em leitura têm rotas próprias (`/api/governanca/acessos` e `/api/governanca/configuracao`, só do gerente).
 */
fun Route.rotasDeGovernanca(ctx: AppContext, auditoria: AccessLog) {
    val store = SqliteWorkStore(requireNotNull(ctx.dataSource))
    rotaDeCard(ctx, store, auditoria, CardQualidade, PAPEIS_DA_QUALIDADE)
}

/** Todos os cartões da Governança, para o `catalogoPadrao()` (as Decisões de distribuição moram em `distribuicao/`). */
fun cartoesDeGovernanca(): List<CartaoRegistrado> = cartoesDeQualidade()
