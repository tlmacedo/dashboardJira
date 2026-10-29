package br.sidia.painel.config

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Auditoria final (M-3): erro de configuração cita só o NOME da variável, nunca o valor lido (nem na causa encadeada).
 * Nenhum desses campos é segredo hoje, mas a regra única evita que um campo secreto novo herde o vazamento.
 */
class SettingsMensagensNaoEcoamValorTest {
    private val marca = "NaoEcoar"

    private fun cadeia(e: Throwable): List<String> = generateSequence(e) { it.cause }.map { it.message.orEmpty() }.toList()

    private fun conferir(nome: String, valor: String) {
        val e = assertFailsWith<IllegalArgumentException>("$nome=$valor deveria ser recusado") {
            Settings.fromEnv(mapOf(nome to valor))
        }
        assertTrue(nome in e.message.orEmpty(), "a mensagem cita o nome $nome: ${e.message}")
        for (mensagem in cadeia(e)) {
            assertFalse(marca in mensagem, "$nome: a mensagem (ou a causa) ecoa o valor lido: $mensagem")
        }
    }

    @Test
    fun fusoInvalidoCitaSoONome() = conferir("PAINEL_TZ", "Marca/$marca")

    @Test
    fun tamanhoDoPoolInvalidoCitaSoONome() = conferir("DATABASE_POOL_SIZE", marca)

    @Test
    fun caminhosInvalidosCitamSoONome() {
        for (nome in listOf("PAINEL_CONFIG_DIR", "DATABASE_PATH", "PAINEL_STATIC_DIR")) conferir(nome, "$marca\u0000x")
    }

    @Test
    fun booleanosInvalidosCitamSoONome() {
        for (nome in listOf("JIRA_LIVE_ENABLED", "DASHBOARD_DEV_MODE", "DASHBOARD_DEMO_DATA")) conferir(nome, marca)
    }

    @Test
    fun demaisVariaveisInvalidasCitamSoONome() {
        conferir("DASHBOARD_PORT", marca)
        conferir("PAINEL_CACHE_SEG", marca)
        conferir("PAINEL_HOST", "$marca!")
        conferir("DASHBOARD_ORIGEM_PUBLICA", marca)
        conferir("DASHBOARD_PROXY_CONFIAVEL", marca)
        conferir("RETENTION_MONTHS", marca)
        conferir("SYNC_INTERVALO_MIN", marca)
    }
}
