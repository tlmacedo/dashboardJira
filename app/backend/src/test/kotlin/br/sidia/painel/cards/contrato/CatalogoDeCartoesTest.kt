package br.sidia.painel.cards.contrato

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.TestPaths
import br.sidia.painel.config.Settings
import br.sidia.painel.module
import br.sidia.painel.security.AtribuicaoPapel
import br.sidia.painel.security.Authenticator
import br.sidia.painel.security.Pessoa
import br.sidia.painel.security.Principal
import br.sidia.painel.security.Role
import br.sidia.painel.security.RoleDirectory
import br.sidia.painel.security.Vigencia
import br.sidia.painel.security.principalFor
import br.sidia.painel.testContextComBanco
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.AfterAll
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Catálogo de cartões: ids únicos, texto em pt/en, classes válidas, filtro por papel e a rota auditada.
 * SQLite real em arquivo temporário; nenhuma rede, nenhum Jira.
 */
class CatalogoDeCartoesTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagarBanco() {
            banco.apagar()
        }

        private val pastaDeTextos = TestPaths.repoDir.resolve("app/frontend/src/i18n")

        /** Chaves e textos de um arquivo de tradução do frontend (JSON plano). */
        private fun traducoes(idioma: String): Map<String, String> =
            Json.parseToJsonElement(Files.readString(pastaDeTextos.resolve("$idioma.json"))).jsonObject
                .mapValues { (_, v) -> (v as JsonPrimitive).content }
    }

    private val zona = ZoneId.of("America/Manaus")
    private val relogio: Clock = Clock.fixed(Instant.parse("2026-10-25T16:00:00Z"), zona)
    private val hoje: LocalDate = LocalDate.now(relogio)
    private val producao = Settings(dashboardDevMode = false)

    private fun pessoa(id: String, papel: Role, squad: String? = null, ate: LocalDate? = null) =
        Pessoa(id, id, listOf(AtribuicaoPapel(papel, Vigencia(null, ate), squad)))

    private val diretorio = RoleDirectory(
        listOf(
            pessoa("ana.silva", Role.USUARIO, "Alfa"),
            pessoa("lider.alfa", Role.SQUAD_LEADER, "Alfa"),
            pessoa("coord.um", Role.COORDENADOR),
            pessoa("gerente.exemplo", Role.GERENTE),
            pessoa("ex.papel", Role.USUARIO, "Alfa", ate = LocalDate.of(2026, 10, 24)),
        ),
    )

    private lateinit var ctx: AppContext

    @BeforeTest
    fun preparar() {
        ctx = testContextComBanco(producao, banco, diretorio, relogio)
    }

    @AfterTest
    fun encerrar() {
        ctx.close()
    }

    private val autenticador = object : Authenticator {
        override suspend fun authenticate(call: ApplicationCall): Principal? =
            call.request.headers["X-Teste-Usuario"]?.let { principalFor(it, diretorio, hoje) }
    }

    private class Resp(val status: HttpStatusCode, val corpo: String)

    private fun rodar(bloco: suspend (suspend (String?, String) -> Resp) -> Unit) = testApplication {
        application { module(producao, ctx, autenticador) }
        val http: HttpClient = client
        bloco { usuario, caminho ->
            val r = http.get(caminho) { if (usuario != null) header("X-Teste-Usuario", usuario) }
            Resp(r.status, r.bodyAsText())
        }
    }

    private fun linhasDoAccessLog(): List<Triple<String, String, String>> =
        requireNotNull(ctx.dataSource).connection.use { c ->
            c.createStatement().executeQuery("SELECT user_id, route, filters FROM access_log ORDER BY id").use { rs ->
                buildList { while (rs.next()) add(Triple(rs.getString(1), rs.getString(2), rs.getString(3).orEmpty())) }
            }
        }

    /** Cartão sintético para provar regras do catálogo sem depender de qual cartão existe hoje. */
    private fun falso(id: String, grupo: GrupoDeCartao, vararg papeis: Role) = object : CartaoRegistrado {
        override val meta = CartaoMeta(
            id = id, grupo = grupo, natureza = Natureza.QUALIDADE, tela = "falsa",
            tituloChave = "falso.titulo", oQueMostraChave = "falso.oQueMostra", comoCalculaChave = "falso.comoCalcula",
            fontes = listOf(FonteDoDado.ISSUE), papeis = papeis.toSet(),
            temDrill = false, exigeChangelog = false, exigeWorklog = false,
        )

        override suspend fun valores(e: EntradaDeCartao): Map<String, Int> = emptyMap()
        override suspend fun drill(e: EntradaDeCartao, segmento: String): List<LinhaDeDrill> = emptyList()
    }

    // ---- Registro ----

    @Test
    fun catalogoPadraoTemOsCartoesDeTempoEmStatusESincronizacao() {
        val ids = catalogoPadrao().cartoes.map { it.meta.id }
        assertTrue(ids.containsAll((1..7).map { "TS-0$it" }), ids.toString())
        assertTrue(ids.any { it.startsWith("SY-") }, "Sincronização também se registra: $ids")
    }

    @Test
    fun idsSaoUnicos() {
        val ids = catalogoPadrao().cartoes.map { it.meta.id }
        assertEquals(ids.size, ids.toSet().size, ids.toString())
        // Id repetido derruba o registro, em vez de um cartão esconder o outro.
        assertFailsWith<IllegalArgumentException> {
            CatalogoDeCartoes(listOf(falso("ZZ-01", GrupoDeCartao.AUDITORIA, Role.GERENTE), falso("ZZ-01", GrupoDeCartao.ACAO, Role.GERENTE)))
        }
    }

    @Test
    fun todoCartaoTemTextoPtEEn() {
        val pt = traducoes("pt")
        val en = traducoes("en")
        val cartoes = catalogoPadrao().cartoes.map { it.meta }
        assertTrue(cartoes.isNotEmpty())
        for (m in cartoes) {
            val chaves = listOf(m.tituloChave, m.oQueMostraChave, m.comoCalculaChave, "grupo.${m.grupo.id}") +
                m.fontes.map { "catalogo.fonte.${it.name}" } + "catalogo.natureza.${m.natureza.name}"
            for (chave in chaves) {
                assertFalse(pt[chave].isNullOrBlank(), "${m.id}: sem texto pt para $chave")
                assertFalse(en[chave].isNullOrBlank(), "${m.id}: sem texto en para $chave")
            }
            // Inglês de verdade: o texto de "o que mostra" não pode ser a cópia do português.
            assertTrue(pt.getValue(m.oQueMostraChave) != en.getValue(m.oQueMostraChave), "${m.id}: oQueMostra igual nos dois idiomas")
            assertTrue(pt.getValue(m.comoCalculaChave) != en.getValue(m.comoCalculaChave), "${m.id}: comoCalcula igual nos dois idiomas")
        }
    }

    @Test
    fun todaNaturezaETodoGrupoValidos() {
        assertEquals(setOf("ESTOQUE", "FLUXO", "PRAZO", "PESSOAS", "QUALIDADE"), Natureza.entries.map { it.name }.toSet())
        // Os 7 grupos do menu que têm cartões; cada um com tradução própria nos dois idiomas.
        assertEquals(
            setOf("trabalho", "resultados", "prazos", "pessoas", "acao", "auditoria", "governanca"),
            GrupoDeCartao.entries.map { it.id }.toSet(),
        )
        for (idioma in listOf("pt", "en")) {
            val textos = traducoes(idioma)
            for (g in GrupoDeCartao.entries) assertTrue("grupo.${g.id}" in textos, "$idioma: grupo.${g.id}")
            for (n in Natureza.entries) assertTrue("catalogo.natureza.${n.name}" in textos, "$idioma: natureza ${n.name}")
        }
        for (m in catalogoPadrao().cartoes.map { it.meta }) {
            assertTrue(CartaoMeta.FORMATO_ID.matches(m.id), m.id)
            assertTrue(m.papeis.isNotEmpty() && m.fontes.isNotEmpty(), m.id)
        }
        // Cartão que ninguém vê não existe.
        assertFailsWith<IllegalArgumentException> { falso("ZZ-02", GrupoDeCartao.ACAO).meta }
    }

    // ---- Papel ----

    @Test
    fun catalogoSoComOQueOPapelVe() {
        val auditoria = falso("ZZ-01", GrupoDeCartao.AUDITORIA, Role.COORDENADOR, Role.GERENTE)
        val geral = falso("ZZ-02", GrupoDeCartao.RESULTADOS, Role.USUARIO, Role.SQUAD_LEADER, Role.COORDENADOR, Role.GERENTE)
        val catalogo = CatalogoDeCartoes(listOf(auditoria, geral))
        assertEquals(listOf("ZZ-02"), catalogo.visiveisPara(Role.USUARIO).map { it.meta.id })
        assertEquals(listOf("ZZ-02"), catalogo.visiveisPara(Role.SQUAD_LEADER).map { it.meta.id })
        assertEquals(listOf("ZZ-01", "ZZ-02"), catalogo.visiveisPara(Role.GERENTE).map { it.meta.id })
        // Quem acumula papéis vê a união.
        assertEquals(2, catalogo.visiveisPara(setOf(Role.USUARIO, Role.COORDENADOR)).size)

        // No catálogo real: o gerente vê todos; o usuário não vê o que é só do gerente (Sincronização).
        val real = catalogoPadrao()
        assertEquals(real.cartoes.size, real.visiveisPara(Role.GERENTE).size)
        assertTrue(real.visiveisPara(Role.USUARIO).size < real.cartoes.size)
        assertTrue(real.visiveisPara(Role.USUARIO).none { it.meta.id.startsWith("SY-") })
    }

    @Test
    fun rotaDoCatalogoDevolveSoOQueOPapelVe() = rodar { get ->
        val total = catalogoPadrao().cartoes.size
        val gerente = Json.decodeFromString<CatalogoDto>(get("gerente.exemplo", "/api/cartoes/catalogo").corpo)
        assertEquals(total, gerente.cartoes.size)
        val usuario = get("ana.silva", "/api/cartoes/catalogo")
        assertEquals(HttpStatusCode.OK, usuario.status, usuario.corpo)
        val doUsuario = Json.decodeFromString<CatalogoDto>(usuario.corpo)
        assertTrue(doUsuario.cartoes.isNotEmpty() && doUsuario.cartoes.size < total)
        assertTrue(doUsuario.cartoes.all { "USUARIO" in it.papeis }, "o catálogo do usuário só traz o que o papel dele vê")
        assertTrue(doUsuario.cartoes.none { it.id.startsWith("SY-") })
    }

    @Test
    fun catalogoSemTextoLivreDoJira() = rodar { get ->
        val corpo = get("gerente.exemplo", "/api/cartoes/catalogo").corpo
        val cartoes = Json.parseToJsonElement(corpo).jsonObject.getValue("cartoes")
        val chave = Regex("[a-z][A-Za-z0-9]*(\\.[A-Za-z0-9]+)+")
        val identificador = Regex("[A-Za-z][A-Za-z0-9_-]*")
        var vistos = 0
        for (c in (cartoes as kotlinx.serialization.json.JsonArray)) {
            val o: JsonObject = c.jsonObject
            for ((campo, valor) in o) {
                val textos = when (valor) {
                    is JsonPrimitive -> if (valor.isString) listOf(valor.content) else emptyList()
                    is kotlinx.serialization.json.JsonArray -> valor.map { (it as JsonPrimitive).content }
                    else -> error("campo inesperado $campo")
                }
                for (texto in textos) {
                    vistos++
                    val ok = if (campo.endsWith("Chave")) chave.matches(texto) else identificador.matches(texto)
                    assertTrue(ok, "$campo não é chave nem identificador: $texto")
                }
            }
        }
        assertTrue(vistos > 0)
    }

    // ---- Auditoria ----

    @Test
    fun rotaDoCatalogoGravaAccessLog() = rodar { get ->
        assertEquals(HttpStatusCode.OK, get("ana.silva", "/api/cartoes/catalogo").status)
        // Papel encerrado ontem: sem papel vigente, 403 sem corpo, e a negação também é registrada.
        val negada = get("ex.papel", "/api/cartoes/catalogo")
        assertEquals(HttpStatusCode.Forbidden, negada.status)
        assertEquals("", negada.corpo)
        assertEquals(HttpStatusCode.Unauthorized, get(null, "/api/cartoes/catalogo").status)
        val linhas = linhasDoAccessLog()
        assertEquals(listOf("ana.silva", "ex.papel"), linhas.map { it.first }, linhas.toString())
        assertTrue(linhas.all { it.second == "/api/cartoes/catalogo" }, linhas.toString())
        assertTrue("200" in linhas[0].third && "403" in linhas[1].third, linhas.toString())
    }
}
