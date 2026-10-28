package br.sidia.painel.security

import br.sidia.painel.config.Settings
import br.sidia.painel.operacao.MascaraDeLog
import org.junit.jupiter.api.AfterEach
import java.time.Duration
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Rotação do segredo da sessão sem derrubar quem está logado. Segredos aqui são fictícios e gerados pelo teste. */
class SessaoRotacaoTest {
    private val b64 = Base64.getEncoder()
    private fun segredo(semente: Int): String = b64.encodeToString(ByteArray(48) { (it * 7 + semente).toByte() })

    private val antigo = segredo(1)
    private val novo = segredo(2)
    private val agora = Instant.parse("2026-10-30T12:00:00Z")

    @AfterEach
    fun limpar() = MascaraDeLog.limparRegistrosParaTeste()

    private fun sessao(atual: String, anterior: String? = null, relogio: Instant = agora) =
        SessaoAssinada(SegredosDaSessao.de(atual, anterior), Duration.ofHours(8)) { relogio }

    @Test
    fun sessaoNovaUsaSegredoAtual() {
        val cookie = sessao(novo, antigo).emitir("ana.silva")
        // quem só conhece o segredo atual lê; quem só conhece o antigo não lê
        assertEquals("ana.silva", sessao(novo).ler(cookie)?.userId)
        assertNull(sessao(antigo).ler(cookie))
        assertFalse(sessao(novo, antigo).ler(cookie)!!.usouSegredoAnterior)
    }

    @Test
    fun sessaoComSegredoAnteriorAindaValida() {
        val cookieDeAntes = sessao(antigo).emitir("ana.silva")
        // depois da rotação (atual = novo, anterior = antigo) a mesma sessão continua valendo
        val leitura = sessao(novo, antigo).ler(cookieDeAntes)
        assertEquals("ana.silva", leitura?.userId)
        assertTrue(leitura!!.usouSegredoAnterior)
        // sem o anterior (depois de remover a variável), ela cai
        assertNull(sessao(novo).ler(cookieDeAntes))
    }

    @Test
    fun sessaoLidaComAnteriorEhRegravadaComAtual() {
        val cookieDeAntes = sessao(antigo).emitir("ana.silva")
        val s = sessao(novo, antigo)
        val leitura = s.ler(cookieDeAntes)!!
        val regravado = s.regravarSeNecessario(leitura)
        assertTrue(regravado != null && regravado != cookieDeAntes)
        // o cookie regravado vale só com o segredo atual
        assertEquals("ana.silva", sessao(novo).ler(regravado!!)?.userId)
        // lida com o segredo atual não precisa regravar
        assertNull(s.regravarSeNecessario(s.ler(regravado)!!))
    }

    @Test
    fun regravacaoMantemOFimOriginalDaSessao() {
        val cookieDeAntes = sessao(antigo, relogio = agora).emitir("ana.silva") // vence 20:00Z
        val maisTarde = agora.plus(Duration.ofHours(5))
        val s = sessao(novo, antigo, relogio = maisTarde)
        val regravado = s.regravarSeNecessario(s.ler(cookieDeAntes)!!)!!
        // 8 h depois da emissão original, não da regravação: a rotação não estende a sessão
        assertNull(sessao(novo, relogio = agora.plus(Duration.ofHours(8)).plusSeconds(1)).ler(regravado))
        assertEquals("ana.silva", sessao(novo, relogio = agora.plus(Duration.ofHours(7))).ler(regravado)?.userId)
    }

    @Test
    fun semAnteriorComoAntes() {
        val s = sessao(novo)
        val cookie = s.emitir("ana.silva")
        assertEquals("ana.silva", s.ler(cookie)?.userId)
        assertNull(s.ler(cookie.dropLast(2) + "xx"), "assinatura adulterada")
        assertNull(s.ler("lixo"))
        assertNull(s.ler(""))
        // expirada
        assertNull(sessao(novo, relogio = agora.plus(Duration.ofHours(8)).plusSeconds(1)).ler(cookie))
    }

    @Test
    fun cookieNaoRevelaOSegredo() {
        val cookie = sessao(novo, antigo).emitir("ana.silva")
        assertFalse(novo in cookie || antigo in cookie)
    }

    @Test
    fun anteriorIgualAoAtualFalha() {
        val e = assertFailsWith<IllegalArgumentException> { SegredosDaSessao.de(novo, novo) }
        assertTrue("SESSION_SECRET_ANTERIOR" in e.message.orEmpty() && novo !in e.message.orEmpty())
    }

    @Test
    fun anteriorCurtoFalha() {
        val curto = b64.encodeToString(ByteArray(16) { 1 })
        val e = assertFailsWith<IllegalArgumentException> { SegredosDaSessao.de(novo, curto) }
        assertTrue("SESSION_SECRET_ANTERIOR" in e.message.orEmpty() && curto !in e.message.orEmpty())
        assertFailsWith<IllegalArgumentException> { SegredosDaSessao.de(curto, null) }
        assertFailsWith<IllegalArgumentException> { SegredosDaSessao.de("isto não é base64!!", null) }
    }

    @Test
    fun segredoAnteriorRegistradoNaMascara() {
        val settings = Settings.fromEnv(mapOf("SESSION_SECRET" to novo, "SESSION_SECRET_ANTERIOR" to antigo))
        MascaraDeLog.registrarSegredos(settings.valoresSecretos())
        val texto = MascaraDeLog.mascarar("erro com $novo e com $antigo no meio")
        assertFalse(novo in texto || antigo in texto, texto)
        assertNotEquals(texto, "erro com $novo e com $antigo no meio")
    }

    @Test
    fun settingsLeOsDoisSegredosESemEles() {
        val sem = Settings.fromEnv(emptyMap())
        assertNull(sem.operacao.sessao)
        assertTrue(sem.valoresSecretos().isEmpty())
        val com = Settings.fromEnv(mapOf("SESSION_SECRET" to novo, "SESSION_SECRET_ANTERIOR" to antigo)).operacao.sessao!!
        assertTrue(com.anterior != null)
        // o toString nunca mostra o segredo
        assertFalse(novo in com.toString() || antigo in com.toString())
    }

    @Test
    fun anteriorSemAtualFalhaCitandoSoOsNomes() {
        val e = assertFailsWith<IllegalArgumentException> { Settings.fromEnv(mapOf("SESSION_SECRET_ANTERIOR" to antigo)) }
        assertTrue("SESSION_SECRET" in e.message.orEmpty() && antigo !in e.message.orEmpty())
    }
}
