package br.sidia.painel.operacao

import br.sidia.painel.AppContext
import br.sidia.painel.ErroDeConfiguracao
import br.sidia.painel.config.Settings
import br.sidia.painel.domain.UnmappedStatusException
import br.sidia.painel.jql.CardSpecs
import br.sidia.painel.security.RoleDirectory
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar
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
 * abrir o banco, criar cliente do Jira nem subir o servidor, mais os cruzamentos entre arquivos que só o conjunto revela.
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
        val avisos = if (settings.operacao.metricasPorta != 0 && settings.operacao.metricasHost !in LOOPBACK) {
            listOf(ProblemaDeConfig("METRICAS_HOST_NAO_LOOPBACK", "PAINEL_METRICAS_HOST"))
        } else {
            emptyList()
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

    /** Pessoa de `equipes.yaml`/`ausencias.yaml` que não consta em `papeis.yaml`, squad sem líder e período invertido. */
    private fun conferirPessoas(ctx: AppContext, dir: Path, hoje: LocalDate, dev: Boolean): List<ProblemaDeConfig> {
        val problemas = mutableListOf<ProblemaDeConfig>()
        efetivo(dir, "equipes", dev)?.let { arquivo ->
            val nome = arquivo.fileName.toString()
            val squadsJaAvisados = mutableSetOf<String>()
            for (entrada in entradasCom(arquivo, "id")) {
                val id = entrada.valor.trim().lowercase()
                if (!ctx.roles.conhece(id)) {
                    problemas += ProblemaDeConfig("PESSOA_DESCONHECIDA", nome, entrada.linha)
                    continue
                }
                val squad = ctx.roles.squadOf(id, hoje) ?: continue
                val temLider = ctx.roles.membersOf(squad, hoje).any { squad in ctx.roles.squadsLedBy(it, hoje) }
                if (!temLider && squadsJaAvisados.add(squad)) problemas += ProblemaDeConfig("SQUAD_SEM_LIDER", nome, entrada.linha)
            }
        }
        efetivo(dir, "ausencias", dev)?.let { arquivo ->
            val nome = arquivo.fileName.toString()
            for (entrada in entradasCom(arquivo, "pessoa")) {
                if (!ctx.roles.conhece(entrada.valor.trim().lowercase())) {
                    problemas += ProblemaDeConfig("PESSOA_DESCONHECIDA", nome, entrada.linha)
                }
                val de = data(entrada.mapa, "de")
                val ate = data(entrada.mapa, "ate")
                if (de != null && ate != null && ate < de) problemas += ProblemaDeConfig("PERIODO_INVERTIDO", nome, entrada.linha)
            }
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

    // ---- leitura tolerante de equipes/ausências ----
    // Os formatos finais são do Plano 6; aqui basta achar, em qualquer nível, mapas com a chave que identifica a pessoa
    // (`id` em equipes.yaml, `pessoa` em ausencias.yaml). Arquivo sem essas chaves não gera problema.

    private class Entrada(val valor: String, val linha: Int, val mapa: YamlMap)

    private fun entradasCom(arquivo: Path, chave: String): List<Entrada> {
        val raiz = try {
            Yaml.default.parseToYamlNode(Files.readString(arquivo))
        } catch (e: Exception) {
            return emptyList()
        }
        val achadas = mutableListOf<Entrada>()
        fun visitar(no: YamlNode) {
            when (no) {
                is YamlMap -> {
                    val alvo = no.entries.entries.firstOrNull { it.key.content == chave }?.value as? YamlScalar
                    if (alvo != null) achadas += Entrada(alvo.content, alvo.location.line, no)
                    no.entries.values.forEach(::visitar)
                }
                is YamlList -> no.items.forEach(::visitar)
                else -> Unit
            }
        }
        visitar(raiz)
        return achadas
    }

    private fun data(mapa: YamlMap, chave: String): LocalDate? =
        (mapa.entries.entries.firstOrNull { it.key.content == chave }?.value as? YamlScalar)?.content?.let {
            try {
                LocalDate.parse(it)
            } catch (e: Exception) {
                null
            }
        }
}
