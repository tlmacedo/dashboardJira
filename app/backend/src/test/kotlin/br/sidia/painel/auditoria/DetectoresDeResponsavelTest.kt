package br.sidia.painel.auditoria

import br.sidia.painel.time.BusinessCalendar
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Sinais de responsável, passa-bola, troca perto do vencimento e cancelamento (cenário AUD-1, vencimento 15/10). */
class DetectoresDeResponsavelTest {
    private val c = CenarioAud1()

    private fun detectar(vararg eventos: br.sidia.painel.sync.ChangeEventRecord, assignee: String? = "ana", duedate: LocalDate? = LocalDate.parse("2026-10-15")) =
        c.detectar(DetectoresDeResponsavel.todos, eventos.toList(), assignee, duedate)

    private fun de(codigo: CodigoSinal, sinais: List<Sinal>) = sinais.filter { it.codigo == codigo }

    @Test
    fun trocaPorUsuarioComumGera() {
        val s = de(
            CodigoSinal.RESPONSAVEL_TROCADO_NAO_AUTORIZADO,
            detectar(c.ev("assignee", "ana", "bruno", "2026-10-03T12:00:00Z", "bruno")),
        ).single()
        assertEquals(Resultado.AUDITORIA, s.resultado)
        assertEquals(MotivoAutorizacao.NAO_AUTORIZADO, s.motivo)
        assertEquals("ana", s.de)
        assertEquals("bruno", s.para)
        assertEquals("ana", s.responsavel)
        assertEquals(3, s.gravidade)
    }

    @Test
    fun trocaPeloReporterNaoGera() {
        assertTrue(detectar(c.ev("assignee", "ana", "bruno", "2026-10-03T12:00:00Z", "pl.alfa")).isEmpty())
    }

    @Test
    fun trocaDeTaskSemResponsavelUsaSquadDoNovo() {
        val s = detectar(c.ev("assignee", null, "ana", "2026-10-11T12:00:00Z", "lider.alfa2"), assignee = "ana")
        assertTrue(de(CodigoSinal.RESPONSAVEL_TROCADO_NAO_AUTORIZADO, s).isEmpty())
    }

    @Test
    fun autorJirauserNaoResolvidoAparece() {
        val s = de(
            CodigoSinal.RESPONSAVEL_TROCADO_NAO_AUTORIZADO,
            detectar(c.ev("assignee", "ana", "bruno", "2026-10-03T12:00:00Z", "JIRAUSER99")),
        ).single()
        assertEquals("JIRAUSER99", s.autor)
        assertEquals(MotivoAutorizacao.NAO_AUTORIZADO, s.motivo)
    }

    @Test
    fun passaBolaTresTrocasNaJanela() {
        val s = detectar(
            c.ev("assignee", "ana", "bruno", "2026-10-13T15:00:00Z"),
            c.ev("assignee", "bruno", "carla", "2026-10-14T15:00:00Z"),
            c.ev("assignee", "carla", "davi", "2026-10-16T15:00:00Z"),
        )
        val p = de(CodigoSinal.PASSA_BOLA, s).single()
        assertEquals(c.i("2026-10-16T15:00:00Z"), p.at)
        assertEquals(Resultado.AUDITORIA, p.resultado)
        assertEquals(3, p.gravidade)
    }

    @Test
    fun passaBolaVoltaAQuemJaTeve() {
        val s = detectar(
            c.ev("assignee", "ana", "bruno", "2026-10-13T15:00:00Z"),
            c.ev("assignee", "bruno", "ana", "2026-10-14T15:00:00Z"),
            c.ev("assignee", "ana", "bruno", "2026-10-15T15:00:00Z"),
        )
        // UM sinal por issue, na troca que completa a condição
        assertEquals(c.i("2026-10-14T15:00:00Z"), de(CodigoSinal.PASSA_BOLA, s).single().at)
    }

    @Test
    fun passaBolaIndependeDoAutor() {
        val s = detectar(
            c.ev("assignee", "ana", "bruno", "2026-10-13T15:00:00Z", "coord.um"),
            c.ev("assignee", "bruno", "ana", "2026-10-14T15:00:00Z", "coord.um"),
        )
        val p = de(CodigoSinal.PASSA_BOLA, s).single()
        assertEquals(true, p.autorizado)
    }

