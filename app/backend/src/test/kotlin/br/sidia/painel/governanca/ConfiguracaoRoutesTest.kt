package br.sidia.painel.governanca

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.config.Settings
import br.sidia.painel.trabalho.RespTrb
import br.sidia.painel.trabalho.novoContextoTrb1
import br.sidia.painel.trabalho.rodarTrb1
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Configuração em leitura (CF-01): só o gerente e nunca dado pessoal, segredo, URL ou caminho. */
class ConfiguracaoRoutesTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()
    }

    private var ctx: AppContext? = null
    private val pasta: Path = Files.createTempDirectory("cfg-teste")

    @AfterTest
    fun encerrar() {
        ctx?.close()
        pasta.toFile().deleteRecursively()
    }

    private fun escrever(nome: String, texto: String) {
        val alvo = pasta.resolve(nome)
        Files.createDirectories(alvo.parent)
        Files.writeString(alvo, texto)
    }

    private fun rodar(demo: Boolean = false, bloco: suspend (suspend (String?, String) -> RespTrb) -> Unit) {
        val c = novoContextoTrb1(banco, settings = Settings(dashboardDevMode = false, dashboardDemoData = demo, configDir = pasta)).also { ctx = it }
        rodarTrb1(c, bloco = bloco)
    }

    private val rota = "/api/governanca/configuracao"
    private fun dto(r: RespTrb) = Json.decodeFromString<ConfiguracaoDto>(r.corpo)

    @Test
    fun soGerente() = rodar { get ->
        assertEquals(HttpStatusCode.OK, get("gerente.exemplo", rota).status)
        for (u in listOf("coord.um", "lider.alfa", "ana")) assertEquals(HttpStatusCode.Forbidden, get(u, rota).status, u)
        assertEquals(HttpStatusCode.Unauthorized, get(null, rota).status)
    }

    @Test
    fun semDadosPessoaisSegredosNemCaminhos() = rodar { get ->
        escrever("papeis.yaml", "versao: 1\npessoas:\n  - id: ana\n")
        escrever("equipes.yaml", "versao: 1\n")
        escrever("ausencias.yaml", "versao: 1\n")
        escrever("metas.yaml", "versao: 3 # nota\nmeta: 90\n")
        val r = get("gerente.exemplo", rota)
        assertEquals(HttpStatusCode.OK, r.status, r.corpo)
        val corpo = r.corpo.lowercase()
        for (proibido in listOf("\"ana\"", "carla", "lider.alfa", "https://", "http://", "token", "secret", "oidc", "senha", "password", pasta.toString().lowercase(), "papeis", "equipes", "ausencias")) {
            assertFalse(proibido in corpo, "$proibido em ${r.corpo}")
        }
        val d = dto(r)
        assertTrue(d.arquivos.none { it.nome.contains("papeis") || it.nome.contains("equipes") || it.nome.contains("ausencias") })
        assertEquals(listOf("metas"), d.arquivos.map { it.nome })
        assertEquals(3, d.arquivos.single().versao)
        assertTrue(d.pessoas > 0 && d.squads > 0)
    }

    @Test
    fun origemExemploQuandoSoHaModelo() = rodar { get ->
        escrever("risco.exemplo.yaml", "versao: 1\n")
        escrever("calendario.yaml", "versao: 2\n")
        escrever("calendario.exemplo.yaml", "versao: 1\n")
        escrever("cards/qualidade.card.yaml", "id: qualidade\n")
        val d = dto(get("gerente.exemplo", rota))
        val por = d.arquivos.associateBy { it.nome }
        assertEquals("exemplo", por.getValue("risco").origem)
        assertEquals("real", por.getValue("calendario").origem)
        assertEquals(2, por.getValue("calendario").versao, "vale o arquivo real, não o modelo")
        assertEquals("real", por.getValue("cards/qualidade").origem)
        assertNull(por.getValue("cards/qualidade").versao)
        assertEquals(d.arquivos.map { it.nome }, d.arquivos.map { it.nome }.sorted(), "ordem alfabética")
    }

    @Test
    fun hashMudaQuandoOArquivoMuda() = rodar { get ->
        escrever("metas.yaml", "versao: 1\nmeta: 90\n")
        val antes = dto(get("gerente.exemplo", rota)).arquivos.single().sha256
        assertEquals(64, antes.length)
        assertEquals(antes, dto(get("gerente.exemplo", rota)).arquivos.single().sha256, "estável")
        escrever("metas.yaml", "versao: 1\nmeta: 91\n")
        assertNotEquals(antes, dto(get("gerente.exemplo", rota)).arquivos.single().sha256)
    }

    @Test
    fun modoDeSincronizacao() {
        rodar(demo = true) { get ->
            val d = dto(get("gerente.exemplo", rota))
            assertEquals("DEMONSTRACAO", d.modoSync)
            assertFalse(d.liveEnabled)
            assertFalse(d.devMode)
        }
        encerrar()
        rodar(demo = false) { get -> assertEquals("DESLIGADO", dto(get("gerente.exemplo", rota)).modoSync) }
    }
}
