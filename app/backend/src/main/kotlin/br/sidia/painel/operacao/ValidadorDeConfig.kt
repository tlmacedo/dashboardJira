package br.sidia.painel.operacao

import br.sidia.painel.AppContext
import br.sidia.painel.ErroDeConfiguracao
import br.sidia.painel.config.Settings
import br.sidia.painel.domain.UnmappedStatusException
import br.sidia.painel.jql.CardSpecs
import br.sidia.painel.security.RoleDirectory
import br.sidia.painel.pessoas.linhaDoItem
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.LocalDate

/** Um problema de configuração: código estável, arquivo (nome relativo) e linha. NUNCA o valor errado (pode ser nome de pessoa). */
data class ProblemaDeConfig(val codigo: String, val arquivo: String, val linha: Int? = null)

data class ResultadoDaValidacao(
    val problemas: List<ProblemaDeConfig>,
    val arquivosOk: List<String>,
    /** Avisos não reprovam a validação (ex.: métricas fora do loopback, que pode ser proposital). */
    val avisos: List<ProblemaDeConfig> = emptyList(),
) {
    val ok: Boolean get() = problemas.isEmpty()
}

/**
 * Valida a configuração ANTES da subida (e no CI): o mesmo caminho de leitura de `AppContext.load`, sem tomar a trava,
 * abrir o banco, fazer requisição ao Jira nem subir o servidor, mais os cruzamentos entre arquivos que só o conjunto revela.
 * Cada problema sai com código, arquivo e linha, sem o valor. Para em um erro de carga por vez (o primeiro que a subida
 * acharia); os cruzamentos só rodam com a carga limpa.
 */
object ValidadorDeConfig {
    fun validar(env: Map<String, String>, dir: Path, hoje: LocalDate): ResultadoDaValidacao {
        val settings = try {
            Settings.fromEnv(env).copy(configDir = dir)
        } catch (e: IllegalArgumentException) {
            // A mensagem cita a variável e às vezes o valor: só o NOME da variável sai.
            return ResultadoDaValidacao(listOf(ProblemaDeConfig("SETTINGS_INVALIDO", nomeDaVariavel(e.message))), emptyList())
        }
        val problemas = mutableListOf<ProblemaDeConfig>()
        val dev = settings.dashboardDevMode

        // Conferências textuais que dão um código mais preciso do que o erro genérico do parser.
        problemas += conferirCustomIds(efetivo(dir, "jira_campos", dev))
        problemas += conferirIdsReservados(efetivo(dir, "papeis", dev))

        val contexto = try {
            AppContext.carregarSemBanco(settings, Clock.fixed(hoje.atStartOfDay(settings.zone).toInstant(), settings.zone))
        } catch (e: ErroDeConfiguracao) {
            // O mesmo arquivo já tem um código mais preciso (ex.: customId): o genérico seria ruído.
            if (problemas.none { mesmoArquivo(it.arquivo, e.arquivo) }) problemas += ProblemaDeConfig(e.codigo, e.arquivo, e.linha)
            null
        } catch (e: Exception) {
            // Falha de carga sem arquivo identificado (ex.: ficha repetida): só o tipo, nunca a mensagem.
            if (problemas.isEmpty()) problemas += ProblemaDeConfig("CONFIG_INVALIDA", "config")
            null
        }

        contexto?.use { ctx ->
            problemas += conferirCalendario(ctx, dir, hoje, dev)
            problemas += conferirFichas(ctx, dir)
            problemas += conferirPessoas(ctx, dir, hoje, dev)
        }
        if (!dev) problemas += conferirPastaDoBanco(settings.databasePath)
        problemas += conferirBackup(settings, dev)

        val comProblema = problemas.map { it.arquivo }.toSet()
        val avisos = buildList {
            if (settings.operacao.metricasPorta != 0 && settings.operacao.metricasHost !in LOOPBACK) {
                add(ProblemaDeConfig("METRICAS_HOST_NAO_LOOPBACK", "PAINEL_METRICAS_HOST"))
            }
            // O painel exposto além da própria máquina é decisão de quem implanta; o firewall passa a ser a única barreira de rede.
            if (settings.host !in LOOPBACK) add(ProblemaDeConfig("ESCUTA_FORA_DO_LOOPBACK", "PAINEL_HOST"))
        }
        return ResultadoDaValidacao(problemas, arquivosDaPasta(dir).filter { it !in comProblema }, avisos)
    }

    // ---- arquivos ----

    /** O arquivo que a subida leria: o real; sem ele, em desenvolvimento, o modelo; senão nenhum. */
    private fun efetivo(dir: Path, nome: String, dev: Boolean): Path? {
        val real = dir.resolve("$nome.yaml")
        if (Files.isRegularFile(real)) return real
        val exemplo = dir.resolve("$nome.exemplo.yaml")
        return if (dev && Files.isRegularFile(exemplo)) exemplo else null
    }

