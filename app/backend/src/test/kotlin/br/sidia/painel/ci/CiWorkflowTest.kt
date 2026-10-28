package br.sidia.painel.ci

import br.sidia.painel.TestPaths
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlNull
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.yamlMap
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Trava o workflow de CI (.github/workflows/ci.yml) com um teste estático. O YAML é lido sem rede.
 * Os nomes dos jobs são os nomes dos checks que a proteção da `main` exige: mudar um deles, filtrar por
 * caminho ou deixar de rodar em pull request faria a proteção esperar para sempre (ou ser contornada).
 */
class CiWorkflowTest {
    private val texto: String = Files.readString(TestPaths.repoDir.resolve(".github/workflows/ci.yml"))
    private val raiz: YamlMap = Yaml.default.parseToYamlNode(texto).yamlMap

    private fun mapa(no: YamlNode?, onde: String): YamlMap =
        assertNotNull(no, "Faltou $onde") as? YamlMap ?: error("$onde deveria ser um mapa")

    private fun texto(no: YamlNode?): String? = (no as? YamlScalar)?.content

    private val jobs: Map<String, YamlMap> =
        mapa(raiz.get<YamlNode>("jobs"), "jobs").entries.map { (k, v) -> k.content to mapa(v, "job ${k.content}") }.toMap()

    private fun passos(job: String): List<YamlMap> =
        ((jobs.getValue(job).get<YamlNode>("steps")) as YamlList).items.map { mapa(it, "passo de $job") }

    private fun comandos(job: String): List<String> = passos(job).mapNotNull { texto(it.get<YamlNode>("run")) }

    private fun usos(): List<String> = jobs.keys.flatMap { passos(it) }.mapNotNull { texto(it.get<YamlNode>("uses")) }

    private fun com(passo: YamlMap): YamlMap? = passo.get<YamlNode>("with") as? YamlMap

    @Test
    fun cincoJobsDoWorkflow() {
        assertEquals(setOf("backend", "frontend", "guarda", "guarda-windows", "varredura"), jobs.keys)
    }

    @Test
    fun guardaWindowsProvaOHookNosDoisPowerShell() {
        assertEquals("windows-latest", texto(jobs.getValue("guarda-windows").get<YamlNode>("runs-on")))
        val testes = passos("guarda-windows").filter { texto(it.get<YamlNode>("run"))?.contains("test-PreToolUse.ps1") == true }
        assertEquals(listOf("powershell", "pwsh"), testes.map { texto(it.get<YamlNode>("shell")) }, "5.1 e depois 7")
        testes.forEach {
            val cmd = texto(it.get<YamlNode>("run"))!!
            assertTrue(cmd.contains("-NoProfile -ExecutionPolicy Bypass -File .clinerules\\hooks\\test-PreToolUse.ps1"), cmd)
            assertTrue(cmd.contains("\$LASTEXITCODE"), "precisa checar o código de saída")
            assertTrue(cmd.contains("falhas: 0"), "precisa checar o resumo de falhas")
            // Select-String ignora maiúsculas por padrão: sem -CaseSensitive, "falhas: 0" casaria com ^FALHA.
            assertTrue(cmd.contains("-Pattern '^FALHA ' -CaseSensitive"), "padrão de caso falho sensível a maiúsculas: $cmd")
            assertTrue(cmd.contains("-Pattern '^falhas: 0\$' -CaseSensitive"), "resumo sensível a maiúsculas: $cmd")
        }
        // CRLF/BOM dos .ps1 não podem ser alterados pelo checkout.
        assertTrue(comandos("guarda-windows").any { it.contains("core.autocrlf false") })
    }

    @Test
    fun nomesDosJobsSaoOsChecksObrigatorios() {
        jobs.forEach { (id, job) -> assertEquals(id, texto(job.get<YamlNode>("name")), "name do job $id") }
        // Filtro de caminho ou de branch faria o check obrigatório nunca aparecer em alguns PRs.
        val filtros = Regex("(?m)^\\s*(paths|paths-ignore|branches-ignore)\\s*:").findAll(texto).map { it.value.trim() }.toList()
        assertTrue(filtros.isEmpty(), "Filtros proibidos no workflow: $filtros")
    }

    @Test
    fun gatilhosSaoPullRequestEPushNaMain() {
        val gatilhos = mapa(raiz.get<YamlNode>("on"), "on")
        assertEquals(setOf("pull_request", "push"), gatilhos.entries.keys.map { it.content }.toSet())
        // pull_request sem filtro nenhum (sem branches, sem tipos).
        val pr = gatilhos.get<YamlNode>("pull_request")
        assertTrue(pr == null || pr is YamlNull || (pr is YamlMap && pr.entries.isEmpty()), "pull_request não pode ter filtro")
        val push = mapa(gatilhos.get<YamlNode>("push"), "push")
        assertEquals(setOf("branches"), push.entries.keys.map { it.content }.toSet())
        val branches = (push.get<YamlNode>("branches") as YamlList).items.map { texto(it) }
        assertEquals(listOf("main"), branches)
    }

    @Test
    fun flagDoJiraFixadaEmFalseNoWorkflow() {
        val env = mapa(raiz.get<YamlNode>("env"), "env")
        assertEquals("false", texto(env.get<YamlNode>("JIRA_LIVE_ENABLED")))
        val variaveis = Regex("JIRA_[A-Z0-9_]+").findAll(texto).map { it.value }.toSet()
        assertEquals(setOf("JIRA_LIVE_ENABLED"), variaveis, "Só a flag do Jira, fixada em false, pode aparecer")
        assertFalse(texto.contains("secrets.JIRA"), "O workflow não declara segredo do Jira")
    }

