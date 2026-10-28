package br.sidia.painel.implantacao

import br.sidia.painel.TestPaths
import br.sidia.painel.config.Settings
import br.sidia.painel.operacao.ValidadorDeConfig
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pacote de produção (Plano 8, Tarefa 7): conferências ESTÁTICAS dos scripts, do modelo de variáveis e do guia de
 * implantação. Não monta o pacote (isso é `montar-pacote` + `verificar-pacote`, que precisam de Node, Gradle e rede para os
 * registros de pacotes) e não sobe nada.
 */
class PacoteDeProducaoTest {
    private val repo: Path = TestPaths.repoDir
    private val scripts: Path = repo.resolve("app/scripts")

    private fun ler(relativo: String): String = Files.readString(repo.resolve(relativo))

    private val nomesDosScripts = listOf("montar-pacote.ps1", "montar-pacote.sh", "verificar-pacote.ps1", "verificar-pacote.sh")

    @Test
    fun scriptsDeMontagemEVerificacaoNosDoisSistemas() {
        for (nome in nomesDosScripts) assertTrue(Files.isRegularFile(scripts.resolve(nome)), "falta app/scripts/$nome")
        assertTrue(Files.isRegularFile(scripts.resolve("LEIA-ME-pacote.txt")), "falta o LEIA-ME copiado para o pacote")
    }

    @Test
    fun scriptsShTemShebangEFalhaNoPrimeiroErro() {
        for (nome in nomesDosScripts.filter { it.endsWith(".sh") }) {
            val texto = Files.readString(scripts.resolve(nome))
            assertTrue(texto.startsWith("#!/bin/sh\n"), "$nome sem #!/bin/sh")
            assertTrue("\nset -eu\n" in texto, "$nome sem set -eu")
        }
    }

    @Test
    fun scriptsShTemSintaxeValida() {
        val sh = listOf("/bin/sh", "/usr/bin/sh").firstOrNull { Files.isExecutable(Path.of(it)) }
        assumeTrue(sh != null, "sem sh neste ambiente")
        for (nome in nomesDosScripts.filter { it.endsWith(".sh") }) {
            val p = ProcessBuilder(sh, "-n", scripts.resolve(nome).toString()).redirectErrorStream(true).start()
            val saida = p.inputStream.bufferedReader().readText()
            assertTrue(p.waitFor(30, TimeUnit.SECONDS), "sh -n travou")
            assertEquals(0, p.exitValue(), "$nome: $saida")
        }
    }

    @Test
    fun scriptsPowerShellSaoSoAscii() {
        // O Windows PowerShell 5.1 lê arquivo sem BOM como ANSI: acento num .ps1 estraga o script.
        for (nome in nomesDosScripts.filter { it.endsWith(".ps1") }) {
            val bytes = Files.readAllBytes(scripts.resolve(nome))
            val ruim = bytes.indexOfFirst { it < 0 }
            assertTrue(ruim < 0, "$nome tem caractere não ASCII no byte $ruim")
        }
    }

    @Test
    fun montagemCopiaSoModelos() {
        for (nome in listOf("montar-pacote.ps1", "montar-pacote.sh")) {
            val texto = Files.readString(scripts.resolve(nome))
            for (esperado in listOf("*.exemplo.yaml", "*.demo.yaml", "cards", ".env.example", "installDist", "--frozen-lockfile")) {
                assertTrue(esperado in texto, "$nome não cita $esperado")
            }
            // nada de arquivo de variáveis real, de banco, de configuração real nem do legacy
            assertFalse(Regex("\\.env(?!\\.example)").containsMatchIn(texto), "$nome cita um arquivo de variáveis real")
            assertFalse(Regex("\\.db\\b").containsMatchIn(texto), "$nome cita arquivo de banco")
            assertFalse(Regex("legacy", RegexOption.IGNORE_CASE).containsMatchIn(texto), "$nome cita o legacy")
            // as cópias da configuração usam sempre um sufixo de modelo (nunca config/* solto)
            assertFalse(Regex("config[/\\\\]\\*(?!\\.)").containsMatchIn(texto), "$nome copia config sem filtro de modelo")
            assertFalse(Regex("(?i)\\b(papeis|equipes|ausencias|squads|calendario|status_map|jira_campos|vinculos)\\.yaml").containsMatchIn(texto))
            // só se apaga dentro de app/pacote
            assertTrue("pacote" in texto && ("rm -rf" in texto || "Remove-Item" in texto))
        }
    }

