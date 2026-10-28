package br.sidia.painel.cards.cobertura

import br.sidia.painel.pessoas.Ausencia
import br.sidia.painel.pessoas.Ausencias
import br.sidia.painel.pessoas.Cap1
import br.sidia.painel.pessoas.Equipes
import br.sidia.painel.pessoas.MembroEquipe
import br.sidia.painel.pessoas.MotorDePessoas
import br.sidia.painel.security.AtribuicaoPapel
import br.sidia.painel.security.Pessoa
import br.sidia.painel.security.Role
import br.sidia.painel.security.RoleDirectory
import br.sidia.painel.security.Vigencia
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Cobertura por ausência: o trabalho que a ausência deixa sem dono e quem do mesmo squad pode cobrir (CAP-1 e um cenário próprio). */
class CoberturaCalculatorTest {
    private val calc = CoberturaCalculator(Cap1.motor, Cap1.papeis)
    private val todos = setOf("ana", "bruno", "carla")

    private fun rodar(visiveis: Set<String> = todos, dias: Int = 30) = calc.calcular(visiveis, Cap1.abertas(), Cap1.hoje, dias)

    @Test
    fun coberturaDaAna() {
        val ana = rodar().dto.ausencias.first { it.pessoa == "ana" }
        assertEquals("2026-11-04", ana.de)
        assertEquals("2026-11-05", ana.ate)
        // A TG-202 cairia nos dias da ausência (04/11); a TG-201 termina em 03/11 e fica de fora.
        assertEquals(listOf("TG-202"), ana.issues)
        assertEquals(5.6, ana.horasAReplanejar, 1e-9)
        assertEquals(listOf("android"), ana.competencias)
        // O bruno (mesmo squad): capacidade 11,2 h menos demanda 2,0 h na semana de 02/11. A carla é de outro squad.
        assertEquals(listOf(CandidatoDto("bruno", 9.2, true)), ana.podemCobrir)
    }

    @Test
    fun ausenciasEmOrdemAlfabeticaEDeData() {
        val d = rodar().dto
        assertEquals(listOf("ana", "bruno", "carla"), d.ausencias.map { it.pessoa })
        assertEquals(30, d.dias)
    }

    @Test
    fun ausenciaSemTrabalhoAfetadoFicaListadaSemCandidatos() {
        val bruno = rodar().dto.ausencias.first { it.pessoa == "bruno" }
        assertEquals(emptyList(), bruno.issues)
        assertEquals(0.0, bruno.horasAReplanejar, 1e-9)
        assertEquals(emptyList(), bruno.competencias)
        assertEquals(emptyList(), bruno.podemCobrir, "sem trabalho a cobrir não há candidato")
    }

    @Test
    fun carlaSemColegaNoSquadNaoTemCandidato() {
        val carla = rodar().dto.ausencias.first { it.pessoa == "carla" }
        // A TG-205 (vencida, horas assumidas) consome 0,4 h em 03/11 no cenário sem ausência.
        assertEquals(listOf("TG-205"), carla.issues)
        assertEquals(0.4, carla.horasAReplanejar, 1e-9)
        assertEquals(emptyList(), carla.podemCobrir)
    }

    @Test
    fun horizonteLimitaAsAusencias() {
        // 1 dia de horizonte (até 31/10): nenhuma ausência toca; 4 dias (até 03/11): só a da carla.
        assertEquals(emptyList(), rodar(dias = 1).dto.ausencias)
        assertEquals(listOf("carla"), rodar(dias = 4).dto.ausencias.map { it.pessoa })
    }

    @Test
    fun soPessoasVisiveisEntramComoAusenteECandidato() {
        // Só a ana visível: a ausência da carla some e o bruno (invisível) não pode ser candidato.
        val d = rodar(visiveis = setOf("ana")).dto
        assertEquals(listOf("ana"), d.ausencias.map { it.pessoa })
        assertEquals(emptyList(), d.ausencias.single().podemCobrir)
    }

    @Test
    fun segmentosAbremAsMesmasTasksDoNumero() {
        val a = rodar()
        for (aus in a.dto.ausencias) {
            val segmento = a.segmentos.getValue("ausencia:${aus.pessoa}:${aus.de}")
            assertEquals(aus.issues, segmento.map { it.key })
        }
    }

    // ---- Cenário próprio: candidatos em ordem alfabética, sem ordenar por folga ----