    @Test
    fun permissaoSoLeitura() {
        val permissoes = mapa(raiz.get<YamlNode>("permissions"), "permissions")
        assertEquals(mapOf("contents" to "read"), permissoes.entries.map { (k, v) -> k.content to texto(v) }.toMap())
        jobs.forEach { (id, job) -> assertNull(job.get<YamlNode>("permissions"), "job $id não redefine permissões") }
    }

    private fun assertNull(valor: Any?, mensagem: String) = assertTrue(valor == null, mensagem)

    @Test
    fun semPullRequestTarget() {
        assertFalse(texto.contains("pull_request_target"))
    }

    @Test
    fun soSegredoDaVarredura() {
        val segredos = Regex("secrets\\.([A-Z_]+)").findAll(texto).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("VARREDURA_NOMES"), segredos)
    }

    @Test
    fun semExpressaoDentroDeRun() {
        jobs.keys.flatMap { comandos(it) }.forEach { assertFalse(it.contains("\${{"), "Expressão dentro de run: $it") }
    }

    @Test
    fun checkoutSemCredencialPersistida() {
        val checkouts = jobs.keys.flatMap { passos(it) }.filter { texto(it.get<YamlNode>("uses"))?.startsWith("actions/checkout") == true }
        assertEquals(5, checkouts.size, "Um checkout por job")
        checkouts.forEach { assertEquals("false", texto(com(it)?.get<YamlNode>("persist-credentials"))) }
    }

    @Test
    fun actionsPorVersaoFixa() {
        val valida = Regex("^[A-Za-z0-9_./-]+@(v\\d+|[0-9a-f]{40})$")
        usos().forEach { assertTrue(valida.matches(it), "Action sem versão fixa: $it") }
        assertTrue(usos().isNotEmpty())
    }

    @Test
    fun versoesFixas() {
        val java = jobs.keys.flatMap { passos(it) }.first { texto(it.get<YamlNode>("uses"))?.startsWith("actions/setup-java") == true }
        assertEquals("21", texto(com(java)?.get<YamlNode>("java-version")))
        assertEquals("temurin", texto(com(java)?.get<YamlNode>("distribution")))
        val node = jobs.keys.flatMap { passos(it) }.first { texto(it.get<YamlNode>("uses"))?.startsWith("actions/setup-node") == true }
        assertEquals("22", texto(com(node)?.get<YamlNode>("node-version")))
        assertTrue(comandos("frontend").any { it.contains("corepack prepare pnpm@9.12.3 --activate") })
        assertTrue(comandos("frontend").any { it.contains("pnpm install --frozen-lockfile") })
    }

    @Test
    fun semServicosNemContainers() {
        jobs.forEach { (id, job) ->
            assertNull(job.get<YamlNode>("services"), "job $id usa services")
            assertNull(job.get<YamlNode>("container"), "job $id usa container")
        }
        listOf("docker", "testcontainers", "postgres").forEach {
            assertFalse(texto.lowercase().contains(it), "O workflow não pode citar $it")
        }
    }

    @Test
    fun backendRodaASuiteInteira() {
        val gradle = comandos("backend").filter { it.contains("./gradlew") }
        assertTrue(gradle.any { Regex("\\./gradlew\\b.*\\btest\\b").containsMatchIn(it) }, "O backend precisa rodar ./gradlew test")
        gradle.forEach {
            assertFalse(it.contains("-x test"), it)
            assertFalse(it.contains("--tests"), it)
        }
    }

    @Test
    fun frontendRodaLintTestesEBuild() {
        val cmds = comandos("frontend")
        val ordem = listOf("pnpm lint", "pnpm vitest run", "pnpm build").map { alvo -> cmds.indexOfFirst { it.contains(alvo) } }
        assertTrue(ordem.all { it >= 0 }, "Faltou lint, testes ou build: $ordem")
        assertEquals(ordem.sorted(), ordem, "Ordem: lint, testes, build")
        jobs.getValue("frontend").let { assertEquals("app/frontend", texto(passos("frontend").first { p -> texto(p.get<YamlNode>("run"))?.contains("pnpm lint") == true }.get<YamlNode>("working-directory"))) }
    }

    @Test
    fun frontendVerificaCsp() {
        val cmds = comandos("frontend")
        val build = cmds.indexOfFirst { it.contains("pnpm build") }
        val csp = cmds.indexOfFirst { it.contains("node scripts/verificar-csp.mjs") }
        assertTrue(csp >= 0, "O job frontend precisa rodar verificar-csp.mjs")
        assertTrue(csp > build, "A verificação de CSP vem depois do build")
    }

    @Test
    fun guardaRodaOsDoisTestesDeHook() {
        val cmds = comandos("guarda").joinToString("\n")
        assertTrue(cmds.contains("sh .claude/hooks/test-guard.sh"))
        assertTrue(cmds.contains("sh .clinerules/hooks/test-PreToolUse.sh"))
    }

    @Test
    fun varreduraRodaTesteEScan() {
        val checkout = passos("varredura").first { texto(it.get<YamlNode>("uses"))?.startsWith("actions/checkout") == true }
        assertEquals("0", texto(com(checkout)?.get<YamlNode>("fetch-depth")))
        val cmds = comandos("varredura").joinToString("\n")
        assertTrue(cmds.contains("scripts/ci/test-varredura.sh"))
        assertTrue(cmds.contains("scripts/ci/varrer-dados-pessoais.sh"))
    }

    @Test
    fun timeoutsDefinidos() {
        jobs.forEach { (id, job) -> assertNotNull(job.get<YamlNode>("timeout-minutes"), "job $id sem timeout-minutes") }
    }
}
