package br.sidia.painel.seguranca

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.MODULOS_DA_API
import br.sidia.painel.cards.rotaSensivel
import br.sidia.painel.comCsrf
import br.sidia.painel.module
import br.sidia.painel.security.Authenticator
import br.sidia.painel.security.Principal
import br.sidia.painel.security.ROTAS_PUBLICAS
import br.sidia.painel.security.Role
import br.sidia.painel.security.principalFor
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
    private fun rodar(bloco: suspend (rotas: List<Pair<String, String>>, http: HttpClient, c: AppContext) -> Unit) {
        val c = novoContextoTrb1(banco).also { ctx = it }
        val raizes = mutableListOf<Route>()
        testApplication {
            application {
                module(c.settings, c, object : Authenticator {
                    override suspend fun authenticate(call: ApplicationCall): Principal? =
                        call.request.headers["X-Teste-Usuario"]?.let { principalFor(it, Trb1.papeis, HOJE) }
                })
                raizes += routing { }
            }
            startApplication()
            bloco(rotasReais(raizes.single()), client, c)
        }
    }

    private fun folhas(r: Route, acc: MutableList<String>) {
        val filhos = (r as RoutingNode).children
        if (filhos.isEmpty()) acc.add(r.toString()) else filhos.forEach { folhas(it, acc) }
    }

    /** Folhas `(método, modelo)` da árvore de rotas sob `/api`, sem o curinga `/api/{...}` que devolve 404. */
    private fun rotasReais(raiz: Route): List<Pair<String, String>> {
        val folhas = mutableListOf<String>().also { folhas(raiz, it) }
        return folhas.filter { it.startsWith("/api/") && "(method:" in it }
            .map { it.substringAfter("(method:").substringBefore(")") to it.substringBefore("/(method:").replace("/(requireRole)", "") }
            .sortedBy { "${it.second} ${it.first}" }
    }

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
        val reais = rotas.map { "${it.first} ${it.second}" }.toSet()
        val esperadas = INVENTARIO.map { it.chave }.toSet() + PUBLICAS_DA_API
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
        val mutantes = rotas.filter { it.first != "GET" }.map { "${it.first} ${it.second}" }
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
                if (status != HttpStatusCode.Forbidden) passou += papel
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
        val modulosReais = rotas.map { it.second.split('/')[2] }.toSet() - "health"
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