    @Test
    fun verificacaoCobreOsRiscosDoPacote() {
        for (nome in listOf("verificar-pacote.ps1", "verificar-pacote.sh")) {
            val texto = Files.readString(scripts.resolve(nome))
            for (esperado in listOf("OIDC_ISSUER", "/api/health", "8080", "legacy", ".db", "exemplo.yaml", "demo.yaml", "DASHBOARD_DEV_MODE=false", "DASHBOARD_DEV_MODE=true")) {
                assertTrue(esperado in texto, "$nome não cita $esperado")
            }
            // a flag do acesso ao Jira nunca é escrita nem ligada pela verificação
            assertFalse("JIRA_LIVE_ENABLED" in texto, "$nome cita a chave do acesso ao Jira")
        }
        assertTrue("taskkill" in Files.readString(scripts.resolve("verificar-pacote.ps1")), "no Windows a árvore do processo é encerrada")
    }

    @Test
    fun pastaDoPacoteIgnoradaPeloGit() {
        val linhas = ler(".gitignore").lines().map { it.trim() }
        assertTrue("app/pacote/" in linhas, ".gitignore sem app/pacote/")
    }

    @Test
    fun envExemploSemValoresDeSegredo() {
        val variaveis = ler("app/.env.example").lines().mapNotNull {
            Regex("^(#?)\\s?([A-Z][A-Z0-9_]{2,})=(.*)$").find(it)?.destructured
        }
        assertTrue(variaveis.size >= 30, "o modelo deveria listar as variáveis do painel")
        for ((comentario, nome, valor) in variaveis) {
            val ehSegredo = nome.endsWith("_SECRET") || nome.endsWith("_TOKEN") || nome.endsWith("_PASSWORD") ||
                nome.startsWith("OIDC_") || nome.startsWith("SESSION_SECRET")
            if (nome.startsWith("OIDC_") || nome.startsWith("SESSION_SECRET")) {
                assertEquals("#", comentario, "$nome deve estar comentada no modelo")
            }
            if (ehSegredo) assertEquals("", valor.trim(), "$nome não pode ter valor no modelo")
        }
        val nomes = variaveis.map { it.component2() }.toSet()
        for (esperado in listOf("OIDC_ISSUER", "OIDC_CLIENT_ID", "OIDC_CLIENT_SECRET", "OIDC_REDIRECT_URI", "OIDC_CLAIM_USERNAME", "SESSION_SECRET", "PAINEL_HOST")) {
            assertTrue(esperado in nomes, "o modelo não lista $esperado")
        }
    }

    @Test
    fun semArtefatosDeContainer() {
        val proibido = Regex("(?i)^(dockerfile.*|docker-compose.*|compose.*\\.ya?ml|\\.dockerignore)$")
        val ignorar = setOf("legacy", "node_modules", "build", "dist", ".git", ".claude", ".gradle", ".kotlin", "pacote", "data", "superpowers")
        val achados = mutableListOf<String>()
        // raiz do repositório: só os arquivos da própria raiz; app/: tudo, menos as pastas de build e de dados
        Files.list(repo).use { s -> s.filter { Files.isRegularFile(it) && proibido.matches(it.fileName.toString()) }.forEach { achados += repo.relativize(it).toString() } }
        Files.walk(repo.resolve("app")).use { s ->
            s.filter { p -> p.fileName.toString() !in ignorar && repo.relativize(p).none { it.toString() in ignorar } }
                .filter { Files.isRegularFile(it) && proibido.matches(it.fileName.toString()) }
                .forEach { achados += repo.relativize(it).toString() }
        }
        assertEquals(emptyList(), achados, "o painel não usa contêiner")
    }

    private val documento get() = ler("docs/implantacao.md")

    @Test
    fun documentoDeImplantacaoCobreOsTopicos() {
        for (
            esperado in listOf(
                "installDist", "montar-pacote", "verificar-pacote", "PAINEL_CONFIG_DIR", "DATABASE_PATH", "PAINEL_ENV_FILE",
                "PAINEL_STATIC_DIR", "DATABASE_POOL_SIZE", "RETENTION_MONTHS", "ACCESS_LOG_RETENTION_MONTHS", "OIDC_ISSUER",
                "OIDC_CLIENT_ID", "OIDC_REDIRECT_URI", "SESSION_SECRET", "painel.db.lock", "PAINEL_HOST",
                "docs/jira/checklist-primeira-conexao.md", "docs/jira/runbook-sincronizacao.md", "Plano 12", "Plano 13", "DPO",
                "montar-pacote.ps1", "montar-pacote.sh", "verificar-pacote.ps1", "verificar-pacote.sh", "falha fechado",
            )
        ) {
            assertTrue(esperado in documento, "docs/implantacao.md não cita $esperado")
        }
        // os documentos citados existem
        for (alvo in listOf("docs/jira/checklist-primeira-conexao.md", "docs/jira/runbook-sincronizacao.md", "docs/operacao/configuracao.md", "docs/operacao/rotacao-de-segredos.md", "docs/operacao/runbook-diario.md")) {
            assertTrue(Files.isRegularFile(repo.resolve(alvo)), "o guia cita $alvo, que não existe")
        }
        assertTrue("só o usuário" in documento || "é só o usuário" in documento || "quem liga é só o usuário" in documento)
    }

