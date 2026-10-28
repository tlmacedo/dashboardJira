package br.sidia.painel.http

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.TestPaths
import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.trabalho.RespTrb
import br.sidia.painel.trabalho.Trb1
import br.sidia.painel.trabalho.novoContextoTrb1
import br.sidia.painel.trabalho.rodarTrb1
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.AfterAll
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Todo aviso e todo erro do backend sai por código, com tradução em pt e en (Plano 10, T9). */
class CodigosDeMensagemTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()
    }

    private fun traducoes(idioma: String): JsonObject =
        Json.parseToJsonElement(Files.readString(TestPaths.repoDir.resolve("app/frontend/src/i18n/$idioma.json"))).jsonObject

    @Test
    fun todoCodigoTemTraducaoPtEEn() {
        for (idioma in listOf("pt", "en")) {
            val chaves = traducoes(idioma).keys
            for (c in CodigosDeMensagem.avisos) assertTrue("cardpage.aviso.$c" in chaves, "aviso $c sem tradução em $idioma")
            for (c in CodigosDeMensagem.erros) assertTrue("erros.$c" in chaves, "erro $c sem tradução em $idioma")
        }
        // O aviso desconhecido também tem texto (a tela nunca mostra a mensagem em português do servidor).
        for (idioma in listOf("pt", "en")) assertTrue("cardpage.aviso.DESCONHECIDO" in traducoes(idioma).keys)
    }

    @Test
    fun codigosConhecidosCobremOsEmitidos() {
        val fontes = Files.walk(TestPaths.mainSrcDir.resolve("kotlin")).use { s ->
            s.filter { it.toString().endsWith(".kt") }.map { Files.readString(it) }.toList()
        }
        val todos = CodigosDeMensagem.avisos + CodigosDeMensagem.erros + CodigosDeMensagem.outros
        val achados = mutableSetOf<String>()
        // Literais passados direto aos DTOs.
        val literais = Regex("""(?:AvisoDto|ErroDto)\(\s*"([A-Z_0-9]+)"""")
        // Constantes de código: `const val X = "X"` (aviso, erro ou enum de texto) e `const val CODIGO_X = "X"`.
        val constantes = Regex("""const val ([A-Z_0-9]+) = "([A-Z_0-9]+)"""")
        for (f in fontes) {
            literais.findAll(f).forEach { achados += it.groupValues[1] }
            constantes.findAll(f).forEach { if (it.groupValues[1] == it.groupValues[2] || it.groupValues[1].startsWith("CODIGO")) achados += it.groupValues[2] }
        }
        assertTrue(achados.size > 40, "a varredura achou poucos códigos: ${achados.size}")
        assertEquals(emptySet(), achados - todos, "código emitido que não está em CodigosDeMensagem (acrescente-o e traduza)")
        // A mão contrária: nada sobrando nos conjuntos (cada código listado existe no código-fonte).
        val soUsados = (CodigosDeMensagem.avisos + CodigosDeMensagem.erros).filter { c -> fontes.none { "\"$c\"" in it } }
        assertEquals(emptyList(), soUsados, "código listado que nenhum arquivo emite")
        // O próprio aviso do contrato existe.
        assertTrue(AvisoDto.OPCOES_TRUNCADAS in CodigosDeMensagem.avisos)
    }

    @Test
    fun respostas400SaoErroDto() {
        val c = novoContextoTrb1(banco, Trb1.issues())
        try {
            rodarTrb1(c) { get ->
                val amostra: List<Pair<String, String>> = listOf(
                    "/api/cards/tempo-em-status?de=2026-13-01" to "gerente.exemplo",
                    "/api/cards/abertas?campo.x=1" to "gerente.exemplo",
                    "/api/cards/fechadas?de=2026-10-30&ate=2026-10-01" to "gerente.exemplo",
                    "/api/auditoria/sinais?de=2026-01-01&ate=2026-12-31" to "coord.um",
                    "/api/cards/abertas?unit=semana" to "gerente.exemplo",
                    "/api/tarefa/XX%3B" to "gerente.exemplo",
                )
                for ((caminho, usuario) in amostra) {
                    val r: RespTrb = get(usuario, caminho)
                    assertEquals(HttpStatusCode.BadRequest, r.status, "$caminho ${r.corpo}")
                    assertTrue(r.corpo.isNotEmpty(), "400 sem corpo em $caminho (usuário $usuario)")
                    val erro = Json.decodeFromString<ErroDto>(r.corpo)
                    assertTrue(erro.codigo in CodigosDeMensagem.erros, "$caminho devolveu ${erro.codigo}")
                    // Sem texto em português no corpo.
                    assertTrue(Regex("[áàâãéêíóôõúç]", RegexOption.IGNORE_CASE).find(r.corpo) == null, r.corpo)
                }
            }
        } finally {
            c.close()
        }
    }
}
