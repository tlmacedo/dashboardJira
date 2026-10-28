package br.sidia.painel.distribuicao

import br.sidia.painel.trabalho.SnapDeTeste
import br.sidia.painel.trabalho.Trb1
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Motor de distribuição (modo sombra). Cenário DI-1, sobre as pessoas do CAP-1: `ana` (16,8 h de carga, 67,2 h de capacidade,
 * WIP 1 de 3), `bruno` (8,0 h; 32,0 h; WIP 3 de 3; já trabalhou no épico) e `carla` (0 h; 44,8 h; WIP 0 de 3; falta 1 competência).
 */
class DistribuidorTest {
    private val regras = RegrasDeDistribuicao.PADRAO
    private val motor = Distribuidor(regras)

    private fun ana(carga: Double = 16.8, ultima: Instant? = null) = Candidato("ana", carga, 67.2, 1, 3, 0, false, ultima, 1.0)
    private val bruno = Candidato("bruno", 8.0, 32.0, 3, 3, 0, true, null, 1.0)
    private val carla = Candidato("carla", 0.0, 44.8, 0, 3, 1, false, null, 1.0)

    private fun item(chave: String, horas: Double) = SnapDeTeste.snap(chave, resp = null) to horas
    private val semCompetencia: (br.sidia.painel.trabalho.IssueSnapshot) -> Set<String> = { emptySet() }

    @Test
    fun scoreDosCandidatos() {
        val r = motor.distribuir(listOf(item("TG-1", 6.0)), listOf(ana(), bruno, carla), semCompetencia)
        val fatores = r.sugestoes.single().fatores.associateBy { it.pessoa }
        assertEquals(0.416667, fatores.getValue("ana").score, 1e-6)
        assertEquals(1.6, fatores.getValue("bruno").score, 1e-9)
        assertEquals(0.6, fatores.getValue("carla").score, 1e-9)
        assertEquals("ana", r.sugestoes.single().sugerido)
        // Os termos explicam o score, sem esconder nada.
        val b = fatores.getValue("bruno")
        assertEquals(0.25, b.carga, 1e-9)
        assertEquals(0.5, b.wip, 1e-9)
        assertEquals(1.0, b.wipCheio, 1e-9)
        assertEquals(0.15, b.bonus, 1e-9)
        assertEquals(0.6, fatores.getValue("carla").competencias, 1e-9)
        assertEquals("distribuicao v1", r.versaoRegras)
    }

    @Test
    fun fatoresEmOrdemAlfabetica() {
        // Entrada fora de ordem e o melhor score é o último alfabeticamente: a lista nunca segue o score.
        val zeca = Candidato("zeca", 0.0, 80.0, 0, 3, 0, false, null, 1.0)
        val r = motor.distribuir(listOf(item("TG-1", 6.0)), listOf(zeca, carla, bruno, ana()), semCompetencia)
        val s = r.sugestoes.single()
        assertEquals("zeca", s.sugerido)
        assertEquals(listOf("ana", "bruno", "carla", "zeca"), s.fatores.map { it.pessoa })
    }

    @Test
    fun empatePorUltimaAtribuicao() {
        // Mesmo score: quem recebeu há mais tempo (ou nunca) vem primeiro; depois a ordem alfabética.
        val a = Candidato("ana", 10.0, 40.0, 0, 3, 0, false, Instant.parse("2026-10-20T12:00:00Z"), 1.0)
        val b = Candidato("bruno", 10.0, 40.0, 0, 3, 0, false, Instant.parse("2026-10-10T12:00:00Z"), 1.0)
        assertEquals("bruno", motor.distribuir(listOf(item("TG-1", 6.0)), listOf(a, b), semCompetencia).sugestoes.single().sugerido)
        val nunca = Candidato("carla", 10.0, 40.0, 0, 3, 0, false, null, 1.0)
        assertEquals("carla", motor.distribuir(listOf(item("TG-1", 6.0)), listOf(a, b, nunca), semCompetencia).sugestoes.single().sugerido)
        // Empate total (inclusive na última atribuição): ordem alfabética, nunca a ordem de entrada.
        val c = a.copy(pessoa = "carla")
        assertEquals("ana", motor.distribuir(listOf(item("TG-1", 6.0)), listOf(c, a), semCompetencia).sugestoes.single().sugerido)
    }

    @Test
    fun atualizaCargaEntreItens() {
        // Dois itens de 6 h: depois do primeiro, a carga da ana passa de 16,8 para 22,8 h e o segundo item vê isso.
        val r = motor.distribuir(listOf(item("TG-1", 6.0), item("TG-2", 6.0)), listOf(ana(), bruno), semCompetencia)
        assertEquals("ana", r.sugestoes[0].sugerido)
        val segundo = r.sugestoes[1].fatores.first { it.pessoa == "ana" }
        assertEquals(22.8 / 67.2 + 0.5 / 3, segundo.score, 1e-9)
        // O WIP não muda: o item novo entra em "a fazer".
        assertEquals(0.5 / 3, segundo.wip, 1e-9)
    }

