package br.sidia.painel.operacao

import br.sidia.painel.config.Settings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MascaraDeLogTest {
    @BeforeEach
    @AfterEach
    fun limpar() = MascaraDeLog.limparRegistrosParaTeste()

    @Test
    fun bearerEBasicMascarados() {
        val saida = MascaraDeLog.mascarar("falhou com Bearer abcdefghijklmnop e Basic dXNlcjpzZW5oYTEyMw== ok")
        assertFalse("abcdefghijklmnop" in saida)
        assertFalse("dXNlcjpzZW5oYTEyMw" in saida)
        assertTrue("Bearer ***" in saida && "Basic ***" in saida, saida)
        assertTrue(saida.startsWith("falhou com ") && saida.endsWith(" ok"), saida)
    }

    @Test
    fun cabecalhoAuthorizationMascarado() {
        val saida = MascaraDeLog.mascarar("Authorization: Token-qualquer-coisa\noutra linha")
        assertEquals("Authorization: ***\noutra linha", saida)
    }

    @Test
    fun senhaEmChaveValorMascarada() {
        // valores fictícios (a palavra "secreto" os marca como exemplo para a varredura de segredos do CI)
        assertEquals("password=***", MascaraDeLog.mascarar("password=valor-secreto-1"))
        assertEquals("senha: ***", MascaraDeLog.mascarar("senha: valor-secreto-1"))
        assertEquals("SESSION_SECRET=*** fim", MascaraDeLog.mascarar("SESSION_SECRET=valor-secreto-1 fim"))
        assertEquals("""{"token":"***"}""", MascaraDeLog.mascarar("""{"token":"valor-secreto-1"}"""))
    }

    @Test
    fun credencialEmUrlMascarada() {
        assertEquals("https://***@h.invalid/x", MascaraDeLog.mascarar("https://u:p@h.invalid/x"))
    }

    @Test
    fun segredoRegistradoMascaradoTambemEscapado() {
        val segredo = "abc/def+ghi12345"
        MascaraDeLog.registrarSegredos(listOf(segredo))
        val texto = "cru $segredo; url abc%2Fdef%2Bghi12345; json abc\\/def+ghi12345"
        val saida = MascaraDeLog.mascarar(texto)
        assertEquals("cru ***; url ***; json ***", saida)
    }

    @Test
    fun segredoRegistradoMascaradoEmBase64() {
        val segredo = "token-ficticio-9876"
        MascaraDeLog.registrarSegredos(listOf(segredo))
        val b64 = java.util.Base64.getEncoder().encodeToString(segredo.toByteArray())
        assertFalse(b64 in MascaraDeLog.mascarar("auth $b64 fim"))
    }

    @Test
    fun segredoCurtoNaoERegistrado() {
        MascaraDeLog.registrarSegredos(listOf("abc"))
        assertEquals("o abc passou", MascaraDeLog.mascarar("o abc passou"))
    }

    @Test
    fun cookieDeSessaoMascarado() {
        val saida = MascaraDeLog.mascarar("Cookie: painel_sessao=Zm9vYmFy; outro=1")
        assertFalse("Zm9vYmFy" in saida)
        val soValor = MascaraDeLog.mascarar("leu painel_sessao=Zm9vYmFy;")
        assertEquals("leu painel_sessao=***;", soValor)
    }

    @Test
    fun textoComumIntacto() {
        val texto = "Sincronização OK em 12 ms; rota=/api/tarefa/{key} status=200 caminho path=/x"
        assertEquals(texto, MascaraDeLog.mascarar(texto))
    }

    @Test
    fun valorSecretoDoSettingsRegistrado() {
        val settings = Settings.fromEnv(
            mapOf(
                "JIRA_SAMSUNG_URL" to "https://samsung.invalid",
                "JIRA_SAMSUNG_TOKEN" to "tok-ficticio-AbC12345",
            ),
        )
        val valores = settings.valoresSecretos()
        assertTrue("tok-ficticio-AbC12345" in valores, "o token da instância deve vir de valoresSecretos()")
        MascaraDeLog.registrarSegredos(valores)
        assertEquals("erro *** fim", MascaraDeLog.mascarar("erro tok-ficticio-AbC12345 fim"))
    }

    @Test
    fun segredoDoOidcVemDeValoresSecretos() {
        val settings = Settings.fromEnv(mapOf("OIDC_CLIENT_SECRET" to "oidc-ficticio-Zx98765"))
        val valores = settings.valoresSecretos()
        assertTrue("oidc-ficticio-Zx98765" in valores)
        MascaraDeLog.registrarSegredos(valores)
        assertEquals("erro *** fim", MascaraDeLog.mascarar("erro oidc-ficticio-Zx98765 fim"))
        assertFalse("oidc-ficticio-Zx98765" in settings.toString())
    }

    @Test
    fun valorComEspacoOuAspasNaChaveValorEMascaradoPorInteiro() {
        // entre aspas, o valor vai até a aspa de fechamento (com espaços); sem aspas, até o espaço
        assertEquals("""password="***" fim""", MascaraDeLog.mascarar("""password="valor secreto 1" fim"""))
        assertEquals("""{"client_secret":"***"}""", MascaraDeLog.mascarar("""{"client_secret":"valor secreto 1"}"""))
        assertEquals("OIDC_CLIENT_SECRET='***' fim", MascaraDeLog.mascarar("OIDC_CLIENT_SECRET='valor secreto 1' fim"))
        assertEquals("""senha: "***"""", MascaraDeLog.mascarar("""senha: "a b\"c d""""))
    }

    @Test
    fun jsonDentroDeJsonComAspasEscapadasEMascarado() {
        // um corpo JSON dentro do campo "msg" de um log JSON: as aspas internas chegam escapadas
        val interno = """{"msg":"corpo: {\"password\":\"valor secreto 1\",\"user\":\"ana\"}"}"""
        val saida = MascaraDeLog.mascarar(interno)
        assertFalse("valor secreto 1" in saida, saida)
        assertFalse("secreto" in saida, saida)
        assertTrue("""\"user\":\"ana\"""" in saida, "o resto do texto fica intacto: $saida")
        assertEquals("""{"msg":"x {\"client_secret\":\"***\"}"}""", MascaraDeLog.mascarar("""{"msg":"x {\"client_secret\":\"a b c\"}"}"""))
        // escapado duas vezes
        assertEquals("""{\\"token\\":\\"***\\"}""", MascaraDeLog.mascarar("""{\\"token\\":\\"abc def\\"}"""))
    }

    @Test
    fun senhaComEspacoSemAspasEMascaradaPorInteiro() {
        assertEquals("password=***", MascaraDeLog.mascarar("password=a b"))
        assertEquals("falha: senha: *** fim", MascaraDeLog.mascarar("falha: senha: minha senha 123\nfim").replace("\n", " "))
        // para na próxima chave=valor, na linha, na vírgula e na aspa
        assertEquals("password=*** user=ana", MascaraDeLog.mascarar("password=a b user=ana"))
        assertEquals("password=***, depois", MascaraDeLog.mascarar("password=a b, depois"))
        // token é opaco: continua parando no espaço
        assertEquals("token=*** fim", MascaraDeLog.mascarar("token=abc fim"))
        // idempotente
        assertEquals("password=***", MascaraDeLog.mascarar(MascaraDeLog.mascarar("password=a b")))
    }

    @Test
    fun settingsSemSegredosNaoDevolveNada() {
        assertTrue(Settings().valoresSecretos().isEmpty())
    }

    private fun medirMs(texto: String): Long {
        MascaraDeLog.mascarar("aquecimento password=x")
        val ini = System.nanoTime()
        MascaraDeLog.mascarar(texto)
        return (System.nanoTime() - ini) / 1_000_000
    }

    @Test
    fun entradaGrandeNaoTravaAMascara() {
        // antes: tempo quadrático (cerca de 7 s com 20 mil caracteres)
        val casos = mapOf(
            "alfanumerico" to "a".repeat(20_000),
            "espacos" to " ".repeat(20_000),
            "alternado" to "a ".repeat(10_000),
            "chaves" to "x=".repeat(10_000),
            "esquema" to "://".repeat(7_000),
            "bearer" to "Bearer ".repeat(3_000),
            "senhas" to "senha: ".repeat(3_000),
        )
        for ((nome, texto) in casos) {
            val ms = medirMs(texto)
            assertTrue(ms < 200, "$nome levou ${ms}ms")
        }
    }

    @Test
    fun linhaLongaECortadaComMarcaAntesDeMascarar() {
        val saida = MascaraDeLog.mascarar("password=segredo-1234 " + "x".repeat(20_000))
        assertTrue(saida.length < 9_000, "${saida.length}")
        assertTrue(saida.endsWith("[truncado]") && saida.startsWith("password=***"), saida.takeLast(30))
        assertTrue("segredo-1234" !in saida)
    }

    @Test
    fun segredoNoFimDaLinhaLongaNaoVazaPeloCorte() {
        MascaraDeLog.registrarSegredos(listOf("SEGREDO-REGISTRADO-XYZ"))
        // o segredo registrado cruza o limite do corte: nem inteiro nem pedaço pode sobrar
        for (deslocamento in listOf(0, 3, 10, 21)) {
            val texto = "a ".repeat(4_090 - deslocamento / 2) + "z".repeat(deslocamento % 2) + " SEGREDO-REGISTRADO-XYZ " + "b".repeat(200)
            val saida = MascaraDeLog.mascarar(texto)
            assertFalse("SEGREDO-REG" in saida, "deslocamento $deslocamento")
        }
    }
}
