package br.sidia.painel.distribuicao

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.db.Database
import org.junit.jupiter.api.AfterAll
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Decisões gravadas só no painel (SQLite real): uma vez por sugestão e dia, listadas por data, sem agregar por pessoa. */
class DecisaoStoreTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()
    }

    private fun novoStore(): SqliteDecisaoStore {
        val ds = Database.connect(Database.urlDeArquivo(banco.novoArquivo()), poolSize = 4)
        Database.migrate(ds)
        return SqliteDecisaoStore(ds)
    }

    private val dia = LocalDate.of(2026, 10, 30)
    private val agora = Instant.parse("2026-10-30T12:00:00Z")

    private fun registro(chave: String = "TG-301", decisao: Decisao = Decisao.APROVADA, em: Instant = agora) = RegistroDecisao(
        tipo = TipoDeSugestao.DISTRIBUICAO, issueKey = chave, sugerido = "ana", escolhido = if (decisao == Decisao.REJEITADA) null else "ana",
        decisao = decisao, motivo = null, decididoPor = "lider.alfa", decididoEm = em, versaoRegras = "distribuicao v1",
    )

    private fun chave(issue: String = "TG-301", sugerido: String? = "ana", d: LocalDate = dia, tipo: String = TipoDeSugestao.DISTRIBUICAO) =
        ChaveDaSugestao.de("samsung", tipo, issue, sugerido, "distribuicao v1", d)

    @Test
    fun registraUmaVez() {
        val s = novoStore()
        assertTrue(s.registrar("samsung", registro(), chave()))
        assertEquals(false, s.registrar("samsung", registro(decisao = Decisao.REJEITADA), chave()), "a segunda decisão da mesma sugestão é recusada")
        val lista = s.listar("samsung", agora.minusSeconds(60), agora.plusSeconds(60), null)
        assertEquals(1, lista.size)
        assertEquals(Decisao.APROVADA, lista.single().decisao, "a primeira decisão é a que vale")
        // No dia seguinte a mesma sugestão pode ser decidida de novo (a chave leva o dia de Manaus).
        assertTrue(s.registrar("samsung", registro(), chave(d = dia.plusDays(1))))
    }

    @Test
    fun duasAbasAoMesmoTempoGravamUmaSo() {
        val s = novoStore()
        val pool = Executors.newFixedThreadPool(8)
        val largada = CountDownLatch(1)
        val k = chave("TG-777")
        val resultados = (1..8).map { pool.submit<Boolean> { largada.await(); s.registrar("samsung", registro("TG-777"), k) } }
        largada.countDown()
        val gravaram = resultados.count { it.get(30, TimeUnit.SECONDS) }
        pool.shutdown()
        assertEquals(1, gravaram, "só uma das 8 tentativas simultâneas grava")
        assertEquals(1, s.listar("samsung", agora.minusSeconds(60), agora.plusSeconds(60), null).size)
    }

    @Test
    fun chaveEstavelEQueDiferenciaCadaCampo() {
        val base = chave()
        assertEquals(64, base.length)
        assertTrue(base.all { it in "0123456789abcdef" })
        assertEquals(base, chave(), "mesma entrada, mesma chave")
        assertNotEquals(base, chave(issue = "TG-302"))
        assertNotEquals(base, chave(sugerido = "bruno"))
        assertNotEquals(base, chave(sugerido = null))
        assertNotEquals(base, chave(d = dia.plusDays(1)))
        assertNotEquals(base, chave(tipo = TipoDeSugestao.REBALANCEAMENTO))
        assertNotEquals(base, ChaveDaSugestao.de("outra", TipoDeSugestao.DISTRIBUICAO, "TG-301", "ana", "distribuicao v1", dia))
        assertNotEquals(base, ChaveDaSugestao.de("samsung", TipoDeSugestao.DISTRIBUICAO, "TG-301", "ana", "distribuicao v2", dia))
        // Valor fixo conhecido (calculado fora do código com `sha256sum`): a chave não pode mudar sem aviso, senão as decisões já
        // gravadas deixariam de casar com a sugestão do mesmo dia.
        assertEquals("c1a2c327af405c333949acbe41ade6fd00e9ac31cd8157e3c21d27a8f6ba99d1", base)
    }

    @Test
    fun listaPorDataEPorTasksSemAgregarPorPessoa() {
        val s = novoStore()
        s.registrar("samsung", registro("TG-1", em = Instant.parse("2026-10-28T12:00:00Z")), chave("TG-1"))
        s.registrar("samsung", registro("TG-2", Decisao.TROCADA, Instant.parse("2026-10-29T12:00:00Z")).copy(motivo = "competência"), chave("TG-2"))
        s.registrar("samsung", registro("TG-3", Decisao.REJEITADA, Instant.parse("2026-10-30T12:00:00Z")), chave("TG-3"))
        s.registrar("outra", registro("TG-4"), chave("TG-4"))
        val tudo = s.listar("samsung", Instant.parse("2026-10-28T00:00:00Z"), Instant.parse("2026-10-31T00:00:00Z"), null)
        assertEquals(listOf("TG-3", "TG-2", "TG-1"), tudo.map { it.issueKey }, "da mais recente para a mais antiga; outra instância fora")
        assertEquals("competência", tudo[1].motivo)
        // `ate` é exclusivo e `de` inclusivo.
        assertEquals(listOf("TG-2"), s.listar("samsung", Instant.parse("2026-10-29T12:00:00Z"), Instant.parse("2026-10-30T12:00:00Z"), null).map { it.issueKey })
        assertEquals(listOf("TG-1"), s.listar("samsung", Instant.parse("2026-10-28T00:00:00Z"), Instant.parse("2026-10-31T00:00:00Z"), setOf("TG-1", "TG-9")).map { it.issueKey })
        assertEquals(1, s.listar("samsung", Instant.parse("2026-10-28T00:00:00Z"), Instant.parse("2026-10-31T00:00:00Z"), null, limite = 1).size)
        assertEquals(emptyList(), s.listar("samsung", Instant.parse("2026-10-28T00:00:00Z"), Instant.parse("2026-10-31T00:00:00Z"), emptySet()))
    }

    @Test
    fun decididasPorChave() {
        val s = novoStore()
        s.registrar("samsung", registro("TG-1"), chave("TG-1"))
        s.registrar("samsung", registro("TG-2", Decisao.REJEITADA), chave("TG-2"))
        val r = s.decididas("samsung", listOf(chave("TG-1"), chave("TG-2"), chave("TG-3")))
        assertEquals(mapOf(chave("TG-1") to Decisao.APROVADA, chave("TG-2") to Decisao.REJEITADA), r)
        assertEquals(emptyMap(), s.decididas("samsung", emptyList()))
        assertEquals(emptyMap(), s.decididas("outra", listOf(chave("TG-1"))))
    }

    @Test
    fun restricoesDoBancoRecusamValorForaDaLista() {
        val s = novoStore()
        val ds = Database.connect(Database.urlDeArquivo(banco.novoArquivo()), poolSize = 2).also { Database.migrate(it) }
        ds.connection.use { c ->
            val erro = runCatching {
                c.createStatement().executeUpdate(
                    "INSERT INTO decisao_distribuicao(instance, tipo, issue_key, decisao, decidido_por, decidido_em, versao_regras, chave_sugestao) " +
                        "VALUES ('samsung', 'ESCREVER_NO_JIRA', 'TG-1', 'APROVADA', 'x', 0, 'v', 'k')",
                )
            }
            assertTrue(erro.isFailure, "o tipo fora da lista é recusado pelo CHECK")
        }
        assertTrue(s.listar("samsung", agora, agora.plusSeconds(1), null).isEmpty())
    }
}
