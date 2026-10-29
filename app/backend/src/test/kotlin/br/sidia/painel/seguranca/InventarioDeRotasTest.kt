package br.sidia.painel.seguranca

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.MODULOS_DA_API
import br.sidia.painel.cards.PREFIXOS_SENSIVEIS
import br.sidia.painel.cards.contrato.catalogoPadrao
import br.sidia.painel.cards.rotaSensivel
import br.sidia.painel.comCsrf
import br.sidia.painel.config.Settings
import br.sidia.painel.integridade.Int2
import br.sidia.painel.lgpd.LgpdSettings
import br.sidia.painel.module
import br.sidia.painel.security.AtribuicaoPapel
import br.sidia.painel.security.Authenticator
import br.sidia.painel.security.OidcAuthenticator
import br.sidia.painel.security.OidcSettings
import br.sidia.painel.security.Pessoa
import br.sidia.painel.security.Principal
import br.sidia.painel.security.ROTAS_PUBLICAS
import br.sidia.painel.security.Role
import br.sidia.painel.security.RoleDirectory
import br.sidia.painel.security.SegredosDaSessao
import br.sidia.painel.security.EmissorOidcFalso
import br.sidia.painel.security.Vigencia
import br.sidia.painel.security.principalFor
import br.sidia.painel.testContextComBanco
import io.ktor.client.statement.bodyAsText
import br.sidia.painel.trabalho.Trb1
import br.sidia.painel.trabalho.novoContextoTrb1
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingNode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Inventário de rotas (Plano 8, Tarefa 1): TODA rota de `/api` tem papel, sensibilidade e registro de acesso declarados
 * numa tabela versionada ([INVENTARIO]). O teste percorre a árvore real de rotas do Ktor, de modo que uma rota nova sem
 * entrada na tabela, ou uma entrada de rota que deixou de existir, derruba [inventarioConhecido]; e prova, rota a rota, que
 * o papel observado é o declarado e que cada chamada autenticada (inclusive a negada) grava exatamente uma linha no
 * `access_log`, com o MODELO da rota casada e nunca o caminho digitado.
 *
 * Como ler a tabela:
 * - **papéis**: quem passa da trava de papel (o status observado não é 403). Não há hierarquia: GERENTE não implica
 *   COORDENADOR. `null` = o papel depende do cartão consultado (`/api/cartoes/{id}/tasks`).
 * - **sensibilidade** ([Sens]): `PREFIXO` = a rota está em `PREFIXOS_SENSIVEIS` e o `access_log` guarda só QUEM consultou
 *   (sem pessoa, squad nem sinal); `CONTEUDO` = a rota marca `ROTA_SENSIVEL` pelo que devolve (cartão IN-, AU-, DV-, VG-14
 *   ou itens de auditoria na Central).
 * - **rótulo no `access_log`**: sempre o modelo da rota (`/api/tarefa/{key}`); a única exceção é o cartão RESOLVIDO no
 *   catálogo (`/api/cartoes/TS-03/tasks`), que mantém o id de uma lista fechada.
 */
