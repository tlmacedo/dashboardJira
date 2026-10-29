package br.sidia.painel.config

import br.sidia.painel.security.FaixaCidr
import java.nio.file.Path
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Plano 13, Tarefa 4: produção que sobe insegura é recusada ANTES de abrir o banco. Cada guarda devolve um código estável
 * (nunca o valor). Tudo com valores fictícios e hosts `.invalid`.
 */
class GuardasDeProducaoTest {
    private val tmp: Path = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath()

    private fun segredo(bytes: Int) = Base64.getEncoder().encodeToString(ByteArray(bytes) { (it * 11 + 5).toByte() })

    private fun envValido(): Map<String, String> = mapOf(
        "OIDC_ISSUER" to "https://sso.exemplo.invalid",
        "OIDC_REDIRECT_URI" to "https://painel.exemplo.invalid/auth/callback",
        "DASHBOARD_ORIGEM_PUBLICA" to "https://painel.exemplo.invalid",
        "SESSION_SECRET" to segredo(48),
    )

    private fun settingsValidas() = Settings(
        dashboardDevMode = false,
        host = "127.0.0.1",
        databasePath = tmp.resolve("dados/painel.db"),
        configDir = tmp.resolve("config"),
        staticDir = tmp.resolve("frontend-dist"),
        proxyConfiavel = listOf(FaixaCidr.parse("127.0.0.1/32")),
    )

    private fun codigos(s: Settings = settingsValidas(), env: Map<String, String> = envValido()): List<String> =
        try {
            GuardasDeProducao.verificar(s, env)
            emptyList()
        } catch (e: ViolacaoDeProducao) {
            e.codigos
        }

    @Test
    fun producaoValidaSobe() {
        assertEquals(emptyList(), codigos())
    }

    @Test
    fun producaoComDemoNaoSobe() {
        assertEquals(listOf("DEMO_EM_PRODUCAO"), codigos(settingsValidas().copy(dashboardDemoData = true)))
    }

    @Test
    fun issuerHttpNaoSobe() {
        assertEquals(listOf("OIDC_ISSUER_SEM_HTTPS"), codigos(env = envValido() + ("OIDC_ISSUER" to "http://sso.exemplo.invalid")))
        assertEquals(listOf("OIDC_ISSUER_SEM_HTTPS"), codigos(env = envValido() - "OIDC_ISSUER"))
    }

    @Test
    fun redirectUriHttpNaoSobe() {
        val env = envValido() + ("OIDC_REDIRECT_URI" to "http://painel.exemplo.invalid/auth/callback")
        // http também está fora da origem pública https: as duas violações aparecem
        assertEquals(listOf("REDIRECT_URI_SEM_HTTPS", "REDIRECT_URI_FORA_DA_ORIGEM_PUBLICA"), codigos(env = env))
    }

    @Test
    fun origemPublicaAusenteNaoSobe() {
        assertEquals(listOf("ORIGEM_PUBLICA_AUSENTE"), codigos(env = envValido() - "DASHBOARD_ORIGEM_PUBLICA"))
        assertEquals(
            listOf("ORIGEM_PUBLICA_AUSENTE"),
            codigos(env = envValido() + ("DASHBOARD_ORIGEM_PUBLICA" to "http://painel.exemplo.invalid")),
        )
    }

    @Test
    fun redirectUriForaDaOrigemPublicaNaoSobe() {
        val env = envValido() + ("OIDC_REDIRECT_URI" to "https://outro.exemplo.invalid/auth/callback")
        assertEquals(listOf("REDIRECT_URI_FORA_DA_ORIGEM_PUBLICA"), codigos(env = env))
        // mesma origem com maiúsculas e porta padrão explícita não é violação
        assertEquals(
            emptyList(),
            codigos(env = envValido() + ("OIDC_REDIRECT_URI" to "https://PAINEL.exemplo.invalid/auth/callback")),
        )
    }

    @Test
    fun segredoCurtoNaoSobe() {
        assertEquals(listOf("SESSION_SECRET_CURTO"), codigos(env = envValido() + ("SESSION_SECRET" to segredo(31))))
        assertEquals(emptyList(), codigos(env = envValido() + ("SESSION_SECRET" to segredo(32))))
        assertEquals(listOf("SESSION_SECRET_CURTO"), codigos(env = envValido() - "SESSION_SECRET"))
    }

    @Test
    fun segredoBase64InvalidoNaoSobe() {
        assertEquals(listOf("SESSION_SECRET_CURTO"), codigos(env = envValido() + ("SESSION_SECRET" to "isto não é base64 !!!")))
    }

    @Test
    fun proxyAusenteNaoSobe() {
        assertEquals(listOf("PROXY_NAO_CONFIGURADO"), codigos(settingsValidas().copy(proxyConfiavel = emptyList())))
    }

    @Test
    fun escutaAbertaNaoSobe() {
        for (aberto in listOf("0.0.0.0", "::", "[::]", "", "  ")) {
            assertEquals(listOf("ESCUTA_ABERTA_A_TODAS"), codigos(settingsValidas().copy(host = aberto)), "'$aberto'")
        }
        // um endereço interno específico é permitido (proxy em outra máquina, com firewall)
        assertEquals(emptyList(), codigos(settingsValidas().copy(host = "10.0.0.7")))
    }