    private fun nome(dir: Path, arquivo: Path): String = dir.relativize(arquivo).toString().replace('\\', '/')

    private fun mesmoArquivo(a: String, b: String): Boolean = a.substringAfterLast('/') == b.substringAfterLast('/')

    private fun arquivosDaPasta(dir: Path): List<String> {
        if (!Files.isDirectory(dir)) return emptyList()
        val topo = Files.list(dir).use { it.filter { f -> Files.isRegularFile(f) && f.fileName.toString().endsWith(".yaml") }.toList() }
        val fichas = dir.resolve("cards").takeIf { Files.isDirectory(it) }?.let { pasta ->
            Files.list(pasta).use { it.filter { f -> f.fileName.toString().endsWith(".card.yaml") }.toList() }
        }.orEmpty()
        return (topo + fichas).map { nome(dir, it) }.sorted()
    }

    private fun nomeDaVariavel(mensagem: String?): String =
        mensagem?.let { Regex("\\b[A-Z][A-Z0-9_]{2,}\\b").find(it)?.value } ?: "ambiente"

    // ---- conferências textuais ----

    private val LINHA_CUSTOM_ID = Regex("^\\s*(?:-\\s*)?customId\\s*:\\s*(.*?)\\s*(?:#.*)?$")
    /** Mesmo formato que o dicionário de campos aceita (`FieldDef`): `customfield_NNNNN` ou `cf[NNNNN]`. */
    private val FORMATO_CUSTOM_ID = Regex("""customfield_\d+|cf\[\d+]""")
    private val LINHA_ID =Regex("^\\s*(?:-\\s*)?id\\s*:\\s*(.*?)\\s*(?:#.*)?$")

    private fun conferirCustomIds(arquivo: Path?): List<ProblemaDeConfig> {
        arquivo ?: return emptyList()
        val nome = arquivo.fileName.toString()
        return Files.readAllLines(arquivo).mapIndexedNotNull { i, linha ->
            val valor = LINHA_CUSTOM_ID.matchEntire(linha)?.groupValues?.get(1)?.trim('"', '\'')?.takeIf { it.isNotEmpty() && it != "~" && it != "null" }
            if (valor != null && !FORMATO_CUSTOM_ID.matches(valor)) ProblemaDeConfig("CUSTOM_ID_INVALIDO", nome, i + 1) else null
        }
    }

    private fun conferirIdsReservados(arquivo: Path?): List<ProblemaDeConfig> {
        arquivo ?: return emptyList()
        val nome = arquivo.fileName.toString()
        return Files.readAllLines(arquivo).mapIndexedNotNull { i, linha ->
            val valor = LINHA_ID.matchEntire(linha)?.groupValues?.get(1)?.trim('"', '\'')?.lowercase()
            if (valor != null && valor in RoleDirectory.IDS_RESERVADOS) ProblemaDeConfig("ID_RESERVADO", nome, i + 1) else null
        }
    }

    // ---- cruzamentos ----

    /** Feriados móveis do ano atual e do seguinte; o aviso operacional só começa em 1º/11, mas a validação não espera. */
    private fun conferirCalendario(ctx: AppContext, dir: Path, hoje: LocalDate, dev: Boolean): List<ProblemaDeConfig> {
        val anos = ctx.calendar.movableHolidays.map { it.year }.toSet()
        if (hoje.year in anos && hoje.year + 1 in anos) return emptyList()
        val arquivo = efetivo(dir, "calendario", dev)
        val linha = arquivo?.let { Files.readAllLines(it).indexOfFirst { l -> l.startsWith("feriados_moveis") } }?.takeIf { it >= 0 }?.plus(1)
        return listOf(ProblemaDeConfig("CALENDARIO_SEM_ANO", arquivo?.fileName?.toString() ?: "calendario.yaml", linha))
    }

    /** Todo status citado como filtro fixo de uma ficha precisa constar no `status_map.yaml`. */
    private fun conferirFichas(ctx: AppContext, dir: Path): List<ProblemaDeConfig> {
        val pasta = dir.resolve("cards")
        if (!Files.isDirectory(pasta)) return emptyList()
        val fichas = Files.list(pasta).use { it.filter { f -> f.fileName.toString().endsWith(".card.yaml") }.sorted().toList() }
        return fichas.mapNotNull { arquivo ->
            val spec = CardSpecs.fromYaml(arquivo, ctx.campos)
            val status = spec.filtrosFixos.entries.filter { it.key.equals("status", ignoreCase = true) }.flatMap { it.value }
            val semMapa = status.any {
                try {
                    ctx.statusMap.category(it)
                    false
                } catch (e: UnmappedStatusException) {
                    true
                }
            }
            if (semMapa) ProblemaDeConfig("STATUS_DA_FICHA_SEM_MAPA", nome(dir, arquivo)) else null
        }
    }