    @Test
    fun duasTrocasForaDaJanelaNaoGeram() {
        val s = detectar(
            c.ev("assignee", "ana", "bruno", "2026-10-01T15:00:00Z"),
            c.ev("assignee", "bruno", "ana", "2026-10-02T15:00:00Z"),
        )
        assertTrue(de(CodigoSinal.PASSA_BOLA, s).isEmpty())
    }

    @Test
    fun passaBolaSemVencimentoNaoGera() {
        val s = detectar(
            c.ev("assignee", "ana", "bruno", "2026-10-13T15:00:00Z"),
            c.ev("assignee", "bruno", "ana", "2026-10-14T15:00:00Z"),
            duedate = null,
        )
        assertTrue(s.isEmpty())
    }

    @Test
    fun limiteDaJanela() {
        // 2026-10-13T04:00Z = 13/10 00:00 em Manaus: conta (distância 2); 03:59:59Z = 12/10: não (distância 3)
        val dentro = detectar(c.ev("assignee", "ana", "bruno", "2026-10-13T04:00:00Z", "carla"))
        assertEquals(1, de(CodigoSinal.TROCA_PERTO_DO_VENCIMENTO, dentro).size)
        val fora = detectar(c.ev("assignee", "ana", "bruno", "2026-10-13T03:59:59Z", "carla"))
        assertTrue(de(CodigoSinal.TROCA_PERTO_DO_VENCIMENTO, fora).isEmpty())
        // a outra ponta: 17/10 conta, 18/10 não
        val ponta = detectar(c.ev("assignee", "ana", "bruno", "2026-10-17T15:00:00Z", "carla"))
        assertEquals(1, de(CodigoSinal.TROCA_PERTO_DO_VENCIMENTO, ponta).size)
        val alem = detectar(c.ev("assignee", "ana", "bruno", "2026-10-18T15:00:00Z", "carla"))
        assertTrue(de(CodigoSinal.TROCA_PERTO_DO_VENCIMENTO, alem).isEmpty())
    }

    @Test
    fun trocaPertoPorUsuarioGera() {
        val s = de(
            CodigoSinal.TROCA_PERTO_DO_VENCIMENTO,
            detectar(c.ev("assignee", "ana", "bruno", "2026-10-15T15:00:00Z", "carla")),
        ).single()
        assertEquals(Resultado.AUDITORIA, s.resultado)
        assertEquals(2, s.gravidade)
    }

    @Test
    fun trocaPertoPeloLiderNaoGera() {
        val s = detectar(c.ev("assignee", "ana", "bruno", "2026-10-15T15:00:00Z", "lider.alfa2"))
        assertTrue(de(CodigoSinal.TROCA_PERTO_DO_VENCIMENTO, s).isEmpty())
    }

    @Test
    fun trocaPertoSemVencimentoNaoGera() {
        val s = detectar(c.ev("assignee", "ana", "bruno", "2026-10-15T15:00:00Z", "carla"), duedate = null)
        assertTrue(de(CodigoSinal.TROCA_PERTO_DO_VENCIMENTO, s).isEmpty())
    }

    @Test
    fun diasUteisQuandoConfigurado() {
        // vencimento segunda 19/10; troca na sexta 16/10 por carla
        val corrido = CenarioAud1()
        val evs = listOf(corrido.ev("assignee", "ana", "bruno", "2026-10-16T15:00:00Z", "carla"))
        val venc = LocalDate.parse("2026-10-19")
        assertEquals(3, corrido.ctx.distanciaDias(LocalDate.parse("2026-10-16"), venc))
        assertTrue(corrido.ctx.perto(LocalDate.parse("2026-10-16"), venc).not())
        assertTrue(de(CodigoSinal.TROCA_PERTO_DO_VENCIMENTO, corrido.detectar(DetectoresDeResponsavel.todos, evs, duedate = venc)).isEmpty())

        val util = CenarioAud1(corrido.regras.copy(unidade = UnidadeDias.UTIL), BusinessCalendar(ZoneId.of("America/Manaus")))
        assertEquals(1, util.ctx.distanciaDias(LocalDate.parse("2026-10-16"), venc))
        assertEquals(-1, util.ctx.distanciaDias(venc, LocalDate.parse("2026-10-16")))
        assertTrue(util.ctx.perto(LocalDate.parse("2026-10-16"), venc))
        assertEquals(1, de(CodigoSinal.TROCA_PERTO_DO_VENCIMENTO, util.detectar(DetectoresDeResponsavel.todos, evs, duedate = venc)).size)
    }

