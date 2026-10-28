package br.sidia.painel.security

import br.sidia.painel.operacao.RelogioDeTeste
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Endurecimento do login (revisão do Plano 8): uso único sem DoS e cache negativo da descoberta. */
class EndurecimentoOidcTest {
    private val inicio = Instant.parse("2026-10-28T15:00:00Z")
    private val relogio = RelogioDeTeste(inicio)
    private val emissor = EmissorOidcFalso()

    private fun configuracao() = OidcSettings(
        issuer = emissor.issuer,
        clientId = emissor.clientId,
        clientSecret = null,
        redirectUri = "https://painel.exemplo.invalid/auth/callback",
        sessao = SegredosDaSessao.de(Base64.getEncoder().encodeToString(ByteArray(48) { (it * 5 + 1).toByte() }), null),
    )

    private fun descobertas() = emissor.requisicoes.count { it.endsWith("/.well-known/openid-configuration") }

    @Test
    fun consumoUnicoCheioDescartaOMaisAntigoEmVezDeRecusar() {
        val c = ConsumoUnico(relogio, limite = 3)
        val ate = inicio.plusSeconds(600)
        for (k in listOf("a", "b", "c")) assertTrue(c.consumir(k, ate), k)
        // cheio: o login legítimo seguinte é aceito (o mais antigo sai), não recusado por 10 minutos
        assertTrue(c.consumir("d", ate))
        // os recentes continuam valendo uma vez só
        assertFalse(c.consumir("d", ate))
        assertFalse(c.consumir("c", ate))
        assertFalse(c.consumir("b", ate))
    }

    @Test
    fun consumoUnicoNuncaPassaDoLimite() {
        val c = ConsumoUnico(relogio, limite = 50)
        val ate = inicio.plusSeconds(600)
        repeat(500) { assertTrue(c.consumir("k$it", ate)) }
        assertTrue(c.tamanho() <= 50)
    }

    @Test
    fun consumoUnicoLimpaOsVencidosAntes() {
        val c = ConsumoUnico(relogio, limite = 2)
        assertTrue(c.consumir("a", inicio.plusSeconds(10)))
        assertTrue(c.consumir("b", inicio.plusSeconds(600)))
        relogio.avancar(Duration.ofSeconds(30))
        assertTrue(c.consumir("c", inicio.plusSeconds(600)))
        assertFalse(c.consumir("b", inicio.plusSeconds(600)), "o vencido saiu, o vigente ficou")
    }

    @Test
    fun falhaNaDescobertaFicaEm30SegundosSemNovaRequisicao() = runBlocking {
        emissor.statusDaDescoberta = HttpStatusCode.InternalServerError
        val p = ProvedorOidc(configuracao(), emissor.cliente(), relogio)
        repeat(5) { assertEquals("DESCOBERTA_FALHOU", assertFailsWith<FalhaNoProvedor> { p.descoberta() }.codigo) }
        assertEquals(1, descobertas(), "as 4 chamadas seguintes saem do cache negativo")
        relogio.avancar(Duration.ofSeconds(31))
        assertFailsWith<FalhaNoProvedor> { p.descoberta() }
        assertEquals(2, descobertas())
        // o provedor volta: a próxima tentativa depois da janela dá certo
        emissor.statusDaDescoberta = HttpStatusCode.OK
        relogio.avancar(Duration.ofSeconds(31))
        assertTrue(p.descoberta().autorizacao.startsWith("https://"))
    }
}