class InventarioDeRotasTest {
    companion object {
        private val banco = BancoEmArquivo()
        private val HOJE = LocalDate.of(2026, 10, 30)

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()

        enum class Sens { NAO, PREFIXO, CONTEUDO }

        private val TODOS = Role.entries.toSet()
        private val LIDERANCA = setOf(Role.SQUAD_LEADER, Role.COORDENADOR, Role.GERENTE)
        private val COORDENACAO = setOf(Role.COORDENADOR, Role.GERENTE)
        private val SO_GERENTE = setOf(Role.GERENTE)

        data class Entrada(val metodo: String, val modelo: String, val papeis: Set<Role>?, val sens: Sens = Sens.NAO) {
            val chave get() = "$metodo $modelo"
        }

        private fun get(modelo: String, papeis: Set<Role>?, sens: Sens = Sens.NAO) = Entrada("GET", modelo, papeis, sens)

        /** A tabela versionada. Rota nova: acrescentar aqui, decidir o papel e a sensibilidade e revisar o `access_log`. */
        val INVENTARIO: List<Entrada> = listOf(
            // Auditoria (coordenação e gerente; histórico de alterações é dado sensível)
            get("/api/auditoria/historico/{key}", COORDENACAO, Sens.PREFIXO),
            get("/api/auditoria/sinais", COORDENACAO, Sens.PREFIXO),
            // Cards de leitura (um por rota); `papeis` do catálogo
            get("/api/cards/abertas", TODOS), get("/api/cards/aging-wip", TODOS), get("/api/cards/bloqueios", TODOS),
            get("/api/cards/calendario", TODOS), get("/api/cards/capacidade", LIDERANCA), get("/api/cards/carga-tipo", TODOS),
            get("/api/cards/carga", TODOS), get("/api/cards/checklist-review", TODOS), get("/api/cards/cobertura", LIDERANCA),
            get("/api/cards/competencias", TODOS), get("/api/cards/controle-ciclo", TODOS), get("/api/cards/cruzamentos", TODOS),
            get("/api/cards/epicos", TODOS), get("/api/cards/esforco", TODOS), get("/api/cards/fechadas", TODOS),
            get("/api/cards/fluxo-da-semana", TODOS), get("/api/cards/gargalos", LIDERANCA), get("/api/cards/metas", TODOS),
            get("/api/cards/negativas", TODOS), get("/api/cards/negligenciadas", TODOS), get("/api/cards/plano", LIDERANCA),
            get("/api/cards/posse", TODOS), get("/api/cards/previsao", TODOS), get("/api/cards/qualidade", COORDENACAO),
            get("/api/cards/revisoes", TODOS), get("/api/cards/riscos", TODOS), get("/api/cards/simulador", LIDERANCA),
            get("/api/cards/task-review", COORDENACAO, Sens.PREFIXO), get("/api/cards/tempo-em-status", TODOS),
            get("/api/cards/tendencia", LIDERANCA), get("/api/cards/vencimentos", TODOS),
            // Catálogo e detalhamento por cartão (o papel é o do cartão)
            get("/api/cartoes/catalogo", TODOS),
            get("/api/cartoes/{id}/tasks", null, Sens.CONTEUDO),
            // Central, distribuição e governança
            get("/api/central", COORDENACAO, Sens.CONTEUDO),
            get("/api/distribuicao", LIDERANCA),
            Entrada("POST", "/api/distribuicao/decisao", LIDERANCA),
            get("/api/rebalanceamento", LIDERANCA),
            get("/api/filtros/opcoes", TODOS),
            get("/api/governanca/acessos", SO_GERENTE, Sens.PREFIXO),
            get("/api/governanca/configuracao", SO_GERENTE),
            get("/api/governanca/decisoes", COORDENACAO),
            get("/api/integridade", SO_GERENTE, Sens.PREFIXO),
            get("/api/integridade/catalogo", SO_GERENTE, Sens.PREFIXO),
            // Identidade e telas pessoais
            get("/api/me", TODOS), get("/api/meu-painel", TODOS), get("/api/squad", LIDERANCA),
            get("/api/tarefa/{key}", TODOS),
            get("/api/titular/meus-dados", TODOS, Sens.PREFIXO),
            // Operação
            get("/api/operacao/alertas", SO_GERENTE), get("/api/sync/status", SO_GERENTE),
        )

        /** Rotas públicas de `/api` (as de `/auth` só existem com o autenticador OIDC e são cobertas em `OidcTest`). */
        private val PUBLICAS_DA_API = setOf("GET /api/health", "GET /api/health/pronto", "GET /api/meta")

        /** Nós do próprio painel que não são rotas de dados: os curingas de 404 da API e a casca estática da SPA. */
        private val DO_FRAMEWORK = setOf("* /api/{...}", "GET /{...}", "HEAD /{...}")

        private val USUARIOS = mapOf(
            Role.USUARIO to "ana", Role.SQUAD_LEADER to "lider.alfa", Role.COORDENADOR to "coord.um", Role.GERENTE to "gerente.exemplo",
        )
    }

