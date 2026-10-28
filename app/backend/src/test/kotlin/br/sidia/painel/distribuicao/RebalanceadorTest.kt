package br.sidia.painel.distribuicao

import br.sidia.painel.pessoas.CargaDaPessoa
import br.sidia.painel.pessoas.Ocupacao
import br.sidia.painel.pessoas.SemanaDeCarga
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.SnapDeTeste
import br.sidia.painel.trabalho.Trb1
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Motor de rebalanceamento (modo sombra): gatilho de 110%, só não iniciadas, competência do destino, histerese de 20 pp. */
class RebalanceadorTest {
    private val motor = Rebalanceador(RegrasDeDistribuicao.PADRAO)
    private val segunda = LocalDate.of(2026, 10, 26)

    /** Uma pessoa com a demanda e a capacidade do horizonte (uma semana basta: o motor soma o horizonte). */
    private fun carga(pessoa: String, demandaH: Double, capacidadeH: Double): CargaDaPessoa {
        val ocupacao = if (capacidadeH <= 0.0) Ocupacao.SemCapacidade else Ocupacao.Valor(demandaH / capacidadeH)
        return CargaDaPessoa(pessoa, listOf(SemanaDeCarga(segunda, demandaH, capacidadeH, ocupacao)), 0.0, false, ocupacao)
    }

    private fun item(chave: String, horas: Double) = SnapDeTeste.snap(chave, resp = "ana") to horas
    private val semExigencia: (IssueSnapshot) -> Set<String> = { emptySet() }

    private fun mapa(vararg c: CargaDaPessoa) = c.associateBy { it.pessoa }

    @Test
    fun moveDaSobrecarregada() {
        val r = motor.calcular(
            mapa(carga("ana", 60.0, 40.0), carga("bruno", 16.0, 40.0)),
            mapOf("ana" to listOf(item("TG-1", 10.0))),
            mapOf("ana" to setOf("android"), "bruno" to setOf("android")),
            { setOf("android") },
        )
        val m = r.single()
        assertEquals("TG-1", m.issueKey)
        assertEquals("ana", m.de)
        assertEquals("bruno", m.para)
        assertEquals(10.0, m.horas)
        assertEquals(1.5, m.ocupacaoOrigemAntes, 1e-9)
        assertEquals(1.25, m.ocupacaoOrigemDepois, 1e-9)
        assertEquals(0.65, m.ocupacaoDestinoDepois, 1e-9)
    }

    @Test
    fun naoMoveIniciada() {
        // O motor só recebe as não iniciadas: sem elas, nada se move, por mais carregada que esteja a origem.
        val r = motor.calcular(mapa(carga("ana", 60.0, 40.0), carga("bruno", 16.0, 40.0)), mapOf("ana" to emptyList()), emptyMap(), semExigencia)
        assertTrue(r.isEmpty())
        // E quem é "não iniciada": a fazer E nunca passou por status ativo (uma task reaberta já foi iniciada).
        val aFazer = SnapDeTeste.snap("TG-1", status = "Open")
        val emAndamento = SnapDeTeste.snap("TG-2", status = "In Progress", eventos = SnapDeTeste.trilha("TG-2", "Open", "2026-10-02T12:00:00Z" to "In Progress"))
        val reaberta = SnapDeTeste.snap(
            "TG-3", status = "Open",
            eventos = SnapDeTeste.trilha("TG-3", "Open", "2026-10-02T12:00:00Z" to "In Progress", "2026-10-03T12:00:00Z" to "Open"),
        )
        val bloqueada = SnapDeTeste.snap("TG-4", status = "On Hold")
        assertEquals(listOf("TG-1"), Rebalanceador.naoIniciadas(listOf(aFazer, emAndamento, reaberta, bloqueada), Trb1.statusMap).map { it.key })
    }

    @Test
    fun respeitaCompetencia() {
        val cargas = mapa(carga("ana", 60.0, 40.0), carga("bruno", 16.0, 40.0), carga("carla", 20.0, 40.0))
        val itens = mapOf("ana" to listOf(item("TG-1", 10.0)))
        // Só a carla tem a competência que a task pede, mesmo estando mais ocupada que o bruno.
        val comCarla = motor.calcular(cargas, itens, mapOf("bruno" to setOf("rede"), "carla" to setOf("android")), { setOf("android") })
        assertEquals("carla", comCarla.single().para)
        // Ninguém tem: nenhum movimento.
        assertTrue(motor.calcular(cargas, itens, mapOf("bruno" to setOf("rede")), { setOf("android") }).isEmpty())
    }

