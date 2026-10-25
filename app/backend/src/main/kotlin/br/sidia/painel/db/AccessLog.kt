package br.sidia.painel.db

import br.sidia.painel.jql.amostra
import br.sidia.painel.security.normalizarUsername
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.Locale
import javax.sql.DataSource

/**
 * Falha ao gravar em `access_log`. Não carrega usuário, rota, filtros nem a causa original (o driver
 * pode ecoar valores). Quem chama decide: a consulta auditada não deve cair por causa do registro.
 */
class AccessLogException : RuntimeException("falha ao gravar access_log")

/**
 * Registro de consultas (auditoria, spec 7.3): uma linha por consulta em `access_log`.
 * Nunca grava token, senha nem corpo de requisição, e nunca consulta o Jira.
 */
class AccessLog(private val ds: DataSource) {
    /**
     * Grava uma consulta. [userId] é normalizado ([normalizarUsername]; vazio lança
     * [IllegalArgumentException] antes de tocar no banco). [route] perde a query string e é truncada
     * em 200 caracteres. [filters] vira JSON: chaves de segredo são descartadas, valores são saneados
     * (controle vira `?`) e truncados em 200 caracteres, e no máximo 50 filtros são mantidos.
     * Falha de gravação lança [AccessLogException] (sem dados).
     */
    fun record(userId: String, route: String, filters: Map<String, String>, at: Instant) {
        val usuario = normalizarUsername(userId)
        val rota = route.substringBefore('?').let { amostra(it, MAX_VALOR) }
        val json = montarFiltros(filters)
        try {
            ds.connection.use { c ->
                c.prepareStatement("INSERT INTO access_log(user_id, route, filters, at) VALUES (?, ?, ?, ?)").use { ps ->
                    ps.setString(1, usuario)
                    ps.setString(2, rota)
                    ps.setString(3, json)
                    ps.setTimestamp(4, Timestamp.from(at))
                    ps.executeUpdate()
                }
            }
        } catch (e: SQLException) {
            throw AccessLogException()
        } catch (e: RuntimeException) {
            // pool fechado ou indisponível (ex.: SQLTransientConnectionException vira SQLException; o resto chega aqui)
            throw AccessLogException()
        }
    }

    private fun montarFiltros(filters: Map<String, String>): String {
        val aceitos = filters.entries
            .filter { it.key.lowercase(Locale.ROOT) !in CHAVES_SECRETAS }
            .take(MAX_FILTROS)
            .associate { amostra(it.key, MAX_VALOR) to JsonPrimitive(amostra(it.value, MAX_VALOR)) }
        return JsonObject(aceitos).toString()
    }

    private companion object {
        const val MAX_VALOR = 200
        const val MAX_FILTROS = 50
        val CHAVES_SECRETAS = setOf("token", "password", "senha", "secret", "authorization", "cookie", "key")
    }
}