    @Test
    fun documentoSemComandosDeContainerNemPostgres() {
        for (proibido in listOf("docker compose", "docker-compose", "docker run", "Dockerfile", "pg_dump", "pg_restore", "Testcontainers", "psql ", "kubectl")) {
            assertFalse(proibido.lowercase() in documento.lowercase(), "docs/implantacao.md não pode citar '$proibido'")
        }
    }

    @Test
    fun documentoSemHostRealNemSegredoNemFlagLigada() {
        for (m in Regex("https?://([^/\\s:?#)`>\\]]+)", RegexOption.IGNORE_CASE).findAll(documento)) {
            val host = m.groupValues[1].lowercase()
            assertTrue(host.endsWith(".invalid") || host in setOf("localhost", "127.0.0.1", "example.com"), "host real no guia: ${m.value}")
        }
        assertFalse(Regex("Bearer\\s+[A-Za-z0-9._~+/=-]{8,}").containsMatchIn(documento))
        assertFalse(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+").containsMatchIn(documento), "e-mail no guia")
        assertFalse(Regex("JIRA_LIVE_ENABLED\\s*=").containsMatchIn(documento), "o guia nunca escreve a chave do Jira com valor")
    }

    // ---------------------------------------------------------------- endereço de escuta (opção recomendada)

    @Test
    fun escutaSoEmLocalhostPorPadrao() {
        assertEquals("127.0.0.1", Settings().host)
        assertEquals("127.0.0.1", Settings.fromEnv(emptyMap()).host)
        assertEquals("127.0.0.1", Settings.fromEnv(mapOf("PAINEL_HOST" to "  ")).host)
        // trocar é escolha explícita
        assertEquals("10.0.0.5", Settings.fromEnv(mapOf("PAINEL_HOST" to " 10.0.0.5 ")).host)
        assertEquals("0.0.0.0", Settings.fromEnv(mapOf("PAINEL_HOST" to "0.0.0.0")).host)
        for (ruim in listOf("a b", "10.0.0.5;rm", "http://x", "x/y", "é")) {
            val e = assertFailsWith<IllegalArgumentException>(ruim) { Settings.fromEnv(mapOf("PAINEL_HOST" to ruim)) }
            assertTrue(e.message!!.startsWith("PAINEL_HOST "), e.message)
            assertFalse(ruim in e.message!!)
        }
        // o servidor usa mesmo a interface configurada
        val aplicacao = Files.readString(TestPaths.mainSrcDir.resolve("kotlin/br/sidia/painel/Application.kt"))
        assertTrue("host = settings.host" in aplicacao, "Application.kt deve passar settings.host ao embeddedServer")
    }

    @Test
    fun validarConfigAvisaQuandoAEscutaSaiDoLoopback() {
        val hoje = LocalDate.of(2026, 10, 28)
        val dev = mapOf("DASHBOARD_DEV_MODE" to "true")
        val padrao = ValidadorDeConfig.validar(dev, TestPaths.configDir, hoje)
        assertTrue(padrao.avisos.none { it.codigo == "ESCUTA_FORA_DO_LOOPBACK" })
        for (host in listOf("127.0.0.1", "localhost", "::1")) {
            assertTrue(ValidadorDeConfig.validar(dev + ("PAINEL_HOST" to host), TestPaths.configDir, hoje).avisos.none { it.codigo == "ESCUTA_FORA_DO_LOOPBACK" }, host)
        }
        val aberto = ValidadorDeConfig.validar(dev + ("PAINEL_HOST" to "0.0.0.0"), TestPaths.configDir, hoje)
        assertEquals(listOf("ESCUTA_FORA_DO_LOOPBACK" to "PAINEL_HOST"), aberto.avisos.map { it.codigo to it.arquivo })
    }
}