    private fun usuario(id: String, squad: String) = Pessoa(id, id, listOf(AtribuicaoPapel(Role.USUARIO, Vigencia(null, null), squad)))

    private val papeis = RoleDirectory(
        listOf(usuario("ana", "Alfa"), usuario("bia", "Alfa"), usuario("caio", "Alfa"), usuario("davi", "Alfa"), usuario("edu", "Beta")),
    )

    private fun membro(id: String, vararg competencias: String) = MembroEquipe(id, 40.0, 1.0, 1.0, competencias.toSet())

    private val motor = MotorDePessoas(
        Equipes(listOf(membro("ana", "rede"), membro("bia", "rede"), membro("caio", "android"), membro("davi", "rede", "android"), membro("edu", "rede"))),
        Ausencias(listOf(Ausencia("ana", LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 5), 1.0))),
        Cap1.calendario, Cap1.regras, Cap1.statusMap,
    )

    private fun task(key: String, resp: String, horas: Long, vence: String, labels: List<String> = emptyList(), prioridade: String = "Medium") =
        Cap1.snapshot(
            key, "Checklist", resp, "Open", Cap1.agora.minusSeconds(86400L * 5), LocalDate.parse(vence), horas * 3600, null, null, prioridade, labels,
        )

    @Test
    fun candidatosAlfabeticosSemOrdenarPorFolga() {
        val abertas = listOf(
            task("TG-1", "ana", 24, "2026-11-06", listOf("skill:rede")), // 8 h por dia: 30/10, 03/11 e 04/11 (cai na ausência)
            task("TG-2", "bia", 60, "2026-11-06"), // 30 h por semana: a bia quase não tem folga
            task("TG-3", "davi", 16, "2026-11-06"), // 8 h por semana
        )
        val d = CoberturaCalculator(motor, papeis).calcular(setOf("ana", "bia", "caio", "davi", "edu"), abertas, Cap1.hoje, 30).dto
        val ana = d.ausencias.single()
        assertEquals(listOf("TG-1"), ana.issues)
        assertEquals(8.0, ana.horasAReplanejar, 1e-9)
        assertEquals(listOf("rede"), ana.competencias)
        // Ordem alfabética (bia, caio, davi); o edu é de outro squad. A folga NÃO está em ordem nenhuma: 2, 32, 24.
        assertEquals(listOf("bia", "caio", "davi"), ana.podemCobrir.map { it.pessoa })
        assertEquals(listOf(2.0, 32.0, 24.0), ana.podemCobrir.map { it.folgaH })
        assertNotEquals(ana.podemCobrir.map { it.folgaH }.sorted(), ana.podemCobrir.map { it.folgaH })
        assertNotEquals(ana.podemCobrir.map { it.folgaH }.sortedDescending(), ana.podemCobrir.map { it.folgaH })
        // Quem tem a competência pedida (rede): bia e davi; o caio só tem android.
        assertEquals(listOf(true, false, true), ana.podemCobrir.map { it.temTodasAsCompetencias })
    }

    @Test
    fun quemNaoTemFolgaNaoEhCandidato() {
        val abertas = listOf(
            task("TG-1", "ana", 24, "2026-11-06"),
            task("TG-2", "bia", 120, "2026-11-06"), // 60 h por semana contra 32 h de capacidade
        )
        val d = CoberturaCalculator(motor, papeis).calcular(setOf("ana", "bia", "caio"), abertas, Cap1.hoje, 30).dto
        assertEquals(listOf("caio"), d.ausencias.single().podemCobrir.map { it.pessoa })
    }

    @Test
    fun venceNaAusenciaENaoTerminariaAntesEntraMesmoSemHoraNosDias() {
        // O TG-9 está na fila da ana atrás de muita coisa: só começaria depois da ausência, mas vence DENTRO dela.
        val abertas = listOf(
            task("TG-1", "ana", 100, "2026-12-30", prioridade = "High"),
            task("TG-9", "ana", 8, "2026-11-05", prioridade = "Lowest"),
        )
        val d = CoberturaCalculator(motor, papeis).calcular(setOf("ana", "bia"), abertas, Cap1.hoje, 30).dto.ausencias.single()
        assertEquals(listOf("TG-1", "TG-9"), d.issues)
        // As horas só contam o que foi AGENDADO nos dias da ausência (a TG-1: 8 h em 04/11 e 8 h em 05/11); a TG-9 não tem hora lá.
        assertEquals(16.0, d.horasAReplanejar, 1e-9)
    }
}
