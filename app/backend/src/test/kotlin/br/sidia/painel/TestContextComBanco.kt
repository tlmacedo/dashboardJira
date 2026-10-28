package br.sidia.painel

import br.sidia.painel.config.Settings
import br.sidia.painel.db.Database
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.jira.JiraClientFactory
import br.sidia.painel.security.RoleDirectory
import br.sidia.painel.time.BusinessCalendar
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import kotlin.io.path.deleteRecursively

/**
 * Pasta temporária com arquivos SQLite de teste (nada de servidor). Cada classe de teste cria uma
 * (`companion object`) e a apaga no `@AfterAll`. [novoArquivo] devolve sempre um arquivo novo e vazio, então cada
 * teste começa com banco limpo e nunca herda dados nem conexões de outro.
 */
class BancoEmArquivo {
    private val pasta: Path = Files.createTempDirectory("painel-teste-")
    private var contador = 0

    /** Arquivo do banco "padrão" desta pasta (para testes que abrem o banco por conta própria). */
    val arquivo: Path get() = pasta.resolve("painel.db")

    /** URL JDBC do [arquivo] (sem ajustes de conexão: o [Database.connect] os acrescenta). */
    val jdbcUrl: String get() = Database.urlDeArquivo(arquivo)

    /** Um arquivo novo (inexistente) nesta pasta. */
    fun novoArquivo(): Path = pasta.resolve("painel-${++contador}.db")

    /** Esvazia o banco "padrão": apaga o arquivo e os auxiliares do WAL (chamar com o pool fechado ou antes de abrir). */
    fun limpar() {
        for (sufixo in listOf("", "-wal", "-shm")) runCatching { Files.deleteIfExists(pasta.resolve("painel.db$sufixo")) }
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    fun apagar() {
        runCatching { pasta.deleteRecursively() }
    }
}

/**
 * Contexto de teste COM SQLite real em arquivo (nunca mock). O [banco] é do chamador: cada classe de teste cria um só
 * (`companion object`) e o apaga no `@AfterAll`. Cada chamada usa um arquivo novo, migrado do zero. Calendário e papéis
 * vêm dos arquivos de exemplo. Nenhuma requisição ao Jira é aberta.
 */
fun testContextComBanco(
    settings: Settings,
    banco: BancoEmArquivo,
    roles: RoleDirectory = RoleDirectory.fromYaml(TestPaths.configDir.resolve("papeis.exemplo.yaml")),
    clock: Clock = Clock.system(settings.zone),
    jira: JiraClientFactory = JiraClientFactory(settings),
    /** Mapa de status e calendário próprios do cenário (padrão: os arquivos `.exemplo`). */
    statusMap: StatusMap? = null,
    calendar: BusinessCalendar? = null,
    /** Banco já pronto (ex.: uma cópia restaurada) em vez de um arquivo novo; as migrações são conferidas, não refeitas. */
    arquivoExistente: java.nio.file.Path? = null,
    /** Equipe, ausências e regras de planejamento do cenário (padrão: os modelos `.exemplo`). */
    equipes: br.sidia.painel.pessoas.Equipes = equipesDeTeste(),
    ausencias: br.sidia.painel.pessoas.Ausencias = ausenciasDeTeste(),
    regrasPlanejamento: br.sidia.painel.pessoas.RegrasDePlanejamento = regrasDePlanejamentoDeTeste(),
): AppContext {
    val campos = camposDeTeste()
    val vinculos = vinculosDeTeste()
    val ds = Database.connect(Database.urlDeArquivo(arquivoExistente ?: banco.novoArquivo()), poolSize = 4)
    Database.migrate(ds)
    return AppContext(
        settings = settings,
        calendar = calendar ?: BusinessCalendar.fromYaml(TestPaths.configDir.resolve("calendario.exemplo.yaml"), settings.zone),
        roles = roles,
        dataSource = ds,
        jira = jira,
        statusMap = statusMap ?: StatusMap.fromYaml(TestPaths.configDir.resolve("status_map.exemplo.yaml")),
        campos = campos,
        fichas = fichasDeTeste(campos),
        vinculos = vinculos,
        labelsDeAtraso = labelsDeAtrasoDeTeste(),
        regrasAuditoria = regrasDeAuditoriaDeTeste(vinculos),
        clock = clock,
        regrasDeMetas = regrasDeMetasDeTeste(),
        regrasDeRisco = regrasDeRiscoDeTeste(),
        regrasDePrevisao = regrasDePrevisaoDeTeste(),
        equipes = equipes,
        ausencias = ausencias,
        regrasPlanejamento = regrasPlanejamento,
    )
}

/** Apaga todas as linhas de `access_log` (limpeza entre testes que reaproveitam o contexto). */
fun AppContext.limparAccessLog() {
    requireNotNull(dataSource).connection.use { it.createStatement().execute("DELETE FROM access_log") }
}

/** Quantidade de linhas em `access_log`. */
fun AppContext.contarAccessLog(): Int =
    requireNotNull(dataSource).connection.use { c ->
        c.createStatement().executeQuery("SELECT count(*) FROM access_log").use { it.next(); it.getInt(1) }
    }
