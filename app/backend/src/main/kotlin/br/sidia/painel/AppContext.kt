package br.sidia.painel

import br.sidia.painel.config.Settings
import br.sidia.painel.db.Database
import br.sidia.painel.jira.JiraClientFactory
import br.sidia.painel.security.RoleDirectory
import br.sidia.painel.time.BusinessCalendar
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import javax.sql.DataSource

/**
 * Dependências de longa vida do painel, montadas uma vez na subida: calendário, diretório de papéis,
 * pool do banco e fábrica do cliente Jira. [dataSource] é nulo apenas em testes sem banco.
 * Nenhuma mensagem nem log carrega senha, token ou URL com credenciais.
 */
class AppContext(
    val settings: Settings,
    val calendar: BusinessCalendar,
    val roles: RoleDirectory,
    val dataSource: DataSource?,
    val jira: JiraClientFactory,
) : AutoCloseable {
    /** Fecha o pool de conexões (se houver). */
    override fun close() {
        (dataSource as? AutoCloseable)?.close()
    }

    companion object {
        private val log = LoggerFactory.getLogger(AppContext::class.java)

        /**
         * Carrega arquivos de configuração, valida a senha do banco, abre o pool e migra o schema.
         * Falhas de configuração e de banco viram [IllegalStateException] sem segredos.
         */
        @Suppress("UNUSED_PARAMETER")
        fun load(settings: Settings, clock: Clock = Clock.system(settings.zone)): AppContext {
            val arquivoCalendario = resolver(settings, "calendario")
            val arquivoPapeis = resolver(settings, "papeis")
            val calendario = BusinessCalendar.fromYaml(arquivoCalendario, settings.zone)
            val papeis = RoleDirectory.fromYaml(arquivoPapeis)

            if (settings.databasePassword.isEmpty()) {
                if (!settings.dashboardDevMode) {
                    throw IllegalStateException("DATABASE_PASSWORD obrigatório em produção")
                }
                log.warn("DATABASE_PASSWORD vazio: aceito apenas em modo desenvolvimento.")
            }

            val ds = abrirBanco(settings)
            return AppContext(settings, calendario, papeis, ds, JiraClientFactory(settings))
        }

        /**
         * Arquivo real em `configDir`; sem ele, em desenvolvimento usa o `.exemplo.yaml` com aviso,
         * e em produção falha citando o caminho esperado.
         */
        private fun resolver(settings: Settings, nome: String): Path {
            val real = settings.configDir.resolve("$nome.yaml")
            if (Files.isRegularFile(real)) return real
            if (!settings.dashboardDevMode) {
                throw IllegalStateException("Arquivo de configuração obrigatório em produção não encontrado: $real")
            }
            val exemplo = settings.configDir.resolve("$nome.exemplo.yaml")
            if (!Files.isRegularFile(exemplo)) {
                throw IllegalStateException("Nem $real nem o modelo $exemplo foram encontrados")
            }
            log.warn("$nome.yaml não encontrado em ${settings.configDir}: usando o modelo $nome.exemplo.yaml (dados fictícios, só em desenvolvimento).")
            return exemplo
        }

        /** Abre o pool e migra; a falha cita só o tipo da exceção (a original pode trazer URL e senha). */
        private fun abrirBanco(settings: Settings): DataSource {
            val destino = Settings.mascararUrl(settings.databaseUrl)
            val ds = try {
                Database.connect(settings.databaseUrl, settings.databaseUser, settings.databasePassword)
            } catch (e: Exception) {
                throw IllegalStateException("Falha ao conectar ao banco em $destino (${e.javaClass.simpleName})")
            }
            try {
                Database.migrate(ds)
            } catch (e: Exception) {
                (ds as? AutoCloseable)?.close()
                throw IllegalStateException("Falha ao migrar o banco em $destino (${e.javaClass.simpleName})")
            }
            return ds
        }
    }
}
