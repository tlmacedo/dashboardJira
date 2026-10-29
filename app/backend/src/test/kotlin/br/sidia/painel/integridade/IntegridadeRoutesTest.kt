package br.sidia.painel.integridade

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.contrato.CatalogoDto
import br.sidia.painel.cards.contrato.DrillGenericoDto
import br.sidia.painel.config.Settings
import br.sidia.painel.http.ErroDto
import br.sidia.painel.module
import br.sidia.painel.trabalho.RespTrb
import br.sidia.painel.trabalho.Trb1
import br.sidia.painel.trabalho.novoContextoTrb1
import br.sidia.painel.trabalho.rodarTrb1
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import java.net.URLEncoder
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A Integridade pelo HTTP no cenário INT-2: SÓ o gerente, em TODAS as rotas (tela, gaveta e catálogo), toda consulta no
 * `access_log` (inclusive 400, 403 e 404), 503 sem dados se não der para registrar, avisos fixos e a porta do DPO fechada em
 * produção. SQLite real; nenhuma rede, nenhum Jira.
 */
class IntegridadeRoutesTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()

        /** Desenvolvimento offline: a porta do DPO está aberta (dados fictícios). */
        val DEV = Settings(dashboardDevMode = true)

        /** Produção sem aceite do DPO: a porta está FECHADA. */
        val PRODUCAO = Settings(dashboardDevMode = false)
    }

    private var ctx: AppContext? = null

    @AfterTest
    fun encerrar() {
        ctx?.close()
    }

    private fun rodar(settings: Settings = DEV, issues: List<Trb1.IssueDeTeste> = Int2.issues(), bloco: suspend (suspend (String?, String) -> RespTrb) -> Unit) {
        val c = novoContextoTrb1(banco, issues, settings).also { ctx = it }
        rodarTrb1(c, bloco = bloco)
    }

    private fun dto(r: RespTrb) = Json.decodeFromString<IntegridadeDto>(r.corpo)
    private fun gaveta(r: RespTrb) = Json.decodeFromString<DrillGenericoDto>(r.corpo)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private val gerente = "gerente.exemplo"
    private val outros = listOf("coord.um", "lider.alfa", "ana", "bruno", "carla")

    private fun linhasDoAccessLog(): List<Triple<String, String, String>> =
        requireNotNull(ctx!!.dataSource).connection.use { c ->
            c.createStatement().executeQuery("SELECT user_id, route, filters FROM access_log ORDER BY id").use { rs ->
                buildList { while (rs.next()) add(Triple(rs.getString(1), rs.getString(2), rs.getString(3).orEmpty())) }
            }
        }

    @Test
    fun numerosDoCenario() = rodar { get ->
        val d = dto(get(gerente, "/api/integridade"))
        assertEquals(9, d.resumo.evidencias)
        assertEquals(9, d.resumo.issuesAfetadas)
        assertEquals(1, d.resumo.pessoasAlto)
        assertEquals(1, d.resumo.pessoasMedio)
        // Só alto e médio saem na lista, em ordem alfabética, cada um com o nível em texto.
        assertEquals(listOf("ana" to "MEDIO", "bruno" to "ALTO"), d.resumo.pessoas.map { it.pessoa to it.nivel })
        val bruno = d.resumo.pessoas.first { it.pessoa == "bruno" }
        assertEquals(18, bruno.pontos)
        assertEquals(6, bruno.concluidas)
        assertEquals(6, bruno.graves)
        assertEquals(6, bruno.evidencias)
        assertEquals(0.05, d.resumo.medianaTaxa, 1e-9, "a mediana cai no piso")
        // Os doze sinais, na ordem do catálogo, com a contagem de cada um.
        assertEquals(SinalIntegridade.entries.map { it.name }, d.resumo.porSinal.map { it.sinal })
        assertEquals(6, d.resumo.porSinal.first { it.sinal == "FECHAMENTO_EM_LOTE" }.n)
        assertEquals(3, d.resumo.porSinal.first { it.sinal == "VENCIMENTO_PRORROGADO" }.n)
        assertEquals(true, d.resumo.porSinal.first { it.sinal == "ESTIMATIVA_AUMENTADA" }.ativo)
        assertEquals(6, d.resumo.evidenciasNivelAlto)
        assertEquals(3, d.resumo.evidenciasNivelMedio)
        assertEquals("integridade v1", d.versaoRegras)
        assertEquals(9, d.evidencias.size)
        assertTrue(d.evidencias.first { it.sinal == "FECHAMENTO_EM_LOTE" }.pessoa == "bruno")
    }

    @Test
    fun integridadeSoGerenteEmTodasAsRotas() = rodar { get ->
        val rotas = listOf(
            "/api/integridade", "/api/integridade?pessoa=bruno", "/api/integridade/catalogo",
            "/api/cartoes/IN-01/tasks?segmento=todas", "/api/cartoes/IN-02/tasks?segmento=${enc("nivel:ALTO")}",
            "/api/cartoes/IN-05/tasks?segmento=${enc("sinal:FECHAMENTO_EM_LOTE")}",
        )
        for (u in outros) {
            for (rota in rotas) {
                val r = get(u, rota)
                assertEquals(HttpStatusCode.Forbidden, r.status, "$u $rota")
                assertEquals("", r.corpo, "$u $rota: nenhum dado nem código no corpo")
            }
        }
        for (rota in rotas) assertEquals(HttpStatusCode.OK, get(gerente, rota).status, "gerente $rota")
        // O catálogo de cartões não anuncia os cartões da Integridade a quem não é gerente (nem a gaveta os serve).
        for (u in outros) {
            val ids = Json.decodeFromString<CatalogoDto>(get(u, "/api/cartoes/catalogo").corpo).cartoes.map { it.id }
            assertTrue(ids.none { it.startsWith("IN-") }, "$u vê ${ids.filter { it.startsWith("IN-") }}")
        }
        val doGerente = Json.decodeFromString<CatalogoDto>(get(gerente, "/api/cartoes/catalogo").corpo).cartoes.map { it.id }
        assertEquals((1..8).map { "IN-0$it" }, doGerente.filter { it.startsWith("IN-") })
    }

    @Test
    fun todaConsultaGravaAccessLog() = rodar { get ->
        assertEquals(HttpStatusCode.OK, get(gerente, "/api/integridade").status)
        assertEquals(HttpStatusCode.BadRequest, get(gerente, "/api/integridade?de=lixo").status)
        assertEquals(HttpStatusCode.Forbidden, get("coord.um", "/api/integridade").status)
        assertEquals(HttpStatusCode.NotFound, get(gerente, "/api/cartoes/IN-99/tasks?segmento=x").status)
        assertEquals(HttpStatusCode.BadRequest, get(gerente, "/api/cartoes/IN-01/tasks?segmento=${enc("TG-701")}").status)
        assertEquals(HttpStatusCode.Forbidden, get("lider.alfa", "/api/integridade/catalogo").status)
        val linhas = linhasDoAccessLog()
        assertEquals(6, linhas.size, linhas.toString())
        assertEquals(
            listOf("/api/integridade", "/api/integridade", "/api/integridade", "/api/cartoes/{id}/tasks", "/api/cartoes/IN-01/tasks", "/api/integridade/catalogo"),
            linhas.map { it.second },
        )
        assertEquals(listOf(gerente, gerente, "coord.um", gerente, gerente, "lider.alfa"), linhas.map { it.first })
        listOf("200", "400", "403", "404", "400", "403").forEachIndexed { i, status -> assertTrue("\"resultado\":\"$status\"" in linhas[i].third, "${linhas[i]}") }
        // Nenhuma chave de issue vai para a auditoria (nem na rota, nem nos filtros).
        assertTrue(linhas.none { Regex("TG-\\d+").containsMatchIn(it.second + it.third) }, linhas.toString())
    }

    @Test
    fun accessLogNaoGuardaOAchado() = rodar { get ->
        // Enumera todas as pessoas (com e sem indício, e uma que não existe), por gaveta e por filtro da tela.
        val pessoas = listOf("ana", "bruno", "carla", "lider.alfa", "coord.um", "fulano.inexistente")
        val status = mutableSetOf<HttpStatusCode>()
        val tamanhos = mutableMapOf<String, Int>()
        for (p in pessoas) {
            val r = get(gerente, "/api/cartoes/IN-02/tasks?segmento=${enc("pessoa:$p")}")
            status += r.status
            tamanhos[p] = gaveta(r).linhas.size
            assertEquals(HttpStatusCode.OK, get(gerente, "/api/integridade?pessoa=${enc(p)}&sinal=FECHAMENTO_EM_LOTE").status)
        }
        assertEquals(setOf(HttpStatusCode.OK), status, "sem oráculo: pessoa fora de ALTO/MÉDIO também responde 200")
        assertEquals(0, tamanhos.getValue("carla"), "pessoa normal: lista vazia")
        assertEquals(0, tamanhos.getValue("fulano.inexistente"), "pessoa inexistente: lista vazia")
        assertTrue(tamanhos.getValue("bruno") > 0, "quem tem indício continua vendo as tasks")
        get(gerente, "/api/cartoes/IN-02/tasks?segmento=${enc("nivel:ALTO")}")
        get(gerente, "/api/cartoes/IN-05/tasks?segmento=SINAL_QUE_NAO_EXISTE")
        get("coord.um", "/api/cartoes/IN-02/tasks?segmento=${enc("pessoa:bruno")}")
        val linhas = linhasDoAccessLog()
        assertTrue(linhas.isNotEmpty())
        for ((usuario, rota, filtros) in linhas) {
            val resto = "$rota $filtros"
            for (p in pessoas) assertFalse(p in resto, "id de pessoa no access_log: $p em $resto")
            assertFalse("total" in filtros, "contagem do achado no access_log: $filtros")
            assertFalse(Regex("FECHAMENTO_EM_LOTE|SINAL_QUE_NAO_EXISTE").containsMatchIn(filtros), "nome de sinal no access_log: $filtros")
            assertTrue(usuario == gerente || usuario == "coord.um")
        }
        // Continua registrando QUEM consultou o módulo, e só o tipo do segmento.
        assertTrue(linhas.any { it.third.contains("\"segmento\":\"pessoa:*\"") }, linhas.toString())
    }

    @Test
    fun caminhoCodificadoNaoContornaOAccessLog() = rodar { get ->
        val pessoas = listOf("bruno", "ana", "carla")
        val caminhos = listOf(
            "/api/cartoes/IN%2D02/tasks?segmento=pessoa:bruno",
            "/api/cartoes/%49N-02/tasks?segmento=pessoa:bruno",
            "/api/cartoes/%49%4E%2D02/tasks?segmento=pessoa:bruno",
            "/api/%69ntegridade?pessoa=bruno&sinal=FECHAMENTO_EM_LOTE",
            "/api/%49NTEGRIDADE?pessoa=ana&sinal=FECHAMENTO_EM_LOTE",
            "/api/integridade/%2e%2e/integridade?pessoa=carla&sinal=FECHAMENTO_EM_LOTE",
            "/api//integridade?pessoa=bruno&sinal=FECHAMENTO_EM_LOTE",
            "/api/integridade;x=1?pessoa=bruno&sinal=FECHAMENTO_EM_LOTE",
            "/api/cartoes/IN%252D02/tasks?segmento=pessoa:bruno",
            "/api/%252569ntegridade?pessoa=bruno&sinal=FECHAMENTO_EM_LOTE",
            "/api/cartoes/in-02/tasks?segmento=pessoa:bruno",
        )
        for (c in caminhos) get(gerente, c)
        val linhas = linhasDoAccessLog()
        // Caminho que o roteador não casa (dupla codificação, `//`, `;x`) dá 404 sem passar pela rota: não é registrado, e também não vaza.
        assertTrue(linhas.size in 4..caminhos.size, linhas.toString())
        for ((_, rota, filtros) in linhas) {
            val tudo = "$rota $filtros".lowercase()
            for (p in pessoas) assertFalse(p in tudo, "pessoa no access_log: $p em $tudo")
            assertFalse("fechamento_em_lote" in tudo || "total" in tudo, "sinal/contagem no access_log: $tudo")
            assertFalse('%' in rota, "rota codificada no access_log: $rota")
        }
        // O roteador não trata a dupla codificação como outra rota: é 404, sem dado.
        val dupla = get(gerente, "/api/cartoes/IN%252D02/tasks?segmento=pessoa:bruno")
        assertEquals(HttpStatusCode.NotFound, dupla.status)
        assertFalse("TG-" in dupla.corpo)
        // A consulta de auditoria por /api/integridade acha os acessos de verdade a ela (nenhum some do rastro).
        assertTrue(linhas.count { it.second == "/api/integridade" } >= 2, linhas.map { it.second }.toString())
        assertTrue(linhas.count { it.second == "/api/cartoes/IN-02/tasks" } >= 2, linhas.map { it.second }.toString())
    }

    @Test
    fun chaveDeIssueEmMinusculasTambemNaoVaiParaOAccessLog() = rodar { get ->
        // Rota não sensível (Checklist × Review): a máscara vale para qualquer caixa (M7).
        get(gerente, "/api/cartoes/CR-01/tasks?segmento=${enc("tg-701")}")
        get(gerente, "/api/cartoes/CR-01/tasks?segmento=${enc("alerta:Tg-701")}")
        val linhas = linhasDoAccessLog()
        assertEquals(2, linhas.size)
        assertTrue(linhas.none { it.third.contains("701", ignoreCase = true) }, linhas.toString())
    }

    @Test
    fun falhaAoRegistrar503SemDados() = rodar { get ->
        requireNotNull(ctx!!.dataSource).connection.use { it.createStatement().execute("ALTER TABLE access_log RENAME TO access_log_fora") }
        try {
            for (rota in listOf("/api/integridade", "/api/integridade/catalogo", "/api/cartoes/IN-01/tasks?segmento=todas")) {
                val r = get(gerente, rota)
                assertEquals(HttpStatusCode.ServiceUnavailable, r.status, rota)
                assertEquals("", r.corpo, "$rota: nenhum dado sem o registro")
            }
        } finally {
            requireNotNull(ctx!!.dataSource).connection.use { it.createStatement().execute("ALTER TABLE access_log_fora RENAME TO access_log") }
        }
    }

    @Test
    fun avisosFixos() = rodar { get ->
        val d = dto(get(gerente, "/api/integridade"))
        assertEquals("INDICIO_NAO_PROVA", d.aviso)
        assertEquals("VALIDAR_BASE_LEGAL_COM_DPO", d.avisoDpo)
        // Os avisos saem no JSON (o serializador omite campo com valor padrão; estes não têm).
        assertTrue("\"aviso\":\"INDICIO_NAO_PROVA\"" in get(gerente, "/api/integridade?pessoa=carla").corpo)
        val cat = Json.decodeFromString<CatalogoIntegridadeDto>(get(gerente, "/api/integridade/catalogo").corpo)
        assertEquals("INDICIO_NAO_PROVA", cat.aviso)
        assertEquals("VALIDAR_BASE_LEGAL_COM_DPO", cat.avisoDpo)
    }

    @Test
    fun drillTemOMesmoTamanhoDoCard() = rodar { get ->
        val d = dto(get(gerente, "/api/integridade"))
        suspend fun confere(cartao: String, segmento: String, esperado: Int) {
            val g = get(gerente, "/api/cartoes/$cartao/tasks?segmento=${enc(segmento)}")
            assertEquals(HttpStatusCode.OK, g.status, "$cartao $segmento ${g.corpo}")
            assertEquals(esperado, gaveta(g).total, "$cartao $segmento")
            assertEquals(esperado, gaveta(g).linhas.size, "$cartao $segmento")
        }
        confere("IN-01", "todas", d.resumo.evidencias)
        confere("IN-07", "todas", d.resumo.evidencias)
        confere("IN-03", "afetadas", d.resumo.issuesAfetadas)
        for (s in d.resumo.porSinal) confere("IN-05", "sinal:${s.sinal}", s.n)
        confere("IN-02", "nivel:ALTO", d.resumo.evidenciasNivelAlto)
        confere("IN-02", "nivel:MEDIO", d.resumo.evidenciasNivelMedio)
        for (p in d.resumo.pessoas) confere("IN-02", "pessoa:${p.pessoa}", p.evidencias)
        // Cada cartão só abre os seus números; pessoa de nível normal ou inexistente tem gaveta VAZIA (200, sem oráculo).
        assertEquals(HttpStatusCode.BadRequest, get(gerente, "/api/cartoes/IN-05/tasks?segmento=todas").status)
        confere("IN-02", "pessoa:carla", 0)
        // Cartões sem tasks por trás não têm gaveta.
        for (sem in listOf("IN-04", "IN-06", "IN-08")) assertEquals(HttpStatusCode.NotFound, get(gerente, "/api/cartoes/$sem/tasks?segmento=x").status, sem)
    }

    @Test
    fun filtrosRecortamAListaSemMudarONivelDeNinguem() = rodar { get ->
        // Filtrar por sinal ou por pessoa recorta a lista; os níveis seguem os do período inteiro.
        val porSinal = dto(get(gerente, "/api/integridade?sinal=VENCIMENTO_PRORROGADO"))
        assertEquals(3, porSinal.resumo.evidencias)
        assertEquals(listOf("ana", "bruno"), porSinal.resumo.pessoas.map { it.pessoa })
        val soBruno = dto(get(gerente, "/api/integridade?pessoa=bruno"))
        assertEquals(6, soBruno.resumo.evidencias)
        assertEquals(listOf("bruno"), soBruno.resumo.pessoas.map { it.pessoa })
        assertEquals("ALTO", soBruno.resumo.pessoas.single().nivel, "o nível não depende de quem foi filtrado")
        // Pessoa normal: a lista de evidências vem vazia e ninguém é listado.
        val carla = dto(get(gerente, "/api/integridade?pessoa=carla"))
        assertEquals(0, carla.resumo.evidencias)
        assertEquals(emptyList(), carla.resumo.pessoas)
        // Squad: o Beta só tem a carla, sem nenhum indício.
        val beta = dto(get(gerente, "/api/integridade?squad=Beta"))
        assertEquals(0, beta.resumo.evidencias)
        assertEquals(9, dto(get(gerente, "/api/integridade?squad=Alfa")).resumo.evidencias)
        assertEquals(HttpStatusCode.BadRequest, get(gerente, "/api/integridade?squad=Inexistente").status)
        assertEquals(HttpStatusCode.BadRequest, get(gerente, "/api/integridade?sinal=SINAL_INVENTADO").status)
        assertEquals(HttpStatusCode.BadRequest, get(gerente, "/api/integridade?pessoa=${enc("a b")}").status)
    }

    @Test
    fun periodoMaximo180() = rodar { get ->
        // 180 dias (inclusive) passa; 181 não; padrão de 90 dias; datas invertidas ou absurdas são 400, nunca 500.
        assertEquals(HttpStatusCode.OK, get(gerente, "/api/integridade?de=2026-05-04&ate=2026-10-30").status)
        assertEquals(HttpStatusCode.BadRequest, get(gerente, "/api/integridade?de=2026-05-03&ate=2026-10-30").status)
        val padrao = dto(get(gerente, "/api/integridade"))
        assertEquals("2026-10-30", padrao.ate)
        assertEquals("2026-08-02", padrao.de)
        assertEquals(HttpStatusCode.BadRequest, get(gerente, "/api/integridade?de=2026-10-30&ate=2026-10-01").status)
        assertEquals(HttpStatusCode.BadRequest, get(gerente, "/api/integridade?de=+999999999-12-31").status)
        assertEquals(HttpStatusCode.BadRequest, get(gerente, "/api/integridade?ate=1800-01-01").status)
    }

    @Test
    fun catalogoSemPessoas() = rodar { get ->
        val r = get(gerente, "/api/integridade/catalogo")
        val cat = Json.decodeFromString<CatalogoIntegridadeDto>(r.corpo)
        assertEquals(SinalIntegridade.entries.map { it.name }, cat.sinais.map { it.sinal })
        assertEquals("2", cat.sinais.first { it.sinal == "TROCA_ANTES_DE_FECHAR" }.params["horas"])
        assertEquals("CRITICAL, BLOCKER", cat.sinais.first { it.sinal == "LABEL_CRITICIDADE_REMOVIDA" }.params["labels"])
        // Nada de pessoa, de task nem de ranking.
        for (proibido in listOf("\"pessoa", "\"bruno\"", "\"ana\"", "\"carla\"", "\"lider.alfa\"", "TG-", "\"key\"")) assertFalse(proibido in r.corpo, "o catálogo traz '$proibido'")
    }

    @Test
    fun statusSemMapeamentoDaAvisoBloqueanteEZeraTudo() = rodar(issues = Int2.issues() + Trb1.issue("TG-799", "Checklist", "ana", "10-01T12", "Status Inventado", eventos = listOf(Trb1.ev("TG-799", "status", "Open", "Status Inventado", "10-14T12", "ana")))) { get ->
        val r = get(gerente, "/api/integridade")
        assertEquals(HttpStatusCode.OK, r.status)
        val d = dto(r)
        assertTrue(d.avisos.any { it.codigo == AvisoDto.STATUS_SEM_MAPEAMENTO && it.gravidade == AvisoDto.BLOQUEANTE }, d.avisos.toString())
        assertEquals(0, d.resumo.evidencias)
        assertEquals(emptyList(), d.evidencias)
        // A gaveta também responde 200 e vazia (nunca 500).
        assertEquals(0, gaveta(get(gerente, "/api/cartoes/IN-01/tasks?segmento=todas")).total)
    }

    @Test
    fun evidenciaTemSoNumerosEDatasNoDetalhe() = rodar { get ->
        val d = dto(get(gerente, "/api/integridade"))
        val e = d.evidencias.first { it.sinal == "VENCIMENTO_PRORROGADO" }
        assertEquals("ana", e.pessoa)
        assertEquals("2026-10-14", e.dia)
        assertEquals("2026-10-15", e.detalhe["de"])
        assertEquals("2026-10-25", e.detalhe["para"])
        assertEquals("ana", e.detalhe["responsavel"])
        assertNotNull(e.quando)
        // O resumo das tasks (texto livre do Jira) não vai na lista de evidências.
        assertFalse("Task fictícia" in get(gerente, "/api/integridade").corpo)
    }

    // ---- A porta do DPO ----

    @Test
    fun desenvolvimentoSobreBancoComSincronizacaoRealFicaFechado() = rodar { get ->
        // Aberto sobre a demonstração...
        assertEquals(HttpStatusCode.OK, get(gerente, "/api/integridade").status)
        // ...e fechado assim que o banco registra uma sincronização real (a flag do Jira pode ter sido desligada depois).
        requireNotNull(ctx!!.dataSource).connection.use {
            it.createStatement().execute("INSERT INTO sync_run(instance, kind, started_at, finished_at, status) VALUES ('samsung', 'INCREMENTAL', 1, 2, 'OK')")
        }
        for (rota in listOf("/api/integridade", "/api/integridade/catalogo", "/api/cartoes/IN-01/tasks?segmento=todas")) {
            val r = get(gerente, rota)
            assertEquals(HttpStatusCode.Forbidden, r.status, rota)
            assertEquals("AGUARDANDO_ACEITE_DPO", Json.decodeFromString<ErroDto>(r.corpo).codigo, rota)
        }
    }

    @Test
    fun producaoSemAceiteBloqueiaMesmoOGerente()= rodar(PRODUCAO) { get ->
        for (rota in listOf("/api/integridade", "/api/integridade/catalogo", "/api/cartoes/IN-01/tasks?segmento=todas", "/api/integridade?de=lixo")) {
            val r = get(gerente, rota)
            assertEquals(HttpStatusCode.Forbidden, r.status, rota)
            assertEquals("AGUARDANDO_ACEITE_DPO", Json.decodeFromString<ErroDto>(r.corpo).codigo, rota)
            assertFalse("evidencias" in r.corpo || "bruno" in r.corpo || "TG-" in r.corpo, "$rota: nenhum dado")
        }
        // Quem nem é gerente continua com 403 sem código especial (não fica sabendo do módulo).
        assertEquals("", get("coord.um", "/api/integridade").corpo)
        // Cada tentativa ficou no access_log.
        assertEquals(5, linhasDoAccessLog().size)
        assertTrue(linhasDoAccessLog().all { "\"resultado\":\"403\"" in it.third })
    }

    @Test
    fun devModeGerenteVe() = testApplication {
        val c = novoContextoTrb1(banco, Int2.issues(), DEV).also { ctx = it }
        // Sem login (autenticador de desenvolvimento): o principal `dev` é GERENTE.
        application { module(c.settings, c) }
        val r = client.get("/api/integridade")
        assertEquals(HttpStatusCode.OK, r.status)
        val corpo = r.bodyAsText()
        assertEquals(9, Json.decodeFromString<IntegridadeDto>(corpo).resumo.evidencias)
        assertEquals(HttpStatusCode.OK, client.get("/api/integridade/catalogo").status)
        assertEquals(HttpStatusCode.OK, client.get("/api/cartoes/IN-01/tasks?segmento=todas").status)
        assertEquals(3, linhasDoAccessLog().size)
        assertEquals(setOf("dev"), linhasDoAccessLog().map { it.first }.toSet())
    }

    @Test
    fun producaoSemLoginRetorna401() = rodar(PRODUCAO) { get ->
        for (rota in listOf("/api/integridade", "/api/integridade/catalogo", "/api/cartoes/IN-01/tasks?segmento=todas")) {
            assertEquals(HttpStatusCode.Unauthorized, get(null, rota).status, rota)
        }
    }
}
