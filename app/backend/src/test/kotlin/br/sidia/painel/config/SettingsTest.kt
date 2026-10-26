package br.sidia.painel.config

import br.sidia.painel.jira.JiraAuthKind
import java.nio.file.Path
import br.sidia.painel.sync.SyncSettings
import java.time.LocalTime
import java.time.ZoneId
import kotlin.time.Duration.Companion.minutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsTest {
    /** A tarefa `test` do Gradle fixa a flag em `false`: a suíte não herda a flag ligada no terminal. */
    @Test
    fun suiteNaoHerdaAFlagDoAmbiente() {
        assertEquals("false", System.getenv("JIRA_LIVE_ENABLED"))
    }

    @Test
    fun padroesSaoSeguros() {
        val s = Settings.fromEnv(emptyMap())
        assertFalse(s.jiraLiveEnabled)
        assertTrue(s.dashboardDevMode)
        assertEquals(24, s.retentionMonths)
        assertEquals(ZoneId.of("America/Manaus"), s.zone)
    }

    @Test
    fun leVariaveis() {
        val s = Settings.fromEnv(
            mapOf(
                "JIRA_LIVE_ENABLED" to "true",
                "DASHBOARD_DEV_MODE" to "false",
                "RETENTION_MONTHS" to "12",
            ),
        )
        assertTrue(s.jiraLiveEnabled)
        assertFalse(s.dashboardDevMode)
        assertEquals(12, s.retentionMonths)
    }

    @Test
    fun diretorioEstaticoTemPadraoELeVariavel() {
        assertEquals(Path.of("frontend/dist"), Settings.fromEnv(emptyMap()).staticDir)
        assertEquals(
            Path.of("/app/static"),
            Settings.fromEnv(mapOf("PAINEL_STATIC_DIR" to " /app/static ")).staticDir,
        )
    }

    // Revisado: variável presente mas vazia (comum no docker-compose) agora significa "usar o padrão".
    @Test
    fun diretorioEstaticoVazioUsaPadrao() {
        assertEquals(Path.of("frontend/dist"), Settings.fromEnv(mapOf("PAINEL_STATIC_DIR" to "  ")).staticDir)
    }

    @Test
    fun vazioUsaPadraoParaTodos() {
        val s = Settings.fromEnv(
            mapOf(
                "JIRA_LIVE_ENABLED" to "",
                "DASHBOARD_DEV_MODE" to " ",
                "RETENTION_MONTHS" to "  ",
                "PAINEL_TZ" to "",
                "PAINEL_CONFIG_DIR" to "",
                "DATABASE_URL" to "",
                "DATABASE_USER" to "",
                "DATABASE_PASSWORD" to "",
                "PAINEL_STATIC_DIR" to "",
            ),
        )
        assertEquals(Settings(), s)
    }

    @Test
    fun poolTemPadraoDezELeVariavel() {
        assertEquals(10, Settings.fromEnv(emptyMap()).databasePoolSize)
        assertEquals(10, Settings.fromEnv(mapOf("DATABASE_POOL_SIZE" to "  ")).databasePoolSize)
        assertEquals(25, Settings.fromEnv(mapOf("DATABASE_POOL_SIZE" to " 25 ")).databasePoolSize)
        assertEquals(1, Settings.fromEnv(mapOf("DATABASE_POOL_SIZE" to "1")).databasePoolSize)
        assertEquals(100, Settings.fromEnv(mapOf("DATABASE_POOL_SIZE" to "100")).databasePoolSize)
    }

    @Test
    fun poolInvalidoCitaAVariavel() {
        listOf("0", "101", "-3", "abc", "2.5").forEach { v ->
            val e = assertFailsWith<IllegalArgumentException>(v) {
                Settings.fromEnv(mapOf("DATABASE_POOL_SIZE" to v))
            }
            assertTrue("DATABASE_POOL_SIZE" in e.message.orEmpty(), e.message)
        }
    }

    @Test
    fun lePainelBanco() {
        val s = Settings.fromEnv(mapOf("DATABASE_USER" to "painel", "DATABASE_PASSWORD" to "segredo"))
        assertEquals("painel", s.databaseUser)
        assertEquals("segredo", s.databasePassword)
    }

    @Test
    fun toStringNaoVazaSegredos() {
        val s = Settings.fromEnv(
            mapOf(
                "DATABASE_URL" to "jdbc:postgresql://usuarioX:senhaUrlY@h/db",
                "DATABASE_PASSWORD" to "senhaBancoZ",
                "JIRA_SAMSUNG_URL" to "https://x.exemplo.com",
                "JIRA_SAMSUNG_TOKEN" to "tokenSecretoW",
            ),
        )
        val texto = s.toString()
        listOf("senhaBancoZ", "tokenSecretoW", "usuarioX", "senhaUrlY").forEach {
            assertFalse(texto.contains(it), "vazou $it")
        }
        assertTrue(texto.contains("jdbc:postgresql://***@h/db"))
        assertFalse(s.jiraInstances.getValue("samsung").toString().contains("tokenSecretoW"))
    }

    @Test
    fun toStringMascaraSenhaNaQuery() {
        val a = Settings(databaseUrl = "jdbc:postgresql://h/db?user=u&password=abc123&ssl=true").toString()
        val b = Settings(databaseUrl = "jdbc:postgresql://h/db?password=abc123").toString()
        assertFalse(a.contains("abc123"))
        assertFalse(b.contains("abc123"))
        assertTrue(a.contains("password=***&ssl=true"))
        assertTrue(b.contains("?password=***"))
    }

    @Test
    fun toStringMascaraSenhaComCaracteresEspeciais() {
        listOf(
            "jdbc:postgresql://u:p/x@h/db",
            "jdbc:postgresql://u:p?x@h/db",
            "jdbc:postgresql://u:p@w@h/db",
            "jdbc:postgresql://u:p:q@h/db",
        ).forEach { url ->
            val texto = Settings(databaseUrl = url).toString()
            assertTrue(texto.contains("jdbc:postgresql://***@h/db"), "não mascarou $url: $texto")
            assertFalse(texto.contains("u:p"), "vazou em $url")
        }
    }

    @Test
    fun toStringMascaraParametrosSecretosNaQuery() {
        listOf(
            "jdbc:postgresql://h/db?PASSWORD=sg1",
            "jdbc:postgresql://h/db?user=u;password=sg1",
            "jdbc:postgresql://h/db?sslpassword=sg1&ssl=true",
            "jdbc:postgresql://h/db?pwd=sg1",
            "jdbc:postgresql://h/db?a=1&pass=sg1",
            "jdbc:postgresql://h/db?passwd=sg1",
            "jdbc:postgresql://h/db?secret=sg1",
            "jdbc:postgresql://h/db?Token=sg1",
        ).forEach { url ->
            val texto = Settings(databaseUrl = url).toString()
            assertFalse(texto.contains("sg1"), "vazou em $url: $texto")
            assertTrue(texto.contains("=***"), "sem máscara em $url")
        }
        assertTrue(Settings(databaseUrl = "jdbc:postgresql://h/db?user=u&ssl=true").toString().contains("user=u&ssl=true"))
    }

    @Test
    fun toStringDaInstanciaMascaraCredenciaisNaUrl() {
        val i = JiraInstanceSettings("https://usuarioX:tokenY@host.exemplo.com", JiraAuthKind.BEARER, null, "t")
        val texto = i.toString()
        assertFalse(texto.contains("usuarioX"))
        assertFalse(texto.contains("tokenY"))
        assertTrue(texto.contains("https://***@host.exemplo.com"))
    }

    @Test
    fun tokenRecebeTrim() {
        val s = Settings.fromEnv(
            mapOf("JIRA_SAMSUNG_URL" to " https://s.exemplo.com ", "JIRA_SAMSUNG_TOKEN" to "  tok \n"),
        )
        assertEquals("tok", s.jiraInstances.getValue("samsung").token)
        assertEquals("https://s.exemplo.com", s.jiraInstances.getValue("samsung").baseUrl)
    }

    @Test
    fun booleanoInvalidoFalhaCitandoAVariavel() {
        val e = assertFailsWith<IllegalArgumentException> {
            Settings.fromEnv(mapOf("JIRA_LIVE_ENABLED" to "yes", "DATABASE_PASSWORD" to "segredoK"))
        }
        assertTrue(e.message!!.contains("JIRA_LIVE_ENABLED"))
        assertFalse(e.message!!.contains("segredoK"))
    }

    @Test
    fun instanciaSamsungCompleta() {
        val s = Settings.fromEnv(
            mapOf("JIRA_SAMSUNG_URL" to "https://s.exemplo.com", "JIRA_SAMSUNG_TOKEN" to "t"),
        )
        assertEquals(setOf("samsung"), s.jiraInstances.keys)
        val i = s.jiraInstances.getValue("samsung")
        assertEquals("https://s.exemplo.com", i.baseUrl)
        assertEquals(JiraAuthKind.BEARER, i.auth)
        assertEquals(null, i.username)
        assertEquals("t", i.token)
    }

    @Test
    fun instanciaSidiaCompleta() {
        val s = Settings.fromEnv(
            mapOf("JIRA_SIDIA_URL" to "https://d.exemplo.com", "JIRA_SIDIA_USER" to "u", "JIRA_SIDIA_TOKEN" to "t"),
        )
        val i = s.jiraInstances.getValue("sidia")
        assertEquals(JiraAuthKind.BASIC, i.auth)
        assertEquals("u", i.username)
        assertEquals("t", i.token)
    }

    @Test
    fun instanciaIncompletaFalha() {
        val e = assertFailsWith<IllegalArgumentException> {
            Settings.fromEnv(mapOf("JIRA_SIDIA_URL" to "https://valorSecretoQ.exemplo.com", "JIRA_SIDIA_USER" to " "))
        }
        val msg = e.message!!
        assertTrue(msg.contains("JIRA_SIDIA_USER"))
        assertTrue(msg.contains("JIRA_SIDIA_TOKEN"))
        assertFalse(msg.contains("valorSecretoQ"))
    }

    @Test
    fun semInstanciasMapaVazio() {
        assertTrue(Settings.fromEnv(emptyMap()).jiraInstances.isEmpty())
        assertTrue(Settings.fromEnv(mapOf("JIRA_SAMSUNG_URL" to "")).jiraInstances.isEmpty())
    }

    @Test
    fun valorInvalidoFalha() {
        val e = assertFailsWith<IllegalArgumentException> {
            Settings.fromEnv(mapOf("RETENTION_MONTHS" to "abc"))
        }
        assertTrue(e.message!!.contains("RETENTION_MONTHS"))
    }

    @Test
    fun syncLeTodasAsVariaveis() {
        val s = Settings.fromEnv(
            mapOf(
                "SYNC_INSTANCIAS" to " samsung , sidia ",
                "SYNC_INTERVALO_MIN" to "15",
                "SYNC_HORA_RECONCILIACAO" to "02:30",
                "SYNC_HORA_RETENCAO" to "05:15",
                "SYNC_MAX_ITENS" to "1000",
                "SYNC_MAX_REQUISICOES" to "100",
                "SYNC_SOBREPOSICAO_MIN" to "10",
                "SYNC_FUSO_SERVIDOR" to "America/Sao_Paulo",
                "SYNC_TEMPO_MAX_MIN" to "30",
            ),
        ).sync
        assertEquals(listOf("samsung", "sidia"), s.instancias)
        assertEquals(15.minutes, s.intervalo)
        assertEquals(LocalTime.of(2, 30), s.horaReconciliacao)
        assertEquals(LocalTime.of(5, 15), s.horaRetencao)
        assertEquals(1000, s.maxItens)
        assertEquals(100, s.maxRequisicoes)
        assertEquals(10.minutes, s.sobreposicao)
        assertEquals(ZoneId.of("America/Sao_Paulo"), s.fusoDoServidor)
        assertEquals(30.minutes, s.tempoMaximo)
        // vazio = padrão
        assertEquals(SyncSettings(), Settings.fromEnv(mapOf("SYNC_INTERVALO_MIN" to " ")).sync)
    }

    @Test
    fun limitesForaDoIntervaloFalham() {
        val casos = listOf(
            "SYNC_INTERVALO_MIN" to "4", "SYNC_INTERVALO_MIN" to "1441",
            "SYNC_MAX_ITENS" to "200001", "SYNC_MAX_ITENS" to "0",
            "SYNC_MAX_REQUISICOES" to "20001", "SYNC_SOBREPOSICAO_MIN" to "61",
            "SYNC_TEMPO_MAX_MIN" to "241", "SYNC_TEMPO_MAX_MIN" to "abc",
        )
        for ((nome, valor) in casos) {
            val e = assertFailsWith<IllegalArgumentException> { Settings.fromEnv(mapOf(nome to valor)) }
            assertTrue(e.message!!.contains(nome), "mensagem deve citar $nome")
            // a mensagem cita só o nome da variável, nunca o valor informado
            assertFalse(e.message!!.contains("'$valor'"), "mensagem não deve ecoar o valor de $nome")
        }
    }

    @Test
    fun instanciaDesconhecidaFalha() {
        val e = assertFailsWith<IllegalArgumentException> { Settings.fromEnv(mapOf("SYNC_INSTANCIAS" to "samsung,xpto")) }
        assertTrue(e.message!!.contains("SYNC_INSTANCIAS"))
    }

    @Test
    fun horaInvalidaFalha() {
        val e = assertFailsWith<IllegalArgumentException> { Settings.fromEnv(mapOf("SYNC_HORA_RECONCILIACAO" to "25:00")) }
        assertTrue(e.message!!.contains("SYNC_HORA_RECONCILIACAO"))
        assertFailsWith<IllegalArgumentException> { Settings.fromEnv(mapOf("SYNC_HORA_RETENCAO" to "3h")) }
    }

    @Test
    fun demoPadraoEhFalse() {
        assertFalse(Settings.fromEnv(emptyMap()).dashboardDemoData)
        assertFalse(Settings.fromEnv(mapOf("DASHBOARD_DEMO_DATA" to "  ")).dashboardDemoData)
        assertTrue(Settings.fromEnv(mapOf("DASHBOARD_DEMO_DATA" to "true")).dashboardDemoData)
    }

    @Test
    fun demoEmProducaoDerrubaSubida() {
        val e = assertFailsWith<IllegalArgumentException> {
            Settings.fromEnv(mapOf("DASHBOARD_DEV_MODE" to "false", "DASHBOARD_DEMO_DATA" to "true"))
        }
        assertEquals("DASHBOARD_DEMO_DATA exige DASHBOARD_DEV_MODE=true", e.message)
    }

    @Test
    fun demoComFlagLigadaDerrubaSubida() {
        val e = assertFailsWith<IllegalArgumentException> {
            Settings.fromEnv(mapOf("JIRA_LIVE_ENABLED" to "true", "DASHBOARD_DEMO_DATA" to "true"))
        }
        assertEquals("DASHBOARD_DEMO_DATA exige JIRA_LIVE_ENABLED=false", e.message)
        // Demonstração desligada não impede a flag ligada nem a produção.
        assertTrue(Settings.fromEnv(mapOf("JIRA_LIVE_ENABLED" to "true", "DASHBOARD_DEMO_DATA" to "false")).jiraLiveEnabled)
        assertFalse(Settings.fromEnv(mapOf("DASHBOARD_DEV_MODE" to "false")).dashboardDemoData)
    }

    @Test
    fun demoValorInvalidoFalha() {
        val e = assertFailsWith<IllegalArgumentException> { Settings.fromEnv(mapOf("DASHBOARD_DEMO_DATA" to "sim")) }
        assertTrue(e.message!!.contains("DASHBOARD_DEMO_DATA"))
    }
}
