package br.sidia.painel.cards.contrato

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.ErroHttp
import br.sidia.painel.config.Settings
import br.sidia.painel.demo.DadosDeDemonstracao
import br.sidia.painel.demo.lerManifestoDemo
import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.http.ErroDto
import br.sidia.painel.module
import br.sidia.painel.security.AtribuicaoPapel
import br.sidia.painel.security.Authenticator
import br.sidia.painel.security.Pessoa
import br.sidia.painel.security.Principal
import br.sidia.painel.security.Role
import br.sidia.painel.security.RoleDirectory
import br.sidia.painel.security.Vigencia
import br.sidia.painel.security.principalFor
import br.sidia.painel.sync.SqliteIssueStore
import br.sidia.painel.testContextComBanco
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.parametersOf
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingNode
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import java.net.URLEncoder
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Teste de contrato: percorre TODO cartão registrado, com a fixture padrão (dados de demonstração fictícios), e exige
 * que o número do cartão seja o total do seu drill. É também a porta de entrada dos planos seguintes: um cartão novo
 * só passa se se registrar e cumprir o contrato. SQLite real em arquivo; nenhuma rede, nenhum Jira.
 */
class ContratoDeCartoesTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagarBanco() {
            banco.apagar()
        }
    }

    private val zona = ZoneId.of("America/Manaus")
    private val manifesto = lerManifestoDemo("demo")

    // Sábado, um dia depois da âncora das fixtures: tudo que a demonstração grava fica no passado.
    private val relogio: Clock = Clock.fixed(manifesto.ancora.plusDays(1).atTime(12, 0).atZone(zona).toInstant(), zona)
    private val hoje: LocalDate = LocalDate.now(relogio)
    private val producao = Settings(dashboardDevMode = false)

    private fun pessoa(id: String, papel: Role, squad: String? = null) =
        Pessoa(id, id, listOf(AtribuicaoPapel(papel, Vigencia(null, null), squad)))

    private val diretorio = RoleDirectory(
        listOf(
            pessoa("dev.alfa1", Role.USUARIO, "Alfa"),
            pessoa("dev.alfa2", Role.USUARIO, "Alfa"),
            pessoa("lider.alfa", Role.SQUAD_LEADER, "Alfa"),
            pessoa("dev.beta1", Role.USUARIO, "Beta"),
            pessoa("lider.beta", Role.SQUAD_LEADER, "Beta"),
            pessoa("coord.um", Role.COORDENADOR),
            pessoa("gerente.exemplo", Role.GERENTE),
        ),
    )

    private lateinit var ctx: AppContext
    private lateinit var store: SqliteIssueStore
    private val catalogo = catalogoPadrao()

    @BeforeTest
    fun preparar() {
        ctx = testContextComBanco(producao, banco, diretorio, relogio)
        val ds = requireNotNull(ctx.dataSource)
        store = SqliteIssueStore(ds)
        DadosDeDemonstracao(ds, store, ctx.clock, ctx.settings.zone).carregar()
    }

    @AfterTest
    fun encerrar() {
        ctx.close()
    }

    private fun entrada(usuario: String, params: Parameters = Parameters.Empty) =
        EntradaDeCartao(ctx, store, principalFor(usuario, diretorio, hoje), params)

    private val usuarios = listOf("gerente.exemplo", "coord.um", "lider.alfa", "lider.beta", "dev.alfa1", "dev.alfa2", "dev.beta1")

    private fun cartoesComDrillVistosPor(usuario: String) =
        catalogo.visiveisPara(principalFor(usuario, diretorio, hoje).roles).filter { it.meta.temDrill }

    /** Confere o par (segmento, valor) de todo cartão com drill; devolve quantos pares conferiu. */
    private fun conferirNumerosIguaisAoDrill(usuario: String, params: Parameters): Int {
        var pares = 0
        val e = entrada(usuario, params)
        for (cartao in cartoesComDrillVistosPor(usuario)) {
            val valores = runBlocking { cartao.valores(e) }
            for ((segmento, valor) in valores) {
                val linhas = runBlocking { cartao.drill(e, segmento) }
                assertEquals(valor, linhas.size, "${cartao.meta.id} / $segmento / $usuario / $params")
                pares++
            }
        }
        return pares
    }

    // ---- O contrato ----

    @Test
    fun numeroDoCartaoIgualAoTotalDoDrill() {
        var pares = 0
        for (u in usuarios) pares += conferirNumerosIguaisAoDrill(u, Parameters.Empty)
        // A fixture não pode ser vazia: sem números a invariante não prova nada.
        assertTrue(pares >= 20, "poucos pares conferidos: $pares")
        // O gerente vê números positivos nos sete cartões de Tempo em status.
        val e = entrada("gerente.exemplo")
        for (n in 1..7) {
            val valores = runBlocking { catalogo.porId("TS-0$n")!!.valores(e) }
            assertTrue(valores.isNotEmpty() && valores.values.any { it > 0 }, "TS-0$n sem número na fixture: $valores")
        }
    }

    @Test
    fun cadaTelaDeTrabalhoTemParPositivoNaDemonstracao() {
        // Sem par positivo o contrato "número == drill" seria vácuo (0 == 0) para a tela inteira.
        val e = entrada("gerente.exemplo")
        for (prefixo in listOf("AB", "NG", "FE", "MT", "NE", "RI", "PV", "FX", "AW")) {
            val positivos = catalogo.visiveisPara(Role.GERENTE).filter { it.meta.id.startsWith("$prefixo-") && it.meta.temDrill }
                .flatMap { c -> runBlocking { c.valores(e) }.filterValues { it > 0 }.keys.map { c to it } }
            assertTrue(positivos.isNotEmpty(), "$prefixo sem par positivo na demonstração: " + catalogo.visiveisPara(Role.GERENTE).filter { it.meta.id.startsWith("$prefixo-") }.map { c -> c.meta.id to runBlocking { c.valores(e) } })
            val (cartao, segmento) = positivos.first()
            assertTrue(runBlocking { cartao.drill(e, segmento) }.isNotEmpty(), "${cartao.meta.id} / $segmento")
        }
    }

    @Test
    fun todoCartaoComDrillRegistradoPassaPeloContrato() {
        // Garante que o teste acima enxerga todos: o catálogo do gerente só tem cartão com drill ou sem tasks por trás.
        for (cartao in catalogo.visiveisPara(Role.GERENTE)) {
            if (!cartao.meta.temDrill) {
                assertTrue(runBlocking { cartao.valores(entrada("gerente.exemplo")) }.isEmpty(), "${cartao.meta.id} sem drill não tem números por segmento")
            }
        }
        assertTrue(cartoesComDrillVistosPor("gerente.exemplo").map { it.meta.id }.containsAll((1..7).map { "TS-0$it" }))
    }

    @Test
    fun drillComFiltroTemOMesmoTamanhoDoCartao() {
        val combinacoes = listOf(
            parametersOf("unit", "corrido"),
            parametersOf("squad", "Alfa"),
            parametersOf("tipo", "Checklist"),
            parametersOf("semOutliers", "true"),
            Parameters.build {
                append("unit", "corrido")
                append("squad", "Beta")
                append("os", "Android 17.0 (OneUI_9.0)")
            },
            Parameters.build {
                append("de", hoje.minusDays(60).toString())
                append("ate", hoje.minusDays(10).toString())
                append("tipo", "Checklist")
            },
        )
        var pares = 0
        for (params in combinacoes) pares += conferirNumerosIguaisAoDrill("gerente.exemplo", params)
        for (params in combinacoes.take(3)) pares += conferirNumerosIguaisAoDrill("lider.alfa", params)
        assertTrue(pares > 10, "poucos pares com filtro: $pares")
        // O filtro muda mesmo o número: o contrato não está sendo satisfeito por filtro ignorado.
        val semFiltro = runBlocking { catalogo.porId("TS-01")!!.valores(entrada("gerente.exemplo")) }.getValue("analisadas")
        val soAlfa = runBlocking { catalogo.porId("TS-01")!!.valores(entrada("gerente.exemplo", parametersOf("squad", "Alfa"))) }.getValue("analisadas")
        assertTrue(soAlfa < semFiltro, "squad=Alfa deveria reduzir ($soAlfa < $semFiltro)")
    }

    @Test
    fun drillForaDaVisaoEmOutraPessoa() {
        // dev.alfa1 (USUARIO) só enxerga o que é dela; nenhuma linha traz username de quem está fora da visão.
        val e = entrada("dev.alfa1")
        val proibidos = listOf("dev.alfa2", "dev.beta1", "lider.beta", "lider.alfa", "fora.do.diretorio", "dev.mudou")
        var linhasVistas = 0
        for (cartao in cartoesComDrillVistosPor("dev.alfa1")) {
            for (segmento in runBlocking { cartao.valores(e) }.keys) {
                for (linha in runBlocking { cartao.drill(e, segmento) }) {
                    linhasVistas++
                    assertTrue(linha.responsavel == null || linha.responsavel == "dev.alfa1", "${cartao.meta.id}: ${linha.responsavel}")
                    val texto = linha.toString()
                    for (p in proibidos) assertFalse(p in texto, "${cartao.meta.id}/$segmento vazou $p: $texto")
                }
            }
        }
        assertTrue(linhasVistas > 0, "a fixture precisa ter tasks da dev.alfa1")
        // Pedir a matriz de outra pessoa não amplia a visão: 403, sem lista.
        val erro = runCatching { runBlocking { catalogo.porId("TS-06")!!.drill(e, "pessoa:dev.beta1:status:In Progress") } }.exceptionOrNull()
        assertTrue(erro is ErroHttp && erro.status == HttpStatusCode.Forbidden, "esperava 403: $erro")
    }

    @Test
    fun drillOrdenaPessoasAlfabeticamente() {
        val e = entrada("gerente.exemplo")
        // Segmentos por pessoa saem em ordem alfabética (sem ranking por tempo ou por quantidade).
        val segmentos = runBlocking { catalogo.porId("TS-06")!!.valores(e) }.keys.toList()
        val pessoas = segmentos.map { it.removePrefix("pessoa:").substringBefore(":status:") }
        assertTrue(pessoas.size > 3, "a fixture precisa de várias pessoas: $pessoas")
        assertEquals(pessoas.sorted(), pessoas, "pessoas fora da ordem alfabética")
        // As linhas das tasks seguem a ordem natural das chaves (TG-2 antes de TG-10), nunca por tempo nem por pessoa.
        for (cartao in cartoesComDrillVistosPor("gerente.exemplo")) {
            for (segmento in runBlocking { cartao.valores(e) }.keys) {
                val chaves = runBlocking { cartao.drill(e, segmento) }.map { it.key }
                assertEquals(chaves.sortedWith(ORDEM_NATURAL_DE_CHAVES), chaves, "${cartao.meta.id}/$segmento")
            }
        }
    }

    // ---- A rota genérica ----

    private val autenticador = object : Authenticator {
        override suspend fun authenticate(call: ApplicationCall): Principal? =
            call.request.headers["X-Teste-Usuario"]?.let { principalFor(it, diretorio, hoje) }
    }

    private class Resp(val status: HttpStatusCode, val corpo: String)

    private fun rodar(bloco: suspend (suspend (String?, String) -> Resp) -> Unit) = testApplication {
        application { module(producao, ctx, autenticador) }
        val http = client
        bloco { usuario, caminho ->
            val r = http.get(caminho) { if (usuario != null) header("X-Teste-Usuario", usuario) }
            Resp(r.status, r.bodyAsText())
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8)

    private fun linhasDoAccessLog(): List<Triple<String, String, String>> =
        requireNotNull(ctx.dataSource).connection.use { c ->
            c.createStatement().executeQuery("SELECT user_id, route, filters FROM access_log ORDER BY id").use { rs ->
                buildList { while (rs.next()) add(Triple(rs.getString(1), rs.getString(2), rs.getString(3).orEmpty())) }
            }
        }

    @Test
    fun rotaGenericaDevolveOMesmoTamanhoDoCartao() = rodar { get ->
        val r = get("gerente.exemplo", "/api/cartoes/TS-03/tasks?segmento=paradas")
        assertEquals(HttpStatusCode.OK, r.status, r.corpo)
        val dto = Json.decodeFromString<DrillGenericoDto>(r.corpo)
        val esperado = runBlocking { catalogo.porId("TS-03")!!.valores(entrada("gerente.exemplo")) }.getValue("paradas")
        assertEquals(esperado, dto.total)
        assertEquals(esperado, dto.linhas.size)
        assertEquals("card.ts03.oQueMostra", dto.definicaoChave)
        assertEquals(listOf("chave", "resumo", "responsavel", "status", "detalhe"), dto.colunas)
        // Com a flag do Jira desligada (o padrão), o link para o Jira nunca existe (ver LinkJiraTest para o caso ligado).
        assertEquals(null, dto.linkJira)
        // Com filtro, a rota respeita o mesmo recorte.
        val filtrado = Json.decodeFromString<DrillGenericoDto>(get("gerente.exemplo", "/api/cartoes/TS-01/tasks?segmento=analisadas&squad=Alfa").corpo)
        val doCartao = runBlocking { catalogo.porId("TS-01")!!.valores(entrada("gerente.exemplo", parametersOf("squad", "Alfa"))) }.getValue("analisadas")
        assertEquals(doCartao, filtrado.total)
    }

    @Test
    fun segmentoInexistenteDevolve400ComCodigo() = rodar { get ->
        for (segmento in listOf("nao-existe", "status:", "pessoa:x", "")) {
            val r = get("gerente.exemplo", "/api/cartoes/TS-01/tasks?segmento=${enc(segmento)}")
            assertEquals(HttpStatusCode.BadRequest, r.status, "segmento '$segmento'")
            assertEquals("SEGMENTO_INVALIDO", Json.decodeFromString<ErroDto>(r.corpo).codigo)
        }
        // Sem o parâmetro também; e o valor recebido nunca volta no corpo.
        val sem = get("gerente.exemplo", "/api/cartoes/TS-01/tasks")
        assertEquals(HttpStatusCode.BadRequest, sem.status)
        assertEquals("SEGMENTO_INVALIDO", Json.decodeFromString<ErroDto>(sem.corpo).codigo)
        val eco = get("gerente.exemplo", "/api/cartoes/TS-01/tasks?segmento=${enc("valor-estranho-123")}")
        assertFalse("valor-estranho-123" in eco.corpo)
        // Segmento de outro cartão do mesmo grupo também é recusado (cada cartão só abre os seus números).
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cartoes/TS-03/tasks?segmento=analisadas").status)
    }

    @Test
    fun cartaoInexistenteSemDrillOuDeOutroPapel() = rodar { get ->
        assertEquals(HttpStatusCode.NotFound, get("gerente.exemplo", "/api/cartoes/ZZ-99/tasks?segmento=x").status)
        // Cartão sem tasks por trás (Sincronização): não há gaveta.
        assertEquals(HttpStatusCode.NotFound, get("gerente.exemplo", "/api/cartoes/SY-01/tasks?segmento=x").status)
        // Cartão que o papel não vê: 403 sem corpo.
        val negada = get("dev.alfa1", "/api/cartoes/SY-01/tasks?segmento=x")
        assertEquals(HttpStatusCode.Forbidden, negada.status)
        assertEquals("", negada.corpo)
    }

    @Test
    fun rotaGenericaNaoAmpliaAVisaoDoUsuario() = rodar { get ->
        assertEquals(HttpStatusCode.Forbidden, get("dev.alfa1", "/api/cartoes/TS-06/tasks?segmento=${enc("pessoa:dev.beta1:status:In Progress")}").status)
        // Squad de outro: o usuário comum continua só com o que é dele.
        val dto = Json.decodeFromString<DrillGenericoDto>(get("dev.alfa1", "/api/cartoes/TS-01/tasks?segmento=analisadas&squad=Beta").corpo)
        assertEquals(0, dto.total)
        assertEquals(HttpStatusCode.Forbidden, get("dev.alfa1", "/api/cartoes/TS-01/tasks?segmento=analisadas&pessoa=dev.beta1").status)
    }

    @Test
    fun rotaGravaAccessLogInclusiveNos400() = rodar { get ->
        assertEquals(HttpStatusCode.OK, get("dev.alfa1", "/api/cartoes/TS-01/tasks?segmento=analisadas").status)
        assertEquals(HttpStatusCode.BadRequest, get("dev.alfa1", "/api/cartoes/TS-01/tasks?segmento=ruim").status)
        assertEquals(HttpStatusCode.Forbidden, get("dev.alfa1", "/api/cartoes/TS-01/tasks?segmento=analisadas&pessoa=dev.beta1").status)
        assertEquals(HttpStatusCode.NotFound, get("dev.alfa1", "/api/cartoes/ZZ-99/tasks?segmento=x").status)
        val linhas = linhasDoAccessLog()
        assertEquals(4, linhas.size, linhas.toString())
        assertEquals(
            listOf("/api/cartoes/TS-01/tasks", "/api/cartoes/TS-01/tasks", "/api/cartoes/TS-01/tasks", "/api/cartoes/ZZ-99/tasks"),
            linhas.map { it.second },
        )
        assertTrue("\"segmento\":\"analisadas\"" in linhas[0].third && "200" in linhas[0].third, linhas[0].third)
        assertTrue("400" in linhas[1].third && "\"segmento\":\"ruim\"" in linhas[1].third, linhas[1].third)
        assertTrue("403" in linhas[2].third, linhas[2].third)
        assertTrue("404" in linhas[3].third, linhas[3].third)
    }

    @Test
    fun semLoginRetorna401() = rodar { get ->
        assertEquals(HttpStatusCode.Unauthorized, get(null, "/api/cartoes/TS-01/tasks?segmento=analisadas").status)
    }

    // ---- Porta de entrada dos próximos planos ----

    /** Todos os caminhos de rota do servidor, como o Ktor os imprime (`/api/cards/x/(method:GET)`). */
    private fun caminhosDasRotas(raiz: RoutingNode): List<String> {
        val caminhos = mutableListOf<String>()
        fun andar(r: RoutingNode) {
            caminhos += r.toString()
            r.children.forEach(::andar)
        }
        andar(raiz)
        return caminhos
    }

    /** Telas de `/api/cards/<tela>` registradas no servidor que nenhum cartão do catálogo declara. */
    private fun rotasDeCardSemRegistro(raiz: RoutingNode, catalogo: CatalogoDeCartoes): List<String> {
        val telas = catalogo.cartoes.map { it.meta.tela }.toSet()
        return caminhosDasRotas(raiz)
            .mapNotNull { Regex("^/api/cards/([^/]+)").find(it)?.groupValues?.get(1) }
            .filter { it !in telas }
            .distinct()
            .sorted()
    }

    @Test
    fun todaRotaDeCardEstaNoRegistro() = testApplication {
        application { module(producao, ctx, autenticador) }
        startApplication()
        val raiz = application.routing { }
        // O teste precisa enxergar a rota de Tempo em status, senão não prova nada.
        val todas = caminhosDasRotas(raiz)
        assertTrue(todas.any { it.startsWith("/api/cards/tempo-em-status") }, todas.toString())
        assertEquals(emptyList(), rotasDeCardSemRegistro(raiz, catalogo))
    }

    @Test
    fun rotaDeCardForaDoRegistroEhAcusada() = testApplication {
        application {
            module(producao, ctx, autenticador)
            routing { get("/api/cards/novo-sem-registro") { call.respond(HttpStatusCode.OK) } }
        }
        startApplication()
        val raiz = application.routing { }
        assertEquals(listOf("novo-sem-registro"), rotasDeCardSemRegistro(raiz, catalogo))
    }
}