    private var ctx: AppContext? = null

    @AfterTest
    fun encerrar() {
        ctx?.close()
    }

    /** Sobe o painel com o autenticador por cabeçalho e entrega a lista real de rotas `(método, modelo)` e um cliente. */
    private fun rodar(
        contexto: AppContext? = null,
        diretorio: RoleDirectory = Trb1.papeis,
        bloco: suspend (rotas: List<Pair<String, String>>, http: HttpClient, c: AppContext) -> Unit,
    ) {
        val c = (contexto ?: novoContextoTrb1(banco)).also { ctx = it }
        val raizes = mutableListOf<Route>()
        testApplication {
            application {
                module(c.settings, c, object : Authenticator {
                    override suspend fun authenticate(call: ApplicationCall): Principal? =
                        call.request.headers["X-Teste-Usuario"]?.let { principalFor(it, diretorio, HOJE) }
                })
                raizes += routing { }
            }
            startApplication()
            bloco(todasAsRotas(raizes.single()), client, c)
        }
    }

    /** Toda FOLHA da árvore (com método, ou `handle {}` sem método: `*`). Handler em nó interno não é visível pela API pública do Ktor. */
    private fun comAtendimento(r: Route, acc: MutableList<String>) {
        val no = r as RoutingNode
        if (no.children.isEmpty()) acc.add(r.toString())
        no.children.forEach { comAtendimento(it, acc) }
    }

    /** Pares `(método, modelo)` de TODA a árvore (dentro e fora de `/api`); `*` quando o nó não tem método. */
    private fun todasAsRotas(raiz: Route): List<Pair<String, String>> {
        val nos = mutableListOf<String>().also { comAtendimento(raiz, it) }
        return nos.map {
            val metodo = if ("(method:" in it) it.substringAfter("(method:").substringBefore(")") else "*"
            metodo to it.substringBefore("/(method:").replace("/(requireRole)", "")
        }.sortedBy { "${it.second} ${it.first}" }
    }

    /** As rotas de dados sob `/api` (as que têm método e não são o curinga). */
    private fun rotasReais(raiz: Route): List<Pair<String, String>> =
        todasAsRotas(raiz).filter { it.second.startsWith("/api/") && it.first != "*" }

    private fun caminhoDeExemplo(modelo: String) = modelo.replace("{key}", "TG-111").replace("{id}", "TS-03")

    private fun linhasDoLog(c: AppContext): Int =
        requireNotNull(c.dataSource).connection.use { k ->
            k.createStatement().executeQuery("SELECT count(*) FROM access_log").use { it.next(); it.getInt(1) }
        }

    private fun ultimaLinha(c: AppContext): Triple<String, String, String?> =
        requireNotNull(c.dataSource).connection.use { k ->
            k.createStatement().executeQuery("SELECT user_id, route, filters FROM access_log ORDER BY id DESC LIMIT 1").use {
                assertTrue(it.next(), "sem linha no access_log")
                Triple(it.getString(1), it.getString(2), it.getString(3))
            }
        }

    private suspend fun chamar(http: HttpClient, e: Entrada, caminho: String, usuario: String?, query: String = ""): HttpResponse =
        if (e.metodo == "POST") {
            http.post(caminho + query) {
                if (usuario != null) header("X-Teste-Usuario", usuario)
                comCsrf(); contentType(ContentType.Application.Json); setBody("{}")
            }
        } else {
            http.get(caminho + query) { if (usuario != null) header("X-Teste-Usuario", usuario) }
        }

