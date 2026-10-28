package br.sidia.painel.db

import br.sidia.painel.jql.amostra
import br.sidia.painel.security.normalizarUsername
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import org.slf4j.LoggerFactory
import java.util.Locale
import javax.sql.DataSource

/**
 * Falha ao gravar em `access_log`. Não carrega usuário, rota, filtros nem a causa original (o driver
 * pode ecoar valores). Quem chama decide: nas rotas de dados, falha de auditoria faz a rota responder
 * 503 sem dados (sem registro não há resposta). O log do servidor guarda só o SQLState.
 */
class AccessLogException : RuntimeException("falha ao gravar access_log")

/**
 * Ponto de injeção da observação: quem monta o painel (métricas e alertas) registra um ouvinte e passa a saber de cada
 * falha de gravação do `access_log`, sem mudar a assinatura de [AccessLog] nem de quem a instancia. Nunca recebe dados.
 */
object ObservadorDeAccessLog {
    @Volatile
    var aoFalhar: () -> Unit = {}

    internal fun avisarFalha() {
        try {
            aoFalhar()
        } catch (e: Exception) {
            // observar nunca muda o desfecho da gravação
        }
    }
}

/** Uma linha do registro como está gravada: quem, qual rota (o padrão, sem chave de issue), os filtros em JSON e quando. */
data class LinhaAcesso(val userId: String, val route: String, val filters: String?, val at: Instant)

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
        val usuario = sanearUsuario(normalizarUsername(userId))
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
            // Só o SQLState (código padrão, sem dados) vai para o log do servidor; a mensagem do driver pode ecoar valores.
            log.error("Falha ao gravar access_log (SQLState={})", e.sqlState)
            ObservadorDeAccessLog.avisarFalha()
            throw AccessLogException()
        } catch (e: RuntimeException) {
            // pool fechado ou indisponível (ex.: SQLTransientConnectionException vira SQLException; o resto chega aqui)
            log.error("Falha ao gravar access_log ({})", e.javaClass.simpleName)
            ObservadorDeAccessLog.avisarFalha()
            throw AccessLogException()
        }
    }

    /**
     * Lê o registro, do mais recente ao mais antigo (por `id`), SEM nunca ir além do que [record] guarda. [de] é inclusivo e [ate]
     * exclusivo. [usuario] é igualdade exata (já normalizado por quem chama); [rotaPrefixo] casa o começo da rota gravada (sem
     * diferenciar caixa; `%`, `_` e `\` do texto não viram curinga). [depoisDe] é o cursor: só linhas com `id` menor que ele.
     * Devolve até [limite] linhas com o `id` de cada uma (para o cursor da próxima página). Não grava nada.
     */
    fun listar(de: Instant, ate: Instant, usuario: String?, rotaPrefixo: String?, limite: Int, depoisDe: Long?): List<Pair<Long, LinhaAcesso>> {
        require(limite in 1..MAX_LIMITE_LISTAR) { "limite fora de 1..$MAX_LIMITE_LISTAR" }
        val sql = buildString {
            append("SELECT id, user_id, route, filters, at FROM access_log WHERE at >= ? AND at < ?")
            if (usuario != null) append(" AND user_id = ?")
            if (rotaPrefixo != null) append(" AND route LIKE ? ESCAPE '\\'")
            if (depoisDe != null) append(" AND id < ?")
            append(" ORDER BY id DESC LIMIT ?")
        }
        return ds.connection.use { c ->
            c.prepareStatement(sql).use { ps ->
                var i = 1
                ps.setTimestamp(i++, Timestamp.from(de))
                ps.setTimestamp(i++, Timestamp.from(ate))
                if (usuario != null) ps.setString(i++, usuario)
                if (rotaPrefixo != null) ps.setString(i++, rotaPrefixo.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%")
                if (depoisDe != null) ps.setLong(i++, depoisDe)
                ps.setInt(i, limite)
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) add(rs.getLong(1) to LinhaAcesso(rs.getString(2), rs.getString(3), rs.getString(4), rs.getTimestamp(5).toInstant()))
                    }
                }
            }
        }
    }

    /** Controle vira `?` e o id é limitado a [MAX_USUARIO] caracteres (o username real tem no máximo 64). */
    private fun sanearUsuario(id: String): String =
        id.map { if (it.isISOControl()) '?' else it }.joinToString("").take(MAX_USUARIO)

    private fun montarFiltros(filters: Map<String, String>): String {
        val aceitos = filters.entries
            .filter { !chaveSecreta(it.key) }
            .take(MAX_FILTROS)
            .associate { amostra(it.key, MAX_VALOR) to JsonPrimitive(amostra(it.value, MAX_VALOR)) }
        return JsonObject(aceitos).toString()
    }

    private companion object {
        const val MAX_LIMITE_LISTAR = 1_000
        const val MAX_VALOR = 200
        const val MAX_FILTROS = 50
        const val MAX_USUARIO = 64
        val log = LoggerFactory.getLogger(AccessLog::class.java)

        /** Fragmentos que, em qualquer posição da chave normalizada, marcam um segredo. */
        val FRAGMENTOS_SECRETOS = listOf(
            "token", "secret", "password", "passwd", "senha", "authorization", "cookie", "jwt",
            "apikey", "bearer", "credential", "session", "assertion",
        )

        private val SEPARADORES = Regex("[_\\-\\s.]")

        /**
         * Normaliza (minúsculas, sem `_`, `-`, `.` nem espaço) e casa por fragmento: `access_token`,
         * `Api-Key`, `client_secret`, `" token"` e `jwt` são segredos. `key` sozinho também.
         */
        fun chaveSecreta(chave: String): Boolean {
            val n = chave.lowercase(Locale.ROOT).replace(SEPARADORES, "")
            return n == "key" || FRAGMENTOS_SECRETOS.any { it in n }
        }
    }
}
