package br.sidia.painel.operacao

import br.sidia.painel.TestPaths
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlin.io.path.ExperimentalPathApi::class)
class ValidadorDeConfigTest {
    private val temporarias = mutableListOf<Path>()
    private val hoje = LocalDate.of(2026, 10, 30)

    @AfterEach
    fun limpar() {
        temporarias.forEach { runCatching { it.deleteRecursively() } }
    }

    private fun nova(prefixo: String = "painel-config-"): Path = Files.createTempDirectory(prefixo).also { temporarias.add(it) }

    /** Cópia da pasta `app/config` de exemplo (só os modelos); em produção, os modelos viram arquivos reais. */
    private fun pastaDeConfig(comoReal: Boolean = false): Path {
        val destino = nova()
        Files.walk(TestPaths.configDir).use { arvore ->
            arvore.filter { Files.isRegularFile(it) }.forEach { origem ->
                val relativo = TestPaths.configDir.relativize(origem).toString()
                val nome = if (comoReal) relativo.replace(".exemplo.yaml", ".yaml") else relativo
                val alvo = destino.resolve(nome)
                Files.createDirectories(alvo.parent)
                Files.copy(origem, alvo)
            }
        }
        return destino
    }

    private val dev = mapOf("DASHBOARD_DEV_MODE" to "true")

    private fun producao(vararg extras: Pair<String, String>): Map<String, String> =
        mapOf(
            "DASHBOARD_DEV_MODE" to "false",
            "DATABASE_PATH" to nova("painel-dados-").resolve("painel.db").toString(),
            "BACKUP_DIR" to nova("painel-copias-").toString(),
        ) + extras

    private fun codigos(r: ResultadoDaValidacao) = r.problemas.map { it.codigo }

    private fun escrever(pasta: Path, nome: String, texto: String) = Files.writeString(pasta.resolve(nome), texto)

    @Test
    fun exemplosPassamEmDev() {
        val r = ValidadorDeConfig.validar(dev, TestPaths.configDir, hoje)
        assertEquals(emptyList(), r.problemas)
        assertTrue(r.ok)
        assertTrue(r.arquivosOk.any { it.startsWith("papeis") })
    }

    @Test
    fun exemplosComoArquivosReaisPassamEmProducao() {
        val r = ValidadorDeConfig.validar(producao(), pastaDeConfig(comoReal = true), hoje)
        assertEquals(emptyList(), r.problemas)
    }

    @Test
    fun exemploEmProducaoFalha() {
        val r = ValidadorDeConfig.validar(producao(), TestPaths.configDir, hoje)
        assertFalse(r.ok)
        assertTrue("EXEMPLO_EM_PRODUCAO" in codigos(r), r.problemas.toString())
    }

    @Test
    fun arquivoAusenteEmProducao() {
        val pasta = pastaDeConfig(comoReal = true)
        Files.delete(pasta.resolve("calendario.yaml"))
        val r = ValidadorDeConfig.validar(producao(), pasta, hoje)
        assertEquals(listOf(ProblemaDeConfig("ARQUIVO_AUSENTE", "calendario.yaml")), r.problemas)
    }

    @Test
    fun yamlInvalidoCitaArquivoSemOValor() {
        val pasta = pastaDeConfig()
        escrever(pasta, "metas.yaml", "isto: [não fecha\n  - x: y: z\n")
        val r = ValidadorDeConfig.validar(dev, pasta, hoje)
        assertEquals(listOf("YAML_INVALIDO"), codigos(r))
        assertEquals("metas.yaml", r.problemas.single().arquivo)
    }

    @Test
    fun chaveDesconhecidaTemCodigoPropio() {
        val pasta = pastaDeConfig()
        val original = Files.readString(TestPaths.configDir.resolve("status_map.exemplo.yaml"))
        escrever(pasta, "status_map.yaml", original + "\nchave_que_nao_existe: 1\n")
        val r = ValidadorDeConfig.validar(dev, pasta, hoje)
        assertEquals(listOf("CHAVE_DESCONHECIDA"), codigos(r), r.problemas.toString())
        assertEquals("status_map.yaml", r.problemas.single().arquivo)
    }

    @Test
    fun pessoaDesconhecidaNaAusencia() {
        val pasta = pastaDeConfig()
        escrever(
            pasta, "ausencias.yaml",
            "ausencias:\n  - pessoa: joao.ficticio.x\n    de: 2026-11-02\n    ate: 2026-11-06\n    fracao: 1.0\n",
        )
        val r = ValidadorDeConfig.validar(dev, pasta, hoje)
        assertEquals(listOf(ProblemaDeConfig("PESSOA_DESCONHECIDA", "ausencias.yaml", 2)), r.problemas)
    }

