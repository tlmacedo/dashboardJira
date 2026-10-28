package br.sidia.painel.cards.fluxo

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.db.Database
import br.sidia.painel.sync.SqliteIssueLinkStore
import br.sidia.painel.sync.VinculosConfig
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.Situacao
import br.sidia.painel.trabalho.SqliteWorkStore
import br.sidia.painel.trabalho.Trb1
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Bloqueios entre tasks (I-08) sobre o cenário BQ-1: o que bloqueia, o que não conta e o que fica escondido. */
class BloqueiosCalculatorTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()

        private val config = VinculosConfig(versao = 1, tipoDeLink = "Blocks", tiposReview = setOf("Checklist Review"))
    }

    private lateinit var ds: DataSource

    @BeforeTest
    fun preparar() {
        ds = Database.connect(Database.urlDeArquivo(banco.novoArquivo()), poolSize = 4)
        Database.migrate(ds)
        Bq1.carregar(ds, Bq1.issues())
    }

    @AfterTest
    fun encerrar() {
        (ds as AutoCloseable).close()
    }

    /** Todos veem todos, a menos que se diga o contrário. */
    private fun analisar(
        cfg: VinculosConfig = config, visivel: (String?) -> Boolean = { true }, soDe: Set<String>? = null,
    ): BloqueiosAnalise {
        val abertas = SqliteWorkStore(ds).snapshots(ConsultaDeTrabalho(Trb1.INSTANCIA, null, Situacao.ABERTAS))
            .filter { soDe == null || it.assignee in soDe }
        val vinculos = SqliteIssueLinkStore(ds).ativos(Trb1.INSTANCIA, cfg.tipoDeLink)
        val pontas = LeitorDePontas(ds).ler(Trb1.INSTANCIA, vinculos.flatMap { listOf(it.fromKey, it.toKey) }.toSet())
        return BloqueiosCalculator(Trb1.statusMap, cfg).analisar(abertas, vinculos, pontas, visivel)
    }

    private fun chaves(l: List<br.sidia.painel.trabalho.IssueSnapshot>) = l.map { it.key }

    @Test
    fun numerosDoCenario() {
        val a = analisar()
        assertEquals(listOf("TG-801", "TG-803", "TG-811"), chaves(a.bloqueadas))
        assertEquals(listOf("TG-802", "TG-804", "TG-812"), a.bloqueadorasVisiveis.map { it.key })
        assertEquals(listOf("TG-801"), chaves(a.prazoEmConflito))
        assertEquals(listOf("TG-809"), chaves(a.verificarNoJira))
        assertEquals(listOf("TG-805", "TG-810"), chaves(a.semCausa))
        assertEquals(listOf(3, 3, 0, 1, 1, 2), with(a.dto) { listOf(bloqueadas, bloqueadoras, bloqueadorasForaDaVisao, prazoEmConflito, verificarNoJira, semCausa) })
        assertEquals("Blocks", a.dto.tipoDeLink)
    }

    @Test
    fun excluiOsParesTaskReview() {
        // TG-807 (Review) bloqueada por TG-808 (task) é o par que a Divergência já trata: nem lista, nem conta.
        val a = analisar()
        assertTrue("TG-807" !in chaves(a.bloqueadas) && "TG-808" !in a.bloqueadorasVisiveis.map { it.key })
        assertTrue(a.dto.linhas.none { it.bloqueada == "TG-807" || it.bloqueadora.key == "TG-808" })
    }

    @Test
    fun soVinculosNaoRemovidos() {
        // TG-814 foi bloqueada por TG-802; o vínculo some no Jira e o sincronizador marca removida_em.
        assertTrue("TG-814" !in chaves(analisar().bloqueadas))
        Bq1.carregar(ds, listOf(Bq1.comOVinculoDaTG814()))
        assertTrue("TG-814" in chaves(analisar().bloqueadas))
        Bq1.carregar(ds, listOf(Bq1.semOVinculoDaTG814()))
        val a = analisar()
        assertTrue("TG-814" !in chaves(a.bloqueadas))
        assertTrue(a.dto.linhas.none { it.bloqueada == "TG-814" })
    }

    @Test
    fun bloqueadoraConcluidaNaoBloqueia() {
        val a = analisar()
        assertTrue("TG-805" !in chaves(a.bloqueadas))
        assertTrue("TG-806" !in a.bloqueadorasVisiveis.map { it.key })
        assertTrue(a.dto.linhas.none { it.bloqueadora.key == "TG-806" })
    }

    @Test
    fun prazoEmConflitoQuandoBloqueadoraVenceDepois() {
        val a = analisar()
        val l801 = a.dto.linhas.single { it.bloqueada == "TG-801" }
        assertEquals(listOf("2026-10-28", "2026-11-03", true), listOf(l801.vence, l801.bloqueadora.vence, l801.bloqueadora.conflito))
        // TG-803 vence depois da bloqueadora: sem conflito. TG-811: a bloqueadora não tem vencimento (informativo, não conflito).
        assertFalse(a.dto.linhas.single { it.bloqueada == "TG-803" }.bloqueadora.conflito)
        val l811 = a.dto.linhas.single { it.bloqueada == "TG-811" }
        assertEquals(listOf(false, true), listOf(l811.bloqueadora.conflito, l811.bloqueadora.semVencimento))
        assertTrue(a.detalhePorBloqueada.getValue("TG-801").contains("vence depois"), a.detalhePorBloqueada.getValue("TG-801"))
    }

    @Test
    fun bloqueadoraForaDaVisaoApareceComoOutroSquadSemChaveNemNome() {
        // A ana só vê as próprias: TG-802 (bruno) e TG-812 (carla) são de outras pessoas.
        val a = analisar(visivel = { it == "ana" }, soDe = setOf("ana"))
        val l801 = a.dto.linhas.single { it.bloqueada == "TG-801" }
        assertEquals(EstadoDaBloqueadora.OUTRO_SQUAD, l801.bloqueadora.estado)
        assertNull(l801.bloqueadora.key)
        assertEquals("2026-11-03", l801.bloqueadora.vence)
        assertTrue(l801.bloqueadora.conflito, "o conflito vale mesmo com a bloqueadora escondida")
        val json = Json.encodeToString(a.dto)
        for (escondido in listOf("TG-802", "TG-812", "bruno", "carla")) assertFalse(escondido in json, "$escondido na resposta")
        assertTrue(a.detalhePorBloqueada.values.none { "TG-802" in it || "TG-812" in it || "bruno" in it || "carla" in it })
        assertTrue(a.detalhePorBloqueada.getValue("TG-801").contains("outro squad"))
        // Para a ana, nenhuma bloqueadora está ao alcance: o número é 0 e as duas escondidas ficam à parte.
        assertEquals(listOf(2, 0, 2), listOf(a.dto.bloqueadas, a.dto.bloqueadoras, a.dto.bloqueadorasForaDaVisao))
        assertTrue(a.bloqueadorasVisiveis.isEmpty())
    }

    @Test
    fun pontaForaDaBaseViraVerificarNoJira() {
        val a = analisar()
        val linha = a.dto.linhas.single { it.bloqueada == "TG-809" }
        assertEquals(EstadoDaBloqueadora.FORA_DA_BASE, linha.bloqueadora.estado)
        assertEquals("TG-9999", linha.bloqueadora.key)
        assertNull(linha.bloqueadora.vence)
        // Não conta como bloqueada (a outra ponta pode estar concluída), mas aparece à parte.
        assertTrue("TG-809" !in chaves(a.bloqueadas))
        assertTrue("TG-809" !in chaves(a.semCausa), "tem vínculo, ainda que a ponta esteja fora da base")
        assertTrue(a.dto.avisos.any { it.codigo == AvisosDoFluxo.BLOQUEIO_FORA_DA_BASE })
    }

    @Test
    fun semRankingDeBloqueadoras() {
        // A TG-802 passa a bloquear mais de uma task; nada na resposta conta quantas, nem ordena por isso.
        Bq1.carregar(ds, listOf(Bq1.comOVinculoDaTG814()))
        val a = analisar()
        val json = Json.encodeToString(a.dto)
        assertFalse(Regex("\"(bloqueou|vezes|ranking|posicao|total)").containsMatchIn(json), json)
        // A lista sai pelo vencimento da bloqueada (sem vencimento por último), depois pela chave, nunca por quem bloqueia:
        // TG-801 (28/10), TG-803 (02/11), TG-811 (05/11), e as sem vencimento (TG-809 fora da base, TG-814) por chave.
        assertEquals(listOf("TG-801", "TG-803", "TG-811", "TG-809", "TG-814"), a.dto.linhas.map { it.bloqueada })
        // As bloqueadoras ao alcance saem em ordem natural de chave.
        assertEquals(listOf("TG-802", "TG-804", "TG-812"), a.bloqueadorasVisiveis.map { it.key })
    }

    @Test
    fun desempateDasLinhasNaoUsaAChaveEscondida() {
        // TG-814 é bloqueada por TG-802 (vence 03/11) e TG-804 (vence 30/10), ambas de pessoas fora da visão da ana.
        // Pela chave, TG-802 viria primeiro; o desempate vem do vencimento, que a linha mostra, e não da chave escondida.
        val dupla = Trb1.issue(
            "TG-814", "Checklist", "ana", "10-20T12", "In Progress", eventos = Trb1.trilha("TG-814", "Open", "10-26T12" to "In Progress"),
        ).comLinks(bloqueadaPor = listOf("TG-802", "TG-804"))
        Bq1.carregar(ds, listOf(dupla))
        val a = analisar(visivel = { it == "ana" })
        val doDuplo = a.dto.linhas.filter { it.bloqueada == "TG-814" }
        assertEquals(listOf(null, null), doDuplo.map { it.bloqueadora.key })
        assertEquals(listOf("2026-10-30", "2026-11-03"), doDuplo.map { it.bloqueadora.vence })
    }

    @Test
    fun pontaRemovidaLogicamenteDeForaDaVisaoNaoMostraAChave() {
        // TG-815 (ana) é bloqueada por TG-820 (carla), que foi removida logicamente: vira "verificar no Jira".
        val bloqueada = Trb1.issue(
            "TG-815", "Checklist", "ana", "10-20T12", "In Progress", eventos = Trb1.trilha("TG-815", "Open", "10-26T12" to "In Progress"),
        ).comLinks(bloqueadaPor = listOf("TG-820"))
        val removida = Trb1.issue("TG-820", "Checklist", "carla", "10-20T12", "In Progress", eventos = Trb1.trilha("TG-820", "Open", "10-26T12" to "In Progress"))
        Bq1.carregar(ds, listOf(bloqueada, removida))
        ds.connection.use { it.createStatement().use { st -> st.executeUpdate("UPDATE issue SET removida_em = 1793275200000 WHERE key = 'TG-820'") } }

        val daAna = analisar(visivel = { it == "ana" })
        assertTrue("TG-815" in chaves(daAna.verificarNoJira), "continua em verificar no Jira")
        assertNull(daAna.dto.linhas.single { it.bloqueada == "TG-815" }.bloqueadora.key, "a chave de pessoa fora da visão não aparece")
        assertFalse("TG-820" in daAna.detalhePorBloqueada.getValue("TG-815"), daAna.detalhePorBloqueada.getValue("TG-815"))
        assertFalse("TG-820" in Json.encodeToString(daAna.dto))

        // Quem enxerga a carla vê a chave, como antes.
        val completa = analisar()
        assertEquals("TG-820", completa.dto.linhas.single { it.bloqueada == "TG-815" }.bloqueadora.key)
    }

    @Test
    fun bloqueadaSemCausaRegistradaNaQualidadeDosDados() {
        val a = analisar()
        // Status bloqueado com a única bloqueadora já concluída (TG-805) e flag de impedimento sem vínculo (TG-810).
        assertEquals(listOf("TG-805", "TG-810"), chaves(a.semCausa))
        assertTrue(BloqueiosCalculator.CODIGO_SEM_CAUSA == "BLOQUEADA_SEM_CAUSA")
        // Quem tem uma bloqueadora aberta tem causa, ainda que seja o par Task x Review.
        assertTrue("TG-801" !in chaves(a.semCausa) && "TG-807" !in chaves(a.semCausa))
    }

    @Test
    fun tipoEDirecaoVemDaMatriz() {
        // O tipo do vínculo vem da configuração: com "Relates" só a TG-813 (e o inverso, ligada à TG-802) conta.
        val relates = analisar(cfg = VinculosConfig(versao = 1, tipoDeLink = "Relates", tiposReview = setOf("Checklist Review")))
        assertEquals(listOf("TG-813"), chaves(relates.bloqueadas))
        assertEquals("Relates", relates.dto.tipoDeLink)
        // A direção é a do Jira: from_key bloqueia to_key. TG-802 nunca é "bloqueada" por TG-801.
        assertTrue("TG-802" !in chaves(analisar().bloqueadas))
        // Sem nenhum vínculo do tipo, o aviso diz que os vínculos não estão na base.
        val nenhum = analisar(cfg = VinculosConfig(versao = 1, tipoDeLink = "Inexistente", tiposReview = setOf("Checklist Review")))
        assertEquals(0, nenhum.dto.bloqueadas)
        assertTrue(nenhum.dto.avisos.any { it.codigo == AvisosDoFluxo.SEM_VINCULOS_NA_BASE })
    }
}