    @Test
    fun inventarioConhecido() = rodar { rotas, _, _ ->
        // TODA a árvore: rota fora de /api (seria tratada como casca estática pública) e folha com `handle {}` sem método
        // também aparecem aqui e reprovam se não estiverem declaradas.
        val reais = rotas.map { "${it.first} ${it.second}" }.toSet()
        val esperadas = INVENTARIO.map { it.chave }.toSet() + PUBLICAS_DA_API + DO_FRAMEWORK
        assertEquals(INVENTARIO.size, INVENTARIO.map { it.chave }.toSet().size, "entrada repetida no inventário")
        assertEquals(
            emptySet(), reais - esperadas,
            "Rota nova sem entrada no INVENTARIO: decida o papel, a sensibilidade e o registro de acesso antes de publicá-la",
        )
        assertEquals(emptySet(), esperadas - reais, "Entrada do INVENTARIO sem rota correspondente (rota removida ou renomeada)")
        // As públicas de /api são exatamente as declaradas em ROTAS_PUBLICAS; /auth/* só existe com OIDC.
        assertEquals(
            setOf("/api/health", "/api/health/pronto", "/api/meta", "/auth/callback", "/auth/login", "/auth/logout"), ROTAS_PUBLICAS,
        )
        assertEquals(PUBLICAS_DA_API, ROTAS_PUBLICAS.filter { it.startsWith("/api/") }.map { "GET $it" }.toSet())
    }

    @Test
    fun postSoOndeEsperado() = rodar { rotas, _, _ ->
        val mutantes = rotas.map { "${it.first} ${it.second}" }.filter { !it.startsWith("GET ") && it !in DO_FRAMEWORK }
        assertEquals(listOf("POST /api/distribuicao/decisao"), mutantes, "método que altera estado fora da lista: $mutantes")
    }

    @Test
    fun todaRotaApiTemPapelERegistro() = rodar { _, http, c ->
        for (e in INVENTARIO) {
            val caminho = caminhoDeExemplo(e.modelo)
            // Sem principal: 401 e nada a registrar (a negação acontece antes de qualquer dado).
            val antesAnonimo = linhasDoLog(c)
            assertEquals(HttpStatusCode.Unauthorized, chamar(http, e, caminho, null).status, "${e.chave} sem principal")
            assertEquals(antesAnonimo, linhasDoLog(c), "${e.chave}: 401 sem principal não grava linha")

            // Cada papel: o status só é 403 fora dos papéis declarados, e TODA chamada grava exatamente uma linha.
            val passou = mutableSetOf<Role>()
            for ((papel, usuario) in USUARIOS) {
                val antes = linhasDoLog(c)
                val status = chamar(http, e, caminho, usuario).status
                assertEquals(antes + 1, linhasDoLog(c), "${e.chave} como $papel ($status): exatamente 1 linha no access_log")
                val (quem, rotulo, _) = ultimaLinha(c)
                assertEquals(usuario, quem, "${e.chave}: usuário registrado")
                // Modelo da rota, nunca a chave digitada; só o cartão resolvido mantém o id.
                val esperado = if (e.modelo == "/api/cartoes/{id}/tasks") "/api/cartoes/TS-03/tasks" else e.modelo
                assertEquals(esperado, rotulo, "${e.chave}: rótulo no access_log")
                // Quem passa da trava recebe o status "de sucesso" da rota (200; 400 só no POST de corpo vazio e na rota de
                // tasks sem segmento): nunca 401, 5xx nem outro erro disfarçado de "passou".
                val semErro = if (e.metodo == "POST" || e.modelo == "/api/cartoes/{id}/tasks") HttpStatusCode.BadRequest else HttpStatusCode.OK
                if (status != HttpStatusCode.Forbidden) {
                    assertEquals(semErro, status, "${e.chave} como $papel: fora da trava de papel o status esperado é $semErro")
                    passou += papel
                }
            }
            if (e.papeis != null) assertEquals(e.papeis, passou, "${e.chave}: papéis que passam da trava (observados x declarados)")
        }
    }

