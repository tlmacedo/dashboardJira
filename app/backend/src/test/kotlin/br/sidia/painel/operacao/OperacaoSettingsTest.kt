package br.sidia.painel.operacao

import br.sidia.painel.config.Settings
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OperacaoSettingsTest {
    @Test
    fun padroesSemNenhumaVariavel() {
        val o = Settings.fromEnv(emptyMap()).operacao
        assertNull(o.logFormato)
        assertNull(o.logDir)
        assertEquals(500, o.discoMinMb)
    }

    @Test
    fun lePastaEFormatoDeLog() {
        val o = Settings.fromEnv(mapOf("PAINEL_LOG_FORMATO" to " TEXTO ", "PAINEL_LOG_DIR" to "logs")).operacao
        assertEquals("TEXTO", o.logFormato)
        assertEquals(Path.of("logs"), o.logDir)
    }

    @Test
    fun alertasArquivoEValidadeDoPat() {
        val o = Settings.fromEnv(
            mapOf("PAINEL_ALERTAS_ARQUIVO" to "alertas/alertas.log", "SYNC_PAT_VALIDADE_SAMSUNG" to "2026-12-01"),
        ).operacao
        assertEquals(Path.of("alertas/alertas.log"), o.alertasArquivo)
        assertEquals(mapOf("samsung" to java.time.LocalDate.of(2026, 12, 1)), o.patValidade)
        assertNull(Settings.fromEnv(emptyMap()).operacao.alertasArquivo)
        assertEquals(emptyMap(), Settings.fromEnv(emptyMap()).operacao.patValidade)
    }

    @Test
    fun validadeDoPatInvalidaCitaSoONome() {
        val e = assertFailsWith<IllegalArgumentException> { Settings.fromEnv(mapOf("SYNC_PAT_VALIDADE_SIDIA" to "31/12/2026")) }
        assertTrue("SYNC_PAT_VALIDADE_SIDIA" in e.message.orEmpty())
        assertFalse("31/12/2026" in e.message.orEmpty())
    }

    @Test
    fun alertasYmlOpcionalEValido() {
        val arquivo = br.sidia.painel.TestPaths.repoDir.resolve("ops/prometheus/alertas.yml")
        val raiz = com.charleskorn.kaml.Yaml.default.parseToYamlNode(java.nio.file.Files.readString(arquivo)).let {
            it as com.charleskorn.kaml.YamlMap
        }
        val grupos = (raiz.get<com.charleskorn.kaml.YamlList>("groups")!!).items.map { it as com.charleskorn.kaml.YamlMap }
        val painel = grupos.single { (it.get<com.charleskorn.kaml.YamlScalar>("name")!!).content == "painel" }
        val regras = (painel.get<com.charleskorn.kaml.YamlList>("rules")!!).items.map { it as com.charleskorn.kaml.YamlMap }
        assertTrue(regras.isNotEmpty())
        assertTrue(regras.all { (it.get<com.charleskorn.kaml.YamlScalar>("expr")!!).content.isNotBlank() })
        assertTrue(regras.map { (it.get<com.charleskorn.kaml.YamlScalar>("alert")!!).content }.containsAll(listOf("PainelForaDoAr")))
        // Os códigos citados nas regras existem, e juntos cobrem os 14.
        val citados = regras.mapNotNull { Regex("codigo=~\"([A-Z_|]+)\"").find((it.get<com.charleskorn.kaml.YamlScalar>("expr")!!).content)?.groupValues?.get(1) }
            .flatMap { it.split('|') }.toSet()
        assertEquals(CodigoAlerta.entries.map { it.name }.toSet(), citados)
    }

    @Test
    fun discoMinForaDaFaixaFalhaCitandoSoONome() {
        for (valor in listOf("49", "1000001", "abc")) {
            val e = assertFailsWith<IllegalArgumentException> { Settings.fromEnv(mapOf("PAINEL_DISCO_MIN_MB" to valor)) }
            assertTrue("PAINEL_DISCO_MIN_MB" in e.message.orEmpty())
            assertFalse(valor in e.message.orEmpty().replace("1000000", "").replace("50", ""), e.message)
        }
        assertEquals(50, Settings.fromEnv(mapOf("PAINEL_DISCO_MIN_MB" to "50")).operacao.discoMinMb)
    }
}
