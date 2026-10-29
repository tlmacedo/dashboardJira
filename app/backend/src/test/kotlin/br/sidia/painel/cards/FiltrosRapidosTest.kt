package br.sidia.painel.cards

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.TestPaths
import br.sidia.painel.config.Settings
import br.sidia.painel.trabalho.Trb1
import br.sidia.painel.trabalho.novoContextoTrb1
import br.sidia.painel.trabalho.rodarTrb1
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Filtros rápidos nomeados em `filtros_rapidos.yaml` (ideia I-27): só combinam filtros que já existem; nunca nomeiam pessoa. */
class FiltrosRapidosTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()
    }

    private var ctx: AppContext? = null

    @AfterTest
    fun encerrar() {
        ctx?.close()
    }

    private fun yaml(conteudo: String) = Files.createTempFile("rapidos", ".yaml").also { Files.writeString(it, conteudo) }

    @Test
    fun modeloVersionadoEhValido() {
        val lista = FiltrosRapidos.fromYaml(TestPaths.configDir.resolve("filtros_rapidos.exemplo.yaml"))
        assertTrue(lista.isNotEmpty())
        assertEquals(lista.map { it.id }.toSet().size, lista.size, "ids únicos")
        assertTrue(lista.all { it.aplica.keys.none { k -> k == "pessoa" } })
    }

    @Test
    fun rejeitaPessoaEChaveDesconhecida() {
        val base = "versao: 1\nfiltros:\n  - id: x\n    rotuloPt: X\n    rotuloEn: X\n    aplica:\n"
        assertFailsWith<IllegalArgumentException> { FiltrosRapidos.fromYaml(yaml(base + "      pessoa: [ana]\n")) }
        assertFailsWith<IllegalArgumentException> { FiltrosRapidos.fromYaml(yaml(base + "      jql: [\"x = 1\"]\n")) }
        assertFailsWith<IllegalArgumentException> { FiltrosRapidos.fromYaml(yaml(base + "      campo.Sku Type: [MR]\n")) }
        assertFailsWith<IllegalArgumentException> { FiltrosRapidos.fromYaml(yaml(base + "      tipo: []\n")) }
        // Id repetido e versão desconhecida.
        val duplo = "versao: 1\nfiltros:\n  - {id: a, rotuloPt: A, rotuloEn: A, aplica: {tipo: [Task]}}\n  - {id: a, rotuloPt: B, rotuloEn: B, aplica: {tipo: [Task]}}\n"
        assertFailsWith<IllegalArgumentException> { FiltrosRapidos.fromYaml(yaml(duplo)) }
        assertFailsWith<IllegalArgumentException> { FiltrosRapidos.fromYaml(yaml("versao: 2\nfiltros: []\n")) }
    }

    @Test
    fun validaSlugDoDicionarioEUnit() {
        val base = "versao: 1\nfiltros:\n  - id: x\n    rotuloPt: X\n    rotuloEn: X\n    aplica:\n"
        val slugs = setOf("sku-type")
        FiltrosRapidos.fromYaml(yaml(base + "      campo.sku-type: [MR]\n      unit: [corrido]\n"), slugs)
        assertFailsWith<IllegalArgumentException> { FiltrosRapidos.fromYaml(yaml(base + "      campo.outro: [MR]\n"), slugs) }
        assertFailsWith<IllegalArgumentException> { FiltrosRapidos.fromYaml(yaml(base + "      unit: [semanas]\n"), slugs) }
        assertFailsWith<IllegalArgumentException> { FiltrosRapidos.fromYaml(yaml(base + "      unit: [util, corrido]\n"), slugs) }
    }

    @Test
    fun arquivoIlegivelNaoDerrubaALeitura() {
        val dir = Files.createTempDirectory("rapidos-ruim")
        // Bytes que não são UTF-8 válido: a leitura do texto lança IOException, e não pode virar 500.
        Files.write(dir.resolve("filtros_rapidos.yaml"), byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0xC0.toByte()))
        assertEquals(emptyList(), FiltrosRapidos.ler(dir))
    }

    @Test
    fun arquivoAusenteDaListaVazia() {
        assertEquals(emptyList(), FiltrosRapidos.ler(Files.createTempDirectory("sem-config")))
    }

    @Test
    fun opcoesTrazOsRapidosDoYaml() {
        val c = novoContextoTrb1(banco, Trb1.issues(), Settings(dashboardDevMode = false, configDir = TestPaths.configDir)).also { ctx = it }
        rodarTrb1(c) { get ->
            val r = get("ana", "/api/filtros/opcoes")
            val dto = Json.decodeFromString<OpcoesDeFiltroDto>(r.corpo)
            assertTrue(dto.rapidos.isNotEmpty(), r.corpo)
            assertTrue(dto.rapidos.all { it.rotuloPt.isNotBlank() && it.rotuloEn.isNotBlank() })
        }
    }
}