    @Test
    fun papelDoCartaoNaRotaDeTasks() = rodar { _, http, c ->
        val e = INVENTARIO.single { it.modelo == "/api/cartoes/{id}/tasks" }
        // IN-01 é só do gerente; TS-03 é de todos. O papel vem do catálogo, não da rota.
        for ((papel, usuario) in USUARIOS) {
            val status = chamar(http, e, "/api/cartoes/IN-01/tasks", usuario).status
            assertEquals(papel != Role.GERENTE, status == HttpStatusCode.Forbidden, "IN-01 como $papel: $status")
            assertFalse(chamar(http, e, "/api/cartoes/TS-03/tasks", usuario).status == HttpStatusCode.Forbidden, "TS-03 como $papel")
        }
        // Cartão não resolvido no catálogo: o rótulo é o MODELO, nunca o texto digitado (pode ser uma chave de issue).
        chamar(http, e, "/api/cartoes/TG-111/tasks", "gerente.exemplo")
        assertEquals("/api/cartoes/{id}/tasks", ultimaLinha(c).second)
    }

    @Test
    fun semPapelVigenteEhNegadoERegistradoPeloModulo() = rodar { _, http, c ->
        for (e in INVENTARIO) {
            val antes = linhasDoLog(c)
            val status = chamar(http, e, caminhoDeExemplo(e.modelo), "fulano.sem.papel").status
            assertEquals(HttpStatusCode.Forbidden, status, "${e.chave}: autenticado sem papel vigente")
            assertEquals(antes + 1, linhasDoLog(c), "${e.chave}: a negação sem papel também é registrada")
            val (quem, rotulo, _) = ultimaLinha(c)
            assertEquals("fulano.sem.papel", quem)
            // Antes do roteador só o MÓDULO da lista fechada vai para o registro (nunca o resto do caminho).
            assertEquals("/api/" + e.modelo.split('/')[2], rotulo, e.chave)
        }
    }

    @Test
    fun modulosDaApiSincronizadosComAsRotasReais() = rodar { rotas, _, _ ->
        val modulosReais = rotas.filter { it.second.startsWith("/api/") && it.first != "*" }.map { it.second.split('/')[2] }.toSet() - "health"
        assertEquals(
            modulosReais, MODULOS_DA_API,
            "MODULOS_DA_API (rótulo da negação antes do roteador) precisa listar exatamente os módulos reais de /api",
        )
    }

    @Test
    fun sensibilidadeDeclaradaBateComOsPrefixosEOAccessLog() = rodar { _, http, c ->
        val query = "?pessoa=bruno&squad=Alfa&sinal=SINAL-X&segmento=pessoa:bruno"
        for (e in INVENTARIO.filter { it.metodo == "GET" }) {
            val caminho = caminhoDeExemplo(e.modelo)
            assertEquals(e.sens == Sens.PREFIXO, rotaSensivel(caminho), "${e.chave}: PREFIXOS_SENSIVEIS x inventário")
            if (e.sens != Sens.PREFIXO) continue
            chamar(http, e, caminho, "gerente.exemplo", query)
            val filtros = ultimaLinha(c).third.orEmpty()
            for (proibido in listOf("bruno", "Alfa", "SINAL-X")) {
                assertFalse(proibido in filtros, "${e.chave}: rota sensível não grava '$proibido' (filtros: $filtros)")
            }
        }
    }

