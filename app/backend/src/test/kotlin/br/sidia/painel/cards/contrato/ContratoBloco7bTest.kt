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
 * Contrato dos cartões do bloco 7b (Distribuição, Rebalanceamento e Qualidade dos dados) sobre os dados de demonstração: o número de cada cartão é o
 * total do seu drill, para todos os papéis que o veem, e cada prefixo novo tem ao menos um par positivo (sem isso "número == drill"
 * seria 0 == 0). SQLite real; nenhuma rede.
 */
class ContratoBloco7bTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()

        val PREFIXOS = listOf("DI", "RB", "QD")
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
        // A demonstração não tem task "a fazer" sem dono nem ninguém acima da capacidade: estas issues fictícias (só deste teste) dão
        // à Distribuição e ao Rebalanceamento números positivos, sem mexer nas fixtures que os outros testes contam.
        for (i in extras()) store.upsert(i.json, i.eventos, Trb1.INSTANCIA, Trb1.CUSTOM_OS_VERSION, null, ctx.clock.instant(), Trb1.ids)
    }

    /**
     * TG-951 e TG-952: "a fazer" sem dono (a primeira pede `skill:android`). TG-961 a TG-964: 40 h cada, "a fazer", do `dev.alfa1`,
     * com vencimento daqui a 3 semanas: 160 h de fila não iniciada para quatro semanas de capacidade, acima do gatilho.
     */
    private fun extras() = listOf(
        Trb1.issue("TG-951", "Checklist", null, "10-21T12", "To Do", labels = listOf("skill:android"), estimativaS = 6 * 3600),
        Trb1.issue("TG-952", "Checklist", null, "10-22T12", "To Do", estimativaS = 4 * 3600),
    ) + (961..964).map {
        Trb1.issue("TG-$it", "Checklist", "dev.alfa1", "10-22T12", "To Do", vence = hoje.plusDays(21).toString(), estimativaS = 40 * 3600)
    }

    @AfterTest
    fun encerrar() = ctx.close()

    private fun entrada(usuario: String) = EntradaDeCartao(ctx, store, principalFor(usuario, diretorio, hoje), Parameters.Empty)

    private fun cartoes(usuario: String) =
        catalogo.visiveisPara(principalFor(usuario, diretorio, hoje).roles).filter { c -> PREFIXOS.any { c.meta.id.startsWith("$it-") } }

    @Test
    fun numeroDoCartaoIgualAoTotalDoDrillParaTodosOsPapeis() {
        var pares = 0
        for (u in listOf("gerente.exemplo", "coord.um", "lider.alfa", "lider.beta")) {
            val e = entrada(u)
            for (c in cartoes(u).filter { it.meta.temDrill }) {
                for ((segmento, valor) in runBlocking { c.valores(e) }) {
                    assertEquals(valor, runBlocking { c.drill(e, segmento) }.size, "${c.meta.id} / $segmento / $u")
                    pares++
                }
            }
        }
        assertTrue(pares >= 8, "poucos pares conferidos: $pares")
        // A lista de movimentos sai em ordem natural de chave, nunca pela ocupação de quem cede (seria um ranking de pessoas).
        val e = entrada("gerente.exemplo")
        val chaves = runBlocking { catalogo.porId("RB-01")!!.drill(e, "movimentos") }.map { it.key }
        assertTrue(chaves.size >= 2, "a fixture precisa de mais de um movimento: $chaves")
        assertEquals(chaves.sortedWith(br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES), chaves)
        // O usuário comum não vê estes cartões.
        assertEquals(emptyList(), cartoes("dev.alfa1").map { it.meta.id })
    }

    @Test
    fun cadaPrefixoNovoTemParPositivoNaDemonstracao() {
        val e = entrada("gerente.exemplo")
        val semPar = mutableListOf<String>()
        for (prefixo in PREFIXOS) {
            val positivos = catalogo.visiveisPara(Role.GERENTE).filter { it.meta.id.startsWith("$prefixo-") && it.meta.temDrill }
                .flatMap { c -> runBlocking { c.valores(e) }.filterValues { it > 0 }.keys.map { c to it } }
            if (positivos.isEmpty()) {
                semPar += prefixo
                continue
            }
            val (cartao, segmento) = positivos.first()
            assertTrue(runBlocking { cartao.drill(e, segmento) }.isNotEmpty(), "${cartao.meta.id} / $segmento")
        }
        assertEquals(emptyList(), semPar, "prefixos sem par positivo")
    }
}