    @Test
    fun pessoaDesconhecidaNaEquipe() {
        val pasta = pastaDeConfig()
        escrever(pasta, "equipes.yaml", "equipes:\n  - id: alguem.que.nao.existe\n    horasSemanais: 40\n")
        val r = ValidadorDeConfig.validar(dev, pasta, hoje)
        assertEquals(listOf(ProblemaDeConfig("PESSOA_DESCONHECIDA", "equipes.yaml", 2)), r.problemas)
    }

    @Test
    fun squadSemLider() {
        val pasta = pastaDeConfig()
        escrever(
            pasta, "papeis.yaml",
            "pessoas:\n  - id: gerente.exemplo\n    nome: G\n    papeis:\n      - papel: GERENTE\n" +
                "  - id: dev.gama1\n    nome: D\n    papeis:\n      - papel: USUARIO\n        squad: Gama\n",
        )
        escrever(pasta, "equipes.yaml", "equipes:\n  - id: dev.gama1\n    horasSemanais: 40\n")
        val r = ValidadorDeConfig.validar(dev, pasta, hoje)
        assertEquals(listOf(ProblemaDeConfig("SQUAD_SEM_LIDER", "equipes.yaml", 2)), r.problemas)
    }

    @Test
    fun periodoInvertido() {
        val pasta = pastaDeConfig()
        escrever(
            pasta, "ausencias.yaml",
            "ausencias:\n  - pessoa: dev.alfa1\n    de: 2026-11-06\n    ate: 2026-11-02\n    fracao: 1.0\n",
        )
        val r = ValidadorDeConfig.validar(dev, pasta, hoje)
        assertEquals(listOf(ProblemaDeConfig("PERIODO_INVERTIDO", "ausencias.yaml", 2)), r.problemas)
    }

    @Test
    fun calendarioSemAnoSeguinte() {
        val r = ValidadorDeConfig.validar(dev, TestPaths.configDir, LocalDate.of(2030, 11, 2))
        assertEquals(1, r.problemas.size, r.problemas.toString())
        assertEquals("CALENDARIO_SEM_ANO", r.problemas.single().codigo)
        assertTrue(r.problemas.single().arquivo.startsWith("calendario"))
    }

    @Test
    fun statusDaFichaSemMapa() {
        val pasta = pastaDeConfig()
        // "Open" consta no vocabulário de status do dicionário (a ficha carrega), mas não no status_map de exemplo.
        val ficha = Files.readString(TestPaths.configDir.resolve("cards/abertas.card.yaml"))
            .replace("project: [\"TG-Basic\"]", "project: [\"TG-Basic\"]\n  status: [\"Open\"]")
        escrever(pasta, "cards/abertas.card.yaml", ficha)
        val r = ValidadorDeConfig.validar(dev, pasta, hoje)
        assertEquals(listOf("STATUS_DA_FICHA_SEM_MAPA"), codigos(r), r.problemas.toString())
        assertEquals("cards/abertas.card.yaml", r.problemas.single().arquivo)
        assertFalse(r.problemas.toString().contains("Open"))
    }

    @Test
    fun customIdInvalido() {
        val pasta = pastaDeConfig()
        val campos = Files.readString(TestPaths.configDir.resolve("jira_campos.exemplo.yaml"))
        // acrescenta ao primeiro campo um customId fora do formato customfield_NNNNN
        val linhas = campos.lines().toMutableList()
        val i = linhas.indexOfFirst { it.trimStart().startsWith("- nome:") }
        linhas.add(i + 1, "    customId: abc123")
        escrever(pasta, "jira_campos.yaml", linhas.joinToString("\n"))
        val r = ValidadorDeConfig.validar(dev, pasta, hoje)
        assertEquals(listOf("CUSTOM_ID_INVALIDO"), codigos(r), r.problemas.toString())
        assertEquals("jira_campos.yaml", r.problemas.single().arquivo)
        assertEquals(i + 2, r.problemas.single().linha)
    }

    @Test
    fun papeisSemGerente() {
        val pasta = pastaDeConfig(comoReal = true)
        escrever(pasta, "papeis.yaml", "pessoas:\n  - id: coord.um\n    nome: C\n    papeis:\n      - papel: COORDENADOR\n")
        val r = ValidadorDeConfig.validar(producao(), pasta, hoje)
        assertEquals(listOf(ProblemaDeConfig("PAPEIS_SEM_GERENTE_VIGENTE", "papeis.yaml")), r.problemas)
    }

    @Test
    fun idReservado() {
        val pasta = pastaDeConfig()
        escrever(
            pasta, "papeis.yaml",
            "pessoas:\n  - id: gerente.exemplo\n    nome: G\n    papeis:\n      - papel: GERENTE\n" +
                "  - id: admin\n    nome: A\n    papeis:\n      - papel: COORDENADOR\n",
        )
        val r = ValidadorDeConfig.validar(dev, pasta, hoje)
        assertEquals(listOf(ProblemaDeConfig("ID_RESERVADO", "papeis.yaml", 6)), r.problemas)
    }

