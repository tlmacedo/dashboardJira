package br.sidia.painel.cards.contrato

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.fluxo.comLinks
import br.sidia.painel.config.Settings
import br.sidia.painel.demo.DadosDeDemonstracao
import br.sidia.painel.demo.lerManifestoDemo
import br.sidia.painel.security.AtribuicaoPapel
import br.sidia.painel.security.Pessoa
import br.sidia.painel.security.Role
import br.sidia.painel.security.RoleDirectory
import br.sidia.painel.security.Vigencia
import br.sidia.painel.security.principalFor
import br.sidia.painel.sync.IssueLinks
import br.sidia.painel.sync.SqliteIssueStore
import br.sidia.painel.testContextComBanco
import br.sidia.painel.trabalho.Trb1
import io.ktor.http.Parameters
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.AfterAll
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contrato dos cartões do Checklist × Review (CR-nn) sobre os dados de demonstração: o número de cada cartão é o total do seu
 * drill, para todos os papéis, e há par positivo em cada cartão com tasks por trás (sem isso "número == drill" seria 0 == 0).
 * A demonstração só tem pares Task × Review (o lado task é do tipo Task), então as issues fictícias abaixo (só deste teste)
 * dão ao Checklist × Review um par de cada alerta sem mexer nas fixtures que os outros testes contam. SQLite real; nenhuma rede.
 */
class ContratoChecklistReviewTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()
    }

    private val zona = ZoneId.of("America/Manaus")
    private val manifesto = lerManifestoDemo("demo")
    private val relogio: Clock = Clock.fixed(manifesto.ancora.plusDays(1).atTime(12, 0).atZone(zona).toInstant(), zona)
    private val hoje: LocalDate = LocalDate.now(relogio)

    private fun pessoa(id: String, papel: Role, squad: String? = null) = Pessoa(id, id, listOf(AtribuicaoPapel(papel, Vigencia(null, null), squad)))

    private val diretorio = RoleDirectory(
        listOf(
            pessoa("dev.alfa1", Role.USUARIO, "Alfa"), pessoa("dev.alfa2", Role.USUARIO, "Alfa"), pessoa("lider.alfa", Role.SQUAD_LEADER, "Alfa"),
            pessoa("dev.beta1", Role.USUARIO, "Beta"), pessoa("lider.beta", Role.SQUAD_LEADER, "Beta"),
            pessoa("coord.um", Role.COORDENADOR), pessoa("gerente.exemplo", Role.GERENTE),
        ),
    )

    private lateinit var ctx: AppContext
    private lateinit var store: SqliteIssueStore
    private val catalogo = catalogoPadrao()

    @BeforeTest
    fun preparar() {
        ctx = testContextComBanco(Settings(dashboardDevMode = false), banco, diretorio, relogio)
        val ds = requireNotNull(ctx.dataSource)
        store = SqliteIssueStore(ds)
        DadosDeDemonstracao(ds, store, ctx.clock, ctx.settings.zone).carregar()
        for (i in extras()) {
            val chave = (i.json["key"] as JsonPrimitive).content
            store.upsert(i.json, i.eventos, Trb1.INSTANCIA, Trb1.CUSTOM_OS_VERSION, IssueLinks.ler(chave, i.json), ctx.clock.instant(), Trb1.ids)
        }
    }

    /**
     * TG-951/TG-952: checklist e review do mesmo responsável, review com vencimento depois do pai, resolvida depois do pai e do
     * prazo dele (mesma pessoa, vence depois, pai fechado antes, review atrasada, atraso em cadeia, 24 h ativas). TG-953: checklist
     * sem review. TG-954/TG-955: par ainda aberto de outro squad.
     */
    private fun extras() = listOf(
        Trb1.issue(
            "TG-951", "Checklist", "dev.alfa1", "10-20T12", "Done", resolution = "Done", resolvida = "10-27T12", vence = "2026-10-26",
            labels = listOf("ui"), eventos = Trb1.trilha("TG-951", "To Do", "10-21T12" to "In Progress", "10-27T12" to "Done"),
        ).comLinks(bloqueadaPor = listOf("TG-952")),
        Trb1.issue(
            "TG-952", "Checklist Review", "dev.alfa1", "10-21T12", "Done", resolution = "Done", resolvida = "10-29T12", vence = "2026-10-28",
            labels = listOf("rede"), eventos = Trb1.trilha("TG-952", "To Do", "10-28T12" to "In Progress", "10-29T12" to "Done"),
        ),
        Trb1.issue("TG-953", "Checklist", "dev.alfa2", "10-22T12", "To Do", vence = "2026-11-10", labels = listOf("ui")),
        Trb1.issue(
            "TG-954", "Checklist", "dev.beta1", "10-22T12", "In Progress", vence = "2026-11-10",
            eventos = Trb1.trilha("TG-954", "To Do", "10-23T12" to "In Progress"),
        ).comLinks(bloqueia = listOf("TG-955")),
        Trb1.issue("TG-955", "Checklist Review", "lider.beta", "10-22T12", "To Do", vence = "2026-10-27"),
    )

    @AfterTest
    fun encerrar() = ctx.close()

    private fun entrada(usuario: String) = EntradaDeCartao(ctx, store, principalFor(usuario, diretorio, hoje), Parameters.Empty)

    private fun cartoes(usuario: String) =
        catalogo.visiveisPara(principalFor(usuario, diretorio, hoje).roles).filter { it.meta.id.startsWith("CR-") }

    @Test
    fun numeroDoCartaoIgualAoTotalDoDrillParaTodosOsPapeis() {
        var pares = 0
        for (u in listOf("gerente.exemplo", "coord.um", "lider.alfa", "lider.beta", "dev.alfa1", "dev.alfa2", "dev.beta1")) {
            val e = entrada(u)
            for (c in cartoes(u).filter { it.meta.temDrill }) {
                for ((segmento, valor) in runBlocking { c.valores(e) }) {
                    assertEquals(valor, runBlocking { c.drill(e, segmento) }.size, "${c.meta.id} / $segmento / $u")
                    pares++
                }
            }
        }
        assertTrue(pares > 20, "poucos pares conferidos: $pares")
    }

    @Test
    fun cadaCartaoComDrillTemParPositivo() {
        val e = entrada("gerente.exemplo")
        val semPar = mutableListOf<String>()
        for (c in cartoes("gerente.exemplo").filter { it.meta.temDrill }) {
            val positivos = runBlocking { c.valores(e) }.filterValues { it > 0 }
            if (positivos.isEmpty()) {
                semPar += c.meta.id
                continue
            }
            val (segmento, valor) = positivos.entries.first()
            assertEquals(valor, runBlocking { c.drill(e, segmento) }.size, "${c.meta.id} / $segmento")
        }
        assertEquals(emptyList(), semPar, "cartões sem par positivo")
        // Os treze cartões do Checklist × Review estão no catálogo, um deles (aprendizados) sem tasks por trás.
        assertEquals(13, cartoes("gerente.exemplo").size)
        assertEquals(listOf("CR-10"), cartoes("gerente.exemplo").filter { !it.meta.temDrill }.map { it.meta.id })
    }
}