    @Test
    fun quemTemMaisDeUmPapelPassaPelaUniaoDeles() {
        // Pessoa com USUARIO (Alfa) e COORDENADOR ao mesmo tempo: passa onde QUALQUER dos dois passa, e só aí.
        val duplo = Pessoa(
            "duplo.papel", "duplo.papel",
            listOf(AtribuicaoPapel(Role.USUARIO, Vigencia(null, null), "Alfa"), AtribuicaoPapel(Role.COORDENADOR, Vigencia(null, null))),
        )
        val base = listOf("ana", "bruno", "carla").map { Pessoa(it, it, listOf(AtribuicaoPapel(Role.USUARIO, Vigencia(null, null), if (it == "carla") "Beta" else "Alfa"))) } +
            listOf(
                Pessoa("lider.alfa", "lider.alfa", listOf(AtribuicaoPapel(Role.SQUAD_LEADER, Vigencia(null, null), "Alfa"))),
                Pessoa("coord.um", "coord.um", listOf(AtribuicaoPapel(Role.COORDENADOR, Vigencia(null, null)))),
                Pessoa("gerente.exemplo", "gerente.exemplo", listOf(AtribuicaoPapel(Role.GERENTE, Vigencia(null, null)))),
                duplo,
            )
        val diretorio = RoleDirectory(base)
        val c = testContextComBanco(
            Settings(dashboardDevMode = false, lgpd = LgpdSettings(true, "TESTE-TRB1")), banco, diretorio, Trb1.relogio,
            statusMap = Trb1.statusMap, calendar = Trb1.calendario,
        )
        Trb1.carregar(requireNotNull(c.dataSource), Trb1.issues())
        rodar(c, diretorio) { _, http, _ ->
            val uniao = setOf(Role.USUARIO, Role.COORDENADOR)
            for (e in INVENTARIO.filter { it.papeis != null }) {
                val esperado = if (e.papeis!!.any { it in uniao }) setOf(HttpStatusCode.OK, HttpStatusCode.BadRequest) else setOf(HttpStatusCode.Forbidden)
                val status = chamar(http, e, caminhoDeExemplo(e.modelo), "duplo.papel").status
                assertTrue(status in esperado, "${e.chave} com USUARIO+COORDENADOR: $status, esperado $esperado")
            }
        }
    }

    @Test
    fun rotasDeLoginSoExistemComOidcEMantemAsPublicas() {
        val emissor = EmissorOidcFalso()
        val segredo = java.util.Base64.getEncoder().encodeToString(ByteArray(48) { (it * 5 + 1).toByte() })
        val cfg = OidcSettings(
            issuer = emissor.issuer, clientId = emissor.clientId, clientSecret = "segredo-do-cliente-ficticio-1",
            redirectUri = "https://painel.exemplo.invalid/auth/callback", sessao = SegredosDaSessao.de(segredo, null),
            duracaoDaSessao = java.time.Duration.ofHours(8),
        )
        val settings = Settings(dashboardDevMode = false, oidc = cfg)
        val c = testContextComBanco(settings, banco).also { ctx = it }
        val autenticador = OidcAuthenticator(cfg, c.roles, java.time.Clock.systemUTC(), emissor.cliente(), null)
        val raizes = mutableListOf<Route>()
        testApplication {
            application {
                module(settings, c, autenticador)
                raizes += routing { }
            }
            startApplication()
        }
        val auth = todasAsRotas(raizes.single()).filter { it.second.startsWith("/auth") }.map { "${it.first} ${it.second}" }.toSet()
        assertEquals(setOf("GET /auth/login", "GET /auth/callback", "POST /auth/logout"), auth)
        // Toda rota de /auth é pública declarada em ROTAS_PUBLICAS (e nenhuma outra de /auth existe).
        assertEquals(auth.map { it.substringAfter(' ') }.toSet(), ROTAS_PUBLICAS.filter { it.startsWith("/auth") }.toSet())
    }