    @Test
    fun settingsInvalidoCitaSoONomeDaVariavel() {
        val r = ValidadorDeConfig.validar(dev + ("DATABASE_POOL_SIZE" to "9999"), TestPaths.configDir, hoje)
        assertEquals(listOf(ProblemaDeConfig("SETTINGS_INVALIDO", "DATABASE_POOL_SIZE")), r.problemas)
    }

    @Test
    fun bancoEmPastaSemEscrita() {
        val somenteLeitura = nova("painel-ro-")
        somenteLeitura.toFile().setWritable(false, false)
        assumeTrue(!Files.isWritable(somenteLeitura), "este sistema deixa escrever em pasta somente leitura (ex.: executando como root)")
        val env = producao("DATABASE_PATH" to somenteLeitura.resolve("painel.db").toString())
        val r = ValidadorDeConfig.validar(env, pastaDeConfig(comoReal = true), hoje)
        assertEquals(listOf("BANCO_PASTA_SEM_ESCRITA"), codigos(r))
        somenteLeitura.toFile().setWritable(true, false)
    }

    @Test
    fun backupDirAusenteEmProducao() {
        val env = producao().filterKeys { it != "BACKUP_DIR" }
        val r = ValidadorDeConfig.validar(env, pastaDeConfig(comoReal = true), hoje)
        assertEquals(listOf(ProblemaDeConfig("BACKUP_DIR_AUSENTE", "BACKUP_DIR")), r.problemas)
        // em desenvolvimento o backup é opcional
        assertEquals(emptyList(), ValidadorDeConfig.validar(dev, TestPaths.configDir, hoje).problemas)
    }

    @Test
    fun backupDirDentroDoRepositorio() {
        val repo = nova("painel-repo-")
        Files.createDirectory(repo.resolve(".git"))
        val dentro = repo.resolve("copias")
        val r = ValidadorDeConfig.validar(producao("BACKUP_DIR" to dentro.toString()), pastaDeConfig(comoReal = true), hoje)
        assertEquals(listOf(ProblemaDeConfig("BACKUP_DIR_DENTRO_DO_REPOSITORIO", "BACKUP_DIR")), r.problemas)
    }

    @Test
    fun backupDirNaPastaDoBanco() {
        val dados = nova("painel-dados-")
        val iguais = ValidadorDeConfig.validar(
            producao("DATABASE_PATH" to dados.resolve("painel.db").toString(), "BACKUP_DIR" to dados.toString()),
            pastaDeConfig(comoReal = true), hoje,
        )
        assertEquals(listOf(ProblemaDeConfig("BACKUP_DIR_NA_PASTA_DO_BANCO", "BACKUP_DIR")), iguais.problemas)
        val dentro = ValidadorDeConfig.validar(
            producao("DATABASE_PATH" to dados.resolve("painel.db").toString(), "BACKUP_DIR" to dados.resolve("copias").toString()),
            pastaDeConfig(comoReal = true), hoje,
        )
        assertEquals(listOf("BACKUP_DIR_NA_PASTA_DO_BANCO"), codigos(dentro))
    }

    @Test
    fun backupDirSemEscrita() {
        val arquivoComum = Files.createTempFile("painel-copias-arquivo-", ".txt").also { temporarias.add(it) }
        val r = ValidadorDeConfig.validar(producao("BACKUP_DIR" to arquivoComum.resolve("dentro").toString()), pastaDeConfig(comoReal = true), hoje)
        assertEquals(listOf(ProblemaDeConfig("BACKUP_DIR_SEM_ESCRITA", "BACKUP_DIR")), r.problemas)
    }

    @Test
    fun backupHoraERetencaoInvalidosViramSettingsInvalido() {
        for ((nome, valor) in listOf("BACKUP_HORA" to "25:99", "BACKUP_RETENCAO_COPIAS" to "0")) {
            val r = ValidadorDeConfig.validar(producao(nome to valor), pastaDeConfig(comoReal = true), hoje)
            assertEquals(listOf(ProblemaDeConfig("SETTINGS_INVALIDO", nome)), r.problemas, nome)
        }
    }

    @Test
    fun problemaNuncaGuardaValor() {
        val pasta = pastaDeConfig()
        escrever(
            pasta, "ausencias.yaml",
            "ausencias:\n  - pessoa: joao.ficticio.x\n    de: 2026-11-02\n    ate: 2026-11-06\n    fracao: 1.0\n",
        )
        val r = ValidadorDeConfig.validar(dev, pasta, hoje)
        assertFalse(r.problemas.toString().contains("joao.ficticio.x"))
        assertNull(r.problemas.single().linha?.takeIf { it < 1 })
    }
}
