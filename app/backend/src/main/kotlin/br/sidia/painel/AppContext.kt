package br.sidia.painel

import br.sidia.painel.config.Settings
import br.sidia.painel.db.Database
import br.sidia.painel.auditoria.RegrasDeAuditoria
import br.sidia.painel.domain.LabelsDeAtraso
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.jira.JiraClientFactory
import br.sidia.painel.jql.CardSpec
import br.sidia.painel.jql.CardSpecs
import br.sidia.painel.jql.FieldDictionary
import br.sidia.painel.security.Role
import br.sidia.painel.security.RoleDirectory
import br.sidia.painel.sync.VinculosConfig
import br.sidia.painel.time.BusinessCalendar
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.LocalDate
import javax.sql.DataSource

/**
 * Dependências de longa vida do painel, montadas uma vez na subida: calendário, diretório de papéis,
 * mapa de status, pool do banco, fábrica do cliente Jira e relógio. [dataSource] é nulo apenas em
 * testes sem banco. [clock] é o relógio do painel (injetável em testes), sempre no fuso das configurações.
 * Nenhuma mensagem nem log carrega senha, token ou URL com credenciais.
 */
class AppContext(
    val settings: Settings,
    val calendar: BusinessCalendar,
    val roles: RoleDirectory,
    val dataSource: DataSource?,
    val jira: JiraClientFactory,
    val statusMap: StatusMap,
    val campos: FieldDictionary,
    val fichas: Map<String, CardSpec>,
    /** Como a Review se liga à sua task (tipo do link, tipos de issue); lido de `vinculos.yaml`. */
    val vinculos: VinculosConfig,
    /** Labels que justificam atraso e os que marcam entrega tardia; lido de `labels_atraso.yaml`. */
    val labelsDeAtraso: LabelsDeAtraso,
    /** Limites, gravidades e versão das regras da auditoria; lido de `auditoria.yaml`. */
    val regrasAuditoria: RegrasDeAuditoria,
    val clock: Clock = Clock.system(settings.zone),
) : AutoCloseable {
    private val aoFechar = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    /** Registra uma ação que roda ANTES de fechar o Jira e o banco (ex.: parar o agendador). */
    fun aoFechar(acao: () -> Unit) {
        aoFechar += acao
    }

    /**
     * Roda as ações registradas (cada uma isolada: a falha de uma não impede as outras), fecha os clientes Jira e,
     * SEMPRE depois (mesmo se isso falhar), o pool de conexões (se houver).
     */
    override fun close() {
        try {
            val acoes = aoFechar.toList().also { aoFechar.clear() }
            for (acao in acoes) {
                try {
                    acao()
                } catch (e: Exception) {
                    // Só a classe: a mensagem pode trazer dados. A falha de uma ação não impede as outras.
                    log.warn("Falha ao encerrar um componente: {}", e.javaClass.simpleName)
                }
            }
            jira.close()
        } finally {
            (dataSource as? AutoCloseable)?.close()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AppContext::class.java)

        /**
         * Carrega arquivos de configuração, valida a senha do banco, abre o pool e migra o schema.
         * Falhas de configuração e de banco viram [IllegalStateException] sem segredos.
         */
        fun load(settings: Settings, clock: Clock = Clock.system(settings.zone)): AppContext {
            val arquivoCalendario = resolver(settings, "calendario")
            val arquivoPapeis = resolver(settings, "papeis")
            val calendario = lerYaml(arquivoCalendario) { BusinessCalendar.fromYaml(arquivoCalendario, settings.zone) }
            val papeis = lerYaml(arquivoPapeis) { RoleDirectory.fromYaml(arquivoPapeis) }
            exigirGerenteVigente(settings, papeis, arquivoPapeis, LocalDate.now(clock))
            // Mapa de status: em produção exige o arquivo real (sem inventar padrão); o modelo só cai em dev.
            val arquivoStatus = resolver(settings, "status_map")
            val mapaDeStatus = lerYaml(arquivoStatus) { StatusMap.fromYaml(arquivoStatus) }

            // Dicionário de campos do Jira e fichas dos cards: vocabulário e consultas, sem tocar o Jira.
            val arquivoCampos = resolverDicionario(settings)
            val campos = lerYaml(arquivoCampos) { FieldDictionary.fromYaml(arquivoCampos) }
            val fichas = carregarFichas(settings, campos)
            // Vínculos Review x task (tipo do link e tipos de issue): config sem dado pessoal; o modelo só cai em dev.
            val arquivoVinculos = resolver(settings, "vinculos")
            val vinculos = lerYaml(arquivoVinculos) { VinculosConfig.fromYaml(arquivoVinculos) }
            // Labels de atraso e regras da auditoria: config versionada sem dado pessoal; o modelo só cai em dev.
            val arquivoLabels = resolver(settings, "labels_atraso")
            val labelsDeAtraso = lerYaml(arquivoLabels) { LabelsDeAtraso.fromYaml(arquivoLabels) }
            val arquivoAuditoria = resolver(settings, "auditoria")
            val regrasAuditoria = lerYaml(arquivoAuditoria) { RegrasDeAuditoria.fromYaml(arquivoAuditoria, vinculos) }

            // Valida na subida todas as instâncias do Jira configuradas (sem enviar nada); só id e regra na falha.
            val jira = JiraClientFactory(settings)
            jira.validarInstancias()

            if (settings.databasePassword.isBlank()) {
                if (!settings.dashboardDevMode) {
                    throw IllegalStateException("DATABASE_PASSWORD obrigatório em produção")
                }
                log.warn("DATABASE_PASSWORD vazio: aceito apenas em modo desenvolvimento.")
            }

            val ds = abrirBanco(settings)
            return AppContext(settings, calendario, papeis, ds, jira, mapaDeStatus, campos, fichas, vinculos, labelsDeAtraso, regrasAuditoria, clock)
        }

        /**
         * Lê um YAML citando só o caminho e o TIPO da exceção, sem causa encadeada: as mensagens do
         * parser trazem trechos do arquivo, e o arquivo de papéis tem nomes de pessoas.
         */
        private fun <T> lerYaml(arquivo: Path, leitura: () -> T): T =
            try {
                leitura()
            } catch (e: Exception) {
                throw IllegalStateException("arquivo $arquivo inválido: ${e.javaClass.simpleName}")
            }

        /** Sem nenhum GERENTE vigente hoje ninguém acessaria a Integridade: falha em produção, só avisa em dev. */
        private fun exigirGerenteVigente(settings: Settings, papeis: RoleDirectory, arquivo: Path, hoje: LocalDate) {
            if (papeis.hasRoleOn(Role.GERENTE, hoje)) return
            if (!settings.dashboardDevMode) {
                throw IllegalStateException("Nenhum GERENTE vigente em $arquivo")
            }
            log.warn("Nenhum GERENTE vigente em $arquivo (aceito apenas em modo desenvolvimento).")
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

        /** Com o Jira ligado em produção, dicionário e fichas reais são obrigatórios (nada de modelo ou lista vazia). */
        private fun exigeArquivosReaisDoJira(settings: Settings): Boolean =
            settings.jiraLiveEnabled && !settings.dashboardDevMode

        /**
         * Dicionário de campos: o arquivo real `jira_campos.yaml`; sem ele, o modelo `jira_campos.exemplo.yaml`
         * com aviso, exceto em produção com o Jira ligado, onde falha citando o caminho esperado.
         */
        private fun resolverDicionario(settings: Settings): Path {
            val real = settings.configDir.resolve("jira_campos.yaml")
            if (Files.isRegularFile(real)) return real
            if (exigeArquivosReaisDoJira(settings)) {
                throw IllegalStateException("Arquivo de configuração obrigatório com o Jira ligado em produção não encontrado: $real")
            }
            val exemplo = settings.configDir.resolve("jira_campos.exemplo.yaml")
            if (!Files.isRegularFile(exemplo)) {
                throw IllegalStateException("Nem $real nem o modelo $exemplo foram encontrados")
            }
            log.warn("jira_campos.yaml não encontrado em ${settings.configDir}: usando o modelo jira_campos.exemplo.yaml (vocabulário de exemplo, sem consulta ao Jira).")
            return exemplo
        }

        /**
         * Fichas `configDir/cards/` (arquivos terminados em `.card.yaml`), por `id`, conferidas contra o dicionário.
         * Ficha inválida ou `id` repetido falha a subida citando só o arquivo e o TIPO da exceção.
         * Sem a pasta `cards`: falha com o Jira ligado em produção; nos demais casos avisa e segue sem fichas.
         */
        private fun carregarFichas(settings: Settings, campos: FieldDictionary): Map<String, CardSpec> {
            val pasta = settings.configDir.resolve("cards")
            if (!Files.isDirectory(pasta)) {
                if (exigeArquivosReaisDoJira(settings)) {
                    throw IllegalStateException("Pasta de fichas obrigatória com o Jira ligado em produção não encontrada: $pasta")
                }
                log.warn("Pasta de fichas $pasta não encontrada: nenhuma ficha carregada.")
                return emptyMap()
            }
            val arquivos = Files.list(pasta).use { itens ->
                itens.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".card.yaml") }.sorted().toList()
            }
            val fichas = LinkedHashMap<String, CardSpec>()
            for (arquivo in arquivos) {
                val ficha = lerFicha(arquivo) { CardSpecs.fromYaml(arquivo, campos) }
                check(fichas.put(ficha.id, ficha) == null) { "ficha com id repetido '${ficha.id}' em $arquivo" }
            }
            return fichas
        }

        private fun <T> lerFicha(arquivo: Path, leitura: () -> T): T =
            try {
                leitura()
            } catch (e: Exception) {
                throw IllegalStateException("ficha $arquivo inválida: ${e.javaClass.simpleName}")
            }

        /** Abre o pool e migra; a falha cita só o tipo da exceção (a original pode trazer URL e senha). */
        private fun abrirBanco(settings: Settings): DataSource {
            val destino = Settings.mascararUrl(settings.databaseUrl)
            val ds = try {
                Database.connect(settings.databaseUrl, settings.databaseUser, settings.databasePassword, settings.databasePoolSize)
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