    @Test
    fun destinoComMenorOcupacaoDepoisEEmpateAlfabetico() {
        val cargas = mapa(carga("ana", 60.0, 40.0), carga("bruno", 16.0, 40.0), carga("carla", 16.0, 40.0), carga("dora", 8.0, 40.0))
        val itens = mapOf("ana" to listOf(item("TG-1", 10.0)))
        assertEquals("dora", motor.calcular(cargas, itens, emptyMap(), semExigencia).single().para)
        assertEquals("bruno", motor.calcular(cargas - "dora", itens, emptyMap(), semExigencia).single().para)
    }

    @Test
    fun histerese() {
        // Origem 130% (52/40); item de 4 h: origem depois 120%, destino depois 105%: só 15 pp abaixo. Nenhum movimento.
        val itens = mapOf("ana" to listOf(item("TG-1", 4.0)))
        assertTrue(motor.calcular(mapa(carga("ana", 52.0, 40.0), carga("bruno", 38.0, 40.0)), itens, emptyMap(), semExigencia).isEmpty())
        // Com 20 pp ou mais de folga (destino depois a 100%) o movimento acontece.
        val m = motor.calcular(mapa(carga("ana", 52.0, 40.0), carga("bruno", 36.0, 40.0)), itens, emptyMap(), semExigencia).single()
        assertEquals(1.0, m.ocupacaoDestinoDepois, 1e-9)
        assertEquals(1.2, m.ocupacaoOrigemDepois, 1e-9)
    }

    @Test
    fun destinoNaoPassaDoGatilho() {
        // Mover para quem ficaria acima do gatilho só trocaria o problema de lugar.
        val itens = mapOf("ana" to listOf(item("TG-1", 10.0)))
        assertTrue(motor.calcular(mapa(carga("ana", 90.0, 40.0), carga("bruno", 36.0, 40.0)), itens, emptyMap(), semExigencia).isEmpty())
    }

    @Test
    fun maximoDeMovimentos() {
        val itens = (1..20).map { item("TG-$it", 2.0) }
        val r = motor.calcular(mapa(carga("ana", 80.0, 20.0), carga("bruno", 0.0, 400.0)), mapOf("ana" to itens), emptyMap(), semExigencia)
        assertEquals(10, r.size)
        assertEquals(10, r.map { it.issueKey }.toSet().size, "cada task se move uma vez só")
        // A origem desce a cada movimento.
        assertEquals(r.map { it.ocupacaoOrigemAntes }.sortedDescending(), r.map { it.ocupacaoOrigemAntes })
    }

    @Test
    fun semCapacidadeNuncaEhDestino() {
        val itens = mapOf("ana" to listOf(item("TG-1", 10.0)))
        assertTrue(motor.calcular(mapa(carga("ana", 60.0, 40.0), carga("bruno", 0.0, 0.0)), itens, emptyMap(), semExigencia).isEmpty())
        // E a pessoa sem capacidade não aparece como origem: não há ocupação para comparar com o gatilho.
        assertTrue(motor.calcular(mapa(carga("ana", 10.0, 0.0), carga("bruno", 0.0, 40.0)), mapOf("ana" to listOf(item("TG-1", 5.0))), emptyMap(), semExigencia).isEmpty())
    }

    @Test
    fun abaixoDoGatilhoNaoMove() {
        val itens = mapOf("ana" to listOf(item("TG-1", 4.0)))
        // 110% exatos não é "acima do gatilho".
        assertTrue(motor.calcular(mapa(carga("ana", 44.0, 40.0), carga("bruno", 0.0, 40.0)), itens, emptyMap(), semExigencia).isEmpty())
        assertEquals(1, motor.calcular(mapa(carga("ana", 44.4, 40.0), carga("bruno", 0.0, 40.0)), itens, emptyMap(), semExigencia).size)
    }

    @Test
    fun origemMaisCarregadaPrimeiroEOrdemDeItens() {
        // Duas origens: a mais carregada (200%) é atendida primeiro, e dentro dela o item maior sai antes.
        val cargas = mapa(carga("ana", 60.0, 40.0), carga("bruno", 80.0, 40.0), carga("carla", 0.0, 400.0))
        val itens = mapOf(
            "ana" to listOf(item("TG-1", 3.0)),
            "bruno" to listOf(item("TG-2", 3.0), item("TG-3", 9.0)),
        )
        val r = motor.calcular(cargas, itens, emptyMap(), semExigencia)
        assertEquals(listOf("TG-3", "TG-2", "TG-1"), r.map { it.issueKey })
    }
}