    @Test
    fun rotasDeConteudoSensivelNaoGravamAlvoNoAccessLog() {
        // Cenário INT-2: o bruno tem indício ALTO, então a Central do gerente traz INDICIO_ALTO (conteúdo sensível). Com o
        // modo de desenvolvimento a trava do DPO fica aberta.
        val c = novoContextoTrb1(banco, Int2.issues(), Settings(dashboardDevMode = true))
        rodar(c) { _, http, ctxDoTeste ->
            val query = "?pessoa=bruno&squad=Alfa&sinal=SINAL-X&segmento=pessoa:bruno&total=7"
            fun filtrosGravados() = ultimaLinha(ctxDoTeste).third.orEmpty()
            fun naoGrava(rotulo: String) {
                val f = filtrosGravados()
                for (proibido in listOf("bruno", "Alfa", "SINAL-X", "INDICIO", "total")) {
                    assertFalse(proibido in f, "$rotulo: rota sensível não grava '$proibido' (filtros: $f)")
                }
            }
            // Controle: uma rota NÃO sensível grava os mesmos filtros (o teste não é vazio).
            chamar(http, INVENTARIO.single { it.modelo == "/api/cards/riscos" }, "/api/cards/riscos", "gerente.exemplo", query)
            assertTrue("bruno" in filtrosGravados() && "Alfa" in filtrosGravados(), "controle: ${filtrosGravados()}")

            // Central: a marca só liga quando a resposta traz item sensível; confirma a precondição do cenário.
            val central = INVENTARIO.single { it.modelo == "/api/central" }
            val resp = chamar(http, central, "/api/central", "gerente.exemplo", query)
            assertEquals(HttpStatusCode.OK, resp.status)
            assertTrue("INDICIO_ALTO" in resp.bodyAsText(), "o cenário precisa devolver o item sensível")
            naoGrava("/api/central")

            // Cartões sensíveis reais pelo detalhamento: IN-01 (integridade), AU-01, DV-01 e VG-14 (auditoria/divergência) e VG-13.
            val tasks = INVENTARIO.single { it.modelo == "/api/cartoes/{id}/tasks" }
            for (cartao in listOf("IN-01", "AU-01", "DV-01", "VG-14", "VG-13")) {
                chamar(http, tasks, "/api/cartoes/$cartao/tasks", "gerente.exemplo", query + "&segmento=INDICIO_ALTO")
                assertEquals("/api/cartoes/$cartao/tasks", ultimaLinha(ctxDoTeste).second, cartao)
                naoGrava(cartao)
            }
        }
    }

    @Test
    fun todoPrefixoSensivelCorrespondeARotaOuCartaoReal() = rodar { _, _, _ ->
        // Sem prefixo fantasma: cada um é prefixo de uma rota do inventário ou de um cartão do catálogo.
        val cartoes = catalogoPadrao().cartoes.map { "/api/cartoes/${it.meta.id}".lowercase() }
        for (prefixo in PREFIXOS_SENSIVEIS) {
            val deRota = INVENTARIO.any { it.modelo.lowercase().startsWith(prefixo) }
            val deCartao = prefixo.startsWith("/api/cartoes/") && cartoes.any { it.startsWith(prefixo) }
            assertTrue(deRota || deCartao, "prefixo sensível sem rota nem cartão correspondente: $prefixo")
        }
    }

    @Test
    fun integridadeEAcessosSoGerente() = rodar { _, http, _ ->
        val soGerente = INVENTARIO.filter { it.papeis == SO_GERENTE }.map { it.modelo }
        for (obrigatoria in listOf(
            "/api/integridade", "/api/integridade/catalogo", "/api/governanca/acessos", "/api/governanca/configuracao",
            "/api/sync/status", "/api/operacao/alertas",
        )) assertTrue(obrigatoria in soGerente, "$obrigatoria deve ser só do gerente")
        for (modelo in soGerente) {
            val e = INVENTARIO.single { it.modelo == modelo }
            for (usuario in listOf("ana", "lider.alfa", "coord.um")) {
                assertEquals(HttpStatusCode.Forbidden, chamar(http, e, modelo, usuario).status, "$modelo como $usuario")
            }
        }
    }
}