    /**
     * Squad sem líder entre os membros de `equipes.yaml`. A leitura (formato, pessoa desconhecida, período invertido,
     * motivo, sobreposição) é a ESTRITA da subida, feita em `AppContext.carregarSemBanco`; aqui só o cruzamento com os papéis.
     */
    private fun conferirPessoas(ctx: AppContext, dir: Path, hoje: LocalDate, dev: Boolean): List<ProblemaDeConfig> {
        val arquivo = efetivo(dir, "equipes", dev) ?: return emptyList()
        val nome = arquivo.fileName.toString()
        val problemas = mutableListOf<ProblemaDeConfig>()
        val squadsJaAvisados = mutableSetOf<String>()
        ctx.equipes.todos.forEachIndexed { i, membro ->
            val squad = ctx.roles.squadOf(membro.id, hoje) ?: return@forEachIndexed
            val temLider = ctx.roles.membersOf(squad, hoje).any { squad in ctx.roles.squadsLedBy(it, hoje) }
            if (!temLider && squadsJaAvisados.add(squad)) problemas += ProblemaDeConfig("SQUAD_SEM_LIDER", nome, linhaDoItem(arquivo, "membros", i + 1))
        }
        return problemas
    }

    /** Em produção a pasta do banco precisa existir (ou poder ser criada) e aceitar escrita. */
    private fun conferirPastaDoBanco(banco: Path): List<ProblemaDeConfig> {
        var pasta: Path? = banco.toAbsolutePath().parent
        while (pasta != null && !Files.exists(pasta)) pasta = pasta.parent
        val gravavel = pasta != null && Files.isDirectory(pasta) && Files.isWritable(pasta)
        return if (gravavel) emptyList() else listOf(ProblemaDeConfig("BANCO_PASTA_SEM_ESCRITA", "DATABASE_PATH"))
    }

    /**
     * `BACKUP_DIR`: obrigatório em produção; fora do repositório (nenhuma pasta acima com `.git`), fora da pasta do banco
     * (perder o disco perderia banco e cópias juntos) e com escrita. `BACKUP_HORA` e `BACKUP_RETENCAO_COPIAS` inválidos já
     * saem como `SETTINGS_INVALIDO`. O "arquivo" citado é o NOME da variável, nunca o caminho.
     */
    private fun conferirBackup(settings: Settings, dev: Boolean): List<ProblemaDeConfig> {
        val dir = settings.operacao.backup.dir
        if (dir == null) {
            return if (dev) emptyList() else listOf(ProblemaDeConfig("BACKUP_DIR_AUSENTE", "BACKUP_DIR"))
        }
        val problemas = mutableListOf<ProblemaDeConfig>()
        // Symlinks resolvidos: um atalho não esconde que o destino cai dentro do banco ou do repositório.
        val absoluto = caminhoReal(dir)
        if (generateSequence(absoluto) { it.parent }.any { Files.exists(it.resolve(".git")) }) {
            problemas += ProblemaDeConfig("BACKUP_DIR_DENTRO_DO_REPOSITORIO", "BACKUP_DIR")
        }
        val pastaDoBanco = settings.databasePath.toAbsolutePath().normalize().parent?.let { caminhoReal(it) }
        if (pastaDoBanco != null && absoluto.startsWith(pastaDoBanco)) {
            problemas += ProblemaDeConfig("BACKUP_DIR_NA_PASTA_DO_BANCO", "BACKUP_DIR")
        }
        val existente = generateSequence(absoluto) { it.parent }.firstOrNull { Files.exists(it) }
        if (existente == null || !Files.isDirectory(existente) || !Files.isWritable(existente)) {
            problemas += ProblemaDeConfig("BACKUP_DIR_SEM_ESCRITA", "BACKUP_DIR")
        }
        return problemas
    }

    private val LOOPBACK = setOf("127.0.0.1", "localhost", "::1", "[::1]")

    /** Caminho absoluto com os symlinks do trecho que já existe resolvidos; o resto (a criar) segue como está. */
    private fun caminhoReal(caminho: Path): Path {
        val absoluto = caminho.toAbsolutePath().normalize()
        val existente = generateSequence(absoluto) { it.parent }.firstOrNull { Files.exists(it) } ?: return absoluto
        val real = try {
            existente.toRealPath()
        } catch (e: java.io.IOException) {
            existente
        }
        return real.resolve(existente.relativize(absoluto)).normalize()
    }
}