    @Test
    fun caminhoRelativoNaoSobe() {
        assertEquals(listOf("CAMINHO_RELATIVO"), codigos(settingsValidas().copy(databasePath = Path.of("data/painel.db"))))
        assertEquals(listOf("CAMINHO_RELATIVO"), codigos(settingsValidas().copy(configDir = Path.of("config"))))
        assertEquals(listOf("CAMINHO_RELATIVO"), codigos(settingsValidas().copy(staticDir = Path.of("frontend/dist"))))
    }

    @Test
    fun listaTodasAsViolacoes() {
        val s = settingsValidas().copy(host = "0.0.0.0", dashboardDemoData = true, proxyConfiavel = emptyList())
        val e = assertFailsWith<ViolacaoDeProducao> { GuardasDeProducao.verificar(s, envValido() - "SESSION_SECRET") }
        // ordem estável, a mesma da documentação
        assertEquals(listOf("DEMO_EM_PRODUCAO", "SESSION_SECRET_CURTO", "PROXY_NAO_CONFIGURADO", "ESCUTA_ABERTA_A_TODAS"), e.codigos)
    }

    @Test
    fun mensagemSemValor() {
        val segredoLongo = segredo(20)
        val env = envValido() + ("SESSION_SECRET" to segredoLongo) + ("OIDC_ISSUER" to "http://sso.segredo-interno.invalid")
        val s = settingsValidas().copy(databasePath = Path.of("pasta-secreta/painel.db"))
        val e = assertFailsWith<ViolacaoDeProducao> { GuardasDeProducao.verificar(s, env) }
        val msg = e.message!!
        assertTrue("OIDC_ISSUER_SEM_HTTPS" in msg && "SESSION_SECRET_CURTO" in msg && "CAMINHO_RELATIVO" in msg, msg)
        for (proibido in listOf(segredoLongo, "segredo-interno", "pasta-secreta")) assertFalse(proibido in msg, "vazou $proibido")
    }

    @Test
    fun estrategiaDeDominioSemDominioPermitidoNaoSobe() {
        for (estrategia in listOf("parte_local_do_email", "CONTA_WINDOWS")) {
            val base = envValido() + ("OIDC_USERNAME_ESTRATEGIA" to estrategia)
            assertEquals(listOf("DOMINIO_DO_USERNAME_AUSENTE"), codigos(env = base), estrategia)
            assertEquals(listOf("DOMINIO_DO_USERNAME_AUSENTE"), codigos(env = base + ("OIDC_DOMINIOS_PERMITIDOS" to " , ")), estrategia)
            assertEquals(emptyList(), codigos(env = base + ("OIDC_DOMINIOS_PERMITIDOS" to "empresa.invalid")), estrategia)
        }
        // claim_direto (ou ausente) não exige domínio
        assertEquals(emptyList(), codigos(env = envValido() + ("OIDC_USERNAME_ESTRATEGIA" to "claim_direto")))
    }

    @Test
    fun aceiteDoDpoSemReferenciaNaoSobe() {
        for (aceite in listOf("true", "TRUE", " true ")) {
            assertEquals(
                listOf("TRAVA_DPO_NAO_DECLARADA"),
                codigos(env = envValido() + ("DASHBOARD_DPO_ACEITE" to aceite)),
                aceite,
            )
            assertEquals(
                listOf("TRAVA_DPO_NAO_DECLARADA"),
                codigos(env = envValido() + mapOf("DASHBOARD_DPO_ACEITE" to aceite, "DASHBOARD_DPO_ACEITE_REF" to "  ")),
            )
        }
    }

    @Test
    fun travaDpoFechadaNaoImpedeASubida() {
        // aceite ausente, vazio ou false: a trava fica FECHADA (fail-closed) e a subida é permitida
        for (env in listOf(envValido(), envValido() + ("DASHBOARD_DPO_ACEITE" to ""), envValido() + ("DASHBOARD_DPO_ACEITE" to "false"))) {
            assertEquals(emptyList(), codigos(env = env))
        }
        // aceite com a referência do parecer também passa
        assertEquals(
            emptyList(),
            codigos(env = envValido() + mapOf("DASHBOARD_DPO_ACEITE" to "true", "DASHBOARD_DPO_ACEITE_REF" to "parecer-2026/11")),
        )
    }

    @Test
    fun devModeIgnoraGuardas() {
        val dev = Settings(dashboardDevMode = true, dashboardDemoData = true, host = "0.0.0.0")
        GuardasDeProducao.verificar(dev, emptyMap())
    }

    // ---------------------------------------------------------------- Settings: escuta e porta

    @Test
    fun escutaPadraoEhLoopback() {
        assertEquals("127.0.0.1", Settings().host)
        assertEquals(8080, Settings().porta)
        assertEquals(8080, Settings.fromEnv(emptyMap()).porta)
    }

    @Test
    fun portaVemDeDashboardPort() {
        assertEquals(18080, Settings.fromEnv(mapOf("DASHBOARD_PORT" to " 18080 ")).porta)
        assertEquals(8080, Settings.fromEnv(mapOf("DASHBOARD_PORT" to "  ")).porta)
    }

    @Test
    fun portaForaDaFaixaNaoSobe() {
        for (ruim in listOf("0", "70000", "-1", "abc", "80.5")) {
            val e = assertFailsWith<IllegalArgumentException>(ruim) { Settings.fromEnv(mapOf("DASHBOARD_PORT" to ruim)) }
            assertTrue(e.message!!.startsWith("DASHBOARD_PORT "), e.message)
            assertFalse(ruim in e.message!!)
        }
    }
}
