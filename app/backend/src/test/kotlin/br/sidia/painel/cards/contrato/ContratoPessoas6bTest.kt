package br.sidia.painel.cards.contrato

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.config.Settings
import br.sidia.painel.demo.DadosDeDemonstracao
import br.sidia.painel.demo.lerManifestoDemo
import br.sidia.painel.security.AtribuicaoPapel
import br.sidia.painel.security.Pessoa
import br.sidia.painel.security.Role
import br.sidia.painel.security.RoleDirectory
import br.sidia.painel.security.Vigencia
import br.sidia.painel.security.principalFor
import br.sidia.painel.sync.SqliteIssueStore
import br.sidia.painel.testContextComBanco
import br.sidia.painel.trabalho.Trb1
import io.ktor.http.Parameters
import kotlinx.coroutines.runBlocking
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
 * Contrato dos cartões do bloco 6b sobre os dados de demonstração: o número de cada cartão é o total do seu drill, para todos os
 * papéis, e cada tela nova tem ao menos um par positivo (sem isso "número == drill" seria 0 == 0). SQLite real; nenhuma rede.
 */
class ContratoPessoas6bTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()

        /** Prefixos dos cartões novos do 6b e se cada um deve ter um par positivo na demonstração. */
        val PREFIXOS = listOf("PS", "EF", "CB", "CM", "RV", "CG", "SI")
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
        // A demonstração não tem review aberta, label `skill:x` nem fila com horas: estas issues fictícias (só deste teste) dão
        // a cada tela nova um número positivo, sem mexer nas fixtures que os outros testes contam.
        for (i in extras()) store.upsert(i.json, i.eventos, Trb1.INSTANCIA, Trb1.CUSTOM_OS_VERSION, null, ctx.clock.instant(), Trb1.ids)
    }

    /**
     * TG-901: review do `dev.alfa1` criada em 20/10 e vencida em 28/10 (pendente, vencida e fora do prazo). TG-902 e TG-903: tasks
     * que pedem `skill:android` (o `dev.alfa1` tem) e `skill:cobol` (ninguém tem). TG-904: 4 h, a mais prioritária e com vencimento
     * próximo do `dev.alfa1`: uma ausência longa dele a faz atrasar.
     */
    private fun extras() = listOf(
        Trb1.issue("TG-901", "Checklist Review", "dev.alfa1", "10-20T12", "In Progress", vence = "2026-10-28", eventos = Trb1.trilha("TG-901", "To Do", "10-21T12" to "In Progress")),
        Trb1.issue("TG-902", "Checklist", "dev.alfa1", "10-21T12", "To Do", labels = listOf("skill:android"), estimativaS = 3600),
        Trb1.issue("TG-903", "Checklist", "dev.alfa2", "10-21T12", "To Do", labels = listOf("skill:cobol"), estimativaS = 3600),
        Trb1.issue(
            "TG-904", "Checklist", "dev.alfa1", "10-22T12", "In Progress", vence = "2026-11-04", prioridade = "Highest", estimativaS = 4 * 3600,
            eventos = Trb1.trilha("TG-904", "To Do", "10-23T12" to "In Progress"),
        ),
    )

    /** Parâmetros de URL que o cartão precisa para ter número (o Simulador só calcula com pessoa e período). */
    private fun parametrosDo(prefixo: String): Parameters = when (prefixo) {
        "SI" -> Parameters.build {
            append("pessoa", "dev.alfa1")
            append("de", hoje.plusDays(1).toString())
            append("ate", hoje.plusDays(40).toString())
        }
        else -> Parameters.Empty
    }

    @AfterTest
    fun encerrar() = ctx.close()

    private fun entrada(usuario: String, params: Parameters = Parameters.Empty) =
        EntradaDeCartao(ctx, store, principalFor(usuario, diretorio, hoje), params)

    private fun cartoes(usuario: String) =
        catalogo.visiveisPara(principalFor(usuario, diretorio, hoje).roles).filter { c -> PREFIXOS.any { c.meta.id.startsWith("$it-") } }

    @Test
    fun numeroDoCartaoIgualAoTotalDoDrillParaTodosOsPapeis() {
        var pares = 0
        for (u in listOf("gerente.exemplo", "coord.um", "lider.alfa", "lider.beta", "dev.alfa1", "dev.beta1")) {
            val e = entrada(u)
            for (c in cartoes(u).filter { it.meta.temDrill }) {
                for ((segmento, valor) in runBlocking { c.valores(e) }) {
                    assertEquals(valor, runBlocking { c.drill(e, segmento) }.size, "${c.meta.id} / $segmento / $u")
                    pares++
                }
            }
        }
        assertTrue(pares > 0, "nenhum par conferido")
    }

    @Test
    fun cadaTelaNovaTemParPositivoNaDemonstracao() {
        val semPar = mutableListOf<String>()
        for (prefixo in PREFIXOS) {
            val e = entrada("gerente.exemplo", parametrosDo(prefixo))
            val positivos = catalogo.visiveisPara(Role.GERENTE).filter { it.meta.id.startsWith("$prefixo-") && it.meta.temDrill }
                .flatMap { c -> runBlocking { c.valores(e) }.filterValues { it > 0 }.keys.map { c to it } }
            if (positivos.isEmpty()) {
                semPar += prefixo
                continue
            }
            val (cartao, segmento) = positivos.first()
            assertTrue(runBlocking { cartao.drill(e, segmento) }.isNotEmpty(), "${cartao.meta.id} / $segmento")
        }
        assertEquals(emptyList(), semPar, "telas sem par positivo na demonstração")
    }
}
