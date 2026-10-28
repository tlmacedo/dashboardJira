package br.sidia.painel.operacao

import br.sidia.painel.AppContext
import br.sidia.painel.db.Database
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import org.sqlite.SQLiteDataSource
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.time.Clock
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Por que o painel não está pronto. O nome vai no corpo da resposta e nos runbooks. */
enum class MotivoNaoPronto { BANCO_INDISPONIVEL, MIGRACAO_PENDENTE, DISCO_BAIXO, TRAVA_PERDIDA }

/** Resposta pública e mínima da prontidão: nada de versão, host, caminho nem detalhe. */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class ProntidaoDto(val pronto: Boolean, @EncodeDefault(EncodeDefault.Mode.NEVER) val codigo: String? = null)

/** Espaço livre do volume. */
object VerificacaoDeDisco {
    /** Bytes utilizáveis no volume de [pasta]; pasta inexistente sobe ao primeiro ancestral que existe. */
    fun livreBytes(pasta: Path): Long {
        var p: Path? = pasta.toAbsolutePath()
        while (p != null && !Files.exists(p)) p = p.parent
        return Files.getFileStore(p ?: pasta.toAbsolutePath().root).usableSpace
    }
}

/**
 * Verifica se o painel está pronto para trabalhar: arquivo do banco acessível e gravável (por permissão do arquivo e da
 * pasta; o WAL escreve na pasta), migrações em dia, espaço em disco e trava de processo ainda detida. A sonda usa conexão
 * PRÓPRIA (somente leitura, `busy_timeout` curto), fora do pool, para responder mesmo com o pool esgotado ou o banco
 * ocupado. Não faz escrita de teste (uma gravação longa, como a retenção, geraria falso alarme): a escrita real é provada
 * a cada sincronização e a cada backup. O resultado vale 5 s; a checagem inteira tem [tempoMaximo].
 */
class Prontidao(
    private val arquivoDoBanco: Path,
    /** `Database.urlDeArquivo(arquivo)` + `?busy_timeout=1500&open_mode=1`. */
    private val jdbcUrlDeSonda: String,
    /** Quantas migrações faltam (`Database.migracoesPendentes`, também por conexão própria). */
    private val pendentes: () -> Int,
    private val travaValida: () -> Boolean,
    private val discoMinBytes: Long,
    private val clock: Clock,
    private val tempoMaximo: Duration = 2.seconds,
) {
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val trava = Mutex()
    private var guardado: Pair<Instant, ProntidaoDto>? = null
    private var ultimoCodigo: String? = null

    suspend fun avaliar(): ProntidaoDto = trava.withLock {
        val agora = clock.instant()
        guardado?.let { (quando, r) -> if (agora < quando.plusSeconds(VALIDADE_S)) return r }
        val r = calcular()
        guardado = agora to r
        registrarMudanca(r)
        r
    }

    private suspend fun calcular(): ProntidaoDto {
        // O trabalho bloqueante roda numa corrotina própria: o tempo máximo solta quem chamou mesmo que o JDBC demore.
        // Sem resposta no tempo máximo = banco indisponível (a corrotina órfã termina sozinha no busy_timeout).
        val sonda = withTimeoutOrNull(tempoMaximo) { escopo.async { sondar() }.await() } ?: ResultadoDaSonda.BANCO
        val motivo = when (sonda) {
            ResultadoDaSonda.BANCO -> MotivoNaoPronto.BANCO_INDISPONIVEL
            ResultadoDaSonda.MIGRACAO -> MotivoNaoPronto.MIGRACAO_PENDENTE
            ResultadoDaSonda.OK -> motivoDeDiscoOuTrava()
        }
        return if (motivo == null) ProntidaoDto(true) else ProntidaoDto(false, motivo.name)
    }

    private enum class ResultadoDaSonda { OK, BANCO, MIGRACAO }

    private fun sondar(): ResultadoDaSonda {
        if (!bancoAcessivel()) return ResultadoDaSonda.BANCO
        val faltam = try {
            pendentes()
        } catch (e: Exception) {
            return ResultadoDaSonda.BANCO
        }
        return if (faltam > 0) ResultadoDaSonda.MIGRACAO else ResultadoDaSonda.OK
    }

    private fun motivoDeDiscoOuTrava(): MotivoNaoPronto? = when {
        VerificacaoDeDisco.livreBytes(arquivoDoBanco.toAbsolutePath().parent ?: arquivoDoBanco) < discoMinBytes -> MotivoNaoPronto.DISCO_BAIXO
        !travaValida() -> MotivoNaoPronto.TRAVA_PERDIDA
        else -> null
    }

    private fun bancoAcessivel(): Boolean =
        try {
            val pasta = arquivoDoBanco.toAbsolutePath().parent
            Files.isRegularFile(arquivoDoBanco) && Files.isWritable(arquivoDoBanco) && pasta != null && Files.isWritable(pasta) &&
                DriverManager.getConnection(jdbcUrlDeSonda).use { c ->
                    c.createStatement().use { st ->
                        st.queryTimeout = 2
                        st.executeQuery("SELECT 1 FROM flyway_schema_history LIMIT 1").use { it.next() }
                        true
                    }
                }
        } catch (e: Exception) {
            false
        }

    private fun registrarMudanca(r: ProntidaoDto) {
        if (r.codigo == ultimoCodigo) return
        if (r.pronto) log.info("Prontidão restabelecida") else log.warn("Prontidão: não pronto ({})", r.codigo)
        ultimoCodigo = r.codigo
    }

    private companion object {
        const val VALIDADE_S = 5L
        val log = LoggerFactory.getLogger(Prontidao::class.java)
    }
}

/** Prontidão do painel real: sonda e checagem de migrações por conexão própria sobre o arquivo do pool em uso. */
fun prontidaoPadrao(ctx: AppContext): Prontidao? {
    val ds = ctx.dataSource ?: return null
    val arquivo = Database.arquivoDe(ds) ?: return null
    val url = Database.urlDeArquivo(arquivo) + "?busy_timeout=1500&open_mode=1"
    val sonda = SQLiteDataSource().apply { this.url = url }
    return Prontidao(
        arquivoDoBanco = arquivo,
        jdbcUrlDeSonda = url,
        pendentes = { Database.migracoesPendentes(sonda) },
        travaValida = { ctx.travaValida() },
        discoMinBytes = ctx.settings.operacao.discoMinMb.toLong() * 1024 * 1024,
        clock = ctx.clock,
    )
}

/**
 * `GET /api/health/pronto` (pública): 200 `{"pronto":true}` ou 503 `{"pronto":false,"codigo":"..."}`. Sem prontidão (só em
 * testes, sem banco) responde 503 `BANCO_INDISPONIVEL`. `/api/health` continua sendo a vivacidade (o processo responde).
 */
fun Route.rotaProntidao(prontidao: Prontidao?) {
    get("/api/health/pronto") {
        val r = prontidao?.avaliar() ?: ProntidaoDto(false, MotivoNaoPronto.BANCO_INDISPONIVEL.name)
        call.respond(if (r.pronto) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable, r)
    }
}