    @Test
    fun distanciaEhAssinada() {
        assertEquals(2, c.ctx.distanciaDias(LocalDate.parse("2026-10-13"), LocalDate.parse("2026-10-15")))
        assertEquals(-2, c.ctx.distanciaDias(LocalDate.parse("2026-10-17"), LocalDate.parse("2026-10-15")))
        assertEquals(0, c.ctx.distanciaDias(LocalDate.parse("2026-10-15"), LocalDate.parse("2026-10-15")))
    }

    @Test
    fun canceladaDepoisDoVencimento() {
        val s = detectar(c.ev("status", "Open", "Cancelled", "2026-10-16T15:00:00Z", "coord.um")).single()
        assertEquals(CodigoSinal.CANCELAMENTO_SUSPEITO, s.codigo)
        assertEquals(Resultado.AUDITORIA, s.resultado)
        assertEquals(true, s.autorizado)
        assertEquals("Cancelled", s.para)
        assertEquals(2, s.gravidade)
    }

    @Test
    fun canceladaPorUsuario() {
        val s = detectar(c.ev("status", "Open", "Cancelled", "2026-10-10T15:00:00Z", "ana")).single()
        assertEquals(CodigoSinal.CANCELAMENTO_SUSPEITO, s.codigo)
        assertEquals(false, s.autorizado)
    }

    @Test
    fun canceladaPeloReporterAteOVencimento() {
        assertTrue(detectar(c.ev("status", "Open", "Cancelled", "2026-10-15T15:00:00Z", "pl.alfa")).isEmpty())
    }

    @Test
    fun canceladaDepoisDoVencimentoPorUsuarioGeraUmSoSinal() {
        assertEquals(1, detectar(c.ev("status", "Open", "Cancelled", "2026-10-16T15:00:00Z", "ana")).size)
    }

    @Test
    fun statusDeCancelamentoSemDiferenciarMaiusculas() {
        assertEquals(1, detectar(c.ev("status", "Open", "dropped", "2026-10-10T15:00:00Z", "ana")).size)
    }

    @Test
    fun outraTransicaoNaoGera() {
        assertTrue(detectar(c.ev("status", "Open", "In Progress", "2026-10-16T15:00:00Z", "ana")).isEmpty())
    }

    @Test
    fun canceladaSemVencimentoSoPorNaoAutorizado() {
        assertTrue(detectar(c.ev("status", "Open", "Cancelled", "2026-10-16T15:00:00Z", "coord.um"), duedate = null).isEmpty())
        assertEquals(1, detectar(c.ev("status", "Open", "Cancelled", "2026-10-16T15:00:00Z", "ana"), duedate = null).size)
    }

    @Test
    fun vencimentoAbsurdoTerminaRapidoEDaResultadoCorreto() {
        val util = CenarioAud1(CenarioAud1().regras.copy(unidade = UnidadeDias.UTIL))
        val dia = LocalDate.parse("2026-10-15")
        for (extremo in listOf(LocalDate.of(9999, 12, 31), LocalDate.of(1, 1, 1))) {
            for (cenario in listOf(c, util)) {
                org.junit.jupiter.api.Assertions.assertTimeout(java.time.Duration.ofSeconds(1)) {
                    assertEquals(false, cenario.ctx.perto(dia, extremo))
                    assertEquals(false, cenario.ctx.perto(extremo, dia))
                    val d = cenario.ctx.distanciaDias(dia, extremo)
                    assertEquals(extremo.isAfter(dia), d > 0)
                }
                val s = cenario.detectar(
                    DetectoresDeResponsavel.todos,
                    listOf(cenario.ev("assignee", "ana", "bruno", "2026-10-15T15:00:00Z", "carla")),
                    duedate = extremo,
                )
                assertTrue(s.none { it.codigo == CodigoSinal.TROCA_PERTO_DO_VENCIMENTO })
            }
        }
    }

