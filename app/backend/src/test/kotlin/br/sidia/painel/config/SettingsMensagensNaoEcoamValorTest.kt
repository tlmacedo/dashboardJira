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
    fun operacaoSyncERetencaoInvalidasCitamSoONome() {
        for (nome in listOf(
            "PAINEL_DISCO_MIN_MB", "BACKUP_RETENCAO_COPIAS", "PAINEL_METRICAS_PORTA", "SYNC_INTERVALO_MIN", "SYNC_MAX_ITENS",
            "SYNC_MAX_REQUISICOES", "SYNC_SOBREPOSICAO_MIN", "SYNC_TEMPO_MAX_MIN", "RETENTION_HISTORICO_MONTHS",
            "RETENTION_ACCESS_LOG_MONTHS", "RETENTION_DECISOES_MONTHS", "RETENTION_EXECUCOES_MONTHS", "ACCESS_LOG_RETENTION_MONTHS",
            "BACKUP_HORA", "SYNC_HORA_RECONCILIACAO", "SYNC_HORA_RETENCAO", "SYNC_FUSO_SERVIDOR", "SYNC_INSTANCIAS",
        )) conferir(nome, marca)
        conferir("PAINEL_METRICAS_HOST", "$marca!")
        conferir("DASHBOARD_DPO_ACEITE", marca)
        for (nome in listOf("PAINEL_LOG_DIR", "BACKUP_DIR", "PAINEL_ALERTAS_ARQUIVO")) conferir(nome, "$marca\u0000x")
    }

    @Test
    fun oidcESegredoDeSessaoInvalidosCitamSoONome() {
        val base = mapOf(
            "OIDC_ISSUER" to "https://emissor.invalid", "OIDC_CLIENT_ID" to "cliente",
            "OIDC_REDIRECT_URI" to "https://painel.invalid/auth/callback",
            "SESSION_SECRET" to java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 7 }),
        )
        val casos = listOf(
            mapOf("SESSION_SECRET" to marca), mapOf("SESSION_SECRET_ANTERIOR" to marca),
            mapOf("OIDC_ISSUER" to "http://$marca.invalid"), mapOf("OIDC_REDIRECT_URI" to "https://$marca.invalid/x"),
            mapOf("OIDC_CLIENT_ID" to "$marca com espaço"), mapOf("OIDC_CLAIM_USERNAME" to "$marca com espaço"),
            mapOf("OIDC_CLIENT_SECRET" to "$marca\u0000"),
        )
        for (troca in casos) {
            val e = runCatching { br.sidia.painel.security.OidcSettings.fromEnv(base + troca, devMode = false) }.exceptionOrNull()
            // OIDC_CLIENT_SECRET aceita qualquer texto: sem erro não há o que vazar
            if (e == null) continue
            for (mensagem in cadeia(e)) assertFalse(marca in mensagem, "$troca: a mensagem (ou a causa) ecoa o valor lido: $mensagem")
        }
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
