package br.sidia.painel.docs

import br.sidia.painel.TestPaths
import br.sidia.painel.operacao.CodigoAlerta
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Os runbooks de operação (Plano 12) são lidos como código: todo alerta tem procedimento, todo comando citado existe, toda
 * variável do modelo está documentada, cada passo vem em PowerShell E em sh e nada de plataforma proibida, host real, token ou
 * e-mail entra. Só lê arquivos do repositório; nunca fala com o Jira.
 */
class RunbooksDocTest {
    private val raiz: Path = TestPaths.repoDir
    private fun ler(relativo: String): String = Files.readString(raiz.resolve(relativo))

    /** Os documentos de operação escritos neste plano (o `ci.md` é de outro plano e tem as suas próprias regras). */
    private val documentos = listOf(
        "configuracao.md", "observabilidade.md", "backup-e-restauracao.md", "runbook-diario.md", "rotacao-de-segredos.md",
    ).map { "docs/operacao/$it" }

    private val diario get() = ler("docs/operacao/runbook-diario.md")

    /** O trecho de uma seção `### ...` até o próximo título de qualquer nível. */
    private fun secao(texto: String, titulo: String): String {
        val inicio = texto.indexOf(titulo)
        assertTrue(inicio >= 0, "faltou a seção '$titulo'")
        val resto = texto.substring(inicio + titulo.length)
        val fim = Regex("(?m)^#{1,3} ").find(resto)?.range?.first ?: resto.length
        return resto.substring(0, fim)
    }

    @Test
    fun cadaAlertaTemSecao() {
        for (codigo in CodigoAlerta.entries) {
            val titulo = "### ALERTA ${codigo.name}"
            assertTrue(Regex("(?m)^${Regex.escape(titulo)}$").containsMatchIn(diario), "runbook-diario.md sem '$titulo'")
            val corpo = secao(diario, titulo)
            for (campo in listOf("O que significa", "Como confirmar", "O que fazer", "Quando escalar")) {
                assertTrue(campo in corpo, "$titulo sem '$campo'")
            }
        }
    }

    @Test
    fun cadaVariavelDoEnvExampleEstaDocumentada() {
        val nomes = ler("app/.env.example").lines()
            .mapNotNull { Regex("^#?\\s?([A-Z][A-Z0-9_]{2,})=").find(it)?.groupValues?.get(1) }
            .toSet()
        assertTrue(nomes.size >= 30, "o modelo deveria listar as variáveis do painel (achou ${nomes.size})")
        val fontes = documentos.map { ler(it) } + listOf("app/README.md").map { ler(it) } +
            listOf("docs/implantacao.md").filter { Files.exists(raiz.resolve(it)) }.map { ler(it) }
        val semDoc = nomes.filter { nome -> fontes.none { nome in it } }
        assertEquals(emptyList(), semDoc, "variáveis do app/.env.example sem documentação em docs/operacao, docs/implantacao.md ou app/README.md")
    }

    @Test
    fun semHostRealTokenNemEmail() {
        val hostsPermitidos = setOf("localhost", "127.0.0.1", "example.com", "exemplo.invalid")
        for (relativo in documentos) {
            val texto = ler(relativo)
            for (m in Regex("https?://([^/\\s:?#)`>\\]]+)", RegexOption.IGNORE_CASE).findAll(texto)) {
                val host = m.groupValues[1].lowercase()
                assertTrue(host in hostsPermitidos || host.endsWith(".invalid"), "$relativo: host real em '${m.value}'")
            }
            assertFalse(Regex("Bearer\\s+[A-Za-z0-9._~+/=-]{8,}").containsMatchIn(texto), "$relativo: 'Bearer' seguido de valor")
            assertFalse(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+").containsMatchIn(texto), "$relativo: e-mail no documento")
        }
    }

    @Test
    fun jiraSoPeloUsuario() {
        for (relativo in documentos.filter { it.endsWith("runbook-diario.md") || it.endsWith("runbook-incidentes.md") }) {
            val texto = ler(relativo)
            assertTrue("só o usuário" in texto, "$relativo precisa dizer que só o usuário muda a flag")
            assertTrue("JIRA_LIVE_ENABLED" in texto)
            assertFalse("autorização expressa" in texto, "$relativo cita a regra antiga")
        }
    }

    @Test
    fun cadaSistemaTemOsDoisBlocos() {
        val comandos = Regex("(?m)^### COMANDO .+$").findAll(diario).map { it.value }.toList()
        assertTrue(comandos.size >= 9, "esperados os comandos do dia a dia, achou ${comandos.size}")
        for (titulo in comandos) {
            val corpo = secao(diario, titulo)
            assertTrue("```powershell" in corpo, "$titulo sem bloco powershell")
            assertTrue("```sh" in corpo, "$titulo sem bloco sh")
        }
    }

    @Test
    fun semDockerNemPostgres() {
        val proibidas = Regex("docker|pg_dump|testcontainers|postgres", RegexOption.IGNORE_CASE)
        for (relativo in documentos) {
            ler(relativo).lines().filter { proibidas.containsMatchIn(it) }.forEach { linha ->
                assertTrue(
                    Regex("não existe|nem |sem ", RegexOption.IGNORE_CASE).containsMatchIn(linha),
                    "$relativo cita plataforma proibida fora de uma negação: '$linha'",
                )
            }
        }
    }

    @Test
    fun comandosCitadosExistem() {
        val fonte = Files.readString(TestPaths.mainSrcDir.resolve("kotlin/br/sidia/painel/operacao/ComandosDeOperacao.kt"))
        val subcomandos = listOf("validar-config", "backup-agora", "verificar-restauracao")
        for (sub in subcomandos) assertTrue("\"$sub\"" in fonte, "o subcomando $sub não existe no código")
        assertTrue(Files.isRegularFile(raiz.resolve("app/scripts/painel-cli.sh")))
        assertTrue(Files.isRegularFile(raiz.resolve("app/scripts/painel-cli.ps1")))
        // todo "painel-cli.<sh|ps1> <subcomando>" citado nos documentos usa um subcomando que existe
        for (relativo in documentos) {
            for (m in Regex("painel-cli\\.(?:sh|ps1)\\s+([a-z][a-z-]+)").findAll(ler(relativo))) {
                assertTrue(m.groupValues[1] in subcomandos, "$relativo cita o subcomando inexistente '${m.groupValues[1]}'")
            }
        }
        // os documentos citam os três
        val tudo = documentos.joinToString("\n") { ler(it) }
        for (sub in subcomandos) assertTrue(sub in tudo, "nenhum documento cita $sub")
    }

    @Test
    fun runbookDiarioTemAsSecoesFixas() {
        for (titulo in listOf(
            "## Rotina", "## Comandos do dia a dia", "## Alertas", "## Entrada e saída de pessoas", "## Ausências",
            "## Feriados do ano seguinte", "## Pedido do titular", "## Atualizar e reverter versão", "## Ligar e desligar o Jira",
        )) {
            assertTrue(Regex("(?m)^${Regex.escape(titulo)}$").containsMatchIn(diario), "runbook-diario.md sem '$titulo'")
        }
    }
}
