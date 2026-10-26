package br.sidia.painel.auditoria

import br.sidia.painel.security.AtribuicaoPapel
import br.sidia.painel.security.Pessoa
import br.sidia.painel.security.Role
import br.sidia.painel.security.RoleDirectory
import br.sidia.painel.security.Vigencia
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

/** Autorização retroativa pela vigência do papel na data civil de Manaus (cenário AUD-1). */
class AutorizacaoTest {
    private fun d(s: String) = LocalDate.parse(s)
    private fun i(s: String) = Instant.parse(s)

    private fun usuario(id: String, squad: String) =
        Pessoa(id, id, listOf(AtribuicaoPapel(Role.USUARIO, Vigencia(null, null), squad)))

    private val roles = RoleDirectory(
        listOf(
            usuario("pl.alfa", "Alfa"), usuario("ana", "Alfa"), usuario("bruno", "Alfa"), usuario("carla", "Beta"),
            Pessoa("lider.alfa", "x", listOf(AtribuicaoPapel(Role.SQUAD_LEADER, Vigencia(null, d("2026-10-09")), "Alfa"))),
            Pessoa("lider.alfa2", "x", listOf(AtribuicaoPapel(Role.SQUAD_LEADER, Vigencia(d("2026-10-10"), null), "Alfa"))),
            Pessoa("lider.beta", "x", listOf(AtribuicaoPapel(Role.SQUAD_LEADER, Vigencia(null, null), "Beta"))),
            Pessoa("coord.um", "x", listOf(AtribuicaoPapel(Role.COORDENADOR, Vigencia(null, null)))),
            Pessoa("gerente.exemplo", "x", listOf(AtribuicaoPapel(Role.GERENTE, Vigencia(null, null)))),
        ),
    )
    private val aut = Autorizacao(roles, ZoneId.of("America/Manaus"))
    private val meioDia = i("2026-10-10T12:00:00Z")

    @Test
    fun reporterAutorizado() {
        assertEquals(Avaliacao(true, MotivoAutorizacao.REPORTER), aut.avaliar("pl.alfa", "pl.alfa", "ana", meioDia))
    }

    @Test
    fun coordenadorAutorizado() {
        assertEquals(Avaliacao(true, MotivoAutorizacao.COORDENACAO), aut.avaliar("coord.um", "pl.alfa", "ana", meioDia))
        assertEquals(Avaliacao(true, MotivoAutorizacao.COORDENACAO), aut.avaliar("gerente.exemplo", "pl.alfa", "ana", meioDia))
    }

    @Test
    fun liderDoSquadDoResponsavelAutorizado() {
        assertEquals(Avaliacao(true, MotivoAutorizacao.LIDER_DO_SQUAD), aut.avaliar("lider.alfa2", "pl.alfa", "ana", meioDia))
    }

    @Test
    fun liderDeOutroSquadNaoAutorizado() {
        assertEquals(Avaliacao(false, MotivoAutorizacao.NAO_AUTORIZADO), aut.avaliar("lider.beta", "pl.alfa", "ana", meioDia))
        // responsável sem squad / nulo
        assertEquals(Avaliacao(false, MotivoAutorizacao.NAO_AUTORIZADO), aut.avaliar("lider.alfa2", "pl.alfa", null, meioDia))
    }

    @Test
    fun proprioResponsavelNaoAutorizadoMesmoSendoLider() {
        assertEquals(
            Avaliacao(false, MotivoAutorizacao.PROPRIO_RESPONSAVEL),
            aut.avaliar("lider.alfa2", "pl.alfa", "lider.alfa2", meioDia),
        )
        assertEquals(Avaliacao(false, MotivoAutorizacao.PROPRIO_RESPONSAVEL), aut.avaliar("ana", "pl.alfa", "ana", meioDia))
    }

    @Test
    fun reporterQueEhOResponsavelContinuaReporter() {
        assertEquals(Avaliacao(true, MotivoAutorizacao.REPORTER), aut.avaliar("pl.alfa", "pl.alfa", "pl.alfa", meioDia))
    }

    @Test
    fun autorDesconhecidoNaoEhAutorizado() {
        val esperado = Avaliacao(false, MotivoAutorizacao.AUTOR_DESCONHECIDO)
        assertEquals(esperado, aut.avaliar(null, "pl.alfa", "ana", meioDia))
        assertEquals(esperado, aut.avaliar("", "pl.alfa", "ana", meioDia))
        // mesmo com reporter nulo e autor nulo, nunca vira REPORTER por igualdade de nulos
        assertEquals(esperado, aut.avaliar(null, null, null, meioDia))
    }

    @Test
    fun vigenciaTerminouOntem() {
        assertEquals(Avaliacao(false, MotivoAutorizacao.NAO_AUTORIZADO), aut.avaliar("lider.alfa", "pl.alfa", "ana", meioDia))
        assertEquals(
            Avaliacao(true, MotivoAutorizacao.LIDER_DO_SQUAD),
            aut.avaliar("lider.alfa", "pl.alfa", "ana", i("2026-10-09T12:00:00Z")),
        )
    }

    @Test
    fun dataCivilDeManaus() {
        // 03:30Z de 10/10 = 23:30 de 09/10 em Manaus: ainda vale o líder antigo
        assertEquals(
            Avaliacao(true, MotivoAutorizacao.LIDER_DO_SQUAD),
            aut.avaliar("lider.alfa", "pl.alfa", "ana", i("2026-10-10T03:30:00Z")),
        )
        assertEquals(
            Avaliacao(false, MotivoAutorizacao.NAO_AUTORIZADO),
            aut.avaliar("lider.alfa2", "pl.alfa", "ana", i("2026-10-10T03:30:00Z")),
        )
    }

    @Test
    fun coerenteComIsReporterAuthorized() {
        val data = d("2026-10-10")
        val combinacoes = listOf(
            Triple("pl.alfa", "pl.alfa", "ana"),
            Triple("coord.um", "pl.alfa", "ana"),
            Triple("lider.alfa2", "pl.alfa", "ana"),
            Triple("lider.beta", "pl.alfa", "ana"),
            Triple("lider.alfa", "pl.alfa", "ana"),
            Triple("bruno", "pl.alfa", "ana"),
        )
        for ((autor, reporter, resp) in combinacoes) {
            val esperado = roles.isReporterAuthorized(reporter, autor, resp, data)
            assertEquals(esperado, aut.avaliar(autor, reporter, resp, meioDia).autorizado, autor)
        }
    }
}
