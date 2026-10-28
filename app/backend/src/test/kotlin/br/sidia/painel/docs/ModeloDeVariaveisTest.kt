package br.sidia.painel.docs

import br.sidia.painel.TestPaths
import br.sidia.painel.config.ArquivoEnv
import br.sidia.painel.config.PerfilDeExecucao
import br.sidia.painel.config.Settings
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Protege o modelo de variáveis (`app/.env.example`) e o `app/README.md`: copiar o modelo para produção
 * nunca pode ligar o modo desenvolvimento nem os dados de demonstração, e nada de endereço ou segredo real entra.
 * Só lê arquivos do repositório; não toca no arquivo de variáveis real nem no Jira.
 */
class ModeloDeVariaveisTest {
    private val modeloPath: Path = TestPaths.repoDir.resolve("app/.env.example")
    private val readmePath: Path = TestPaths.repoDir.resolve("app/README.md")

    private fun textoDoModelo(): String {
        assertTrue(Files.isRegularFile(modeloPath), "Modelo ausente: $modeloPath")
        return Files.readString(modeloPath)
    }

    /** Só as atribuições ativas (linhas comentadas não entram), como o painel lê. */
    private fun modeloAtivo(): Map<String, String> = ArquivoEnv.interpretar(Files.readAllLines(modeloPath))

    @Test
    fun modeloNaoLigaModoDevNemDemonstracao() {
        val ativo = modeloAtivo()
        for (chave in listOf("DASHBOARD_DEV_MODE", "DASHBOARD_DEMO_DATA")) {
            val valor = ativo[chave]
            assertTrue(valor == null || valor.trim().equals("false", ignoreCase = true), "$chave ativa no modelo com valor ligado")
        }
    }

    @Test
    fun modeloSobeEmProducaoNoPacote() {
        // O modelo copiado SEM preencher o login não sobe em produção (falha fechada, citando só o nome da variável)...
        val erro = kotlin.test.assertFailsWith<IllegalArgumentException> {
            Settings.fromEnv(PerfilDeExecucao.aplicar(PerfilDeExecucao.PRODUCAO, modeloAtivo()))
        }
        assertTrue(erro.message.orEmpty().startsWith("OIDC_ISSUER "), erro.message)
        // ...e com o login preenchido sobe em produção, nunca em modo desenvolvimento.
        val settings = Settings.fromEnv(PerfilDeExecucao.aplicar(PerfilDeExecucao.PRODUCAO, modeloAtivo() + br.sidia.painel.ENV_OIDC_DE_TESTE))
        assertEquals(false, settings.dashboardDevMode)
    }

    @Test
    fun modeloMantemOJiraDesligado() {
        assertEquals("false", modeloAtivo()["JIRA_LIVE_ENABLED"]?.trim()?.lowercase())
    }

    @Test
    fun modeloNaoTrazSegredoNemEnderecoReal() {
        val sensiveis = Regex("(_URL|_USER|_TOKEN|_SECRET|_PASSWORD|_SENHA|PASSWORD|SENHA|SECRET|TOKEN)$")
        val comValor = modeloAtivo().filter { (chave, valor) -> sensiveis.containsMatchIn(chave) && valor.isNotBlank() }
        assertTrue(comValor.isEmpty(), "Variáveis sensíveis com valor no modelo: ${comValor.keys}")

        val hostsReais = Regex("https?://([^/\\s:?#]+)", RegexOption.IGNORE_CASE)
            .findAll(textoDoModelo())
            .map { it.groupValues[1].lowercase() }
            .filterNot { it.endsWith(".invalid") || it.endsWith(".example") || it == "localhost" || it == "127.0.0.1" }
            .toList()
        assertTrue(hostsReais.isEmpty(), "O modelo cita ${hostsReais.size} endereço(s) fora de .invalid/.example/localhost")
    }

    @Test
    fun modeloDocumentaOrigemPublicaEPacote() {
        val texto = textoDoModelo()
        assertTrue(texto.contains("DASHBOARD_ORIGEM_PUBLICA"), "Modelo sem DASHBOARD_ORIGEM_PUBLICA")
        assertTrue(texto.contains("pacote instalado"), "Modelo sem a explicação do pacote instalado")
    }

    @Test
    fun readmeDiferenciaOModoDevDoPacote() {
        assertTrue(Files.isRegularFile(readmePath), "README ausente: $readmePath")
        val texto = Files.readString(readmePath)
        for (termo in listOf("installDist", "DASHBOARD_DEV_MODE", "pacote instalado")) {
            assertTrue(texto.contains(termo), "README sem '$termo'")
        }
    }
}