    @Test
    fun feriadoContaComoDiaNaoUtilEmUtil() {
        val venc = LocalDate.parse("2026-10-19") // segunda
        val regras = c.regras.copy(unidade = UnidadeDias.UTIL, pertoDoVencimentoDias = 1)
        val semFeriado = CenarioAud1(regras)
        val comFeriado = CenarioAud1(regras, BusinessCalendar(ZoneId.of("America/Manaus"), movableHolidays = setOf(LocalDate.parse("2026-10-16"))))
        val quinta = LocalDate.parse("2026-10-15")
        assertEquals(2, semFeriado.ctx.distanciaDias(quinta, venc))
        assertEquals(1, comFeriado.ctx.distanciaDias(quinta, venc))
        assertEquals(false, semFeriado.ctx.perto(quinta, venc))
        assertEquals(true, comFeriado.ctx.perto(quinta, venc))
    }

    @Test
    fun coordenadorQueDeixouDeSerNaDataNaoAutoriza() {
        val s = detectar(c.ev("assignee", "ana", "bruno", "2026-10-03T12:00:00Z", "coord.antigo"))
        assertTrue(de(CodigoSinal.RESPONSAVEL_TROCADO_NAO_AUTORIZADO, s).isEmpty())
        val antes = detectar(c.ev("assignee", "ana", "bruno", "2026-10-08T12:00:00Z", "coord.antigo"))
        assertTrue(de(CodigoSinal.RESPONSAVEL_TROCADO_NAO_AUTORIZADO, antes).isEmpty())
        val depois = detectar(c.ev("assignee", "ana", "bruno", "2026-10-12T12:00:00Z", "coord.antigo"))
        assertEquals(1, de(CodigoSinal.RESPONSAVEL_TROCADO_NAO_AUTORIZADO, depois).size)
    }

    @Test
    fun deApenasEspacosContaComoSemResponsavel() {
        // from=" " e to=null: não há troca real
        assertTrue(detectar(c.ev("assignee", " ", null, "2026-10-03T12:00:00Z", "carla")).isEmpty())
        // from=" " e to=ana: referência é o novo responsável (lider.alfa2 do Alfa autoriza)
        val s = detectar(c.ev("assignee", " ", "ana", "2026-10-11T12:00:00Z", "lider.alfa2"))
        assertTrue(de(CodigoSinal.RESPONSAVEL_TROCADO_NAO_AUTORIZADO, s).isEmpty())
    }

    @Test
    fun empateDeInstanteUsaOrdemEstavelDeEntrada() {
        // mesmo instante: a consulta "em at" usa '<' estrito, então os eventos não se enxergam entre si;
        // a ordem do empate é a de entrada (sort estável) e o passa-bola cai na terceira troca
        val mesmo = "2026-10-14T15:00:00Z"
        val e1 = c.ev("assignee", "ana", "bruno", mesmo, "carla")
        val e2 = c.ev("assignee", "bruno", "carla", mesmo, "carla")
        val e3 = c.ev("assignee", "carla", "davi", mesmo, "carla")
        val p = de(CodigoSinal.PASSA_BOLA, detectar(e1, e2, e3)).single()
        assertEquals(e3.changelogId, p.changelogId)
    }

    @Test
    fun regraInativaNaoGera() {
        val inativas = c.regras.copy(sinais = c.regras.sinais.mapValues { it.value.copy(ativo = false) })
        val evs = listOf(
            c.ev("assignee", "ana", "bruno", "2026-10-13T15:00:00Z", "carla"),
            c.ev("assignee", "bruno", "ana", "2026-10-14T15:00:00Z", "carla"),
            c.ev("status", "Open", "Cancelled", "2026-10-16T15:00:00Z", "ana"),
        )
        assertTrue(c.detectar(DetectoresDeResponsavel.todos, evs, contexto = c.comRegras(inativas)).isEmpty())
    }
}