    @Test
    fun itensDoMaiorParaOMenorMasSaidaNaOrdemDeEntrada() {
        val edu = Candidato("edu", 0.0, 40.0, 0, 3, 0, false, null, 1.0)
        val r = motor.distribuir(listOf(item("TG-1", 2.0), item("TG-2", 9.0)), listOf(ana(), edu), semCompetencia)
        assertEquals(listOf("TG-1", "TG-2"), r.sugestoes.map { it.issueKey })
        // O item grande (TG-2, 9 h) foi distribuído primeiro: o pequeno já vê a carga somada do edu (9 h de 40 h).
        assertEquals(9.0 / 40.0, r.sugestoes[0].fatores.first { it.pessoa == "edu" }.carga, 1e-9)
        assertEquals(0.0, r.sugestoes[1].fatores.first { it.pessoa == "edu" }.carga, 1e-9)
    }

    @Test
    fun inelegivelSemCapacidade() {
        val sem = Candidato("dora", 0.0, 0.4, 0, 3, 0, false, null, 1.0)
        val r = motor.distribuir(listOf(item("TG-1", 6.0)), listOf(sem, ana()), semCompetencia)
        val s = r.sugestoes.single()
        assertEquals("ana", s.sugerido)
        assertEquals(listOf("ana"), s.fatores.map { it.pessoa })
        // Ninguém elegível: sem sugestão e sem fatores.
        val nada = motor.distribuir(listOf(item("TG-1", 6.0)), listOf(sem), semCompetencia).sugestoes.single()
        assertNull(nada.sugerido)
        assertTrue(nada.fatores.isEmpty())
        // Exatamente na capacidade mínima também fica de fora (o limite é "acima de").
        assertNull(motor.distribuir(listOf(item("TG-1", 6.0)), listOf(sem.copy(capacidadeH = 0.5)), semCompetencia).sugestoes.single().sugerido)
    }

    @Test
    fun produtividadeLimitada() {
        fun carga(produtividade: Double) = motor.distribuir(
            listOf(item("TG-1", 6.0)), listOf(Candidato("ana", 20.0, 40.0, 0, 3, 0, false, null, produtividade)), semCompetencia,
        ).sugestoes.single().fatores.single().carga
        assertEquals(20.0 * 1.15 / 40.0, carga(1.5), 1e-9)
        assertEquals(20.0 * 0.85 / 40.0, carga(0.2), 1e-9)
        assertEquals(20.0 * 1.0 / 40.0, carga(1.0), 1e-9)
        assertEquals(20.0 * 1.05 / 40.0, carga(1.05), 1e-9)
    }

    @Test
    fun competenciaEAfinidadePorItem() {
        // Com o conjunto de competências do candidato, o motor conta o que falta PARA CADA ITEM.
        val a = Candidato("ana", 0.0, 40.0, 0, 3, 0, false, null, 1.0, competencias = setOf("android"), afinidades = setOf("EP-1"))
        val b = Candidato("bruno", 0.0, 40.0, 0, 3, 0, false, null, 1.0, competencias = setOf("rede", "android"), afinidades = emptySet())
        val i = SnapDeTeste.snap("TG-1", resp = null)
        val r = motor.distribuir(listOf(i to 6.0), listOf(a, b), { setOf("android", "rede") }, { setOf("EP-1") })
        val f = r.sugestoes.single().fatores.associateBy { it.pessoa }
        assertEquals(0.6, f.getValue("ana").competencias, 1e-9)
        assertEquals(0.15, f.getValue("ana").bonus, 1e-9)
        assertEquals(0.0, f.getValue("bruno").competencias, 1e-9)
        assertEquals(0.0, f.getValue("bruno").bonus, 1e-9)
        assertEquals("bruno", r.sugestoes.single().sugerido)
    }

    @Test
    fun jainAntesEDepois() {
        // Ocupações antes: ana 25%, carla 0%. Depois de dar 8 h à carla (44,8 h de capacidade): 25% e ~17,9%: mais igual.
        val c = carla.copy(competenciasFaltantes = 0)
        val r = motor.distribuir(listOf(item("TG-1", 8.0)), listOf(ana(), c), semCompetencia)
        assertEquals("carla", r.sugestoes.single().sugerido)
        val antes = (0.25 + 0.0).let { it * it } / (2 * (0.25 * 0.25))
        assertEquals(antes, r.jainAntes!!, 1e-9)
        val x = 8.0 / 44.8
        assertEquals((0.25 + x) * (0.25 + x) / (2 * (0.25 * 0.25 + x * x)), r.jainDepois!!, 1e-9)
        assertTrue(r.jainDepois!! > r.jainAntes!!)
        // Menos de dois elegíveis: o índice não existe.
        assertNull(motor.distribuir(listOf(item("TG-1", 8.0)), listOf(ana()), semCompetencia).jainAntes)
    }

    @Test
    fun pendentesSemResponsavelOuComGerente() {
        val aberta = { chave: String, resp: String?, status: String -> SnapDeTeste.snap(chave, resp = resp, status = status) }
        val abertas = listOf(
            aberta("TG-10", null, "Open"), // sem responsável: pendente
            aberta("TG-11", "gerente.exemplo", "Open"), // com o gerente: pendente
            aberta("TG-12", "ana", "Open"), // com dono: não
            aberta("TG-13", null, "In Progress"), // já em andamento: não
            aberta("TG-14", "gerente.exemplo", "In Progress"),
            aberta("TG-2", null, "Open"),
            aberta("TG-15", "coord.um", "Open"), // coordenação não é "gerente": a regra é só do GERENTE
        )
        val r = motor.pendentes(abertas, Trb1.statusMap, Trb1.papeis, LocalDate.of(2026, 10, 30))
        assertEquals(listOf("TG-2", "TG-10", "TG-11"), r.map { it.key }, "ordem natural de chaves")
    }
}
