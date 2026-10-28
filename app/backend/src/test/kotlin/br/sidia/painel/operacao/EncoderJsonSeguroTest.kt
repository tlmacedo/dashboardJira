package br.sidia.painel.operacao

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.LoggingEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EncoderJsonSeguroTest {
    private val contexto = LoggerContext()
    private val logger = contexto.getLogger("br.sidia.painel.teste")
    private val encoder = EncoderJsonSeguro().also { it.context = contexto; it.start() }

    @BeforeEach
    @AfterEach
    fun limpar() = MascaraDeLog.limparRegistrosParaTeste()

    private fun evento(msg: String, erro: Throwable? = null, mdc: Map<String, String> = emptyMap()): LoggingEvent =
        LoggingEvent("fqcn", logger, Level.INFO, msg, erro, null).also { it.mdcPropertyMap = mdc }

    private fun codificar(e: LoggingEvent): String = String(encoder.encode(e), Charsets.UTF_8)

    @Test
    fun umaLinhaJsonValidaPorEvento() {
        val saida = codificar(evento("olá, ação"))
        assertTrue(saida.endsWith("\n"))
        assertEquals(1, saida.trimEnd('\n').lines().size)
        val json = Json.parseToJsonElement(saida).jsonObject
        assertEquals("INFO", json.getValue("nivel").jsonPrimitive.content)
        assertEquals("br.sidia.painel.teste", json.getValue("logger").jsonPrimitive.content)
        assertEquals("olá, ação", json.getValue("msg").jsonPrimitive.content)
        assertTrue(json.getValue("ts").jsonPrimitive.content.endsWith("Z"))
        assertNull(json["requisicao"])
        assertNull(json["exc"])
    }

    @Test
    fun quebraDeLinhaNaoCriaEntrada() {
        val saida = codificar(evento("a\nINFO falso\r\"aspas\""))
        assertEquals(1, saida.trimEnd('\n').lines().size, saida)
        assertTrue("\\n" in saida)
        assertEquals("a\nINFO falso\r\"aspas\"", Json.parseToJsonElement(saida).jsonObject.getValue("msg").jsonPrimitive.content)
    }

    @Test
    fun excecaoComTokenSaiMascarada() {
        val saida = codificar(evento("falhou", IllegalStateException("resposta 401 com Bearer abcdefghijklmnop na URL")))
        assertFalse("abcdefghijklmnop" in saida)
        val exc = Json.parseToJsonElement(saida).jsonObject.getValue("exc").jsonObject
        assertEquals("java.lang.IllegalStateException", exc.getValue("classe").jsonPrimitive.content)
        assertTrue("Bearer ***" in exc.getValue("msg").jsonPrimitive.content)
    }

    @Test
    fun pilhaLimitadaA20() {
        fun fundo(n: Int): Throwable = if (n == 0) RuntimeException("x") else fundo(n - 1)
        val exc = Json.parseToJsonElement(codificar(evento("p", fundo(60)))).jsonObject.getValue("exc").jsonObject
        val pilha = exc.getValue("pilha").jsonArray
        assertEquals(20, pilha.size)
        assertTrue(pilha[0].jsonPrimitive.content.contains("EncoderJsonSeguroTest"), pilha[0].toString())
    }

    @Test
    fun mdcDaRequisicaoNoEvento() {
        val json: JsonObject = Json.parseToJsonElement(
            codificar(evento("m", mdc = mapOf("requisicao" to "abc-12345678", "outra" to "nao"))),
        ).jsonObject
        assertEquals("abc-12345678", json.getValue("requisicao").jsonPrimitive.content)
        assertNull(json["outra"])
    }

    @Test
    fun segredoRegistradoNaMensagemSaiMascarado() {
        MascaraDeLog.registrarSegredos(listOf("segredo-ficticio-123"))
        assertFalse("segredo-ficticio-123" in codificar(evento("usou segredo-ficticio-123 aqui")))
    }
}
